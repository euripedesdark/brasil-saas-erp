package br.com.brasil_saas.enterprise.model;

import br.com.brasil_saas.shared.model.AuditableEntity;
import br.com.brasil_saas.shared.web.MapValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "bc_gov_controle", schema = "brasil_saas")
@Getter
@Setter
public class GovControle extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "codigo", length = 80, nullable = false)
    private String codigo;

    @Column(name = "processo", length = 100, nullable = false)
    private String processo;

    @Column(name = "titulo", length = 200, nullable = false)
    private String titulo;

    @Column(name = "objetivo", columnDefinition = "text")
    private String objetivo;

    @Column(name = "frequencia", length = 30, nullable = false)
    private String frequencia = "MENSAL";

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo = "PREVENTIVO";

    @Column(name = "responsavel_id")
    private Long responsavelId;

    @Column(name = "status", length = 25, nullable = false)
    private String status = "ATIVO";

    @Column(name = "ultima_execucao")
    private LocalDate ultimaExecucao;

    @Column(name = "proxima_execucao")
    private LocalDate proximaExecucao;

    @Column(name = "evidencias", columnDefinition = "text")
    private String evidencias;

    @Column(name = "resultado", length = 30)
    private String resultado;

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "codigo" -> setCodigo(MapValues.str(v));
                case "processo" -> setProcesso(MapValues.str(v));
                case "titulo" -> setTitulo(MapValues.str(v));
                case "objetivo" -> setObjetivo(MapValues.str(v));
                case "frequencia" -> setFrequencia(MapValues.str(v));
                case "tipo" -> setTipo(MapValues.str(v));
                case "responsavel_id" -> setResponsavelId(MapValues.lng(v));
                case "status" -> setStatus(MapValues.str(v));
                case "ultima_execucao" -> setUltimaExecucao(MapValues.dt(v));
                case "proxima_execucao" -> setProximaExecucao(MapValues.dt(v));
                case "evidencias" -> setEvidencias(MapValues.str(v));
                case "resultado" -> setResultado(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("codigo", codigo); m.put("processo", processo); m.put("titulo", titulo);
        m.put("objetivo", objetivo);
        m.put("frequencia", frequencia); m.put("tipo", tipo);
        m.put("responsavel_id", responsavelId); m.put("status", status);
        m.put("ultima_execucao", ultimaExecucao); m.put("proxima_execucao", proximaExecucao);
        m.put("evidencias", evidencias); m.put("resultado", resultado);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
