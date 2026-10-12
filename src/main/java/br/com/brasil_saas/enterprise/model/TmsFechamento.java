package br.com.brasil_saas.enterprise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bc_tms_fechamento", schema = "brasil_saas")
@Getter
@Setter
public class TmsFechamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "ordem_id", nullable = false)
    private Long ordemId;

    @Column(name = "frete_contratado", precision = 15, scale = 2, nullable = false)
    private BigDecimal freteContratado = BigDecimal.ZERO;

    @Column(name = "adicionais", precision = 15, scale = 2, nullable = false)
    private BigDecimal adicionais = BigDecimal.ZERO;

    @Column(name = "descontos", precision = 15, scale = 2, nullable = false)
    private BigDecimal descontos = BigDecimal.ZERO;

    @Column(name = "frete_aprovado", precision = 15, scale = 2, nullable = false)
    private BigDecimal freteAprovado = BigDecimal.ZERO;

    @Column(name = "documento", length = 120)
    private String documento;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "PENDENTE";

    @Column(name = "aprovado_por", length = 160)
    private String aprovadoPor;

    @Column(name = "aprovado_em")
    private LocalDateTime aprovadoEm;
}
