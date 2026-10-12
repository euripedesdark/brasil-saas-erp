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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bc_grc_evidencia", schema = "brasil_saas")
@Getter
@Setter
public class GrcEvidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "entidade_tipo", length = 60, nullable = false)
    private String entidadeTipo;

    @Column(name = "entidade_id", nullable = false)
    private Long entidadeId;

    @Column(name = "nome", length = 200, nullable = false)
    private String nome;

    @Column(name = "localizacao", length = 1000)
    private String localizacao;

    @Column(name = "validade")
    private LocalDate validade;

    @Column(name = "hash_documento", length = 128)
    private String hashDocumento;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
