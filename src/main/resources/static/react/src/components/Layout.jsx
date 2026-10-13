import React, { useMemo, useState, useEffect, useRef, useCallback } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Button } from 'primereact/button';
import { Avatar } from 'primereact/avatar';
import { Divider } from 'primereact/divider';
import { PanelMenu } from 'primereact/panelmenu';
import { Dropdown } from 'primereact/dropdown';
import { TieredMenu } from 'primereact/tieredmenu';
import { Toast } from 'primereact/toast';
import { ConfirmDialog } from 'primereact/confirmdialog';
import { useAuth } from '../contexts/AuthContext';
import ApiConfig, { apiFetch } from '../services/ApiConfig';
import './Layout.css';
import './shared/responsivo.css';
import { ErrorBoundary } from './shared/ErrorBoundary';
import RecentUpdates from './RecentUpdates';
import { AssistenteCopilotoDialog } from './ia/AssistenteCopilotoDialog';
import { ExigeEmpresa } from './core/ExigeEmpresa';
import { useTranslation } from 'react-i18next';
import i18n, { supportedLanguages } from '../i18n';

const STATIC_BG = '/images/tela-inicial.jpeg';

export const Layout = () => {
    const { user, logout, isSuperuser, temModulo } = useAuth();
    const { t } = useTranslation();
    const navigate = useNavigate();
    const location = useLocation();
    const [collapsed, setCollapsed] = useState(false);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [copilotoAberto, setCopilotoAberto] = useState(false);
    const userMenuRef = useRef(null);
    const [bgStyle, setBgStyle] = useState({
        backgroundImage: `url(${STATIC_BG})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
        backgroundRepeat: 'no-repeat'
    });
    const toast = useRef(null);

    // /inicio e / → só fundo; /dashboard → mostra o painel
    const isHomeOnly =
        location.pathname === '/' ||
        location.pathname === '/inicio' ||
        location.pathname === '';

    useEffect(() => {
        let objectUrl = null;

        const applyImageUrl = (url) => {
            setBgStyle({
                backgroundImage: `url(${url})`,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
                backgroundRepeat: 'no-repeat'
            });
        };

        const loadBg = async () => {
            try {
                const response = await apiFetch(ApiConfig.BASE_URL + '/api/superadmin/assets/system/SISTEMA_BACKGROUND');
                if (response.ok && response.status !== 204) {
                    const blob = await response.blob();
                    if (blob.size > 0) {
                        objectUrl = URL.createObjectURL(blob);
                        applyImageUrl(objectUrl);
                        return;
                    }
                }
            } catch (err) {
                console.warn('Imagem de fundo (Mongo) indisponível', err);
            }

            const img = new Image();
            img.onload = () => applyImageUrl(STATIC_BG);
            img.onerror = () => {
                setBgStyle({
                    background: 'linear-gradient(160deg, #0b1220 0%, #152238 45%, #1e3a5f 100%)'
                });
            };
            img.src = STATIC_BG;
        };

        loadBg();

        const handleToast = (e) => {
            toast.current?.show(e.detail);
        };

        window.addEventListener('bc-toast', handleToast);
        return () => {
            window.removeEventListener('bc-toast', handleToast);
            if (objectUrl) URL.revokeObjectURL(objectUrl);
        };
    }, []);

    const handleNavigation = useCallback((path) => {
        navigate(path);
        setDrawerOpen(false);
    }, [navigate]);

    const menuItems = useMemo(() => {
        const item = (labelKey, icon, path) => ({
            label: t(labelKey),
            icon,
            command: () => handleNavigation(path)
        });

        const groups = [
            {
                label: t('nav.registrations'),
                modulo: 'cadastro',
                icon: 'pi pi-database',
                items: [
                    item('menu.people', 'pi pi-users', '/cadastro/pessoas'),
                    item('menu.customers', 'pi pi-user', '/cadastro/clientes'),
                    item('menu.suppliers', 'pi pi-building', '/cadastro/fornecedores'),
                    item('menu.products', 'pi pi-box', '/cadastro/produtos'),
                    item('menu.categories', 'pi pi-tags', '/cadastro/categorias'),
                    item('menu.brands', 'pi pi-bookmark', '/cadastro/marcas'),
                    item('menu.serviceCatalog', 'pi pi-wrench', '/cadastro/servicos'),
                    item('menu.carriers', 'pi pi-truck', '/cadastro/transportadoras'),
                    item('menu.units', 'pi pi-sliders-h', '/cadastro/unidades-medida'),
                    item('menu.cities', 'pi pi-map-marker', '/cadastro/municipios'),
                    item('menu.banks', 'pi pi-building-columns', '/cadastro/bancos')
                ]
            },
            {
                label: t('nav.sales'),
                modulo: 'vendas',
                icon: 'pi pi-shopping-cart',
                items: [
                    item('menu.salesOrders', 'pi pi-file', '/vendas'),
                    item('menu.pos', 'pi pi-shopping-cart', '/vendas/pdv'),
                    item('menu.priceTables', 'pi pi-tags', '/vendas/tabelas-preco'),
                    item('menu.devolucoes', 'pi pi-undo', '/vendas/devolucoes')
                ]
            },
            {
                label: t('nav.purchases'),
                modulo: 'compras',
                icon: 'pi pi-truck',
                items: [
                    item('menu.purchaseOrders', 'pi pi-file-import', '/compras'),
                    item('menu.supplyChain', 'pi pi-sitemap', '/compras/supply-chain'),
                    item('menu.receipts', 'pi pi-download', '/compras/recebimentos'),
                    item('menu.threeWayMatch', 'pi pi-check-square', '/compras/conferencia-faturas'),
                    item('Contratos de fornecimento', 'pi pi-file', '/compras/contratos'),
                    item('Devolucoes de compra', 'pi pi-undo', '/compras/devolucoes')
                ]
            },
            {
                label: t('nav.inventory'),
                modulo: 'estoque',
                icon: 'pi pi-inbox',
                items: [
                    item('menu.stockBalances', 'pi pi-box', '/estoque'),
                    item('menu.stockMovements', 'pi pi-arrow-left-arrow-right', '/estoque/movimentacoes'),
                    item('menu.warehouses', 'pi pi-building', '/estoque/depositos'),
                    item('menu.transfers', 'pi pi-arrow-right-arrow-left', '/estoque/transferencias'),
                    item('menu.counts', 'pi pi-clipboard', '/estoque/inventarios'),
                    item('menu.wms', 'pi pi-map', '/estoque/enderecos'),
                    item('menu.lots', 'pi pi-calendar', '/estoque/lotes'),
                    item('menu.reservations', 'pi pi-lock', '/estoque/reservas'),
                    item('menu.shipping', 'pi pi-send', '/estoque/expedicoes')
                ]
            },
            {
                label: t('nav.production'),
                modulo: 'producao',
                icon: 'pi pi-cog',
                items: [
                    item('menu.productionOrders', 'pi pi-cog', '/producao'),
                    item('menu.productionWaybills', 'pi pi-file', '/producao/romaneios'),
                    item('menu.bom', 'pi pi-sitemap', '/producao/estrutura'),
                    item('Roteiros e centros de trabalho', 'pi pi-list', '/producao/roteiros'),
                    item('MRP', 'pi pi-cog', '/producao/mrp'),
                    item('MPS', 'pi pi-calendar-plus', '/producao/mps'),
                    item('OEE', 'pi pi-gauge', '/producao/oee'),
                    item('Capacidade', 'pi pi-calendar', '/producao/capacidade'),
                    item('menu.productionReports', 'pi pi-clock', '/producao/apontamentos')
                ]
            },
            {
                label: t('nav.finance'),
                modulo: 'financeiro',
                icon: 'pi pi-wallet',
                items: [
                    item('menu.overview', 'pi pi-chart-pie', '/financeiro'),
                    item('menu.payablesReceivables', 'pi pi-money-bill', '/financeiro/lancamentos'),
                    item('menu.titles', 'pi pi-file', '/financeiro/titulos'),
                    item('menu.titleApprovals', 'pi pi-verified', '/financeiro/aprovacoes-titulos'),
                    item('menu.commissions', 'pi pi-percentage', '/financeiro/comissoes'),
                    item('Regras de comissão', 'pi pi-sliders-h', '/financeiro/regras-comissao'),
                    item('menu.cash', 'pi pi-wallet', '/financeiro/caixa'),
                    item('menu.bankSlips', 'pi pi-file-pdf', '/financeiro/boletos'),
                    item('menu.statements', 'pi pi-chart-line', '/financeiro/extrato'),
                    item('menu.reconciliation', 'pi pi-check-square', '/financeiro/conciliacao'),
                    item('menu.functionalAudit', 'pi pi-list-check', '/bi/auditoria-funcional'),
                    item('menu.bankAccounts', 'pi pi-building', '/financeiro/contas-bancarias'),
                    item('menu.chartOfAccounts', 'pi pi-sitemap', '/financeiro/plano-contas'),
                    item('menu.accountingEntries', 'pi pi-book', '/financeiro/contabil'),
                    item('Contabilidade Enterprise / Close', 'pi pi-lock', '/contabilidade/enterprise'),
                    item('menu.costCenters', 'pi pi-th-large', '/financeiro/centro-custos'),
                    item('menu.paymentTerms', 'pi pi-calendar', '/financeiro/condicoes-pagamento'),
                    item('menu.paymentTypes', 'pi pi-credit-card', '/financeiro/tipos-pagamento'),
                    item('menu.renegotiation', 'pi pi-refresh', '/financeiro/renegociacao'),
                    item('menu.budget', 'pi pi-chart-bar', '/financeiro/orcamento'),
                    item('menu.loans', 'pi pi-building-columns', '/financeiro/emprestimos'),
                    item('menu.credito', 'pi pi-shield', '/financeiro/credito'),
                    item('menu.fluxoCaixa', 'pi pi-chart-line', '/financeiro/fluxo-caixa'),
                    item('menu.cobranca', 'pi pi-phone', '/financeiro/cobranca')
                ]
            },
            {
                label: t('nav.tax'),
                modulo: 'fiscal',
                icon: 'pi pi-file-check',
                items: [
                    item('menu.nfe', 'pi pi-file', '/fiscal/nfe'),
                    item('menu.nfse', 'pi pi-file-edit', '/fiscal/nfse'),
                    item('menu.certificates', 'pi pi-key', '/fiscal/certificados'),
                    item('menu.sped', 'pi pi-file-export', '/fiscal/sped'),
                    item('menu.ncm', 'pi pi-list', '/fiscal/ncm'),
                    item('menu.cfop', 'pi pi-list', '/fiscal/cfop'),
                    item('menu.cest', 'pi pi-list', '/fiscal/cest'),
                    item('menu.issqn', 'pi pi-list', '/fiscal/issqn'),
                    item('menu.taxEntries', 'pi pi-download', '/fiscal/entradas'),
                    item('menu.taxes', 'pi pi-percentage', '/fiscal/impostos'),
                    item('Painel fiscal', 'pi pi-chart-bar', '/fiscal/painel'),
                    item('Prontidão fiscal', 'pi pi-check-circle', '/fiscal/prontidao'),
                    item('Apuracao', 'pi pi-calculator', '/fiscal/apuracoes'),
                    item('EFD-Reinf', 'pi pi-book', '/fiscal/reinf'),
                    item('ICMS DIFAL', 'pi pi-percentage', '/fiscal/difal'),
                    item('ICMS-ST (MVA)', 'pi pi-percentage', '/fiscal/icms-st'),
                    item('Regras tributárias', 'pi pi-list', '/fiscal/regras-tributarias'),
                    item('Simulador tributação', 'pi pi-calculator', '/fiscal/simulador'),
                    item('menu.sefaz', 'pi pi-cloud', '/fiscal/sefaz'),
                    item('menu.cteMdfe', 'pi pi-truck', '/fiscal/cte-mdfe'),
                    item('menu.obrigacoes', 'pi pi-calendar-check', '/fiscal/obrigacoes'),
                    item('menu.buscaFiscal', 'pi pi-search', '/fiscal/busca')
                ]
            },
            {
                label: t('nav.services'),
                modulo: 'servicos',
                icon: 'pi pi-wrench',
                items: [
                    item('menu.serviceCatalogFull', 'pi pi-list', '/servicos'),
                    item('menu.serviceOrders', 'pi pi-file-edit', '/ordens-servico')
                ]
            },
            {
                label: t('nav.hr'),
                modulo: 'rh',
                icon: 'pi pi-users',
                items: [
                    item('menu.employees', 'pi pi-user', '/rh'),
                    item('menu.positions', 'pi pi-briefcase', '/rh/cargos'),
                    item('menu.payroll', 'pi pi-money-bill', '/rh/folha'),
                    item('menu.employeePhotos', 'pi pi-image', '/rh/fotos'),
                    item('menu.ponto', 'pi pi-clock', '/rh/ponto'),
                    item('menu.rescisao', 'pi pi-sign-out', '/rh/rescisao'),
                    item('Ferias', 'pi pi-calendar', '/rh/ferias'),
                    item('menu.esocial', 'pi pi-building', '/rh/esocial'),
                ]
            },
            {
                label: t('nav.qualidade', { defaultValue: 'Qualidade' }),
                modulo: 'qualidade',
                icon: 'pi pi-check-circle',
                items: [
                    item('Planos de inspeção', 'pi pi-list-check', '/qualidade'),
                    item('Inspeções', 'pi pi-search', '/qualidade'),
                    item('Não conformidades', 'pi pi-exclamation-triangle', '/qualidade')
                ]
            },
            {
                label: t('nav.ativos', { defaultValue: 'Ativos e Manutenção' }),
                modulo: 'ativos',
                icon: 'pi pi-cog',
                items: [
                    item('Ativos imobilizados', 'pi pi-building', '/ativos'),
                    item('Manutenção (ordens, notas, planos)', 'pi pi-wrench', '/ativos/manutencao'),
                    item('Indicadores MTBF/MTTR', 'pi pi-chart-line', '/ativos/indicadores')
                ]
            },
            {
                label: t('nav.analytics'),
                modulo: 'bi',
                icon: 'pi pi-chart-bar',
                items: [
                    item('BI Control Tower', 'pi pi-chart-line', '/supply-chain/control-tower'),
                    item('menu.kpis', 'pi pi-chart-bar', '/bi/kpis'),
                    item('menu.reports', 'pi pi-file', '/bi/relatorios')
                ]
            },
            {
                // BI e IA sao modulos separados; o grupo so aparece se a
                // pessoa tiver pelo menos um dos dois liberados.
                label: t('nav.intelligence'),
                modulo: 'ia',
                modulosAlternativos: ['bi', 'ia'],
                icon: 'pi pi-chart-line',
                items: [
                    item('menu.bi', 'pi pi-chart-bar', '/bi'),
                    item('menu.kpis', 'pi pi-percentage', '/bi/kpis'),
                    item('menu.biReports', 'pi pi-file', '/bi/relatorios'),
                    item('menu.scheduledReports', 'pi pi-clock', '/bi/relatorios-agendados'),
                    item('nav.ai', 'pi pi-sparkles', '/ia'),
                    item('nav.assistant', 'pi pi-compass', '/assistente'),
                    item('nav.reports', 'pi pi-chart-line', '/relatorios')
                ]
            }
        ];

        // Cada grupo declara o modulo a que pertence. Grupos com
        // `modulosAlternativos` aparecem se a pessoa tiver qualquer um deles
        // (o grupo de IA tambem cobre as telas de BI). Sem `modulo`, o grupo
        // aparece sempre.
        const grupoVisivel = (g) => {
            if (!g.modulo) return true;
            if (g.modulosAlternativos) {
                return g.modulosAlternativos.some(temModulo);
            }
            return temModulo(g.modulo);
        };

        const items = [
            item('nav.dashboard', 'pi pi-home', '/dashboard'),
            ...groups.filter(grupoVisivel)
        ];

        const podeGerirUsuarios = user?.isAdmin || user?.isDiretoria;
        if (podeGerirUsuarios) {
            const adminItens = [
                item('admin.usersPermissions', 'pi pi-user-edit', '/admin/usuarios'),
                item('admin.systemImages', 'pi pi-image', '/admin/configuracoes'),
                item('nav.documents', 'pi pi-folder-open', '/documentos'),
                item('Fluxo de documentos', 'pi pi-share-alt', '/documento-fluxo'),
                item('nav.profile', 'pi pi-user', '/perfil')
            ];

            if (user?.isAdmin) {
                adminItens.push(item('admin.parityAudit', 'pi pi-list-check', '/admin/paridade-erp'));
                adminItens.push(item('admin.endpointCoverage', 'pi pi-sitemap', '/admin/endpoints'));
            }

            // Gerenciador SQL: restrito a SUPERUSER. A ideia e que a manutencao
            // do banco nao dependa do usuario postgres do ERP.
            if (isSuperuser) {
                adminItens.push(item('admin.sqlManager', 'pi pi-database', '/admin/sql'));
                // o controller exige hasRole('ADMIN') e as rotas manipulam
                // arquivos do disco e collections do Mongo
                adminItens.push(item('admin.imageStorage', 'pi pi-server', '/admin/armazenamento'));
            }

            items.unshift({
                label: t('nav.administration'),
                icon: 'pi pi-shield',
                items: adminItens
            });
        }

        items.push({
            label: t('nav.contabilidade', { defaultValue: 'Contabilidade' }),
            icon: 'pi pi-book',
            command: () => handleNavigation('/contabilidade')
        });
        items.push({
            label: t('nav.crm', { defaultValue: 'CRM' }),
            icon: 'pi pi-users',
            command: () => handleNavigation('/crm')
        });
        items.push({
            label: t('nav.wms', { defaultValue: 'WMS' }),
            icon: 'pi pi-box',
            command: () => handleNavigation('/wms')
        });
        items.push({
            label: t('nav.workflow', { defaultValue: 'Workflow' }),
            icon: 'pi pi-sitemap',
            command: () => handleNavigation('/workflow')
        });
        items.push({
            label: t('nav.projetos', { defaultValue: 'Projetos' }),
            icon: 'pi pi-briefcase',
            command: () => handleNavigation('/projetos')
        });
        items.push({
            label: t('nav.dms', { defaultValue: 'DMS' }),
            icon: 'pi pi-folder',
            command: () => handleNavigation('/dms')
        });
        items.push({
            label: t('nav.portais', { defaultValue: 'Portais' }),
            icon: 'pi pi-globe',
            command: () => handleNavigation('/portais')
        });

        items.push({
            label: t('nav.metas', { defaultValue: 'Metas comerciais' }),
            icon: 'pi pi-chart-bar',
            command: () => handleNavigation('/vendas/metas')
        });
        items.push({
            label: t('nav.contratosVenda', { defaultValue: 'Contratos de venda' }),
            icon: 'pi pi-file',
            command: () => handleNavigation('/vendas/contratos')
        });
        items.push({
            label: t('nav.helpdesk', { defaultValue: 'Helpdesk' }),
            icon: 'pi pi-ticket',
            command: () => handleNavigation('/helpdesk')
        });
        items.push({
            label: t('nav.agenda', { defaultValue: 'Agenda' }),
            icon: 'pi pi-calendar',
            command: () => handleNavigation('/agenda')
        });
        items.push({
            label: t('nav.notificacoes', { defaultValue: 'Notificações' }),
            icon: 'pi pi-bell',
            command: () => handleNavigation('/notificacoes')
        });
        items.push({
            label: t('nav.conhecimento', { defaultValue: 'Conhecimento' }),
            icon: 'pi pi-book',
            command: () => handleNavigation('/conhecimento')
        });
        items.push({ label: t('nav.integracoes', { defaultValue: 'Integrações Enterprise' }), icon: 'pi pi-share-alt', command: () => handleNavigation('/integracoes-enterprise') });
        items.push({
            label: t('nav.supply', { defaultValue: 'Supply Chain / Engenharia' }),
            items: [
                item('Supply Chain / Engenharia', 'pi pi-sitemap', '/supply-chain-enterprise'),
                item('Supply Chain Control Tower', 'pi pi-chart-line', '/supply-chain/control-tower'),
                item('Ciclo de vida (PLM)', 'pi pi-cog', '/plm/mudancas')
            ],
            icon: 'pi pi-sitemap'
        });
        items.push({
            label: t('nav.tms', { defaultValue: 'TMS Planejamento' }),
            icon: 'pi pi-truck',
            command: () => handleNavigation('/enterprise/tms-planejamento')
        });
        items.push({
            label: t('nav.grcTms', { defaultValue: 'GRC / TMS Enterprise' }),
            icon: 'pi pi-shield',
            command: () => handleNavigation('/riscos-tms-enterprise')
        });
        items.push({
            label: t('nav.eliminacoes', { defaultValue: 'Eliminações intercompany' }),
            icon: 'pi pi-sync',
            command: () => handleNavigation('/enterprise/intercompany-eliminacoes')
        });
        items.push({
            label: t('nav.governanca', { defaultValue: 'Governança Corporativa' }),
            icon: 'pi pi-shield',
            command: () => handleNavigation('/governanca-corporativa')
        });
        items.push({
            label: t('nav.gestao', { defaultValue: 'Gestão Empresarial' }),
            icon: 'pi pi-building',
            command: () => handleNavigation('/gestao-empresarial')
        });
        items.push({
            label: t('nav.operacoes', { defaultValue: 'Operações (painel)' }),
            icon: 'pi pi-th-large',
            command: () => handleNavigation('/operacoes')
        });
        items.push({
            label: t('nav.empresasCert', { defaultValue: 'Empresas (Stripe/Cert)' }),
            icon: 'pi pi-key',
            command: () => handleNavigation('/empresas-credenciais')
        });
        items.push({
            label: t('nav.configurarEmpresa', { defaultValue: 'Configurar empresa' }),
            icon: 'pi pi-cog',
            command: () => handleNavigation('/configurar-empresa')
        });
        items.push({
            label: t('nav.golive', { defaultValue: 'Go-live' }),
            icon: 'pi pi-flag',
            command: () => handleNavigation('/golive')
        });
        items.push({
            label: t('nav.aboutProject'),
            icon: 'pi pi-info-circle',
            command: () => handleNavigation('/sobre')
        });
        items.push({
            label: t('nav.donations', { defaultValue: 'Doacoes' }),
            icon: 'pi pi-heart-fill',
            command: () => handleNavigation('/doacoes')
        });
        return items;
    }, [handleNavigation, t, user?.isAdmin, user?.isDiretoria, isSuperuser, temModulo]);

    const userMenuItems = useMemo(() => [
        {
            label: t('nav.logout'),
            icon: 'pi pi-sign-out',
            command: async () => {
                await logout();
                navigate('/login');
            }
        }
    ], [logout, navigate, t]);

    return (
        <div className="enterprise-layout" style={bgStyle}>
            <Toast ref={toast} />
            <ConfirmDialog />

            <div
                className={'bc-sidebar-backdrop' + (drawerOpen ? ' visible' : '')}
                onClick={() => setDrawerOpen(false)}
            />

            <aside className={'enterprise-sidebar' + (collapsed ? ' collapsed' : '') + (drawerOpen ? ' drawer-open' : '')}>
                <div className="sidebar-header">
                    <div className="sidebar-brand">
                        <div className="brand-logo">BS</div>
                        {!collapsed && <span className="brand-name">Brasil SaaS ERP</span>}
                    </div>

                    <Button
                        icon={collapsed ? 'pi pi-angle-double-right' : 'pi pi-angle-double-left'}
                        className="p-button-rounded p-button-text collapse-btn"
                        onClick={() => setCollapsed(c => !c)}
                        tooltip={collapsed ? t('common.expandMenu') : t('common.collapseMenu')}
                        tooltipOptions={{ position: 'right' }}
                    />
                </div>

                <div className="sidebar-user">
                    <Avatar
                        label={(user?.nome || user?.username || '?').charAt(0)}
                        size={collapsed ? 'normal' : 'large'}
                        shape="circle"
                    />
                    {!collapsed && (
                        <div className="user-details">
                            <span className="user-name">{user?.nome || user?.username}</span>
                            <span className="user-role">{user?.perfil || t('common.user')}</span>
                        </div>
                    )}
                </div>

                {!collapsed && <Divider style={{ background: 'var(--bc-sidebar-border)' }} />}

                <div className="sidebar-nav">
                    <PanelMenu model={menuItems} className="enterprise-panel-menu" />
                </div>

                <div className="sidebar-footer">
                    <Button
                        label={collapsed ? '' : (user?.nome || user?.username)}
                        icon="pi pi-user"
                        text
                        onClick={(e) => userMenuRef.current?.toggle(e)}
                        className="user-profile-btn"
                    />
                    <TieredMenu model={userMenuItems} popup ref={userMenuRef} />
                </div>
            </aside>

            <main className="enterprise-main">
                <header className="enterprise-header">
                    <div className="header-content">
                        <div className="current-page-info flex align-items-center gap-2">
                            <Button
                                icon="pi pi-bars"
                                className="p-button-rounded p-button-text bc-menu-toggle"
                                onClick={() => setDrawerOpen(true)}
                                aria-label="Menu"
                            />
                            <h2 className="page-title">{t('app.managementTitle')}</h2>
                        </div>
                        <div className="header-actions flex align-items-center gap-2">
                            <Button
                                icon="pi pi-sparkles"
                                label="Assistente IA"
                                className="p-button-rounded p-button-outlined p-button-sm ai-assistant-top-btn"
                                onClick={() => setCopilotoAberto(true)}
                                tooltip="Copiloto IA (Dúvida ou Ação)"
                                tooltipOptions={{ position: 'bottom' }}
                            />
                            <Dropdown
                                value={i18n.resolvedLanguage || i18n.language}
                                options={supportedLanguages}
                                optionLabel="label"
                                optionValue="code"
                                aria-label={t('login.language')}
                                onChange={event => i18n.changeLanguage(event.value)}
                                className="bc-language-selector"
                                style={{ minWidth: '9rem' }}
                            />
                            <RecentUpdates />
                        </div>
                    </div>
                </header>

                <div className="page-content">
                    {/* A barreira fica aqui, e nao em volta das <Route>, para que o
                        menu lateral e o cabecalho continuem de pe quando uma tela
                        falha. Sem ela, um TypeError no render de um cadastro
                        derrubava a arvore React inteira e o usuario via apenas o
                        fundo — o que parecia "a rota nao abre". */}
                    {!isHomeOnly && (
                        <ErrorBoundary onSair={() => navigate('/dashboard')}>
                            {/* ExigeEmpresa no Outlet, e nao rota por rota: e o
                                que garante que TODO o sistema fique fechado para
                                quem ainda nao cadastrou a empresa, inclusive as
                                telas que forem adicionadas depois. */}
                            <ExigeEmpresa>
                                <Outlet />
                            </ExigeEmpresa>
                        </ErrorBoundary>
                    )}
                </div>
                <Button
                    icon="pi pi-sparkles"
                    className="p-button-rounded p-button-raised ai-fab-btn"
                    onClick={() => setCopilotoAberto(true)}
                    aria-label="Assistente IA"
                    tooltip="Assistente IA"
                    tooltipOptions={{ position: 'left' }}
                    style={{
                        position: 'fixed',
                        bottom: '24px',
                        right: '24px',
                        zIndex: 999,
                        width: '3.5rem',
                        height: '3.5rem',
                        boxShadow: '0 4px 14px rgba(0,0,0,0.25)'
                    }}
                />
                <AssistenteCopilotoDialog
                    visible={copilotoAberto}
                    onHide={() => setCopilotoAberto(false)}
                />
            </main>
        </div>
    );
};
