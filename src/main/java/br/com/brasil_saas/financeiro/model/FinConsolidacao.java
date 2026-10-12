package br.com.brasil_saas.financeiro.model;

import br.com.brasil_saas.shared.web.MapValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "bc_fin_consolidacao", schema = "brasil_saas")
@Getter
@Setter
public class FinConsolidacao {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "competencia", nullable = false)
    private LocalDate competencia;

    @Column(name = "grupo", length = 100, nullable = false)
    private String grupo = "PADRAO";

    @Column(name = "status", length = 25, nullable = false)
    private String status = "RASCUNHO";

    @Column(name = "eliminacoes", precision = 18, scale = 2, nullable = false)
    private BigDecimal eliminacoes = BigDecimal.ZERO;

    @Column(name = "ajustes", precision = 18, scale = 2, nullable = false)
    private BigDecimal ajustes = BigDecimal.ZERO;

    @Column(name = "cambio_medio", precision = 18, scale = 8)
    private BigDecimal cambioMedio;

    @Column(name = "cambio_fechamento", precision = 18, scale = 8)
    private BigDecimal cambioFechamento;

    @Column(name = "valor_consolidado", precision = 18, scale = 2, nullable = false)
    private BigDecimal valorConsolidado = BigDecimal.ZERO;

    @Column(name = "consolidado_em")
    private LocalDateTime consolidadoEm;

    @Column(name = "consolidado_por")
    private Long consolidadoPor;

    @Column(name = "observacao", columnDefinition = "text")
    private String observacao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    void prePersist() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        LocalDateTime agora = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = agora;
        }
        updatedAt = agora;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "competencia" -> setCompetencia(MapValues.dt(v));
                case "grupo" -> setGrupo(MapValues.str(v));
                case "status" -> setStatus(MapValues.str(v));
                case "eliminacoes" -> { BigDecimal x = MapValues.bd(v); if (x != null) setEliminacoes(x); }
                case "ajustes" -> { BigDecimal x = MapValues.bd(v); if (x != null) setAjustes(x); }
                case "cambio_medio" -> setCambioMedio(MapValues.bd(v));
                case "cambio_fechamento" -> setCambioFechamento(MapValues.bd(v));
                case "valor_consolidado" -> { BigDecimal x = MapValues.bd(v); if (x != null) setValorConsolidado(x); }
                case "consolidado_em" -> setConsolidadoEm(MapValues.dtTime(v));
                case "consolidado_por" -> setConsolidadoPor(MapValues.lng(v));
                case "observacao" -> setObservacao(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("uuid", uuid); m.put("empresa_id", empresaId);
        m.put("competencia", competencia); m.put("grupo", grupo); m.put("status", status);
        m.put("eliminacoes", eliminacoes); m.put("ajustes", ajustes);
        m.put("cambio_medio", cambioMedio); m.put("cambio_fechamento", cambioFechamento);
        m.put("valor_consolidado", valorConsolidado);
        m.put("consolidado_em", consolidadoEm); m.put("consolidado_por", consolidadoPor);
        m.put("observacao", observacao);
        m.put("created_at", createdAt); m.put("updated_at", updatedAt);
        m.put("created_by", createdBy); m.put("updated_by", updatedBy);
        return m;
    }
}
