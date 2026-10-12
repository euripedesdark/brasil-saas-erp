import React, { useState, useEffect, useRef } from 'react';
import { Dialog } from 'primereact/dialog';
import { Button } from 'primereact/button';
import { InputTextarea } from 'primereact/inputtextarea';
import { Tag } from 'primereact/tag';
import { ProgressBar } from 'primereact/progressbar';
import { Message } from 'primereact/message';
import { useNavigate } from 'react-router-dom';
import { AssistenteService } from '../../services/AssistenteService';
import './AssistenteCopilotoDialog.css';

/**
 * Caixa pequena do Copiloto IA.
 * - [ Consultar ] → ensina onde fica e como fazer
 * - [ Executar ]  → mostra resumo do pedido/ordem e pede confirmação antes de emitir
 */
export const AssistenteCopilotoDialog = ({ visible, onHide }) => {
    const navigate = useNavigate();
    const [texto, setTexto] = useState('');
    const [carregando, setCarregando] = useState(false);
    const [resultado, setResultado] = useState(null);
    const [erro, setErro] = useState(null);
    const [ouvindo, setOuvindo] = useState(false);
    const recognitionRef = useRef(null);

    const empresaId = (() => {
        try {
            return JSON.parse(localStorage.getItem('empresaSelecionada') || '{}')?.id;
        } catch {
            return null;
        }
    })();

    useEffect(() => {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (SpeechRecognition) {
            const recognition = new SpeechRecognition();
            recognition.continuous = false;
            recognition.interimResults = false;
            recognition.lang = 'pt-BR';
            recognition.onstart = () => setOuvindo(true);
            recognition.onend = () => setOuvindo(false);
            recognition.onerror = () => setOuvindo(false);
            recognition.onresult = (event) => {
                const transcricao = event.results[0][0].transcript;
                setTexto((prev) => (prev ? prev + ' ' + transcricao : transcricao));
            };
            recognitionRef.current = recognition;
        }
    }, []);

    useEffect(() => {
        if (visible) {
            setResultado(null);
            setErro(null);
        }
    }, [visible]);

    const toggleVoz = () => {
        if (!recognitionRef.current) {
            alert('Reconhecimento de voz não suportado. Use Chrome ou Edge.');
            return;
        }
        if (ouvindo) {
            recognitionRef.current.stop();
        } else {
            recognitionRef.current.start();
        }
    };

    const enviarComando = async (modo, confirmar = false) => {
        const conteudo = texto.trim();
        if (!conteudo || carregando) return;

        setCarregando(true);
        setErro(null);
        if (!confirmar) {
            setResultado(null);
        }

        try {
            const res = await AssistenteService.executarComando(empresaId, conteudo, modo, confirmar);
            setResultado(res);
        } catch (e) {
            setErro(e.message || 'Falha ao processar o comando.');
        } finally {
            setCarregando(false);
        }
    };

    const confirmarExecucao = () => {
        // Segunda chamada: confirma e executa de verdade
        enviarComando('ACAO', true);
    };

    const cancelarConfirmacao = () => {
        setResultado(null);
    };

    const irParaTela = (rota) => {
        if (rota) {
            onHide();
            navigate(rota);
        }
    };

    const getStatusSeverity = (status) => {
        switch (status) {
            case 'SUCESSO': return 'success';
            case 'AVISO': return 'warning';
            case 'ERRO': return 'danger';
            case 'CONFIRMACAO': return 'info';
            default: return 'info';
        }
    };

    const precisaConfirmacao = resultado?.status === 'CONFIRMACAO';

    return (
        <Dialog
            header={
                <div className="flex align-items-center gap-2">
                    <i className="pi pi-sparkles text-primary" />
                    <span className="font-semibold">Copiloto ERP</span>
                </div>
            }
            visible={visible}
            onHide={onHide}
            className="assistente-copiloto-dialog"
            style={{ width: '92vw', maxWidth: '420px' }}
            breakpoints={{ '641px': '96vw' }}
            modal
            dismissableMask
            draggable={false}
        >
            <div className="copiloto-input-container">
                <InputTextarea
                    value={texto}
                    onChange={(e) => {
                        setTexto(e.target.value);
                        setResultado(null);
                        setErro(null);
                    }}
                    placeholder="Ex: emitir nota do pedido 2"
                    className="copiloto-textarea"
                    autoResize
                    rows={2}
                    disabled={carregando}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                            e.preventDefault();
                            enviarComando('DUVIDA');
                        }
                    }}
                />
                <Button
                    icon={ouvindo ? 'pi pi-microphone text-red-500' : 'pi pi-microphone'}
                    className={`p-button-rounded p-button-text p-button-sm copiloto-mic-btn ${ouvindo ? 'copiloto-mic-active' : ''}`}
                    onClick={toggleVoz}
                    tooltip={ouvindo ? 'Gravando...' : 'Falar'}
                    tooltipOptions={{ position: 'top' }}
                    type="button"
                />
            </div>

            <div className="copiloto-botoes-acao">
                <Button
                    label="Consultar"
                    icon="pi pi-search"
                    className="p-button-outlined p-button-info p-button-sm"
                    onClick={() => enviarComando('DUVIDA')}
                    disabled={carregando || !texto.trim()}
                    tooltip="Ensina onde fica e como fazer"
                />
                <Button
                    label="Executar"
                    icon="pi pi-play"
                    className="p-button-success p-button-sm"
                    onClick={() => enviarComando('ACAO', false)}
                    disabled={carregando || !texto.trim()}
                    tooltip="Prepara a ação e pede confirmação antes de emitir"
                />
            </div>

            <div className="copiloto-exemplos">
                <span
                    className="copiloto-exemplo-chip"
                    onClick={() => setTexto('emitir nota fiscal do pedido 2')}
                >
                    emitir nota do pedido 2
                </span>
                <span
                    className="copiloto-exemplo-chip"
                    onClick={() => setTexto('onde emito nota fiscal')}
                >
                    onde emito nota
                </span>
                <span
                    className="copiloto-exemplo-chip"
                    onClick={() => setTexto('status do pedido 2')}
                >
                    status do pedido 2
                </span>
            </div>

            {carregando && (
                <div className="mt-2">
                    <ProgressBar mode="indeterminate" style={{ height: '4px' }} />
                    <p className="text-center text-xs text-500 mt-1">
                        {precisaConfirmacao ? 'Emitindo...' : 'Processando...'}
                    </p>
                </div>
            )}

            {erro && (
                <Message severity="error" text={erro} className="w-full mt-2" />
            )}

            {resultado && !carregando && (
                <div className={`copiloto-resultado-card ${precisaConfirmacao ? 'copiloto-confirmacao' : ''}`}>
                    <div className="copiloto-resultado-header">
                        <Tag
                            value={
                                precisaConfirmacao
                                    ? 'Confirmação'
                                    : resultado.modo === 'ACAO'
                                      ? 'Executado'
                                      : 'Consulta'
                            }
                            severity={getStatusSeverity(resultado.status)}
                        />
                        <span className="text-xs text-500">{resultado.status}</span>
                    </div>

                    <h4 className="copiloto-resultado-titulo">{resultado.titulo}</h4>

                    {resultado.caminhoMenu && !precisaConfirmacao && (
                        <div className="copiloto-caminho-menu">
                            <i className="pi pi-compass text-primary" />
                            <span>{resultado.caminhoMenu}</span>
                        </div>
                    )}

                    <p className="copiloto-resultado-msg">{resultado.mensagem}</p>

                    {precisaConfirmacao ? (
                        <div className="copiloto-confirm-botoes">
                            <Button
                                label="Cancelar"
                                icon="pi pi-times"
                                className="p-button-outlined p-button-secondary p-button-sm"
                                onClick={cancelarConfirmacao}
                            />
                            <Button
                                label="Confirmar emissão"
                                icon="pi pi-check"
                                className="p-button-success p-button-sm"
                                onClick={confirmarExecucao}
                            />
                        </div>
                    ) : (
                        resultado.rota && (
                            <Button
                                label={resultado.rotuloBotao || 'Ir para a tela'}
                                icon="pi pi-arrow-right"
                                className="p-button-sm copiloto-navegacao-btn p-button-primary"
                                onClick={() => irParaTela(resultado.rota)}
                            />
                        )
                    )}
                </div>
            )}
        </Dialog>
    );
};
