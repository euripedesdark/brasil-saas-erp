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
 * Modal Animado do Copiloto IA (PrimeReact).
 * Abre via botão no topo direito ou canto inferior direito.
 * Permite ao usuário digitar ou falar por voz, escolhendo entre:
 * - [ Dúvida ]: Encontra a tela, o caminho no menu e explica o passo a passo.
 * - [ Ação ]: Executa ou valida a operação solicitada (ex: emissão de nota de produto ou serviço).
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

    // Configuração do reconhecimento de voz via Web Speech API (Nativo do navegador)
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

    const toggleVoz = () => {
        if (!recognitionRef.current) {
            alert('Reconhecimento de voz não suportado neste navegador. Use o Google Chrome ou Edge.');
            return;
        }
        if (ouvindo) {
            recognitionRef.current.stop();
        } else {
            recognitionRef.current.start();
        }
    };

    const enviarComando = async (modo) => {
        const conteudo = texto.trim();
        if (!conteudo || carregando) return;

        setCarregando(true);
        setErro(null);

        try {
            const res = await AssistenteService.executarComando(empresaId, conteudo, modo);
            setResultado(res);
        } catch (e) {
            setErro(e.message || 'Falha ao processar comando com o assistente.');
        } finally {
            setCarregando(false);
        }
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
            default: return 'info';
        }
    };

    return (
        <Dialog
            header={
                <div className="flex align-items-center gap-2">
                    <i className="pi pi-sparkles text-primary text-xl" />
                    <span className="font-bold text-lg">Copiloto Inteligente ERP</span>
                </div>
            }
            visible={visible}
            onHide={onHide}
            className="assistente-copiloto-dialog"
            style={{ width: '92vw', maxWidth: '640px' }}
            breakpoints={{ '960px': '85vw', '641px': '96vw' }}
            modal
            dismissableMask
        >
            <div className="copiloto-input-container">
                <InputTextarea
                    value={texto}
                    onChange={(e) => setTexto(e.target.value)}
                    placeholder="O que você precisa? Ex: 'Onde emito nota fiscal?' ou 'Emita a nota do pedido 54'..."
                    className="copiloto-textarea"
                    autoResize
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
                    className={`p-button-rounded p-button-text copiloto-mic-btn ${ouvindo ? 'copiloto-mic-active' : ''}`}
                    onClick={toggleVoz}
                    tooltip={ouvindo ? 'Gravando... Fale agora' : 'Falar por voz'}
                    tooltipOptions={{ position: 'top' }}
                    type="button"
                />
            </div>

            <div className="copiloto-botoes-acao">
                <Button
                    label="Dúvida"
                    icon="pi pi-question-circle"
                    className="p-button-outlined p-button-info"
                    onClick={() => enviarComando('DUVIDA')}
                    disabled={carregando || !texto.trim()}
                    tooltip="Buscar onde fica a tela, caminho no menu e instruções"
                />
                <Button
                    label="Ação"
                    icon="pi pi-bolt"
                    className="p-button-success"
                    onClick={() => enviarComando('ACAO')}
                    disabled={carregando || !texto.trim()}
                    tooltip="Executar comando (ex: emitir nota fiscal do pedido X)"
                />
            </div>

            <div className="copiloto-exemplos">
                <span>Exemplos rápidos:</span>
                <span
                    className="copiloto-exemplo-chip"
                    onClick={() => setTexto('Onde emito nota fiscal?')}
                >
                    ❓ Onde emito nota?
                </span>
                <span
                    className="copiloto-exemplo-chip"
                    onClick={() => setTexto('Como cadastrar um cliente?')}
                >
                    ❓ Cadastrar cliente
                </span>
                <span
                    className="copiloto-exemplo-chip"
                    onClick={() => setTexto('Emita a nota fiscal do pedido 54')}
                >
                    ⚡ Emita a nota do pedido 54
                </span>
            </div>

            {carregando && (
                <div className="mt-3">
                    <ProgressBar mode="indeterminate" style={{ height: '6px' }} />
                    <p className="text-center text-sm text-500 mt-2">Consultando o sistema...</p>
                </div>
            )}

            {erro && (
                <Message severity="error" text={erro} className="w-full mt-3" />
            )}

            {resultado && !carregando && (
                <div className="copiloto-resultado-card">
                    <div className="copiloto-resultado-header">
                        <Tag
                            value={resultado.modo === 'ACAO' ? 'Ação' : 'Dúvida'}
                            severity={getStatusSeverity(resultado.status)}
                        />
                        <span className="text-xs text-500 font-medium">
                            Status: {resultado.status}
                        </span>
                    </div>

                    <h4 className="copiloto-resultado-titulo">{resultado.titulo}</h4>

                    {resultado.caminhoMenu && (
                        <div className="copiloto-caminho-menu mt-2">
                            <i className="pi pi-compass text-primary" />
                            <span><strong>Menu:</strong> {resultado.caminhoMenu}</span>
                        </div>
                    )}

                    <p className="copiloto-resultado-msg">{resultado.mensagem}</p>

                    {resultado.rota && (
                        <Button
                            label={resultado.rotuloBotao || 'Ir para a tela'}
                            icon="pi pi-arrow-right"
                            className="p-button-sm copiloto-navegacao-btn p-button-primary"
                            onClick={() => irParaTela(resultado.rota)}
                        />
                    )}
                </div>
            )}
        </Dialog>
    );
};
