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
@Table(name = "bc_gov_risco", schema = "brasil_saas")
@Getter
@Setter
public class GovRisco extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "codigo", length = 80, nullable = false)
    private String codigo;

    @Column(name = "categoria", length = 60, nullable = false)
    private String categoria;

    @Column(name = "titulo", length = 200, nullable = false)
    private String titulo;

    @Column(name = "descricao", columnDefinition = "text")
    private String descricao;

    @Column(name = "probabilidade", precision = 6, scale = 2, nullable = false)
    private BigDecimal probabilidade = BigDecimal.ZERO;

    @Column(name = "impacto", precision = 6, scale = 2, nullable = false)
    private BigDecimal impacto = BigDecimal.ZERO;

    @Column(name = "score", precision = 10, scale = 2, nullable = false)
    private BigDecimal score = BigDecimal.ZERO;

    @Column(name = "tratamento", length = 40)
    private String tratamento = "MITIGAR";

    @Column(name = "responsavel_id")
    private Long responsavelId;

    @Column(name = "status", length = 25, nullable = false)
    private String status = "ABERTO";

    @Column(name = "prazo")
    private LocalDate prazo;

    @Column(name = "risco_residual", precision = 10, scale = 2)
    private BigDecimal riscoResidual;

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "codigo" -> setCodigo(MapValues.str(v));
                case "categoria" -> setCategoria(MapValues.str(v));
                case "titulo" -> setTitulo(MapValues.str(v));
                case "descricao" -> setDescricao(MapValues.str(v));
                case "probabilidade" -> { BigDecimal x = MapValues.bd(v); if (x != null) setProbabilidade(x); }
                case "impacto" -> { BigDecimal x = MapValues.bd(v); if (x != null) setImpacto(x); }
                case "score" -> { BigDecimal x = MapValues.bd(v); if (x != null) setScore(x); }
                case "tratamento" -> setTratamento(MapValues.str(v));
                case "responsavel_id" -> setResponsavelId(MapValues.lng(v));
                case "status" -> setStatus(MapValues.str(v));
                case "prazo" -> setPrazo(MapValues.dt(v));
                case "risco_residual" -> setRiscoResidual(MapValues.bd(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("codigo", codigo); m.put("categoria", categoria); m.put("titulo", titulo);
        m.put("descricao", descricao);
        m.put("probabilidade", probabilidade); m.put("impacto", impacto); m.put("score", score);
        m.put("tratamento", tratamento); m.put("responsavel_id", responsavelId);
        m.put("status", status); m.put("prazo", prazo); m.put("risco_residual", riscoResidual);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
