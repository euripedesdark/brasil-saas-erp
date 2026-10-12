package br.com.brasil_saas.ativos.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="bc_ativo_imobilizado", schema="brasil_saas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AtivoImobilizado extends TenantEntity {
    @Column(nullable=false,length=50) private String codigo;
    @Column(nullable=false) private String descricao;
    @Column(length=100) private String classe;
    @Column(name="numero_serie",length=100) private String numeroSerie;
    @Column private String localizacao;
    @Column(name="responsavel_id") private Long responsavelId;
    @Column(name="data_aquisicao") private LocalDate dataAquisicao;
    @Column(name="valor_aquisicao",precision=15,scale=2,nullable=false) private BigDecimal valorAquisicao=BigDecimal.ZERO;
    @Column(name="valor_residual",precision=15,scale=2,nullable=false) private BigDecimal valorResidual=BigDecimal.ZERO;
    @Column(name="valor_depreciado",precision=15,scale=2,nullable=false) private BigDecimal valorDepreciado=BigDecimal.ZERO;
    @Column(nullable=false,length=30) private String status="ATIVO";
    @Column(name="vida_util_meses") private Integer vidaUtilMeses;
    @Column(name="classe_id") private Long classeId;
    @Column(name="centro_custo_id") private Long centroCustoId;
    @Column(name="fornecedor_id") private Long fornecedorId;
    @Column(name="numero_documento",length=60) private String numeroDocumento;
    @Column(name="data_inicio_depreciacao") private LocalDate dataInicioDepreciacao;
    @Column(name="metodo_depreciacao",length=30) private String metodoDepreciacao;
    @Column(name="taxa_anual",precision=7,scale=4) private BigDecimal taxaAnual;
    @Column(name="ativo_pai_id") private Long ativoPaiId;
    @Column(length=120) private String fabricante;
    @Column(length=120) private String modelo;
    @Column(name="garantia_ate") private LocalDate garantiaAte;
    @Column(name="valor_reavaliacao",precision=15,scale=2,nullable=false) private BigDecimal valorReavaliacao=BigDecimal.ZERO;
    @Column(name="valor_impairment",precision=15,scale=2,nullable=false) private BigDecimal valorImpairment=BigDecimal.ZERO;
    @Column(name="data_baixa") private LocalDate dataBaixa;
    @Column(name="valor_baixa",precision=15,scale=2) private BigDecimal valorBaixa;
    @Column(name="motivo_baixa",length=500) private String motivoBaixa;
    @Column(name="ultimo_periodo_depreciado",length=7) private String ultimoPeriodoDepreciado;
    @Column(name="contador_atual",precision=15,scale=2) private BigDecimal contadorAtual;
    @Column(name="unidade_contador",length=20) private String unidadeContador;
    @Column(nullable=false) private Boolean critico=Boolean.FALSE;
}