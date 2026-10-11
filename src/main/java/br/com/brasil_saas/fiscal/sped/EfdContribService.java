package br.com.brasil_saas.fiscal.sped;
import br.com.swconsultoria.efd.contribuicoes.bo.GerarEfdContribuicoes;
import br.com.swconsultoria.efd.contribuicoes.registros.EfdContribuicoes;
import br.com.swconsultoria.efd.contribuicoes.registros.bloco0.Bloco0;
import br.com.swconsultoria.efd.contribuicoes.registros.bloco0.Registro0000;
import br.com.swconsultoria.efd.contribuicoes.registros.bloco0.Registro0001;
import br.com.swconsultoria.efd.contribuicoes.registros.bloco0.Registro0110;
import br.com.swconsultoria.efd.contribuicoes.registros.bloco1.Bloco1;
import br.com.swconsultoria.efd.contribuicoes.registros.bloco1.Registro1001;
import br.com.swconsultoria.efd.contribuicoes.registros.blocoM.BlocoM;
import br.com.swconsultoria.efd.contribuicoes.registros.blocoM.RegistroM001;
import br.com.swconsultoria.efd.contribuicoes.registros.blocoM.RegistroM200;
import br.com.swconsultoria.efd.contribuicoes.registros.blocoM.RegistroM210;
import br.com.swconsultoria.efd.contribuicoes.registros.blocoM.RegistroM600;
import br.com.swconsultoria.efd.contribuicoes.registros.blocoM.RegistroM610;
import br.com.brasil_saas.fiscal.model.Apuracao;
import br.com.brasil_saas.fiscal.model.Imposto;
import br.com.brasil_saas.fiscal.repository.ApuracaoRepository;
import br.com.brasil_saas.fiscal.repository.ImpostoRepository;
import br.com.brasil_saas.fiscal.model.SpedContribuicoes;
import br.com.brasil_saas.fiscal.repository.SpedContribuicoesRepository;
import br.com.brasil_saas.fiscal.sped.SpedEfdController.PedidoEfd;
import br.com.brasil_saas.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Service @RequiredArgsConstructor
public class EfdContribService {
    private final ApuracaoRepository apuracoes;
    private final ImpostoRepository impostos;
    private final SpedContribuicoesRepository historico;
    @Transactional(readOnly = true)
    public Map<String, Object> gerarPeriodo(Long empresaId, PedidoEfd pedido) {
        LocalDate ini = inicio(pedido.competencia());
        String dtIni = String.format("%02d%02d%04d", 1, ini.getMonthValue(), ini.getYear());
        String dtFin = String.format("%02d%02d%04d", ini.lengthOfMonth(), ini.getMonthValue(), ini.getYear());
        Apuracao pis = apuracao(empresaId, pedido.competencia(), "PIS");
        Apuracao cofins = apuracao(empresaId, pedido.competencia(), "COFINS");
        BigDecimal aliqPis = aliquota(empresaId, "PIS");
        BigDecimal aliqCof = aliquota(empresaId, "COFINS");
        EfdContribuicoes documento = new EfdContribuicoes();
        Bloco0 b0 = new Bloco0();
        documento.setBloco0(b0);
        Registro0000 r0000 = new Registro0000();
        r0000.setCod_fin("0");
        r0000.setDt_ini(dtIni);
        r0000.setDt_fin(dtFin);
        r0000.setNome(pedido.nome());
        r0000.setCnpj(digitos(pedido.cnpj()));
        r0000.setUf(pedido.uf());
        r0000.setCod_mun(pedido.codMun());
        r0000.setInd_nat_pj("00");
        r0000.setInd_ativ("0");
        b0.setRegistro0000(r0000);
        Registro0001 r0001 = new Registro0001();
        r0001.setInd_mov("0");
        b0.setRegistro0001(r0001);
        Registro0110 r0110 = new Registro0110();
        r0110.setCod_inc_trib("1");
        boolean temCred = pis.getValorCredito().signum() > 0 || cofins.getValorCredito().signum() > 0;
        r0110.setInd_apro_cred(temCred ? "1" : "0");
        r0110.setCod_tipo_cont("1");
        r0110.setInd_reg_cum("0");
        b0.setRegistro0110(r0110);
        BlocoM bm = new BlocoM();
        documento.setBlocoM(bm);
        RegistroM001 m001 = new RegistroM001();
        m001.setInd_mov("0");
        bm.setRegistroM001(m001);
        RegistroM200 m200 = new RegistroM200();
        m200.setVl_tot_cont_nc_per(moeda(pis.getValorDevido()));
        m200.setVl_tot_cred_desc(moeda(pis.getValorCredito()));
        m200.setVl_tot_cont_nc_dev(moeda(pis.getValorPagar()));
        RegistroM210 m210 = new RegistroM210();
        m210.setCod_cont("01");
        m210.setVl_rec_brt(moeda(pis.getBaseCalculo()));
        m210.setVl_bc_cont(moeda(pis.getBaseCalculo()));
        m210.setAliq_pis_percentual(aliq(aliqPis));
        m210.setVl_cont_apur(moeda(pis.getValorDevido()));
        m210.setVl_cont_per(moeda(pis.getValorPagar()));
        m200.getRegistroM210().add(m210);
        bm.setRegistroM200(m200);
        RegistroM600 m600 = new RegistroM600();
        m600.setVl_tot_cont_nc_per(moeda(cofins.getValorDevido()));
        m600.setVl_tot_cred_desc(moeda(cofins.getValorCredito()));
        m600.setVl_tot_cont_nc_dev(moeda(cofins.getValorPagar()));
        RegistroM610 m610 = new RegistroM610();
        m610.setCod_cont("01");
        m610.setVl_rec_brt(moeda(cofins.getBaseCalculo()));
        m610.setVl_bc_cont(moeda(cofins.getBaseCalculo()));
        m610.setAliq_cofins_percentual(aliq(aliqCof));
        m610.setVl_cont_apur(moeda(cofins.getValorDevido()));
        m610.setVl_cont_per(moeda(cofins.getValorPagar()));
        m600.getRegistroM610().add(m610);
        bm.setRegistroM600(m600);
        Bloco1 b1 = new Bloco1();
        documento.setBloco1(b1);
        Registro1001 r1001 = new Registro1001();
        r1001.setInd_mov("0");
        b1.setRegistro1001(r1001);
        StringBuilder sb = new StringBuilder();
        GerarEfdContribuicoes.gerar(documento, sb);
        String conteudo = sb.toString();
        List<String> linhas = List.of(conteudo.split("\\R"));
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("sucesso", true);
        saida.put("competencia", pedido.competencia());
        saida.put("regime", "NAO_CUMULATIVO");
        saida.put("pisStatus", pis.getStatus());
        saida.put("cofinsStatus", cofins.getStatus());
        saida.put("totalLinhas", linhas.size());
        saida.put("conteudo", conteudo);
        SpedContribuicoes reg = historico.findByEmpresaIdAndCompetencia(empresaId, pedido.competencia()).orElse(null);
        if (reg == null) { reg = new SpedContribuicoes(); reg.setEmpresaId(empresaId); reg.setCompetencia(pedido.competencia()); }
        reg.setStatus("GERADO"); reg.setGeradoAt(java.time.LocalDateTime.now());
        historico.save(reg);
        return saida;
    }
    private Apuracao apuracao(Long empresaId, String competencia, String sigla) {
        Imposto imp = impostos.findByEmpresaIdAndSiglaAndDeletedAtIsNull(empresaId, sigla).orElse(null);
        if (imp == null) throw new BusinessException("Imposto " + sigla + " nao cadastrado");
        Apuracao enc = null; Apuracao aberta = null;
        for (Apuracao a : apuracoes.findByEmpresaIdOrderByCompetenciaDesc(empresaId)) {
            if (a.getImpostoId().equals(imp.getId()) == false || competencia.equals(a.getCompetencia()) == false) continue;
            if ("ENCERRADA".equals(a.getStatus()) || "TRANSMITIDA".equals(a.getStatus())) enc = a;
            else aberta = a;
        }
        if (enc != null) return enc;
        if (aberta != null) return aberta;
        throw new BusinessException("Apure " + sigla + " da competencia " + competencia + " antes de gerar");
    }
    private BigDecimal aliquota(Long empresaId, String sigla) {
        return impostos.findByEmpresaIdAndSiglaAndDeletedAtIsNull(empresaId, sigla).map(Imposto::getAliquotaPadrao).orElse(BigDecimal.ZERO);
    }
    private LocalDate inicio(String competencia) {
        try {
            String[] q = competencia.split("/");
            return LocalDate.of(Integer.parseInt(q[1]), Integer.parseInt(q[0]), 1);
        } catch (Exception e) { throw new BusinessException("Competencia invalida, use MM/AAAA"); }
    }
    private String digitos(String v) { return v == null ? null : v.replaceAll("\\D", ""); }
    private String moeda(BigDecimal v) {
        if (v == null || v.signum() == 0) return null;
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }
    private String aliq(BigDecimal v) {
        if (v == null || v.signum() == 0) return null;
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }
}
