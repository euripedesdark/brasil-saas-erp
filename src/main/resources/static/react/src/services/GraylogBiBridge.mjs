export const BI_PROTOCOL = 'brasil-saas-bi-v1';
export const BI_AREAS = Object.freeze(['financeiro', 'producao', 'vendas', 'servicos']);

// getRandomValues also works on the existing HTTP LAN deployment.
export const biNonce = () => Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, '0')).join('');

export function validPeriod(inicio, fim) {
    const parse = value => {
        if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return NaN;
        const date = new Date(`${value}T00:00:00Z`);
        return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value ? date.getTime() : NaN;
    };
    const start = parse(inicio), end = parse(fim);
    return Number.isFinite(start) && Number.isFinite(end) && end >= start && end - start <= 365 * 86400000;
}

export function presentationUrl(webUrl, erpOrigin, nonce, locale) {
    const url = new URL(webUrl);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) throw new Error('Invalid Graylog URL');
    url.pathname = `${url.pathname.replace(/\/$/, '')}/brasil-saas-bi`;
    url.hash = new URLSearchParams({ erpOrigin, nonce }).toString();
    if (['pt-BR', 'en-US', 'es-ES', 'fr-FR'].includes(locale)) url.hash += `&locale=${locale}`;
    return url;
}

// Only the ERP performs authenticated requests. No JWT or company identifier crosses this channel.
export function createBiHost({ target, origin, nonce, query, reply, now = Date.now, onReady = () => {} }) {
    let disposed = false;
    let active = 0;
    let count = 0;
    let windowStart = now();
    const ids = new Set();
    const controllers = new Set();
    const send = data => {
        if (!disposed) { try { reply({ protocol: BI_PROTOCOL, nonce, ...data }, origin); } catch { /* Closed presentation window. */ } }
    };
    return {
        async receive(event) {
            const data = event.data;
            if (disposed || event.source !== target || event.origin !== origin || data?.protocol !== BI_PROTOCOL || data.nonce !== nonce) return;
            if (data.type === 'hello') { onReady(); send({ type: 'ready' }); return; }
            if (data.type !== 'query' || typeof data.id !== 'string' || !/^[a-zA-Z0-9-]{1,64}$/.test(data.id)) return;
            if (!BI_AREAS.includes(data.area) || !validPeriod(data.inicio, data.fim)) { send({ type: 'error', id: data.id, code: 'INVALID_QUERY' }); return; }
            if (ids.has(data.id)) return;
            if (now() - windowStart >= 60000) { count = 0; windowStart = now(); ids.clear(); }
            if (active >= 4 || count >= 24) { send({ type: 'error', id: data.id, code: 'BUSY' }); return; }
            ids.add(data.id); count++; active++;
            const controller = new AbortController(); controllers.add(controller);
            const timer = setTimeout(() => controller.abort(), 10000);
            try {
                const result = await query(data.area, data.inicio, data.fim, controller.signal);
                if (!controller.signal.aborted) send({ type: 'result', id: data.id, data: result });
                else send({ type: 'error', id: data.id, code: 'UNAVAILABLE' });
            } catch {
                send({ type: 'error', id: data.id, code: 'UNAVAILABLE' });
            } finally { clearTimeout(timer); active--; controllers.delete(controller); }
        },
        dispose() {
            if (disposed) return;
            send({ type: 'closed' }); disposed = true;
            controllers.forEach(controller => controller.abort()); controllers.clear();
        },
    };
}

export function createBiClient({ parent, origin, nonce, send, onReady, onResult, onError, onClosed, id = biNonce }) {
    let disposed = false, ready = false;
    const pending = new Map();
    const post = data => send({ protocol: BI_PROTOCOL, nonce, ...data }, origin);
    const finish = requestId => {
        const request = pending.get(requestId);
        if (request) { clearTimeout(request.timer); pending.delete(requestId); }
        return request;
    };
    const close = () => {
        if (disposed) return;
        disposed = true; ready = false;
        pending.forEach(request => clearTimeout(request.timer)); pending.clear(); onClosed();
    };
    return {
        hello() { if (!disposed) post({ type: 'hello' }); },
        query(area, inicio, fim) {
            if (disposed || !ready || pending.size >= 4 || !BI_AREAS.includes(area) || !validPeriod(inicio, fim)) return false;
            const requestId = id();
            const timer = setTimeout(() => { finish(requestId); if (!disposed) onError(area); }, 12000);
            pending.set(requestId, { area, timer, inicio, fim });
            post({ type: 'query', id: requestId, area, inicio, fim }); return true;
        },
        receive(event) {
            const data = event.data;
            if (disposed || event.source !== parent || event.origin !== origin || data?.protocol !== BI_PROTOCOL || data.nonce !== nonce) return;
            if (data.type === 'closed') { close(); return; }
            if (data.type === 'ready') { if (!ready) { ready = true; onReady(); } return; }
            if (!['result', 'error'].includes(data.type)) return;
            const request = finish(data.id);
            if (!request) return;
            const result = data.data;
            if (data.type === 'error' || result?.inicio !== request.inicio || result?.fim !== request.fim
                    || !Array.isArray(result?.areas) || result.areas.length !== 1 || result.areas[0]?.codigo !== request.area
                    || !Array.isArray(result.areas[0].indicadores) || result.areas[0].indicadores.length !== 3
                    || !result.areas[0].indicadores.every(item => item && typeof item.codigo === 'string'
                        && ['BRL', 'COUNT'].includes(item.unidade) && ['number', 'string'].includes(typeof item.valor)
                        && String(item.valor).length <= 64 && Number.isFinite(Number(item.valor)))) {
                onError(request.area); return;
            }
            onResult(request.area, result);
        },
        dispose: close,
    };
}
