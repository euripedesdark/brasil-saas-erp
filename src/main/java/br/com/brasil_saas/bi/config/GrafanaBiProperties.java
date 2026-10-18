package br.com.brasil_saas.bi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Endereco do dashboard do Grafana.
 *
 * <p>So metadado de conexao: o Grafana guarda a propria senha (a do AD), e ela
 * nunca sai daqui para o navegador. O front recebe o endereco e monta a URL do
 * dashboard; a leitura dos dados e o filtro de departamento acontecem dentro do
 * Grafana, nas queries do painel.
 */
@Component
@ConfigurationProperties(prefix = "bi.grafana")
public class GrafanaBiProperties {
    /** Endereco do dashboard, com o prefixo publico do Grafana. */
    private String dashboardUrl;
    /** Identificador do dashboard no Grafana. */
    private String dashboardUid = "bi-brasil-saas";

    public String getDashboardUrl() { return dashboardUrl; }
    public void setDashboardUrl(String dashboardUrl) { this.dashboardUrl = dashboardUrl; }
    public String getDashboardUid() { return dashboardUid; }
    public void setDashboardUid(String dashboardUid) { this.dashboardUid = dashboardUid; }
}
