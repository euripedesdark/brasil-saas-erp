# Inventário verificado de microserviços e bibliotecas — 2026-10-11

Base de código: main, commit 664d5b705876b21257afe36858bad32750d29455.
Verificação do servidor dc-erp: 2026-10-11 19:24 UTC / 16:24 Brasília.
[Execução de diagnóstico](https://github.com/euripedesdark/brasil-saas-erp/actions/runs/38167863558).

“Integrado” significa que há dependência/importação ou chamada HTTP no código. Não comprova uma emissão fiscal bem-sucedida. “Não demonstrado” significa que esta inspeção não encontrou integração; não é autorização para apagar a pasta.

## Classificação das 21 pastas

Todas as pastas abaixo estão em src/main/resources/microservices/.

| Pasta | Papel verificado | Evidência e limite |
| --- | --- | --- |
| EchoAvatar | Projeto auxiliar, integração não demonstrada | Código de avatar separado; nenhuma referência encontrada no backend Java inspecionado. |
| Java-Efd-Contribuicoes | Biblioteca utilizada via Maven | pom.xml declara java-efd-contribuicoes 1.32.1; EfdContribService importa e gera registros. A cópia local não é o mecanismo de inclusão no JAR. |
| Java-Efd-Icms | Biblioteca utilizada via Maven | java-efd-icms 3.21.1; SpedEfdIcmsService, EfdPeriodoService e SpedEfdController. |
| Java_CTe | Biblioteca declarada no build | java-cte 4.00.14 no POM. Os serviços CTeEmissaoService/CTeOperacao usam classes Fincatto; execução da biblioteca SW não foi demonstrada pelos imports. |
| Java_Certificado | Biblioteca usada pela integração fiscal | Classes de certificado nos configs SefazConfig e DynamicNFeConfig; dependência transitiva da família java-nfe. |
| Java_MDFe | Referência da biblioteca alternativa | Nesta revisão a pasta tem somente .gitignore, LICENSE e README. O ERP implementa MDF-e com a biblioteca Fincatto. |
| Java_NFe | Biblioteca utilizada via Maven | java-nfe 4.1.3; chamadas reais em NFeServiceImpl, NFeXmlBuilder e SefazConsultaService. |
| Java_Pdf_Signature | Utilitário disponível, integração não demonstrada | Fonte e JAR local; nenhuma dependência ou importação correspondente encontrada no backend. |
| NFSe-SaoPaulo-SP | Especificações e schemas fiscais | Material de referência PDF/XSD. Não é um processo de serviço. Não foi comprovado carregamento diretamente desta pasta pelo ERP. |
| PL_MDFe_300b_NT012025_1.05 | Especificações e schemas fiscais | Material de leiaute MDF-e; não é serviço executável. |
| boleto-cnab-api | Serviço externo integrado por HTTP | BoletoCnabClient usa brasil-saas.cnab.url, padrão localhost:9292. Porta local fechada no diagnóstico; endpoint remoto configurado não foi inspecionado. |
| esocial | Serviço externo integrado ao RH | EsocialFilaService envia POST /ocorrencias e consulta GET /ocorrencias/{id}, via ESOCIAL_URL, padrão localhost:8081. Porta local fechada; implantação remota e frontend próprio não foram comprovados. |
| nfe | Biblioteca Fincatto utilizada via Maven | com.github.wmixvideo:nfe no POM; imports em CTe/MDF-e. Versão do ERP 5.1.2; cópia local 5.1.3-SNAPSHOT, portanto não presumir equivalência dos alertas por linha. |
| nfse-failover | Componente opcional de implantação | Instalador contém configuração de failover. Nenhuma unit correspondente apareceu no levantamento de units do servidor. |
| nfse-nacional-api | Serviço HTTP integrado ao ERP | NfseEmissaoService aponta por padrão para 127.0.0.1:4580/api/nfse-nacional. Projeto Spring Boot separado; porta 4580 não foi testada nesta execução. |
| nfse-sp-api | Serviço HTTP integrado, instalado com falha | NfseEmissaoService usa por padrão 127.0.0.1:4567/api/nfse-sp. Unit habilitada, arquivo de execução ausente no caminho configurado e portas 4567/4568 fechadas. |
| nfse-sp-bridge | Serviço Ruby de alternativa ao Java | Instalador e unit próprios; run-bridge.sh. Unit habilitada com caminho ausente; porta 4569 fechada. |
| nfse-watchdog | Supervisão operacional | Unit brasil_saas-watchdog habilitada, porém script configurado ausente e reinicializações repetidas. Não classificar como documentação. |
| nfse | Cliente/servidor alternativo, uso atual não demonstrado | Projeto independente; não há dependência no POM principal. A integração nacional atual aponta para nfse-nacional-api. |
| nfse_prefeitura_sp | Biblioteca Ruby usada pelo bridge | Gemfile/Gemfile.lock do nfse-sp-bridge referenciam ../nfse_prefeitura_sp; app.rb carrega a gem. |
| spring-ai | SDK usado via Maven; cópia local de fonte | Starter 1.0.0-M3 no POM; ChatClientConfig, ChatServiceImpl e AssistenteService importam Spring AI. A árvore local não é compilada como parte do backend principal. |

Também há schemas MDF-e nas pastas src/main/resources/microservice/PL_MDFe_300b_NT012025_1.04 e ...1.05, com “microservice” no singular. São material de leiaute; existência de duplicatas não autoriza remoção sem conferir os consumidores.

O POM exclui microservices/** dos recursos do JAR principal. Essa exclusão não elimina dependências Maven nem serviços executados separadamente. Portanto, não sustenta excluir indiscriminadamente a pasta inteira da análise de segurança.

## Falha operacional comprovada

| Unit | Reinicializações na observação | Arquivo configurado existe? | Estado |
| --- | ---: | --- | --- |
| nfse-sp-api.service | 1.311 | Não | activating, Result=exit-code, ExecMainStatus=200 |
| nfse-sp-bridge.service | 1.313 | Não | activating, Result=exit-code, ExecMainStatus=200 |
| brasil_saas-watchdog.service | 1.314 | Não | activating, Result=exit-code, ExecMainStatus=200 |

As units usam WorkingDirectory e artefatos sob /home/euripedes/BrasilCloudERP, diretório inexistente. O checkout existente está em /home/euripedes/brasil-saas-erp. Isso é evidência direta de configuração obsoleta e arquivos ausentes nos caminhos usados para inicialização.

Portas locais 4567, 4568, 4569, 9292 e 8081 fechadas; os três GET locais /api/nfse-sp/status falharam na conexão. Porta 8083 aberta, sem identificação suficiente para atribuí-la a um destes serviços. Processos Java de produção, staging e autenticação foram observados; isso não valida todos os módulos fiscais.

Esta inspeção não corrigiu as units. O reparo deve verificar os artefatos no destino real, configurações privadas, usuário de execução e limites de memória antes de ajustar caminhos e reiniciar. Habilitar mais JVMs sem esse exame pode agravar a falta de memória já relatada.

## Consequências para o Qodana

SARIF fornecido: revisão fbaf3f7574d09e1d4900580faac69363351cc276, branch qodana-automation-1306042537. Não é exatamente o commit main inspecionado acima.

- 7.089 achados: 47 críticos, 1.053 altos, 5.989 moderados.
- Backend src/main/java: 697 achados, incluindo 4 críticos e 533 altos.
- Árvores microservices e microservice: 5.816 achados, incluindo 19 críticos e 378 altos.
- Outros caminhos: 576 achados.

A presença em uma cópia de biblioteca deve ser confrontada com a versão Maven usada. Código de serviço separado continua relevante mesmo quando não entra no JAR do ERP. Schemas/documentação podem receber tratamento próprio para inspeções de estilo, preservando validação XML e análise de segurança aplicável.

Prioridades de investigação:

1. Backend próprio: prefixo duplicado brasil-saas.auth-service em duas classes AuthServiceProperties; conferir consumidores e defaults antes de consolidar.
2. Vulnerabilidades de dependências efetivamente resolvidas pelo Maven, usando versões e CVEs concretos.
3. Serviços fiscais integrados, sobretudo a disponibilidade do NFS-e comprovadamente quebrada.
4. Achados no frontend eSocial: conferir versões resolvidas e se esse frontend é implantado, sem confundir uso do serviço Java pelo RH com uso do frontend próprio.
5. Achados de estilo em fontes de terceiros e schemas, separados dos problemas operacionais.

O alerta de senha na linha 464 de install_ruby_certificados_banco.sh corresponde ao exemplo comentado mongodb://admin:SENHA@..., não a uma credencial real demonstrada. O aviso de JavaMailSender sem bean exige conferir a autoconfiguração e o ambiente; isoladamente não comprova erro de inicialização.

Nenhuma supressão global do Qodana foi aplicada por este inventário. A coleta usou consultas a systemd, caminhos de artefatos, portas locais e endpoints de status; não realizou emissão fiscal nem expôs variáveis privadas.
