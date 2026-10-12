package br.com.brasil_saas.vendas.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bc_ven_tabela_preco", schema = "brasil_saas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TabelaPreco extends TenantEntity {
    @Column(nullable=false, length=100) private String nome;
    @Column(length=30) private String codigo;
    @Column private String descricao;
    @Column(length=3, nullable=false) private String moeda = "BRL";
    @Column(name="vigencia_inicio") private LocalDate vigenciaInicio;
    @Column(name="vigencia_fim") private LocalDate vigenciaFim;
    @Column(name="percentual_desconto_maximo", precision=7, scale=4, nullable=false)
    private BigDecimal percentualDescontoMaximo = BigDecimal.ZERO;
    @Column(nullable=false) private Boolean ativo = true;
}