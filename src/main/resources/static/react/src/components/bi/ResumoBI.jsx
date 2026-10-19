import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Chart, registerables } from 'chart.js';
import ApiConfig, { apiFetch } from '../../services/ApiConfig';
import { useAuth } from '../../contexts/AuthContext';
import './ResumoBI.css';

Chart.register(...registerables);

const AREA_COLORS = { financeiro: '#60a5fa', producao: '#34d399', vendas: '#fbbf24', servicos: '#c084fc' };
const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const number = new Intl.NumberFormat('pt-BR');
const month = new Intl.DateTimeFormat('pt-BR', { month: 'short', year: 'numeric' });

function metric(area, code) {
    return area?.indicadores?.find(item => item.codigo === code)?.valor ?? 0;
}
function fmt(value, unit) { return unit === 'BRL' ? money.format(Number(value) || 0) : number.format(Number(value) || 0); }
function ChartCanvas({ area, title, color = AREA_COLORS[area?.codigo] || '#60a5fa' }) {
    const ref = useRef(null);
    const chart = useRef(null);
    useEffect(() => {
        if (!ref.current) return undefined;
        chart.current?.destroy();
        const pontos = area?.evolucao || [];
        chart.current = new Chart(ref.current, {
            type: 'line',
            data: { labels: pontos.map(p => month.format(new Date(`${p.mes}T12:00:00`))), datasets: [{ label: title, data: pontos.map(p => Number(p.valor) || 0), borderColor: color, backgroundColor: `${color}33`, fill: true, tension: .35, pointRadius: 3 }] },
            options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { x: { ticks: { color: '#aab4c3' }, grid: { display: false } }, y: { ticks: { color: '#aab4c3', callback: v => area?.evolucao?.[0]?.unidade === 'BRL' ? money.format(v) : number.format(v) }, grid: { color: '#ffffff12' } } } }
        });
        return () => chart.current?.destroy();
    }, [area, title, color]);
    return <div className="bi-chart-wrap"><canvas ref={ref} /></div>;
}

function KpiCard({ title, value, unit = 'BRL', accent }) {
    return <article className="bi-kpi" style={{ '--kpi-accent': accent }}><span>{title}</span><strong>{fmt(value, unit)}</strong></article>;
}

export function ResumoBI() {
    const { t } = useTranslation();
    const { user } = useAuth();
    const [resumo, setResumo] = useState(null);
    const [loading, setLoading] = useState(true);
    const [erro, setErro] = useState(false);
    useEffect(() => {
        const controller = new AbortController();
        setLoading(true); setErro(false);
        apiFetch(`${ApiConfig.BASE_URL}/api/bi/resumo`, { signal: controller.signal })
            .then(async response => response.ok ? response.json() : Promise.reject(new Error('indisponível')))
            .then(body => { if (!controller.signal.aborted) setResumo(body?.data); })
            .catch(() => { if (!controller.signal.aborted) setErro(true); })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [user?.empresaId, user?.id]);
    const areas = useMemo(() => Object.fromEntries((resumo?.areas || []).map(a => [a.codigo, a])), [resumo]);
    const periodo = resumo ? `${new Date(`${resumo.inicio}T12:00:00`).toLocaleDateString('pt-BR')} – ${new Date(`${resumo.fim}T12:00:00`).toLocaleDateString('pt-BR')}` : '';
    return <section className="bi-overview" aria-labelledby="bi-overview-title">
        <div className="bi-heading"><div><p className="bi-eyebrow">BUSINESS INTELLIGENCE</p><h2 id="bi-overview-title">{t('biOverview.title')}</h2>{periodo && <small>{periodo}</small>}</div><button className="bi-refresh" onClick={() => window.location.reload()}>{t('biOverview.refresh', 'Atualizar')}</button></div>
        {loading && <div className="bi-state">{t('biOverview.loading')}</div>}
        {erro && <div className="bi-state bi-error">{t('biOverview.error')}</div>}
        {!loading && !erro && resumo && <>
            <div className="bi-kpis">
                <KpiCard title="A receber" value={metric(areas.financeiro, 'a_receber')} accent="#60a5fa" />
                <KpiCard title="A pagar" value={metric(areas.financeiro, 'a_pagar')} accent="#fb7185" />
                <KpiCard title="Saldo previsto" value={metric(areas.financeiro, 'saldo_previsto')} accent="#34d399" />
                <KpiCard title="Vendas faturadas" value={metric(areas.vendas, 'valor_faturado')} accent="#fbbf24" />
            </div>
            <div className="bi-grid">
                <article className="bi-panel bi-panel-wide"><header><h3>Posição financeira ao longo do tempo</h3><span>A receber − a pagar</span></header><ChartCanvas area={areas.financeiro} title="Saldo previsto" /></article>
                <article className="bi-panel"><header><h3>Ordens de produção por mês</h3><span>{number.format(metric(areas.producao, 'ordens'))} ordens</span></header><ChartCanvas area={areas.producao} title="Ordens" color={AREA_COLORS.producao} /></article>
                <article className="bi-panel"><header><h3>Ordens de serviço por mês</h3><span>{number.format(metric(areas.servicos, 'abertas'))} abertas</span></header><ChartCanvas area={areas.servicos} title="Serviços" color={AREA_COLORS.servicos} /></article>
                <article className="bi-panel"><header><h3>Valor de pedidos de venda</h3><span>{money.format(metric(areas.vendas, 'valor_pedidos'))}</span></header><ChartCanvas area={areas.vendas} title="Vendas" color={AREA_COLORS.vendas} /></article>
            </div>
        </>}
    </section>;
}
export default ResumoBI;
