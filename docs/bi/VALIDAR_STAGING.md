# Preparar pacote e validar staging

O workflow Prepare validated BI package and inspect staging baixa o plugin validado
no main (run 38179676389) e prepara uma pasta privada no usuário do runner. Não
instala plugin, reinicia serviços ou modifica configuração do sistema pelo workflow.
O usuário executa localmente:

```bash
sudo bash /home/euripedes/.local/share/brasil-saas-bi-ready/activate-bi.sh
```

O comando instala o JAR validado, ajusta somente os campos permitidos de server.conf,
reinicia Graylog e pede login Graylog no terminal para registrar a conexão no ERP.
Preserva senhas, MongoDB de metadados, porta 9002, permissões e backups privados.
Não compila no dc-erp nem atualiza os JARs de produção/staging.

O validador usa exclusivamente HTTP local na porta 18081 e recusa banco diferente
de brasil_saas_staging no PostgreSQL local dedicado da porta 15432 antes de autenticar.
Usa a credencial privada staging-admin
existente e confere consultas seletivas, resumo e rejeição de acesso anônimo/área
inválida. Relatório somente de status HTTP/contrato: sem token, empresa, senha ou
valores financeiros. Falha real permanece falha. Também inspeciona estados systemd
e marcador de deploy sem ler journal ou imprimir segredos.

```bash
python3 scripts/bi/validate-staging-bi.py
python3 scripts/bi/test-validate-staging-bi.py
```

O build self-hosted anterior compilou com sucesso, mas aguardou deploy.status por
cinco minutos sem confirmação. Isso não comprova atualização no servidor. Não
reexecutar deploy de produção para contornar a falha sem diagnosticar o serviço.
Checagem de contrato não substitui teste visual ou comparação entre empresas.
Instalação e registro Graylog dependem da execução local com sudo e login.
