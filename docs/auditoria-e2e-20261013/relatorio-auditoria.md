# Auditoria E2E — Brasil SaaS ERP

- **Data:** 2026-10-12/13 (UTC) · **Ambiente:** produção via Cloudflare Tunnel
- **URLs:** prod `https://feelings-persian-globe-correlation.trycloudflare.com` (anterior: `scholars-jesus-batch-acne`) · staging `https://org-import-found-army.trycloudflare.com`
- **Usuário:** emilly (id 221, perfis DIRETORIA/USUARIO, empresa 1 SRVCLOUD CONSULTORIA)
- **Build:** branch `hardening/jpa-migration` ( ngãiy até `a86c49d2` no deploy; fixes até `067a146e`/`f9e2d6c9`/`61e96a15`/`a86c49d2` commitados, reteste pós-deploy pendente)
- **Método:** navegador real + hooks de console/rede, sondas de API somente-leitura, inspeção de código/logs/banco; **nenhuma escrita em produção** (sem autorização).

## Resumo executivo

| Métrica | Qtd |
|---|---:|
| Testes executados | 58 |
| Aprovados | 43 |
| Reprovados (defeitos) | 10 bugs + 3 infra |
| Bloqueados (perfil) | 9 |
| Não executados (sem autorização de escrita / dependências) | 12 |
| Defeitos críticos | 1 (INFRA-001, corrigido e retestado) |
| Defeitos alta | 4 (BUG-001..004; 002–004 corrigidos, reteste pendente) |

O sistema está **operacional**: login, navegação, 30+ telas de leitura, paginação/ordenação, i18n EN, BI/ETL com dados íntegros (775 registros) e saúde geral UP (db/mongo/redis/rabbit após correção).

## Achados por módulo

- **Auth:** login válido/inválido, sessão, logout e refresh negativo OK. Logout API 404 (BUG-006). Erro cru axios no login inválido (BUG-008).
- **Cadastros:** produtos OK completo (175 itens, paginação/ordenação). Clientes/fornecedores/pessoas e filtros auxiliares 403 para o perfil (correto).
- **Vendas/Estoque/Financeiro/Fiscal(básico)/RH/Produção/Serviços/CRM/Helpdesk/Agenda/DMS/Projetos/Qualidade/Ativos/Go-live:** telas de leitura OK com dados reais.
- **Contabilidade/Enterprise/PLM/EHS/Supply:** endpoints 200 (majoritariamente vazios), telas carregam.
- **Fiscal NFe:** 404 por conditional sefaz (BUG-001) — emissão e LEITURA desligadas juntas.
- **BI:** ETL íntegro no `brasil_saas_bi` (275/116/268/116), views OK, Graylog no ar, datasource dedicado desligado (opt-in), dashboards bloqueados para o perfil (correto). Reports 500 corrigido no código (BUG-002).
- **Admin:** telas sob controle de acesso correto; logo 404 corrigido no frontend (BUG-005); `/api/core/usuarios|empresas` sem mapping (BUG-007).
- **i18n:** EN funcional com 7 labels sem tradução (BUG-009); ES/FR com arquivos completos, não percorridos na UI.

## Correções realizadas (branch hardening/jpa-migration)

- `61e96a15` BUG-003 (CRM pendentes) · `f9e2d6c9` BUG-002 (reports lazy) · `067a146e` BUG-004 (romaneios lazy) · `a86c49d2` BUG-005 (logo URL)
- Infra: LDAP auth-service → 127.0.0.1:636 + CA no cacerts (login 200 retestado) · RabbitMQ reiniciado (health UP)

## Atualização 2026-10-13 (bugs restantes)

- BUG-007 reclassificado: falso-positivo de sonda própria; nenhum caller real usa as bases inexistentes.
- BUG-009 corrigido: 19 labels hardcoded → `t('nav.*')` + 26 chaves × 4 locales; `i18n-locale.test.js` verde; reteste visual pós-deploy pendente.
- BUG-010 corrigido sem deletes: 58 relatórios SEED preservados; `agendado=false` (29 pais) + `ativo=false` (29 agendamentos); 0 execuções pendentes de SEED restantes.

## Pendências

1. Deploy com os 4 fixes p/ reteste (aguarda autorização).
2. BUG-001/006/007/008/009/010 em aberto.
3. Testes de escrita (CAD-003+, fluxos P2P/O2C, fiscal real, pagamentos) — exigem ambiente de homologação ou autorização.
4. ES/FR na UI, expiração total de sessão, isolamento multi-empresa.
5. mTLS dedicado do BI sem certificado próprio; ETL sem automação (manual).
