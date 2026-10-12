package br.com.brasil_saas.enterprise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;

@Entity
@Table(name = "bc_grc_teste_controle", schema = "brasil_saas")
@Getter
@Setter
public class GrcTesteControle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "controle_id", nullable = false)
    private Long controleId;

    @Column(name = "periodo", length = 7, nullable = false)
    private String periodo;

    @Column(name = "resultado", length = 30, nullable = false)
    private String resultado = "PENDENTE";

    @Column(name = "observacao", length = 1000)
    private String observacao;

    @Column(name = "testado_por", length = 160)
    private String testadoPor;

    @Column(name = "testado_em")
    private LocalDateTime testadoEm;
}
