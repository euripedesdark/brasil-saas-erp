package br.com.brasil_saas.fiscal.nfse;

import br.com.brasil_saas.fiscal.model.Nfse;
import br.com.brasil_saas.fiscal.model.NfseRetorno;
import br.com.brasil_saas.fiscal.repository.NfseRetornoRepository;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.image.DocumentoArquivo;
import br.com.brasil_saas.shared.service.GenericoDocumentoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grava o que a prefeitura respondeu em cada chamada.
 *
 * <h2>O que este serviço evita
 *
 * <p>Este serviço existe porque a resposta da prefeitura morria com a tela de
 * erro. O ERP lançava a exceção, o frontend mostrava o toast, e quando a pessoa
 * fechava o motivo era uma linha de log — que rotaciona. O registro fiscal
 * ficava {@code FALHA_EMISSAO} e nenhum motivo, que é o pior estado possível:
 * sabe-se que falhou, não sabe-se por quê, e a correção recomeça do zero toda
 * vez.
 *
 * <p>Em 26/09/2026 isso custou as notas 29 e 30. As duas foram emitidas de
 * verdade, e o ERP respondeu "a prefeitura não confirmou". O motivo do erro
 * real — o bridge Ruby devolvia a resposta embrulhada em {@code error}, com o
 * sucesso em inglês — vivia numa linha de log. Sem número e sem código de
 * verificação gravados, cancelar as duas exigia ir na prefeitura de mão.
 *
 * <h2>Três decisões
 *
 * <ol>
 *   <li><b>Falha aqui não desfaz a operação.</b> Se gravar o retorno falhar, a
 *       emissão continua valendo. A nota existe na prefeitura; reportar erro
 *       levaria o usuário a repetir e criar duplicidade, que é o problema mais
 *       caro deste módulo.</li>
 *   <li><b>Grava também a falha.</b> A recusa é gravada antes de a exceção
 *       subir. Sem isso o registro fica {@code FALHA_EMISSAO} sem causa, que é
 *       exatamente o estado que havia antes disto.</li>
 *   <li><b>Transação própria.</b> Roda em {@code REQUIRES_NEW} porque o
 *       registro da recusa precisa ser gravado mesmo quando a operação está em
 *       rollback — e é no caminho da exceção que ele mais importa.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NfseRetornoService {

    public static final String TIPO_RETORNO = "nfse_retorno";
    public static final String MODULO = "fiscal";

    /**
     * Código de erro da prefeitura no começo da mensagem.
     *
     * <p>A Prefeitura de São Paulo escreve {@code [1001] XML não compatível com
     * Schema...}, com o número entre colchetes logo no início, então dá para
     * pegar sem adivinhar posição.
     */
    private static final Pattern CODIGO = Pattern.compile("\\[(\\d{3,5})]");

    private final NfseRetornoRepository retornoRepository;
    private final GenericoDocumentoService documentoService;
    private final ObjectMapper objectMapper;

    /**
     * Grava o retorno de uma chamada.
     *
     * @param nfse      a nota da conversa; pode ser {@code null} em consulta
     *                  que ainda não tem nota
     * @param resposta  o corpo JSON como veio, {@code null} se a chamada não
     *                  chegou a ter resposta
     * @param erro      a exceção de transporte, quando houve
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long empresaId, Nfse nfse, String operacao,
                          Map<String, Object> resposta, Integer httpStatus,
                          Long duracaoMs, RuntimeException erro) {

        NfseRetorno registro = new NfseRetorno();
        registro.setEmpresaId(empresaId);
        registro.setNfse(nfse);
        registro.setOperacao(operacao);
        registro.setSucesso(sucesso(resposta, erro));
        registro.setHttpStatus(httpStatus);
        registro.setDuracaoMs(duracaoMs);
        registro.setMensagem(mensagem(resposta, erro));
        registro.setCodigo(codigoDa(registro.getMensagem()));
        registro.setAlertas(String.join("\n", alertas(resposta)));

        try {
            // A linha primeiro, porque o arquivo precisa do id dela para ser
            // gravado. Se o arquivo falhar, sobra a linha com documento_id
            // nulo — o que é honesto e consultável. O inverso, ponteiro sem
            // linha, seria um registro que promete um corpo e não tem.
            NfseRetorno gravada = retornoRepository.saveAndFlush(registro);
            gravada.setDocumentoId(guardarBruto(empresaId, gravada.getId(), operacao, resposta, erro));
            retornoRepository.saveAndFlush(gravada);
        } catch (RuntimeException e) {
            log.error("Nao consegui gravar o retorno da {} da NFS-e {}. A prefeitura respondeu "
                            + "mas o motivo nao ficou registrado. Causa: {}",
                    operacao, nfse == null ? "?" : nfse.getNumero(), e);
        }
    }

    /**
     * Versão sem nota, para quando a chamada acontece antes de existir registro.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarSemNota(Long empresaId, String operacao, Map<String, Object> resposta,
                                 Integer httpStatus, Long duracaoMs) {
        registrar(empresaId, null, operacao, resposta, httpStatus, duracaoMs, null);
    }

    // ------------------------------------------------------------------

    /**
     * {@code true} só quando a prefeitura respondeu e disse que sim.
     *
     * <p>{@code false} quando ela respondeu e disse que não. {@code null} quando
     * não houve resposta.
     *
     * <p>A distinção entre {@code false} e {@code null} é o que separa problema
     * de negócio de problema de infraestrutura, e por isso ela não pode vir de
     * "deu exceção". A prefeitura recusa com HTTP 422 e um corpo: houve
     * resposta, e a resposta foi não. Erro de transporte — rede, certificado,
     * serviço fora — é que não tem resposta e vira {@code null}.
     *
     * <p>Colapsar as duas faria o suporte investigar cadastro quando o que caiu
     * foi a máquina, e a consulta "o que a prefeitura recusou" perderia
     * justamente as recusas, que é o que a tela precisa mostrar.
     */
    private Boolean sucesso(Map<String, Object> resposta, RuntimeException erro) {
        if (resposta != null) {
            return Boolean.TRUE.equals(resposta.get("sucesso"));
        }
        if (erro instanceof RestClientResponseException) {
            // Veio status HTTP: a prefeitura respondeu. Se o cliente reclamou
            // do 4xx/5xx, a resposta dela foi "não".
            return false;
        }
        // Sem corpo e sem status: a chamada não chegou a ter retorno.
        return null;
    }

    /**
     * A mensagem que a pessoa precisa ler.
     *
     * <p>Quando a recusa vem por status HTTP, o corpo está dentro da exceção e
     * {@code resposta} é nulo. Sem abrir esse corpo, a mensagem gravada seria o
     * embrulho do cliente HTTP — algo como {@code 422 : "{"erro":"...
     * "sucesso":false}"}, que obriga quem lê a decifrar JSON de dentro de outra
     * string. Por isso o corpo da exceção entra no mesmo caminho.
     */
    private String mensagem(Map<String, Object> resposta, RuntimeException erro) {
        StringBuilder texto = new StringBuilder();

        if (erro instanceof RestClientResponseException http) {
            Map<String, Object> corpo = lerCorpo(http);
            if (corpo != null && corpo.get("erro") != null) {
                texto.append(corpo.get("erro"));
            }
        } else if (erro != null) {
            texto.append(erro.getMessage());
        }

        if (resposta != null) {
            Object mensagem = resposta.get("erro");
            if (mensagem != null && !String.valueOf(mensagem).isBlank()) {
                if (!texto.isEmpty()) {
                    texto.append(" | ");
                }
                texto.append(mensagem);
            }
        }
        return texto.isEmpty() ? null : texto.toString();
    }

    /**
     * Abre o corpo JSON que veio dentro da exceção de status HTTP.
     *
     * @return o corpo como mapa, ou {@code null} se não veio JSON legível
     */
    private Map<String, Object> lerCorpo(RestClientResponseException erro) {
        try {
            String bruto = erro.getResponseBodyAsString(StandardCharsets.UTF_8);
            if (bruto.isBlank()) {
                return null;
            }
            return objectMapper.readValue(bruto, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // Corpo ausente ou não-JSON é caso previsto: a API pode responder
            // HTML de erro, por exemplo. Não é motivo para falhar a gravação.
            log.debug("Corpo da resposta de status {} nao era JSON legivel: {}",
                    erro.getStatusCode().value(), e);
            return null;
        }
    }

    /**
     * Extrai o código da prefeitura, quando ela mandar.
     *
     * <p>Existe porque o código é o que dá para consultar. A mensagem do 1001
     * mede centenas de caracteres e muda conforme o campo que falhou; o código
     * é estável e cabe numa coluna que dá para filtrar.
     */
    private String codigoDa(String mensagem) {
        if (mensagem == null) {
            return null;
        }
        Matcher m = CODIGO.matcher(mensagem);
        return m.find() ? m.group(1) : null;
    }

    private List<String> alertas(Map<String, Object> resposta) {
        if (resposta == null) {
            return List.of();
        }
        Object lista = resposta.get("alertas");
        if (!(lista instanceof List<?> itens)) {
            return List.of();
        }
        return itens.stream().map(String::valueOf).toList();
    }

    /**
     * Grava o JSON bruto no Mongo.
     *
     * <p>O {@code tipoEntidade} é o mesmo para toda linha e o {@code entidadeId}
     * é o id do próprio registro de retorno, que é único. Assim o
     * {@code GenericoDocumentoService} — que apaga o anterior do mesmo
     * (empresa, tipo, entidade) — nunca tem o que apagar: cada evento é um
     * documento novo. Reaproveitar o id da nota aqui apagaria a emissão quando o
     * cancelamento fosse gravado, que é a memória que mais importa perder.
     */
    private String guardarBruto(Long empresaId, Long registroId, String operacao,
                                Map<String, Object> resposta, RuntimeException erro) {
        try {
            String nome = "retorno-" + operacao.toLowerCase() + "-" + registroId + ".json";

            Map<String, Object> aGravar = resposta;
            if (aGravar == null && erro instanceof RestClientResponseException http) {
                // A recusa chega por status HTTP, e o corpo vem dentro da
                // exceção. Sem abrir aqui, o arquivo JSON ficaria vazio justo na
                // hora que ele mais importa, que é a recusa.
                aGravar = lerCorpo(http);
            }

            byte[] conteudo;
            if (aGravar != null) {
                conteudo = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(aGravar);
            } else {
                conteudo = ("{\"erro_transporte\": "
                        + objectMapper.writeValueAsString(
                                erro == null ? "sem resposta" : String.valueOf(erro.getMessage()))
                        + "}").getBytes(StandardCharsets.UTF_8);
            }

            DocumentoArquivo doc = documentoService.salvarBytes(
                    empresaId, MODULO, TIPO_RETORNO, registroId,
                    nome, "application/json", conteudo);
            return doc.getId();
        } catch (Exception e) {
            log.error("Nao consegui arquivar o JSON bruto do retorno {} da operacao {}. "
                            + "Fica o registro no Postgres sem o corpo original. Causa: {}",
                    registroId, operacao, e);
            return null;
        }
    }

    /**
     * As recusas de verdade, paginadas.
     *
     * <p>Para a tela de "o que a prefeitura recusou". O motivo vem na linha, e
     * é o que a pessoa precisa para corrigir sem pedir ajuda a ninguém.
     */
    @Transactional(readOnly = true)
    public List<RetornoView> listarRecusas(Long empresaId, int pagina, int tamanho) {
        return retornoRepository.recusas(empresaId, PageRequest.of(pagina, tamanho)).stream()
                .map(NfseRetornoService::paraView)
                .toList();
    }

    /**
     * Os casos em que não deu para saber se a nota saiu.
     *
     * <p>A pergunta que eles provocam é sempre a mesma, "saiu ou não?", e a
     * resposta é sempre a mesma: conferir na prefeitura. Por isso ficam numa
     * tela só, em vez de se esconderem na lista de falhas.
     */
    @Transactional(readOnly = true)
    public List<RetornoView> listarIndecisos(Long empresaId, int pagina, int tamanho) {
        return retornoRepository.indecisos(empresaId, PageRequest.of(pagina, tamanho)).stream()
                .map(NfseRetornoService::paraView)
                .toList();
    }

    /**
     * A conversa inteira com a prefeitura sobre uma nota.
     *
     * <p>Ordenada da mais recente para a mais antiga porque o que se procura é o
     * que aconteceu por último: a recusa que travou a última tentativa vem
     * primeiro na tela.
     *
     * <p>Devolve DTO, não a entidade. A entidade tem referência {@code LAZY}
     * para a nota, e serializar o proxy depois que a sessão fechou estoura
     * {@code LazyInitializationException} — o controller não tem mais sessão
     * aberta, então o acesso ao proxy falha. Além disso, serializar entidade
     * expõe tudo que o Lombok gerar.
     */
    @Transactional(readOnly = true)
    public List<RetornoView> listar(Long nfseId, Long empresaId) {
        return retornoRepository.porNota(nfseId, empresaId).stream()
                .map(NfseRetornoService::paraView)
                .toList();
    }

    private static RetornoView paraView(NfseRetorno r) {
        return new RetornoView(
                r.getId(),
                // O id da nota. Sem ele a tela nao consegue ligar o retorno a
                // nota nem abrir o JSON bruto, porque os dois endpoints sao
                // /{nfseId}/retornos/{retornoId}. A coluna nfse_id existe e
                // estava de fora do DTO.
                r.getNfse() != null ? r.getNfse().getId() : null,
                r.getOperacao(),
                r.getSucesso(),
                r.getHttpStatus(),
                r.getDuracaoMs(),
                r.getCodigo(),
                r.getMensagem(),
                r.getAlertas(),
                r.getDocumentoId() != null,
                r.getCreatedAt());
    }

    /**
     * O que a tela mostra de um retorno.
     *
     * @param nfseId        a nota a que este retorno pertence, para a tela
     *                      conseguir abrir o JSON bruto
     * @param duracaoMs     quanto a chamada levou. Um retorno de 30 s e a
     *                      prefeitura travada; de 40 ms e recusa de schema.
     *                      Sem esse numero os dois parecem iguais na tela.
     * @param temDocumento  se o JSON bruto foi arquivado, para a tela saber se
     *                      ha o que abrir
     */
    public record RetornoView(Long id, Long nfseId, String operacao, Boolean sucesso,
                              Integer httpStatus, Long duracaoMs, String codigo,
                              String mensagem, String alertas, boolean temDocumento,
                              LocalDateTime criadoEm) {
    }

    /**
     * O corpo bruto de um retorno específico.
     *
     * <p>Filtra pela empresa e pela nota junto. Sem os dois, um id de retorno
     * adivinhado leria a resposta da prefeitura de outra empresa.
     */
    public String lerBrutoDe(Long nfseId, Long retornoId, Long empresaId) {
        NfseRetorno registro = retornoRepository.findById(retornoId)
                .filter(r -> empresaId.equals(r.getEmpresaId()))
                .filter(r -> r.getNfse() != null && nfseId.equals(r.getNfse().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Retorno " + retornoId + " da NFS-e " + nfseId + " nao encontrado."));
        return lerBruto(registro.getDocumentoId());
    }

    /**
     * Lê o JSON bruto de um retorno.
     *
     * @return o corpo como veio, ou {@code null} se não houver arquivo
     */
    public String lerBruto(String documentoId) {
        if (documentoId == null || documentoId.isBlank()) {
            return null;
        }
        byte[] conteudo = documentoService.lerConteudo(documentoId);
        return conteudo == null ? null : new String(conteudo, StandardCharsets.UTF_8);
    }
}
