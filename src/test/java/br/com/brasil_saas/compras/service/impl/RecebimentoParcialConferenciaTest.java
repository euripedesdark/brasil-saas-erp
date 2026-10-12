package br.com.brasil_saas.compras.service.impl;

import br.com.brasil_saas.compras.model.*;
import br.com.brasil_saas.compras.repository.*;
import br.com.brasil_saas.compras.service.ConferenciaFaturaCompraService.Request;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import br.com.brasil_saas.fiscal.model.*;
import br.com.brasil_saas.fiscal.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecebimentoParcialConferenciaTest {
    @Mock ConferenciaFaturaCompraRepository conferencias;
    @Mock ConferenciaFaturaCompraItemRepository itens;
    @Mock PedidoCompraRepository pedidos;
    @Mock RecebimentoCompraRepository recebimentos;
    @Mock RecebimentoCompraItemRepository recebidos;
    @Mock TituloRepository titulos;
    @Mock NfeRepository notas;
    @Mock NfeItemRepository notasItens;
    @InjectMocks ConferenciaFaturaCompraServiceImpl service;
    private Nfe nf;
    private RecebimentoCompraItem recebido;
    private NfeItem faturado;

    @BeforeEach
    void preparar() {
        var pedido = new PedidoCompra();
        pedido.setId(1L); pedido.setEmpresaId(2L); pedido.setStatus("PARCIAL");
        pedido.setValorTotal(new BigDecimal("150"));
        pedido.getItens().add(item(6L, "10"));
        pedido.getItens().add(item(7L, "5")); // produto ainda nao entregue
        when(pedidos.findByIdForUpdate(1L)).thenReturn(Optional.of(pedido));
        var rec = new RecebimentoCompra();
        rec.setId(4L); rec.setPedidoId(1L); rec.setValorTotal(new BigDecimal("40"));
        when(recebimentos.findByIdAndEmpresaIdAndDeletedAtIsNull(4L, 2L)).thenReturn(Optional.of(rec));
        recebido = new RecebimentoCompraItem();
        recebido.setProdutoId(6L); recebido.setQuantidadeRecebida(new BigDecimal("4"));
        recebido.setValorUnitario(BigDecimal.TEN);
        when(recebidos.findByEmpresaIdAndRecebimentoIdAndDeletedAtIsNullOrderByIdAsc(2L, 4L))
                .thenReturn(List.of(recebido));
        nf = new Nfe();
        nf.setId(5L); nf.setTipoOperacao("E"); nf.setPedidoCompraId(1L);
        nf.setStatus("AUTORIZADA"); nf.setValorTotal(new BigDecimal("40"));
        when(notas.findByIdAndEmpresaIdAndDeletedAtIsNull(5L, 2L)).thenReturn(Optional.of(nf));
        faturado = new NfeItem();
        faturado.setProdutoId(6L); faturado.setQuantidade(new BigDecimal("4"));
        faturado.setValorUnitario(BigDecimal.TEN); faturado.setValorTotal(new BigDecimal("40"));
        when(notasItens.findByNfeIdOrderByNumeroItem(5L)).thenReturn(List.of(faturado));
        when(conferencias.save(any())).thenAnswer(i -> {
            ConferenciaFaturaCompra c = i.getArgument(0); c.setId(8L); return c;
        });
    }

    private ItemPedidoCompra item(Long produto, String qtd) {
        var item = new ItemPedidoCompra(); item.setProdutoId(produto);
        item.setQuantidade(new BigDecimal(qtd)); item.setValorUnitario(BigDecimal.TEN);
        return item;
    }

    private ConferenciaFaturaCompra conferir(String valor, String tolerancia) {
        return service.conferir(2L, new Request(1L, new BigDecimal(valor), 4L, null, 5L,
                new BigDecimal(tolerancia)));
    }

    @Test
    void aprovaEntregaParcialSemExigirProdutoAindaNaoEntregue() {
        var c = conferir("40", "0");
        assertEquals("APROVADA", c.getStatus());
        assertEquals(new BigDecimal("150"), c.getValorPedido());
        assertEquals(new BigDecimal("40"), c.getValorFatura());
        verify(itens).saveAll(argThat(l -> {
            var lista = (List<ConferenciaFaturaCompraItem>) l;
            return lista.size()==1 && Boolean.TRUE.equals(lista.get(0).getConforme())
                    && lista.get(0).getQuantidadePedida().compareTo(new BigDecimal("10"))==0
                    && lista.get(0).getQuantidadeRecebida().compareTo(new BigDecimal("4"))==0;
        }));
    }

    @Test
    void naoAceitaValorDoPedidoInteiroParaRecebimentoParcial() {
        assertEquals("DIVERGENTE", conferir("150", "0").getStatus());
    }

    @Test
    void totalFiscalDiferenteImpedeAprovacaoMesmoComItensIguais() {
        nf.setValorTotal(new BigDecimal("45"));
        var c = conferir("40", "0");
        assertEquals("DIVERGENTE", c.getStatus());
        assertTrue(c.getDivergencia().contains("total NF-e=45"));
    }

    @Test
    void documentoSemTotalNaoPodeSerAprovado() {
        nf.setValorTotal(null);
        assertEquals("DIVERGENTE", conferir("40", "0").getStatus());
    }

    @Test
    void toleranciaAceitaLimiteExatoDoTotalFiscal() {
        nf.setValorTotal(new BigDecimal("40.01"));
        assertEquals("APROVADA", conferir("40", "0.01").getStatus());
    }

    @Test
    void toleranciaNaoAceitaValorAcimaDoLimite() {
        nf.setValorTotal(new BigDecimal("40.02"));
        assertEquals("DIVERGENTE", conferir("40", "0.01").getStatus());
    }

    @Test
    void quantidadeFaturadaMaiorQueRecebidaContinuaBloqueada() {
        faturado.setQuantidade(new BigDecimal("5"));
        assertEquals("DIVERGENTE", conferir("40", "0").getStatus());
    }

    @Test
    void conferenciaVaziaNaoAprovaSomentePelosTotais() {
        when(recebidos.findByEmpresaIdAndRecebimentoIdAndDeletedAtIsNullOrderByIdAsc(2L, 4L))
                .thenReturn(List.of());
        when(notasItens.findByNfeIdOrderByNumeroItem(5L)).thenReturn(List.of());
        var c = conferir("40", "0");
        assertEquals("DIVERGENTE", c.getStatus());
        assertTrue(c.getDivergencia().contains("sem itens"));
    }

    @Test
    void recebimentoTotalmenteConsumidoBloqueiaNovaNf() {
        var anterior = new ConferenciaFaturaCompra(); anterior.setId(3L);
        anterior.setStatus("APROVADA"); anterior.setRecebimentoId(4L);
        anterior.setNfeId(6L); anterior.setValorFatura(new BigDecimal("40"));
        when(conferencias.findConsumoAcumuladoRecebimento(2L, 4L, 5L)).thenReturn(List.of(anterior));
        var consumida = new ConferenciaFaturaCompraItem();
        consumida.setProdutoId(6L); consumida.setQuantidadeFaturada(new BigDecimal("4"));
        when(itens.findByEmpresaIdAndConferenciaIdAndDeletedAtIsNullOrderByNumeroItemAsc(2L, 3L))
                .thenReturn(List.of(consumida));
        var c = conferir("40", "0");
        assertEquals("DIVERGENTE", c.getStatus());
        assertTrue(c.getDivergencia().contains("consumo acumulado excedido"));
    }
    @Test
    void mesmaNfComOutroRecebimentoContinuaBloqueada() {
        var anterior = new ConferenciaFaturaCompra(); anterior.setId(3L);
        anterior.setNfeId(5L); anterior.setRecebimentoId(99L);
        when(conferencias.findConsumosConflitantes(2L, 4L, 5L)).thenReturn(List.of(anterior));
        var c = conferir("40", "0");
        assertEquals("DIVERGENTE", c.getStatus());
        assertTrue(c.getDivergencia().contains("ja consumida pela conferencia 3"));
    }

    @Test
    void precoDentroDaToleranciaPorItemAprova() {
        faturado.setValorUnitario(new BigDecimal("10.05"));
        faturado.setValorTotal(new BigDecimal("40.20"));
        var c = conferir("40.20", "0.50");
        assertEquals("APROVADA", c.getStatus());
        List<ConferenciaFaturaCompraItem> linhas = capturarItensSalvos();
        assertEquals(1, linhas.size());
        assertTrue(linhas.get(0).getConforme());
        assertEquals(new BigDecimal("0.50"), linhas.get(0).getTolerancia());
    }

    @Test
    void precoAcimaDaToleranciaPorItemDiverge() {
        faturado.setValorUnitario(new BigDecimal("11"));
        faturado.setValorTotal(new BigDecimal("44"));
        var c = conferir("44", "0.50");
        assertEquals("DIVERGENTE", c.getStatus());
        List<ConferenciaFaturaCompraItem> linhas = capturarItensSalvos();
        assertTrue(linhas.stream().anyMatch(x -> "PRECO_DIVERGENTE".equals(x.getTipoDivergencia())));
    }

    /** Centraliza o ArgumentCaptor tipado para nao gerar unchecked em cada teste. */
    @SuppressWarnings("unchecked")
    private List<ConferenciaFaturaCompraItem> capturarItensSalvos() {
        ArgumentCaptor<List<ConferenciaFaturaCompraItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(itens).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void reconferenciaDoMesmoParSemConflitoContinuaAprovada() {

        when(conferencias.findConsumosConflitantes(2L, 4L, 5L)).thenReturn(List.of());
        assertEquals("APROVADA", conferir("40", "0").getStatus());
    }
}
