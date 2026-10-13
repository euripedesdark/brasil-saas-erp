package br.com.brasil_saas.cadastro.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bc_cad_produto", schema = "brasil_saas")
@Getter @Setter
public class Produto extends TenantEntity {
    @Column(length = 30, nullable = false)
    private String codigo;
    @Column(length = 200, nullable = false)
    private String nome;
    @Column(columnDefinition = "text")
    private String descricao;
    @Column(name = "url_produto", length = 500)
    private String urlProduto;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_medida_id")
    private UnidadeMedida unidadeMedida;
    @Column(length = 8)
    private String ncm;
    @Column(name = "cfop_padrao", length = 4)
    private String cfopPadrao;

    /**
     * CEST: especificador da substituicao tributaria, 7 digitos.
     *
     * <p>Segue o produto e nao a nota: se o item veio com CEST 1300201, e o
     * mesmo CEST nas proximas 500 compras. Guardar so em bc_fis_nfe_item
     * obrigaria a redigitar no cadastro toda vez.
     */
    @Column(length = 7)
    private String cest;
    @Column(name = "codigo_barras", length = 30)
    private String codigoBarras;
    @Column(name = "preco_custo", precision = 15, scale = 4, nullable = false)
    private BigDecimal precoCusto = BigDecimal.ZERO;
    @Column(name = "preco_venda", precision = 15, scale = 2, nullable = false)
    private BigDecimal precoVenda = BigDecimal.ZERO;
    @Column(name = "estoque_minimo", precision = 15, scale = 3, nullable = false)
    private BigDecimal estoqueMinimo = BigDecimal.ZERO;
    @Column(name = "estoque_maximo", precision = 15, scale = 3, nullable = false)
    private BigDecimal estoqueMaximo = BigDecimal.ZERO;
    @Column(precision = 10, scale = 3)
    private BigDecimal peso;
    @Column(length = 20, nullable = false)
    private String tipo = "PRODUTO";
    @Column(nullable = false)
    private Boolean ativo = Boolean.TRUE;
    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProdutoVariacao> variacoes = new ArrayList<>();
    @OneToMany(mappedBy = "produto")
    private List<ProdutoImagem> imagens = new ArrayList<>();
}
