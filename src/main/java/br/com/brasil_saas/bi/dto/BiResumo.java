package br.com.brasil_saas.bi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BiResumo(LocalDate inicio, LocalDate fim, LocalDate referencia, List<Area> areas) {
    public record Area(String codigo, List<Indicador> indicadores) {}
    public record Indicador(String codigo, BigDecimal valor, String unidade) {}
}
