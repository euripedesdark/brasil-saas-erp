package br.com.brasil_saas.ativos.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="bc_ativo_movimento", schema="brasil_saas")
@Getter @Setter @NoArgsConstructor
public class AtivoMovimento extends TenantEntity {
    @Column(name="ativo_id",nullable=false) private Long ativoId;
    @Column(nullable=false,length=30) private String tipo;
    @Column(name="data_movimento",nullable=false) private LocalDate dataMovimento;
    @Column(nullable=false,length=7) private String periodo;
    @Column(precision=15,scale=2,nullable=false) private BigDecimal valor=BigDecimal.ZERO;
    @Column(name="valor_depreciacao",precision=15,scale=2,nullable=false) private BigDecimal valorDepreciacao=BigDecimal.ZERO;
    @Column(name="valor_venda",precision=15,scale=2) private BigDecimal valorVenda;
    @Column(precision=15,scale=2) private BigDecimal resultado;
    @Column(name="execucao_id") private Long execucaoId;
    @Column(name="lancamento_id") private Long lancamentoId;
    @Column(name="centro_custo_origem_id") private Long centroCustoOrigemId;
    @Column(name="centro_custo_destino_id") private Long centroCustoDestinoId;
    @Column(name="localizacao_origem",length=255) private String localizacaoOrigem;
    @Column(name="localizacao_destino",length=255) private String localizacaoDestino;
    @Column(name="responsavel_origem_id") private Long responsavelOrigemId;
    @Column(name="responsavel_destino_id") private Long responsavelDestinoId;
    @Column(length=60) private String documento;
    @Column(length=1000) private String observacao;
    @Column(nullable=false,length=20) private String status="ATIVO";
    @Column(name="usuario_id") private Long usuarioId;
}
