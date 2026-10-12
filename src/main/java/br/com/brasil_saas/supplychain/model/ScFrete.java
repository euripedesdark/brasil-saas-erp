package br.com.brasil_saas.supplychain.model;

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
@Table(name = "bc_sc_frete", schema = "brasil_saas")
@Getter
@Setter
public class ScFrete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "carga_id", nullable = false)
    private Long cargaId;

    @Column(name = "transportadora_id")
    private Long transportadoraId;

    @Column(name = "documento", length = 100)
    private String documento;

    @Column(name = "frete", precision = 18, scale = 2, nullable = false)
    private BigDecimal frete = BigDecimal.ZERO;

    @Column(name = "pedagio", precision = 18, scale = 2)
    private BigDecimal pedagio;

    @Column(name = "adicionais", precision = 18, scale = 2)
    private BigDecimal adicionais;

    @Column(name = "total", precision = 18, scale = 2, nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "status", length = 25, nullable = false)
    private String status = "PENDENTE";

    @Column(name = "conferido_em")
    private LocalDateTime conferidoEm;

    @Column(name = "pago_em")
    private LocalDateTime pagoEm;
}
