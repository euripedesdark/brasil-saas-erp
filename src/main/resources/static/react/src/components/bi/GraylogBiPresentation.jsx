import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../contexts/AuthContext';
import { apiFetch } from '../../services/ApiConfig';
import { biNonce, createBiHost, presentationUrl } from '../../services/GraylogBiBridge.mjs';

export default function GraylogBiPresentation({ webUrl }) {
    const { t, i18n } = useTranslation();
    const { user } = useAuth();
    const session = useRef(null);
    const [status, setStatus] = useState('idle');
    useEffect(() => {
        setStatus('idle');
        return () => { session.current?.(); session.current = null; };
    }, [user?.empresaId, user?.id, webUrl]);

    const open = () => {
        session.current?.(); session.current = null;
        const nonce = biNonce();
        let url;
        try { url = presentationUrl(webUrl, window.location.origin, nonce, i18n.resolvedLanguage); }
        catch { setStatus('unavailable'); return; }
        const popup = window.open(url.href, `brasil-saas-bi-${nonce}`, 'popup,width=1150,height=800');
        if (!popup) { setStatus('blocked'); return; }
        setStatus('connecting');
        let closed = false;
        const host = createBiHost({
            target: popup, origin: url.origin, nonce,
            reply: (data, origin) => popup.postMessage(data, origin),
            onReady: () => { clearTimeout(timeout); setStatus('ready'); },
            query: async (area, inicio, fim, signal) => {
                const response = await apiFetch(`/api/bi/resumo/${area}?${new URLSearchParams({ inicio, fim })}`, { signal });
                if (!response.ok) throw new Error('BI unavailable');
                const body = await response.json();
                if (!body.data || body.data.areas?.length !== 1 || body.data.areas[0].codigo !== area) throw new Error('Invalid BI response');
                return body.data;
            },
        });
        const receive = event => { void host.receive(event); };
        window.addEventListener('message', receive);
        const dispose = () => {
            if (closed) return;
            closed = true; clearTimeout(timeout); clearInterval(poll);
            host.dispose(); window.removeEventListener('message', receive);
            window.removeEventListener('pagehide', dispose);
            popup.close();
        };
        const timeout = setTimeout(() => { dispose(); setStatus('unavailable'); }, 60000);
        const poll = setInterval(() => { if (popup.closed) { dispose(); setStatus('idle'); } }, 1000);
        window.addEventListener('pagehide', dispose);
        session.current = dispose;
    };
    return <div>
        <button type="button" onClick={open}>{t('biOverview.graylogBi')}</button>
        {status !== 'idle' && <p role={['blocked', 'unavailable'].includes(status) ? 'alert' : 'status'}>{t(`biOverview.graylogStatus.${status}`)}</p>}
    </div>;
}
