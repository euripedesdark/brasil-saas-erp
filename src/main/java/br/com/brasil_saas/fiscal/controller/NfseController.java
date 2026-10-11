package br.com.brasil_saas.fiscal.controller;

import br.com.brasil_saas.fiscal.nfse.NfseEmissaoDtos;
import br.com.brasil_saas.fiscal.nfse.NfseEmissaoService;
import br.com.brasil_saas.fiscal.nfse.NfseArquivoService;
import br.com.brasil_saas.fiscal.nfse.NfseRetornoService;
import br.com.brasil_saas.fiscal.model.Nfse;
import br.com.brasil_saas.fiscal.repository.NfseRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * Emissao, consulta e cancelamento de NFS-e pelos provedores municipais e nacional.
 *
 * <p>Restrito ao modulo fiscal e aos perfis autorizados, porque emite documento
 * fiscal assinado.
 */
@Slf4j
@RestController
@RequestMapping("/api/fiscal/nfse")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCEIRO', 'GESTOR', 'GERENTE', 'DIRETORIA', 'ADMIN', 'SUPERUSER')")
public class NfseController {

    private final NfseEmissaoService emissaoService;
    private final NfseArquivoService arquivoService;
    private final NfseRetornoService retornoService;
    private final NfseRepository nfseRepository;

    /** Lista as NFS-e da empresa do token para a tela fiscal. */
    @GetMapping
    public ResponseEntity<?> listar(@AuthenticationPrincipal AuthenticatedUser usuario) {
        Long empresaId = empresaDo(usuario);
        return ResponseEntity.ok(nfseRepository
                .findAllByEmpresaIdAndDeletedAtIsNullOrderByDataEmissaoDesc(empresaId)
                .stream()
                .map(n -> {
                    java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("id", n.getId());
                    item.put("numero", n.getNumero());
                    item.put("tipo", "NFSe");
                    item.put("dataEmissao", n.getDataEmissao());
                    item.put("valorTotal", n.getValorTotal());
                    item.put("status", n.getStatus());
                    return item;
                })
                .toList());
    }

    /** Emite a nota. */
    @PostMapping("/emitir")
    public ResponseEntity<?> emitir(@RequestBody NfseEmissaoDtos.Emitir requisicao,
                                    @AuthenticationPrincipal AuthenticatedUser usuario) {
        Long empresaId = empresaDo(usuario);
        if (requisicao == null) {
            throw new BusinessException("Dados de emissao da NFS-e sao obrigatorios.");
        }
        // O tenant vem sempre do token. Nunca aceitar empresaId enviado pelo cliente.
        requisicao.setEmpresaId(empresaId);
        return ResponseEntity.ok(emissaoService.emitir(requisicao));
    }

    /**
     * Cancela a nota.
     *
     * <p>Usa o numero e o codigo de verificacao guardados na emissao. Sem
     * eles a prefeitura nao tem como cancelar, e o registro fiscal e a
     * unica fonte desses dados.
     */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id,
                                      @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(emissaoService.cancelar(id, empresaDo(usuario)));
    }

    /**
     * O que a prefeitura respondeu, em ordem.
     *
     * <p>Existe porque a prefeitura de São Paulo não tem consulta por chave
     * para este caso. Sem este registro, uma recusa que já aconteceu some assim
     * que o log roda, e ninguém descobre o que a prefeitura respondeu.
     *
     * <p>É também o que a pessoa consulta <b>depois</b> de fechar a tela de
     * erro. Antes, o motivo vivia no toast e no log do servidor: fechar a tela
     * e rotacionar o log significava perder a causa e recomeçar a correção do
     * zero. Aqui o motivo fica na tabela, com a data e o código de erro.
     */
    @GetMapping("/{id}/retornos")
    public ResponseEntity<?> retornos(@PathVariable Long id,
                                      @AuthenticationPrincipal AuthenticatedUser usuario) {
        Long empresaId = empresaDo(usuario);
        exigirNota(id, empresaId);
        return ResponseEntity.ok(retornoService.listar(id, empresaId));
    }

    /**
     * O JSON bruto de um retorno, como veio da API.
     *
     * <p>Serve para quando o ERP ainda não sabe ler um campo que a prefeitura
     * começou a mandar — o problema não é adivinhar, é poder olhar.
     */
    @GetMapping("/{id}/retornos/{retornoId}/bruto")
    public ResponseEntity<?> retornoBruto(@PathVariable Long id, @PathVariable Long retornoId,
                                          @AuthenticationPrincipal AuthenticatedUser usuario) {
        Long empresaId = empresaDo(usuario);
        exigirNota(id, empresaId);
        return ResponseEntity.ok(retornoService.lerBrutoDe(id, retornoId, empresaId));
    }

    /**
     * As recusas da empresa, para a tela de "o que a prefeitura recusou".
     *
     * <p>Só as de verdade. Quando a chamada não chegou a ter resposta o
     * {@code sucesso} fica nulo e não entra: não foi recusa, foi falha de
     * infraestrutura, e juntar as duas esconderia uma das duas.
     */
    @GetMapping("/retornos/recusas")
    public ResponseEntity<?> recusas(@AuthenticationPrincipal AuthenticatedUser usuario,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "50") int tamanho) {
        Long empresaId = empresaDo(usuario);
        int limite = Math.min(Math.max(tamanho, 1), 200);
        return ResponseEntity.ok(retornoService.listarRecusas(empresaId, pagina, limite));
    }

    /**
     * Os casos em que não deu para saber se a nota saiu.
     *
     * <p>Ficam numa tela só porque a pergunta que eles provocam é sempre a
     * mesma: "saiu ou não?". E porque em todos eles a resposta é a mesma:
     * conferir na prefeitura antes de emitir de novo.
     */
    @GetMapping("/retornos/para-conferir")
    public ResponseEntity<?> paraConferir(@AuthenticationPrincipal AuthenticatedUser usuario,
                                          @RequestParam(defaultValue = "0") int pagina,
                                          @RequestParam(defaultValue = "50") int tamanho) {
        Long empresaId = empresaDo(usuario);
        int limite = Math.min(Math.max(tamanho, 1), 200);
        return ResponseEntity.ok(retornoService.listarIndecisos(empresaId, pagina, limite));
    }

    /** Baixa o XML assinado. Guardado por 5 anos. */
    @GetMapping("/{id}/xml")
    public ResponseEntity<byte[]> xml(@PathVariable Long id,
                                      @AuthenticationPrincipal AuthenticatedUser usuario) {
        return arquivo(id, false, empresaDo(usuario));
    }

    /** Baixa o PDF de conferência. Expira em 60 dias. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id,
                                      @AuthenticationPrincipal AuthenticatedUser usuario) {
        return arquivo(id, true, empresaDo(usuario));
    }

    /**
     * A empresa do token, sempre.
     *
     * <p>Nunca do corpo ou da query: {@code ExigeEmpresaFilter} já garante que
     * exista empresa em {@code /api/**}, então aceitar uma da requisição seria
     * abrir brecha sem necessidade.
     */
    private Long empresaDo(AuthenticatedUser usuario) {
        if (usuario == null || usuario.getEmpresaId() == null) {
            throw new BusinessException("Usuario sem empresa no token. Emissao de NFS-e exige empresa.");
        }
        return usuario.getEmpresaId();
    }

    /**
     * Carrega a nota garantindo que ela é desta empresa.
     *
     * <p>O filtro por tenant é na query, e não depois no Java: os ids são
     * sequenciais e previsíveis, então um ADMIN de uma empresa que adivinhe o id
     * da nota de outra consegue ler o retorno da prefeitura, baixar o XML e o
     * PDF, e cancelar a nota. Se o id for de outra empresa, a query não acha.
     */
    private Nfse exigirNota(Long id, Long empresaId) {
        return nfseRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("NFS-e " + id + " nao encontrada."));
    }

    private ResponseEntity<byte[]> arquivo(Long id, boolean pdf, Long empresaId) {
        Nfse nfse = exigirNota(id, empresaId);
        byte[] conteudo = pdf ? arquivoService.lerPdf(nfse) : arquivoService.lerXml(nfse);

        if (conteudo == null || conteudo.length == 0) {
            String prazo = pdf
                    ? "O PDF expira 60 dias apos a emissao. Depois disso use o XML."
                    : "O XML nao foi arquivado. Verifique o log da emissao: a nota pode ter sido "
                    + "emitida sem o arquivo ser salvo.";
            throw new BusinessException("Arquivo indisponivel para a NFS-e " + nfse.getNumero() + ". " + prazo);
        }

        String extensao = pdf ? ".pdf" : ".xml";
        String tipo = pdf ? "application/pdf" : "application/xml";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"nfse-" + nfse.getNumero() + extensao + "\"")
                .contentType(MediaType.parseMediaType(tipo))
                .body(conteudo);
    }
}
