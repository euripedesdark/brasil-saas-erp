package br.com.brasil_saas.bi.service;

import br.com.brasil_saas.bi.dto.KpiRequest;
import br.com.brasil_saas.bi.dto.KpiResponse;
import br.com.brasil_saas.shared.web.PageResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface KpiService {
    
    KpiResponse create(KpiRequest request, Long empresaId);
    
    KpiResponse update(Long id, KpiRequest request, Long empresaId);
    
    KpiResponse getById(Long id, Long empresaId);
    
    PageResponse<KpiResponse> listAll(Long empresaId, Pageable pageable);
    
    List<KpiResponse> listByType(String kpiType, Long empresaId);
    
    void delete(Long id, Long empresaId);
    
    void refreshKpiValues(Long empresaId);
    
    Object calculateKpiValue(Long kpiId, Long empresaId);
}
