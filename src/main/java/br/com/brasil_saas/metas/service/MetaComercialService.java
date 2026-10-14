package br.com.brasil_saas.metas.service;

import br.com.brasil_saas.metas.model.MetaComercial;
import br.com.brasil_saas.metas.repository.MetaComercialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MetaComercialService {
    private final MetaComercialRepository repository;

    public List<MetaComercial> listar(Long empresaId, Integer ano, Integer mes) {
        if (ano != null && mes != null) {
            return repository.findByEmpresaIdAndAnoAndMesAndDeletedAtIsNull(empresaId, ano, mes);
        }
        return repository.findByEmpresaIdAndDeletedAtIsNullOrderByAnoDescMesDesc(empresaId);
    }

    public MetaComercial buscar(Long empresaId, Long id) {
        return repository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meta não encontrada"));
    }

    @Transactional
    public MetaComercial salvar(Long empresaId, MetaComercial m) {
        if (m.getAno() == null || m.getMes() == null || m.getMes() < 1 || m.getMes() > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ano/mês inválidos");
        }
        if (m.getValorMeta() == null) m.setValorMeta(BigDecimal.ZERO);
        if (m.getValorRealizado() == null) m.setValorRealizado(BigDecimal.ZERO);
        if (m.getId() != null) {
            MetaComercial a = buscar(empresaId, m.getId());
            a.setAno(m.getAno());
            a.setMes(m.getMes());
            a.setVendedorId(m.getVendedorId());
            a.setCanal(m.getCanal());
            a.setValorMeta(m.getValorMeta());
            a.setValorRealizado(m.getValorRealizado());
            a.setObservacao(m.getObservacao());
            return repository.save(a);
        }
        m.setEmpresaId(empresaId);
        if (m.getUuid() == null) m.setUuid(UUID.randomUUID());
        return repository.save(m);
    }

    @Transactional
    public void excluir(Long empresaId, Long id) {
        MetaComercial m = buscar(empresaId, id);
        m.setDeletedAt(java.time.LocalDateTime.now());
        repository.save(m);
    }

    public MetaComercial registrarRealizado(Long empresaId, Long id, BigDecimal valor) {
        MetaComercial m = buscar(empresaId, id);
        m.setValorRealizado(valor == null ? BigDecimal.ZERO : valor);
        return repository.save(m);
    }

    public Map<String, Object> resumo(Long empresaId, Integer ano, Integer mes) {
        List<MetaComercial> list = listar(empresaId, ano, mes);
        BigDecimal meta = list.stream().map(MetaComercial::getValorMeta).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal real = list.stream().map(MetaComercial::getValorRealizado).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("qtd", list.size());
        out.put("valorMeta", meta);
        out.put("valorRealizado", real);
        out.put("atingimentoPct", meta.signum() == 0 ? BigDecimal.ZERO
                : real.multiply(BigDecimal.valueOf(100)).divide(meta, 2, RoundingMode.HALF_UP));
        return out;
    }
}
