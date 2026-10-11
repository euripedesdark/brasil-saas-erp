package br.com.brasil_saas.supplychain.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;

/**
 * ATP (disponivel para prometer) com entradas futuras. Classe pura.
 *
 * <p>disponivelEm(d) = estoque - reservas + entradas ate d - saidas
 * comprometidas ate d. prometivel(d) e o menor disponivel de d em diante:
 * prometer mais que isso numa data faria uma saida posterior ficar sem saldo.
 */
public final class AtpTemporalCalculator {

    public record Movimento(LocalDate data, BigDecimal quantidade) {}

    private AtpTemporalCalculator() {}

    public static BigDecimal disponivelEm(LocalDate data, BigDecimal estoque, BigDecimal reservas,
                                          List<Movimento> entradas, List<Movimento> saidas) {
        BigDecimal v = nz(estoque).subtract(nz(reservas));
        for (Movimento m : lista(entradas)) if (!m.data().isAfter(data)) v = v.add(m.quantidade());
        for (Movimento m : lista(saidas)) if (!m.data().isAfter(data)) v = v.subtract(m.quantidade());
        return v;
    }

    public static BigDecimal prometivel(LocalDate data, BigDecimal estoque, BigDecimal reservas,
                                        List<Movimento> entradas, List<Movimento> saidas) {
        TreeSet<LocalDate> pontos = new TreeSet<>();
        pontos.add(data);
        for (Movimento m : lista(entradas)) if (m.data().isAfter(data)) pontos.add(m.data());
        for (Movimento m : lista(saidas)) if (m.data().isAfter(data)) pontos.add(m.data());
        BigDecimal menor = disponivelEm(data, estoque, reservas, entradas, saidas);
        for (LocalDate d : pontos) {
            BigDecimal v = disponivelEm(d, estoque, reservas, entradas, saidas);
            if (v.compareTo(menor) < 0) menor = v;
        }
        return menor.max(BigDecimal.ZERO);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static List<Movimento> lista(List<Movimento> l) { return l == null ? List.of() : l; }
}
