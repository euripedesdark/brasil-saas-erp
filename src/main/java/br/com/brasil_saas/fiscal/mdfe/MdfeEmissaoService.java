package br.com.brasil_saas.fiscal.mdfe;

import com.fincatto.documentofiscal.DFAmbiente;
import com.fincatto.documentofiscal.DFUnidadeFederativa;
import com.fincatto.documentofiscal.mdfe3.classes.consultastatusservico.MDFeConsStatServRet;
import com.fincatto.documentofiscal.mdfe3.webservices.WSFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MDF-e pela fincatto {@code documentofiscal}, contra a SVRS.
 *
 * <h3>Por que SVRS e nao o portal de SP</h3>
 * A classe {@code MDFAutorizador3} da lib tem <b>um valor so no enum, RS</b>, e
 * o fallback e silencioso: qualquer UF fora do enum cai em RS. Isso e o
 * comportamento certo. MDF-e e nacional, e o Virtual Ambiente Nacional
 * Autorizador autoriza para o pais inteiro. Emitente de SP emite pelo SVRS;
 * nao existe URL de MDF-e de SP para escolher.
 *
 * <h3>Ordem de chamada, e por que nesta ordem</h3>
 * Status antes de qualquer emissao. Se a SVRS estiver fora do ar, a emissao
 * falha com timeout de 30 s e o operador conclui que o problema e o MDF-e
 * dele. O status responde em milissegundos.
 *
 * <p>Diferente da swconsultoria, aqui o certificado e exigido <b>no
 * construtor</b> do {@code WSFacade}, e nao na chamada. Sem ele a excecao sai
 * de dentro do Apache HttpClient e nao diz nada sobre certificado. Por isso a
 * verificacao e feita antes de construir.
 */
@Slf4j
@Service
public class MdfeEmissaoService {

    /** cStat 107: servico em operacao. O unico que significa "pode emitir". */
    public static final int CSTAT_EM_OPERACAO = 107;

    private final String estado;
    private final String ambiente;
    private final String caminhoCertificado;
    private final String senhaCertificado;
    private final String caminhoCadeia;
    private final String senhaCadeia;
    private final int timeoutSegundos;

    public MdfeEmissaoService(
            @Value("${BRASIL_SAAS_MDFE_ESTADO:SP}") String estado,
            @Value("${BRASIL_SAAS_MDFE_AMBIENTE:HOMOLOGACAO}") String ambiente,
            @Value("${BRASIL_SAAS_MDFE_CERTIFICADO:}") String caminhoCertificado,
            @Value("${BRASIL_SAAS_MDFE_CERT_PASS:}") String senhaCertificado,
            @Value("${BRASIL_SAAS_MDFE_CADEIA:}") String caminhoCadeia,
            @Value("${BRASIL_SAAS_MDFE_CADEIA_PASS:}") String senhaCadeia,
            @Value("${BRASIL_SAAS_MDFE_TIMEOUT:30}") int timeoutSegundos) {
        this.estado = estado;
        this.ambiente = ambiente;
        this.caminhoCertificado = caminhoCertificado;
        this.senhaCertificado = senhaCertificado;
        this.caminhoCadeia = caminhoCadeia;
        this.senhaCadeia = senhaCadeia;
        this.timeoutSegundos = timeoutSegundos;
    }

    /**
     * Status do servico na SVRS. Nao grava nada e nao muda nada la.
     *
     * <p>E o health check. Se a SVRS estiver fora, devolve
     * {@code sucesso=false} com o motivo, em vez de estourar timeout para o
     * chamador e deixar ele sem saber se e a SEFAZ ou o ERP.
     */
    public RespostaMdfePadrao statusServico() {
        Instant inicio = Instant.now();

        if (!certificadoConfigurado()) {
            return RespostaMdfePadrao.resposta(false, 0,
                    "Certificado A1 nao configurado. A fincatto exige o certificado "
                            + "no construtor do WSFacade, nao na chamada. Defina "
                            + "BRASIL_SAAS_MDFE_CERTIFICADO, _CERT_PASS, "
                            + "_CADEIA e _CADEIA_PASS.",
                    "status", 0, Map.of());
        }

        try {
            try (WSFacade facade = new WSFacade(configuracoes().comoMDFe())) {
            MDFeConsStatServRet ret = facade.consultaStatus();

            long ms = Duration.between(inicio, Instant.now()).toMillis();
            int cStat = inteiro(ret.getCodigoStatus(), 0);
            boolean emOperacao = cStat == CSTAT_EM_OPERACAO;

            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put("ambiente", ret.getAmbiente() == null ? null : ret.getAmbiente().name());
            dados.put("versaoAplicativo", ret.getVersaoAplicacao());
            dados.put("ufAtendente", ret.getUf() == null ? null : ret.getUf().name());
            dados.put("dataRecebimento", ret.getDataRecebimento());
            dados.put("tempoMedioResposta", ret.getTempoMedio());
            dados.put("observacao", ret.getObservacao());
            dados.put("emOperacao", emOperacao);
            dados.put("autorizador", "SVRS (Virtual Ambiente Nacional)");
            dados.put("estadoEmissor", estado);
            dados.put("ambienteConfigurado", ambiente);
            dados.put("certificadoConfigurado", true);

            return RespostaMdfePadrao.resposta(emOperacao, cStat,
                    ret.getMotivo(), "status", ms, dados);
            }
        } catch (Exception e) {
            long ms = Duration.between(inicio, Instant.now()).toMillis();
            log.warn("status do MDF-e falhou: {}", e.getMessage());
            return RespostaMdfePadrao.resposta(false, 0,
                    "Falha ao consultar o status: " + e.getMessage(), "status", ms, Map.of());
        }
    }

    /**
     * Segunda chamada do MDF-e: a partir do recibo, busca chave e protocolo.
     *
     * <p>E o que fecha o ciclo de autorizacao, e e o motivo de o MDF-e
     * precisar de duas chamadas onde a NFe precisa de uma. O
     * {@code MDFeRecepcaoSinc} devolve so {@code nRec}; a chave e o protocolo
     * vemem aqui.
     */
    public RespostaMdfePadrao consultarRecibo(String numeroRecibo) {
        Instant inicio = Instant.now();
        if (!certificadoConfigurado()) {
            return RespostaMdfePadrao.resposta(false, 0,
                    "Certificado A1 nao configurado.", "recibo", 0, Map.of());
        }
        try {
            try (WSFacade facade = new WSFacade(configuracoes().comoMDFe())) {
            var ret = facade.consultaRecibo(numeroRecibo);
            long ms = Duration.between(inicio, Instant.now()).toMillis();

            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put("recibo", numeroRecibo);
            dados.put("retorno", String.valueOf(ret));

            // O retorno da lib nao e interpretado aqui: cStat e protocolo
            // ainda nao sao extraidos. Por isso sucesso fica null ("nao deu
            // para saber") e nao true -- afirmar sucesso sem ler a resposta
            // faria o chamador tratar como autorizado um MDF-e que a SVRS
            // pode ter rejeitado ou ainda estar processando.
            return RespostaMdfePadrao.resposta(
                    null, 0,
                    "Retorno bruto da SVRS. Confira cStat e protocolo em 'dados.retorno' "
                            + "antes de considerar o MDF-e autorizado.",
                    "recibo", ms, dados);
            }
        } catch (Exception e) {
            long ms = Duration.between(inicio, Instant.now()).toMillis();
            log.warn("consulta de recibo falhou: {}", e.getMessage());
            return RespostaMdfePadrao.indefinido("recibo",
                    "Falha ao consultar o recibo: " + e.getMessage());
        }
    }

    /** Constroi a config para o ambiente pedido. */
    public ConfigCertificadoDocumentoFiscal configuracoes() {
        DFUnidadeFederativa uf = DFUnidadeFederativa.valueOf(estado.trim().toUpperCase());
        DFAmbiente amb = DFAmbiente.valueOf(ambiente.trim().toUpperCase());
        return new ConfigCertificadoDocumentoFiscal(uf, amb,
                caminhoCertificado, senhaCertificado,
                caminhoCadeia, senhaCadeia, timeoutSegundos);
    }

    public boolean certificadoConfigurado() {
        return caminhoCertificado != null && !caminhoCertificado.isBlank();
    }

    public boolean cadeiaConfigurada() {
        return caminhoCadeia != null && !caminhoCadeia.isBlank();
    }

    private int inteiro(String valor, int padrao) {
        try {
            return Integer.parseInt(valor);
        } catch (Exception e) {
            return padrao;
        }
    }
}
