import ApiConfig, { apiFetch } from './ApiConfig';

const empresaHeaders = (empresaId, json = false) => ({
    ...(json ? { 'Content-Type': 'application/json' } : {}),
    'X-Empresa-Id': String(empresaId)
});

/**
 * Serviço do Assistente ERP.
 *
 * Suporta o fluxo clássico e o novo modelo unificado com botões [ Dúvida ] e [ Ação ].
 */
export const AssistenteService = {
    perguntar: async (empresaId, pergunta) => {
        const resposta = await apiFetch(`${ApiConfig.BASE_URL}/api/ia/assistente`, {
            method: 'POST',
            headers: empresaHeaders(empresaId, true),
            body: JSON.stringify({ pergunta })
        });
        if (!resposta.ok) {
            throw new Error('Não foi possível consultar o assistente');
        }
        return resposta.json();
    },

    executarComando: async (empresaId, texto, modo) => {
        const resposta = await apiFetch(`${ApiConfig.BASE_URL}/api/ia/assistente/comando`, {
            method: 'POST',
            headers: empresaHeaders(empresaId, true),
            body: JSON.stringify({ texto, modo })
        });
        if (!resposta.ok) {
            const err = await resposta.json().catch(() => ({}));
            throw new Error(err.message || 'Falha ao processar comando com o assistente');
        }
        return resposta.json();
    },

    auditoria: async (empresaId) => {
        const resposta = await apiFetch(`${ApiConfig.BASE_URL}/api/ia/assistente/auditoria`, {
            headers: empresaHeaders(empresaId)
        });
        return resposta.ok ? resposta.json() : [];
    }
};
