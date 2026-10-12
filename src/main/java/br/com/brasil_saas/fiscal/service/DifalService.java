package br.com.brasil_saas.fiscal.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ICMS DIFAL (EC 87/2015) — operação interestadual para consumidor final não contribuinte.
 * Partilha: 100% UF destino desde 2019. FCP separado (até 2%).
 * Não substitui apuração completa; é calculadora de apoio para o faturamento.
 */
@Service
public class DifalService {

    public Map<String, Object> calcular(BigDecimal base, BigDecimal aliqInter, BigDecimal aliqInterna, BigDecimal aliqFcp) {
        if (base == null || base.signum() < 0) {
            throw new IllegalArgumentException("Base de cálculo inválida");
        }
        BigDecimal inter = n(aliqInter);
        BigDecimal interna = n(aliqInterna);
        BigDecimal fcp = n(aliqFcp);
        // se interna < inter, difal fica zero no clamp abaixo
        BigDecimal icmsOrigem = base.multiply(inter).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal difal = base.multiply(interna.subtract(inter)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (difal.signum() < 0) difal = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal valorFcp = base.multiply(fcp).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal totalDestino = difal.add(valorFcp);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("base", base.setScale(2, RoundingMode.HALF_UP));
        out.put("aliqInterestadual", inter);
        out.put("aliqInternaDestino", interna);
        out.put("aliqFcp", fcp);
        out.put("icmsOrigem", icmsOrigem);
        out.put("difal", difal);
        out.put("fcp", valorFcp);
        out.put("totalUfDestino", totalDestino);
        out.put("partilhaUfDestinoPct", 100);
        out.put("observacao", "Partilha 100% UF destino (a partir de 2019). Validar alíquotas no CAD/SEFAZ.");
        return out;
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
