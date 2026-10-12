package br.com.brasil_saas.cadastro.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bc_cad_papel", schema = "brasil_saas")
@Getter @Setter
public class Papel extends TenantEntity {
    @Column(length = 50, nullable = false)
    private String nome; // CLIENTE, FORNECEDOR, TRANSPORTADORA...
    @Column
    private String descricao;
}
