package br.com.brasil_saas.cadastro.service.impl;

import br.com.brasil_saas.cadastro.mapper.TransportadoraMapper;
import br.com.brasil_saas.cadastro.model.Pessoa;
import br.com.brasil_saas.cadastro.model.Transportadora;
import br.com.brasil_saas.cadastro.repository.PessoaRepository;
import br.com.brasil_saas.cadastro.repository.TransportadoraRepository;
import br.com.brasil_saas.cadastro.service.TransportadoraService;
import br.com.brasil_saas.cadastro.service.dto.TransportadoraDtos;
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
public class TransportadoraServiceImpl implements TransportadoraService {

    private final TransportadoraRepository repository;
    private final PessoaRepository pessoaRepository;
    private final TransportadoraMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransportadoraDtos.Response> listar(Long empresaId, Pageable pageable) {
        return PageResponse.from(repository.findByEmpresaIdAndDeletedAtIsNull(empresaId, pageable), mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TransportadoraDtos.Response buscarPorId(Long empresaId, Long id) {
        return mapper.toResponse(obter(empresaId, id));
    }

    @Override
    @Transactional
    public TransportadoraDtos.Response criar(Long empresaId, TransportadoraDtos.Request request) {
        if (!repository.findByEmpresaIdAndPessoaIdAndDeletedAtIsNull(empresaId, request.pessoaId()).isEmpty()) {
            throw new BusinessException("Pessoa já possui cadastro de transportadora nesta empresa");
        }
        Transportadora t = mapper.toEntity(request);
        t.setEmpresaId(empresaId);
        t.setPessoa(obterPessoa(empresaId, request.pessoaId()));
        if (t.getAtivo() == null) t.setAtivo(Boolean.TRUE);
        return mapper.toResponse(repository.save(t));
    }

    @Override
    @Transactional
    public TransportadoraDtos.Response atualizar(Long empresaId, Long id, TransportadoraDtos.Request request) {
        Transportadora t = obter(empresaId, id);
        mapper.update(request, t);
        return mapper.toResponse(repository.save(t));
    }

    @Override
    @Transactional
    public void excluir(Long empresaId, Long id) {
        Transportadora t = obter(empresaId, id);
        t.setDeletedAt(LocalDateTime.now());
        repository.save(t);
    }

    private Transportadora obter(Long empresaId, Long id) {
        return repository.findById(id)
                .filter(t -> t.getEmpresaId() != null && t.getEmpresaId().equals(empresaId) && t.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("transportadora", id));
    }

    private Pessoa obterPessoa(Long empresaId, Long pessoaId) {
        return pessoaRepository.findById(pessoaId)
                .filter(p -> p.getEmpresaId() != null && p.getEmpresaId().equals(empresaId) && p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("pessoa", pessoaId));
    }

}
