package br.com.brasil_saas.contabilidade.model;

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
import java.time.LocalDateTime;

@Entity
@Table(name = "bc_cont_fechamento_check", schema = "brasil_saas")
@Getter
@Setter
public class FechamentoCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "periodo_id", nullable = false)
    private Long periodoId;

    @Column(name = "etapa", length = 50, nullable = false)
    private String etapa;

    @Column(name = "codigo", length = 80, nullable = false)
    private String codigo;

    @Column(name = "descricao", length = 300, nullable = false)
    private String descricao;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDENTE";

    @Column(name = "quantidade", precision = 18, scale = 2)
    private BigDecimal quantidade;

    @Column(name = "executado_em")
    private LocalDateTime executadoEm;

    @Column(name = "executado_por")
    private Long executadoPor;

    @Column(name = "observacao", length = 1000)
    private String observacao;
}
