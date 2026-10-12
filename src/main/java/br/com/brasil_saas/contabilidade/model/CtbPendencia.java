package br.com.brasil_saas.contabilidade.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bc_ctb_pendencia", schema = "brasil_saas")
@Getter
@Setter
public class CtbPendencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "evento", length = 60, nullable = false)
    private String evento;

    @Column(name = "origem_tipo", length = 60, nullable = false)
    private String origemTipo;

    @Column(name = "origem_id", nullable = false)
    private Long origemId;

    @Column(name = "motivo", length = 500, nullable = false)
    private String motivo;

    @Column(name = "valor", precision = 18, scale = 2)
    private BigDecimal valor;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolvido_em")
    private LocalDateTime resolvidoEm;
}
