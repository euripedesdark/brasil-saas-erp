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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bc_cont_rateio", schema = "brasil_saas")
@Getter
@Setter
public class ContRateio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "lancamento_id")
    private Long lancamentoId;

    @Column(name = "centro_custo_id")
    private Long centroCustoId;

    @Column(name = "plano_contas_id")
    private Long planoContasId;

    @Column(name = "percentual", precision = 9, scale = 4, nullable = false)
    private BigDecimal percentual;

    @Column(name = "valor", precision = 18, scale = 2, nullable = false)
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "competencia", nullable = false)
    private LocalDate competencia;

    @Column(name = "status", length = 20)
    private String status = "PENDENTE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
