package br.com.brasil_saas.producao.service;

import br.com.brasil_saas.producao.model.ApontamentoProducao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ApontamentoProducaoService {

    ApontamentoProducao criar(Long empresaId, ApontamentoProducaoRequest request);

    ApontamentoProducao atualizar(Long empresaId, Long id, ApontamentoProducaoRequest request);

    ApontamentoProducao finalizar(Long empresaId, Long id);

    void excluir(Long empresaId, Long id);

    List<ApontamentoProducao> listarPorProducao(Long empresaId, Long producaoId);

    List<ApontamentoProducao> listarPorFuncionario(Long empresaId, Long funcionarioId);

    List<ApontamentoProducao> listarPorPeriodo(Long empresaId, LocalDateTime dataInicio, LocalDateTime dataFim);

    List<ApontamentoProducao> listarPorStatus(Long empresaId, String status);

    Map<String, Object> getEstatisticas(Long empresaId, Long producaoId);
}
