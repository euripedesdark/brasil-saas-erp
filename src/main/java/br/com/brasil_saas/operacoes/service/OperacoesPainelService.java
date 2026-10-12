package br.com.brasil_saas.operacoes.service;

import br.com.brasil_saas.agenda.repository.EventoAgendaRepository;
import br.com.brasil_saas.agenda.service.AgendaService;
import br.com.brasil_saas.ativos.repository.AtivoImobilizadoRepository;
import br.com.brasil_saas.crm.repository.CrmLeadRepository;
import br.com.brasil_saas.dms.repository.DmsDocumentoRepository;
import br.com.brasil_saas.helpdesk.repository.ChamadoRepository;
import br.com.brasil_saas.helpdesk.service.HelpdeskService;
import br.com.brasil_saas.projetos.repository.PrjProjetoRepository;
import br.com.brasil_saas.qualidade.repository.InspecaoRepository;
import br.com.brasil_saas.workflow.repository.WkfInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Painel concentrado de módulos operacionais (não fiscal).
 */
@Service
@RequiredArgsConstructor
public class OperacoesPainelService {

    private final HelpdeskService helpdeskService;
    private final AgendaService agendaService;
    private final CrmLeadRepository crmLeadRepository;
    private final PrjProjetoRepository prjProjetoRepository;
    private final DmsDocumentoRepository dmsDocumentoRepository;
    private final WkfInstanceRepository wkfInstanceRepository;
    private final InspecaoRepository inspecaoRepository;
    private final AtivoImobilizadoRepository ativoImobilizadoRepository;
    private final ChamadoRepository chamadoRepository;
    private final EventoAgendaRepository eventoAgendaRepository;

    public Map<String, Object> resumo(Long empresaId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("empresaId", empresaId);
        out.put("helpdesk", helpdeskService.resumo(empresaId));
        out.put("agendaAgendados", agendaService.agendados(empresaId));
        out.put("agendaHoje", agendaService.doDia(empresaId, java.time.LocalDate.now()).size());

        List<Map<String, Object>> mods = new ArrayList<>();
        mods.add(mod("CRM", "crm", cnt(crmLeadRepository::countByEmpresaIdAndDeletedAtIsNull, empresaId), "/crm"));
        mods.add(mod("Projetos", "projetos", cnt(prjProjetoRepository::countByEmpresaIdAndDeletedAtIsNull, empresaId), "/projetos"));
        mods.add(mod("DMS", "dms", cnt(dmsDocumentoRepository::countByEmpresaIdAndDeletedAtIsNull, empresaId), "/dms"));
        mods.add(mod("Workflow", "workflow", cnt(wkfInstanceRepository::countByEmpresaIdAndDeletedAtIsNull, empresaId), "/workflow"));
        mods.add(mod("Qualidade", "qualidade", cnt(inspecaoRepository::countByEmpresaIdAndDeletedAtIsNull, empresaId), "/qualidade"));
        mods.add(mod("Ativos", "ativos", cnt(ativoImobilizadoRepository::countByEmpresaIdAndDeletedAtIsNull, empresaId), "/ativos"));
        mods.add(mod("Helpdesk", "helpdesk", cnt(chamadoRepository::countByEmpresaIdAndDeletedAtIsNull, empresaId), "/helpdesk"));
        mods.add(mod("Agenda", "agenda", cnt(eventoAgendaRepository::countByEmpresaIdAndDeletedAtIsNull, empresaId), "/agenda"));
        out.put("modulos", mods);
        return out;
    }

    private Map<String, Object> mod(String nome, String id, long qtd, String rota) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nome", nome);
        m.put("registros", qtd);
        m.put("rota", rota);
        m.put("status", qtd > 0 ? "ATIVO" : "VAZIO");
        return m;
    }

    private long cnt(Function<Long, Long> contador, Long empresaId) {
        try {
            Long v = contador.apply(empresaId);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return -1; // tabela ausente / nome diferente
        }
    }
}
