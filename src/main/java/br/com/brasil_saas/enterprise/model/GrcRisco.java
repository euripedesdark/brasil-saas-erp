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
@Table(name = "bc_grc_risco", schema = "brasil_saas")
@Getter
@Setter
public class GrcRisco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "codigo", length = 60, nullable = false)
    private String codigo;

    @Column(name = "descricao", length = 500, nullable = false)
    private String descricao;

    @Column(name = "categoria", length = 80)
    private String categoria;

    @Column(name = "probabilidade", precision = 8, scale = 2, nullable = false)
    private BigDecimal probabilidade = BigDecimal.ZERO;

    @Column(name = "impacto", precision = 8, scale = 2, nullable = false)
    private BigDecimal impacto = BigDecimal.ZERO;

    @Column(name = "nivel", precision = 12, scale = 2, nullable = false)
    private BigDecimal nivel = BigDecimal.ZERO;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "ABERTO";

    @Column(name = "responsavel", length = 160)
    private String responsavel;

    @Column(name = "prazo")
    private LocalDate prazo;

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
