package br.com.brasil_saas.financeiro.service.impl;
import br.com.brasil_saas.financeiro.dto.FinanceiroDtos.*;
import br.com.brasil_saas.financeiro.model.LancamentoContabil;
import br.com.brasil_saas.financeiro.model.LancamentoPartida;
import br.com.brasil_saas.financeiro.repository.LancamentoContabilRepository;
import br.com.brasil_saas.financeiro.repository.LancamentoPartidaRepository;
import br.com.brasil_saas.financeiro.service.LancamentoContabilService;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal; import java.util.List;

@Service @RequiredArgsConstructor
public class LancamentoContabilServiceImpl implements LancamentoContabilService {
    private final LancamentoContabilRepository lancamentoRepository;
    private final LancamentoPartidaRepository partidaRepository;

    @Override @Transactional
    public LancamentoResponse criar(Long empresaId, LancamentoRequest r) {
        if (r == null || r.dataLancamento() == null) throw new BusinessException("Data do lançamento é obrigatória");
        if (r.descricaoHistorico() == null || r.descricaoHistorico().isBlank()) throw new BusinessException("Histórico contábil é obrigatório");
        if (r.partidas() == null || r.partidas().size() < 2) {
            throw new BusinessException("Lançamento exige ao menos 2 partidas (débito e crédito)");
        }
        validarPartidas(r.partidas());
        BigDecimal debitos = r.partidas().stream().filter(p -> "D".equalsIgnoreCase(p.tipo()))
            .map(PartidaRequest::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditos = r.partidas().stream().filter(p -> "C".equalsIgnoreCase(p.tipo()))
            .map(PartidaRequest::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debitos.compareTo(creditos) != 0) {
            throw new BusinessException("Partidas desbalanceadas: débito=" + debitos + " crédito=" + creditos);
        }
        LancamentoContabil l = new LancamentoContabil();
        l.setEmpresaId(empresaId);
        l.setDataLancamento(r.dataLancamento());
        l.setDescricaoHistorico(r.descricaoHistorico());
        l.setOrigem(r.origem());
        l.setIdOrigem(r.idOrigem());
        l.setValorTotal(debitos);
        l = lancamentoRepository.save(l);
        for (PartidaRequest pr : r.partidas()) {
            LancamentoPartida p = new LancamentoPartida();
            p.setEmpresaId(empresaId);
            p.setLancamentoId(l.getId());
            p.setPlanoContasId(pr.planoContasId());
            p.setCentroCustoId(pr.centroCustoId());
            p.setTipo(pr.tipo().toUpperCase());
            p.setValor(pr.valor());
            partidaRepository.save(p);
        }
        return buscar(empresaId, l.getId());
    }

    @Override @Transactional(readOnly = true)
    public LancamentoResponse buscar(Long empresaId, Long id) {
        LancamentoContabil l = lancamentoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
            .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado"));
        List<PartidaResponse> partidas = partidaRepository.findByLancamentoIdAndDeletedAtIsNull(id).stream()
            .map(p -> new PartidaResponse(p.getId(), p.getPlanoContasId(), p.getCentroCustoId(), p.getTipo(), p.getValor()))
            .toList();
        return new LancamentoResponse(l.getId(), l.getDataLancamento(), l.getDescricaoHistorico(),
            l.getValorTotal(), l.getOrigem(), l.getIdOrigem(), partidas);
    }

    @Override @Transactional(readOnly = true)
    public List<LancamentoResponse> listar(Long empresaId) {
        return lancamentoRepository.findByEmpresaIdAndDeletedAtIsNullOrderByDataLancamentoDesc(empresaId).stream()
            .map(l -> buscar(empresaId, l.getId())).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<PartidaResponse> listarPartidas(Long empresaId, Long id) {
        // Garante que o lancamento pertence a empresa antes de expor as partidas
        buscar(empresaId, id);
        return partidaRepository.findByLancamentoIdAndDeletedAtIsNull(id).stream()
            .map(p -> new PartidaResponse(p.getId(), p.getPlanoContasId(), p.getCentroCustoId(), p.getTipo(), p.getValor()))
            .toList();
    }

    @Override @Transactional
    public LancamentoResponse atualizar(Long empresaId, Long id, LancamentoRequest r) {
        LancamentoContabil l = lancamentoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
            .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado"));

        if (l.getOrigem() != null && !l.getOrigem().isBlank()) {
            throw new BusinessException("Lançamento integrado (" + l.getOrigem() + ") não pode ser alterado manualmente");
        }
        if (r == null) throw new BusinessException("Dados do lançamento são obrigatórios");
        if (r.partidas() != null) {
            validarPartidas(r.partidas());
            partidaRepository.deleteAll(partidaRepository.findByLancamentoIdAndDeletedAtIsNull(id));
            BigDecimal debitos = BigDecimal.ZERO;
            for (PartidaRequest pr : r.partidas()) {
                LancamentoPartida p = new LancamentoPartida();
                p.setEmpresaId(empresaId);
                p.setLancamentoId(id);
                p.setPlanoContasId(pr.planoContasId());
                p.setCentroCustoId(pr.centroCustoId());
                p.setTipo(pr.tipo().toUpperCase());
                p.setValor(pr.valor());
                partidaRepository.save(p);
                if ("D".equalsIgnoreCase(pr.tipo())) debitos = debitos.add(pr.valor());
            }
            l.setValorTotal(debitos);
        }

        if (r.dataLancamento() != null) l.setDataLancamento(r.dataLancamento());
        if (r.descricaoHistorico() != null) {
            if (r.descricaoHistorico().isBlank()) throw new BusinessException("Histórico contábil não pode ser vazio");
            l.setDescricaoHistorico(r.descricaoHistorico());
        }
        l.setOrigem(r.origem());
        l.setIdOrigem(r.idOrigem());
        lancamentoRepository.save(l);
        return buscar(empresaId, id);
    }

    @Override @Transactional
    public void excluir(Long empresaId, Long id) {
        LancamentoContabil l = lancamentoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
            .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado"));
        if (l.getOrigem() != null && !l.getOrigem().isBlank()) throw new BusinessException("Lançamento integrado não pode ser excluído");
        partidaRepository.findByLancamentoIdAndDeletedAtIsNull(id)
            .forEach(partidaRepository::delete);
        lancamentoRepository.delete(l);
    }

    private void validarPartidas(List<PartidaRequest> partidas) {
        if (partidas == null || partidas.size() < 2) {
            throw new BusinessException("Lançamento exige ao menos 2 partidas (débito e crédito)");
        }
        for (PartidaRequest p : partidas) {
            if (p == null || p.planoContasId() == null || p.planoContasId() <= 0) throw new BusinessException("Conta contábil é obrigatória em todas as partidas");
            if (p.valor() == null || p.valor().signum() <= 0) throw new BusinessException("Valor das partidas deve ser maior que zero");
            if (!"D".equalsIgnoreCase(p.tipo()) && !"C".equalsIgnoreCase(p.tipo())) throw new BusinessException("Tipo de partida deve ser D ou C");
        }
        BigDecimal debitos = partidas.stream().filter(p -> "D".equalsIgnoreCase(p.tipo()))
            .map(PartidaRequest::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditos = partidas.stream().filter(p -> "C".equalsIgnoreCase(p.tipo()))
            .map(PartidaRequest::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debitos.compareTo(creditos) != 0) {
            throw new BusinessException("Partidas desbalanceadas: débito=" + debitos + " crédito=" + creditos);
        }
    }
}
