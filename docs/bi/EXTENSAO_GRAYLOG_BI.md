# Extensão de consultas do BI para Graylog 7.1.9

## Arquitetura implementada

O plugin registra /brasil-saas-bi e uma entrada Brasil SaaS BI no frontend Graylog.
Mostra financeiro, produção, vendas e serviços, com seleção de áreas e período.
O módulo ERP continua exibindo seu resumo e abre a apresentação Graylog em uma
janela vinculada à sessão corrente. Nenhuma função paga, entrada GELF, cópia de
banco ou conexão PostgreSQL direta no Graylog foi adicionada.

As consultas passam pelo ERP: a janela Graylog envia área/período via postMessage;
o ERP valida origem, janela, identificador de sessão, área e datas, executa somente
GET /api/bi/resumo/{area} com sua autenticação existente e retorna indicadores.
O Graylog nunca recebe JWT, senha, SQL, empresaId ou credenciais PostgreSQL. Empresa
e permissão continuam determinadas no servidor ERP. O canal limita concorrência,
repetições e frequência. Fecha e cancela consultas ao sair do módulo ou trocar empresa.

A janela do ERP deve permanecer aberta. Esta primeira integração usa uma janela
Graylog vinculada ao módulo, sem alterar iframe/CSP, CORS ou cabeçalhos de segurança.
Se o navegador/proxy impedir o vínculo entre janelas, a conexão informa falha;
não remover cabeçalhos de segurança para contornar sem avaliar a política local.
Os cartões não são os widgets de índices nativos: são uma página da extensão.
O datalake continua sendo as views seletivas brasil_saas_dl da V197 no PostgreSQL.

## Configuração real do dc-erp

O arquivo fornecido usa /etc/graylog/server/server.conf, Graylog na porta 9002,
http://192.168.2.10:9002/ e plugin_dir=/usr/share/graylog-server/plugin.
Consultas à porta 9000 não demonstram disponibilidade da API da porta 9002.
selfsigned_startup configura certificados entre servidor e Data Node e não deve
ser removido para implementar BI. mongodb_uri permanece: MongoDB guarda os
metadados necessários ao Graylog, não os indicadores do ERP. A integração de
observabilidade foi adiada pelo usuário e não faz parte desta entrega.

O instalador pode ajustar plugin_dir e http_external_uri no server.conf original,
com backup privado e preservação do dono, permissões e demais bytes (Latin-1).
Preserva senha, password_secret, MongoDB, bind/publish existentes, Data Node,
portas do Nginx e configuração de observabilidade. Se os valores já estiverem
corretos, não reescreve o arquivo. Não versionar o server.conf real ou suas senhas.

## Build e instalação

O workflow Validate Graylog BI query extension compila em runner GitHub hospedado
e fornece o artefato brasil-saas-graylog-bi-7.1.9. Prefira baixar esse artefato para
não consumir memória do dc-erp com a compilação do frontend Graylog.
Após o merge, atualizar o repositório e usar o JAR baixado desse workflow:

```bash
cd ~/brasil-saas-erp
git pull --ff-only
sudo python3 scripts/bi/install-graylog-bi.py --jar /caminho/brasil-saas-graylog-bi-1.0.0.jar --web-url http://192.168.2.10:9002/
```

Sem --apply apenas valida. Para instalar, ajustar a configuração necessária e
reiniciar SOMENTE o Graylog (haverá interrupção breve desse serviço):

```bash
sudo python3 scripts/bi/install-graylog-bi.py --jar /caminho/brasil-saas-graylog-bi-1.0.0.jar --web-url http://192.168.2.10:9002/ --apply --restart
sudo python3 scripts/bi/configure-graylog.py --config-file /etc/graylog/server/server.conf --web-url http://192.168.2.10:9002/ --apply --login
```

O segundo comando solicita credenciais do Graylog localmente, se necessárias,
e registra a conexão do ERP. Não salva a senha. Os JARs do ERP de staging e produção
precisam conter a atualização do módulo; este instalador não os compila ou reinicia.
Usar o fluxo de build/deploy existente e validar primeiro no staging.

Para compilar por conta própria, com Git, Node 22+, Java 21 e Maven:

```bash
python3 scripts/bi/build-graylog-bi.py
```

Esse comando fixa fontes Graylog 7.1.9 no commit
002c047579cebd085973470afe5cb7170073c8da e usa o lockfile oficial. Compila o vendor
compatível e somente o bundle do plugin; não compila o ERP nem instala serviços.
O JAR fica em integrations/graylog-bi/target/brasil-saas-graylog-bi-1.0.0.jar.
Não usar mvn clean após gerar o bundle: isso o apaga; a regra Maven recusa JAR sem web.

## Validação funcional obrigatória

1. No staging, autenticar usuário administrador da empresa A; abrir BI e Indicadores no Graylog.
2. Fazer login no Graylog antes de abrir o painel se necessário. Confirmar estado conectado.
3. Selecionar só Vendas e consultar; deve ocorrer somente a requisição /resumo/vendas no ERP.
4. Comparar as três métricas ao resumo ERP do mesmo período e empresa.
5. Repetir nas outras áreas, incluindo um intervalo vazio e erro de serviço; erro nunca vira zero.
6. Trocar empresa no ERP: janela antiga deve fechar, consulta pendente deve ser cancelada.
7. Reabrir com empresa B; validar valores sem dados da empresa A. Sair do módulo fecha o vínculo.

Testes automatizados: protocolo Node (origem, janela, nonce, período, área,
replay, frequência, erro, cancelamento e troca host/cliente), instalador Python
(JAR, recursos web, backups, repetição, links simbólicos, preservação de config)
e descoberta Python. Build frontend ERP e plugin complementam esses testes.
Compilação não substitui validação autenticada no Graylog real.
