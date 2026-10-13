package br.com.brasil_saas.producao.service;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.producao.model.MpsItem;
import br.com.brasil_saas.producao.repository.MpsItemRepository;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Service @RequiredArgsConstructor
public class MpsService {
    private final MpsItemRepository repo;
    private final PedidoVendaRepository pedidos;
    private final SaldoEstoqueRepository saldos;
    public List<MpsItem> listar(Long empresaId, String periodo) {
        if (periodo == null || periodo.isBlank()) return repo.findByEmpresaIdAndDeletedAtIsNull(empresaId);
        return repo.findByEmpresaIdAndPeriodoAndDeletedAtIsNull(empresaId, periodo);
    }
    @Transactional public List<MpsItem> gerar(Long empresaId, String periodo) {
        if (periodo == null || !periodo.matches("\\d{4}-\\d{2}")) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Periodo AAAA-MM obrigatorio");
        int ano = Integer.parseInt(periodo.substring(0, 4));
        int mes = Integer.parseInt(periodo.substring(5, 7));
        LocalDate ini = LocalDate.of(ano, mes, 1);
        LocalDate fim = ini.withDayOfMonth(ini.lengthOfMonth());
        for (MpsItem anterior : repo.findByEmpresaIdAndPeriodoAndDeletedAtIsNull(empresaId, periodo)) {
            if ("RASCUNHO".equals(anterior.getStatus())) {
                anterior.setDeletedAt(java.time.LocalDateTime.now());
                repo.save(anterior);
            }
        }
        Map<Long, BigDecimal> demanda = new LinkedHashMap<>();
        for (var p : pedidos.findByEmpresaIdAndPeriodo(empresaId, ini, fim)) {
            if (!"ABERTO".equals(p.getStatus())) continue;
            if (p.getItens() == null) continue;
            for (var it : p.getItens()) {
                if (it.getProdutoId() == null) continue;
                demanda.merge(it.getProdutoId(), it.getQuantidade() == null ? BigDecimal.ZERO : it.getQuantidade(), BigDecimal::add);
            }
        }
        List<MpsItem> out = new ArrayList<>();
        for (var e : demanda.entrySet()) {
            BigDecimal estoque = saldos.findByEmpresaIdAndProdutoIdAndDeletedAtIsNull(empresaId, e.getKey()).map(x -> x.getQuantidade() == null ? BigDecimal.ZERO : x.getQuantidade()).orElse(BigDecimal.ZERO);
            BigDecimal planejada = e.getValue().subtract(estoque);
            if (planejada.signum() < 0) planejada = BigDecimal.ZERO;
            MpsItem m = new MpsItem();
            m.setEmpresaId(empresaId);
            m.setPeriodo(periodo);
            m.setProdutoId(e.getKey());
            m.setQtdDemandada(e.getValue());
            m.setQtdEstoque(estoque);
            m.setQtdPlanejada(planejada);
            m.setOrigem("PEDIDOS");
            m.setStatus("RASCUNHO");
            out.add(repo.save(m));
        }
        return out;
    }
    @Transactional public MpsItem confirmar(Long empresaId, Long id) {
        MpsItem m = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item MPS inexistente"));
        if ("CONFIRMADO".equals(m.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Item MPS já está confirmado");
        if (m.getQtdPlanejada() == null || m.getQtdPlanejada().signum() < 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Quantidade planejada inválida");
        m.setStatus("CONFIRMADO");
        return repo.save(m);
    }
    @Transactional public void excluir(Long empresaId, Long id) {
        MpsItem m = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item MPS inexistente"));
        repo.delete(m);
    }
}
