package br.com.brasil_saas.rh.controller;

import br.com.brasil_saas.rh.model.Funcionario;
import br.com.brasil_saas.rh.repository.FuncionarioRepository;
import br.com.brasil_saas.cadastro.repository.PessoaRepository;
import org.springframework.data.domain.PageRequest;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rh/funcionarios")
@RequiredArgsConstructor
public class FuncionarioController {

    private final FuncionarioRepository repo;
    private final PessoaRepository pessoaRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('rh:funcionario:leitura')")
    public List<Funcionario> listar(@AuthenticationPrincipal AuthenticatedUser u) {
        return repo.listarAtivosOrdenados(u.getEmpresaId());
    }

    /**
     * Lookup interno para vincular o colaborador a uma Pessoa já cadastrada.
     * Retorna apenas os campos necessários para a seleção e sempre usa o tenant
     * do usuário autenticado; não é uma consulta externa.
     */
    @GetMapping("/pessoas")
    @PreAuthorize("hasAuthority('rh:funcionario:leitura')")
    public List<PessoaLookup> pessoas(@RequestParam(required = false) String busca,
                                      @AuthenticationPrincipal AuthenticatedUser u) {
        String termo = busca == null || busca.isBlank() ? null : busca.trim();
        return pessoaRepository.buscar(u.getEmpresaId(), termo, null, PageRequest.of(0, 50))
                .getContent().stream()
                .map(p -> new PessoaLookup(p.getId(), p.getNome(), p.getDocumento()))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:funcionario:leitura')")
    public Funcionario buscar(@PathVariable Long id,
                              @AuthenticationPrincipal AuthenticatedUser u) {
        return repo.findById(id)
            .filter(f -> f.getEmpresaId().equals(u.getEmpresaId()))
            .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('rh:funcionario:escrita')")
    @ResponseStatus(HttpStatus.CREATED)
    public Funcionario criar(@RequestBody Funcionario f,
                             @AuthenticationPrincipal AuthenticatedUser u) {
        f.setId(null);
        // A empresa vem do token, nunca do corpo. Sem esta linha o registro era
        // gravado com empresa_id nulo e o banco respondia 409 "Violação de
        // integridade (duplicidade ou FK inválida)" — mensagem que não tem nada
        // a ver com o que o usuário fez de errado.
        f.setEmpresaId(u.getEmpresaId());
        if (f.getAtivo() == null) {
            f.setAtivo(true);
        }
        return repo.save(f);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:funcionario:escrita')")
    @Transactional
    public Funcionario atualizar(@PathVariable Long id, @RequestBody Funcionario payload,
                                 @AuthenticationPrincipal AuthenticatedUser u) {
        // id sozinho não filtra por empresa: buscar por id trazia o colaborador
        // de qualquer tenant e a edição gravava nele
        Funcionario existente = repo.findById(id)
            .filter(f -> f.getEmpresaId().equals(u.getEmpresaId()))
            .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado"));

        existente.setPessoaId(payload.getPessoaId());
        existente.setUsuarioId(payload.getUsuarioId());
        existente.setCargo(payload.getCargo());
        existente.setMatricula(payload.getMatricula());
        existente.setDataAdmissao(payload.getDataAdmissao());
        existente.setSalario(payload.getSalario());
        existente.setTipoColaborador(payload.getTipoColaborador());
        existente.setPercentualComissao(payload.getPercentualComissao());
        existente.setValorHora(payload.getValorHora());

        if (payload.getAtivo() != null) {
            existente.setAtivo(payload.getAtivo());
        }
        // Desativação/desativação reativação coerentes com a data de demissão
        if (Boolean.FALSE.equals(existente.getAtivo()) && existente.getDataDemissao() == null) {
            existente.setDataDemissao(LocalDate.now());
        } else if (Boolean.TRUE.equals(existente.getAtivo())) {
            existente.setDataDemissao(null);
        }

        return repo.save(existente);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:funcionario:escrita')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void excluir(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser u) {
        Funcionario existente = repo.findById(id)
            .filter(f -> f.getEmpresaId().equals(u.getEmpresaId()))
            .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado"));

        if (Boolean.TRUE.equals(existente.getAtivo())) {
            // Soft delete: mantém o histórico de apontamentos de produção e folha
            existente.setAtivo(false);
            existente.setDataDemissao(LocalDate.now());
            repo.save(existente);
        }
    }
    public record PessoaLookup(Long id, String nome, String documento) {}
}
