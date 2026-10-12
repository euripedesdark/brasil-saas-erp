package br.com.brasil_saas.compras.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="bc_com_solicitacao_item", schema="brasil_saas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SolicitacaoCompraItem extends TenantEntity {
    // A volta para a solicitacao fica fora do JSON. SolicitacaoCompra tem itens
    // de volta, entao o Jackson percorre solicitacao -> itens -> solicitacao -> ...
    // e morre em "Document nesting depth (1001) exceeds the maximum allowed
    // (1000)". Como os cabecalhos ja tinham saído, a aprovacao respondia 200 com
    // JSON cortado e o status da solicitacao nunca chegava a mudar na tela.
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="solicitacao_id",nullable=false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private SolicitacaoCompra solicitacao;

    // Idem para a folha: mesma armadilha, mesmo sintoma.
    @Column(name = "solicitacao_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonProperty("solicitacaoId")
    private Long solicitacaoIdRef;
    @Column(name="produto_id",nullable=false) private Long produtoId;
    @Column(nullable=false,precision=15,scale=4) private BigDecimal quantidade;
    @Column private String observacao;
}