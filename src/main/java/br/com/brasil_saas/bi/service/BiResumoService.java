package br.com.brasil_saas.bi.service;

import br.com.brasil_saas.bi.config.BiDataSourceConfig.BiReadClient;
import br.com.brasil_saas.bi.dto.BiResumo;
import br.com.brasil_saas.bi.dto.BiResumo.Area;
import br.com.brasil_saas.bi.dto.BiResumo.Indicador;
import br.com.brasil_saas.shared.exception.BusinessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Consultas fixas: nenhum SQL, tabela ou empresa pode ser escolhido pelo navegador. */
@Service
public class BiResumoService {
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate snapshot;

    /** Compatibilidade com testes e modo legado sem BI dedicado. */
    public BiResumoService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = withTimeout(jdbc);
        this.snapshot = snapshot(this.jdbc);
    }

    @Autowired
    public BiResumoService(NamedParameterJdbcTemplate jdbc, ObjectProvider<BiReadClient> biClient) {
        BiReadClient dedicated = biClient.getIfAvailable();
        this.jdbc = dedicated == null ? withTimeout(jdbc) : dedicated.jdbc();
        this.snapshot = snapshot(this.jdbc);
    }

    // Gerenciador privado: nao registra bean nem substitui o transaction manager JPA.
    // Todas as areas e series enxergam a mesma carga ETL durante a requisicao.
    private static TransactionTemplate snapshot(NamedParameterJdbcTemplate jdbc) {
        var template = new TransactionTemplate(new DataSourceTransactionManager(
                java.util.Objects.requireNonNull(jdbc.getJdbcTemplate().getDataSource())));
        template.setReadOnly(true);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(30);
        return template;
    }

    private static NamedParameterJdbcTemplate withTimeout(NamedParameterJdbcTemplate jdbc) {
        var consultas = new org.springframework.jdbc.core.JdbcTemplate(
                java.util.Objects.requireNonNull(jdbc.getJdbcTemplate().getDataSource()));
        consultas.setQueryTimeout(5);
        return new NamedParameterJdbcTemplate(consultas);
    }

    public BiResumo consultar(Long empresaId, LocalDate inicio, LocalDate fim) {
        return snapshot.execute(status -> consultarSelecionadas(empresaId, inicio, fim, null));
    }

    public BiResumo consultarArea(Long empresaId, LocalDate inicio, LocalDate fim, String area) {
        if (area == null || !java.util.Set.of("financeiro", "producao", "vendas", "servicos").contains(area)) {
            throw new BusinessException("Area de BI invalida");
        }
        return snapshot.execute(status -> consultarSelecionadas(empresaId, inicio, fim, area));
    }

    private BiResumo consultarSelecionadas(Long empresaId, LocalDate inicio, LocalDate fim, String area) {
        if (empresaId == null || empresaId <= 0) throw new BusinessException("Usuario sem empresa vinculada para consultar o BI");
        if (inicio == null || fim == null || fim.isBefore(inicio) || ChronoUnit.DAYS.between(inicio, fim) > 365) {
            throw new BusinessException("Informe um periodo de ate 366 dias, com inicio anterior ao fim");
        }
        LocalDate hoje = LocalDate.now();
        Map<String, Object> parametros = Map.of("empresa", empresaId, "inicio", inicio, "fim", fim, "hoje", hoje);
        var areas = new java.util.ArrayList<Area>();
        if (area == null || area.equals("financeiro")) areas.add(executar("financeiro", """
            SELECT COALESCE(SUM(CASE WHEN tipo = 'R' THEN valor_saldo ELSE 0 END),0) AS a_receber,
                   COALESCE(SUM(CASE WHEN tipo = 'P' THEN valor_saldo ELSE 0 END),0) AS a_pagar,
                   COALESCE(SUM(CASE WHEN tipo = 'R' AND data_vencimento < :hoje THEN valor_saldo ELSE 0 END),0) AS receber_vencido,
                   COALESCE(SUM(CASE WHEN tipo = 'R' THEN valor_saldo WHEN tipo = 'P' THEN -valor_saldo ELSE 0 END),0) AS saldo_previsto
            FROM brasil_saas_dl.v_bi_financeiro
            WHERE empresa_id = :empresa AND data_vencimento BETWEEN :inicio AND :fim
              AND status <> 'CANCELADO' AND valor_saldo > 0
            """, parametros, List.of("a_receber", "a_pagar", "receber_vencido", "saldo_previsto"), List.of("BRL", "BRL", "BRL", "BRL")));
        if (area == null || area.equals("producao")) areas.add(executar("producao", """
            SELECT COUNT(*) AS ordens,
                   COALESCE(SUM(CASE WHEN status = 'FINALIZADO' THEN 1 ELSE 0 END),0) AS finalizadas,
                   COALESCE(SUM(CASE WHEN status = 'EM_PROCESSO' THEN 1 ELSE 0 END),0) AS em_processo
            FROM brasil_saas_dl.v_bi_producao
            WHERE empresa_id = :empresa AND data_referencia BETWEEN :inicio AND :fim AND status <> 'CANCELADO'
            """, parametros, List.of("ordens", "finalizadas", "em_processo"), List.of("COUNT", "COUNT", "COUNT")));
        if (area == null || area.equals("vendas")) areas.add(executar("vendas", """
            SELECT COUNT(*) AS pedidos, COALESCE(SUM(valor_total),0) AS valor_pedidos,
                   COALESCE(AVG(valor_total),0) AS ticket_medio,
                   COALESCE(SUM(CASE WHEN status = 'FATURADO' THEN valor_total ELSE 0 END),0) AS valor_faturado
            FROM brasil_saas_dl.v_bi_vendas
            WHERE empresa_id = :empresa AND data_referencia BETWEEN :inicio AND :fim
              AND tipo = 'PEDIDO' AND status <> 'CANCELADO'
            """, parametros, List.of("pedidos", "valor_pedidos", "ticket_medio", "valor_faturado"), List.of("COUNT", "BRL", "BRL", "BRL")));
        if (area == null || area.equals("servicos")) areas.add(executar("servicos", """
            SELECT COALESCE(SUM(CASE WHEN status NOT IN ('FECHADA','CANCELADA') AND data_abertura BETWEEN :inicio AND :fim THEN 1 ELSE 0 END),0) AS abertas,
                   COALESCE(SUM(CASE WHEN status = 'FECHADA' AND data_fechamento BETWEEN :inicio AND :fim THEN 1 ELSE 0 END),0) AS concluidas,
                   COALESCE(SUM(CASE WHEN status = 'FECHADA' AND data_fechamento BETWEEN :inicio AND :fim THEN valor_total ELSE 0 END),0) AS valor_concluido
            FROM brasil_saas_dl.v_bi_servicos WHERE empresa_id = :empresa
              AND (data_abertura BETWEEN :inicio AND :fim OR data_fechamento BETWEEN :inicio AND :fim)
            """, parametros, List.of("abertas", "concluidas", "valor_concluido"), List.of("COUNT", "COUNT", "BRL")));
        return new BiResumo(inicio, fim, hoje, areas.stream().map(a -> new Area(a.codigo(), a.indicadores(),
                evolucao(a.codigo(), parametros))).toList());
    }

    private List<BiResumo.Ponto> evolucao(String area, Map<String, Object> parametros) {
        // Identificadores e expressoes exclusivamente fixos; parametros do tenant vinculados.
        String data;
        String valor;
        String filtro;
        String unidade;
        switch (area) {
            case "financeiro" -> {
                data = "data_vencimento";
                valor = "SUM(CASE WHEN tipo='R' THEN valor_saldo WHEN tipo='P' THEN -valor_saldo ELSE 0 END)";
                filtro = "status <> 'CANCELADO' AND valor_saldo > 0";
                unidade = "BRL";
            }
            case "producao" -> { data = "data_referencia"; valor = "COUNT(*)"; filtro = "status <> 'CANCELADO'"; unidade = "COUNT"; }
            case "vendas" -> { data = "data_referencia"; valor = "SUM(valor_total)"; filtro = "tipo='PEDIDO' AND status <> 'CANCELADO'"; unidade = "BRL"; }
            case "servicos" -> { data = "data_fechamento"; valor = "COUNT(*)"; filtro = "status='FECHADA'"; unidade = "COUNT"; }
            default -> throw new BusinessException("Area de BI invalida");
        }
        String sql = "SELECT EXTRACT(YEAR FROM " + data + ") AS ano, EXTRACT(MONTH FROM " + data
                + ") AS mes, COALESCE(" + valor + ",0) AS valor FROM brasil_saas_dl.v_bi_" + area
                + " WHERE empresa_id=:empresa AND " + data + " BETWEEN :inicio AND :fim AND " + filtro
                + " GROUP BY EXTRACT(YEAR FROM " + data + "),EXTRACT(MONTH FROM " + data + ") ORDER BY ano,mes";
        return jdbc.query(sql, parametros, (rs, row) -> new BiResumo.Ponto(
                LocalDate.of(rs.getInt("ano"), rs.getInt("mes"), 1), rs.getBigDecimal("valor"), unidade));
    }

    private Area executar(String area, String sql, Map<String, Object> parametros, List<String> codigos, List<String> unidades) {
        return jdbc.queryForObject(sql, parametros, (rs, row) -> {
            var indicadores = new java.util.ArrayList<Indicador>();
            for (int i = 0; i < codigos.size(); i++) {
                BigDecimal valor = rs.getBigDecimal(codigos.get(i));
                indicadores.add(new Indicador(codigos.get(i), valor, unidades.get(i)));
            }
            return new Area(area, List.copyOf(indicadores));
        });
    }
}
