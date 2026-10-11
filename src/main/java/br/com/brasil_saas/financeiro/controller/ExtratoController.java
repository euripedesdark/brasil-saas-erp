package br.com.brasil_saas.financeiro.controller;

import br.com.brasil_saas.financeiro.dto.FinanceiroDtos.*;
import br.com.brasil_saas.financeiro.model.ContaBancaria;
import br.com.brasil_saas.financeiro.model.Extrato;
import br.com.brasil_saas.financeiro.repository.ContaBancariaRepository;
import br.com.brasil_saas.financeiro.repository.ExtratoRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/financeiro/extratos")
@RequiredArgsConstructor
public class ExtratoController {
    private final ExtratoRepository repo;
    private final ContaBancariaRepository contaRepository;

    @GetMapping("/conta/{contaId}")
    @PreAuthorize("hasAuthority('financeiro:extrato:leitura')")
    public List<ExtratoResponse> porConta(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long contaId) {
        contaRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(contaId, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conta bancária não encontrada"));
        return repo.findByContaBancariaIdAndDeletedAtIsNullOrderByDataMovimento(contaId).stream()
            .map(this::toResponse).toList();
    }

    @GetMapping("/pendentes-conciliacao")
    @PreAuthorize("hasAuthority('financeiro:extrato:leitura')")
    public List<ExtratoResponse> pendentes(@AuthenticationPrincipal AuthenticatedUser u) {
        return repo.findByEmpresaIdAndConciliadoFalseAndDeletedAtIsNull(u.getEmpresaId())
            .stream().map(this::toResponse).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('financeiro:extrato:escrita')")
    public ResponseEntity<ExtratoResponse> criar(@AuthenticationPrincipal AuthenticatedUser u,
                                                  @Valid @RequestBody ExtratoRequest r) {
        if (r.contaBancariaId() == null || r.dataMovimento() == null || r.valor() == null
            || r.valor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Conta, data e valor positivo são obrigatórios");
        }
        ContaBancaria conta = contaRepository.findForUpdate(r.contaBancariaId(), u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conta bancária não encontrada ou inativa"));

        Extrato e = new Extrato();
        e.setEmpresaId(u.getEmpresaId());
        e.setContaBancariaId(conta.getId());
        e.setDataMovimento(r.dataMovimento());
        e.setDescricao(r.descricao());
        e.setValor(r.valor());
        String tipo = "ENTRADA".equalsIgnoreCase(r.tipo()) || "C".equalsIgnoreCase(r.tipo()) ? "C" : "D";
        e.setTipo(tipo);
        BigDecimal saldoInicial = conta.getSaldoInicial();
        var ultimo = repo.findTopByContaBancariaIdAndDeletedAtIsNullOrderByDataMovimentoDescIdDesc(conta.getId());
        BigDecimal saldoAnterior = ultimo.map(Extrato::getSaldoAtual).orElse(saldoInicial);
        e.setSaldoAnterior(saldoAnterior);
        e.setSaldoAtual("C".equals(tipo)
            ? saldoAnterior.add(r.valor())
            : saldoAnterior.subtract(r.valor()));
        e.setConciliado(false);
        e = repo.save(e);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(e));
    }

    private ExtratoResponse toResponse(Extrato e) {
        return new ExtratoResponse(e.getId(), e.getContaBancariaId(), e.getDataMovimento(),
            e.getDescricao(), e.getValor(), e.getTipo(), e.getSaldoAnterior(), e.getSaldoAtual(), e.getConciliado());
    }
}