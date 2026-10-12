package br.com.brasil_saas.ia.assistente;

import br.com.brasil_saas.fiscal.busca.NormalizacaoFiscal;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Catálogo central de telas, rotas e funcionalidades do Brasil SaaS ERP.
 * Permite que a IA saiba exatamente onde cada função do sistema está localizada,
 * o caminho no menu, a rota no frontend e o passo a passo para o usuário.
 */
@Service
public class CatalogoNavegacaoService {

    public record ItemNavegacao(
            String id,
            String titulo,
            String modulo,
            String caminhoMenu,
            String rota,
            List<String> sinonimos,
            String descricaoPassoAPasso
    ) {}

    private final List<ItemNavegacao> catalogo = new ArrayList<>();

    public CatalogoNavegacaoService() {
        carregarCatalogo();
    }

    private void carregarCatalogo() {
        // === FISCAL ===
        registrar("fiscal-nfe", "Emissão de NF-e (Produto)", "Fiscal",
                "Fiscal > Documentos Fiscais > NF-e", "/fiscal/nfe",
                List.of("nfe", "nota fiscal", "emitir nota", "danfe", "nota de produto", "faturar nfe", "sefaz"),
                "Acesse a tela de NF-e para visualizar, transmitir ou cancelar notas fiscais eletrônicas de mercadorias. " +
                "Para emitir: 1. Selecione o pedido de venda faturado ou clique em Nova NF-e; " +
                "2. Confira os dados tributários e destinatário; 3. Clique em Transmitir para a SEFAZ.");

        registrar("fiscal-nfse", "Emissão de NFS-e (Serviço)", "Fiscal",
                "Fiscal > Documentos Fiscais > NFS-e", "/fiscal/nfse",
                List.of("nfse", "nota de servico", "nota municipal", "rps", "servico", "prefeitura", "issqn"),
                "Acesse a tela de NFS-e para emitir notas fiscais de serviço municipal. " +
                "Passo a passo: 1. Selecione o tomador (cliente) e o serviço cadastrado com código municipal; " +
                "2. Informe o valor do serviço e retenções; 3. Clique em Emitir NFS-e.");

        registrar("fiscal-cte", "Conhecimento de Transporte (CT-e)", "Fiscal",
                "Fiscal > Documentos Fiscais > CT-e", "/fiscal/cte",
                List.of("cte", "transporte", "conhecimento de transporte", "frete"),
                "Emissão e acompanhamento de CT-e para prestação de serviços de transporte de carga.");

        registrar("fiscal-mdfe", "Manifesto de Documentos Fiscais (MDF-e)", "Fiscal",
                "Fiscal > Documentos Fiscais > MDF-e", "/fiscal/mdfe",
                List.of("mdfe", "manifesto", "carga", "transporte manifesto"),
                "Emissão de MDF-e para consolidação de cargas e documentos fiscais de transporte.");

        registrar("fiscal-regras", "Regras Tributárias & Simulador", "Fiscal",
                "Fiscal > Configurações > Regras Tributárias", "/fiscal/regras-tributarias",
                List.of("regra tributaria", "tributacao", "cfop", "aliquota", "icms", "ipi", "pis", "cofins", "cst"),
                "Configuração e simulação das regras tributárias automáticas aplicadas a produtos e operações.");

        registrar("fiscal-reinf", "EFD-Reinf & Apuração Fiscal", "Fiscal",
                "Fiscal > Obrigações > EFD-Reinf", "/fiscal/reinf",
                List.of("reinf", "efd reinf", "apuracao", "retencoes", "sped"),
                "Geração e transmissão dos eventos periódicos da EFD-Reinf e apuração de tributos retidos.");

        // === VENDAS ===
        registrar("vendas-pedidos", "Pedidos de Venda", "Vendas",
                "Vendas > Pedidos de Venda", "/vendas/pedidos",
                List.of("pedido de venda", "pedidos", "vender", "novo pedido", "orcamento", "faturar pedido", "vendas"),
                "Gerenciamento completo de pedidos de venda e orçamentos. " +
                "Passo a passo: 1. Clique em Novo Pedido; 2. Selecione o cliente e a condição de pagamento; " +
                "3. Adicione os itens (produtos ou serviços); 4. Salve e aprove o pedido para faturamento.");

        registrar("vendas-devolucoes", "Devoluções de Venda", "Vendas",
                "Vendas > Devoluções", "/vendas/devolucoes",
                List.of("devolucao", "devolver venda", "troca", "devolver produto", "estorno venda"),
                "Registro e controle de devoluções de venda, geração de crédito para cliente e retorno ao estoque.");

        registrar("vendas-contratos", "Contratos de Venda", "Vendas",
                "Vendas > Contratos", "/vendas/contratos",
                List.of("contrato", "contrato de venda", "recorrencia", "assinatura"),
                "Gestão de contratos comerciais, faturamento recorrente e reajustes de contratos de venda.");

        // === CADASTROS ===
        registrar("cadastros-clientes", "Cadastro de Clientes", "Cadastros",
                "Cadastros > Clientes", "/cadastros/clientes",
                List.of("cliente", "cadastrar cliente", "novo cliente", "cnpj cliente", "comprador"),
                "Cadastro e gestão da carteira de clientes. " +
                "Passo a passo: 1. Clique em Novo Cliente; 2. Digite o CNPJ/CPF (com consulta automática); " +
                "3. Complete os dados de contato e endereço; 4. Clique em Salvar.");

        registrar("cadastros-fornecedores", "Cadastro de Fornecedores", "Cadastros",
                "Cadastros > Fornecedores", "/cadastros/fornecedores",
                List.of("fornecedor", "cadastrar fornecedor", "novo fornecedor", "parceiro"),
                "Cadastro de fornecedores de insumos, mercadorias e prestadores de serviço.");

        registrar("cadastros-produtos", "Cadastro de Produtos", "Cadastros",
                "Cadastros > Produtos", "/cadastros/produtos",
                List.of("produto", "cadastrar produto", "mercadoria", "item", "ncm produto", "estoque produto"),
                "Cadastro de produtos para compra, venda e manufatura. " +
                "Passo a passo: 1. Clique em Novo Produto; 2. Preencha nome, unidade e preço; " +
                "3. Na aba Fiscal, informe o NCM e regras de tributação; 4. Salve.");

        registrar("cadastros-servicos", "Cadastro de Serviços", "Cadastros",
                "Cadastros > Serviços", "/cadastros/servicos",
                List.of("servico", "cadastrar servico", "codigo municipal", "aliquota iss"),
                "Cadastro de catálogo de serviços prestados, com código municipal e NBS para emissão de NFS-e.");

        // === COMPRAS ===
        registrar("compras-pedidos", "Pedidos de Compra", "Compras",
                "Compras > Pedidos de Compra", "/compras/pedidos",
                List.of("pedido de compra", "comprar", "compras", "ordem de compra", "fornecedor pedido"),
                "Gestão de pedidos de compra e aprovação de suprimentos.");

        registrar("compras-conferencia", "Conferência de Entrada (3-Way Match)", "Compras",
                "Compras > Conferência de Entrada", "/compras/conferencia",
                List.of("conferencia", "conferencia cega", "entrada de nota", "recebimento", "xml compra"),
                "Conferência física e fiscal no recebimento de mercadorias comparando XML da NF-e e pedido de compra.");

        // === ESTOQUE & WMS ===
        registrar("estoque-saldos", "Saldos e Posição de Estoque", "Estoque",
                "Estoque > Posição de Estoque", "/estoque/saldos",
                List.of("estoque", "saldo", "quantidade em estoque", "inventario", "consultar estoque"),
                "Consulta em tempo real dos saldos disponíveis, reservados e custos médios por depósito.");

        registrar("estoque-movimentacoes", "Movimentações de Estoque", "Estoque",
                "Estoque > Movimentações", "/estoque/movimentacoes",
                List.of("movimentacao estoque", "entrada estoque", "saida estoque", "ajuste estoque", "transferencia"),
                "Registro manual de entradas, saídas, perdas e transferências entre almoxarifados.");

        // === FINANCEIRO & CONTABILIDADE ===
        registrar("financeiro-titulos", "Contas a Pagar e Receber", "Financeiro",
                "Financeiro > Títulos", "/financeiro/titulos",
                List.of("contas a pagar", "contas a receber", "titulo", "boleto", "duplicata", "vencimento", "pagamento"),
                "Controle integrado de contas a pagar e a receber, baixas de títulos e controle de inadimplência.");

        registrar("financeiro-conciliacao", "Conciliação Bancária", "Financeiro",
                "Financeiro > Conciliação Bancária", "/financeiro/conciliacao",
                List.of("conciliacao", "extrato", "ofx", "banco conciliacao", "bancario"),
                "Importação de extratos OFX e conciliação bancária automática com os lançamentos financeiros.");

        registrar("financeiro-fluxo", "Fluxo de Caixa & DRE", "Financeiro",
                "Financeiro > Fluxo de Caixa", "/financeiro/fluxo-caixa",
                List.of("fluxo de caixa", "dre", "demonstrativo", "receita", "despesa", "lucro"),
                "Relatórios gerenciais de fluxo de caixa previsto versus realizado e DRE.");

        // === PRODUÇÃO & PCP ===
        registrar("producao-ordens", "Ordens de Produção (PCP)", "Produção",
                "Produção > Ordens de Produção", "/producao/ordens",
                List.of("ordem de producao", "op", "produzir", "fabricacao", "pcp", "mrp"),
                "Abertura, acompanhamento e fechamento de ordens de produção industrial.");

        // === RECURSOS HUMANOS ===
        registrar("rh-funcionarios", "Gestão de Colaboradores", "Recursos Humanos",
                "RH > Funcionários", "/rh/funcionarios",
                List.of("funcionario", "colaborador", "admissao", "rh", "folha", "esocial"),
                "Cadastro de funcionários, dependentes, cargos e integração com eSocial.");

        // === BI & RELATÓRIOS ===
        registrar("bi-relatorios", "Business Intelligence & Relatórios", "BI",
                "BI > Relatórios Gerenciais", "/bi/relatorios",
                List.of("relatorio", "bi", "dashboard", "indicadores", "graficos", "vendas bi", "metricas"),
                "Painéis executivos e relatórios consolidados de vendas, compras, finanças e estoque.");
    }

    private void registrar(String id, String titulo, String modulo, String caminhoMenu, String rota,
                           List<String> sinonimos, String descricaoPassoAPasso) {
        catalogo.add(new ItemNavegacao(id, titulo, modulo, caminhoMenu, rota, sinonimos, descricaoPassoAPasso));
    }

    /**
     * Busca os itens mais relevantes no catálogo para a pergunta ou termo informado.
     */
    public List<ItemNavegacao> buscar(String textoBusca) {
        if (textoBusca == null || textoBusca.isBlank()) {
            return List.of();
        }
        String normalizado = NormalizacaoFiscal.texto(textoBusca);
        List<String> palavras = Arrays.stream(normalizado.split("\\s+"))
                .filter(p -> p.length() >= 3)
                .toList();

        Map<ItemNavegacao, Integer> pontuacoes = new HashMap<>();

        for (ItemNavegacao item : catalogo) {
            int score = 0;
            String tituloNorm = NormalizacaoFiscal.texto(item.titulo());
            String moduloNorm = NormalizacaoFiscal.texto(item.modulo());
            String menuNorm = NormalizacaoFiscal.texto(item.caminhoMenu());

            // Correspondência exata em título ou rota
            if (normalizado.contains(tituloNorm) || normalizado.contains(item.id())) {
                score += 50;
            }

            for (String sin : item.sinonimos()) {
                String sinNorm = NormalizacaoFiscal.texto(sin);
                if (normalizado.contains(sinNorm)) {
                    score += 30;
                }
            }

            for (String palavra : palavras) {
                if (tituloNorm.contains(palavra)) score += 10;
                if (moduloNorm.contains(palavra)) score += 5;
                if (menuNorm.contains(palavra)) score += 5;
                for (String sin : item.sinonimos()) {
                    if (NormalizacaoFiscal.texto(sin).contains(palavra)) {
                        score += 8;
                    }
                }
            }

            if (score > 0) {
                pontuacoes.put(item, score);
            }
        }

        return pontuacoes.entrySet().stream()
                .sorted(Map.Entry.<ItemNavegacao, Integer>comparingByValue().reversed())
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
