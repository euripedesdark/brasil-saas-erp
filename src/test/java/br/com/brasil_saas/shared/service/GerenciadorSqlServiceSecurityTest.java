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
    @Test void nomesInjetadosSaoRecusadosAntesDeConsultarCatalogo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new GerenciadorSqlService(jdbc);
        assertThrows(GerenciadorSqlService.IdentificadorInvalidoException.class,
                () -> service.contarRegistros("itens; DROP TABLE itens"));
        verifyNoInteractions(jdbc);
    }

    @Test void nomeValidoMasAusenteNoCatalogoNaoMontaConsultaDinamica() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("brasil_saas"), eq("inexistente"))).thenReturn(0);
        var service = new GerenciadorSqlService(jdbc);
        assertThrows(GerenciadorSqlService.IdentificadorInvalidoException.class,
                () -> service.contarRegistros("inexistente"));
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), eq("brasil_saas"), eq("inexistente"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test void navegacaoUsaNomesDoCatalogoELimitesBindados() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("brasil_saas"), eq("itens"))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq("brasil_saas"), eq("itens")))
                .thenReturn(java.util.List.of(java.util.Map.of("column_name", "id")));
        when(jdbc.queryForObject("select count(*) from \"brasil_saas\".\"itens\"", Long.class)).thenReturn(0L);
        var service = new GerenciadorSqlService(jdbc);
        service.navegar("itens", 2, 50, null, null);
        verify(jdbc).queryForList("select * from \"brasil_saas\".\"itens\" limit ? offset ?", new Object[]{50, 100L});
    }
}
