package br.com.brasil_saas.fiscal.mdfe;

import com.fincatto.documentofiscal.DFAmbiente;
import com.fincatto.documentofiscal.DFUnidadeFederativa;
import com.fincatto.documentofiscal.cte.CTeConfig;
import com.fincatto.documentofiscal.mdfe3.MDFeConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.TimeZone;

/**
 * Configuracao de certificado e ambiente para a fincatto
 * {@code documentofiscal}, serve MDF-e e CT-e.
 *
 * <h3>Por que uma so classe para os dois</h3>
 * {@code MDFeConfig} e {@code CTeConfig} herdam do mesmo {@code DFConfig}, e os
 * 5 metodos abstratos sao identicos: UF, keystore do certificado, senha,
 * keystore da cadeia, senha da cadeia. Sao exatamente as mesmas 5 coisas. Duas
 * classes seriam o mesmo codigo duas vezes, e a segunda ia divergir quando a
 * primeira mudasse.
 *
 * <p>Por isso a classe nao estende {@code MDFeConfig} diretamente: ela
 * implementa os 5 metodos e expoe {@link #comoMDFe()} e {@link #comoCTe()},
 * cada uma devolvendo a classe concreta que a lib espera. Estender as duas nao
 * compila, sao hierarquias irmas.
 *
 * <h3>A cadeia de confianca (.cacerts) e obrigatoria</h3>
 * A SEFAZ usa mutual TLS: os dois lados apresentam certificado. Sem a cadeia, o
 * handshake falha e o erro aparece como {@code SSLHandshakeException} ou
 * timeout, sem falar de certificado — o que leva a conclusão errada de que o
 * problema e o A1.
 *
 * <h3>Por que a fincatto, e nao a swconsultoria</h3>
 * A {@code java-mdfe 3.00.4} chama {@code Certificado.getTipo()}, que nao
 * existe no {@code java_certificado 3.16} que o ERP ja usa — o resultado e
 * {@code NoSuchMethodError} e o MDF-e inteiro fora do ar. A fincatto assina
 * com o Apache Santuario e nao depende daquela classe. O conflito nao existe.
 */
@Slf4j
@Getter
public class ConfigCertificadoDocumentoFiscal {

    private final DFUnidadeFederativa uf;
    private final DFAmbiente ambiente;
    private final String caminhoCertificado;
    private final String senhaCertificado;
    private final String caminhoCadeia;
    private final String senhaCadeia;
    private final int timeoutSegundos;

    private KeyStore keystoreCertificado;
    private KeyStore keystoreCadeia;

    public ConfigCertificadoDocumentoFiscal(
            DFUnidadeFederativa uf, DFAmbiente ambiente,
            String caminhoCertificado, String senhaCertificado,
            String caminhoCadeia, String senhaCadeia, int timeoutSegundos) {
        this.uf = uf;
        this.ambiente = ambiente;
        this.caminhoCertificado = caminhoCertificado;
        this.senhaCertificado = senhaCertificado;
        this.caminhoCadeia = caminhoCadeia;
        this.senhaCadeia = senhaCadeia;
        this.timeoutSegundos = timeoutSegundos;
    }

    public DFUnidadeFederativa getCUF() {
        return uf;
    }

    public String getCertificadoSenha() {
        return senhaCertificado;
    }

    public String getCadeiaCertificadosSenha() {
        return senhaCadeia;
    }

    public KeyStore getCertificadoKeyStore() throws KeyStoreException {
        if (keystoreCertificado == null) {
            keystoreCertificado = carrega("PKCS12", caminhoCertificado, senhaCertificado);
        }
        return keystoreCertificado;
    }

    public KeyStore getCadeiaCertificadosKeyStore() throws KeyStoreException {
        if (keystoreCadeia == null) {
            keystoreCadeia = carrega("JKS", caminhoCadeia, senhaCadeia);
        }
        return keystoreCadeia;
    }

    public String getCertificadoAlias() {
        try {
            return getCertificadoKeyStore().aliases().nextElement();
        } catch (KeyStoreException e) {
            log.error("erro ao identificar o alias do certificado: {}", e.getMessage());
            return null;
        }
    }

    public TimeZone getTimeZone() {
        return TIMEZONE_SP_LOCAL;
    }

    /**
     * O ambiente.
     *
     * <p>A {@code DFConfig} da 5.1.2 <b>devolve HOMOLOGACAO fixo</b> e
     * <b>nao tem setter</b>: o bytecode e um {@code getstatic} para
     * {@code DFAmbiente.HOMOLOGACAO} e acabou. Por isso {@code setAmbiente}
     * nao existe e a unica forma de chegar em producao e sobrescrever este
     * metodo.
     */
    public DFAmbiente getAmbiente() {
        return ambiente;
    }

    /** Timeout da chamada, em segundos. */
    public int getTimeoutRequisicao() {
        return timeoutSegundos;
    }

    public int getTimeoutRequisicaoEmMillis() {
        return timeoutSegundos * 1000;
    }

    public int getSoTimeoutEmMillis() {
        return timeoutSegundos * 1000;
    }

    /** A mesma config, vestida de MDF-e, que a lib do MDF-e exige. */
    public MDFeConfig comoMDFe() {
        return new MDFeConfig() {
            public DFUnidadeFederativa getCUF() { return uf; }

            public KeyStore getCertificadoKeyStore() throws KeyStoreException {
                return ConfigCertificadoDocumentoFiscal.this.getCertificadoKeyStore();
            }

            public String getCertificadoSenha() {
                return ConfigCertificadoDocumentoFiscal.this.getCertificadoSenha();
            }

            public KeyStore getCadeiaCertificadosKeyStore() throws KeyStoreException {
                return ConfigCertificadoDocumentoFiscal.this.getCadeiaCertificadosKeyStore();
            }

            public String getCadeiaCertificadosSenha() {
                return ConfigCertificadoDocumentoFiscal.this.getCadeiaCertificadosSenha();
            }

            public String getCertificadoAlias() {
                return ConfigCertificadoDocumentoFiscal.this.getCertificadoAlias();
            }

            public TimeZone getTimeZone() { return TIMEZONE_SP_LOCAL; }

            public DFAmbiente getAmbiente() {
                return ConfigCertificadoDocumentoFiscal.this.getAmbiente();
            }
        };
    }

    /** A mesma config, vestida de CT-e. */
    public CTeConfig comoCTe() {
        return new CTeConfig() {
            public DFUnidadeFederativa getCUF() { return uf; }

            public KeyStore getCertificadoKeyStore() throws KeyStoreException {
                return ConfigCertificadoDocumentoFiscal.this.getCertificadoKeyStore();
            }

            public String getCertificadoSenha() {
                return ConfigCertificadoDocumentoFiscal.this.getCertificadoSenha();
            }

            public KeyStore getCadeiaCertificadosKeyStore() throws KeyStoreException {
                return ConfigCertificadoDocumentoFiscal.this.getCadeiaCertificadosKeyStore();
            }

            public String getCadeiaCertificadosSenha() {
                return ConfigCertificadoDocumentoFiscal.this.getCadeiaCertificadosSenha();
            }

            public String getCertificadoAlias() {
                return ConfigCertificadoDocumentoFiscal.this.getCertificadoAlias();
            }

            public TimeZone getTimeZone() { return TIMEZONE_SP_LOCAL; }

            public DFAmbiente getAmbiente() {
                return ConfigCertificadoDocumentoFiscal.this.getAmbiente();
            }
        };
    }

    /**
     * Carrega um keystore do disco.
     *
     * <p>O tipo e parametro porque o A1 e PKCS12 e a cadeia da SEFAZ e JKS.
     * Fixar um dos dois e o jeito de o handshake falhar sem mensagem util.
     */
    private KeyStore carrega(String tipo, String caminho, String senha)
            throws KeyStoreException {
        if (caminho == null || caminho.isBlank()) {
            throw new KeyStoreException("certificado nao configurado: "
                    + tipo + " sem caminho");
        }
        try (InputStream in = new FileInputStream(caminho)) {
            KeyStore ks = KeyStore.getInstance(tipo);
            ks.load(in, senha == null ? null : senha.toCharArray());
            return ks;
        } catch (Exception e) {
            // A mensagem original e "keystore password was incorrect" ou
            // "Invalid keystore format", e nenhuma das duas diz qual arquivo.
            throw new KeyStoreException("nao foi possivel abrir " + caminho
                    + " (" + tipo + "): " + e.getMessage(), e);
        }
    }

    // DFConfig.TIMEZONE_SP esta deprecated na lib documentofiscal
    private static final TimeZone TIMEZONE_SP_LOCAL =
            TimeZone.getTimeZone("America/Sao_Paulo");
}
