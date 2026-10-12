package br.com.brasil_saas.workflow.service.impl;
import br.com.brasil_saas.workflow.model.*;
import br.com.brasil_saas.workflow.repository.*;
import br.com.brasil_saas.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
@Service @RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {
    private final WkfDefinitionRepository definitions;
    private final WkfStageRepository stages;
    private final WkfInstanceRepository instances;
    private final WkfTaskRepository tasks;
    private <T> T exigir(java.util.Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    private void garantir(java.util.Optional<?> o, String msg) {
        o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    @Override public List<WkfDefinition> definitions(Long empresaId) {
        return definitions.findByEmpresaIdAndDeletedAtIsNull(empresaId);
    }
    @Override @Transactional public WkfDefinition salvarDefinition(Long empresaId, Long userId, WkfDefinition d) {
        d.setId(null);
        return definitions.save(d);
    }
    @Override @Transactional public WkfDefinition addStage(Long empresaId, Long definitionId, WkfStage s) {
        WkfDefinition d = exigir(definitions.findByIdAndEmpresaIdAndDeletedAtIsNull(definitionId, empresaId), "Definicao inexistente");
        s.setId(null);
        s.setDefinitionId(d.getId());
        if (s.getOrdem() == null) {
            int max = stages.findByDefinitionIdAndEmpresaIdAndDeletedAtIsNullOrderByOrdem(d.getId(), empresaId)
                    .stream().mapToInt(x -> x.getOrdem() == null ? 0 : x.getOrdem()).max().orElse(0);
            s.setOrdem(max + 1);
        }
        stages.save(s);
        return d;
    }
    @Override public List<WkfStage> stages(Long empresaId, Long definitionId) {
        garantir(definitions.findByIdAndEmpresaIdAndDeletedAtIsNull(definitionId, empresaId), "Definicao inexistente");
        return stages.findByDefinitionIdAndEmpresaIdAndDeletedAtIsNullOrderByOrdem(definitionId, empresaId);
    }
    @Override @Transactional public WkfInstance abrir(Long empresaId, Long userId, String entidadeTipo, Long entidadeId, Long definitionId, String observacao) {
        WkfDefinition d = exigir(definitions.findByIdAndEmpresaIdAndDeletedAtIsNull(definitionId, empresaId), "Definicao inexistente");
        if (Boolean.FALSE.equals(d.getAtivo())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Definicao inativa");
        WkfInstance i = new WkfInstance();
        i.setDefinitionId(d.getId());
        i.setEntidadeTipo(entidadeTipo);
        i.setEntidadeId(entidadeId);
        i.setStatus("EM_ANDAMENTO");
        i.setEtapaAtual(1);
        i.setSolicitadoPor(userId);
        i.setObservacao(observacao);
        i = instances.save(i);
        criarTarefaEtapa(empresaId, i, 1);
        return i;
    }
    private void criarTarefaEtapa(Long empresaId, WkfInstance i, int ordem) {
        List<WkfStage> ss = stages.findByDefinitionIdAndEmpresaIdAndDeletedAtIsNullOrderByOrdem(i.getDefinitionId(), empresaId);
        WkfStage s = ss.stream().filter(x -> Integer.valueOf(ordem).equals(x.getOrdem())).findFirst().orElse(null);
        if (s == null) {
            i.setStatus("CONCLUIDA");
            i.setConcludedAt(LocalDateTime.now());
            instances.save(i);
            return;
        }
        // Multi-nível / multi-aprovador: se exigeTodos, cria uma tarefa por aprovador (lista separada por vírgula)
        java.util.List<String> aprovadores = new java.util.ArrayList<>();
        if (s.getAprovadores() != null && !s.getAprovadores().isBlank()) {
            for (String p : s.getAprovadores().split("[,;]")) {
                String x = p.trim();
                if (!x.isEmpty()) aprovadores.add(x);
            }
        }
        if (aprovadores.isEmpty()) aprovadores.add("GESTOR");
        boolean multi = Boolean.TRUE.equals(s.getExigeTodos()) && aprovadores.size() > 1;
        for (String resp : (multi ? aprovadores : java.util.List.of(String.join(",", aprovadores)))) {
            WkfTask t = new WkfTask();
            t.setInstanceId(i.getId());
            t.setStageId(s.getId());
            t.setResponsavel(resp);
            t.setStatus("PENDENTE");
            t.setSlaLimite(LocalDateTime.now().plusHours(s.getSlaHoras() == null ? 48 : s.getSlaHoras()));
            tasks.save(t);
            if (!multi) break;
        }
    }
    @Override public List<WkfInstance> instances(Long empresaId, String status) {
        if (status == null || status.isBlank()) return instances.findByEmpresaIdAndDeletedAtIsNull(empresaId);
        return instances.findByEmpresaIdAndStatusAndDeletedAtIsNull(empresaId, status);
    }
    @Override public List<WkfTask> tasks(Long empresaId, Long instanceId) {
        garantir(instances.findByIdAndEmpresaIdAndDeletedAtIsNull(instanceId, empresaId), "Instancia inexistente");
        return tasks.findByInstanceIdAndEmpresaIdAndDeletedAtIsNull(instanceId, empresaId);
    }
    @Override public java.util.Optional<WkfInstance> instanciaPara(Long empresaId, String entidadeTipo, Long entidadeId) {
        return instances.findByEmpresaIdAndDeletedAtIsNull(empresaId).stream().filter(x -> entidadeTipo.equals(x.getEntidadeTipo()) && entidadeId.equals(x.getEntidadeId())).max(java.util.Comparator.comparing(WkfInstance::getId));
    }
    @Override public List<WkfTask> pendentes(Long empresaId) {
        return tasks.findByEmpresaIdAndStatusAndDeletedAtIsNull(empresaId, "PENDENTE");
    }
    @Override @Transactional public WkfTask delegar(Long empresaId,Long userId,Long taskId,Long destinoId){
        WkfTask t=exigir(tasks.findByIdAndEmpresaIdAndDeletedAtIsNull(taskId,empresaId),"Tarefa inexistente");
        if(!"PENDENTE".equals(t.getStatus()))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Tarefa nao pendente");
        WkfInstance i=exigir(instances.findByIdAndEmpresaIdAndDeletedAtIsNull(t.getInstanceId(),empresaId),"Instancia inexistente");
        if(!"EM_ANDAMENTO".equals(i.getStatus()))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Instancia encerrada");
        if(destinoId==null||destinoId<=0||String.valueOf(destinoId).equals(t.getResponsavel()))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Destino invalido");
        t.setResponsavelAnterior(t.getResponsavel());t.setResponsavel(String.valueOf(destinoId));
        t.setDelegadoPor(userId);t.setDelegadoEm(LocalDateTime.now());return tasks.save(t);
    }
    @Override @Transactional public WkfTask decidir(Long empresaId, Long userId, Long taskId, boolean aprovar, String comentario) {
        WkfTask t = exigir(tasks.findByIdAndEmpresaIdAndDeletedAtIsNull(taskId, empresaId), "Tarefa inexistente");
        if (!"PENDENTE".equals(t.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Tarefa ja decidida");
        if(t.getDelegadoEm()!=null&&!String.valueOf(userId).equals(t.getResponsavel()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tarefa delegada a outro usuario");
        WkfInstance i = exigir(instances.findByIdAndEmpresaIdAndDeletedAtIsNull(t.getInstanceId(), empresaId), "Instancia inexistente");
        t.setStatus(aprovar ? "APROVADA" : "REJEITADA");
        t.setDecidedAt(LocalDateTime.now());
        t.setDecididoPor(userId);
        t.setComentario(comentario);
        tasks.save(t);
        if (!aprovar) {
            i.setStatus("REJEITADA");
            i.setConcludedAt(LocalDateTime.now());
            instances.save(i);
            return t;
        }
        // Se a etapa exige todos os aprovadores, só avança quando não restar PENDENTE na mesma stage
        WkfStage stage = stages.findById(t.getStageId()).orElse(null);
        if (stage != null && Boolean.TRUE.equals(stage.getExigeTodos())) {
            boolean aindaPendente = tasks.findByInstanceIdAndEmpresaIdAndDeletedAtIsNull(i.getId(), empresaId)
                    .stream()
                    .filter(x -> t.getStageId().equals(x.getStageId()))
                    .anyMatch(x -> "PENDENTE".equals(x.getStatus()));
            if (aindaPendente) {
                return t; // aguarda demais aprovadores desta etapa
            }
        }
        i.setEtapaAtual(i.getEtapaAtual() + 1);
        instances.save(i);
        criarTarefaEtapa(empresaId, i, i.getEtapaAtual());
        return t;
    }
    @Override @Transactional public void inativarDefinition(Long empresaId, Long id) {
        WkfDefinition d = exigir(definitions.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Definicao inexistente");
        d.setAtivo(false);
        definitions.save(d);
    }
}
