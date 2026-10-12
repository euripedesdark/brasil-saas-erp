package br.com.brasil_saas.servicos.model;

import br.com.brasil_saas.shared.model.AuditableEntity;
import br.com.brasil_saas.shared.web.MapValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "bc_srv_contrato", schema = "brasil_saas")
@Getter
@Setter
public class SrvContrato extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "numero", length = 80, nullable = false)
    private String numero;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "descricao", length = 500, nullable = false)
    private String descricao;

    @Column(name = "inicio", nullable = false)
    private LocalDate inicio;

    @Column(name = "fim")
    private LocalDate fim;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo = "SUPORTE";

    @Column(name = "sla_horas", precision = 10, scale = 2)
    private BigDecimal slaHoras;

    @Column(name = "valor_mensal", precision = 18, scale = 2, nullable = false)
    private BigDecimal valorMensal = BigDecimal.ZERO;

    @Column(name = "franquia_horas", precision = 10, scale = 2)
    private BigDecimal franquiaHoras;

    @Column(name = "horas_consumidas", precision = 10, scale = 2, nullable = false)
    private BigDecimal horasConsumidas = BigDecimal.ZERO;

    @Column(name = "status", length = 25, nullable = false)
    private String status = "ATIVO";

    @Column(name = "renovacao_automatica", nullable = false)
    private Boolean renovacaoAutomatica = false;

    @Column(name = "observacao", columnDefinition = "text")
    private String observacao;

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "numero" -> setNumero(MapValues.str(v));
                case "cliente_id" -> setClienteId(MapValues.lng(v));
                case "descricao" -> setDescricao(MapValues.str(v));
                case "inicio" -> setInicio(MapValues.dt(v));
                case "fim" -> setFim(MapValues.dt(v));
                case "tipo" -> setTipo(MapValues.str(v));
                case "sla_horas" -> setSlaHoras(MapValues.bd(v));
                case "valor_mensal" -> assign(v, this::setValorMensal);
                case "franquia_horas" -> setFranquiaHoras(MapValues.bd(v));
                case "horas_consumidas" -> assign(v, this::setHorasConsumidas);
                case "status" -> setStatus(MapValues.str(v));
                case "renovacao_automatica" -> { Boolean x = MapValues.bool(v); if (x != null) setRenovacaoAutomatica(x); }
                case "observacao" -> setObservacao(MapValues.str(v));
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
        m.put("numero", numero); m.put("cliente_id", clienteId); m.put("descricao", descricao);
        m.put("inicio", inicio); m.put("fim", fim); m.put("tipo", tipo);
        m.put("sla_horas", slaHoras); m.put("valor_mensal", valorMensal);
        m.put("franquia_horas", franquiaHoras); m.put("horas_consumidas", horasConsumidas);
        m.put("status", status); m.put("renovacao_automatica", renovacaoAutomatica);
        m.put("observacao", observacao);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
