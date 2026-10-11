package br.com.brasil_saas.financeiro.service;
import br.com.brasil_saas.financeiro.model.Orcamento;
import br.com.brasil_saas.financeiro.model.OrcamentoRealizado;
import br.com.brasil_saas.financeiro.repository.OrcamentoRealizadoRepository;
import br.com.brasil_saas.financeiro.repository.OrcamentoRepository;
import br.com.brasil_saas.financeiro.repository.PlanoContasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
@Service @RequiredArgsConstructor
public class OrcamentoService {
    private final OrcamentoRepository orcamentos;
    private final OrcamentoRealizadoRepository realizados;
    private final PlanoContasRepository contas;
    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    public List<Orcamento> listar(Long empresaId, Integer ano) {
        if (ano == null) return orcamentos.findByEmpresaIdAndDeletedAtIsNull(empresaId);
        return orcamentos.findByEmpresaIdAndAnoAndDeletedAtIsNull(empresaId, ano);
    }
    @Transactional public Orcamento salvar(Long empresaId, Orcamento o) {
        if (o.getPlanoContasId() != null) exigir(contas.findByIdAndEmpresaIdAndDeletedAtIsNull(o.getPlanoContasId(), empresaId), "Conta inexistente");
        o.setId(null);
        return orcamentos.save(o);
    }
    @Transactional public void excluir(Long empresaId, Long id) {
        Orcamento o = exigir(orcamentos.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Orcamento inexistente");
        for (OrcamentoRealizado r : realizados.findByOrcamentoIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)) realizados.delete(r);
        orcamentos.delete(o);
    }
    @Transactional public OrcamentoRealizado lancarRealizado(Long empresaId, Long orcamentoId, Integer mes, BigDecimal valor) {
        exigir(orcamentos.findByIdAndEmpresaIdAndDeletedAtIsNull(orcamentoId, empresaId), "Orcamento inexistente");
        if (mes == null || mes < 1 || mes > 12) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Mes invalido");
        OrcamentoRealizado r = new OrcamentoRealizado();
        r.setOrcamentoId(orcamentoId);
        r.setMes(mes);
        r.setValorRealizado(valor == null ? BigDecimal.ZERO : valor);
        return realizados.save(r);
    }
    public List<Map<String, Object>> acompanhamento(Long empresaId, Integer ano) {
        List<Orcamento> base = listar(empresaId, ano);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Orcamento o : base) {
            BigDecimal[] porMes = new BigDecimal[12];
            Arrays.fill(porMes, BigDecimal.ZERO);
            for (OrcamentoRealizado r : realizados.findByOrcamentoIdAndEmpresaIdAndDeletedAtIsNull(o.getId(), empresaId)) {
                if (r.getMes() != null && r.getMes() >= 1 && r.getMes() <= 12)
                    porMes[r.getMes() - 1] = porMes[r.getMes() - 1].add(r.getValorRealizado() == null ? BigDecimal.ZERO : r.getValorRealizado());
            }
            BigDecimal total = Arrays.stream(porMes).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal orcado = o.getValorOrcado() == null ? BigDecimal.ZERO : o.getValorOrcado();
            BigDecimal pct = orcado.signum() == 0 ? BigDecimal.ZERO : total.multiply(BigDecimal.valueOf(100)).divide(orcado, 1, RoundingMode.HALF_UP);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orcamentoId", o.getId());
            m.put("ano", o.getAno());
            m.put("contaId", o.getPlanoContasId());
            m.put("orcado", orcado);
            m.put("realizado", total);
            m.put("desvio", total.subtract(orcado));
            m.put("percentual", pct);
            List<BigDecimal> meses = new ArrayList<>();
            for (BigDecimal v : porMes) meses.add(v);
            m.put("porMes", meses);
            out.add(m);
        }
        return out;
    }
    public String nomeConta(Long empresaId, Long contaId) {
        if (contaId == null) return "Geral";
        return contas.findByIdAndEmpresaIdAndDeletedAtIsNull(contaId, empresaId).map(c -> c.getCodigo() + " - " + c.getDescricao()).orElse("Conta " + contaId);
    }
}
