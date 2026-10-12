package br.com.brasil_saas.contabilidade.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bc_ent_periodo_contabil", schema = "brasil_saas")
@Getter
@Setter
public class PeriodoContabilEnt {

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

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ABERTO";

    @Column(name = "fechamento_financeiro", nullable = false)
    private Boolean fechamentoFinanceiro = false;

    @Column(name = "fechamento_fiscal", nullable = false)
    private Boolean fechamentoFiscal = false;

    @Column(name = "fechamento_contabil", nullable = false)
    private Boolean fechamentoContabil = false;

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "fechado_em")
    private LocalDateTime fechadoEm;

    @Column(name = "fechado_por")
    private Long fechadoPor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }

    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("competencia", getCompetencia()); m.put("status", getStatus());
        m.put("fechamento_financeiro", getFechamentoFinanceiro());
        m.put("fechamento_fiscal", getFechamentoFiscal());
        m.put("fechamento_contabil", getFechamentoContabil());
        m.put("observacao", getObservacao());
        m.put("fechado_em", getFechadoEm()); m.put("fechado_por", getFechadoPor());
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        return m;
    }
}