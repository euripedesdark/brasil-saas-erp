package br.com.brasil_saas.cadastro.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bc_cad_categoria", schema = "brasil_saas")
@Getter @Setter
public class Categoria extends TenantEntity {
    @Column(length = 100, nullable = false)
    private String nome;
    @Column
    private String descricao;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_pai_id")
    private Categoria categoriaPai;
    @OneToMany(mappedBy = "categoriaPai")
    private List<Categoria> filhos = new ArrayList<>();
    @Column(length = 20, nullable = false)
    private String tipo = "PRODUTO";
}
