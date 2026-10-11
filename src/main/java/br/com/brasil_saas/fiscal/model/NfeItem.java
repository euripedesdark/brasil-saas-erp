package br.com.brasil_saas.fiscal.model;

import br.com.brasil_saas.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "bc_fis_nfe_item", schema = "brasil_saas")
@Getter @Setter
public class NfeItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nfe_id", nullable = false)
    private Nfe nfe;

    @Column(name = "produto_id")
    private Long produtoId;

    @Column(name = "numero_item", nullable = false)
    private Integer numeroItem;

    @Column(length = 8)
    private String ncm;

    @Column(length = 4)
    private String cfop;

    @Column(length = 7)
    private String cest;

    @Column(precision = 15, scale = 4, nullable = false)
    private BigDecimal quantidade;

    @Column(length = 10)
    private String unidade;
    /** cProd: o codigo do produto no fornecedor. Nao e o codigo interno do ERP. */
    @Column(length = 60)
    private String codigoProduto;

    /**
     * cEAN do item, so quando o emissor escreveu digitos.
     *
     * <p>Quando o produto nao tem GTIN, o emissor escreve texto no lugar
     * ("SEM GTIN", "NAO SE APLICA") porque o campo e obrigatorio no XSD.
     * Guardar esse texto casaria o item com todos os outros da mesma nota.
     * A leitura normaliza e guarda null; ver NfeXmlReader.normalizaEan.
     */
    @Column(length = 20)
    private String codigoBarras;


    @Column(name = "valor_unitario", precision = 15, scale = 4, nullable = false)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorTotal;

    @Column(name = "aliquota_icms", precision = 7, scale = 4)
    private BigDecimal aliquotaIcms;

    @Column(name = "valor_icms", precision = 15, scale = 2)
    private BigDecimal valorIcms;

    @Column(name = "aliquota_ipi", precision = 7, scale = 4)
    private BigDecimal aliquotaIpi;

    @Column(name = "valor_ipi", precision = 15, scale = 2)
    private BigDecimal valorIpi;
}
