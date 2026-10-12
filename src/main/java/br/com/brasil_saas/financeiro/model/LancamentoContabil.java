package br.com.brasil_saas.financeiro.model;
import br.com.brasil_saas.shared.model.AuditableEntity;
import jakarta.persistence.*; import lombok.*;
import java.math.BigDecimal; import java.time.LocalDate;
@Entity @Table(name="bc_fin_lancamento_contabil", schema="brasil_saas") @Getter @Setter @NoArgsConstructor
public class LancamentoContabil extends AuditableEntity {
    @Column(name="data_lancamento", nullable=false) private LocalDate dataLancamento;
    @Column(name="periodo_contabil_id") private Long periodoContabilId;
    @Column(name="descricao_historico", nullable=false) private String descricaoHistorico;
    @Column(name="valor_total", precision=15, scale=2, nullable=false) private BigDecimal valorTotal;
    @Column(length=50) private String origem;
    @Column(name="id_origem") private Long idOrigem;
}
