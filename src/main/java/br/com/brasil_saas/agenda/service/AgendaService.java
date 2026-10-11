package br.com.brasil_saas.agenda.service;

import br.com.brasil_saas.agenda.model.EventoAgenda;
import br.com.brasil_saas.agenda.repository.EventoAgendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgendaService {

    private final EventoAgendaRepository repository;

    public List<EventoAgenda> listar(Long empresaId) {
        return repository.findByEmpresaIdAndDeletedAtIsNullOrderByInicioAsc(empresaId);
    }

    public List<EventoAgenda> doDia(Long empresaId, LocalDate dia) {
        LocalDate d = dia == null ? LocalDate.now() : dia;
        return repository.noPeriodo(empresaId, d.atStartOfDay(), d.plusDays(1).atStartOfDay());
    }

    public List<EventoAgenda> daSemana(Long empresaId) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje.minusDays(hoje.getDayOfWeek().getValue() - 1L);
        return repository.noPeriodo(empresaId, inicio.atStartOfDay(), inicio.plusDays(7).atStartOfDay());
    }

    public EventoAgenda buscar(Long empresaId, Long id) {
        return repository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado"));
    }

    @Transactional
    public EventoAgenda salvar(Long empresaId, EventoAgenda e) {
        if (e.getTitulo() == null || e.getTitulo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Título obrigatório");
        }
        if (e.getInicio() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Início obrigatório");
        }
        if (e.getId() != null) {
            EventoAgenda a = buscar(empresaId, e.getId());
            a.setTitulo(e.getTitulo());
            a.setDescricao(e.getDescricao());
            a.setTipo(e.getTipo() == null ? "REUNIAO" : e.getTipo());
            a.setInicio(e.getInicio());
            a.setFim(e.getFim());
            a.setLocalEvento(e.getLocalEvento());
            a.setLeadId(e.getLeadId());
            a.setClienteId(e.getClienteId());
            a.setResponsavel(e.getResponsavel());
            a.setStatus(e.getStatus() == null ? a.getStatus() : e.getStatus());
            return repository.save(a);
        }
        e.setEmpresaId(empresaId);
        if (e.getUuid() == null) e.setUuid(UUID.randomUUID());
        if (e.getTipo() == null) e.setTipo("REUNIAO");
        if (e.getStatus() == null) e.setStatus("AGENDADO");
        return repository.save(e);
    }

    @Transactional
    public EventoAgenda concluir(Long empresaId, Long id) {
        EventoAgenda e = buscar(empresaId, id);
        e.setStatus("CONCLUIDO");
        return repository.save(e);
    }

    @Transactional
    public void cancelar(Long empresaId, Long id) {
        EventoAgenda e = buscar(empresaId, id);
        e.setStatus("CANCELADO");
        e.setDeletedAt(LocalDateTime.now());
        repository.save(e);
    }

    public long agendados(Long empresaId) {
        return repository.countByEmpresaIdAndStatusAndDeletedAtIsNull(empresaId, "AGENDADO");
    }
}
