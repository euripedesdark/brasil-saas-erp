package br.com.brasil_saas.compras.service.impl;

import br.com.brasil_saas.cadastro.model.Fornecedor;
import br.com.brasil_saas.cadastro.model.Pessoa;
import br.com.brasil_saas.cadastro.repository.FornecedorRepository;
import br.com.brasil_saas.compras.model.ItemPedidoCompra;
import br.com.brasil_saas.compras.model.PedidoCompra;
import br.com.brasil_saas.compras.model.RecebimentoCompra;
import br.com.brasil_saas.compras.repository.AlcadaAprovacaoRepository;
import br.com.brasil_saas.compras.repository.PedidoCompraRepository;
import br.com.brasil_saas.compras.repository.RecebimentoCompraItemRepository;
import br.com.brasil_saas.compras.repository.RecebimentoCompraRepository;
import br.com.brasil_saas.contabilidade.service.EventoContabilService;
import br.com.brasil_saas.core.service.DocumentoFluxoService;
import br.com.brasil_saas.estoque.model.Deposito;
import br.com.brasil_saas.estoque.repository.DepositoRepository;
import br.com.brasil_saas.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.financeiro.model.Titulo;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import br.com.brasil_saas.financeiro.service.TituloService;
import br.com.brasil_saas.qualidade.repository.InspecaoRepository;
import br.com.brasil_saas.qualidade.repository.PlanoInspecaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Elo pedido -> recebimento -> contabilidade do 3-way match de compras.
 * Receber um pedido reconhece a compra no evento RECEBIMENTO_COMPRA, uma vez por recebimento.
 */
class RecebimentoContabilizacaoTest {

    private static final Long EMPRESA = 1L;
    private PedidoCompraRepository pedidos;
    private RecebimentoCompraRepository recebimentos;
    private EventoContabilService evento;
    private PedidoCompraServiceImpl svc;

    @BeforeEach
    void setUp() {
        pedidos = mock(PedidoCompraRepository.class);
        recebimentos = mock(RecebimentoCompraRepository.class);
        evento = mock(EventoContabilService.class);
        var fornecedores = mock(FornecedorRepository.class);
        var saldos = mock(SaldoEstoqueRepository.class);
        var depositos = mock(DepositoRepository.class);
        var titulos = mock(TituloRepository.class);

        var pessoa = new Pessoa(); pessoa.setId(50L);
        var fornecedor = new Fornecedor(); fornecedor.setPessoa(pessoa);
        when(fornecedores.findByIdAndEmpresaIdAndDeletedAtIsNull(9L, EMPRESA)).thenReturn(Optional.of(fornecedor));
        var deposito = new Deposito(); deposito.setId(3L);
        when(depositos.findByEmpresaIdAndCodigoAndAtivoTrue(EMPRESA, "PADRAO")).thenReturn(Optional.of(deposito));
        when(saldos.findByEmpresaIdAndProdutoIdForUpdate(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(titulos.save(any())).thenAnswer(i -> { Titulo t = i.getArgument(0); t.setId(70L); return t; });
        when(recebimentos.save(any())).thenAnswer(i -> {
            RecebimentoCompra r = i.getArgument(0); r.setId(30L); return r;
        });
        when(pedidos.save(any())).thenAnswer(i -> i.getArgument(0));

        svc = new PedidoCompraServiceImpl(pedidos, fornecedores, recebimentos,
                mock(RecebimentoCompraItemRepository.class), saldos, depositos,
                mock(MovimentacaoEstoqueRepository.class), titulos,
                mock(AlcadaAprovacaoRepository.class), mock(TituloService.class),
                mock(DocumentoFluxoService.class), mock(PlanoInspecaoRepository.class),
                mock(InspecaoRepository.class));
        ReflectionTestUtils.setField(svc, "eventoContabil", evento);
    }

    private PedidoCompra pedido() {
        var p = new PedidoCompra();
        p.setId(11L); p.setEmpresaId(EMPRESA); p.setNumero("PC-1"); p.setStatus("ABERTO");
        p.setFornecedorId(9L); p.setValorTotal(new BigDecimal("100"));
        var it = new ItemPedidoCompra();
        it.setId(1L); it.setProdutoId(6L); it.setQuantidade(new BigDecimal("10"));
        it.setValorUnitario(BigDecimal.TEN); it.setQuantidadeRecebida(BigDecimal.ZERO);
        p.setItens(new ArrayList<>(List.of(it)));
        when(pedidos.findByIdForUpdateAndEmpresaId(11L, EMPRESA)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void receberPedidoContabilizaORecebimentoPeloValorRecebido() {
        pedido();
        svc.receber(11L, EMPRESA);
        verify(evento).contabilizar(eq(EMPRESA), eq(EventoContabilService.EVT_RECEBIMENTO_COMPRA),
                eq("RECEBIMENTO"), eq(30L), eq(LocalDate.now()),
                argThat(v -> v.compareTo(new BigDecimal("100")) == 0), contains("PC-1"));
    }

    @Test
    void recebimentoParcialContabilizaSomenteOQueEntrou() {
        pedido();
        svc.receberParcial(11L, EMPRESA, Map.of(1L, new BigDecimal("4")));
        verify(evento, times(1)).contabilizar(eq(EMPRESA), eq(EventoContabilService.EVT_RECEBIMENTO_COMPRA),
                eq("RECEBIMENTO"), eq(30L), any(),
                argThat(v -> v.compareTo(new BigDecimal("40")) == 0), anyString());
    }

    @Test
    void faltaDeRegraContabilNaoBloqueiaORecebimento() {
        var p = pedido();
        when(evento.contabilizar(anyLong(), anyString(), anyString(), anyLong(), any(), any(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "sem regra"));
        assertDoesNotThrow(() -> svc.receber(11L, EMPRESA));
        assertEquals("RECEBIDO", p.getStatus());
        assertEquals(70L, p.getTituloId());
    }

    @Test
    void erroInesperadoDaContabilidadeNaoEEngolido() {
        pedido();
        when(evento.contabilizar(anyLong(), anyString(), anyString(), anyLong(), any(), any(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "falha"));
        assertThrows(ResponseStatusException.class, () -> svc.receber(11L, EMPRESA));
    }

    @Test
    void semContabilidadeConfiguradaORecebimentoSegueNormalmente() {
        var p = pedido();
        ReflectionTestUtils.setField(svc, "eventoContabil", null);
        assertDoesNotThrow(() -> svc.receber(11L, EMPRESA));
        assertEquals("RECEBIDO", p.getStatus());
    }
}
