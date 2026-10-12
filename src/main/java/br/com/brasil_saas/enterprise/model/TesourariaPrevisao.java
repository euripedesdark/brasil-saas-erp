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
@Table(name = "bc_ent_tesouraria_previsao", schema = "brasil_saas")
@Getter
@Setter
public class TesourariaPrevisao extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "data_prevista", nullable = false)
    private LocalDate dataPrevista;

    @Column(name = "tipo", length = 20, nullable = false)
    private String tipo;

    @Column(name = "origem", length = 40)
    private String origem;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(name = "descricao", length = 500, nullable = false)
    private String descricao;

    @Column(name = "valor", precision = 18, scale = 2, nullable = false)
    private BigDecimal valor;

    @Column(name = "probabilidade", precision = 5, scale = 2, nullable = false)
    private BigDecimal probabilidade = new BigDecimal("100");

    @Column(name = "status", length = 20, nullable = false)
    private String status = "PROJETADA";

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "data_prevista" -> setDataPrevista(MapValues.dt(v));
                case "tipo" -> setTipo(MapValues.str(v));
                case "origem" -> setOrigem(MapValues.str(v));
                case "referencia_id" -> setReferenciaId(MapValues.lng(v));
                case "descricao" -> setDescricao(MapValues.str(v));
                case "valor" -> setValor(MapValues.bd(v));
                case "probabilidade" -> { BigDecimal x = MapValues.bd(v); if (x != null) setProbabilidade(x); }
                case "status" -> setStatus(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("data_prevista", dataPrevista); m.put("tipo", tipo); m.put("origem", origem);
        m.put("referencia_id", referenciaId); m.put("descricao", descricao);
        m.put("valor", valor); m.put("probabilidade", probabilidade); m.put("status", status);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
