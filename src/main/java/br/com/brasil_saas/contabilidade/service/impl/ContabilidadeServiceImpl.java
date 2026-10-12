package br.com.brasil_saas.contabilidade.service.impl;
import br.com.brasil_saas.contabilidade.model.*;
import br.com.brasil_saas.contabilidade.repository.*;
import br.com.brasil_saas.contabilidade.service.ContabilidadeService;
import br.com.brasil_saas.financeiro.model.PlanoContas;
import br.com.brasil_saas.financeiro.model.Titulo;
import br.com.brasil_saas.financeiro.repository.PlanoContasRepository;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Service @RequiredArgsConstructor
public class ContabilidadeServiceImpl implements ContabilidadeService {
    private final CtbLancamentoRepository lancamentos;
    private final CtbPartidaRepository partidas;
    private final CtbFechamentoRepository fechamentos;
    private final PlanoContasRepository contas;
    private final TituloRepository titulos;
    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    private void garantir(Optional<?> o, String msg) {
        o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    private String periodoDe(LocalDate d) { return String.format("%04d-%02d", d.getYear(), d.getMonthValue()); }
    private void exigirAberto(Long empresaId, String periodo) {
        boolean fechado = fechamentos.findByEmpresaIdAndDeletedAtIsNull(empresaId).stream()
            .anyMatch(f -> periodo.equals(f.getPeriodo()) && "FECHADO".equals(f.getStatus()));
        if (fechado) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Periodo fechado: " + periodo);
    }
    private String nomeConta(Long empresaId, Long contaId) {
        return contas.findByIdAndEmpresaIdAndDeletedAtIsNull(contaId, empresaId).map(c -> c.getCodigo() + " - " + c.getDescricao()).orElse("Conta " + contaId);
    }
    @Override public List<CtbLancamento> lancamentos(Long empresaId, String periodo, String status) {
        if (periodo != null && !periodo.isBlank()) return lancamentos.findByEmpresaIdAndPeriodoAndDeletedAtIsNullOrderByDataDescIdDesc(empresaId, periodo);
        if (status != null && !status.isBlank()) return lancamentos.findByEmpresaIdAndStatusAndDeletedAtIsNull(empresaId, status);
        return lancamentos.findByEmpresaIdAndDeletedAtIsNull(empresaId);
    }
    @Override @Transactional public CtbLancamento salvar(Long empresaId, CtbLancamento l) {
        l.setId(null);
        if (l.getData() == null) l.setData(LocalDate.now());
        l.setPeriodo(periodoDe(l.getData()));
        exigirAberto(empresaId, l.getPeriodo());
        l.setStatus("RASCUNHO");
        return lancamentos.save(l);
    }
    private CtbLancamento exigirRascunho(Long empresaId, Long id) {
        CtbLancamento l = exigir(lancamentos.findByIdForUpdate(id, empresaId), "Lancamento inexistente");
        if (!"RASCUNHO".equals(l.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Somente rascunho pode ser alterado");
        exigirAberto(empresaId, l.getPeriodo());
        return l;
    }
    @Override @Transactional public CtbPartida addPartida(Long empresaId, Long lancamentoId, CtbPartida p) {
        exigirRascunho(empresaId, lancamentoId);
        garantir(contas.findByIdAndEmpresaIdAndDeletedAtIsNull(p.getContaId(), empresaId), "Conta inexistente");
        BigDecimal d = p.getDebito() == null ? BigDecimal.ZERO : p.getDebito();
        BigDecimal c = p.getCredito() == null ? BigDecimal.ZERO : p.getCredito();
        validarValoresPartida(d, c);
        p.setId(null);
        p.setLancamentoId(lancamentoId);
        p.setDebito(d);
        p.setCredito(c);
        return partidas.save(p);
    }
    @Override public List<CtbPartida> partidas(Long empresaId, Long lancamentoId) {
        garantir(lancamentos.findByIdAndEmpresaIdAndDeletedAtIsNull(lancamentoId, empresaId), "Lancamento inexistente");
        return partidas.findByLancamentoIdAndEmpresaIdAndDeletedAtIsNull(lancamentoId, empresaId);
    }
    @Override @Transactional public void removerPartida(Long empresaId, Long lancamentoId, Long partidaId) {
        exigirRascunho(empresaId, lancamentoId);
        CtbPartida p = exigir(partidas.findByIdAndEmpresaIdAndDeletedAtIsNull(partidaId, empresaId), "Partida inexistente");
        if (!lancamentoId.equals(p.getLancamentoId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida de outro lancamento");
        partidas.delete(p);
    }
    @Override public BigDecimal[] totais(Long empresaId, Long lancamentoId) {
        List<CtbPartida> ps = partidas(empresaId, lancamentoId);
        BigDecimal d = ps.stream().map(CtbPartida::getDebito).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = ps.stream().map(CtbPartida::getCredito).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BigDecimal[]{d, c};
    }
    @Override @Transactional public CtbLancamento lancar(Long empresaId, Long id) {
        CtbLancamento l = exigirRascunho(empresaId, id);
        // Importacoes e rascunhos antigos tambem precisam respeitar a regra na contabilizacao.
        List<CtbPartida> ps = partidas.findByLancamentoIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId);
        for (CtbPartida p : ps) validarValoresPartida(p.getDebito(), p.getCredito());
        BigDecimal[] t = {
            ps.stream().map(CtbPartida::getDebito).reduce(BigDecimal.ZERO, BigDecimal::add),
            ps.stream().map(CtbPartida::getCredito).reduce(BigDecimal.ZERO, BigDecimal::add)
        };
        if (t[0].signum() <= 0 || t[0].compareTo(t[1]) != 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Lancamento desbalanceado");
        l.setStatus("LANCADO");
        return lancamentos.save(l);
    }
    private void validarValoresPartida(BigDecimal debito, BigDecimal credito) {
        if (debito == null || credito == null || debito.signum() < 0 || credito.signum() < 0
                || !(debito.signum() > 0 ^ credito.signum() > 0))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Partida deve ter debito OU credito positivo, sem valores negativos");
    }
    @Override @Transactional public CtbLancamento estornar(Long empresaId, Long id, String motivo) {
        CtbLancamento l = exigir(lancamentos.findByIdForUpdate(id, empresaId), "Lancamento inexistente");
        if (!"LANCADO".equals(l.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Somente lancado pode ser estornado");
        exigirAberto(empresaId, l.getPeriodo());
        CtbLancamento e = new CtbLancamento();
        e.setData(LocalDate.now());
        e.setPeriodo(periodoDe(e.getData()));
        // O estorno e' gravado na data de hoje: esse periodo tambem precisa estar aberto.
        exigirAberto(empresaId, e.getPeriodo());
        e.setHistorico("Estorno de #" + l.getId() + (motivo == null ? "" : " - " + motivo));
        e.setOrigemTipo(l.getOrigemTipo());
        e.setOrigemId(l.getOrigemId());
        e.setStatus("LANCADO");
        e = lancamentos.save(e);
        for (CtbPartida p : partidas(empresaId, id)) {
            CtbPartida r = new CtbPartida();
            r.setLancamentoId(e.getId());
            r.setContaId(p.getContaId());
            r.setCentroCustoId(p.getCentroCustoId());
            r.setDebito(p.getCredito());
            r.setCredito(p.getDebito());
            r.setHistorico(p.getHistorico());
            partidas.save(r);
        }
        l.setStatus("ESTORNADO");
        lancamentos.save(l);
        return e;
    }
    private List<CtbLancamento> lancadosNoPeriodo(Long empresaId, LocalDate de, LocalDate ate) {
        return lancamentos.findByEmpresaIdAndDeletedAtIsNull(empresaId).stream()
            .filter(l -> ("LANCADO".equals(l.getStatus()) || "ESTORNADO".equals(l.getStatus()))
                    && !l.getData().isBefore(de) && !l.getData().isAfter(ate))
            .toList();
    }
    @Override public List<Map<String, Object>> razao(Long empresaId, Long contaId, LocalDate de, LocalDate ate) {
        garantir(contas.findByIdAndEmpresaIdAndDeletedAtIsNull(contaId, empresaId), "Conta inexistente");
        Set<Long> ids = new HashSet<>();
        for (CtbLancamento l : lancadosNoPeriodo(empresaId, de, ate)) ids.add(l.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        BigDecimal saldo = BigDecimal.ZERO;
        for (CtbPartida p : partidas.findByEmpresaIdAndContaIdAndDeletedAtIsNull(empresaId, contaId)) {
            if (!ids.contains(p.getLancamentoId())) continue;
            saldo = saldo.add(p.getDebito()).subtract(p.getCredito());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lancamentoId", p.getLancamentoId());
            m.put("debito", p.getDebito());
            m.put("credito", p.getCredito());
            m.put("saldo", saldo);
            m.put("historico", p.getHistorico());
            out.add(m);
        }
        return out;
    }
    @Override public List<Map<String, Object>> balancete(Long empresaId, LocalDate de, LocalDate ate) {
        Set<Long> ids = new HashSet<>();
        for (CtbLancamento l : lancadosNoPeriodo(empresaId, de, ate)) ids.add(l.getId());
        Map<Long, BigDecimal[]> acc = new LinkedHashMap<>();
        for (CtbLancamento l : lancadosNoPeriodo(empresaId, de, ate))
            for (CtbPartida p : partidas.findByLancamentoIdAndEmpresaIdAndDeletedAtIsNull(l.getId(), empresaId)) {
                BigDecimal[] v = acc.computeIfAbsent(p.getContaId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                v[0] = v[0].add(p.getDebito());
                v[1] = v[1].add(p.getCredito());
            }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal[]> e : acc.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contaId", e.getKey());
            m.put("conta", nomeConta(empresaId, e.getKey()));
            m.put("debito", e.getValue()[0]);
            m.put("credito", e.getValue()[1]);
            m.put("saldo", e.getValue()[0].subtract(e.getValue()[1]));
            out.add(m);
        }
        return out;
    }
    @Override public Map<String, Object> balanco(Long empresaId, int exercicio) {
        LocalDate de = LocalDate.of(exercicio, 1, 1);
        LocalDate ate = LocalDate.of(exercicio, 12, 31);
        Map<String, BigDecimal> grupo = new LinkedHashMap<>();
        grupo.put("ATIVO", BigDecimal.ZERO);
        grupo.put("PASSIVO", BigDecimal.ZERO);
        grupo.put("RESULTADO", BigDecimal.ZERO);
        for (Map<String, Object> linha : balancete(empresaId, de, ate)) {
            Long contaId = (Long) linha.get("contaId");
            BigDecimal saldo = (BigDecimal) linha.get("saldo");
            String codigo = contas.findByIdAndEmpresaIdAndDeletedAtIsNull(contaId, empresaId).map(PlanoContas::getCodigo).orElse("");
            String g = codigo.startsWith("1") ? "ATIVO" : codigo.startsWith("2") ? "PASSIVO" : "RESULTADO";
            grupo.put(g, grupo.get(g).add(saldo));
        }
        return new LinkedHashMap<>(grupo);
    }
    @Override @Transactional public CtbLancamento gerarDeTitulo(Long empresaId, Long userId, Long tituloId, Long contaDebitoId, Long contaCreditoId) {
        Titulo t = exigir(titulos.findForUpdate(tituloId, empresaId), "Titulo inexistente");
        garantir(contas.findByIdAndEmpresaIdAndDeletedAtIsNull(contaDebitoId, empresaId), "Conta debito inexistente");
        garantir(contas.findByIdAndEmpresaIdAndDeletedAtIsNull(contaCreditoId, empresaId), "Conta credito inexistente");
        // O lancamento de emissao usa o valor original; o saldo muda a cada baixa.
        BigDecimal valor = t.getValorOriginal() != null && t.getValorOriginal().signum() > 0 ? t.getValorOriginal() : t.getValorSaldo();
        if (valor == null || valor.signum() <= 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Titulo sem valor para contabilizar");
        if (!lancamentos.findByEmpresaIdAndOrigemTipoAndOrigemIdAndStatusInAndDeletedAtIsNull(
                empresaId, "TITULO", t.getId(), List.of("RASCUNHO", "LANCADO")).isEmpty())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Titulo ja contabilizado");
        CtbLancamento l = new CtbLancamento();
        l.setData(t.getDataEmissao() == null ? LocalDate.now() : t.getDataEmissao());
        l.setPeriodo(periodoDe(l.getData()));
        exigirAberto(empresaId, l.getPeriodo());
        l.setHistorico("Titulo #" + t.getId() + " - " + t.getDescricao());
        l.setOrigemTipo("TITULO");
        l.setOrigemId(t.getId());
        l.setStatus("RASCUNHO");
        l = lancamentos.save(l);
        CtbPartida d = new CtbPartida();
        d.setLancamentoId(l.getId());
        d.setContaId(contaDebitoId);
        d.setDebito(valor);
        d.setCredito(BigDecimal.ZERO);
        partidas.save(d);
        CtbPartida c = new CtbPartida();
        c.setLancamentoId(l.getId());
        c.setContaId(contaCreditoId);
        c.setDebito(BigDecimal.ZERO);
        c.setCredito(valor);
        partidas.save(c);
        return lancar(empresaId, l.getId());
    }
    @Override public List<CtbFechamento> fechamentos(Long empresaId) {
        return fechamentos.findByEmpresaIdAndDeletedAtIsNull(empresaId);
    }
    @Override @Transactional public CtbFechamento fechar(Long empresaId, Long userId, String periodo) {
        if (periodo == null || !periodo.matches("\\d{4}-(0[1-9]|1[0-2])"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo deve estar no formato AAAA-MM");
        if (!lancamentos.findByEmpresaIdAndPeriodoAndDeletedAtIsNullOrderByDataDescIdDesc(empresaId, periodo).stream()
                .filter(x -> "RASCUNHO".equals(x.getStatus())).toList().isEmpty())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Periodo com lancamentos em rascunho: lance ou exclua antes de fechar");
        Optional<CtbFechamento> atual = fechamentos.findByEmpresaIdAndPeriodoAndDeletedAtIsNull(empresaId, periodo);
        if (atual.isPresent()) {
            CtbFechamento f = atual.get();
            f.setStatus("FECHADO");
            f.setFechadoPor(userId);
            f.setFechadoEm(java.time.LocalDateTime.now());
            return fechamentos.save(f);
        }
        CtbFechamento f = new CtbFechamento();
        f.setPeriodo(periodo);
        f.setStatus("FECHADO");
        f.setFechadoPor(userId);
        f.setFechadoEm(java.time.LocalDateTime.now());
        return fechamentos.save(f);
    }
    @Override @Transactional public void reabrir(Long empresaId, String periodo) {
        CtbFechamento f = exigir(fechamentos.findByEmpresaIdAndPeriodoAndDeletedAtIsNull(empresaId, periodo), "Fechamento inexistente");
        f.setStatus("ABERTO");
        fechamentos.save(f);
    }
    @Override public java.util.List<java.util.Map<String, Object>> dre(Long empresaId, int exercicio) {
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (int mes = 1; mes <= 12; mes++) {
            java.time.LocalDate de = java.time.LocalDate.of(exercicio, mes, 1);
            java.time.LocalDate ate = de.withDayOfMonth(de.lengthOfMonth());
            java.math.BigDecimal receitas = java.math.BigDecimal.ZERO;
            java.math.BigDecimal custos = java.math.BigDecimal.ZERO;
            for (CtbLancamento l : lancadosNoPeriodo(empresaId, de, ate))
                for (CtbPartida pt : partidas.findByLancamentoIdAndEmpresaIdAndDeletedAtIsNull(l.getId(), empresaId)) {
                    String codigo = contas.findByIdAndEmpresaIdAndDeletedAtIsNull(pt.getContaId(), empresaId).map(PlanoContas::getCodigo).orElse("");
                    if (!codigo.startsWith("3")) continue;
                    receitas = receitas.add(pt.getCredito() == null ? java.math.BigDecimal.ZERO : pt.getCredito());
                    custos = custos.add(pt.getDebito() == null ? java.math.BigDecimal.ZERO : pt.getDebito());
                }
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("mes", mes);
            m.put("receitas", receitas);
            m.put("custos", custos);
            m.put("resultado", receitas.subtract(custos));
            out.add(m);
        }
        return out;
    }
    @Override @Transactional
    public CtbLancamento apurarResultado(Long empresaId, Long userId, int exercicio, Long contaLucrosId) {
        int anoAtual = LocalDate.now().getYear();
        if (exercicio < 2000 || exercicio >= anoAtual) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Apuracao somente de exercicio encerrado");
        }
        var lucros = exigir(contas.findByIdAndEmpresaIdAndDeletedAtIsNull(contaLucrosId, empresaId), "Conta de lucros acumulados inexistente");
        String codigoLucros = lucros.getCodigo() == null ? "" : lucros.getCodigo();
        if (codigoLucros.startsWith("3")) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Conta de lucros acumulados nao pode ser de resultado");
        }
        for (int mes = 1; mes <= 11; mes++) {
            String periodo = String.format("%04d-%02d", exercicio, mes);
            boolean fechado = fechamentos.findByEmpresaIdAndDeletedAtIsNull(empresaId).stream()
                    .anyMatch(f -> periodo.equals(f.getPeriodo()) && "FECHADO".equals(f.getStatus()));
            if (!fechado) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Periodo " + periodo + " precisa estar fechado");
        }
        String dezembro = String.format("%04d-12", exercicio);
        boolean dezembroFechado = fechamentos.findByEmpresaIdAndDeletedAtIsNull(empresaId).stream()
                .anyMatch(f -> dezembro.equals(f.getPeriodo()) && "FECHADO".equals(f.getStatus()));
        if (dezembroFechado) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Dezembro precisa estar aberto para receber a apuracao");
        boolean jaApurado = lancamentos.findByEmpresaIdAndPeriodoAndDeletedAtIsNullOrderByDataDescIdDesc(empresaId, dezembro).stream()
                .anyMatch(l -> "ENCERRAMENTO".equals(l.getOrigemTipo()) && !"ESTORNADO".equals(l.getStatus()));
        if (jaApurado) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Exercicio ja apurado");
        boolean rascunhoNoAno = lancamentos.findByEmpresaIdAndDeletedAtIsNull(empresaId).stream()
                .anyMatch(l -> "RASCUNHO".equals(l.getStatus()) && l.getPeriodo() != null && l.getPeriodo().startsWith(String.valueOf(exercicio)));
        if (rascunhoNoAno) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Exercicio com lancamentos em rascunho");
        Map<Long, BigDecimal> saldoPorConta = new LinkedHashMap<>();
        for (int mes = 1; mes <= 12; mes++) {
            LocalDate de = LocalDate.of(exercicio, mes, 1);
            LocalDate ate = de.withDayOfMonth(de.lengthOfMonth());
            for (CtbLancamento l : lancadosNoPeriodo(empresaId, de, ate)) {
                for (CtbPartida pt : partidas.findByLancamentoIdAndEmpresaIdAndDeletedAtIsNull(l.getId(), empresaId)) {
                    String codigo = contas.findByIdAndEmpresaIdAndDeletedAtIsNull(pt.getContaId(), empresaId).map(c -> c.getCodigo() == null ? "" : c.getCodigo()).orElse("");
                    if (!codigo.startsWith("3")) continue;
                    BigDecimal liquido = (pt.getCredito() == null ? BigDecimal.ZERO : pt.getCredito()).subtract(pt.getDebito() == null ? BigDecimal.ZERO : pt.getDebito());
                    saldoPorConta.merge(pt.getContaId(), liquido, BigDecimal::add);
                }
            }
        }
        saldoPorConta.entrySet().removeIf(e -> e.getValue().setScale(2, java.math.RoundingMode.HALF_UP).signum() == 0);
        if (saldoPorConta.isEmpty()) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Sem movimento de resultado no exercicio");
        CtbLancamento l = new CtbLancamento();
        l.setData(LocalDate.of(exercicio, 12, 31));
        l.setPeriodo(dezembro);
        exigirAberto(empresaId, l.getPeriodo());
        l.setHistorico("Apuracao do resultado de " + exercicio);
        l.setOrigemTipo("ENCERRAMENTO");
        l.setStatus("RASCUNHO");
        l = lancamentos.save(l);
        for (var e : saldoPorConta.entrySet()) {
            BigDecimal valor = e.getValue().setScale(2, java.math.RoundingMode.HALF_UP).abs();
            boolean lucro = e.getValue().signum() > 0;
            CtbPartida debito = new CtbPartida();
            debito.setLancamentoId(l.getId());
            debito.setContaId(lucro ? e.getKey() : contaLucrosId);
            debito.setDebito(valor);
            debito.setCredito(BigDecimal.ZERO);
            partidas.save(debito);
            CtbPartida credito = new CtbPartida();
            credito.setLancamentoId(l.getId());
            credito.setContaId(lucro ? contaLucrosId : e.getKey());
            credito.setDebito(BigDecimal.ZERO);
            credito.setCredito(valor);
            partidas.save(credito);
        }
        return lancar(empresaId, l.getId());
    }


    @Override @Transactional
    public ContabilidadeService.EspelhoContabil espelharAjusteDevolucao(Long empresaId, Long tituloOrigemId, Long tituloDestinoId, BigDecimal proporcao, String historico) {
        if (proporcao == null || proporcao.signum() <= 0 || proporcao.compareTo(BigDecimal.ONE) > 0) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Proporcao do ajuste invalida");
        }
        var originais = lancamentos.findByEmpresaIdAndOrigemTipoAndOrigemIdAndStatusInAndDeletedAtIsNull(
                empresaId, "TITULO", tituloOrigemId, List.of("LANCADO"));
        if (originais.isEmpty()) {
            return new ContabilidadeService.EspelhoContabil(List.of(), "SEM_LANCAMENTO_ORIGINAL");
        }
        String periodo = periodoDe(LocalDate.now());
        boolean fechado = fechamentos.findByEmpresaIdAndDeletedAtIsNull(empresaId).stream()
                .anyMatch(f -> periodo.equals(f.getPeriodo()) && "FECHADO".equals(f.getStatus()));
        if (fechado) {
            return new ContabilidadeService.EspelhoContabil(List.of(), "PERIODO_FECHADO");
        }
        var gerados = new java.util.ArrayList<CtbLancamento>();
        for (var original : originais) {
            var origem = partidas.findByLancamentoIdAndEmpresaIdAndDeletedAtIsNull(original.getId(), empresaId);
            CtbLancamento l = new CtbLancamento();
            l.setEmpresaId(empresaId);
            l.setData(LocalDate.now());
            l.setPeriodo(periodo);
            l.setHistorico(historico == null || historico.isBlank() ? "Ajuste devolucao titulo #" + tituloDestinoId : historico);
            l.setOrigemTipo("TITULO");
            l.setOrigemId(tituloDestinoId);
            l.setStatus("RASCUNHO");
            l = lancamentos.save(l);
            BigDecimal debito = BigDecimal.ZERO;
            BigDecimal credito = BigDecimal.ZERO;
            BigDecimal maiorValor = BigDecimal.ZERO;
            CtbPartida maior = null;
            boolean maiorDebito = true;
            for (var o : origem) {
                BigDecimal d = o.getCredito() == null ? BigDecimal.ZERO : o.getCredito().multiply(proporcao).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal c = o.getDebito() == null ? BigDecimal.ZERO : o.getDebito().multiply(proporcao).setScale(2, java.math.RoundingMode.HALF_UP);
                if (d.signum() <= 0 && c.signum() <= 0) continue;
                CtbPartida r = new CtbPartida();
                r.setEmpresaId(empresaId);
                r.setLancamentoId(l.getId());
                r.setContaId(o.getContaId());
                r.setCentroCustoId(o.getCentroCustoId());
                r.setDebito(d);
                r.setCredito(c);
                r.setHistorico(o.getHistorico());
                partidas.save(r);
                debito = debito.add(d);
                credito = credito.add(c);
                BigDecimal candidato = d.compareTo(c) >= 0 ? d : c;
                if (maior == null || candidato.compareTo(maiorValor) > 0) { maior = r; maiorValor = candidato; maiorDebito = d.compareTo(c) >= 0; }
            }
            BigDecimal diferenca = debito.subtract(credito);
            if (diferenca.signum() != 0 && maior != null) {
                if (maiorDebito) maior.setDebito(maior.getDebito().subtract(diferenca));
                else maior.setCredito(maior.getCredito().add(diferenca));
                partidas.save(maior);
            }
            gerados.add(lancar(empresaId, l.getId()));
        }
        return new ContabilidadeService.EspelhoContabil(gerados, "APLICADO");
    }
}
