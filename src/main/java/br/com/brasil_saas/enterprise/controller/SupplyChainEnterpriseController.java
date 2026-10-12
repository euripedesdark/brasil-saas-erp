package br.com.brasil_saas.enterprise.controller;

import br.com.brasil_saas.ehs.model.EhsAcao;
import br.com.brasil_saas.ehs.model.EhsInspecao;
import br.com.brasil_saas.ehs.model.EhsOcorrencia;
import br.com.brasil_saas.ehs.model.EhsPermissaoTrabalho;
import br.com.brasil_saas.ehs.model.EhsRisco;
import br.com.brasil_saas.ehs.repository.EhsAcaoRepository;
import br.com.brasil_saas.ehs.repository.EhsInspecaoRepository;
import br.com.brasil_saas.ehs.repository.EhsOcorrenciaRepository;
import br.com.brasil_saas.ehs.repository.EhsPermissaoTrabalhoRepository;
import br.com.brasil_saas.ehs.repository.EhsRiscoRepository;
import br.com.brasil_saas.plm.model.PlmAprovacao;
import br.com.brasil_saas.plm.model.PlmDocumento;
import br.com.brasil_saas.plm.model.PlmEfeito;
import br.com.brasil_saas.plm.model.PlmMudanca;
import br.com.brasil_saas.plm.model.PlmRevisao;
import br.com.brasil_saas.plm.repository.PlmAprovacaoRepository;
import br.com.brasil_saas.plm.repository.PlmDocumentoRepository;
import br.com.brasil_saas.plm.repository.PlmEfeitoRepository;
import br.com.brasil_saas.plm.repository.PlmMudancaRepository;
import br.com.brasil_saas.plm.repository.PlmRevisaoRepository;
import br.com.brasil_saas.producao.model.EstruturaProduto;
import br.com.brasil_saas.producao.model.RoteiroProducao;
import br.com.brasil_saas.producao.repository.EstruturaProdutoRepository;
import br.com.brasil_saas.producao.repository.RoteiroProducaoRepository;
import br.com.brasil_saas.servicos.model.SrvContrato;
import br.com.brasil_saas.servicos.repository.SrvContratoRepository;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import br.com.brasil_saas.shared.web.MapValues;
import br.com.brasil_saas.supplychain.model.ScmOrdemTransporte;
import br.com.brasil_saas.supplychain.model.ScmPoliticaReposicao;
import br.com.brasil_saas.supplychain.repository.ScmOrdemTransporteRepository;
import br.com.brasil_saas.supplychain.repository.ScmPoliticaReposicaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class SupplyChainEnterpriseController {
 private final ScmPoliticaReposicaoRepository reposicaoRepository;
 private final ScmOrdemTransporteRepository transporteRepository;
 private final EhsOcorrenciaRepository ocorrenciaRepository;
 private final SrvContratoRepository srvContratoRepository;
 private final PlmRevisaoRepository revisaoRepository;
 private final PlmMudancaRepository mudancaRepository;
 private final PlmDocumentoRepository documentoRepository;
 private final PlmEfeitoRepository efeitoRepository;
 private final PlmAprovacaoRepository aprovacaoRepository;
 private final EhsRiscoRepository ehsRiscoRepository;
 private final EhsInspecaoRepository inspecaoRepository;
 private final EhsAcaoRepository ehsAcaoRepository;
 private final EhsPermissaoTrabalhoRepository permissaoRepository;
 private final EstruturaProdutoRepository estruturaRepository;
 private final RoteiroProducaoRepository roteiroRepository;
 private static final Set<String> RECURSOS = Set.of(
  "reposicao","transportes","revisoes-produto","mudancas-engenharia","ehs","contratos-servico",
  "plm-documentos","plm-efeitos","plm-aprovacoes","ehs-riscos","ehs-inspecoes","ehs-acoes","ehs-permissoes");
 private static final Set<String> BLOQUEADOS = Set.of(
  "id","uuid","empresa_id","created_by","updated_by","deleted_at");

 @GetMapping("/{resource:reposicao|transportes|revisoes-produto|mudancas-engenharia|ehs|contratos-servico|plm-documentos|plm-efeitos|plm-aprovacoes|ehs-riscos|ehs-inspecoes|ehs-acoes|ehs-permissoes}")
 @PreAuthorize("isAuthenticated()")
 public List<Map<String,Object>> listar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource){
  validar(resource);
  Long empresa = u.getEmpresaId();
  return switch (resource) {
    case "reposicao" -> reposicaoRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
      .stream().map(ScmPoliticaReposicao::toMap).toList();
    case "transportes" -> transporteRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
      .stream().map(ScmOrdemTransporte::toMap).toList();
    case "revisoes-produto" -> revisaoRepository.findByEmpresaIdOrderByIdDesc(empresa)
      .stream().map(PlmRevisao::toMap).toList();
    case "mudancas-engenharia" -> mudancaRepository.findByEmpresaIdOrderByIdDesc(empresa)
      .stream().map(PlmMudanca::toMap).toList();
    case "ehs" -> ocorrenciaRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
      .stream().map(EhsOcorrencia::toMap).toList();
    case "contratos-servico" -> srvContratoRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
      .stream().map(SrvContrato::toMap).toList();
    case "plm-documentos" -> documentoRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
      .stream().map(PlmDocumento::toMap).toList();
    case "plm-efeitos" -> efeitoRepository.findByEmpresaIdOrderByIdDesc(empresa)
      .stream().map(PlmEfeito::toMap).toList();
    case "plm-aprovacoes" -> aprovacaoRepository.findByEmpresaIdOrderByIdDesc(empresa)
      .stream().map(PlmAprovacao::toMap).toList();
    case "ehs-riscos" -> ehsRiscoRepository.findByEmpresaIdOrderByIdDesc(empresa)
      .stream().map(EhsRisco::toMap).toList();
    case "ehs-inspecoes" -> inspecaoRepository.findByEmpresaIdOrderByIdDesc(empresa)
      .stream().map(EhsInspecao::toMap).toList();
    case "ehs-acoes" -> ehsAcaoRepository.findByEmpresaIdOrderByIdDesc(empresa)
      .stream().map(EhsAcao::toMap).toList();
    case "ehs-permissoes" -> permissaoRepository.findByEmpresaIdOrderByIdDesc(empresa)
      .stream().map(EhsPermissaoTrabalho::toMap).toList();
    default -> throw new IllegalArgumentException("Recurso inválido");
  };
 }
 @PostMapping("/{resource:reposicao|transportes|revisoes-produto|mudancas-engenharia|ehs|contratos-servico|plm-documentos|plm-efeitos|plm-aprovacoes|ehs-riscos|ehs-inspecoes|ehs-acoes|ehs-permissoes}")
 @PreAuthorize("isAuthenticated()")
 @Transactional
 public Map<String,Object> criar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource,@RequestBody Map<String,Object> body){
  validar(resource);
  Long empresa = u.getEmpresaId();
  if("revisoes-produto".equals(resource)){
   PlmRevisao r = new PlmRevisao();
   r.setEmpresaId(empresa);
   r.setProdutoId(MapValues.lng(body.get("produto_id")));
   r.setRevisao(MapValues.str(body.get("revisao")));
   r.setDescricao(MapValues.str(body.get("descricao")));
   r.setStatus(body.get("status") == null ? "EM_DESENVOLVIMENTO" : String.valueOf(body.getOrDefault("status","EM_DESENVOLVIMENTO")));
   r.setVigenteDesde(MapValues.dt(body.get("vigente_desde")));
   r.setVigenteAte(MapValues.dt(body.get("vigente_ate")));
   r.setMotivo(MapValues.str(body.get("motivo")));
   r.setDocumentoId(MapValues.str(body.get("documento_id")));
   r.setCriadoPor(u.getId());
   revisaoRepository.save(r);
   return revisaoRepository.findTopByEmpresaIdOrderByIdDesc(empresa)
     .orElseThrow(() -> new NoSuchElementException("Revisão não encontrada")).toMap();
  }
  if("plm-documentos".equals(resource)){
   PlmDocumento d = new PlmDocumento();
   d.setEmpresaId(empresa);
   d.setRevisaoId(MapValues.lng(body.get("revisao_id")));
   d.setMudancaId(MapValues.lng(body.get("mudanca_id")));
   d.setTipo(MapValues.str(body.get("tipo")));
   d.setCodigo(MapValues.str(body.get("codigo")));
   d.setVersao(body.get("versao") == null ? "1" : String.valueOf(body.getOrDefault("versao","1")));
   d.setNome(MapValues.str(body.get("nome")));
   d.setLocalizacao(MapValues.str(body.get("localizacao")));
   d.setHashDocumento(MapValues.str(body.get("hash_documento")));
   d.setStatus(body.get("status") == null ? "RASCUNHO" : String.valueOf(body.getOrDefault("status","RASCUNHO")));
   Object obrig = body.getOrDefault("obrigatorio",false);
   d.setObrigatorio(obrig instanceof Boolean b2 ? b2 : Boolean.parseBoolean(String.valueOf(obrig)));
   d.setVigenciaInicio(MapValues.dt(body.get("vigencia_inicio")));
   d.setVigenciaFim(MapValues.dt(body.get("vigencia_fim")));
   documentoRepository.save(d);
   return documentoRepository.findTopByEmpresaIdOrderByIdDesc(empresa)
     .orElseThrow(() -> new NoSuchElementException("Documento não encontrado")).toMap();
  }
  Map<String,Object> d=sanitize(body); d.put("empresa_id",u.getEmpresaId()); d.put("created_by",u.getId());
  List<String> c=new ArrayList<>(d.keySet()); if(c.isEmpty()) throw new IllegalArgumentException("Nenhum campo informado");
  return switch (resource) {
    case "reposicao" -> {
      ScmPoliticaReposicao e = new ScmPoliticaReposicao();
      e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
      yield reposicaoRepository.save(e).toMap();
    }
    case "transportes" -> {
      ScmOrdemTransporte e = new ScmOrdemTransporte();
      e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
      yield transporteRepository.save(e).toMap();
    }
    case "mudancas-engenharia" -> {
      PlmMudanca e = new PlmMudanca();
      e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
      yield mudancaRepository.save(e).toMap();
    }
    case "ehs" -> {
      EhsOcorrencia e = new EhsOcorrencia();
      e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
      yield ocorrenciaRepository.save(e).toMap();
    }
    case "contratos-servico" -> {
      SrvContrato e = new SrvContrato();
      e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
      yield srvContratoRepository.save(e).toMap();
    }
    case "plm-efeitos" -> {
      PlmEfeito e = new PlmEfeito();
      e.setEmpresaId(empresa); e.aplicar(body);
      yield efeitoRepository.save(e).toMap();
    }
    case "plm-aprovacoes" -> {
      PlmAprovacao e = new PlmAprovacao();
      e.setEmpresaId(empresa); e.aplicar(body);
      yield aprovacaoRepository.save(e).toMap();
    }
    case "ehs-riscos" -> {
      EhsRisco e = new EhsRisco();
      e.setEmpresaId(empresa); e.aplicar(body);
      yield ehsRiscoRepository.save(e).toMap();
    }
    case "ehs-inspecoes" -> {
      EhsInspecao e = new EhsInspecao();
      e.setEmpresaId(empresa); e.aplicar(body);
      yield inspecaoRepository.save(e).toMap();
    }
    case "ehs-acoes" -> {
      EhsAcao e = new EhsAcao();
      e.setEmpresaId(empresa); e.aplicar(body);
      yield ehsAcaoRepository.save(e).toMap();
    }
    case "ehs-permissoes" -> {
      EhsPermissaoTrabalho e = new EhsPermissaoTrabalho();
      e.setEmpresaId(empresa); e.aplicar(body);
      yield permissaoRepository.save(e).toMap();
    }
    default -> throw new IllegalArgumentException("Recurso inválido");
  };
 }
 @PutMapping("/{resource:reposicao|transportes|revisoes-produto|mudancas-engenharia|ehs|contratos-servico|plm-documentos|plm-efeitos|plm-aprovacoes|ehs-riscos|ehs-inspecoes|ehs-acoes|ehs-permissoes}/{id}")
 @PreAuthorize("isAuthenticated()")
 @Transactional
 public Map<String,Object> atualizar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource,@PathVariable Long id,@RequestBody Map<String,Object> body){
  validar(resource);
  Long empresa = u.getEmpresaId();
  Map<String,Object> d=sanitize(body);
  d.remove("empresa_id"); d.remove("created_by"); d.remove("created_at"); d.remove("id"); d.remove("uuid");
  List<String> c=new ArrayList<>(d.keySet()); if(c.isEmpty()) throw new IllegalArgumentException("Nenhum campo informado");
  return switch (resource) {
    case "reposicao" -> {
      ScmPoliticaReposicao e = reposicaoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body); e.setUpdatedBy(u.getId());
      yield reposicaoRepository.save(e).toMap();
    }
    case "transportes" -> {
      ScmOrdemTransporte e = transporteRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body); e.setUpdatedBy(u.getId());
      yield transporteRepository.save(e).toMap();
    }
    case "revisoes-produto" -> {
      PlmRevisao e = revisaoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body);
      yield revisaoRepository.save(e).toMap();
    }
    case "mudancas-engenharia" -> {
      PlmMudanca e = mudancaRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body); e.setUpdatedBy(u.getId());
      yield mudancaRepository.save(e).toMap();
    }
    case "ehs" -> {
      EhsOcorrencia e = ocorrenciaRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body); e.setUpdatedBy(u.getId());
      yield ocorrenciaRepository.save(e).toMap();
    }
    case "contratos-servico" -> {
      SrvContrato e = srvContratoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body); e.setUpdatedBy(u.getId());
      yield srvContratoRepository.save(e).toMap();
    }
    case "plm-documentos" -> {
      PlmDocumento e = documentoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body);
      yield documentoRepository.save(e).toMap();
    }
    case "plm-efeitos" -> {
      PlmEfeito e = efeitoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body);
      yield efeitoRepository.save(e).toMap();
    }
    case "plm-aprovacoes" -> {
      PlmAprovacao e = aprovacaoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body);
      yield aprovacaoRepository.save(e).toMap();
    }
    case "ehs-riscos" -> {
      EhsRisco e = ehsRiscoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body);
      yield ehsRiscoRepository.save(e).toMap();
    }
    case "ehs-inspecoes" -> {
      EhsInspecao e = inspecaoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body);
      yield inspecaoRepository.save(e).toMap();
    }
    case "ehs-acoes" -> {
      EhsAcao e = ehsAcaoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body);
      yield ehsAcaoRepository.save(e).toMap();
    }
    case "ehs-permissoes" -> {
      EhsPermissaoTrabalho e = permissaoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.aplicar(body);
      yield permissaoRepository.save(e).toMap();
    }
    default -> throw new IllegalArgumentException("Recurso inválido");
  };
 }
 @DeleteMapping("/{resource:reposicao|transportes|revisoes-produto|mudancas-engenharia|ehs|contratos-servico|plm-documentos|plm-efeitos|plm-aprovacoes|ehs-riscos|ehs-inspecoes|ehs-acoes|ehs-permissoes}/{id}")
 @PreAuthorize("isAuthenticated()")
 @Transactional
 public void excluir(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource,@PathVariable Long id){
  validar(resource);
  Long empresa = u.getEmpresaId();
  switch (resource) {
    case "reposicao" -> {
      ScmPoliticaReposicao e = reposicaoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.setDeletedAt(LocalDateTime.now()); e.setUpdatedBy(u.getId());
      reposicaoRepository.save(e);
    }
    case "transportes" -> {
      ScmOrdemTransporte e = transporteRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.setDeletedAt(LocalDateTime.now()); e.setUpdatedBy(u.getId());
      transporteRepository.save(e);
    }
    case "ehs" -> {
      EhsOcorrencia e = ocorrenciaRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.setDeletedAt(LocalDateTime.now()); e.setUpdatedBy(u.getId());
      ocorrenciaRepository.save(e);
    }
    case "contratos-servico" -> {
      SrvContrato e = srvContratoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.setDeletedAt(LocalDateTime.now()); e.setUpdatedBy(u.getId());
      srvContratoRepository.save(e);
    }
    case "plm-documentos" -> {
      PlmDocumento e = documentoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado"));
      e.setDeletedAt(LocalDateTime.now());
      documentoRepository.save(e);
    }
    // Tabelas sem deleted_at: exclusão física (o SQL original falhava com 500 aqui).
    case "revisoes-produto" -> revisaoRepository.delete(
      revisaoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado")));
    case "mudancas-engenharia" -> mudancaRepository.delete(
      mudancaRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado")));
    case "plm-efeitos" -> efeitoRepository.delete(
      efeitoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado")));
    case "plm-aprovacoes" -> aprovacaoRepository.delete(
      aprovacaoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado")));
    case "ehs-riscos" -> ehsRiscoRepository.delete(
      ehsRiscoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado")));
    case "ehs-inspecoes" -> inspecaoRepository.delete(
      inspecaoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado")));
    case "ehs-acoes" -> ehsAcaoRepository.delete(
      ehsAcaoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado")));
    case "ehs-permissoes" -> permissaoRepository.delete(
      permissaoRepository.findByIdAndEmpresaId(id, empresa)
        .orElseThrow(() -> new NoSuchElementException("Registro não encontrado")));
    default -> throw new IllegalArgumentException("Recurso inválido");
  }
 }
 @PostMapping("/plm/mudancas/{id}/enviar-aprovacao")
 @PreAuthorize("hasAuthority('plm:escrita')")
 @Transactional
 public Map<String,Object> enviarParaAprovacao(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  Long empresa = u.getEmpresaId();
  PlmMudanca m = mudancaRepository.findByIdAndEmpresaId(id, empresa)
    .filter(x -> Set.of("ABERTA","EM_DESENVOLVIMENTO").contains(x.getStatus()))
    .orElseThrow(() -> new NoSuchElementException("Mudança não está disponível para aprovação"));
  long efeitos = efeitoRepository.countByEmpresaIdAndMudancaId(empresa, id);
  if(efeitos==0) throw new IllegalStateException("A mudança precisa ter pelo menos um efeito de engenharia antes da aprovação");
  m.setStatus("EM_APROVACAO");
  mudancaRepository.save(m);
  if (aprovacaoRepository.findByEmpresaIdAndMudancaIdAndEtapa(empresa, id, 1).isEmpty()) {
    PlmAprovacao a = new PlmAprovacao();
    a.setEmpresaId(empresa); a.setMudancaId(id); a.setEtapa(1);
    a.setDecisao("PENDENTE"); a.setObrigatoria(true);
    aprovacaoRepository.save(a);
  }
  return workflow(u,id);
 }

 @GetMapping("/plm/mudancas/{id}/workflow")
 @PreAuthorize("hasAuthority('plm:leitura')")
 public Map<String,Object> workflow(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  Long empresa = u.getEmpresaId();
  PlmMudanca m = mudancaRepository.findByIdAndEmpresaId(id, empresa)
    .orElseThrow(() -> new NoSuchElementException("Mudança não encontrada"));
  Map<String,Object> r=new LinkedHashMap<>();
  r.put("mudanca", m.toMap());
  r.put("aprovacoes", aprovacaoRepository.findByEmpresaIdAndMudancaIdOrderByEtapaAsc(empresa, id)
    .stream().map(PlmAprovacao::toMap).toList());
  r.put("efeitos", efeitoRepository.findByEmpresaIdAndMudancaIdOrderByOrdemExecucaoAscIdAsc(empresa, id)
    .stream().map(PlmEfeito::toMap).toList());
  r.put("documentos", documentoRepository.findByEmpresaIdAndMudancaIdAndDeletedAtIsNullOrderByIdDesc(empresa, id)
    .stream().map(PlmDocumento::toMap).toList());
  return r;
 }

 @PostMapping("/plm/mudancas/{id}/efeitos")
 @PreAuthorize("hasAuthority('plm:escrita')")
 @Transactional
 public Map<String,Object> adicionarEfeito(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
  Long empresa = u.getEmpresaId();
  PlmMudanca m = mudancaRepository.findByIdAndEmpresaId(id, empresa)
    .orElseThrow(() -> new NoSuchElementException("Mudança não encontrada ou já encerrada"));
  if (Set.of("IMPLEMENTADA","CANCELADA").contains(m.getStatus())) throw new NoSuchElementException("Mudança não encontrada ou já encerrada");
  String tipo=String.valueOf(b.getOrDefault("entidadeTipo",""));
  String acao=String.valueOf(b.getOrDefault("acao",""));
  Long entidade=longValue(b.get("entidadeId"));
  validarEfeito(u,tipo,acao,entidade);
  Integer ordem=integerValue(b.get("ordemExecucao"),
    efeitoRepository.findTopByEmpresaIdAndMudancaIdOrderByOrdemExecucaoDesc(empresa, id)
      .map(e -> e.getOrdemExecucao() + 1).orElse(1));
  PlmEfeito e = new PlmEfeito();
  e.setEmpresaId(empresa); e.setMudancaId(id);
  e.setEntidadeTipo(tipo); e.setEntidadeId(entidade); e.setAcao(acao);
  e.setRevisaoAnterior(MapValues.str(b.get("revisaoAnterior")));
  e.setRevisaoNova(MapValues.str(b.get("revisaoNova")));
  e.setEfetivaEm(MapValues.dt(b.get("efetivaEm")));
  e.setStatus("PENDENTE");
  e.setObservacao(MapValues.str(b.get("observacao")));
  e.setOrdemExecucao(ordem);
  Object obrig = b.getOrDefault("obrigatorio",true);
  e.setObrigatorio(obrig instanceof Boolean bl ? bl : Boolean.parseBoolean(String.valueOf(obrig)));
  efeitoRepository.save(e);
  return Map.of("ok",true,"ordemExecucao",ordem);
 }

 @PostMapping("/plm/mudancas/{id}/aprovar")
 @PreAuthorize("hasAuthority('plm:escrita')")
 @Transactional
 public Map<String,Object> aprovarMudanca(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
  Long empresa = u.getEmpresaId();
  PlmMudanca m = mudancaRepository.findByIdAndEmpresaId(id, empresa)
    .filter(x -> "EM_APROVACAO".equals(x.getStatus()))
    .orElseThrow(() -> new NoSuchElementException("Mudança não está aguardando aprovação"));
  int etapa=integerValue(b.get("etapa"),1);
  String decisao=String.valueOf(b.getOrDefault("decisao","APROVADO")).toUpperCase(Locale.ROOT);
  if(!Set.of("APROVADO","REJEITADO").contains(decisao)) throw new IllegalArgumentException("Decisão inválida");
  PlmAprovacao a = aprovacaoRepository.findByEmpresaIdAndMudancaIdAndEtapa(empresa, id, etapa)
    .orElseGet(PlmAprovacao::new);
  a.setEmpresaId(empresa); a.setMudancaId(id); a.setEtapa(etapa);
  a.setDecisao(decisao); a.setAprovadorId(u.getId());
  a.setObservacao(MapValues.str(b.get("observacao")));
  a.setDecididoEm(LocalDateTime.now());
  aprovacaoRepository.save(a);
  if("REJEITADO".equals(decisao)) {
    m.setStatus("REJEITADA");
    mudancaRepository.save(m);
  } else {
   long pendentes=aprovacaoRepository.countByEmpresaIdAndMudancaIdAndObrigatoriaTrueAndDecisao(empresa,id,"PENDENTE");
   if(pendentes==0) {
     m.setStatus("APROVADA");
     m.setAprovadorId(u.getId());
     m.setAprovadoEm(LocalDateTime.now());
     mudancaRepository.save(m);
   }
  }
  return workflow(u,id);
 }
 @PostMapping("/plm/mudancas/{id}/implementar")
 @PreAuthorize("hasAuthority('plm:escrita')")
 @Transactional
 public Map<String,Object> implementarMudanca(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  Long empresa = u.getEmpresaId();
  PlmMudanca m = mudancaRepository.findByIdAndEmpresaId(id, empresa)
    .filter(x -> "APROVADA".equals(x.getStatus()))
    .orElseThrow(() -> new NoSuchElementException("Mudança não aprovada"));
  List<PlmEfeito> efeitos=efeitoRepository.findByEmpresaIdAndMudancaIdOrderByOrdemExecucaoAscIdAsc(empresa,id);
  if(efeitos.isEmpty()) throw new IllegalStateException("Mudança sem efeitos de engenharia");
  for(PlmEfeito e:efeitos){
   try {
    aplicarEfeito(u,e);
    e.setStatus("APLICADA");
    if (e.getEfetivaEm() == null) e.setEfetivaEm(LocalDate.now());
    e.setAplicadoEm(LocalDateTime.now());
    e.setAplicadoPor(u.getId());
    e.setErroImplementacao(null);
    efeitoRepository.save(e);
   } catch(RuntimeException ex) {
    e.setStatus("ERRO");
    e.setErroImplementacao(ex.getMessage());
    efeitoRepository.save(e);
    throw ex;
   }
  }
  m.setStatus("IMPLEMENTADA");
  m.setImplementadoEm(LocalDateTime.now());
  mudancaRepository.save(m);
  return workflow(u,id);
 }
 @GetMapping("/ehs/riscos/{id}/workflow")
 @PreAuthorize("hasAuthority('ehs:leitura')")
 public Map<String,Object> riscoWorkflow(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  Long empresa = u.getEmpresaId();
  EhsRisco r = ehsRiscoRepository.findByIdAndEmpresaId(id, empresa)
    .orElseThrow(() -> new NoSuchElementException("Risco EHS não encontrado"));
  Map<String,Object> m=new LinkedHashMap<>();
  m.put("risco", r.toMap());
  m.put("acoes", ehsAcaoRepository.findByEmpresaIdOrderByPrazoAscIdDesc(empresa)
    .stream().map(EhsAcao::toMap).toList());
  return m;
 }

 @PostMapping("/ehs/acoes")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 @Transactional
 public Map<String,Object> criarAcaoEhs(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
  Object descricao=b.get("descricao");
  if(descricao==null||descricao.toString().isBlank()) throw new IllegalArgumentException("descricao é obrigatória");
  EhsAcao a = new EhsAcao();
  a.setEmpresaId(u.getEmpresaId());
  a.setOcorrenciaId(longValue(b.get("ocorrenciaId")));
  a.setInspecaoId(longValue(b.get("inspecaoId")));
  a.setDescricao(descricao.toString());
  a.setResponsavel(MapValues.str(b.get("responsavel")));
  a.setPrazo(MapValues.dt(b.get("prazo")));
  a.setStatus(String.valueOf(b.getOrDefault("status","ABERTA")));
  a.setEvidencia(MapValues.str(b.get("evidencia")));
  ehsAcaoRepository.save(a);
  return ehsAcaoRepository.findTopByEmpresaIdOrderByIdDesc(u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Ação EHS não encontrada")).toMap();
 }

 @PostMapping("/ehs/acoes/{id}/concluir")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 @Transactional
 public Map<String,Object> concluirAcaoEhs(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
  EhsAcao a = ehsAcaoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Ação EHS não encontrada"));
  a.setStatus("CONCLUIDA");
  a.setConcluidaEm(LocalDateTime.now());
  if (b.get("evidencia") != null) a.setEvidencia(String.valueOf(b.get("evidencia")));
  ehsAcaoRepository.save(a);
  return ehsAcaoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Ação EHS não encontrada")).toMap();
 }

 @PostMapping("/ehs/inspecoes/{id}/reabrir")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 @Transactional
 public Map<String,Object> reabrirInspecao(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  EhsInspecao i = inspecaoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Inspeção EHS não encontrada"));
  i.setStatus("ABERTA");
  inspecaoRepository.save(i);
  return inspecaoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Inspeção EHS não encontrada")).toMap();
 }

 @PostMapping("/ehs/permissoes/{id}/cancelar")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 @Transactional
 public Map<String,Object> cancelarPermissao(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  EhsPermissaoTrabalho p = permissaoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Permissão não encontrada ou já encerrada"));
  if (Set.of("CONCLUIDA","CANCELADA").contains(p.getStatus())) throw new NoSuchElementException("Permissão não encontrada ou já encerrada");
  p.setStatus("CANCELADA");
  permissaoRepository.save(p);
  return permissaoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Permissão não encontrada ou já encerrada")).toMap();
 }

 @PostMapping("/ehs/riscos/{id}/reavaliar")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 @Transactional
 public Map<String,Object> reavaliarRisco(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
  double p=number(b.get("probabilidade")),i=number(b.get("impacto"));
  EhsRisco r = ehsRiscoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Risco EHS não encontrado"));
  r.setProbabilidade(BigDecimal.valueOf(p));
  r.setImpacto(BigDecimal.valueOf(i));
  r.setNivel(BigDecimal.valueOf(p*i));
  r.setRevisadoEm(LocalDateTime.now());
  if (b.get("responsavel") != null) r.setResponsavel(String.valueOf(b.get("responsavel")));
  ehsRiscoRepository.save(r);
  return Map.of("ok",true,"nivel",p*i);
 }
 @PostMapping("/ehs/inspecoes/{id}/encerrar")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 @Transactional
 public Map<String,Object> encerrarInspecao(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
  EhsInspecao i = inspecaoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Inspeção não encontrada"));
  i.setStatus("ENCERRADA");
  i.setResultado(String.valueOf(b.getOrDefault("resultado","CONFORME")));
  if (b.get("observacao") != null) i.setObservacao(String.valueOf(b.get("observacao")));
  inspecaoRepository.save(i);
  return Map.of("ok",true);
 }
 @PostMapping("/ehs/permissoes/{id}/aprovar")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 @Transactional
 public Map<String,Object> aprovarPermissao(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  EhsPermissaoTrabalho p = permissaoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Permissão não encontrada"));
  p.setStatus("APROVADA");
  p.setAprovadaPor(String.valueOf(u.getId()));
  p.setAprovadaEm(LocalDateTime.now());
  permissaoRepository.save(p);
  return Map.of("ok",true);
 }

 private void validarEfeito(AuthenticatedUser u,String tipo,String acao,Long id){
  if(id==null) throw new IllegalArgumentException("entidadeId é obrigatório");
  boolean existe = switch(tipo){
   case "REVISAO_PRODUTO" -> revisaoRepository.existsByIdAndEmpresaId(id,u.getEmpresaId());
   case "ESTRUTURA" -> estruturaRepository.existsByIdAndEmpresaIdAndDeletedAtIsNull(id,u.getEmpresaId());
   case "ROTEIRO" -> roteiroRepository.existsByIdAndEmpresaIdAndDeletedAtIsNull(id,u.getEmpresaId());
   case "DOCUMENTO" -> documentoRepository.existsByIdAndEmpresaIdAndDeletedAtIsNull(id,u.getEmpresaId());
   default -> throw new IllegalArgumentException("Tipo de entidade PLM inválido");
  };
  if (!existe) throw new NoSuchElementException("Entidade do efeito não encontrada");
  Set<String> permitidas=switch(tipo){
   case "REVISAO_PRODUTO" -> Set.of("VIGENCIAR");
   case "ESTRUTURA" -> Set.of("ATIVAR","INATIVAR");
   case "ROTEIRO" -> Set.of("VIGENCIAR","INATIVAR");
   case "DOCUMENTO" -> Set.of("APROVAR","INATIVAR");
   default -> Set.of();
  };
  if(!permitidas.contains(acao)) throw new IllegalArgumentException("Ação incompatível com o tipo de efeito");
 }

 private void aplicarEfeito(AuthenticatedUser u,PlmEfeito e){
  String tipo=String.valueOf(e.getEntidadeTipo()), acao=String.valueOf(e.getAcao());
  Long id=e.getEntidadeId();
  Long empresa = u.getEmpresaId();
  if("REVISAO_PRODUTO".equals(tipo)&&"VIGENCIAR".equals(acao)){
   PlmRevisao alvo = revisaoRepository.findByIdAndEmpresaId(id, empresa)
     .orElseThrow(() -> new NoSuchElementException("Revisão não encontrada"));
   Long produto=alvo.getProdutoId();
   for (PlmRevisao outra : revisaoRepository.findByEmpresaIdAndProdutoIdAndIdNotAndStatus(empresa, produto, id, "VIGENTE")) {
     outra.setStatus("SUPERADA");
     outra.setVigenteAte(LocalDate.now());
     revisaoRepository.save(outra);
   }
   alvo.setStatus("VIGENTE");
   if (alvo.getVigenteDesde() == null) alvo.setVigenteDesde(LocalDate.now());
   alvo.setVigenteAte(null);
   revisaoRepository.save(alvo);
  } else if("ESTRUTURA".equals(tipo)){
   EstruturaProduto s = estruturaRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
     .orElseThrow(() -> new NoSuchElementException("Estrutura não encontrada"));
   s.setAtivo("ATIVAR".equals(acao));
   estruturaRepository.save(s);
  } else if("ROTEIRO".equals(tipo)){
   RoteiroProducao alvo = roteiroRepository.findByIdAndEmpresaId(id, empresa)
     .orElseThrow(() -> new NoSuchElementException("Roteiro não encontrado"));
   if("VIGENCIAR".equals(acao)){
    Long produto=alvo.getProdutoId();
    for (RoteiroProducao outro : roteiroRepository.findByEmpresaIdAndProdutoIdAndIdNot(empresa, produto, id)) {
      outro.setAtivo(false);
      roteiroRepository.save(outro);
    }
    alvo.setAtivo(true);
    roteiroRepository.save(alvo);
   } else {
     alvo.setAtivo(false);
     roteiroRepository.save(alvo);
   }
  } else if("DOCUMENTO".equals(tipo)){
   PlmDocumento d = documentoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresa)
     .orElseThrow(() -> new NoSuchElementException("Documento não encontrado"));
   d.setStatus("APROVAR".equals(acao)?"APROVADO":"OBSOLETO");
   d.setAprovadoPor(u.getId());
   if("APROVAR".equals(acao)) d.setAprovadoEm(LocalDateTime.now());
   documentoRepository.save(d);
  }
 }
 private void validar(String resource){
  if(!RECURSOS.contains(resource)) throw new IllegalArgumentException("Recurso inválido");
 }
 private Map<String,Object> sanitize(Map<String,Object> s){
  Map<String,Object> d=new LinkedHashMap<>(); s.forEach((k,v)->{if(k!=null&&k.matches("[a-z][a-z0-9_]*")&&!Set.of("id","uuid","empresa_id","created_by","updated_by","deleted_at").contains(k))d.put(k,v);});return d;
 }

 private static double number(Object v){return v==null?0d:Double.parseDouble(v.toString());}
 private static Long longValue(Object v){
  if(v==null||v.toString().isBlank()) return null;
  return v instanceof Number n?n.longValue():Long.valueOf(v.toString().trim());
 }
 private static Integer integerValue(Object v,Integer padrao){
  if(v==null||v.toString().isBlank()) return padrao;
  return v instanceof Number n?n.intValue():Integer.valueOf(v.toString().trim());
 }
}
