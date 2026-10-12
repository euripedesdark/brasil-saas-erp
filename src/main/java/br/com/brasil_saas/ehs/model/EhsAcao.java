package br.com.brasil_saas.ehs.model;

import br.com.brasil_saas.shared.web.MapValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "bc_ehs_acao", schema = "brasil_saas")
@Getter
@Setter
public class EhsAcao {

    private static final Set<String> BLOQUEADOS = Set.of("id", "empresa_id");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "ocorrencia_id")
    private Long ocorrenciaId;

    @Column(name = "inspecao_id")
    private Long inspecaoId;

    @Column(name = "descricao", length = 1000, nullable = false)
    private String descricao;

    @Column(name = "responsavel", length = 160)
    private String responsavel;

    @Column(name = "prazo")
    private LocalDate prazo;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "ABERTA";

    @Column(name = "concluida_em")
    private LocalDateTime concluidaEm;

    @Column(name = "evidencia", length = 1000)
    private String evidencia;

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "ocorrencia_id" -> setOcorrenciaId(MapValues.lng(v));
                case "inspecao_id" -> setInspecaoId(MapValues.lng(v));
                case "descricao" -> setDescricao(MapValues.str(v));
                case "responsavel" -> setResponsavel(MapValues.str(v));
                case "prazo" -> setPrazo(MapValues.dt(v));
                case "status" -> setStatus(MapValues.str(v));
                case "concluida_em" -> setConcluidaEm(MapValues.dtTime(v));
                case "evidencia" -> setEvidencia(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("empresa_id", empresaId);
        m.put("ocorrencia_id", ocorrenciaId); m.put("inspecao_id", inspecaoId);
        m.put("descricao", descricao); m.put("responsavel", responsavel);
        m.put("prazo", prazo); m.put("status", status);
        m.put("concluida_em", concluidaEm); m.put("evidencia", evidencia);
        return m;
    }
}
