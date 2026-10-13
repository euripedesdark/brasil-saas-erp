import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'primereact/dialog';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { Button } from 'primereact/button';
import { apiFetch } from '../services/ApiConfig';
import { salvarBlob } from '../services/downloadService';
import { NotificationService } from '../services/NotificationService';

export const MemorandoDialog = ({ visible, onHide }) => {
    const { t } = useTranslation();
    const [form, setForm] = useState({ numero: '', assunto: '', destinatario: '', texto: '' });
    const [busy, setBusy] = useState(false);
    const change = (key, value) => setForm(previous => ({ ...previous, [key]: value }));
    const generate = async event => {
        event.preventDefault();
        setBusy(true);
        try {
            const response = await apiFetch('/api/relatorios/memorando/pdf', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form)
            });
            if (!response.ok) throw new Error(t('memorando.error'));
            salvarBlob(await response.blob(), 'memorando.pdf');
            NotificationService.showSuccess(t('memorando.success'));
        } catch (error) {
            NotificationService.showError(error.message);
        } finally { setBusy(false); }
    };
    return <Dialog visible={visible} header={t('memorando.title')} onHide={onHide}
        style={{ width: 'min(42rem, 95vw)' }}>
        <form onSubmit={generate} className="p-fluid">
            {['numero', 'assunto', 'destinatario'].map(key => <div className="field" key={key}>
                <label htmlFor={`memo-${key}`}>{t(`memorando.${key}`)}</label>
                <InputText id={`memo-${key}`} value={form[key]} required disabled={busy}
                    maxLength={{ numero: 50, assunto: 200, destinatario: 500 }[key]}
                    onChange={event => change(key, event.target.value)} />
            </div>)}
            <div className="field">
                <label htmlFor="memo-texto">{t('memorando.texto')}</label>
                <InputTextarea id="memo-texto" value={form.texto} required rows={10} maxLength={20000}
                    disabled={busy} onChange={event => change('texto', event.target.value)} />
            </div>
            <Button type="submit" label={t('memorando.generate')} icon="pi pi-file-pdf" loading={busy} />
        </form>
    </Dialog>;
};
