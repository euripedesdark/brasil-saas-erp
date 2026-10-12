package br.com.brasil_saas.contabilidade.service;
import br.com.brasil_saas.contabilidade.model.CtbLancamento;
import br.com.brasil_saas.contabilidade.model.CtbPartida;
import br.com.brasil_saas.contabilidade.repository.CtbLancamentoRepository;
import br.com.brasil_saas.contabilidade.repository.CtbPartidaRepository;
import br.com.brasil_saas.financeiro.model.PlanoContas;
import br.com.brasil_saas.financeiro.repository.PlanoContasRepository;
import br.com.brasil_saas.fiscal.model.Ecd;
import br.com.brasil_saas.fiscal.repository.EcdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
@Service @RequiredArgsConstructor
public class EcdService {
    private final CtbLancamentoRepository lancamentos;
    private final CtbPartidaRepository partidas;
    private final PlanoContasRepository contas;
    private final EcdRepository historico;
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("ddMMyyyy");
    @Transactional(readOnly = true)
    public Map<String, Object> gerar(Long empresaId, int exercicio, String cnpj, String nome, String uf, String codMun) {
        LocalDate de = LocalDate.of(exercicio, 1, 1);
        LocalDate ate = LocalDate.of(exercicio, 12, 31);
        List<CtbLancamento> lcts = lancamentos.findByEmpresaIdAndDeletedAtIsNull(empresaId).stream()
                .filter(l -> "LANCADO".equals(l.getStatus()) && l.getData() != null && !l.getData().isBefore(de) && !l.getData().isAfter(ate))
                .sorted((a, b) -> { int c = a.getData().compareTo(b.getData()); return c != 0 ? c : a.getId().compareTo(b.getId()); }).toList();
        List<PlanoContas> plano = contas.findByEmpresaIdAndAtivaTrueAndDeletedAtIsNullOrderByCodigo(empresaId);
        Map<Long, PlanoContas> porId = new LinkedHashMap<>();
        for (PlanoContas c : plano) porId.put(c.getId(), c);
        List<String> arq = new ArrayList<>();
        arq.add(lin("0000", "LECD001", "0", de.format(DIA), ate.format(DIA), nome, digitos(cnpj), uf, null, codMun, null, "0", "0", "0", null, "0", "0"));
        arq.add(lin("0001", "0"));
        arq.add(lin("I001", "0"));
        for (PlanoContas c : plano) {
            String sup = null;
            if (c.getContaPaiId() != null && porId.containsKey(c.getContaPaiId())) sup = porId.get(c.getContaPaiId()).getCodigo();
            arq.add(lin("I050", null, c.getCodigo(), null, sup, c.getDescricao(), c.getNivel() == null ? null : String.valueOf(c.getNivel()), nat(c.getNatureza()), null));
        }
        Map<Long, BigDecimal[]> acumulado = new LinkedHashMap<>();
        Map<Long, List<CtbLancamento>> porMes = new TreeMap<>();
        for (CtbLancamento l : lcts) porMes.computeIfAbsent((long) l.getData().getMonthValue(), k -> new ArrayList<>()).add(l);
        for (int mes = 1; mes <= 12; mes++) {
            LocalDate ini = LocalDate.of(exercicio, mes, 1);
            LocalDate fim = ini.withDayOfMonth(ini.lengthOfMonth());
            Map<Long, BigDecimal[]> mov = new LinkedHashMap<>();
            for (CtbLancamento l : porMes.getOrDefault((long) mes, List.of())) {
                for (CtbPartida p : partidas.findByLancamentoIdAndEmpresaIdAndDeletedAtIsNull(l.getId(), empresaId)) {
                    BigDecimal[] v = mov.computeIfAbsent(p.getContaId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    v[0] = v[0].add(nz(p.getDebito()));
                    v[1] = v[1].add(nz(p.getCredito()));
                }
            }
            for (PlanoContas c : plano) {
                if (!"A".equals(c.getTipo())) continue;
                BigDecimal[] ant = acumulado.getOrDefault(c.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                BigDecimal[] m = mov.getOrDefault(c.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                BigDecimal sldIni = ant[0].subtract(ant[1]);
                BigDecimal sldFin = sldIni.add(m[0]).subtract(m[1]);
                if (sldIni.signum() == 0 && m[0].signum() == 0 && m[1].signum() == 0 && sldFin.signum() == 0) continue;
                arq.add(lin("I150", ini.format(DIA), fim.format(DIA), c.getCodigo(), null, moeda(sldIni.abs()), dc(sldIni), moeda(m[0]), moeda(m[1]), moeda(sldFin.abs()), dc(sldFin)));
                acumulado.put(c.getId(), new BigDecimal[]{ant[0].add(m[0]), ant[1].add(m[1])});
            }
        }
        for (CtbLancamento l : lcts) {
            List<CtbPartida> pts = partidas.findByLancamentoIdAndEmpresaIdAndDeletedAtIsNull(l.getId(), empresaId);
            BigDecimal total = BigDecimal.ZERO;
            for (CtbPartida pt : pts) total = total.add(nz(pt.getDebito()));
            arq.add(lin("I200", String.valueOf(l.getId()), l.getData().format(DIA), moeda(total), "N"));
            for (CtbPartida pt : pts) {
                PlanoContas c = porId.get(pt.getContaId());
                boolean deb = nz(pt.getDebito()).signum() > 0;
                String hist = pt.getHistorico() != null && !pt.getHistorico().isBlank() ? pt.getHistorico() : l.getHistorico();
                arq.add(lin("I250", c == null ? "SEM_CONTA" : c.getCodigo(), null, moeda(deb ? pt.getDebito() : pt.getCredito()), deb ? "D" : "C", null, null, hist));
            }
        }
        Map<String, Integer> cont = new LinkedHashMap<>();
        for (String linha : arq) {
            String reg = linha.split("\\|", 3)[1];
            cont.merge(reg, 1, Integer::sum);
        }
        arq.add(lin("I990", String.valueOf(arq.size() + 1)));
        cont.put("I990", 1);
        arq.add(lin("9001", "0"));
        for (var e : cont.entrySet()) arq.add(lin("9900", e.getKey(), String.valueOf(e.getValue())));
        arq.add(lin("9900", "9900", String.valueOf(cont.size() + 1)));
        arq.add(lin("9900", "9990", "1"));
        arq.add(lin("9990", String.valueOf(cont.size() + 2)));
        int total = arq.size() + 1;
        arq.add(lin("9999", String.valueOf(total)));
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("sucesso", true);
        saida.put("exercicio", exercicio);
        saida.put("lancamentos", lcts.size());
        saida.put("contas", plano.size());
        saida.put("totalLinhas", total);
        saida.put("demonstracoes", "PENDENTE");
        saida.put("conteudo", String.join("\n", arq));
        String exercicioStr = String.valueOf(exercicio);
        Ecd reg = historico.findByEmpresaIdAndExercicio(empresaId, exercicioStr).orElse(null);
        if (reg == null) { reg = new Ecd(); reg.setEmpresaId(empresaId); reg.setExercicio(exercicioStr); }
        reg.setStatus("GERADA");
        historico.save(reg);
        return saida;
    }
    private String lin(String reg, Object... campos) {
        StringBuilder sb = new StringBuilder();
        sb.append("|").append(reg);
        for (Object c : campos) sb.append("|").append(c == null ? "" : c.toString().replace("|", " ").trim());
        sb.append("|");
        return sb.toString();
    }
    private String nat(String n) { return "C".equalsIgnoreCase(n) ? "C" : "D"; }
    private String dc(BigDecimal v) { return v.signum() < 0 ? "C" : "D"; }
    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private String moeda(BigDecimal v) {
        if (v == null || v.signum() == 0) return "0,00";
        return v.abs().setScale(2, RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }
    private String digitos(String v) { return v == null ? "" : v.replaceAll("\\D", ""); }
}
