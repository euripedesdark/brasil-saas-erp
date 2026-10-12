package br.com.brasil_saas.contabilidade.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "bc_cont_regra_lancamento", schema = "brasil_saas")
@Getter
@Setter
public class RegraLancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "codigo", length = 60, nullable = false)
    private String codigo;

    @Column(name = "nome", length = 200, nullable = false)
    private String nome;

    @Column(name = "origem", length = 50, nullable = false)
    private String origem;

    @Column(name = "conta_debito_id")
    private Long contaDebitoId;

    @Column(name = "conta_credito_id")
    private Long contaCreditoId;

    @Column(name = "centro_custo_id")
    private Long centroCustoId;

    @Column(name = "historico", length = 500)
    private String historico;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuracao", columnDefinition = "jsonb", nullable = false)
    private String configuracao = "{}";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
