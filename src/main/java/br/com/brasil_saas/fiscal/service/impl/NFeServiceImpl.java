package br.com.brasil_saas.fiscal.service.impl;

import br.com.brasil_saas.cadastro.repository.ClienteRepository;
import br.com.brasil_saas.cadastro.repository.ProdutoRepository;
import br.com.brasil_saas.core.model.Empresa;
import br.com.brasil_saas.core.repository.EmpresaRepository;
import br.com.brasil_saas.fiscal.model.NfeItem;
import br.com.brasil_saas.fiscal.repository.NfeItemRepository;
import br.com.brasil_saas.fiscal.repository.NfeRepository;
import br.com.brasil_saas.fiscal.sefaz.SefazConfig;
import br.com.brasil_saas.fiscal.service.NFeEmissaoValidator;
import br.com.brasil_saas.fiscal.service.NFeService;
import br.com.brasil_saas.fiscal.service.NFeXmlBuilder;
import br.com.brasil_saas.vendas.model.PedidoVenda;
import br.com.brasil_saas.fiscal.model.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.Evento;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.dom.enuns.StatusEnum;
import br.com.swconsultoria.nfe.schemas.TEnviNFe;
import br.com.swconsultoria.nfe.schemas.TNFe;
import br.com.swconsultoria.nfe.schemas.TRetConsReciNFe;
import br.com.swconsultoria.nfe.schemas.TRetEnviNFe;
import br.com.swconsultoria.nfe.schemas.TRetConsSitNFe;
import br.com.swconsultoria.nfe.schemas_eventos.TEnvEventoCancelamento;
import br.com.swconsultoria.nfe.schemas_eventos.TRetEnvEventoCancelamento;
import br.com.swconsultoria.nfe.util.CancelamentoUtil;
import br.com.swconsultoria.nfe.util.RetornoUtil;
import br.com.swconsultoria.nfe.util.XmlImpostoUtil;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NFeServiceImpl implements NFeService {

    private final SefazConfig sefazConfig;
    private final EmpresaRepository empresaRepository;
    private final NFeEmissaoValidator emissaoValidator;
    private final NfeRepository nfeRepository;
    private final NfeItemRepository nfeItemRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final NFeXmlBuilder nfeXmlBuilder;

    @Override
    @Transactional
    public String emitirNFe(Long empresaId, PedidoVenda pedido) throws Exception {
        if (pedido == null || pedido.getId() == null) {
            throw new IllegalArgumentException("Pedido de venda obrigatorio para emissao de NF-e");
        }
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa nao encontrada: " + empresaId));
        emissaoValidator.validar(empresa, pedido);

        ConfiguracoesNfe config = carregarConfiguracoes(empresa);
        int serie = 1;
        nfeRepository.lockSequence(empresaId, String.valueOf(serie));
        int numero = proximoNumero(empresaId, serie);

        TEnviNFe enviNFe = nfeXmlBuilder.build(config, empresa, pedido, serie, numero);
        enviNFe = br.com.swconsultoria.nfe.Nfe.montaNfe(config, enviNFe, true);
        TRetEnviNFe retorno = br.com.swconsultoria.nfe.Nfe.enviarNfe(
                config, enviNFe, DocumentoEnum.NFE);
        TNFe nfeXml = enviNFe.getNFe().get(0);

        String xmlFinal;
        String protocolo;
        String chave;
        if (RetornoUtil.isRetornoAssincrono(retorno)) {
            String recibo = retorno.getInfRec().getNRec();
            TRetConsReciNFe retornoNfe = null;
            for (int tentativa = 0; tentativa < 15; tentativa++) {
                retornoNfe = br.com.swconsultoria.nfe.Nfe.consultaRecibo(
                        config, recibo, DocumentoEnum.NFE);
                if (!StatusEnum.LOTE_EM_PROCESSAMENTO.getCodigo().equals(retornoNfe.getCStat())) {
                    break;
                }
                Thread.sleep(1000);
            }
            // consultaRecibo sempre atribui retornoNfe no corpo do loop
            RetornoUtil.validaAssincrono(retornoNfe);
            var prot = retornoNfe.getProtNFe().get(0);
            protocolo = prot.getInfProt().getNProt();
            chave = prot.getInfProt().getChNFe();
            xmlFinal = XmlNfeUtil.criaNfeProc(enviNFe, prot);
        } else {
            RetornoUtil.validaSincrono(retorno);
            protocolo = retorno.getProtNFe().getInfProt().getNProt();
            chave = retorno.getProtNFe().getInfProt().getChNFe();
            xmlFinal = XmlNfeUtil.criaNfeProc(enviNFe, retorno.getProtNFe());
        }

        if (protocolo == null || protocolo.isBlank() || chave == null || !chave.matches("\\d{44}")) {
            throw new IllegalStateException("SEFAZ autorizou a NF-e sem protocolo/chave de acesso validos");
        }

        Nfe nota = new Nfe();
        nota.setEmpresaId(empresaId);
        nota.setPedidoVendaId(pedido.getId());
        nota.setDocumentoOrigemTipo("PEDIDO_VENDA");
        nota.setDocumentoOrigemId(pedido.getId());
        nota.setClienteId(pedido.getClienteId());
        nota.setPessoaId(clienteRepository
                .findByIdAndEmpresaIdAndDeletedAtIsNullWithPessoa(pedido.getClienteId(), empresaId)
                .map(c -> c.getPessoa().getId()).orElse(null));
        nota.setNumero((long) numero);
        nota.setSerie(String.valueOf(serie));
        nota.setChaveAcesso(chave);
        nota.setNaturezaOperacao(nfeXml.getInfNFe().getIde().getNatOp());
        nota.setCfop(nfeXml.getInfNFe().getDet().get(0).getProd().getCFOP());
        nota.setDataEmissao(LocalDateTime.now());
        nota.setDataSaida(LocalDateTime.now());
        nota.setStatus("AUTORIZADA");
        nota.setProtocolo(protocolo);
        nota.setXml(xmlFinal);

        var total = nfeXml.getInfNFe().getTotal().getICMSTot();
        nota.setValorProdutos(decimal(total.getVProd()));
        nota.setValorFrete(decimal(total.getVFrete()));
        nota.setValorDesconto(decimal(total.getVDesc()));
        nota.setValorIcms(decimal(total.getVICMS()));
        nota.setValorIpi(decimal(total.getVIPI()));
        nota.setValorPis(decimal(total.getVPIS()));
        nota.setValorCofins(decimal(total.getVCOFINS()));
        nota.setValorTotal(decimal(total.getVNF()));
        nota.setTipoOperacao("S");
        nota = nfeRepository.save(nota);

        for (TNFe.InfNFe.Det det : nfeXml.getInfNFe().getDet()) {
            NfeItem item = new NfeItem();
            item.setNfe(nota);
            item.setProdutoId(produtoRepository
                    .findByEmpresaIdAndCodigoIgnoreCaseAndDeletedAtIsNull(
                            empresaId, det.getProd().getCProd()).map(p -> p.getId()).orElse(null));
            item.setNumeroItem(Integer.valueOf(det.getNItem()));
            item.setNcm(det.getProd().getNCM());
            item.setCfop(det.getProd().getCFOP());
            item.setCest(det.getProd().getCEST());
            item.setQuantidade(decimal(det.getProd().getQCom()));
            item.setUnidade(det.getProd().getUCom());
            item.setCodigoProduto(det.getProd().getCProd());
            item.setCodigoBarras(normalizarEan(det.getProd().getCEAN()));
            item.setValorUnitario(decimal(det.getProd().getVUnCom()));
            item.setValorTotal(decimal(det.getProd().getVProd()));
            item.setValorIcms(XmlImpostoUtil.getVICMS(det.getImposto().getContent()));
            item.setValorIpi(BigDecimal.ZERO);
            item.setAliquotaIcms(BigDecimal.ZERO);
            item.setAliquotaIpi(BigDecimal.ZERO);
            nfeItemRepository.save(item);
        }
        return protocolo;
    }

    @Override
    public String cancelarNFe(Long empresaId, String chaveAcesso, String motivo) throws Exception {
        if (chaveAcesso == null || !chaveAcesso.matches("\\d{44}")) {
            throw new IllegalArgumentException("Chave de acesso deve conter 44 digitos");
        }
        if (motivo == null || motivo.trim().length() < 15 || motivo.trim().length() > 255) {
            throw new IllegalArgumentException("Motivo de cancelamento deve conter entre 15 e 255 caracteres");
        }
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa nao encontrada: " + empresaId));
        ConfiguracoesNfe config = carregarConfiguracoes(empresa);
        Nfe nota = nfeRepository.findByEmpresaIdAndChaveAcessoAndDeletedAtIsNull(empresaId, chaveAcesso)
                .orElseThrow(() -> new IllegalArgumentException(
                        "NF-e nao encontrada para a empresa: " + chaveAcesso));
        if (nota.getProtocolo() == null || nota.getProtocolo().isBlank()) {
            throw new IllegalStateException("NF-e nao possui protocolo de autorizacao para cancelamento");
        }
        if ("CANCELADA".equalsIgnoreCase(nota.getStatus())) {
            return nota.getProtocolo();
        }
        if (!"AUTORIZADA".equalsIgnoreCase(nota.getStatus())) {
            throw new IllegalStateException(
                    "Somente NF-e autorizada pode ser cancelada. Status atual: " + nota.getStatus());
        }

        Evento evento = new Evento();
        evento.setChave(chaveAcesso);
        evento.setProtocolo(nota.getProtocolo());
        evento.setCnpj(normalizarDocumento(empresa.getCnpj(), 14));
        evento.setMotivo(motivo.trim());
        evento.setDataEvento(LocalDateTime.now());
        TEnvEventoCancelamento enviEvento = CancelamentoUtil.montaCancelamento(evento, config);
        TRetEnvEventoCancelamento retorno = br.com.swconsultoria.nfe.Nfe.cancelarNfe(
                config, enviEvento, true, DocumentoEnum.NFE);
        RetornoUtil.validaCancelamento(retorno);

        if (retorno.getRetEvento() == null || retorno.getRetEvento().isEmpty()
                || retorno.getRetEvento().get(0).getInfEvento() == null) {
            throw new IllegalStateException("SEFAZ nao retornou o resultado do cancelamento");
        }
        var resultado = retorno.getRetEvento().get(0).getInfEvento();
        nota.setStatus("CANCELADA");
        if (resultado.getNProt() != null && !resultado.getNProt().isBlank()) {
            nota.setProtocolo(resultado.getNProt());
        }
        nota.setXml(XmlNfeUtil.objectToXml(retorno));
        nfeRepository.save(nota);
        return resultado.getNProt();
    }

    @Override
    public Page<Nfe> listar(Long empresaId, Pageable pageable) {
        return nfeRepository.findByEmpresaIdOrderByDataEmissaoDesc(empresaId, pageable);
    }

    @Override
    public Nfe consultarPersistidaPorId(Long empresaId, Long id) {
        return nfeRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "NF-e nao encontrada para a empresa: " + id));
    }

    @Override
    public Nfe consultarPersistida(Long empresaId, String chaveAcesso) {
        if (chaveAcesso == null || !chaveAcesso.matches("\\d{44}")) {
            throw new IllegalArgumentException("Chave de acesso deve conter 44 digitos");
        }
        return nfeRepository.findByEmpresaIdAndChaveAcessoAndDeletedAtIsNull(empresaId, chaveAcesso)
                .orElseThrow(() -> new IllegalArgumentException(
                        "NF-e nao encontrada para a empresa: " + chaveAcesso));
    }

    @Override
    public String consultarSituacao(Long empresaId, String chaveAcesso) throws Exception {
        if (chaveAcesso == null || !chaveAcesso.matches("\\d{44}")) {
            throw new IllegalArgumentException("Chave de acesso deve conter 44 digitos");
        }
        ConfiguracoesNfe config = carregarConfiguracoes(empresaId);
        TRetConsSitNFe retorno = br.com.swconsultoria.nfe.Nfe.consultaXml(
                config, chaveAcesso, DocumentoEnum.NFE);
        return XmlNfeUtil.objectToXml(retorno);
    }

    private int proximoNumero(Long empresaId, int serie) {
        return nfeRepository.findTopByEmpresaIdAndSerieAndDeletedAtIsNullOrderByNumeroDesc(
                        empresaId, String.valueOf(serie))
                .map(Nfe::getNumero).map(n -> Math.toIntExact(n + 1)).orElse(1);
    }

    private String normalizarDocumento(String documento, int tamanho) {
        if (documento == null) throw new IllegalArgumentException("CNPJ do emitente nao cadastrado");
        String digitos = documento.replaceAll("\\D", "");
        if (digitos.length() != tamanho) throw new IllegalStateException("CNPJ do emitente invalido");
        return digitos;
    }

    private String normalizarEan(String ean) {
        if (ean == null || ean.isBlank() || "SEM GTIN".equalsIgnoreCase(ean.trim())) return null;
        return ean.trim();
    }

    private BigDecimal decimal(String valor) {
        if (valor == null || valor.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(valor);
    }

    private ConfiguracoesNfe carregarConfiguracoes(Long empresaId) throws Exception {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa nao encontrada: " + empresaId));
        return carregarConfiguracoes(empresa);
    }

    private ConfiguracoesNfe carregarConfiguracoes(Empresa empresa) throws Exception {
        if (empresa.getUf() == null || empresa.getUf().isBlank()) {
            throw new IllegalStateException("UF da empresa nao cadastrada para emissao de NF-e");
        }
        EstadosEnum uf;
        try {
            uf = EstadosEnum.valueOf(empresa.getUf().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "UF da empresa nao suportada pela biblioteca NF-e: " + empresa.getUf(), ex);
        }
        return sefazConfig.montar(empresa.getId(), uf);
    }
}
