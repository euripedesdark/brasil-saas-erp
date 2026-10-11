const fs = require('fs');
const path = require('path');

const localeDir = path.join(__dirname, 'src', 'locales');
const languages = ['pt-BR', 'en-US', 'es-ES', 'fr-FR'];

function flatten(value, prefix = '', result = {}) {
  for (const [key, item] of Object.entries(value || {})) {
    const fullKey = prefix ? prefix + '.' + key : key;
    if (item && typeof item === 'object' && !Array.isArray(item)) {
      flatten(item, fullKey, result);
    } else if (typeof item === 'string') {
      result[fullKey] = item;
    }
  }
  return result;
}

let failures = 0;
const fail = message => {
  failures++;
  console.error('FAIL: ' + message);
};
const ok = message => console.log('OK: ' + message);

const locales = {};
for (const language of languages) {
  const file = path.join(localeDir, language + '.json');
  if (!fs.existsSync(file)) {
    fail('missing locale file: ' + language + '.json');
    continue;
  }
  try {
    locales[language] = flatten(JSON.parse(fs.readFileSync(file, 'utf8')));
  } catch (error) {
    fail('invalid JSON in ' + language + '.json: ' + error.message);
  }
}

const source = locales['pt-BR'];
if (source) {
  for (const language of languages.filter(code => code !== 'pt-BR')) {
    const target = locales[language];
    if (!target) continue;
    const sourceKeys = Object.keys(source).sort();
    const targetKeys = Object.keys(target).sort();
    const missing = sourceKeys.filter(key => !(key in target));
    const extra = targetKeys.filter(key => !(key in source));
    if (missing.length) fail(language + ' missing keys: ' + missing.slice(0, 20).join(', '));
    if (extra.length) fail(language + ' has unexpected keys: ' + extra.slice(0, 20).join(', '));
    if (!missing.length && !extra.length) ok(language + ' has the same ' + sourceKeys.length + ' keys as pt-BR');

    const empty = targetKeys.filter(key => typeof target[key] !== 'string' || !target[key].trim());
    if (empty.length) fail(language + ' has empty translations: ' + empty.slice(0, 20).join(', '));
    else ok(language + ' has no empty translation values');
  }
}

// Termos reutilizados nas telas legadas devem existir nos quatro idiomas.
if (source) {
  for (const language of languages) {
    const legacy = locales[language];
    const keys = Object.keys(source).filter(key => key.startsWith('legacyUi.'));
    if (keys.length < 100) fail('legacyUi has too few labels');
    if (!keys.every(key => legacy[key]?.trim())) fail(language + ' has missing UI terms');
    else ok(language + ' has ' + keys.length + ' legacy UI translations');
  }
}
const requiredTranslations = {
  'en-US': {
    'common.open': 'Open',
    'common.cancel': 'Cancel',
    'common.edit': 'Edit',
    'common.filter': 'Filter',
    'nav.profile': 'Profile',
    'menu.customers': 'Customers'
  },
  'es-ES': {
    'common.open': 'Abrir',
    'common.cancel': 'Cancelar',
    'common.edit': 'Editar',
    'common.filter': 'Filtrar',
    'nav.profile': 'Perfil',
    'menu.customers': 'Clientes'
  },
  'fr-FR': {
    'common.open': 'Ouvrir',
    'common.cancel': 'Annuler',
    'common.edit': 'Modifier',
    'common.filter': 'Filtrer',
    'nav.profile': 'Profil',
    'menu.customers': 'Clients'
  }
};

for (const [language, expected] of Object.entries(requiredTranslations)) {
  const locale = locales[language];
  if (!locale) continue;
  for (const [key, value] of Object.entries(expected)) {
    if (locale[key] !== value) fail(language + ' expected ' + key + ' = ' + JSON.stringify(value));
  }
  if (Object.entries(expected).every(([key, value]) => locale[key] === value)) {
    ok(language + ' critical navigation labels are translated');
  }
}

const biSource = flatten(JSON.parse(fs.readFileSync(path.join(localeDir, 'bi-pt-BR.json'), 'utf8')));
for (const language of languages) {
  const biTarget = flatten(JSON.parse(fs.readFileSync(path.join(localeDir, 'bi-' + language + '.json'), 'utf8')));
  if (JSON.stringify(Object.keys(biSource).sort()) !== JSON.stringify(Object.keys(biTarget).sort())) {
    fail(language + ' BI translation keys differ');
  }
  if (Object.values(biTarget).some(value => !value.trim())) fail(language + ' has empty BI translations');
  else ok(language + ' BI translations are complete');
}

if (failures) {
  console.error('i18n locale test failed: ' + failures + ' failure(s).');
  process.exit(1);
}
console.log('i18n locale test passed.');
