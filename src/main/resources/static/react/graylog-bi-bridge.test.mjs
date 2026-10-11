import { test } from 'node:test';
import assert from 'node:assert/strict';
import { BI_PROTOCOL, biNonce, createBiHost, createBiClient, validPeriod, presentationUrl } from './src/services/GraylogBiBridge.mjs';

function fixture(query = async area => ({ areas: [{ codigo: area }] }), options = {}) {
    const target = {}, sent = [], calls = [];
    const host = createBiHost({ target, origin: 'https://graylog.example', nonce: 'nonce', reply: value => sent.push(value),
        query: (...args) => { calls.push(args); return query(...args); }, ...options });
    const message = overrides => ({ source: target, origin: 'https://graylog.example', data: { protocol: BI_PROTOCOL, nonce: 'nonce', type: 'query', id: 'one', area: 'vendas', inicio: '2026-01-01', fim: '2026-01-31', ...overrides } });
    return { host, sent, calls, message };
}
test('calendar validation and inclusive 366 day limit', () => {
    assert.equal(validPeriod('2024-01-01', '2024-12-31'), true);
    for (const dates of [['2024-01-01','2025-01-01'], ['2026-02-30','2026-03-01'], ['2026-02-01','2026-01-01'], ['bad','2026-01-01']]) assert.equal(validPeriod(...dates), false);
});
test('plugin URL preserves reverse proxy prefix and contains no credentials', () => {
    assert.equal(presentationUrl('https://graylog.example/logs/', 'https://erp.example', 'nonce').pathname, '/logs/brasil-saas-bi');
    for (const url of ['javascript:alert(1)', 'https://user:pass@graylog.example', 'https://graylog.example/?token=secret']) assert.throws(() => presentationUrl(url, 'https://erp.example', 'nonce'));
});
test('requires the bound window, exact origin, protocol and nonce', async () => {
    const f = fixture();
    for (const event of [{ ...f.message(), source: {} }, { ...f.message(), origin: 'https://evil.example' }, f.message({ nonce: 'wrong' }), f.message({ protocol: 'other' })]) await f.host.receive(event);
    assert.equal(f.calls.length, 0); assert.equal(f.sent.length, 0);
});
test('only selected fixed query is called; arbitrary company and SQL are ignored', async () => {
    const f = fixture(); await f.host.receive(f.message({ empresaId: 999, sql: 'DROP TABLE' }));
    assert.equal(f.calls.length, 1); assert.deepEqual(f.calls[0].slice(0, 3), ['vendas','2026-01-01','2026-01-31']);
    assert.equal(f.sent[0].type, 'result'); assert.equal(JSON.stringify(f.sent).includes('empresaId'), false);
});
test('invalid area or period cannot run a query', async () => {
    const f = fixture(); await f.host.receive(f.message({ area: '../admin' })); await f.host.receive(f.message({ inicio: '2026-02-30' }));
    assert.equal(f.calls.length, 0); assert.equal(f.sent.every(x => x.code === 'INVALID_QUERY'), true);
});
test('replays and excess requests do not query the database', async () => {
    const f = fixture(); await f.host.receive(f.message()); await f.host.receive(f.message());
    for (let i = 0; i < 30; i++) await f.host.receive(f.message({ id: `id-${i}` }));
    assert.equal(f.calls.length, 24); assert.equal(f.sent.at(-1).code, 'BUSY');
});
test('errors are generic and never carry database or token details', async () => {
    const f = fixture(async () => { throw new Error('password=secret SQL SELECT'); }); await f.host.receive(f.message());
    assert.equal(f.sent[0].code, 'UNAVAILABLE'); assert.equal(JSON.stringify(f.sent).includes('secret'), false);
});
test('tenant change/unmount aborts work and discards late responses', async () => {
    let resolve; const f = fixture(() => new Promise(done => { resolve = done; }));
    const pending = f.host.receive(f.message()); f.host.dispose();
    assert.equal(f.calls[0][3].aborted, true); resolve({ areas: ['old-company'] }); await pending;
    assert.deepEqual(f.sent.map(x => x.type), ['closed']); await f.host.receive(f.message({ id: 'after' })); assert.equal(f.calls.length, 1);
});
test('handshake reports connection readiness without querying data', async () => {
    let ready = false; const f = fixture(undefined, { onReady: () => { ready = true; } }); await f.host.receive(f.message({ type: 'hello' }));
    assert.equal(ready, true); assert.equal(f.sent[0].type, 'ready'); assert.equal(f.calls.length, 0); f.host.dispose();
});
test('secure nonce works without relying on secure-context randomUUID', () => {
    assert.match(biNonce(), /^[a-f0-9]{32}$/); assert.notEqual(biNonce(), biNonce());
});
test('client accepts only the bound ERP and matching request period/area', () => {
    const parent = {}, results = [], errors = [], sent = [];
    const client = createBiClient({ parent, origin: 'https://erp.example', nonce: 'nonce', send: x => sent.push(x), onReady: () => {},
        onResult: (...args) => results.push(args), onError: area => errors.push(area), onClosed: () => {}, id: () => 'one' });
    const event = data => ({ source: parent, origin: 'https://erp.example', data: { protocol: BI_PROTOCOL, nonce: 'nonce', ...data } });
    assert.equal(client.query('vendas','2026-01-01','2026-01-31'), false);
    client.receive({ ...event({ type: 'ready' }), origin: 'https://evil.example' });
    assert.equal(client.query('vendas','2026-01-01','2026-01-31'), false);
    client.receive(event({ type: 'ready' })); assert.equal(client.query('vendas','2026-01-01','2026-01-31'), true);
    client.receive(event({ type: 'result', id: 'unknown', data: {} })); assert.equal(errors.length, 0);
    client.receive(event({ type: 'result', id: 'one', data: { inicio: '2026-01-01', fim: '2026-01-31', areas: [null] } }));
    assert.deepEqual(errors, ['vendas']); assert.equal(results.length, 0); client.dispose();
});
test('full host/client exchange queries one area and closes the presentation', async () => {
    const parent = {}, popup = {}, results = []; let work, closed = false;
    const origin = 'https://erp.example', graylog = 'https://graylog.example';
    const client = createBiClient({ parent, origin, nonce: 'nonce', onReady: () => {}, onError: () => assert.fail('unexpected error'),
        onResult: (area, result) => results.push({ area, result }), onClosed: () => { closed = true; },
        send: data => { work = host.receive({ source: popup, origin: graylog, data }); } });
    const host = createBiHost({ target: popup, origin: graylog, nonce: 'nonce',
        query: async (area, inicio, fim) => ({ inicio, fim, areas: [{ codigo: area, indicadores: ['pedidos','valor_pedidos','ticket_medio'].map(codigo => ({ codigo, valor: 1, unidade: 'BRL' })) }] }),
        reply: data => client.receive({ source: parent, origin, data }) });
    client.hello(); await work; assert.equal(client.query('vendas','2026-01-01','2026-01-31'), true); await work;
    assert.equal(results.length, 1); assert.equal(results[0].area, 'vendas'); host.dispose(); assert.equal(closed, true);
    assert.equal(client.query('vendas','2026-01-01','2026-01-31'), false);
});
