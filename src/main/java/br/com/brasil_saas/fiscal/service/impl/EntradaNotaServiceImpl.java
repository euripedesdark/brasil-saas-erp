package br.com.brasil_saas.fiscal.service.impl;
import br.com.brasil_saas.cadastro.repository.ProdutoRepository;
import br.com.brasil_saas.estoque.model.MovimentacaoEstoque;
import br.com.brasil_saas.estoque.model.SaldoEstoque;
import br.com.brasil_saas.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.DepositoRepository;
import br.com.brasil_saas.fiscal.model.Manifestacao;
import br.com.brasil_saas.fiscal.model.Nfe;
import br.com.brasil_saas.fiscal.model.NfeItem;
import br.com.brasil_saas.fiscal.repository.ManifestacaoRepository;
import br.com.brasil_saas.fiscal.repository.NfeItemRepository;
import br.com.brasil_saas.fiscal.repository.NfeRepository;
import br.com.brasil_saas.fiscal.service.EntradaNotaService;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal; import java.time.LocalDateTime;
@Service @RequiredArgsConstructor
public class EntradaNotaServiceImpl implements EntradaNotaService {
    private final NfeRepository nfeRepo; private final NfeItemRepository itemRepo;
    private final ManifestacaoRepository manifRepo; private final ProdutoRepository produtoRepo;
    private final SaldoEstoqueRepository saldoRepo; private final DepositoRepository depositoRepo; private final MovimentacaoEstoqueRepository movRepo;
    @Override @Transactional public EntradaResp registrar(Long e, EntradaReq r){
        if (r.chaveAcesso()!=null && !nfeRepo.findByEmpresaIdAndChaveAcessoAndDeletedAtIsNull(e,r.chaveAcesso()).isEmpty())
            throw new BusinessException("Nota de entrada já registrada para esta chave");
        Nfe n=new Nfe(); n.setEmpresaId(e); n.setPessoaId(r.pessoaId()); n.setChaveAcesso(r.chaveAcesso());
        n.setNaturezaOperacao(r.naturezaOperacao()); n.setCfop(r.cfop()); n.setDataEmissao(r.dataEmissao());
        n.setValorTotal(r.valorTotal()); n.setStatus("DIGITADA"); n.setTipoOperacao("E");
        // Sete campos monetarios sao NOT NULL em bc_fis_nfe e nenhum era
        // setado aqui, entao o insert morria com
        //   null value in column "valor_produtos" of relation "bc_fis_nfe"
        // e o GlobalExceptionHandler devolvia 409 "duplicidade ou FK invalida",
        // que nao e a causa. A tabela bc_fis_nfe tinha 0 linhas por causa
        // disto: a entrada digitada nunca funcionou, desde que foi escrita.
        // O que se sabe e o total que o usuario digitou; o resto e zero, e
        // zero e verdade em nota sem imposed, nao palpite. Quem precisar
        // desses valores usa a importacao por XML, que le os sete.
        BigDecimal soma=r.valorTotal()==null?BigDecimal.ZERO:r.valorTotal();
        n.setValorProdutos(soma); n.setValorDesconto(BigDecimal.ZERO);
        n.setValorFrete(BigDecimal.ZERO); n.setValorIcms(BigDecimal.ZERO);
        n.setValorIpi(BigDecimal.ZERO); n.setValorPis(BigDecimal.ZERO);
        n.setValorCofins(BigDecimal.ZERO);
        Nfe salva=nfeRepo.save(n); boolean est=r.gerarEstoque()==null||r.gerarEstoque();
        if(r.itens()!=null){ int i=1; for(ItemIn it:r.itens()){
            NfeItem ni=new NfeItem(); ni.setNfe(salva); ni.setNumeroItem(i++); ni.setProdutoId(it.produtoId());
            ni.setNcm(it.ncm()); ni.setCfop(it.cfop()); ni.setQuantidade(it.quantidade()); ni.setValorUnitario(it.valorUnitario());
            ni.setValorTotal(it.quantidade().multiply(it.valorUnitario())); itemRepo.save(ni);
            if(est && it.produtoId()!=null) entradaEstoque(e, it);
        }} return toResp(salva);
    }
    @Override @Transactional public EntradaResp manifestar(Long e, Long id, ManifReq r){
        Nfe n=nfeRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(id,e).orElseThrow(()->new ResourceNotFoundException("entrada", id));
        if(!"E".equals(n.getTipoOperacao())) throw new BusinessException("Só notas de entrada podem ser manifestadas aqui");
        Manifestacao m=new Manifestacao(); m.setEmpresaId(e); m.setChaveAcesso(n.getChaveAcesso());
        m.setTipo(r.tipo()); m.setJustificativa(r.justificativa()); m.setDataEvento(LocalDateTime.now()); manifRepo.save(m);
        return toResp(n);
    }
    @Override @Transactional(readOnly=true) public EntradaResp porChave(Long e, String c){
        return toResp(nfeRepo.findByEmpresaIdAndChaveAcessoAndDeletedAtIsNull(e,c).orElseThrow(()->new ResourceNotFoundException("entrada", c))); }
    @Override @Transactional(readOnly=true) public PageResponse<EntradaResp> listar(Long e, Pageable p){
        return PageResponse.from(nfeRepo.findByEmpresaIdAndTipoOperacaoAndDeletedAtIsNull(e,"E",p), this::toResp); }
    private void entradaEstoque(Long e, ItemIn it){
        produtoRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(it.produtoId(), e).orElseThrow(()->new ResourceNotFoundException("produto", it.produtoId()));
        SaldoEstoque s=saldoRepo.findByEmpresaIdAndProdutoIdAndDeletedAtIsNull(e,it.produtoId())
            .orElseGet(()->{ SaldoEstoque x=new SaldoEstoque(); x.setEmpresaId(e); x.setProdutoId(it.produtoId()); x.setQuantidade(BigDecimal.ZERO); x.setDepositoId(depositoRepo.findByEmpresaIdAndCodigoAndAtivoTrue(e, "PADRAO").orElseThrow(() -> new BusinessException("Deposito PADRAO nao encontrado para a empresa " + e)).getId()); return x; });
        s.setQuantidade(s.getQuantidade().add(it.quantidade())); saldoRepo.save(s);
        MovimentacaoEstoque mv=new MovimentacaoEstoque(); mv.setEmpresaId(e); mv.setProdutoId(it.produtoId());
        mv.setTipo("ENTRADA"); mv.setOrigem("ENTRADA_NOTA"); mv.setQuantidade(it.quantidade());
        mv.setSaldoApos(s.getQuantidade()); mv.setDataMovimento(LocalDateTime.now()); movRepo.save(mv);
    }
    private EntradaResp toResp(Nfe n){ return new EntradaResp(n.getId(),n.getChaveAcesso(),n.getPessoaId(),n.getStatus(),n.getValorTotal(),n.getDataEmissao()); }
}
