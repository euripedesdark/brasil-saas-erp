package br.com.brasil_saas.financeiro.service;
import br.com.brasil_saas.financeiro.model.Titulo;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
@Service @RequiredArgsConstructor
public class FluxoCaixaService {
    private final TituloRepository titulos;
    @Transactional(readOnly = true) public List<Map<String, Object>> projetar(Long empresaId, int dias) {
        if (dias <= 0) dias = 90;
        if (dias > 365) dias = 365;
        LocalDate hoje = LocalDate.now();
        Map<Long, BigDecimal[]> semanas = new TreeMap<>();
        for (Titulo t : titulos.findByEmpresaIdAndDeletedAtIsNullOrderByDataVencimento(empresaId)) {
            if (!"ABERTO".equals(t.getStatus()) && !"PARCIAL".equals(t.getStatus())) continue;
            if (t.getDataVencimento() == null) continue;
            long diff = ChronoUnit.DAYS.between(hoje, t.getDataVencimento());
            if (diff < 0 || diff > dias) continue;
            long sem = diff / 7;
            BigDecimal[] v = semanas.computeIfAbsent(sem, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal s = t.getValorSaldo() == null ? BigDecimal.ZERO : t.getValorSaldo();
            if ("R".equals(t.getTipo())) v[0] = v[0].add(s); else v[1] = v[1].add(s);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (var e : semanas.entrySet()) {
            BigDecimal liq = e.getValue()[0].subtract(e.getValue()[1]);
            acumulado = acumulado.add(liq);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("semana", "S" + (e.getKey() + 1));
            m.put("entradas", e.getValue()[0]);
            m.put("saidas", e.getValue()[1]);
            m.put("liquido", liq);
            m.put("acumulado", acumulado);
            out.add(m);
        }
        return out;
    }
}
