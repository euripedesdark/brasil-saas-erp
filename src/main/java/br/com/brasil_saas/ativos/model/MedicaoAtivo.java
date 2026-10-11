package br.com.brasil_saas.ativos.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="bc_ativo_medicao", schema="brasil_saas")
@Getter @Setter @NoArgsConstructor
public class MedicaoAtivo extends TenantEntity {
    @Column(name="ativo_id",nullable=false) private Long ativoId;
    @Column(name="data_medicao",nullable=false) private LocalDate dataMedicao;
    @Column(precision=15,scale=2,nullable=false) private BigDecimal valor;
    @Column(length=20) private String unidade;
    @Column(length=500) private String observacao;
}
