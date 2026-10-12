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

import java.util.UUID;

@Entity
@Table(name = "bc_grc_controle", schema = "brasil_saas")
@Getter
@Setter
public class GrcControle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "codigo", length = 60, nullable = false)
    private String codigo;

    @Column(name = "descricao", length = 500, nullable = false)
    private String descricao;

    @Column(name = "tipo", length = 40, nullable = false)
    private String tipo = "PREVENTIVO";

    @Column(name = "frequencia", length = 40)
    private String frequencia;

    @Column(name = "responsavel", length = 160)
    private String responsavel;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "ATIVO";
}
