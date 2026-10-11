package br.com.brasil_saas.bi.service.impl;

import br.com.brasil_saas.bi.dto.KpiRequest;
import br.com.brasil_saas.bi.dto.KpiResponse;
import br.com.brasil_saas.bi.model.Kpi;
import br.com.brasil_saas.bi.repository.KpiRepository;
import br.com.brasil_saas.bi.service.KpiService;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.web.PageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KpiServiceImpl implements KpiService {

    private final KpiRepository kpiRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public KpiResponse create(KpiRequest request, Long empresaId) {
        Kpi kpi = new Kpi();
        kpi.setEmpresaId(empresaId);
        kpi.setName(request.name());
        kpi.setDescription(request.description());
        kpi.setKpiType(request.kpiType());
        kpi.setQueryFormula(request.queryFormula());
        kpi.setTargetValue(request.targetValue());
        kpi.setUnit(request.unit());
        kpi.setFormat(request.format());
        kpi.setColorGood(request.colorGood());
        kpi.setColorWarning(request.colorWarning());
        kpi.setColorBad(request.colorBad());
        kpi.setThresholdGood(request.thresholdGood());
        kpi.setThresholdWarning(request.thresholdWarning());
        kpi.setIsActive(request.isActive());
        
        Kpi saved = kpiRepository.save(kpi);
        
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public KpiResponse update(Long id, KpiRequest request, Long empresaId) {
        Kpi kpi = kpiRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("KPI nao encontrado"));
        
        if (!kpi.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("KPI nao pertence a esta empresa");
        }
        
        kpi.setName(request.name());
        kpi.setDescription(request.description());
        kpi.setKpiType(request.kpiType());
        kpi.setQueryFormula(request.queryFormula());
        kpi.setTargetValue(request.targetValue());
        kpi.setUnit(request.unit());
        kpi.setFormat(request.format());
        kpi.setColorGood(request.colorGood());
        kpi.setColorWarning(request.colorWarning());
        kpi.setColorBad(request.colorBad());
        kpi.setThresholdGood(request.thresholdGood());
        kpi.setThresholdWarning(request.thresholdWarning());
        kpi.setIsActive(request.isActive());
        
        Kpi saved = kpiRepository.save(kpi);
        
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public KpiResponse getById(Long id, Long empresaId) {
        Kpi kpi = kpiRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("KPI nao encontrado"));
        
        if (!kpi.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("KPI nao pertence a esta empresa");
        }
        
        return mapToResponse(kpi);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<KpiResponse> listAll(Long empresaId, Pageable pageable) {
        Page<Kpi> page = kpiRepository.findByEmpresaId(empresaId, pageable);
        return PageResponse.from(page, this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KpiResponse> listByType(String kpiType, Long empresaId) {
        List<Kpi> kpis = kpiRepository.findByEmpresaIdAndKpiTypeOrderByNameAsc(empresaId, kpiType);
        return kpis.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id, Long empresaId) {
        Kpi kpi = kpiRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("KPI nao encontrado"));
        
        if (!kpi.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("KPI nao pertence a esta empresa");
        }
        
        kpiRepository.delete(kpi);
    }

    @Override
    @Transactional
    public void refreshKpiValues(Long empresaId) {
        // List<Kpi> kpis = kpiRepository.findAllByEmpresaId(empresaId); // TODO: adicionar método no repository
    List<Kpi> kpis = kpiRepository.findByEmpresaIdOrderByNameAsc(empresaId);
        
        for (Kpi kpi : kpis) {
            Object value = calculateKpiValue(kpi.getId(), empresaId);
            if (value instanceof Number) {
                kpi.setCurrentValue(((Number) value).doubleValue());
                kpiRepository.save(kpi);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Object calculateKpiValue(Long kpiId, Long empresaId) {
        Kpi kpi = kpiRepository.findById(kpiId)
            .orElseThrow(() -> new ResourceNotFoundException("KPI nao encontrado"));
        if (!empresaId.equals(kpi.getEmpresaId())) {
            throw new ResourceNotFoundException("KPI nao pertence a esta empresa");
        }
        
        if (kpi.getQueryFormula() == null || kpi.getQueryFormula().isBlank()) {
            return 0;
        }
        
        try {
            if (kpi.getQueryFormula().toUpperCase().startsWith("SELECT")) {
                Query query = entityManager.createNativeQuery(kpi.getQueryFormula());
                return query.getSingleResult();
            } else {
                return evaluateFormula(kpi.getQueryFormula(), empresaId);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private Object evaluateFormula(String formula, Long empresaId) {
        return 0;
    }

    private KpiResponse mapToResponse(Kpi kpi) {
        return new KpiResponse(
            kpi.getId(),
            kpi.getName(),
            kpi.getDescription(),
            kpi.getKpiType(),
            kpi.getQueryFormula(),
            kpi.getTargetValue(),
            kpi.getCurrentValue(),
            kpi.getUnit(),
            kpi.getFormat(),
            kpi.getColorGood(),
            kpi.getColorWarning(),
            kpi.getColorBad(),
            kpi.getThresholdGood(),
            kpi.getThresholdWarning(),
            kpi.getIsActive(),
            kpi.getCreatedAt(),
            kpi.getUpdatedAt()
        );
    }
}
