package br.com.brasil_saas.enterprise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;

@Entity
@Table(name = "bc_int_endpoint", schema = "brasil_saas")
@Getter
@Setter
public class IntEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "codigo", length = 100, nullable = false)
    private String codigo;

    @Column(name = "nome", length = 200, nullable = false)
    private String nome;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo = "WEBHOOK";

    @Column(name = "url", length = 1000, nullable = false)
    private String url;

    @Column(name = "segredo_hash", length = 128)
    private String segredoHash;

    @Column(name = "eventos", columnDefinition = "text")
    private String eventos;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 10000;

    @Column(name = "tentativas", nullable = false)
    private Integer tentativas = 3;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (createdAt == null) createdAt = agora;
        updatedAt = agora;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
