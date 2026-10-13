package br.com.brasil_saas.crm.service;
import br.com.brasil_saas.crm.model.*; import br.com.brasil_saas.crm.repository.*;
import lombok.RequiredArgsConstructor; import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class CrmCampanhaService {
 private final CrmCampanhaRepository campanhas;
 private final CrmCampanhaContatoRepository contatos;
 private final CrmLeadRepository leads;
 private ResponseStatusException erro(HttpStatus code,String msg){return new ResponseStatusException(code,msg);}
 private CrmCampanha exigir(Long empresa,Long id){return campanhas.findByIdAndEmpresaIdAndDeletedAtIsNull(id,empresa).orElseThrow(()->erro(HttpStatus.NOT_FOUND,"Campanha inexistente"));}
 private void validar(CrmCampanha c){
  if(c.getNome()==null||c.getNome().isBlank())throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Nome obrigatorio");
  if(c.getDataInicio()!=null&&c.getDataFim()!=null&&c.getDataFim().isBefore(c.getDataInicio()))throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Periodo invalido");
  if(c.getOrcamento()==null||c.getOrcamento().signum()<0||c.getCustoRealizado()==null||c.getCustoRealizado().signum()<0)throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Valores invalidos");
 }
 @Transactional(readOnly=true) public List<CrmCampanha> listar(Long empresa){return campanhas.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa);}
 @Transactional public CrmCampanha criar(Long empresa,CrmCampanha c){
  c.setId(null);c.setEmpresaId(empresa);c.setStatus("RASCUNHO");
  if(c.getCanal()==null||c.getCanal().isBlank())c.setCanal("OUTRO");
  if(c.getOrcamento()==null)c.setOrcamento(BigDecimal.ZERO);
  if(c.getCustoRealizado()==null)c.setCustoRealizado(BigDecimal.ZERO);
  validar(c);return campanhas.save(c);
 }
 @Transactional public CrmCampanha alterar(Long empresa,Long id,CrmCampanha req){
  CrmCampanha c=exigir(empresa,id);
  if(!"RASCUNHO".equals(c.getStatus()))throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Somente rascunho editavel");
  c.setNome(req.getNome());c.setDescricao(req.getDescricao());c.setCanal(req.getCanal()==null||req.getCanal().isBlank()?"OUTRO":req.getCanal());
  c.setDataInicio(req.getDataInicio());c.setDataFim(req.getDataFim());
  c.setOrcamento(req.getOrcamento()==null?BigDecimal.ZERO:req.getOrcamento());
  c.setCustoRealizado(req.getCustoRealizado()==null?BigDecimal.ZERO:req.getCustoRealizado());
  validar(c);return campanhas.save(c);
 }
 @Transactional public CrmCampanha mudarStatus(Long empresa,Long id,String destino){
  CrmCampanha c=exigir(empresa,id);
  if(!(("RASCUNHO".equals(c.getStatus())&&"ATIVA".equals(destino))||("ATIVA".equals(c.getStatus())&&"ENCERRADA".equals(destino))))
   throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Transicao invalida");
  c.setStatus(destino);return campanhas.save(c);
 }
 @Transactional(readOnly=true) public List<CrmCampanhaContato> contatos(Long empresa,Long campanha){
  exigir(empresa,campanha);return contatos.findByCampanhaIdAndEmpresaIdAndDeletedAtIsNull(campanha,empresa);
 }
 @Transactional public CrmCampanhaContato vincular(Long empresa,Long campanha,Long leadId){
  if("ENCERRADA".equals(exigir(empresa,campanha).getStatus()))throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Campanha encerrada");
  if(leadId==null||leads.findByIdAndEmpresaIdAndDeletedAtIsNull(leadId,empresa).isEmpty())throw erro(HttpStatus.NOT_FOUND,"Lead inexistente nesta empresa");
  if(contatos.existsByCampanhaIdAndLeadIdAndEmpresaIdAndDeletedAtIsNull(campanha,leadId,empresa))throw erro(HttpStatus.CONFLICT,"Lead ja vinculado");
  CrmCampanhaContato c=new CrmCampanhaContato();c.setEmpresaId(empresa);c.setCampanhaId(campanha);c.setLeadId(leadId);
  return contatos.save(c);
 }
 @Transactional public CrmCampanhaContato registrar(Long empresa,Long campanha,Long contato,String status,String observacao){
  if(!"ATIVA".equals(exigir(empresa,campanha).getStatus()))throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Campanha nao ativa");
  if(status==null||!Set.of("CONTATADO","RESPONDEU","CONVERTIDO","DESCARTADO").contains(status))throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Resultado invalido");
  CrmCampanhaContato c=contatos.findByIdAndEmpresaIdAndDeletedAtIsNull(contato,empresa).filter(x->campanha.equals(x.getCampanhaId())).orElseThrow(()->erro(HttpStatus.NOT_FOUND,"Contato inexistente"));
  if("CONVERTIDO".equals(c.getStatus()))throw erro(HttpStatus.UNPROCESSABLE_CONTENT,"Conversao ja registrada");
  c.setStatus(status);c.setObservacao(observacao);c.setDataUltimoContato(LocalDateTime.now());return contatos.save(c);
 }
 @Transactional(readOnly=true) public Map<String,Object> resumo(Long empresa,Long campanha){
  CrmCampanha c=exigir(empresa,campanha);
  List<CrmCampanhaContato> lista=contatos.findByCampanhaIdAndEmpresaIdAndDeletedAtIsNull(campanha,empresa);
  Map<String,Object> m=new LinkedHashMap<>();
  m.put("campanhaId",c.getId());m.put("status",c.getStatus());m.put("totalLeads",lista.size());
  m.put("respostas",lista.stream().filter(x->"RESPONDEU".equals(x.getStatus())||"CONVERTIDO".equals(x.getStatus())).count());
  m.put("conversoes",lista.stream().filter(x->"CONVERTIDO".equals(x.getStatus())).count());
  m.put("orcamento",c.getOrcamento());m.put("custoRealizado",c.getCustoRealizado());return m;
 }
}
