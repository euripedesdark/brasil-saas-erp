import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import ApiConfig, { apiFetch } from '../../services/ApiConfig';
import { useAuth } from '../../contexts/AuthContext';
import './ResumoBI.css';
import GrafanaBiDashboard from './GrafanaBiDashboard';

/**
 * Tela de BI.
 *
 * <p>A leitura dos dados e a representacao visual sao do Grafana, sobre o banco
 * {@code brasil_saas_bi}. O ERP nao calcula indicador nenhum aqui: ele diz onde
 * o dashboard esta e quais areas a pessoa pode ver, e o Grafana faz o resto.
 *
 * <p>Foi por isso que os cards com PrimeReact sairam. Eles mostravam um total
 * diferente do painel — R$ 60,00 a receber contra R$ 18.123,92 no banco — porque
 * vinham do {@code /api/bi/resumo} com uma janela de datas, enquanto o Grafana
 * lia a view inteira. Dois numeros para a mesma coisa e' pior do que nenhum.
 */
export function ResumoBI() {
    const { t } = useTranslation();
    const { user } = useAuth();
    const [grafana, setGrafana] = useState(null);
    const [erro, setErro] = useState(false);

    useEffect(() => {
        const controller = new AbortController();
        apiFetch(`${ApiConfig.BASE_URL}/api/bi/grafana/dashboard`, { signal: controller.signal })
            .then(async response => response.ok ? response.json() : Promise.reject(new Error('indisponivel')))
            .then(body => { if (!controller.signal.aborted) setGrafana(body?.data); })
            .catch(() => { if (!controller.signal.aborted) setErro(true); });
        return () => controller.abort();
    }, [user?.empresaId, user?.id]);

    return <section className="bi-overview" aria-labelledby="bi-overview-title">
        <h2 id="bi-overview-title">{t('biOverview.title')}</h2>
        {grafana?.url && <GrafanaBiDashboard
            url={grafana.url}
            empresaId={user?.empresaId}
            areas={grafana.areas}
        />}
        {!grafana?.url && erro && <p role="alert">{t('biOverview.grafanaError')}</p>}
    </section>;
}

export default ResumoBI;
