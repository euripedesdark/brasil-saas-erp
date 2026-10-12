package br.com.brasil_saas.financeiro.model;
import br.com.brasil_saas.shared.model.AuditableEntity;
import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="bc_fin_centro_custo", schema="brasil_saas") @Getter @Setter @NoArgsConstructor
public class CentroCusto extends AuditableEntity {
    @Column(length=50, nullable=false) private String codigo;
    @Column(nullable=false) private String descricao;
    @Column(name="centro_custo_pai_id") private Long centroCustoPaiId;
    @Column private Boolean ativo = true;
}
