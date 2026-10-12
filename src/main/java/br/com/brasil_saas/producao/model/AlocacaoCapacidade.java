package br.com.brasil_saas.producao.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Horas reservadas em um centro de trabalho num dia: o calendario de carga. */
@Entity
@Table(name = "bc_prod_alocacao_capacidade", schema = "brasil_saas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AlocacaoCapacidade extends TenantEntity {
    @Column(name = "centro_trabalho_id", nullable = false) private Long centroTrabalhoId;
    @Column(nullable = false) private LocalDate data;
    @Column(nullable = false, precision = 12, scale = 4) private BigDecimal horas;
    @Column(name = "ordem_producao_id") private Long ordemProducaoId;
    @Column(name = "operacao_roteiro_id") private Long operacaoRoteiroId;
    @Column(nullable = false, length = 20) private String origem = "OP";
    @Column private String observacao;
}
