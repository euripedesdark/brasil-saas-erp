package br.com.brasil_saas.bi.service;

import br.com.brasil_saas.bi.model.Relatorio;
import br.com.brasil_saas.bi.repository.RelatorioRepository;
import br.com.brasil_saas.bi.service.impl.RelatorioServiceImpl;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelatorioTenantSqlTest {
    @Test void tenantDoServidorPrevaleceSobreAmbosParametrosDoCliente() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
        var repository = mock(RelatorioRepository.class);
        var relatorio = new Relatorio();
        relatorio.setEmpresaId(7L);
        relatorio.setNome("Teste");
        relatorio.setSqlQuery("select {empresaId} as tenant, {empresa_id} as tenant_alias, '{nome}' as valor");
        when(repository.findById(1L)).thenReturn(Optional.of(relatorio));
        var result = new RelatorioServiceImpl(repository, new JdbcTemplate(ds)).executarRelatorio(
                7L, 1L, Map.of("empresaId", 99L, "empresa_id", 99L, "nome", "x'; drop table itens; --"));
        var rows = (java.util.List<?>) result.get("dados");
        var row = (Map<?, ?>) rows.getFirst();
        assertEquals(7L, ((Number) row.get("TENANT")).longValue());
        assertEquals(7L, ((Number) row.get("TENANT_ALIAS")).longValue());
        assertEquals("x'; drop table itens; --", row.get("VALOR"));
    }
}
