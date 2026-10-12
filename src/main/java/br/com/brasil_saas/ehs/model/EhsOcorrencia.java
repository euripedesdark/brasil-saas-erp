package br.com.brasil_saas.ehs.model;

import br.com.brasil_saas.shared.model.AuditableEntity;
import br.com.brasil_saas.shared.web.MapValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "bc_ehs_ocorrencia", schema = "brasil_saas")
@Getter
@Setter
public class EhsOcorrencia extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "numero", length = 80, nullable = false)
    private String numero;

    @Column(name = "tipo", length = 40, nullable = false)
    private String tipo;

    @Column(name = "severidade", length = 20, nullable = false)
    private String severidade = "MEDIA";

    @Column(name = "data_ocorrencia", nullable = false)
    private LocalDateTime dataOcorrencia;

    @Column(name = "local_ocorrencia", length = 200)
    private String localOcorrencia;

    @Column(name = "funcionario_id")
    private Long funcionarioId;

    @Column(name = "ativo_id")
    private Long ativoId;

    @Column(name = "descricao", columnDefinition = "text", nullable = false)
    private String descricao;

    @Column(name = "causa_raiz", columnDefinition = "text")
    private String causaRaiz;

    @Column(name = "acao_corretiva", columnDefinition = "text")
    private String acaoCorretiva;

    @Column(name = "status", length = 25, nullable = false)
    private String status = "ABERTA";

    @Column(name = "prazo")
    private LocalDate prazo;

    @Column(name = "encerrado_em")
    private LocalDateTime encerradoEm;

    @Column(name = "encerrado_por")
    private Long encerradoPor;

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
                case "severidade" -> setSeveridade(MapValues.str(v));
                case "data_ocorrencia" -> setDataOcorrencia(MapValues.dtTime(v));
                case "local_ocorrencia" -> setLocalOcorrencia(MapValues.str(v));
                case "funcionario_id" -> setFuncionarioId(MapValues.lng(v));
                case "ativo_id" -> setAtivoId(MapValues.lng(v));
                case "descricao" -> setDescricao(MapValues.str(v));
                case "causa_raiz" -> setCausaRaiz(MapValues.str(v));
                case "acao_corretiva" -> setAcaoCorretiva(MapValues.str(v));
                case "status" -> setStatus(MapValues.str(v));
                case "prazo" -> setPrazo(MapValues.dt(v));
                case "encerrado_em" -> setEncerradoEm(MapValues.dtTime(v));
                case "encerrado_por" -> setEncerradoPor(MapValues.lng(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("numero", numero); m.put("tipo", tipo); m.put("severidade", severidade);
        m.put("data_ocorrencia", dataOcorrencia); m.put("local_ocorrencia", localOcorrencia);
        m.put("funcionario_id", funcionarioId); m.put("ativo_id", ativoId);
        m.put("descricao", descricao); m.put("causa_raiz", causaRaiz); m.put("acao_corretiva", acaoCorretiva);
        m.put("status", status); m.put("prazo", prazo);
        m.put("encerrado_em", encerradoEm); m.put("encerrado_por", encerradoPor);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
