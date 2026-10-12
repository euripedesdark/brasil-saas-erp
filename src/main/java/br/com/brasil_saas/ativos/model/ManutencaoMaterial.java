package br.com.brasil_saas.ativos.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name="bc_ativo_manutencao_material", schema="brasil_saas")
@Getter @Setter @NoArgsConstructor
public class ManutencaoMaterial extends TenantEntity {
    @Column(name="manutencao_id",nullable=false) private Long manutencaoId;
    @Column(name="produto_id") private Long produtoId;
    @Column(nullable=false) private String descricao;
    @Column(precision=15,scale=4,nullable=false) private BigDecimal quantidade=BigDecimal.ZERO;
    @Column(name="custo_unitario",precision=15,scale=4,nullable=false) private BigDecimal custoUnitario=BigDecimal.ZERO;
    @Column(name="custo_total",precision=15,scale=2,nullable=false) private BigDecimal custoTotal=BigDecimal.ZERO;
}
