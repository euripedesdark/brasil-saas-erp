package br.com.brasil_saas.ia.service.impl;

import br.com.brasil_saas.ia.model.PromptTemplate;
import br.com.brasil_saas.ia.repository.PromptTemplateRepository;
import br.com.brasil_saas.ia.service.PromptTemplateService;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateRepository templateRepository;

    @Override
    @Transactional
    public PromptTemplate save(PromptTemplate template) {
        return templateRepository.save(template);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptTemplate> listAll(Long empresaId) {
        return templateRepository.findByEmpresaIdAndIsActiveTrueOrderBySortOrderAsc(empresaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptTemplate> listByCategory(String category, Long empresaId) {
        return templateRepository.findByEmpresaIdAndCategoryOrderBySortOrderAsc(empresaId, category);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromptTemplate> listPaginated(Long empresaId, Pageable pageable) {
        Page<PromptTemplate> page = templateRepository.findByEmpresaId(empresaId, pageable);
        return PageResponse.from(page, item -> item);
    }

    @Override
    @Transactional
    public void delete(Long id, Long empresaId) {
        PromptTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Template nao encontrado"));
        
        if (!template.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Template nao pertence a esta empresa");
        }
        
        templateRepository.delete(template);
    }

    @Override
    @Transactional(readOnly = true)
    public PromptTemplate getById(Long id, Long empresaId) {
        PromptTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Template nao encontrado"));
        
        if (!template.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Template nao pertence a esta empresa");
        }
        
        return template;
    }
}
