package br.com.brasil_saas.shared.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class GerenciadorSqlServiceSecurityTest {
    @Test void writableCteCannotBypassDestructiveConfirmation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        GerenciadorSqlService service = new GerenciadorSqlService(jdbc);
        assertThrows(GerenciadorSqlService.ConfirmacaoNecessariaException.class,
                () -> service.executar("with deleted as (delete from pedidos returning *) select * from deleted", false, "admin"));
        verifyNoInteractions(jdbc);
    }
    @Test void multipleStatementsCannotBypassConfirmationThroughSelect() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        GerenciadorSqlService service = new GerenciadorSqlService(jdbc);
        assertThrows(IllegalArgumentException.class,
                () -> service.executar("select 1; drop table pedidos", false, "admin"));
        verifyNoInteractions(jdbc);
    }
}
