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
@Table(name = "bc_tms_rota", schema = "brasil_saas")
@Getter
@Setter
public class TmsRota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "codigo", length = 60, nullable = false)
    private String codigo;

    @Column(name = "descricao", length = 300)
    private String descricao;

    @Column(name = "origem", length = 200, nullable = false)
    private String origem;

    @Column(name = "destino", length = 200, nullable = false)
    private String destino;

    @Column(name = "distancia_km", precision = 12, scale = 2)
    private BigDecimal distanciaKm;

    @Column(name = "tempo_estimado_min")
    private Integer tempoEstimadoMin;

    @Column(name = "pedagio_estimado", precision = 15, scale = 2)
    private BigDecimal pedagioEstimado;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;
}
