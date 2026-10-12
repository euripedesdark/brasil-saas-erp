package br.com.brasil_saas.ia.assistente;

import br.com.brasil_saas.cadastro.repository.ClienteRepository;
import br.com.brasil_saas.fiscal.nfse.NfseEmissaoService;
import br.com.brasil_saas.fiscal.service.NFeService;
import br.com.brasil_saas.vendas.model.PedidoVenda;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AssistenteComandoTest {

    private CatalogoNavegacaoService catalogo;
    private PedidoVendaRepository pedidoVendaRepository;
    private ClienteRepository clienteRepository;
    private NFeService nfeService;
    private NfseEmissaoService nfseEmissaoService;
    private AssistenteAcaoService acaoService;

    @BeforeEach
    void setUp() {
        catalogo = new CatalogoNavegacaoService();
        pedidoVendaRepository = Mockito.mock(PedidoVendaRepository.class);
        clienteRepository = Mockito.mock(ClienteRepository.class);
        nfeService = Mockito.mock(NFeService.class);
        nfseEmissaoService = Mockito.mock(NfseEmissaoService.class);

        acaoService = new AssistenteAcaoService(
                catalogo,
                pedidoVendaRepository,
                clienteRepository,
                nfeService,
                nfseEmissaoService
        );
    }

    @Test
    void testCatalogoNavegacaoEncontraNFe() {
        var achado = catalogo.buscarMaisRelevante("onde emito nota fiscal");
        assertTrue(achado.isPresent());
        assertEquals("/fiscal/nfe", achado.get().rota());
        assertTrue(achado.get().caminhoMenu().contains("NF-e"));
    }

    @Test
    void testCatalogoNavegacaoEncontraNfse() {
        var achado = catalogo.buscarMaisRelevante("onde emito nota de servico");
        assertTrue(achado.isPresent());
        assertEquals("/fiscal/nfse", achado.get().rota());
    }

    @Test
    void testCatalogoNavegacaoEncontraCliente() {
        var achado = catalogo.buscarMaisRelevante("como cadastrar um cliente");
        assertTrue(achado.isPresent());
        assertEquals("/cadastro/clientes", achado.get().rota());
    }

    @Test
    void testCatalogoNavegacaoEncontraProduto() {
        var achado = catalogo.buscarMaisRelevante("quero cadastrar um produto");
        assertTrue(achado.isPresent());
        assertEquals("/cadastro/produtos", achado.get().rota());
    }

    @Test
    void testCatalogoNavegacaoEncontraContasAPagar() {
        var achado = catalogo.buscarMaisRelevante("onde vejo contas a pagar");
        assertTrue(achado.isPresent());
        assertEquals("/financeiro/lancamentos", achado.get().rota());
    }

    /**
     * Guarda-corpo: nenhuma rota do catálogo pode ficar em branco ou fora do padrão
     * de rota absoluta do React Router. Sem isto, a IA mandaria o usuário para uma
     * tela inexistente.
     */
    @Test
    void testTodasAsRotasSaoAbsolutasEPreenchidas() {
        assertFalse(catalogo.getTodos().isEmpty(), "O catálogo não pode estar vazio");
        for (var item : catalogo.getTodos()) {
            assertNotNull(item.rota(), "Rota nula em " + item.id());
            assertTrue(item.rota().startsWith("/"), "Rota não absoluta em " + item.id() + ": " + item.rota());
            assertTrue(item.rota().length() > 1, "Rota vazia em " + item.id());
            assertNotNull(item.caminhoMenu(), "Menu nulo em " + item.id());
            assertFalse(item.caminhoMenu().isBlank(), "Menu vazio em " + item.id());
            assertFalse(item.sinonimos().isEmpty(), "Sem sinônimos em " + item.id());
            assertNotNull(item.descricaoPassoAPasso(), "Sem passo a passo em " + item.id());
        }
    }

    @Test
    void testIdesDoCatalogoSaoUnicos() {
        var ids = catalogo.getTodos().stream().map(CatalogoNavegacaoService.ItemNavegacao::id).toList();
        assertEquals(ids.size(), ids.stream().distinct().count(), "Existem IDs duplicados no catálogo");
    }

    @Test
    void testResponderDuvidaSucesso() {
        var resposta = acaoService.processar("onde vejo estoque", "DUVIDA", 1L, "euripedes");
        assertEquals("DUVIDA", resposta.modo());
        assertEquals("SUCESSO", resposta.status());
        assertNotNull(resposta.rota());
        assertTrue(resposta.rota().contains("estoque"));
    }

    @Test
    void testExecutarAcaoPedidoNaoEncontrado() {
        when(pedidoVendaRepository.findByIdAndEmpresaId(999L, 1L)).thenReturn(Optional.empty());
        when(pedidoVendaRepository.findByEmpresaIdAndNumero(1L, "999")).thenReturn(Optional.empty());
        when(pedidoVendaRepository.findByEmpresaIdOrderByDataEmissaoDesc(1L)).thenReturn(java.util.Collections.emptyList());

        var resposta = acaoService.processar("emita a nota fiscal do pedido 999", "ACAO", 1L, "euripedes");
        assertEquals("ACAO", resposta.modo());
        assertEquals("AVISO", resposta.status());
        assertTrue(resposta.mensagem().contains("não foi encontrado"));
    }

    @Test
    void testExecutarAcaoPedidoProdutoEmissaoSucesso() throws Exception {
        PedidoVenda pedido = new PedidoVenda();
        pedido.setId(54L);
        pedido.setNumero("54");
        pedido.setStatus("ABERTO");
        pedido.setClienteId(10L);
        pedido.setValorProdutos(new BigDecimal("1500.00"));
        pedido.setValorServicos(BigDecimal.ZERO);
        pedido.setValorTotal(new BigDecimal("1500.00"));

        when(pedidoVendaRepository.findByIdAndEmpresaId(54L, 1L)).thenReturn(Optional.of(pedido));
        when(clienteRepository.nomeDaPessoaPorClienteId(10L)).thenReturn(Optional.of("Empresa Cliente S/A"));
        when(nfeService.emitirNFe(eq(1L), any(PedidoVenda.class))).thenReturn("135260000123456");

        var resposta = acaoService.processar("emita a nota fiscal do pedido 54", "ACAO", 1L, "euripedes");

        assertEquals("ACAO", resposta.modo());
        assertEquals("SUCESSO", resposta.status());
        assertEquals("/fiscal/nfe", resposta.rota());
        assertTrue(resposta.mensagem().contains("autorizada com sucesso"));
        assertTrue(resposta.mensagem().contains("135260000123456"));
    }

    @Test
    void testExecutarAcaoPedidoServicoIdentificado() {
        PedidoVenda pedido = new PedidoVenda();
        pedido.setId(55L);
        pedido.setNumero("55");
        pedido.setStatus("ABERTO");
        pedido.setClienteId(10L);
        pedido.setValorProdutos(BigDecimal.ZERO);
        pedido.setValorServicos(new BigDecimal("800.00"));
        pedido.setValorTotal(new BigDecimal("800.00"));

        when(pedidoVendaRepository.findByIdAndEmpresaId(55L, 1L)).thenReturn(Optional.of(pedido));
        when(clienteRepository.nomeDaPessoaPorClienteId(10L)).thenReturn(Optional.of("Cliente Serviços"));

        var resposta = acaoService.processar("emita a nota do pedido 55", "ACAO", 1L, "euripedes");

        assertEquals("ACAO", resposta.modo());
        assertEquals("SUCESSO", resposta.status());
        assertEquals("/fiscal/nfse", resposta.rota());
        assertTrue(resposta.mensagem().contains("NFS-e"));
    }

    @Test
    void testConsultarStatusPedido() {
        PedidoVenda pedido = new PedidoVenda();
        pedido.setId(54L);
        pedido.setNumero("54");
        pedido.setStatus("ABERTO");
        pedido.setClienteId(10L);
        pedido.setValorProdutos(new BigDecimal("1500.00"));
        pedido.setValorServicos(BigDecimal.ZERO);
        pedido.setValorTotal(new BigDecimal("1500.00"));

        when(pedidoVendaRepository.findByIdAndEmpresaId(54L, 1L)).thenReturn(Optional.of(pedido));
        when(clienteRepository.nomeDaPessoaPorClienteId(10L)).thenReturn(Optional.of("Empresa Cliente S/A"));

        var resposta = acaoService.processar("status do pedido 54", "ACAO", 1L, "euripedes");

        assertEquals("ACAO", resposta.modo());
        assertEquals("SUCESSO", resposta.status());
        assertTrue(resposta.titulo().contains("Status do Pedido"));
        assertTrue(resposta.mensagem().contains("ABERTO"));
        assertTrue(resposta.mensagem().contains("Empresa Cliente S/A"));
    }

}
