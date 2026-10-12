package br.com.brasil_saas.supplychain.model;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bc_sc_demanda", schema = "brasil_saas")
@Getter
@Setter
public class ScDemanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(name = "local_id")
    private Long localId;

    @Column(name = "periodo", nullable = false)
    private LocalDate periodo;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo = "PREVISAO";

    @Column(name = "quantidade", precision = 18, scale = 4, nullable = false)
    private BigDecimal quantidade = BigDecimal.ZERO;

    @Column(name = "confianca", precision = 7, scale = 4)
    private BigDecimal confianca;

    @Column(name = "origem", length = 50)
    private String origem;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ABERTA";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
