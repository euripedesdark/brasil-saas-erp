package br.com.brasil_saas.shared.identity;

import java.util.List;

/**
 * O que o Auth Service respondeu.
 *
 * <p>Existe porque {@code 401} e {@code 503} precisam chegar a quem chama como
 * coisas diferentes, e um {@code Optional} vazio apaga a diferença.
 *
 * <p><b>Se os tres se parecessem, a queda do Active Directory viraria "senha
 * errada".</b> Com {@code 401} caindo para o caminho local, uma senha digitada
 * errada seria aceita pelo BCrypt e a pessoa entraria sem a senha que digitou.
 *
 * <ul>
 *   <li>{@link Estado#RECUSADO} — o servico respondeu e recusou a credencial. A
 *       resposta e definitiva: nao ha caminho alternativo.
 *   <li>{@link Estado#ACEITO} — identidade devolvida, com os grupos do contrato.
 *   <li>{@link Estado#INDISPONIVEL} — o servico ou o AD nao respondeu. Nao se sabe
 *       nada sobre a senha, e quem chama decide se ha o que tentar antes.
 * </ul>
 *
 * @param estado  o que aconteceu
 * @param identidade a identidade devolvida, so no estado {@link Estado#ACEITO}
 */
public record ResultadoAutenticacao(Estado estado, IdentityDto identidade) {

    /** Os tres desfechos possiveis. */
    public enum Estado {
        ACEITO,
        RECUSADO,
        INDISPONIVEL
    }

    public static ResultadoAutenticacao aceito(IdentityDto identidade) {
        return new ResultadoAutenticacao(Estado.ACEITO, identidade);
    }

    public static ResultadoAutenticacao recusado() {
        return new ResultadoAutenticacao(Estado.RECUSADO, null);
    }

    public static ResultadoAutenticacao indisponivel() {
        return new ResultadoAutenticacao(Estado.INDISPONIVEL, null);
    }

    public boolean isAceito() {
        return estado == Estado.ACEITO;
    }

    public boolean isRecusado() {
        return estado == Estado.RECUSADO;
    }

    public boolean isIndisponivel() {
        return estado == Estado.INDISPONIVEL;
    }

    /** Os grupos do contrato, em minusculo, como o ERP compara. */
    public List<String> grupos(IdentityService servico) {
        return identidade == null ? List.of() : List.copyOf(servico.grupos(identidade));
    }
}
