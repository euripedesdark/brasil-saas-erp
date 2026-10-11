import { useTranslation } from 'react-i18next';
import { localeAtivo } from '../shared/LocaleData.js';
import React, { useState, useEffect, useRef } from 'react';
import { Card } from 'primereact/card';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { Dialog } from 'primereact/dialog';
import { InputText } from 'primereact/inputtext';
import { InputNumber } from 'primereact/inputnumber';
import { Dropdown } from 'primereact/dropdown';
import { Chart } from 'primereact/chart';
import { Toast } from 'primereact/toast';
import { Tag } from 'primereact/tag';
import { Divider } from 'primereact/divider';
import { Calendar } from 'primereact/calendar';
import { Message } from 'primereact/message';
import ApiConfig, { apiFetch } from '../../services/ApiConfig';
import { useAuth } from '../../contexts/AuthContext';
import './BI.css';
import ResumoBI from './ResumoBI';

export const BI = () => {
  const { t } = useTranslation();
    const { user } = useAuth();
    const toast = useRef(null);
    const [dashboards, setDashboards] = useState([]);
    const [indicadores, setIndicadores] = useState([]);
    const [loading, setLoading] = useState(false);
    const [dialogVisible, setDialogVisible] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState(false);
    const [managementOpen, setManagementOpen] = useState(false);
    
    const [novoDashboard, setNovoDashboard] = useState({
        id: null,
        titulo: '',
        descricao: '',
        tipo: 'FINANCEIRO',
        periodos: 'MENSAL',
        ativo: true
    });

    const tipoOptions = [
        { label: 'Financeiro', value: 'FINANCEIRO' },
        { label: 'Vendas', value: 'VENDAS' },
        { label: 'Estoque', value: 'ESTOQUE' },
        { label: 'Produção', value: 'PRODUCAO' },
        { label: 'RH', value: 'RH' },
        { label: 'Customizado', value: 'CUSTOMIZADO' }
    ];

    const periodoOptions = [
        { label: 'Diário', value: 'DIARIO' },
        { label: 'Semanal', value: 'SEMANAL' },
        { label: 'Mensal', value: 'MENSAL' },
        { label: 'Trimestral', value: 'TRIMESTRAL' },
        { label: 'Anual', value: 'ANUAL' }
    ];

    const [chartData, setChartData] = useState({
        labels: [],
        datasets: []
    });

    const [chartOptions, setChartOptions] = useState({
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                position: 'top'
            },
            title: {
                display: true,
                text: 'Análise de Desempenho'
            }
        }
    });

    useEffect(() => {
        if (!managementOpen) return;
        fetchDashboards();
        fetchIndicadores();
    }, [managementOpen, user?.empresaId]);

    const fetchDashboards = async () => {
        setLoading(true);
        try {
            const response = await apiFetch(`${ApiConfig.BASE_URL}/api/bi/dashboards?empresaId=${user?.empresaId || ''}`);
            if (response.ok) {
                const data = await response.json();
                setDashboards(data.data || data);
            }
        } catch (err) {
            console.error(t('legacyUi.bi.loadError'), err);
            toast.current?.show({
                severity: 'error',
                summary: 'Erro',
                detail: t('legacyUi.bi.loadError'),
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    const fetchIndicadores = async () => {
        setLoading(true);
        try {
            const response = await apiFetch(`${ApiConfig.BASE_URL}/api/bi/indicadores?empresaId=${user?.empresaId || ''}`);
            if (response.ok) {
                const data = await response.json();
                setIndicadores(data.data || data);
                
                // Atualizar gráfico com dados dos indicadores
                if (data.data && data.data.length > 0) {
                    updateChartData(data.data);
                }
            }
        } catch (err) {
            console.error('Erro ao carregar indicadores', err);
        } finally {
            setLoading(false);
        }
    };

    const updateChartData = (indicadoresData) => {
        const labels = indicadoresData.map(i => i.nome || i.codigo);
        const values = indicadoresData.map(i => i.valor || 0);
        
        setChartData({
            labels: labels,
            datasets: [
                {
                    label: t('legacyUi.bi.indicators'),
                    data: values,
                    backgroundColor: [
                        '#3b82f6', '#10b981', '#f59e0b', '#ef4444', 
                        '#8b5cf6', '#06b6d4', '#eab308', '#ec4899'
                    ],
                    borderColor: '#ffffff',
                    borderWidth: 2
                }
            ]
        });
    };

    const salvarDashboard = async () => {
        setLoading(true);
        setError('');
        setSuccess(false);
        
        try {
            const dashboardParaSalvar = {
                ...novoDashboard,
                empresaId: user?.empresaId
            };

            const method = dashboardParaSalvar.id ? 'PUT' : 'POST';
            const url = dashboardParaSalvar.id 
                ? `${ApiConfig.BASE_URL}/api/bi/dashboards/${dashboardParaSalvar.id}?empresaId=${user?.empresaId}`
                : `${ApiConfig.BASE_URL}/api/bi/dashboards?empresaId=${user?.empresaId}`;

            const response = await apiFetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(dashboardParaSalvar)
            });

            if (response.ok) {
                setSuccess(true);
                toast.current?.show({
                    severity: 'success',
                    summary: 'Sucesso',
                    detail: `Dashboard ${dashboardParaSalvar.id ? 'atualizado' : 'criado'} com sucesso`,
                    life: 3000
                });
                setTimeout(() => {
                    setDialogVisible(false);
                    fetchDashboards();
                    resetForm();
                }, 1500);
            } else {
                const err = await response.text();
                setError(err);
                toast.current?.show({
                    severity: 'error',
                    summary: 'Erro',
                    detail: err || t('legacyUi.bi.saveError'),
                    life: 3000
                });
            }
        } catch (err) {
            setError('Erro ao salvar dashboard');
            toast.current?.show({
                severity: 'error',
                summary: 'Erro',
                detail: 'Erro de conexão ao salvar dashboard',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    const excluirDashboard = async (id) => {
        try {
            const response = await apiFetch(`${ApiConfig.BASE_URL}/api/bi/dashboards/${id}?empresaId=${user?.empresaId}`, {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' }
            });

            if (response.ok) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Sucesso',
                    detail: t('messages.deleted'),
                    life: 3000
                });
                fetchDashboards();
            } else {
                const err = await response.text();
                toast.current?.show({
                    severity: 'error',
                    summary: 'Erro',
                    detail: err || t('legacyUi.bi.deleteError'),
                    life: 3000
                });
            }
        } catch (err) {
            toast.current?.show({
                severity: 'error',
                summary: 'Erro',
                detail: 'Erro de conexão ao excluir dashboard',
                life: 3000
            });
        }
    };

    const abrirDialog = (dashboard = null) => {
        if (dashboard) {
            setNovoDashboard({
                id: dashboard.id,
                titulo: dashboard.titulo || '',
                descricao: dashboard.descricao || '',
                tipo: dashboard.tipo || 'FINANCEIRO',
                periodos: dashboard.periodos || 'MENSAL',
                ativo: dashboard.ativo !== false
            });
        } else {
            resetForm();
        }
        setDialogVisible(true);
    };

    const resetForm = () => {
        setNovoDashboard({
            id: null,
            titulo: '',
            descricao: '',
            tipo: 'FINANCEIRO',
            periodos: 'MENSAL',
            ativo: true
        });
        setError('');
        setSuccess(false);
    };

    const formatarMoeda = (valor) => {
        if (valor === null || valor === undefined) return 'R$ 0,00';
        return valor.toLocaleString(localeAtivo(), { style: 'currency', currency: 'BRL' });
    };

    const formatarPercentual = (valor) => {
        if (valor === null || valor === undefined) return '0%';
        return `${(valor * 100).toFixed(2)}%`;
    };

    const statusTemplate = (rowData) => {
        return (
            <Tag 
                value={rowData.ativo ? t('legacyUi.bi.active') : t('legacyUi.bi.inactive')}
                severity={rowData.ativo ? 'success' : 'danger'}
            />
        );
    };

    const tipoTemplate = (rowData) => {
        const tipoMap = {
            'FINANCEIRO': 'Financeiro',
            'VENDAS': 'Vendas',
            'ESTOQUE': 'Estoque',
            'PRODUCAO': 'Produção',
            'RH': 'RH',
            'CUSTOMIZADO': 'Customizado'
        };
        return <span>{tipoMap[rowData.tipo] || rowData.tipo}</span>;
    };

    const acoesTemplate = (rowData) => {
        return (
            <div className="bi-acoes">
                <Button
                    icon="pi pi-eye"
                    className="p-button-info p-button-sm p-button-text"
                    onClick={() => abrirDialog(rowData)}
                    tooltip="Visualizar"
                />
                <Button
                    icon="pi pi-pencil"
                    className="p-button-primary p-button-sm p-button-text"
                    onClick={() => abrirDialog(rowData)}
                    tooltip="Editar"
                />
                <Button
                    icon="pi pi-trash"
                    className="p-button-danger p-button-sm p-button-text"
                    onClick={() => excluirDashboard(rowData.id)}
                    tooltip="Excluir"
                />
            </div>
        );
    };

    const dialogFooter = (
        <div>
            <Button
                label="Cancelar"
                icon="pi pi-times"
                className="p-button-text"
                onClick={() => {
                    setDialogVisible(false);
                    resetForm();
                }}
            />
            <Button
                label="Salvar Dashboard"
                icon="pi pi-save"
                className="p-button-success"
                onClick={salvarDashboard}
                loading={loading}
            />
        </div>
    );

    return (
        <div className="bi-enterprise-container">
            <Toast ref={toast} />
            <ResumoBI />
            <details onToggle={event => setManagementOpen(event.currentTarget.open)}>
            <summary>{t('biOverview.management')}</summary>
            
            <Card title="Business Intelligence - Brasil SaaS ERP" className="bi-main-card">
                <div className="bi-header-actions mb-4 flex justify-content-between align-items-center">
                    <div className="bi-info">
                        <p className="text-muted m-0">Dashboards, indicadores e análise de desempenho empresarial.</p>
                    </div>
                    <Button
                        label="Novo Dashboard"
                        icon="pi pi-plus"
                        onClick={() => abrirDialog()}
                        className="p-button-success"
                    />
                </div>

                {/* Dashboard de Resumo */}
                <div className="bi-dashboard-summary grid mb-4">
                    <div className="col-12 md:col-3">
                        <Card className="bi-kpi-card">
                            <div className="bi-kpi-content">
                                <i className="pi pi-chart-line" style={{ fontSize: '2rem', color: '#3b82f6' }}></i>
                                <div className="bi-kpi-value">
                                    <span>{dashboards.length}</span>
                                    <small>Dashboards</small>
                                </div>
                            </div>
                        </Card>
                    </div>
                    <div className="col-12 md:col-3">
                        <Card className="bi-kpi-card">
                            <div className="bi-kpi-content">
                                <i className="pi pi-chart-bar" style={{ fontSize: '2rem', color: '#10b981' }}></i>
                                <div className="bi-kpi-value">
                                    <span>{indicadores.length}</span>
                                    <small>Indicadores</small>
                                </div>
                            </div>
                        </Card>
                    </div>
                    <div className="col-12 md:col-3">
                        <Card className="bi-kpi-card">
                            <div className="bi-kpi-content">
                                <i className="pi pi-database" style={{ fontSize: '2rem', color: '#f59e0b' }}></i>
                                <div className="bi-kpi-value">
                                    <span>{formatarMoeda(indicadores.reduce((acc, i) => acc + (i.valor || 0), 0))}</span>
                                    <small>Total Geral</small>
                                </div>
                            </div>
                        </Card>
                    </div>
                    <div className="col-12 md:col-3">
                        <Card className="bi-kpi-card">
                            <div className="bi-kpi-content">
                                <i className="pi pi-clock" style={{ fontSize: '2rem', color: '#ef4444' }}></i>
                                <div className="bi-kpi-value">
                                    <span>Tempo Real</span>
                                    <small>Atualização</small>
                                </div>
                            </div>
                        </Card>
                    </div>
                </div>

                <Divider />

                {/* Gráfico de Indicadores */}
                <Card title="Indicadores de Desempenho" className="bi-chart-card mb-4">
                    <div style={{ height: '400px' }}>
                        <Chart 
                            type="bar" 
                            data={chartData} 
                            options={chartOptions}
                        />
                    </div>
                </Card>

                <Divider />

                {/* Listagem de Dashboards */}
                <Card title="Meus Dashboards" className="bi-dashboards-card">
                    <DataTable
                        value={dashboards}
                        loading={loading}
                        paginator
                        rows={10}
                        rowsPerPageOptions={[10, 20, 50, 100]}
                        responsiveLayout="scroll"
                        className="p-datatable-sm"
                        emptyMessage="Nenhum dashboard encontrado"
                    >
                        <Column field="titulo" header="Título" sortable style={{ width: '200px' }} />
                        <Column field="descricao" header="Descrição" sortable style={{ width: '250px' }} />
                        <Column field="tipo" header="Tipo" body={tipoTemplate} sortable style={{ width: '120px' }} />
                        <Column field="periodos" header="Período" sortable style={{ width: '120px' }} />
                        <Column field="ativo" header="Status" body={statusTemplate} sortable style={{ width: '100px' }} />
                        <Column body={acoesTemplate} style={{ width: '150px' }} />
                    </DataTable>
                </Card>

                {/* Listagem de Indicadores */}
                <Card title="Indicadores Configurados" className="bi-indicadores-card mt-4">
                    <DataTable
                        value={indicadores}
                        loading={loading}
                        paginator
                        rows={10}
                        rowsPerPageOptions={[10, 20, 50, 100]}
                        responsiveLayout="scroll"
                        className="p-datatable-sm"
                        emptyMessage="Nenhum indicador encontrado"
                    >
                        <Column field="nome" header="Nome" sortable style={{ width: '200px' }} />
                        <Column field="codigo" header="Código" sortable style={{ width: '120px' }} />
                        <Column field="valor" header="Valor" body={formatarMoeda} sortable style={{ width: '120px' }} />
                        <Column field="variacao" header="Variação" body={formatarPercentual} sortable style={{ width: '120px' }} />
                        <Column field="descricao" header="Descrição" sortable style={{ width: '200px' }} />
                    </DataTable>
                </Card>
            </Card>

            {/* Dialog de Dashboard */}
            <Dialog
                header={novoDashboard.id ? `Editar Dashboard: ${novoDashboard.titulo}` : 'Novo Dashboard'}
                visible={dialogVisible}
                style={{ width: '700px' }}
                onHide={() => {
                    setDialogVisible(false);
                    resetForm();
                }}
                footer={dialogFooter}
                maximizable
            >
                <div className="p-fluid">
                    {error && <Message severity="error" text={error} className="w-full mb-3" />}
                    {success && <Message severity="success" text="Dashboard salvo com sucesso!" className="w-full mb-3" />}

                    <div className="grid">
                        <div className="col-12 field">
                            <label className="font-bold mb-2 block">Título do Dashboard</label>
                            <InputText
                                value={novoDashboard.titulo}
                                onChange={(e) => setNovoDashboard({...novoDashboard, titulo: e.target.value})}
                                placeholder="Dashboard Financeiro Mensal"
                            />
                        </div>
                        <div className="col-12 field">
                            <label className="font-bold mb-2 block">Descrição</label>
                            <InputText
                                value={novoDashboard.descricao}
                                onChange={(e) => setNovoDashboard({...novoDashboard, descricao: e.target.value})}
                                placeholder="Análise de faturamento e despesas por mês"
                            />
                        </div>
                        <div className="col-12 md:col-6 field">
                            <label className="font-bold mb-2 block">Tipo</label>
                            <Dropdown
                                value={novoDashboard.tipo}
                                options={tipoOptions}
                                onChange={(e) => setNovoDashboard({...novoDashboard, tipo: e.value})}
                                optionLabel="label"
                                optionValue="value"
                            />
                        </div>
                        <div className="col-12 md:col-6 field">
                            <label className="font-bold mb-2 block">Período</label>
                            <Dropdown
                                value={novoDashboard.periodos}
                                options={periodoOptions}
                                onChange={(e) => setNovoDashboard({...novoDashboard, periodos: e.value})}
                                optionLabel="label"
                                optionValue="value"
                            />
                        </div>
                        <div className="col-12 field">
                            <div className="p-checkbox p-col-12">
                                <input
                                    type="checkbox"
                                    id="ativo"
                                    checked={novoDashboard.ativo}
                                    onChange={(e) => setNovoDashboard({...novoDashboard, ativo: e.target.checked})}
                                    className="p-checkbox-input"
                                />
                                <label htmlFor="ativo" className="p-checkbox-label ml-2">
                                    <strong>Dashboard {t('legacyUi.bi.active')}</strong>
                                </label>
                            </div>
                        </div>
                    </div>
                </div>
            </Dialog>
            </details>
        </div>
    );
};

export default BI;
