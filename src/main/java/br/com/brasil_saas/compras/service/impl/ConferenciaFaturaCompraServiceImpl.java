package br.com.brasil_saas.compras.service.impl;

import br.com.brasil_saas.compras.model.ConferenciaFaturaCompra;
import br.com.brasil_saas.compras.model.ConferenciaFaturaCompraItem;
import br.com.brasil_saas.compras.model.ItemPedidoCompra;
import br.com.brasil_saas.compras.model.PedidoCompra;
import br.com.brasil_saas.compras.model.RecebimentoCompra;
import br.com.brasil_saas.compras.model.RecebimentoCompraItem;
import br.com.brasil_saas.compras.repository.ConferenciaFaturaCompraItemRepository;
import br.com.brasil_saas.compras.repository.ConferenciaFaturaCompraRepository;
import br.com.brasil_saas.compras.repository.PedidoCompraRepository;
import br.com.brasil_saas.compras.repository.RecebimentoCompraItemRepository;
import br.com.brasil_saas.compras.repository.RecebimentoCompraRepository;
import br.com.brasil_saas.compras.repository.ToleranciaConferenciaRepository;
import br.com.brasil_saas.compras.service.ConferenciaFaturaCompraService;
import br.com.brasil_saas.core.service.DocumentoFluxoService;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import br.com.brasil_saas.fiscal.model.Nfe;
import br.com.brasil_saas.fiscal.model.NfeItem;
import br.com.brasil_saas.fiscal.repository.NfeItemRepository;
import br.com.brasil_saas.fiscal.repository.NfeRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

/**
 * Conferencia 3-way da compra: pedido x recebimento x NF-e de entrada.
 *
 * <p>A conferencia comparava so' os totais: uma fatura de mesmo valor total
 * passava mesmo com item trocado, quantidade diferente ou preco unitario
 * outro. Agora cada item do pedido e' confrontado com a linha do recebimento
 * e com a linha da NF-e, e a divergencia fica gravada linha a linha.
 *
 * <p>A ordem de severidade define o tipo gravado quando a linha acumula mais
 * de um problema: item sem fatura, faturado acima do recebido, recebido acima
 * do pedido, quantidade diferente, preco diferente. O texto da divergencia
 * lista todos os problemas encontrados, nao so' o mais grave.
 */
@Service
@RequiredArgsConstructor
public class ConferenciaFaturaCompraServiceImpl implements ConferenciaFaturaCompraService {

    static final String OK = "OK";
    static final String ITEM_NAO_FATURADO = "ITEM_NAO_FATURADO";
    static final String QUANTIDADE_FATURADA_MAIOR_QUE_RECEBIDA = "QUANTIDADE_FATURADA_MAIOR_QUE_RECEBIDA";
    static final String QUANTIDADE_RECEBIDA_MAIOR_QUE_PEDIDO = "QUANTIDADE_RECEBIDA_MAIOR_QUE_PEDIDO";
    static final String QUANTIDADE_FATURADA_DIVERGENTE = "QUANTIDADE_FATURADA_DIVERGENTE";
    static final String PRECO_DIVERGENTE = "PRECO_DIVERGENTE";
    static final String ITEM_NAO_PEDIDO = "ITEM_NAO_PEDIDO";
    static final String CONSUMO_ACUMULADO_EXCEDIDO = "CONSUMO_ACUMULADO_EXCEDIDO";

    BigDecimal toleranciaCadastrada(Long empresaId, PedidoCompra pedido, BigDecimal valorPedido) {
        var regras = toleranciaRepository.findByEmpresaIdAndAtivoTrueAndDeletedAtIsNullOrderById(empresaId);
        if (regras == null || regras.isEmpty()) return BigDecimal.ZERO;
        java.util.Set<Long> produtos = new java.util.HashSet<>();
        if (pedido.getItens() != null) for (var it : pedido.getItens()) {
            if (it.getProdutoId() != null) produtos.add(it.getProdutoId());
        }
        var regra = regras.stream()
                .filter(r -> r.getProdutoId() != null && produtos.size() == 1 && produtos.contains(r.getProdutoId()))
                .findFirst()
                .orElseGet(() -> regras.stream().filter(r -> r.getProdutoId() == null).findFirst().orElse(null));
        if (regra == null || regra.getLimite() == null) return BigDecimal.ZERO;
        if ("PERCENTUAL".equals(regra.getTipo())) {
            return nz(valorPedido).multiply(regra.getLimite()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        return regra.getLimite();
    }

    private final ConferenciaFaturaCompraRepository repository;
    private final ConferenciaFaturaCompraItemRepository itemRepository;
    private final PedidoCompraRepository pedidoRepository;
    private final RecebimentoCompraRepository recebimentoRepository;
    private final RecebimentoCompraItemRepository recebimentoItemRepository;
    private final TituloRepository tituloRepository;
    private final NfeRepository nfeRepository;
    private final NfeItemRepository nfeItemRepository;
    private final DocumentoFluxoService documentoFluxoService;
    private final ToleranciaConferenciaRepository toleranciaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ConferenciaFaturaCompra> listar(Long empresaId) {
        return repository.findByEmpresaIdOrderByCreatedAtDesc(empresaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConferenciaFaturaCompraItem> listarItens(Long empresaId, Long conferenciaId) {
        repository.findByIdAndEmpresaIdAndDeletedAtIsNull(conferenciaId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conferencia nao encontrada"));
        return itemRepository.findByEmpresaIdAndConferenciaIdAndDeletedAtIsNullOrderByNumeroItemAsc(empresaId, conferenciaId);
    }

    @Override
    @Transactional
    public ConferenciaFaturaCompra conferir(Long empresaId, Request request) {
        PedidoCompra pedido = pedidoRepository.findByIdForUpdate(request.pedidoId())
                .filter(p -> p.getEmpresaId().equals(empresaId))
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de compra nao encontrado"));

        if ("CANCELADO".equals(pedido.getStatus())) {
            throw new BusinessException("Pedido de compra cancelado nao pode entrar em conferencia");
        }
        if (request.recebimentoId() == null) {
            throw new BusinessException("O 3-way match exige um recebimento");
        }
        if (request.nfeId() == null) {
            throw new BusinessException("O 3-way match exige o documento fiscal de entrada");
        }

        if (pedido.getTituloId() != null && request.tituloId() != null
                && !pedido.getTituloId().equals(request.tituloId())) {
            throw new BusinessException("Titulo informado nao pertence ao pedido de compra");
        }
        Long tituloId = pedido.getTituloId() != null ? pedido.getTituloId() : request.tituloId();
        if (tituloId != null) {
            // A baixa usa a mesma trava. Uma conferencia nao pode ser gravada
            // enquanto o pagamento decide se este titulo esta liberado.
            var titulo = tituloRepository.findForUpdate(tituloId, empresaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Titulo informado nao encontrado"));
            if (!"P".equalsIgnoreCase(titulo.getTipo())) {
                throw new BusinessException("Conferencia de compra exige titulo a pagar");
            }
            if ("CANCELADO".equalsIgnoreCase(titulo.getStatus())
                    || "BAIXADO".equalsIgnoreCase(titulo.getStatus())
                    || (titulo.getValorOriginal() != null && titulo.getValorSaldo() != null
                        && titulo.getValorSaldo().compareTo(titulo.getValorOriginal()) < 0)) {
                throw new BusinessException("Titulo cancelado ou com pagamento nao pode ser reconferido");
            }
        }

        RecebimentoCompra recebimento = recebimentoRepository
                .findByIdAndEmpresaIdAndDeletedAtIsNull(request.recebimentoId(), empresaId)
                .filter(r -> pedido.getId().equals(r.getPedidoId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recebimento nao encontrado para o pedido informado"));

        Nfe nfe = nfeRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(request.nfeId(), empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento fiscal nao encontrado"));
        if (!"E".equalsIgnoreCase(nfe.getTipoOperacao())) {
            throw new BusinessException("Documento fiscal deve ser de entrada");
        }
        if (nfe.getPedidoCompraId() != null && !pedido.getId().equals(nfe.getPedidoCompraId())) {
            throw new BusinessException("Documento fiscal vinculado a outro pedido");
        }
        if (nfe.getStatus() != null
                && !("AUTORIZADA".equalsIgnoreCase(nfe.getStatus()) || "AUTORIZADO".equalsIgnoreCase(nfe.getStatus()))) {
            throw new BusinessException("Documento fiscal ainda nao esta autorizado");
        }

        BigDecimal valorPedido = nz(pedido.getValorTotal());
        BigDecimal valorRecebido = nz(recebimento.getValorTotal());
        BigDecimal valorFatura = nz(request.valorFatura());
        BigDecimal tolerancia = request.tolerancia() != null ? nz(request.tolerancia())
                : toleranciaCadastrada(empresaId, pedido, valorPedido);
        BigDecimal limite = tolerancia.abs();

        // Rateio: um recebimento pode ser faturado por varias NFs, sem
        // ultrapassar o recebido. O consumo acumulado e a soma do valor das
        // conferencias APROVADAS anteriores deste recebimento (outras NFs).
        // A reavaliacao do mesmo par substitui a anterior e nao entra aqui.
        var anteriores = repository.findConsumoAcumuladoRecebimento(empresaId, recebimento.getId(), nfe.getId());
        BigDecimal consumidoValor = BigDecimal.ZERO;
        Map<Long, BigDecimal> consumidoQtd = new LinkedHashMap<>();
        if (anteriores != null) for (var anterior : anteriores) {
            consumidoValor = consumidoValor.add(nz(anterior.getValorFatura()));
            for (var consumida : itemRepository.findByEmpresaIdAndConferenciaIdAndDeletedAtIsNullOrderByNumeroItemAsc(empresaId, anterior.getId())) {
                if (consumida.getProdutoId() == null) continue;
                consumidoQtd.merge(consumida.getProdutoId(), nz(consumida.getQuantidadeFaturada()), BigDecimal::add);
            }
        }
        BigDecimal disponivelValor = valorRecebido.subtract(consumidoValor);
        // Uma NF pode faturar um recebimento parcial, sem faturar o pedido todo.
        // O total fiscal e a fonte do valor, nao somente o informado na tela.
        // No rateio, a NF precisa caber na parte ainda nao consumida.
        boolean totaisOk = nfe.getValorTotal() != null
                && valorFatura.subtract(nfe.getValorTotal()).abs().compareTo(limite) <= 0
                && valorFatura.subtract(disponivelValor).compareTo(limite) <= 0
                && valorRecebido.subtract(valorPedido).compareTo(limite) <= 0;

        List<ConferenciaFaturaCompraItem> itens = compararItens(
                empresaId,
                pedido.getItens(),
                recebimentoItemRepository.findByEmpresaIdAndRecebimentoIdAndDeletedAtIsNullOrderByIdAsc(
                        empresaId, recebimento.getId()),
                nfeItemRepository.findByNfeIdOrderByNumeroItem(nfe.getId()));
        // Tolerancia de preco por item: preenche o campo da linha (V173) e
        // absolve divergencia de preco dentro do limite. Quantidade continua
        // exata: so o preco usa tolerancia.
        for (var linha : itens) {
            linha.setTolerancia(tolerancia);
            if (PRECO_DIVERGENTE.equals(linha.getTipoDivergencia())
                    && linha.getValorUnitarioFaturado() != null
                    && linha.getValorUnitarioPedido() != null
                    && linha.getValorUnitarioFaturado().subtract(linha.getValorUnitarioPedido()).abs().compareTo(limite) <= 0) {
                linha.setConforme(true);
                linha.setStatus("APROVADA");
                linha.setTipoDivergencia(OK);
                linha.setDivergencia(null);
            }
        }

        // Consumo acumulado por produto: o faturado nesta NF nao pode passar
        // do recebido menos o ja faturado por NFs anteriores.
        Set<Long> excedidos = new LinkedHashSet<>();
        for (var linha : itens) {
            if (linha.getProdutoId() == null) continue;
            BigDecimal disponivel = nz(linha.getQuantidadeRecebida())
                    .subtract(consumidoQtd.getOrDefault(linha.getProdutoId(), BigDecimal.ZERO));
            if (nz(linha.getQuantidadeFaturada()).compareTo(disponivel) > 0) {
                linha.setConforme(false);
                linha.setStatus("DIVERGENTE");
                linha.setTipoDivergencia(CONSUMO_ACUMULADO_EXCEDIDO);
                linha.setDivergencia("Faturado " + linha.getQuantidadeFaturada()
                        + " acima do disponivel " + disponivel
                        + " no recebimento (consumo acumulado)");
                excedidos.add(linha.getProdutoId());
            }
        }

        long divergentes = itens.stream().filter(i -> !Boolean.TRUE.equals(i.getConforme())).count();
        // Uma NF pertence a um unico recebimento: a mesma NF com outro
        // recebimento continua bloqueada. O reuso do recebimento por outra
        // NF e o rateio acima, nao um conflito.
        var consumos = repository.findConsumosConflitantes(empresaId, recebimento.getId(), nfe.getId());
        var nfReaproveitada = consumos != null ? consumos.stream()
                .filter(c -> nfe.getId().equals(c.getNfeId())
                        && (c.getRecebimentoId() == null || !c.getRecebimentoId().equals(recebimento.getId())))
                .findFirst().orElse(null) : null;
        boolean aprovada = totaisOk && !itens.isEmpty() && divergentes == 0 && nfReaproveitada == null;

        ConferenciaFaturaCompra conferencia = new ConferenciaFaturaCompra();
        conferencia.setEmpresaId(empresaId);
        conferencia.setPedidoId(pedido.getId());
        conferencia.setRecebimentoId(recebimento.getId());
        conferencia.setTituloId(tituloId);
        conferencia.setNfeId(nfe.getId());
        conferencia.setValorPedido(valorPedido);
        conferencia.setValorRecebido(valorRecebido);
        conferencia.setValorFatura(valorFatura);
        conferencia.setTolerancia(tolerancia);
        conferencia.setStatus(aprovada ? "APROVADA" : "DIVERGENTE");
        conferencia.setDivergencia(aprovada ? null
                : descrever(valorPedido, valorRecebido, valorFatura, tolerancia, totaisOk, divergentes, itens.size())
                    + ", total NF-e=" + nfe.getValorTotal()
                    + (nfReaproveitada != null ? " | NF-e ja consumida pela conferencia "
                        + nfReaproveitada.getId() : "")
                    + (!excedidos.isEmpty() ? " | consumo acumulado excedido no recebimento "
                        + recebimento.getId() + " (produtos " + excedidos + ")" : ""));

        ConferenciaFaturaCompra salva = repository.save(conferencia);

        for (ConferenciaFaturaCompraItem item : itens) {
            item.setConferenciaId(salva.getId());
        }
        itemRepository.saveAll(itens);
        return salva;
    }

    @Override
    @Transactional
    public ConferenciaFaturaCompra aprovarExcepcional(Long empresaId, Long userId, Long conferenciaId, String motivo) {
        String justo = motivo == null ? "" : motivo.trim();
        if (justo.length() < 10 || justo.length() > 500) {
            throw new BusinessException("Motivo obrigatorio, entre 10 e 500 caracteres");
        }
        ConferenciaFaturaCompra conferencia = repository.findByIdForUpdate(conferenciaId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conferencia nao encontrada"));
        if (!"DIVERGENTE".equals(conferencia.getStatus())) {
            throw new BusinessException("Somente conferencia divergente aceita aprovacao excepcional");
        }
        long posteriores = repository.countPosterioresMesmoPar(empresaId, conferencia.getPedidoId(),
                conferencia.getNfeId(), conferencia.getRecebimentoId(), conferencia.getId());
        if (posteriores > 0) {
            throw new BusinessException("Conferencia superada por reavaliacao posterior");
        }
        conferencia.setStatus("APROVADA");
        String base = conferencia.getDivergencia() == null ? "" : conferencia.getDivergencia() + " ";
        conferencia.setDivergencia((base + "| APROVACAO EXCEPCIONAL por " + userId
                + " em " + java.time.LocalDateTime.now() + ": " + justo).trim());
        ConferenciaFaturaCompra salva = repository.save(conferencia);
        documentoFluxoService.ligar(empresaId, userId,
                "CONFERENCIA_COMPRA", salva.getId(), null,
                "PEDIDO_COMPRA", salva.getPedidoId(), null,
                "APROVACAO_EXCEPCIONAL");
        return salva;
    }

    /**
     * Confronta item a item o pedido, o recebimento e a NF-e de entrada.
     *
     * <p>O casamento e' pelo produto. O item do pedido sem produto cadastrado
     * (item de texto livre) cai para o numero do item na NF-e, que e' a unica
     * chave que sobra.
     */
    static List<ConferenciaFaturaCompraItem> compararItens(Long empresaId,
                                                          List<ItemPedidoCompra> itensPedido,
                                                          List<RecebimentoCompraItem> itensRecebimento,
                                                          List<NfeItem> itensNfe) {
        // Somar linhas equivalentes antes do confronto: um produto pode aparecer
        // mais de uma vez, inclusive com precos diferentes. Nao sobrescrever linhas
        // nem reutilizar a mesma quantidade para dois itens do pedido.
        Map<String, ItemPedidoCompra> pedidos = new LinkedHashMap<>();
        for (ItemPedidoCompra item : itensPedido) {
            String chave = chave(item.getProdutoId(), item.getNumeroItem(), item.getValorUnitario());
            ItemPedidoCompra grupo = pedidos.get(chave);
            if (grupo == null) {
                grupo = new ItemPedidoCompra();
                grupo.setId(item.getId());
                grupo.setProdutoId(item.getProdutoId());
                grupo.setNumeroItem(item.getNumeroItem());
                grupo.setDescricao(item.getDescricao());
                grupo.setValorUnitario(nz(item.getValorUnitario()));
                grupo.setQuantidade(BigDecimal.ZERO);
                pedidos.put(chave, grupo);
            } else {
                grupo.setId(null); // agregado nao representa um unico item original
            }
            grupo.setQuantidade(grupo.getQuantidade().add(nz(item.getQuantidade())));
        }
        Map<String, RecebimentoCompraItem> recebidos = new LinkedHashMap<>();
        for (RecebimentoCompraItem item : itensRecebimento) {
            String chave = chave(item.getProdutoId(), null, item.getValorUnitario());
            RecebimentoCompraItem grupo = recebidos.get(chave);
            if (grupo == null) {
                grupo = new RecebimentoCompraItem();
                grupo.setId(item.getId());
                grupo.setProdutoId(item.getProdutoId());
                grupo.setValorUnitario(nz(item.getValorUnitario()));
                grupo.setQuantidadeRecebida(BigDecimal.ZERO);
                recebidos.put(chave, grupo);
            } else {
                grupo.setId(null);
            }
            grupo.setQuantidadeRecebida(grupo.getQuantidadeRecebida().add(nz(item.getQuantidadeRecebida())));
        }
        Map<String, NfeItem> faturados = new LinkedHashMap<>();
        for (NfeItem item : itensNfe) {
            String chave = chave(item.getProdutoId(), item.getNumeroItem(), item.getValorUnitario());
            NfeItem grupo = faturados.get(chave);
            if (grupo == null) {
                grupo = new NfeItem();
                grupo.setId(item.getId());
                grupo.setProdutoId(item.getProdutoId());
                grupo.setNumeroItem(item.getNumeroItem());
                grupo.setValorUnitario(nz(item.getValorUnitario()));
                grupo.setQuantidade(BigDecimal.ZERO);
                grupo.setValorTotal(BigDecimal.ZERO);
                faturados.put(chave, grupo);
            } else {
                grupo.setId(null);
            }
            grupo.setQuantidade(grupo.getQuantidade().add(nz(item.getQuantidade())));
            grupo.setValorTotal(grupo.getValorTotal().add(item.getValorTotal() == null
                    ? total(item.getQuantidade(), item.getValorUnitario()) : item.getValorTotal()));
        }
        List<ConferenciaFaturaCompraItem> linhas = new ArrayList<>();
        // Primeiro consumir precos exatos, para uma linha divergente nao roubar
        // o documento de outra linha cujo preco confere.
        for (boolean exato : new boolean[]{true, false}) {
            var pendentes = pedidos.entrySet().iterator();
            while (pendentes.hasNext()) {
                var entry = pendentes.next();
                ItemPedidoCompra pedido = entry.getValue();
                if (exato && !faturados.containsKey(entry.getKey())) continue;
                String prefixo = identidade(pedido.getProdutoId(), pedido.getNumeroItem()) + "@";
                String chaveNfe = faturados.containsKey(entry.getKey()) ? entry.getKey()
                        : faturados.keySet().stream().filter(k -> k.startsWith(prefixo)).findFirst().orElse(null);
                String chaveRec = recebidos.containsKey(entry.getKey()) ? entry.getKey()
                        : recebidos.keySet().stream().filter(k -> k.startsWith(prefixo)).findFirst().orElse(null);
                RecebimentoCompraItem recebido = recebidos.remove(chaveRec);
                NfeItem faturado = faturados.remove(chaveNfe);
                // Itens ainda nao entregues nao fazem parte desta conferencia.
                if (faturado != null || (recebido != null && nz(recebido.getQuantidadeRecebida()).signum() != 0)) {
                    linhas.add(linha(empresaId, pedido, recebido, faturado));
                }
                pendentes.remove();
            }
        }
        // Toda linha fiscal nao consumida e' divergente, inclusive texto livre.
        faturados.values().forEach(item -> linhas.add(linhaNaoPedida(empresaId, item)));
        recebidos.values().stream().filter(item -> nz(item.getQuantidadeRecebida()).signum() != 0)
                .forEach(item -> {
                    var linha = new ConferenciaFaturaCompraItem();
                    linha.setEmpresaId(empresaId);
                    linha.setProdutoId(item.getProdutoId());
                    linha.setRecebimentoItemId(item.getId());
                    linha.setQuantidadeRecebida(nz(item.getQuantidadeRecebida()));
                    linha.setValorUnitarioRecebido(nz(item.getValorUnitario()));
                    linha.setValorTotalRecebido(total(item.getQuantidadeRecebida(), item.getValorUnitario()));
                    linha.setConforme(false);
                    linha.setStatus("DIVERGENTE");
                    linha.setTipoDivergencia(ITEM_NAO_PEDIDO);
                    linha.setDivergencia("Linha do recebimento sem correspondencia no pedido");
                    linhas.add(linha);
                });
        return linhas;
    }

    private static String identidade(Long produtoId, Integer numeroItem) {
        return produtoId == null ? "ITEM:" + numeroItem : "PRODUTO:" + produtoId;
    }

    private static String chave(Long produtoId, Integer numeroItem, BigDecimal preco) {
        return identidade(produtoId, numeroItem) + "@" + nz(preco).stripTrailingZeros().toPlainString();
    }

    private static ConferenciaFaturaCompraItem linha(Long empresaId, ItemPedidoCompra itemPedido,
                                                     RecebimentoCompraItem itemRecebimento, NfeItem itemNfe) {
        BigDecimal quantidadePedida = nz(itemPedido.getQuantidade());
        BigDecimal valorUnitarioPedido = nz(itemPedido.getValorUnitario());
        BigDecimal quantidadeRecebida = itemRecebimento == null
                ? BigDecimal.ZERO : nz(itemRecebimento.getQuantidadeRecebida());
        BigDecimal valorUnitarioRecebido = itemRecebimento == null
                ? BigDecimal.ZERO : nz(itemRecebimento.getValorUnitario());
        BigDecimal quantidadeFaturada = itemNfe == null ? BigDecimal.ZERO : nz(itemNfe.getQuantidade());
        BigDecimal valorUnitarioFaturado = itemNfe == null ? BigDecimal.ZERO : nz(itemNfe.getValorUnitario());

        List<String> problemas = new ArrayList<>();
        String tipo = OK;

        if (itemNfe == null) {
            tipo = ITEM_NAO_FATURADO;
            problemas.add("o item nao tem linha na NF-e de entrada");
        } else {
            // Rateio: faturado menor que recebido e parcial valida; o teto e
            // dado pelo consumo acumulado em conferir(), nao pela igualdade.
            if (quantidadeFaturada.compareTo(quantidadeRecebida) > 0) {
                tipo = QUANTIDADE_FATURADA_MAIOR_QUE_RECEBIDA;
                problemas.add("faturado " + quantidadeFaturada + " maior que o recebido " + quantidadeRecebida);
            }
            if (valorUnitarioFaturado.compareTo(valorUnitarioPedido) != 0) {
                if (OK.equals(tipo)) {
                    tipo = PRECO_DIVERGENTE;
                }
                problemas.add("preco faturado " + valorUnitarioFaturado
                        + " diferente do pedido " + valorUnitarioPedido);
            }
        }

        if (quantidadeRecebida.compareTo(quantidadePedida) > 0) {
            if (OK.equals(tipo)) {
                tipo = QUANTIDADE_RECEBIDA_MAIOR_QUE_PEDIDO;
            }
            problemas.add("recebido " + quantidadeRecebida + " maior que o pedido " + quantidadePedida);
        }

        ConferenciaFaturaCompraItem linha = new ConferenciaFaturaCompraItem();
        linha.setEmpresaId(empresaId);
        linha.setProdutoId(itemPedido.getProdutoId());
        linha.setPedidoItemId(itemPedido.getId());
        linha.setRecebimentoItemId(itemRecebimento == null ? null : itemRecebimento.getId());
        linha.setNfeItemId(itemNfe == null ? null : itemNfe.getId());
        linha.setNumeroItem(itemPedido.getNumeroItem());
        linha.setDescricao(itemPedido.getDescricao());
        linha.setQuantidadePedida(quantidadePedida);
        linha.setQuantidadeRecebida(quantidadeRecebida);
        linha.setQuantidadeFaturada(quantidadeFaturada);
        linha.setValorUnitarioPedido(valorUnitarioPedido);
        linha.setValorUnitarioRecebido(valorUnitarioRecebido);
        linha.setValorUnitarioFaturado(valorUnitarioFaturado);
        linha.setValorTotalPedido(total(quantidadePedida, valorUnitarioPedido));
        linha.setValorTotalRecebido(total(quantidadeRecebida, valorUnitarioRecebido));
        linha.setValorTotalFaturado(itemNfe != null && itemNfe.getValorTotal() != null
                ? itemNfe.getValorTotal()
                : total(quantidadeFaturada, valorUnitarioFaturado));
        linha.setConforme(problemas.isEmpty());
        linha.setTipoDivergencia(problemas.isEmpty() ? OK : tipo);
        linha.setStatus(problemas.isEmpty() ? "APROVADA" : "DIVERGENTE");
        linha.setDivergencia(problemas.isEmpty() ? null : String.join("; ", problemas));
        return linha;
    }

    private static ConferenciaFaturaCompraItem linhaNaoPedida(Long empresaId, NfeItem itemNfe) {
        ConferenciaFaturaCompraItem linha = new ConferenciaFaturaCompraItem();
        linha.setEmpresaId(empresaId);
        linha.setProdutoId(itemNfe.getProdutoId());
        linha.setNumeroItem(itemNfe.getNumeroItem());
        linha.setDescricao("Item da NF-e de entrada sem linha no pedido de compra");
        linha.setQuantidadeFaturada(nz(itemNfe.getQuantidade()));
        linha.setValorUnitarioFaturado(nz(itemNfe.getValorUnitario()));
        linha.setValorTotalFaturado(itemNfe.getValorTotal() != null
                ? itemNfe.getValorTotal()
                : total(nz(itemNfe.getQuantidade()), nz(itemNfe.getValorUnitario())));
        linha.setConforme(false);
        linha.setTipoDivergencia(ITEM_NAO_PEDIDO);
        linha.setDivergencia("o produto " + itemNfe.getProdutoId()
                + " esta na NF-e de entrada e nao tem linha no pedido de compra");
        return linha;
    }

    private static String descrever(BigDecimal valorPedido, BigDecimal valorRecebido, BigDecimal valorFatura,
                                    BigDecimal tolerancia, boolean totaisOk, long divergentes, int totalItens) {
        StringBuilder texto = new StringBuilder();
        if (totalItens == 0) texto.append("Conferencia sem itens do recebimento ou da NF-e");
        if (!totaisOk) {
            if (!texto.isEmpty()) texto.append(" | ");
            texto.append("Divergencia 3-way: pedido=").append(valorPedido)
                    .append(", recebido=").append(valorRecebido)
                    .append(", fatura=").append(valorFatura)
                    .append(", tolerancia=").append(tolerancia);
        }
        if (divergentes > 0) {
            if (!texto.isEmpty()) {
                texto.append(" | ");
            }
            texto.append(divergentes).append(" de ").append(totalItens).append(" item(ns) com divergencia");
        }
        return texto.toString();
    }

    private static BigDecimal total(BigDecimal quantidade, BigDecimal valorUnitario) {
        return nz(quantidade).multiply(nz(valorUnitario)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
