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
@Table(name = "bc_ent_cenario_tributario", schema = "brasil_saas")
@Getter
@Setter
public class CenarioTributario extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "nome", length = 120, nullable = false)
    private String nome;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "regime", length = 30)
    private String regime;

    @Column(name = "uf_origem", columnDefinition = "bpchar(2)")
    private String ufOrigem;

    @Column(name = "uf_destino", columnDefinition = "bpchar(2)")
    private String ufDestino;

    @Column(name = "cst", length = 10)
    private String cst;

    @Column(name = "cfop", length = 10)
    private String cfop;

    @Column(name = "aliquota_icms", precision = 8, scale = 4)
    private BigDecimal aliquotaIcms;

    @Column(name = "aliquota_ibs", precision = 8, scale = 4)
    private BigDecimal aliquotaIbs;

    @Column(name = "aliquota_cbs", precision = 8, scale = 4)
    private BigDecimal aliquotaCbs;

    @Column(name = "reducao", precision = 8, scale = 4, nullable = false)
    private BigDecimal reducao = BigDecimal.ZERO;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "RASCUNHO";

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
                case "nome" -> setNome(MapValues.str(v));
                case "vigencia_inicio" -> setVigenciaInicio(MapValues.dt(v));
                case "vigencia_fim" -> setVigenciaFim(MapValues.dt(v));
                case "regime" -> setRegime(MapValues.str(v));
                case "uf_origem" -> setUfOrigem(MapValues.str(v));
                case "uf_destino" -> setUfDestino(MapValues.str(v));
                case "cst" -> setCst(MapValues.str(v));
                case "cfop" -> setCfop(MapValues.str(v));
                case "aliquota_icms" -> setAliquotaIcms(MapValues.bd(v));
                case "aliquota_ibs" -> setAliquotaIbs(MapValues.bd(v));
                case "aliquota_cbs" -> setAliquotaCbs(MapValues.bd(v));
                case "reducao" -> { BigDecimal x = MapValues.bd(v); if (x != null) setReducao(x); }
                case "status" -> setStatus(MapValues.str(v));
                case "observacao" -> setObservacao(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("nome", nome);
        m.put("vigencia_inicio", vigenciaInicio); m.put("vigencia_fim", vigenciaFim);
        m.put("regime", regime); m.put("uf_origem", ufOrigem); m.put("uf_destino", ufDestino);
        m.put("cst", cst); m.put("cfop", cfop);
        m.put("aliquota_icms", aliquotaIcms); m.put("aliquota_ibs", aliquotaIbs);
        m.put("aliquota_cbs", aliquotaCbs); m.put("reducao", reducao);
        m.put("status", status); m.put("observacao", observacao);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
