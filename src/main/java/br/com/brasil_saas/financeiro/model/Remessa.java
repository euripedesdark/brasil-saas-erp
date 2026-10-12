package br.com.brasil_saas.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Remessa CNAB gerada e enviada ao banco. Ver migration V90. */
@Entity
@Table(name = "bc_fin_remessa", schema = "brasil_saas")
@Getter
@Setter
public class Remessa {

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

    @Column(name = "numero_sequencial", length = 20)
    private String numeroSequencial;

    // preenchido em Java: o DEFAULT do banco so vale quando a coluna nao entra
    // no INSERT, e o Hibernate sempre a inclui mesmo com null
    @Column(name = "data_geracao", nullable = false)
    private LocalDate dataGeracao = LocalDate.now();

    @Column(name = "data_credito")
    private LocalDate dataCredito;

    @Column(name = "qtde_titulos", nullable = false)
    private Integer qtdeTitulos = 0;

    @Column(name = "valor_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "GERADA";

    @Column(name = "documento_id", length = 64)
    private String documentoId;

    @Column(name = "documento_hash", length = 64)
    private String documentoHash;

    @Column(name = "nome_arquivo")
    private String nomeArquivo;

    @Column(name = "criado_em", insertable = false, updatable = false)
    @Immutable
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
