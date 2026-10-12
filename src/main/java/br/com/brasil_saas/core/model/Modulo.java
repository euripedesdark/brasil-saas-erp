package br.com.brasil_saas.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.UUID;

/** Modulo do ERP ao qual um usuario pode ou nao ter acesso. Ver migration V91. */
@Entity
@Table(name = "bc_core_modulo", schema = "brasil_saas")
@Getter
@Setter
public class Modulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid")
    private UUID uuid;

    /** Chave estavel usada no menu, nas rotas e no filtro de documentos. */
    @Column(name = "chave", length = 30, nullable = false)
    private String chave;

    @Column(name = "nome", length = 100, nullable = false)
    private String nome;

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "icone", length = 50)
    private String icone;

    @Column(name = "rota", length = 100)
    private String rota;

    @Column(name = "ordem")
    private Integer ordem = 100;

    /** true em modulo que guarda documento/certificado: exige SUPERUSER. */
    @Column(name = "exige_superuser", nullable = false)
    private Boolean exigeSuperuser = false;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Immutable
    private LocalDateTime createdAt;
}
