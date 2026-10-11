import React, { useEffect, useRef, useState } from 'react';
import { BI_AREAS, createBiClient, validPeriod } from '../../../../src/main/resources/static/react/src/services/GraylogBiBridge.mjs';
import pt from '../../../../src/main/resources/static/react/src/locales/bi-pt-BR.json';
import en from '../../../../src/main/resources/static/react/src/locales/bi-en-US.json';
import es from '../../../../src/main/resources/static/react/src/locales/bi-es-ES.json';
import fr from '../../../../src/main/resources/static/react/src/locales/bi-fr-FR.json';

const date = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const translations = { pt, en, es, fr };
const language = Object.hasOwn(translations, navigator.language.slice(0, 2)) ? navigator.language.slice(0, 2) : 'en';
const text = translations[language];
const help = {
    pt: 'Abra este painel pelo módulo BI do ERP e mantenha a janela do ERP aberta. Selecione apenas as áreas que deseja consultar.',
    en: 'Open this panel from the ERP BI module and keep the ERP window open. Select only the areas you want to query.',
    es: 'Abra este panel desde el módulo BI del ERP y mantenga abierta la ventana del ERP. Seleccione las áreas que desea consultar.',
    fr: 'Ouvrez ce panneau depuis le module BI de l’ERP et gardez la fenêtre ERP ouverte. Sélectionnez les domaines à consulter.',
};

export default function BrasilSaasBi() {
    const client = useRef(null);
    const [ready, setReady] = useState(false);
    const [inicio, setInicio] = useState(() => date(new Date(new Date().getFullYear(), new Date().getMonth(), 1)));
    const [fim, setFim] = useState(() => date(new Date()));
    const [selected, setSelected] = useState([...BI_AREAS]);
    const [areas, setAreas] = useState({});
    const [busy, setBusy] = useState({});
    const [errors, setErrors] = useState({});
    const [applied, setApplied] = useState(null);

    useEffect(() => {
        const parameters = new URLSearchParams(window.location.hash.slice(1));
        const nonce = parameters.get('nonce');
        let origin;
        try {
            const url = new URL(parameters.get('erpOrigin'));
            if (!['http:', 'https:'].includes(url.protocol) || url.origin !== parameters.get('erpOrigin')) return undefined;
            origin = url.origin;
        } catch { return undefined; }
        if (!window.opener || !nonce || !/^[a-zA-Z0-9-]{32,64}$/.test(nonce)) return undefined;
        const parent = window.opener;
        const connection = createBiClient({ parent, origin, nonce,
            send: (data, targetOrigin) => parent.postMessage(data, targetOrigin),
            onReady: () => {
                setReady(true);
                const loading = {};
                BI_AREAS.forEach(area => { if (connection.query(area, inicio, fim)) loading[area] = true; });
                setBusy(loading); setApplied({ inicio, fim });
            },
            onResult: (area, result) => { setAreas(previous => ({ ...previous, [area]: result.areas[0] })); setBusy(previous => ({ ...previous, [area]: false })); },
            onError: area => { setErrors(previous => ({ ...previous, [area]: true })); setBusy(previous => ({ ...previous, [area]: false })); },
            onClosed: () => { setReady(false); setAreas({}); setErrors({}); setBusy({}); setApplied(null); },
        });
        client.current = connection;
        const receive = event => connection.receive(event);
        window.addEventListener('message', receive);
        connection.hello();
        const poll = setInterval(() => { if (parent.closed) connection.dispose(); else connection.hello(); }, 1000);
        return () => { clearInterval(poll); connection.dispose(); client.current = null; window.removeEventListener('message', receive); };
    }, []);

    const refresh = event => {
        event.preventDefault();
        if (!ready || !validPeriod(inicio, fim)) return;
        setAreas({}); setErrors({});
        const loading = {};
        selected.forEach(area => { if (client.current?.query(area, inicio, fim)) loading[area] = true; });
        setBusy(loading);
        setApplied({ inicio, fim });
    };
    const format = indicator => new Intl.NumberFormat(navigator.language, indicator.unidade === 'BRL'
        ? { style: 'currency', currency: 'BRL' } : { maximumFractionDigits: 0 }).format(Number(indicator.valor));

    return <section style={{ padding: 24 }} aria-labelledby="brasil-saas-bi-title">
        <h1 id="brasil-saas-bi-title">{text.title}</h1>
        <p>{help[language]}</p>
        <form onSubmit={refresh}>
            <fieldset disabled={!ready || Object.values(busy).some(Boolean)}>
                <legend>{text.title}</legend>
                <label>{text.start} <input type="date" required value={inicio} max={fim} onChange={event => setInicio(event.target.value)} /></label>{' '}
                <label>{text.end} <input type="date" required value={fim} min={inicio} onChange={event => setFim(event.target.value)} /></label>
                <div>{BI_AREAS.map(area => <label key={area} style={{ marginRight: 16 }}>
                    <input type="checkbox" checked={selected.includes(area)} onChange={event => {
                        setSelected(previous => event.target.checked ? [...previous, area] : previous.filter(value => value !== area));
                        setAreas(previous => { const next = { ...previous }; delete next[area]; return next; });
                    }} /> {text.areas[area]}
                </label>)}</div>
                <button type="submit" disabled={!selected.length || !validPeriod(inicio, fim)}>{text.refresh}</button>
            </fieldset>
        </form>
        <p>{text.periodHelp}</p>
        {applied && <p>{text.start}: {applied.inicio} — {text.end}: {applied.fim}</p>}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(240px,1fr))', gap: 16 }}>
            {selected.map(area => <article key={area} style={{ border: '1px solid #aaa', padding: 16, borderRadius: 8 }} aria-busy={!!busy[area]}>
                <h2>{text.areas[area]}</h2>
                {busy[area] && <p role="status">{text.loading}</p>}
                {errors[area] && <p role="alert">{text.error}</p>}
                {areas[area] && <>
                    <p style={{ fontSize: 28 }}>{format(areas[area].indicadores[0])}</p>
                    <p>{text.metrics[areas[area].indicadores[0].codigo]}</p>
                    <details><summary>{text.details}</summary><dl>{areas[area].indicadores.slice(1).map(indicator => <React.Fragment key={indicator.codigo}>
                        <dt>{text.metrics[indicator.codigo]}</dt><dd>{format(indicator)}</dd>
                    </React.Fragment>)}</dl></details>
                </>}
            </article>)}
        </div>
    </section>;
}
