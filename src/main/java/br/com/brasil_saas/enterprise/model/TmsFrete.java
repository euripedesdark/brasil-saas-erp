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

@Entity
@Table(name = "bc_tms_frete", schema = "brasil_saas")
@Getter
@Setter
public class TmsFrete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "ordem_id", nullable = false)
    private Long ordemId;

    @Column(name = "componente", length = 80, nullable = false)
    private String componente;

    @Column(name = "valor", precision = 15, scale = 2, nullable = false)
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "documento", length = 120)
    private String documento;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "PENDENTE";
}
