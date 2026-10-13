package br.com.brasil_saas.enterprise.service;

import br.com.brasil_saas.financeiro.model.IntercompanyLancamento;
import br.com.brasil_saas.financeiro.repository.IntercompanyLancamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Reconcilia o lançamento da empresa com o lançamento espelho da parceira (mesmo número, partes invertidas).
 * Só grava na linha da própria empresa; da parceira apenas lê valor e moeda.
 */
@Service
@RequiredArgsConstructor
public class IntercompanyService {
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    private final IntercompanyLancamentoRepository repository;

    @Transactional
    public Map<String, Object> reconciliar(Long empresaId, Long userId, Long id) {
        IntercompanyLancamento meu = repository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new NoSuchElementException("Lançamento intercompany não encontrado"));
        Long parceira = meu.getEmpresaParceiraId();
        List<IntercompanyLancamento> espelhos = repository.findEspelho(
                parceira, empresaId, meu.getNumero(), meu.getCompetencia());
        IntercompanyLancamento par = espelhos.isEmpty() ? null : espelhos.get(0);

        var r = IntercompanyConciliacao.comparar(meu.getValor(), String.valueOf(meu.getMoeda()),
                par == null ? null : par.getValor(), par == null ? null : String.valueOf(par.getMoeda()),
                TOLERANCIA);
        meu.setStatus(r.status());
        meu.setReconciliado(r.reconciliado());
        meu.setDiferenca(r.diferenca());
        meu.setContrapartidaId(par == null ? null : par.getId());
        meu.setReconciliadoEm(r.reconciliado() ? java.time.LocalDateTime.now() : null);
        meu.setUpdatedBy(userId);
        repository.save(meu);
        return lancamentoMap(meu);
    }

    /** Linhas reconciliadas do mês; totais segregados por moeda, sem conversão implícita. */
    @Transactional(readOnly = true)
    public Map<String, Object> eliminacoes(Long empresaId, java.time.LocalDate competencia) {
        java.time.LocalDate inicio = competencia.withDayOfMonth(1);
        List<IntercompanyLancamento> lancamentos = repository
                .findByEmpresaIdAndReconciliadoTrueAndDeletedAtIsNullAndCompetenciaGreaterThanEqualAndCompetenciaLessThanOrderByNumero(
                        empresaId, inicio, inicio.plusMonths(1));
        List<Map<String, Object>> linhas = lancamentos.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("numero", l.getNumero());
            m.put("empresa_parceira_id", l.getEmpresaParceiraId());
            m.put("valor", l.getValor());
            m.put("moeda", l.getMoeda());
            m.put("status", l.getStatus());
            m.put("reconciliado", l.getReconciliado());
            m.put("diferenca", l.getDiferenca());
            m.put("competencia", l.getCompetencia());
            return m;
        }).toList();
        Map<String, BigDecimal> totaisPorMoeda = new LinkedHashMap<>();
        for (Map<String, Object> linha : linhas) {
            BigDecimal valor = (BigDecimal) linha.get("valor");
            String moeda = String.valueOf(linha.get("moeda")).trim();
            totaisPorMoeda.merge(moeda, valor == null ? BigDecimal.ZERO : valor, BigDecimal::add);
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("linhas", linhas);
        resultado.put("totaisPorMoeda", totaisPorMoeda);
        // Mantém o contrato antigo somente quando há uma unidade monetária única.
        resultado.put("total", totaisPorMoeda.size() > 1 ? null :
                totaisPorMoeda.values().stream().findFirst().orElse(BigDecimal.ZERO));
        resultado.put("competencia", inicio.toString());
        return resultado;
    }

    private static Map<String, Object> lancamentoMap(IntercompanyLancamento l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("uuid", l.getUuid());
        m.put("empresa_id", l.getEmpresaId());
        m.put("empresa_parceira_id", l.getEmpresaParceiraId());
        m.put("numero", l.getNumero());
        m.put("tipo", l.getTipo());
        m.put("data_documento", l.getDataDocumento());
        m.put("competencia", l.getCompetencia());
        m.put("descricao", l.getDescricao());
        m.put("valor", l.getValor());
        m.put("moeda", l.getMoeda());
        m.put("status", l.getStatus());
        m.put("reconciliado", l.getReconciliado());
        m.put("documento_origem_tipo", l.getDocumentoOrigemTipo());
        m.put("documento_origem_id", l.getDocumentoOrigemId());
        m.put("observacao", l.getObservacao());
        m.put("contrapartida_id", l.getContrapartidaId());
        m.put("diferenca", l.getDiferenca());
        m.put("reconciliado_em", l.getReconciliadoEm());
        m.put("created_at", l.getCreatedAt());
        m.put("updated_at", l.getUpdatedAt());
        m.put("created_by", l.getCreatedBy());
        m.put("updated_by", l.getUpdatedBy());
        m.put("deleted_at", l.getDeletedAt());
        return m;
    }
}
