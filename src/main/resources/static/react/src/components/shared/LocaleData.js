import i18n from '../../i18n';
import { addLocale, locale } from 'primereact/api';

export const LOCALES_SUPORTADOS = ['pt-BR', 'en-US', 'es-ES', 'fr-FR'];

// Idioma do documento -> locale de formato. Padrao: pt-BR.
// Formato americano (mm/dd) SOMENTE no ingles.
export const localeAtivo = () => {
    const l = String(i18n.language || i18n.resolvedLanguage || 'pt-BR');
    if (l.toLowerCase().startsWith('en')) return 'en-US';
    if (l.toLowerCase().startsWith('es')) return 'es-ES';
    if (l.toLowerCase().startsWith('fr')) return 'fr-FR';
    return 'pt-BR';
};

export const formatoData = () => (localeAtivo() === 'en-US' ? 'mm/dd/yy' : 'dd/mm/yy');

export const fmtData = (v, opts) => {
    if (v == null || v === '') return '';
    const d = v instanceof Date ? v : new Date(v);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleDateString(localeAtivo(), opts);
};

export const fmtDataHora = (v, opts) => {
    if (v == null || v === '') return '';
    const d = v instanceof Date ? v : new Date(v);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString(localeAtivo(), opts);
};

const PRIMEREACT_LOCALES = {
    'en-US': {
        firstDayOfWeek: 0, today: 'Today', clear: 'Clear', dateFormat: 'mm/dd/yy', weekHeader: 'Wk',
        emptyFilterMessage: 'No available options',
        emptyMessage: 'No records found'
    },
    'pt-BR': {
        firstDayOfWeek: 0,
        dayNames: ['domingo', 'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado'],
        dayNamesShort: ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'],
        dayNamesMin: ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'],
        monthNames: ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'],
        monthNamesShort: ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'],
        today: 'Hoje',
        clear: 'Limpar',
        dateFormat: 'dd/mm/yy',
        weekHeader: 'Sm',
        emptyFilterMessage: 'Nenhuma opção disponível',
        emptyMessage: 'Nenhum registro'
    },
    'es-ES': {
        firstDayOfWeek: 1,
        dayNames: ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'],
        dayNamesShort: ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'],
        dayNamesMin: ['D', 'L', 'M', 'X', 'J', 'V', 'S'],
        monthNames: ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'],
        monthNamesShort: ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'],
        today: 'Hoy',
        clear: 'Borrar',
        dateFormat: 'dd/mm/yy',
        weekHeader: 'Sm',
        emptyFilterMessage: 'Sin opciones disponibles',
        emptyMessage: 'Sin registros'
    },
    'fr-FR': {
        firstDayOfWeek: 1,
        dayNames: ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'],
        dayNamesShort: ['dim', 'lun', 'mar', 'mer', 'jeu', 'ven', 'sam'],
        dayNamesMin: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
        monthNames: ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'],
        monthNamesShort: ['jan', 'fév', 'mar', 'avr', 'mai', 'jun', 'jul', 'aoû', 'sep', 'oct', 'nov', 'déc'],
        today: "Aujourd'hui",
        clear: 'Effacer',
        dateFormat: 'dd/mm/yy',
        weekHeader: 'Sm',
        emptyFilterMessage: 'Aucune option disponible',
        emptyMessage: 'Aucun enregistrement'
    }
};

export const aplicarLocalePrime = () => {
    const loc = localeAtivo();
    if (PRIMEREACT_LOCALES[loc]) {
        addLocale(loc, PRIMEREACT_LOCALES[loc]);
        locale(loc);
    }
};

aplicarLocalePrime();
i18n.on('languageChanged', aplicarLocalePrime);

export default { localeAtivo, formatoData, fmtData, fmtDataHora, aplicarLocalePrime };
