package br.com.brasil_saas.database;

import br.com.brasil_saas.compras.model.*;
import br.com.brasil_saas.compras.repository.*;
import br.com.brasil_saas.compras.service.DevCompraService;
import br.com.brasil_saas.vendas.devolucao.*;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import br.com.brasil_saas.cadastro.repository.ClienteRepository;
import br.com.brasil_saas.estoque.repository.*;
import br.com.brasil_saas.core.repository.DocumentoFluxoRepository;
import br.com.brasil_saas.core.service.DocumentoFluxoService;
import br.com.brasil_saas.compras.repository.ConferenciaFaturaCompraRepository;
import br.com.brasil_saas.financeiro.model.Titulo;
import br.com.brasil_saas.financeiro.repository.BaixaRepository;
import br.com.brasil_saas.financeiro.repository.CondicaoPagamentoRepository;
import br.com.brasil_saas.financeiro.repository.ContaBancariaRepository;
import br.com.brasil_saas.financeiro.repository.ExtratoRepository;
import br.com.brasil_saas.financeiro.repository.TituloParcelaRepository;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import br.com.brasil_saas.financeiro.service.impl.TituloServiceImpl;
import br.com.brasil_saas.financeiro.service.CreditoService;
import br.com.brasil_saas.shared.exception.BusinessException;
import org.hibernate.Session;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Devolucoes nas duas direcoes e exposicao de credito reais, com rollback. */
final class DevolucoesCreditoPostgresScenario {
    static void validar(Session session, Long pedidoVendaId) {
        validarUpgradeLegado(session);
        long empresa=900001L;
        var factory=new JpaRepositoryFactory(session);
        var depositos=factory.getRepository(DepositoRepository.class);
        var saldos=factory.getRepository(SaldoEstoqueRepository.class);
        var movimentos=factory.getRepository(MovimentacaoEstoqueRepository.class);
        var pedidosVenda=factory.getRepository(PedidoVendaRepository.class);
        var devVenda=factory.getRepository(VenDevolucaoRepository.class);
        var titulosVenda=factory.getRepository(TituloRepository.class);
        var baixasVenda=factory.getRepository(BaixaRepository.class);
        var tituloSvcVenda=new TituloServiceImpl(titulosVenda,factory.getRepository(TituloParcelaRepository.class),baixasVenda,
                factory.getRepository(CondicaoPagamentoRepository.class),factory.getRepository(ContaBancariaRepository.class),
                factory.getRepository(ExtratoRepository.class),factory.getRepository(ConferenciaFaturaCompraRepository.class),factory.getRepository(br.com.brasil_saas.compras.repository.ConferenciaFaturaCompraItemRepository.class),factory.getRepository(br.com.brasil_saas.compras.repository.PedidoCompraRepository.class),factory.getRepository(br.com.brasil_saas.compras.repository.RecebimentoCompraRepository.class),factory.getRepository(br.com.brasil_saas.compras.repository.RecebimentoCompraItemRepository.class),factory.getRepository(br.com.brasil_saas.fiscal.repository.NfeRepository.class));
        var fluxoVenda=new DocumentoFluxoService(factory.getRepository(DocumentoFluxoRepository.class));
        var contabeis=new br.com.brasil_saas.contabilidade.service.impl.ContabilidadeServiceImpl(factory.getRepository(br.com.brasil_saas.contabilidade.repository.CtbLancamentoRepository.class),factory.getRepository(br.com.brasil_saas.contabilidade.repository.CtbPartidaRepository.class),factory.getRepository(br.com.brasil_saas.contabilidade.repository.CtbFechamentoRepository.class),factory.getRepository(br.com.brasil_saas.financeiro.repository.PlanoContasRepository.class),titulosVenda);
        var comissoes=new br.com.brasil_saas.financeiro.service.impl.ComissaoServiceImpl(
                factory.getRepository(br.com.brasil_saas.financeiro.repository.ComissaoRepository.class),
                factory.getRepository(br.com.brasil_saas.vendas.repository.RegraComissaoRepository.class),
                factory.getRepository(br.com.brasil_saas.rh.repository.FuncionarioRepository.class));
        var venda=new DevolucaoService(devVenda,factory.getRepository(VenDevolucaoItemRepository.class),pedidosVenda,saldos,movimentos,depositos,
                titulosVenda,baixasVenda,tituloSvcVenda,fluxoVenda,contabeis,comissoes);
        var retorno=venda.solicitar(empresa,pedidoVendaId,"Teste sintetico",Map.of(empresa,BigDecimal.ONE));
        assertEquals(empresa,retorno.getClienteId()); assertNotNull(retorno.getNumero());
        venda.decidir(empresa,null,retorno.getId(),true);venda.receber(empresa,retorno.getId());session.flush();
        // Uma unidade de quatro, a R$25: a devolucao reduz o recebivel de R$100 para R$75.
        var pedidoAjustado = pedidosVenda.findByIdAndEmpresaId(pedidoVendaId, empresa).orElseThrow();
        igual("75", titulosVenda.findById(pedidoAjustado.getTituloId()).orElseThrow().getValorSaldo());
        igual("9",saldos.findForUpdate(empresa,empresa,empresa).orElseThrow().getQuantidade());
        assertThrows(ResponseStatusException.class,()->venda.receber(empresa,retorno.getId()));
        assertThrows(ResponseStatusException.class,()->venda.solicitar(empresa,pedidoVendaId,"Excesso",Map.of(empresa,new BigDecimal("4"))));
        assertTrue(devVenda.findByIdForUpdate(retorno.getId(),900002L).isEmpty());

        var pc=new PedidoCompra();pc.setEmpresaId(empresa);pc.setFornecedorId(empresa);pc.setNumero("PC-DEV-TESTE");
        pc.setStatus("RECEBIDO");pc.setDataEmissao(LocalDate.now());
        var item=new ItemPedidoCompra();item.setEmpresaId(empresa);item.setPedido(pc);item.setProdutoId(empresa);
        item.setNumeroItem(1);item.setQuantidade(BigDecimal.TEN);item.setQuantidadeRecebida(BigDecimal.TEN);
        item.setValorUnitario(BigDecimal.ONE);item.setValorTotal(BigDecimal.TEN);pc.getItens().add(item);
        var pedidosCompra=factory.getRepository(PedidoCompraRepository.class);pedidosCompra.save(pc);
        var devCompra=factory.getRepository(DevCompraRepository.class);
        var compra=new DevCompraService(devCompra,factory.getRepository(DevCompraItemRepository.class),pedidosCompra,saldos,movimentos,
                depositos,factory.getRepository(ReservaEstoqueRepository.class),
                titulosVenda,baixasVenda,tituloSvcVenda,fluxoVenda,contabeis);
        var devolucao=compra.solicitar(empresa,pc.getId(),"Teste sintetico",List.of(
                Map.of("produtoId",empresa,"quantidade","1"),Map.of("produtoId",empresa,"quantidade","1")));
        compra.devolver(empresa,devolucao.getId());session.flush();
        igual("7",saldos.findForUpdate(empresa,empresa,empresa).orElseThrow().getQuantidade());
        var saida=movimentos.findByEmpresaIdAndProdutoIdAndDeletedAtIsNullOrderByDataMovimentoDesc(empresa,empresa).stream()
                .filter(m->"DEVOLUCAO_COMPRA".equals(m.getOrigem())).toList();
        assertEquals(1,saida.size());igual("-2",saida.get(0).getQuantidade());assertEquals(empresa,saida.get(0).getDepositoId());
        assertThrows(BusinessException.class,()->compra.devolver(empresa,devolucao.getId()));
        assertTrue(devCompra.findForUpdate(devolucao.getId(),900002L).isEmpty());
        var reservada=compra.solicitar(empresa,pc.getId(),"Preserva reservas",List.of(Map.of("produtoId",empresa,"quantidade","3")));
        assertThrows(BusinessException.class,()->compra.devolver(empresa,reservada.getId()));
        igual("7",saldos.findForUpdate(empresa,empresa,empresa).orElseThrow().getQuantidade());
        compra.cancelar(empresa,reservada.getId());

        var clientes=factory.getRepository(ClienteRepository.class);
        var titulos=titulosVenda;
        var pagar=new Titulo();pagar.setEmpresaId(empresa);pagar.setPessoaId(empresa);pagar.setTipo("P");
        pagar.setDescricao("Conta a pagar sintetica");pagar.setValorOriginal(new BigDecimal("900"));pagar.setValorSaldo(new BigDecimal("900"));
        pagar.setDataEmissao(LocalDate.now());pagar.setDataVencimento(LocalDate.now().minusDays(1));titulos.save(pagar);
        var credito=new CreditoService(clientes,titulos,pedidosVenda);
        credito.definirLimite(empresa,empresa,new BigDecimal("1000"));
        session.flush();
        var analise=credito.analisar(empresa,empresa);
        igual("75",(BigDecimal)analise.get("emAberto"));igual("925",(BigDecimal)analise.get("disponivel"));
        assertEquals("OK",analise.get("situacao"));
        assertTrue(clientes.findForUpdate(empresa,900002L).isEmpty());
    }
    private static void validarUpgradeLegado(Session session) {
        session.doWork(connection -> {
            try (var sql=connection.createStatement()) {
                sql.execute("create schema upgrade_devolucao_test");
                sql.execute("create table upgrade_devolucao_test.bc_ven_pedido(id bigint,empresa_id bigint,cliente_id bigint)");
                sql.execute("create table upgrade_devolucao_test.bc_ven_devolucao(id bigint,pedido_id bigint,empresa_id bigint)");
                sql.execute("insert into upgrade_devolucao_test.bc_ven_pedido values(1,900001,900001),(2,900002,900002)");
                sql.execute("insert into upgrade_devolucao_test.bc_ven_devolucao values(101,1,900001),(102,2,900001),(103,null,900001)");
                String migration;
                try {
                    migration=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/V186__devolucao_venda_identificacao.sql"))
                            .replace("brasil_saas.","upgrade_devolucao_test.");
                } catch (java.io.IOException e) {throw new java.sql.SQLException(e);}
                sql.execute(migration);
                try (var rows=sql.executeQuery("select id,cliente_id,numero from upgrade_devolucao_test.bc_ven_devolucao order by id")) {
                    assertTrue(rows.next());assertEquals(900001L,rows.getLong("cliente_id"));assertEquals("DV-LEG-101",rows.getString("numero"));
                    assertTrue(rows.next());assertNull(rows.getObject("cliente_id"));
                    assertTrue(rows.next());assertNull(rows.getObject("cliente_id"));
                }
                sql.execute("update upgrade_devolucao_test.bc_ven_devolucao set numero='EXISTENTE' where id=101");
                sql.execute(migration);
                try (var rows=sql.executeQuery("select numero from upgrade_devolucao_test.bc_ven_devolucao where id=101")) {
                    assertTrue(rows.next());assertEquals("EXISTENTE",rows.getString(1));
                }
            }
        });
    }
    private static void igual(String valor,BigDecimal atual){assertEquals(0,new BigDecimal(valor).compareTo(atual));}
}
