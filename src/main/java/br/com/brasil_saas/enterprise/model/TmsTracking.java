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

import java.time.LocalDateTime;

@Entity
@Table(name = "bc_tms_tracking", schema = "brasil_saas")
@Getter
@Setter
public class TmsTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "ordem_id", nullable = false)
    private Long ordemId;

    @Column(name = "codigo", length = 100, nullable = false)
    private String codigo;

    @Column(name = "transportadora", length = 160)
    private String transportadora;

    @Column(name = "ultimo_status", length = 80)
    private String ultimoStatus;

    @Column(name = "ultima_atualizacao")
    private LocalDateTime ultimaAtualizacao;
}
