package br.com.brasil_saas.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Retorno CNAB lido do banco. Ver migration V90. */
@Entity
@Table(name = "bc_fin_retorno_bancario", schema = "brasil_saas")
@Getter
@Setter
public class RetornoBancario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "conta_bancaria_id")
    private Long contaBancariaId;

    @Column(name = "banco", length = 10, nullable = false)
    private String banco;

    @Column(name = "tipo", length = 10, nullable = false)
    private String tipo = "cnab240";

    @Column(name = "nome_arquivo")
    private String nomeArquivo;

    @Column(name = "data_arquivo")
    private LocalDate dataArquivo;

    @Column(name = "qtde_registros", nullable = false)
    private Integer qtdeRegistros = 0;

    @Column(name = "qtde_baixados", nullable = false)
    private Integer qtdeBaixados = 0;

    @Column(name = "qtde_divergentes", nullable = false)
    private Integer qtdeDivergentes = 0;

    @Column(name = "valor_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "PROCESSADO";

    @Column(name = "documento_id", length = 64)
    private String documentoId;

    @Column(name = "resumo_json", columnDefinition = "text")
    private String resumoJson;

    @Column(name = "erro", columnDefinition = "text")
    private String erro;

    @Column(name = "criado_em", insertable = false, updatable = false)
    @Immutable
    private LocalDateTime criadoEm;
}
