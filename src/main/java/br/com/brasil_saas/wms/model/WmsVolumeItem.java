package br.com.brasil_saas.wms.model;
import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*; import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="bc_wms_volume_item", schema="brasil_saas") @Getter @Setter
public class WmsVolumeItem extends TenantEntity {
    @Column(name="volume_id", nullable=false) private Long volumeId;
    @Column(name="produto_id", nullable=false) private Long produtoId;
    @Column(nullable=false, precision=15, scale=3) private BigDecimal quantidade;
    @Column(name="onda_item_id") private Long ondaItemId;
}
