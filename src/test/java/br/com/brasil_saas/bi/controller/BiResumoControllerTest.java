package br.com.brasil_saas.bi.controller;

import br.com.brasil_saas.bi.dto.BiResumo;
import br.com.brasil_saas.bi.service.BiResumoService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BiResumoControllerTest {
    private AnnotationConfigApplicationContext context;
    private BiResumoService service;
    private MockMvc mvc;
    private final LocalDate inicio = LocalDate.of(2026, 1, 1);
    private final LocalDate fim = LocalDate.of(2026, 1, 31);

    @TestConfiguration @EnableMethodSecurity
    @org.springframework.context.annotation.Profile("bi-controller-unit")
    static class Config {
        @Bean BiResumoService service() { return mock(BiResumoService.class); }
        @Bean BiResumoController controller(BiResumoService s) { return new BiResumoController(s); }
    }

    @BeforeEach void prepare() {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("bi-controller-unit");
        context.register(Config.class);
        context.refresh();
        service = context.getBean(BiResumoService.class);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(BiResumoController.class))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                        com.fasterxml.jackson.databind.json.JsonMapper.builder()
                                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
        login(7L, "ROLE_ADMIN");
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }
    private void login(Long empresa, String role) {
        var authorities = List.of(new SimpleGrantedAuthority(role));
        var principal = new AuthenticatedUser(1L, "fixture", "unused", true, empresa, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @Test void resumoIgnoraEmpresaEnviadaPeloCliente() throws Exception {
        when(service.consultar(7L, inicio, fim)).thenReturn(new BiResumo(inicio, fim, fim, List.of()));
        mvc.perform(get("/api/bi/resumo").param("inicio", inicio.toString()).param("fim", fim.toString())
                .param("empresaId", "999").header("X-Empresa-Id", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.areas").isArray());
        verify(service).consultar(7L, inicio, fim);
        verifyNoMoreInteractions(service);
    }

    @Test void endpointSelecionaCadaAreaComTenantAutenticado() throws Exception {
        for (String area : List.of("financeiro", "producao", "vendas", "servicos")) {
            when(service.consultarArea(7L, inicio, fim, area)).thenReturn(new BiResumo(inicio, fim, fim,
                    List.of(new BiResumo.Area(area, List.of(), List.of(new BiResumo.Ponto(inicio, java.math.BigDecimal.ONE, "COUNT"))))));
            mvc.perform(get("/api/bi/resumo/" + area).param("inicio", inicio.toString()).param("fim", fim.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.areas[0].codigo").value(area))
                    .andExpect(jsonPath("$.data.areas[0].evolucao[0].mes").value("2026-01-01"));
            verify(service).consultarArea(7L, inicio, fim, area);
        }
    }

    @Test void rejeitaDataInvalidaAntesDeConsultar() throws Exception {
        mvc.perform(get("/api/bi/resumo").param("inicio", "nao-data").param("fim", fim.toString()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void autorizacaoBloqueiaUsuarioSemPerfilAdministrativo() {
        login(7L, "ROLE_USER");
        assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(
                get("/api/bi/resumo").param("inicio", inicio.toString()).param("fim", fim.toString())));
        verifyNoInteractions(service);
    }
}
