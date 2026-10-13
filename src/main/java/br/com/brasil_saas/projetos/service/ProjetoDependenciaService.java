package br.com.brasil_saas.projetos.service;
import br.com.brasil_saas.projetos.model.*; import br.com.brasil_saas.projetos.repository.*;
import lombok.RequiredArgsConstructor; import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*; import java.util.*;
@Service @RequiredArgsConstructor
public class ProjetoDependenciaService {
 private final PrjProjetoRepository projetos; private final PrjEtapaRepository etapas;
 private final PrjDependenciaRepository deps;
 private ResponseStatusException erro(HttpStatus status,String msg){return new ResponseStatusException(status,msg);}
 private void projeto(Long emp,Long pid){if(projetos.findByIdAndEmpresaIdAndDeletedAtIsNull(pid,emp).isEmpty())throw erro(HttpStatus.NOT_FOUND,"Projeto inexistente");}
 private PrjEtapa etapa(Long emp,Long pid,Long id){return etapas.findByIdAndEmpresaIdAndDeletedAtIsNull(id,emp).filter(e->pid.equals(e.getProjetoId())).orElseThrow(()->erro(HttpStatus.NOT_FOUND,"Etapa nao pertence ao projeto"));}
 @Transactional(readOnly=true) public List<PrjDependencia> listar(Long emp,Long pid){projeto(emp,pid);return deps.findByProjetoIdAndEmpresaIdAndDeletedAtIsNull(pid,emp);}
 @Transactional public PrjDependencia adicionar(Long emp,Long pid,Long anterior,Long posterior,Integer espera){
  projeto(emp,pid);
  if(anterior==null||posterior==null||anterior.equals(posterior))throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Etapas invalidas");
  PrjEtapa a=etapa(emp,pid,anterior),b=etapa(emp,pid,posterior);
  int lag=espera==null?0:espera;
  if(lag<0||lag>3650)throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Defasagem invalida");
  List<PrjDependencia> all=deps.findByProjetoIdAndEmpresaIdAndDeletedAtIsNull(pid,emp);
  for(PrjDependencia d:all)if(anterior.equals(d.getAntecessoraId())&&posterior.equals(d.getSucessoraId()))throw erro(HttpStatus.CONFLICT,"Dependencia duplicada");
  Deque<Long> q=new ArrayDeque<>();Set<Long> visitados=new HashSet<>();q.add(posterior);
  while(!q.isEmpty()){
   Long cur=q.removeFirst();if(!visitados.add(cur))continue;
   if(cur.equals(anterior))throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Ciclo de dependencias");
   for(PrjDependencia d:all)if(cur.equals(d.getAntecessoraId()))q.addLast(d.getSucessoraId());
  }
  if(a.getDataFim()!=null&&b.getDataInicio()!=null&&b.getDataInicio().isBefore(a.getDataFim().plusDays(lag)))throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Cronograma viola dependencia");
  PrjDependencia d=new PrjDependencia();d.setEmpresaId(emp);d.setProjetoId(pid);
  d.setAntecessoraId(anterior);d.setSucessoraId(posterior);d.setDefasagemDias(lag);return deps.save(d);
 }
 @Transactional public void excluir(Long emp,Long pid,Long id){
  projeto(emp,pid);PrjDependencia d=deps.findByIdAndEmpresaIdAndDeletedAtIsNull(id,emp).filter(x->pid.equals(x.getProjetoId())).orElseThrow(()->erro(HttpStatus.NOT_FOUND,"Dependencia inexistente"));
  d.setDeletedAt(LocalDateTime.now());deps.save(d);
 }
 @Transactional(readOnly=true) public void validarAvanco(Long emp,Long pid,Long eid){
  for(PrjDependencia d:deps.findByProjetoIdAndEmpresaIdAndDeletedAtIsNull(pid,emp)){
   if(!eid.equals(d.getSucessoraId()))continue;
   PrjEtapa a=etapa(emp,pid,d.getAntecessoraId());
   if(!"CONCLUIDA".equals(a.getStatus()))throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Predecessora incompleta");
   int lag=d.getDefasagemDias()==null?0:d.getDefasagemDias();
   if(lag>0&&(a.getConcluidaEm()==null||LocalDate.now().isBefore(a.getConcluidaEm().toLocalDate().plusDays(lag))))throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Defasagem nao cumprida");
  }
 }
}
