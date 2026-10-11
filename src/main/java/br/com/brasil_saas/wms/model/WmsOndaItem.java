package br.com.brasil_saas.wms.model;
import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*; import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="bc_wms_onda_item", schema="brasil_saas") @Getter @Setter
public class WmsOndaItem extends TenantEntity {
    @Column(name="onda_id", nullable=false) private Long ondaId;
    @Column(name="origem_tipo", length=20) private String origemTipo = "RESERVA";
    @Column(name="origem_id") private Long origemId;
    @Column(name="produto_id", nullable=false) private Long produtoId;
    @Column(name="qtd_solicitada", nullable=false, precision=15, scale=3) private BigDecimal qtdSolicitada;
    @Column(name="qtd_separada", nullable=false, precision=15, scale=3) private BigDecimal qtdSeparada = BigDecimal.ZERO;
    @Column(name="endereco_id") private Long enderecoId;
    @Column(nullable=false, length=20) private String status = "PENDENTE";
}
