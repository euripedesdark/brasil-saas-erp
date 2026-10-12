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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "bc_ent_fornecedor_qualificacao", schema = "brasil_saas")
@Getter
@Setter
public class FornecedorQualificacao extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "fornecedor_id", nullable = false)
    private Long fornecedorId;

    @Column(name = "status", length = 25, nullable = false)
    private String status = "PENDENTE";

    @Column(name = "score", precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "validade")
    private LocalDate validade;

    @Column(name = "categoria", length = 80)
    private String categoria;

    @Column(name = "observacao", columnDefinition = "text")
    private String observacao;

    @Column(name = "aprovado_em")
    private LocalDateTime aprovadoEm;

    @Column(name = "aprovado_por")
    private Long aprovadoPor;

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "fornecedor_id" -> setFornecedorId(MapValues.lng(v));
                case "status" -> setStatus(MapValues.str(v));
                case "score" -> setScore(MapValues.bd(v));
                case "validade" -> setValidade(MapValues.dt(v));
                case "categoria" -> setCategoria(MapValues.str(v));
                case "observacao" -> setObservacao(MapValues.str(v));
                case "aprovado_em" -> setAprovadoEm(MapValues.dtTime(v));
                case "aprovado_por" -> setAprovadoPor(MapValues.lng(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("fornecedor_id", fornecedorId); m.put("status", status); m.put("score", score);
        m.put("validade", validade); m.put("categoria", categoria); m.put("observacao", observacao);
        m.put("aprovado_em", aprovadoEm); m.put("aprovado_por", aprovadoPor);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
