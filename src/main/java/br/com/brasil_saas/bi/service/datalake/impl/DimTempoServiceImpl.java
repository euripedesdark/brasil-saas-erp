package br.com.brasil_saas.bi.service.datalake.impl;

import br.com.brasil_saas.bi.model.datalake.DimTempo;
import br.com.brasil_saas.bi.repository.datalake.DimTempoRepository;
import br.com.brasil_saas.bi.service.datalake.DimTempoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DimTempoServiceImpl implements DimTempoService {

    private final DimTempoRepository dimTempoRepository;

    @Override
    @Transactional
    public DimTempo save(DimTempo dimTempo) {
        return dimTempoRepository.save(dimTempo);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DimTempo> findById(Long id) {
        return dimTempoRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DimTempo> findByData(LocalDate data) {
        return dimTempoRepository.findByData(data);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DimTempo> findAll() {
        return dimTempoRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DimTempo> findByAno(Integer ano) {
        return dimTempoRepository.findByAno(ano);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DimTempo> findByAnoAndMes(Integer ano, Integer mes) {
        return dimTempoRepository.findByAnoAndMes(ano, mes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DimTempo> findByDataBetween(LocalDate start, LocalDate end) {
        return dimTempoRepository.findByDataBetween(start, end);
    }

    @Override
    @Transactional
    public void populateTimeDimension(LocalDate startDate, LocalDate endDate) {
        LocalDate currentDate = startDate;
        List<DimTempo> tempoList = new ArrayList<>();

        while (!currentDate.isAfter(endDate)) {
            if (!dimTempoRepository.findByData(currentDate).isPresent()) {
                DimTempo dimTempo = createDimTempoFromDate(currentDate);
                tempoList.add(dimTempo);
            }
            currentDate = currentDate.plusDays(1);
        }

        if (!tempoList.isEmpty()) {
            dimTempoRepository.saveAll(tempoList);
        }
    }

    @Override
    @Transactional
    public void populateTimeDimensionForYear(Integer year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        populateTimeDimension(startDate, endDate);
    }

    @Override
    @Transactional
    public void populateTimeDimensionForCurrentYear() {
        populateTimeDimensionForYear(LocalDate.now().getYear());
    }

    @Override
    @Transactional
    public void populateTimeDimensionForLastYear() {
        populateTimeDimensionForYear(LocalDate.now().getYear() - 1);
    }

    @Override
    @Transactional
    public void populateTimeDimensionForRange(Integer startYear, Integer endYear) {
        for (int year = startYear; year <= endYear; year++) {
            populateTimeDimensionForYear(year);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DimTempo> getLast30Days() {
        return dimTempoRepository.findByDataGreaterThanEqualOrderByDataAsc(LocalDate.now().minusDays(30));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DimTempo> getLast7Days() {
        return dimTempoRepository.findByDataGreaterThanEqualOrderByDataAsc(LocalDate.now().minusDays(7));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DimTempo> getCurrentYear() {
        return dimTempoRepository.findByAnoOrderByDataAsc(LocalDate.now().getYear());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DimTempo> getCurrentMonth() {
        int currentYear = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();
        return dimTempoRepository.findByAnoAndMes(currentYear, currentMonth);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DimTempo> getCurrentQuarter() {
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentQuarter = (now.getMonthValue() - 1) / 3 + 1;
        
        int startMonth = (currentQuarter - 1) * 3 + 1;
        int endMonth = currentQuarter * 3;
        
        LocalDate startDate = LocalDate.of(currentYear, startMonth, 1);
        LocalDate endDate = LocalDate.of(currentYear, endMonth, 
                endMonth == 12 ? 31 : LocalDate.of(currentYear, endMonth + 1, 1).minusDays(1).getDayOfMonth());
        
        return dimTempoRepository.findByDataBetween(startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DimTempo> getLast12Months() {
        LocalDate now = LocalDate.now();
        LocalDate startDate = now.minusMonths(11).withDayOfMonth(1);
        LocalDate endDate = now.with(TemporalAdjusters.lastDayOfMonth());
        return dimTempoRepository.findByDataBetween(startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return dimTempoRepository.count();
    }

    @Override
    @Transactional
    public void deleteAll() {
        dimTempoRepository.deleteAll();
    }

    // Helper method to create DimTempo from LocalDate
    private DimTempo createDimTempoFromDate(LocalDate date) {
        DimTempo dimTempo = new DimTempo();
        dimTempo.setData(date);
        
        // Dia da semana (1=Domingo, 2=Segunda, ..., 7=Sábado)
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        int diaDaSemana = dayOfWeek.getValue() % 7 + 1; // Convert to 1-7
        dimTempo.setDiaDaSemana(diaDaSemana);
        
        // Nome do dia
        dimTempo.setNomeDiaDaSemana(getNomeDiaDaSemana(diaDaSemana));
        
        dimTempo.setDiaDoMes(date.getDayOfMonth());
        dimTempo.setDiaDoAno(date.getDayOfYear());
        dimTempo.setSemanaDoAno(date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        
        int mes = date.getMonthValue();
        dimTempo.setMes(mes);
        dimTempo.setNomeMes(getNomeMes(mes));
        
        dimTempo.setTrimestre((mes - 1) / 3 + 1);
        dimTempo.setAno(date.getYear());
        
        // Fim de semana
        dimTempo.setEhFimDeSemana(diaDaSemana == 1 || diaDaSemana == 7);
        
        // Feriado (simplificado - pode ser melhorado com tabela de feriados)
        dimTempo.setEhFeriado(false);
        
        return dimTempo;
    }

    private String getNomeDiaDaSemana(int diaDaSemana) {
        return switch (diaDaSemana) {
            case 1 -> "Domingo";
            case 2 -> "Segunda-feira";
            case 3 -> "Terça-feira";
            case 4 -> "Quarta-feira";
            case 5 -> "Quinta-feira";
            case 6 -> "Sexta-feira";
            case 7 -> "Sábado";
            default -> "Desconhecido";
        };
    }

    private String getNomeMes(int mes) {
        return switch (mes) {
            case 1 -> "Janeiro";
            case 2 -> "Fevereiro";
            case 3 -> "Março";
            case 4 -> "Abril";
            case 5 -> "Maio";
            case 6 -> "Junho";
            case 7 -> "Julho";
            case 8 -> "Agosto";
            case 9 -> "Setembro";
            case 10 -> "Outubro";
            case 11 -> "Novembro";
            case 12 -> "Dezembro";
            default -> "Desconhecido";
        };
    }
}
