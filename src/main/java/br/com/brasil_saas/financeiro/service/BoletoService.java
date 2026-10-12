package br.com.brasil_saas.financeiro.service;

import br.com.brasil_saas.core.repository.EmpresaRepository;
import br.com.brasil_saas.financeiro.model.Boleto;
import br.com.brasil_saas.financeiro.model.ContaBancaria;
import br.com.brasil_saas.financeiro.repository.ContaBancariaRepository;
import br.com.brasil_saas.financeiro.model.Remessa;
import br.com.brasil_saas.financeiro.model.RemessaItem;
import br.com.brasil_saas.financeiro.model.RetornoBancario;
import br.com.brasil_saas.financeiro.model.RetornoItem;
import br.com.brasil_saas.financeiro.repository.BoletoRepository;
import br.com.brasil_saas.financeiro.repository.RemessaItemRepository;
import br.com.brasil_saas.financeiro.repository.RemessaRepository;
import br.com.brasil_saas.financeiro.repository.RetornoBancarioRepository;
import br.com.brasil_saas.financeiro.repository.RetornoItemRepository;
import br.com.brasil_saas.shared.service.GenericoDocumentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Boletos, remessas e retornos CNAB do financeiro.
 *
 * O arquivo gerado (PDF do boleto, .rem da remessa, .RET processado) vai
 * para o MongoDB pela colecao "documentos", passando por staging em disco
 * antes — remessa e retorno sao documento de banco, perder um por falha de
 * rede nao e aceitavel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoletoService {

    private final BoletoCnabClient cnab;
    private final BoletoRepository boletoRepository;
    private final RemessaRepository remessaRepository;
    private final RemessaItemRepository remessaItemRepository;
    private final RetornoBancarioRepository retornoRepository;
    private final RetornoItemRepository retornoItemRepository;
    private final GenericoDocumentoService documentoService;
    private final EmpresaRepository empresaRepository;
    private final ContaBancariaRepository contaRepository;

    // ---------------- boleto ----------------

    public boolean validar(Long empresaId, String bank, Map<String, Object> dados) {
        return Boolean.parseBoolean(
                cnab.validarBoleto(bank, json(dados)).trim());
    }

    public String nossoNumero(String bank, Map<String, Object> dados) {
        return cnab.nossoNumero(bank, json(dados)).trim();
    }

    /**
     * Emite o boleto: chama o servico, guarda o PDF no Mongo e registra a
     * linha para conciliação futura.
     */
    @Transactional
    public Boleto emitir(Long empresaId, String bank, Map<String, Object> dados, String formato) {
        String json = json(dados);
        String nossoNumero = cnab.nossoNumero(bank, json);
        byte[] arquivo = cnab.gerarBoleto(bank, formato == null ? "pdf" : formato, json);

        String tipo = "boleto_" + (formato == null ? "pdf" : formato);
        var doc = documentoService.salvarBytes(
                empresaId, tipo, null,
                "boleto_" + nossoNumero + "." + (formato == null ? "pdf" : formato),
                "application/" + (formato == null ? "pdf" : formato),
                arquivo);

        Boleto b = new Boleto();
        b.setEmpresaId(empresaId);
        b.setBanco(bank);
        b.setNossoNumero(nossoNumero);
        b.setNossoNumeroChave(digito(nossoNumero));
        b.setDocumentoId(doc.getId());
        b.setDocumentoHash(doc.getHash());
        b.setValor(toDecimal(dados.get("valor")));
        b.setVencimento(vencimento(dados));
        b.setStatus("EMITIDO");
        return boletoRepository.save(b);
    }

    /**
     * O BRCobranca nomeia o vencimento `data_vencimento` no formato
     * "2026/12/31"; o ERP costuma mandar `vencimento` em ISO. Aceita os dois
     * para nao obrigar a tela a conhecer o nome de campo do servico externo.
     */
    private LocalDate vencimento(Map<String, Object> dados) {
        Object v = dados.containsKey("data_vencimento")
                ? dados.get("data_vencimento")
                : dados.get("vencimento");
        if (v == null) {
            throw new IllegalArgumentException("Informe o vencimento (data_vencimento ou vencimento)");
        }
        String s = String.valueOf(v);
        try {
            return LocalDate.parse(s.contains("/")
                    ? s.replace("/", "-")
                    : s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Vencimento invalido: " + s, e);
        }
    }

    public List<Boleto> listar(Long empresaId) {
        return boletoRepository.findByEmpresaIdOrderByCriadoEmDesc(empresaId);
    }

    // ---------------- remessa ----------------

    /**
     * Dados do sacado sao OBRIGATORIOS no CNAB: sem CPF, nome, endereco, CEP,
     * cidade e UF o BRCobranca rejeita o registro e a remessa inteira volta
     * com 400. Verificado no servico: "Documento sacado não pode estar em branco",
     * "Cep sacado deve ter 8 dígitos", "Uf sacado deve ter 2 dígitos".
     *
     * A tela popula a partir do cadastro ligado ao titulo; quando nao houver
     * titulo, ela manda os campos no proprio item.
     */
    private Map<String, Object> pagamentoDe(RemessaItem i) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("valor", i.getValor());
        m.put("data_vencimento", i.getVencimento() == null ? null : formatar(i.getVencimento()));
        m.put("nosso_numero", digito(i.getNossoNumero()));
        m.put("documento", digito(i.getNossoNumero()));
        m.put("documento_sacado", nz(i.getDocumentoSacado()));
        m.put("nome_sacado", nz(i.getNomeSacado()));
        m.put("endereco_sacado", nz(i.getEnderecoSacado()));
        m.put("bairro_sacado", nz(i.getBairroSacado()));
        m.put("cep_sacado", digito(nz(i.getCepSacado())));
        m.put("cidade_sacado", nz(i.getCidadeSacado()));
        m.put("uf_sacado", nz(i.getUfSacado()));
        return m;
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    /**
     * Gera a remessa com os titulos indicados e arquiva o .rem no Mongo.
     * Os itens ficam registrados para reconciliar com o retorno depois.
     *
     * Os dados do cedente (empresa, agencia, conta) vem da conta bancaria
     * escolhida — o servico CNAB exige esse cabecalho para montar o arquivo.
     */
    @Transactional
    public Remessa gerarRemessa(Long empresaId, String bank, String tipo,
                                Long contaBancariaId, ContaBancaria conta,
                                String sequencial, List<RemessaItem> itens) {
        String cedente = empresaNome(empresaId);

        List<Map<String, Object>> pagamentos = itens.stream()
                .map(this::pagamentoDe)
                .toList();

        Map<String, Object> cabecalho = new java.util.LinkedHashMap<>();
        cabecalho.put("empresa_mae", cedente == null ? "" : cedente);
        cabecalho.put("documento_cedente", conta == null ? "" : conta.getConta());
        cabecalho.put("agencia", conta == null ? "" : conta.getAgencia());
        cabecalho.put("conta_corrente", conta == null ? "" : conta.getConta());
        cabecalho.put("digito_conta", conta == null || conta.getDigito() == null ? "" : conta.getDigito());
        cabecalho.put("carteira", sequencial == null ? "1" : sequencial);
        cabecalho.put("sequencial_remessa", sequencial == null ? "1" : sequencial);
        cabecalho.put("pagamentos", pagamentos);

        String nome = "remessa_" + bank + "_" + System.currentTimeMillis() + ".rem";
        byte[] arquivo = cnab.gerarRemessa(bank, tipo, json(cabecalho), nome);

        var doc = documentoService.salvarBytes(
                empresaId, "remessa_cnab", null, nome, "application/octet-stream", arquivo);

        Remessa r = new Remessa();
        r.setEmpresaId(empresaId);
        r.setContaBancariaId(contaBancariaId);
        r.setBanco(bank);
        r.setTipo(tipo);
        r.setDocumentoId(doc.getId());
        r.setDocumentoHash(doc.getHash());
        r.setNomeArquivo(nome);
        r.setQtdeTitulos(itens.size());
        r.setValorTotal(itens.stream()
                .map(RemessaItem::getValor)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        r.setStatus("GERADA");
        r.setDataGeracao(LocalDate.now());
        r.setDataCredito(LocalDate.now());
        Remessa salva = remessaRepository.save(r);

        for (RemessaItem item : itens) {
            item.setRemessa(salva);
            remessaItemRepository.save(item);
        }

        remessaRepository.marcarEnviada(salva.getId());
        return salva;
    }

    public List<Remessa> listarRemessas(Long empresaId) {
        return remessaRepository.findByEmpresaIdOrderByCriadoEmDesc(empresaId);
    }

    // ---------------- retorno ----------------

    /**
     * Le o retorno do banco e registra linha a linha. Nao baixa titulo
     * automaticamente: a baixa real passa pelo fluxo de aprovacao do
     * financeiro, entao aqui fica so a leitura e a marcacao do que casou.
     */
    @Transactional
    public RetornoBancario processarRetorno(Long empresaId, String bank, String tipo,
                                            Long contaBancariaId, String nomeArquivo, byte[] arquivo) {

        String json = cnab.processarRetorno(bank, tipo, arquivo, nomeArquivo);
        List<Map<String, Object>> linhas = parse(json);

        var doc = documentoService.salvarBytes(
                empresaId, "retorno_cnab", null,
                nomeArquivo == null ? "retorno.RET" : nomeArquivo,
                "application/octet-stream", json.getBytes(StandardCharsets.UTF_8));

        RetornoBancario r = new RetornoBancario();
        r.setEmpresaId(empresaId);
        r.setContaBancariaId(contaBancariaId);
        r.setBanco(bank);
        r.setTipo(tipo);
        r.setNomeArquivo(nomeArquivo);
        r.setQtdeRegistros(linhas.size());
        r.setDocumentoId(doc.getId());
        r.setResumoJson(json);
        r.setStatus(linhas.isEmpty() ? "ERRO" : "PROCESSADO");
        RetornoBancario salvo = retornoRepository.save(r);

        int casados = 0;
        for (Map<String, Object> l : linhas) {
            RetornoItem item = new RetornoItem();
            item.setRetorno(salvo);
            item.setCodigoRegistro(str(l.get("codigo_registro")));
            item.setCodigoOcorrencia(str(l.get("codigo_ocorrencia")));
            item.setNossoNumero(str(l.get("nosso_numero")));
            item.setValorTitulo(toDecimal(l.get("valor_titulo")));
            item.setValorRecebido(toDecimal(l.get("valor_recebido")));
            item.setDataOcorrencia(parseData(l.get("data_ocorrencia")));

            // o banco devolve nosso_numero so com digitos; a chave normalizada
            // do boleto e o que permite casar de fato
            String chaveNn = digito(item.getNossoNumero());
            var boleto = chaveNn.isBlank()
                    ? java.util.Optional.<Boleto>empty()
                    : boletoRepository.findByEmpresaIdAndBancoAndNossoNumeroChave(
                            empresaId, bank, chaveNn);
            if (boleto.isPresent()) {
                item.setBoletoId(boleto.get().getId());
                item.setAplicado(true);
                casados++;
            }
            retornoItemRepository.save(item);
        }

        salvo.setQtdeBaixados(casados);
        salvo.setQtdeDivergentes(linhas.size() - casados);
        salvo.setStatus(casados == 0 ? "ERRO" : (casados < linhas.size() ? "PARCIAL" : "PROCESSADO"));
        return retornoRepository.save(salvo);
    }

    public List<RetornoBancario> listarRetornos(Long empresaId) {
        return retornoRepository.findByEmpresaIdOrderByCriadoEmDesc(empresaId);
    }

    // ---------------- apoio ----------------

    public boolean servicoDisponivel() {
        return cnab.isConfigurado() && cnab.estaVivo();
    }

    public String urlServico() {
        return cnab.getBaseUrl();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parse(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference
                            <List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Retorno invalido do servico CNAB: " + e.getMessage(), e);
        }
    }

    private String json(Object dados) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dados);
        } catch (Exception e) {
            throw new IllegalArgumentException("Nao foi possivel serializar os dados do boleto", e);
        }
    }

    private BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal d) return d;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** CNAB espera numero sem barra/letra. */
    private String empresaNome(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .map(e -> e.getRazaoSocial() == null ? e.getNomeFantasia() : e.getRazaoSocial())
                .orElse("");
    }

    private String digito(String nossoNumero) {
        if (nossoNumero == null) return "";
        return nossoNumero.replaceAll("[^0-9]", "");
    }

    /** CNAB usa "yyyy/MM/dd". */
    private String formatar(LocalDate d) {
        return d.getYear() + "/" + String.format("%02d", d.getMonthValue())
                + "/" + String.format("%02d", d.getDayOfMonth());
    }

    private LocalDate parseData(Object v) {
        if (v == null) return null;
        try {
            String s = String.valueOf(v);
            if (s.length() > 10) s = s.substring(0, 10);
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
