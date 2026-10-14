import React, { useState, useEffect, useRef } from 'react';
import { Card } from 'primereact/card';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { Dialog } from 'primereact/dialog';
import { InputNumber } from 'primereact/inputnumber';
import { Dropdown } from 'primereact/dropdown';
import { Calendar } from 'primereact/calendar';
import { InputText } from 'primereact/inputtext';
import { Toast } from 'primereact/toast';
import { Tag } from 'primereact/tag';
import { Message } from 'primereact/message';
import TituloService from '../../services/TituloService';
import { useAuth } from '../../contexts/AuthContext';
import axios from 'axios';
import ApiConfig from '../../services/ApiConfig';
import { useTranslation } from 'react-i18next';
import { formatoData, localeAtivo } from '../shared/LocaleData.js';

const temPermissao = (user, perm) =>
    Array.isArray(user?.authorities) && user.authorities.some(a => (a?.authority ?? a) === perm);

/** tipo no back: R = receber, P = pagar */

const STATUS_OPTS = [
  { label: 'Abertos', value: 'ABERTO' },
  { label: 'Parciais', value: 'PARCIAL' },
  { label: 'Baixados', value: 'BAIXADO' },
  { label: 'Cancelados', value: 'CANCELADO' },
  { label: 'Todos', value: '' },
];


export const Titulo = () => {
    const { t } = useTranslation();
    const toast = useRef(null);
    const [titulos, setTitulos] = useState([]);
    const [loading, setLoading] = useState(false);
    const [statusFiltro, setStatusFiltro] = useState(null);

    const [parcelas, setParcelas] = useState([]);
    const [parcelasVisible, setParcelasVisible] = useState(false);
    const [tituloAtual, setTituloAtual] = useState(null);

    const [baixaVisible, setBaixaVisible] = useState(false);
    const [baixaForm, setBaixaForm] = useState({
        parcelaId: null,
        contaBancariaId: null,
        tipoPagamentoId: null,
        dataBaixa: new Date(),
        valorBaixa: 0,
        valorDesconto: 0,
        valorJuro: 0,
        valorMulta: 0,
        observacao: '',
    });
    const [error, setError] = useState('');
    const [contas, setContas] = useState([]);
    const [tiposPagamento, setTiposPagamento] = useState([]);

    const { user } = useAuth();
    const podeSolicitarAprovacao = temPermissao(user, 'financeiro:titulo:escrita');
    const podeVerAprovacoes = temPermissao(user, 'financeiro:titulo:aprovacao-leitura');

    const [aprVisible, setAprVisible] = useState(false);
    const [aprForm, setAprForm] = useState({ niveis: 1, usuarioAprovadorId: null, observacao: '' });
    const [aprError, setAprError] = useState('');
    const [trilhaVisible, setTrilhaVisible] = useState(false);
    const [trilha, setTrilha] = useState([]);
    const NIVEIS_OPTS = [1, 2, 3, 4, 5].map(n => ({ label: String(n), value: n }));

    useEffect(() => {
        fetchTitulos();
        Promise.all([
            axios.get(`${ApiConfig.BASE_URL || ''}/api/financeiro/contas-bancarias`, { withCredentials: true }),
            axios.get(`${ApiConfig.BASE_URL || ''}/api/financeiro/tipos-pagamento`, { withCredentials: true }),
        ]).then(([a,b]) => {
            const unwrap = r => Array.isArray(r?.data?.data) ? r.data.data : (Array.isArray(r?.data) ? r.data : []);
            setContas(unwrap(a)); setTiposPagamento(unwrap(b));
        }).catch(err => console.warn(t('legacyUi.tituloLegacy.loadOptionsError'), err));
    }, [statusFiltro]);

    const fetchTitulos = async () => {
        setLoading(true);
        try {
            const res = await TituloService.listar(statusFiltro || undefined);
            const data = res?.data?.data ?? res?.data ?? [];
            setTitulos(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error(err);
            toast.current?.show({
                severity: 'error',
                summary: t('common.error'),
                detail: t('errors.load'),
                life: 3000,
            });
            setTitulos([]);
        } finally {
            setLoading(false);
        }
    };

    const verParcelas = async (row) => {
        setTituloAtual(row);
        setLoading(true);
        try {
            const res = await TituloService.parcelas(row.id);
            const data = res?.data?.data ?? res?.data ?? [];
            setParcelas(Array.isArray(data) ? data : []);
            setParcelasVisible(true);
        } catch (err) {
            toast.current?.show({
                severity: 'error',
                summary: t('common.error'),
                detail: t('errors.load'),
                life: 3000,
            });
        } finally {
            setLoading(false);
        }
    };

    const abrirBaixa = async (row) => {
        setTituloAtual(row);
        try {
            const res = await TituloService.parcelas(row.id);
            const ps = (res?.data?.data ?? res?.data ?? []).filter(p => p.status === 'ABERTO' && Number(p.valorSaldo) > 0);
            setParcelas(ps);
            setBaixaForm({
                parcelaId: ps.length === 1 ? ps[0].id : null,
                contaBancariaId: null, tipoPagamentoId: null, dataBaixa: new Date(),
                valorBaixa: ps.length === 1 ? Number(ps[0].valorSaldo || 0) : Number(row.valorSaldo ?? row.valorOriginal ?? 0),
                valorDesconto: 0, valorJuro: 0, valorMulta: 0, observacao: '',
            });
        } catch (err) {
            setParcelas([]);
            setBaixaForm({
            parcelaId: null,
            contaBancariaId: null,
            tipoPagamentoId: null,
            dataBaixa: new Date(),
            valorBaixa: Number(row.valorSaldo ?? row.valorOriginal ?? 0),
            valorDesconto: 0,
            valorJuro: 0,
            valorMulta: 0,
            observacao: '',
            });
        }
        setError('');
        setBaixaVisible(true);
    };

    const confirmarBaixa = async () => {
        setError('');
        if (!baixaForm.valorBaixa || baixaForm.valorBaixa <= 0) {
                setError(t('common.required'));
            return;
        }
        if (parcelas.length > 1 && !baixaForm.parcelaId) {
            setError(t('common.required'));
            return;
        }
        setLoading(true);
        try {
            const body = {
                parcelaId: baixaForm.parcelaId || null,
                contaBancariaId: baixaForm.contaBancariaId || null,
                tipoPagamentoId: baixaForm.tipoPagamentoId || null,
                dataBaixa: baixaForm.dataBaixa
                    ? new Date(baixaForm.dataBaixa).toISOString().slice(0, 10)
                    : new Date().toISOString().slice(0, 10),
                valorBaixa: baixaForm.valorBaixa,
                valorDesconto: baixaForm.valorDesconto || 0,
                valorJuro: baixaForm.valorJuro || 0,
                valorMulta: baixaForm.valorMulta || 0,
                observacao: baixaForm.observacao || null,
            };
            await TituloService.baixar(tituloAtual.id, body);
            toast.current?.show({
                severity: 'success',
                summary: t('messages.success'),
                detail: t('messages.updated'),
                life: 3000,
            });
            setBaixaVisible(false);
            fetchTitulos();
        } catch (err) {
            const msg =
                err?.response?.data?.message ||
                err?.response?.data?.error ||
                t('legacyUi.tituloLegacy.settleError');
            setError(msg);
            toast.current?.show({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 });
        } finally {
            setLoading(false);
        }
    };

    const moeda = (v) =>
        (v == null ? 0 : Number(v)).toLocaleString(localeAtivo(), { style: 'currency', currency: 'BRL' });

    const abrirAprovacao = (row) => {
        setTituloAtual(row);
        setAprForm({ niveis: 1, usuarioAprovadorId: null, observacao: '' });
        setAprError('');
        setAprVisible(true);
    };

    const confirmarAprovacaoSolicitacao = async () => {
        setAprError('');
        if (!aprForm.niveis || aprForm.niveis < 1 || aprForm.niveis > 10) {
            setAprError(t('legacyUi.tituloLegacy.approvalLevels'));
            return;
        }
        setLoading(true);
        try {
            await TituloService.solicitarAprovacao(tituloAtual.id, {
                niveis: aprForm.niveis,
                usuarioAprovadorId: aprForm.usuarioAprovadorId || null,
                observacao: aprForm.observacao || null,
            });
            toast.current?.show({
                severity: 'success',
                summary: t('messages.success'),
                detail: t('messages.updated'),
                life: 3000,
            });
            setAprVisible(false);
            fetchTitulos();
        } catch (err) {
            const msg =
                err?.response?.data?.message ||
                err?.response?.data?.error ||
                t('legacyUi.tituloLegacy.approvalError');
            setAprError(msg);
        } finally {
            setLoading(false);
        }
    };

    const cobrarComStripe = async (row, invoice = false) => {
        setLoading(true);
        try {
            const res = invoice
                ? await TituloService.criarStripeInvoice(row.id)
                : await TituloService.criarStripeCheckout(row.id);
            const data = res?.data?.data ?? res?.data ?? {};
            const url = invoice ? data.hostedInvoiceUrl : data.url;
            if (!url) throw new Error('Stripe não retornou uma URL de cobrança');
            window.open(url, '_blank', 'noopener,noreferrer');
            toast.current?.show({
                severity: 'success',
                summary: 'Stripe',
                detail: invoice ? 'Invoice Stripe criada.' : 'Checkout Stripe criado.',
                life: 3000,
            });
        } catch (err) {
            const msg = err?.response?.data?.message || err?.response?.data?.error || 'Falha ao criar cobrança Stripe.';
            toast.current?.show({ severity: 'error', summary: t('common.error'), detail: msg, life: 5000 });
        } finally {
            setLoading(false);
        }
    };

    const verTrilha = async (row) => {
        setTituloAtual(row);
        setLoading(true);
        try {
            const res = await TituloService.aprovacoesDoTitulo(row.id);
            const data = res?.data?.data ?? res?.data ?? [];
            setTrilha(Array.isArray(data) ? data : []);
            setTrilhaVisible(true);
        } catch (err) {
            toast.current?.show({
                severity: 'error',
                summary: t('common.error'),
                detail: t('errors.load'),
                life: 3000,
            });
        } finally {
            setLoading(false);
        }
    };

    const dataBr = (d) => (d ? new Date(d).toLocaleDateString(localeAtivo()) : '');

    const tipoBody = (row) => {
        const t = row.tipo;
        if (t === 'R') return <Tag value="Receber" severity="success" />;
        if (t === 'P') return <Tag value="Pagar" severity="warning" />;
        return <Tag value={t || '—'} />;
    };

    const statusBody = (row) => {
        const map = {
            ABERTO: 'warning',
            BAIXADO: 'success',
            PAGO: 'success',
            CANCELADO: 'danger',
            PENDENTE_APROVACAO: 'danger',
        };
        return <Tag value={row.status} severity={map[row.status] || 'info'} />;
    };

    const acoes = (row) => (
        <div className="flex gap-1">
            <Button
                icon="pi pi-list"
                className="p-button-text p-button-sm"
                tooltip={t('common.details')} aria-label={t('common.details')}
                onClick={() => verParcelas(row)}
            />
            {(row.status === 'ABERTO' || Number(row.valorSaldo) > 0) && row.status !== 'PENDENTE_APROVACAO' && podeSolicitarAprovacao && (
                <Button
                    icon="pi pi-send"
                    className="p-button-text p-button-info p-button-sm"
                    tooltip={t('common.send')} aria-label={t('common.send')}
                    onClick={() => abrirAprovacao(row)}
                />
            )}
            {podeVerAprovacoes && (
                <Button
                    icon="pi pi-verified"
                    className="p-button-text p-button-sm"
                    tooltip={t('common.details')} aria-label={t('common.details')}
                    onClick={() => verTrilha(row)}
                />
            )}
            {(row.tipo === 'R' && (row.status === 'ABERTO' || Number(row.valorSaldo) > 0) && podeSolicitarAprovacao) && (
                <>
                    <Button
                        icon="pi pi-credit-card"
                        className="p-button-text p-button-sm"
                        tooltip="Cobrar com Stripe Checkout"
                        onClick={() => cobrarComStripe(row, false)}
                        disabled={row.status === 'PENDENTE_APROVACAO'}
                    />
                    <Button
                        icon="pi pi-file"
                        className="p-button-text p-button-sm"
                        tooltip="Criar Invoice Stripe"
                        onClick={() => cobrarComStripe(row, true)}
                        disabled={row.status === 'PENDENTE_APROVACAO'}
                    />
                </>
            )}
            {(row.status === 'ABERTO' || Number(row.valorSaldo) > 0) && (
                <Button
                    icon="pi pi-check"
                    className="p-button-text p-button-success p-button-sm"
                    tooltip={t('common.confirm')} aria-label={t('common.confirm')}
                    disabled={row.status === 'PENDENTE_APROVACAO'}
                    onClick={() => abrirBaixa(row)}
                />
            )}
        </div>
    );

    return (
        <div>
            <Toast ref={toast} />
            <Card title={t('menu.titles')}>
                <p className="text-color-secondary mb-3">
                    Titulos gerados ao faturar venda (R) ou receber compra (P). Use Baixar para liquidar.
                </p>

                <div className="flex gap-2 align-items-end mb-3">
                    <div className="field m-0">
                        <label className="font-bold block mb-1">{t('common.status')}</label>
                        <Dropdown
                            value={statusFiltro}
                            options={STATUS_OPTS}
                            onChange={(e) => setStatusFiltro(e.value)}
                            optionLabel="label"
                            optionValue="value"
                            style={{ minWidth: '140px' }}
                        />
                    </div>
                    <Button label={t('common.refresh')} icon="pi pi-refresh" onClick={fetchTitulos} />
                </div>

                <DataTable
                    value={titulos}
                    loading={loading}
                    paginator
                    rows={12}
                    rowsPerPageOptions={[12, 25, 50]}
                    emptyMessage={t('legacyUi.tituloLegacy.empty')}
                    size="small"
                >
                    <Column field="numeroDocumento" header={t('common.code')} style={{ width: '130px' }} />
                    <Column field="descricao" header={t('common.description')} />
                    <Column field="tipo" header={t('common.type')} body={tipoBody} style={{ width: '100px' }} />
                    <Column field="pessoaId" header={t('legacyUi.tituloLegacy.person')} style={{ width: '90px' }} />
                    <Column
                        field="valorOriginal"
                        header={t('legacyUi.tituloLegacy.original')}
                        body={(r) => moeda(r.valorOriginal)}
                        style={{ width: '120px' }}
                    />
                    <Column
                        field="valorSaldo"
                        header={t('legacyUi.tituloLegacy.balance')}
                        body={(r) => moeda(r.valorSaldo)}
                        style={{ width: '120px' }}
                    />
                    <Column
                        field="dataVencimento"
                        header={t('legacyUi.tituloLegacy.due')}
                        body={(r) => dataBr(r.dataVencimento)}
                        style={{ width: '110px' }}
                    />
                    <Column field="status" header="Status" body={statusBody} style={{ width: '110px' }} />
                    <Column body={acoes} style={{ width: '100px' }} />
                </DataTable>
            </Card>

            <Dialog
                header={tituloAtual ? `${t('legacyUi.tituloLegacy.installments')} — ${tituloAtual.numeroDocumento || tituloAtual.id}` : 'Parcelas'}
                visible={parcelasVisible}
                style={{ width: '640px' }}
                onHide={() => setParcelasVisible(false)}
            >
                <DataTable value={parcelas} emptyMessage="Sem parcelas" size="small">
                    <Column field="numeroParcela" header="#" style={{ width: '60px' }} />
                    <Column
                        field="valorParcela"
                        header="Valor"
                        body={(r) => moeda(r.valorParcela)}
                    />
                    <Column field="valorSaldo" header={t('legacyUi.tituloLegacy.balance')} body={(r) => moeda(r.valorSaldo)} />
                    <Column
                        field="dataVencimento"
                        header={t('legacyUi.tituloLegacy.due')}
                        body={(r) => dataBr(r.dataVencimento)}
                    />
                    <Column field="status" header="Status" />
                </DataTable>
            </Dialog>

            <Dialog
                header={tituloAtual ? `Baixar titulo #${tituloAtual.id}` : 'Baixar'}
                visible={baixaVisible}
                style={{ width: '480px' }}
                onHide={() => setBaixaVisible(false)}
                footer={
                    <div>
                        <Button
                            label="Cancelar"
                            className="p-button-text"
                            onClick={() => setBaixaVisible(false)}
                        />
                        <Button
                            label="Confirmar baixa"
                            icon="pi pi-check"
                            onClick={confirmarBaixa}
                            loading={loading}
                        />
                    </div>
                }
            >
                {error && <Message severity="error" text={error} className="w-full mb-3" />}
                <div className="p-fluid grid">
                    {parcelas.length > 0 && <div className="col-12 field"><label>Parcela</label><Dropdown value={baixaForm.parcelaId} options={parcelas} optionLabel="numeroParcela" optionValue="id" placeholder="Selecione a parcela" onChange={e => { const p = parcelas.find(x => x.id === e.value); setBaixaForm({ ...baixaForm, parcelaId: e.value, valorBaixa: p ? Number(p.valorSaldo || 0) : baixaForm.valorBaixa }); }} /></div>}
                    <div className="col-12 field">
                        <label className="font-bold">Valor da baixa *</label>
                        <InputNumber
                            value={baixaForm.valorBaixa}
                            onValueChange={(e) =>
                                setBaixaForm({ ...baixaForm, valorBaixa: e.value })
                            }
                            mode="currency"
                            currency="BRL"
                            locale={localeAtivo()}
                        />
                    </div>
                    <div className="col-12 field">
                        <label className="font-bold">Data da baixa</label>
                        <Calendar
                            value={baixaForm.dataBaixa}
                            onChange={(e) => setBaixaForm({ ...baixaForm, dataBaixa: e.value })}
                            dateFormat={formatoData()}
                            showIcon
                        />
                    </div>
                    <div className="col-6 field">
                        <label>Desconto</label>
                        <InputNumber
                            value={baixaForm.valorDesconto}
                            onValueChange={(e) =>
                                setBaixaForm({ ...baixaForm, valorDesconto: e.value })
                            }
                            mode="currency"
                            currency="BRL"
                            locale={localeAtivo()}
                        />
                    </div>
                    <div className="col-6 field">
                        <label>Juros</label>
                        <InputNumber
                            value={baixaForm.valorJuro}
                            onValueChange={(e) =>
                                setBaixaForm({ ...baixaForm, valorJuro: e.value })
                            }
                            mode="currency"
                            currency="BRL"
                            locale={localeAtivo()}
                        />
                    </div>
                    <div className="col-12 field"><label>Conta bancária</label><Dropdown value={baixaForm.contaBancariaId} options={contas} optionLabel="banco" optionValue="id" placeholder="Selecione a conta" showClear onChange={e => setBaixaForm({ ...baixaForm, contaBancariaId: e.value })} /></div>
                    <div className="col-12 field"><label>Tipo de pagamento</label><Dropdown value={baixaForm.tipoPagamentoId} options={tiposPagamento} optionLabel="descricao" optionValue="id" placeholder="Selecione o meio" showClear onChange={e => setBaixaForm({ ...baixaForm, tipoPagamentoId: e.value })} /></div>
                    <div className="col-12 field">
                        <label>Observacao</label>
                        <InputText
                            value={baixaForm.observacao}
                            onChange={(e) =>
                                setBaixaForm({ ...baixaForm, observacao: e.target.value })
                            }
                        />
                    </div>
                </div>
            </Dialog>

            <Dialog
                header={tituloAtual ? `Solicitar aprovacao — ${tituloAtual.numeroDocumento || 'titulo #' + tituloAtual.id}` : 'Solicitar aprovacao'}
                visible={aprVisible}
                style={{ width: '460px' }}
                onHide={() => setAprVisible(false)}
                footer={
                    <div>
                        <Button label="Cancelar" className="p-button-text" onClick={() => setAprVisible(false)} />
                        <Button label="Enviar para aprovacao" icon="pi pi-send" onClick={confirmarAprovacaoSolicitacao} loading={loading} />
                    </div>
                }
            >
                {aprError && <Message severity="error" text={aprError} className="w-full mb-3" />}
                <div className="p-fluid">
                    <div className="field">
                        <label className="font-bold">Niveis de aprovacao *</label>
                        <Dropdown value={aprForm.niveis} options={NIVEIS_OPTS} onChange={(e) => setAprForm({ ...aprForm, niveis: e.value })} />
                    </div>
                    <div className="field">
                        <label className="font-bold">ID do aprovador do nivel 1 (opcional)</label>
                        <InputNumber
                            value={aprForm.usuarioAprovadorId}
                            onValueChange={(e) => setAprForm({ ...aprForm, usuarioAprovadorId: e.value })}
                            useGrouping={false}
                            placeholder="Deixe vazio para qualquer perfil com permissao aprovar"
                        />
                    </div>
                    <div className="field">
                        <label>Observacao</label>
                        <InputText value={aprForm.observacao} onChange={(e) => setAprForm({ ...aprForm, observacao: e.target.value })} />
                    </div>
                </div>
            </Dialog>

            <Dialog
                header={tituloAtual ? `Trilha de aprovacao — ${tituloAtual.numeroDocumento || 'titulo #' + tituloAtual.id}` : 'Trilha de aprovacao'}
                visible={trilhaVisible}
                style={{ width: '760px' }}
                onHide={() => setTrilhaVisible(false)}
            >
                <DataTable value={trilha} emptyMessage="Sem registros de aprovacao para este titulo" size="small">
                    <Column field="nivel" header="Nivel" style={{ width: '70px' }} />
                    <Column field="status" header="Status" body={(r) => (
                        <Tag value={r.status} severity={r.status === 'APROVADO' ? 'success' : r.status === 'REJEITADO' ? 'danger' : r.status === 'PENDENTE' ? 'warning' : 'info'} />
                    )} style={{ width: '150px' }} />
                    <Column field="solicitanteNome" header="Solicitante" body={(r) => r.solicitanteNome || '—'} style={{ width: '150px' }} />
                    <Column field="aprovadorNome" header="Aprovador" body={(r) => r.aprovadorNome || '—'} style={{ width: '150px' }} />
                    <Column field="dataSolicitacao" header="Solicitado em" body={(r) => (r.dataSolicitacao ? new Date(r.dataSolicitacao).toLocaleString(localeAtivo()) : '—')} style={{ width: '160px' }} />
                    <Column field="dataAprovacao" header="Decidido em" body={(r) => (r.dataAprovacao ? new Date(r.dataAprovacao).toLocaleString(localeAtivo()) : r.dataRejeicao ? new Date(r.dataRejeicao).toLocaleString(localeAtivo()) : '—')} style={{ width: '160px' }} />
                    <Column field="observacao" header="Observacao" body={(r) => r.observacao || '—'} />
                </DataTable>
            </Dialog>
        </div>
    );
};

export default Titulo;
