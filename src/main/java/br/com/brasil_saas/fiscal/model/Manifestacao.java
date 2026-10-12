package br.com.brasil_saas.fiscal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bc_fis_manifestacao", schema = "brasil_saas")
@Getter @Setter
public class Manifestacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "chave_acesso", length = 50, nullable = false)
    private String chaveAcesso;

    @Column(length = 30, nullable = false)
    private String tipo;

    @Column
    private String justificativa;

    @Column(length = 50)
    private String protocolo;

    @Column(name = "data_evento")
    private LocalDateTime dataEvento;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
