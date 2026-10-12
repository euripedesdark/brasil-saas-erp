package br.com.brasil_saas.financeiro.model;

import br.com.brasil_saas.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;

@Entity
@Table(name = "bc_fin_titulo", schema = "brasil_saas")
@Getter @Setter @NoArgsConstructor
public class Titulo extends AuditableEntity {
    @JdbcTypeCode(Types.CHAR)
    @Column(nullable = false, length = 1) private String tipo;
    @Column(name = "numero_documento", length = 100) private String numeroDocumento;
    @Column(nullable = false) private String descricao;
    @Column(name = "pessoa_id") private Long pessoaId;
    @Column(name = "valor_original", nullable = false, precision = 15, scale = 2) private BigDecimal valorOriginal;
    @Column(name = "valor_saldo", nullable = false, precision = 15, scale = 2) private BigDecimal valorSaldo;
    @Column(name = "data_emissao", nullable = false) private LocalDate dataEmissao;
    @Column(name = "data_vencimento", nullable = false) private LocalDate dataVencimento;
    @Column(nullable = false) private String status = "ABERTO";
    @Column(name = "centro_custo_id") private Long centroCustoId;
    @Column(name = "plano_contas_id") private Long planoContasId;
}
