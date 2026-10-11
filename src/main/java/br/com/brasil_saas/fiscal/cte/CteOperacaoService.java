package br.com.brasil_saas.fiscal.cte;

import br.com.brasil_saas.fiscal.model.Cte;
import br.com.brasil_saas.fiscal.repository.CteRepository;
import br.com.brasil_saas.fiscal.mdfe.ConfigCertificadoDocumentoFiscal;
import br.com.brasil_saas.shared.exception.BusinessException;
import com.fincatto.documentofiscal.cte400.classes.envio.CTeEnvioRetornoDados;
import com.fincatto.documentofiscal.cte400.classes.nota.CTeNota;
import com.fincatto.documentofiscal.cte400.classes.nota.consulta.CTeNotaConsultaRetorno;
import com.fincatto.documentofiscal.cte400.webservices.WSFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operacao completa do CT-e 4.00.
 *
 * <p>O ERP recebe o XML fiscal montado pela camada comercial/transportes,
 * deixa a biblioteca Fincatto assinar e transmitir, grava o XML assinado e
 * expõe consulta/cancelamento. Assim a tela nao precisa conhecer a lib fiscal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CteOperacaoService {

    private static final Pattern PROTOCOLO = Pattern.compile("<nProt>([^<]+)</nProt>");
    private final CteRepository repository;
    private final CteEmissaoService configuracao;

    @Transactional
    public Map<String, Object> emitir(Long empresaId, String xml) {
        exigirXml(xml);
        ConfigCertificadoDocumentoFiscal conf = configuracao.configuracoes();
        try (WSFacade facade = new WSFacade(conf.comoCTe())) {
            CTeNota nota = conf.comoCTe().getPersister().read(CTeNota.class, xml);
            CTeEnvioRetornoDados retorno = facade.enviaCTe(nota);
            var resposta = retorno.getRetorno();
            String xmlAssinado = retorno.getLoteAssinado().toString();
            String chave = retorno.getLoteAssinado().getCteNotaInfo().getChaveAcesso();

            Cte documento = repository.findByEmpresaIdAndChaveAcesso(empresaId, chave)
                    .orElseGet(Cte::new);
            documento.setEmpresaId(empresaId);
            documento.setChaveAcesso(chave);
            documento.setNumero(nota.getCteNotaInfo().getIdentificacao().getNumero().longValue());
            documento.setSerie(String.valueOf(nota.getCteNotaInfo().getIdentificacao().getSerie()));
            documento.setDataEmissao(nota.getCteNotaInfo().getIdentificacao().getDataEmissao().toLocalDateTime());
            documento.setStatus(status(resposta.getStatus()));
            documento.setTipoOperacao("S");
            documento.setXml(xmlAssinado);
            repository.save(documento);

            Map<String,Object> out = base(documento);
            out.put("cStat", resposta.getStatus());
            out.put("xMotivo", resposta.getMotivo());
            out.put("protocolo", resposta.getProtocolo() == null || resposta.getProtocolo().getInfo() == null
                    ? null : resposta.getProtocolo().getInfo().getNumeroProtocolo());
            out.put("xmlAssinado", xmlAssinado);
            return out;
        } catch (Exception e) {
            log.error("Falha na emissão do CT-e da empresa {}", empresaId, e);
            throw new BusinessException("Falha na emissão do CT-e: " + mensagem(e));
        }
    }

    public Map<String,Object> consultar(Long empresaId, String chave) {
        Cte documento = repository.findByEmpresaIdAndChaveAcesso(empresaId, normalizarChave(chave))
                .orElseThrow(() -> new BusinessException("CT-e não encontrado para esta empresa."));
        try (WSFacade facade = new WSFacade(configuracao.configuracoes().comoCTe())) {
            CTeNotaConsultaRetorno retorno = facade.consultaNota(documento.getChaveAcesso());
            Map<String,Object> out = base(documento);
            out.put("cStat", retorno.getStatus());
            out.put("xMotivo", retorno.getMotivo());
            out.put("protocolo", retorno.getProtocolo() == null || retorno.getProtocolo().getInfo() == null
                    ? null : retorno.getProtocolo().getInfo().getNumeroProtocolo());
            return out;
        } catch (Exception e) {
            throw new BusinessException("Falha na consulta do CT-e: " + mensagem(e));
        }
    }

    @Transactional
    public Map<String,Object> cancelar(Long empresaId, Long id, String motivo) {
        Cte documento = repository.findById(id)
                .filter(d -> empresaId.equals(d.getEmpresaId()))
                .orElseThrow(() -> new BusinessException("CT-e não encontrado para esta empresa."));
        String protocolo = extrairProtocolo(documento.getXml());
        if (protocolo == null) {
            throw new BusinessException("CT-e sem protocolo de autorização armazenado; consulte a SEFAZ antes de cancelar.");
        }
        if (motivo == null || motivo.trim().length() < 15) {
            throw new BusinessException("O motivo do cancelamento do CT-e deve ter pelo menos 15 caracteres.");
        }
        try (WSFacade facade = new WSFacade(configuracao.configuracoes().comoCTe())) {
            var retorno = facade.cancelaNota(documento.getChaveAcesso(), protocolo, motivo.trim());
            var info = retorno.getInfoEventoRetorno();
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("chaveAcesso", documento.getChaveAcesso());
            out.put("cStat", info == null ? null : info.getCodigoStatus());
            out.put("xMotivo", info == null ? null : info.getMotivo());
            if (info != null && Integer.valueOf(135).equals(info.getCodigoStatus())) {
                documento.setStatus("CANCELADO");
                repository.save(documento);
            }
            return out;
        } catch (Exception e) {
            throw new BusinessException("Falha no cancelamento do CT-e: " + mensagem(e));
        }
    }

    private Map<String,Object> base(Cte d) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", d.getId());
        out.put("numero", d.getNumero());
        out.put("serie", d.getSerie());
        out.put("chaveAcesso", d.getChaveAcesso());
        out.put("dataEmissao", d.getDataEmissao());
        out.put("status", d.getStatus());
        out.put("xml", d.getXml());
        return out;
    }

    private String status(String cStat) {
        if ("100".equals(cStat)) return "AUTORIZADA";
        if (cStat == null) return "PENDENTE";
        return "REJEITADA";
    }

    private String extrairProtocolo(String xml) {
        if (xml == null) return null;
        Matcher m = PROTOCOLO.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private String normalizarChave(String chave) {
        if (chave == null || chave.replaceAll("\\D", "").length() != 44) {
            throw new BusinessException("Informe uma chave de acesso CT-e válida com 44 dígitos.");
        }
        return chave.replaceAll("\\D", "");
    }

    private void exigirXml(String xml) {
        if (xml == null || xml.isBlank()) throw new BusinessException("Informe o XML do CT-e.");
        if (!xml.contains("<CTe") || !xml.contains("<infCte")) {
            throw new BusinessException("XML inválido: esperado CT-e 4.00.");
        }
    }

    private String mensagem(Exception e) {
        String m=e.getMessage();
        return m == null ? e.getClass().getSimpleName() : (m.length()>500 ? m.substring(0,500)+"..." : m);
    }
}
