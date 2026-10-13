import React, { useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { MemorandoDialog } from './MemorandoDialog';
import { Card } from 'primereact/card';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { Dropdown } from 'primereact/dropdown';
import { Calendar } from 'primereact/calendar';
import ApiConfig, { apiFetch } from '../services/ApiConfig';
import { useAuth } from '../contexts/AuthContext';
import { NotificationService } from '../services/NotificationService';
import { downloadAuthenticated } from '../services/downloadService';
import { PrintButton } from './shared/PrintButton';
import { formatoData, localeAtivo } from './shared/LocaleData.js';

export const Relatorios = () => {
    const { user } = useAuth();
    const { t } = useTranslation();
    const [memorandoAberto, setMemorandoAberto] = useState(false);
    const tabelaRef = useRef(null);

    const [tipoRelatorio, setTipoRelatorio] = useState('VENDAS');
    const [dataInicio, setDataInicio] = useState(null);
    const [dataFim, setDataFim] = useState(null);
    const [dados, setDados] = useState([]);
    const [resumo, setResumo] = useState({});
    const [nomeRelatorio, setNomeRelatorio] = useState('');
    const [loading, setLoading] = useState(false);
    const [exportando, setExportando] = useState(false);

    const relatoriosOptions = [
        { label: 'Vendas', value: 'VENDAS' },
        { label: 'Financeiro', value: 'FINANCEIRO' },
        { label: 'Produção', value: 'PRODUCAO' },
        { label: 'Fiscal', value: 'FISCAL' },
        { label: 'DRE Gerencial', value: 'DRE' },
        { label: 'Fluxo de Caixa Projetado', value: 'FLUXO_CAIXA' },
    ];

    // O backend resolve o tenant pelo principal autenticado; download usa Bearer.
    const montarQuery = () => {
        const filtros = new URLSearchParams();
        if (dataInicio) filtros.append('dataInicio', dataInicio.toISOString().split('T')[0]);
        if (dataFim) filtros.append('dataFim', dataFim.toISOString().split('T')[0]);
        return filtros.toString();
    };

    const gerarRelatorio = async () => {
        setLoading(true);
        try {
            const response = await apiFetch(
                `${ApiConfig.BASE_URL}/api/relatorios/${tipoRelatorio}?${montarQuery()}`
            );
            if (!response.ok) throw new Error('Erro ao gerar relatório');

            const data = await response.json();
            setDados(data.dados || []);
            setResumo(data.resumo || {});
            setNomeRelatorio(data.nomeRelatorio || tipoRelatorio);
            NotificationService.showSuccess('Relatório gerado com sucesso!');
        } catch (err) {
            NotificationService.showError(err.message);
            setDados([]);
        } finally {
            setLoading(false);
        }
    };

    /**
     * Download autenticado. window.open() nao envia o header Authorization e o
     * backend responderia 401 — por isso o fetch + blob.
     */
    const exportarPdf = async () => {
        setExportando(true);
        try {
            await downloadAuthenticated(
                `${ApiConfig.BASE_URL}/api/relatorios/pdf/${tipoRelatorio}?${montarQuery()}`,
                `relatorio-${tipoRelatorio.toLowerCase()}.pdf`
            );
            NotificationService.showSuccess('PDF gerado com sucesso!');
        } catch (err) {
            NotificationService.showError(err.message);
        } finally {
            setExportando(false);
        }
    };

    return (
        <div className="relatorios-container">
            <Card title="Centro de Relatórios Gerenciais" className="relatorios-main-card">
                <Button label={t('memorando.title')} icon="pi pi-file" className="mb-3"
                    onClick={() => setMemorandoAberto(true)} />
                <MemorandoDialog visible={memorandoAberto} onHide={() => setMemorandoAberto(false)} />
                <div className="grid p-fluid mb-4">
                    <div className="col-12 md:col-4 field">
                        <label className="font-bold mb-2 block">Tipo de Relatório</label>
                        <Dropdown
                            value={tipoRelatorio}
                            options={relatoriosOptions}
                            optionLabel="label"
                            optionValue="value"
                            onChange={(e) => setTipoRelatorio(e.value)}
                        />
                    </div>
                    <div className="col-12 md:col-3 field">
                        <label className="font-bold mb-2 block">Data Início</label>
                        <Calendar value={dataInicio} onChange={(e) => setDataInicio(e.value)} showIcon dateFormat={formatoData()} />
                    </div>
                    <div className="col-12 md:col-3 field">
                        <label className="font-bold mb-2 block">Data Fim</label>
                        <Calendar value={dataFim} onChange={(e) => setDataFim(e.value)} showIcon dateFormat={formatoData()} />
                    </div>
                    <div className="col-12 md:col-2 field flex align-items-end">
                        <Button label="Gerar" icon="pi pi-sync" onClick={gerarRelatorio} loading={loading} className="p-button-success" />
                    </div>
                </div>

                {dados.length > 0 && (
                    <div className="results-section">
                        <div className="grid mb-4">
                            {Object.entries(resumo).map(([key, value]) => (
                                <div key={key} className="col-12 md:col-3">
                                    <Card className="resumo-card">
                                        <span className="resumo-label">
                                            {formatarRotulo(key)}
                                        </span>
                                        <div className="resumo-value">
                                            {formatarValor(key, value)}
                                        </div>
                                    </Card>
                                </div>
                            ))}
                        </div>

                        <div ref={tabelaRef}>
                            <DataTable
                                value={dados}
                                responsiveLayout="scroll"
                                stripedRows
                                showGridlines
                                className="p-datatable-sm"
                                paginator
                                rows={10}
                            >
                                {Object.keys(dados[0] || {}).map((col) => (
                                    <Column key={col} field={col} header={formatarRotulo(col)} sortable />
                                ))}
                            </DataTable>
                        </div>

                        <div className="flex justify-content-end mt-3 gap-2 flex-wrap">
                            <PrintButton
                                label="Imprimir"
                                alvoRef={tabelaRef}
                                titulo={nomeRelatorio}
                            />
                            <Button
                                label="Exportar PDF"
                                icon="pi pi-file-pdf"
                                className="p-button-secondary"
                                onClick={exportarPdf}
                                loading={exportando}
                            />
                        </div>
                    </div>
                )}

                {dados.length === 0 && !loading && (
                    <div className="text-center text-muted p-4">
                        Selecione o tipo de relatório e clique em <strong>Gerar</strong>.
                    </div>
                )}
            </Card>
        </div>
    );
};

const formatarRotulo = (chave) =>
    String(chave)
        .replace(/_/g, ' ')
        .toUpperCase();

const formatarValor = (chave, valor) => {
    const numerico = Number(valor);
    if (Number.isNaN(numerico)) return String(valor ?? '-');
    // Contagens de pedidos/notas sao inteiros, nao valores em reais
    if (/quantidade/i.test(chave)) return numerico.toLocaleString(localeAtivo());
    return numerico.toLocaleString(localeAtivo(), { style: 'currency', currency: 'BRL' });
};

export default Relatorios;
