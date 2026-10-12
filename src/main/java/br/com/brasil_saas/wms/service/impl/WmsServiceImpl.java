package br.com.brasil_saas.wms.service.impl;
import br.com.brasil_saas.estoque.model.EnderecoEstoque;
import br.com.brasil_saas.estoque.model.ExpedicaoEstoque;
import br.com.brasil_saas.estoque.model.ExpedicaoEstoqueItem;
import br.com.brasil_saas.estoque.model.ReservaEstoque;
import br.com.brasil_saas.estoque.repository.EnderecoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.ExpedicaoEstoqueItemRepository;
import br.com.brasil_saas.estoque.repository.ExpedicaoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.ReservaEstoqueRepository;
import br.com.brasil_saas.cadastro.repository.ProdutoRepository;
import br.com.brasil_saas.wms.model.*;
import br.com.brasil_saas.wms.repository.*;
import br.com.brasil_saas.wms.service.WmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import br.com.brasil_saas.core.service.DocumentoFluxoService;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class WmsServiceImpl implements WmsService {
    private final WmsOndaRepository ondas;
    private final WmsOndaItemRepository itens;
    private final WmsVolumeRepository volumes;
    private final WmsVolumeItemRepository volumeItens;
    private final EnderecoEstoqueRepository enderecos;
    private final ExpedicaoEstoqueRepository expedicoes;
    private final ExpedicaoEstoqueItemRepository expedicaoItens;
    private final ReservaEstoqueRepository reservas;
    private final ProdutoRepository produtos;
    private final DocumentoFluxoService documentoFluxo;
    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    @Override public List<WmsOnda> ondas(Long empresaId, String status) {
        if (status == null || status.isBlank()) return ondas.findByEmpresaIdAndDeletedAtIsNull(empresaId);
        return ondas.findByEmpresaIdAndStatusAndDeletedAtIsNull(empresaId, status);
    }
    @Override @Transactional public WmsOnda criarOnda(Long empresaId, WmsOnda o) {
        if (o.getDepositoId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposito obrigatorio");
        o.setId(null);
        o.setEmpresaId(empresaId);
        o.setStatus("ABERTA");
        return ondas.save(o);
    }
    private WmsOnda exigirOnda(Long empresaId, Long id) {
        return exigir(ondas.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Onda inexistente");
    }
    @Override @Transactional public WmsOndaItem addItem(Long empresaId, Long ondaId, WmsOndaItem i) {
        WmsOnda o = exigirOnda(empresaId, ondaId);
        if (!"ABERTA".equals(o.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Onda ja liberada");
        if (i.getProdutoId() == null || !produtos.findByIdAndEmpresaIdAndDeletedAtIsNull(i.getProdutoId(), empresaId).isPresent())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Produto inexistente para a empresa");
        if (i.getQtdSolicitada() == null || i.getQtdSolicitada().signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade solicitada deve ser positiva");
        i.setId(null);
        i.setEmpresaId(empresaId);
        i.setOndaId(ondaId);
        i.setQtdSeparada(BigDecimal.ZERO);
        i.setStatus("PENDENTE");
        return itens.save(i);
    }
    @Override @Transactional public java.util.Map<String, Object> gerarOndaDeReservas(Long empresaId, Long depositoId) {
        List<ReservaEstoque> rs = reservas.findByEmpresaIdAndDeletedAtIsNullOrderByDataReservaDesc(empresaId).stream()
            .filter(r -> "RESERVADA".equals(r.getStatus()) && depositoId.equals(r.getDepositoId()) && r.getProdutoId() != null && r.getQuantidade() != null && r.getQuantidade().signum() > 0).toList();
        java.util.Set<Long> emOnda = new java.util.HashSet<>();
        for (WmsOndaItem i : itens.findByEmpresaIdAndDeletedAtIsNull(empresaId)) {
            if ("RESERVA".equals(i.getOrigemTipo()) && i.getOrigemId() != null && !"SEPARADO".equals(i.getStatus())) emOnda.add(i.getOrigemId());
        }
        List<ReservaEstoque> novas = rs.stream().filter(r -> !emOnda.contains(r.getId())).toList();
        if (novas.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Sem reservas pendentes no deposito");
        WmsOnda o = new WmsOnda();
        o.setDepositoId(depositoId);
        o.setCodigo("ONDA-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        o = ondas.save(o);
        int n = 0;
        for (ReservaEstoque r : novas) {
            WmsOndaItem i = new WmsOndaItem();
            i.setOndaId(o.getId());
            i.setOrigemTipo("RESERVA");
            i.setOrigemId(r.getId());
            i.setProdutoId(r.getProdutoId());
            i.setQtdSolicitada(r.getQuantidade());
            i.setQtdSeparada(java.math.BigDecimal.ZERO);
            i.setStatus("PENDENTE");
            i.setEnderecoId(r.getEnderecoId());
            i.setEmpresaId(empresaId);
            itens.save(i);
            n++;
        }
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("ondaId", o.getId()); m.put("codigo", o.getCodigo()); m.put("itens", n);
        return m;
    }
    @Override public List<WmsOndaItem> itens(Long empresaId, Long ondaId) {
        exigirOnda(empresaId, ondaId);
        return itens.findByOndaIdAndEmpresaIdAndDeletedAtIsNull(ondaId, empresaId);
    }
    @Override @Transactional public WmsOnda liberar(Long empresaId, Long id) {
        WmsOnda o = exigirOnda(empresaId, id);
        if (itens.findByOndaIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId).isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Onda sem itens");
        o.setStatus("EM_SEPARACAO");
        o.setLiberadaEm(LocalDateTime.now());
        return ondas.save(o);
    }
    @Override @Transactional public WmsOndaItem separar(Long empresaId, Long ondaId, Long itemId, BigDecimal qtd, Long enderecoId) {
        if (qtd == null || qtd.signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade separada deve ser positiva");
        WmsOnda o = exigirOnda(empresaId, ondaId);
        if (!"EM_SEPARACAO".equals(o.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Onda nao esta em separacao");
        WmsOndaItem i = exigir(itens.findByIdAndEmpresaIdAndDeletedAtIsNull(itemId, empresaId), "Item inexistente");
        if (!ondaId.equals(i.getOndaId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item de outra onda");
        BigDecimal nova = (i.getQtdSeparada() == null ? BigDecimal.ZERO : i.getQtdSeparada()).add(qtd);
        if (nova.compareTo(i.getQtdSolicitada()) > 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Quantidade acima da solicitada");
        i.setQtdSeparada(nova);
        if (enderecoId != null) {
            EnderecoEstoque endereco = exigir(enderecos.findByIdAndEmpresaIdAndAtivoTrue(enderecoId, empresaId), "Endereco inexistente");
            if (!Objects.equals(endereco.getDepositoId(), o.getDepositoId()))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Endereco pertence a outro deposito");
            i.setEnderecoId(enderecoId);
        }
        i.setStatus(nova.compareTo(i.getQtdSolicitada()) >= 0 ? "SEPARADO" : "PARCIAL");
        return itens.save(i);
    }
    @Override @Transactional public WmsOnda concluir(Long empresaId, Long id) {
        WmsOnda o = exigirOnda(empresaId, id);
        if ("CONCLUIDA".equals(o.getStatus())) return o;
        var itensOnda = itens.findByOndaIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId);
        boolean ok = itensOnda.stream().allMatch(i -> "SEPARADO".equals(i.getStatus()));
        if (!ok) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Ha itens nao separados");
        for (WmsOndaItem i : itensOnda) {
            consumirReservaDaOnda(empresaId, i);
        }
        o.setStatus("CONCLUIDA");
        o.setConcluidaEm(LocalDateTime.now());
        return ondas.save(o);
    }

    /**
     * Consome a reserva de origem do item separado. Sem isso, a reserva
     * da onda concluida continuava RESERVADA e podia ser separada de novo
     * em outra onda. A expedicao aceita CONSUMIDA, entao o fluxo logistico
     * segue intacto; outros status nao sao tocados.
     */
    private void consumirReservaDaOnda(Long empresaId, WmsOndaItem i) {
        if (!"RESERVA".equals(i.getOrigemTipo()) || i.getOrigemId() == null) return;
        reservas.findById(i.getOrigemId()).filter(r -> empresaId.equals(r.getEmpresaId())).ifPresent(r -> {
            if ("RESERVADA".equals(r.getStatus()) || "SEPARACAO".equals(r.getStatus())) {
                r.setStatus("CONSUMIDA");
            }
            reservas.save(r);
        });
    }

    @Override public Map<String, Object> putaway(Long empresaId, Long depositoId, Long produtoId) {
        List<EnderecoEstoque> livres = enderecos.findByEmpresaIdAndDepositoIdAndAtivoTrueOrderByCodigoAsc(empresaId, depositoId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("depositoId", depositoId);
        m.put("produtoId", produtoId);
        EnderecoEstoque pick = livres.stream().filter(e -> "PICKING".equals(e.getTipo())).findFirst().orElse(null);
        EnderecoEstoque pulmao = livres.stream().filter(e -> "PULMAO".equals(e.getTipo())).findFirst().orElse(null);
        m.put("enderecoPickingId", pick == null ? null : pick.getId());
        m.put("enderecoPickingCodigo", pick == null ? null : pick.getCodigo());
        m.put("enderecoPulmaoId", pulmao == null ? null : pulmao.getId());
        m.put("enderecoPulmaoCodigo", pulmao == null ? null : pulmao.getCodigo());
        return m;
    }
    @Override public List<WmsVolume> volumes(Long empresaId, Long expedicaoId) {
        exigir(expedicoes.findByIdAndEmpresaIdAndDeletedAtIsNull(expedicaoId, empresaId), "Expedicao inexistente");
        return volumes.findByExpedicaoIdAndEmpresaIdAndDeletedAtIsNull(expedicaoId, empresaId);
    }
    @Override @Transactional public WmsVolume criarVolume(Long empresaId, WmsVolume v) {
        ExpedicaoEstoque exp = exigir(expedicoes.findByIdAndEmpresaIdAndDeletedAtIsNull(v.getExpedicaoId(), empresaId), "Expedicao inexistente");
        if ("EXPEDIDA".equals(exp.getStatus()) || "CANCELADA".equals(exp.getStatus()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Expedicao encerrada nao aceita novos volumes");
        if (v.getCodigo() == null || v.getCodigo().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codigo do volume obrigatorio");
        v.setId(null);
        v.setEmpresaId(empresaId);
        v.setStatus("ABERTO");
        return volumes.save(v);
    }
    @Override @Transactional public WmsVolumeItem embalar(Long empresaId, Long volumeId, WmsVolumeItem i) {
        WmsVolume v = exigir(volumes.findByIdAndEmpresaIdAndDeletedAtIsNull(volumeId, empresaId), "Volume inexistente");
        if (!"ABERTO".equals(v.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Volume fechado");
        if (i.getProdutoId() == null || !produtos.findByIdAndEmpresaIdAndDeletedAtIsNull(i.getProdutoId(), empresaId).isPresent())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Produto inexistente para a empresa");
        if (i.getQuantidade() == null || i.getQuantidade().signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade embalada deve ser positiva");
        if (i.getOndaItemId() != null) {
            WmsOndaItem oi = exigir(itens.findByIdAndEmpresaIdAndDeletedAtIsNull(i.getOndaItemId(), empresaId), "Item da onda inexistente");
            if (!Objects.equals(oi.getProdutoId(), i.getProdutoId()))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Produto nao corresponde ao item da onda");
            // Nao embalar mais do que foi separado, somando todos os volumes.
            BigDecimal jaEmbalado = volumeItens.findByOndaItemIdAndEmpresaIdAndDeletedAtIsNull(oi.getId(), empresaId).stream()
                .map(x -> x.getQuantidade() == null ? BigDecimal.ZERO : x.getQuantidade())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal separado = oi.getQtdSeparada() == null ? BigDecimal.ZERO : oi.getQtdSeparada();
            if (jaEmbalado.add(i.getQuantidade()).compareTo(separado) > 0)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Quantidade embalada acima da separada");
        }
        i.setId(null);
        i.setEmpresaId(empresaId);
        i.setVolumeId(volumeId);
        return volumeItens.save(i);
    }
    @Override @Transactional public WmsVolume fecharVolume(Long empresaId, Long id) {
        WmsVolume v = exigir(volumes.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Volume inexistente");
        if ("FECHADO".equals(v.getStatus())) return v;
        if (volumeItens.findByVolumeIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId).isEmpty())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Volume vazio nao pode ser fechado");
        v.setStatus("FECHADO");
        return volumes.save(v);
    }
    @Override public Map<String, Object> conferir(Long empresaId, Long expedicaoId) {
        exigir(expedicoes.findByIdAndEmpresaIdAndDeletedAtIsNull(expedicaoId, empresaId), "Expedicao inexistente");
        Map<Long, BigDecimal> esperado = new LinkedHashMap<>();
        for (ExpedicaoEstoqueItem e : expedicaoItens.findByEmpresaIdAndExpedicaoIdOrderByIdAsc(empresaId, expedicaoId))
            esperado.merge(e.getProdutoId(), e.getQuantidade(), BigDecimal::add);
        Map<Long, BigDecimal> embalado = new LinkedHashMap<>();
        for (WmsVolume v : volumes.findByExpedicaoIdAndEmpresaIdAndDeletedAtIsNull(expedicaoId, empresaId))
            for (WmsVolumeItem i : volumeItens.findByVolumeIdAndEmpresaIdAndDeletedAtIsNull(v.getId(), empresaId))
                embalado.merge(i.getProdutoId(), i.getQuantidade(), BigDecimal::add);
        List<Map<String, Object>> diffs = new ArrayList<>();
        Set<Long> prods = new HashSet<>();
        prods.addAll(esperado.keySet());
        prods.addAll(embalado.keySet());
        boolean ok = true;
        for (Long p : prods) {
            BigDecimal e = esperado.getOrDefault(p, BigDecimal.ZERO);
            BigDecimal b = embalado.getOrDefault(p, BigDecimal.ZERO);
            if (e.compareTo(b) != 0) { ok = false; }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("produtoId", p);
            m.put("esperado", e);
            m.put("embalado", b);
            diffs.add(m);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("conferido", ok);
        m.put("diferencas", diffs);
        return m;
    }
    @Override @Transactional public Map<String, Object> finalizarExpedicao(Long empresaId, Long expedicaoId, String codigoRastreio) {
        Map<String, Object> conf = conferir(empresaId, expedicaoId);
        if (!Boolean.TRUE.equals(conf.get("conferido"))) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Conferencia com divergencia");
        ExpedicaoEstoque e = exigir(expedicoes.findByIdAndEmpresaIdAndDeletedAtIsNull(expedicaoId, empresaId), "Expedicao inexistente");
        if ("EXPEDIDA".equals(e.getStatus())) {
            Map<String, Object> jaFinalizada = new LinkedHashMap<>();
            jaFinalizada.put("expedicaoId", expedicaoId);
            jaFinalizada.put("status", "EXPEDIDA");
            return jaFinalizada;
        }
        if (!"EMBALAGEM".equals(e.getStatus()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Expedicao precisa estar em EMBALAGEM");
        List<WmsVolume> volumesFechados = volumes.findByExpedicaoIdAndEmpresaIdAndDeletedAtIsNull(expedicaoId, empresaId);
        if (volumesFechados.stream().anyMatch(v -> !"FECHADO".equals(v.getStatus())))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Todos os volumes precisam estar fechados");
        for (ExpedicaoEstoqueItem item : expedicaoItens.findByEmpresaIdAndExpedicaoIdOrderByIdAsc(empresaId, expedicaoId)) {
            item.setStatus("EXPEDIDO");
            expedicaoItens.save(item);
            if (item.getReservaId() != null) {
                reservas.findById(item.getReservaId()).filter(r -> empresaId.equals(r.getEmpresaId())).ifPresent(r -> {
                    if ("RESERVADA".equals(r.getStatus()) || "SEPARACAO".equals(r.getStatus()))
                        r.setStatus("CONSUMIDA");
                    reservas.save(r);
                });
            }
        }
        e.setStatus("EXPEDIDA");
        e.setCodigoRastreio(codigoRastreio);
        e.setDataExpedicao(LocalDateTime.now());
        expedicoes.save(e);
        documentoFluxo.ligar(empresaId, null, "PEDIDO_VENDA", e.getPedidoVendaId(), null,
                "EXPEDICAO", expedicaoId, codigoRastreio, "EXPEDE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expedicaoId", expedicaoId);
        m.put("status", "EXPEDIDA");
        return m;
    }
}
