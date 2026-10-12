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
@Table(name = "bc_sc_carga", schema = "brasil_saas")
@Getter
@Setter
public class ScCarga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "codigo", length = 80, nullable = false)
    private String codigo;

    @Column(name = "transportadora_id")
    private Long transportadoraId;

    @Column(name = "veiculo", length = 100)
    private String veiculo;

    @Column(name = "origem", length = 200)
    private String origem;

    @Column(name = "destino", length = 200)
    private String destino;

    @Column(name = "peso", precision = 18, scale = 3)
    private BigDecimal peso;

    @Column(name = "volume", precision = 18, scale = 3)
    private BigDecimal volume;

    @Column(name = "valor_mercadoria", precision = 18, scale = 2)
    private BigDecimal valorMercadoria;

    @Column(name = "rota_id")
    private Long rotaId;

    @Column(name = "frete_estimado", precision = 18, scale = 2)
    private BigDecimal freteEstimado;

    @Column(name = "frete_real", precision = 18, scale = 2)
    private BigDecimal freteReal;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "PLANEJADA";

    @Column(name = "saida")
    private LocalDateTime saida;

    @Column(name = "entrega_prevista")
    private LocalDateTime entregaPrevista;

    @Column(name = "entrega")
    private LocalDateTime entrega;
}
