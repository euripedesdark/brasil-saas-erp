package br.com.brasil_saas.enterprise.controller;

import br.com.brasil_saas.enterprise.model.GrcAvaliacao;
import br.com.brasil_saas.enterprise.model.GrcControle;
import br.com.brasil_saas.enterprise.model.GrcEvidencia;
import br.com.brasil_saas.enterprise.model.GrcPlanoAcao;
import br.com.brasil_saas.enterprise.model.GrcRisco;
import br.com.brasil_saas.enterprise.model.GrcRiscoControle;
import br.com.brasil_saas.enterprise.model.GrcTesteControle;
import br.com.brasil_saas.enterprise.model.TmsDocumentoEntrega;
import br.com.brasil_saas.enterprise.model.TmsEvento;
import br.com.brasil_saas.enterprise.model.TmsFechamento;
import br.com.brasil_saas.enterprise.model.TmsFrete;
import br.com.brasil_saas.enterprise.model.TmsOrdem;
import br.com.brasil_saas.enterprise.model.TmsParada;
import br.com.brasil_saas.enterprise.model.TmsRota;
import br.com.brasil_saas.enterprise.model.TmsTracking;
import br.com.brasil_saas.enterprise.repository.GrcAvaliacaoRepository;
import br.com.brasil_saas.enterprise.repository.GrcControleRepository;
import br.com.brasil_saas.enterprise.repository.GrcEvidenciaRepository;
import br.com.brasil_saas.enterprise.repository.GrcPlanoAcaoRepository;
import br.com.brasil_saas.enterprise.repository.GrcRiscoControleRepository;
import br.com.brasil_saas.enterprise.repository.GrcRiscoRepository;
import br.com.brasil_saas.enterprise.repository.GrcTesteControleRepository;
import br.com.brasil_saas.enterprise.repository.TmsDocumentoEntregaRepository;
import br.com.brasil_saas.enterprise.repository.TmsEventoRepository;
import br.com.brasil_saas.enterprise.repository.TmsFechamentoRepository;
import br.com.brasil_saas.enterprise.repository.TmsFreteRepository;
import br.com.brasil_saas.enterprise.repository.TmsOrdemRepository;
import br.com.brasil_saas.enterprise.repository.TmsParadaRepository;
import br.com.brasil_saas.enterprise.repository.TmsRotaRepository;
import br.com.brasil_saas.enterprise.repository.TmsTrackingRepository;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import br.com.brasil_saas.shared.web.MapValues;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class RiskTransportEnterpriseController {
    private final GrcRiscoRepository riscoRepository;
    private final GrcControleRepository controleRepository;
    private final GrcRiscoControleRepository vinculoRepository;
    private final GrcEvidenciaRepository evidenciaRepository;
    private final GrcTesteControleRepository testeRepository;
    private final GrcPlanoAcaoRepository planoRepository;
    private final GrcAvaliacaoRepository avaliacaoRepository;
    private final TmsOrdemRepository ordemRepository;
    private final TmsRotaRepository rotaRepository;
    private final TmsParadaRepository paradaRepository;
    private final TmsDocumentoEntregaRepository entregaRepository;
    private final TmsFechamentoRepository fechamentoRepository;
    private final TmsEventoRepository eventoRepository;
    private final TmsTrackingRepository trackingRepository;
    private final TmsFreteRepository freteRepository;

    @GetMapping("/grc/riscos")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> riscos(@AuthenticationPrincipal AuthenticatedUser u) {
        return riscoRepository.findByEmpresaIdOrderByStatusAscNivelDescIdDesc(u.getEmpresaId())
                .stream().map(RiskTransportEnterpriseController::riscoMap).toList();
    }

    @PostMapping("/grc/riscos")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> risco(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String,Object> b) {
        double p = number(b.get("probabilidade")), i = number(b.get("impacto"));
        GrcRisco r = new GrcRisco();
        r.setEmpresaId(u.getEmpresaId());
        r.setCodigo(required(b,"codigo"));
        r.setDescricao(required(b,"descricao"));
        r.setCategoria(MapValues.str(b.get("categoria")));
        r.setProbabilidade(BigDecimal.valueOf(p));
        r.setImpacto(BigDecimal.valueOf(i));
        r.setNivel(BigDecimal.valueOf(p*i));
        r.setStatus(b.get("status") == null ? "ABERTO" : String.valueOf(b.getOrDefault("status","ABERTO")));
        r.setResponsavel(MapValues.str(b.get("responsavel")));
        r.setPrazo(MapValues.dt(b.get("prazo")));
        riscoRepository.save(r);
        return Map.of("ok", true, "nivel", p*i);
    }

    @PutMapping("/grc/riscos/{id}")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> atualizarRisco(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
        GrcRisco r = riscoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
                .orElseThrow(() -> new IllegalArgumentException("Risco inválido"));
        Object p=b.get("probabilidade"),i=b.get("impacto");
        if (b.get("descricao") != null) r.setDescricao(String.valueOf(b.get("descricao")));
        if (b.get("categoria") != null) r.setCategoria(String.valueOf(b.get("categoria")));
        BigDecimal np = p == null ? null : BigDecimal.valueOf(number(p));
        BigDecimal ni = i == null ? null : BigDecimal.valueOf(number(i));
        if (np != null) r.setProbabilidade(np);
        if (ni != null) r.setImpacto(ni);
        if (p != null || i != null) {
            r.setNivel(coalesce(np, r.getProbabilidade()).multiply(coalesce(ni, r.getImpacto())));
        }
        if (b.get("status") != null) r.setStatus(String.valueOf(b.get("status")));
        if (b.get("responsavel") != null) r.setResponsavel(String.valueOf(b.get("responsavel")));
        if (b.get("prazo") != null) r.setPrazo(MapValues.dt(b.get("prazo")));
        riscoRepository.save(r);
        return Map.of("ok",true);
    }

    @PutMapping("/grc/controles/{id}")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> atualizarControle(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
        GrcControle c = controleRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
                .orElseThrow(() -> new IllegalArgumentException("Controle inválido"));
        if (b.get("descricao") != null) c.setDescricao(String.valueOf(b.get("descricao")));
        if (b.get("tipo") != null) c.setTipo(String.valueOf(b.get("tipo")));
        if (b.get("frequencia") != null) c.setFrequencia(String.valueOf(b.get("frequencia")));
        if (b.get("responsavel") != null) c.setResponsavel(String.valueOf(b.get("responsavel")));
        if (b.get("status") != null) c.setStatus(String.valueOf(b.get("status")));
        controleRepository.save(c);
        return Map.of("ok",true);
    }

    @GetMapping("/grc/vinculos")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> vinculos(@AuthenticationPrincipal AuthenticatedUser u){
        Long empresa = u.getEmpresaId();
        Map<Long, String> riscos = riscoRepository.findByEmpresaIdOrderByStatusAscNivelDescIdDesc(empresa)
                .stream().collect(Collectors.toMap(GrcRisco::getId, GrcRisco::getCodigo, (a, b) -> a));
        Map<Long, String> controles = controleRepository.findByEmpresaIdOrderByCodigoAsc(empresa)
                .stream().collect(Collectors.toMap(GrcControle::getId, GrcControle::getCodigo, (a, b) -> a));
        return vinculoRepository.findByEmpresaIdOrderByIdAsc(empresa).stream().map(rc -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", rc.getId()); m.put("empresa_id", rc.getEmpresaId());
            m.put("risco_id", rc.getRiscoId()); m.put("controle_id", rc.getControleId());
            m.put("risco_codigo", riscos.get(rc.getRiscoId()));
            m.put("controle_codigo", controles.get(rc.getControleId()));
            return m;
        }).toList();
    }

    @PutMapping("/tms/ordens/{id}")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> atualizarOrdem(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
        TmsOrdem o = ordemRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
                .orElseThrow(() -> new IllegalArgumentException("Ordem inválida"));
        if (b.get("origem") != null) o.setOrigem(String.valueOf(b.get("origem")));
        if (b.get("destino") != null) o.setDestino(String.valueOf(b.get("destino")));
        Long t = longValue(b.get("transportadoraId")); if (t != null) o.setTransportadoraId(t);
        Long r = longValue(b.get("rotaId")); if (r != null) o.setRotaId(r);
        if (b.get("modalidade") != null) o.setModalidade(String.valueOf(b.get("modalidade")));
        if (b.get("veiculo") != null) o.setVeiculo(String.valueOf(b.get("veiculo")));
        if (b.get("motorista") != null) o.setMotorista(String.valueOf(b.get("motorista")));
        if (b.get("dataPrevistaSaida") != null) o.setDataPrevistaSaida(MapValues.dtTime(b.get("dataPrevistaSaida")));
        if (b.get("dataPrevistaEntrega") != null) o.setDataPrevistaEntrega(MapValues.dtTime(b.get("dataPrevistaEntrega")));
        BigDecimal peso = MapValues.bd(b.get("peso")); if (peso != null) o.setPeso(peso);
        BigDecimal volume = MapValues.bd(b.get("volume")); if (volume != null) o.setVolume(volume);
        BigDecimal fp = MapValues.bd(b.get("fretePrevisto")); if (fp != null) o.setFretePrevisto(fp);
        ordemRepository.save(o);
        return Map.of("ok",true);
    }

    @GetMapping("/grc/controles")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> controles(@AuthenticationPrincipal AuthenticatedUser u) {
        return controleRepository.findByEmpresaIdOrderByCodigoAsc(u.getEmpresaId())
                .stream().map(RiskTransportEnterpriseController::controleMap).toList();
    }

    @PostMapping("/grc/controles")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> controle(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String,Object> b) {
        GrcControle c = new GrcControle();
        c.setEmpresaId(u.getEmpresaId());
        c.setCodigo(required(b,"codigo"));
        c.setDescricao(required(b,"descricao"));
        c.setTipo(String.valueOf(b.getOrDefault("tipo","PREVENTIVO")));
        c.setFrequencia(MapValues.str(b.get("frequencia")));
        c.setResponsavel(MapValues.str(b.get("responsavel")));
        c.setStatus(String.valueOf(b.getOrDefault("status","ATIVO")));
        controleRepository.save(c);
        return Map.of("ok", true);
    }

    @PostMapping("/grc/vinculos")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> vinculo(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        Long risco=longValue(b.get("riscoId")), controle=longValue(b.get("controleId"));
        Long empresa = u.getEmpresaId();
        if(risco==null||controle==null
                || !riscoRepository.existsByIdAndEmpresaId(risco, empresa)
                || !controleRepository.existsByIdAndEmpresaId(controle, empresa))
            throw new IllegalArgumentException("Risco/controle inválido para a empresa");
        if (!vinculoRepository.existsByEmpresaIdAndRiscoIdAndControleId(empresa, risco, controle)) {
            GrcRiscoControle rc = new GrcRiscoControle();
            rc.setEmpresaId(empresa); rc.setRiscoId(risco); rc.setControleId(controle);
            vinculoRepository.save(rc);
        }
        return Map.of("ok",true);
    }

    @GetMapping("/grc/evidencias")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> evidencias(@AuthenticationPrincipal AuthenticatedUser u){
        return evidenciaRepository.findByEmpresaIdOrderByIdDesc(u.getEmpresaId())
                .stream().map(RiskTransportEnterpriseController::evidenciaMap).toList();
    }

    @PostMapping("/grc/evidencias")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> evidencia(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        GrcEvidencia e = new GrcEvidencia();
        e.setEmpresaId(u.getEmpresaId());
        e.setEntidadeTipo(required(b,"entidadeTipo"));
        e.setEntidadeId(longValue(b.get("entidadeId")));
        e.setNome(required(b,"nome"));
        e.setLocalizacao(MapValues.str(b.get("localizacao")));
        e.setValidade(MapValues.dt(b.get("validade")));
        e.setHashDocumento(MapValues.str(b.get("hashDocumento")));
        evidenciaRepository.save(e);
        return Map.of("ok",true);
    }

    @GetMapping("/grc/testes")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> testes(@AuthenticationPrincipal AuthenticatedUser u) {
        Map<Long, String> controles = controleMapById(u.getEmpresaId());
        return testeRepository.findByEmpresaIdOrderByPeriodoDescIdDesc(u.getEmpresaId()).stream().map(t -> {
            Map<String,Object> m = testeMap(t);
            m.put("controle_codigo", controles.get(t.getControleId()));
            return m;
        }).toList();
    }

    @PostMapping("/grc/testes")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> teste(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String,Object> b) {
        Long controle = longValue(b.get("controleId"));
        Long empresa = u.getEmpresaId();
        if (controle == null || !controleRepository.existsByIdAndEmpresaId(controle, empresa))
            throw new IllegalArgumentException("Controle inválido para a empresa");
        String periodo = required(b,"periodo");
        GrcTesteControle t = testeRepository.findByEmpresaIdAndControleIdAndPeriodo(empresa, controle, periodo)
                .orElseGet(GrcTesteControle::new);
        t.setEmpresaId(empresa); t.setControleId(controle); t.setPeriodo(periodo);
        t.setResultado(String.valueOf(b.getOrDefault("resultado","PENDENTE")));
        t.setObservacao(MapValues.str(b.get("observacao")));
        t.setTestadoPor(MapValues.str(b.get("testadoPor")));
        t.setTestadoEm(LocalDateTime.now());
        testeRepository.save(t);
        return Map.of("ok", true);
    }

    @GetMapping("/grc/planos")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> planos(@AuthenticationPrincipal AuthenticatedUser u){
        Map<Long, String> riscos = riscoMapById(u.getEmpresaId());
        return planoRepository.findByEmpresaIdOrderByPrazoAscIdDesc(u.getEmpresaId()).stream().map(p -> {
            Map<String,Object> m = planoMap(p);
            m.put("risco_codigo", riscos.get(p.getRiscoId()));
            return m;
        }).toList();
    }

    @PostMapping("/grc/planos")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> plano(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        Long risco=longValue(b.get("riscoId"));
        Long empresa = u.getEmpresaId();
        if (!riscoRepository.existsByIdAndEmpresaId(risco, empresa)) throw new IllegalArgumentException("Risco inválido");
        GrcPlanoAcao p = new GrcPlanoAcao();
        p.setEmpresaId(empresa); p.setRiscoId(risco);
        p.setDescricao(required(b,"descricao"));
        p.setResponsavel(MapValues.str(b.get("responsavel")));
        p.setPrazo(MapValues.dt(b.get("prazo")));
        p.setStatus(String.valueOf(b.getOrDefault("status","ABERTO")));
        BigDecimal pc = MapValues.bd(b.get("percentualConclusao"));
        if (pc != null) p.setPercentualConclusao(pc);
        p.setEvidenciaId(longValue(b.get("evidenciaId")));
        planoRepository.save(p);
        return Map.of("ok",true);
    }

    @PutMapping("/grc/planos/{id}")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> atualizarPlano(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
        GrcPlanoAcao p = planoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
                .orElseThrow(() -> new IllegalArgumentException("Plano inválido"));
        if (b.get("descricao") != null) p.setDescricao(String.valueOf(b.get("descricao")));
        if (b.get("responsavel") != null) p.setResponsavel(String.valueOf(b.get("responsavel")));
        if (b.get("prazo") != null) p.setPrazo(MapValues.dt(b.get("prazo")));
        if (b.get("status") != null) p.setStatus(String.valueOf(b.get("status")));
        BigDecimal pc = MapValues.bd(b.get("percentualConclusao"));
        if (pc != null) p.setPercentualConclusao(pc);
        Long ev = longValue(b.get("evidenciaId")); if (ev != null) p.setEvidenciaId(ev);
        planoRepository.save(p);
        return Map.of("ok",true);
    }

    @GetMapping("/grc/avaliacoes")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> avaliacoes(@AuthenticationPrincipal AuthenticatedUser u){
        Map<Long, String> riscos = riscoMapById(u.getEmpresaId());
        return avaliacaoRepository.findByEmpresaIdOrderByPeriodoDescIdDesc(u.getEmpresaId()).stream().map(a -> {
            Map<String,Object> m = avaliacaoMap(a);
            m.put("risco_codigo", riscos.get(a.getRiscoId()));
            return m;
        }).toList();
    }

    @PostMapping("/grc/avaliacoes")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> avaliacao(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        Long risco=longValue(b.get("riscoId"));
        double p=number(b.get("probabilidade")),i=number(b.get("impacto"));
        Long empresa = u.getEmpresaId();
        if (!riscoRepository.existsByIdAndEmpresaId(risco, empresa)) throw new IllegalArgumentException("Risco inválido");
        String periodo = required(b,"periodo");
        GrcAvaliacao a = avaliacaoRepository.findByEmpresaIdAndRiscoIdAndPeriodo(empresa, risco, periodo)
                .orElseGet(GrcAvaliacao::new);
        a.setEmpresaId(empresa); a.setRiscoId(risco); a.setPeriodo(periodo);
        a.setProbabilidade(BigDecimal.valueOf(p)); a.setImpacto(BigDecimal.valueOf(i));
        a.setNivel(BigDecimal.valueOf(p*i));
        a.setTendencia(MapValues.str(b.get("tendencia")));
        a.setAvaliador(MapValues.str(b.get("avaliador")));
        a.setObservacao(MapValues.str(b.get("observacao")));
        a.setAvaliadoEm(LocalDateTime.now());
        avaliacaoRepository.save(a);
        return Map.of("ok",true,"nivel",p*i);
    }

    @GetMapping("/tms/ordens")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> ordens(@AuthenticationPrincipal AuthenticatedUser u) {
        return ordemRepository.findByEmpresaIdOrderByIdDesc(u.getEmpresaId())
                .stream().map(RiskTransportEnterpriseController::ordemMap).toList();
    }

    @PostMapping("/tms/ordens")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> ordem(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String,Object> b) {
        TmsOrdem o = new TmsOrdem();
        o.setEmpresaId(u.getEmpresaId());
        o.setNumero(required(b,"numero"));
        o.setOrigem(MapValues.str(b.get("origem"))); o.setDestino(MapValues.str(b.get("destino")));
        o.setTransportadoraId(longValue(b.get("transportadoraId")));
        o.setRotaId(longValue(b.get("rotaId")));
        o.setModalidade(String.valueOf(b.getOrDefault("modalidade","RODOVIARIO")));
        o.setStatus(String.valueOf(b.getOrDefault("status","PLANEJADA")));
        o.setDataPrevistaSaida(MapValues.dtTime(b.get("dataPrevistaSaida")));
        o.setDataPrevistaEntrega(MapValues.dtTime(b.get("dataPrevistaEntrega")));
        o.setPeso(MapValues.bd(b.get("peso"))); o.setVolume(MapValues.bd(b.get("volume")));
        o.setFretePrevisto(MapValues.bd(b.get("fretePrevisto")));
        o.setFreteReal(MapValues.bd(b.get("freteReal")));
        o.setVeiculo(MapValues.str(b.get("veiculo"))); o.setMotorista(MapValues.str(b.get("motorista")));
        ordemRepository.save(o);
        return Map.of("ok", true);
    }

    @GetMapping("/tms/rotas")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> rotas(@AuthenticationPrincipal AuthenticatedUser u){
        return rotaRepository.findByEmpresaIdOrderByCodigoAsc(u.getEmpresaId())
                .stream().map(RiskTransportEnterpriseController::rotaMap).toList();
    }

    @PostMapping("/tms/rotas")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> rota(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        TmsRota r = new TmsRota();
        r.setEmpresaId(u.getEmpresaId());
        r.setCodigo(required(b,"codigo"));
        r.setDescricao(MapValues.str(b.get("descricao")));
        r.setOrigem(required(b,"origem")); r.setDestino(required(b,"destino"));
        r.setDistanciaKm(MapValues.bd(b.get("distanciaKm")));
        r.setTempoEstimadoMin(MapValues.integer(b.get("tempoEstimadoMin")));
        r.setPedagioEstimado(MapValues.bd(b.get("pedagioEstimado")));
        Object ativo = b.getOrDefault("ativo",true);
        r.setAtivo(ativo instanceof Boolean bl ? bl : Boolean.parseBoolean(String.valueOf(ativo)));
        rotaRepository.save(r);
        return Map.of("ok",true);
    }

    @PostMapping("/tms/ordens/{id}/status")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> statusOrdem(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
        TmsOrdem o = ordemRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
                .orElseThrow(() -> new IllegalArgumentException("Ordem inválida"));
        String s=required(b,"status");
        o.setStatus(s);
        LocalDateTime agora = LocalDateTime.now();
        if ("EM_TRANSITO".equals(s) && o.getDataSaida() == null) o.setDataSaida(agora);
        if ("ENTREGUE".equals(s) && o.getDataEntrega() == null) o.setDataEntrega(agora);
        ordemRepository.save(o);
        TmsEvento e = new TmsEvento();
        e.setEmpresaId(u.getEmpresaId()); e.setOrdemId(id); e.setTipo("STATUS"); e.setDescricao(s);
        eventoRepository.save(e);
        return Map.of("ok",true);
    }

    @GetMapping("/tms/paradas")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> paradas(@AuthenticationPrincipal AuthenticatedUser u){
        Map<Long, String> numeros = ordemNumeroMap(u.getEmpresaId());
        return paradaRepository.findByEmpresaIdOrderByOrdemIdAscSequenciaAsc(u.getEmpresaId()).stream().map(p -> {
            Map<String,Object> m = paradaMap(p);
            m.put("numero", numeros.get(p.getOrdemId()));
            return m;
        }).toList();
    }

    @PostMapping("/tms/paradas")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> parada(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        Long id=longValue(b.get("ordemId"));
        Long empresa = u.getEmpresaId();
        if (!ordemRepository.existsByIdAndEmpresaId(id, empresa)) throw new IllegalArgumentException("Ordem inválida");
        TmsParada p = new TmsParada();
        p.setEmpresaId(empresa); p.setOrdemId(id);
        p.setSequencia(intValue(b.get("sequencia")));
        p.setTipo(required(b,"tipo")); p.setLocalizacao(required(b,"localizacao"));
        p.setPrevistaEm(MapValues.dtTime(b.get("previstaEm")));
        p.setStatus(String.valueOf(b.getOrDefault("status","PENDENTE")));
        paradaRepository.save(p);
        return Map.of("ok",true);
    }

    @PostMapping("/tms/paradas/{id}/realizar")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> realizarParada(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
        TmsParada p = paradaRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
                .orElseThrow(() -> new IllegalArgumentException("Parada inválida"));
        p.setStatus("REALIZADA");
        p.setRealizadaEm(LocalDateTime.now());
        paradaRepository.save(p);
        return Map.of("ok",true);
    }

    @GetMapping("/tms/entregas")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> entregas(@AuthenticationPrincipal AuthenticatedUser u){
        Map<Long, String> numeros = ordemNumeroMap(u.getEmpresaId());
        return entregaRepository.findByEmpresaIdOrderByIdDesc(u.getEmpresaId()).stream().map(d -> {
            Map<String,Object> m = entregaMap(d);
            m.put("numero", numeros.get(d.getOrdemId()));
            return m;
        }).toList();
    }

    @PostMapping("/tms/entregas")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> entrega(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        Long id=longValue(b.get("ordemId"));
        Long empresa = u.getEmpresaId();
        TmsOrdem o = ordemRepository.findByIdAndEmpresaId(id, empresa)
                .orElseThrow(() -> new IllegalArgumentException("Ordem inválida"));
        String s=String.valueOf(b.getOrDefault("status","RECEBIDO"));
        TmsDocumentoEntrega d = new TmsDocumentoEntrega();
        d.setEmpresaId(empresa); d.setOrdemId(id);
        d.setTipo(String.valueOf(b.getOrDefault("tipo","COMPROVANTE_ENTREGA")));
        d.setNumero(MapValues.str(b.get("numero")));
        d.setRecebedor(MapValues.str(b.get("recebedor")));
        d.setRecebidoEm(MapValues.dtTime(b.get("recebidoEm")));
        d.setAssinaturaLocalizacao(MapValues.str(b.get("assinaturaLocalizacao")));
        d.setObservacao(MapValues.str(b.get("observacao")));
        d.setDocumentoLocalizacao(MapValues.str(b.get("documentoLocalizacao")));
        d.setStatus(s);
        entregaRepository.save(d);
        if("RECEBIDO".equals(s)) {
            o.setStatus("ENTREGUE");
            ordemRepository.save(o);
        }
        return Map.of("ok",true);
    }

    @GetMapping("/tms/fechamentos")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> fechamentos(@AuthenticationPrincipal AuthenticatedUser u){
        Map<Long, String> numeros = ordemNumeroMap(u.getEmpresaId());
        return fechamentoRepository.findByEmpresaIdOrderByIdDesc(u.getEmpresaId()).stream().map(f -> {
            Map<String,Object> m = fechamentoMap(f);
            m.put("numero", numeros.get(f.getOrdemId()));
            return m;
        }).toList();
    }

    @PostMapping("/tms/fechamentos")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> fechamento(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        Long id=longValue(b.get("ordemId"));
        Long empresa = u.getEmpresaId();
        TmsOrdem o = ordemRepository.findByIdAndEmpresaId(id, empresa)
                .orElseThrow(() -> new IllegalArgumentException("Ordem inválida"));
        double c=number(b.get("freteContratado")),a=number(b.get("adicionais")),d=number(b.get("descontos")),total=c+a-d;
        TmsFechamento f = fechamentoRepository.findByEmpresaIdAndOrdemId(empresa, id)
                .orElseGet(TmsFechamento::new);
        f.setEmpresaId(empresa); f.setOrdemId(id);
        f.setFreteContratado(BigDecimal.valueOf(c));
        f.setAdicionais(BigDecimal.valueOf(a));
        f.setDescontos(BigDecimal.valueOf(d));
        f.setFreteAprovado(BigDecimal.valueOf(total));
        f.setDocumento(MapValues.str(b.get("documento")));
        String s = String.valueOf(b.getOrDefault("status","APROVADO"));
        f.setStatus(s);
        f.setAprovadoPor(MapValues.str(b.get("aprovadoPor")));
        f.setAprovadoEm("APROVADO".equals(s) ? LocalDateTime.now() : null);
        fechamentoRepository.save(f);
        o.setFreteReal(BigDecimal.valueOf(total));
        ordemRepository.save(o);
        return Map.of("ok",true,"freteAprovado",total);
    }

    @GetMapping("/tms/eventos")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> eventos(@AuthenticationPrincipal AuthenticatedUser u) {
        Map<Long, String> numeros = ordemNumeroMap(u.getEmpresaId());
        return eventoRepository.findByEmpresaIdOrderByDataEventoDesc(u.getEmpresaId()).stream().map(e -> {
            Map<String,Object> m = eventoMap(e);
            m.put("numero", numeros.get(e.getOrdemId()));
            return m;
        }).toList();
    }

    @PostMapping("/tms/eventos")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> evento(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String,Object> b) {
        Long ordem = longValue(b.get("ordemId"));
        Long empresa = u.getEmpresaId();
        TmsOrdem o = ordemRepository.findByIdAndEmpresaId(ordem, empresa)
                .orElseThrow(() -> new IllegalArgumentException("Ordem de transporte inválida para a empresa"));
        TmsEvento e = new TmsEvento();
        e.setEmpresaId(empresa); e.setOrdemId(ordem);
        e.setTipo(required(b,"tipo"));
        e.setDataEvento(MapValues.dtTime(b.get("dataEvento")));
        e.setLocalizacao(MapValues.str(b.get("localizacao")));
        e.setDescricao(MapValues.str(b.get("descricao")));
        eventoRepository.save(e);
        if (b.get("status") != null) {
            o.setStatus(String.valueOf(b.get("status")));
            ordemRepository.save(o);
        }
        return Map.of("ok", true);
    }

    @GetMapping("/tms/tracking")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> tracking(@AuthenticationPrincipal AuthenticatedUser u) {
        Map<Long, TmsOrdem> ordens = ordemRepository.findByEmpresaIdOrderByIdDesc(u.getEmpresaId())
                .stream().collect(Collectors.toMap(TmsOrdem::getId, Function.identity(), (a, b) -> a));
        return trackingRepository.findByEmpresaIdOrderByUltimaAtualizacaoDesc(u.getEmpresaId()).stream().map(t -> {
            Map<String,Object> m = trackingMap(t);
            TmsOrdem o = ordens.get(t.getOrdemId());
            m.put("numero", o == null ? null : o.getNumero());
            m.put("origem", o == null ? null : o.getOrigem());
            m.put("destino", o == null ? null : o.getDestino());
            m.put("ordem_status", o == null ? null : o.getStatus());
            return m;
        }).toList();
    }

    @PostMapping("/tms/tracking")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> trackingSave(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b) {
        Long ordem=longValue(b.get("ordemId"));
        Long empresa = u.getEmpresaId();
        if(!ordemRepository.existsByIdAndEmpresaId(ordem, empresa))
            throw new IllegalArgumentException("Ordem de transporte inválida para a empresa");
        TmsTracking t = trackingRepository.findByEmpresaIdAndOrdemId(empresa, ordem)
                .orElseGet(TmsTracking::new);
        t.setEmpresaId(empresa); t.setOrdemId(ordem);
        t.setCodigo(required(b,"codigo"));
        t.setTransportadora(MapValues.str(b.get("transportadora")));
        t.setUltimoStatus(MapValues.str(b.get("ultimoStatus")));
        t.setUltimaAtualizacao(LocalDateTime.now());
        trackingRepository.save(t);
        return Map.of("ok",true);
    }

    @GetMapping("/tms/fretes")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> fretes(@AuthenticationPrincipal AuthenticatedUser u) {
        Map<Long, String> numeros = ordemNumeroMap(u.getEmpresaId());
        return freteRepository.findByEmpresaIdOrderByIdDesc(u.getEmpresaId()).stream().map(f -> {
            Map<String,Object> m = freteMap(f);
            m.put("numero", numeros.get(f.getOrdemId()));
            return m;
        }).toList();
    }

    @PostMapping("/tms/fretes")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    @Transactional
    public Map<String,Object> frete(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b) {
        Long ordem=longValue(b.get("ordemId"));
        Long empresa = u.getEmpresaId();
        if(!ordemRepository.existsByIdAndEmpresaId(ordem, empresa))
            throw new IllegalArgumentException("Ordem de transporte inválida para a empresa");
        TmsFrete f = new TmsFrete();
        f.setEmpresaId(empresa); f.setOrdemId(ordem);
        f.setComponente(required(b,"componente"));
        f.setValor(BigDecimal.valueOf(number(b.get("valor"))));
        f.setDocumento(MapValues.str(b.get("documento")));
        f.setStatus(String.valueOf(b.getOrDefault("status","PENDENTE")));
        freteRepository.save(f);
        return Map.of("ok",true);
    }

    private Map<Long, String> ordemNumeroMap(Long empresa) {
        return ordemRepository.findByEmpresaIdOrderByIdDesc(empresa)
                .stream().collect(Collectors.toMap(TmsOrdem::getId, TmsOrdem::getNumero, (a, b) -> a));
    }

    private Map<Long, String> riscoMapById(Long empresa) {
        return riscoRepository.findByEmpresaIdOrderByStatusAscNivelDescIdDesc(empresa)
                .stream().collect(Collectors.toMap(GrcRisco::getId, GrcRisco::getCodigo, (a, b) -> a));
    }

    private Map<Long, String> controleMapById(Long empresa) {
        return controleRepository.findByEmpresaIdOrderByCodigoAsc(empresa)
                .stream().collect(Collectors.toMap(GrcControle::getId, GrcControle::getCodigo, (a, b) -> a));
    }

    private static BigDecimal coalesce(BigDecimal v, BigDecimal fb) {
        return v != null ? v : fb;
    }

    private static Map<String,Object> riscoMap(GrcRisco r) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", r.getId()); m.put("uuid", r.getUuid()); m.put("empresa_id", r.getEmpresaId());
        m.put("codigo", r.getCodigo()); m.put("descricao", r.getDescricao()); m.put("categoria", r.getCategoria());
        m.put("probabilidade", r.getProbabilidade()); m.put("impacto", r.getImpacto()); m.put("nivel", r.getNivel());
        m.put("status", r.getStatus()); m.put("responsavel", r.getResponsavel()); m.put("prazo", r.getPrazo());
        m.put("created_at", r.getCreatedAt()); m.put("updated_at", r.getUpdatedAt());
        return m;
    }

    private static Map<String,Object> controleMap(GrcControle c) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", c.getId()); m.put("uuid", c.getUuid()); m.put("empresa_id", c.getEmpresaId());
        m.put("codigo", c.getCodigo()); m.put("descricao", c.getDescricao());
        m.put("tipo", c.getTipo()); m.put("frequencia", c.getFrequencia());
        m.put("responsavel", c.getResponsavel()); m.put("status", c.getStatus());
        return m;
    }

    private static Map<String,Object> testeMap(GrcTesteControle t) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", t.getId()); m.put("empresa_id", t.getEmpresaId()); m.put("controle_id", t.getControleId());
        m.put("periodo", t.getPeriodo()); m.put("resultado", t.getResultado());
        m.put("observacao", t.getObservacao());
        m.put("testado_por", t.getTestadoPor()); m.put("testado_em", t.getTestadoEm());
        return m;
    }

    private static Map<String,Object> planoMap(GrcPlanoAcao p) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", p.getId()); m.put("uuid", p.getUuid()); m.put("empresa_id", p.getEmpresaId());
        m.put("risco_id", p.getRiscoId()); m.put("descricao", p.getDescricao());
        m.put("responsavel", p.getResponsavel()); m.put("prazo", p.getPrazo());
        m.put("status", p.getStatus()); m.put("percentual_conclusao", p.getPercentualConclusao());
        m.put("evidencia_id", p.getEvidenciaId());
        m.put("created_at", p.getCreatedAt()); m.put("updated_at", p.getUpdatedAt());
        return m;
    }

    private static Map<String,Object> avaliacaoMap(GrcAvaliacao a) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", a.getId()); m.put("empresa_id", a.getEmpresaId()); m.put("risco_id", a.getRiscoId());
        m.put("periodo", a.getPeriodo());
        m.put("probabilidade", a.getProbabilidade()); m.put("impacto", a.getImpacto()); m.put("nivel", a.getNivel());
        m.put("tendencia", a.getTendencia()); m.put("avaliador", a.getAvaliador());
        m.put("observacao", a.getObservacao()); m.put("avaliado_em", a.getAvaliadoEm());
        return m;
    }

    private static Map<String,Object> evidenciaMap(GrcEvidencia e) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("empresa_id", e.getEmpresaId());
        m.put("entidade_tipo", e.getEntidadeTipo()); m.put("entidade_id", e.getEntidadeId());
        m.put("nome", e.getNome()); m.put("localizacao", e.getLocalizacao());
        m.put("validade", e.getValidade()); m.put("hash_documento", e.getHashDocumento());
        m.put("created_at", e.getCreatedAt());
        return m;
    }

    private static Map<String,Object> ordemMap(TmsOrdem o) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("uuid", o.getUuid()); m.put("empresa_id", o.getEmpresaId());
        m.put("numero", o.getNumero()); m.put("origem", o.getOrigem()); m.put("destino", o.getDestino());
        m.put("transportadora_id", o.getTransportadoraId()); m.put("modalidade", o.getModalidade());
        m.put("status", o.getStatus());
        m.put("data_prevista_saida", o.getDataPrevistaSaida());
        m.put("data_prevista_entrega", o.getDataPrevistaEntrega());
        m.put("peso", o.getPeso()); m.put("volume", o.getVolume());
        m.put("frete_previsto", o.getFretePrevisto()); m.put("frete_real", o.getFreteReal());
        m.put("rota_id", o.getRotaId()); m.put("veiculo", o.getVeiculo()); m.put("motorista", o.getMotorista());
        m.put("data_saida", o.getDataSaida()); m.put("data_entrega", o.getDataEntrega());
        m.put("distancia_real_km", o.getDistanciaRealKm());
        return m;
    }

    private static Map<String,Object> rotaMap(TmsRota r) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", r.getId()); m.put("empresa_id", r.getEmpresaId());
        m.put("codigo", r.getCodigo()); m.put("descricao", r.getDescricao());
        m.put("origem", r.getOrigem()); m.put("destino", r.getDestino());
        m.put("distancia_km", r.getDistanciaKm()); m.put("tempo_estimado_min", r.getTempoEstimadoMin());
        m.put("pedagio_estimado", r.getPedagioEstimado()); m.put("ativo", r.getAtivo());
        return m;
    }

    private static Map<String,Object> paradaMap(TmsParada p) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", p.getId()); m.put("empresa_id", p.getEmpresaId()); m.put("ordem_id", p.getOrdemId());
        m.put("sequencia", p.getSequencia()); m.put("tipo", p.getTipo()); m.put("localizacao", p.getLocalizacao());
        m.put("prevista_em", p.getPrevistaEm()); m.put("realizada_em", p.getRealizadaEm());
        m.put("status", p.getStatus());
        m.put("referencia_tipo", p.getReferenciaTipo()); m.put("referencia_id", p.getReferenciaId());
        m.put("peso", p.getPeso()); m.put("volume", p.getVolume());
        return m;
    }

    private static Map<String,Object> entregaMap(TmsDocumentoEntrega d) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", d.getId()); m.put("empresa_id", d.getEmpresaId()); m.put("ordem_id", d.getOrdemId());
        m.put("tipo", d.getTipo()); m.put("numero", d.getNumero()); m.put("recebedor", d.getRecebedor());
        m.put("recebido_em", d.getRecebidoEm());
        m.put("assinatura_localizacao", d.getAssinaturaLocalizacao());
        m.put("observacao", d.getObservacao()); m.put("documento_localizacao", d.getDocumentoLocalizacao());
        m.put("status", d.getStatus());
        return m;
    }

    private static Map<String,Object> fechamentoMap(TmsFechamento f) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", f.getId()); m.put("empresa_id", f.getEmpresaId()); m.put("ordem_id", f.getOrdemId());
        m.put("frete_contratado", f.getFreteContratado()); m.put("adicionais", f.getAdicionais());
        m.put("descontos", f.getDescontos()); m.put("frete_aprovado", f.getFreteAprovado());
        m.put("documento", f.getDocumento()); m.put("status", f.getStatus());
        m.put("aprovado_por", f.getAprovadoPor()); m.put("aprovado_em", f.getAprovadoEm());
        return m;
    }

    private static Map<String,Object> eventoMap(TmsEvento e) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("empresa_id", e.getEmpresaId()); m.put("ordem_id", e.getOrdemId());
        m.put("tipo", e.getTipo()); m.put("data_evento", e.getDataEvento());
        m.put("localizacao", e.getLocalizacao()); m.put("descricao", e.getDescricao());
        return m;
    }

    private static Map<String,Object> trackingMap(TmsTracking t) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", t.getId()); m.put("empresa_id", t.getEmpresaId()); m.put("ordem_id", t.getOrdemId());
        m.put("codigo", t.getCodigo()); m.put("transportadora", t.getTransportadora());
        m.put("ultimo_status", t.getUltimoStatus()); m.put("ultima_atualizacao", t.getUltimaAtualizacao());
        return m;
    }

    private static Map<String,Object> freteMap(TmsFrete f) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", f.getId()); m.put("empresa_id", f.getEmpresaId()); m.put("ordem_id", f.getOrdemId());
        m.put("componente", f.getComponente()); m.put("valor", f.getValor());
        m.put("documento", f.getDocumento()); m.put("status", f.getStatus());
        return m;
    }

    private static int intValue(Object v){return v==null?0:Integer.parseInt(v.toString());}
    private static String required(Map<String,Object>b,String k){Object v=b.get(k);if(v==null||v.toString().isBlank())throw new IllegalArgumentException(k+" é obrigatório");return v.toString();}
    private static double number(Object v){return v==null?0:Double.parseDouble(v.toString());}
    private static Long longValue(Object v){return v==null?null:Long.valueOf(v.toString());}
}
