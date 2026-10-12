package br.com.brasil_saas.enterprise.service;

import br.com.brasil_saas.enterprise.model.TmsOrdem;
import br.com.brasil_saas.enterprise.model.TmsParada;
import br.com.brasil_saas.enterprise.repository.TmsOrdemRepository;
import br.com.brasil_saas.enterprise.repository.TmsParadaRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Planejamento de entrega: agrupa entregas em ordens de transporte (uma por carga) com paradas. */
@Service
@RequiredArgsConstructor
public class TmsPlanejamentoService {
    private final TmsOrdemRepository ordemRepository;
    private final TmsParadaRepository paradaRepository;

    @Transactional
    public List<Map<String, Object>> planejar(Long empresaId, String origem, BigDecimal capPeso, BigDecimal capVolume,
                                              Long transportadoraId, LocalDateTime saida,
                                              List<PlanejadorCarga.Entrega> entregas) {
        if (entregas == null || entregas.isEmpty()) throw new BusinessException("Nenhuma entrega informada");
        for (var e : entregas) {
            if (e.referenciaTipo() == null || e.referenciaId() == null) throw new BusinessException("Entrega sem documento de origem");
            boolean ja = paradaRepository.existsByEmpresaIdAndReferenciaTipoAndReferenciaIdAndStatusNot(
                    empresaId, e.referenciaTipo(), e.referenciaId(), "CANCELADA");
            if (ja) throw new BusinessException("Documento " + e.referenciaTipo() + " " + e.referenciaId() + " já está planejado");
        }
        List<PlanejadorCarga.Carga> cargas = PlanejadorCarga.planejar(entregas, capPeso, capVolume);
        List<Map<String, Object>> ordens = new ArrayList<>();
        long base = System.currentTimeMillis();
        int n = 1;
        for (var c : cargas) {
            String numero = "TMS-" + base + "-" + n++;
            TmsOrdem ordem = new TmsOrdem();
            ordem.setEmpresaId(empresaId);
            ordem.setNumero(numero);
            ordem.setOrigem(origem);
            ordem.setDestino(c.destino());
            ordem.setTransportadoraId(transportadoraId);
            ordem.setStatus("PLANEJADA");
            ordem.setDataPrevistaSaida(saida);
            ordem.setPeso(c.peso());
            ordem.setVolume(c.volume());
            ordem = ordemRepository.save(ordem);
            Long ordemId = ordem.getId();
            int seq = 1;
            List<TmsParada> paradas = new ArrayList<>();
            for (var e : c.entregas()) {
                TmsParada p = new TmsParada();
                p.setEmpresaId(empresaId);
                p.setOrdemId(ordemId);
                p.setSequencia(seq++);
                p.setTipo("ENTREGA");
                p.setLocalizacao(e.destino());
                p.setStatus("PENDENTE");
                p.setReferenciaTipo(e.referenciaTipo());
                p.setReferenciaId(e.referenciaId());
                p.setPeso(e.peso());
                p.setVolume(e.volume());
                paradas.add(p);
            }
            paradaRepository.saveAll(paradas);
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("ordemId", ordemId); o.put("numero", numero); o.put("destino", c.destino());
            o.put("peso", c.peso()); o.put("volume", c.volume()); o.put("paradas", c.entregas().size());
            ordens.add(o);
        }
        return ordens;
    }
}
