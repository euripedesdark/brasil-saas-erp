# Relatório de Usabilidade — Brasil SaaS ERP (visão de usuário)

- **Data:** 2026-10-13 · **Perfil:** emilly (DIRETORIA/USUARIO, empresa 1)
- **Método:** jornada real de usuário no navegador (cliques, formulários, diálogos) + DevTools (rede/console) + verificação de API
- **Escopo de escrita:** apenas operações permitidas ao perfil; sem emissão/importação de nota; 1 resíduo de teste (meta id 1, sem DELETE disponível)

## Jornadas executadas ponta a ponta

### UJ-A — Criar contrato (Gestão Empresarial)
1. Aba Contratos → Novo abre diálogo com 10+ campos texto (Status, Descrição, Observação, Tipo, Número, Parceiro, ID parceiro, Início, Fim, Valor...).
2. Salvar vazio → **POST 409 + mensagem crua** (UJ-001). Sem validação client-side.
3. Preenchido (TESTE-QA/QA-USAB-001/FORNECIMENTO) → **POST 200**, linha aparece (RASCUNHO, R$ 0,00). Sem toast de confirmação (UJ-003).
4. Linha **sem botões de ação**; clique na linha não abre edição (UJ-002).
5. Edição e exclusão via API: PUT 200 (descricao/valor persistidos) → DELETE 200 → lista `[]`. Sem resíduos.

### UJ-B — Criar meta (Metas comerciais)
1. Aba → "Nova meta" abre diálogo (Ano, Mês, Canal, Valor meta em InputNumber).
2. Valor digitado `5000` **não foi persistido** (gravou 0.00) — UJ-004 (suspeita front/back, retestar).
3. Registro criado (id 1, POST 201). **DELETE retorna 405** — sem como remover (UJ-005). Resíduo: 1 meta TESTE-QA.

## Classificação front-end × back-end

| ID | Achado | Camada | Detalhe |
|---|---|---|---|
| UJ-001 | Sem validação client-side; erro cru; 409 em vez de 400 | Ambos | Front não valida; back valida via exceção genérica que cai em 409 (constraint) em vez de 400 |
| UJ-002 | Linhas sem editar/excluir na UI | Frontend | API tem PUT/DELETE; UI não expõe ações |
| UJ-003 | Sem toast de sucesso ao criar | Frontend | Diálogo fecha + linha aparece, sem confirmação |
| UJ-004 | Valor da meta zerado | A apurar | InputNumber pode não submeter, ou API ignora campo |
| UJ-005 | Metas sem DELETE (405) | Backend | Sem remoção/correção de erro do usuário |
| UJ-006 | "Novo Contratos" (gramática) | Frontend | Título do diálogo |
| UJ-007 | Empty states em inglês ("No available options") | Frontend | chaves i18n ausentes nos componentes de tabela |
| UJ-008 | Coluna Cliente exibe ID (1, 9053...) | Frontend (com causa back) | Sem permissão de leitura, front mostra ID cru; deveria ocultar ou exibir "—" |

## Review de design por tela (opinião técnica)

**Aprovado, manter:** login (pb elegante, hierarquia clara), dashboard (cards consistentes), tabelas dark (contraste bom, badges de status legíveis).

**Precisa melhorar:**
1. **Sidebar colapsada corta rótulos no meio** ("Ad…", "Das…", "en/US…") — usar modo só-ícones com tooltip ou largura mínima (todas as telas).
2. **Header sobreposto**: "Sistema de Gestão Empresarial" sobrepõe o logo "Brasil SaaS ERP" (z-index/espaçamento).
3. **Tabelas estouram a largura** (colunas cortadas à direita, ex. Títulos/Contratos) — falta scroll-x visível ou colunas responsivas.
4. **Diálogo NovoContratos ocupa a tela toda** sem max-width; 10 campos texto corridos sem agrupamento, sem máscaras (data, moeda) e sem indicar obrigatórios.
5. **Dados SEED em produção** (SEED NUMERO/DESCRICAO, caixas NOME-1) poluem todas as listas — ambiente produtivo com cara de homologação.
6. **Botão flutuante roxo** (assistente) sobrepõe paginação no canto inferior direito.
7. **"—" em excesso**: colunas vazias sem tooltip; confundem com dado ausente vs. sem permissão.
8. **Contraste do texto auxiliar** (cinza sobre fundo claro no card de login) no limite da legibilidade.
