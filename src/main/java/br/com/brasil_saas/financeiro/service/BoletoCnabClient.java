package br.com.brasil_saas.financeiro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

import br.com.brasil_saas.shared.exception.BusinessException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Cliente do servico boleto_cnab_api (Ruby, em microservices/boleto-cnab-api).
 *
 * O ERP trata este servico como OPCIONAL de proposito: a API e terceira
 * parte, isolada num container proprio, e o financeiro precisa funcionar sem
 * ela. Se o servico estiver fora, o chamador recebe
 * {@link CnabIndisponivelException} e segue — nada trava na inicializacao.
 *
 * Por isso nao ha @PreAuthorize de servico aqui: o acesso e do tenant e
 * medido no controller, via empresaId do token.
 */
@Slf4j
@Component
public class BoletoCnabClient {

    private final RestClient http;
    private final String apiKey;
    private final String baseUrl;

    public BoletoCnabClient(
            @Value("${brasil-saas.cnab.url:http://localhost:9292}") String url,
            @Value("${brasil-saas.cnab.api-key:}") String apiKey,
            @Value("${brasil-saas.cnab.timeout-ms:30000}") long timeoutMs) {

        this.baseUrl = url;
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeoutMs);
        factory.setReadTimeout((int) timeoutMs);

        this.http = RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isConfigurado() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    /**
     * Valida os dados de um boleto.
     * @return "true"/"false"
     */
    public String validarBoleto(String bank, String dataJson) {
        try {
            return semAspas(http.get()
                    .uri(uriBoleto("/api/boleto/validate", bank, null, dataJson))
                    .retrieve().body(String.class));
        } catch (HttpClientErrorException e) {
            // 4xx do emissor e recusa de dado: e erro do usuario, nao do servico
            throw new BusinessException("O banco emissor recusou o boleto: " + corpo(e));
        } catch (RestClientException e) {
            throw new CnabIndisponivelException(mensagem(e), e);
        }
    }

    /**
     * Nosso numero calculado pelo banco emissor.
     *
     * A API devolve uma JSON string ("175/12345678-4"), entao o corpo chega
     * com as aspas. Sem desembrulhar, o valor persistido no banco levava aspas
     * junto e quebrava a conciliacao — nosso_numero nao casa com o retorno CNAB.
     */
    public String nossoNumero(String bank, String dataJson) {
        try {
            return semAspas(http.get()
                    .uri(uriBoleto("/api/boleto/nosso_numero", bank, null, dataJson))
                    .retrieve().body(String.class));
        } catch (HttpClientErrorException e) {
            throw new BusinessException("O banco emissor recusou os dados: " + corpo(e));
        } catch (RestClientException e) {
            throw new CnabIndisponivelException(mensagem(e), e);
        }
    }

    /**
     * Gera o arquivo do boleto. type: pdf | png | jpg | tif
     * @return bytes do arquivo
     */
    public byte[] gerarBoleto(String bank, String type, String dataJson) {
        byte[] r = http.get()
                .uri(uriBoleto("/api/boleto", bank, type, dataJson))
                .retrieve()
                .body(byte[].class);
        return r == null ? new byte[0] : r;
    }

    /** Remove as aspas de uma resposta JSON simples (string ou booleano). */
    private String semAspas(String bruto) {
        if (bruto == null) return null;
        String v = bruto.strip();
        if (v.length() >= 2 && v.charAt(0) == 34 && v.charAt(v.length() - 1) == 34) {
            v = v.substring(1, v.length() - 1);
        }
        return v.strip();
    }

    /**
     * Monta a URI do endpoint de boleto.
     *
     * O `data` e codificado aqui de proposito: o UriBuilder do Spring trata
     * as chaves como variavel de template, entao passar o JSON cru faz a URI
     * ser reinterpretada e o servico responde "Illegal character in query".
     * Chegando ja percent-encoded, nao sobra chave para o Spring expandir.
     *
     * A URI e montada como absoluta e passada como URI (e nao String) de
     * proposito: o metodo uri(String) reaplica encoding no que ja estava
     * codificado e o JSON chega ao servico como "%7B%22valor..." — lido como
     * texto, nao como JSON. Com URI absoluta o cliente nao toca no conteudo.
     */
    private URI uriBoleto(String path, String bank, String type, String dataJson) {
        StringBuilder sb = new StringBuilder(baseUrl)
                .append(path)
                .append("?bank=").append(codificar(bank));
        if (type != null && !type.isBlank()) {
            sb.append("&type=").append(codificar(type));
        }
        sb.append("&data=").append(codificar(dataJson));
        return URI.create(sb.toString());
    }

    private String codificar(String valor) {
        return valor == null ? "" : URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    /**
     * Gera a remessa CNAB. `data` e um JSON com a lista de pagamentos e vai
     * como arquivo (multipart), diferente do GET /boleto que vai na query.
     *
     * @return bytes do arquivo .rem
     */
    public byte[] gerarRemessa(String bank, String tipo, String jsonPagamentos, String nomeArquivo) {
        return postArquivo("/api/remessa", bank, tipo, jsonPagamentos, nomeArquivo);
    }

    /**
     * Converte o retorno do banco em JSON. O arquivo e binario/texto cru
     * (CNAB), nao JSON — por isso vai como resource.
     */
    public String processarRetorno(String bank, String tipo, byte[] arquivoRetorno, String nomeArquivo) {
        MultiValueMap<String, Object> form = newForm(bank, tipo, nomeArquivo);
        form.add("data", new ByteArrayResource(arquivoRetorno) {
            @Override
            public String getFilename() {
                return nomeArquivo;
            }
        });

        try {
            return http.post()
                    .uri("/api/retorno")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new CnabIndisponivelException(mensagem(e), e);
        }
    }

    /** Saudacao: usada pelo health check da tela. */
    public boolean estaVivo() {
        try {
            String r = http.get().uri("/docs").retrieve().body(String.class);
            return r != null;
        } catch (RestClientException e) {
            return false;
        }
    }

    // ---------------- interno ----------------

    private String get(String uri, String... vars) {
        try {
            return http.get()
                    .uri(uri, (Object[]) vars)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new CnabIndisponivelException(mensagem(e), e);
        }
    }

    private byte[] postArquivo(String path, String bank, String tipo, String conteudo, String nome) {
        MultiValueMap<String, Object> form = newForm(bank, tipo, nome);
        form.add("data", new ByteArrayResource(conteudo.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return nome;
            }
        });

        try {
            byte[] r = http.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(byte[].class);
            return r == null ? new byte[0] : r;
        } catch (RestClientException e) {
            throw new CnabIndisponivelException(mensagem(e), e);
        }
    }

    private MultiValueMap<String, Object> newForm(String bank, String tipo, String nome) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("bank", bank);
        form.add("type", tipo);
        if (apiKey != null && !apiKey.isBlank()) {
            form.add("__api_key", apiKey);
        }
        return form;
    }

    /**
     * Corpo da resposta do emissor, para a recusa chegar com o motivo real.
     *
     * O 5xx sem corpo e o caso comum: o boleto-cnab-api estoura a pilha e
     * devolve "[no body]". A mensagem de indisponibilidade acima e a
     * correta para o usuario nesses casos.
     */
    private String corpo(RestClientException e) {
        // getResponseBodyAsString vive em RestClientResponseException, nao em
        // RestClientException — erro de transporte nao tem corpo.
        if (e instanceof RestClientResponseException rcre) {
            String b = rcre.getResponseBodyAsString();
            if (!b.isBlank() && !b.contains("no body")) {
                return b.length() > 300 ? b.substring(0, 300) + "..." : b;
            }
        }
        if (e instanceof HttpServerErrorException) {
            return "o servico do banco emissor falhou.";
        }
        return String.valueOf(e.getMessage());
    }

    private String mensagem(RestClientException e) {
        String url = baseUrl + " (" + e.getClass().getSimpleName() + ")";
        return "Servico de boletos indisponivel em " + url
                + ". O financeiro continua funcionando; "
                + "so geracao de boleto/remessa e leitura de retorno ficam indisponiveis.";
    }

    /** HTTP 503 no controller; o usuario ve que e um servico externo fora do ar. */
    public static class CnabIndisponivelException extends RuntimeException {
        public CnabIndisponivelException(String message, Throwable causa) {
            super(message, causa);
        }
    }
}
