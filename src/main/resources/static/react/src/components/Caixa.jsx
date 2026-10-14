import { useTranslation } from 'react-i18next';
import React, { useState, useEffect, useRef } from 'react';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { InputNumber } from 'primereact/inputnumber';
import { InputTextarea } from 'primereact/inputtextarea';
import { Dialog } from 'primereact/dialog';
import { Dropdown } from 'primereact/dropdown';
import { Toast } from 'primereact/toast';
import { Tag } from 'primereact/tag';
import { CaixaService } from '../services/CaixaService';
import { localeAtivo } from './shared/LocaleData.js';

const fmt = (v) => Number(v ?? 0).toLocaleString(localeAtivo(), { style: 'currency', currency: 'BRL' });

export default function Caixa() {
    const { t } = useTranslation();
    const [caixas, setCaixas] = useState([]);
    const [loading, setLoading] = useState(false);
    const [totalRecords, setTotalRecords] = useState(0);
    const [lazyParams, setLazyParams] = useState({ first: 0, rows: 20, page: 0 });
    const [selectedCaixa, setSelectedCaixa] = useState(null);
    const [dialogVisible, setDialogVisible] = useState(false);
    const [movDlg, setMovDlg] = useState(false);
    const [histDlg, setHistDlg] = useState(false);
    const [movs, setMovs] = useState([]);
    const [movForm, setMovForm] = useState({ tipo: 'SUPRIMENTO', valor: null, observacao: '' });
    const [searchTerm, setSearchTerm] = useState('');
    const toast = useRef(null);

    useEffect(() => { loadCaixas(); }, [lazyParams]);

    const loadCaixas = async () => {
        setLoading(true);
        try {
            const data = await CaixaService.search(searchTerm, lazyParams.page, lazyParams.rows);
            const lista = Array.isArray(data) ? data : (data?.content ?? []);
            setCaixas(lista);
            setTotalRecords(lista.length);
        } catch (error) {
            toast.current?.show({ severity: 'error', summary: t('legacyUi.caixa.attention'), detail: t('legacyUi.caixa.loadError') });
        } finally {
            setLoading(false);
        }
    };

    const onPageChange = (event) => {
        setLazyParams({ first: event.first, rows: event.rows, page: event.page });
    };

    const openNew = () => {
        setSelectedCaixa({});
        setDialogVisible(true);
    };

    const hideDialog = () => setDialogVisible(false);

    const saveCaixa = async () => {
        try {
            const body = {
                nome: selectedCaixa.nome,
                saldo: Number(selectedCaixa.saldo || 0),
                status: selectedCaixa.status || 'ATIVO'
            };
            if (selectedCaixa.id) {
                await CaixaService.update(selectedCaixa.id, body);
            } else {
                await CaixaService.save(body);
            }
            toast.current?.show({ severity: 'success', summary: 'OK', detail: 'Caixa salvo', life: 2500 });
            setDialogVisible(false);
            loadCaixas();
        } catch (e) {
            toast.current?.show({ severity: 'error', summary: 'Erro', detail: e?.response?.data?.message || e.message, life: 4000 });
        }
    };

    const deleteCaixa = async (row) => {
        try {
            await CaixaService.delete(row.id);
            toast.current?.show({ severity: 'success', summary: 'Desativado', life: 2500 });
            loadCaixas();
        } catch (e) {
            toast.current?.show({ severity: 'error', summary: 'Erro', detail: e.message, life: 4000 });
        }
    };

    const abrirMov = (row, tipo) => {
        setSelectedCaixa(row);
        setMovForm({ tipo: tipo || 'SUPRIMENTO', valor: null, observacao: '' });
        setMovDlg(true);
    };

    const confirmarMov = async () => {
        if (!movForm.valor || movForm.valor <= 0) {
            toast.current?.show({ severity: 'warn', summary: 'Informe o valor', life: 3000 });
            return;
        }
        try {
            await CaixaService.movimentar(selectedCaixa.id, movForm.tipo, movForm.valor, movForm.observacao);
            toast.current?.show({ severity: 'success', summary: movForm.tipo + ' registrada', life: 2500 });
            setMovDlg(false);
            loadCaixas();
        } catch (e) {
            const m = e?.response?.data?.message || e?.response?.data?.errors?.[0]?.message || e.message;
            toast.current?.show({ severity: 'error', summary: 'Erro', detail: m, life: 4500 });
        }
    };

    const abrirHist = async (row) => {
        setSelectedCaixa(row);
        try {
            const data = await CaixaService.listarMovimentos(row.id);
            setMovs(Array.isArray(data) ? data : (data?.content ?? []));
            setHistDlg(true);
        } catch (e) {
            toast.current?.show({ severity: 'error', summary: 'Erro ao carregar histórico', life: 4000 });
        }
    };

    const actionBodyTemplate = (rowData) => (
        <div className="flex gap-1 flex-wrap">
            <Button icon="pi pi-plus" rounded text severity="success" tooltip="Suprimento" aria-label="Suprimento" disabled={rowData.status === 'INATIVO'} onClick={() => abrirMov(rowData, 'SUPRIMENTO')} />
            <Button icon="pi pi-minus" rounded text severity="warning" tooltip="Sangria" aria-label="Sangria" disabled={rowData.status === 'INATIVO'} onClick={() => abrirMov(rowData, 'SANGRIA')} />
            <Button icon="pi pi-list" rounded text tooltip="Histórico" aria-label="Histórico" onClick={() => abrirHist(rowData)} />
            <Button icon="pi pi-pencil" rounded outlined tooltip="Editar" aria-label="Editar" onClick={() => { setSelectedCaixa(rowData); setDialogVisible(true); }} />
            <Button icon="pi pi-trash" rounded outlined severity="danger" tooltip="Excluir" aria-label="Excluir" onClick={() => deleteCaixa(rowData)} />
        </div>
    );

    return (
        <div>
            <Toast ref={toast} />
            <div className="flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
                <span className="bc-muted">Cadastro, saldo, sangria e suprimento</span>
                <div className="flex gap-2">
                    <span className="p-input-icon-left">
                        <i className="pi pi-search" />
                        <InputText value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && loadCaixas()} placeholder="Buscar" />
                    </span>
                    <Button label={t('legacyUi.caixa.new')} icon="pi pi-plus" onClick={openNew} />
                </div>
            </div>

            <DataTable
                value={caixas}
                paginator
                first={lazyParams.first}
                rows={lazyParams.rows}
                totalRecords={totalRecords}
                lazy
                loading={loading}
                onPage={onPageChange}
                paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                currentPageReportTemplate="Mostrando {first} a {last} de {totalRecords} caixas"
                rowsPerPageOptions={[10, 20, 50, 100]}
                emptyMessage={t('legacyUi.caixa.empty')}
            >
                <Column field="id" header="ID" sortable style={{ width: '80px' }} />
                <Column field="nome" header={t('legacyUi.caixa.name')} sortable />
                <Column field="saldo" header={t('legacyUi.caixa.balance')} sortable body={(data) => fmt(data.saldo)} />
                <Column field="status" header={t('common.status')} sortable body={(r) => <Tag value={r.status} severity={r.status === 'ATIVO' ? 'success' : 'danger'} />} />
                <Column body={actionBodyTemplate} style={{ width: '14rem' }} />
            </DataTable>

            <Dialog visible={dialogVisible} style={{ width: '500px' }} header={t('legacyUi.caixa.title')} modal onHide={hideDialog}>
                <div className="flex flex-column gap-3">
                    <div className="field">
                        <label htmlFor="nome">Nome</label>
                        <InputText id="nome" value={selectedCaixa?.nome || ''} onChange={(e) => setSelectedCaixa({ ...selectedCaixa, nome: e.target.value })} className="w-full" />
                    </div>
                    {!selectedCaixa?.id && (
                        <div className="field">
                            <label htmlFor="saldo">Saldo Inicial</label>
                            <InputNumber id="saldo" value={selectedCaixa?.saldo} onValueChange={(e) => setSelectedCaixa({ ...selectedCaixa, saldo: e.value })} mode="currency" currency="BRL" locale={localeAtivo()} className="w-full" />
                        </div>
                    )}
                </div>
                <div className="flex justify-end gap-2 mt-4">
                    <Button label={t('common.cancel')} icon="pi pi-times" onClick={hideDialog} severity="secondary" />
                    <Button label={t('common.save')} icon="pi pi-check" onClick={saveCaixa} />
                </div>
            </Dialog>

            <Dialog visible={movDlg} style={{ width: '420px' }} header={`${movForm.tipo} — ${selectedCaixa?.nome || ''}`} modal onHide={() => setMovDlg(false)}>
                <div className="grid p-fluid">
                    <div className="col-12">
                        <label>Tipo</label>
                        <Dropdown value={movForm.tipo} options={[{ label: 'Suprimento', value: 'SUPRIMENTO' }, { label: 'Sangria', value: 'SANGRIA' }]}
                            onChange={(e) => setMovForm({ ...movForm, tipo: e.value })} />
                    </div>
                    <div className="col-12">
                        <label>Valor *</label>
                        <InputNumber value={movForm.valor} onValueChange={(e) => setMovForm({ ...movForm, valor: e.value })} mode="currency" currency="BRL" locale={localeAtivo()} min={0.01} />
                    </div>
                    <div className="col-12">
                        <label>Observação</label>
                        <InputTextarea rows={2} value={movForm.observacao} onChange={(e) => setMovForm({ ...movForm, observacao: e.target.value })} />
                    </div>
                    <div className="col-12"><small className="bc-muted">Saldo atual: {fmt(selectedCaixa?.saldo)}</small></div>
                </div>
                <div className="flex justify-end gap-2 mt-3">
                    <Button label="Cancelar" text severity="secondary" onClick={() => setMovDlg(false)} />
                    <Button label="Confirmar" icon="pi pi-check" severity={movForm.tipo === 'SANGRIA' ? 'warning' : 'success'} onClick={confirmarMov} />
                </div>
            </Dialog>

            <Dialog visible={histDlg} style={{ width: 'min(720px, 96vw)' }} header={`Histórico — ${selectedCaixa?.nome || ''}`} modal onHide={() => setHistDlg(false)}>
                <DataTable value={movs} emptyMessage="Sem movimentos" paginator rows={10} size="small">
                    <Column field="createdAt" header="Data" body={(r) => r.createdAt ? new Date(r.createdAt).toLocaleString(localeAtivo()) : '—'} />
                    <Column field="tipo" header="Tipo" body={(r) => <Tag value={r.tipo} severity={r.tipo === 'SANGRIA' ? 'warning' : 'success'} />} />
                    <Column field="valor" header="Valor" body={(r) => fmt(r.valor)} />
                    <Column field="saldoAnterior" header="Antes" body={(r) => fmt(r.saldoAnterior)} />
                    <Column field="saldoPosterior" header="Depois" body={(r) => fmt(r.saldoPosterior)} />
                    <Column field="observacao" header="Obs." />
                </DataTable>
            </Dialog>
        </div>
    );
}
