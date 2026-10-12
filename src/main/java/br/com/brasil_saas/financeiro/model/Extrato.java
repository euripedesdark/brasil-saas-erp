package br.com.brasil_saas.financeiro.model;
import br.com.brasil_saas.shared.model.AuditableEntity;
import jakarta.persistence.*; import lombok.*;
import java.math.BigDecimal; import java.time.LocalDate;
@Entity @Table(name="bc_fin_extrato", schema="brasil_saas") @Getter @Setter @NoArgsConstructor
public class Extrato extends AuditableEntity {
    @Column(name="conta_bancaria_id", nullable=false) private Long contaBancariaId;
    @Column(name="data_movimento", nullable=false) private LocalDate dataMovimento;
    @Column(nullable=false) private String descricao;
    @Column(precision=15, scale=2, nullable=false) private BigDecimal valor;
    @Column(length=1, nullable=false, columnDefinition="bpchar(1)") private String tipo; // D|C
    @Column(name="saldo_anterior", precision=15, scale=2) private BigDecimal saldoAnterior;
    @Column(name="saldo_atual", precision=15, scale=2) private BigDecimal saldoAtual;
    @Column(nullable=false) private Boolean conciliado = false;
    @Column(length=100) private String fitid;
}
