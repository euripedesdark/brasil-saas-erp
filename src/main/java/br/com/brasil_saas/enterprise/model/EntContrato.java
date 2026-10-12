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
@Table(name = "bc_ent_contrato", schema = "brasil_saas")
@Getter
@Setter
public class EntContrato extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "tipo", length = 40, nullable = false)
    private String tipo;

    @Column(name = "numero", length = 80, nullable = false)
    private String numero;

    @Column(name = "parceiro_tipo", length = 30)
    private String parceiroTipo;

    @Column(name = "parceiro_id")
    private Long parceiroId;

    @Column(name = "descricao", length = 500, nullable = false)
    private String descricao;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "RASCUNHO";

    @Column(name = "inicio")
    private LocalDate inicio;

    @Column(name = "fim")
    private LocalDate fim;

    @Column(name = "valor_total", precision = 18, scale = 2, nullable = false)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "renovacao_automatica", nullable = false)
    private Boolean renovacaoAutomatica = false;

    @Column(name = "indice_reajuste", length = 40)
    private String indiceReajuste;

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
                case "tipo" -> setTipo(MapValues.str(v));
                case "numero" -> setNumero(MapValues.str(v));
                case "parceiro_tipo" -> setParceiroTipo(MapValues.str(v));
                case "parceiro_id" -> setParceiroId(MapValues.lng(v));
                case "descricao" -> setDescricao(MapValues.str(v));
                case "status" -> setStatus(MapValues.str(v));
                case "inicio" -> setInicio(MapValues.dt(v));
                case "fim" -> setFim(MapValues.dt(v));
                case "valor_total" -> { BigDecimal x = MapValues.bd(v); if (x != null) setValorTotal(x); }
                case "renovacao_automatica" -> { Boolean x = MapValues.bool(v); if (x != null) setRenovacaoAutomatica(x); }
                case "indice_reajuste" -> setIndiceReajuste(MapValues.str(v));
                case "observacao" -> setObservacao(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("tipo", tipo); m.put("numero", numero);
        m.put("parceiro_tipo", parceiroTipo); m.put("parceiro_id", parceiroId);
        m.put("descricao", descricao); m.put("status", status);
        m.put("inicio", inicio); m.put("fim", fim);
        m.put("valor_total", valorTotal); m.put("renovacao_automatica", renovacaoAutomatica);
        m.put("indice_reajuste", indiceReajuste); m.put("observacao", observacao);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
