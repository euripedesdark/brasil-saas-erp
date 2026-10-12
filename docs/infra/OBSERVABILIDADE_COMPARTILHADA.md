# ERP e Astral: uma instalação de observabilidade por host

## Evidência e limites

O top fornecido mostra dois mongod (1104 e 1354), uma JVM do usuário do ERP
(160318) e uma JVM graylog (2839). Não prova dois servidores Graylog. MongoDB
duplicado pode ter dados distintos: identificar dbPath, porta, unit e container
antes de consolidar. Não matar PID nem apagar dados automaticamente.

No Astral, `scripts/install-graylog.sh` regravava segredo/configuração e procurava
outra porta ocupada. No ERP, `scripts/brasilsaas-observability.sh` recriava segredo,
usava senha fixa, reiniciava Graylog e provisionava Elasticsearch 8/MongoDB 6.
Não era uma política de reutilização nem uma matriz atual validada.

## Mudanças

Os dois scripts usam `scripts/shared-observability.sh` e o mesmo lock
`/run/lock/brasilcloud-observability.lock`. Uma unit existente é reutilizada;
inativa é iniciada sem regenerar configuração. Processo sem a unit esperada ou
múltiplos processos interrompem o instalador, sem iniciar uma cópia ou encerrar
a existente. O ERP não provisiona outra pilha Graylog/Mongo/search backend.
No Astral, a verificação ocorre antes de pedir credenciais ou escolher porta.
Graylog Data Node não é contado como segundo Graylog Server.

Não atualizar o JDK interno do Graylog junto com o ERP. Usar o Java que os
pacotes oficiais do Graylog fornecem e a matriz da versão instalada. MongoDB
continua necessário ao Graylog mesmo que não seja usado como banco do BI.
ERP/Astral podem compartilhar a instância Mongo, mas com bancos/usuários e
permissões separados; compartilhar processo não significa compartilhar dados.

## Diagnóstico local (somente leitura)

```bash
pgrep -x mongod
systemctl show mongod graylog-server graylog-datanode -p MainPID -p LoadState -p ActiveState
systemctl list-units --type=service --all | rg -i 'mongo|graylog|astral|brasil'
sudo ss -lntp | rg ':27017|:27018|:9000|:9001|:9002'
docker ps --format '{{.Names}} {{.Image}} {{.Ports}}'
free -m
vmstat 1 5
```

Consultar localmente cmdline, arquivos de configuração e dbPath dos dois PIDs;
não colar URIs/senhas no relatório. Swap ocupada não comprova swap ativa:
observar `si/so` no vmstat antes de atribuir lentidão. O objetivo é reduzir
duplicações, não garantir um número de MB sem medição.

Referência: https://go2docs.graylog.org/current/downloading_and_installing_graylog/compatibility_matrix.htm
