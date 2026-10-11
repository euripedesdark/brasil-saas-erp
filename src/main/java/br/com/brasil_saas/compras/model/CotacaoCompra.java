package br.com.brasil_saas.compras.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity @Table(name="bc_com_cotacao", schema="brasil_saas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CotacaoCompra extends TenantEntity {
    @Column(name="solicitacao_id") private Long solicitacaoId;
    @Column(nullable=false,length=20) private String numero;
    @Column(nullable=false,length=20) private String status="ABERTA";
    @Column(name="data_abertura",nullable=false) private LocalDate dataAbertura=LocalDate.now();
    @Column(name="data_limite") private LocalDate dataLimite;
    @Column(columnDefinition="TEXT") private String observacao;
}