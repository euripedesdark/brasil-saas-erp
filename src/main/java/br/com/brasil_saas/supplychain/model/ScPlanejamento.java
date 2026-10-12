package br.com.brasil_saas.supplychain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bc_sc_planejamento", schema = "brasil_saas")
@Getter
@Setter
public class ScPlanejamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "codigo", length = 80, nullable = false)
    private String codigo;

    @Column(name = "periodo_inicio", nullable = false)
    private LocalDate periodoInicio;

    @Column(name = "periodo_fim", nullable = false)
    private LocalDate periodoFim;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo;

    @Column(name = "status", length = 25, nullable = false)
    private String status = "RASCUNHO";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parametros", columnDefinition = "jsonb")
    private String parametros = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultado", columnDefinition = "jsonb")
    private String resultado = "{}";

    @Column(name = "executado_em")
    private LocalDateTime executadoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
