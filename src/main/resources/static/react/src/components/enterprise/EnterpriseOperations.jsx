import React, {useEffect, useMemo, useState} from 'react';
import { localeAtivo } from '../shared/LocaleData.js';
import {Card} from 'primereact/card';
import {TabView, TabPanel} from 'primereact/tabview';
import {DataTable} from 'primereact/datatable';
import {Column} from 'primereact/column';
import {Button} from 'primereact/button';
import {Dialog} from 'primereact/dialog';
import {InputText} from 'primereact/inputtext';
import {InputNumber} from 'primereact/inputnumber';
import {Dropdown} from 'primereact/dropdown';
import {Calendar} from 'primereact/calendar';
import {Message} from 'primereact/message';
import {Tag} from 'primereact/tag';
import {api} from '../../services/ApiConfig';
import { formatoData } from '../shared/LocaleData.js';

const resources = [
 {key:'contratos',label:'Contratos',singular:'contrato',icon:'pi pi-file-edit'},
 {key:'qualificacoes',label:'Fornecedores',singular:'qualificação',icon:'pi pi-verified'},
 {key:'metas',label:'Metas comerciais',singular:'meta comercial',icon:'pi pi-chart-line'},
 {key:'periodos',label:'Fechamento',singular:'fechamento',icon:'pi pi-lock'},
 {key:'orcamentos',label:'Orçamento',singular:'orçamento',icon:'pi pi-wallet'},
 {key:'tesouraria',label:'Tesouraria projetada',singular:'tesouraria projetada',icon:'pi pi-chart-bar'},
 {key:'tributacao',label:'Cenários tributários',singular:'cenário tributário',icon:'pi pi-calculator'}
];

const money = v => Number(v || 0).toLocaleString(localeAtivo(),{style:'currency',currency:'BRL'});

export default function EnterpriseOperations(){
 const [active,setActive]=useState(0), [rows,setRows]=useState([]), [loading,setLoading]=useState(false);
 const [dialog,setDialog]=useState(false), [payload,setPayload]=useState({}), [error,setError]=useState('');
 const resource=resources[active];

 const load=async()=>{setLoading(true);setError('');try{
   const r=await api.get('/enterprise/'+resource.key); setRows(Array.isArray(r.data)?r.data:(r.data?.data||[]));
 }catch(e){setError(e?.response?.data?.message||e.message||'Falha ao consultar');}finally{setLoading(false)}};
 useEffect(()=>{load()},[active]);

 const fields=useMemo(()=>{
   const common={status:'Status',descricao:'Descrição',observacao:'Observação'};
   if(resource.key==='contratos') return {...common,tipo:'Tipo',numero:'Número',parceiro_tipo:'Parceiro',parceiro_id:'ID parceiro',inicio:'Início',fim:'Fim',valor_total:'Valor total'};
   if(resource.key==='qualificacoes') return {fornecedor_id:'Fornecedor ID',status:'Status',score:'Score',validade:'Validade',categoria:'Categoria',observacao:'Observação'};
   if(resource.key==='metas') return {vendedor_id:'Vendedor ID',periodo_inicio:'Início',periodo_fim:'Fim',meta_valor:'Meta valor',meta_quantidade:'Meta quantidade',status:'Status'};
   if(resource.key==='periodos') return {competencia:'Competência',status:'Status',observacao:'Observação'};
   if(resource.key==='orcamentos') return {competencia:'Competência',centro_custo_id:'Centro de custo',conta_contabil_id:'Conta contábil',versao:'Versão',valor_orcado:'Orçado',valor_revisado:'Revisado',valor_realizado:'Realizado',status:'Status'};
   if(resource.key==='tesouraria') return {data_prevista:'Data',tipo:'Tipo',origem:'Origem',referencia_id:'Referência',descricao:'Descrição',valor:'Valor',probabilidade:'Probabilidade',status:'Status'};
   return {nome:'Nome',vigencia_inicio:'Início',vigencia_fim:'Fim',regime:'Regime',uf_origem:'UF origem',uf_destino:'UF destino',cst:'CST',cfop:'CFOP',aliquota_icms:'ICMS %',aliquota_ibs:'IBS %',aliquota_cbs:'CBS %',reducao:'Redução %',status:'Status',observacao:'Observação'};
 },[resource.key]);

 const save=async()=>{setError('');try{await api.post('/enterprise/'+resource.key,payload);setDialog(false);setPayload({});load()}catch(e){setError(e?.response?.data?.message||e.message||'Falha ao salvar')}};
 const textFields=['tipo','numero','parceiro_tipo','categoria','origem','descricao','observacao','regime','uf_origem','uf_destino','cst','cfop','nome','status'];
 const dateFields=['inicio','fim','validade','periodo_inicio','periodo_fim','competencia','data_prevista','vigencia_inicio','vigencia_fim'];

 return <div className="p-3">
  <Card title="Gestão Empresarial">
   <Message severity="info" text="Bloco transversal: contratos, homologação de fornecedores, metas, fechamento, orçamento, tesouraria e cenários tributários."/>
   {error&&<Message severity="error" text={error} className="mt-2"/>}
   <TabView activeIndex={active} onTabChange={e=>setActive(e.index)}>
    {resources.map((r)=><TabPanel key={r.key} header={r.label} leftIcon={r.icon+' mr-2'}>
      <div className="flex justify-content-end mb-2"><Button label="Novo" icon="pi pi-plus" onClick={()=>{setPayload({});setDialog(true)}}/></div>
      <DataTable value={rows} loading={loading} paginator rows={10} stripedRows responsiveLayout="scroll">
       {Object.keys(fields).map(k=><Column key={k} field={k} header={fields[k]} body={v=>k.includes('valor')||k==='meta_valor'||k==='valor'?money(v[k]):v[k] ?? '—'}/>)}
       {resource.key==='periodos'&&<Column header="Ações" body={r=><Button size="small" label="Fechar contábil" icon="pi pi-lock" disabled={r.fechamento_contabil} onClick={async()=>{await api.post('/enterprise/periodos/'+r.id+'/fechar?etapa=contabil');load()}}/>}/>}
      </DataTable>
    </TabPanel>)}
   </TabView>
  </Card>
  <Dialog header={'Adicionar '+(resource.singular||resource.label)} visible={dialog} style={{width:'min(720px,95vw)'}} onHide={()=>setDialog(false)}>
   <div className="grid">
    {Object.keys(fields).map(k=><div className="col-12 md:col-6" key={k}><label className="block mb-1">{fields[k]}</label>
      {dateFields.includes(k)?<Calendar value={payload[k]?new Date(payload[k]):null} onChange={e=>setPayload({...payload,[k]:e.value?.toISOString().slice(0,10)})} dateFormat={formatoData()} className="w-full"/>:
       k.includes('valor')||k.includes('quantidade')||k.includes('score')||k.includes('probabilidade')||k.includes('aliquota')||k==='reducao'?<InputNumber value={payload[k]} onValueChange={e=>setPayload({...payload,[k]:e.value})} className="w-full"/>:
       <InputText value={payload[k]??''} onChange={e=>setPayload({...payload,[k]:e.target.value})} className="w-full"/>}
    </div>)}
   </div>
   <div className="flex justify-content-end gap-2 mt-3"><Button label="Cancelar" severity="secondary" onClick={()=>setDialog(false)}/><Button label="Salvar" icon="pi pi-check" onClick={save}/></div>
  </Dialog>
 </div>;
}
