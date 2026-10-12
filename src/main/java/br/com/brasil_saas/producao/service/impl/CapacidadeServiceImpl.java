package br.com.brasil_saas.producao.service.impl;

import br.com.brasil_saas.producao.model.AlocacaoCapacidade;
import br.com.brasil_saas.producao.model.CentroTrabalho;
import br.com.brasil_saas.producao.model.OperacaoRoteiro;
import br.com.brasil_saas.producao.model.Producao;
import br.com.brasil_saas.producao.model.RoteiroProducao;
import br.com.brasil_saas.producao.repository.AlocacaoCapacidadeRepository;
import br.com.brasil_saas.producao.repository.CentroTrabalhoRepository;
import br.com.brasil_saas.producao.repository.OperacaoRoteiroRepository;
import br.com.brasil_saas.producao.repository.ProducaoRepository;
import br.com.brasil_saas.producao.repository.RoteiroProducaoRepository;
import br.com.brasil_saas.producao.service.CapacidadeService;
import br.com.brasil_saas.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Capacidade finita sobre o calendario de carga.
 *
 * Tempo da operacao = setup (uma vez por lote) + maquina_minutos * quantidade.
 * Operacoes rodam em sequencia. Cada operacao consome as horas livres dos dias
 * uteis (seg-sex) do seu centro: livre = capacidade_horas_dia - ja alocado no
 * calendario - ja alocado por este mesmo plano. Uma operacao pode se espalhar
 * por varios dias; a seguinte comeca no dia em que a anterior termina.
 *
 * Feriados nao sao considerados (nao ha calendario de feriados no sistema).
 */
@Service
@RequiredArgsConstructor
public class CapacidadeServiceImpl implements CapacidadeService {

    private static final BigDecimal SESSENTA = BigDecimal.valueOf(60);
    private static final int ESCALA = 4;
    private static final int HORIZONTE_DIAS = 400;

    private final RoteiroProducaoRepository roteiros;
    private final OperacaoRoteiroRepository operacoes;
    private final CentroTrabalhoRepository centros;
    private final AlocacaoCapacidadeRepository alocacoes;
    private final ProducaoRepository producoes;

    private record Fatia(Long centroId, Long operacaoId, LocalDate data, BigDecimal horas) {}

    private static final class Plano {
        RoteiroProducao roteiro;
        final List<Map<String, Object>> linhas = new ArrayList<>();
        final List<Fatia> fatias = new ArrayList<>();
        final Map<Long, BigDecimal> horasPorCentro = new LinkedHashMap<>();
        final List<String> alertas = new ArrayList<>();
        BigDecimal totalHoras = BigDecimal.ZERO;
        LocalDate inicio;
        LocalDate fim;
    }

    // ------------------------------------------------------------ publico

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> simular(Long empresaId, Request req) {
        validar(req == null ? null : req.produtoId(), req == null ? null : req.quantidade());
        Plano p = planejar(empresaId, req.produtoId(), req.quantidade(), req.dataInicio(), null);
        return resposta(req.produtoId(), req.quantidade(), p);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> cargaPorCentro(Long empresaId, List<Request> pedidos) {
        Map<Long, BigDecimal> total = new LinkedHashMap<>();
        for (Request r : pedidos == null ? List.<Request>of() : pedidos) {
            validar(r.produtoId(), r.quantidade());
            planejar(empresaId, r.produtoId(), r.quantidade(), r.dataInicio(), null)
                    .horasPorCentro.forEach((id, h) -> total.merge(id, h, BigDecimal::add));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        total.forEach((id, horas) -> {
            CentroTrabalho ct = centro(empresaId, id, new HashMap<>());
            if (ct == null) return;
            BigDecimal cap = nz(ct.getCapacidadeHorasDia());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("centroTrabalhoId", id);
            r.put("centroTrabalho", ct.getCodigo());
            r.put("horasNecessarias", horas);
            r.put("capacidadeHorasDia", cap);
            r.put("diasNecessarios", cap.signum() > 0 ? horas.divide(cap, 2, RoundingMode.HALF_UP) : null);
            out.add(r);
        });
        out.sort((a, b) -> {
            BigDecimal x = (BigDecimal) a.get("diasNecessarios"), y = (BigDecimal) b.get("diasNecessarios");
            if (x == null) return 1;
            if (y == null) return -1;
            return y.compareTo(x);
        });
        return out;
    }

    @Override
    @Transactional
    public Map<String, Object> agendar(Long empresaId, AgendarRequest req) {
        validar(req == null ? null : req.produtoId(), req == null ? null : req.quantidade());
        Long ordemId = req.ordemProducaoId();
        if (ordemId != null) liberarOrdem(empresaId, ordemId);   // reagendar substitui

        Plano p = planejar(empresaId, req.produtoId(), req.quantidade(), req.dataInicio(), ordemId);

        List<AlocacaoCapacidade> novas = new ArrayList<>();
        for (Fatia f : p.fatias) {
            AlocacaoCapacidade a = new AlocacaoCapacidade();
            a.setEmpresaId(empresaId);
            a.setCentroTrabalhoId(f.centroId());
            a.setOperacaoRoteiroId(f.operacaoId());
            a.setData(f.data());
            a.setHoras(f.horas());
            a.setOrdemProducaoId(ordemId);
            a.setOrigem(ordemId == null ? "RESERVA" : "OP");
            novas.add(a);
        }
        List<AlocacaoCapacidade> salvas = alocacoes.saveAll(novas);

        Map<String, Object> out = resposta(req.produtoId(), req.quantidade(), p);
        out.put("ordemProducaoId", ordemId);
        out.put("alocacaoIds", salvas.stream().map(AlocacaoCapacidade::getId).toList());
        out.put("horasReservadas", p.fatias.stream().map(Fatia::horas).reduce(BigDecimal.ZERO, BigDecimal::add));
        return out;
    }

    @Override
    @Transactional
    public Map<String, Object> agendarOrdem(Long empresaId, Long ordemProducaoId, LocalDate dataInicio) {
        Producao op = producoes.findById(ordemProducaoId)
                .filter(x -> empresaId.equals(x.getEmpresaId()))
                .orElseThrow(() -> new BusinessException("Ordem de produção não encontrada"));
        if (!"ABERTO".equals(op.getStatus()) && !"EM_PROCESSO".equals(op.getStatus())) {
            throw new BusinessException("Só é possível agendar ordem ABERTA ou EM_PROCESSO (atual: " + op.getStatus() + ")");
        }
        return agendar(empresaId, new AgendarRequest(
                op.getProdutoFinalId(), op.getQuantidadePlanejada(), dataInicio, op.getId()));
    }

    @Override
    @Transactional
    public int liberarOrdem(Long empresaId, Long ordemProducaoId) {
        List<AlocacaoCapacidade> ativas =
                alocacoes.findByEmpresaIdAndOrdemProducaoIdAndDeletedAtIsNull(empresaId, ordemProducaoId);
        LocalDateTime agora = LocalDateTime.now();
        ativas.forEach(a -> a.setDeletedAt(agora));
        alocacoes.saveAll(ativas);
        return ativas.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> calendario(Long empresaId, Long centroId, LocalDate de, LocalDate ate) {
        if (centroId == null) throw new BusinessException("Informe o centro de trabalho");
        LocalDate ini = de == null ? LocalDate.now() : de;
        LocalDate fim = ate == null ? ini.plusDays(29) : ate;
        if (fim.isBefore(ini)) throw new BusinessException("Período inválido");
        if (ini.plusDays(366).isBefore(fim)) throw new BusinessException("Período máximo de 366 dias");

        CentroTrabalho ct = centro(empresaId, centroId, new HashMap<>());
        if (ct == null) throw new BusinessException("Centro de trabalho não encontrado");
        BigDecimal cap = nz(ct.getCapacidadeHorasDia());
        Map<LocalDate, BigDecimal> alocado = somaPorDia(empresaId, centroId, ini, fim, null);

        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = ini; !d.isAfter(fim); d = d.plusDays(1)) {
            boolean util = util(d);
            BigDecimal aloc = alocado.getOrDefault(d, BigDecimal.ZERO);
            BigDecimal capDia = util ? cap : BigDecimal.ZERO;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("data", d);
            r.put("diaUtil", util);
            r.put("capacidadeHoras", capDia);
            r.put("alocadoHoras", aloc);
            r.put("livreHoras", capDia.subtract(aloc).max(BigDecimal.ZERO));
            r.put("sobrecarga", aloc.compareTo(capDia) > 0);
            out.add(r);
        }
        return out;
    }

    // ------------------------------------------------------------ nucleo

    private Plano planejar(Long empresaId, Long produtoId, BigDecimal qtd, LocalDate inicioReq, Long ignorarOrdemId) {
        Plano p = new Plano();
        p.inicio = proximoDiaUtil(inicioReq == null ? LocalDate.now() : inicioReq);
        p.roteiro = roteiroVigente(empresaId, produtoId, p.inicio);

        List<OperacaoRoteiro> ops = operacoes
                .findByEmpresaIdAndRoteiroIdAndDeletedAtIsNullOrderBySequenciaAsc(empresaId, p.roteiro.getId());

        Map<Long, CentroTrabalho> cacheCentros = new HashMap<>();
        Map<Long, Map<LocalDate, BigDecimal>> existente = new HashMap<>();   // calendario gravado
        Map<Long, Map<LocalDate, BigDecimal>> interno = new HashMap<>();     // deste plano

        LocalDate cursor = p.inicio;
        boolean temOperacao = false;

        for (OperacaoRoteiro op : ops) {
            if (!Boolean.TRUE.equals(op.getAtivo())) continue;
            temOperacao = true;

            BigDecimal minutos = nz(op.getSetupMinutos()).add(nz(op.getMaquinaMinutos()).multiply(qtd));
            BigDecimal horas = minutos.divide(SESSENTA, ESCALA, RoundingMode.HALF_UP);

            CentroTrabalho ct = op.getCentroTrabalhoId() == null ? null
                    : centro(empresaId, op.getCentroTrabalhoId(), cacheCentros);
            BigDecimal cap = ct == null ? null : nz(ct.getCapacidadeHorasDia());

            if (ct == null) {
                p.alertas.add("Operação " + op.getCodigo() + " sem centro de trabalho válido: capacidade não validada.");
            } else if (!Boolean.TRUE.equals(ct.getAtivo())) {
                p.alertas.add("Centro de trabalho " + ct.getCodigo() + " está inativo.");
            }
            if (cap != null && cap.signum() <= 0) {
                p.alertas.add("Centro " + ct.getCodigo() + " com capacidade diária zero: capacidade não validada.");
                cap = null;
            }

            LocalDate ini;
            LocalDate fim;
            if (cap == null || horas.signum() == 0) {
                ini = fim = proximoDiaUtil(cursor);
            } else {
                Long cid = ct.getId();
                Map<LocalDate, BigDecimal> ex = existente.computeIfAbsent(cid,
                        k -> somaPorDia(empresaId, k, p.inicio, p.inicio.plusDays(HORIZONTE_DIAS + 60L), ignorarOrdemId));
                Map<LocalDate, BigDecimal> in = interno.computeIfAbsent(cid, k -> new HashMap<>());

                BigDecimal restante = horas;
                LocalDate dia = cursor;
                ini = null;
                fim = null;
                int guarda = 0;
                while (restante.signum() > 0) {
                    if (++guarda > HORIZONTE_DIAS) {
                        throw new BusinessException("Sem capacidade livre no centro " + ct.getCodigo()
                                + " nos próximos " + HORIZONTE_DIAS + " dias");
                    }
                    dia = proximoDiaUtil(dia);
                    BigDecimal livre = cap.subtract(ex.getOrDefault(dia, BigDecimal.ZERO))
                            .subtract(in.getOrDefault(dia, BigDecimal.ZERO));
                    if (livre.signum() > 0) {
                        BigDecimal toma = livre.min(restante);
                        p.fatias.add(new Fatia(cid, op.getId(), dia, toma));
                        in.merge(dia, toma, BigDecimal::add);
                        restante = restante.subtract(toma);
                        if (ini == null) ini = dia;
                        fim = dia;
                    }
                    if (restante.signum() > 0) dia = dia.plusDays(1);
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sequencia", op.getSequencia());
            row.put("codigo", op.getCodigo());
            row.put("operacao", op.getNome());
            row.put("centroTrabalhoId", ct == null ? null : ct.getId());
            row.put("centroTrabalho", ct == null ? null : ct.getCodigo());
            row.put("horas", horas);
            row.put("capacidadeHorasDia", cap);
            row.put("dias", contarDiasUteis(ini, fim));
            row.put("inicio", ini);
            row.put("fim", fim);
            p.linhas.add(row);

            p.totalHoras = p.totalHoras.add(horas);
            if (ct != null) p.horasPorCentro.merge(ct.getId(), horas, BigDecimal::add);
            cursor = fim;
        }

        if (!temOperacao) {
            throw new BusinessException("Roteiro " + p.roteiro.getCodigo() + " não possui operações ativas");
        }
        p.fim = cursor;
        return p;
    }

    private Map<String, Object> resposta(Long produtoId, BigDecimal qtd, Plano p) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("produtoId", produtoId);
        out.put("quantidade", qtd);
        out.put("roteiroId", p.roteiro.getId());
        out.put("roteiro", p.roteiro.getCodigo());
        out.put("versao", p.roteiro.getVersao());
        out.put("operacoes", p.linhas);
        out.put("horasTotais", p.totalHoras);
        out.put("dataInicio", p.inicio);
        out.put("dataTermino", p.fim);
        out.put("leadTimeDiasUteis", contarDiasUteis(p.inicio, p.fim));
        out.put("horasPorCentro", p.horasPorCentro);
        out.put("alertas", p.alertas);
        return out;
    }

    // ------------------------------------------------------------ helpers

    private Map<LocalDate, BigDecimal> somaPorDia(Long empresaId, Long centroId, LocalDate de, LocalDate ate, Long ignorarOrdemId) {
        Map<LocalDate, BigDecimal> m = new HashMap<>();
        for (AlocacaoCapacidade a : alocacoes
                .findByEmpresaIdAndCentroTrabalhoIdAndDataBetweenAndDeletedAtIsNull(empresaId, centroId, de, ate)) {
            if (ignorarOrdemId != null && ignorarOrdemId.equals(a.getOrdemProducaoId())) continue;
            m.merge(a.getData(), nz(a.getHoras()), BigDecimal::add);
        }
        return m;
    }

    private CentroTrabalho centro(Long empresaId, Long id, Map<Long, CentroTrabalho> cache) {
        if (cache.containsKey(id)) return cache.get(id);
        CentroTrabalho ct = centros.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId).orElse(null);
        cache.put(id, ct);
        return ct;
    }

    private RoteiroProducao roteiroVigente(Long empresaId, Long produtoId, LocalDate data) {
        return roteiros.findByEmpresaIdAndProdutoIdAndDeletedAtIsNullOrderByVersaoDesc(empresaId, produtoId)
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.getAtivo()))
                .filter(r -> r.getVigenciaInicio() == null || !r.getVigenciaInicio().isAfter(data))
                .filter(r -> r.getVigenciaFim() == null || !r.getVigenciaFim().isBefore(data))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Produto " + produtoId + " não possui roteiro ativo e vigente em " + data));
    }

    private static void validar(Long produtoId, BigDecimal qtd) {
        if (produtoId == null || qtd == null || qtd.signum() <= 0) {
            throw new BusinessException("Produto e quantidade positiva são obrigatórios");
        }
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static boolean util(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private static LocalDate proximoDiaUtil(LocalDate d) {
        while (!util(d)) d = d.plusDays(1);
        return d;
    }

    private static long contarDiasUteis(LocalDate ini, LocalDate fim) {
        long n = 0;
        for (LocalDate d = ini; !d.isAfter(fim); d = d.plusDays(1)) if (util(d)) n++;
        return n;
    }
}
