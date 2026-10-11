package br.com.brasil_saas.fiscal.cte;

import br.com.brasil_saas.fiscal.mdfe.ConfigCertificadoDocumentoFiscal;
import br.com.brasil_saas.fiscal.mdfe.RespostaMdfePadrao;
import com.fincatto.documentofiscal.DFAmbiente;
import com.fincatto.documentofiscal.DFUnidadeFederativa;
import com.fincatto.documentofiscal.cte400.classes.consultastatusservico.CTeConsStatServRet;
import com.fincatto.documentofiscal.cte400.webservices.WSFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CT-e contra a SVRS, pela fincatto {@code documentofiscal}.
 *
 * <h3>Por que CT-e e MDF-e sao o mesmo trabalho</h3>
 * Os dois sao documentos de transporte terrestre, os dois vao para a mesma
 * autorizadora nacional, os dois usam certificado A1 com mutual TLS, e os dois
 * devolvem recibo numa chamada e protocolo na seguinte. A diferenca de layout
 * esta na lib. Por isso a config e a mesma classe e o contrato e o mesmo
 * registro — e por isso o que se aprende emitting um vale para o outro.
 *
 * <h3>Versao do layout</h3>
 * O CT-e 4.00 e o vigente. A lib traz tambem {@code cte200} e {@code cte300},
 * que sao de leiautes antigos e servem para consultar documento ja emitido
 * naqueles formatos.
 *
 * <h3>Ambiente fixo na lib</h3>
 * Igual ao MDF-e: a {@code DFConfig} 5.1.2 devolve {@code HOMOLOGACAO} e nao
 * tem setter. A config sobrescreve o metodo. Efeito colateral bom: a lib fica
 * sempre em homologacao por padrao.
 */
@Slf4j
@Service
public class CteEmissaoService {

    /** cStat 107: servico em operacao. */
    public static final int CSTAT_EM_OPERACAO = 107;

    private final String estado;
    private final String ambiente;
    private final String caminhoCertificado;
    private final String senhaCertificado;
    private final String caminhoCadeia;
    private final String senhaCadeia;
    private final int timeoutSegundos;

    public CteEmissaoService(
            @Value("${BRASIL_SAAS_CTE_ESTADO:SP}") String estado,
            @Value("${BRASIL_SAAS_CTE_AMBIENTE:HOMOLOGACAO}") String ambiente,
            @Value("${BRASIL_SAAS_MDFE_CERTIFICADO:}") String caminhoCertificado,
            @Value("${BRASIL_SAAS_MDFE_CERT_PASS:}") String senhaCertificado,
            @Value("${BRASIL_SAAS_MDFE_CADEIA:}") String caminhoCadeia,
            @Value("${BRASIL_SAAS_MDFE_CADEIA_PASS:}") String senhaCadeia,
            @Value("${BRASIL_SAAS_CTE_TIMEOUT:30}") int timeoutSegundos) {
        this.estado = estado;
        this.ambiente = ambiente;
        this.caminhoCertificado = caminhoCertificado;
        this.senhaCertificado = senhaCertificado;
        this.caminhoCadeia = caminhoCadeia;
        this.senhaCadeia = senhaCadeia;
        this.timeoutSegundos = timeoutSegundos;
    }

    /**
     * Status do servico do CT-e na SVRS. Nao grava e nao muda nada la.
     *
     * <p>Tem o mesmo cuidado do MDF-e: certificado ausente devolve erro
     * dizendo que falta certificado, e nao deixa a excecao do HttpClient
     * escapar e parecer falha de rede.
     */
    public RespostaMdfePadrao statusServico() {
        Instant inicio = Instant.now();

        if (!certificadoConfigurado()) {
            return RespostaMdfePadrao.resposta(false, 0,
                    "Certificado A1 nao configurado. A fincatto exige o certificado "
                            + "no construtor do WSFacade, nao na chamada.",
                    "cte-status", 0, Map.of());
        }

        try {
            ConfigCertificadoDocumentoFiscal conf = configuracoes();
            try (WSFacade facade = new WSFacade(conf.comoCTe())) {
            // O CT-e exige a UF na chamada; o MDF-e tem sobrecarga sem
            // argumento. Sem a UF o compilador reclama de "actual and formal
            // argument lists differ in length", e em tempo de execucao o
            // endpoint do CT-e responde 404 para a assinatura errada.
            CTeConsStatServRet ret = facade.consultaStatus(conf.getCUF());

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
            dados.put("layout", "CT-e 4.00");
            dados.put("estadoEmissor", estado);
            dados.put("ambienteConfigurado", ambiente);
            dados.put("certificadoConfigurado", true);

            return RespostaMdfePadrao.resposta(emOperacao, cStat,
                    ret.getMotivo(), "cte-status", ms, dados);
            }
        } catch (Exception e) {
            long ms = Duration.between(inicio, Instant.now()).toMillis();
            log.warn("status do CT-e falhou: {}", e.getMessage());
            return RespostaMdfePadrao.resposta(false, 0,
                    "Falha ao consultar o status: " + e.getMessage(),
                    "cte-status", ms, Map.of());
        }
    }

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

    private int inteiro(String valor, int padrao) {
        try {
            return Integer.parseInt(valor);
        } catch (Exception e) {
            return padrao;
        }
    }
}
