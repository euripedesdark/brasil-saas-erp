package br.com.brasil_saas.ia.assistente;

import br.com.brasil_saas.fiscal.busca.NormalizacaoFiscal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extrai da pergunta os termos que fazem sentido como busca.
 *
 * <p>Existe porque a pergunta e o termo de busca não são a mesma coisa. "Qual NCM
 * devo usar para cerveja?" é uma pergunta; "cerveja" é o termo. Mandar a
 * pergunta inteira para a busca fiscal não acha nada — e foi o que aconteceu na
 * primeira versão: a resposta veio só da documentação, sem nenhum achado fiscal,
 * porque o termo de 32 caracteres não casa com nenhum código.
 *
 * <p>E não basta devolver um termo: é preciso devolver vários, do mais específico
 * ao mais genérico, porque não se sabe de antemão qual deles o cadastro usa. A
 * busca fiscal é o próprio árbitro — ela devolve o que casa, e o primeiro termo
 * que devolve alguma coisa é o termo certo.
 */
public final class TermoDeBusca {

    /**
     * Palavras da pergunta, e não do assunto. "usar", "devo", "qual", "existe",
     * "posso" aparecem em quase toda pergunta de negócio e não discriminam nada.
     * Some "ncm", "issqn" e "cfop" também: são o assunto da pergunta, não o
     * objeto que se quer achar. O que se quer achar é "cerveja".
     */
    private static final Set<String> PALAVRAS_DA_PERGUNTA = Set.of(
            "qual", "quais", "como", "onde", "quando", "porque", "por", "para", "com",
            "devo", "deveria", "usar", "usa", "usado", "usando", "existe", "existem",
            "tenho", "tem", "ha", "posso", "pode", "quero", "preciso", "serve",
            "significa", "quer", "querer", "de", "do", "da", "das", "dos",
            "em", "no", "na", "nos", "nas", "a", "o", "as", "os", "e", "ou", "um",
            "uma", "isso", "isto", "esse", "essa", "aquele", "aquela", "meu", "minha",
            "ncm", "issqn", "cfop", "cest", "codigo", "classificacao", "tributacao",
            "imposto", "icms", "ipi", "pis", "cofins", "nota", "fiscal", "erp",
            "sistema", "banco", "produto", "servico", "cnpj", "emitir", "emissao");

    private static final int MINIMO = 4;

    private TermoDeBusca() {
    }

    /**
     * Os termos candidatos, do mais específico ao mais genérico.
     *
     * <p>O primeiro é a frase inteira sem as palavras da pergunta — que é o
     * melhor termo quando a pessoa escreve "cerveja de garrafa 600ml". Depois
     * vêm as palavras soltas, da mais longa para a mais curta: "suporte técnico"
     * antes de "suporte".
     */
    public static List<String> termos(final String pergunta) {
        final String limpo = NormalizacaoFiscal.texto(pergunta);
        if (limpo.isBlank()) {
            return List.of();
        }
        final List<String> out = new ArrayList<>();

        final List<String> palavras = List.of(limpo.split(" "));
        final StringBuilder frase = new StringBuilder();
        for (final String p : palavras) {
            if (p.length() >= MINIMO && !PALAVRAS_DA_PERGUNTA.contains(p)) {
                if (!frase.isEmpty()) {
                    frase.append(' ');
                }
                frase.append(p);
            }
        }
        if (!frase.isEmpty()) {
            out.add(frase.toString());
        }

        // palavras soltas, da mais longa para a mais curta
        final List<String> soltas = new ArrayList<>();
        for (final String p : palavras) {
            if (p.length() >= MINIMO && !PALAVRAS_DA_PERGUNTA.contains(p) && !out.contains(p)) {
                soltas.add(p);
            }
        }
        soltas.sort((a, b) -> Integer.compare(b.length(), a.length()));
        out.addAll(soltas);

        return out;
    }

    /**
     * Só as palavras que valem para a documentação. A busca de arquivo pontua
     * por palavra, então "qual" e "usar" — que aparecem em quase toda
     * documentação e em quase toda pergunta — precisam ficar de fora, ou o
     * resultado é um monte de arquivo irrelevante com pontuação alta.
     */
    public static List<String> palavrasRelevantes(final String pergunta) {
        final List<String> out = new ArrayList<>();
        for (final String p : NormalizacaoFiscal.texto(pergunta).split(" ")) {
            if (p.length() >= MINIMO && !PALAVRAS_DA_PERGUNTA.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }
}
