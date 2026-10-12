package br.com.brasil_saas.bi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BiResumo(LocalDate inicio, LocalDate fim, LocalDate referencia, List<Area> areas) {
    public record Area(String codigo, List<Indicador> indicadores, List<Ponto> evolucao) {
        public Area(String codigo, List<Indicador> indicadores) { this(codigo, indicadores, List.of()); }
    }
    public record Ponto(LocalDate mes, BigDecimal valor, String unidade) {}
    public record Indicador(String codigo, BigDecimal valor, String unidade) {}
}
