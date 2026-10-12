package br.com.brasil_saas.qualidade.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bc_qual_plano_inspecao", schema = "brasil_saas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PlanoInspecao extends TenantEntity {
    @Column(nullable=false, length=50) private String codigo;
    @Column(nullable=false) private String descricao;
    @Column(nullable=false, length=30) private String tipo;
    @Column(nullable=false) private Boolean ativo = true;
}
