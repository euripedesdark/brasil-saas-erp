package br.com.brasil_saas.bi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Connection metadata only. Graylog credentials must never be sent to a browser. */
@Component
@ConfigurationProperties(prefix = "brasil-saas.bi.graylog")
public class GraylogBiProperties {
    private boolean configured;
    private String apiUrl;
    private String webUrl;
    private String version;

    public boolean isConfigured() { return configured; }
    public void setConfigured(boolean configured) { this.configured = configured; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getWebUrl() { return webUrl; }
    public void setWebUrl(String webUrl) { this.webUrl = webUrl; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
