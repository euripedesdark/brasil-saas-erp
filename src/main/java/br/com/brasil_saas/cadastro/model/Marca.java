package br.com.brasil_saas.cadastro.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bc_cad_marca", schema = "brasil_saas")
@Getter @Setter
public class Marca extends TenantEntity {
    @Column(length = 100, nullable = false)
    private String nome;
    @Column
    private String descricao;
}
