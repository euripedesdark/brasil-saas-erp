package br.com.brasil_saas.fiscal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bc_fis_issqn", schema = "brasil_saas")
@Getter @Setter
public class Issqn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String descricao;

    @Column(name = "aliquota", precision = 7, scale = 4)
    private BigDecimal aliquota;

    @Column(name = "cod_ibge", length = 7)
    private String codIbge;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uf", length = 2)
    private String uf;

    @Column(length = 150)
    private String municipio;

    @Column(name = "codigo_municipal", length = 20)
    private String codigoMunicipal;

    @Column(name = "vigencia")
    private LocalDate vigencia;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
