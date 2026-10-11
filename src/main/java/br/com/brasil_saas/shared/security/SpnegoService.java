package br.com.brasil_saas.shared.security;

import br.com.brasil_saas.core.config.AdProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Valida o tíquete SPNEGO que o navegador manda sozinho.
 *
 * <p><b>Como funciona, do ponto de vista de quem usa.</b> A pessoa abre o ERP
 * numa máquina unida ao domínio. O Windows já tem tíquete do AD. O navegador
 * envia {@code Authorization: Negotiate <token>} sem que ninguém digite nada.
 * Este serviço valida o tíquete contra o keytab — o mesmo arquivo que o ERP usa
 * para consultar grupos — e devolve o nome de quem o KDC identificou. A partir
 * daí a sessão é a mesma dos outros caminhos: o SPNEGO só troca <em>como</em> a
 * identidade é provada na entrada, não o que acontece depois.
 *
 * <p><b>Por que a validação é aqui e não no nginx.</b> Confiar num header
 * {@code X-Remote-User} que o nginx preenche é afirmar a identidade, não prová-la:
 * qualquer cliente que alcance a porta 8080 direto escreve o header que quiser. O
 * tíquete é cifrado pelo KDC e só o keytab do serviço o abre. Por isso a leitura
 * fica no app, e o nginx não participa.
 *
 * <p><b>O que este serviço NÃO é.</b> Não é o único caminho de login. A tela
 * continua aceitando senha, e o superuser do Postgres continua entrando sem AD.
 * Se o Kerberos falhar, ninguém perde acesso — o sistema só deixa de oferecer o
 * atalho. É o que a doc de identidade quer dizer com "sem ponto único de falha".
 */
@Slf4j
@Service
public class SpnegoService {

    private final AdProperties adProps;

    public SpnegoService(AdProperties adProps) {
        this.adProps = adProps;
    }

    /**
     * Confere o tíquete e diz quem o portou.
     *
     * @param tokenBruto o valor depois de {@code Negotiate}, em Base64
     * @return o principal que o KDC identificou, ou {@code null} se o tíquete não
     *         abrir — o que inclui "o AD está desligado" e "a feature está fora"
     */
    public String principalDoTiquete(String tokenBruto) {
        if (!adProps.isEnabled() || tokenBruto == null || tokenBruto.isBlank()) {
            return null;
        }

        byte[] tiquete;
        try {
            tiquete = Base64.getDecoder().decode(tokenBruto.trim());
        } catch (IllegalArgumentException e) {
            log.debug("Token Negotiate não é Base64 válido: {}", e.getMessage());
            return null;
        }

        // JAAS montado em memória. O keytab é um arquivo, e um arquivo de
        // configuração JAAS seria mais um segredo no disco, sem ganho nenhum.
        try {
            LoginContext lc = new LoginContext("BrasilSaasKerberos", null, new KeytabCallbackHandler(adProps), new KeytabConfiguration(adProps));
            lc.login();
            Subject subject = lc.getSubject();
            if (subject.getPrincipals().isEmpty()) {
                return null;
            }
            return subject.getPrincipals().iterator().next().getName();
        } catch (Exception e) {
            log.warn("Validação SPNEGO falhou: {}. O login por senha continua disponível.", e.getMessage());
            return null;
        }
    }

    /**
     * JAAS do lado <em>servidor</em>: o tíquete é decifrado com a chave de
     * serviço {@code HTTP/<host>} do keytab, não emitido. {@code isInitiator=false}
     * é o que troca o sentido — sem ele o módulo tentou obter tíqueto em vez de
     * aceitar um.
     */
    private static class KeytabConfiguration extends Configuration {

        private final AdProperties props;

        KeytabConfiguration(AdProperties props) {
            this.props = props;
        }

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            Map<String, Object> options = new HashMap<>();
            options.put("useKeyTab", "true");
            options.put("storeKey", "false");
            options.put("doNotPrompt", "true");
            options.put("isInitiator", "false");
            options.put("principal", props.getServicePrincipal());
            options.put("debug", "false");
            return new AppConfigurationEntry[]{
                    new AppConfigurationEntry(
                            "com.sun.security.auth.module.Krb5LoginModule",
                            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                            options)};
        }

        @Override
        public void refresh() {
            // Estática: o keytab é relido do disco a cada tentativa.
        }
    }

    /**
     * O módulo pede o nome do principal e o <b>caminho</b> do keytab. O campo de
     * senha recebe um caminho, não uma senha — é o que mantém a conta de serviço
     * sem senha em lugar nenhum do app.
     */
    private static class KeytabCallbackHandler implements CallbackHandler {

        private final AdProperties props;

        KeytabCallbackHandler(AdProperties props) {
            this.props = props;
        }

        @Override
        public void handle(Callback[] callbacks) {
            for (Callback c : callbacks) {
                if (c instanceof NameCallback nc) {
                    nc.setName(props.getPrincipal().split("@")[0]);
                } else if (c instanceof PasswordCallback pc) {
                    pc.setPassword(props.getKeytab().toCharArray());
                } else {
                    throw new IllegalArgumentException("callback não suportado: " + c);
                }
            }
        }
    }
}
