package br.com.brasil_saas.ativos.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="bc_ativo_plano_manutencao", schema="brasil_saas")
@Getter @Setter @NoArgsConstructor
public class PlanoManutencao extends TenantEntity {
    @Column(name="ativo_id",nullable=false) private Long ativoId;
    @Column(nullable=false,length=30) private String codigo;
    @Column(nullable=false) private String descricao;
    @Column(name="tipo_ciclo",nullable=false,length=20) private String tipoCiclo="TEMPO";
    @Column(name="intervalo_dias") private Integer intervaloDias;
    @Column(name="intervalo_contador",precision=15,scale=2) private BigDecimal intervaloContador;
    @Column(name="antecedencia_dias",nullable=false) private Integer antecedenciaDias=0;
    @Column(name="ultima_execucao") private LocalDate ultimaExecucao;
    @Column(name="contador_ultima_execucao",precision=15,scale=2) private BigDecimal contadorUltimaExecucao;
    @Column(name="proxima_data") private LocalDate proximaData;
    @Column(length=4000) private String checklist;
    @Column(name="horas_estimadas",precision=10,scale=2) private BigDecimal horasEstimadas;
    @Column(name="custo_estimado",precision=15,scale=2) private BigDecimal custoEstimado;
    @Column(nullable=false,length=20) private String prioridade="MEDIA";
    @Column(name="responsavel_id") private Long responsavelId;
    @Column(nullable=false) private Boolean ativo=Boolean.TRUE;
}
