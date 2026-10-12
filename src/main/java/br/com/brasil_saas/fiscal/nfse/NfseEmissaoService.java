package br.com.brasil_saas.fiscal.nfse;

import br.com.brasil_saas.cadastro.model.Servico;
import br.com.brasil_saas.cadastro.repository.ServicoRepository;
import br.com.brasil_saas.core.model.Empresa;
import br.com.brasil_saas.core.repository.EmpresaRepository;
import br.com.brasil_saas.fiscal.model.Nfse;
import br.com.brasil_saas.fiscal.model.NfseItem;
import br.com.brasil_saas.fiscal.model.NfseRetorno;
import br.com.brasil_saas.fiscal.nfse.NfseEmissaoDtos.Emitir;
import br.com.brasil_saas.fiscal.nfse.NfseEmissaoDtos.Resultado;
import br.com.brasil_saas.fiscal.repository.NfseItemRepository;
import br.com.brasil_saas.fiscal.repository.NfseRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Emissao de NFS-e municipal integrada ao ERP. O provedor é escolhido pelo município da empresa.
 *
 * <p>Encadeamento de uma emissao:
 * <ol>
 *   <li>o codigo municipal vem do <b>cadastro de servico</b> — nao da tela, para
 *       nao mandar codigo que nao bate com o cadastro;</li>
 *   <li>a API Java assina e chama a prefeitura (mTLS, SOAP, XMLDSig);</li>
 *   <li>o registro fiscal vai para {@code bc_fis_nfse} com os identificadores
 *       que <b>somente</b> a prefeitura devolve — numero e codigo de
 *       verificacao, sem os quais nao ha como cancelar;</li>
 *   <li>o XML assinado vai para o MongoDB com prazo de 5 anos.</li>
 * </ol>
 *
 * <p>Se o arquivamento falhar, a emissao <b>nao e desfeita</b>: a nota existe
 * na prefeitura. Desfazer levaria o usuario a tentar de novo e criar
 * duplicidade. O registro fiscal e gravado e a perda do arquivo vira alarme.
 */
@Slf4j
@Service
public class NfseEmissaoService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final String IBGE_RONDONOPOLIS = "5107602";
    private static final String IBGE_SAO_PAULO = "3550308";

    private final ServicoRepository servicoRepository;
    private final EmpresaRepository empresaRepository;
    private final NfseRepository nfseRepository;
    private final NfseItemRepository nfseItemRepository;
    private final NfseArquivoService arquivoService;
    private final NfseRetornoService retornoService;
    private final NfsePdfService pdfService;
    private final RestClient httpSp;
    private final RestClient httpRondonopolis;
    private final RestClient httpNacional;

    private final String inscricaoMunicipalPadrao;
    private final String serieRpsPadrao;

    public NfseEmissaoService(
            ServicoRepository servicoRepository,
            EmpresaRepository empresaRepository,
            NfseRepository nfseRepository,
            NfseItemRepository nfseItemRepository,
            NfseArquivoService arquivoService,
            NfseRetornoService retornoService,
            NfsePdfService pdfService,
            @Value("${brasil-saas.fiscal.nfse.url:http://127.0.0.1:4567/api/nfse-sp}") String url,
            @Value("${brasil-saas.fiscal.nfse.inscricao-municipal:2130033}") String inscricaoMunicipal,
            @Value("${brasil-saas.fiscal.nfse.serie-rps:BC}") String serieRps,
            @Value("${brasil-saas.fiscal.nfse.timeout-ms:60000}") long timeoutMs,
            @Value("${brasil-saas.fiscal.nfse.nacional-url:http://127.0.0.1:4580/api/nfse-nacional}") String urlNacional) {

        this.servicoRepository = servicoRepository;
        this.empresaRepository = empresaRepository;
        this.nfseRepository = nfseRepository;
        this.nfseItemRepository = nfseItemRepository;
        this.arquivoService = arquivoService;
        this.retornoService = retornoService;
        this.pdfService = pdfService;
        this.inscricaoMunicipalPadrao = inscricaoMunicipal;
        this.serieRpsPadrao = serieRps;

        // A prefeitura leva cerca de 1s, mas em horario de pico demora.
        // 60s evita dar timeout no meio de uma emissao e deixar o usuario sem
        // saber se a nota saiu.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeoutMs);
        factory.setReadTimeout((int) timeoutMs);
        this.httpSp = RestClient.builder().baseUrl(url).requestFactory(factory).build();

        String rondonopolisUrl = System.getProperty("brasil-saas.fiscal.nfse.rondonopolis-url",
                "http://127.0.0.1:4570/api/nfse-sp");
        this.httpRondonopolis = RestClient.builder().baseUrl(rondonopolisUrl)
                .requestFactory(factory).build();
        this.httpNacional = RestClient.builder().baseUrl(urlNacional)
                .requestFactory(factory).build();
    }

    /**
     * Emissao de NFS-e.
     *
     * <p><b>Nao e transacional de proposito.</b> A prefeitura e um sistema
     * externo: se a gravacao local viesse depois da chamada e falhasse, a nota
     * estaria emitida sem registro aqui, e o usuario — vendo erro — tentaria de
     *novo, criando duplicidade na prefeitura. Isso aconteceu de verdade em
     * 25/09/2026: a NF-e 13 foi emitida e o INSERT do item falhou.
     *
     * <p>A ordem correta e:
     * <ol>
     *   <li>grava a intencao com status {@code EMITINDO} e a chave do RPS
     *       (IM + serie + numero) — info que ja basta para cancelar, como a
     *       prefeitura aceita sem o codigo de verificacao;</li>
     *   <li>chama a prefeitura;</li>
     *   <li>atualiza com os identificadores que so ela devolve, status
     *       {@code EMITIDA};</li>
     *   <li>arquiva XML e PDF.</li>
     * </ol>
     * Se a chamada falhar, o registro fica como {@code FALHA_EMISSAO} e pode
     * ser consultado. Se a atualizacao falhar, o registro EMITINDO com a chave
     * do RPS ainda permite recuperar e cancelar a nota.
     */
    public Resultado emitir(Emitir req) {
        Empresa empresa = empresaRepository.findById(req.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa " + req.getEmpresaId() + " nao encontrada."));
        RestClient http = clienteNfse(empresa);

        Servico servico = servicoRepository.findById(req.getServicoId())
                .orElseThrow(() -> new BusinessException(
                        "Servico " + req.getServicoId() + " nao encontrado no cadastro."));
        NfseEmissaoDtos.exigirCodigoMunicipal(servico);
        boolean nacional = !IBGE_RONDONOPOLIS.equals(empresa.getCodigoIbge())
                && !IBGE_SAO_PAULO.equals(empresa.getCodigoIbge());
        if (nacional && (servico.getCodigoTributacaoNacional() == null
                || !servico.getCodigoTributacaoNacional().matches("\\d{6}"))) {
            throw new BusinessException("O servico '" + servico.getNome()
                    + "' precisa de codigo de tributacao nacional com 6 digitos para emissao pela SEFIN Nacional.");
        }

        String inscricaoMunicipal = empresa.getInscricaoMunicipal() != null
                && !empresa.getInscricaoMunicipal().isBlank()
                ? empresa.getInscricaoMunicipal()
                : inscricaoMunicipalPadrao;
        String serie = req.getSerieRps() == null || req.getSerieRps().isBlank()
                ? serieRpsPadrao : req.getSerieRps();
        String dataEmissao = req.getDataEmissao() == null || req.getDataEmissao().isBlank()
                ? LocalDate.now().format(ISO) : req.getDataEmissao();
        String tributacao = req.getTributacaoRps() == null || req.getTributacaoRps().isBlank()
                ? "T" : req.getTributacaoRps();

        BigDecimal base = valor(req.getValorServicos(), "valor do servico");
        BigDecimal deducoes = valor(req.getValorDeducoes(), "valor das deducoes");
        BigDecimal aliquota = req.getAliquota() != null
                ? req.getAliquota()
                : valor(servico.getAliquotaIss(), "aliquota do servico");
        BigDecimal valorIss = base.subtract(deducoes)
                .multiply(aliquota)
                .setScale(2, RoundingMode.HALF_UP);

        // ---- 1) chamada a API Java ------------------------------------
        Map<String, Object> corpo = new LinkedHashMap<>();
        // O provedor de Rondonópolis aceita o prestador no próprio contrato;
        // enviamos os dados da empresa para não deixar CNPJ/IM fixos no microserviço.
        corpo.put("inscricaoMunicipalPrestador", inscricaoMunicipal);
        corpo.put("cnpjPrestador", somenteDigitos(empresa.getCnpj()));
        corpo.put("unidadeGestora", System.getProperty("brasil-saas.fiscal.nfse.rondonopolis-unidade-gestora",
                "03347101000121"));
        corpo.put("imPrestador", inscricaoMunicipal);
        // Contrato comum do adapter NFS-e Nacional. Os dados estruturados vêm
        // do cadastro da empresa/serviço; a tela não informa tributação fiscal.
        corpo.put("codigoIbge", empresa.getCodigoIbge());
        corpo.put("nomePrestador", empresa.getRazaoSocial());
        corpo.put("telefonePrestador", empresa.getTelefone());
        corpo.put("logradouroPrestador", empresa.getEndereco());
        corpo.put("numeroPrestador", empresa.getNumero());
        corpo.put("complementoPrestador", empresa.getComplemento());
        corpo.put("bairroPrestador", empresa.getBairro());
        corpo.put("cepPrestador", empresa.getCep());
        corpo.put("codigoTributacaoNacional", servico.getCodigoTributacaoNacional());
        corpo.put("codigoNbs", servico.getNbs());
        corpo.put("codigoMunicipioPrestacao", empresa.getCodigoIbge());

        corpo.put("serieRps", serie);
        corpo.put("numeroRps", String.valueOf(req.getNumeroRps()));
        corpo.put("dataEmissao", dataEmissao);
        corpo.put("tributacaoRps", tributacao);
        corpo.put("codigoServico", servico.getCodigoTributacaoMunicipal());
        corpo.put("aliquotaServicos", aliquota.setScale(4, RoundingMode.HALF_UP).toPlainString());
        corpo.put("valorServicos", base.setScale(2, RoundingMode.HALF_UP).toPlainString());
        corpo.put("valorDeducoes", deducoes.setScale(2, RoundingMode.HALF_UP).toPlainString());
        corpo.put("issRetido", Boolean.TRUE.equals(req.getIssRetido()));
        corpo.put("discriminacao", req.getDiscriminacao() == null || req.getDiscriminacao().isBlank()
                ? servico.getDescricao() : req.getDiscriminacao());

        if (somenteDigitos(req.getCpfCnpjTomador()).length() == 11) {
            corpo.put("cpfTomador", somenteDigitos(req.getCpfCnpjTomador()));
        } else {
            corpo.put("cnpjTomador", somenteDigitos(req.getCpfCnpjTomador()));
        }
        if (req.getInscricaoMunicipalTomador() != null) {
            corpo.put("inscricaoMunicipalTomador", req.getInscricaoMunicipalTomador());
        }
        if (req.getRazaoSocialTomador() != null) {
            corpo.put("razaoSocialTomador", req.getRazaoSocialTomador());
        }
        if (req.getEmailTomador() != null) {
            corpo.put("emailTomador", req.getEmailTomador());
        }

        Map<String, Object> resposta;
        Integer httpStatus;
        Long duracaoMs;
        RuntimeException falha = null;
        // ---- 1) registra a intencao ANTES de chamar a prefeitura ----
        Nfse nfse = new Nfse();
        nfse.setEmpresaId(req.getEmpresaId());
        nfse.setClienteId(req.getClienteId());
        nfse.setPessoaId(req.getPessoaId());
        nfse.setServicoId(servico.getId());
        nfse.setSerieRps(serie);
        nfse.setNumeroRps(String.valueOf(req.getNumeroRps()));
        nfse.setLc116Codigo(servico.getLc116Codigo());
        nfse.setCodigoTributacaoMunicipal(servico.getCodigoTributacaoMunicipal());
        nfse.setDataEmissao(LocalDateTime.now());
        nfse.setStatus("EMITINDO");
        nfse.setBaseCalculo(base.setScale(2, RoundingMode.HALF_UP));
        nfse.setAliquotaIss(aliquota.setScale(4, RoundingMode.HALF_UP));
        nfse.setValorIss(valorIss);
        nfse.setValorTotal(base.setScale(2, RoundingMode.HALF_UP));
        nfse.setTipoOperacao("S");
        Nfse salva = nfseRepository.saveAndFlush(nfse);
        long nfseId = salva.getId();

        long inicio = System.nanoTime();
        try {
            var respostaBruta = http.post()
                    .uri("/emitir-rps")
                    .body(corpo)
                    .retrieve()
                    .toEntity(Map.class);
            resposta = respostaBruta.getBody();
            httpStatus = respostaBruta.getStatusCode().value();
        } catch (RuntimeException e) {
            falha = e;
            resposta = null;
            httpStatus = statusDaExcecao(e);
        }
        duracaoMs = (System.nanoTime() - inicio) / 1_000_000;

        // Grava o retorno ANTES de qualquer traducao para excecao. E o unico
        // momento em que a resposta da prefeitura ainda esta em maos: depois
        // dela virar BusinessException, a causa se perde — e era o que a pessoa
        // lia na tela antes de fechar.
        retornoService.registrar(req.getEmpresaId(), salva,
                NfseRetorno.OPERACAO_EMISSAO, resposta, httpStatus, duracaoMs, falha);

        if (falha != null) {
            marcarFalha(nfseId, "A prefeitura recusou a emissao: " + mensagemDoErro(falha));
            throw new BusinessException(
                    "A prefeitura recusou a emissao: " + mensagemDoErro(falha)
                            + " Nenhuma nota foi emitida — pode tentar de novo com os mesmos dados.", "NFS_E_ERRO");
        }

        // A resposta so e lida se estiver no contrato canonico. As duas
        // implementacoes falavam linguas diferentes e o fallback ligava com o
        // ERP achando que a nota nao tinha saido; o emissor Ruby foi arrumado
        // para falar o mesmo formato, entao aqui nao se tolera variation —
        // leitura que aceita dois formatos e o mesmo bug esperando a proxima
        // implementacao.
        String respostaBruta = String.valueOf(resposta);
        resposta = RespostaNfse.ler(resposta);
        Boolean confirmado = RespostaNfse.confirmado(resposta);

        if (Boolean.FALSE.equals(confirmado)) {
            // Recusa de verdade: a prefeitura respondeu que nao.
            String motivo = RespostaNfse.motivo(resposta);
            marcarFalha(nfseId, "A prefeitura recusou a emissao: " + motivo);
            throw new BusinessException("A prefeitura recusou a emissao: " + motivo
                    + " Nenhuma nota foi emitida — pode tentar de novo com os mesmos dados.", "NFS_E_ERRO");
        }

        if (confirmado == null) {
            // Terceiro estado, e o que faltava: nao deu para saber.
            //
            // Dizer "nao emitiu" aqui e mentira, e a pessoa reemite — que e como
            // as notas 29 e 30 viraram duplicata possivel. Dizer que nao deu para
            // confirmar, guardar os identificadores que vieram e mandar conferir
            // deixa a decisao com quem tem como ver.
            String ids = RespostaNfse.identificadores(resposta);
            marcarFalha(nfseId, "Resposta da prefeitura fora do contrato: " + respostaBruta);
            throw new BusinessException(
                    "A prefeitura respondeu em formato desconhecido, entao nao da para afirmar se a "
                            + "nota foi emitida." + (ids == null ? "" : " O que veio: " + ids + ".")
                            + " CONFIRA NA PREFEITURA antes de emitir de novo — emitir duas vezes cria "
                            + "nota duplicada.", "NFS_E_CONFIRMAR");
        }

        // ---- 2) atualiza com o que so a prefeitura devolve ------------
        salva.setNumero(paraLong(resposta.get("numero_nfse")));
        salva.setCodigoVerificacao(texto(resposta.get("codigo_verificacao")));
        salva.setChaveNotaNacional(nacional ? texto(resposta.get("chave_nota_nacional")) : null);
        salva.setIdDps(nacional ? texto(resposta.get("id_dps")) : null);
        salva.setProtocoloNacional(nacional ? texto(resposta.get("protocolo")) : null);
        salva.setStatus("EMITIDA");
        salva = nfseRepository.saveAndFlush(salva);

        NfseItem item = new NfseItem();
        item.setNfse(salva);
        item.setServicoId(servico.getId());
        item.setDescricao(req.getDiscriminacao() == null ? servico.getNome() : req.getDiscriminacao());
        item.setQuantidade(BigDecimal.ONE);
        item.setValorUnitario(base.setScale(2, RoundingMode.HALF_UP));
        item.setValorTotal(base.setScale(2, RoundingMode.HALF_UP));
        nfseItemRepository.save(item);

        // ---- 3) arquivamento --------------------------------------------
        // Falha aqui nao desfaz a emissao: a nota ja foi emitida.
        String xmlId = arquivoService.guardarXml(salva, xmlDaApi(resposta));
        salva.setXmlDocumentoId(xmlId);
        byte[] pdf = xmlId != null ? pdfService.gerar(salva, arquivoService.lerXml(salva)) : null;
        salva.setPdfDocumentoId(pdf != null ? arquivoService.guardarPdf(salva, pdf) : null);
        nfseRepository.saveAndFlush(salva);

        log.info("NFS-e {} emitida para o servico {} (codigo municipal {}). XML arquivado: {}.",
                salva.getNumero(), servico.getCodigo(), servico.getCodigoTributacaoMunicipal(),
                xmlId != null ? "sim" : "NAO — ver log");

        return new Resultado(salva.getId(), String.valueOf(salva.getNumero()),
                salva.getCodigoVerificacao(), salva.getChaveNotaNacional(),
                inscricaoMunicipal, salva.getStatus(), xmlId, salva.getPdfDocumentoId(),
                alertas(resposta));
    }

    /**
     * Seleciona o emissor municipal conforme o cadastro da empresa.
     *
     * <p>São Paulo continua no contrato da API 4567. Rondonópolis usa a
     * implementação Java AGILIBlue em 4570, que já foi construída e testada
     * separadamente no repositório. O ERP passa a escolher o provedor pelo
     * município, sem duplicar a tela, persistência ou fluxo fiscal.
     *
     * <p>5107602 é o código IBGE oficial de Rondonópolis. Não inferimos o
     * município pelo texto do endereço: o código IBGE é a fonte estruturada.
     */
    private RestClient clienteNfse(Empresa empresa) {
        if (IBGE_RONDONOPOLIS.equals(empresa.getCodigoIbge())) {
            return httpRondonopolis;
        }
        if (IBGE_SAO_PAULO.equals(empresa.getCodigoIbge())) {
            return httpSp;
        }
        return httpNacional;
    }


    /**
     * Marca o registro como falha de emissao.
     *
     * <p>Se nem isso gravar, o registro fica como {@code EMITINDO}, que tambem
     * serve: a chave do RPS permite descobrir o que aconteceu na prefeitura.
     */
    private void marcarFalha(Long nfseId, String motivo) {
        try {
            nfseRepository.findById(nfseId).ifPresent(n -> {
                n.setStatus("FALHA_EMISSAO");
                nfseRepository.save(n);
            });
        } catch (RuntimeException e) {
            log.error("Nao consegui marcar a NFS-e {} como falha de emissao. Ela permanece como "
                    + "EMITINDO e precisa de conferencia manual. Motivo original: {}",
                    nfseId, motivo, e);
        }
    }

    /**
     * Cancela a nota pelo que a prefeitura devolveu na emissao.
     *
     * <p>E por isso que o registro fiscal guarda {@code numero} e
     * {@code codigo_verificacao}: a prefeitura nao tem consulta por chave
     * para este caso, e sem eles a nota nao tem como ser cancelada depois.
     */
    @Transactional
    public Resultado cancelar(Long nfseId, Long empresaId) {
        Nfse nfse = nfseRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(nfseId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("NFS-e " + nfseId + " nao encontrada."));

        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa " + empresaId + " nao encontrada."));
        RestClient http = clienteNfse(empresa);

        if ("CANCELADA".equals(nfse.getStatus())) {
            throw new BusinessException("A NFS-e " + nfse.getNumero() + " ja esta cancelada.");
        }
        if (nfse.getNumero() == null) {
            throw new BusinessException(
                    "O registro " + nfseId + " ficou sem numero de NF-e, o que significa que a "
                            + "prefeitura nao confirmou a emissao. Confira o status antes de cancelar.");
        }

        boolean nacional = !IBGE_RONDONOPOLIS.equals(empresa.getCodigoIbge())
                && !IBGE_SAO_PAULO.equals(empresa.getCodigoIbge());

        // O padrao nacional cancela pela chave de acesso e registra um evento
        // assinado. SP/Rondonopolis continuam no contrato municipal legado.
        if (nacional) {
            Map<String, Object> resposta;
            Integer httpStatus;
            Long duracaoMs;
            RuntimeException falha = null;
            long inicio = System.nanoTime();
            try {
                var respostaBruta = http.post()
                        .uri("/cancelar")
                        .body(Map.of(
                                "chaveNfse", Optional.ofNullable(nfse.getChaveNotaNacional())
                                        .orElseThrow(() -> new BusinessException(
                                                "NFS-e Nacional sem chave de acesso; nao e seguro cancelar.")),
                                "cnpjAutor", somenteDigitos(empresa.getCnpj()),
                                "motivo", "Cancelamento solicitado pelo contribuinte"))
                        .retrieve()
                        .toEntity(Map.class);
                resposta = respostaBruta.getBody();
                httpStatus = respostaBruta.getStatusCode().value();
            } catch (RuntimeException e) {
                falha = e;
                resposta = null;
                httpStatus = statusDaExcecao(e);
            }
            duracaoMs = (System.nanoTime() - inicio) / 1_000_000;

            retornoService.registrar(nfse.getEmpresaId(), nfse,
                    NfseRetorno.OPERACAO_CANCELAMENTO, resposta, httpStatus, duracaoMs, falha);

            if (falha != null) {
                throw new BusinessException(
                        "A SEFIN Nacional recusou o cancelamento: " + mensagemDoErro(falha), "NFS_E_ERRO");
            }

            resposta = RespostaNfse.ler(resposta);
            String chaveRetorno = resposta == null ? null : texto(resposta.get("chave_nota_nacional"));
            if (resposta != null && chaveRetorno != null && !chaveRetorno.isBlank()) {
                nfse.setChaveNotaNacional(chaveRetorno);
                String idDps = texto(resposta.get("id_dps"));
                String protocolo = texto(resposta.get("protocolo"));
                if (idDps != null) nfse.setIdDps(idDps);
                if (protocolo != null) nfse.setProtocoloNacional(protocolo);
                nfseRepository.save(nfse);
            }
            Boolean confirmado = RespostaNfse.confirmado(resposta);
            if (Boolean.FALSE.equals(confirmado)) {
                throw new BusinessException("A SEFIN Nacional recusou o cancelamento: "
                        + RespostaNfse.motivo(resposta), "NFS_E_ERRO");
            }
            if (confirmado == null) {
                throw new BusinessException(
                        "A SEFIN Nacional respondeu em formato desconhecido. Confira a nota antes "
                                + "de tentar cancelar novamente.", "NFS_E_CONFIRMAR");
            }

            nfse.setStatus("CANCELADA");
            nfseRepository.save(nfse);
            return new Resultado(nfse.getId(), String.valueOf(nfse.getNumero()),
                    nfse.getCodigoVerificacao(), nfse.getChaveNotaNacional(),
                    empresa.getInscricaoMunicipal(), nfse.getStatus(),
                    nfse.getXmlDocumentoId(), nfse.getPdfDocumentoId(), alertas(resposta));
        }

        // O codigo de verificacao e 0-1 no XSD: a prefeitura aceita o
        // cancelamento so com IM + numero. Isso importa porque e o que permite
        // recuperar uma nota cuja gravacao local falhou depois da emissao.
        Map<String, Object> detalhe = new LinkedHashMap<>();
        detalhe.put("inscricaoPrestador", empresa.getInscricaoMunicipal() != null
                && !empresa.getInscricaoMunicipal().isBlank()
                ? empresa.getInscricaoMunicipal() : inscricaoMunicipalPadrao);
        detalhe.put("numeroNfe", String.valueOf(nfse.getNumero()));
        if (nfse.getCodigoVerificacao() != null && !nfse.getCodigoVerificacao().isBlank()) {
            detalhe.put("codigoVerificacao", nfse.getCodigoVerificacao());
        } else {
            log.warn("NFS-e {} sem codigo de verificacao. Tentando cancelar so com IM e numero.",
                    nfse.getNumero());
        }
        if (nfse.getChaveNotaNacional() != null) {
            detalhe.put("chaveNotaNacional", nfse.getChaveNotaNacional());
        }

        Map<String, Object> resposta;
        Integer httpStatus;
        Long duracaoMs;
        RuntimeException falha = null;
        long inicio = System.nanoTime();
        try {
            var respostaBruta = http.post()
                    .uri("/cancelar")
                    .body(Map.of("detalhes", List.of(detalhe)))
                    .retrieve()
                    .toEntity(Map.class);
            resposta = respostaBruta.getBody();
            httpStatus = respostaBruta.getStatusCode().value();
        } catch (RuntimeException e) {
            falha = e;
            resposta = null;
            httpStatus = statusDaExcecao(e);
        }
        duracaoMs = (System.nanoTime() - inicio) / 1_000_000;

        // Mesmo motivo da emissao: grava antes de traduzir para excecao, para o
        // "a prefeitura recusou o cancelamento" ficar registrado alem da tela.
        retornoService.registrar(nfse.getEmpresaId(), nfse,
                NfseRetorno.OPERACAO_CANCELAMENTO, resposta, httpStatus, duracaoMs, falha);

        if (falha != null) {
            throw new BusinessException(
                    "A prefeitura recusou o cancelamento: " + mensagemDoErro(falha), "NFS_E_ERRO");
        }

        resposta = RespostaNfse.ler(resposta);
        Boolean confirmado = RespostaNfse.confirmado(resposta);

        if (Boolean.FALSE.equals(confirmado)) {
            throw new BusinessException("A prefeitura recusou o cancelamento: "
                    + RespostaNfse.motivo(resposta), "NFS_E_ERRO");
        }

        if (confirmado == null) {
            throw new BusinessException(
                    "A prefeitura respondeu em formato desconhecido, entao nao da para afirmar se o "
                            + "cancelamento foi feito. CONFIRA NA PREFEITURA antes de tentar de novo.",
                    "NFS_E_CONFIRMAR");
        }

        nfse.setStatus("CANCELADA");
        nfseRepository.save(nfse);
        log.info("NFS-e {} cancelada.", nfse.getNumero());

        return new Resultado(nfse.getId(), String.valueOf(nfse.getNumero()),
                nfse.getCodigoVerificacao(), nfse.getChaveNotaNacional(), null,
                nfse.getStatus(), nfse.getXmlDocumentoId(), nfse.getPdfDocumentoId(),
                alertas(resposta));
    }

    // ------------------------------------------------------------------

    private List<String> alertas(Map<String, Object> resposta) {
        List<String> saida = new ArrayList<>();
        Object lista = resposta == null ? null : resposta.get("alertas");
        if (lista instanceof List<?> itens) {
            for (Object item : itens) {
                saida.add(String.valueOf(item));
            }
        }
        return saida;
    }

    /**
     * Extrai o XML assinado que a API devolveu.
     *
     * <p>A API tem o proprio Mongo; o ERP tem outro. Se o XML nao vier na
     * resposta, ele se perde quando a API e desligada — e ele e a prova da
     * nota, com prazo legal de 5 anos.
     */
    private String xmlDaApi(Map<String, Object> resposta) {
        if (resposta == null) {
            return null;
        }
        Object xml = resposta.get("xml_assinado");
        if (!(xml instanceof String) || ((String) xml).isBlank()) {
            Object nacional = resposta.get("xml_nacional");
            if (nacional instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        if (xml instanceof String s && !s.isBlank()) {
            return s;
        }
        log.error("A API nao devolveu o XML assinado da nota. O arquivo nao sera arquivado e a "
                + "prova fiscal fica apenas no banco da API.");
        return null;
    }

    private BigDecimal valor(BigDecimal v, String campo) {
        if (v == null) {
            throw new BusinessException("Informe o " + campo + ".");
        }
        return v;
    }

    private String somenteDigitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private Long paraLong(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String texto(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String mensagemDoErro(RuntimeException e) {
        String m = e.getMessage();
        if (m == null) {
            return "sem detalhe";
        }
        int i = m.indexOf("\"erro\"");
        if (i >= 0) {
            int fim = m.indexOf("}", i);
            if (fim > i) {
                return m.substring(i, fim + 1);
            }
        }
        return m.length() > 400 ? m.substring(0, 400) + "..." : m;
    }

    /**
     * Extrai o status HTTP de uma falha do RestClient, quando ela carrega um.
     *
     * <p>Sem isso, o registro da recusa ficaria com {@code http_status} nulo, e a
     * distinção entre "a prefeitura recusou" e "a chamada não chegou" se
     * perderia justamente no registro que existe para preservar essa distinção.
     * O 422 é o que traduz a recusa; a ausência de status é o resto.
     */
    private Integer statusDaExcecao(RuntimeException e) {
        Throwable atual = e;
        while (atual != null) {
            if (atual instanceof RestClientResponseException http) {
                return http.getStatusCode().value();
            }
            atual = atual.getCause();
        }
        return null;
    }
}
