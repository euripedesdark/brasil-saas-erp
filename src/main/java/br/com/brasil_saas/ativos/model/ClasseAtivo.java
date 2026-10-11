package br.com.brasil_saas.ativos.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name="bc_ativo_classe", schema="brasil_saas")
@Getter @Setter @NoArgsConstructor
public class ClasseAtivo extends TenantEntity {
    @Column(nullable=false,length=30) private String codigo;
    @Column(nullable=false,length=255) private String descricao;
    @Column(name="metodo_depreciacao",nullable=false,length=30) private String metodoDepreciacao="LINEAR";
    @Column(name="vida_util_meses") private Integer vidaUtilMeses;
    @Column(name="taxa_anual",precision=7,scale=4) private BigDecimal taxaAnual;
    @Column(name="conta_ativo_id") private Long contaAtivoId;
    @Column(name="conta_depreciacao_acumulada_id") private Long contaDepreciacaoAcumuladaId;
    @Column(name="conta_despesa_depreciacao_id") private Long contaDespesaDepreciacaoId;
    @Column(name="conta_ganho_baixa_id") private Long contaGanhoBaixaId;
    @Column(name="conta_perda_baixa_id") private Long contaPerdaBaixaId;
    @Column(name="conta_reavaliacao_id") private Long contaReavaliacaoId;
    @Column(name="conta_impairment_id") private Long contaImpairmentId;
    @Column(nullable=false) private Boolean ativo=Boolean.TRUE;
}
