package br.com.brasil_saas.plm.model;

import jakarta.persistence.*;
import br.com.brasil_saas.shared.web.MapValues;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

@Entity @Table(name = "bc_plm_mudanca", schema = "brasil_saas")
@Getter @Setter @NoArgsConstructor
public class PlmMudanca {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(nullable = false, length = 80) private String numero;
    @Column(length = 30) private String tipo = "ENGENHARIA";
    @Column(nullable = false, length = 200) private String titulo;
    @Column(columnDefinition = "TEXT") private String descricao;
    @Column(length = 20) private String prioridade = "NORMAL";
    @Column(nullable = false, length = 25) private String status = "ABERTA";
    @Column(name = "solicitante_id") private Long solicitanteId;
    @Column(name = "aprovador_id") private Long aprovadorId;
    @Column(name = "aprovado_em") private LocalDateTime aprovadoEm;
    @Column(name = "implementado_em") private LocalDateTime implementadoEm;
    @Column(columnDefinition = "TEXT") private String observacao;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "numero" -> setNumero(MapValues.str(v));
                case "tipo" -> setTipo(MapValues.str(v));
                case "titulo" -> setTitulo(MapValues.str(v));
                case "descricao" -> setDescricao(MapValues.str(v));
                case "prioridade" -> setPrioridade(MapValues.str(v));
                case "status" -> setStatus(MapValues.str(v));
                case "solicitante_id" -> setSolicitanteId(MapValues.lng(v));
                case "aprovador_id" -> setAprovadorId(MapValues.lng(v));
                case "aprovado_em" -> setAprovadoEm(MapValues.dtTime(v));
                case "implementado_em" -> setImplementadoEm(MapValues.dtTime(v));
                case "observacao" -> setObservacao(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("empresa_id", getEmpresaId());
        m.put("numero", getNumero()); m.put("tipo", getTipo()); m.put("titulo", getTitulo());
        m.put("descricao", getDescricao()); m.put("prioridade", getPrioridade());
        m.put("status", getStatus());
        m.put("solicitante_id", getSolicitanteId());
        m.put("aprovador_id", getAprovadorId()); m.put("aprovado_em", getAprovadoEm());
        m.put("implementado_em", getImplementadoEm()); m.put("observacao", getObservacao());
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        return m;
    }
}
