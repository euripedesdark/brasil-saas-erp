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
import java.util.UUID;

@Entity
@Table(name = "bc_tms_ordem", schema = "brasil_saas")
@Getter
@Setter
public class TmsOrdem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "numero", length = 60, nullable = false)
    private String numero;

    @Column(name = "origem", length = 200)
    private String origem;

    @Column(name = "destino", length = 200)
    private String destino;

    @Column(name = "transportadora_id")
    private Long transportadoraId;

    @Column(name = "modalidade", length = 40, nullable = false)
    private String modalidade = "RODOVIARIO";

    @Column(name = "status", length = 30, nullable = false)
    private String status = "PLANEJADA";

    @Column(name = "data_prevista_saida")
    private LocalDateTime dataPrevistaSaida;

    @Column(name = "data_prevista_entrega")
    private LocalDateTime dataPrevistaEntrega;

    @Column(name = "peso", precision = 15, scale = 3)
    private BigDecimal peso;

    @Column(name = "volume", precision = 15, scale = 3)
    private BigDecimal volume;

    @Column(name = "frete_previsto", precision = 15, scale = 2)
    private BigDecimal fretePrevisto;

    @Column(name = "frete_real", precision = 15, scale = 2)
    private BigDecimal freteReal;

    @Column(name = "rota_id")
    private Long rotaId;

    @Column(name = "veiculo", length = 120)
    private String veiculo;

    @Column(name = "motorista", length = 160)
    private String motorista;

    @Column(name = "data_saida")
    private LocalDateTime dataSaida;

    @Column(name = "data_entrega")
    private LocalDateTime dataEntrega;

    @Column(name = "distancia_real_km", precision = 12, scale = 2)
    private BigDecimal distanciaRealKm;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
