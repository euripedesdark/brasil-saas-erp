import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../contexts/AuthContext';
import { apiFetch } from '../../services/ApiConfig';
import './grafana-embed.css';

/**
 * Dashboard do BI embutido na tela do sistema.
 *
 * <p>O Grafana escuta so no loopback. O endereco devolvido aqui e um caminho
 * do proprio ERP (`/api/bi/grafana/d/...`), e o ERP repassa a chamada para o
 * Grafana com a sessao aberta. Por isso o iframe fica na mesma origem do
 * sistema, nao ha porta 3000 exposta, e o filtro de departamento ja vem
 * fechado do servidor.
 */
export default function GrafanaBiDashboard() {
    const { t } = useTranslation();
    const { user } = useAuth();
    const [dashboard, setDashboard] = useState(null);
    const [erro, setErro] = useState(false);

    useEffect(() => {
        const controller = new AbortController();
        setDashboard(null);
        setErro(false);
        apiFetch('/api/bi/grafana/dashboard', { signal: controller.signal })
            .then(response => (response.ok ? response.json() : Promise.reject(new Error('BI indisponivel'))))
            .then(body => { if (!controller.signal.aborted) setDashboard(body.data); })
            .catch(() => { if (!controller.signal.aborted) setErro(true); });
        return () => controller.abort();
    }, [user?.empresaId, user?.id]);

    const src = useMemo(() => {
        if (!dashboard?.url) return null;
        const query = new URLSearchParams(dashboard.query || {}).toString();
        return query ? `${dashboard.url}?${query}` : dashboard.url;
    }, [dashboard]);

    if (erro) {
        return <p role="alert" className="bi-grafana-status">{t('biOverview.grafanaError')}</p>;
    }
    if (!src) {
        return <p role="status" className="bi-grafana-status">{t('biOverview.grafanaLoading')}</p>;
    }

    return <div className="bi-grafana">
        <iframe
            title={t('biOverview.grafanaTitle')}
            src={src}
            className="bi-grafana-frame"
            loading="lazy"
            referrerPolicy="same-origin"
            sandbox="allow-scripts allow-same-origin allow-popups allow-downloads"
        />
    </div>;
}
