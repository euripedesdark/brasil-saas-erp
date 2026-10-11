package br.com.brasil_saas.ativos.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="bc_ativo_nota_manutencao", schema="brasil_saas")
@Getter @Setter @NoArgsConstructor
public class NotaManutencao extends TenantEntity {
    @Column(name="ativo_id",nullable=false) private Long ativoId;
    @Column(nullable=false,length=50) private String numero;
    @Column(nullable=false,length=30) private String tipo="AVARIA";
    @Column(nullable=false,length=20) private String prioridade="MEDIA";
    @Column(nullable=false,length=30) private String status="ABERTA";
    @Column(nullable=false,length=2000) private String descricao;
    @Column(length=1000) private String sintoma;
    @Column(length=1000) private String causa;
    @Column(name="equipamento_parado",nullable=false) private Boolean equipamentoParado=Boolean.FALSE;
    @Column(name="inicio_parada") private LocalDateTime inicioParada;
    @Column(name="fim_parada") private LocalDateTime fimParada;
    @Column(name="data_nota",nullable=false) private LocalDate dataNota;
    @Column(name="manutencao_id") private Long manutencaoId;
    @Column(name="solicitante_id") private Long solicitanteId;
}
