package br.com.brasil_saas.fiscal.busca;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalização de texto e de código fiscal.
 *
 * <p><b>Por que isto é Java e não SQL.</b> Remover acento mantendo só letras depende
 * da collation do banco. Nesta base, que é {@code pt_BR.UTF-8},
 * {@code regexp_replace('Cafe ACAO 1.07', '[^a-z0-9]+', ' ', 'g')} devolve
 * {@code " afe 1 07"} — o {@code C} e o {@code ACAO} são engolidos. Uma função
 * que dá resultado diferente conforme o locale do servidor é pior do que não
 * ter função: o mesmo dado muda de resultado entre dois ambientes.
 *
 * <p>Aí fica a divisão: o Java normaliza e grava; o banco só filtra por
 * igualdade e prefixo, que não dependem de collation.
 *
 * <p>A normalização de <b>código</b> também existe em SQL
 * ({@code fiscal_normaliza_codigo}), porque usa só dígito. A de texto é esta.
 */
public final class NormalizacaoFiscal {

    private NormalizacaoFiscal() {
    }

    /**
     * Texto de busca: minúsculo, sem acento, só letras e dígitos, espaços
     * colapsados. {@code "Café AÇÃO"} vira {@code "cafe acao"}.
     */
    public static String texto(final String entrada) {
        if (entrada == null) {
            return "";
        }
        // NFD separa o acento da letra base; o Forms recompõe. Depois, o que não
        // for letra ASCII ou dígito vira espaço.
        final String semAcento = Normalizer.normalize(entrada, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    /**
     * Código de busca: só dígitos, sem zeros à esquerda, para o usuário não
     * precisar saber a pontuação oficial.
     *
     * <p>{@code "01.07.01.000" -> "10701000"}, {@code "1.07" -> "107"},
     * {@code "2203.00" -> "220300"}, {@code "22030000" -> "22030000"}.
     *
     * <p>É a mesma regra de {@code fiscal_normaliza_codigo} no banco. O
     * resultado de um é igual ao do outro, e isso é verificado em teste.
     */
    public static String codigo(final String entrada) {
        if (entrada == null) {
            return "";
        }
        final String digitos = entrada.replaceAll("[^0-9]", "");
        int inicio = 0;
        while (inicio < digitos.length() - 1 && digitos.charAt(inicio) == '0') {
            inicio++;
        }
        return digitos.substring(inicio);
    }

    /** Verdadeiro quando o termo tem conteúdo para ser comparado. */
    public static boolean temConteudo(final String termo) {
        return termo != null && !termo.trim().isEmpty();
    }
}
