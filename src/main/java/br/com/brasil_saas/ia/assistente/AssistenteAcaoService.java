package br.com.brasil_saas.ia.assistente;

import br.com.brasil_saas.cadastro.repository.ClienteRepository;
import br.com.brasil_saas.fiscal.busca.NormalizacaoFiscal;
import br.com.brasil_saas.fiscal.service.NFeService;
import br.com.brasil_saas.fiscal.nfse.NfseEmissaoService;
import br.com.brasil_saas.vendas.model.PedidoVenda;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquestrador do Assistente ERP para o modelo "Dúvida" ou "Ação".
 * - No modo DÚVIDA: consulta o catálogo de funcionalidades, telas, rotas e documentação do sistema.
 * - No modo AÇÃO: interpreta a intenção (ex: "emita a nota fiscal do pedido 54"), inspeciona o pedido,
 *   avalia se é produto (NF-e) ou serviço (NFS-e), e aciona o fluxo oficial do ERP com validação.
 */
@Service
@Slf4j
public class AssistenteAcaoService {

    public record ComandoResponse(
            String modo,
            String status, // SUCESSO, AVISO, ERRO
            String titulo,
            String mensagem,
            String rota,
            String caminhoMenu,
            String rotuloBotao,
            Map<String, Object> dadosExtras
    ) {}

    private final CatalogoNavegacaoService catalogoNavegacao;
    private final PedidoVendaRepository pedidoVendaRepository;
    private final ClienteRepository clienteRepository;
    private final Optional<NFeService> nfeService;
    private final Optional<NfseEmissaoService> nfseEmissaoService;

    public AssistenteAcaoService(
            CatalogoNavegacaoService catalogoNavegacao,
            PedidoVendaRepository pedidoVendaRepository,
            ClienteRepository clienteRepository,
            @Autowired(required = false) NFeService nfeService,
            @Autowired(required = false) NfseEmissaoService nfseEmissaoService) {
        this.catalogoNavegacao = catalogoNavegacao;
        this.pedidoVendaRepository = pedidoVendaRepository;
        this.clienteRepository = clienteRepository;
        this.nfeService = Optional.ofNullable(nfeService);
        this.nfseEmissaoService = Optional.ofNullable(nfseEmissaoService);
    }

    @Transactional
    public ComandoResponse processar(String texto, String modo, Long empresaId, String usuarioNome) {
        String textoLimpo = texto == null ? "" : texto.trim();
        String modoNormalizado = modo == null ? "DUVIDA" : modo.trim().toUpperCase(Locale.ROOT);

        if ("ACAO".equals(modoNormalizado)) {
            return executarAcao(textoLimpo, empresaId, usuarioNome);
        } else {
            return responderDuvida(textoLimpo, empresaId);
        }
    }

    private ComandoResponse responderDuvida(String pergunta, Long empresaId) {
        Optional<CatalogoNavegacaoService.ItemNavegacao> achado = catalogoNavegacao.buscarMaisRelevante(pergunta);

        if (achado.isPresent()) {
            CatalogoNavegacaoService.ItemNavegacao item = achado.get();
            Map<String, Object> extras = new LinkedHashMap<>();
            extras.put("modulo", item.modulo());
            extras.put("funcionalidadeId", item.id());

            return new ComandoResponse(
                    "DUVIDA",
                    "SUCESSO",
                    item.titulo(),
                    item.descricaoPassoAPasso(),
                    item.rota(),
                    item.caminhoMenu(),
                    "Ir para " + item.titulo(),
                    extras
            );
        }

        return new ComandoResponse(
                "DUVIDA",
                "AVISO",
                "Orientação de Navegação",
                "Não encontrei uma tela específica para a sua pergunta: \"" + pergunta + "\". " +
                "Você pode navegar pelo menu lateral nos módulos principais (Vendas, Compras, Fiscal, Estoque, Financeiro, RH, Produção) " +
                "ou tentar uma busca mais direta como onde emitir nota, como cadastrar cliente ou onde ver estoque.",
                "/dashboard",
                "Início > Dashboard",
                "Ir para o Dashboard",
                Map.of()
        );
    }

    private ComandoResponse executarAcao(String comando, Long empresaId, String usuarioNome) {
        // Padrão para emissão de nota de pedido: "emita a nota do pedido 54", "gerar nfe pedido 54", etc.
        Matcher matcherEmissao = Pattern.compile("(?i)(?:emitir|emita|gerar|gera|transmitir|transmita|faturar|fatura|nota|nfe|nfse).*?(?:pedido|ped|#|n[oº]|numero)?\\s*(\\d+)").matcher(comando);
        Matcher matcherApenasPedido = Pattern.compile("(?i)(?:pedido|ped|#|n[oº]|numero)\\s*(\\d+)").matcher(comando);

        String numeroOuId = null;
        if (matcherEmissao.find()) {
            numeroOuId = matcherEmissao.group(1);
        } else if (matcherApenasPedido.find()) {
            numeroOuId = matcherApenasPedido.group(1);
        }

        if (numeroOuId != null && (ehComandoDeNota(comando) || ehComandoDeFaturamento(comando))) {
            return processarEmissaoNotaPedido(numeroOuId, empresaId, usuarioNome);
        }

        // Se não for comando de nota de pedido, tenta encontrar a ação correspondente no catálogo
        Optional<CatalogoNavegacaoService.ItemNavegacao> item = catalogoNavegacao.buscarMaisRelevante(comando);
        if (item.isPresent()) {
            CatalogoNavegacaoService.ItemNavegacao i = item.get();
            return new ComandoResponse(
                    "ACAO",
                    "AVISO",
                    "Ação Não Automatizada Diretamente",
                    "Essa operação não pode ser executada 100% de forma autônoma sem confirmação de dados. " +
                    "Você pode realizá-la na tela correspondente em: " + i.caminhoMenu() + ".\n" +
                    "Instruções: " + i.descricaoPassoAPasso(),
                    i.rota(),
                    i.caminhoMenu(),
                    "Abrir " + i.titulo(),
                    Map.of("sugestao", i.titulo())
            );
        }

        return new ComandoResponse(
                "ACAO",
                "AVISO",
                "Comando Não Reconhecido",
                "Não consegui identificar a ação solicitada para \"" + comando + "\". " +
                "Experimente comandos como: Emita a nota fiscal do pedido 54 ou consulte como fazer no botão [ Dúvida ].",
                "/vendas/pedidos",
                "Vendas > Pedidos de Venda",
                "Ver Pedidos de Venda",
                Map.of()
        );
    }

    private ComandoResponse processarEmissaoNotaPedido(String idOuNumeroStr, Long empresaId, String usuarioNome) {
        Long pedidoId = null;
        try {
            pedidoId = Long.parseLong(idOuNumeroStr);
        } catch (NumberFormatException ignored) {
        }

        Optional<PedidoVenda> pedidoOpt = Optional.empty();
        if (pedidoId != null) {
            pedidoOpt = pedidoVendaRepository.findByIdAndEmpresaId(pedidoId, empresaId);
        }

        if (pedidoOpt.isEmpty()) {
            // Busca direta pelo número do pedido (evita carregar toda a lista em memória)
            pedidoOpt = pedidoVendaRepository.findByEmpresaIdAndNumero(empresaId, idOuNumeroStr);
            if (pedidoOpt.isEmpty()) {
                // Fallback limitado: match por sufixo em pedidos recentes (máx 200)
                List<PedidoVenda> recentes = pedidoVendaRepository.findByEmpresaIdOrderByDataEmissaoDesc(empresaId);
                pedidoOpt = recentes.stream()
                        .limit(200)
                        .filter(p -> p.getNumero() != null && p.getNumero().endsWith(idOuNumeroStr))
                        .findFirst();
            }
        }

        if (pedidoOpt.isEmpty()) {
            return new ComandoResponse(
                    "ACAO",
                    "AVISO",
                    "Pedido Não Localizado",
                    "O pedido #" + idOuNumeroStr + " não foi encontrado para esta empresa. " +
                    "Verifique o número do pedido e confirme se ele pertence à empresa atualmente selecionada.",
                    "/vendas/pedidos",
                    "Vendas > Pedidos de Venda",
                    "Ver Pedidos de Venda",
                    Map.of("pedidoBuscado", idOuNumeroStr)
            );
        }

        PedidoVenda pedido = pedidoOpt.get();
        String pedidoNumero = pedido.getNumero() != null ? pedido.getNumero() : String.valueOf(pedido.getId());
        String clienteNome = clienteRepository.nomeDaPessoaPorClienteId(pedido.getClienteId())
                .orElse("Cliente #" + pedido.getClienteId());

        BigDecimal valProdutos = pedido.getValorProdutos() != null ? pedido.getValorProdutos() : BigDecimal.ZERO;
        BigDecimal valServicos = pedido.getValorServicos() != null ? pedido.getValorServicos() : BigDecimal.ZERO;
        BigDecimal valTotal = pedido.getValorTotal() != null ? pedido.getValorTotal() : valProdutos.add(valServicos);

        if ("CANCELADO".equalsIgnoreCase(pedido.getStatus())) {
            return new ComandoResponse(
                    "ACAO",
                    "ERRO",
                    "Pedido Cancelado",
                    "O pedido #" + pedidoNumero + " está com status CANCELADO. Não é permitido emitir documentos fiscais para pedidos cancelados.",
                    "/vendas/pedidos",
                    "Vendas > Pedidos de Venda",
                    "Visualizar Pedido",
                    Map.of("pedidoId", pedido.getId(), "status", pedido.getStatus())
            );
        }

        // Avaliar natureza: SERVIÇO, PRODUTO ou MISTO
        boolean temProdutos = valProdutos.compareTo(BigDecimal.ZERO) > 0;
        boolean temServicos = valServicos.compareTo(BigDecimal.ZERO) > 0;

        if (temServicos && !temProdutos) {
            // Operação de NFS-e Municipal
            return tratarEmissaoNfse(pedido, pedidoNumero, clienteNome, valServicos, empresaId);
        } else if (temProdutos && !temServicos) {
            // Operação de NF-e Estadual (Mercadorias)
            return tratarEmissaoNfe(pedido, pedidoNumero, clienteNome, valTotal, empresaId);
        } else {
            // Operação com ambos ou padrão de mercadorias
            if (temProdutos && temServicos) {
                return new ComandoResponse(
                        "ACAO",
                        "AVISO",
                        "Pedido com Itens Mistos (Produto + Serviço)",
                        "O pedido #" + pedidoNumero + " possui tanto produtos (R$ " + valProdutos + ") quanto serviços (R$ " + valServicos + "). " +
                        "No Brasil, itens de produto são emitidos via NF-e estadual (SEFAZ) e serviços via NFS-e municipal (Prefeitura). " +
                        "Acesse as telas de emissão para transmitir cada documento.",
                        "/fiscal/nfe",
                        "Fiscal > Documentos Fiscais",
                        "Acessar Emissão Fiscal",
                        Map.of("pedidoId", pedido.getId(), "produtos", valProdutos, "servicos", valServicos)
                );
            } else {
                // Caso ambos sejam zero ou não preenchidos no cabeçalho, assume NF-e de produto padrão
                return tratarEmissaoNfe(pedido, pedidoNumero, clienteNome, valTotal, empresaId);
            }
        }
    }

    private ComandoResponse tratarEmissaoNfe(PedidoVenda pedido, String pedidoNumero, String clienteNome,
                                             BigDecimal valTotal, Long empresaId) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("pedidoId", pedido.getId());
        extras.put("pedidoNumero", pedidoNumero);
        extras.put("cliente", clienteNome);
        extras.put("valorTotal", valTotal);
        extras.put("tipoDocumento", "NF-e");

        if (nfeService.isPresent()) {
            try {
                String protocolo = nfeService.get().emitirNFe(empresaId, pedido);
                extras.put("protocolo", protocolo);

                return new ComandoResponse(
                        "ACAO",
                        "SUCESSO",
                        "NF-e Emitida com Sucesso!",
                        "A Nota Fiscal Eletrônica (NF-e) do Pedido #" + pedidoNumero + " foi autorizada com sucesso pela SEFAZ!\n" +
                        "• Cliente: " + clienteNome + "\n" +
                        "• Valor Total: R$ " + valTotal + "\n" +
                        "• Protocolo SEFAZ: " + protocolo,
                        "/fiscal/nfe",
                        "Fiscal > Documentos Fiscais > NF-e",
                        "Ver Notas Fiscais",
                        extras
                );
            } catch (RuntimeException e) {
                // Falhas de validação SEFAZ / certificado / regras do NFeService
                log.warn("Tentativa de emissão de NF-e para pedido {}: {}", pedidoNumero, e.getMessage());
                return new ComandoResponse(
                        "ACAO",
                        "AVISO",
                        "Pendência na Emissão da NF-e",
                        "Identifiquei o Pedido #" + pedidoNumero + " (Produtos, R$ " + valTotal + ") para o cliente " + clienteNome + ".\n" +
                        "A SEFAZ retornou a seguinte validação: " + e.getMessage() + "\n" +
                        "Verifique os dados tributários e certificado digital na tela de NF-e.",
                        "/fiscal/nfe",
                        "Fiscal > Documentos Fiscais > NF-e",
                        "Abrir Tela de NF-e",
                        extras
                );
            }
        } else {
            // Em ambiente local/dev onde o módulo SEFAZ não está conectado a um certificado real
            return new ComandoResponse(
                    "ACAO",
                    "SUCESSO",
                    "Pedido Pronto para Emissão de NF-e",
                    "O Pedido #" + pedidoNumero + " foi analisado com sucesso:\n" +
                    "• Operação: Venda de Mercadoria (NF-e de Produto)\n" +
                    "• Cliente: " + clienteNome + "\n" +
                    "• Valor Total: R$ " + valTotal + "\n" +
                    "• Status do Pedido: " + pedido.getStatus() + "\n\n" +
                    "O pedido está pronto para transmissão à SEFAZ. Clique no botão abaixo para revisar e concluir na tela de NF-e.",
                    "/fiscal/nfe",
                    "Fiscal > Documentos Fiscais > NF-e",
                    "Ir para Emissão de NF-e",
                    extras
            );
        }
    }

    private ComandoResponse tratarEmissaoNfse(PedidoVenda pedido, String pedidoNumero, String clienteNome,
                                              BigDecimal valServicos, Long empresaId) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("pedidoId", pedido.getId());
        extras.put("pedidoNumero", pedidoNumero);
        extras.put("cliente", clienteNome);
        extras.put("valorServicos", valServicos);
        extras.put("tipoDocumento", "NFS-e");

        return new ComandoResponse(
                "ACAO",
                "SUCESSO",
                "Pedido de Serviços Identificado (NFS-e)",
                "O Pedido #" + pedidoNumero + " foi identificado como prestação de serviços municipais:\n" +
                "• Operação: NFS-e (Prefeitura)\n" +
                "• Tomador (Cliente): " + clienteNome + "\n" +
                "• Valor dos Serviços: R$ " + valServicos + "\n\n" +
                "Para emitir o RPS/NFS-e municipal com os dados do provedor da prefeitura, acerte os códigos municipais na tela de NFS-e.",
                "/fiscal/nfse",
                "Fiscal > Documentos Fiscais > NFS-e",
                "Ir para Emissão de NFS-e",
                extras
        );
    }

    private boolean ehComandoDeNota(String s) {
        String n = NormalizacaoFiscal.texto(s);
        return n.contains("nota") || n.contains("nfe") || n.contains("nfse") || n.contains("danfe");
    }

    private boolean ehComandoDeFaturamento(String s) {
        String n = NormalizacaoFiscal.texto(s);
        return n.contains("faturar") || n.contains("emitir") || n.contains("gerar") || n.contains("transmita");
    }
}
