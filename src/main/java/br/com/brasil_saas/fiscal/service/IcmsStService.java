package br.com.brasil_saas.fiscal.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calculadora ICMS-ST (substituição tributária) por MVA ajustada.
 * baseST = baseOperacao * (1 + MVA/100)
 * icmsST = baseST * aliqInterna/100 - icmsOperacao
 * Não grava apuração; apoio ao faturamento/pedido.
 */
@Service
public class IcmsStService {

    public Map<String, Object> calcular(BigDecimal baseOperacao, BigDecimal aliqInter,
                                        BigDecimal aliqInterna, BigDecimal mva, BigDecimal reducaoBasePct) {
        if (baseOperacao == null || baseOperacao.signum() < 0) {
            throw new IllegalArgumentException("Base da operação inválida");
        }
        BigDecimal inter = percentual(aliqInter, "Alíquota interestadual", true);
        BigDecimal interna = percentual(aliqInterna, "Alíquota interna", true);
        BigDecimal m = percentual(mva, "MVA", false);
        BigDecimal red = percentual(reducaoBasePct == null ? BigDecimal.ZERO : reducaoBasePct, "Redução de base", true);

        BigDecimal baseOp = baseOperacao;
        if (red.signum() > 0) {
            baseOp = baseOperacao.multiply(BigDecimal.ONE.subtract(red.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal icmsOperacao = baseOp.multiply(inter).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal baseSt = baseOp.multiply(BigDecimal.ONE.add(m.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal icmsStCheio = baseSt.multiply(interna).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal icmsSt = icmsStCheio.subtract(icmsOperacao);
        if (icmsSt.signum() < 0) icmsSt = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseOperacao", baseOp);
        out.put("aliqInterestadual", inter);
        out.put("aliqInterna", interna);
        out.put("mva", m);
        out.put("reducaoBasePct", red);
        out.put("icmsOperacao", icmsOperacao);
        out.put("baseSt", baseSt);
        out.put("icmsSt", icmsSt);
        out.put("totalIcms", icmsOperacao.add(icmsSt));
        out.put("observacao", "Cálculo por MVA. Confirme MVA/alíquotas no protocolo ICMS da UF e CEST.");
        return out;
    }

    private static BigDecimal percentual(BigDecimal v, String campo, boolean limitado) {
        if (v == null || v.signum() < 0 || (limitado && v.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException(campo + " inválida");
        }
        return v;
    }
}
