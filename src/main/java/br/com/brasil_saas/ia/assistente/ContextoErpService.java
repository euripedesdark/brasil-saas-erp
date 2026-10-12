package br.com.brasil_saas.ia.assistente;

import br.com.brasil_saas.cadastro.model.Produto;
import br.com.brasil_saas.cadastro.repository.ProdutoRepository;
import br.com.brasil_saas.fiscal.busca.BuscaFiscalService;
import br.com.brasil_saas.fiscal.busca.NormalizacaoFiscal;
import br.com.brasil_saas.fiscal.model.Cfop;
import br.com.brasil_saas.fiscal.repository.CfopRepository;
import br.com.brasil_saas.fiscal.repository.NcmRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Monta o contexto de uma pergunta, na ordem que o dono definiu.
 *
 * <p>A ordem é o desenho, não uma preferência: <b>fiscal, dados, documentação,
 * e só então o modelo</b>. Quem chega antes do modelo é quem tem a resposta
 * certa; o modelo escreve a frase.
 *
 * <p>Isso não é teoria. Medido nesta base, um modelo gratuito do OpenRouter
 * respondeu {@code "o NCM de cerveja é 2203"} quando o código é
 * {@code 22030000}. Com a busca fiscal antes, a resposta sai com o código
 * certo e o motivo da correspondência, e o modelo não tem como errar o
 * código — só pode errar a explicação, que é onde erro de texto não vira nota
 * fiscal errada.
 */
@Service
public class ContextoErpService {

    /** Termo que indica que a pergunta é sobre classificação fiscal. */
    private static final List<String> MARCAS_FISCais = List.of(
            "ncm", "issqn", "cfop", "cest", "tributa", "tributacao", "imposto",
            "icms", "ipi", "pis", "cofins", "nota fiscal", "codigo fiscal",
            "classificacao", "aliquota", "cst");

    private static final int LIMITE_FISCAL = 3;
    private static final int LIMITE_PRODUTO = 3;

    private final BuscaFiscalService buscaFiscal;
    private final BuscaDocumentacao buscaDocs;
    private final CfopRepository cfopRepository;
    private final ProdutoRepository produtoRepository;
    private final NcmRepository ncmRepository;

    public ContextoErpService(final BuscaFiscalService buscaFiscal,
                              final BuscaDocumentacao buscaDocs,
                              final CfopRepository cfopRepository,
                              final ProdutoRepository produtoRepository,
                              final NcmRepository ncmRepository) {
        this.buscaFiscal = buscaFiscal;
        this.buscaDocs = buscaDocs;
        this.cfopRepository = cfopRepository;
        this.produtoRepository = produtoRepository;
        this.ncmRepository = ncmRepository;
    }

    @Transactional(readOnly = true)
    public Contexto montar(final String pergunta, final Long empresaId) {
        final List<Achado> achados = new ArrayList<>();
        final List<String> termos = TermoDeBusca.termos(pergunta);

        // A busca fiscal é o árbitro do termo: tenta o mais específico primeiro
        // e para no primeiro que devolve alguma coisa. Sem isso, a pergunta
        // inteira vai como termo e não casa com código nenhum.
        if (ehFiscal(pergunta) || !termos.isEmpty()) {
            for (final String termo : termos) {
                final List<Achado> achadosFiscais = fiscal(termo);
                if (!achadosFiscais.isEmpty()) {
                    achados.addAll(achadosFiscais);
                    break;
                }
            }
        }
        // "Qual CFOP devo usar?" não tem termo: CFOP é o assunto da pergunta, e
        // o objeto a achar disappeared junto com "usar" e "devo". A resposta
        // honesta é que CFOP depende da operação, e os mais usados de saída
        // ajudam a pessoa a escolher. Devolver vazio aqui seria dizer "não
        // achei", e isso é mentira: há 331 CFOP de saída cadastrados.
        if (achados.stream().noneMatch(a -> "cfop".equals(a.tipo()))) {
            achados.addAll(cfopsDeSaida(pergunta));
        }
        achados.addAll(produtos(termos, empresaId));
        achados.addAll(buscaDocs.buscar(pergunta));
        return new Contexto(achados);
    }

    /**
     * A busca fiscal é <b>obrigatória</b> para pergunta de classificação, mesmo
     * quando o termo parece óbvio. É o que o dono pediu, e o motivo está no
     * banco: 4 produtos com NCM inexistente, 7 medicamentos num código com 68
     * respostas possíveis.
     */
    private List<Achado> fiscal(final String termo) {
        if (termo.isBlank()) {
            return List.of();
        }
        final List<Achado> achados = new ArrayList<>();
        for (final BuscaFiscalService.Resultado r : buscaFiscal.buscar(termo, null, LIMITE_FISCAL)) {
            final String origem = origemDe(r.motivo());
            achados.add(new Achado(
                    "fiscal", r.tabela(),
                    r.codigo(),
                    r.codigo() + " — " + resumir(r.descricao()),
                    r.codigo() + " | " + nulos(r.descricao()),
                    r.motivo(),
                    origem,
                    r.codigo(),
                    Achado.daClassificacao(r.pontuacao(), origem)));
        }
        return achados;
    }

    /**
     * Os CFOP de saída mais comuns, para quando a pergunta é sobre CFOP sem dizer qual operação.
     */
    private List<Achado> cfopsDeSaida(final String pergunta) {
        if (!NormalizacaoFiscal.texto(pergunta).contains("cfop")) {
            return List.of();
        }
        final List<Achado> achados = new ArrayList<>();
        for (Cfop cfop : cfopRepository.findByTipoOperacaoAndCodigoInOrderByCodigo(
                "SAIDA", List.of("5101","5102","5103","5104","5115","5405","5656","5933"))) {
            achados.add(new Achado("fiscal", "cfop",
                    cfop.getCodigo(),
                    cfop.getCodigo() + " — " + resumir(cfop.getDescricao()),
                    cfop.getCodigo() + " — " + resumir(cfop.getDescricao()),
                    cfop.getCodigo() + " | " + nulos(cfop.getDescricao()),
                    "tabela oficial", cfop.getCodigo(),
                    "media"));
        }
        return achados;
    }

    private List<Achado> produtos(final List<String> termos, final Long empresaId) {
        // Um termo por placeholder, sempre. A primeira versão montava o filtro
        // com um placeholder a mais do que parâmetro, e a consulta nem rodava.
        final List<String> padroes = new ArrayList<>();
        for (final String t : termos) {
            if (t.length() >= 4) {
                padroes.add("%" + t.toLowerCase() + "%");
            }
        }
        if (padroes.isEmpty()) {
            return List.of();
        }

        final List<Achado> achados = new ArrayList<>();
        final java.util.Set<Long> vistos = new java.util.LinkedHashSet<>();
        for (String padrao : padroes) {
            if (achados.size() >= LIMITE_PRODUTO) break;
            String termoLike = padrao.replace("%", "");
            for (Produto prod : produtoRepository.buscar(
                    empresaId, termoLike, null, null, null, null,
                    PageRequest.of(0, LIMITE_PRODUTO)).getContent()) {
                if (!vistos.add(prod.getId())) continue;
                final String ncm = prod.getNcm();
                final String diagnostico = diagnosticoNcm(ncm);
                achados.add(new Achado(
                        "dados", "produto",
                        ncm,
                        prod.getNome(),
                        "ncm=" + nulos(ncm) + ", cfop=" + nulos(prod.getCfopPadrao())
                                + ", cest=" + nulos(prod.getCest()),
                        diagnostico,
                        "cadastro de produto",
                        "produto " + prod.getId(),
                        // NCM vazio, em formato errado ou inexistente: a classificação
                        // não tem confiança nenhuma, e dizer "média" seria Falsear.
                        "NCM válido".equals(diagnostico) ? "alta" : "nenhuma"));
                if (achados.size() >= LIMITE_PRODUTO) break;
            }
        }
        return achados;
    }

    /**
     * O diagnóstico é o que a pessoa mais precisa: se o NCM está
     * {@code SEED NCM}, não é cadastro, é ausência. Se tem 6 dígitos, é formato
     * errado — a coluna é 8. Se não existe na tabela oficial, o código não
     * existe. Cada caso pede uma correção diferente, e dizer qual deles é o
     * caso é metade da resposta.
     */
    private String diagnosticoNcm(final String ncm) {
        if (ncm == null || ncm.isBlank() || ncm.toUpperCase().startsWith("SEED")) {
            return "sem classificação fiscal: não há NCM válido, então a nota não sai";
        }
        if (ncm.length() < 8) {
            return "NCM com " + ncm.length() + " dígitos; a coluna é varchar(8) e o NCM oficial tem 8";
        }
        if (ncmRepository.findByCodigo(ncm).isEmpty()) {
            return "NCM " + ncm + " não existe na tabela oficial";
        }
        return "NCM válido";
    }

    private boolean ehFiscal(final String pergunta) {
        final String p = NormalizacaoFiscal.texto(pergunta);
        for (final String marca : MARCAS_FISCais) {
            if (p.contains(marca)) {
                return true;
            }
        }
        return false;
    }

    private String origemDe(final String motivo) {
        if (motivo == null) {
            return "busca fiscal";
        }
        return motivo.startsWith("palavra-chave") ? "vocabulário curado" : "tabela oficial";
    }

    private static String nulos(final String s) {
        return s == null || s.isBlank() ? "(vazio)" : s;
    }

    private static String resumir(final String s) {
        if (s == null || s.isBlank()) {
            return "(sem descrição)";
        }
        return s.length() > 90 ? s.substring(0, 90) + "…" : s;
    }

    /** O contexto montado, e a lista de fontes que responderam. */
    public record Contexto(List<Achado> achados) {

        public boolean vazio() {
            return achados.isEmpty();
        }

        public String fontes() {
            return achados.stream().map(Achado::fonte).distinct().reduce((a, b) -> a + "," + b)
                    .orElse("");
        }
    }
}
