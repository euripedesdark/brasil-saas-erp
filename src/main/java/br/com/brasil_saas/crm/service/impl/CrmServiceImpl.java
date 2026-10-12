package br.com.brasil_saas.crm.service.impl;
import br.com.brasil_saas.crm.model.*;
import br.com.brasil_saas.crm.repository.*;
import br.com.brasil_saas.crm.service.CrmService;
import br.com.brasil_saas.vendas.dto.PedidoVendaRequest;
import br.com.brasil_saas.vendas.dto.PedidoVendaResponse;
import br.com.brasil_saas.vendas.service.PedidoVendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class CrmServiceImpl implements CrmService {
    private final CrmLeadRepository leads;
    private final CrmAtividadeRepository atividades;
    private final PedidoVendaService pedidos;
    private static final List<String> ETAPAS = List.of("PROSPECCAO", "QUALIFICACAO", "PROPOSTA", "NEGOCIACAO", "FECHAMENTO");
    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    private void garantir(Optional<?> o, String msg) {
        o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    @Override public List<CrmLead> leads(Long empresaId, String etapa, String status) {
        List<CrmLead> base = (etapa == null || etapa.isBlank()) ? leads.findByEmpresaIdAndDeletedAtIsNull(empresaId) : leads.findByEmpresaIdAndEtapaAndDeletedAtIsNull(empresaId, etapa);
        if (status == null || status.isBlank()) return base;
        return base.stream().filter(l -> status.equals(l.getStatus())).toList();
    }
    @Override @Transactional public CrmLead salvar(Long empresaId, CrmLead l) {
        l.setId(null);
        if (l.getEtapa() == null) l.setEtapa("PROSPECCAO");
        if (l.getStatus() == null) l.setStatus("ABERTO");
        if (!ETAPAS.contains(l.getEtapa())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Etapa invalida");
        return leads.save(l);
    }
    @Override @Transactional public CrmLead moverEtapa(Long empresaId, Long id, String etapa) {
        if (!ETAPAS.contains(etapa)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Etapa invalida");
        CrmLead l = exigir(leads.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Lead inexistente");
        l.setEtapa(etapa);
        if ("FECHAMENTO".equals(etapa)) l.setStatus("GANHO");
        return leads.save(l);
    }
    @Override @Transactional public void excluirLead(Long empresaId, Long id) {
        CrmLead l = exigir(leads.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Lead inexistente");
        atividades.deleteAll(atividades.findByLeadIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId));
        leads.delete(l);
    }
    @Override public List<Map<String, Object>> pipeline(Long empresaId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String etapa : ETAPAS) {
            List<CrmLead> ls = leads.findByEmpresaIdAndEtapaAndDeletedAtIsNull(empresaId, etapa).stream().filter(l -> "ABERTO".equals(l.getStatus())).toList();
            BigDecimal total = ls.stream().map(x -> x.getValorEstimado() == null ? BigDecimal.ZERO : x.getValorEstimado()).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("etapa", etapa);
            m.put("qtd", ls.size());
            m.put("total", total);
            m.put("leads", ls.stream().map(x -> Map.of("id", x.getId(), "nome", x.getNome() == null ? "" : x.getNome(), "valor", x.getValorEstimado() == null ? BigDecimal.ZERO : x.getValorEstimado())).toList());
            out.add(m);
        }
        return out;
    }
    @Override public Map<String, Object> forecast(Long empresaId, Integer ano, Integer mes) {
        List<CrmLead> base = leads.findByEmpresaIdAndDeletedAtIsNull(empresaId).stream().filter(l -> "ABERTO".equals(l.getStatus())).toList();
        if (ano != null && mes != null) base = base.stream().filter(l -> l.getDataPrevFechamento() != null && l.getDataPrevFechamento().getYear() == ano && l.getDataPrevFechamento().getMonthValue() == mes).toList();
        BigDecimal bruto = BigDecimal.ZERO;
        BigDecimal ponderado = BigDecimal.ZERO;
        for (CrmLead l : base) {
            BigDecimal v = l.getValorEstimado() == null ? BigDecimal.ZERO : l.getValorEstimado();
            int p = l.getProbabilidade() == null ? 10 : Math.min(100, Math.max(0, l.getProbabilidade()));
            bruto = bruto.add(v);
            ponderado = ponderado.add(v.multiply(BigDecimal.valueOf(p)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("qtd", base.size());
        m.put("bruto", bruto);
        m.put("ponderado", ponderado);
        return m;
    }
    @Override public List<CrmAtividade> atividades(Long empresaId, Long leadId, Boolean pendentes) {
        List<CrmAtividade> base = (leadId == null) ? atividades.findByEmpresaIdAndDeletedAtIsNull(empresaId) : atividades.findByLeadIdAndEmpresaIdAndDeletedAtIsNull(leadId, empresaId);
        if (Boolean.TRUE.equals(pendentes)) base = base.stream().filter(a -> !Boolean.TRUE.equals(a.getConcluida())).toList();
        base.sort(Comparator.comparing(CrmAtividade::getDataAgendada, Comparator.nullsLast(Comparator.naturalOrder())));
        return base;
    }
    @Override @Transactional public CrmAtividade salvarAtividade(Long empresaId, CrmAtividade a) {
        if (a.getLeadId() != null) exigir(leads.findByIdAndEmpresaIdAndDeletedAtIsNull(a.getLeadId(), empresaId), "Lead inexistente");
        a.setId(null);
        a.setConcluida(false);
        return atividades.save(a);
    }
    @Override @Transactional public CrmAtividade concluir(Long empresaId, Long id) {
        CrmAtividade a = exigir(atividades.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Atividade inexistente");
        a.setConcluida(true);
        a.setConcluidaEm(LocalDateTime.now());
        return atividades.save(a);
    }
    @Override @Transactional public java.util.Map<String, Object> gerarPedido(Long empresaId, Long leadId, Long clienteId) {
        CrmLead l = exigir(leads.findByIdAndEmpresaIdAndDeletedAtIsNull(leadId, empresaId), "Lead inexistente");
        if (!"ABERTO".equals(l.getStatus())) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Lead nao esta aberto");
        PedidoVendaRequest req = new PedidoVendaRequest(empresaId, clienteId, null, "PEDIDO", "ABERTO", java.time.LocalDate.now(), null, null, null, java.math.BigDecimal.ZERO, null, "CRM", "Lead #" + l.getId() + " " + (l.getNome() == null ? "" : l.getNome()), java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.util.List.of());
        PedidoVendaResponse pv = pedidos.criar(req.comEmpresaDa(empresaId));
        l.setEtapa("FECHAMENTO");
        l.setStatus("GANHO");
        leads.save(l);
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("pedidoId", pv.id());
        m.put("leadId", l.getId());
        return m;
    }
}
