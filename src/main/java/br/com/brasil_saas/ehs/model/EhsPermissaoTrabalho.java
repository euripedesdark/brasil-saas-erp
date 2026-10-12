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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "bc_ehs_permissao_trabalho", schema = "brasil_saas")
@Getter
@Setter
public class EhsPermissaoTrabalho {

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

    @Column(name = "localizacao", length = 300, nullable = false)
    private String localizacao;

    @Column(name = "solicitante", length = 160)
    private String solicitante;

    @Column(name = "responsavel", length = 160)
    private String responsavel;

    @Column(name = "inicio")
    private LocalDateTime inicio;

    @Column(name = "fim")
    private LocalDateTime fim;

    @Column(name = "riscos", columnDefinition = "text")
    private String riscos;

    @Column(name = "controles", columnDefinition = "text")
    private String controles;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "SOLICITADA";

    @Column(name = "aprovada_por", length = 160)
    private String aprovadaPor;

    @Column(name = "aprovada_em")
    private LocalDateTime aprovadaEm;

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
                case "solicitante" -> setSolicitante(MapValues.str(v));
                case "responsavel" -> setResponsavel(MapValues.str(v));
                case "inicio" -> setInicio(MapValues.dtTime(v));
                case "fim" -> setFim(MapValues.dtTime(v));
                case "riscos" -> setRiscos(MapValues.str(v));
                case "controles" -> setControles(MapValues.str(v));
                case "status" -> setStatus(MapValues.str(v));
                case "aprovada_por" -> setAprovadaPor(MapValues.str(v));
                case "aprovada_em" -> setAprovadaEm(MapValues.dtTime(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("empresa_id", empresaId);
        m.put("numero", numero); m.put("tipo", tipo); m.put("localizacao", localizacao);
        m.put("solicitante", solicitante); m.put("responsavel", responsavel);
        m.put("inicio", inicio); m.put("fim", fim);
        m.put("riscos", riscos); m.put("controles", controles);
        m.put("status", status);
        m.put("aprovada_por", aprovadaPor); m.put("aprovada_em", aprovadaEm);
        return m;
    }
}
