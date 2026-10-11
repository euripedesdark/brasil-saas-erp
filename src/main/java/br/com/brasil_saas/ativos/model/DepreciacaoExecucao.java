package br.com.brasil_saas.ativos.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="bc_ativo_depreciacao_execucao", schema="brasil_saas")
@Getter @Setter @NoArgsConstructor
public class DepreciacaoExecucao extends TenantEntity {
    @Column(nullable=false,length=7) private String periodo;
    @Column(nullable=false,length=20) private String status="EFETIVADA";
    @Column(name="quantidade_ativos",nullable=false) private Integer quantidadeAtivos=0;
    @Column(name="valor_total",precision=15,scale=2,nullable=false) private BigDecimal valorTotal=BigDecimal.ZERO;
    @Column(name="valor_contabilizado",precision=15,scale=2,nullable=false) private BigDecimal valorContabilizado=BigDecimal.ZERO;
    @Column(name="lancamento_id") private Long lancamentoId;
    @Column(name="usuario_id") private Long usuarioId;
    @Column(name="estornada_em") private LocalDateTime estornadaEm;
}
