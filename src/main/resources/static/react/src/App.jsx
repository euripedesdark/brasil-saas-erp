import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Login } from './components/Login';
import { Dashboard } from './components/Dashboard';
import { PaginaNaoEncontrada } from './components/PaginaNaoEncontrada';
import { Usuarios } from './components/admin/Usuarios';
import { Configuracoes } from './components/admin/Configuracoes';
import { SqlConsole } from './components/admin/SqlConsole';
import { Documentos } from './components/core/Documentos';
import { DocumentoFluxoPage } from './components/core/DocumentoFluxoPage';
import { ArmazenamentoImagens } from './components/admin/ArmazenamentoImagens';
import RelatorioParidadeERP from './components/admin/RelatorioParidadeERP';
import EndpointCoverage from './components/admin/EndpointCoverage';

import { CadastroPessoas } from './components/cadastro/CadastroPessoas';
import { CadastroProdutos } from './components/cadastro/CadastroProdutos';
import { Categoria } from './components/cadastro/Categoria';
import { Cliente } from './components/cadastro/Cliente';
import { Bancos } from './components/cadastro/Bancos';
import { Fornecedor } from './components/cadastro/Fornecedor';
import { Marca } from './components/cadastro/Marca';
import { ServicoCadastro } from './components/cadastro/ServicoCadastro';
import { Transportadora } from './components/cadastro/Transportadora';
import { UnidadeMedida } from './components/cadastro/UnidadeMedida';
import Municipios from './components/Municipios';

import { Financeiro } from './components/financeiro/Financeiro';
import FinanceiroHub from './components/financeiro/FinanceiroHub';
import { Comissoes } from './components/financeiro/Comissoes';
import { CentroCusto } from './components/financeiro/CentroCusto';
import { CondicaoPagamento } from './components/financeiro/CondicaoPagamento';
import { ContaBancaria } from './components/financeiro/ContaBancaria';
import { Extrato } from './components/financeiro/Extrato';
import ConciliacaoBancaria from './components/financeiro/ConciliacaoBancaria';
import AuditoriaFuncionalERP from './components/bi/AuditoriaFuncionalERP';
import { LancamentoContabil } from './components/financeiro/LancamentoContabil';
import { PlanoContas } from './components/financeiro/PlanoContas';
import { TipoPagamento } from './components/financeiro/TipoPagamento';
import { Titulo } from './components/financeiro/Titulo';
import { StripePagamentos } from './components/financeiro/StripePagamentos';
import { StripeResultado } from './components/financeiro/StripeResultado';
import { Renegociacao } from './components/financeiro/Renegociacao';
import { Orcamento } from './components/financeiro/Orcamento';
import { Emprestimos } from './components/financeiro/Emprestimos';
import Credito from './components/financeiro/Credito';
import { FluxoCaixa } from './components/financeiro/FluxoCaixa';
import { Cobranca } from './components/financeiro/Cobranca';
import { Copa } from './components/financeiro/Copa';
import RegrasComissao from './components/financeiro/RegrasComissao';
import AprovacoesTitulos from './components/financeiro/AprovacoesTitulos';
import Caixa from './components/Caixa';
import { Workflow } from "./components/workflow/Workflow";
import { Contabilidade } from "./components/contabil/Contabilidade";
import ContabilidadeEnterprise from './components/contabil/ContabilidadeEnterprise';
import { CRM } from "./components/crm/CRM";
import { WMS } from "./components/wms/WMS";
import { Projetos } from "./components/projetos/Projetos";
import { DMS } from "./components/dms/DMS";
import { Portais } from "./components/portais/Portais";
import { PortalPublico } from "./components/portais/PortalPublico";
import { Boletos } from './components/financeiro/Boletos';
import ConfigurarEmpresa from './components/core/ConfigurarEmpresa';
import { EmpresasCredenciais } from './components/core/EmpresasCredenciais';
import Sobre from './components/core/Sobre';
import EnterpriseOperations from './components/enterprise/EnterpriseOperations';
import SupplyChainEnterprise from './components/enterprise/SupplyChainEnterprise';
import SupplyChainControlTower from './components/enterprise/SupplyChainControlTower';
import { Plm } from './components/plm/Plm';
import CorporateGovernance from './components/enterprise/CorporateGovernance';
import RiskTransportEnterprise from './components/enterprise/RiskTransportEnterprise';
import { TmsPlanejamento } from './components/enterprise/TmsPlanejamento';
import { IntercompanyEliminacoes } from './components/enterprise/IntercompanyEliminacoes';
import IntegrationEnterprise from './components/enterprise/IntegrationEnterprise';

import { Nfse } from './components/fiscal/Nfse';
import { NFe } from './components/fiscal/NFe';
import { Ncm } from './components/fiscal/Ncm';
import { Cfop } from './components/fiscal/Cfop';
import { Cest } from './components/fiscal/Cest';
import { Issqn } from './components/fiscal/Issqn';
import { EntradaNota } from './components/fiscal/EntradaNota';
import { Apuracoes } from './components/fiscal/Apuracoes';
import { CertificadoDigital } from './components/fiscal/CertificadoDigital';
import { SefazConsulta } from './components/fiscal/SefazConsulta';
import { Impostos } from './components/fiscal/Impostos';
import { SpedEfd } from './components/fiscal/SpedEfd';
import { CteMdfe } from './components/fiscal/CteMdfe';
import { Obrigacoes } from './components/fiscal/Obrigacoes';
import { Reinf } from './components/fiscal/Reinf';
import { Difal } from './components/fiscal/Difal';
import { IcmsSt } from './components/fiscal/IcmsSt';
import { RegrasTributarias } from './components/fiscal/RegrasTributarias';
import { TributacaoSimulador } from './components/fiscal/TributacaoSimulador';
import { FiscalPainel } from './components/fiscal/FiscalPainel';
import { FiscalProntidao } from './components/fiscal/FiscalProntidao';
import { BuscaFiscal } from './components/fiscal/BuscaFiscal';
import FiscalHub from './components/fiscal/FiscalHub';

import { RH } from './components/rh/RH';
import { Cargo } from './components/rh/Cargo';
import { FolhaPagamento } from './components/rh/FolhaPagamento';
import { Ponto } from './components/rh/Ponto';
import { Rescisao } from './components/rh/Rescisao';
import { Ferias } from './components/rh/Ferias';
import { Esocial } from './components/rh/Esocial';
import { Encargos } from './components/rh/Encargos';
import { FuncionarioFoto } from './components/rh/FuncionarioFoto';

import { Vendas } from './components/vendas/Vendas';
import { TabelasPreco } from './components/vendas/TabelasPreco'
import { Pdv } from './components/vendas/Pdv';
import { Devolucoes } from './components/vendas/Devolucoes';
import { Compras } from './components/compras/Compras';
import { DevCompra } from './components/compras/DevCompra';
import SupplyChainCompras from './components/compras/SupplyChainCompras';
import RecebimentosCompra from './components/compras/RecebimentosCompra';
import ConferenciaFaturasCompra from './components/compras/ConferenciaFaturasCompra';
import { ContratosFornecimento } from './components/compras/ContratosFornecimento';
import { Estoque } from './components/estoque/Estoque';
import { MovimentacoesEstoque } from './components/estoque/MovimentacoesEstoque';
import Depositos from './components/estoque/Depositos';
import TransferenciasEstoque from './components/estoque/TransferenciasEstoque';
import InventariosEstoque from './components/estoque/InventariosEstoque';
import EnderecosEstoque from './components/estoque/EnderecosEstoque';
import LotesEstoque from './components/estoque/LotesEstoque';
import ReservasEstoque from './components/estoque/ReservasEstoque';
import ExpedicoesEstoque from './components/estoque/ExpedicoesEstoque';
import { Servicos } from './components/servicos/Servicos';
import OrdemServico from './components/OrdemServico';
import { Producao } from './components/producao/Producao';
import Qualidade from './components/qualidade/Qualidade';
import { Helpdesk } from './components/helpdesk/Helpdesk';
import { Agenda } from './components/agenda/Agenda';
import { OperacoesPainel } from './components/operacoes/OperacoesPainel';
import { MetasComerciais } from './components/metas/MetasComerciais';
import { Notificacoes } from './components/notificacoes/Notificacoes';
import { Conhecimento } from './components/conhecimento/Conhecimento';
import { ContratosVenda } from './components/contratosvenda/ContratosVenda';
import { GoLive } from './components/golive/GoLive';
import Ativos from './components/ativos/Ativos';
import IndicadoresAtivos from './components/ativos/IndicadoresAtivos';
import ManutencaoAtivos from './components/ativos/ManutencaoAtivos';
import { RomaneioProducao } from './components/producao/RomaneioProducao';
import { ApontamentosProducao } from './components/producao/ApontamentosProducao';
import { EstruturaProduto } from './components/producao/EstruturaProduto';
import Mrp from './components/producao/Mrp';
import MPS from './components/producao/MPS';
import OEE from './components/producao/OEE';
import Capacidade from './components/producao/Capacidade';
import Roteiros from './components/producao/Roteiros';

import { Relatorios } from './components/Relatorios';
// A tela de BI e o painel do Grafana. A gestao de dashboards saiu do
// produto: o BI se configura por banco e por codigo, nao pela interface.
import { ResumoBI } from './components/bi/ResumoBI';
import { Kpis } from './components/bi/Kpis';
import { RelatoriosAgendados } from './components/bi/RelatoriosAgendados';
import { RelatoriosBI } from './components/bi/RelatoriosBI';
import { IA } from './components/ia/IA';
import { Assistente } from './components/ia/Assistente';
import { Perfil } from './components/Perfil';
import { useAuth } from './contexts/AuthContext';
import { I18nDomBridge } from './components/shared/I18nDomBridge';

function App() {
    const { isAuthenticated, loading } = useAuth();

    if (loading) {
        return (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
                <div className="pi pi-spin pi-spinner" style={{ fontSize: '2rem' }} />
            </div>
        );
    }

    return (
        <>
            <I18nDomBridge />
            <Router>
            <Routes>
                <Route path="portal" element={<PortalPublico />} />
                <Route
                    path="/login"
                    element={!isAuthenticated ? <Login /> : <Navigate to="/inicio" replace />}
                />

                <Route
                    path="/"
                    element={isAuthenticated ? <Layout /> : <Navigate to="/login" replace />}
                >
                    <Route index element={<Dashboard />} />
                    <Route path="inicio" element={<Dashboard />} />
                    <Route path="dashboard" element={<Dashboard />} />

                    <Route path="configurar-empresa" element={<ConfigurarEmpresa />} />
                    <Route path="empresas-credenciais" element={<EmpresasCredenciais />} />
                    <Route path="sobre" element={<Sobre />} />
                    <Route path="gestao-empresarial" element={<EnterpriseOperations />} />
                    <Route path="supply-chain-enterprise" element={<SupplyChainEnterprise />} />
                    <Route path="supply-chain/control-tower" element={<SupplyChainControlTower />} />
                    <Route path="plm/mudancas" element={<Plm />} />
                    <Route path="bi/control-tower" element={<Navigate to="/supply-chain/control-tower" replace />} />
                    <Route path="governanca-corporativa" element={<CorporateGovernance />} />
                    <Route path="riscos-tms-enterprise" element={<RiskTransportEnterprise />} />
                    <Route path="enterprise/tms-planejamento" element={<TmsPlanejamento />} />
                    <Route path="enterprise/intercompany-eliminacoes" element={<IntercompanyEliminacoes />} />
                    <Route path="integracoes-enterprise" element={<IntegrationEnterprise />} />
                    <Route path="doacoes" element={<Sobre />} />
                    <Route path="workflow" element={<Workflow />} />
                    <Route path="contabilidade" element={<Contabilidade />} />
                    <Route path="contabilidade/enterprise" element={<ContabilidadeEnterprise />} />
                    <Route path="crm" element={<CRM />} />
                    <Route path="wms" element={<WMS />} />
                    <Route path="projetos" element={<Projetos />} />
                    <Route path="dms" element={<DMS />} />
                    <Route path="portais" element={<Portais />} />
                    <Route path="admin/usuarios" element={<Usuarios />} />
                    <Route path="admin/configuracoes" element={<Configuracoes />} />
                    <Route path="admin/sql" element={<SqlConsole />} />
                    <Route path="admin/armazenamento" element={<ArmazenamentoImagens />} />
                    <Route path="admin/paridade-erp" element={<RelatorioParidadeERP />} />
                    <Route path="admin/endpoints" element={<EndpointCoverage />} />
                    <Route path="perfil" element={<Perfil />} />
                    <Route path="documentos" element={<Documentos />} />
                    <Route path="documento-fluxo" element={<DocumentoFluxoPage />} />

                    <Route path="cadastro/pessoas" element={<CadastroPessoas />} />
                    <Route path="cadastro/produtos" element={<CadastroProdutos />} />
                    <Route path="cadastro/categorias" element={<Categoria />} />
                    <Route path="cadastro/clientes" element={<Cliente />} />
                    <Route path="cadastro/fornecedores" element={<Fornecedor />} />
                    <Route path="cadastro/marcas" element={<Marca />} />
                    <Route path="cadastro/servicos" element={<ServicoCadastro />} />
                    <Route path="cadastro/transportadoras" element={<Transportadora />} />
                    <Route path="cadastro/unidades-medida" element={<UnidadeMedida />} />
                    <Route path="cadastro/municipios" element={<Municipios />} />
                    <Route path="cadastro/bancos" element={<Bancos />} />
                    <Route path="cadastros/municipios" element={<Municipios />} />

                    <Route path="vendas/*" element={<Vendas />} />
                    <Route path="vendas/pdv" element={<Pdv />} />
                    <Route path="vendas/tabelas-preco" element={<TabelasPreco />} />
                    <Route path="vendas/devolucoes" element={<Devolucoes />} />
                    <Route path="compras/*" element={<Compras />} />
                    <Route path="compras/supply-chain" element={<SupplyChainCompras />} />
                    <Route path="compras/recebimentos" element={<RecebimentosCompra />} />
                    <Route path="compras/conferencia-faturas" element={<ConferenciaFaturasCompra />} />
                    <Route path="compras/contratos" element={<ContratosFornecimento />} />
                    <Route path="compras/devolucoes" element={<DevCompra />} />
                    <Route path="estoque/*" element={<Estoque />} />
                    <Route path="estoque/movimentacoes" element={<MovimentacoesEstoque />} />
                    <Route path="estoque/depositos" element={<Depositos />} />
                    <Route path="estoque/transferencias" element={<TransferenciasEstoque />} />
                    <Route path="estoque/inventarios" element={<InventariosEstoque />} />
                    <Route path="estoque/enderecos" element={<EnderecosEstoque />} />
                    <Route path="estoque/lotes" element={<LotesEstoque />} />
                    <Route path="estoque/reservas" element={<ReservasEstoque />} />
                    <Route path="estoque/expedicoes" element={<ExpedicoesEstoque />} />
                    <Route path="servicos" element={<Servicos />} />
                    <Route path="servicos/*" element={<Servicos />} />
                    <Route path="servicos/ordens" element={<OrdemServico />} />
                    <Route path="ordens-servico" element={<OrdemServico />} />

                    <Route path="financeiro" element={<FinanceiroHub />} />
                    <Route path="financeiro/lancamentos" element={<Titulo />} />
                    <Route path="financeiro/titulos" element={<Titulo />} />
                    <Route path="financeiro/stripe" element={<StripePagamentos />} />
                    <Route path="financeiro/stripe/sucesso" element={<StripeResultado />} />
                    <Route path="financeiro/stripe/cancelado" element={<StripeResultado />} />
                    <Route path="financeiro/renegociacao" element={<Renegociacao />} />
                    <Route path="financeiro/orcamento" element={<Orcamento />} />
                    <Route path="financeiro/emprestimos" element={<Emprestimos />} />
                    <Route path="financeiro/credito" element={<Credito />} />
                    <Route path="financeiro/fluxo-caixa" element={<FluxoCaixa />} />
                    <Route path="financeiro/cobranca" element={<Cobranca />} />
                    <Route path="financeiro/copa" element={<Copa />} />
                    <Route path="financeiro/aprovacoes-titulos" element={<AprovacoesTitulos />} />
                    <Route path="financeiro/comissoes" element={<Comissoes />} />
                    <Route path="financeiro/regras-comissao" element={<RegrasComissao />} />
                    <Route path="financeiro/extrato" element={<Extrato />} />
                    <Route path="financeiro/conciliacao" element={<ConciliacaoBancaria />} />
                    <Route path="bi/auditoria-funcional" element={<AuditoriaFuncionalERP />} />
                    <Route path="financeiro/caixa" element={<Caixa />} />
                    <Route path="financeiro/boletos" element={<Boletos />} />
                    <Route path="financeiro/contas-bancarias" element={<ContaBancaria />} />
                    <Route path="financeiro/plano-contas" element={<PlanoContas />} />
                    <Route path="financeiro/contabil" element={<LancamentoContabil />} />
                    <Route path="financeiro/contabil/lancamentos" element={<LancamentoContabil />} />
                    <Route path="financeiro/centro-custos" element={<CentroCusto />} />
                    <Route path="financeiro/condicoes-pagamento" element={<CondicaoPagamento />} />
                    <Route path="financeiro/tipos-pagamento" element={<TipoPagamento />} />

                    <Route path="fiscal" element={<FiscalHub />} />
                    <Route path="fiscal/nfe" element={<NFe />} />
                    <Route path="fiscal/nfse" element={<Nfse />} />
                    <Route path="fiscal/ncm" element={<Ncm />} />
                    <Route path="fiscal/cfop" element={<Cfop />} />
                    <Route path="fiscal/cest" element={<Cest />} />
                    <Route path="fiscal/issqn" element={<Issqn />} />
                    <Route path="fiscal/entradas" element={<EntradaNota />} />
                    <Route path="fiscal/apuracoes" element={<Apuracoes />} />
                    <Route path="fiscal/certificados" element={<CertificadoDigital />} />
                    <Route path="fiscal/impostos" element={<Impostos />} />
                    <Route path="fiscal/sefaz" element={<SefazConsulta />} />
                    <Route path="fiscal/sped" element={<SpedEfd />} />
                    <Route path="fiscal/cte-mdfe" element={<CteMdfe />} />
                    <Route path="fiscal/obrigacoes" element={<Obrigacoes />} />
                    <Route path="fiscal/reinf" element={<Reinf />} />
                    <Route path="fiscal/difal" element={<Difal />} />
                    <Route path="fiscal/icms-st" element={<IcmsSt />} />
                    <Route path="fiscal/regras-tributarias" element={<RegrasTributarias />} />
                    <Route path="fiscal/simulador" element={<TributacaoSimulador />} />
                    <Route path="fiscal/painel" element={<FiscalPainel />} />
                    <Route path="fiscal/prontidao" element={<FiscalProntidao />} />
                    <Route path="fiscal/busca" element={<BuscaFiscal />} />

                    <Route path="rh" element={<RH />} />
                    <Route path="rh/*" element={<RH />} />
                    <Route path="rh/cargos" element={<Cargo />} />
                    <Route path="rh/folha" element={<FolhaPagamento />} />
                    <Route path="rh/ponto" element={<Ponto />} />
                    <Route path="rh/rescisao" element={<Rescisao />} />
                    <Route path="rh/ferias" element={<Ferias />} />
                    <Route path="rh/esocial" element={<Esocial />} />
                    <Route path="rh/encargos" element={<Encargos />} />
                    <Route path="rh/fotos" element={<FuncionarioFoto />} />

                    <Route path="producao" element={<Producao />} />
                    <Route path="producao/*" element={<Producao />} />
                    <Route path="producao/romaneios" element={<RomaneioProducao />} />
                    <Route path="producao/apontamentos" element={<ApontamentosProducao />} />
                    <Route path="producao/estrutura" element={<EstruturaProduto />} />
                    <Route path="producao/roteiros" element={<Roteiros />} />
                    <Route path="producao/mrp" element={<Mrp />} />
                    <Route path="producao/mps" element={<MPS />} />
                    <Route path="producao/oee" element={<OEE />} />
                    <Route path="producao/capacidade" element={<Capacidade />} />
                    <Route path="qualidade" element={<Qualidade />} />
                    <Route path="helpdesk" element={<Helpdesk />} />
                    <Route path="agenda" element={<Agenda />} />
                    <Route path="operacoes" element={<OperacoesPainel />} />
                    <Route path="vendas/metas" element={<MetasComerciais />} />
                    <Route path="vendas/contratos" element={<ContratosVenda />} />
                    <Route path="notificacoes" element={<Notificacoes />} />
                    <Route path="conhecimento" element={<Conhecimento />} />
                    <Route path="golive" element={<GoLive />} />
                    <Route path="ativos" element={<Ativos />} />
                    <Route path="ativos/indicadores" element={<IndicadoresAtivos />} />
                    <Route path="ativos/manutencao" element={<ManutencaoAtivos />} />

                    <Route path="bi" element={<ResumoBI />} />
                    <Route path="bi/kpis" element={<Kpis />} />
                    <Route path="bi/relatorios" element={<RelatoriosBI />} />
                    <Route path="bi/relatorios-agendados" element={<RelatoriosAgendados />} />
                    <Route path="ia" element={<IA />} />
                    <Route path="assistente" element={<Assistente />} />
                    <Route path="relatorios" element={<Relatorios />} />
                    <Route path="relatorios/*" element={<Relatorios />} />
                </Route>

                <Route path="*" element={<PaginaNaoEncontrada />} />
            </Routes>
            </Router>
        </>
    );
}

export default App;
