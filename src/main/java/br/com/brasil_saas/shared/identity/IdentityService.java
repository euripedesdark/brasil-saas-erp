package br.com.brasil_saas.shared.identity;

import br.com.brasil_saas.core.config.AuthServiceProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * O ERP pergunta ao Auth Service quem e a pessoa. Nao pergunta a ninguem mais.
 *
 * <p><b>O que este servico faz.</b> Uma coisa so: chamar o Auth Service e
 * devolver o que veio. O Active Directory e problema do Auth Service, nao deste
 * arquivo.
 *
 * <p><b>O que este arquivo nao importa, e nao vai importar.</b> Nao ha
 * {@code javax.naming}, nao ha {@code LdapTemplate}, nao ha
 * {@code InitialLdapContext}, nao ha bind, nao ha {@code memberOf}, nao ha
 * keytab. Se alguem precisar dessas coisas aqui, o desenho esta errado.
 *
 * <p><b>O contrato, como ele existe.</b> O Auth Service fala REST com HTTP Basic
 * e nao emite token nenhum. Por isso existem dois caminhos, e eles nao sao
 * equivalentes:
 *
 * <ul>
 *   <li>{@link #autenticar} — {@code POST /api/v1/identity/authenticate}, com
 *       usuario e senha no corpo. E o caminho do login, e o unico momento em que
 *       o ERP tem a senha em maos. E o que devolve os grupos.
 *   <li>{@link #identidadeDe} — {@code GET /api/v1/identity/me}, com HTTP Basic.
 *       Exige a senha de novo, entao so serve para quem a tem a mao.
 * </ul>
 *
 * <p><b>Por que a senha nao e guardada.</b> O ERP nao guarda a senha de ninguem
 * para reaproveitar. E por isso que o consumo do Auth Service acontece no login
 * e nao a cada requisicao: depois do login o ERP tem a identidade, e nao a
 * credencial.
 *
 * <p><b>Resolucao de grupo herdado nao e responsabilidade daqui.</b> O Auth
 * Service devolve os grupos que ele devolve. Este servico repete, nao completa.
 * Se a heranca sumir um dia, a mudanca e no Auth Service, e este arquivo nao
 * muda junto.
 *
 * <p><b>Quando o Auth Service nao responde.</b> Devolve vazio e registra o
 * aviso, distinguindo 401 de 503: 401 e credencial recusada, 503 e o servico ou
 * o AD fora do ar. Tratar os dois como "senha errada" esconde queda de
 * infraestrutura. Nao tenta LDAP, nao tenta contornar, nao guarda para depois.
 */
@Slf4j
@Service
public class IdentityService {

    private static final String CAMINHO_AUTHENTICATE = "/api/v1/identity/authenticate";
    private static final String CAMINHO_ME = "/api/v1/identity/me";

    private final AuthServiceProperties props;
    private final RestClient client;

    public IdentityService(AuthServiceProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(props.getConnectTimeoutMs());
        fabrica.setReadTimeout(props.getReadTimeoutMs());
        this.client = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(fabrica)
                .build();
    }

    /**
     * Login no Auth Service: valida a credencial e devolve a identidade com os
     * grupos do contrato publico.
     *
     * <p>E o unico ponto do ERP onde a senha existe em maos, e portanto o unico
     * lugar do fluxo oficial. A senha vai para o Auth Service e nao volta: este
     * metodo nao a guarda em lugar nenhum, e o {@link IdentityDto} devolvido
     * nao a contem.
     *
     * @param username o rotulo informado pela pessoa
     * @param password a senha informada pela pessoa, usada so nesta chamada
     * @return a identidade, ou {@link Optional#empty()} se recusada ou indisponivel
     */
    public ResultadoAutenticacao autenticar(String username, String password) {
        if (!props.isEnabled()) {
            return ResultadoAutenticacao.indisponivel();
        }
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return ResultadoAutenticacao.recusado();
        }
        try {
            IdentityDto dto = client.post()
                    .uri(CAMINHO_AUTHENTICATE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginDto(username, password, props.getProvider()))
                    .retrieve()
                    .body(IdentityDto.class);
            if (dto == null) {
                log.warn("Auth Service devolveu corpo vazio em {}. Sem grupos nesta sessao.",
                        CAMINHO_AUTHENTICATE);
                return ResultadoAutenticacao.indisponivel();
            }
            log.info("Identidade resolvida pelo Auth Service: provider={} username={} grupos={}",
                    dto.provider(), dto.username(), dto.gruposOuVazio().size());
            return ResultadoAutenticacao.aceito(dto);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return desfecho(e.getStatusCode());
        } catch (Exception e) {
            log.warn("Auth Service indisponivel em {} ({}). Seguindo sem grupos.",
                    props.getBaseUrl(), e.toString());
            return ResultadoAutenticacao.indisponivel();
        }
    }

    /**
     * Traduz o codigo HTTP no desfecho que o ERP sabe usar.
     *
     * <p>401 e a unica resposta definitiva: o servico respondeu e recusou a
     * credencial. Qualquer outra coisa — 403, 500, 503, timeout, conexao
     * recusada — e indisponibilidade, e sobre credencial nao diz nada. Por isso
     * 503 e 500 caem no mesmo ramo: sao a mesma coisa para quem decide, que e
     * "nao deu para saber".
     */
    private ResultadoAutenticacao desfecho(HttpStatusCode status) {
        if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            log.warn("Auth Service recusou a credencial (401).");
            return ResultadoAutenticacao.recusado();
        }
        if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            log.warn("Auth Service indisponivel: o servico ou o AD nao respondeu (503).");
            return ResultadoAutenticacao.indisponivel();
        }
        log.warn("Auth Service respondeu {}.", status.value());
        return ResultadoAutenticacao.indisponivel();
    }

    /**
     * groups em minusculo, como o ERP ja comparava.
     *
     * <p>O Auth Service devolve o CN como o AD escreveu. O ERP comparava em
     * minusculo, entao a conversao fica aqui, na fronteira, para nenhuma
     * verificacao de permissao mudar de resultado por causa desta migracao.
     *
     * @return os grupos em minusculo, nunca nulo
     */
    public Set<String> grupos(IdentityDto dto) {
        if (dto == null) {
            return Set.of();
        }
        Set<String> saida = new LinkedHashSet<>();
        for (String grupo : dto.gruposOuVazio()) {
            if (grupo != null && !grupo.isBlank()) {
                saida.add(grupo.trim().toLowerCase(Locale.ROOT));
            }
        }
        return saida;
    }

    /**
     * Consulta de identidade com HTTP Basic.
     *
     * <p>Existe no contrato, mas so serve para quem tem a credencial em mao — e
     * o ERP nao guarda a senha. Por isso o fluxo do login usa
     * {@link #autenticar} e nao este metodo. Fica aqui porque o contrato publica
     * a rota, e um consumidor pode precisar dela em um fluxo em que a credencial
     * acabou de ser informada.
     *
     * @param username o rotulo da pessoa
     * @param password a senha, usada so nesta chamada
     * @return a identidade, ou vazio se recusada ou indisponivel
     */
    public Optional<IdentityDto> identidadeDe(String username, String password) {
        if (!props.isEnabled() || username == null || password == null) {
            return Optional.empty();
        }
        try {
            IdentityDto dto = client.get()
                    .uri(CAMINHO_ME)
                    .header("Authorization", basica(username, password))
                    .retrieve()
                    .body(IdentityDto.class);
            return Optional.ofNullable(dto);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            registrar(e.getStatusCode());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Auth Service indisponivel em {} ({}). Seguindo sem grupos.",
                    props.getBaseUrl(), e.toString());
            return Optional.empty();
        }
    }

    /** O consumption esta ligado? */
    public boolean ativo() {
        return props.isEnabled();
    }

    /**
     * 401 e credencial recusada; 503 e o servico ou o AD fora do ar.
     *
     * <p> Sao coisas diferentes e nao podem virar a mesma mensagem: quem opera o
     * sistema precisa saber se alguem digitou a senha errada ou se o AD caiu.
     */
    private void registrar(HttpStatusCode status) {
        if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            log.warn("Auth Service recusou a credencial (401).");
        } else if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            log.warn("Auth Service indisponivel: o servico ou o AD nao respondeu (503).");
        } else {
            log.warn("Auth Service respondeu {}.", status.value());
        }
    }

    /** Cabecalho HTTP Basic. A senha vive so na chamada, nunca em campo. */
    private String basica(String username, String password) {
        String credencial = username + ":" + password;
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString(credencial.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * O corpo do POST de autenticacao.
     *
     * <p>Os nomes dos campos sao os do contrato publicado, palavra por palavra.
     * Nao ha campo de senha na resposta, e o registro nao guarda esta instance
     * alem da chamada.
     */
    private record LoginDto(String username, String password, String provider) {
    }

    /** Grupos do contrato que o ERP consome, em minusculo. */
    public List<String> gruposPublicos(IdentityDto dto) {
        return List.copyOf(grupos(dto));
    }
}
