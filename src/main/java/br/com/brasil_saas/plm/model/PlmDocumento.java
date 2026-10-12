package br.com.brasil_saas.plm.model;

import br.com.brasil_saas.shared.web.MapValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "bc_plm_documento", schema = "brasil_saas")
@Getter
@Setter
public class PlmDocumento {

    private static final Set<String> BLOQUEADOS = Set.of(
            "id", "empresa_id", "created_at", "updated_at", "deleted_at");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "revisao_id")
    private Long revisaoId;

    @Column(name = "mudanca_id")
    private Long mudancaId;

    @Column(name = "tipo", length = 40, nullable = false)
    private String tipo;

    @Column(name = "codigo", length = 100, nullable = false)
    private String codigo;

    @Column(name = "versao", length = 30, nullable = false)
    private String versao = "1";

    @Column(name = "nome", length = 300, nullable = false)
    private String nome;

    @Column(name = "localizacao", length = 1000)
    private String localizacao;

    @Column(name = "hash_documento", length = 128)
    private String hashDocumento;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "RASCUNHO";

    @Column(name = "obrigatorio", nullable = false)
    private Boolean obrigatorio = false;

    @Column(name = "aprovado_por")
    private Long aprovadoPor;

    @Column(name = "aprovado_em")
    private LocalDateTime aprovadoEm;

    @Column(name = "vigencia_inicio")
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = agora;
        }
        updatedAt = agora;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void aplicar(Map<String, Object> b) {
        for (var e : b.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = e.getValue();
            switch (k) {
                case "revisao_id" -> setRevisaoId(MapValues.lng(v));
                case "mudanca_id" -> setMudancaId(MapValues.lng(v));
                case "tipo" -> setTipo(MapValues.str(v));
                case "codigo" -> setCodigo(MapValues.str(v));
                case "versao" -> setVersao(MapValues.str(v));
                case "nome" -> setNome(MapValues.str(v));
                case "localizacao" -> setLocalizacao(MapValues.str(v));
                case "hash_documento" -> setHashDocumento(MapValues.str(v));
                case "status" -> setStatus(MapValues.str(v));
                case "obrigatorio" -> { Boolean x = MapValues.bool(v); if (x != null) setObrigatorio(x); }
                case "aprovado_por" -> setAprovadoPor(MapValues.lng(v));
                case "aprovado_em" -> setAprovadoEm(MapValues.dtTime(v));
                case "vigencia_inicio" -> setVigenciaInicio(MapValues.dt(v));
                case "vigencia_fim" -> setVigenciaFim(MapValues.dt(v));
                default -> { }
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("empresa_id", empresaId);
        m.put("revisao_id", revisaoId); m.put("mudanca_id", mudancaId);
        m.put("tipo", tipo); m.put("codigo", codigo); m.put("versao", versao);
        m.put("nome", nome); m.put("localizacao", localizacao); m.put("hash_documento", hashDocumento);
        m.put("status", status); m.put("obrigatorio", obrigatorio);
        m.put("aprovado_por", aprovadoPor); m.put("aprovado_em", aprovadoEm);
        m.put("vigencia_inicio", vigenciaInicio); m.put("vigencia_fim", vigenciaFim);
        m.put("created_at", createdAt); m.put("updated_at", updatedAt); m.put("deleted_at", deletedAt);
        return m;
    }
}
