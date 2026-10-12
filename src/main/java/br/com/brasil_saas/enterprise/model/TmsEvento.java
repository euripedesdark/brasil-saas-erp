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

import java.time.LocalDateTime;

@Entity
@Table(name = "bc_tms_evento", schema = "brasil_saas")
@Getter
@Setter
public class TmsEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "ordem_id", nullable = false)
    private Long ordemId;

    @Column(name = "tipo", length = 50, nullable = false)
    private String tipo;

    @Column(name = "data_evento", nullable = false)
    private LocalDateTime dataEvento;

    @Column(name = "localizacao", length = 200)
    private String localizacao;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @PrePersist
    void prePersist() {
        if (dataEvento == null) {
            dataEvento = LocalDateTime.now();
        }
    }
}
