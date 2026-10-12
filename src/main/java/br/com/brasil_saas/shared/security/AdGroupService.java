package br.com.brasil_saas.shared.security;

import br.com.brasil_saas.core.config.AdProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lê os grupos de uma pessoa no Active Directory, por GSSAPI com keytab.
 *
 * <p><b>Como se autentica.</b> O ERP não tem senha de AD. Ele faz login no KDC
 * com a chave do keytab — um {@code Krb5LoginModule} apontando para o arquivo, sem
 * senha em lugar nenhum — e usa o tíquete obtido para abrir uma conexão LDAP
 * {@code GSSAPI}. O DC reconhece a conta de serviço e libera a busca. Nada de
 * senha em configuração, nada de conta do AD no {@code .env}.
 *
 * <p><b>Grupos aninhados.</b> O {@code memberOf} direto não mostra o grupo
 * herdado: quem entra em {@code GRP_ERP_ADMIN} herda {@code GRP_ERP_ACESSO}, e o
 * AD não lista o herdado no {@code memberOf} da pessoa. Por isso a busca sobe a
 * árvore: pega os grupos da pessoa, depois os grupos de cada um desses, até
 * fechar. A alternativa seria o OID transitivo
 * {@code 1.2.840.113556.1.4.1941}, que depende do servidor aceitar a extensão —
 * a subida não depende de nada do servidor além do LDAP comum.
 *
 * <p><b>Por que o cache é em arquivo.</b> São três motivos que importam mais que
 * desempenho. O filtro de JWT recarrega o usuário a cada requisição, então sem
 * cache seria uma consulta ao AD por clique. Um restart não pode jogar fora o que
 * o ERP já sabia, senão quem entra logo após a subida espera consulta. E o mais
 * importante: com o AD fora do ar, o ERP lê o <b>último estado conhecido</b> do
 * arquivo e continua autorizando. Perder o AD não pode virar perder o acesso de
 * todo mundo — é o princípio da doc de identidade, "sem ponto único de falha".
 *
 * <p><b>Falha não é exceção de login.</b> Se a consulta ao AD falhar, o método
 * devolve o que houver em cache (ou vazio) e registra um aviso. O login continua.
 * O que o AD concede é um acréscimo em cima do perfil no Postgres, então o pior
 * caso do AD fora é ninguém ganhar o acréscimo — nunca ninguém perder o que já
 * tinha.
 */
@Slf4j
@Service
public class AdGroupService {

    /** Registro do cache: os grupos e quando foram lidos. */
    record CacheEntry(List<String> grupos, long emMilis) {}

    /** Estrutura do arquivo: um registro por usuário, mais o instante da leitura. */
    static class ArquivoCache {
        public Map<String, CacheEntry> usuarios = new LinkedHashMap<>();
    }

    private final AdProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Espelho em memória do arquivo, para não ler o disco a cada requisição. */
    private final Map<String, CacheEntry> memoria = new ConcurrentHashMap<>();

    /** Até quando não tentamos o AD de novo depois de uma falha. */
    private volatile long ateNaoTentarAte = 0L;

    public AdGroupService(AdProperties props) {
        this.props = props;
        carregarArquivo();
    }

    /**
     * Os grupos da pessoa no AD, em minúsculo e sem o prefixo {@code CN=} — do
     * jeito que o nome do grupo aparece, para comparar direto.
     *
     * @return os nomes dos grupos, ou vazio se o AD estiver desligado, fora do ar
     *         e sem cache
     */
    public Set<String> gruposDe(String username) {
        if (!props.isEnabled() || username == null || username.isBlank()) {
            return Set.of();
        }

        String chave = username.trim().toLowerCase(Locale.ROOT);
        long agora = System.currentTimeMillis();
        long validadeMilis = props.getCacheSegundos() * 1000L;

        CacheEntry fresco = memoria.get(chave);
        if (fresco != null && agora - fresco.emMilis() < validadeMilis) {
            return new LinkedHashSet<>(fresco.grupos());
        }

        // O disco é a fonte da verdade quando há dúvida entre memória e arquivo.
        CacheEntry doDisco = lerArquivo(chave);
        if (fresco == null && doDisco != null) {
            fresco = doDisco;
            memoria.put(chave, doDisco);
        }
        if (fresco != null && agora - fresco.emMilis() < validadeMilis) {
            return new LinkedHashSet<>(fresco.grupos());
        }

        if (agora < ateNaoTentarAte) {
            // Cooldown: o AD acabou de falhar. Não se martela.
            return doCacheOuVazio(fresco, doDisco, "AD em cooldown após falha");
        }

        try {
            List<String> grupos = consultarAd(chave);
            gravar(chave, grupos);
            return new LinkedHashSet<>(grupos);
        } catch (Exception e) {
            ateNaoTentarAte = agora + props.getCooldownFalhaSegundos() * 1000L;
            log.warn("Consulta de grupos no AD falhou para '{}': {}. Usando o último estado "
                            + "conhecido; as permissões do perfil no Postgres continuam valendo.",
                    username, e.getMessage());
            return doCacheOuVazio(fresco, doDisco, "AD indisponível");
        }
    }

    /**
     * Se a pessoa tem o grupo de acesso, que é o que permite criar sessão pelo
     * caminho do AD.
     */
    public boolean temGrupoAcesso(String username) {
        return contem(gruposDe(username), props.getGrupoAcesso());
    }

    /** Se a pessoa tem o grupo que concede {@code ROLE_ADMIN}. */
    public boolean temGrupoAdmin(String username) {
        return contem(gruposDe(username), props.getGrupoAdmin());
    }

    /**
     * O CNPJ da empresa da pessoa, tirado do grupo {@code ERP_EMPRESA_<cnpj>}.
     *
     * @return o CNPJ, ou null se a pessoa não estiver em nenhum grupo de empresa
     */
    public String cnpjDaEmpresa(String username) {
        for (String g : gruposDe(username)) {
            if (g.startsWith(props.getPrefixoGrupoEmpresa().toLowerCase(Locale.ROOT))) {
                return g.substring(props.getPrefixoGrupoEmpresa().length());
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // A consulta, de verdade
    // ------------------------------------------------------------------

    private List<String> consultarAd(String username) throws Exception {
        // A configuração JAAS é montada em memória: o keytab é um arquivo, e um
        // config-file de JAAS seria um arquivo a mais para proteger sem trazer
        // nada. O PasswordCallback recebe o CAMINHO do keytab, não a senha.

        LoginContext lc = new LoginContext("BrasilSaasKerberos", null, new KeytabCallbackHandler(props), new Krb5KeytabConfiguration(props));
        lc.login();
        Subject subject = lc.getSubject();
        log.debug("Autenticado no KDC como {}", subject.getPrincipals());

        // A identidade do Subject é obrigatória: é ele que torna o tíquete visível para o
        // mecanismo de GSSAPI do JNDI. Sem, o JNDI não acha credencial nenhuma.
        return Subject.callAs(subject, () -> {
            try {
                return buscaComGssapi(username);
            } catch (Exception e) {
                throw new RuntimeException("LDAP recusou a autenticação GSSAPI: " + e.getMessage(), e);
            }
        });
    }

    private List<String> buscaComGssapi(String username) throws Exception {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, props.getUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "GSSAPI");
        // Pacote SASL que faz GSSAPI sobre Kerberos. Vem do JDK, por isso não
        // precisa de dependência no pom.
        env.put("com.sun.security.sasl.client.pkgs", "com.sun.security.sasl.gsskerb");

        Set<String> nomes = new LinkedHashSet<>();
        Set<String> dnsVisitados = new LinkedHashSet<>();

        LdapContext ctx = new InitialLdapContext(env, null);
        try {
            // BFS dos grupos, subindo a árvore. memberOf direto primeiro; depois
            // cada grupo é consultado pelos grupos DELE, que é onde está o
            // herdado.
            Deque<String> fila = new ArrayDeque<>();
            String filtroPessoa = "(&(objectCategory=person)(objectClass=user)(sAMAccountName="
                    + escaparFiltro(username) + "))";
            enfileirarGrupos(ctx, filtroPessoa, fila, nomes, dnsVisitados);

            int limite = 32; // guarda contra ciclo malformado no diretório
            while (!fila.isEmpty() && limite-- > 0) {
                String dn = fila.poll();
                enfileirarGrupos(ctx, "(distinguishedName=" + escaparFiltro(dn) + ")",
                        fila, nomes, dnsVisitados);
            }
        } finally {
            try {
                ctx.close();
            } catch (Exception ignorada) {
                // Fechar o contexto é boa higiene, mas um erro aqui não pode
                // mascarar o resultado da consulta, que já foiondertido acima.
                log.trace("Falha ao fechar o contexto LDAP: {}", ignorada.getMessage());
            }
        }
        return new ArrayList<>(nomes);
    }

    private void enfileirarGrupos(LdapContext ctx, String filtro, Deque<String> fila,
                                  Set<String> nomes, Set<String> dnsVisitados) throws Exception {
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setReturningAttributes(new String[]{"memberOf"});

        NamingEnumeration<SearchResult> ne = ctx.search(props.getBaseDn(), filtro, sc);
        while (ne.hasMore()) {
            Attributes a = ne.next().getAttributes();
            Attribute mo = a.get("memberOf");
            if (mo == null) {
                continue;
            }
            for (int i = 0; i < mo.size(); i++) {
                String dn = String.valueOf(mo.get(i));
                String nome = nomeDoGrupo(dn);
                if (nome != null) {
                    nomes.add(nome);
                }
                if (dnsVisitados.add(dn)) {
                    fila.add(dn);
                }
            }
        }
    }

    /** Tira o {@code CN=} do Distinguished Name. {@code CN=x,CN=y} vira {@code x}. */
    private static String nomeDoGrupo(String dn) {
        for (String parte : dn.split(",")) {
            String p = parte.trim();
            if (p.regionMatches(true, 0, "CN=", 0, 3)) {
                return p.substring(3).trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    /**
     * Escapa o que a RFC 4515 exige num filtro LDAP. Sem isso, um nome de usuário
     * com parênteses, asterisco ou barra invertida quebraria a consulta — e a
     * consulta vem do que o usuário digitou na tela de login.
     */
    static String escaparFiltro(String valor) {
        StringBuilder sb = new StringBuilder(valor.length() + 16);
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\5c");
                case '*' -> sb.append("\\2a");
                case '(' -> sb.append("\\28");
                case ')' -> sb.append("\\29");
                case ' ' -> sb.append("\\00");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // O cache em arquivo
    // ------------------------------------------------------------------

    private Set<String> doCacheOuVazio(CacheEntry memoriaEntry, CacheEntry discoEntry, String motivo) {
        CacheEntry e = memoriaEntry != null ? memoriaEntry : discoEntry;
        if (e == null) {
            log.debug("Sem grupos do AD para adicionar ({})", motivo);
            return Set.of();
        }
        log.debug("Usando grupos em cache ({})", motivo);
        return new LinkedHashSet<>(e.grupos());
    }

    private void gravar(String username, List<String> grupos) {
        CacheEntry entrada = new CacheEntry(List.copyOf(grupos), System.currentTimeMillis());
        memoria.put(username, entrada);
        try {
            ArquivoCache arquivo = lerTudo();
            arquivo.usuarios.put(username, entrada);
            Path destino = Path.of(props.getArquivoCache());
            Files.createDirectories(destino.getParent());
            // Grava em .tmp e move: se o processo morrer no meio, o arquivo antigo
            // continua inteiro em vez de ficar truncado.
            Path tmp = destino.resolveSibling(destino.getFileName() + ".tmp");
            Files.writeString(tmp, mapper.writeValueAsString(arquivo), StandardCharsets.UTF_8);
            Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // O cache em disco é conveniência. Se não deu para gravar, o login
            // continua funcionando com o cache em memória — não vale falhar por
            // causa de arquivo.
            log.warn("Não consegui gravar o cache de grupos em {}: {}",
                    props.getArquivoCache(), e.getMessage());
        }
    }

    private CacheEntry lerArquivo(String username) {
        try {
            return lerTudo().usuarios.get(username);
        } catch (Exception e) {
            return null;
        }
    }

    private ArquivoCache lerTudo() {
        File f = new File(props.getArquivoCache());
        if (!f.isFile() || f.length() == 0) {
            return new ArquivoCache();
        }
        try {
            return mapper.readValue(f, ArquivoCache.class);
        } catch (Exception e) {
            // Arquivo corrompido não pode derrubar o login. Começa limpo e a
            // próxima consulta bem-sucedida reescreve.
            log.warn("Cache de grupos ilegível ({}), recomeçando: {}",
                    f.getAbsolutePath(), e.getMessage());
            return new ArquivoCache();
        }
    }

    private void carregarArquivo() {
        try {
            ArquivoCache a = lerTudo();
            memoria.putAll(a.usuarios);
            log.info("Cache de grupos do AD carregado de {} ({} usuários)",
                    props.getArquivoCache(), a.usuarios.size());
        } catch (Exception e) {
            log.debug("Cache de grupos ainda não existe: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // JAAS: o login no KDC com o keytab
    // ------------------------------------------------------------------

    /**
     * Configuração JAAS que o {@link LoginContext} consome, com o módulo do Kerberos
     * apontado para o arquivo de keytab.
     *
     * <p>{@code useKeyTab=true} é o que troca senha por chave. {@code storeKey=false}
     * evita gravar a chave no disco de novo, e {@code doNotPrompt=true} impede o
     * módulo de tentar ler da senha de uma conta que não tem senha.
     */
    private static class Krb5KeytabConfiguration extends Configuration {

        private final AdProperties props;

        Krb5KeytabConfiguration(AdProperties props) {
            this.props = props;
        }

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            Map<String, Object> options = new HashMap<>();
            options.put("useKeyTab", "true");
            options.put("storeKey", "false");
            options.put("doNotPrompt", "true");
            options.put("useTicketCache", "false");
            options.put("isInitiator", "true");
            options.put("refreshKrb5Config", "true");
            options.put("principal", props.getPrincipal());
            options.put("debug", "false");
            return new AppConfigurationEntry[]{
                    new AppConfigurationEntry(
                            "com.sun.security.auth.module.Krb5LoginModule",
                            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                            options)};
        }

        @Override
        public void refresh() {
            // Configuração estática: o keytab é lido do disco a cada login, então
            // recarregar a configuração não mudaria nada.
        }
    }

    /**
     * O {@code Krb5LoginModule} com {@code useKeyTab} pede o nome do principal num
     * {@link NameCallback} e o <b>caminho</b> do keytab num
     * {@link PasswordCallback}. O campo de senha recebe um caminho, não uma senha —
     * e é o que evita a conta de serviço ter senha em lugar nenhum do app.
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

    private static boolean contem(Set<String> grupos, String desejado) {
        return grupos.contains(desejado.toLowerCase(Locale.ROOT));
    }
}
