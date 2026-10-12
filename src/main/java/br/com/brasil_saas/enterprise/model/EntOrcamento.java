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
@Table(name = "bc_ent_orcamento", schema = "brasil_saas")
@Getter
@Setter
public class EntOrcamento extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "competencia", nullable = false)
    private LocalDate competencia;

    @Column(name = "centro_custo_id")
    private Long centroCustoId;

    @Column(name = "conta_contabil_id")
    private Long contaContabilId;

    @Column(name = "versao", nullable = false)
    private Integer versao = 1;

    @Column(name = "valor_orcado", precision = 18, scale = 2, nullable = false)
    private BigDecimal valorOrcado = BigDecimal.ZERO;

    @Column(name = "valor_revisado", precision = 18, scale = 2, nullable = false)
    private BigDecimal valorRevisado = BigDecimal.ZERO;

    @Column(name = "valor_realizado", precision = 18, scale = 2, nullable = false)
    private BigDecimal valorRealizado = BigDecimal.ZERO;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "RASCUNHO";

    @Column(name = "observacao", length = 500)
    private String observacao;

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "competencia" -> setCompetencia(MapValues.dt(v));
                case "centro_custo_id" -> setCentroCustoId(MapValues.lng(v));
                case "conta_contabil_id" -> setContaContabilId(MapValues.lng(v));
                case "versao" -> { Integer x = MapValues.integer(v); if (x != null) setVersao(x); }
                case "valor_orcado" -> { BigDecimal x = MapValues.bd(v); if (x != null) setValorOrcado(x); }
                case "valor_revisado" -> { BigDecimal x = MapValues.bd(v); if (x != null) setValorRevisado(x); }
                case "valor_realizado" -> { BigDecimal x = MapValues.bd(v); if (x != null) setValorRealizado(x); }
                case "status" -> setStatus(MapValues.str(v));
                case "observacao" -> setObservacao(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("competencia", competencia);
        m.put("centro_custo_id", centroCustoId); m.put("conta_contabil_id", contaContabilId);
        m.put("versao", versao);
        m.put("valor_orcado", valorOrcado); m.put("valor_revisado", valorRevisado);
        m.put("valor_realizado", valorRealizado);
        m.put("status", status); m.put("observacao", observacao);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
