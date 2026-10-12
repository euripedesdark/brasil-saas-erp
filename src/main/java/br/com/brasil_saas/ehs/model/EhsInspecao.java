package br.com.brasil_saas.ehs.model;

import br.com.brasil_saas.shared.web.MapValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "bc_ehs_inspecao", schema = "brasil_saas")
@Getter
@Setter
public class EhsInspecao {

    private static final Set<String> BLOQUEADOS = Set.of("id", "empresa_id");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "numero", length = 80, nullable = false)
    private String numero;

    @Column(name = "tipo", length = 80, nullable = false)
    private String tipo;

    @Column(name = "localizacao", length = 300)
    private String localizacao;

    @Column(name = "responsavel", length = 160)
    private String responsavel;

    @Column(name = "data_inspecao", nullable = false)
    private LocalDateTime dataInspecao;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "ABERTA";

    @Column(name = "resultado", length = 30)
    private String resultado;

    @Column(name = "observacao", columnDefinition = "text")
    private String observacao;

    @PrePersist
    void prePersist() {
        if (dataInspecao == null) {
            dataInspecao = LocalDateTime.now();
        }
    }

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
                case "localizacao" -> setLocalizacao(MapValues.str(v));
                case "responsavel" -> setResponsavel(MapValues.str(v));
                case "data_inspecao" -> setDataInspecao(MapValues.dtTime(v));
                case "status" -> setStatus(MapValues.str(v));
                case "resultado" -> setResultado(MapValues.str(v));
                case "observacao" -> setObservacao(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("empresa_id", empresaId);
        m.put("numero", numero); m.put("tipo", tipo);
        m.put("localizacao", localizacao); m.put("responsavel", responsavel);
        m.put("data_inspecao", dataInspecao);
        m.put("status", status); m.put("resultado", resultado); m.put("observacao", observacao);
        return m;
    }
}
