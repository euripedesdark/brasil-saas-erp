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
@Table(name = "bc_tms_parada", schema = "brasil_saas")
@Getter
@Setter
public class TmsParada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "ordem_id", nullable = false)
    private Long ordemId;

    @Column(name = "sequencia", nullable = false)
    private Integer sequencia;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo;

    @Column(name = "localizacao", length = 300, nullable = false)
    private String localizacao;

    @Column(name = "prevista_em")
    private LocalDateTime previstaEm;

    @Column(name = "realizada_em")
    private LocalDateTime realizadaEm;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "PENDENTE";

    @Column(name = "referencia_tipo", length = 40)
    private String referenciaTipo;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(name = "peso", precision = 15, scale = 3)
    private BigDecimal peso;

    @Column(name = "volume", precision = 15, scale = 3)
    private BigDecimal volume;
}
