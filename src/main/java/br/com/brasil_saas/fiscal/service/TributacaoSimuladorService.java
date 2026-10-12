package br.com.brasil_saas.fiscal.service;

import br.com.brasil_saas.fiscal.model.RegraTributaria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Simula impostos de uma linha (base) com regra resolvida + DIFAL + ST.
 * Não emite documento fiscal — apoio a pedido/orçamento.
 */
@Service
@RequiredArgsConstructor
public class TributacaoSimuladorService {

    private final RegraTributariaService regras;
    private final DifalService difalService;
    private final IcmsStService icmsStService;

    public Map<String, Object> simular(Long empresaId, BigDecimal base, String ncm, String cfop,
                                       String ufOrigem, String ufDestino,
                                       Boolean consumidorFinal, Boolean contribuinte) {
        if (base == null || base.signum() < 0) {
            throw new IllegalArgumentException("Base inválida");
        }
        Optional<RegraTributaria> opt = regras.resolverOpcional(empresaId, ncm, cfop, ufOrigem, ufDestino);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("base", base.setScale(2, RoundingMode.HALF_UP));
        out.put("ncm", ncm);
        out.put("cfop", cfop);
        out.put("ufOrigem", ufOrigem);
        out.put("ufDestino", ufDestino);

        if (opt.isEmpty()) {
            out.put("regraEncontrada", false);
            out.put("mensagem", "Nenhuma regra ativa — informe alíquotas manuais ou cadastre regra");
            return out;
        }
        RegraTributaria r = opt.get();
        out.put("regraEncontrada", true);
        out.put("regraId", r.getId());
        out.put("regraNome", r.getNome());
        out.put("cstIcms", r.getCstIcms());

        BigDecimal aliqIcms = nz(r.getAliquotaIcms());
        BigDecimal icms = pct(base, aliqIcms);
        out.put("aliquotaIcms", aliqIcms);
        out.put("icms", icms);
        out.put("ipi", pct(base, nz(r.getAliquotaIpi())));
        out.put("pis", pct(base, nz(r.getAliquotaPis())));
        out.put("cofins", pct(base, nz(r.getAliquotaCofins())));

        boolean interestadual = ufOrigem != null && ufDestino != null
                && !ufOrigem.equalsIgnoreCase(ufDestino);
        boolean cf = Boolean.TRUE.equals(consumidorFinal);
        boolean naoContrib = contribuinte == null || !contribuinte;

        if (interestadual && cf && naoContrib) {
            BigDecimal interna = r.getAliquotaInterna() != null ? r.getAliquotaInterna() : aliqIcms;
            Map<String, Object> d = difalService.calcular(base, aliqIcms, interna, nz(r.getAliquotaFcp()));
            out.put("difal", d);
        }
        if (r.getMva() != null && r.getMva().signum() > 0) {
            BigDecimal interna = r.getAliquotaInterna() != null ? r.getAliquotaInterna()
                    : (r.getAliquotaSt() != null ? r.getAliquotaSt() : aliqIcms);
            Map<String, Object> st = icmsStService.calcular(base, aliqIcms, interna, r.getMva(), nz(r.getReducaoBasePct()));
            out.put("icmsSt", st);
        }
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal aliq) {
        return base.multiply(aliq).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public Map<String, Object> simularItens(Long empresaId, String ufOrigem, String ufDestino,
                                           Boolean consumidorFinal, Boolean contribuinte,
                                           List<Map<String, Object>> itens) {
        List<Map<String, Object>> linhas = new java.util.ArrayList<>();
        BigDecimal totIcms = BigDecimal.ZERO;
        BigDecimal totPis = BigDecimal.ZERO;
        BigDecimal totCofins = BigDecimal.ZERO;
        BigDecimal totDifal = BigDecimal.ZERO;
        BigDecimal totSt = BigDecimal.ZERO;
        if (itens != null) {
            for (Map<String, Object> it : itens) {
                BigDecimal base = toBd(it.get("base"));
                String ncm = str(it.get("ncm"));
                String cfop = str(it.get("cfop"));
                Map<String, Object> linha = simular(empresaId, base, ncm, cfop, ufOrigem, ufDestino, consumidorFinal, contribuinte);
                linha.put("itemRef", it.get("ref"));
                linhas.add(linha);
                totIcms = totIcms.add(toBd(linha.get("icms")));
                totPis = totPis.add(toBd(linha.get("pis")));
                totCofins = totCofins.add(toBd(linha.get("cofins")));
                if (linha.get("difal") instanceof Map<?, ?> d) {
                    totDifal = totDifal.add(toBd(d.get("difal")));
                }
                if (linha.get("icmsSt") instanceof Map<?, ?> s) {
                    totSt = totSt.add(toBd(s.get("icmsSt")));
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("linhas", linhas);
        out.put("totalIcms", totIcms);
        out.put("totalPis", totPis);
        out.put("totalCofins", totCofins);
        out.put("totalDifal", totDifal);
        out.put("totalIcmsSt", totSt);
        out.put("totalImpostos", totIcms.add(totPis).add(totCofins).add(totDifal).add(totSt));
        return out;
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

}
