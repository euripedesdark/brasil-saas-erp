import { useTranslation } from 'react-i18next';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { apiFetch } from '../../services/ApiConfig';
import { Button } from 'primereact/button';
import { Dropdown } from 'primereact/dropdown';
import { InputMask } from 'primereact/inputmask';
import { InputText } from 'primereact/inputtext';
import { Password } from 'primereact/password';
import { Checkbox } from 'primereact/checkbox';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Message } from 'primereact/message';
import { Tag } from 'primereact/tag';
import { Toast } from 'primereact/toast';
import { useAuth } from '../../contexts/AuthContext';
import { ImagemRegistro } from '../shared/ImagemRegistro';

const REGIMES = [
    { label: 'Simples Nacional', value: 'SIMPLES_NACIONAL' },
    { label: 'Regime Efeito Normal', value: 'REGIME_NORMAL' },
    { label: 'MEI', value: 'MEI' }
];

const UFS = ['AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'];

const erroDe = (e, padrao) => {
    const d = e?.payload;
    if (d?.errors?.[0]?.message) return d.errors[0].message;
    if (d?.message) return d.message;
    return padrao;
};

/**
 * Cadastro da empresa.
 *
 * Empresa e o tenant: e o filtro de todos os dados do sistema. Um usuario sem
 * empresa nao ve nada — e esta e a unica coisa que ele pode fazer ate ter uma.
 *
 * A tela funciona nos dois sentidos: sem empresa, cadastra; com empresa, edita
 * os dados dela (razao social, inscricoes, endereco, regime, Stripe e certificado A1).
 */
export const ConfigurarEmpresa = () => {
    const { t } = useTranslation();
    const { user, refreshPerfil } = useAuth();
    const toast = useRef(null);

    const [carregando, setCarregando] = useState(true);
    const [salvando, setSalvando] = useState(false);
    const [situacao, setSituacao] = useState(null);
    const [empresaId, setEmpresaId] = useState(null);
    const podeConfigurarCredenciais = !!(
        user?.isSuperuser ||
        user?.isDiretoria ||
        user?.perfis?.includes('GESTOR') ||
        user?.authorities?.includes('ROLE_GESTOR')
    );
    const [stripe, setStripe] = useState({
        configurada: false, habilitada: false, chaveMascarada: null,
        webhookConfigurado: false, stripeAccountId: null
    });
    const [stripeKey, setStripeKey] = useState('');
    const [stripeWebhook, setStripeWebhook] = useState('');
    const [stripeEnabled, setStripeEnabled] = useState(false);
    const [stripeLoading, setStripeLoading] = useState(false);
    const [stripeTesting, setStripeTesting] = useState(false);
    const [certCaminho, setCertCaminho] = useState('');
    const [certSenha, setCertSenha] = useState('');
    const [certSalvar, setCertSalvar] = useState(true);
    const [certLoading, setCertLoading] = useState(false);
    const [certGuardados, setCertGuardados] = useState([]);

    const vazio = {
        razaoSocial: '', nomeFantasia: '', cnpj: '',
        inscricaoEstadual: '', inscricaoMunicipal: '',
        regimeTributario: 'SIMPLES_NACIONAL', codigoIbge: '',
        endereco: '', numero: '', complemento: '', bairro: '',
        uf: '', cep: '', telefone: ''
    };
    const [form, setForm] = useState(vazio);
    const [erros, setErros] = useState({});

    const set = (campo) => (e) => setForm((f) => ({ ...f, [campo]: e?.value ?? e?.target?.value ?? '' }));

    // O código IBGE vem de outra fonte e é só um número para o usuário. Buscar
    // pelo código mostra de que município é, e avisa quando o código não existe
    // na tabela — sem isso o campo aceita qualquer número e o erro só aparece na
    // emissão do documento fiscal.
    const [municipio, setMunicipio] = useState(null);
    useEffect(() => {
        const codigo = String(form.codigoIbge || '').trim();
        if (!/^\d{7}$/.test(codigo)) { setMunicipio(null); return undefined; }
        let cancelado = false;
        const t = setTimeout(() => {
            apiFetch(`/api/municipios/codigo/${codigo}`)
                .then((r) => (r.ok ? r.json() : null))
                .then((d) => { if (!cancelado) setMunicipio(d?.data ?? null); })
                .catch(() => { if (!cancelado) setMunicipio(null); });
        }, 350);
        return () => { cancelado = true; clearTimeout(t); };
    }, [form.codigoIbge]);

    const carregar = useCallback(async () => {
        setCarregando(true);
        try {
            const r = await apiFetch('/api/core/minha-empresa');
            if (!r.ok) throw new Error(t('legacyUi.empresa.loadError'));
            const s = (await r.json()).data;
            setSituacao(s);
            // id da empresa: e o que a rota do logo usa, e so existe depois de
            // cadastrar — antes disso nao ha logo para enviar
            if (s?.empresaId) {
                setEmpresaId(s.empresaId);
                if (podeConfigurarCredenciais) {
                    try {
                        const sr = await apiFetch('/api/core/minha-empresa/stripe');
                        if (sr.ok) {
                            const sj = await sr.json();
                            const cfg = sj?.data ?? {};
                            setStripe(cfg);
                            setStripeEnabled(!!cfg.habilitada);
                        }
                    } catch (e) {
                        console.warn('Não foi possível carregar a configuração Stripe', e);
                    }
                    try {
                        const cr = await apiFetch('/api/fiscal/certificados');
                        if (cr.ok) {
                            const cj = await cr.json();
                            setCertGuardados(cj?.data || cj || []);
                        }
                    } catch (e) {
                        console.warn('Não foi possível listar certificados da empresa', e);
                    }
                }
            }
            setForm((f) => ({
                ...f,
                razaoSocial: s?.razaoSocial ?? '',
                cnpj: s?.cnpj ?? ''
            }));
        } catch (e) {
            toast.current?.show({
                severity: 'error', summary: 'Erro',
                detail: erroDe(e, t('legacyUi.empresa.loadError')), life: 5000
            });
        } finally {
            setCarregando(false);
        }
    }, [podeConfigurarCredenciais]);

    useEffect(() => { carregar(); }, [carregar]);

    const validar = () => {
        const novos = {};
        if (!form.razaoSocial?.trim()) novos.razaoSocial = t('legacyUi.empresa.nameRequired');
        const digitos = (form.cnpj || '').replace(/\D/g, '');
        if (digitos.length !== 14) novos.cnpj = t('legacyUi.empresa.cnpjRequired');
        setErros(novos);
        return Object.keys(novos).length === 0;
    };

    const salvar = async () => {
        if (!validar()) {
            toast.current?.show({
                severity: 'warn', summary: 'Confira os campos',
                detail: 'Some os campos destacados em vermelho', life: 4000
            });
            return;
        }
        setSalvando(true);
        try {
            const jaTem = situacao?.cadastrada;
            const r = await apiFetch('/api/core/minha-empresa', {
                method: jaTem ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(form)
            });
            const json = await r.json();
            if (!r.ok) {
                const mensagem = json?.errors?.[0]?.message || t('legacyUi.empresa.saveError');
                if (mensagem.toLowerCase().includes('cnpj')) setErros({ cnpj: mensagem });
                throw new Error(mensagem);
            }

            // O token carrega a empresa; sem recarregar o perfil, a interface
            // continuaria achando que o usuario esta sem empresa.
            await refreshPerfil?.();
            setSituacao(json.data);
            toast.current?.show({
                severity: 'success', summary: t('legacyUi.empresa.saved'),
                detail: jaTem ? t('legacyUi.empresa.updated') : 'Agora o sistema está liberado',
                life: 4000
            });
        } catch (e) {
            toast.current?.show({
                severity: 'error', summary: t('common.error'),
                detail: erroDe(e, t('legacyUi.empresa.saveError')), life: 6000
            });
        } finally {
            setSalvando(false);
        }
    };

    const semEmpresa = situacao && situacao.cadastrada === false;
    const campo = (id, rotulo, children, msg) => (
        <div className="col-12 md:col-6">
            <label className="bc-label" htmlFor={id}>{rotulo}</label>
            {children}
            {msg && <div className="bc-field-mensagem">{msg}</div>}
        </div>
    );

    return (
        <div className="p-4">
            <Toast ref={toast} />
            <div style={{ maxWidth: 860, margin: '0 auto' }}>
                <div className="mb-4">
                    <h2 className="m-0">Sua empresa</h2>
                    <span className="bc-muted">
                        {semEmpresa
                            ? 'Cadastre a empresa para começar a usar o sistema'
                            : 'Dados da empresa do seu usuário'}
                    </span>
                </div>

                {semEmpresa && (
                    <Message severity="info" className="mb-4" text="O sistema é multiempresa: os dados são separados por empresa. Enquanto não houver empresa cadastrada, o restante do sistema fica indisponível." />
                )}

                {carregando ? (
                    <Message severity="secondary" text="Carregando..." />
                ) : (
                    <div className="grid p-fluid">
                        {campo('razaoSocial', 'Razão social *',
                            <InputText id="razaoSocial" value={form.razaoSocial}
                                       onChange={set('razaoSocial')} className={erros.razaoSocial ? 'p-invalid' : ''} />,
                            erros.razaoSocial)}

                        {campo('nomeFantasia', 'Nome fantasia',
                            <InputText id="nomeFantasia" value={form.nomeFantasia} onChange={set('nomeFantasia')} />)}

                        {campo('cnpj', 'CNPJ *',
                            <InputMask id="cnpj" mask="99.999.999/9999-99" value={form.cnpj}
                                       onChange={set('cnpj')} placeholder="00.000.000/0000-00"
                                       className={erros.cnpj ? 'p-invalid' : ''} />,
                            erros.cnpj)}

                        {campo('regimeTributario', 'Regime tributário',
                            <Dropdown id="regimeTributario" value={form.regimeTributario} options={REGIMES}
                                      onChange={set('regimeTributario')} />)}

                        {campo('inscricaoEstadual', 'Inscrição estadual',
                            <InputText id="inscricaoEstadual" value={form.inscricaoEstadual} onChange={set('inscricaoEstadual')} />)}

                        {campo('inscricaoMunicipal', 'Inscrição municipal',
                            <InputText id="inscricaoMunicipal" value={form.inscricaoMunicipal} onChange={set('inscricaoMunicipal')} />)}

                        {campo('telefone', 'Telefone',
                            <InputMask id="telefone" mask="(99) 99999-9999" value={form.telefone || ''} onChange={set('telefone')} />)}

                        <div className="col-12"><hr className="mt-2 mb-0" /></div>

                        {empresaId && podeConfigurarCredenciais && (
                            <>
                                <div className="col-12 mt-3">
                                    <div className="flex align-items-center justify-content-between gap-3 flex-wrap">
                                        <div>
                                            <h3 className="m-0">Stripe — pagamentos da empresa</h3>
                                            <span className="bc-muted">
                                                Configuração opcional. A empresa pode ser cadastrada e usada normalmente sem Stripe e configurada depois.
                                                A credencial é armazenada criptografada e nunca é exibida integralmente.
                                            </span>
                                        </div>
                                        <Tag
                                            severity={stripe.configurada && stripe.habilitada ? 'success' : 'secondary'}
                                            value={stripe.configurada && stripe.habilitada ? 'Stripe ativo' : 'Stripe opcional'}
                                        />
                                    </div>
                                </div>

                                <div className="col-12 md:col-6">
                                    <label className="bc-label" htmlFor="stripeSecretKey">Secret API Key</label>
                                    <InputText
                                        id="stripeSecretKey"
                                        type="password"
                                        value={stripeKey}
                                        onChange={(e) => setStripeKey(e.target.value)}
                                        placeholder={stripe.chaveMascarada || 'sk_test_... / rk_test_...'}
                                        autoComplete="new-password"
                                    />
                                    <small className="bc-muted">
                                        Deixe vazio para manter a chave atual.
                                    </small>
                                </div>

                                <div className="col-12 md:col-6">
                                    <label className="bc-label" htmlFor="stripeWebhookSecret">Webhook Signing Secret</label>
                                    <InputText
                                        id="stripeWebhookSecret"
                                        type="password"
                                        value={stripeWebhook}
                                        onChange={(e) => setStripeWebhook(e.target.value)}
                                        placeholder={stripe.webhookConfigurado ? 'Webhook já configurado' : 'whsec_...'}
                                        autoComplete="new-password"
                                    />
                                    <small className="bc-muted">
                                        Também fica criptografado; deixe vazio para manter.
                                    </small>
                                </div>

                                <div className="col-12 flex align-items-center gap-3">
                                    <input
                                        id="stripeEnabled"
                                        type="checkbox"
                                        checked={stripeEnabled}
                                        onChange={(e) => setStripeEnabled(e.target.checked)}
                                    />
                                    <label htmlFor="stripeEnabled">Habilitar cobranças Stripe para esta empresa</label>
                                </div>

                                {stripe.stripeAccountId && (
                                    <div className="col-12">
                                        <Message severity="success" text={`Conta Stripe validada: ${stripe.stripeAccountId}`} />
                                    </div>
                                )}

                                {empresaId && (
                                    <div className="col-12">
                                        <Message
                                            severity="secondary"
                                            text={`Webhook: ${window.location.origin}/api/financeiro/stripe/webhook/${empresaId}`}
                                        />
                                    </div>
                                )}

                                <div className="col-12 flex justify-content-end gap-2">
                                    <Button
                                        type="button"
                                        label="Testar conexão Stripe"
                                        icon="pi pi-check-circle"
                                        outlined
                                        loading={stripeTesting}
                                        disabled={stripeTesting || !stripe.configurada}
                                        onClick={async () => {
                                            setStripeTesting(true);
                                            try {
                                                const r = await apiFetch('/api/core/minha-empresa/stripe/testar', { method: 'POST' });
                                                const j = await r.json();
                                                if (!r.ok) throw new Error(j?.errors?.[0]?.message || j?.message || 'Falha ao testar Stripe');
                                                setStripe((v) => ({ ...v, configurada: true, stripeAccountId: j?.data?.stripeAccountId || v.stripeAccountId }));
                                                toast.current?.show({
                                                    severity: 'success',
                                                    summary: 'Stripe validado',
                                                    detail: `Conta ${j?.data?.stripeAccountId || ''} respondeu corretamente à API`,
                                                    life: 5000
                                                });
                                            } catch (e) {
                                                toast.current?.show({ severity: 'error', summary: 'Stripe', detail: e.message, life: 6000 });
                                            } finally {
                                                setStripeTesting(false);
                                            }
                                        }}
                                    />
                                    <Button
                                        type="button"
                                        label="Salvar Stripe"
                                        icon="pi pi-save"
                                        loading={stripeLoading}
                                        disabled={stripeLoading}
                                        onClick={async () => {
                                            setStripeLoading(true);
                                            try {
                                                const r = await apiFetch('/api/core/minha-empresa/stripe', {
                                                    method: 'PUT',
                                                    headers: { 'Content-Type': 'application/json' },
                                                    body: JSON.stringify({
                                                        secretKey: stripeKey.trim() || null,
                                                        webhookSecret: stripeWebhook.trim() || null,
                                                        habilitada: stripeEnabled
                                                    })
                                                });
                                                const j = await r.json();
                                                if (!r.ok) throw new Error(j?.errors?.[0]?.message || j?.message || 'Falha ao salvar Stripe');
                                                setStripe(j?.data || {});
                                                setStripeKey('');
                                                setStripeWebhook('');
                                                toast.current?.show({ severity: 'success', summary: 'Stripe salvo', detail: 'A credencial desta empresa foi atualizada.', life: 5000 });
                                            } catch (e) {
                                                toast.current?.show({ severity: 'error', summary: 'Stripe', detail: e.message, life: 6000 });
                                            } finally {
                                                setStripeLoading(false);
                                            }
                                        }}
                                    />
                                </div>
                            </>
                        )}

                        {empresaId && podeConfigurarCredenciais && (
                            <>
                                <div className="col-12"><hr className="mt-2 mb-0" /></div>
                                <div className="col-12 mt-3">
                                    <div className="flex align-items-center justify-content-between gap-3 flex-wrap">
                                        <div>
                                            <h3 className="m-0">Certificado digital (A1) — desta empresa</h3>
                                            <span className="bc-muted">
                                                Matriz ou filial: cada empresa usa o próprio .pfx para NFe/NFS-e/CT-e.
                                                Informe o caminho no servidor e a senha. A senha não é gravada no banco.
                                            </span>
                                        </div>
                                        <Tag
                                            severity={certGuardados.length ? 'success' : 'secondary'}
                                            value={certGuardados.length ? `${certGuardados.length} guardado(s)` : 'Sem certificado'}
                                        />
                                    </div>
                                </div>
                                <div className="col-12 md:col-8">
                                    <label className="bc-label" htmlFor="certCaminho">Caminho do .pfx / .p12 no servidor</label>
                                    <InputText
                                        id="certCaminho"
                                        className="w-full"
                                        value={certCaminho}
                                        onChange={(e) => setCertCaminho(e.target.value)}
                                        placeholder="/caminho/do/certificado.pfx"
                                    />
                                </div>
                                <div className="col-12 md:col-4">
                                    <label className="bc-label" htmlFor="certSenha">Senha do certificado</label>
                                    <Password
                                        id="certSenha"
                                        className="w-full"
                                        inputClassName="w-full"
                                        value={certSenha}
                                        onChange={(e) => setCertSenha(e.target.value)}
                                        feedback={false}
                                        toggleMask
                                    />
                                </div>
                                <div className="col-12 flex align-items-center gap-2">
                                    <Checkbox inputId="certSalvar" checked={certSalvar} onChange={(e) => setCertSalvar(!!e.checked)} />
                                    <label htmlFor="certSalvar">Guardar certificado no repositório da empresa (recomendado)</label>
                                </div>
                                <div className="col-12 flex gap-2 flex-wrap">
                                    <Button
                                        label="Carregar certificado"
                                        icon="pi pi-upload"
                                        loading={certLoading}
                                        disabled={certLoading || !certCaminho.trim()}
                                        onClick={async () => {
                                            setCertLoading(true);
                                            try {
                                                const r = await apiFetch('/api/fiscal/certificados/carregar', {
                                                    method: 'POST',
                                                    headers: { 'Content-Type': 'application/json' },
                                                    body: JSON.stringify({
                                                        caminho: certCaminho.trim(),
                                                        senha: certSenha,
                                                        salvar: certSalvar
                                                    })
                                                });
                                                const j = await r.json();
                                                if (!r.ok) throw new Error(j?.data || j?.errors?.[0]?.message || j?.message || ('HTTP ' + r.status));
                                                setCertSenha('');
                                                toast.current?.show({
                                                    severity: 'success',
                                                    summary: 'Certificado',
                                                    detail: j?.data?.mensagem || 'Certificado processado para esta empresa',
                                                    life: 5000
                                                });
                                                const cr = await apiFetch('/api/fiscal/certificados');
                                                if (cr.ok) {
                                                    const cj = await cr.json();
                                                    setCertGuardados(cj?.data || cj || []);
                                                }
                                            } catch (e) {
                                                toast.current?.show({ severity: 'error', summary: 'Certificado', detail: e.message, life: 7000 });
                                            } finally {
                                                setCertLoading(false);
                                            }
                                        }}
                                    />
                                </div>
                                <div className="col-12">
                                    <DataTable value={Array.isArray(certGuardados) ? certGuardados : []} size="small" emptyMessage="Nenhum certificado guardado nesta empresa">
                                        <Column field="id" header="ID" style={{ width: '5rem' }} />
                                        <Column field="nomeArquivo" header="Arquivo" body={(r) => r.nomeArquivo || r.nome || r.caminho || '—'} />
                                        <Column field="createdAt" header="Quando" body={(r) => r.createdAt || r.criadoEm || '—'} />
                                    </DataTable>
                                </div>
                            </>
                        )}


                        {/* Logo da empresa. Os bytes vao para o MongoDB e o Postgres
                            guarda so a referencia, como a imagem do produto. As tres
                            rotas existiam sem nenhuma tela chamando. */}
                        {empresaId && (
                            <div className="col-12">
                                <ImagemRegistro
                                    registroId={empresaId}
                                    lerUrl={`/api/core/empresas/${empresaId}/logo`}
                                    enviarUrl={`/api/core/empresas/${empresaId}/logo`}
                                    removerUrl={`/api/core/empresas/${empresaId}/logo`}
                                    campo="logo"
                                    rotulo="Logo da empresa"
                                />
                            </div>
                        )}

                        {campo('cep', 'CEP',
                            <InputMask id="cep" mask="99999-999" value={form.cep || ''} onChange={set('cep')} />)}

                        {campo('endereco', 'Endereço',
                            <InputText id="endereco" value={form.endereco || ''} onChange={set('endereco')} />)}

                        {campo('numero', 'Número',
                            <InputText id="numero" value={form.numero || ''} onChange={set('numero')} />)}

                        {campo('complemento', 'Complemento',
                            <InputText id="complemento" value={form.complemento || ''} onChange={set('complemento')} />)}

                        {campo('bairro', 'Bairro',
                            <InputText id="bairro" value={form.bairro || ''} onChange={set('bairro')} />)}

                        {campo('ibge', 'Código IBGE do município',
                            <InputText id="ibge" value={form.codigoIbge} maxLength={7}
                                       onChange={set('codigoIbge')}/>,
                            form.codigoIbge && /\d{7}$/.test(form.codigoIbge) && !municipio
                                ? 'Código IBGE não encontrado na tabela' : null)}
                        <div className="col-12">
                            {municipio && (
                                <Message severity="success" text={`Município: ${municipio.nome} — ${municipio.uf}`} />
                            )}
                        </div>

                        {campo('uf', 'UF',
                            <Dropdown id="uf" value={form.uf || null} options={UFS.map((u) => ({ label: u, value: u }))}
                                      onChange={set('uf')} placeholder="Selecione" />)}

                        <div className="col-12 flex justify-end gap-2 mt-3">
                            <Button label={semEmpresa ? t('legacyUi.empresa.save') : t('common.saveChanges')}
                                    icon="pi pi-check" loading={salvando}
                                    disabled={salvando} onClick={salvar} />
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default ConfigurarEmpresa;
