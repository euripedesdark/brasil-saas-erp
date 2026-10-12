package br.com.brasil_saas.enterprise.model;

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
import java.time.LocalDateTime;

@Entity
@Table(name = "bc_grc_avaliacao", schema = "brasil_saas")
@Getter
@Setter
public class GrcAvaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "risco_id", nullable = false)
    private Long riscoId;

    @Column(name = "periodo", length = 7, nullable = false)
    private String periodo;

    @Column(name = "probabilidade", precision = 8, scale = 2, nullable = false)
    private BigDecimal probabilidade;

    @Column(name = "impacto", precision = 8, scale = 2, nullable = false)
    private BigDecimal impacto;

    @Column(name = "nivel", precision = 12, scale = 2, nullable = false)
    private BigDecimal nivel;

    @Column(name = "tendencia", length = 30)
    private String tendencia;

    @Column(name = "avaliador", length = 160)
    private String avaliador;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    @Column(name = "avaliado_em", nullable = false)
    private LocalDateTime avaliadoEm;

    @PrePersist
    void prePersist() {
        if (avaliadoEm == null) {
            avaliadoEm = LocalDateTime.now();
        }
    }
}
