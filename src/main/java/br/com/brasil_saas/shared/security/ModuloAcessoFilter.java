package br.com.brasil_saas.shared.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.lang.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.brasil_saas.core.service.ModuloAcessoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A segunda metade do isolamento: a empresa e' do token, o modulo e' daqui.
 *
 * <p><b>Por que este filtro existe.</b> O acesso por modulo era gravado em
 * {@code bc_core_usuario_modulo} e devolvido no login, e nada mais. O menu do
 * React escondia o item, o que e' cosmese: <b>digitar a URL na mao funcionava</b>.
 * Quem o dono descreveu — "se um usuario e' rh ele so pode acessar coisa de rh
 * daquela empresa, para ele acessar o financeiro ele precisa de permissao de
 * financeiro e rh" — e' uma regra de acesso, e acesso se impõe no servidor.
 *
 * <p><b>Como a URL vira modulo.</b> O primeiro segmento depois de {@code /api/}
 * e' a chave do modulo: {@code /api/financeiro/titulos} e' o modulo
 * {@code financeiro}, {@code /api/rh/funcionarios} e' o modulo {@code rh}. As
 * chaves estao em {@code bc_core_modulo.chave} e batem com o prefixo.
 *
 * <p><b>Prefixo desconhecido e' bloqueio, e nao liberacao.</b> A tentacao e
 * devolver {@code true} para o que nao se reconhece, e o resultado e' que
 * {@code /api/qualquerCoisaNova/} passa livre para qualquer usuario
 * autenticado. Um controller novo entra em producao com o filtro calado e so
 * alguem lembra. Aqui o desconhecido responde 403, e o nome do prefixo vai na
 * mensagem — o sintoma passa a ser "acesso negado em /api/novoModulo", que
 * aponta direto para o cadastro que falta.
 *
 * <p><b>A ordem importa.</b> Este filtro roda <b>antes</b> do
 * {@link ExigeEmpresaFilter} e antes do authorization do Spring. O modulo e'
 * mais barato de avaliar e mais especifico: um usuario sem empresa nao deveria
 * receber "sem modulo" — ele nao tem empresa nenhuma, e o diagnostico util e' o
 * outro. E {@code @PreAuthorize} continua valendo para o que o modulo nao
 * decide: ter acesso ao modulo financeiro e' necessario, nao suficiente.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ModuloAcessoFilter extends OncePerRequestFilter {

    /**
     * Prefixos que nao sao modulo.
     *
     * <p>{@code auth} e' o login — nao ha modulo para quem ainda nao entrou.
     * {@code superadmin} e' administracao do sistema, que tem o proprio
     * {@code @PreAuthorize} e o proprio {@code ROLE_SUPERUSER}.
     * {@code core} e' o que o usuario acessa sobre ele mesmo: perfil, empresa
     * propria. Travar o proprio perfil trancaria a pessoa fora sem dar para ela
     * descobrir por que.
     */
    private static final Set<String> NAO_SAO_MODULO = Set.of(
            "auth",
            "superadmin",
            "core",
            "actuator",
            "error"
    );

    /**
     * Prefixo que e' consulta de apoio, sem modulo proprio.
     *
     * <p>{@code /api/municipios} e' a lista de municipios do IBGE, usada para
     * preencher endereco em qualquer formulario. Se ficasse bloqueado, ninguem
     * conseguiria cadastrar um cliente. Se fosse amarrado a {@code cadastro},
     * quem so tem {@code financeiro} nao poderia pagar uma nota. E' dado de
     * consulta, nao dado do negocio: fica liberado para qualquer autenticado.
     */
    private static final Set<String> APOIO = Set.of(
            "municipios"
    );

    private static final String PREFIJO = "/api/";

    private final ModuloAcessoService moduloAcesso;

    public ModuloAcessoFilter(ModuloAcessoService moduloAcesso) {
        this.moduloAcesso = moduloAcesso;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain) throws ServletException, IOException {

        // Como veio na URL e' o que a mensagem de erro mostra: e' com essa
        // grafia que o nome aparece no @RequestMapping, que e' onde se procura
        // o controller quando o modulo nao existe.
        String segmentoNaUrl = segmentoDaUrl(request.getRequestURI());
        if (segmentoNaUrl == null) {
            chain.doFilter(request, response);
            return;
        }
        // A chave do modulo e' minuscula, e e' assim que a comparacao funciona.
        String modulo = segmentoNaUrl.toLowerCase();

        // O login e' publico: nao ha principal, e nao ha modulo para checar.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth.getPrincipal() instanceof String) {
            chain.doFilter(request, response);
            return;
        }

        if (!(auth.getPrincipal() instanceof AuthenticatedUser usuario)) {
            chain.doFilter(request, response);
            return;
        }

        if (!moduloAcesso.podeAcessarModulo(usuario, modulo)) {
            // A mensagem cita a grafia da URL, que e' a do @RequestMapping. E' por
            // ela que se procura o controller quando o modulo nao existe.
            responderNegado(response, segmentoNaUrl,
                    "Voce nao tem acesso ao modulo '" + segmentoNaUrl + "'. "
                            + "Um superuser libera este modulo para a sua conta.");
            return;
        }

        // Somente leitura: bloqueia escrita no modulo, nao a leitura dele. E' o
        // que o dono descreveu como "ler o financeiro sem mexer nele".
        if (moduloAcesso.eSomenteLeitura(usuario, modulo) && ehEscrita(request.getMethod())) {
            responderNegado(response, segmentoNaUrl,
                    "Seu acesso ao modulo '" + segmentoNaUrl + "' e' somente leitura.");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * O segmento da URL que nomeia o modulo, ou {@code null} quando a rota nao
     * e' de modulo.
     *
     * <p>O segundo retorno — um prefixo desconhecido — e' o que impede o
     * vazamento. {@link #DESCONHECIDO} e' o valor devolvido para
     * {@code /api/naoExiste/...}: o filtro trata como bloqueio e diz o nome na
     * mensagem, em vez de deixar passar em silencio.
     */
    private String segmentoDaUrl(String uri) {
        if (uri == null || !uri.startsWith(PREFIJO)) {
            return null;
        }
        String resto = uri.substring(PREFIJO.length());
        int barra = resto.indexOf('/');
        String segmento = barra < 0 ? resto : resto.substring(0, barra);
        if (segmento.isBlank()) {
            return null;
        }
        if (NAO_SAO_MODULO.contains(segmento.toLowerCase())
                || APOIO.contains(segmento.toLowerCase())) {
            return null;
        }
        // Devolve COM a grafia original. A normalizacao para minusculo acontece
        // no chamador, na hora de comparar com a chave do banco.
        return segmento;
    }

    private boolean ehEscrita(String metodo) {
        return !("GET".equalsIgnoreCase(metodo)
                || "HEAD".equalsIgnoreCase(metodo)
                || "OPTIONS".equalsIgnoreCase(metodo));
    }

    /**
     * O 403 em JSON, nomeando o modulo como ele aparece na URL.
     *
     * <p>A chave no banco e' minuscula e e' assim que a comparacao funciona, mas
     * o nome do controller no codigo tem a grafia dele. Quem recebe o erro esta
     * procurando o que falta, e procura por {@code /api/algoQueNaoExiste/} no
     * {@code @RequestMapping} — nao por uma versao minuscula que nao existe em
     * lugar nenhum do codigo.
     */
    private void responderNegado(HttpServletResponse response, String modulo, String mensagem)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"erro\":\"MODULO_SEM_ACESSO\","
                        + "\"modulo\":\"" + modulo + "\","
                        + "\"mensagem\":\"" + mensagem.replace("\"", "'") + "\"}");
    }
}
