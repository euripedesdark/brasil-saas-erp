package br.com.brasil_saas.wms.model;
import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*; import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="bc_wms_volume", schema="brasil_saas") @Getter @Setter
public class WmsVolume extends TenantEntity {
    @Column(name="expedicao_id", nullable=false) private Long expedicaoId;
    @Column(nullable=false, length=60) private String codigo;
    @Column(precision=15, scale=3) private BigDecimal peso;
    @Column(nullable=false, length=20) private String status = "ABERTO";
}
