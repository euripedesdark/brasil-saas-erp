package br.com.brasil_saas.ia.assistente;

import br.com.brasil_saas.ia.model.AIConfig;
import br.com.brasil_saas.ia.repository.AIConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * O Assistente ERP: um especialista que responde com o ERP, e não com o modelo.
 *
 * <p>A ordem das fontes é o desenho, e foi o dono a definir: <b>busca fiscal,
 * dados do ERP, documentação, e só então o modelo</b>. O modelo é a última fonte,
 * e a consequência é que a resposta sobrevive ao modelo estar fora.
 *
 * <p>Isso não é robustez teórica. O free tier do OpenRouter é instável: de 20
 * modelos gratuitos, vários devolvem 429, e o {@code openrouter/free} cai num
 * modelo que devolve {@code content=null} com o texto em {@code reasoning}. Se o
 * modelo fosse a primeira fonte, o sistema cairia junto com ele.
 *
 * <p>Por isso a resposta é montada em duas partes. A <b>espinha</b> — o código,
 * a descrição, o motivo e a origem — sai do ERP e é determinística. O modelo
 * recebe essa espinha como contexto e escreve a frase em volta. Ele pode errar a
 * explicação; não pode errar o código, porque o código não é dele.
 */
@Service
public class AssistenteService {

    private static final Logger log = LoggerFactory.getLogger(AssistenteService.class);
    private static final int MAXIMO_NO_PROMPT = 4000;

    private final ContextoErpService contexto;
    private final AIConfigRepository configs;
    private final AssistenteAuditoriaRepository auditoria;
    private final ChatClient chatClient;

    @Value("${brasil-saas.ia.assistente.modelo-padrao:inclusionai/ling-3.0-flash-sante:free}")
    private String modeloPadrao;

    @Value("${brasil-saas.ia.assistente.consultar-modelo:true}")
    private boolean consultarModelo;

    public AssistenteService(final ContextoErpService contexto,
                             final AIConfigRepository configs,
                             final AssistenteAuditoriaRepository auditoria,
                             final ChatClient chatClient) {
        this.contexto = contexto;
        this.configs = configs;
        this.auditoria = auditoria;
        this.chatClient = chatClient;
    }

    @Transactional
    public Resposta perguntar(final String pergunta, final Long empresaId, final String usuarioNome) {
        final long inicio = System.nanoTime();

        final ContextoErpService.Contexto ctx = contexto.montar(pergunta, empresaId);
        final String espinha = montarEspinha(pergunta, ctx);
        final String modelo = modeloDe(empresaId);

        String textoModelo = null;
        String erro = null;
        String status = "ok";
        if (consultarModelo) {
            try {
                textoModelo = consultar(modelo, pergunta, espinha);
                if (textoModelo == null || textoModelo.isBlank()) {
                    status = "modelo-indisponivel";
                }
            } catch (RuntimeException e) {
                // O modelo é a última fonte. Se ele cai, a resposta continua — e
                // a auditoria registra que ele não respondeu, que é informação.
                status = "modelo-indisponivel";
                erro = e.getMessage();
                log.warn("Assistente: modelo indisponivel para empresa {}: {}", empresaId, e.toString());
            }
        } else {
            status = "modelo-indisponivel";
        }

        final String respostaFinal = textoModelo == null || textoModelo.isBlank()
                ? espinha
                : textoModelo.trim() + "\n\n---\n" + espinha;
        final long duracao = (System.nanoTime() - inicio) / 1_000_000;

        gravar(pergunta, empresaId, usuarioNome, ctx, modelo, textoModelo, respostaFinal,
                status, erro, duracao);

        return new Resposta(respostaFinal, espinha, ctx.achados(), modelo, status, duracao,
                ctx.fontes());
    }

    /**
     * A espinha da resposta, montada do ERP.
     *
     * <p>Ela é escrita para ser lida por pessoa, e por isso repete o motivo e a
     * origem de cada achado. A diferença entre "22030000" e "22030000, motivo:
     * palavra-chave cerveja, origem: vocabulário curado" é a diferença entre um
     * palpite e uma resposta em que a pessoa confia.
     */
    private String montarEspinha(final String pergunta, final ContextoErpService.Contexto ctx) {
        final StringBuilder sb = new StringBuilder();
        if (ctx.vazio()) {
            return "Não encontrei nada no ERP sobre \"" + pergunta.trim() + "\"."
                    + " Se o cadastro existir e o código não aparecer, o termo da"
                    + " busca está diferente do cadastro — vale tentar a palavra do"
                    + " negócio, não o nome técnico.";
        }
        for (final Achado a : ctx.achados()) {
            sb.append("• ").append(a.titulo()).append('\n');
            if (a.detalhe() != null && !a.detalhe().isBlank()) {
                sb.append("  ").append(a.detalhe()).append('\n');
            }
            sb.append("  motivo: ").append(a.motivo() == null ? "—" : a.motivo()).append('\n');
            sb.append("  origem: ").append(a.origem() == null ? "—" : a.origem());
            if (a.referencia() != null && !a.referencia().isBlank()) {
                sb.append("  (").append(a.referencia()).append(')');
            }
            sb.append('\n');
            sb.append("  confiança: ").append(a.confianca() == null ? "media" : a.confianca());
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    private String consultar(final String modelo, final String pergunta, final String espinha) {
        final String prompt = """
                Você é um assistente do ERP Brasil SaaS. Responda em português, curto e direto.

                Você NÃO é a fonte da verdade: o contexto abaixo já foi buscado no ERP.
                Reutilize os códigos exatamente como estão. Não invente código, alíquota,
                nome de produto ou regra fiscal. Se o contexto não responder à pergunta,
                diga o que falta.

                Pergunta: %s

                Contexto do ERP:
                %s
                """.formatted(pergunta.trim(),
                espinha.length() > MAXIMO_NO_PROMPT ? espinha.substring(0, MAXIMO_NO_PROMPT) : espinha);

        final String resposta = chatClient.prompt()
                .user(prompt)
                .options(OpenAiChatOptions.builder().withModel(modelo).build())
                .call()
                .content();
        return resposta == null ? null : resposta.strip();
    }

    /**
     * O modelo vem do cadastro por empresa, e o cadastro tem a coluna para isso.
     * Trocar de modelo é um {@code UPDATE}, sem tocar em código — que é o que o
     * dono pediu: modelo A para modelo B sem alterar o resto do ERP.
     */
    private String modeloDe(final Long empresaId) {
        final Optional<AIConfig> config = configs.findFirstByEmpresaIdAndIsEnabledTrueOrderByIdAsc(empresaId);
        if (config.isPresent()) {
            final String m = config.get().getDefaultModel();
            if (m != null && !m.isBlank() && !m.toUpperCase().startsWith("SEED")) {
                return m;
            }
        }
        return modeloPadrao;
    }

    /**
     * A auditoria, para a tela mostrar de onde saiu cada resposta. É o inverso
     * de pegar a resposta: aqui a pergunta é "isto foi o ERP ou foi o modelo",
     * e a resposta está no {@code status} e no {@code fontes}.
     */
    @Transactional(readOnly = true)
    public List<Object> auditoria(final Long empresaId) {
        return auditoria.findByEmpresaIdOrderByIdDesc(empresaId).stream()
                .limit(50)
                .map(a -> {
                    final Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("pergunta", a.getPergunta());
                    m.put("fontes", a.getFontes());
                    m.put("modelo", a.getModelo());
                    m.put("status", a.getStatus());
                    m.put("duracaoMs", a.getDuracaoMs());
                    m.put("resposta", a.getResposta());
                    m.put("criadoEm", a.getCreatedAt());
                    return (Object) m;
                })
                .toList();
    }

    private void gravar(final String pergunta, final Long empresaId, final String usuarioNome,
                        final ContextoErpService.Contexto ctx, final String modelo,
                        final String textoModelo, final String resposta, final String status,
                        final String erro, final long duracaoMs) {
        try {
            final AssistenteAuditoria a = new AssistenteAuditoria();
            a.setEmpresaId(empresaId);
            a.setUsuarioNome(usuarioNome);
            a.setPergunta(pergunta);
            a.setContexto(Map.of("achados", ctx.achados().stream().map(x -> Map.of(
                    "fonte", String.valueOf(x.fonte()),
                    "titulo", String.valueOf(x.titulo()),
                    "motivo", String.valueOf(x.motivo()),
                    "origem", String.valueOf(x.origem()),
                    "confianca", String.valueOf(x.confianca()),
                    "referencia", String.valueOf(x.referencia()))).toList()));
            a.setFontes(ctx.fontes());
            a.setModelo(modelo);
            a.setRespostaModelo(textoModelo);
            a.setResposta(resposta);
            a.setStatus(status);
            a.setErro(erro);
            a.setDuracaoMs(duracaoMs);
            auditoria.save(a);
        } catch (RuntimeException e) {
            // Auditar não pode derrubar a resposta que o usuário já esperou.
            log.warn("Assistente: falha ao gravar auditoria: {}", e.toString());
        }
    }

    /**
     * A resposta. {@link #achados()} vai separada porque a tela precisa mostrar
     * código, motivo e origem como campos, e não dentro de um texto.
     */
    public record Resposta(String resposta,
                           String espinha,
                           List<Achado> achados,
                           String modelo,
                           String status,
                           long duracaoMs,
                           String fontes) {

        public boolean respondeuOModelo() {
            return !"modelo-indisponivel".equals(status);
        }
    }
}
