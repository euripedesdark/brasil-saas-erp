package br.com.brasil_saas.ativos.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="bc_ativo_manutencao_apontamento", schema="brasil_saas")
@Getter @Setter @NoArgsConstructor
public class ManutencaoApontamento extends TenantEntity {
    @Column(name="manutencao_id",nullable=false) private Long manutencaoId;
    @Column(name="funcionario_id") private Long funcionarioId;
    @Column(name="data_apontamento",nullable=false) private LocalDate dataApontamento;
    @Column(precision=10,scale=2,nullable=false) private BigDecimal horas=BigDecimal.ZERO;
    @Column(name="custo_hora",precision=15,scale=2,nullable=false) private BigDecimal custoHora=BigDecimal.ZERO;
    @Column(name="custo_total",precision=15,scale=2,nullable=false) private BigDecimal custoTotal=BigDecimal.ZERO;
    @Column(length=1000) private String descricao;
}
