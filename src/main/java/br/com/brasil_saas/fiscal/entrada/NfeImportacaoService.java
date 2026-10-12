package br.com.brasil_saas.fiscal.entrada;

import br.com.brasil_saas.cadastro.model.Produto;
import br.com.brasil_saas.cadastro.model.Pessoa;
import br.com.brasil_saas.cadastro.repository.ProdutoRepository;
import br.com.brasil_saas.cadastro.repository.PessoaRepository;
import br.com.brasil_saas.estoque.model.MovimentacaoEstoque;
import br.com.brasil_saas.estoque.model.SaldoEstoque;
import br.com.brasil_saas.estoque.repository.DepositoRepository;
import br.com.brasil_saas.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.fiscal.model.Nfe;
import br.com.brasil_saas.fiscal.model.NfeItem;
import br.com.brasil_saas.fiscal.repository.NfeItemRepository;
import br.com.brasil_saas.fiscal.repository.NfeRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Importa uma NFe de arquivo XML e da entrada no estoque.
 *
 * <h3>Por que dois passos e nao um so</h3>
 * Importar e uma acao que mexe no estoque, cria produto e grava documento
 * fiscal. Um unico botao que faz tudo e um botao cujo erro sai caro e nao tem
 * volta facil: o estoque ja recebeu quantidade a mais e a movimentacao ja foi
 * gravada. Por isso o fluxo e <b>ler, mostrar o plano, confirmar</b>:
 * <ol>
 *   <li>{@link #analisar} le o XML e diz o que vai acontecer, sem gravar
 *       nada — inclusive quantos produtos precisam ser criados e quais itens
 *       ficaram sem casar.</li>
 *   <li>A tela mostra o plano. O usuario ve os valores antes de o estoque
 *       mudar.</li>
 *   <li>{@link #confirmar} grava.</li>
 * </ol>
 *
 * <h3>Por que ler XML e nao consultar a SEFAZ</h3>
 * A SEFAZ exige o certificado do destinatario e cobra cota por consulta. Ler o
 * arquivo que o fornecedor ja mandou nao gasta cota e funciona com o ERP
 * desconectado. Alem disso o ERP nao tem cliente de consulta a SEFAZ
 * funcionando: {@code NFeServiceImpl} tem 89 linhas e tres TODOs. "Consultar
 * na SEFAZ" hoje nao e opcao, e escrever o cliente do zero e um trabalho
 * separado.
 *
 * <h3>O casamento de item com produto</h3>
 * Nenhum criterio sozinho e confiavel, entao e em tres degraus e a tela mostra
 * em qual degrau cada item casou:
 * <ol>
 *   <li><b>GTIN (cEAN)</b> — confiavel. O mesmo produto recebe codigo de
 *       fornecedor diferente em cada um, mas o codigo de barras e o mesmo
 *       em qualquer lugar do mundo.</li>
 *   <li><b>cProd do fornecedor + descricao igual</b> — confiavel dentro de um
 *       mesmo fornecedor, que e o caso comum. Fora dele, o mesmo cProd pode
 *       significar outra coisa.</li>
 *   <li><b>Nada</b> — o item fica <b>sem produto</b> e nao entra no estoque.
 *       Criar produto com nome chumbado do XML e o jeito de encher o cadastro
 *       de "CHURRASQUEIRA ELETRICA PORTATIL SEM FUMACA GRILL 2000W GRANDE
 *       LINHA PREMIUM", que depois ninguem acha por "churrasqueira".</li>
 * </ol>
 * O item sem produto <b>nao impede</b> a entrada: a nota e gravada inteira, e
 * so o item fica sem movimento de estoque. Perder a nota inteira por causa de
 * um item e pior do que o item ficar de fora, porque a conciliação com o
 * fornecedor depende da nota existir.
 *
 * <h3>Sobre o desconto</h3>
 * O valor que entra no estoque e a soma dos itens <b>menos o desconto da
 * nota</b>, e nao o {@code vNF} nem o {@code vProd} do item. Numa das notas de
 * teste a soma dos itens da 377,24 e o {@code vNF} da 293,98, porque a nota
 * tem {@code vDesc} de 83,26. Entrar pela soma infla o estoque; entrar pelo
 * vProd item a item faz o mesmo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NfeImportacaoService {

    private final NfeXmlReader reader;
    private final NfeRepository nfeRepo;
    private final NfeItemRepository itemRepo;
    private final ProdutoRepository produtoRepo;
    private final PessoaRepository pessoaRepo;
    private final SaldoEstoqueRepository saldoRepo;
    private final DepositoRepository depositoRepo;
    private final MovimentacaoEstoqueRepository movRepo;

    /** Em quanto centavos o estoque aceita a diferenca. */
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    // ------------------------------------------------------------------ //
    // 1. Analise: le e diz o plano, sem gravar nada
    // ------------------------------------------------------------------ //

    /**
     * Como cada item da nota foi resolvido contra o cadastro.
     * situacao: CADASTRADO (EAN/cProd), A_CRIAR, SEM_PRODUTO, CADASTRADO_COM_COMPLEMENTO.
     */
    public record SituacaoItem(
            int numeroItem, String descricao, String cProd, String ean,
            String ncm, String cest, String cfop, String unidade,
            BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotal,
            Long produtoId, String produtoNome, Situacao situacao) {
    }

    public enum Situacao { CADASTRADO, A_CRIAR, SEM_PRODUTO, CADASTRADO_COM_COMPLEMENTO }

    /**
     * O que a tela mostra antes de confirmar.
     *
     * @param avisos mensagens que o usuario precisa ler antes de confirmar.
     *                Nenhuma delas impede a confirmacao, porque a tela é que
     *                decide; o que elas fazem e evitar que o usuario confirme
     *                sem saber.
     */
    public record Plano(
            String chave, String numero, String serie, LocalDateTime emissao,
            String emitenteCnpj, String emitenteNome,
            String destinatarioCnpj, String destinatarioNome,
            String naturezaOperacao,
            BigDecimal valorTotal, BigDecimal valorDesconto,
            BigDecimal somaItens, BigDecimal valorLiquido,
            BigDecimal valorFrete, BigDecimal valorIcms,
            BigDecimal valorIpi, BigDecimal valorPis, BigDecimal valorCofins,
            Long pessoaId, String pessoaSituacao,
            List<SituacaoItem> itens,
            List<String> avisos) {
    }

    /**
     * Le o XML e monta o plano. Nao grava nada.
     *
     * @param criarProdutos true para listar como "a criar"; false para listar
     *                      como "sem produto", que e o padrao. Criar produto
     *                      com o nome do XML suja o cadastro, entao e opcao.
     */
    @Transactional(readOnly = true)
    public Plano analisar(Long empresaId, MultipartFile arquivo, Long pessoaId,
                           boolean criarProdutos) {
        NfeXmlReader.Leitura l = reader.ler(arquivo);
        return plano(empresaId, l, pessoaId, criarProdutos);
    }

    /** O mesmo, a partir do XML ja lido. Usado no teste. */
    @Transactional(readOnly = true)
    public Plano analisar(Long empresaId, byte[] xml, String nome, Long pessoaId,
                           boolean criarProdutos) {
        return plano(empresaId, reader.ler(xml, nome), pessoaId, criarProdutos);
    }

    private Plano plano(Long empresaId, NfeXmlReader.Leitura l, Long pessoaId,
                        boolean criarProdutos) {
        List<String> avisos = new ArrayList<>();

        // --- fornecedor -------------------------------------------------
        // A pessoa vem do formulario quando o usuario ja escolheu. Sem ela,
        // tenta pelo CNPJ do emitente, que e o caminho comum: o fornecedor ja
        // esta cadastrado e o usuario nao deveria ter que escolher.
        Long pessoa = pessoaId;
        String sitPessoa;
        if (pessoa != null) {
            sitPessoa = "ESCOLHIDO";
        } else {
            Optional<Pessoa> achada = l.emitenteCnpj() == null
                    ? Optional.empty()
                    : pessoaRepo.findByEmpresaIdAndDocumentoAndDeletedAtIsNull(empresaId, l.emitenteCnpj());
            if (!achada.isEmpty()) {
                pessoa = achada.get().getId();
                sitPessoa = "ACHADO_PELO_CNPJ";
            } else {
                sitPessoa = "NAO_CADASTRADO";
                avisos.add("O emitente " + (l.emitenteNome() == null ? "" : l.emitenteNome())
                        + " (CNPJ " + l.emitenteCnpj() + ") nao esta cadastrado como "
                        + "fornecedor. A nota entra assim mesmo e o vinculo fica pendente.");
            }
        }

        // --- chave ja registrada ---------------------------------------
        if (!l.chave() != null && nfeRepo
                .findByEmpresaIdAndChaveAcessoAndDeletedAtIsNull(empresaId, l.chave()).isEmpty()) {
            throw new BusinessException("Esta nota ja foi registrada. Chave " + l.chave());
        }
        if (l.chave() == null) {
            avisos.add("O XML nao tem chave de acesso. A nota entra sem chave, "
                    + "e nao da para consultar o status dela na prefeitura depois.");
        }

        // --- valores ----------------------------------------------------
        BigDecimal desconto = l.valorDesconto() == null ? BigDecimal.ZERO : l.valorDesconto();
        BigDecimal soma = soma(l.itens());
        BigDecimal liquido = soma.subtract(desconto);

        // A nota e da prefeitura, entao quando soma menos desconto nao bate
        // com o vNF, ou um dos dois esta errado. Isso NAO e erro do leitor: e
        // o emissor que fechou a conta de um jeito. Avisa e deixa o usuario
        // decidir, porque o valor que entra no estoque tem que ser o da nota.
        if (l.valorTotal() != null && l.valorTotal().compareTo(BigDecimal.ZERO) != 0
                && l.valorTotal().subtract(liquido).abs().compareTo(TOLERANCIA) > 0) {
            avisos.add("A soma dos itens menos o desconto da " + l.valorTotal()
                    + " e o total da nota sao diferentes ("
                    + l.valorTotal().subtract(liquido).setScale(2, RoundingMode.HALF_UP)
                    + " de diferenca). Vou usar a soma dos itens menos o desconto, "
                    + "que e o que a prefeitura desconta. Confira antes de confirmar.");
        }
        if (desconto.compareTo(BigDecimal.ZERO) > 0) {
            avisos.add("Desconto de " + desconto + " na nota. Ele e descontado do "
                    + "estoque: sem isso o saldo entraria inflado.");
        }

        // --- itens ------------------------------------------------------
        List<SituacaoItem> itens = new ArrayList<>();
        int aCriar = 0, semProduto = 0, comComplemento = 0;
        for (NfeXmlReader.Item it : l.itens()) {
            Optional<Produto> achado = casar(empresaId, it);
            Situacao sit;
            Long produtoId = achado.map(Produto::getId).orElse(null);
            String nomeProduto = achado.map(Produto::getNome).orElse(null);

            if (!achado.isEmpty()) {
                // O produto existe mas pode estar sem o dado fiscal que a nota
                // traz. Isso e comum — o cadastro foi feito antes de a nota
                // aparecer — e e a opportunidade de corrigir sem digitar.
                if (faltou(achado.get(), it)) {
                    sit = Situacao.CADASTRADO_COM_COMPLEMENTO;
                    comComplemento++;
                } else {
                    sit = Situacao.CADASTRADO;
                }
            } else if (criarProdutos) {
                sit = Situacao.A_CRIAR;
                aCriar++;
            } else {
                sit = Situacao.SEM_PRODUTO;
                semProduto++;
            }

            itens.add(new SituacaoItem(
                    it.numeroItem(), it.descricao(), it.cProd(), it.ean(),
                    it.ncm(), it.cest(), it.cfop(), it.unidade(),
                    it.quantidade(), it.valorUnitario(), it.valorTotal(),
                    produtoId, nomeProduto, sit));
        }

        if (semProduto > 0) {
            avisos.add(semProduto + " de " + itens.size() + " item(ns) nao casaram com "
                    + "produto do cadastro e nao vao mexer no estoque. A nota entra "
                    + "inteira assim mesmo. Marque 'criar produtos' se quiser que "
                    + "eles sejam cadastrados com o nome do XML.");
        }
        if (aCriar > 0) {
            avisos.add(aCriar + " produto(s) serao criados com o nome que veio no XML. "
                    + "Vale conferir a descricao depois: o nome do fornecedor costuma "
                    + "ser mais longo que o do seu cadastro.");
        }
        if (comComplemento > 0) {
            avisos.add(comComplemento + " produto(s) existem mas estao sem NCM, CEST ou "
                    + "CFOP. Confirmar preenche com o que veio na nota.");
        }
        if (itens.isEmpty()) {
            avisos.add("O XML nao tem nenhum item. Uma nota sem item nao entra no estoque.");
        }

        return new Plano(
                l.chave(), l.numero(), l.serie(),
                l.emissao() == null ? null : l.emissao().atStartOfDay(),
                l.emitenteCnpj(), l.emitenteNome(),
                l.destinatarioCnpj(), l.destinatarioNome(),
                l.naturezaOperacao(),
                l.valorTotal(), desconto, soma, liquido,
                valor(l.valorFrete()), valor(l.valorIcms()),
                valor(l.valorIpi()), valor(l.valorPis()), valor(l.valorCofins()),
                pessoa, sitPessoa, itens, avisos);
    }

    /**
     * Casa o item com um produto, em tres degraus.
     *
     * <p>O terceiro degrau nao existe de proposito. Casar por nome parece
     * sensato e nao e: "CHURRASQUEIRA ELETRICA PORTATIL SEM FUMACA GRILL 2000W
     * GRANDE LINHA PREMIUM" e o mesmo produto com grafia diferente em notas de
     * fornecedores diferentes, e produto com nome parecido que nao e o mesmo
     * produto. Errar aqui nao da erro na tela — da entrada errada no estoque.
     */
    private Optional<Produto> casar(Long empresaId, NfeXmlReader.Item it) {
        if (it.ean() != null) {
            Optional<Produto> porEan = produtoRepo
                    .findByEmpresaIdAndCodigoBarrasAndDeletedAtIsNull(empresaId, it.ean());
            if (!porEan.isEmpty()) return porEan;
        }
        if (it.cProd() != null) {
            Optional<Produto> porCodigo = produtoRepo
                    .findFirstByCodigoIgnoreCaseAndDeletedAtIsNull(it.cProd());
            if (!porCodigo.isEmpty()) return porCodigo;
        }
        return Optional.empty();
    }

    /** O que a nota traz e o produto nao tem. */
    private boolean faltou(Produto p, NfeXmlReader.Item it) {
        return (it.ncm() != null && !it.ncm().isBlank()
                && (p.getNcm() == null || p.getNcm().isBlank()))
                || (it.cest() != null && !it.cest().isBlank()
                    && (p.getCest() == null || p.getCest().isBlank()))
                || (it.cfop() != null && !it.cfop().isBlank()
                    && (p.getCfopPadrao() == null || p.getCfopPadrao().isBlank()));
    }

    // ------------------------------------------------------------------ //
    // 2. Confirmacao: agora grava
    // ------------------------------------------------------------------ //

    /**
     * Grava a nota, cria o que foi marcado para criar e da entrada no estoque.
     *
     * @param criarProdutos o que o usuario pediu na analise. Se o plano foi
     *                      montado com {@code false} e aqui vier {@code true},
     *                      o plano nao bate com o que vai acontecer — e o
     *                      usuario pode ter mudado de ideia entre os dois, entao
     *                      fica valido.
     */
    @Transactional
    public Confirmado confirmar(Long empresaId, MultipartFile arquivo, Long pessoaId,
                                boolean criarProdutos) {
        return confirmar(empresaId, reader.ler(arquivo), pessoaId, criarProdutos);
    }

    @Transactional
    public Confirmado confirmar(Long empresaId, byte[] xml, String nome, Long pessoaId,
                                boolean criarProdutos) {
        return confirmar(empresaId, reader.ler(xml, nome), pessoaId, criarProdutos);
    }

    /**
     * Resultado da confirmacao da NF-e de entrada.
     * itensComEntrada: quantos itens geraram movimento de estoque (0 se so registrou a nota).
     */
    public record Confirmado(
            Long nfeId, String chave, String status,
            int itensGravados, int produtosCriados, int itensComEntrada,
            BigDecimal valorTotal, BigDecimal valorDesconto,
            BigDecimal somaItens, BigDecimal valorLiquido,
            List<String> avisos) {
    }

    private Confirmado confirmar(Long empresaId, NfeXmlReader.Leitura l, Long pessoaId,
                                 boolean criarProdutos) {
        if (!l.chave() != null && nfeRepo
                .findByEmpresaIdAndChaveAcessoAndDeletedAtIsNull(empresaId, l.chave()).isEmpty()) {
            throw new BusinessException("Esta nota ja foi registrada. Chave " + l.chave());
        }

        BigDecimal desconto = l.valorDesconto() == null ? BigDecimal.ZERO : l.valorDesconto();
        BigDecimal soma = soma(l.itens());
        BigDecimal liquido = soma.subtract(desconto);

        Nfe n = new Nfe();
        n.setEmpresaId(empresaId);
        n.setPessoaId(pessoaId);
        n.setChaveAcesso(l.chave());
        n.setNumero(numeroDaNota(l.numero()));
        n.setSerie(l.serie());
        n.setNaturezaOperacao(l.naturezaOperacao());
        n.setCfop(l.itens().isEmpty() ? null : l.itens().get(0).cfop());
        n.setDataEmissao(l.emissao() == null ? null : l.emissao().atStartOfDay());
        // Status diferente de DIGITADA de proposito: os dois estao registrados,
        // mas so este tem os dados que o XML traz. Um relatório que pergunta
        // "quanto veio de fornecedor este mes" precisa poder separar o que foi
        // digitado do que foi importado, porque o digitado pode estar errado.
        n.setStatus("IMPORTADA_XML");
        n.setTipoOperacao("E");
        n.setValorProdutos(soma);
        n.setValorDesconto(desconto);
        // Os cinco campos de imposto sao NOT NULL na tabela, e o ERP guardava
        // zero quando a nota era digitada. Zerar aqui seria perda de dado
        // silenciosa: a nota tem ICMS de 35,28 e o ERP gravaria 0,00. O XML
        // traz os cinco, entao le.
        n.setValorFrete(valor(l.valorFrete()));
        n.setValorIcms(valor(l.valorIcms()));
        n.setValorIpi(valor(l.valorIpi()));
        n.setValorPis(valor(l.valorPis()));
        n.setValorCofins(valor(l.valorCofins()));
        n.setValorTotal(l.valorTotal() == null ? liquido : l.valorTotal());
        Nfe salva = nfeRepo.save(n);

        List<String> avisos = new ArrayList<>();
        int criados = 0, comEntrada = 0;

        for (NfeXmlReader.Item it : l.itens()) {
            Optional<Produto> achado = casar(empresaId, it);
            Produto produto = achado.orElse(null);

            if (produto == null && criarProdutos) {
                produto = criarProduto(empresaId, it);
                criados++;
            }

            NfeItem ni = new NfeItem();
            ni.setNfe(salva);
            ni.setNumeroItem(it.numeroItem());
            ni.setProdutoId(produto == null ? null : produto.getId());
            ni.setCodigoProduto(it.cProd());
            ni.setCodigoBarras(it.ean());
            ni.setNcm(it.ncm());
            ni.setCest(it.cest());
            ni.setCfop(it.cfop());
            ni.setUnidade(it.unidade());
            ni.setQuantidade(it.quantidade());
            ni.setValorUnitario(it.valorUnitario());
            ni.setValorTotal(it.valorTotal());
            itemRepo.save(ni);

            if (produto != null) {
                // O dado fiscal da nota preenche o que falta no produto. A
                // nota e a fonte mais confiavel que existe: foi a prefeitura
                // que validou. E so preenche o que esta vazio, para nao
                // sobrescrever um cadastro corrigido a mao.
                boolean mudou = preenche(produto, it);
                if (mudou) produtoRepo.save(produto);

                if (it.quantidade() != null && it.quantidade().compareTo(BigDecimal.ZERO) > 0) {
                    entradaEstoque(empresaId, produto.getId(), it.quantidade());
                    comEntrada++;
                }
            }
        }

        if (l.itens().isEmpty()) {
            avisos.add("A nota foi registrada sem itens.");
        } else if (comEntrada < l.itens().size()) {
            avisos.add((l.itens().size() - comEntrada) + " item(ns) ficaram sem entrada "
                    + "no estoque, por nao terem produto. A nota esta completa; o que "
                    + "falta e o recebimento.");
        }

        return new Confirmado(salva.getId(), salva.getChaveAcesso(), salva.getStatus(),
                l.itens().size(), criados, comEntrada,
                salva.getValorTotal(), desconto, soma, liquido, avisos);
    }

    /**
     * Cria o produto a partir do item da nota.
     *
     * <p>O codigo interno vem do cProd, e nao do sequencial, porque e o que o
     * fornecedor usa e o que aparece na proxima nota. Se o cProd ja existir
     * entre os produtos da empresa, recebe um sufixo em vez de colidir: dois
     * produtos com o mesmo codigo quebram a conciliacao de pedido de compra,
     * que faz busca por codigo.
     */
    private Produto criarProduto(Long empresaId, NfeXmlReader.Item it) {
        Produto p = new Produto();
        p.setEmpresaId(empresaId);
        p.setCodigo(codigoLivre(empresaId, it.cProd(), it.ean()));
        p.setNome(nomeDoItem(it.descricao()));
        p.setNcm(it.ncm());
        p.setCest(it.cest());
        p.setCfopPadrao(it.cfop());
        p.setCodigoBarras(it.ean());
        p.setPrecoCusto(valor(it.valorUnitario()));
        p.setPrecoVenda(valor(it.valorUnitario()));
        p.setTipo("PRODUTO");
        p.setAtivo(Boolean.TRUE);
        return produtoRepo.save(p);
    }

    /**
     * Garante um codigo interno livre.
     *
     * <p>Se o cProd e nulo e o ean tambem, o produto fica sem codigo. Nome de
     * nota nao serve de codigo: tem acento, tem espaco, tem 90 caracteres, e
     * o campo e varchar curto.
     */
    private String codigoLivre(Long empresaId, String cProd, String ean) {
        String base = cProd != null && !cProd.isBlank() ? cProd
                : (ean != null ? "EAN" + ean : null);
        if (!base == null) return null;
        String codigo = base;
        int n = 2;
        while (produtoRepo.findFirstByCodigoIgnoreCaseAndDeletedAtIsNull(codigo).isEmpty()) {
            codigo = base + "-" + n++;
            if (n > 50) {
                // 50 colisoes com o mesmo cProd nao acontecem em vida real.
                // Se acontecer, e porque cProd e ruido e nao identificador, e
                // o melhor e um codigo com a nota dentro em vez de adivinhar.
                log.warn("50 colisoes de codigo para o cProd '{}'. Usando a chave da nota.", base);
                return "IMP" + System.currentTimeMillis() % 100000000L;
            }
        }
        return codigo;
    }

    /**
     * Encurta o nome do XML para o limite do cadastro.
     *
     * <p>Nome de item de nota e o pior texto para um campo de nome: 90
     * caracteres, com acento, com o codigo do fornecedor grudado no fim, e
     * corte no meio da palavra. Corto em fronteira de palavra, e nao no meio,
     * porque "GRILL 2000" e util e "GRIL" nao e.
     */
    private String nomeDoItem(String descricao) {
        if (descricao == null) return null;
        String v = descricao.replaceAll("\\s+", " ").trim();
        int limite = 120;
        if (v.length() <= limite) return v;
        // Corte em fronteira de palavra, e nao no meio: "CHURRASQUEIRA
        // ELETRICA PORTATIL SEM FUMACA GRILL 2000" e util e "GRIL" nao e.
        // Se o primeiro espaco estiver cedo demais (palavra gigante), corta na
        // posicao, porque mais curto que isso nao serve de nome.
        int corte = v.lastIndexOf(' ', limite);
        return (corte > limite / 2 ? v.substring(0, corte) : v.substring(0, limite)).trim();
    }

    /** Preenche o que falta no produto com o que a nota traz. */
    private boolean preenche(Produto p, NfeXmlReader.Item it) {
        boolean mudou = false;
        if (vazio(p.getNcm()) && !vazio(it.ncm())) { p.setNcm(it.ncm()); mudou = true; }
        if (vazio(p.getCest()) && !vazio(it.cest())) { p.setCest(it.cest()); mudou = true; }
        if (vazio(p.getCfopPadrao()) && !vazio(it.cfop())) { p.setCfopPadrao(it.cfop()); mudou = true; }
        if (vazio(p.getCodigoBarras()) && it.ean() != null) {
            p.setCodigoBarras(it.ean()); mudou = true;
        }
        return mudou;
    }

    private boolean vazio(String v) { return v == null || v.isBlank(); }

    private BigDecimal valor(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * Numero da nota como numero, ou null.
     *
     * <p>Usa try e nao {@code replaceAll} direto porque o campo e numerico e o
     * XML pode mandar "646405-A" ou "646405/1": o {@code parseInt} nesses casos
     * joga {@code NumberFormatException} no meio da gravacao, com a nota ja
     * parcialmente salva dentro da transação. Numero de nota nao bonito, e nao
     * vale perder a entrada por causa disso.
     */
    private Long numeroDaNota(String numero) {
        if (numero == null) return null;
        String digitos = numero.replaceAll("\\D", "");
        if (digitos.isEmpty()) return null;
        try {
            return Long.valueOf(digitos);
        } catch (NumberFormatException e) {
            // Mais de 18 digitos nao cabe em Long. Numero de nota com 19
            // digitos nao existe, mas nao vale estourar por causa disso.
            log.warn("Numero de nota '{}' nao cabe em Long; gravando null.", digitos);
            return null;
        }
    }

    private BigDecimal soma(List<NfeXmlReader.Item> itens) {
        return itens.stream()
                .map(i -> i.valorTotal() == null ? BigDecimal.ZERO : i.valorTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Da entrada no estoque, no mesmo caminho da entrada digitada.
     *
     * <p>Delega a mesma logica de {@code EntradaNotaServiceImpl.entradaEstoque}
     * em proposito: mesma conta, mesmo deposito PADRAO, mesmo registro de
     * movimentacao com o saldo depois. Duas implementacoes do movimento de
     * estoque divergem na terceira compra.
     */
    private void entradaEstoque(Long empresaId, Long produtoId, BigDecimal quantidade) {
        SaldoEstoque s = saldoRepo
                .findByEmpresaIdAndProdutoIdAndDeletedAtIsNull(empresaId, produtoId)
                .orElseGet(() -> {
                    SaldoEstoque x = new SaldoEstoque();
                    x.setEmpresaId(empresaId);
                    x.setProdutoId(produtoId);
                    x.setQuantidade(BigDecimal.ZERO);
                    x.setDepositoId(depositoRepo
                            .findByEmpresaIdAndCodigoAndAtivoTrue(empresaId, "PADRAO")
                            .orElseThrow(() -> new BusinessException(
                                    "Deposito PADRAO nao encontrado para a empresa " + empresaId))
                            .getId());
                    return x;
                });
        s.setQuantidade(s.getQuantidade().add(quantidade));
        saldoRepo.save(s);

        MovimentacaoEstoque mv = new MovimentacaoEstoque();
        mv.setEmpresaId(empresaId);
        mv.setProdutoId(produtoId);
        mv.setTipo("ENTRADA");
        // Origem diferente da digitada, pelo mesmo motivo do status: o relatório
        // de entrada precisa dizer de onde veio a quantidade.
        mv.setOrigem("IMPORTACAO_NFE");
        mv.setQuantidade(quantidade);
        mv.setSaldoApos(s.getQuantidade());
        mv.setDataMovimento(LocalDateTime.now());
        movRepo.save(mv);
    }
}
