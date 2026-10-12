import ApiConfig, { apiFetch } from './ApiConfig';

const empresaHeaders = (empresaId, json = false) => ({
    ...(json ? { 'Content-Type': 'application/json' } : {}),
    'X-Empresa-Id': String(empresaId)
});

/**
 * Serviço do Assistente ERP.
 * - Consultar: modo DUVIDA
 * - Executar: modo ACAO (primeira chamada pede confirmação; segunda com confirmar=true executa)
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

    /**
     * @param {number|string} empresaId
     * @param {string} texto
     * @param {string} modo  DUVIDA | ACAO
     * @param {boolean} [confirmar=false]  true só após o usuário confirmar a execução
     */
    executarComando: async (empresaId, texto, modo, confirmar = false) => {
        const resposta = await apiFetch(`${ApiConfig.BASE_URL}/api/ia/assistente/comando`, {
            method: 'POST',
            headers: empresaHeaders(empresaId, true),
            body: JSON.stringify({ texto, modo, confirmar: !!confirmar })
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
