package br.com.brasil_saas.bi.service;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReportSqlParametersTest {
    @Test void valuesNeverBecomeSqlEvenWithQuotesOrStatements() {
        String sql = "select * from pedidos where nome = '{nome}' and empresa_id = {empresaId}";
        Map<String, Object> parameters = Map.of("nome", "x'; drop table pedidos; --", "empresaId", 7L);
        assertEquals("select * from pedidos where nome = :nome and empresa_id = :empresaId",
                ReportSqlParameters.bind(sql, parameters));
    }
    @Test void missingParameterIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ReportSqlParameters.bind("select {empresaId}", Map.of()));
    }
    @Test void ordinarySqlAndRepeatedParametersRemainValid() {
        assertEquals("select :id, :id, 'fixo'",
                ReportSqlParameters.bind("select {id}, '{id}', 'fixo'", Map.of("id", 1)));
    }
}
