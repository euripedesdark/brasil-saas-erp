package br.com.brasil_saas.wms.model;
import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*; import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="bc_wms_onda", schema="brasil_saas") @Getter @Setter
public class WmsOnda extends TenantEntity {
    @Column(name="deposito_id", nullable=false) private Long depositoId;
    @Column(nullable=false, length=60) private String codigo;
    @Column(nullable=false, length=20) private String status = "ABERTA";
    @Column(length=200) private String responsavel;
    @Column(name="liberada_em") private LocalDateTime liberadaEm;
    @Column(name="concluida_em") private LocalDateTime concluidaEm;
}
