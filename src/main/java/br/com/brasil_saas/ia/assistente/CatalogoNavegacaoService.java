package br.com.brasil_saas.ia.assistente;

import br.com.brasil_saas.fiscal.busca.NormalizacaoFiscal;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Catálogo central de telas, rotas e funcionalidades do Brasil SaaS ERP.
 *
 * <p>Permite que a IA (modo Dúvida) saiba exatamente onde cada função do sistema
 * está localizada: o caminho no menu lateral, a rota real do frontend e o passo a
 * passo de uso.
 *
 * <p><b>Regra de ouro:</b> as rotas deste catálogo devem existir no {@code App.jsx}
 * e os nomes de menu devem bater com o {@code PanelMenu} do {@code Layout.jsx}.
 * A fonte de verdade é {@code docs/navegacao.md} + {@code App.jsx}. Quando a rota
 * do App.jsx muda, esta lista precisa mudar junto — do contrário a IA manda o
 * usuário para uma tela inexistente.
 */
@Service
public class CatalogoNavegacaoService {

    /** Documento com uma funcionalidade/tela navegável do ERP. */
    public record ItemNavegacao(
            String id,
            String titulo,
            String modulo,
            String caminhoMenu,
            String rota,
            List<String> sinonimos,
            String descricaoPassoAPasso
    ) {}

    /** Palavras de ligação que não ajudam a localizar uma tela. */
    private static final Set<String> STOPWORDS = Set.of(
            "onde", "como", "vejo", "ver", "ve", "vou", "faco", "fazer", "faz", "para",
            "que", "qual", "quais", "quando", "um", "uma", "uns", "umas", "de", "da",
            "do", "das", "dos", "em", "no", "na", "nos", "nas", "e", "o", "a", "os",
            "as", "meu", "minha", "meus", "minhas", "quero", "preciso", "gostaria",
            "pode", "poderia", "consegue", "me", "ajuda", "ajudar", "esta", "este",
            "isso", "aqui", "tela", "sistema", "erp", "favor", "por", "com", "se",
            "tem", "abrir", "acessar", "ir", "local", "lugar", "funcao", "opcao");

    private final List<ItemNavegacao> catalogo = new ArrayList<>();

    public CatalogoNavegacaoService() {
        carregarCatalogo();
    }

    private void carregarCatalogo() {
        // =========================================================================
        // NÚCLEO / INÍCIO
        // =========================================================================
        reg("core-dashboard", "Dashboard", "Início", "Início > Dashboard", "/dashboard",
                List.of("dashboard", "inicio", "painel", "home", "visao geral", "tela inicial"),
                "Painel inicial com os principais indicadores da empresa.");

        reg("core-perfil", "Meu Perfil", "Início", "Início > Perfil", "/perfil",
                List.of("perfil", "meus dados", "minha conta", "alterar senha", "trocar senha"),
                "Edição dos seus dados de usuário e senha.");

        reg("core-notificacoes", "Notificações", "Início", "Início > Notificações", "/notificacoes",
                List.of("notificacao", "notificacoes", "avisos", "alertas", "mensagens"),
                "Central de avisos e alertas do sistema.");

        reg("core-documentos", "Documentos", "Início", "Início > Documentos", "/documentos",
                List.of("documento", "documentos", "arquivos", "anexos"),
                "Repositório de documentos e anexos.");

        reg("core-documento-fluxo", "Fluxo de Documentos", "Início", "Início > Fluxo de Documentos", "/documento-fluxo",
                List.of("fluxo de documentos", "tramitacao de documento", "workflow de documento"),
                "Acompanhamento do fluxo e tramitação de documentos.");

        reg("core-configurar-empresa", "Configurar Empresa", "Início", "Início > Configurar Empresa", "/configurar-empresa",
                List.of("configurar empresa", "dados da empresa", "parametros da empresa", "certificado da empresa"),
                "Configuração dos dados cadastrais e parâmetros da empresa ativa.");

        reg("core-empresas-credenciais", "Empresas (Stripe/Cert)", "Início", "Início > Empresas (Stripe/Cert)", "/empresas-credenciais",
                List.of("empresas", "credenciais", "stripe", "certificado", "multiempresa", "selecionar empresa"),
                "Gestão de empresas e credenciais de integração (Stripe, certificados).");

        reg("core-sobre", "Sobre o Projeto", "Início", "Início > Sobre", "/sobre",
                List.of("sobre", "versao", "licenca", "informacoes do sistema"),
                "Informações sobre o sistema, versão e licença.");

        reg("core-golive", "Go-live", "Início", "Início > Go-live", "/golive",
                List.of("golive", "go live", "implantacao", "checklist de implantacao"),
                "Checklist de implantação e go-live do sistema.");

        // =========================================================================
        // ADMINISTRAÇÃO
        // =========================================================================
        reg("admin-usuarios", "Usuários e Permissões", "Administração", "Administração > Usuários e Permissões", "/admin/usuarios",
                List.of("usuario", "usuarios", "permissao", "permissoes", "criar usuario", "acesso", "grupo de acesso"),
                "Cadastro de usuários e definição de permissões de acesso.");

        reg("admin-config", "Configurações do Sistema", "Administração", "Administração > Imagens e Configurações", "/admin/configuracoes",
                List.of("configuracoes", "parametros do sistema", "settings", "imagens do sistema"),
                "Configurações gerais e imagens do sistema.");

        reg("admin-paridade", "Paridade ERP", "Administração", "Administração > Paridade ERP", "/admin/paridade-erp",
                List.of("paridade", "paridade erp", "comparativo de funcionalidades", "funcionalidades faltantes"),
                "Relatório de paridade funcional do ERP.");

        reg("admin-endpoints", "Cobertura de Endpoints", "Administração", "Administração > Endpoints", "/admin/endpoints",
                List.of("endpoints", "api", "cobertura da api", "documentacao da api"),
                "Lista e cobertura dos endpoints da API.");

        reg("admin-sql", "Gerenciador SQL", "Administração", "Administração > SQL", "/admin/sql",
                List.of("sql", "consulta sql", "banco de dados", "executar sql"),
                "Console SQL restrito para manutenção do banco de dados.");

        reg("admin-armazenamento", "Armazenamento de Imagens", "Administração", "Administração > Armazenamento", "/admin/armazenamento",
                List.of("armazenamento", "imagens", "arquivos", "minio"),
                "Gestão do armazenamento de arquivos e imagens (MinIO).");

        // =========================================================================
        // CADASTROS
        // =========================================================================
        reg("cad-pessoas", "Pessoas", "Cadastros", "Cadastros > Pessoas", "/cadastro/pessoas",
                List.of("pessoa", "pessoas", "pessoa fisica", "pessoa juridica", "cpf", "cnpj", "contato"),
                "Cadastro unificado de pessoas físicas e jurídicas.");

        reg("cad-clientes", "Clientes", "Cadastros", "Cadastros > Clientes", "/cadastro/clientes",
                List.of("cliente", "clientes", "cadastrar cliente", "novo cliente", "comprador", "carteira de clientes"),
                "Cadastro e gestão da carteira de clientes. Passo a passo: 1. Clique em Novo Cliente; " +
                "2. Informe o CPF/CNPJ (com consulta automática); 3. Complete contato e endereço; 4. Salve.");

        reg("cad-fornecedores", "Fornecedores", "Cadastros", "Cadastros > Fornecedores", "/cadastro/fornecedores",
                List.of("fornecedor", "fornecedores", "cadastrar fornecedor", "novo fornecedor", "parceiro", "prestador"),
                "Cadastro de fornecedores de mercadorias e serviços.");

        reg("cad-produtos", "Produtos", "Cadastros", "Cadastros > Produtos", "/cadastro/produtos",
                List.of("produto", "produtos", "mercadoria", "item", "cadastrar produto", "ncm", "sku", "preco"),
                "Cadastro de produtos. Passo a passo: 1. Novo Produto; 2. Nome, unidade e preço; " +
                "3. Na aba Fiscal informe o NCM e a tributação; 4. Salve.");

        reg("cad-categorias", "Categorias", "Cadastros", "Cadastros > Categorias", "/cadastro/categorias",
                List.of("categoria", "categorias", "grupo de produto", "classificacao de produto"),
                "Cadastro de categorias e grupos de produtos.");

        reg("cad-marcas", "Marcas", "Cadastros", "Cadastros > Marcas", "/cadastro/marcas",
                List.of("marca", "marcas", "fabricante", "brand"),
                "Cadastro de marcas e fabricantes.");

        reg("cad-servicos", "Catálogo de Serviços", "Cadastros", "Cadastros > Serviços", "/cadastro/servicos",
                List.of("servico", "servicos", "cadastrar servico", "codigo municipal", "iss", "nbs", "catalogo de servico"),
                "Cadastro do catálogo de serviços, com código municipal e NBS para emissão de NFS-e.");

        reg("cad-transportadoras", "Transportadoras", "Cadastros", "Cadastros > Transportadoras", "/cadastro/transportadoras",
                List.of("transportadora", "transportadoras", "transportador", "frete", "logistica"),
                "Cadastro de transportadoras e dados de frete.");

        reg("cad-unidades", "Unidades de Medida", "Cadastros", "Cadastros > Unidades de Medida", "/cadastro/unidades-medida",
                List.of("unidade", "unidades de medida", "unidade de medida", "un", "kg", "medida"),
                "Cadastro de unidades de medida.");

        reg("cad-municipios", "Municípios IBGE", "Cadastros", "Cadastros > Municípios IBGE", "/cadastro/municipios",
                List.of("municipio", "municipios", "cidade", "ibge", "codigo ibge"),
                "Base de municípios do IBGE.");

        reg("cad-bancos", "Bancos", "Cadastros", "Cadastros > Bancos", "/cadastro/bancos",
                List.of("banco", "bancos", "codigo do banco", "instituicao financeira"),
                "Cadastro de bancos e instituições financeiras.");

        // =========================================================================
        // VENDAS
        // =========================================================================
        reg("vendas-pedidos", "Pedidos de Venda", "Vendas", "Vendas > Pedidos de Venda", "/vendas",
                List.of("pedido de venda", "pedidos de venda", "pedido", "vender", "venda", "novo pedido", "orcamento", "faturar pedido"),
                "Gestão de pedidos de venda e orçamentos. Passo a passo: 1. Novo Pedido; " +
                "2. Selecione cliente e condição de pagamento; 3. Adicione os itens (produtos/serviços); 4. Salve e aprove para faturar.");

        reg("vendas-pdv", "PDV - Ponto de Venda", "Vendas", "Vendas > PDV - Ponto de Venda", "/vendas/pdv",
                List.of("pdv", "ponto de venda", "venda balcao", "caixa de vendas", "cupom"),
                "Venda rápida no balcão (PDV).");

        reg("vendas-tabelas-preco", "Tabelas de Preço", "Vendas", "Vendas > Tabelas de Preço", "/vendas/tabelas-preco",
                List.of("tabela de preco", "tabelas de preco", "precos", "politica de preco", "desconto"),
                "Cadastro de tabelas e políticas de preço.");

        reg("vendas-devolucoes", "Trocas e Devoluções", "Vendas", "Vendas > Trocas e devoluções", "/vendas/devolucoes",
                List.of("devolucao", "devolucoes", "devolver venda", "troca", "trocar produto", "estorno de venda"),
                "Registro de trocas e devoluções de venda, com crédito ao cliente e retorno ao estoque.");

        reg("vendas-metas", "Metas Comerciais", "Vendas", "Vendas > Metas comerciais", "/vendas/metas",
                List.of("meta", "metas", "metas comerciais", "objetivo de vendas"),
                "Definição e acompanhamento de metas comerciais.");

        reg("vendas-contratos", "Contratos de Venda", "Vendas", "Vendas > Contratos de venda", "/vendas/contratos",
                List.of("contrato de venda", "contratos de venda", "contrato", "recorrencia", "faturamento recorrente"),
                "Gestão de contratos comerciais e faturamento recorrente.");

        // =========================================================================
        // COMPRAS
        // =========================================================================
        reg("compras-pedidos", "Pedidos de Compra", "Compras", "Compras > Pedidos de Compra", "/compras",
                List.of("pedido de compra", "pedidos de compra", "compra", "comprar", "ordem de compra"),
                "Gestão de pedidos de compra. Passo a passo: 1. Novo Pedido; 2. Selecione o fornecedor; " +
                "3. Adicione itens e preços; 4. Salve e aprove.");

        reg("compras-supply-chain", "Suprimentos: Solicitações e Cotações", "Compras", "Compras > Suprimentos", "/compras/supply-chain",
                List.of("solicitacao de compra", "cotacao", "cotacoes", "suprimentos", "supply chain"),
                "Solicitações de compra e cotações com fornecedores.");

        reg("compras-recebimentos", "Recebimentos", "Compras", "Compras > Recebimentos", "/compras/recebimentos",
                List.of("recebimento", "recebimentos", "receber mercadoria", "entrada de mercadoria"),
                "Recebimento de mercadorias dos pedidos de compra.");

        reg("compras-conferencia", "Conferência 3-way", "Compras", "Compras > Conferência 3-way", "/compras/conferencia-faturas",
                List.of("conferencia", "conferencia 3 way", "conferencia de faturas", "three way match", "divergencia de nota"),
                "Conferência entre pedido, nota fiscal e recebimento (3-way match).");

        reg("compras-contratos", "Contratos de Fornecimento", "Compras", "Compras > Contratos de fornecimento", "/compras/contratos",
                List.of("contrato de fornecimento", "contrato de compra", "contrato fornecedor"),
                "Contratos de fornecimento com fornecedores.");

        reg("compras-devolucoes", "Devoluções de Compra", "Compras", "Compras > Devoluções de compra", "/compras/devolucoes",
                List.of("devolucao de compra", "devolucoes de compra", "devolver ao fornecedor", "devolucao fornecedor"),
                "Devolução de mercadorias ao fornecedor.");

        // =========================================================================
        // ESTOQUE
        // =========================================================================
        reg("estoque-saldos", "Produtos e Saldos", "Estoque", "Estoque > Produtos e Saldos", "/estoque",
                List.of("estoque", "saldos", "saldo", "saldo de estoque", "posicao de estoque",
                        "quantidade em estoque", "disponivel", "consultar estoque"),
                "Consulta de saldos e posição de estoque por depósito.");

        reg("estoque-movimentacoes", "Movimentações", "Estoque", "Estoque > Movimentações", "/estoque/movimentacoes",
                List.of("movimentacao", "movimentacoes", "entrada de estoque", "saida de estoque", "ajuste de estoque"),
                "Registro de entradas, saídas, ajustes e perdas de estoque.");

        reg("estoque-depositos", "Depósitos", "Estoque", "Estoque > Depósitos", "/estoque/depositos",
                List.of("deposito", "depositos", "armazem", "almoxarifado", "galpao"),
                "Cadastro de depósitos e armazéns.");

        reg("estoque-transferencias", "Transferências", "Estoque", "Estoque > Transferências", "/estoque/transferencias",
                List.of("transferencia", "transferencias", "transferir estoque", "movimentar entre depositos"),
                "Transferência de estoque entre depósitos.");

        reg("estoque-inventarios", "Inventários", "Estoque", "Estoque > Inventários", "/estoque/inventarios",
                List.of("inventario", "inventarios", "contagem de estoque", "balanco de estoque"),
                "Contagens e inventários de estoque.");

        reg("estoque-enderecos", "Endereçamento WMS", "Estoque", "Estoque > Endereçamento WMS", "/estoque/enderecos",
                List.of("enderecamento", "enderecos", "wms", "endereco de estoque", "rua", "prateleira"),
                "Endereçamento de posições no armazém (WMS).");

        reg("estoque-lotes", "Lotes e Validade", "Estoque", "Estoque > Lotes e Validade", "/estoque/lotes",
                List.of("lote", "lotes", "validade", "rastreabilidade", "numero de serie"),
                "Controle de lotes, validade e rastreabilidade.");

        reg("estoque-reservas", "Reservas", "Estoque", "Estoque > Reservas", "/estoque/reservas",
                List.of("reserva", "reservas", "reserva de estoque", "reserva fracionada"),
                "Reservas de estoque para pedidos e vendas.");

        reg("estoque-expedicoes", "Separação e Expedição", "Estoque", "Estoque > Separação e Expedição", "/estoque/expedicoes",
                List.of("expedicao", "expedicoes", "separacao", "expedir", "romaneio de saida"),
                "Separação e expedição de mercadorias.");

        // =========================================================================
        // PRODUÇÃO
        // =========================================================================
        reg("producao-ordens", "Ordens de Produção", "Produção", "Produção > Ordens de Produção", "/producao",
                List.of("ordem de producao", "ordens de producao", "op", "produzir", "fabricacao", "pcp"),
                "Abertura e acompanhamento de ordens de produção.");

        reg("producao-romaneios", "Romaneios de Produção", "Produção", "Produção > Romaneios de Produção", "/producao/romaneios",
                List.of("romaneio", "romaneios", "romaneio de producao"),
                "Romaneios de produção.");

        reg("producao-estrutura", "Estrutura / BOM", "Produção", "Produção > Estrutura / BOM", "/producao/estrutura",
                List.of("estrutura de produto", "bom", "ficha tecnica", "composicao do produto"),
                "Estrutura de produto (BOM / ficha técnica).");

        reg("producao-roteiros", "Roteiros e Centros de Trabalho", "Produção", "Produção > Roteiros", "/producao/roteiros",
                List.of("roteiro", "roteiros", "centro de trabalho", "operacoes de producao"),
                "Roteiros de fabricação e centros de trabalho.");

        reg("producao-mrp", "MRP", "Produção", "Produção > MRP", "/producao/mrp",
                List.of("mrp", "planejamento de necessidades", "necessidade de material"),
                "Planejamento de necessidades de materiais (MRP).");

        reg("producao-mps", "MPS", "Produção", "Produção > MPS", "/producao/mps",
                List.of("mps", "plano mestre de producao"),
                "Plano mestre de produção (MPS).");

        reg("producao-oee", "OEE", "Produção", "Produção > OEE", "/producao/oee",
                List.of("oee", "eficiencia global", "indicador oee"),
                "Indicador de eficiência global dos equipamentos (OEE).");

        reg("producao-capacidade", "Capacidade", "Produção", "Produção > Capacidade", "/producao/capacidade",
                List.of("capacidade", "capacidade produtiva", "carga de maquina"),
                "Planejamento de capacidade produtiva.");

        reg("producao-apontamentos", "Apontamentos de Produção", "Produção", "Produção > Apontamentos", "/producao/apontamentos",
                List.of("apontamento", "apontamentos", "apontar producao", "registro de producao"),
                "Apontamentos de produção por ordem e operador.");

        // =========================================================================
        // FINANCEIRO
        // =========================================================================
        reg("fin-hub", "Visão Geral Financeira", "Financeiro", "Financeiro > Visão Geral", "/financeiro",
                List.of("financeiro", "visao geral", "resumo financeiro"),
                "Painel geral do módulo financeiro.");

        reg("fin-lancamentos", "Contas a Pagar e Receber", "Financeiro", "Financeiro > Contas a Pagar e Receber", "/financeiro/lancamentos",
                List.of("contas a pagar", "contas a receber", "lancamento", "lancamentos", "pagar", "receber"),
                "Contas a pagar e a receber. Passo a passo: 1. Novo lançamento; 2. Cliente/fornecedor e valor; " +
                "3. Vencimento; 4. Salve e dê baixa quando liquidado.");

        reg("fin-titulos", "Títulos", "Financeiro", "Financeiro > Títulos", "/financeiro/titulos",
                List.of("titulo", "titulos", "duplicata", "vencimento", "baixa de titulo"),
                "Controle de títulos a pagar e a receber.");

        reg("fin-aprovacoes", "Aprovações de Títulos", "Financeiro", "Financeiro > Aprovações de Títulos", "/financeiro/aprovacoes-titulos",
                List.of("aprovacao de titulo", "aprovacoes de titulos", "aprovar pagamento", "alcada"),
                "Aprovação de títulos conforme alçadas.");

        reg("fin-comissoes", "Comissões", "Financeiro", "Financeiro > Comissões", "/financeiro/comissoes",
                List.of("comissao", "comissoes", "comissao de vendedor"),
                "Apuração e pagamento de comissões.");

        reg("fin-regras-comissao", "Regras de Comissão", "Financeiro", "Financeiro > Regras de comissão", "/financeiro/regras-comissao",
                List.of("regra de comissao", "regras de comissao", "percentual de comissao"),
                "Configuração das regras de comissão.");

        reg("fin-caixa", "Caixa", "Financeiro", "Financeiro > Caixa", "/financeiro/caixa",
                List.of("caixa", "movimento de caixa", "sangria", "suprimento"),
                "Movimentação de caixa (abertura, sangria e fechamento).");

        reg("fin-boletos", "Boletos", "Financeiro", "Financeiro > Boletos", "/financeiro/boletos",
                List.of("boleto", "boletos", "gerar boleto", "remessa", "retorno"),
                "Emissão e gestão de boletos.");

        reg("fin-extrato", "Extratos", "Financeiro", "Financeiro > Extratos", "/financeiro/extrato",
                List.of("extrato", "extratos", "extrato bancario"),
                "Consulta de extratos e lançamentos bancários.");

        reg("fin-conciliacao", "Conciliação Bancária", "Financeiro", "Financeiro > Conciliação Bancária", "/financeiro/conciliacao",
                List.of("conciliacao", "conciliacao bancaria", "ofx", "extrato ofx", "conciliar banco"),
                "Importação de extrato OFX e conciliação bancária automática.");

        reg("fin-contas-bancarias", "Contas Bancárias", "Financeiro", "Financeiro > Contas Bancárias", "/financeiro/contas-bancarias",
                List.of("conta bancaria", "contas bancarias", "agencia", "conta corrente"),
                "Cadastro de contas bancárias.");

        reg("fin-plano-contas", "Plano de Contas", "Financeiro", "Financeiro > Plano de Contas", "/financeiro/plano-contas",
                List.of("plano de contas", "conta contabil", "classificacao contabil"),
                "Plano de contas contábil.");

        reg("fin-contabil", "Lançamentos Contábeis", "Financeiro", "Financeiro > Lançamentos Contábeis", "/financeiro/contabil",
                List.of("lancamento contabil", "lancamentos contabeis", "partida dobrada", "contabilizacao"),
                "Lançamentos contábeis (partida dobrada).");

        reg("fin-centro-custos", "Centro de Custos", "Financeiro", "Financeiro > Centro de Custos", "/financeiro/centro-custos",
                List.of("centro de custo", "centros de custo", "rateio de custo"),
                "Cadastro de centros de custo e rateios.");

        reg("fin-condicoes", "Condições de Pagamento", "Financeiro", "Financeiro > Condições de Pagamento", "/financeiro/condicoes-pagamento",
                List.of("condicao de pagamento", "condicoes de pagamento", "prazo", "parcelamento"),
                "Cadastro de condições e prazos de pagamento.");

        reg("fin-tipos-pagamento", "Tipos de Pagamento", "Financeiro", "Financeiro > Tipos de Pagamento", "/financeiro/tipos-pagamento",
                List.of("tipo de pagamento", "tipos de pagamento", "forma de pagamento", "pix", "dinheiro", "cartao"),
                "Cadastro de formas e tipos de pagamento.");

        reg("fin-renegociacao", "Renegociação", "Financeiro", "Financeiro > Renegociação", "/financeiro/renegociacao",
                List.of("renegociacao", "renegociar divida", "acordo", "parcelar divida"),
                "Renegociação de dívidas com clientes e fornecedores.");

        reg("fin-orcamento", "Orçado x Realizado", "Financeiro", "Financeiro > Orçado x Realizado", "/financeiro/orcamento",
                List.of("orcamento", "orcado x realizado", "budget", "planejamento financeiro"),
                "Comparativo orçado versus realizado.");

        reg("fin-emprestimos", "Empréstimos", "Financeiro", "Financeiro > Empréstimos", "/financeiro/emprestimos",
                List.of("emprestimo", "emprestimos", "financiamento"),
                "Gestão de empréstimos e financiamentos.");

        reg("fin-credito", "Crédito", "Financeiro", "Financeiro > Crédito", "/financeiro/credito",
                List.of("credito", "limite de credito", "analise de credito", "score"),
                "Análise e limite de crédito de clientes.");

        reg("fin-fluxo-caixa", "Fluxo de Caixa", "Financeiro", "Financeiro > Fluxo de caixa", "/financeiro/fluxo-caixa",
                List.of("fluxo de caixa", "caixa previsto", "projecao financeira", "dre"),
                "Fluxo de caixa previsto versus realizado e DRE.");

        reg("fin-cobranca", "Cobrança", "Financeiro", "Financeiro > Cobrança", "/financeiro/cobranca",
                List.of("cobranca", "inadimplencia", "cobrar cliente"),
                "Rotinas de cobrança e controle de inadimplência.");

        reg("fin-stripe", "Pagamentos Stripe", "Financeiro", "Financeiro > Stripe", "/financeiro/stripe",
                List.of("stripe", "pagamento online", "cartao de credito", "checkout"),
                "Integração de pagamentos via Stripe.");

        // =========================================================================
        // FISCAL
        // =========================================================================
        reg("fiscal-nfe", "NF-e (Produto)", "Fiscal", "Fiscal > NF-e", "/fiscal/nfe",
                List.of("nfe", "nf e", "nota fiscal", "nota fiscal eletronica", "emitir nota", "danfe",
                        "sefaz", "nota de produto", "nota de mercadoria"),
                "Emissão e gestão de NF-e. Passo a passo: 1. Selecione o pedido faturado ou clique em Nova NF-e; " +
                "2. Confira os dados tributários e o destinatário; 3. Clique em Transmitir para a SEFAZ.");

        reg("fiscal-nfse", "NFS-e (Serviço)", "Fiscal", "Fiscal > NFS-e", "/fiscal/nfse",
                List.of("nfse", "nfse municipal", "nota de servico", "nota fiscal de servico", "rps",
                        "prefeitura", "issqn", "nota municipal"),
                "Emissão de NFS-e. Passo a passo: 1. Selecione o tomador e o serviço cadastrado; " +
                "2. Informe o valor e as retenções; 3. Emita o RPS/NFS-e municipal.");

        reg("fiscal-certificados", "Certificado Digital", "Fiscal", "Fiscal > Certificado Digital", "/fiscal/certificados",
                List.of("certificado", "certificado digital", "a1", "assinatura digital"),
                "Gestão do certificado digital (A1/A3).");

        reg("fiscal-sped", "SPED EFD", "Fiscal", "Fiscal > SPED EFD", "/fiscal/sped",
                List.of("sped", "efd", "efd icms ipi", "arquivo sped"),
                "Geração dos arquivos SPED/EFD.");

        reg("fiscal-ncm", "NCM", "Fiscal", "Fiscal > NCM", "/fiscal/ncm",
                List.of("ncm", "codigo ncm", "classificacao ncm", "nomenclatura"),
                "Consulta e cadastro de NCM.");

        reg("fiscal-cfop", "CFOP", "Fiscal", "Fiscal > CFOP", "/fiscal/cfop",
                List.of("cfop", "codigo cfop", "natureza da operacao"),
                "Consulta e cadastro de CFOP.");

        reg("fiscal-cest", "CEST", "Fiscal", "Fiscal > CEST", "/fiscal/cest",
                List.of("cest", "codigo cest", "especificador st"),
                "Consulta e cadastro de CEST.");

        reg("fiscal-issqn", "ISSQN", "Fiscal", "Fiscal > ISSQN", "/fiscal/issqn",
                List.of("issqn", "iss", "imposto sobre servico"),
                "Configuração de alíquotas de ISSQN.");

        reg("fiscal-entradas", "Entradas de NF", "Fiscal", "Fiscal > Entradas de NF", "/fiscal/entradas",
                List.of("entrada de nota", "entradas", "importar xml", "manifestacao", "ciencia da operacao"),
                "Entrada e manifestação de notas fiscais recebidas.");

        reg("fiscal-impostos", "Impostos", "Fiscal", "Fiscal > Impostos", "/fiscal/impostos",
                List.of("imposto", "impostos", "tributo", "icms", "ipi", "pis", "cofins", "aliquota"),
                "Cadastro e parâmetros de impostos.");

        reg("fiscal-painel", "Painel Fiscal", "Fiscal", "Fiscal > Painel fiscal", "/fiscal/painel",
                List.of("painel fiscal", "resumo fiscal", "indicadores fiscais"),
                "Painel de indicadores fiscais.");

        reg("fiscal-prontidao", "Prontidão Fiscal", "Fiscal", "Fiscal > Prontidão fiscal", "/fiscal/prontidao",
                List.of("prontidao fiscal", "checklist fiscal", "conformidade fiscal"),
                "Checklist de prontidão e conformidade fiscal.");

        reg("fiscal-apuracoes", "Apuração", "Fiscal", "Fiscal > Apuração", "/fiscal/apuracoes",
                List.of("apuracao", "apuracao fiscal", "calculo de impostos"),
                "Apuração de impostos por período.");

        reg("fiscal-reinf", "EFD-Reinf", "Fiscal", "Fiscal > EFD-Reinf", "/fiscal/reinf",
                List.of("reinf", "efd reinf", "retencoes", "evento reinf"),
                "Eventos periódicos da EFD-Reinf.");

        reg("fiscal-difal", "ICMS DIFAL", "Fiscal", "Fiscal > ICMS DIFAL", "/fiscal/difal",
                List.of("difal", "icms difal", "partilha icms", "interestadual"),
                "Cálculo do ICMS DIFAL.");

        reg("fiscal-icms-st", "ICMS-ST (MVA)", "Fiscal", "Fiscal > ICMS-ST (MVA)", "/fiscal/icms-st",
                List.of("icms st", "substituicao tributaria", "mva", "st"),
                "Cálculo de ICMS Substituição Tributária.");

        reg("fiscal-regras", "Regras Tributárias", "Fiscal", "Fiscal > Regras tributárias", "/fiscal/regras-tributarias",
                List.of("regra tributaria", "regras tributarias", "tributacao", "configurar imposto", "regra fiscal"),
                "Regras tributárias automáticas aplicadas a produtos e operações.");

        reg("fiscal-simulador", "Simulador Tributação", "Fiscal", "Fiscal > Simulador tributação", "/fiscal/simulador",
                List.of("simulador", "simulador tributario", "simular imposto", "calculadora tributaria"),
                "Simulador de tributação de operações.");

        reg("fiscal-sefaz", "Consulta SEFAZ", "Fiscal", "Fiscal > Consulta SEFAZ", "/fiscal/sefaz",
                List.of("sefaz", "consulta sefaz", "status sefaz", "contingencia"),
                "Consultas e status dos serviços da SEFAZ.");

        reg("fiscal-cte-mdfe", "CT-e / MDF-e", "Fiscal", "Fiscal > CT-e / MDF-e", "/fiscal/cte-mdfe",
                List.of("cte", "mdfe", "conhecimento de transporte", "manifesto", "transporte", "frete"),
                "Emissão de CT-e e MDF-e.");

        reg("fiscal-obrigacoes", "Obrigações", "Fiscal", "Fiscal > Obrigações", "/fiscal/obrigacoes",
                List.of("obrigacoes", "obrigacao fiscal", "calendario fiscal", "esocial"),
                "Calendário e controle de obrigações fiscais.");

        reg("fiscal-busca", "Busca Fiscal", "Fiscal", "Fiscal > Busca fiscal", "/fiscal/busca",
                List.of("busca fiscal", "pesquisa fiscal", "consultar regra fiscal", "catalogo fiscal"),
                "Busca unificada no catálogo fiscal.");

        // =========================================================================
        // SERVIÇOS
        // =========================================================================
        reg("servicos-catalogo", "Catálogo de Serviços", "Serviços", "Serviços > Catálogo de Serviços", "/servicos",
                List.of("catalogo de servicos", "servicos prestados", "lista de servicos"),
                "Catálogo de serviços prestados.");

        reg("servicos-ordens", "Ordens de Serviço", "Serviços", "Serviços > Ordens de Serviço", "/ordens-servico",
                List.of("ordem de servico", "ordens de servico", "os", "assistencia tecnica", "servico de campo"),
                "Abertura e gestão de ordens de serviço.");

        // =========================================================================
        // RECURSOS HUMANOS
        // =========================================================================
        reg("rh-colaboradores", "Colaboradores", "Recursos Humanos", "Recursos Humanos > Colaboradores", "/rh",
                List.of("colaborador", "colaboradores", "funcionario", "funcionarios", "admissao", "rh"),
                "Cadastro de colaboradores e dados trabalhistas.");

        reg("rh-cargos", "Cargos", "Recursos Humanos", "Recursos Humanos > Cargos", "/rh/cargos",
                List.of("cargo", "cargos", "salario", "salarios"),
                "Cadastro de cargos e faixas salariais.");

        reg("rh-folha", "Folha de Pagamento", "Recursos Humanos", "Recursos Humanos > Folha de Pagamento", "/rh/folha",
                List.of("folha de pagamento", "folha", "holerite", "pagamento de funcionario"),
                "Processamento da folha de pagamento.");

        reg("rh-fotos", "Fotos dos Colaboradores", "Recursos Humanos", "Recursos Humanos > Fotos", "/rh/fotos",
                List.of("foto do colaborador", "fotos", "imagem do funcionario"),
                "Gestão das fotos dos colaboradores.");

        reg("rh-ponto", "Ponto", "Recursos Humanos", "Recursos Humanos > Ponto", "/rh/ponto",
                List.of("ponto", "jornada", "batida de ponto", "espelho de ponto", "horas"),
                "Controle de ponto e jornada.");

        reg("rh-rescisao", "Rescisão", "Recursos Humanos", "Recursos Humanos > Rescisão", "/rh/rescisao",
                List.of("rescisao", "desligamento", "demissao", "termo de rescisao"),
                "Processamento de rescisões trabalhistas.");

        reg("rh-ferias", "Férias", "Recursos Humanos", "Recursos Humanos > Férias", "/rh/ferias",
                List.of("ferias", "programacao de ferias", "aviso de ferias"),
                "Programação e controle de férias.");

        reg("rh-esocial", "eSocial", "Recursos Humanos", "Recursos Humanos > eSocial", "/rh/esocial",
                List.of("esocial", "eventos esocial", "sst", "envio esocial"),
                "Envio de eventos ao eSocial.");

        reg("rh-encargos", "Encargos", "Recursos Humanos", "Recursos Humanos > Encargos", "/rh/encargos",
                List.of("encargos", "inss", "fgts", "guias"),
                "Apuração de encargos sociais.");

        // =========================================================================
        // QUALIDADE
        // =========================================================================
        reg("qualidade", "Qualidade", "Qualidade", "Qualidade", "/qualidade",
                List.of("qualidade", "plano de inspecao", "inspecao", "nao conformidade", "controle de qualidade"),
                "Planos de inspeção, inspeções e não conformidades.");

        // =========================================================================
        // ATIVOS E MANUTENÇÃO
        // =========================================================================
        reg("ativos", "Ativos Imobilizados", "Ativos e Manutenção", "Ativos e Manutenção > Ativos imobilizados", "/ativos",
                List.of("ativo", "ativos", "patrimonio", "imobilizado", "bem"),
                "Cadastro de ativos imobilizados.");

        reg("ativos-manutencao", "Manutenção", "Ativos e Manutenção", "Ativos e Manutenção > Manutenção", "/ativos/manutencao",
                List.of("manutencao", "ordem de manutencao", "plano de manutencao"),
                "Manutenção de ativos (ordens, notas e planos).");

        reg("ativos-indicadores", "Indicadores MTBF/MTTR", "Ativos e Manutenção", "Ativos e Manutenção > Indicadores", "/ativos/indicadores",
                List.of("mtbf", "mttr", "indicadores de manutencao"),
                "Indicadores de manutenção (MTBF/MTTR).");

        // =========================================================================
        // BI, IA E RELATÓRIOS
        // =========================================================================
        reg("bi", "Business Intelligence", "Inteligência e Análise", "Inteligência e Análise > Business Intelligence", "/bi",
                List.of("bi", "business intelligence", "dashboard gerencial", "indicadores", "painel"),
                "Painéis e dashboards gerenciais.");

        reg("bi-kpis", "KPIs", "Inteligência e Análise", "Inteligência e Análise > KPIs", "/bi/kpis",
                List.of("kpi", "kpis", "indicadores", "metas de indicadores"),
                "Indicadores-chave de desempenho.");

        reg("bi-relatorios", "Relatórios BI", "Inteligência e Análise", "Inteligência e Análise > Relatórios BI", "/bi/relatorios",
                List.of("relatorio bi", "relatorios bi", "relatorios gerenciais", "analise"),
                "Relatórios analíticos de BI.");

        reg("bi-relatorios-agendados", "Relatórios Agendados", "Inteligência e Análise", "Inteligência e Análise > Relatórios Agendados", "/bi/relatorios-agendados",
                List.of("relatorio agendado", "relatorios agendados", "agendar relatorio", "envio de relatorio"),
                "Agendamento e envio automático de relatórios.");

        reg("bi-auditoria", "Auditoria Funcional ERP", "Inteligência e Análise", "Inteligência e Análise > Auditoria Funcional ERP", "/bi/auditoria-funcional",
                List.of("auditoria", "auditoria funcional", "paridade funcional"),
                "Auditoria funcional do ERP.");

        reg("ia", "Inteligência Artificial", "Inteligência e Análise", "Inteligência e Análise > Inteligência Artificial", "/ia",
                List.of("ia", "inteligencia artificial", "assistente", "copiloto", "chat ia"),
                "Módulo de IA assistiva (classificação, previsão e embeddings).");

        reg("assistente", "Assistente ERP", "Inteligência e Análise", "Inteligência e Análise > Assistente ERP", "/assistente",
                List.of("assistente", "assistente erp", "ajuda", "copiloto", "duvidas"),
                "Tela do assistente ERP para perguntas e orientações.");

        reg("relatorios", "Relatórios", "Inteligência e Análise", "Inteligência e Análise > Relatórios", "/relatorios",
                List.of("relatorio", "relatorios", "imprimir relatorio"),
                "Central de relatórios do sistema.");

        // =========================================================================
        // DEMAIS MÓDULOS E TELAS ENTERPRISE
        // =========================================================================
        reg("crm", "CRM", "CRM", "CRM", "/crm",
                List.of("crm", "lead", "leads", "oportunidade", "pipeline", "funil de vendas", "forecast"),
                "Gestão de leads, oportunidades e pipeline de vendas.");

        reg("wms", "WMS", "WMS", "WMS", "/wms",
                List.of("wms", "armazem", "warehouse", "picking", "separacao"),
                "Gestão de armazém (WMS).");

        reg("projetos", "Projetos", "Projetos", "Projetos", "/projetos",
                List.of("projeto", "projetos", "tarefas", "cronograma"),
                "Gestão de projetos e tarefas.");

        reg("dms", "DMS", "DMS", "DMS", "/dms",
                List.of("dms", "gestao de documentos", "documentos", "ged"),
                "Gestão eletrônica de documentos (DMS).");

        reg("portais", "Portais", "Portais", "Portais", "/portais",
                List.of("portal", "portais", "area do cliente"),
                "Gestão de portais e áreas externas.");

        reg("workflow", "Workflow", "Workflow", "Workflow", "/workflow",
                List.of("workflow", "fluxo de aprovacao", "processos", "automacao"),
                "Workflow e fluxos de aprovação.");

        reg("contabilidade", "Contabilidade", "Contabilidade", "Contabilidade", "/contabilidade",
                List.of("contabilidade", "contabil", "escrituracao"),
                "Rotinas contábeis e escrituração.");

        reg("contabilidade-enterprise", "Contabilidade Enterprise / Close", "Contabilidade", "Financeiro > Contabilidade Enterprise / Close", "/contabilidade/enterprise",
                List.of("contabilidade enterprise", "fechamento contabil", "close contabil"),
                "Fechamento contábil corporativo (close).");

        reg("helpdesk", "Helpdesk", "Helpdesk", "Helpdesk", "/helpdesk",
                List.of("helpdesk", "atendimento", "ticket", "chamado", "suporte"),
                "Central de atendimento e chamados (helpdesk).");

        reg("agenda", "Agenda", "Agenda", "Agenda", "/agenda",
                List.of("agenda", "compromissos", "eventos", "calendario"),
                "Agenda de compromissos e eventos.");

        reg("conhecimento", "Conhecimento", "Conhecimento", "Conhecimento", "/conhecimento",
                List.of("conhecimento", "base de conhecimento", "artigos", "faq", "ajuda"),
                "Base de conhecimento e artigos de ajuda.");

        reg("operacoes", "Operações (Painel)", "Operações", "Operações (painel)", "/operacoes",
                List.of("operacoes", "painel de operacoes", "torre de controle"),
                "Painel consolidado de operações.");

        reg("integracoes-enterprise", "Integrações Enterprise", "Integrações", "Integrações Enterprise", "/integracoes-enterprise",
                List.of("integracoes", "integracoes enterprise", "api de integracao"),
                "Integrações corporativas.");

        reg("supply-chain-enterprise", "Supply Chain / Engenharia", "Supply Chain", "Supply Chain / Engenharia", "/supply-chain-enterprise",
                List.of("supply chain", "cadeia de suprimentos", "engenharia"),
                "Supply chain e engenharia (enterprise).");

        reg("supply-chain-control-tower", "Supply Chain Control Tower", "Supply Chain", "Supply Chain Control Tower", "/supply-chain/control-tower",
                List.of("control tower", "torre de controle supply chain", "bi control tower"),
                "Torre de controle da cadeia de suprimentos.");

        reg("plm", "Ciclo de Vida (PLM)", "Supply Chain", "Ciclo de vida (PLM)", "/plm/mudancas",
                List.of("plm", "ciclo de vida", "engenharia de produto", "gestao de mudancas"),
                "Gestão do ciclo de vida do produto (PLM).");

        reg("tms-planejamento", "TMS Planejamento", "Logística", "TMS Planejamento", "/enterprise/tms-planejamento",
                List.of("tms", "transporte", "planejamento de transporte"),
                "Planejamento de transportes (TMS).");

        reg("grc-tms", "GRC / TMS Enterprise", "Governança", "GRC / TMS Enterprise", "/riscos-tms-enterprise",
                List.of("grc", "riscos", "compliance", "tms enterprise"),
                "Governança, riscos e compliance (GRC).");

        reg("intercompany", "Eliminações Intercompany", "Financeiro", "Eliminações intercompany", "/enterprise/intercompany-eliminacoes",
                List.of("intercompany", "eliminacoes", "consolidacao"),
                "Eliminações intercompany para consolidação.");

        reg("governanca", "Governança Corporativa", "Governança", "Governança Corporativa", "/governanca-corporativa",
                List.of("governanca", "governanca corporativa", "conselho"),
                "Rotinas de governança corporativa.");

        reg("gestao-empresarial", "Gestão Empresarial", "Gestão", "Gestão Empresarial", "/gestao-empresarial",
                List.of("gestao empresarial", "cockpit", "visao executiva"),
                "Cockpit de gestão empresarial.");
    }

    private void reg(String id, String titulo, String modulo, String caminhoMenu, String rota,
                     List<String> sinonimos, String descricaoPassoAPasso) {
        catalogo.add(new ItemNavegacao(id, titulo, modulo, caminhoMenu, rota, sinonimos, descricaoPassoAPasso));
    }

    /**
     * Busca os itens mais relevantes no catálogo para a pergunta ou termo informado.
     *
     * <p>A pontuação prioriza, nesta ordem: título idêntico (frase), sinônimo idêntico
     * (frase), palavra no título, palavra no módulo/menu e palavra em sinônimo.
     * Palavras de ligação ("onde", "como", "ver"...) são ignoradas.
     */
    public List<ItemNavegacao> buscar(String textoBusca) {
        if (textoBusca == null || textoBusca.isBlank()) {
            return List.of();
        }
        String normalizado = NormalizacaoFiscal.texto(textoBusca);
        List<String> palavras = Arrays.stream(normalizado.split("\\s+"))
                .filter(p -> p.length() >= 3)
                .filter(p -> !STOPWORDS.contains(p))
                .distinct()
                .toList();

        Map<ItemNavegacao, Integer> pontuacoes = new HashMap<>();

        for (ItemNavegacao item : catalogo) {
            String tituloNorm = NormalizacaoFiscal.texto(item.titulo());
            String moduloNorm = NormalizacaoFiscal.texto(item.modulo());
            String menuNorm = NormalizacaoFiscal.texto(item.caminhoMenu());
            List<String> sinonimosNorm = item.sinonimos().stream()
                    .map(NormalizacaoFiscal::texto)
                    .filter(s -> !s.isBlank())
                    .toList();

            int score = 0;

            if (!tituloNorm.isBlank() && normalizado.contains(tituloNorm)) {
                score += 50;
            }
            for (String sin : sinonimosNorm) {
                if (normalizado.contains(sin)) {
                    score += 40;
                    break;
                }
            }
            for (String palavra : palavras) {
                if (tituloNorm.contains(palavra)) {
                    score += 10;
                }
                if (moduloNorm.contains(palavra)) {
                    score += 4;
                }
                if (menuNorm.contains(palavra)) {
                    score += 4;
                }
                for (String sin : sinonimosNorm) {
                    if (sin.contains(palavra)) {
                        score += 6;
                        break;
                    }
                }
            }

            if (score > 0) {
                pontuacoes.put(item, score);
            }
        }

        return pontuacoes.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<ItemNavegacao, Integer> e) -> e.getValue()).reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    public Optional<ItemNavegacao> buscarMaisRelevante(String textoBusca) {
        List<ItemNavegacao> resultados = buscar(textoBusca);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public List<ItemNavegacao> getTodos() {
        return Collections.unmodifiableList(catalogo);
    }
}
