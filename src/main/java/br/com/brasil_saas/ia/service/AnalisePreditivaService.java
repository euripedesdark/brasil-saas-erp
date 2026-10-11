package br.com.brasil_saas.ia.service;

import br.com.brasil_saas.ia.dto.AnalisePreditivaRequest;
import br.com.brasil_saas.ia.model.AnalisePreditiva;
import java.util.List;
import java.util.Map;

public interface AnalisePreditivaService {

    AnalisePreditiva criar(Long empresaId, AnalisePreditivaRequest request);

    AnalisePreditiva atualizar(Long empresaId, Long id, AnalisePreditivaRequest request);

    AnalisePreditiva buscarPorId(Long empresaId, Long id);

    List<AnalisePreditiva> listarPorEmpresa(Long empresaId);

    List<AnalisePreditiva> listarPorTipo(Long empresaId, String tipo);

    List<AnalisePreditiva> listarPorEntidade(Long empresaId, String entidade, Long entidadeId);

    List<AnalisePreditiva> listarPendentes(Long empresaId);

    List<AnalisePreditiva> listarHighConfidence(Long empresaId, String tipo, Double minConfianca);

    void excluir(Long empresaId, Long id);

    Map<String, Object> preverVendas(Long empresaId, Long produtoId, Integer dias);

    Map<String, Object> preverEstoque(Long empresaId, Long produtoId, Integer dias);

    Map<String, Object> preverFinanceiro(Long empresaId, Integer dias);

    void executarAnalisesPendentes();

    void recalcularAnalise(Long empresaId, Long id);
}
