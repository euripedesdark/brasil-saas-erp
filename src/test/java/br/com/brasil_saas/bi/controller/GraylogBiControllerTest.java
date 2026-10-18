package br.com.brasil_saas.bi.controller;

import br.com.brasil_saas.bi.config.GraylogBiProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraylogBiControllerTest {
    @Test void defaultsDoNotClaimConnectionOrPresentation() {
        var response = new GraylogBiController(new GraylogBiProperties()).status().getData();
        assertFalse(response.configured());
        assertFalse(response.presentationReady());
        assertNull(response.webUrl());
    }

    @Test void configuredConnectionCarriesNoPresentation() {
        var properties = new GraylogBiProperties();
        properties.setConfigured(true);
        properties.setWebUrl("https://example.org/graylog/");
        properties.setApiUrl("http://127.0.0.1:9000/api/");
        properties.setVersion("7.1.9");
        var response = new GraylogBiController(properties).status().getData();
        assertTrue(response.configured());
        assertFalse(response.presentationReady());
        assertEquals("https://example.org/graylog/", response.webUrl());
        assertEquals("7.1.9", response.version());
    }

    @Test void refusesUnsafeBrowserUrls() {
        for (String url : java.util.List.of("javascript:alert(1)", "https://admin:secret@example.org/", "https://example.org/?token=secret", "https://example.org/#token")) {
            var properties = new GraylogBiProperties();
            properties.setConfigured(true); properties.setWebUrl(url);
            var response = new GraylogBiController(properties).status().getData();
            assertFalse(response.configured()); assertNull(response.webUrl());
        }
    }
}
