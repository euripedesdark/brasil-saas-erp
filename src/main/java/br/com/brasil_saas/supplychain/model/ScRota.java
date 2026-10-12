package br.com.brasil_saas.supplychain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "bc_sc_rota", schema = "brasil_saas")
@Getter
@Setter
public class ScRota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "codigo", length = 80, nullable = false)
    private String codigo;

    @Column(name = "origem", length = 200, nullable = false)
    private String origem;

    @Column(name = "destino", length = 200, nullable = false)
    private String destino;

    @Column(name = "distancia_km", precision = 12, scale = 2)
    private BigDecimal distanciaKm;

    @Column(name = "tempo_minutos")
    private Integer tempoMinutos;

    @Column(name = "custo_base", precision = 18, scale = 2)
    private BigDecimal custoBase;

    @Column(name = "custo_km", precision = 18, scale = 4)
    private BigDecimal custoKm;

    @Column(name = "pedagio", precision = 18, scale = 2)
    private BigDecimal pedagio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "restricoes", columnDefinition = "jsonb")
    private String restricoes = "{}";

    @Column(name = "ativo")
    private Boolean ativo = true;
}
