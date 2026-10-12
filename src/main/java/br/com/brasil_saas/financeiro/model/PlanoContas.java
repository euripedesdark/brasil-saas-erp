package br.com.brasil_saas.financeiro.model;
import br.com.brasil_saas.shared.model.AuditableEntity;
import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="bc_fin_plano_contas", schema="brasil_saas") @Getter @Setter @NoArgsConstructor
public class PlanoContas extends AuditableEntity {
    @Column(length=50, nullable=false) private String codigo;
    @Column(nullable=false) private String descricao;
    @Column(length=1, nullable=false, columnDefinition="bpchar(1)") private String tipo; // S|A
    @Column(length=1, nullable=false, columnDefinition="bpchar(1)") private String natureza; // D|C
    @Column(name="conta_pai_id") private Long contaPaiId;
    @Column(nullable=false) private Integer nivel = 1;
    @Column private Boolean ativa = true;
}
