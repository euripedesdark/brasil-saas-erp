package br.com.brasil_saas.bi.service;

import br.com.brasil_saas.bi.model.Report;
import br.com.brasil_saas.bi.repository.*;
import br.com.brasil_saas.bi.service.impl.ReportServiceImpl;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportServiceBindingTest {
    @Test void similarlyNamedParametersAndSqlLikeValuesAreBoundExactly() {
        JdbcDataSource source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:report-bind;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("create table if not exists marker (id int)");
        ReportRepository repository = mock(ReportRepository.class);
        Report report = new Report(); report.setName("Seguro"); report.setEmpresaId(7L);
        report.setQuerySql("select :id as curto, :idLong as longo, '{empresaId}' as empresa");
        when(repository.findByIdAndEmpresaId(1L, 7L)).thenReturn(Optional.of(report));
        ReportServiceImpl service = new ReportServiceImpl(repository, mock(ReportParameterRepository.class),
                new NamedParameterJdbcTemplate(source));
        Map<String,Object> params = new LinkedHashMap<>();
        params.put("id", "x'; drop table marker; --"); params.put("idLong", "LONGO"); params.put("empresaId", 999L);
        String csv = new String(service.generateReport(1L, params, "CSV", 7L), StandardCharsets.UTF_8);
        assertTrue(csv.contains("LONGO")); assertTrue(csv.contains("drop table marker"));
        assertFalse(csv.contains("999"));
        assertEquals(0, jdbc.queryForObject("select count(*) from marker", Integer.class));
    }
}
