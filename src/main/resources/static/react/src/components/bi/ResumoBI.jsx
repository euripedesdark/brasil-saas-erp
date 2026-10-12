import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import ApiConfig, { apiFetch } from '../../services/ApiConfig';
import { useAuth } from '../../contexts/AuthContext';
import './ResumoBI.css';
import { Chart } from 'primereact/chart';
import GraylogBiPresentation from './GraylogBiPresentation';

const isoDate = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export default function ResumoBI() {
    const { t, i18n } = useTranslation();
    const { user } = useAuth();
    const [inicio, setInicio] = useState(() => isoDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1)));
    const [fim, setFim] = useState(() => isoDate(new Date()));
    const [periodo, setPeriodo] = useState({ inicio, fim });
    const [resumo, setResumo] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [graylog, setGraylog] = useState(null);

    useEffect(() => {
        const controller = new AbortController();
        setGraylog(null);
        apiFetch(`${ApiConfig.BASE_URL}/api/bi/integracao/graylog`, { signal: controller.signal })
            .then(async response => response.ok ? response.json() : null)
            .then(body => { if (!controller.signal.aborted) setGraylog(body?.data); })
            .catch(() => {});
        return () => controller.abort();
    }, [user?.empresaId]);

    useEffect(() => {
        const controller = new AbortController();
        setLoading(true); setError(false); setResumo(null);
        apiFetch(`${ApiConfig.BASE_URL}/api/bi/resumo?${new URLSearchParams(periodo)}`, { signal: controller.signal })
            .then(async response => {
                if (!response.ok) throw new Error('BI request failed');
                return response.json();
            })
            .then(body => { if (!controller.signal.aborted) setResumo(body.data); })
            .catch(() => { if (!controller.signal.aborted) setError(true); })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [periodo, user?.empresaId]);

    const month = value => new Intl.DateTimeFormat(i18n.resolvedLanguage || 'pt-BR',
        { month: 'short', year: 'numeric' }).format(new Date(`${value}T12:00:00`));
    const format = indicador => new Intl.NumberFormat(i18n.resolvedLanguage || 'pt-BR',
        indicador.unidade === 'BRL' ? { style: 'currency', currency: 'BRL' } : { maximumFractionDigits: 0 }).format(indicador.valor);

    return <section className="bi-overview" aria-labelledby="bi-overview-title" aria-busy={loading}>
        <h2 id="bi-overview-title">{t('biOverview.title')}</h2>
        {graylog?.configured && <p>
            <a href={graylog.webUrl} target="_blank" rel="noopener noreferrer">{t('biOverview.openGraylog')}</a>
            {!graylog.presentationReady && <span> — {t('biOverview.graylogPending')}</span>}
        </p>}
        {graylog?.configured && <GraylogBiPresentation webUrl={graylog.webUrl} />}
        <form className="bi-overview-filters" onSubmit={event => { event.preventDefault(); setPeriodo({ inicio, fim }); }}>
            <label>{t('biOverview.start')}<input type="date" required value={inicio} max={fim} onChange={event => setInicio(event.target.value)} /></label>
            <label>{t('biOverview.end')}<input type="date" required value={fim} min={inicio} onChange={event => setFim(event.target.value)} /></label>
            <button type="submit" disabled={loading}>{t('biOverview.refresh')}</button>
        </form>
        <p>{t('biOverview.periodHelp')}</p>
        {loading && <p role="status">{t('biOverview.loading')}</p>}
        {error && <div role="alert"><p>{t('biOverview.error')}</p><button onClick={() => setPeriodo({ inicio, fim })}>{t('biOverview.retry')}</button></div>}
        {resumo && <div className="bi-overview-grid">
            {resumo.areas.map(area => <article key={area.codigo} className="bi-overview-card">
                <h3>{t(`biOverview.areas.${area.codigo}`)}</h3>
                <p className="bi-overview-value">{format(area.indicadores[0])}</p>
                <p>{t(`biOverview.metrics.${area.indicadores[0].codigo}`)}</p>
                <dl>{area.indicadores.slice(1).map(indicador => <React.Fragment key={indicador.codigo}>
                    <dt>{t(`biOverview.metrics.${indicador.codigo}`)}</dt><dd>{format(indicador)}</dd>
                </React.Fragment>)}</dl>
                <h4>{t(`biOverview.evolution.${area.codigo}`)}</h4>
                {area.evolucao?.length > 0 ? <>
                    <Chart type="bar" data={{ labels: area.evolucao.map(point => month(point.mes)),
                        datasets: [{ label: t(`biOverview.evolution.${area.codigo}`),
                            data: area.evolucao.map(point => Number(point.valor)), backgroundColor: '#3b82f6' }] }}
                        options={{ maintainAspectRatio: false, plugins: { legend: { display: false },
                            tooltip: { callbacks: { label: context => format({ valor: context.raw,
                                unidade: area.evolucao[context.dataIndex].unidade }) } } },
                            scales: { y: { beginAtZero: true, ticks: { callback: value => format({ valor: value,
                                unidade: area.evolucao[0].unidade }) } } } }} />
                    <details><summary>{t('biOverview.details')}</summary>
                        <table><caption>{t(`biOverview.evolution.${area.codigo}`)}</caption>
                            <thead><tr><th scope="col">{t('biOverview.month')}</th><th scope="col">{t('biOverview.value')}</th></tr></thead>
                            <tbody>{area.evolucao.map(point => <tr key={point.mes}><th scope="row">{month(point.mes)}</th><td>{format(point)}</td></tr>)}</tbody>
                        </table>
                    </details>
                </> : <p>{t('biOverview.noEvolution')}</p>}
            </article>)}
        </div>}
    </section>;
}
