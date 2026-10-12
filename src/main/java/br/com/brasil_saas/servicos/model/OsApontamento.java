package br.com.brasil_saas.servicos.model;
import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*; import lombok.*; import java.math.BigDecimal; import java.time.LocalDateTime;
@Entity @Table(name="bc_srv_os_apontamento", schema="brasil_saas") @Getter @Setter
public class OsApontamento extends TenantEntity {
    @Column(name="os_id", nullable=false) private Long osId;
    @Column(name="usuario_id", nullable=false) private Long usuarioId;
    @Column(name="data_apontamento", nullable=false) private LocalDateTime dataApontamento;
    @Column(nullable=false, precision=7, scale=2) private BigDecimal horas;
    @Column private String descricao;
}
