package br.com.brasil_saas.financeiro.service.impl;

import br.com.brasil_saas.financeiro.dto.ComissaoDtos.RegraRequest;
import br.com.brasil_saas.financeiro.model.Comissao;
import br.com.brasil_saas.financeiro.repository.ComissaoRepository;
import br.com.brasil_saas.financeiro.service.ComissaoService;
import br.com.brasil_saas.rh.repository.FuncionarioRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.vendas.model.RegraComissao;
import br.com.brasil_saas.vendas.repository.RegraComissaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComissaoServiceImpl implements ComissaoService {

    private final ComissaoRepository comissaoRepository;
    private final RegraComissaoRepository regraComissaoRepository;
    private final FuncionarioRepository funcionarioRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Comissao> listar(Long empresaId, String status, Long funcionarioId) {
        List<Comissao> base = (funcionarioId != null)
                ? comissaoRepository.findByEmpresaIdAndFuncionarioId(empresaId, funcionarioId)
                : comissaoRepository.findByEmpresaIdOrderByCreatedAtDesc(empresaId);

        if (status != null && !status.isBlank()) {
            final String alvo = status.trim().toUpperCase();
            base = base.stream().filter(c -> alvo.equals(c.getStatus())).toList();
        }
        return base;
    }

    @Override
    @Transactional
    public Comissao pagar(Long id, Long empresaId) {
        Comissao comissao = comissaoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Comissao nao encontrada"));

        if ("PAGO".equalsIgnoreCase(comissao.getStatus())) {
            // Pagar duas vezes moveria dinheiro duas vezes. Aqui vira erro
            // visivel em vez de efeito silencioso.
            throw new BusinessException("Comissao ja esta paga");
        }

        comissao.setStatus("PAGO");
        comissao.setDataPagamento(java.time.LocalDateTime.now());
        return comissaoRepository.save(comissao);
    }

    @Override
    @Transactional
    public int estornarPorPedido(Long empresaId, Long pedidoId, String motivo) {
        var lista = comissaoRepository.findByEmpresaIdAndPedidoId(empresaId, pedidoId);
        int n = 0;
        for (Comissao c : lista) {
            if ("ESTORNADA".equalsIgnoreCase(c.getStatus())) continue;
            if ("PENDENTE".equalsIgnoreCase(c.getStatus())) {
                c.setStatus("ESTORNADA");
                comissaoRepository.save(c);
                n++;
            } else if ("PAGO".equalsIgnoreCase(c.getStatus())) {
                Comissao ajuste = new Comissao();
                ajuste.setEmpresaId(empresaId);
                ajuste.setFuncionarioId(c.getFuncionarioId());
                ajuste.setPedidoId(pedidoId);
                ajuste.setValorVenda(c.getValorVenda());
                ajuste.setPercentual(c.getPercentual());
                ajuste.setValorComissao(c.getValorComissao() == null ? BigDecimal.ZERO : c.getValorComissao().negate());
                ajuste.setStatus("PENDENTE");
                comissaoRepository.save(ajuste);
                c.setStatus("ESTORNADA");
                comissaoRepository.save(c);
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ regras

    @Override
    @Transactional(readOnly = true)
    public List<RegraComissao> listarRegras(Long empresaId, Long vendedorId) {
        return regraComissaoRepository.findByEmpresaIdOrderByFaixaValorMinAsc(empresaId).stream()
                .filter(r -> vendedorId == null || vendedorId.equals(r.getVendedorId()))
                .toList();
    }

    @Override
    @Transactional
    public RegraComissao criarRegra(RegraRequest request, Long empresaId) {
        RegraComissao regra = new RegraComissao();
        regra.setEmpresaId(empresaId);
        aplicar(regra, request);
        return regraComissaoRepository.save(regra);
    }

    @Override
    @Transactional
    public RegraComissao atualizarRegra(Long id, RegraRequest request, Long empresaId) {
        RegraComissao regra = regraComissaoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Regra de comissao nao encontrada"));
        aplicar(regra, request);
        return regraComissaoRepository.save(regra);
    }

    @Override
    @Transactional
    public void excluirRegra(Long id, Long empresaId) {
        RegraComissao regra = regraComissaoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Regra de comissao nao encontrada"));
        regraComissaoRepository.delete(regra);
    }

    private void aplicar(RegraComissao regra, RegraRequest r) {
        if (r.faixaValorMax() != null && r.faixaValorMin() != null
                && r.faixaValorMax().compareTo(r.faixaValorMin()) < 0) {
            throw new BusinessException("O teto da faixa e menor que a base");
        }
        if (r.vigenciaFim() != null && r.vigenciaInicio() != null
                && r.vigenciaFim().isBefore(r.vigenciaInicio())) {
            throw new BusinessException("O fim da vigencia e anterior ao inicio");
        }

        regra.setNome(r.nome().trim());
        regra.setVendedorId(r.vendedorId());
        regra.setVigenciaInicio(r.vigenciaInicio() == null ? null : r.vigenciaInicio().toLocalDate());
        regra.setVigenciaFim(r.vigenciaFim() == null ? null : r.vigenciaFim().toLocalDate());
        regra.setMetaValor(r.metaValor());
        regra.setFaixaValorMin(r.faixaValorMin() == null ? BigDecimal.ZERO : r.faixaValorMin());
        regra.setFaixaValorMax(r.faixaValorMax());
        // defense em profundidade: a validacao do DTO ja barra 0, mas o service
        // nao deve depender que toda entrada passe por ela.
        if (r.percentual() == null || r.percentual().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("O percentual deve ser maior que zero");
        }
        regra.setPercentual(r.percentual());
        regra.setBaseCalculo(r.baseCalculo() == null || r.baseCalculo().isBlank()
                ? "VALOR_LIQUIDO" : r.baseCalculo().trim().toUpperCase());
        regra.setAtivo(r.ativo() == null || r.ativo());
    }
}
