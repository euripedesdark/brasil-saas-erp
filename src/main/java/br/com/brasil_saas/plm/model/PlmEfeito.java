package br.com.brasil_saas.plm.model;

import jakarta.persistence.*;
import br.com.brasil_saas.shared.web.MapValues;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "bc_plm_efeito_mudanca", schema = "brasil_saas")
@Getter @Setter @NoArgsConstructor
public class PlmEfeito {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "mudanca_id", nullable = false) private Long mudancaId;
    @Column(name = "entidade_tipo", nullable = false, length = 50) private String entidadeTipo;
    @Column(name = "entidade_id", nullable = false) private Long entidadeId;
    @Column(nullable = false, length = 30) private String acao;
    @Column(name = "revisao_anterior", length = 30) private String revisaoAnterior;
    @Column(name = "revisao_nova", length = 30) private String revisaoNova;
    @Column(name = "efetiva_em") private LocalDate efetivaEm;
    @Column(nullable = false, length = 30) private String status = "PENDENTE";
    @Column(columnDefinition = "TEXT") private String observacao;
    @Column(name = "ordem_execucao", nullable = false) private Integer ordemExecucao = 1;
    @Column(nullable = false) private Boolean obrigatorio = true;
    @Column(name = "aplicado_em") private LocalDateTime aplicadoEm;
    @Column(name = "aplicado_por") private Long aplicadoPor;
    @Column(name = "erro_implementacao", length = 2000) private String erroImplementacao;

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
                case "mudanca_id" -> setMudancaId(MapValues.lng(v));
                case "entidade_tipo" -> setEntidadeTipo(MapValues.str(v));
                case "entidade_id" -> setEntidadeId(MapValues.lng(v));
                case "acao" -> setAcao(MapValues.str(v));
                case "revisao_anterior" -> setRevisaoAnterior(MapValues.str(v));
                case "revisao_nova" -> setRevisaoNova(MapValues.str(v));
                case "efetiva_em" -> setEfetivaEm(MapValues.dt(v));
                case "status" -> setStatus(MapValues.str(v));
                case "observacao" -> setObservacao(MapValues.str(v));
                case "ordem_execucao" -> { Integer x = MapValues.integer(v); if (x != null) setOrdemExecucao(x); }
                case "obrigatorio" -> { Boolean x = MapValues.bool(v); if (x != null) setObrigatorio(x); }
                case "aplicado_em" -> setAplicadoEm(MapValues.dtTime(v));
                case "aplicado_por" -> setAplicadoPor(MapValues.lng(v));
                case "erro_implementacao" -> setErroImplementacao(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("empresa_id", getEmpresaId()); m.put("mudanca_id", getMudancaId());
        m.put("entidade_tipo", getEntidadeTipo()); m.put("entidade_id", getEntidadeId());
        m.put("acao", getAcao());
        m.put("revisao_anterior", getRevisaoAnterior()); m.put("revisao_nova", getRevisaoNova());
        m.put("efetiva_em", getEfetivaEm()); m.put("status", getStatus());
        m.put("observacao", getObservacao());
        m.put("ordem_execucao", getOrdemExecucao()); m.put("obrigatorio", getObrigatorio());
        m.put("aplicado_em", getAplicadoEm()); m.put("aplicado_por", getAplicadoPor());
        m.put("erro_implementacao", getErroImplementacao());
        return m;
    }
}
