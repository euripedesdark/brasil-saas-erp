package br.com.brasil_saas.cadastro.service.impl;

import br.com.brasil_saas.cadastro.mapper.ClienteMapper;
import br.com.brasil_saas.cadastro.model.Cliente;
import br.com.brasil_saas.cadastro.model.Pessoa;
import br.com.brasil_saas.cadastro.repository.ClienteRepository;
import br.com.brasil_saas.cadastro.repository.PessoaRepository;
import br.com.brasil_saas.cadastro.service.ClienteService;
import br.com.brasil_saas.cadastro.service.dto.ClienteDtos;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.web.PageResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository repository;
    private final PessoaRepository pessoaRepository;
    private final ClienteMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ClienteDtos.Response> listar(Long empresaId, Pageable pageable) {
        return PageResponse.from(repository.findByEmpresaIdAndDeletedAtIsNull(empresaId, pageable), mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteDtos.Response buscarPorId(Long empresaId, Long id) {
        return mapper.toResponse(obter(empresaId, id));
    }

    @Override
    @Transactional
    public ClienteDtos.Response criar(Long empresaId, ClienteDtos.Request request) {
        if (!repository.findByEmpresaIdAndPessoaIdAndDeletedAtIsNull(empresaId, request.pessoaId()).isEmpty()) {
            throw new BusinessException("Pessoa já possui cadastro de cliente nesta empresa");
        }
        Cliente cliente = mapper.toEntity(request);
        cliente.setEmpresaId(empresaId);
        cliente.setPessoa(obterPessoa(empresaId, request.pessoaId()));
        if (cliente.getAtivo() == null) cliente.setAtivo(Boolean.TRUE);
        return mapper.toResponse(repository.save(cliente));
    }

    @Override
    @Transactional
    public ClienteDtos.Response atualizar(Long empresaId, Long id, ClienteDtos.Request request) {
        Cliente cliente = obter(empresaId, id);
        mapper.update(request, cliente);
        return mapper.toResponse(repository.save(cliente));
    }

    @Override
    @Transactional
    public void excluir(Long empresaId, Long id) {
        Cliente cliente = obter(empresaId, id);
        cliente.setDeletedAt(LocalDateTime.now());
        repository.save(cliente);
    }

    private Cliente obter(Long empresaId, Long id) {
        return repository.findById(id)
                .filter(c -> c.getEmpresaId() != null && c.getEmpresaId().equals(empresaId) && c.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("cliente", id));
    }

    private Pessoa obterPessoa(Long empresaId, Long pessoaId) {
        return pessoaRepository.findById(pessoaId)
                .filter(p -> p.getEmpresaId() != null && p.getEmpresaId().equals(empresaId) && p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("pessoa", pessoaId));
    }
}
