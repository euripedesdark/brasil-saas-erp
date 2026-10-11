package br.com.brasil_saas.financeiro.controller;

import br.com.brasil_saas.financeiro.model.*;
import br.com.brasil_saas.financeiro.repository.*;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/financeiro/conciliacoes")
@RequiredArgsConstructor
public class ConciliacaoController {
    private final ConciliacaoBancariaRepository conciliacaoRepository;
    private final ConciliacaoItemRepository itemRepository;
    private final ExtratoRepository extratoRepository;
    private final BaixaRepository baixaRepository;
    private final ContaBancariaRepository contaRepository;

    public record CriarRequest(Long contaBancariaId, LocalDate dataInicio, LocalDate dataFim) {}
    public record VincularRequest(Long extratoId, Long baixaId) {}
    public record BaixaCandidata(Long id, Long tituloId, Long parcelaId, LocalDate dataBaixa,
                                 java.math.BigDecimal valorBaixa, java.math.BigDecimal valorDesconto,
                                 java.math.BigDecimal valorJuro, java.math.BigDecimal valorMulta) {}

    @GetMapping
    @PreAuthorize("hasAuthority('financeiro:extrato:leitura')")
    public List<ConciliacaoBancaria> listar(@AuthenticationPrincipal AuthenticatedUser u) {
        return conciliacaoRepository.findByEmpresaIdAndDeletedAtIsNullOrderByDataFimDesc(u.getEmpresaId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('financeiro:extrato:escrita')")
    public ConciliacaoBancaria criar(@AuthenticationPrincipal AuthenticatedUser u, @Valid @RequestBody CriarRequest r) {
        if (r.contaBancariaId() == null || r.dataInicio() == null || r.dataFim() == null || r.dataFim().isBefore(r.dataInicio())) {
            throw new BusinessException("Conta e período de conciliação são obrigatórios");
        }
        contaRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(r.contaBancariaId(), u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conta bancária não encontrada"));
        ConciliacaoBancaria c = new ConciliacaoBancaria();
        c.setEmpresaId(u.getEmpresaId());
        c.setContaBancariaId(r.contaBancariaId());
        c.setDataInicio(r.dataInicio());
        c.setDataFim(r.dataFim());
        c.setStatus("EM_ABERTO");
        return conciliacaoRepository.save(c);
    }

    @GetMapping("/{id}/itens")
    @PreAuthorize("hasAuthority('financeiro:extrato:leitura')")
    public List<ConciliacaoItem> itens(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        conciliacaoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conciliação não encontrada"));
        return itemRepository.findByConciliacaoIdAndDeletedAtIsNullOrderByIdAsc(id);
    }

    @GetMapping("/{id}/pendentes")
    @PreAuthorize("hasAuthority('financeiro:extrato:leitura')")
    public List<Extrato> pendentes(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        ConciliacaoBancaria c = conciliacaoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conciliação não encontrada"));
        return extratoRepository.findByEmpresaIdAndConciliadoFalseAndDeletedAtIsNull(u.getEmpresaId()).stream()
            .filter(e -> c.getContaBancariaId().equals(e.getContaBancariaId()))
            .filter(e -> e.getDataMovimento() != null)
            .filter(e -> !e.getDataMovimento().isBefore(c.getDataInicio()) && !e.getDataMovimento().isAfter(c.getDataFim()))
            .toList();
    }

    @GetMapping("/{id}/baixas")
    @PreAuthorize("hasAuthority('financeiro:extrato:leitura')")
    public List<BaixaCandidata> baixas(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        ConciliacaoBancaria c = conciliacaoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conciliação não encontrada"));
        return baixaRepository.findByContaPeriodo(u.getEmpresaId(), c.getContaBancariaId(), c.getDataInicio(), c.getDataFim()).stream()
            .map(b -> new BaixaCandidata(b.getId(), b.getTituloId(), b.getParcelaId(), b.getDataBaixa(),
                b.getValorBaixa(), b.getValorDesconto(), b.getValorJuro(), b.getValorMulta()))
            .toList();
    }

    @PostMapping("/{id}/vincular")
    @PreAuthorize("hasAuthority('financeiro:extrato:escrita')")
    public ConciliacaoItem vincular(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id,
                                    @RequestBody VincularRequest r) {
        ConciliacaoBancaria c = conciliacaoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conciliação não encontrada"));
        if (!"EM_ABERTO".equals(c.getStatus())) throw new BusinessException("Conciliação já fechada");

        Extrato e = extratoRepository.findById(r.extratoId())
            .orElseThrow(() -> new ResourceNotFoundException("Movimento de extrato não encontrado"));
        if (!u.getEmpresaId().equals(e.getEmpresaId()) || !c.getContaBancariaId().equals(e.getContaBancariaId())) {
            throw new BusinessException("Movimento não pertence à conta/empresa da conciliação");
        }
        if (Boolean.TRUE.equals(e.getConciliado())) throw new BusinessException("Movimento já conciliado");
        if (e.getDataMovimento() == null || e.getDataMovimento().isBefore(c.getDataInicio()) || e.getDataMovimento().isAfter(c.getDataFim())) {
            throw new BusinessException("Movimento fora do período da conciliação");
        }
        if (itemRepository.existsByConciliacaoIdAndExtratoIdAndDeletedAtIsNull(id, e.getId())) {
            throw new BusinessException("Movimento já está na conciliação");
        }

        if (r.baixaId() != null) {
            Baixa b = baixaRepository.findById(r.baixaId())
                .orElseThrow(() -> new ResourceNotFoundException("Baixa não encontrada"));
            if (!u.getEmpresaId().equals(b.getEmpresaId())) throw new BusinessException("Baixa não pertence à empresa");
            if (b.getDataBaixa() == null || b.getDataBaixa().isBefore(c.getDataInicio()) || b.getDataBaixa().isAfter(c.getDataFim())) throw new BusinessException("Baixa fora do período da conciliação");
        }

        ConciliacaoItem item = new ConciliacaoItem();
        item.setEmpresaId(u.getEmpresaId());
        item.setConciliacaoId(id);
        item.setExtratoId(e.getId());
        item.setBaixaId(r.baixaId());
        item.setStatus("CONCILIADO");
        e.setConciliado(true);
        extratoRepository.save(e);
        return itemRepository.save(item);
    }

    @PostMapping("/{id}/conciliar-automatico")
    @PreAuthorize("hasAuthority('financeiro:extrato:escrita')")
    @Transactional
    public Map<String, Object> conciliarAutomatico(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id, @RequestParam(required = false, defaultValue = "3") int toleranciaDias) {
        ConciliacaoBancaria c = conciliacaoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conciliação não encontrada"));
        if (!"EM_ABERTO".equals(c.getStatus())) throw new BusinessException("Conciliação já fechada");
        if (toleranciaDias < 0 || toleranciaDias > 30) throw new BusinessException("Tolerância deve estar entre 0 e 30 dias");
        List<ConciliacaoItem> existentes = itemRepository.findByConciliacaoIdAndDeletedAtIsNullOrderByIdAsc(id);
        Set<Long> baixasUsadas = new HashSet<>();
        for (ConciliacaoItem it : existentes) if (it.getBaixaId() != null) baixasUsadas.add(it.getBaixaId());
        List<Baixa> baixas = baixaRepository.findByContaPeriodo(u.getEmpresaId(), c.getContaBancariaId(), c.getDataInicio(), c.getDataFim()).stream()
            .filter(b -> b.getDataBaixa() != null && b.getValorBaixa() != null && !baixasUsadas.contains(b.getId())).toList();
        int vinculados = 0; int semMatch = 0;
        for (Extrato e : extratoRepository.findByEmpresaIdAndConciliadoFalseAndDeletedAtIsNull(u.getEmpresaId())) {
            if (!c.getContaBancariaId().equals(e.getContaBancariaId())) continue;
            if (e.getDataMovimento() == null || e.getDataMovimento().isBefore(c.getDataInicio()) || e.getDataMovimento().isAfter(c.getDataFim())) continue;
            if (e.getValor() == null) { semMatch++; continue; }
            if (itemRepository.existsByConciliacaoIdAndExtratoIdAndDeletedAtIsNull(id, e.getId())) continue;
            Baixa melhor = null;
            for (Baixa b : baixas) {
                if (baixasUsadas.contains(b.getId())) continue;
                if (b.getValorBaixa().abs().compareTo(e.getValor().abs()) != 0) continue;
                long dias = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(b.getDataBaixa(), e.getDataMovimento()));
                if (dias > toleranciaDias) continue;
                melhor = b; break;
            }
            if (melhor == null) { semMatch++; continue; }
            ConciliacaoItem item = new ConciliacaoItem();
            item.setEmpresaId(u.getEmpresaId());
            item.setConciliacaoId(id);
            item.setExtratoId(e.getId());
            item.setBaixaId(melhor.getId());
            item.setStatus("CONCILIADO");
            e.setConciliado(true);
            extratoRepository.save(e);
            itemRepository.save(item);
            baixasUsadas.add(melhor.getId());
            vinculados++;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("vinculados", vinculados); r.put("semMatch", semMatch);
        return r;
    }

    @PostMapping("/{id}/fechar")
    @PreAuthorize("hasAuthority('financeiro:extrato:escrita')")
    public ConciliacaoBancaria fechar(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        ConciliacaoBancaria c = conciliacaoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conciliação não encontrada"));
        if (!"EM_ABERTO".equals(c.getStatus())) throw new BusinessException("Conciliação já fechada");
        c.setStatus("FECHADA");
        return conciliacaoRepository.save(c);
    }
}