package br.com.brasil_saas.financeiro.model;

import br.com.brasil_saas.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bc_fin_intercompany", schema = "brasil_saas")
@Getter
@Setter
public class IntercompanyLancamento extends AuditableEntity {

    @Column(name = "empresa_parceira_id", nullable = false)
    private Long empresaParceiraId;

    @Column(name = "numero", length = 80, nullable = false)
    private String numero;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo = "LANCAMENTO";

    @Column(name = "data_documento", nullable = false)
    private LocalDate dataDocumento;

    @Column(name = "competencia")
    private LocalDate competencia;

    @Column(name = "descricao", length = 500, nullable = false)
    private String descricao;

    @Column(name = "valor", precision = 18, scale = 2, nullable = false)
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "moeda", length = 3, nullable = false)
    private String moeda = "BRL";

    @Column(name = "status", length = 25, nullable = false)
    private String status = "ABERTO";

    @Column(name = "reconciliado", nullable = false)
    private Boolean reconciliado = false;

    @Column(name = "documento_origem_tipo", length = 50)
    private String documentoOrigemTipo;

    @Column(name = "documento_origem_id")
    private Long documentoOrigemId;

    @Column(name = "observacao", columnDefinition = "text")
    private String observacao;

    @Column(name = "contrapartida_id")
    private Long contrapartidaId;

    @Column(name = "diferenca", precision = 18, scale = 2)
    private BigDecimal diferenca;

    @Column(name = "reconciliado_em")
    private LocalDateTime reconciliadoEm;
}
