#!/usr/bin/env bash
# Carga completa e atomica das quatro views de BI do ERP para o banco dedicado.
# Execute no host PostgreSQL com sudo -u postgres bash scripts/bi/sincronizar_reporting.sh
set -Eeuo pipefail
export PGOPTIONS="-c statement_timeout=0"
SOURCE_DB="${BI_SOURCE_DB:-brasil-saas}"
TARGET_DB="${BI_TARGET_DB:-brasil_saas_bi}"
WORKDIR="$(mktemp -d)"
chmod 700 "$WORKDIR"
trap 'rm -rf "$WORKDIR"' EXIT

# Extrai todas as fontes no mesmo snapshot MVCC, evitando misturar estados
# de tabelas de uma mesma carga. PostgreSQL \\copy nao oferece snapshot entre processos;
# uma unica transacao COPY para arquivos locais preserva consistencia.
cat > "$WORKDIR/export.sql" <<SQL
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
\\copy (SELECT empresa_id,tipo,valor_saldo,data_vencimento,status FROM brasil_saas_dl.v_bi_financeiro) TO '$WORKDIR/financeiro.csv' WITH (FORMAT csv, HEADER true)
\\copy (SELECT empresa_id,status,data_referencia,data_fim FROM brasil_saas_dl.v_bi_producao) TO '$WORKDIR/producao.csv' WITH (FORMAT csv, HEADER true)
\\copy (SELECT empresa_id,tipo,status,data_referencia,valor_total FROM brasil_saas_dl.v_bi_vendas) TO '$WORKDIR/vendas.csv' WITH (FORMAT csv, HEADER true)
\\copy (SELECT empresa_id,status,valor_total,data_abertura,data_fechamento,data_previsao FROM brasil_saas_dl.v_bi_servicos) TO '$WORKDIR/servicos.csv' WITH (FORMAT csv, HEADER true)
COMMIT;
SQL

psql -X -v ON_ERROR_STOP=1 -d "$SOURCE_DB" -f "$WORKDIR/export.sql"

# A substituicao so ocorre apos extracao bem-sucedida de TODAS as fontes.
# Se uma carga falhar, o ROLLBACK preserva a versao anterior do BI.
cat > "$WORKDIR/import.sql" <<SQL
BEGIN;
CREATE TEMP TABLE stg_financeiro (LIKE bi_reporting.financeiro) ON COMMIT DROP;
CREATE TEMP TABLE stg_producao (LIKE bi_reporting.producao) ON COMMIT DROP;
CREATE TEMP TABLE stg_vendas (LIKE bi_reporting.vendas) ON COMMIT DROP;
CREATE TEMP TABLE stg_servicos (LIKE bi_reporting.servicos) ON COMMIT DROP;
\\copy stg_financeiro FROM '$WORKDIR/financeiro.csv' WITH (FORMAT csv, HEADER true)
\\copy stg_producao FROM '$WORKDIR/producao.csv' WITH (FORMAT csv, HEADER true)
\\copy stg_vendas FROM '$WORKDIR/vendas.csv' WITH (FORMAT csv, HEADER true)
\\copy stg_servicos FROM '$WORKDIR/servicos.csv' WITH (FORMAT csv, HEADER true)
TRUNCATE bi_reporting.financeiro,bi_reporting.producao,bi_reporting.vendas,bi_reporting.servicos;
INSERT INTO bi_reporting.financeiro SELECT * FROM stg_financeiro;
INSERT INTO bi_reporting.producao SELECT * FROM stg_producao;
INSERT INTO bi_reporting.vendas SELECT * FROM stg_vendas;
INSERT INTO bi_reporting.servicos SELECT * FROM stg_servicos;
COMMIT;
SELECT 'financeiro' AS area,count(*) FROM bi_reporting.financeiro
UNION ALL SELECT 'producao',count(*) FROM bi_reporting.producao
UNION ALL SELECT 'vendas',count(*) FROM bi_reporting.vendas
UNION ALL SELECT 'servicos',count(*) FROM bi_reporting.servicos;
SQL

psql -X -v ON_ERROR_STOP=1 -d "$TARGET_DB" -f "$WORKDIR/import.sql"
