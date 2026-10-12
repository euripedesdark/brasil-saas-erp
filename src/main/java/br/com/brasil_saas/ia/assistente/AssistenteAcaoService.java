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
            String status, // SUCESSO, AVISO, ERRO, CONFIRMACAO
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
    public ComandoResponse processar(String texto, String modo, Long empresaId, String usuarioNome, boolean confirmar) {
        String textoLimpo = texto == null ? "" : texto.trim();
        String modoNormalizado = modo == null ? "DUVIDA" : modo.trim().toUpperCase(Locale.ROOT);

        if ("ACAO".equals(modoNormalizado)) {
            return executarAcao(textoLimpo, empresaId, usuarioNome, confirmar);
        } else {
            return responderDuvida(textoLimpo, empresaId);
        }
    }

    /** Compatibilidade: chamada sem confirmação (sempre pede confirmação em ações críticas). */
    @Transactional
    public ComandoResponse processar(String texto, String modo, Long empresaId, String usuarioNome) {
        return processar(texto, modo, empresaId, usuarioNome, false);
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

    private ComandoResponse executarAcao(String comando, Long empresaId, String usuarioNome, boolean confirmar) {
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
            return processarEmissaoNotaPedido(numeroOuId, empresaId, usuarioNome, confirmar);
        }

        // Consulta de status do pedido: "status do pedido 54", "como está o pedido 54", etc.
        if (numeroOuId != null && ehComandoDeStatusPedido(comando)) {
            return consultarStatusPedido(numeroOuId, empresaId);
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

    private ComandoResponse processarEmissaoNotaPedido(String idOuNumeroStr, Long empresaId, String usuarioNome, boolean confirmar) {
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

        // Avaliar natureza: PRODUTO, SERVIÇO, MISTO ou indefinido
        boolean temProdutos = valProdutos.compareTo(BigDecimal.ZERO) > 0;
        boolean temServicos = valServicos.compareTo(BigDecimal.ZERO) > 0;

        String natureza;
        String tipoDoc;
        if (temProdutos && temServicos) {
            natureza = "Misto (Produtos + Serviços)";
            tipoDoc = "NF-e + NFS-e";
        } else if (temServicos) {
            natureza = "Serviços";
            tipoDoc = "NFS-e";
        } else {
            natureza = "Produtos / Mercadorias";
            tipoDoc = "NF-e";
        }

        // SEM confirmação: devolve resumo do pedido e pede confirmação explícita
        if (!confirmar) {
            Map<String, Object> extras = new LinkedHashMap<>();
            extras.put("pedidoId", pedido.getId());
            extras.put("pedidoNumero", pedidoNumero);
            extras.put("cliente", clienteNome);
            extras.put("statusPedido", pedido.getStatus());
            extras.put("valorProdutos", valProdutos);
            extras.put("valorServicos", valServicos);
            extras.put("valorTotal", valTotal);
            extras.put("natureza", natureza);
            extras.put("tipoDocumento", tipoDoc);
            extras.put("precisaConfirmacao", true);

            return new ComandoResponse(
                    "ACAO",
                    "CONFIRMACAO",
                    "Confirmar emissão — Pedido #" + pedidoNumero,
                    "Revise os dados antes de emitir:\n\n" +
                    "• Consumidor / Destinatário: " + clienteNome + "\n" +
                    "• Status do pedido: " + pedido.getStatus() + "\n" +
                    "• Natureza: " + natureza + "\n" +
                    "• Documento: " + tipoDoc + "\n" +
                    "• Produtos: R$ " + valProdutos + "\n" +
                    "• Serviços: R$ " + valServicos + "\n" +
                    "• Total: R$ " + valTotal + "\n\n" +
                    "Clique em Confirmar para emitir a nota com o nome do consumidor acima.",
                    "/vendas/pedidos",
                    "Vendas > Pedidos de Venda",
                    "Confirmar emissão",
                    extras
            );
        }

        // COM confirmação: executa
        if (temProdutos && temServicos) {
            return new ComandoResponse(
                    "ACAO",
                    "AVISO",
                    "Pedido com Itens Mistos (Produto + Serviço)",
                    "O pedido #" + pedidoNumero + " (consumidor: " + clienteNome + ") possui produtos (R$ " + valProdutos +
                    ") e serviços (R$ " + valServicos + ").\n" +
                    "Emita a NF-e na tela de NF-e e a NFS-e na tela de NFS-e.",
                    "/fiscal/nfe",
                    "Fiscal > Documentos Fiscais",
                    "Acessar Emissão Fiscal",
                    Map.of("pedidoId", pedido.getId(), "cliente", clienteNome,
                            "produtos", valProdutos, "servicos", valServicos)
            );
        }
        if (temServicos) {
            return tratarEmissaoNfse(pedido, pedidoNumero, clienteNome, valServicos, empresaId);
        }
        return tratarEmissaoNfe(pedido, pedidoNumero, clienteNome, valTotal, empresaId);
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
                        "NF-e emitida para " + clienteNome,
                        "Pedido #" + pedidoNumero + " — Nota Fiscal Eletrônica autorizada pela SEFAZ.\n\n" +
                        "• Consumidor / Destinatário: " + clienteNome + "\n" +
                        "• Valor Total: R$ " + valTotal + "\n" +
                        "• Protocolo SEFAZ: " + protocolo,
                        "/fiscal/nfe",
                        "Fiscal > Documentos Fiscais > NF-e",
                        "Ver Notas Fiscais",
                        extras
                );
            } catch (Exception e) {
                // Falhas de validação SEFAZ / certificado / regras do NFeService.
                // O catch é amplo de propósito: NFeService.emitirNFe declara
                // `throws Exception`, então falhas checadas da SEFAZ/certificado
                // precisam ser tratadas aqui para virar uma resposta ao usuário.
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
                    "Pedido #" + pedidoNumero + " pronto — consumidor: " + clienteNome,
                    "Análise concluída:\n\n" +
                    "• Consumidor / Destinatário: " + clienteNome + "\n" +
                    "• Operação: Venda de Mercadoria (NF-e)\n" +
                    "• Valor Total: R$ " + valTotal + "\n" +
                    "• Status do Pedido: " + pedido.getStatus() + "\n\n" +
                    "Clique no botão abaixo para revisar e transmitir a NF-e à SEFAZ com o nome do consumidor acima.",
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
        // O emissor municipal só existe quando o provedor da prefeitura está
        // configurado; a resposta informa isso e mantém a navegação guiada para
        // a tela de NFS-e, onde os dados do RPS são preenchidos.
        extras.put("emissaoAutomaticaNfse", nfseEmissaoService.isPresent());

        return new ComandoResponse(
                "ACAO",
                "SUCESSO",
                "NFS-e pronta — tomador: " + clienteNome,
                "Pedido #" + pedidoNumero + " identificado como serviço municipal.\n\n" +
                "• Tomador / Consumidor: " + clienteNome + "\n" +
                "• Operação: NFS-e (Prefeitura)\n" +
                "• Valor dos Serviços: R$ " + valServicos + "\n\n" +
                "Clique no botão abaixo para emitir o RPS/NFS-e com o nome do tomador acima.",
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

    private boolean ehComandoDeStatusPedido(String s) {
        String n = NormalizacaoFiscal.texto(s);
        return n.contains("status") || n.contains("como esta") || n.contains("como esta o")
                || n.contains("situacao") || n.contains("consultar pedido") || n.contains("ver pedido");
    }

    /**
     * Consulta o status e resumo financeiro de um pedido de venda.
     */
    private ComandoResponse consultarStatusPedido(String idOuNumeroStr, Long empresaId) {
        Optional<PedidoVenda> pedidoOpt = Optional.empty();
        try {
            Long pedidoId = Long.parseLong(idOuNumeroStr);
            pedidoOpt = pedidoVendaRepository.findByIdAndEmpresaId(pedidoId, empresaId);
        } catch (NumberFormatException ignored) {
            // continua com busca por número
        }
        if (pedidoOpt.isEmpty()) {
            pedidoOpt = pedidoVendaRepository.findByEmpresaIdAndNumero(empresaId, idOuNumeroStr);
        }
        if (pedidoOpt.isEmpty()) {
            List<PedidoVenda> recentes = pedidoVendaRepository.findByEmpresaIdOrderByDataEmissaoDesc(empresaId);
            pedidoOpt = recentes.stream()
                    .filter(p -> p.getNumero() != null && p.getNumero().endsWith(idOuNumeroStr))
                    .findFirst();
        }
        if (pedidoOpt.isEmpty()) {
            return new ComandoResponse(
                    "ACAO",
                    "AVISO",
                    "Pedido Não Encontrado",
                    "Não localizei o pedido #" + idOuNumeroStr + " nesta empresa. Verifique o número e tente novamente.",
                    "/vendas/pedidos",
                    "Vendas > Pedidos de Venda",
                    "Ver Pedidos",
                    Map.of()
            );
        }

        PedidoVenda pedido = pedidoOpt.get();
        String pedidoNumero = pedido.getNumero() != null ? pedido.getNumero() : String.valueOf(pedido.getId());
        String clienteNome = "Cliente não informado";
        if (pedido.getClienteId() != null) {
            clienteNome = clienteRepository.nomeDaPessoaPorClienteId(pedido.getClienteId()).orElse(clienteNome);
        }
        BigDecimal valProdutos = pedido.getValorProdutos() != null ? pedido.getValorProdutos() : BigDecimal.ZERO;
        BigDecimal valServicos = pedido.getValorServicos() != null ? pedido.getValorServicos() : BigDecimal.ZERO;
        BigDecimal valTotal = pedido.getValorTotal() != null ? pedido.getValorTotal() : BigDecimal.ZERO;

        String natureza;
        if (valProdutos.compareTo(BigDecimal.ZERO) > 0 && valServicos.compareTo(BigDecimal.ZERO) > 0) {
            natureza = "Misto (Produtos + Serviços)";
        } else if (valServicos.compareTo(BigDecimal.ZERO) > 0) {
            natureza = "Serviços (NFS-e)";
        } else {
            natureza = "Produtos (NF-e)";
        }

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("pedidoId", pedido.getId());
        extras.put("pedidoNumero", pedidoNumero);
        extras.put("status", pedido.getStatus());
        extras.put("cliente", clienteNome);
        extras.put("valorTotal", valTotal);
        extras.put("natureza", natureza);

        return new ComandoResponse(
                "ACAO",
                "SUCESSO",
                "Status do Pedido #" + pedidoNumero,
                "• Status: " + pedido.getStatus() + "\n" +
                "• Cliente: " + clienteNome + "\n" +
                "• Natureza: " + natureza + "\n" +
                "• Produtos: R$ " + valProdutos + "\n" +
                "• Serviços: R$ " + valServicos + "\n" +
                "• Total: R$ " + valTotal,
                "/vendas/pedidos",
                "Vendas > Pedidos de Venda",
                "Abrir Pedido",
                extras
        );
    }
}
