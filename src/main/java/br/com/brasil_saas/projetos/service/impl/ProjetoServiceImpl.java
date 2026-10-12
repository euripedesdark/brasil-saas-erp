package br.com.brasil_saas.projetos.service.impl;

import java.math.BigDecimal;
import br.com.brasil_saas.projetos.model.*;
import br.com.brasil_saas.projetos.repository.*;
import br.com.brasil_saas.projetos.service.ProjetoService;
import br.com.brasil_saas.cadastro.model.Cliente;
import br.com.brasil_saas.cadastro.model.Pessoa;
import br.com.brasil_saas.cadastro.repository.ClienteRepository;
import br.com.brasil_saas.cadastro.repository.PessoaRepository;
import br.com.brasil_saas.cadastro.repository.ServicoRepository;
import br.com.brasil_saas.fiscal.nfse.NfseEmissaoDtos;
import br.com.brasil_saas.fiscal.nfse.NfseEmissaoService;
import br.com.brasil_saas.fiscal.repository.NfseRepository;
import br.com.brasil_saas.workflow.model.WkfDefinition;
import br.com.brasil_saas.workflow.model.WkfInstance;
import br.com.brasil_saas.workflow.model.WkfStage;
import br.com.brasil_saas.workflow.model.WkfTask;
import br.com.brasil_saas.workflow.service.WorkflowService;
import br.com.brasil_saas.financeiro.model.Titulo;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class ProjetoServiceImpl implements ProjetoService {
    private final PrjProjetoRepository projetos;
    private final PrjEtapaRepository etapas;
    private final br.com.brasil_saas.projetos.service.ProjetoDependenciaService dependenciaService;
    private final PrjMovimentoRepository movimentos;
    private final PrjRiscoRepository riscos;
    private final PrjMudancaRepository mudancas;
    private final PrjFaturamentoRepository faturamentos;
    private final WorkflowService workflow;
    private final TituloRepository titulos;
    private final NfseEmissaoService nfseEmissao;
    private final NfseRepository nfseRepo;
    private final ServicoRepository servicos;
    private final ClienteRepository clientes;
    private final PessoaRepository pessoas;
    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    private void garantir(Optional<?> o, String msg) {
        o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    private PrjProjeto exigirProjeto(Long empresaId, Long id) {
        return exigir(projetos.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Projeto inexistente");
    }
    @Override public List<PrjProjeto> projetos(Long empresaId, String status) {
        List<PrjProjeto> base = projetos.findByEmpresaIdAndDeletedAtIsNull(empresaId);
        if (status == null || status.isBlank()) return base;
        return base.stream().filter(p -> status.equals(p.getStatus())).toList();
    }
    @Override @Transactional public PrjProjeto salvar(Long empresaId, PrjProjeto p) {
        p.setId(null);
        p.setEmpresaId(empresaId);
        if (p.getNome() == null || p.getNome().isBlank()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Nome do projeto é obrigatório");
        if (p.getOrcamentoTotal() != null && p.getOrcamentoTotal().signum() < 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Orçamento não pode ser negativo");
        if (p.getStatus() == null) p.setStatus("PLANEJADO");
        return projetos.save(p);
    }
    @Override public List<PrjEtapa> etapas(Long empresaId, Long projetoId) {
        exigirProjeto(empresaId, projetoId);
        return etapas.findByProjetoIdAndEmpresaIdAndDeletedAtIsNullOrderByOrdem(projetoId, empresaId);
    }
    @Override @Transactional public PrjEtapa salvarEtapa(Long empresaId, Long projetoId, PrjEtapa e) {
        exigirProjeto(empresaId, projetoId);
        if (e.getPaiId() != null) garantir(etapas.findByIdAndEmpresaIdAndDeletedAtIsNull(e.getPaiId(), empresaId), "Etapa pai inexistente");
        e.setId(null);
        e.setEmpresaId(empresaId);
        e.setProjetoId(projetoId);
        if (e.getStatus() == null) e.setStatus("NAO_INICIADA");
        return etapas.save(e);
    }
    @Override @Transactional public PrjEtapa avancarEtapa(Long empresaId, Long projetoId, Long etapaId, Integer pct) {
        exigirProjeto(empresaId, projetoId);
        PrjEtapa e = exigir(etapas.findByIdAndEmpresaIdAndDeletedAtIsNull(etapaId, empresaId), "Etapa inexistente");
        if (!e.getProjetoId().equals(projetoId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Etapa de outro projeto");
        int p = Math.min(100, Math.max(0, pct == null ? 0 : pct));
        if(p>0) dependenciaService.validarAvanco(empresaId,projetoId,etapaId);
        e.setConcluidaEm(p==100?(e.getConcluidaEm()==null?LocalDateTime.now():e.getConcluidaEm()):null);
        e.setPctConcluido(p);
        e.setStatus(p <= 0 ? "NAO_INICIADA" : p >= 100 ? "CONCLUIDA" : "EM_ANDAMENTO");
        return etapas.save(e);
    }
    @Override public List<PrjMovimento> movimentos(Long empresaId, Long projetoId, String tipo) {
        exigirProjeto(empresaId, projetoId);
        List<PrjMovimento> base = movimentos.findByProjetoIdAndEmpresaIdAndDeletedAtIsNull(projetoId, empresaId);
        if (tipo == null || tipo.isBlank()) return base;
        return base.stream().filter(m -> tipo.equals(m.getTipo())).toList();
    }
    @Override @Transactional public PrjMovimento lancarMovimento(Long empresaId, Long projetoId, PrjMovimento m) {
        exigirProjeto(empresaId, projetoId);
        if (!"CUSTO".equals(m.getTipo()) && !"RECEITA".equals(m.getTipo())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Tipo deve ser CUSTO ou RECEITA");
        if (m.getEtapaId() != null) garantir(etapas.findByIdAndEmpresaIdAndDeletedAtIsNull(m.getEtapaId(), empresaId), "Etapa inexistente");
        m.setId(null);
        m.setEmpresaId(empresaId);
        m.setProjetoId(projetoId);
        if (m.getValor() == null || m.getValor().signum() < 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Valor do movimento não pode ser negativo");
        if (m.getData() == null) m.setData(LocalDate.now());
        return movimentos.save(m);
    }
    @Override public List<PrjRisco> riscos(Long empresaId, Long projetoId) {
        exigirProjeto(empresaId, projetoId);
        return riscos.findByProjetoIdAndEmpresaIdAndDeletedAtIsNull(projetoId, empresaId);
    }
    @Override @Transactional public PrjRisco salvarRisco(Long empresaId, Long projetoId, PrjRisco r) {
        exigirProjeto(empresaId, projetoId);
        r.setId(null);
        r.setEmpresaId(empresaId);
        r.setProjetoId(projetoId);
        if (r.getProbabilidade() != null && (r.getProbabilidade() < 0 || r.getProbabilidade() > 100)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Probabilidade deve estar entre 0 e 100");
        if (r.getStatus() == null) r.setStatus("ABERTO");
        return riscos.save(r);
    }
    @Override public List<PrjMudanca> mudancas(Long empresaId, Long projetoId) {
        exigirProjeto(empresaId, projetoId);
        return mudancas.findByProjetoIdAndEmpresaIdAndDeletedAtIsNull(projetoId, empresaId);
    }
    @Override @Transactional public PrjMudanca solicitarMudanca(Long empresaId, Long projetoId, PrjMudanca m) {
        exigirProjeto(empresaId, projetoId);
        m.setId(null);
        m.setEmpresaId(empresaId);
        m.setProjetoId(projetoId);
        if (m.getDescricao() == null || m.getDescricao().isBlank()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Descrição da mudança é obrigatória");
        m.setStatus("SOLICITADA");
        return mudancas.save(m);
    }
    @Override @Transactional public PrjMudanca decidirMudanca(Long empresaId, Long userId, Long projetoId, Long mudancaId, boolean aprovar) {
        exigirProjeto(empresaId, projetoId);
        PrjMudanca m = exigir(mudancas.findByIdAndEmpresaIdAndDeletedAtIsNull(mudancaId, empresaId), "Mudanca inexistente");
        if (!m.getProjetoId().equals(projetoId)) throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Mudanca de outro projeto");
        if (!"SOLICITADA".equals(m.getStatus())) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Mudanca ja decidida");
        WkfDefinition d = workflow.definitions(empresaId).stream().filter(x -> "MUDANCA_PROJETO".equals(x.getEntidadeAlvo()) && Boolean.TRUE.equals(x.getAtivo())).findFirst().orElseGet(() -> {
            WkfDefinition n = new WkfDefinition();
            n.setCodigo("PRJ-MUDANCA");
            n.setNome("Aprovacao de mudanca de projeto");
            n.setEntidadeAlvo("MUDANCA_PROJETO");
            n.setAtivo(true);
            n = workflow.salvarDefinition(empresaId, userId, n);
            WkfStage st = new WkfStage();
            st.setNome("Aprovacao");
            st.setTipo("APROVACAO");
            st.setSlaHoras(72);
            workflow.addStage(empresaId, n.getId(), st);
            return n;
        });
        WkfInstance inst = workflow.instanciaPara(empresaId, "MUDANCA_PROJETO", mudancaId).filter(x -> "EM_ANDAMENTO".equals(x.getStatus())).orElseGet(() -> workflow.abrir(empresaId, userId, "MUDANCA_PROJETO", mudancaId, d.getId(), m.getDescricao()));
        java.util.List<WkfTask> pend = workflow.tasks(empresaId, inst.getId()).stream().filter(x -> "PENDENTE".equals(x.getStatus())).toList();
        if (pend.isEmpty()) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Sem tarefa pendente no workflow");
        workflow.decidir(empresaId, userId, pend.get(0).getId(), aprovar, null);
        WkfInstance atual = workflow.instanciaPara(empresaId, "MUDANCA_PROJETO", mudancaId).orElse(inst);
        if ("REJEITADA".equals(atual.getStatus())) { m.setStatus("REJEITADA"); }
        else if ("CONCLUIDA".equals(atual.getStatus())) { m.setStatus("APROVADA");
        }
        m.setDecididaPor(userId);
        m.setDecididaEm(LocalDateTime.now());
        return mudancas.save(m);
    }
    @Override public List<PrjFaturamento> faturamentos(Long empresaId, Long projetoId) {
        exigirProjeto(empresaId, projetoId);
        return faturamentos.findByProjetoIdAndEmpresaIdAndDeletedAtIsNull(projetoId, empresaId);
    }
    @Override @Transactional public PrjFaturamento salvarFaturamento(Long empresaId, Long projetoId, PrjFaturamento f) {
        exigirProjeto(empresaId, projetoId);
        f.setId(null);
        f.setEmpresaId(empresaId);
        f.setProjetoId(projetoId);
        if (f.getValor() == null || f.getValor().signum() < 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Valor do faturamento não pode ser negativo");
        if (f.getStatus() == null) f.setStatus("PREVISTO");
        return faturamentos.save(f);
    }
    @Override @Transactional public PrjFaturamento faturar(Long empresaId, Long projetoId, Long faturamentoId, Long servicoId, Long clienteId) {
        return faturarComNfse(empresaId, projetoId, faturamentoId, servicoId, clienteId);
    }
    @Override @Transactional public PrjFaturamento faturar(Long empresaId, Long projetoId, Long faturamentoId) {
        return faturarComNfse(empresaId, projetoId, faturamentoId, null, null);
    }
    private PrjFaturamento faturarComNfse(Long empresaId, Long projetoId, Long faturamentoId, Long servicoId, Long clienteId) {
        exigirProjeto(empresaId, projetoId);
        PrjFaturamento f = exigir(faturamentos.findByIdAndEmpresaIdAndDeletedAtIsNull(faturamentoId, empresaId), "Faturamento inexistente");
        if (!f.getProjetoId().equals(projetoId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Faturamento de outro projeto");
        if ("FATURADO".equalsIgnoreCase(f.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Faturamento já foi realizado");
        if (f.getValor() == null || f.getValor().signum() <= 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Faturamento deve possuir valor positivo");
        Titulo t = new Titulo();
        t.setEmpresaId(empresaId);
        t.setDescricao("Projeto #" + f.getProjetoId() + " - " + f.getDescricao());
        t.setTipo("R");
        t.setValorOriginal(f.getValor() == null ? java.math.BigDecimal.ZERO : f.getValor());
        t.setValorSaldo(t.getValorOriginal());
        t.setDataEmissao(java.time.LocalDate.now());
        t.setDataVencimento(java.time.LocalDate.now().plusDays(30));
        t.setStatus("ABERTO");
        t = titulos.save(t);
        f.setTituloId(t.getId());
        if (servicoId != null && clienteId != null) {
            f.setNfseId(emitirNfseMarco(empresaId, f, servicoId, clienteId));
        }
        f.setStatus("FATURADO");
        f.setDataFaturado(LocalDate.now());
        return faturamentos.save(f);
    }
    private Long emitirNfseMarco(Long empresaId, PrjFaturamento f, Long servicoId, Long clienteId) {
        Cliente cli = clientes.findByIdAndEmpresaIdAndDeletedAtIsNull(clienteId, empresaId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Cliente inexistente"));
        Pessoa pess = cli.getPessoa();
        if (pess == null || pess.getDocumento() == null || pess.getDocumento().isBlank()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Tomador sem documento");
        long proximoRps = 1L;
        java.util.Optional<br.com.brasil_saas.fiscal.model.Nfse> ult = nfseRepo.findTop1ByEmpresaIdOrderByIdDesc(empresaId);
        String numeroRps = ult.map(br.com.brasil_saas.fiscal.model.Nfse::getNumeroRps).orElse(null);
        if (numeroRps != null) {
            try { proximoRps = Long.parseLong(numeroRps) + 1; } catch (NumberFormatException ignored) { proximoRps = ult.map(br.com.brasil_saas.fiscal.model.Nfse::getId).orElse(0L) + 1; }
        }
        NfseEmissaoDtos.Emitir req = NfseEmissaoDtos.Emitir.builder()
            .empresaId(empresaId)
            .servicoId(servicoId)
            .clienteId(clienteId)
            .pessoaId(pess.getId())
            .cpfCnpjTomador(pess.getDocumento().replaceAll("[^0-9]", ""))
            .razaoSocialTomador(pess.getNome())
            .emailTomador(pess.getEmail())
            .valorServicos(f.getValor())
            .discriminacao("Projeto #" + f.getProjetoId() + " - " + f.getDescricao())
            .numeroRps(proximoRps)
            .build();
        return nfseEmissao.emitir(req).nfseId();
    }
    @Override public Map<String, Object> resumo(Long empresaId, Long projetoId) {
        PrjProjeto p = exigirProjeto(empresaId, projetoId);
        BigDecimal custo = BigDecimal.ZERO;
        BigDecimal receita = BigDecimal.ZERO;
        for (PrjMovimento m : movimentos.findByProjetoIdAndEmpresaIdAndDeletedAtIsNull(projetoId, empresaId)) {
            if ("CUSTO".equals(m.getTipo())) custo = custo.add(m.getValor() == null ? BigDecimal.ZERO : m.getValor());
            if ("RECEITA".equals(m.getTipo())) receita = receita.add(m.getValor() == null ? BigDecimal.ZERO : m.getValor());
        }
        BigDecimal faturado = BigDecimal.ZERO;
        for (PrjFaturamento f : faturamentos.findByProjetoIdAndEmpresaIdAndDeletedAtIsNull(projetoId, empresaId))
            if ("FATURADO".equals(f.getStatus())) faturado = faturado.add(f.getValor() == null ? BigDecimal.ZERO : f.getValor());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orcamento", p.getOrcamentoTotal() == null ? BigDecimal.ZERO : p.getOrcamentoTotal());
        m.put("custoRealizado", custo);
        m.put("receitaRealizada", receita);
        m.put("faturado", faturado);
        m.put("saldoOrcamento", (p.getOrcamentoTotal() == null ? BigDecimal.ZERO : p.getOrcamentoTotal()).subtract(custo));
        m.put("margem", receita.subtract(custo));
        java.util.List<java.util.Map<String, Object>> expo = new java.util.ArrayList<>();
        for (PrjRisco r : riscos.findByProjetoIdAndEmpresaIdAndDeletedAtIsNull(projetoId, empresaId)) {
            if (!"ABERTO".equals(r.getStatus())) continue;
            int prob = r.getProbabilidade() == null ? 0 : r.getProbabilidade();
            String imp = r.getImpacto() == null ? "" : r.getImpacto().trim().toUpperCase();
            int peso = "BAIXO".equals(imp) ? 1 : "ALTO".equals(imp) ? 3 : "CRITICO".equals(imp) ? 4 : 2;
            java.util.Map<String, Object> e = new java.util.LinkedHashMap<>();
            e.put("id", r.getId()); e.put("descricao", r.getDescricao()); e.put("probabilidade", prob); e.put("impacto", r.getImpacto()); e.put("exposicao", prob * peso);
            expo.add(e);
        }
        expo.sort((a, b) -> Integer.compare((Integer) b.get("exposicao"), (Integer) a.get("exposicao")));
        m.put("riscosAbertos", expo.size());
        m.put("exposicaoTop", expo.stream().limit(3).toList());
        return m;
    }
}
