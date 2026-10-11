package br.com.brasil_saas.servicos.service.impl;
import br.com.brasil_saas.servicos.model.*;
import br.com.brasil_saas.servicos.repository.*;
import br.com.brasil_saas.servicos.service.OrdemServicoService;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.web.PageResponse;
import br.com.brasil_saas.financeiro.model.Comissao;
import br.com.brasil_saas.financeiro.repository.ComissaoRepository;
import br.com.brasil_saas.rh.model.Funcionario;
import br.com.brasil_saas.rh.repository.FuncionarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import br.com.brasil_saas.core.service.DocumentoFluxoService;
import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.List;
@Service @RequiredArgsConstructor
public class OrdemServicoServiceImpl implements OrdemServicoService {
    private final OrdemServicoRepository osRepo;
    private final OsItemRepository itemRepo;
    private final OsApontamentoRepository apontRepo;
    private final ComissaoRepository comissaoRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final DocumentoFluxoService documentoFluxo;

    @Override @Transactional
    public OsResp abrir(Long empresaId, Long usuarioId, AberturaReq r) {
        OrdemServico os = new OrdemServico();
        os.setEmpresaId(empresaId); os.setClienteId(r.clienteId());
        os.setEquipamento(r.equipamento()); os.setDescricao(r.descricao());
        os.setPrevisaoAt(r.previsaoAt()); os.setUsuarioId(usuarioId);
        os.setAberturaAt(LocalDateTime.now()); os.setStatus("ABERTA");
        os.setNumero(String.valueOf(osRepo.countByEmpresaIdAndDeletedAtIsNull(empresaId) + 1));
        return toResp(osRepo.save(os));
    }
    @Override @Transactional
    public OsResp addItem(Long empresaId, Long osId, ItemReq r) {
        OrdemServico os = obter(empresaId, osId);
        if (!"ABERTA".equals(os.getStatus())) throw new BusinessException("OS não está aberta");
        OsItem it = new OsItem();
        it.setEmpresaId(empresaId); it.setOsId(osId);
        it.setProdutoId(r.produtoId()); it.setServicoId(r.servicoId());
        it.setQuantidade(r.quantidade()); it.setValorUnitario(r.valorUnitario());
        it.setValorTotal(r.quantidade().multiply(r.valorUnitario()));
        itemRepo.save(it);
        os.setValorTotal(os.getValorTotal().add(it.getValorTotal()));
        return toResp(osRepo.save(os));
    }
    @Override @Transactional
    public OsResp apontar(Long empresaId, Long usuarioId, Long osId, ApontReq r) {
        OrdemServico os = obter(empresaId, osId);
        if (!"ABERTA".equals(os.getStatus())) throw new BusinessException("OS não está aberta");
        OsApontamento a = new OsApontamento();
        a.setEmpresaId(empresaId); a.setOsId(osId); a.setUsuarioId(usuarioId);
        a.setDataApontamento(LocalDateTime.now()); a.setHoras(r.horas()); a.setDescricao(r.descricao());
        apontRepo.save(a);
        return toResp(os);
    }
    @Override @Transactional
    public OsResp fechar(Long empresaId, Long osId, String laudo) {
        OrdemServico os = obter(empresaId, osId);
        if (!"ABERTA".equals(os.getStatus())) throw new BusinessException("OS não está aberta");

        // 1. Calcular e gerar comissões de mão de obra para técnicos/prestadores
        var apontamentos = apontRepo.findByOsIdAndDeletedAtIsNull(osId);
        var usuarios = apontamentos.stream().map(OsApontamento::getUsuarioId).distinct().toList();

        for (Long usuarioId : usuarios) {
            funcionarioRepository.findByUsuarioId(usuarioId).ifPresent(func -> {
                if ("TECNICO".equals(func.getTipoColaborador()) || "PRESTADOR".equals(func.getTipoColaborador())) {
                    BigDecimal valorTrabalho = BigDecimal.ZERO;

                    // A. Mão de obra por hora
                    BigDecimal totalHoras = apontamentos.stream()
                        .filter(a -> a.getUsuarioId().equals(usuarioId))
                        .map(OsApontamento::getHoras)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (func.getValorHora() != null && func.getValorHora().compareTo(BigDecimal.ZERO) > 0) {
                        valorTrabalho = valorTrabalho.add(totalHoras.multiply(func.getValorHora()));
                    }

                    // B. Mão de obra por serviço (percentual do total da OS)
                    if (func.getPercentualComissao() != null && func.getPercentualComissao().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal comissaoServico = os.getValorTotal().multiply(func.getPercentualComissao()).movePointLeft(2);
                        valorTrabalho = valorTrabalho.add(comissaoServico);
                    }

                    if (valorTrabalho.compareTo(BigDecimal.ZERO) > 0) {
                        Comissao com = new Comissao();
                        com.setEmpresaId(empresaId);
                        com.setFuncionarioId(func.getId());
                        com.setPedidoId(osId); // Usando osId no campo pedidoId por simplicidade
                        com.setValorVenda(os.getValorTotal());
                        com.setPercentual(func.getPercentualComissao() != null ? func.getPercentualComissao() : BigDecimal.ZERO);
                        com.setValorComissao(valorTrabalho);
                        com.setStatus("PENDENTE");
                        Comissao salva = comissaoRepository.save(com);
                        documentoFluxo.ligar(empresaId, usuarioId, "ORDEM_SERVICO", osId, os.getNumero(),
                                "COMISSAO", salva.getId(), null, "COMISSIONA");
                    }
                }
            });
        }

        os.setStatus("FECHADA"); os.setFechamentoAt(LocalDateTime.now()); os.setLaudo(laudo);
        return toResp(osRepo.save(os));
    }
    @Override @Transactional(readOnly=true)
    public OsResp buscar(Long empresaId, Long osId) { return toResp(obter(empresaId, osId)); }
    @Override @Transactional
    public OsResp atualizar(Long empresaId, Long osId, EdicaoReq r) {
        OrdemServico os = obter(empresaId, osId);
        if (!"ABERTA".equals(os.getStatus())) throw new BusinessException("Somente OS ABERTA pode ser editada");
        if (r.clienteId() != null) os.setClienteId(r.clienteId());
        if (r.equipamento() != null) os.setEquipamento(r.equipamento());
        if (r.descricao() != null) os.setDescricao(r.descricao());
        if (r.previsaoAt() != null) os.setPrevisaoAt(r.previsaoAt());
        return toResp(osRepo.save(os));
    }
    @Override @Transactional
    public void excluir(Long empresaId, Long osId) {
        OrdemServico os = obter(empresaId, osId);
        if ("FECHADA".equals(os.getStatus())) throw new BusinessException("OS FECHADA não pode ser excluída");
        osRepo.delete(os);
    }
    @Override @Transactional(readOnly=true)
    public List<OsItemResp> listarItens(Long empresaId, Long osId) {
        obter(empresaId, osId); // valida o tenant antes de expor os itens
        return itemRepo.findByOsIdAndDeletedAtIsNull(osId).stream()
            .map(i -> new OsItemResp(i.getId(), i.getProdutoId(), i.getServicoId(),
                i.getQuantidade(), i.getValorUnitario(), i.getValorTotal()))
            .toList();
    }
    @Override @Transactional(readOnly=true)
    public PageResponse<OsResp> listar(Long empresaId, String status, Pageable p) {
        var page = (status==null||status.isBlank())
            ? osRepo.findByEmpresaIdAndDeletedAtIsNull(empresaId, p)
            : osRepo.findByEmpresaIdAndStatusAndDeletedAtIsNull(empresaId, status, p);
        return PageResponse.from(page, this::toResp);
    }
    private OrdemServico obter(Long e, Long id){
        return osRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(id,e)
            .orElseThrow(()->new ResourceNotFoundException("ordemServico", id));
    }
    private OsResp toResp(OrdemServico o){
        return new OsResp(o.getId(), o.getNumero(), o.getClienteId(), o.getEquipamento(),
            o.getStatus(), o.getValorTotal(), o.getAberturaAt(), o.getFechamentoAt());
    }
}
