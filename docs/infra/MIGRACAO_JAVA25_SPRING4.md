# Java 25 e Spring Boot 4 — preparação verificável

> Documento da etapa de planejamento. A implementação e as instruções atuais estão em [JAVA25_SPRING4_JASPER_IMPLANTACAO.md](../infra/JAVA25_SPRING4_JASPER_IMPLANTACAO.md).

Estado: preparação automatizada, não homologada. Não executar deploy do candidato
antes de adaptar APIs e observar compilação, testes e inicialização aprovados.

## Scripts alterados

`compilar.py --prepare-upgrade DESTINO` cria uma worktree separada a partir de
HEAD e prepara POM, Dockerfile e Java dos workflows. A árvore original, serviços,
portas, credenciais e banco não são alterados. O destino deve ser novo.

```bash
python3 compilar.py --prepare-upgrade ../brasil-saas-java25-spring4
# Com JDK 25 selecionado em JAVA_HOME/PATH e Maven disponível:
python3 compilar.py --prepare-upgrade ../brasil-saas-java25-spring4-validacao --verify-upgrade
```

O segundo comando executa `mvn -B clean verify` e devolve falha quando o Maven
falha; não instala JAR, não executa migrations em produção e não inicia serviços.
Não usar senhas/dumps de produção no ambiente de teste.

Alvos fixados: Java 25, Boot 4.1.1, Cloud 2025.1.2, Spring AI 2.0.1 e Springdoc
3.1.1. O Flyway passa ao gerenciamento do Boot e ganha starter próprio.
Jackson 2 é adicionado como ponte temporária para integrações existentes;
não representa a conclusão da migração para Jackson 3.

`installbase.sh` lê `java.version` do POM e seleciona JDK completo dessa versão.
`ERP_JAVA_HOME` pode apontar para um JDK já instalado. O script não executa
`alternatives --set` nem `sdk default`. Não rodar o instalador completo apenas
para validar a migração: ele continua sendo um provisionador de infraestrutura.

## Trabalho que a preparação não resolve

- Imports movidos de autoconfiguração/testes no Boot 4, como EntityScan.
- Spring Security 7, serialização Jackson 3, integração JWT e clientes HTTP.
- Hibernate/Envers: isolamento por empresa, consultas, tipos e auditoria.
- Spring AI: APIs alteradas desde M3, configuração OpenRouter e tratamento de erro.
- Flyway em base nova e atualizada; nenhuma migration histórica será reescrita.
- Bibliotecas fiscais: XML, assinatura e HTTPS em homologação.
- Geradores e scripts antigos de módulos ainda declaram versões antigas;
  não usá-los para regenerar o monólito migrado.
- CI de integração Graylog: preservar o JDK exigido pelo Graylog, não impor JDK 25
  ao processo do Graylog. Workflows cujo nome contém graylog preservam o JDK original; revisar outros jobs mistos.

## Homologação necessária

Executar `mvn clean verify` e, no frontend canônico, `npm ci`, `npm test` e
`npm run build`. Depois testar login, permissões, duas empresas, cadastros,
compras/recebimento/estoque, vendas/faturamento/devolução, financeiro/baixas/
estornos, contabilidade, produção, serviços, relatórios e integrações.
Registrar cenário, resultado esperado/observado, stack trace sem segredos,
empresa, versão, evidência e reprodução. Compilar não equivale a homologar ERP.

Referências: https://docs.spring.io/spring-boot/system-requirements.html,
https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide,
https://spring.io/projects/spring-cloud/,
https://docs.spring.io/spring-ai/reference/getting-started.html,
https://springdoc.org/.
