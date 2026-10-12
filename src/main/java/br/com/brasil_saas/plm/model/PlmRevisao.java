package br.com.brasil_saas.plm.model;

import jakarta.persistence.*;
import br.com.brasil_saas.shared.web.MapValues;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "bc_plm_produto_revisao", schema = "brasil_saas")
@Getter @Setter @NoArgsConstructor
public class PlmRevisao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "produto_id", nullable = false) private Long produtoId;
    @Column(nullable = false, length = 30) private String revisao;
    @Column(length = 500) private String descricao;
    @Column(nullable = false, length = 25) private String status = "EMDESENV";
    @Column(name = "vigente_desde") private LocalDate vigenteDesde;
    @Column(name = "vigente_ate") private LocalDate vigenteAte;
    @Column(length = 500) private String motivo;
    @Column(name = "documento_id", length = 120) private String documentoId;
    @Column(name = "criado_por") private Long criadoPor;
    @Column(name = "aprovado_por") private Long aprovadoPor;
    @Column(name = "aprovado_em") private LocalDateTime aprovadoEm;

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "produto_id" -> setProdutoId(MapValues.lng(v));
                case "revisao" -> setRevisao(MapValues.str(v));
                case "descricao" -> setDescricao(MapValues.str(v));
                case "status" -> setStatus(MapValues.str(v));
                case "vigente_desde" -> setVigenteDesde(MapValues.dt(v));
                case "vigente_ate" -> setVigenteAte(MapValues.dt(v));
                case "motivo" -> setMotivo(MapValues.str(v));
                case "documento_id" -> setDocumentoId(MapValues.str(v));
                case "criado_por" -> setCriadoPor(MapValues.lng(v));
                case "aprovado_por" -> setAprovadoPor(MapValues.lng(v));
                case "aprovado_em" -> setAprovadoEm(MapValues.dtTime(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("empresa_id", getEmpresaId());
        m.put("produto_id", getProdutoId()); m.put("revisao", getRevisao());
        m.put("descricao", getDescricao()); m.put("status", getStatus());
        m.put("vigente_desde", getVigenteDesde()); m.put("vigente_ate", getVigenteAte());
        m.put("motivo", getMotivo()); m.put("documento_id", getDocumentoId());
        m.put("criado_por", getCriadoPor());
        m.put("aprovado_por", getAprovadoPor()); m.put("aprovado_em", getAprovadoEm());
        return m;
    }
}
