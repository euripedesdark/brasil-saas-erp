package br.com.brasil_saas.enterprise.controller;

import br.com.brasil_saas.contabilidade.model.PeriodoContabilEnt;
import br.com.brasil_saas.contabilidade.repository.PeriodoContabilEntRepository;
import br.com.brasil_saas.enterprise.model.CenarioTributario;
import br.com.brasil_saas.enterprise.model.EntContrato;
import br.com.brasil_saas.enterprise.model.EntOrcamento;
import br.com.brasil_saas.enterprise.model.FornecedorQualificacao;
import br.com.brasil_saas.enterprise.model.MetaComercial;
import br.com.brasil_saas.enterprise.model.TesourariaPrevisao;
import br.com.brasil_saas.enterprise.repository.CenarioTributarioRepository;
import br.com.brasil_saas.enterprise.repository.EntContratoRepository;
import br.com.brasil_saas.enterprise.repository.EntOrcamentoRepository;
import br.com.brasil_saas.enterprise.repository.FornecedorQualificacaoRepository;
import br.com.brasil_saas.enterprise.repository.MetaComercialRepository;
import br.com.brasil_saas.enterprise.repository.TesourariaPrevisaoRepository;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class EnterpriseOperationsController {
    private final EntContratoRepository contratoRepository;
    private final FornecedorQualificacaoRepository qualificacaoRepository;
    private final MetaComercialRepository metaRepository;
    private final PeriodoContabilEntRepository periodoRepository;
    private final EntOrcamentoRepository orcamentoRepository;
    private final TesourariaPrevisaoRepository tesourariaRepository;
    private final CenarioTributarioRepository cenarioRepository;

    private static final Set<String> RECURSOS = Set.of(
        "contratos", "qualificacoes", "metas", "periodos", "orcamentos", "tesouraria", "tributacao");

    private static final Set<String> BLOQUEADOS = Set.of(
        "id", "uuid", "empresa_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at");

    @GetMapping("/{resource:contratos|qualificacoes|metas|periodos|orcamentos|tesouraria|tributacao}")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> listar(@AuthenticationPrincipal AuthenticatedUser u,
                                            @PathVariable String resource) {
        validar(resource);
        Long empresa = u.getEmpresaId();
        return switch (resource) {
            case "contratos" -> contratoRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
                    .stream().map(EntContrato::toMap).toList();
            case "qualificacoes" -> qualificacaoRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
                    .stream().map(FornecedorQualificacao::toMap).toList();
            case "metas" -> metaRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
                    .stream().map(MetaComercial::toMap).toList();
            case "periodos" -> periodoRepository.findAllByEmpresaIdOrderByIdDesc(empresa)
                    .stream().map(EnterpriseOperationsController::periodoMap).toList();
            case "orcamentos" -> orcamentoRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
                    .stream().map(EntOrcamento::toMap).toList();
            case "tesouraria" -> tesourariaRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
                    .stream().map(TesourariaPrevisao::toMap).toList();
            case "tributacao" -> cenarioRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
                    .stream().map(CenarioTributario::toMap).toList();
            default -> throw new IllegalArgumentException("Recurso empresarial inválido");
        };
    }

    @PostMapping("/{resource:contratos|qualificacoes|metas|periodos|orcamentos|tesouraria|tributacao}")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> criar(@AuthenticationPrincipal AuthenticatedUser u,
                                    @PathVariable String resource,
                                    @RequestBody Map<String,Object> body) {
        validar(resource);
        body = somentePreenchidos(body);
        if (!temCampoUtil(body)) {
            throw new IllegalArgumentException("Nenhum campo informado");
        }
        Long empresa = u.getEmpresaId();
        return switch (resource) {
            case "contratos" -> {
                EntContrato e = new EntContrato();
                e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
                yield contratoRepository.save(e).toMap();
            }
            case "qualificacoes" -> {
                FornecedorQualificacao e = new FornecedorQualificacao();
                e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
                yield qualificacaoRepository.save(e).toMap();
            }
            case "metas" -> {
                MetaComercial e = new MetaComercial();
                e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
                yield metaRepository.save(e).toMap();
            }
            case "periodos" -> {
                PeriodoContabilEnt e = new PeriodoContabilEnt();
                e.setEmpresaId(empresa); e.setCreatedBy(u.getId());
                aplicarPeriodo(e, body);
                yield periodoRepository.save(e).toMap();
            }
            case "orcamentos" -> {
                EntOrcamento e = new EntOrcamento();
                e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
                yield orcamentoRepository.save(e).toMap();
            }
            case "tesouraria" -> {
                TesourariaPrevisao e = new TesourariaPrevisao();
                e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
                yield tesourariaRepository.save(e).toMap();
            }
            case "tributacao" -> {
                CenarioTributario e = new CenarioTributario();
                e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
                yield cenarioRepository.save(e).toMap();
            }
            default -> throw new IllegalArgumentException("Recurso empresarial inválido");
        };
    }

    @PutMapping("/{resource:contratos|qualificacoes|metas|periodos|orcamentos|tesouraria|tributacao}/{id}")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> atualizar(@AuthenticationPrincipal AuthenticatedUser u,
                                         @PathVariable String resource,
                                         @PathVariable Long id,
                                         @RequestBody Map<String,Object> body) {
        validar(resource);
        body = somentePreenchidos(body);
        if (!temCampoUtil(body)) {
            throw new IllegalArgumentException("Nenhum campo informado");
        }
        Long empresa = u.getEmpresaId();
        return switch (resource) {
            case "contratos" -> {
                EntContrato e = contratoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.aplicar(body); e.setUpdatedBy(u.getId());
                yield contratoRepository.save(e).toMap();
            }
            case "qualificacoes" -> {
                FornecedorQualificacao e = qualificacaoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.aplicar(body); e.setUpdatedBy(u.getId());
                yield qualificacaoRepository.save(e).toMap();
            }
            case "metas" -> {
                MetaComercial e = metaRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.aplicar(body); e.setUpdatedBy(u.getId());
                yield metaRepository.save(e).toMap();
            }
            case "periodos" -> {
                PeriodoContabilEnt e = periodoRepository.findByIdAndEmpresaId(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                aplicarPeriodo(e, body); e.setUpdatedBy(u.getId());
                yield periodoRepository.save(e).toMap();
            }
            case "orcamentos" -> {
                EntOrcamento e = orcamentoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.aplicar(body); e.setUpdatedBy(u.getId());
                yield orcamentoRepository.save(e).toMap();
            }
            case "tesouraria" -> {
                TesourariaPrevisao e = tesourariaRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.aplicar(body); e.setUpdatedBy(u.getId());
                yield tesourariaRepository.save(e).toMap();
            }
            case "tributacao" -> {
                CenarioTributario e = cenarioRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.aplicar(body); e.setUpdatedBy(u.getId());
                yield cenarioRepository.save(e).toMap();
            }
            default -> throw new IllegalArgumentException("Recurso empresarial inválido");
        };
    }

    @DeleteMapping("/{resource:contratos|qualificacoes|metas|periodos|orcamentos|tesouraria|tributacao}/{id}")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public void excluir(@AuthenticationPrincipal AuthenticatedUser u,
                        @PathVariable String resource, @PathVariable Long id) {
        validar(resource);
        Long empresa = u.getEmpresaId();
        LocalDateTime agora = LocalDateTime.now();
        switch (resource) {
            case "contratos" -> {
                EntContrato e = contratoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.setDeletedAt(agora); e.setUpdatedBy(u.getId());
                contratoRepository.save(e);
            }
            case "qualificacoes" -> {
                FornecedorQualificacao e = qualificacaoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.setDeletedAt(agora); e.setUpdatedBy(u.getId());
                qualificacaoRepository.save(e);
            }
            case "metas" -> {
                MetaComercial e = metaRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.setDeletedAt(agora); e.setUpdatedBy(u.getId());
                metaRepository.save(e);
            }
            case "periodos" -> {
                // bc_ent_periodo_contabil não tem deleted_at: exclusão física.
                PeriodoContabilEnt e = periodoRepository.findByIdAndEmpresaId(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                periodoRepository.delete(e);
            }
            case "orcamentos" -> {
                EntOrcamento e = orcamentoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.setDeletedAt(agora); e.setUpdatedBy(u.getId());
                orcamentoRepository.save(e);
            }
            case "tesouraria" -> {
                TesourariaPrevisao e = tesourariaRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.setDeletedAt(agora); e.setUpdatedBy(u.getId());
                tesourariaRepository.save(e);
            }
            case "tributacao" -> {
                CenarioTributario e = cenarioRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
                        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
                e.setDeletedAt(agora); e.setUpdatedBy(u.getId());
                cenarioRepository.save(e);
            }
            default -> throw new IllegalArgumentException("Recurso empresarial inválido");
        }
    }

    @PostMapping("/periodos/{id}/fechar")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> fecharPeriodo(@AuthenticationPrincipal AuthenticatedUser u,
                                             @PathVariable Long id,
                                             @RequestParam(defaultValue="contabil") String etapa) {
        boolean financeiro, fiscal, contabil;
        switch (etapa.toLowerCase(Locale.ROOT)) {
            case "financeiro" -> { financeiro = true; fiscal = false; contabil = false; }
            case "fiscal" -> { financeiro = false; fiscal = true; contabil = false; }
            case "contabil" -> { financeiro = false; fiscal = false; contabil = true; }
            default -> throw new IllegalArgumentException("Etapa de fechamento inválida");
        }
        PeriodoContabilEnt p = periodoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
                .orElseThrow(() -> new NoSuchElementException("Período não encontrado"));
        if (financeiro) {
            p.setFechamentoFinanceiro(true);
        }
        if (fiscal) {
            p.setFechamentoFiscal(true);
        }
        if (contabil) {
            p.setFechamentoContabil(true);
        }
        boolean fechado = Boolean.TRUE.equals(p.getFechamentoFinanceiro())
                && Boolean.TRUE.equals(p.getFechamentoFiscal())
                && Boolean.TRUE.equals(p.getFechamentoContabil());
        p.setStatus(fechado ? "FECHADO" : "EM_FECHAMENTO");
        LocalDateTime agora = LocalDateTime.now();
        p.setFechadoEm(agora);
        p.setFechadoPor(u.getId());
        p.setUpdatedBy(u.getId());
        periodoRepository.save(p);
        return periodoMap(periodoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
                .orElseThrow(() -> new NoSuchElementException("Período não encontrado")));
    }

    private void validar(String resource) {
        if (!RECURSOS.contains(resource)) {
            throw new IllegalArgumentException("Recurso empresarial inválido");
        }
    }

    private Map<String, Object> somentePreenchidos(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (body == null) {
            return out;
        }
        body.forEach((k, v) -> {
            if (v == null) {
                return;
            }
            if (v instanceof String s && s.isBlank()) {
                return;
            }
            out.put(k, v);
        });
        return out;
    }

    private boolean temCampoUtil(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return body.keySet().stream()
                .anyMatch(k -> k != null && k.matches("[a-z][a-z0-9_]*") && !BLOQUEADOS.contains(k));
    }

    private void aplicarPeriodo(PeriodoContabilEnt e, Map<String, Object> b) {
        for (var entry : b.entrySet()) {
            String k = entry.getKey();
            if (k == null || !k.matches("[a-z][a-z0-9_]*") || BLOQUEADOS.contains(k)) {
                continue;
            }
            Object v = entry.getValue();
            switch (k) {
                case "competencia" -> e.setCompetencia(br.com.brasil_saas.shared.web.MapValues.dt(v));
                case "status" -> e.setStatus(br.com.brasil_saas.shared.web.MapValues.str(v));
                case "fechamento_financeiro" -> {
                    Boolean x = br.com.brasil_saas.shared.web.MapValues.bool(v);
                    if (x != null) {
                        e.setFechamentoFinanceiro(x);
                    }
                }
                case "fechamento_fiscal" -> {
                    Boolean x = br.com.brasil_saas.shared.web.MapValues.bool(v);
                    if (x != null) {
                        e.setFechamentoFiscal(x);
                    }
                }
                case "fechamento_contabil" -> {
                    Boolean x = br.com.brasil_saas.shared.web.MapValues.bool(v);
                    if (x != null) {
                        e.setFechamentoContabil(x);
                    }
                }
                case "observacao" -> e.setObservacao(br.com.brasil_saas.shared.web.MapValues.str(v));
                case "fechado_em" -> e.setFechadoEm(br.com.brasil_saas.shared.web.MapValues.dtTime(v));
                case "fechado_por" -> e.setFechadoPor(br.com.brasil_saas.shared.web.MapValues.lng(v));
                default -> { }
            }
        }
    }

    private static Map<String, Object> periodoMap(PeriodoContabilEnt p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId()); m.put("uuid", p.getUuid()); m.put("empresa_id", p.getEmpresaId());
        m.put("competencia", p.getCompetencia()); m.put("status", p.getStatus());
        m.put("fechamento_financeiro", p.getFechamentoFinanceiro());
        m.put("fechamento_fiscal", p.getFechamentoFiscal());
        m.put("fechamento_contabil", p.getFechamentoContabil());
        m.put("observacao", p.getObservacao());
        m.put("fechado_em", p.getFechadoEm()); m.put("fechado_por", p.getFechadoPor());
        m.put("created_at", p.getCreatedAt()); m.put("updated_at", p.getUpdatedAt());
        m.put("created_by", p.getCreatedBy()); m.put("updated_by", p.getUpdatedBy());
        return m;
    }
}
