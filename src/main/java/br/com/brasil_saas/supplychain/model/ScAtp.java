package br.com.brasil_saas.supplychain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bc_sc_atp", schema = "brasil_saas")
@Getter
@Setter
public class ScAtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(name = "local_id")
    private Long localId;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "estoque_disponivel", precision = 18, scale = 4)
    private BigDecimal estoqueDisponivel;

    @Column(name = "entradas_confirmadas", precision = 18, scale = 4)
    private BigDecimal entradasConfirmadas;

    @Column(name = "reservas", precision = 18, scale = 4)
    private BigDecimal reservas;

    @Column(name = "demanda_aberta", precision = 18, scale = 4)
    private BigDecimal demandaAberta;

    @Column(name = "quantidade_atp", precision = 18, scale = 4)
    private BigDecimal quantidadeAtp;

    @Column(name = "quantidade_ctp", precision = 18, scale = 4)
    private BigDecimal quantidadeCtp;

    @Column(name = "calculado_em", nullable = false)
    private LocalDateTime calculadoEm;
}
