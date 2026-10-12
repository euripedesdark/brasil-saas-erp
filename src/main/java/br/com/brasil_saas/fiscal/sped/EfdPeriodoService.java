package br.com.brasil_saas.fiscal.sped;
import br.com.swconsultoria.efd.icms.registros.EfdIcms;
import br.com.swconsultoria.efd.icms.registros.bloco0.Bloco0;
import br.com.swconsultoria.efd.icms.registros.blocoC.BlocoC;
import br.com.swconsultoria.efd.icms.registros.blocoC.RegistroC001;
import br.com.swconsultoria.efd.icms.registros.blocoC.RegistroC100;
import br.com.swconsultoria.efd.icms.registros.blocoC.RegistroC170;
import br.com.swconsultoria.efd.icms.registros.blocoE.BlocoE;
import br.com.swconsultoria.efd.icms.registros.blocoE.RegistroE001;
import br.com.swconsultoria.efd.icms.registros.blocoE.RegistroE100;
import br.com.swconsultoria.efd.icms.registros.blocoE.RegistroE110;
import br.com.brasil_saas.cadastro.model.Pessoa;
import br.com.brasil_saas.cadastro.repository.PessoaRepository;
import br.com.brasil_saas.fiscal.sped.SpedEfdController.PedidoEfd;
import br.com.brasil_saas.fiscal.model.Nfe;
import br.com.brasil_saas.fiscal.model.NfeItem;
import br.com.brasil_saas.fiscal.repository.NfeItemRepository;
import br.com.brasil_saas.fiscal.repository.NfeRepository;
import br.com.brasil_saas.fiscal.model.SpedFiscal;
import br.com.brasil_saas.fiscal.repository.SpedFiscalRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Service @RequiredArgsConstructor
public class EfdPeriodoService {
    private final NfeRepository nfes;
    private final NfeItemRepository itens;
    private final PessoaRepository pessoas;
    private final SpedEfdIcmsService efd;
    private final SpedFiscalRepository historico;
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("ddMMyyyy");
    @Transactional(readOnly = true)
    public Map<String, Object> gerarPeriodo(Long empresaId, PedidoEfd pedido) {
        LocalDate ini = inicio(pedido.competencia());
        LocalDateTime de = ini.atStartOfDay();
        LocalDateTime ate = ini.plusMonths(1).atStartOfDay();
        List<Nfe> notas = new ArrayList<>();
        notas.addAll(nfes.findByEmpresaIdAndTipoOperacaoAndDataEmissaoBetweenAndDeletedAtIsNull(empresaId, "S", de, ate));
        notas.addAll(nfes.findByEmpresaIdAndTipoOperacaoAndDataEmissaoBetweenAndDeletedAtIsNull(empresaId, "E", de, ate));
        notas = notas.stream().filter(n -> !cancelada(n.getStatus())).sorted((a, b) -> b.getDataEmissao().compareTo(a.getDataEmissao())).toList();
        if (notas.isEmpty()) throw new BusinessException("Sem documentos fiscais no periodo");
        EfdIcms documento = new EfdIcms();
        Bloco0 b0 = new Bloco0();
        documento.setBloco0(b0);
        b0.setRegistro0000(efd.cabecalho(pedido.competencia(), pedido.cnpj(), pedido.nome(), pedido.uf(), pedido.ie(), pedido.codMun(), pedido.im(), pedido.indPerfil(), pedido.indAtiv()));
        if (pedido.cep() != null) b0.setRegistro0100(efd.contadorEmitente(pedido.nome(), pedido.cnpj(), pedido.cep(), pedido.endereco(), pedido.numero(), pedido.complemento(), pedido.bairro(), pedido.telefone(), pedido.email(), pedido.codMun()));
        Map<Long, String> partes = new LinkedHashMap<>();
        Map<String, NfeItem> prods = new LinkedHashMap<>();
        for (Nfe n : notas) {
            if (n.getPessoaId() != null && !partes.containsKey(n.getPessoaId())) {
                Pessoa p = pessoas.findById(n.getPessoaId()).filter(x -> empresaId.equals(x.getEmpresaId())).orElse(null);
                if (p != null) {
                    String cod = "P" + p.getId();
                    boolean fisica = "FISICA".equalsIgnoreCase(p.getTipo());
                    b0.getRegistro0150().add(efd.participante(cod, p.getNome(), fisica ? null : p.getDocumento(), fisica ? p.getDocumento() : null, null, null));
                    partes.put(n.getPessoaId(), cod);
                }
            }
            for (NfeItem it : itens.findByNfeIdOrderByNumeroItem(n.getId())) {
                String cod = it.getCodigoProduto() != null && !it.getCodigoProduto().isBlank() ? it.getCodigoProduto() : "PROD-" + it.getProdutoId();
                prods.putIfAbsent(cod, it);
            }
        }
        for (var e : prods.entrySet()) {
            NfeItem it = e.getValue();
            b0.getRegistro0200().add(efd.produto(e.getKey(), e.getKey(), it.getUnidade() == null ? "UN" : it.getUnidade(), it.getNcm(), it.getCest(), it.getAliquotaIcms() == null ? null : it.getAliquotaIcms().toPlainString(), null));
        }
        BlocoC bc = new BlocoC();
        documento.setBlocoC(bc);

        // Bloco E — apuração do ICMS do período (E001 + E100 + E110).
        // Débito = ICMS das saídas; crédito = ICMS das entradas. Sem saldo anterior
        // (empresa sem histórico de EFD) e sem ajustes/deduções especiais.
        BigDecimal debIcms = BigDecimal.ZERO;
        BigDecimal credIcms = BigDecimal.ZERO;
        for (Nfe n : notas) {
            if (cancelada(n.getStatus())) continue;
            BigDecimal v = n.getValorIcms() == null ? BigDecimal.ZERO : n.getValorIcms();
            if ("S".equalsIgnoreCase(n.getTipoOperacao())) debIcms = debIcms.add(v);
            else if ("E".equalsIgnoreCase(n.getTipoOperacao())) credIcms = credIcms.add(v);
        }
        BlocoE be = new BlocoE();
        RegistroE001 e001 = new RegistroE001();
        e001.setInd_mov(debIcms.signum() == 0 && credIcms.signum() == 0 ? "1" : "0");
        be.setRegistroE001(e001);
        if (e001.getInd_mov().equals("0")) {
            LocalDate fim = ini.plusMonths(1).minusDays(1);
            RegistroE100 e100 = new RegistroE100();
            e100.setDt_ini(ini.format(DIA));
            e100.setDt_fin(fim.format(DIA));
            RegistroE110 e110 = new RegistroE110();
            String zero = "0,00";
            e110.setVl_tot_debitos(moedaOuZero(debIcms));
            e110.setVl_aj_debitos(zero);
            e110.setVl_tot_aj_debitos(zero);
            e110.setVl_estornos_cred(zero);
            e110.setVl_tot_creditos(moedaOuZero(credIcms));
            e110.setVl_aj_creditos(zero);
            e110.setVl_tot_aj_creditos(zero);
            e110.setVl_estornos_deb(zero);
            e110.setVl_sld_credor_ant(zero);
            BigDecimal sld = debIcms.subtract(credIcms);
            if (sld.signum() > 0) {
                e110.setVl_sld_apurado(moedaOuZero(sld));
                e110.setVl_tot_ded(zero);
                e110.setVl_icms_recolher(moedaOuZero(sld));
                e110.setVl_sld_credor_transportar(zero);
            } else {
                e110.setVl_sld_apurado(zero);
                e110.setVl_tot_ded(zero);
                e110.setVl_icms_recolher(zero);
                e110.setVl_sld_credor_transportar(moedaOuZero(sld.abs()));
            }
            e110.setDeb_esp(zero);
            e100.setRegistroE110(e110);
            be.getRegistroE100().add(e100);
        }
        documento.setBlocoE(be);

        RegistroC001 c001 = new RegistroC001();
        c001.setInd_mov("0");
        bc.setRegistroC001(c001);
        int docs = 0; int ignoradas = 0;
        for (Nfe n : notas) {
            String codPart = n.getPessoaId() == null ? null : partes.get(n.getPessoaId());
            if (codPart == null) { ignoradas++; continue; }
            boolean entrada = "E".equalsIgnoreCase(n.getTipoOperacao());
            RegistroC100 c = new RegistroC100();
            c.setInd_oper(entrada ? "0" : "1");
            c.setInd_emit(entrada ? "1" : "0");
            c.setCod_part(codPart);
            c.setCod_mod("55");
            c.setCod_sit("00");
            c.setSer(n.getSerie());
            c.setNum_doc(n.getNumero() == null ? null : String.valueOf(n.getNumero()));
            c.setChv_nfe(n.getChaveAcesso());
            c.setDt_doc(n.getDataEmissao() == null ? null : n.getDataEmissao().format(DIA));
            c.setDt_e_s(n.getDataEmissao() == null ? null : n.getDataEmissao().format(DIA));
            c.setVl_doc(moeda(n.getValorTotal()));
            c.setVl_merc(moeda(n.getValorProdutos()));
            c.setVl_desc(moeda(n.getValorDesconto()));
            c.setVl_frt(moeda(n.getValorFrete()));
            c.setVl_icms(moeda(n.getValorIcms()));
            c.setVl_ipi(moeda(n.getValorIpi()));
            c.setVl_pis(moeda(n.getValorPis()));
            c.setVl_cofins(moeda(n.getValorCofins()));
            for (NfeItem it : itens.findByNfeIdOrderByNumeroItem(n.getId())) {
                RegistroC170 i170 = new RegistroC170();
                i170.setNum_item(it.getNumeroItem() == null ? null : String.valueOf(it.getNumeroItem()));
                i170.setCod_item(it.getCodigoProduto() != null && !it.getCodigoProduto().isBlank() ? it.getCodigoProduto() : "PROD-" + it.getProdutoId());
                i170.setQtd(it.getQuantidade() == null ? null : it.getQuantidade().toPlainString());
                i170.setUnid(it.getUnidade());
                i170.setVl_item(moeda(it.getValorTotal()));
                i170.setCfop(it.getCfop() != null ? it.getCfop() : n.getCfop());
                i170.setAliq_icms(it.getAliquotaIcms() == null ? null : it.getAliquotaIcms().toPlainString());
                i170.setVl_icms(moeda(it.getValorIcms()));
                i170.setAliq_ipi(it.getAliquotaIpi() == null ? null : it.getAliquotaIpi().toPlainString());
                i170.setVl_ipi(moeda(it.getValorIpi()));
                c.getRegistroC170().add(i170);
            }
            bc.getRegistroC100().add(c);
            docs++;
        }
        if (docs == 0) throw new BusinessException("Nenhum documento com participante identificado");
        String conteudo = efd.gerar(documento);
        List<String> linhas = List.of(conteudo.split("\\R"));
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("sucesso", true);
        saida.put("competencia", pedido.competencia());
        saida.put("documentos", docs);
        saida.put("ignoradas", ignoradas);
        saida.put("totalLinhas", linhas.size());
        saida.put("icmsDebito", debIcms.setScale(2, RoundingMode.HALF_UP));
        saida.put("icmsCredito", credIcms.setScale(2, RoundingMode.HALF_UP));
        saida.put("icmsRecolher", debIcms.subtract(credIcms).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        saida.put("conteudo", conteudo);
        SpedFiscal reg = historico.findByEmpresaIdAndCompetencia(empresaId, pedido.competencia()).orElse(null);
        if (reg == null) { reg = new SpedFiscal(); reg.setEmpresaId(empresaId); reg.setCompetencia(pedido.competencia()); }
        reg.setStatus("GERADO"); reg.setGeradoAt(java.time.LocalDateTime.now());
        historico.save(reg);
        return saida;
    }
    private LocalDate inicio(String competencia) {
        try {
            String[] q = competencia.split("/");
            return LocalDate.of(Integer.parseInt(q[1]), Integer.parseInt(q[0]), 1);
        } catch (Exception e) { throw new BusinessException("Competencia invalida, use MM/AAAA"); }
    }
    private boolean cancelada(String status) {
        return status != null && status.trim().toUpperCase().startsWith("CANCEL");
    }
    private String moedaOuZero(BigDecimal v) {
        return v == null ? "0,00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }
    private String moeda(BigDecimal v) {
        if (v == null || v.signum() == 0) return null;
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }
}