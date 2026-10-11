package br.com.brasil_saas.ativos.service;

import br.com.brasil_saas.ativos.model.*;
import br.com.brasil_saas.ativos.repository.*;
import br.com.brasil_saas.contabilidade.model.CtbLancamento;
import br.com.brasil_saas.contabilidade.model.CtbPartida;
import br.com.brasil_saas.contabilidade.service.ContabilidadeService;
import br.com.brasil_saas.financeiro.repository.PlanoContasRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Contabilidade de ativos (FI-AA): classes, movimentos, depreciacao mensal,
 * baixa, reavaliacao, impairment e relatorios de posicao e projecao.
 *
 * <p>Quando a classe do ativo tem as contas preenchidas, cada movimento gera um
 * lancamento contabil ja lancado. Sem as contas o movimento e' registrado so
 * no razao do ativo.
 */
@Service
@RequiredArgsConstructor
public class AtivoContabilService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final AtivoImobilizadoRepository ativos;
    private final ClasseAtivoRepository classes;
    private final AtivoMovimentoRepository movimentos;
    private final DepreciacaoExecucaoRepository execucoes;
    private final ContabilidadeService contabilidade;
    private final PlanoContasRepository contas;

    public record AdicaoReq(LocalDate data, BigDecimal valor, String documento, Long contaContrapartidaId, String observacao) {}
    public record TransferenciaReq(LocalDate data, Long centroCustoDestinoId, String localizacaoDestino, Long responsavelDestinoId, String observacao) {}
    public record ValorReq(LocalDate data, BigDecimal valor, String observacao) {}
    public record BaixaReq(LocalDate data, BigDecimal percentual, BigDecimal valorVenda, Long contaContrapartidaId, String motivo) {}
    private record Linha(Long contaId, Long centroCustoId, BigDecimal debito, BigDecimal credito) {}

    // ---------------------------------------------------------------- classes

    public List<ClasseAtivo> classes(Long empresaId) {
        return classes.findAllByEmpresaIdAndDeletedAtIsNullOrderByCodigo(empresaId);
    }

    @Transactional
    public ClasseAtivo salvarClasse(Long empresaId, Long id, ClasseAtivo c) {
        if (c.getCodigo() == null || c.getCodigo().isBlank()) throw new BusinessException("Código da classe é obrigatório");
        if (c.getDescricao() == null || c.getDescricao().isBlank()) throw new BusinessException("Descrição da classe é obrigatória");
        validarMetodo(c.getMetodoDepreciacao());
        if (c.getVidaUtilMeses() != null && c.getVidaUtilMeses() <= 0) throw new BusinessException("Vida útil deve ser maior que zero");
        if (id != null) {
            ClasseAtivo atual = classe(empresaId, id);
            c.setId(atual.getId());
            c.setUuid(atual.getUuid());
            c.setCreatedAt(atual.getCreatedAt());
        } else {
            c.setId(null);
        }
        for (Long conta : Arrays.asList(c.getContaAtivoId(), c.getContaDepreciacaoAcumuladaId(), c.getContaDespesaDepreciacaoId(),
                c.getContaGanhoBaixaId(), c.getContaPerdaBaixaId(), c.getContaReavaliacaoId(), c.getContaImpairmentId())) {
            if (conta != null && contas.findByIdAndEmpresaIdAndDeletedAtIsNull(conta, empresaId).isEmpty())
                throw new BusinessException("Conta contábil " + conta + " não encontrada nesta empresa");
        }
        c.setEmpresaId(empresaId);
        c.setDeletedAt(null);
        if (c.getMetodoDepreciacao() == null) c.setMetodoDepreciacao(DepreciacaoCalculadora.LINEAR);
        if (c.getAtivo() == null) c.setAtivo(Boolean.TRUE);
        return classes.save(c);
    }

    @Transactional
    public void excluirClasse(Long empresaId, Long id) {
        ClasseAtivo c = classe(empresaId, id);
        boolean emUso = ativos.findAllByEmpresaIdAndDeletedAtIsNullOrderByCodigo(empresaId).stream()
                .anyMatch(a -> id.equals(a.getClasseId()));
        if (emUso) throw new BusinessException("Classe em uso por ativos");
        c.setDeletedAt(LocalDateTime.now());
        classes.save(c);
    }

    ClasseAtivo classe(Long empresaId, Long id) {
        return classes.findById(id)
                .filter(c -> empresaId.equals(c.getEmpresaId()) && c.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Classe de ativo não encontrada"));
    }

    private void validarMetodo(String metodo) {
        if (metodo != null && !Set.of(DepreciacaoCalculadora.LINEAR, DepreciacaoCalculadora.SOMA_DIGITOS,
                DepreciacaoCalculadora.SALDO_DECRESCENTE).contains(metodo))
            throw new BusinessException("Método de depreciação inválido: " + metodo);
    }

    // ----------------------------------------------------------------- ativos

    public AtivoImobilizado ativo(Long empresaId, Long id) {
        return ativos.findById(id)
                .filter(a -> empresaId.equals(a.getEmpresaId()) && a.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Ativo não encontrado"));
    }

    @Transactional
    public AtivoImobilizado criar(Long empresaId, Long usuarioId, AtivoImobilizado a) {
        a.setId(null);
        a.setEmpresaId(empresaId);
        a.setDeletedAt(null);
        aplicarPadroesDaClasse(empresaId, a);
        validarAtivo(a);
        if (a.getValorDepreciado() == null) a.setValorDepreciado(BigDecimal.ZERO);
        if (a.getStatus() == null) a.setStatus("ATIVO");
        AtivoImobilizado salvo = ativos.save(a);
        AtivoMovimento m = movimento(salvo, "AQUISICAO", a.getDataAquisicao() == null ? LocalDate.now() : a.getDataAquisicao(), usuarioId);
        m.setValor(salvo.getValorAquisicao());
        m.setDocumento(salvo.getNumeroDocumento());
        m.setCentroCustoDestinoId(salvo.getCentroCustoId());
        m.setLocalizacaoDestino(salvo.getLocalizacao());
        movimentos.save(m);
        return salvo;
    }

    @Transactional
    public AtivoImobilizado atualizar(Long empresaId, Long id, AtivoImobilizado dados) {
        AtivoImobilizado a = ativo(empresaId, id);
        if ("BAIXADO".equals(a.getStatus())) throw new BusinessException("Ativo baixado não pode ser alterado");
        a.setCodigo(dados.getCodigo());
        a.setDescricao(dados.getDescricao());
        a.setClasse(dados.getClasse());
        a.setClasseId(dados.getClasseId());
        a.setNumeroSerie(dados.getNumeroSerie());
        a.setFabricante(dados.getFabricante());
        a.setModelo(dados.getModelo());
        a.setGarantiaAte(dados.getGarantiaAte());
        a.setFornecedorId(dados.getFornecedorId());
        a.setNumeroDocumento(dados.getNumeroDocumento());
        a.setAtivoPaiId(dados.getAtivoPaiId());
        a.setCritico(dados.getCritico() == null ? Boolean.FALSE : dados.getCritico());
        a.setUnidadeContador(dados.getUnidadeContador());
        a.setVidaUtilMeses(dados.getVidaUtilMeses());
        a.setMetodoDepreciacao(dados.getMetodoDepreciacao());
        a.setTaxaAnual(dados.getTaxaAnual());
        a.setValorResidual(dados.getValorResidual() == null ? BigDecimal.ZERO : dados.getValorResidual());
        // custo, depreciacao, localizacao e centro de custo so mudam por movimento
        if (a.getUltimoPeriodoDepreciado() == null) {
            a.setDataAquisicao(dados.getDataAquisicao());
            a.setDataInicioDepreciacao(dados.getDataInicioDepreciacao());
        }
        aplicarPadroesDaClasse(empresaId, a);
        validarAtivo(a);
        return ativos.save(a);
    }

    private void aplicarPadroesDaClasse(Long empresaId, AtivoImobilizado a) {
        if (a.getClasseId() == null) return;
        ClasseAtivo c = classe(empresaId, a.getClasseId());
        if (a.getClasse() == null || a.getClasse().isBlank()) a.setClasse(c.getDescricao());
        if (a.getVidaUtilMeses() == null) a.setVidaUtilMeses(c.getVidaUtilMeses());
        if (a.getMetodoDepreciacao() == null) a.setMetodoDepreciacao(c.getMetodoDepreciacao());
        if (a.getTaxaAnual() == null) a.setTaxaAnual(c.getTaxaAnual());
    }

    private void validarAtivo(AtivoImobilizado a) {
        if (a.getCodigo() == null || a.getCodigo().isBlank()) throw new BusinessException("Código do ativo é obrigatório");
        if (a.getDescricao() == null || a.getDescricao().isBlank()) throw new BusinessException("Descrição do ativo é obrigatória");
        if (a.getValorAquisicao() == null || a.getValorAquisicao().signum() < 0) throw new BusinessException("Valor de aquisição inválido");
        if (a.getValorResidual() == null) a.setValorResidual(BigDecimal.ZERO);
        if (a.getValorResidual().signum() < 0 || a.getValorResidual().compareTo(a.getValorAquisicao()) > 0) throw new BusinessException("Valor residual inválido");
        if (a.getVidaUtilMeses() == null || a.getVidaUtilMeses() <= 0) throw new BusinessException("Vida útil deve ser maior que zero");
        validarMetodo(a.getMetodoDepreciacao());
        if (a.getValorReavaliacao() == null) a.setValorReavaliacao(BigDecimal.ZERO);
        if (a.getValorImpairment() == null) a.setValorImpairment(BigDecimal.ZERO);
        if (a.getCritico() == null) a.setCritico(Boolean.FALSE);
    }

    public List<AtivoMovimento> movimentos(Long empresaId, Long ativoId) {
        ativo(empresaId, ativoId);
        return movimentos.findAllByEmpresaIdAndAtivoIdAndDeletedAtIsNullOrderByDataMovimentoAscIdAsc(empresaId, ativoId);
    }

    // -------------------------------------------------------------- valores

    public static BigDecimal custoBruto(AtivoImobilizado a) {
        return nz(a.getValorAquisicao()).add(nz(a.getValorReavaliacao()));
    }

    public static BigDecimal valorContabil(AtivoImobilizado a) {
        return custoBruto(a).subtract(nz(a.getValorDepreciado())).subtract(nz(a.getValorImpairment()));
    }

    public static BigDecimal baseDepreciavel(AtivoImobilizado a) {
        return custoBruto(a).subtract(nz(a.getValorResidual())).subtract(nz(a.getValorImpairment())).max(BigDecimal.ZERO);
    }

    public static BigDecimal cotaDoPeriodo(AtivoImobilizado a, YearMonth periodo) {
        LocalDate inicio = a.getDataInicioDepreciacao() != null ? a.getDataInicioDepreciacao() : a.getDataAquisicao();
        if (inicio == null || a.getVidaUtilMeses() == null) return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        return DepreciacaoCalculadora.cota(a.getMetodoDepreciacao(), baseDepreciavel(a), nz(a.getValorDepreciado()),
                a.getVidaUtilMeses(), a.getTaxaAnual(), DepreciacaoCalculadora.indiceMes(inicio, periodo));
    }

    // ------------------------------------------------------------- movimentos

    @Transactional
    public AtivoMovimento adicao(Long empresaId, Long usuarioId, Long id, AdicaoReq r) {
        AtivoImobilizado a = ativoEmUso(empresaId, id);
        BigDecimal valor = positivo(r.valor(), "Valor da adição");
        LocalDate data = r.data() == null ? LocalDate.now() : r.data();
        a.setValorAquisicao(nz(a.getValorAquisicao()).add(valor));
        ativos.save(a);
        AtivoMovimento m = movimento(a, "ADICAO", data, usuarioId);
        m.setValor(valor);
        m.setDocumento(r.documento());
        m.setObservacao(r.observacao());
        ClasseAtivo c = classeDoAtivo(empresaId, a);
        if (c != null) m.setLancamentoId(contabilizar(empresaId, data, "Adição ao ativo " + a.getCodigo(), "ATIVO_ADICAO", a.getId(), List.of(
                new Linha(c.getContaAtivoId(), a.getCentroCustoId(), valor, BigDecimal.ZERO),
                new Linha(r.contaContrapartidaId(), null, BigDecimal.ZERO, valor))));
        return movimentos.save(m);
    }

    @Transactional
    public AtivoMovimento transferir(Long empresaId, Long usuarioId, Long id, TransferenciaReq r) {
        AtivoImobilizado a = ativoEmUso(empresaId, id);
        if (r.centroCustoDestinoId() == null && (r.localizacaoDestino() == null || r.localizacaoDestino().isBlank()) && r.responsavelDestinoId() == null)
            throw new BusinessException("Informe centro de custo, localização ou responsável de destino");
        AtivoMovimento m = movimento(a, "TRANSFERENCIA", r.data() == null ? LocalDate.now() : r.data(), usuarioId);
        m.setValor(valorContabil(a));
        m.setCentroCustoOrigemId(a.getCentroCustoId());
        m.setLocalizacaoOrigem(a.getLocalizacao());
        m.setResponsavelOrigemId(a.getResponsavelId());
        if (r.centroCustoDestinoId() != null) a.setCentroCustoId(r.centroCustoDestinoId());
        if (r.localizacaoDestino() != null && !r.localizacaoDestino().isBlank()) a.setLocalizacao(r.localizacaoDestino());
        if (r.responsavelDestinoId() != null) a.setResponsavelId(r.responsavelDestinoId());
        m.setCentroCustoDestinoId(a.getCentroCustoId());
        m.setLocalizacaoDestino(a.getLocalizacao());
        m.setResponsavelDestinoId(a.getResponsavelId());
        m.setObservacao(r.observacao());
        ativos.save(a);
        return movimentos.save(m);
    }

    @Transactional
    public AtivoMovimento reavaliar(Long empresaId, Long usuarioId, Long id, ValorReq r) {
        AtivoImobilizado a = ativoEmUso(empresaId, id);
        BigDecimal valor = positivo(r.valor(), "Valor da reavaliação");
        LocalDate data = r.data() == null ? LocalDate.now() : r.data();
        a.setValorReavaliacao(nz(a.getValorReavaliacao()).add(valor));
        ativos.save(a);
        AtivoMovimento m = movimento(a, "REAVALIACAO", data, usuarioId);
        m.setValor(valor);
        m.setObservacao(r.observacao());
        ClasseAtivo c = classeDoAtivo(empresaId, a);
        if (c != null) m.setLancamentoId(contabilizar(empresaId, data, "Reavaliação do ativo " + a.getCodigo(), "ATIVO_REAVALIACAO", a.getId(), List.of(
                new Linha(c.getContaAtivoId(), a.getCentroCustoId(), valor, BigDecimal.ZERO),
                new Linha(c.getContaReavaliacaoId(), null, BigDecimal.ZERO, valor))));
        return movimentos.save(m);
    }

    @Transactional
    public AtivoMovimento impairment(Long empresaId, Long usuarioId, Long id, ValorReq r) {
        AtivoImobilizado a = ativoEmUso(empresaId, id);
        BigDecimal valor = positivo(r.valor(), "Valor da perda por impairment");
        if (valor.compareTo(valorContabil(a)) > 0) throw new BusinessException("Impairment maior que o valor contábil do ativo");
        LocalDate data = r.data() == null ? LocalDate.now() : r.data();
        a.setValorImpairment(nz(a.getValorImpairment()).add(valor));
        ativos.save(a);
        AtivoMovimento m = movimento(a, "IMPAIRMENT", data, usuarioId);
        m.setValor(valor);
        m.setObservacao(r.observacao());
        ClasseAtivo c = classeDoAtivo(empresaId, a);
        if (c != null) m.setLancamentoId(contabilizar(empresaId, data, "Impairment do ativo " + a.getCodigo(), "ATIVO_IMPAIRMENT", a.getId(), List.of(
                new Linha(c.getContaImpairmentId(), a.getCentroCustoId(), valor, BigDecimal.ZERO),
                new Linha(c.getContaDepreciacaoAcumuladaId(), null, BigDecimal.ZERO, valor))));
        return movimentos.save(m);
    }

    /** Baixa total (percentual 100 ou nulo) ou parcial, com venda opcional e apuracao de ganho/perda. */
    @Transactional
    public AtivoMovimento baixar(Long empresaId, Long usuarioId, Long id, BaixaReq r) {
        AtivoImobilizado a = ativoEmUso(empresaId, id);
        BigDecimal percentual = r == null || r.percentual() == null ? CEM : r.percentual();
        if (percentual.signum() <= 0 || percentual.compareTo(CEM) > 0) throw new BusinessException("Percentual de baixa deve estar entre 0 e 100");
        BigDecimal venda = r == null || r.valorVenda() == null ? BigDecimal.ZERO : r.valorVenda();
        if (venda.signum() < 0) throw new BusinessException("Valor de venda não pode ser negativo");
        if (venda.signum() > 0 && (r.contaContrapartidaId() == null) && classeDoAtivo(empresaId, a) != null)
            throw new BusinessException("Informe a conta de contrapartida da venda");
        LocalDate data = r == null || r.data() == null ? LocalDate.now() : r.data();
        boolean total = percentual.compareTo(CEM) == 0;
        BigDecimal f = percentual.divide(CEM, 10, RoundingMode.HALF_UP);

        BigDecimal aquisicao = total ? nz(a.getValorAquisicao()) : parte(a.getValorAquisicao(), f);
        BigDecimal reavaliacao = total ? nz(a.getValorReavaliacao()) : parte(a.getValorReavaliacao(), f);
        BigDecimal depreciado = total ? nz(a.getValorDepreciado()) : parte(a.getValorDepreciado(), f);
        BigDecimal imp = total ? nz(a.getValorImpairment()) : parte(a.getValorImpairment(), f);
        BigDecimal residual = total ? nz(a.getValorResidual()) : parte(a.getValorResidual(), f);
        BigDecimal custo = aquisicao.add(reavaliacao);
        BigDecimal redutora = depreciado.add(imp);
        BigDecimal contabil = custo.subtract(redutora);
        BigDecimal resultado = venda.subtract(contabil);

        a.setValorAquisicao(nz(a.getValorAquisicao()).subtract(aquisicao));
        a.setValorReavaliacao(nz(a.getValorReavaliacao()).subtract(reavaliacao));
        a.setValorDepreciado(nz(a.getValorDepreciado()).subtract(depreciado));
        a.setValorImpairment(nz(a.getValorImpairment()).subtract(imp));
        a.setValorResidual(nz(a.getValorResidual()).subtract(residual));
        if (total) {
            a.setStatus("BAIXADO");
            a.setDataBaixa(data);
            a.setValorBaixa(venda);
            a.setMotivoBaixa(r == null ? null : r.motivo());
        }
        ativos.save(a);

        AtivoMovimento m = movimento(a, total ? "BAIXA_TOTAL" : "BAIXA_PARCIAL", data, usuarioId);
        m.setValor(custo);
        m.setValorDepreciacao(redutora);
        m.setValorVenda(venda);
        m.setResultado(resultado);
        m.setObservacao(r == null ? null : r.motivo());
        ClasseAtivo c = classeDoAtivo(empresaId, a);
        if (c != null) {
            List<Linha> linhas = new ArrayList<>();
            linhas.add(new Linha(c.getContaDepreciacaoAcumuladaId(), null, redutora, BigDecimal.ZERO));
            linhas.add(new Linha(c.getContaAtivoId(), a.getCentroCustoId(), BigDecimal.ZERO, custo));
            if (venda.signum() > 0) linhas.add(new Linha(r.contaContrapartidaId(), null, venda, BigDecimal.ZERO));
            if (resultado.signum() > 0) linhas.add(new Linha(c.getContaGanhoBaixaId(), null, BigDecimal.ZERO, resultado));
            if (resultado.signum() < 0) linhas.add(new Linha(c.getContaPerdaBaixaId(), a.getCentroCustoId(), resultado.negate(), BigDecimal.ZERO));
            m.setLancamentoId(contabilizar(empresaId, data, "Baixa do ativo " + a.getCodigo(), "ATIVO_BAIXA", a.getId(), linhas));
        }
        return movimentos.save(m);
    }

    /**
     * Depreciacao acumulada ate o mes corrente para um ativo, registrando a
     * diferenca como movimento. Mantem o comportamento do botao "Depreciar".
     */
    @Transactional
    public AtivoImobilizado depreciarAteHoje(Long empresaId, Long usuarioId, Long id) {
        AtivoImobilizado a = ativo(empresaId, id);
        if (!"ATIVO".equals(a.getStatus())) throw new BusinessException("Somente ativos em situação ATIVO podem ser depreciados");
        LocalDate inicio = a.getDataInicioDepreciacao() != null ? a.getDataInicioDepreciacao() : a.getDataAquisicao();
        if (inicio == null || a.getVidaUtilMeses() == null || a.getVidaUtilMeses() <= 0) throw new BusinessException("Dados de depreciação incompletos");
        YearMonth atual = YearMonth.now();
        YearMonth p = a.getUltimoPeriodoDepreciado() == null ? YearMonth.from(inicio) : YearMonth.parse(a.getUltimoPeriodoDepreciado()).plusMonths(1);
        BigDecimal total = BigDecimal.ZERO;
        for (; !p.isAfter(atual); p = p.plusMonths(1)) {
            BigDecimal cota = cotaDoPeriodo(a, p);
            if (cota.signum() <= 0) continue;
            a.setValorDepreciado(nz(a.getValorDepreciado()).add(cota));
            a.setUltimoPeriodoDepreciado(p.toString());
            total = total.add(cota);
        }
        if (total.signum() > 0) {
            AtivoMovimento m = movimento(a, "DEPRECIACAO", LocalDate.now(), usuarioId);
            m.setPeriodo(atual.toString());
            m.setValor(total);
            m.setObservacao("Depreciação acumulada até " + atual);
            ClasseAtivo c = classeDoAtivo(empresaId, a);
            if (c != null && c.getContaDespesaDepreciacaoId() != null && c.getContaDepreciacaoAcumuladaId() != null)
                m.setLancamentoId(contabilizar(empresaId, LocalDate.now(), "Depreciação do ativo " + a.getCodigo() + " até " + atual,
                        "ATIVO_DEPRECIACAO", a.getId(), List.of(
                                new Linha(c.getContaDespesaDepreciacaoId(), a.getCentroCustoId(), total, BigDecimal.ZERO),
                                new Linha(c.getContaDepreciacaoAcumuladaId(), null, BigDecimal.ZERO, total))));
            movimentos.save(m);
        }
        return ativos.save(a);
    }

    // ---------------------------------------------------- depreciacao mensal

    public List<Map<String, Object>> simular(Long empresaId, YearMonth periodo) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AtivoImobilizado a : ativos.findAllByEmpresaIdAndDeletedAtIsNullOrderByCodigo(empresaId)) {
            if (!"ATIVO".equals(a.getStatus())) continue;
            if (a.getUltimoPeriodoDepreciado() != null && !YearMonth.parse(a.getUltimoPeriodoDepreciado()).isBefore(periodo)) continue;
            BigDecimal cota = cotaDoPeriodo(a, periodo);
            if (cota.signum() <= 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ativoId", a.getId());
            m.put("codigo", a.getCodigo());
            m.put("descricao", a.getDescricao());
            m.put("classeId", a.getClasseId());
            m.put("centroCustoId", a.getCentroCustoId());
            m.put("metodo", a.getMetodoDepreciacao() == null ? DepreciacaoCalculadora.LINEAR : a.getMetodoDepreciacao());
            m.put("baseDepreciavel", baseDepreciavel(a));
            m.put("acumuladaAnterior", nz(a.getValorDepreciado()));
            m.put("cota", cota);
            m.put("acumuladaNova", nz(a.getValorDepreciado()).add(cota));
            m.put("valorContabilApos", valorContabil(a).subtract(cota));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public DepreciacaoExecucao executar(Long empresaId, Long usuarioId, YearMonth periodo) {
        boolean jaExiste = execucoes.findAllByEmpresaIdAndDeletedAtIsNullOrderByPeriodoDescIdDesc(empresaId).stream()
                .anyMatch(e -> periodo.toString().equals(e.getPeriodo()) && "EFETIVADA".equals(e.getStatus()));
        if (jaExiste) throw new BusinessException("Depreciação de " + periodo + " já foi executada");
        for (AtivoImobilizado a : ativos.findAllByEmpresaIdAndDeletedAtIsNullOrderByCodigo(empresaId)) {
            if ("ATIVO".equals(a.getStatus()) && a.getUltimoPeriodoDepreciado() != null
                    && YearMonth.parse(a.getUltimoPeriodoDepreciado()).isAfter(periodo))
                throw new BusinessException("Ativo " + a.getCodigo() + " já está depreciado até " + a.getUltimoPeriodoDepreciado()
                        + "; períodos devem ser executados em ordem cronológica");
        }
        List<Map<String, Object>> linhas = simular(empresaId, periodo);
        if (linhas.isEmpty()) throw new BusinessException("Nenhum ativo a depreciar em " + periodo);

        DepreciacaoExecucao e = new DepreciacaoExecucao();
        e.setEmpresaId(empresaId);
        e.setPeriodo(periodo.toString());
        e.setStatus("EFETIVADA");
        e.setUsuarioId(usuarioId);
        e = execucoes.save(e);

        LocalDate data = periodo.atEndOfMonth();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal contabilizado = BigDecimal.ZERO;
        Map<String, BigDecimal> despesa = new LinkedHashMap<>();
        Map<Long, BigDecimal> acumulada = new LinkedHashMap<>();
        Map<Long, ClasseAtivo> cache = new HashMap<>();
        for (Map<String, Object> l : linhas) {
            AtivoImobilizado a = ativo(empresaId, (Long) l.get("ativoId"));
            BigDecimal cota = (BigDecimal) l.get("cota");
            a.setValorDepreciado(nz(a.getValorDepreciado()).add(cota));
            a.setUltimoPeriodoDepreciado(periodo.toString());
            ativos.save(a);
            AtivoMovimento m = movimento(a, "DEPRECIACAO", data, usuarioId);
            m.setValor(cota);
            m.setExecucaoId(e.getId());
            movimentos.save(m);
            total = total.add(cota);
            ClasseAtivo c = a.getClasseId() == null ? null : cache.computeIfAbsent(a.getClasseId(), k -> classes.findById(k).orElse(null));
            if (c != null && c.getContaDespesaDepreciacaoId() != null && c.getContaDepreciacaoAcumuladaId() != null) {
                despesa.merge(c.getContaDespesaDepreciacaoId() + ":" + (a.getCentroCustoId() == null ? "" : a.getCentroCustoId()), cota, BigDecimal::add);
                acumulada.merge(c.getContaDepreciacaoAcumuladaId(), cota, BigDecimal::add);
                contabilizado = contabilizado.add(cota);
            }
        }
        if (contabilizado.signum() > 0) {
            List<Linha> partidas = new ArrayList<>();
            despesa.forEach((k, v) -> {
                String[] p = k.split(":", -1);
                partidas.add(new Linha(Long.valueOf(p[0]), p[1].isEmpty() ? null : Long.valueOf(p[1]), v, BigDecimal.ZERO));
            });
            acumulada.forEach((conta, v) -> partidas.add(new Linha(conta, null, BigDecimal.ZERO, v)));
            e.setLancamentoId(contabilizar(empresaId, data, "Depreciação de ativos " + periodo, "ATIVO_DEPRECIACAO", e.getId(), partidas));
        }
        e.setQuantidadeAtivos(linhas.size());
        e.setValorTotal(total);
        e.setValorContabilizado(contabilizado);
        return execucoes.save(e);
    }

    public List<DepreciacaoExecucao> execucoes(Long empresaId) {
        return execucoes.findAllByEmpresaIdAndDeletedAtIsNullOrderByPeriodoDescIdDesc(empresaId);
    }

    @Transactional
    public DepreciacaoExecucao estornar(Long empresaId, Long execucaoId) {
        DepreciacaoExecucao e = execucoes.findById(execucaoId)
                .filter(x -> empresaId.equals(x.getEmpresaId()) && x.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Execução não encontrada"));
        if (!"EFETIVADA".equals(e.getStatus())) throw new BusinessException("Execução já estornada");
        boolean temPosterior = execucoes(empresaId).stream()
                .anyMatch(x -> "EFETIVADA".equals(x.getStatus()) && x.getPeriodo().compareTo(e.getPeriodo()) > 0);
        if (temPosterior) throw new BusinessException("Estorne primeiro as execuções de períodos posteriores");
        String anterior = YearMonth.parse(e.getPeriodo()).minusMonths(1).toString();
        for (AtivoMovimento m : movimentos.findAllByExecucaoIdAndDeletedAtIsNull(e.getId())) {
            if (!"ATIVO".equals(m.getStatus())) continue;
            AtivoImobilizado a = ativo(empresaId, m.getAtivoId());
            if (a.getUltimoPeriodoDepreciado() != null && a.getUltimoPeriodoDepreciado().compareTo(e.getPeriodo()) > 0)
                throw new BusinessException("Ativo " + a.getCodigo() + " foi depreciado após " + e.getPeriodo() + "; estorno não permitido");
        }
        for (AtivoMovimento m : movimentos.findAllByExecucaoIdAndDeletedAtIsNull(e.getId())) {
            if (!"ATIVO".equals(m.getStatus())) continue;
            AtivoImobilizado a = ativo(empresaId, m.getAtivoId());
            if ("BAIXADO".equals(a.getStatus())) throw new BusinessException("Ativo " + a.getCodigo() + " já foi baixado; estorno não permitido");
            a.setValorDepreciado(nz(a.getValorDepreciado()).subtract(m.getValor()).max(BigDecimal.ZERO));
            if (e.getPeriodo().equals(a.getUltimoPeriodoDepreciado())) a.setUltimoPeriodoDepreciado(anterior);
            ativos.save(a);
            m.setStatus("ESTORNADO");
            movimentos.save(m);
        }
        if (e.getLancamentoId() != null) contabilidade.estornar(empresaId, e.getLancamentoId(), "Estorno da depreciação " + e.getPeriodo());
        e.setStatus("ESTORNADA");
        e.setEstornadaEm(LocalDateTime.now());
        return execucoes.save(e);
    }

    // -------------------------------------------------------------- relatorios

    public Map<String, Object> posicao(Long empresaId) {
        Map<String, Map<String, Object>> porClasse = new LinkedHashMap<>();
        BigDecimal[] totais = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        int quantidade = 0;
        for (AtivoImobilizado a : ativos.findAllByEmpresaIdAndDeletedAtIsNullOrderByCodigo(empresaId)) {
            if ("BAIXADO".equals(a.getStatus())) continue;
            String chave = a.getClasse() == null || a.getClasse().isBlank() ? "Sem classe" : a.getClasse();
            Map<String, Object> g = porClasse.computeIfAbsent(chave, k -> {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("classe", k);
                n.put("quantidade", 0);
                n.put("custo", BigDecimal.ZERO);
                n.put("depreciacaoAcumulada", BigDecimal.ZERO);
                n.put("valorContabil", BigDecimal.ZERO);
                return n;
            });
            BigDecimal redutora = nz(a.getValorDepreciado()).add(nz(a.getValorImpairment()));
            g.put("quantidade", (Integer) g.get("quantidade") + 1);
            g.put("custo", ((BigDecimal) g.get("custo")).add(custoBruto(a)));
            g.put("depreciacaoAcumulada", ((BigDecimal) g.get("depreciacaoAcumulada")).add(redutora));
            g.put("valorContabil", ((BigDecimal) g.get("valorContabil")).add(valorContabil(a)));
            totais[0] = totais[0].add(custoBruto(a));
            totais[1] = totais[1].add(redutora);
            totais[2] = totais[2].add(valorContabil(a));
            quantidade++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("porClasse", new ArrayList<>(porClasse.values()));
        out.put("quantidade", quantidade);
        out.put("custo", totais[0]);
        out.put("depreciacaoAcumulada", totais[1]);
        out.put("valorContabil", totais[2]);
        return out;
    }

    /** Projecao da depreciacao dos proximos meses, sem gravar nada. */
    public List<Map<String, Object>> projecao(Long empresaId, YearMonth inicio, int meses) {
        if (meses <= 0 || meses > 120) throw new BusinessException("Informe entre 1 e 120 meses");
        List<AtivoImobilizado> base = ativos.findAllByEmpresaIdAndDeletedAtIsNullOrderByCodigo(empresaId).stream()
                .filter(a -> "ATIVO".equals(a.getStatus())).toList();
        Map<Long, BigDecimal> acumulado = new HashMap<>();
        for (AtivoImobilizado a : base) {
            acumulado.put(a.getId(), nz(a.getValorDepreciado()));
            LocalDate ini = a.getDataInicioDepreciacao() != null ? a.getDataInicioDepreciacao() : a.getDataAquisicao();
            if (ini == null || a.getVidaUtilMeses() == null) continue;
            YearMonth p = a.getUltimoPeriodoDepreciado() == null ? YearMonth.from(ini) : YearMonth.parse(a.getUltimoPeriodoDepreciado()).plusMonths(1);
            for (; p.isBefore(inicio); p = p.plusMonths(1)) {
                acumulado.merge(a.getId(), DepreciacaoCalculadora.cota(a.getMetodoDepreciacao(), baseDepreciavel(a), acumulado.get(a.getId()),
                        a.getVidaUtilMeses(), a.getTaxaAnual(), DepreciacaoCalculadora.indiceMes(ini, p)), BigDecimal::add);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < meses; i++) {
            YearMonth p = inicio.plusMonths(i);
            BigDecimal total = BigDecimal.ZERO;
            for (AtivoImobilizado a : base) {
                if (a.getUltimoPeriodoDepreciado() != null && !YearMonth.parse(a.getUltimoPeriodoDepreciado()).isBefore(p)) continue;
                LocalDate ini = a.getDataInicioDepreciacao() != null ? a.getDataInicioDepreciacao() : a.getDataAquisicao();
                if (ini == null || a.getVidaUtilMeses() == null) continue;
                BigDecimal cota = DepreciacaoCalculadora.cota(a.getMetodoDepreciacao(), baseDepreciavel(a), acumulado.get(a.getId()),
                        a.getVidaUtilMeses(), a.getTaxaAnual(), DepreciacaoCalculadora.indiceMes(ini, p));
                acumulado.merge(a.getId(), cota, BigDecimal::add);
                total = total.add(cota);
            }
            out.add(Map.of("periodo", p.toString(), "depreciacao", total));
        }
        return out;
    }

    // ---------------------------------------------------------------- apoio

    private AtivoImobilizado ativoEmUso(Long empresaId, Long id) {
        AtivoImobilizado a = ativo(empresaId, id);
        if ("BAIXADO".equals(a.getStatus())) throw new BusinessException("Ativo já está baixado");
        return a;
    }

    private ClasseAtivo classeDoAtivo(Long empresaId, AtivoImobilizado a) {
        if (a.getClasseId() == null) return null;
        return classes.findById(a.getClasseId()).filter(c -> empresaId.equals(c.getEmpresaId())).orElse(null);
    }

    private AtivoMovimento movimento(AtivoImobilizado a, String tipo, LocalDate data, Long usuarioId) {
        AtivoMovimento m = new AtivoMovimento();
        m.setEmpresaId(a.getEmpresaId());
        m.setAtivoId(a.getId());
        m.setTipo(tipo);
        m.setDataMovimento(data);
        m.setPeriodo(YearMonth.from(data).toString());
        m.setUsuarioId(usuarioId);
        return m;
    }

    /** Gera e lanca o lancamento. Devolve null quando falta conta em alguma linha com valor. */
    private Long contabilizar(Long empresaId, LocalDate data, String historico, String origemTipo, Long origemId, List<Linha> linhas) {
        List<Linha> comValor = linhas.stream().filter(l -> l.debito().signum() > 0 || l.credito().signum() > 0).toList();
        if (comValor.isEmpty() || comValor.stream().anyMatch(l -> l.contaId() == null)) return null;
        CtbLancamento l = new CtbLancamento();
        l.setEmpresaId(empresaId);
        l.setData(data);
        l.setHistorico(historico);
        l.setOrigemTipo(origemTipo);
        l.setOrigemId(origemId);
        l = contabilidade.salvar(empresaId, l);
        for (Linha x : comValor) {
            CtbPartida p = new CtbPartida();
            p.setEmpresaId(empresaId);
            p.setContaId(x.contaId());
            p.setCentroCustoId(x.centroCustoId());
            p.setDebito(x.debito());
            p.setCredito(x.credito());
            p.setHistorico(historico);
            contabilidade.addPartida(empresaId, l.getId(), p);
        }
        return contabilidade.lancar(empresaId, l.getId()).getId();
    }

    private static BigDecimal positivo(BigDecimal v, String campo) {
        if (v == null || v.signum() <= 0) throw new BusinessException(campo + " deve ser maior que zero");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal parte(BigDecimal v, BigDecimal fator) {
        return nz(v).multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
