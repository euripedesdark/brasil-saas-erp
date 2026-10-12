package br.com.brasil_saas.supplychain.model;

import br.com.brasil_saas.shared.model.AuditableEntity;
import br.com.brasil_saas.shared.web.MapValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "bc_scm_politica_reposicao", schema = "brasil_saas")
@Getter
@Setter
public class ScmPoliticaReposicao extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(name = "deposito_id")
    private Long depositoId;

    @Column(name = "metodo", length = 30, nullable = false)
    private String metodo = "PONTO_PEDIDO";

    @Column(name = "estoque_minimo", precision = 18, scale = 3, nullable = false)
    private BigDecimal estoqueMinimo = BigDecimal.ZERO;

    @Column(name = "estoque_maximo", precision = 18, scale = 3, nullable = false)
    private BigDecimal estoqueMaximo = BigDecimal.ZERO;

    @Column(name = "estoque_seguranca", precision = 18, scale = 3, nullable = false)
    private BigDecimal estoqueSeguranca = BigDecimal.ZERO;

    @Column(name = "ponto_pedido", precision = 18, scale = 3, nullable = false)
    private BigDecimal pontoPedido = BigDecimal.ZERO;

    @Column(name = "lote_economico", precision = 18, scale = 3, nullable = false)
    private BigDecimal loteEconomico = BigDecimal.ZERO;

    @Column(name = "lead_time_dias", nullable = false)
    private Integer leadTimeDias = 0;

    @Column(name = "fornecedor_preferencial_id")
    private Long fornecedorPreferencialId;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "produto_id" -> setProdutoId(MapValues.lng(v));
                case "deposito_id" -> setDepositoId(MapValues.lng(v));
                case "metodo" -> setMetodo(MapValues.str(v));
                case "estoque_minimo" -> assign(v, this::setEstoqueMinimo);
                case "estoque_maximo" -> assign(v, this::setEstoqueMaximo);
                case "estoque_seguranca" -> assign(v, this::setEstoqueSeguranca);
                case "ponto_pedido" -> assign(v, this::setPontoPedido);
                case "lote_economico" -> assign(v, this::setLoteEconomico);
                case "lead_time_dias" -> { Integer x = MapValues.integer(v); if (x != null) setLeadTimeDias(x); }
                case "fornecedor_preferencial_id" -> setFornecedorPreferencialId(MapValues.lng(v));
                case "ativo" -> { Boolean x = MapValues.bool(v); if (x != null) setAtivo(x); }
                default -> { }
            }
        }
    }

    private static void assign(Object v, java.util.function.Consumer<BigDecimal> set) {
        BigDecimal x = MapValues.bd(v);
        if (x != null) {
            set.accept(x);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("produto_id", produtoId); m.put("deposito_id", depositoId); m.put("metodo", metodo);
        m.put("estoque_minimo", estoqueMinimo); m.put("estoque_maximo", estoqueMaximo);
        m.put("estoque_seguranca", estoqueSeguranca); m.put("ponto_pedido", pontoPedido);
        m.put("lote_economico", loteEconomico); m.put("lead_time_dias", leadTimeDias);
        m.put("fornecedor_preferencial_id", fornecedorPreferencialId); m.put("ativo", ativo);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
