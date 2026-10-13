package br.com.brasil_saas.financeiro.service;
import br.com.brasil_saas.financeiro.model.Renegociacao;
import br.com.brasil_saas.financeiro.model.Titulo;
import br.com.brasil_saas.financeiro.repository.RenegociacaoRepository;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
@Service @RequiredArgsConstructor
public class RenegociacaoService {
    private final RenegociacaoRepository renegociacoes;
    private final TituloRepository titulos;
    public List<Renegociacao> listar(Long empresaId) {
        return renegociacoes.findByEmpresaIdAndDeletedAtIsNullOrderByDataRenegociacaoDesc(empresaId);
    }
    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    @Transactional public Renegociacao renegociar(Long empresaId, Long tituloId, LocalDate novoVencimento, BigDecimal acrescimo, String observacao) {
        Titulo t = exigir(titulos.findByIdAndEmpresaIdAndDeletedAtIsNull(tituloId, empresaId), "Titulo inexistente");
        if (!"ABERTO".equals(t.getStatus()) && !"PARCIAL".equals(t.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Titulo nao esta em aberto");
        if (novoVencimento == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Novo vencimento obrigatorio");
        BigDecimal ac = acrescimo == null ? BigDecimal.ZERO : acrescimo;
        BigDecimal novoValor = (t.getValorSaldo() == null ? BigDecimal.ZERO : t.getValorSaldo()).add(ac);
        Titulo n = new Titulo();
        n.setTipo(t.getTipo());
        n.setDescricao(t.getDescricao() + " (renegociado)");
        n.setPessoaId(t.getPessoaId());
        n.setValorOriginal(novoValor);
        n.setValorSaldo(novoValor);
        n.setDataEmissao(LocalDate.now());
        n.setDataVencimento(novoVencimento);
        n.setStatus("ABERTO");
        n.setCentroCustoId(t.getCentroCustoId());
        n.setPlanoContasId(t.getPlanoContasId());
        n = titulos.save(n);
        Renegociacao r = new Renegociacao();
        r.setTituloOriginalId(t.getId());
        r.setNovoTituloId(n.getId());
        r.setDataRenegociacao(LocalDate.now());
        r.setValorAcrescimo(ac);
        r.setObservacao(observacao);
        r = renegociacoes.save(r);
        t.setStatus("RENEGOCIADO");
        t.setValorSaldo(BigDecimal.ZERO);
        titulos.save(t);
        return r;
    }
}
