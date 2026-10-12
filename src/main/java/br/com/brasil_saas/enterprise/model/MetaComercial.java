package br.com.brasil_saas.enterprise.model;

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
@Table(name = "bc_ent_meta_comercial", schema = "brasil_saas")
@Getter
@Setter
public class MetaComercial extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "vendedor_id")
    private Long vendedorId;

    @Column(name = "periodo_inicio", nullable = false)
    private LocalDate periodoInicio;

    @Column(name = "periodo_fim", nullable = false)
    private LocalDate periodoFim;

    @Column(name = "meta_valor", precision = 18, scale = 2, nullable = false)
    private BigDecimal metaValor = BigDecimal.ZERO;

    @Column(name = "meta_quantidade", precision = 18, scale = 3, nullable = false)
    private BigDecimal metaQuantidade = BigDecimal.ZERO;

    @Column(name = "realizado_valor", precision = 18, scale = 2, nullable = false)
    private BigDecimal realizadoValor = BigDecimal.ZERO;

    @Column(name = "realizado_quantidade", precision = 18, scale = 3, nullable = false)
    private BigDecimal realizadoQuantidade = BigDecimal.ZERO;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ABERTA";

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "vendedor_id" -> setVendedorId(MapValues.lng(v));
                case "periodo_inicio" -> setPeriodoInicio(MapValues.dt(v));
                case "periodo_fim" -> setPeriodoFim(MapValues.dt(v));
                case "meta_valor" -> { BigDecimal x = MapValues.bd(v); if (x != null) setMetaValor(x); }
                case "meta_quantidade" -> { BigDecimal x = MapValues.bd(v); if (x != null) setMetaQuantidade(x); }
                case "realizado_valor" -> { BigDecimal x = MapValues.bd(v); if (x != null) setRealizadoValor(x); }
                case "realizado_quantidade" -> { BigDecimal x = MapValues.bd(v); if (x != null) setRealizadoQuantidade(x); }
                case "status" -> setStatus(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("vendedor_id", vendedorId);
        m.put("periodo_inicio", periodoInicio); m.put("periodo_fim", periodoFim);
        m.put("meta_valor", metaValor); m.put("meta_quantidade", metaQuantidade);
        m.put("realizado_valor", realizadoValor); m.put("realizado_quantidade", realizadoQuantidade);
        m.put("status", status);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
