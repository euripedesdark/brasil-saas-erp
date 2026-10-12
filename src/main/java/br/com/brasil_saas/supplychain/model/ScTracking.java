package br.com.brasil_saas.supplychain.model;

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
@Table(name = "bc_sc_tracking", schema = "brasil_saas")
@Getter
@Setter
public class ScTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "carga_id", nullable = false)
    private Long cargaId;

    @Column(name = "evento", length = 50, nullable = false)
    private String evento;

    @Column(name = "data_evento", nullable = false)
    private LocalDateTime dataEvento;

    @Column(name = "localizacao", length = 200)
    private String localizacao;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "ocorrencia", length = 500)
    private String ocorrencia;

    @Column(name = "comprovante", length = 500)
    private String comprovante;

    @PrePersist
    void prePersist() {
        if (dataEvento == null) {
            dataEvento = LocalDateTime.now();
        }
    }
}
