# Descoberta e configuracao local do Graylog

Executar no dc-erp, dentro da pasta do repositorio atualizado. Usa Python 3 padrao,
sem pip, containers novos, licencas ou servicos adicionais.

```bash
sudo python3 scripts/bi/configure-graylog.py --apply --login
```

O script busca server.conf nos caminhos padrao e na unit systemd, versao instalada,
porta de bind e URLs externas. Tambem procura portas publicadas de containers
Graylog sem ler variaveis de ambiente. Testa a API sem proxy de ambiente, preserva
validacao TLS e nao segue redirects com credenciais. Se a API exige autenticacao,
pede usuario e senha Graylog no terminal; nao e a senha sudo nem a senha do ERP.
Senha e token nao sao impressos ou salvos. Necessita usuario com acesso a /api/system.

Sem --login, pede token no terminal. Para automacao nao interativa:

```bash
sudo python3 scripts/bi/configure-graylog.py --apply --non-interactive --token-file /caminho/privado/graylog-token
```

O arquivo de token precisa ter permissao 600. Nunca passar segredo como argumento.
Para apenas diagnosticar, sem gravar: sudo python3 scripts/bi/configure-graylog.py.
Se o certificado usa uma CA privada, informar --ca-file /caminho/ca.crt.
Se a instalacao usa caminhos diferentes, informar --config-file /caminho/server.conf.
Para substituir enderecos, usar --api-url https://host/graylog/api/ e
--web-url https://host/graylog/. O endereco web deve funcionar no navegador do usuario;
localhost identifica a maquina onde o navegador esta, nao necessariamente o servidor.

## Resultado concreto

Ao validar a identidade Graylog em /api/system, grava atomicamente
/etc/brasil-saas/bi-graylog.properties (somente metadados, sem credenciais).
Reexecucao identica nao duplica arquivos; alteracoes preservam backup privado.
Falha de autenticacao ou resposta de outro aplicativo nao sobrescreve conexao anterior.
Codigo 0: diagnostico concluido ou configuracao salva. Codigo 2: --apply nao validou
acesso autenticado; codigo 1: parametros, arquivo ou permissao invalidos.

O JAR com esta alteracao importa o arquivo opcional no proximo inicio. Ausencia
nao impede subir o ERP. /api/bi/integracao/graylog, autenticado e restrito aos papeis
administrativos existentes do BI, informa registro da conexao sem expor API interna
ou credenciais. A tela oferece Abrir Graylog e distingue conexao registrada de
paineis integrados. Registro nao e teste de saude permanente.

## Limites desta etapa

Nao reinicia servicos, modifica a configuracao de seguranca do Graylog, troca portas
80/443, copia banco ou cria dashboards vazios fingindo consultas PostgreSQL.
Graylog 7.1.9 foi encontrado ativo no dc-erp, mas a leitura autenticada da configuracao
so pode ser concluida com acesso local autorizado. A extensao para consultas diretas
continua como proxima etapa: este script prepara e valida a conexao real do control plane.
Nao instala plugin nao testado nem atribui licenca paga.

## Testes

python3 scripts/bi/test-configure-graylog.py verifica descoberta, segredos, URLs,
HTTP autenticado, redirects, outro aplicativo, backups, repeticao e falhas.
CI executa esses testes e o build/testes Java e frontend usuais.
