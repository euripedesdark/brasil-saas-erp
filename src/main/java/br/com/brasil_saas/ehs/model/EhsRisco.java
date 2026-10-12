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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "bc_ehs_risco", schema = "brasil_saas")
@Getter
@Setter
public class EhsRisco {

    private static final Set<String> BLOQUEADOS = Set.of("id", "empresa_id");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "codigo", length = 80, nullable = false)
    private String codigo;

    @Column(name = "perigo", length = 300, nullable = false)
    private String perigo;

    @Column(name = "atividade", length = 300)
    private String atividade;

    @Column(name = "localizacao", length = 300)
    private String localizacao;

    @Column(name = "probabilidade", precision = 8, scale = 2, nullable = false)
    private BigDecimal probabilidade = BigDecimal.ONE;

    @Column(name = "impacto", precision = 8, scale = 2, nullable = false)
    private BigDecimal impacto = BigDecimal.ONE;

    @Column(name = "nivel", precision = 12, scale = 2, nullable = false)
    private BigDecimal nivel = BigDecimal.ONE;

    @Column(name = "controle_existente", columnDefinition = "text")
    private String controleExistente;

    @Column(name = "responsavel", length = 160)
    private String responsavel;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "ATIVO";

    @Column(name = "revisado_em")
    private LocalDateTime revisadoEm;

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "codigo" -> setCodigo(MapValues.str(v));
                case "perigo" -> setPerigo(MapValues.str(v));
                case "atividade" -> setAtividade(MapValues.str(v));
                case "localizacao" -> setLocalizacao(MapValues.str(v));
                case "probabilidade" -> assign(v, this::setProbabilidade);
                case "impacto" -> assign(v, this::setImpacto);
                case "nivel" -> assign(v, this::setNivel);
                case "controle_existente" -> setControleExistente(MapValues.str(v));
                case "responsavel" -> setResponsavel(MapValues.str(v));
                case "status" -> setStatus(MapValues.str(v));
                case "revisado_em" -> setRevisadoEm(MapValues.dtTime(v));
                default -> { }
            }
        }
    }

    private static void assign(Object v, java.util.function.Consumer<BigDecimal> set) {
        BigDecimal x = MapValues.bd(v);
        if (x != null) {
            set.accept(x);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("empresa_id", empresaId);
        m.put("codigo", codigo); m.put("perigo", perigo);
        m.put("atividade", atividade); m.put("localizacao", localizacao);
        m.put("probabilidade", probabilidade); m.put("impacto", impacto); m.put("nivel", nivel);
        m.put("controle_existente", controleExistente);
        m.put("responsavel", responsavel); m.put("status", status);
        m.put("revisado_em", revisadoEm);
        return m;
    }
}
