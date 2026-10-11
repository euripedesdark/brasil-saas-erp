package br.com.brasil_saas.financeiro.controller;

import br.com.brasil_saas.financeiro.model.Boleto;
import br.com.brasil_saas.financeiro.model.ContaBancaria;
import br.com.brasil_saas.financeiro.model.Remessa;
import br.com.brasil_saas.financeiro.model.RemessaItem;
import br.com.brasil_saas.financeiro.model.RetornoBancario;
import br.com.brasil_saas.financeiro.repository.ContaBancariaRepository;
import br.com.brasil_saas.financeiro.service.BoletoCnabClient;
import br.com.brasil_saas.financeiro.service.BoletoService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import br.com.brasil_saas.shared.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Boletos, remessas e retornos CNAB.
 *
 * Todos os pontos leem a empresa do token — nunca da requisicao. Um tenant
 * nao gera remessa na conta de outro.
 */
@Slf4j
@RestController
@RequestMapping("/api/financeiro/boletos")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class BoletoController {

    private final BoletoService boletoService;
    private final ContaBancariaRepository contaRepository;

    /** Health do servico externo, para a tela avisar antes de o usuario tentar. */
    @GetMapping("/servico")
    public ResponseEntity<?> servico() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "disponivel", boletoService.servicoDisponivel(),
                "url", boletoService.urlServico())));
    }

    @GetMapping
    public ResponseEntity<?> listar(@AuthenticationPrincipal AuthenticatedUser user) {
        List<Boleto> lista = boletoService.listar(user.getEmpresaId());
        return ResponseEntity.ok(ApiResponse.success(lista.stream().map(this::resumo).toList()));
    }

    @PostMapping("/validar")
    public ResponseEntity<?> validar(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("bank") String bank,
            @RequestBody Map<String, Object> dados) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("valido", boletoService.validar(user.getEmpresaId(), bank, dados))));
        } catch (BoletoCnabClient.CnabIndisponivelException e) {
            return ResponseEntity.status(503).body(ApiResponse.error("CNAB_INDISPONIVEL", e.getMessage()));
        }
    }

    @PostMapping("/nosso-numero")
    public ResponseEntity<?> nossoNumero(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("bank") String bank,
            @RequestBody Map<String, Object> dados) {
        try {
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "nosso_numero", boletoService.nossoNumero(bank, dados))));
        } catch (BoletoCnabClient.CnabIndisponivelException e) {
            return ResponseEntity.status(503).body(ApiResponse.error("CNAB_INDISPONIVEL", e.getMessage()));
        }
    }

    /** Emite o boleto e arquiva o PDF no Mongo. */
    @PostMapping("/emitir")
    public ResponseEntity<?> emitir(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("bank") String bank,
            @RequestParam(value = "formato", defaultValue = "pdf") String formato,
            @RequestParam(value = "tituloId", required = false) Long tituloId,
            @RequestBody Map<String, Object> dados) {
        try {
            Boleto b = boletoService.emitir(user.getEmpresaId(), bank, dados, formato);
            if (tituloId != null) {
                b.setTituloId(tituloId);
            }
            return ResponseEntity.ok(ApiResponse.success(resumo(b)));
        } catch (BoletoCnabClient.CnabIndisponivelException e) {
            return ResponseEntity.status(503).body(ApiResponse.error("CNAB_INDISPONIVEL", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("DADOS_INVALIDOS", e.getMessage()));
        }
    }

    @GetMapping("/{id}/arquivo")
    public ResponseEntity<?> arquivo(@PathVariable Long id) {
        // documento vive no Mongo; a tela baixa via /api/documentos
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "mensagem", "Use GET /api/documentos/{documentoId}/conteudo",
                "documentoId", id)));
    }

    // ---------------- remessa ----------------

    @GetMapping("/remessas")
    public ResponseEntity<?> listarRemessas(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.success(
                boletoService.listarRemessas(user.getEmpresaId())));
    }

    /**
     * Gera a remessa. O corpo e a lista de itens (titulos a cobrar), no
     * formato que a tela ja tem: nosso_numero, valor, vencimento.
     *
     * Os dados do cedente (empresa, agencia, conta, carteira) vem da conta
     * bancaria escolhida; por isso sao obrigatorios e sao validados aqui.
     */
    @PostMapping("/remessas")
    public ResponseEntity<?> gerarRemessa(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("bank") String bank,
            @RequestParam(value = "tipo", defaultValue = "cnab240") String tipo,
            @RequestParam("contaBancariaId") Long contaBancariaId,
            @RequestParam(value = "sequencial", defaultValue = "1") String sequencial,
            @RequestBody List<Map<String, Object>> itens) {

        ContaBancaria conta;
        try {
            conta = contaRepository.findById(contaBancariaId)
                    .filter(c -> c.getEmpresaId().equals(user.getEmpresaId()))
                    .orElseThrow(() -> new DadosInvalidosException(
                            "Conta bancaria nao encontrada para esta empresa"));
        } catch (DadosInvalidosException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("DADOS_INVALIDOS", e.getMessage()));
        }

        if (itens == null || itens.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("SEM_ITENS", "Selecione ao menos um titulo para a remessa"));
        }

        List<RemessaItem> lista;
        try {
            // erro de dado do sacado e do cliente: precisa sair como 400 com o
            // nome do campo, nao como 500 generico do handler global
            validarSacados(itens);
            lista = montarItens(itens);
        } catch (DadosInvalidosException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("DADOS_INVALIDOS", e.getMessage()));
        }

        try {
            Remessa r = boletoService.gerarRemessa(
                    user.getEmpresaId(), bank, tipo, contaBancariaId, conta, sequencial, lista);
            return ResponseEntity.ok(ApiResponse.success(r));

        } catch (BoletoCnabClient.CnabIndisponivelException e) {
            return ResponseEntity.status(503).body(ApiResponse.error("CNAB_INDISPONIVEL", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("DADOS_INVALIDOS", e.getMessage()));
        }
    }

    private List<RemessaItem> montarItens(List<Map<String, Object>> itens) {
        List<RemessaItem> lista = new ArrayList<>();
        for (Map<String, Object> m : itens) {
            RemessaItem i = new RemessaItem();
            i.setNossoNumero(texto(m, "nosso_numero", "nossoNumero"));
            i.setValor(decimal(texto(m, "valor")));
            i.setVencimento(LocalDate.parse(texto(m, "vencimento", "data_vencimento").replace("/", "-")));
            Long tituloId = m.get("titulo_id") == null ? null : Long.valueOf(texto(m, "titulo_id"));
            i.setTituloId(tituloId);

            // CNAB exige os dados do sacado; a tela manda ou busca do cadastro
            i.setDocumentoSacado(opcional(m, "documento_sacado", "cpf_cnpj"));
            i.setNomeSacado(opcional(m, "nome_sacado", "nome"));
            i.setEnderecoSacado(opcional(m, "endereco_sacado", "logradouro"));
            i.setBairroSacado(opcional(m, "bairro_sacado", "bairro"));
            i.setCepSacado(opcional(m, "cep_sacado", "cep"));
            i.setCidadeSacado(opcional(m, "cidade_sacado", "cidade"));
            i.setUfSacado(opcional(m, "uf_sacado", "uf"));

            lista.add(i);
        }
        return lista;
    }

    private String opcional(Map<String, Object> m, String... chaves) {
        for (String c : chaves) {
            if (m.get(c) != null) return String.valueOf(m.get(c));
        }
        return null;
    }

    /**
     * O CNAB exige CPF/CNPJ, nome, endereco, bairro, CEP (8), cidade e UF (2)
     * do sacado. Sem qualquer um deles o BRCobranca recusa o registro e a
     * remessa inteira nao e gerada — por isso a checagem aqui, com o nome do
     * campo, em vez de deixar o 400 generico do servico externo chegar.
     */
    private void validarSacados(List<Map<String, Object>> itens) {
        List<String> problemas = new ArrayList<>();

        for (int idx = 0; idx < itens.size(); idx++) {
            Map<String, Object> m = itens.get(idx);
            int p = idx + 1;

            if (isVazio(opcional(m, "documento_sacado", "cpf_cnpj"))) {
                problemas.add("item " + p + ": documento_sacado (CPF/CNPJ) obrigatorio");
            }
            if (isVazio(opcional(m, "nome_sacado", "nome"))) {
                problemas.add("item " + p + ": nome_sacado obrigatorio");
            }
            if (isVazio(opcional(m, "endereco_sacado", "logradouro"))) {
                problemas.add("item " + p + ": endereco_sacado obrigatorio");
            }
            if (isVazio(opcional(m, "bairro_sacado", "bairro"))) {
                problemas.add("item " + p + ": bairro_sacado obrigatorio");
            }
            String cep = opcional(m, "cep_sacado", "cep");
            if (cep == null || cep.replaceAll("\\D", "").length() != 8) {
                problemas.add("item " + p + ": cep_sacado deve ter 8 digitos");
            }
            if (isVazio(opcional(m, "cidade_sacado", "cidade"))) {
                problemas.add("item " + p + ": cidade_sacado obrigatorio");
            }
            String uf = opcional(m, "uf_sacado", "uf");
            if (uf == null || uf.trim().length() != 2) {
                problemas.add("item " + p + ": uf_sacado deve ter 2 letras");
            }
        }

        if (!problemas.isEmpty()) {
            throw new DadosInvalidosException(
                    "O CNAB exige os dados do sacado de cada titulo:\n- "
                            + String.join("\n- ", problemas));
        }
    }

    private boolean isVazio(String v) {
        return v == null || v.isBlank();
    }

    private String texto(Map<String, Object> m, String... chaves) {
        for (String c : chaves) {
            if (m.get(c) != null) return String.valueOf(m.get(c));
        }
        throw new IllegalArgumentException("Campo obrigatorio ausente: " + chaves[0]);
    }

    private BigDecimal decimal(String v) {
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor invalido: " + v);
        }
    }

    // ---------------- retorno ----------------

    @GetMapping("/retornos")
    public ResponseEntity<?> listarRetornos(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.success(
                boletoService.listarRetornos(user.getEmpresaId())));
    }

    /** Upload do arquivo .RET do banco. */
    @PostMapping("/retornos")
    public ResponseEntity<?> processarRetorno(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("bank") String bank,
            @RequestParam(value = "tipo", defaultValue = "cnab240") String tipo,
            @RequestParam(value = "contaBancariaId", required = false) Long contaBancariaId,
            @RequestParam("arquivo") MultipartFile arquivo) throws java.io.IOException {

        if (arquivo.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("ARQUIVO_VAZIO", "Envie o arquivo de retorno do banco"));
        }

        try {
            RetornoBancario r = boletoService.processarRetorno(
                    user.getEmpresaId(), bank, tipo, contaBancariaId,
                    arquivo.getOriginalFilename(), arquivo.getBytes());

            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "id", r.getId(),
                    "status", r.getStatus(),
                    "registros", r.getQtdeRegistros(),
                    "baixados", r.getQtdeBaixados(),
                    "divergentes", r.getQtdeDivergentes(),
                    "documentoId", r.getDocumentoId() == null ? "" : r.getDocumentoId())));

        } catch (BoletoCnabClient.CnabIndisponivelException e) {
            return ResponseEntity.status(503).body(ApiResponse.error("CNAB_INDISPONIVEL", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("RETORNO_INVALIDO", e.getMessage()));
        }
    }

    private Map<String, Object> resumo(Boleto b) {
        return Map.of(
                "id", b.getId(),
                "banco", b.getBanco(),
                "nossoNumero", b.getNossoNumero() == null ? "" : b.getNossoNumero(),
                "valor", b.getValor(),
                "vencimento", b.getVencimento() == null ? "" : b.getVencimento().toString(),
                "status", b.getStatus(),
                "documentoId", b.getDocumentoId() == null ? "" : b.getDocumentoId());
    }
}
