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

@Entity
@Table(name = "bc_grc_risco_controle", schema = "brasil_saas")
@Getter
@Setter
public class GrcRiscoControle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "risco_id", nullable = false)
    private Long riscoId;

    @Column(name = "controle_id", nullable = false)
    private Long controleId;
}
