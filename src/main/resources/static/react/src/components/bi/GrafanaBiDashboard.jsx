import React, { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import './grafana-embed.css';

/**
 * O dashboard do Grafana dentro da tela de BI.
 *
 * <p>Aponta direto para o Grafana. Sem proxy, sem camada no meio: o Grafana
 * fala com o banco `brasil_saas_bi` e o filtro de departamento vive nas
 * queries do painel, em `$empresa` e `$area`.
 *
 * <p>Quem abre o Grafana direto no navegador entra com a mesma conta do AD
 * (LDAP). Quem vem pela tela do sistema chega pelo `kiosk`, que é a sessão já
 * aberta pelo Grafana para aquele navegador.
 *
 * @param url        Endereco do dashboard (vem da configuracao do sistema).
 * @param areas      Áreas que a pessoa pode ver, ja fechadas pelo escopo.
 * @param empresaId  Empresa da pessoa, vem do token.
 */
export default function GrafanaBiDashboard({ url, uid, empresaId, areas }) {
    const { t } = useTranslation();

    const src = useMemo(() => {
        if (!url) return null;
        // Aponta direto para o dashboard pelo UID. A raiz do Grafana mostra o
        // painel inicial de cada pessoa, e quem entra pela primeira vez nao
        // tem nenhum — abriria um iframe em branco.
        const base = uid ? `${url.replace(/\/$/, '')}/d/${encodeURIComponent(uid)}` : url.replace(/\/$/, '');
        // kiosk sem valor esconde a navegacao sem travar o painel numa tela
        // de 1920. Com kiosk=tvpc o Grafana desenha o painel naquele tamanho
        // fixo e o iframe so mostra um recorte, com os KPIs sobrepostos.
        const query = new URLSearchParams({
            orgId: '1',
            kiosk: '',
            refresh: '5m',
            theme: 'dark',
            'var-empresa': String(empresaId ?? ''),
            'var-area': (areas?.length ? areas : ['financeiro', 'producao', 'vendas', 'servicos']).join(','),
        });
        return `${base}?${query.toString()}`;
    }, [url, uid, empresaId, areas]);

    if (!src) return null;

    return <div className="bi-grafana">
        <iframe
            title={t('biOverview.grafanaTitle')}
            src={src}
            className="bi-grafana-frame"
            loading="lazy"
            referrerPolicy="same-origin"
        />
    </div>;
}
