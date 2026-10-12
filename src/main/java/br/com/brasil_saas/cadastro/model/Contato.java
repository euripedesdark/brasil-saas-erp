package br.com.brasil_saas.cadastro.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bc_cad_contato", schema = "brasil_saas")
@Getter @Setter
public class Contato extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;
    @Column(length = 30, nullable = false)
    private String tipo = "COMERCIAL";
    @Column(length = 150)
    private String nome;
    @Column(length = 150)
    private String email;
    @Column(length = 20)
    private String telefone;
    @Column
    private String observacao;
}
