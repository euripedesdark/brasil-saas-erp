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

@Entity @Table(name = "bc_plm_aprovacao", schema = "brasil_saas")
@Getter @Setter @NoArgsConstructor
public class PlmAprovacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "mudanca_id", nullable = false) private Long mudancaId;
    @Column(nullable = false) private Integer etapa;
    @Column(name = "aprovador_id") private Long aprovadorId;
    @Column(nullable = false, length = 30) private String decisao = "PENDENTE";
    @Column(columnDefinition = "TEXT") private String observacao;
    @Column(name = "decidido_em") private LocalDateTime decididoEm;
    @Column(nullable = false) private Boolean obrigatoria = true;

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
                case "etapa" -> { Integer x = MapValues.integer(v); if (x != null) setEtapa(x); }
                case "aprovador_id" -> setAprovadorId(MapValues.lng(v));
                case "decisao" -> setDecisao(MapValues.str(v));
                case "observacao" -> setObservacao(MapValues.str(v));
                case "decidido_em" -> setDecididoEm(MapValues.dtTime(v));
                case "obrigatoria" -> { Boolean x = MapValues.bool(v); if (x != null) setObrigatoria(x); }
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("empresa_id", getEmpresaId()); m.put("mudanca_id", getMudancaId());
        m.put("etapa", getEtapa()); m.put("aprovador_id", getAprovadorId());
        m.put("decisao", getDecisao()); m.put("observacao", getObservacao());
        m.put("decidido_em", getDecididoEm()); m.put("obrigatoria", getObrigatoria());
        return m;
    }
}
