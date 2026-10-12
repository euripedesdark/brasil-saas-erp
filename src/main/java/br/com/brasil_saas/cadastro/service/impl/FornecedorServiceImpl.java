package br.com.brasil_saas.cadastro.service.impl;

import br.com.brasil_saas.cadastro.mapper.FornecedorMapper;
import br.com.brasil_saas.cadastro.model.Fornecedor;
import br.com.brasil_saas.cadastro.model.Pessoa;
import br.com.brasil_saas.cadastro.repository.FornecedorRepository;
import br.com.brasil_saas.cadastro.repository.PessoaRepository;
import br.com.brasil_saas.cadastro.service.FornecedorService;
import br.com.brasil_saas.cadastro.service.dto.FornecedorDtos;
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
public class FornecedorServiceImpl implements FornecedorService {

    private final FornecedorRepository repository;
    private final PessoaRepository pessoaRepository;
    private final FornecedorMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FornecedorDtos.Response> listar(Long empresaId, Pageable pageable) {
        return PageResponse.from(repository.findByEmpresaIdAndDeletedAtIsNull(empresaId, pageable), mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public FornecedorDtos.Response buscarPorId(Long empresaId, Long id) {
        return mapper.toResponse(obter(empresaId, id));
    }

    @Override
    @Transactional
    public FornecedorDtos.Response criar(Long empresaId, FornecedorDtos.Request request) {
        if (!repository.findByEmpresaIdAndPessoaIdAndDeletedAtIsNull(empresaId, request.pessoaId()).isEmpty()) {
            throw new BusinessException("Pessoa já possui cadastro de fornecedor nesta empresa");
        }
        Fornecedor fornecedor = mapper.toEntity(request);
        fornecedor.setEmpresaId(empresaId);
        fornecedor.setPessoa(obterPessoa(empresaId, request.pessoaId()));
        if (fornecedor.getAtivo() == null) fornecedor.setAtivo(Boolean.TRUE);
        return mapper.toResponse(repository.save(fornecedor));
    }

    @Override
    @Transactional
    public FornecedorDtos.Response atualizar(Long empresaId, Long id, FornecedorDtos.Request request) {
        Fornecedor fornecedor = obter(empresaId, id);
        mapper.update(request, fornecedor);
        return mapper.toResponse(repository.save(fornecedor));
    }

    @Override
    @Transactional
    public void excluir(Long empresaId, Long id) {
        Fornecedor fornecedor = obter(empresaId, id);
        fornecedor.setDeletedAt(LocalDateTime.now());
        repository.save(fornecedor);
    }

    private Fornecedor obter(Long empresaId, Long id) {
        return repository.findById(id)
                .filter(f -> f.getEmpresaId() != null && f.getEmpresaId().equals(empresaId) && f.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("fornecedor", id));
    }

    private Pessoa obterPessoa(Long empresaId, Long pessoaId) {
        return pessoaRepository.findById(pessoaId)
                .filter(p -> p.getEmpresaId() != null && p.getEmpresaId().equals(empresaId) && p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("pessoa", pessoaId));
    }
}
