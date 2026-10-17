import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../contexts/AuthContext';
import { apiFetch } from '../../services/ApiConfig';
import { biNonce, createBiHost, presentationUrl } from '../../services/GraylogBiBridge.mjs';

export default function GraylogBiPresentation({ webUrl }) {
    const { t, i18n } = useTranslation();
    const { user } = useAuth();
    const iframeRef = useRef(null);
    const session = useRef(null);
    const [status, setStatus] = useState('idle');
    const [iframeUrl, setIframeUrl] = useState(null);

    useEffect(() => {
        setStatus('idle');
        setIframeUrl(null);
        return () => { session.current?.(); session.current = null; };
    }, [user?.empresaId, user?.id, webUrl]);

    useEffect(() => {
        if (!iframeUrl || !iframeRef.current) return;
        const iframe = iframeRef.current;
        const nonce = biNonce();
        let url;
        try { url = presentationUrl(webUrl, window.location.origin, nonce, i18n.resolvedLanguage); }
        catch { setStatus('unavailable'); return; }

        const host = createBiHost({
            target: iframe.contentWindow,
            origin: url.origin,
            nonce,
            reply: (data, origin) => { try { iframe.contentWindow.postMessage(data, origin); } catch {} },
            onReady: () => { setStatus('ready'); },
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
        const timeout = setTimeout(() => { setStatus('unavailable'); }, 60000);
        session.current = () => { clearTimeout(timeout); host.dispose(); window.removeEventListener('message', receive); };
        iframe.src = url.href;
        return () => { session.current?.(); session.current = null; };
    }, [iframeUrl]);

    // Auto-load iframe when configured
    useEffect(() => {
        if (webUrl && !iframeUrl) {
            setIframeUrl(webUrl);
            setStatus('connecting');
        }
    }, [webUrl]);

    if (!webUrl) return null;

    return <div className="graylog-bi-presentation">
        <iframe
            ref={iframeRef}
            title="BI Graylog"
            style={{ width: '100%', height: '600px', border: '1px solid #e5e7eb', borderRadius: '8px' }}
            sandbox="allow-scripts allow-same-origin allow-popups"
        />
        {status !== 'idle' && status !== 'connecting' && (
            <p role={['blocked', 'unavailable'].includes(status) ? 'alert' : 'status'}>
                {t(`biOverview.graylogStatus.${status}`)}
            </p>
        )}
    </div>;
}