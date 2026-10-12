package br.com.brasil_saas.bi.service;

import br.com.brasil_saas.bi.dto.BiResumo;
import br.com.brasil_saas.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.*;

class BiResumoServiceTest {
    private JdbcTemplate jdbc;
    private BiResumoService service;
    private final LocalDate inicio = LocalDate.of(2026, 1, 1);
    private final LocalDate fim = LocalDate.of(2026, 1, 31);

    @BeforeEach void preparar() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE SCHEMA brasil_saas");
        jdbc.execute("CREATE TABLE brasil_saas.bc_fin_titulo (empresa_id bigint,tipo varchar(1),valor_saldo numeric(15,2),data_vencimento date,status varchar(30),deleted_at timestamp)");
        jdbc.execute("CREATE TABLE brasil_saas.bc_prod_ordem (empresa_id bigint,status varchar(30),data_inicio timestamp,created_at timestamp,data_fim timestamp,deleted_at timestamp)");
        jdbc.execute("CREATE TABLE brasil_saas.bc_ven_pedido (empresa_id bigint,tipo varchar(30),status varchar(30),data_emissao date,valor_total numeric(15,2),deleted_at timestamp)");
        jdbc.execute("CREATE TABLE brasil_saas.bc_srv_ordem_servico (empresa_id bigint,status varchar(30),valor_total numeric(15,2),abertura_at timestamp,fechamento_at timestamp,previsao_at timestamp,deleted_at timestamp)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V197__bi_consultas_quatro_areas.sql")).execute(ds);
        service = new BiResumoService(new NamedParameterJdbcTemplate(ds));
    }

    @Test void vazioRetornaQuatroAreasSemInventarDados() {
        var resumo = service.consultar(1L, inicio, fim);
        assertEquals(java.util.List.of("financeiro", "producao", "vendas", "servicos"), resumo.areas().stream().map(BiResumo.Area::codigo).toList());
        resumo.areas().forEach(area -> area.indicadores().forEach(i -> assertEquals(0, i.valor().compareTo(BigDecimal.ZERO))));
    }

    @Test void filtraEmpresaPeriodoExclusaoECancelamento() {
        jdbc.execute("INSERT INTO brasil_saas.bc_ven_pedido VALUES (1,'PEDIDO','ABERTO','2026-01-01',100,NULL),(1,'PEDIDO','FATURADO','2026-01-31',300,NULL),(2,'PEDIDO','ABERTO','2026-01-15',9000,NULL),(1,'PEDIDO','CANCELADO','2026-01-15',800,NULL),(1,'ORCAMENTO','ABERTO','2026-01-15',500,NULL),(1,'PEDIDO','ABERTO','2025-12-31',700,NULL),(1,'PEDIDO','ABERTO','2026-01-15',600,CURRENT_TIMESTAMP)");
        var resumo = service.consultar(1L, inicio, fim);
        valor(resumo, "vendas", "pedidos", "2");
        valor(resumo, "vendas", "valor_pedidos", "400");
        valor(resumo, "vendas", "ticket_medio", "200");
        valor(service.consultar(2L, inicio, fim), "vendas", "valor_pedidos", "9000");
    }

    @Test void saldosFinanceirosNaoSaoRecebimentosEOrdensUsamDatasCorretas() {
        jdbc.execute("INSERT INTO brasil_saas.bc_fin_titulo VALUES (1,'R',120,'2026-01-01','ABERTO',NULL),(1,'P',70,'2026-01-31','ABERTO',NULL),(1,'R',0,'2026-01-15','PAGO',NULL),(2,'R',900,'2026-01-15','ABERTO',NULL)");
        jdbc.execute("INSERT INTO brasil_saas.bc_prod_ordem VALUES (1,'EM_PROCESSO','2026-01-01','2026-01-01',NULL,NULL),(1,'FINALIZADO','2026-01-31','2026-01-31','2026-02-01',NULL),(2,'FINALIZADO','2026-01-15','2026-01-15',NULL,NULL)");
        jdbc.execute("INSERT INTO brasil_saas.bc_srv_ordem_servico VALUES (1,'ABERTA',40,'2026-01-01',NULL,NULL,NULL),(1,'FECHADA',250,'2025-12-10','2026-01-31',NULL,NULL),(2,'FECHADA',800,'2026-01-10','2026-01-15',NULL,NULL)");
        var resumo = service.consultar(1L, inicio, fim);
        valor(resumo, "financeiro", "a_receber", "120");
        valor(resumo, "financeiro", "a_pagar", "70");
        valor(resumo, "producao", "ordens", "2");
        valor(resumo, "producao", "finalizadas", "1");
        valor(resumo, "producao", "em_processo", "1");
        valor(resumo, "servicos", "abertas", "1");
        valor(resumo, "servicos", "concluidas", "1");
        valor(resumo, "servicos", "valor_concluido", "250");
    }

    @Test void exigeEmpresaEPeriodoLimitado() {
        assertThrows(BusinessException.class, () -> service.consultar(null, inicio, fim));
        assertThrows(BusinessException.class, () -> service.consultar(0L, inicio, fim));
        assertThrows(BusinessException.class, () -> service.consultar(1L, fim, inicio));
        assertThrows(BusinessException.class, () -> service.consultar(1L, inicio, inicio.plusDays(366)));
        assertDoesNotThrow(() -> service.consultar(1L, inicio, inicio.plusDays(365)));
    }

    @Test void consultaAreaSelecionadaSemConsultarAsDemais() {
        jdbc.execute("DROP VIEW brasil_saas_dl.v_bi_financeiro");
        jdbc.execute("DROP VIEW brasil_saas_dl.v_bi_producao");
        jdbc.execute("DROP VIEW brasil_saas_dl.v_bi_servicos");
        var resumo = service.consultarArea(1L, inicio, fim, "vendas");
        assertEquals(1, resumo.areas().size());
        assertEquals("vendas", resumo.areas().getFirst().codigo());
        assertThrows(BusinessException.class, () -> service.consultarArea(1L, inicio, fim, "vendas;DROP TABLE x"));
    }

    @Test void evolucaoMensalUsaDadosReaisEIsolaEmpresa() {
        jdbc.execute("INSERT INTO brasil_saas.bc_fin_titulo VALUES (1,'R',120,'2026-01-01','ABERTO',NULL),(1,'P',70,'2026-01-31','ABERTO',NULL),(2,'R',900,'2026-01-15','ABERTO',NULL)");
        jdbc.execute("INSERT INTO brasil_saas.bc_ven_pedido VALUES (1,'PEDIDO','FATURADO','2026-01-15',300,NULL),(1,'PEDIDO','PARCIAL','2026-01-16',100,NULL),(2,'PEDIDO','FATURADO','2026-01-15',9000,NULL)");
        var resumo = service.consultar(1L, inicio, fim);
        valor(resumo, "financeiro", "saldo_previsto", "50");
        valor(resumo, "vendas", "valor_faturado", "300");
        var vendas = resumo.areas().stream().filter(a -> a.codigo().equals("vendas")).findFirst().orElseThrow();
        assertEquals(1, vendas.evolucao().size());
        assertEquals(inicio, vendas.evolucao().getFirst().mes());
        assertEquals(0, new BigDecimal("400").compareTo(vendas.evolucao().getFirst().valor()));
        assertEquals("BRL", vendas.evolucao().getFirst().unidade());
        assertTrue(resumo.areas().stream().filter(a -> a.codigo().equals("producao")).findFirst().orElseThrow().evolucao().isEmpty());
    }

    @Test void agrupaMesesEmOrdemEPreservaDataDeFechamentoDosServicos() {
        jdbc.execute("INSERT INTO brasil_saas.bc_srv_ordem_servico VALUES (1,'FECHADA',250,'2025-12-10','2026-01-31',NULL,NULL),(1,'FECHADA',800,'2026-01-10','2026-02-15',NULL,NULL),(2,'FECHADA',900,'2026-01-10','2026-02-15',NULL,NULL)");
        var area = service.consultarArea(1L, inicio, fim.plusMonths(1), "servicos").areas().getFirst();
        assertEquals(java.util.List.of(inicio, inicio.plusMonths(1)), area.evolucao().stream().map(BiResumo.Ponto::mes).toList());
        assertTrue(area.evolucao().stream().allMatch(p -> p.valor().compareTo(BigDecimal.ONE) == 0));
    }

    @Test void conexaoDedicadaNaoConsultaBancoOperacional() {
        var operational = new DriverManagerDataSource("jdbc:h2:mem:operational" + UUID.randomUUID(), "sa", "");
        var client = org.mockito.Mockito.mock(br.com.brasil_saas.bi.config.BiDataSourceConfig.BiReadClient.class);
        org.mockito.Mockito.when(client.jdbc()).thenReturn(new NamedParameterJdbcTemplate(jdbc.getDataSource()));
        @SuppressWarnings("unchecked")
        var provider = (org.springframework.beans.factory.ObjectProvider<br.com.brasil_saas.bi.config.BiDataSourceConfig.BiReadClient>)
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(client);
        var dedicated = new BiResumoService(new NamedParameterJdbcTemplate(operational), provider);
        assertEquals(4, dedicated.consultar(1L, inicio, fim).areas().size());
        assertFalse(new JdbcTemplate(operational).queryForObject("SELECT COUNT(*) > 0 FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='BRASIL_SAAS_DL'", Boolean.class));
    }

    private void valor(BiResumo resumo, String area, String indicador, String esperado) {
        BigDecimal atual = resumo.areas().stream().filter(a -> a.codigo().equals(area)).findFirst().orElseThrow()
                .indicadores().stream().filter(i -> i.codigo().equals(indicador)).findFirst().orElseThrow().valor();
        assertEquals(0, atual.compareTo(new BigDecimal(esperado)), area + "/" + indicador);
    }
}
