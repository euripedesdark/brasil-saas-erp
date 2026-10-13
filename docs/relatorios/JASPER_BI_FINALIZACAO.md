# Finalização Jasper + BI — BrasilCloud ERP

**Data:** 2026-10-13  
**Escopo:** identidade visual dos relatórios Jasper e consolidação do módulo BI.

## Identidade visual (templates JRXML)

Paleta alinhada ao sistema e aos memorandos de planejamento:

| Token | Cor | Uso |
|-------|-----|-----|
| Primary | `#2c3e50` | Barra de marca, cabeçalho de colunas |
| Accent | `#3498db` | Faixa sob a marca, título do bloco RESUMO |
| Surface | `#f8f9fa` | Fundo de linhas / caixa de resumo |
| Text | `#1e293b` / `#475569` / `#64748b` | Corpo, metadados, rodapé |
| Divider | `#e2e8f0` | Separadores de seção |

### Templates atualizados

- `reports/brasilcloud/gerencial.jrxml` — landscape dinâmico (colunas via renderer)
- `reports/brasilcloud/memorando.jrxml` — portrait institucional
- `reports/brasilcloud/vendas.jrxml`
- `reports/brasilcloud/financeiro.jrxml`
- `reports/brasilcloud/producao.jrxml`
- `reports/brasilcloud/dre.jrxml`
- `reports/brasilcloud/fluxo_caixa.jrxml`

Padrão de layout:

1. Barra de marca **BrasilCloud ERP** + módulo
2. Faixa accent `#3498db`
3. Título, subtítulo/período, data de geração, empresa
4. Cabeçalho de colunas em `#2c3e50` com texto branco
5. Linhas com fundo `#f8f9fa`, valores monetários alinhados à direita
6. Rodapé com identificação + página
7. Bloco **RESUMO** com faixa accent e fundo claro

Fonte: **DejaVu Sans** (suporte a acentos PT-BR; alinhado ao exporter PDFBox).

## Jasper — o que está fechado

- `JasperReportRenderer` + `JasperPdfBoxExporter` (sem OpenPDF antigo)
- PDFs gerenciais, OS, memorando e BI não-fiscal via Jasper
- Tipos tipados: VENDAS, FINANCEIRO, PRODUCAO, DRE, FLUXO_CAIXA
- Limites: 10k linhas, 20 colunas, 500 páginas, 2 slots, virtualizer
- API memorando: `POST /api/relatorios/memorando/pdf`
- UI: Centro de Relatórios + `MemorandoDialog` (PT/EN/ES/FR)
- Testes: `JasperReportRendererTest`, `JasperSpecificTemplatesTest`

Fiscal permanece em OpenPDF (decisão de produto).

## BI — estado consolidado

| Área | Status |
|------|--------|
| Controllers (Dashboard, KPI, Indicador, Relatorio, Report, Agendado, Graylog, Resumo) | Implementado |
| Models + repositories + datalake dimensional | Implementado |
| Export PDF (Jasper) / Excel / CSV / HTML | Implementado |
| SQL parametrizado seguro (`ReportSqlParameters`) | Implementado |
| Agendamento de relatórios | Implementado |
| DimTempo + fatos (vendas, compras, estoque, financeiro, produção) | Modelagem pronta |
| ETL completo / carga automática do datalake | Operacional no host (não é gap de template Jasper) |

## Como validar após pull

```bash
mvn -q -Dtest=JasperReportRendererTest,JasperSpecificTemplatesTest test
```

Gerar PDF pela UI (Centro de Relatórios) ou:

```http
GET /api/relatorios/pdf/VENDAS?dataInicio=2026-01-01&dataFim=2026-10-13
Authorization: Bearer <token>
```

## Observações

- Templates são recursos do classpath; o renderer **não** aceita JRXML externo nem Connection/SQL do cliente.
- Empresa/tenant vêm sempre do usuário autenticado.
- Identidade visual consolidada nesta entrega; ajustes finos de logo da empresa emitente podem usar o serviço de logos existente sem alterar o layout base.
