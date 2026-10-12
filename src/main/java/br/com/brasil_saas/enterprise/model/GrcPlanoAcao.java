package br.com.brasil_saas.enterprise.model;

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
import java.util.UUID;

@Entity
@Table(name = "bc_grc_plano_acao", schema = "brasil_saas")
@Getter
@Setter
public class GrcPlanoAcao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "risco_id", nullable = false)
    private Long riscoId;

    @Column(name = "descricao", length = 1000, nullable = false)
    private String descricao;

    @Column(name = "responsavel", length = 160)
    private String responsavel;

    @Column(name = "prazo")
    private LocalDate prazo;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "ABERTO";

    @Column(name = "percentual_conclusao", precision = 5, scale = 2, nullable = false)
    private BigDecimal percentualConclusao = BigDecimal.ZERO;

    @Column(name = "evidencia_id")
    private Long evidenciaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
}
