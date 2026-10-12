package br.com.brasil_saas.supplychain.model;

import br.com.brasil_saas.shared.model.AuditableEntity;
import br.com.brasil_saas.shared.web.MapValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "bc_scm_ordem_transporte", schema = "brasil_saas")
@Getter
@Setter
public class ScmOrdemTransporte extends AuditableEntity {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @Column(name = "numero", length = 80, nullable = false)
    private String numero;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo = "ENTREGA";

    @Column(name = "status", length = 25, nullable = false)
    private String status = "PLANEJADA";

    @Column(name = "origem", length = 200)
    private String origem;

    @Column(name = "destino", length = 200)
    private String destino;

    @Column(name = "transportadora_id")
    private Long transportadoraId;

    @Column(name = "veiculo", length = 100)
    private String veiculo;

    @Column(name = "motorista", length = 160)
    private String motorista;

    @Column(name = "data_prevista")
    private LocalDate dataPrevista;

    @Column(name = "data_saida")
    private LocalDate dataSaida;

    @Column(name = "data_entrega")
    private LocalDate dataEntrega;

    @Column(name = "valor_frete", precision = 18, scale = 2, nullable = false)
    private BigDecimal valorFrete = BigDecimal.ZERO;

    @Column(name = "peso", precision = 18, scale = 3)
    private BigDecimal peso;

    @Column(name = "volume", precision = 18, scale = 3)
    private BigDecimal volume;

    @Column(name = "observacao", columnDefinition = "text")
    private String observacao;

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "numero" -> setNumero(MapValues.str(v));
                case "tipo" -> setTipo(MapValues.str(v));
                case "status" -> setStatus(MapValues.str(v));
                case "origem" -> setOrigem(MapValues.str(v));
                case "destino" -> setDestino(MapValues.str(v));
                case "transportadora_id" -> setTransportadoraId(MapValues.lng(v));
                case "veiculo" -> setVeiculo(MapValues.str(v));
                case "motorista" -> setMotorista(MapValues.str(v));
                case "data_prevista" -> setDataPrevista(MapValues.dt(v));
                case "data_saida" -> setDataSaida(MapValues.dt(v));
                case "data_entrega" -> setDataEntrega(MapValues.dt(v));
                case "valor_frete" -> { BigDecimal x = MapValues.bd(v); if (x != null) setValorFrete(x); }
                case "peso" -> setPeso(MapValues.bd(v));
                case "volume" -> setVolume(MapValues.bd(v));
                case "observacao" -> setObservacao(MapValues.str(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("uuid", getUuid()); m.put("empresa_id", getEmpresaId());
        m.put("numero", numero); m.put("tipo", tipo); m.put("status", status);
        m.put("origem", origem); m.put("destino", destino);
        m.put("transportadora_id", transportadoraId);
        m.put("veiculo", veiculo); m.put("motorista", motorista);
        m.put("data_prevista", dataPrevista); m.put("data_saida", dataSaida); m.put("data_entrega", dataEntrega);
        m.put("valor_frete", valorFrete); m.put("peso", peso); m.put("volume", volume);
        m.put("observacao", observacao);
        m.put("created_at", getCreatedAt()); m.put("updated_at", getUpdatedAt());
        m.put("created_by", getCreatedBy()); m.put("updated_by", getUpdatedBy());
        m.put("deleted_at", getDeletedAt());
        return m;
    }
}
