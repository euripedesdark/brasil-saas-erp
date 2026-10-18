import { api } from './ApiConfig';

const USER_STORAGE_KEY = 'brasil-saas_user';
const AUTH_SOURCE_STORAGE_KEY = 'brasil-saas_auth_source';

export const authService = {
    async login(credentials, provider = 'ad') {
        try {
            const route = provider === 'database' ? '/auth/login/database' : '/auth/login/ad';
            const authSource = provider === 'database' ? 'DB' : 'AD';
            const response = await api.post(route, { ...credentials, provider: authSource });
            const result = response.data;

            if (result?.data?.accessToken) {
                const loginData = result.data;

                localStorage.setItem('brasil-saas_token', loginData.accessToken);
                if (loginData.refreshToken) {
                    localStorage.setItem('brasil-saas_refresh_token', loginData.refreshToken);
                }

                localStorage.setItem(AUTH_SOURCE_STORAGE_KEY, loginData.authSource || authSource);

                const user = {
                    id: loginData.usuarioId,
                    usuarioId: loginData.usuarioId,
                    empresaId: loginData.empresaId,
                    username: loginData.username,
                    authorities: loginData.authorities || [],
                    authSource: loginData.authSource || authSource
                };

                localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
                api.defaults.headers.common['Authorization'] = `Bearer ${loginData.accessToken}`;

                return user;
            }

            throw new Error(result?.message || 'Falha na autenticação');
        } catch (error) {
            console.error(`Erro de login (${provider}):`, error);
            const message =
                error.response?.data?.message ||
                error.response?.data?.data?.message ||
                (error.response?.status === 401 || error.response?.status === 422
                    ? 'Usuário ou senha inválidos'
                    : error.response?.status >= 500
                        ? 'Serviço de autenticação indisponível. Tente novamente mais tarde.'
                        : error.response
                            ? 'Não foi possível autenticar. Verifique os dados e tente novamente.'
                            : 'Não foi possível conectar ao servidor de autenticação.');
            throw new Error(message);
        }
    },

    async logout() {
        try {
            // O token continua válido no cliente até expirar (não há revogação
            // no servidor), mas o cookie de sessão — o que o iframe do BI usa
            // para carregar — é apagado aqui. Sem isso o dashboard do BI
            // continuaria abrindo depois da saída.
            await fetch('/api/auth/logout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
            }).catch(() => {});
        } finally {
            localStorage.removeItem('brasil-saas_token');
            localStorage.removeItem('brasil-saas_refresh_token');
            localStorage.removeItem(USER_STORAGE_KEY);
            localStorage.removeItem(AUTH_SOURCE_STORAGE_KEY);
            delete api.defaults.headers.common['Authorization'];
        }
    },

    async getCurrentUser() {
        const token = localStorage.getItem('brasil-saas_token');
        if (!token) return null;

        try {
            api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
            const storedUser = localStorage.getItem(USER_STORAGE_KEY);

            if (storedUser) {
                return JSON.parse(storedUser);
            }

            return null;
        } catch (error) {
            console.error('Erro ao restaurar sessão:', error);
            localStorage.removeItem('brasil-saas_token');
            localStorage.removeItem('brasil-saas_refresh_token');
            localStorage.removeItem(USER_STORAGE_KEY);
            delete api.defaults.headers.common['Authorization'];
            return null;
        }
    },

    async loadProfile() {
        const token = localStorage.getItem('brasil-saas_token');
        if (!token) return null;

        try {
            api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
            const response = await api.get('/auth/me');
            const profile = response.data?.data ?? response.data;

            if (profile) {
                const current = JSON.parse(localStorage.getItem(USER_STORAGE_KEY) || '{}');
                const merged = { ...current, ...profile };
                localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(merged));
                return merged;
            }

            return null;
        } catch (error) {
            console.error('Erro ao carregar perfil:', error);
            return null;
        }
    }
};
