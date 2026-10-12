package br.com.brasil_saas.bi.service.impl;

import br.com.brasil_saas.bi.dto.IndicadorRequest;
import br.com.brasil_saas.bi.model.Indicador;
import br.com.brasil_saas.bi.repository.IndicadorRepository;
import br.com.brasil_saas.bi.service.IndicadorService;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndicadorServiceImpl implements IndicadorService {

    private final IndicadorRepository indicadorRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public Indicador criar(Long empresaId, IndicadorRequest request) {
        Indicador indicador = new Indicador();
        indicador.setEmpresaId(empresaId);
        indicador.setNome(request.nome());
        indicador.setDescricao(request.descricao());
        indicador.setCategoria(request.categoria());
        indicador.setFormula(request.formula());
        indicador.setValorMeta(request.valorMeta());
        indicador.setUnidadeMedida(request.unidadeMedida());
        indicador.setCorValorBaixo(request.corValorBaixo());
        indicador.setCorValorMedio(request.corValorMedio());
        indicador.setCorValorAlto(request.corValorAlto());
        indicador.setAtivo(request.ativo());
        indicador.setFrequenciaAtualizacao(request.frequenciaAtualizacao());
        indicador.setVisivelDashboard(request.visivelDashboard());
        indicador.setOrdemExibicao(request.ordemExibicao());
        indicador.setDataCalculo(LocalDate.now());

        // Calcular valor inicial
        BigDecimal valor = calcularValor(empresaId, request.formula());
        indicador.setValorAtual(valor);

        return indicadorRepository.save(indicador);
    }

    @Override
    @Transactional
    public Indicador atualizar(Long empresaId, Long id, IndicadorRequest request) {
        Indicador indicador = indicadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Indicador nao encontrado"));

        if (!indicador.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Indicador nao pertence a esta empresa");
        }

        indicador.setNome(request.nome());
        indicador.setDescricao(request.descricao());
        indicador.setCategoria(request.categoria());
        indicador.setFormula(request.formula());
        indicador.setValorMeta(request.valorMeta());
        indicador.setUnidadeMedida(request.unidadeMedida());
        indicador.setCorValorBaixo(request.corValorBaixo());
        indicador.setCorValorMedio(request.corValorMedio());
        indicador.setCorValorAlto(request.corValorAlto());
        indicador.setAtivo(request.ativo());
        indicador.setFrequenciaAtualizacao(request.frequenciaAtualizacao());
        indicador.setVisivelDashboard(request.visivelDashboard());
        indicador.setOrdemExibicao(request.ordemExibicao());

        // Recalcular valor
        BigDecimal valor = calcularValor(empresaId, request.formula());
        indicador.setValorAtual(valor);
        indicador.setDataCalculo(LocalDate.now());

        return indicadorRepository.save(indicador);
    }

    @Override
    @Transactional(readOnly = true)
    public Indicador buscarPorId(Long empresaId, Long id) {
        Indicador indicador = indicadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Indicador nao encontrado"));

        if (!indicador.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Indicador nao pertence a esta empresa");
        }

        return indicador;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Indicador> listarPorEmpresa(Long empresaId) {
        return indicadorRepository.findByEmpresaIdAndAtivoTrue(empresaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Indicador> listarPorCategoria(Long empresaId, String categoria) {
        return indicadorRepository.findByEmpresaIdAndCategoria(empresaId, categoria);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Indicador> listarVisiveisDashboard(Long empresaId) {
        return indicadorRepository.findDashboardIndicators(empresaId, null);
    }

    @Override
    @Transactional
    public void excluir(Long empresaId, Long id) {
        Indicador indicador = indicadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Indicador nao encontrado"));

        if (!indicador.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Indicador nao pertence a esta empresa");
        }

        indicadorRepository.delete(indicador);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> calcularIndicadores(Long empresaId, String categoria) {
        List<Indicador> indicadores = indicadorRepository.findByEmpresaIdAndCategoria(empresaId, categoria);
        Map<String, Object> resultado = new HashMap<>();

        for (Indicador indicador : indicadores) {
            BigDecimal valor = calcularValor(empresaId, indicador.getFormula());
            resultado.put(indicador.getNome(), valor);
        }

        return resultado;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> calcularTodosIndicadores(Long empresaId) {
        List<Indicador> indicadores = indicadorRepository.findByEmpresaIdAndAtivoTrue(empresaId);
        Map<String, Object> resultado = new HashMap<>();

        for (Indicador indicador : indicadores) {
            BigDecimal valor = calcularValor(empresaId, indicador.getFormula());
            resultado.put(indicador.getNome(), valor);
        }

        return resultado;
    }

    @Override
    @Transactional
    public void atualizarValores(Long empresaId) {
        List<Indicador> indicadores = indicadorRepository.findByEmpresaIdAndAtivoTrue(empresaId);

        for (Indicador indicador : indicadores) {
            BigDecimal valor = calcularValor(empresaId, indicador.getFormula());
            indicador.setValorAnterior(indicador.getValorAtual());
            indicador.setValorAtual(valor);
            indicador.setDataCalculo(LocalDate.now());
            indicadorRepository.save(indicador);
        }
    }

    @Override
    @Transactional
    public void atualizarValor(Long empresaId, Long indicadorId) {
        Indicador indicador = indicadorRepository.findById(indicadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Indicador nao encontrado"));

        if (!indicador.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Indicador nao pertence a esta empresa");
        }

        BigDecimal valor = calcularValor(empresaId, indicador.getFormula());
        indicador.setValorAnterior(indicador.getValorAtual());
        indicador.setValorAtual(valor);
        indicador.setDataCalculo(LocalDate.now());
        indicadorRepository.save(indicador);
    }

    private BigDecimal calcularValor(Long empresaId, String formula) {
        if (formula == null || formula.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {
            // Se for uma query SQL
            if (formula.trim().toUpperCase().startsWith("SELECT")) {
                List<Map<String, Object>> resultados = jdbcTemplate.queryForList(formula, empresaId);
                if (!resultados.isEmpty() && resultados.get(0).containsKey("valor")) {
                    Object valorObj = resultados.get(0).get("valor");
                    if (valorObj instanceof Number) {
                        return new BigDecimal(((Number) valorObj).doubleValue());
                    }
                }
                return BigDecimal.ZERO;
            }

            // Se for uma expressao simples (ex: "100 + 50")
            // Implementar parser de expressoes matematicas
            // Por enquanto, retorna 0
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Erro ao calcular indicador com formula {}: {}", formula, e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }
}
