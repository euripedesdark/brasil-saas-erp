package br.com.brasil_saas.ativos.service;
import br.com.brasil_saas.ativos.model.AtivoImobilizado;
import br.com.brasil_saas.ativos.model.Manutencao;
import br.com.brasil_saas.ativos.repository.AtivoImobilizadoRepository;
import br.com.brasil_saas.ativos.repository.ManutencaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
@Service @RequiredArgsConstructor
public class IndicadoresService {
    private final AtivoImobilizadoRepository ativos;
    private final ManutencaoRepository manutencoes;
    @Transactional(readOnly = true) public List<Map<String, Object>> indicadores(Long empresaId) {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<Long, List<Manutencao>> porAtivo = new LinkedHashMap<>();
        for (Manutencao m : manutencoes.findAllByEmpresaIdAndDeletedAtIsNullOrderByDataProgramadaAsc(empresaId)) {
            if (m.getAtivoId() == null) continue;
            porAtivo.computeIfAbsent(m.getAtivoId(), k -> new ArrayList<>()).add(m);
        }
        for (AtivoImobilizado a : ativos.findAllByEmpresaIdAndDeletedAtIsNullOrderByCodigo(empresaId)) {
            List<Manutencao> ms = porAtivo.getOrDefault(a.getId(), List.of());
            long corretivas = ms.stream().filter(m -> "CORRETIVA".equals(m.getTipo()) && "CONCLUIDA".equals(m.getStatus())).count();
            long preventivas = ms.stream().filter(m -> "PREVENTIVA".equals(m.getTipo())).count();
            BigDecimal custo = ms.stream().map(m -> m.getCusto() == null ? BigDecimal.ZERO : m.getCusto()).reduce(BigDecimal.ZERO, BigDecimal::add);
            LocalDate ultimaFalha = ms.stream().filter(m -> "CORRETIVA".equals(m.getTipo()) && m.getDataConclusao() != null).map(Manutencao::getDataConclusao).max(LocalDate::compareTo).orElse(null);
            long diasSemFalha = ultimaFalha == null ? -1 : ChronoUnit.DAYS.between(ultimaFalha, LocalDate.now());
            double mttr = ms.stream().filter(m -> "CONCLUIDA".equals(m.getStatus()) && m.getDataProgramada() != null && m.getDataConclusao() != null).mapToLong(m -> ChronoUnit.DAYS.between(m.getDataProgramada(), m.getDataConclusao())).average().orElse(-1);
            double mtbf = corretivas <= 1 || ultimaFalha == null ? -1 : (double) ChronoUnit.DAYS.between(ms.stream().filter(m -> "CORRETIVA".equals(m.getTipo()) && m.getDataConclusao() != null).map(Manutencao::getDataConclusao).min(LocalDate::compareTo).orElse(ultimaFalha), ultimaFalha) / (corretivas - 1);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ativoId", a.getId());
            m.put("codigo", a.getCodigo());
            m.put("descricao", a.getDescricao());
            m.put("corretivas", corretivas);
            m.put("preventivas", preventivas);
            m.put("custo", custo);
            m.put("diasSemFalha", diasSemFalha);
            m.put("mttrDias", BigDecimal.valueOf(mttr).setScale(1, RoundingMode.HALF_UP));
            m.put("mtbfDias", BigDecimal.valueOf(mtbf).setScale(1, RoundingMode.HALF_UP));
            m.put("proximaPreventiva", proximaPreventiva(ultimaFalha, mtbf));
            out.add(m);
        }
        return out;
    }

    @Transactional public java.util.List<java.util.Map<String, Object>> gerarPreventivas(Long empresaId) {
        java.util.List<java.util.Map<String, Object>> criadas = new java.util.ArrayList<>();
        java.util.Map<Long, java.util.List<Manutencao>> porAtivo = new java.util.LinkedHashMap<>();
        for (Manutencao m : manutencoes.findAllByEmpresaIdAndDeletedAtIsNullOrderByDataProgramadaAsc(empresaId)) {
            if (m.getAtivoId() == null) continue;
            porAtivo.computeIfAbsent(m.getAtivoId(), k -> new java.util.ArrayList<>()).add(m);
        }
        for (AtivoImobilizado a : ativos.findAllByEmpresaIdAndDeletedAtIsNullOrderByCodigo(empresaId)) {
            java.util.List<Manutencao> ms = porAtivo.getOrDefault(a.getId(), java.util.List.of());
            java.util.List<LocalDate> falhas = ms.stream().filter(m -> "CORRETIVA".equals(m.getTipo()) && m.getDataConclusao() != null).map(Manutencao::getDataConclusao).sorted().toList();
            if (falhas.size() <= 1) continue;
            long mtbf = Math.round((double) ChronoUnit.DAYS.between(falhas.get(0), falhas.get(falhas.size() - 1)) / (falhas.size() - 1));
            if (mtbf <= 0) continue;
            boolean aberta = ms.stream().anyMatch(m -> "PREVENTIVA".equals(m.getTipo()) && !"CONCLUIDA".equals(m.getStatus()) && !"CANCELADA".equals(m.getStatus()));
            if (aberta) continue;
            LocalDate ultima = falhas.get(falhas.size() - 1);
            LocalDate prevista = ultima.plusDays(mtbf);
            if (prevista.isBefore(LocalDate.now())) prevista = LocalDate.now();
            Manutencao n = new Manutencao();
            n.setEmpresaId(empresaId);
            n.setAtivoId(a.getId());
            n.setNumero("PREV-" + a.getId() + "-" + String.valueOf(prevista));
            n.setTipo("PREVENTIVA"); n.setStatus("ABERTA"); n.setPrioridade("MEDIA");
            n.setDescricao("Preventiva gerada pelo MTBF");
            n.setDataProgramada(prevista);
            manutencoes.save(n);
            java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("ativoId", a.getId()); r.put("codigo", a.getCodigo()); r.put("dataProgramada", String.valueOf(prevista));
            criadas.add(r);
        }
        return criadas;
    }
    private String proximaPreventiva(LocalDate ultimaFalha, double mtbf) {
        if (ultimaFalha == null || mtbf <= 0) return null;
        return String.valueOf(ultimaFalha.plusDays(Math.round(mtbf)));
    }
}
