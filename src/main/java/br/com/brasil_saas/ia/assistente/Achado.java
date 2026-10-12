package br.com.brasil_saas.ia.assistente;

import java.util.List;

/**
 * O que uma fonte respondeu, e por que respondeu.
 *
 * <p>O {@link #origem()} não é decoração. A diferença entre
 * {@code "NCM 22030000"} e {@code "NCM 22030000, palavra-chave cerveja, origem
 * vocabulário oficial"} é a diferença entre um palpite e uma resposta em que a
 * pessoa confia. Toda fonte devolve os dois campos, mesmo quando só tem um.
 *
 * @param fonte     de onde veio
 * @param tipo      {@code fiscal}, {@code produto}, {@code documento}, ...
 * @param titulo    o que o usuário vai ler
 * @param detalhe   o conteúdo, já resumido
 * @param motivo    por que esta peça entrou na resposta
 * @param origem    de onde veio a peça: oficial, curado, banco, arquivo
 * @param referencia o identificador que permite conferir: o código, o id, o arquivo
 * @param confianca  {@code alta}, {@code media}, {@code baixa} ou {@code nenhuma}
 * @param codigo     o código fiscal, sozinho, quando houver. A tela mostra este
 *                    campo e não extrai de {@link #titulo()}:_number_ de código
 *                    dentro de um texto com prefixo éExactly o que quebra quando
 *                    alguém troca a montagem do texto.
 */
public record Achado(String fonte,
                     String tipo,
                     String codigo,
                     String titulo,
                     String detalhe,
                     String motivo,
                     String origem,
                     String referencia,
                     String confianca) {

    /**
     * A confiança é uma <b>faixa, e não um número</b>.
     *
     * <p>Um "confiança 0,87" seria mentira: não existe cálculo que transforme
     * "palavra-chave curada" em probabilidade, e um número que parece preciso
     * convence mais do que a coisa que ele mede. A faixa diz o que se sabe: se o
     * código veio de correspondência exata, de prefixo, ou de um texto que só
     * menciona o assunto.
     */
    public static String faixaDa(final int pontuacao, final String origem) {
        final String o = origem == null ? "" : origem;
        // A ordem importa. Uma palavra curada tem pontuação 90, e se a faixa fosse
        // pelo número antes da origem, ela cairia em "media" — que é o oposto do
        // que se sabe: o vocabulário é mantido e aponta para um código oficial
        // que existe. O que é de verdade é o que a pessoa digitou: o código
        // exato, ou a palavra do vocabulário.
        if (pontuacao >= 100) {
            return "alta";                       // código exato
        }
        if (o.contains("curado")) {
            return "alta";                       // vocabulário mantido
        }
        if (o.contains("arquivo")) {
            return "baixa";                       // prosa menciona o assunto
        }
        // A partir daqui a faixa é sempre "media": pontuação >= 90 é prefixo (pode
        // casar com mais de um código) e o restante vem da descrição oficial.
        return "media";
    }

    /** Confiança de um achado que é uma classificação fiscal. */
    public static String daClassificacao(final int pontuacao, final String origem) {
        return faixaDa(pontuacao, origem);
    }

    /** Um achado de produto cuja classificação está com problema não tem confiança. */
    public static String deProdutoComProblema() {
        return "nenhuma";
    }

    public static Achado de(String fonte, String tipo, String titulo, String detalhe,
                            String motivo, String origem, String referencia) {
        return new Achado(fonte, tipo, null, titulo, detalhe, motivo, origem, referencia, "media");
    }

    /**
     * A lista que vai para o modelo. A ordem é a das fontes: fiscal, dados, docs.
     * Importa porque o modelo lê em ordem, e o dono definiu a ordem das fontes
     * justamente para que a mais confiável apareça primeiro.
     */
    public static String paraContexto(final List<Achado> achados) {
        final StringBuilder sb = new StringBuilder();
        for (final Achado a : achados) {
            sb.append("- [").append(a.origem()).append("] ").append(a.titulo());
            if (a.referencia() != null && !a.referencia().isBlank()) {
                sb.append(" (").append(a.referencia()).append(")");
            }
            sb.append(": ").append(a.detalhe());
            if (a.motivo() != null && !a.motivo().isBlank()) {
                sb.append(" — ").append(a.motivo());
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
