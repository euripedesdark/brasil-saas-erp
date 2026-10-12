package br.com.brasil_saas.fiscal.sefaz;
import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.brasil_saas.fiscal.model.CertificadoDigital;
import br.com.brasil_saas.fiscal.repository.CertificadoDigitalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Monta ConfiguracoesNfe a partir do certificado salvo em bc_fis_certificado_digital.
 * Só é efetivamente usada quando SefazProperties.enabled = true.
 */
@Slf4j @Configuration @RequiredArgsConstructor
public class SefazConfig {
    private final SefazProperties props;
    private final CertificadoDigitalRepository certRepo;

    public ConfiguracoesNfe montar(Long empresaId, EstadosEnum uf) throws Exception {
        if (!props.isEnabled()) {
            throw new IllegalStateException("SEFAZ desabilitada (brasil-saas.fiscal.sefaz.enabled=false). " +
                "Configure o certificado em bc_fis_certificado_digital e ative a flag para consultar/transmitir.");
        }
        Optional<CertificadoDigital> cert = certRepo.findFirstByEmpresaIdAndAtivoTrueAndDeletedAtIsNullOrderByValidadeAtDesc(empresaId);
        if (cert.isEmpty()) throw new IllegalStateException("Nenhum certificado digital ativo cadastrado para a empresa " + empresaId);
        CertificadoDigital cd = cert.get();
        if (cd.getValidadeAt() == null || cd.getValidadeAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Certificado digital expirado para a empresa " + empresaId);
        }
        if (cd.getArquivoUrl() == null || cd.getArquivoUrl().isBlank()) {
            throw new IllegalStateException("Caminho/identificador do certificado digital não configurado para a empresa " + empresaId);
        }
        Certificado certificado;
        if ("A1".equalsIgnoreCase(cd.getTipo())) {
            // cd.getArquivoUrl() guarda o caminho/bytes do .pfx; senha padrão ou do certificado
            certificado = CertificadoService.certificadoPfx(cd.getArquivoUrl(), System.getenv("NFE_CERT_PASSWORD"));
        } else {
            // Certificado A3 requer Provider do Java Security
            // O caminho pode conter o nome do provider (ex: "SafeNet", "Aladdin", etc.)
            String providerName = cd.getArquivoUrl();
            java.security.Provider provider = java.security.Security.getProvider(providerName);
            if (provider == null) {
                // Tenta encontrar provider por nome parcial
                java.security.Provider[] providers = java.security.Security.getProviders();
                for (java.security.Provider p : providers) {
                    if (p.getName().toLowerCase().contains(providerName.toLowerCase())) {
                        provider = p;
                        break;
                    }
                }
                if (provider == null) {
                    throw new IllegalStateException("Provider de certificado A3 não encontrado: " + providerName);
                }
            }
            certificado = CertificadoService.certificadoA3(System.getenv("NFSE_CERT_PASSWORD"), provider);
        }
        AmbienteEnum amb = "PRODUCAO".equalsIgnoreCase(props.getAmbiente()) ? AmbienteEnum.PRODUCAO : AmbienteEnum.HOMOLOGACAO;
        return ConfiguracoesNfe.criarConfiguracoes(uf, amb, certificado, "4.00", ZoneId.of("America/Sao_Paulo"));
    }
}
