# Ambiente STAGING

Staging e isolado da producao e nao tem URL/DNS publico configurado por este repositorio.

- Aplicacao Spring Boot: porta 8081; teste local no servidor por `http://127.0.0.1:8081`.
- Diretorio de deploy: `/opt/brasil-saas-erp/staging`.
- Unit systemd: `brasil-saas-erp-staging.service`.
- Variaveis: `/etc/brasil-saas/staging.env`, criado a partir de `staging.env.example`.
- PostgreSQL: database exclusivo `brasil-saas-staging` e usuario exclusivo `brasil-saas-staging`.
- MongoDB: database `brasil-saas-staging`.
- Redis: database index 1.
- RabbitMQ: virtual host `/staging` e usuario exclusivo.
- MinIO: bucket `brasil-saas-staging`.
- Auth Service/AD desabilitados por padrao para evitar autenticacao acidental contra producao.
- URLs Stripe de sucesso/cancelamento ficam em localhost ate que o operador defina conscientemente uma URL de teste.

## Preparacao obrigatoria

1. Crie database e usuario PostgreSQL exclusivos, conceda as permissoes necessarias e inicialize o schema/migracoes em staging separadamente. O perfil desativa Flyway para evitar alteracoes automaticas; habilite-o somente se o processo de staging estiver preparado para migrar o banco isolado.
2. Crie database MongoDB, usuario/vhost RabbitMQ e bucket MinIO exclusivos.
3. Gere uma chave JWT exclusiva para staging. Nunca copie segredos de producao.
4. Instale o arquivo de ambiente com dono `root:root` e permissao `0600`.
5. Confirme que a porta 8081 nao esta publicada pelo proxy de producao.
6. Instale a unit systemd e valide `curl -fsS http://127.0.0.1:8081/actuator/health`.

Nenhum dominio, IP publico, Nginx ou URL de producao deve ser definido automaticamente para staging. O workflow de build nao reinicia nem substitui o servico de producao.
