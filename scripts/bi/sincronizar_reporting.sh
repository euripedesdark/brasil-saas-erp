#!/usr/bin/env bash
# Carga completa e atomica das quatro views de BI do ERP para o banco dedicado.
# Execute no host PostgreSQL com sudo -u postgres bash scripts/bi/sincronizar_reporting.sh
set -Eeuo pipefail
# Uma trava comum ao timer e a execucoes manuais. O instalador cria /run/lock
# via tmpfiles.d; o fallback pertence ao usuario para execucao sem instalacao.
if [[ -d /run/lock/brasil-saas-bi ]]; then
    DEFAULT_LOCK=/run/lock/brasil-saas-bi/etl.lock
else
    DEFAULT_LOCK="${TMPDIR:-/tmp}/brasil-saas-bi-etl-$(id -u).lock"
fi
LOCK_FILE="${BI_ETL_LOCK_FILE:-$DEFAULT_LOCK}"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
    echo "BI ETL: sincronizacao ja em execucao; carga ignorada"
    exit 0
fi
export PGOPTIONS="-c statement_timeout=${BI_ETL_STATEMENT_TIMEOUT_MS:-300000} -c lock_timeout=${BI_ETL_LOCK_TIMEOUT_MS:-5000}"
STARTED=$SECONDS
trap 'echo "BI ETL: erro na linha $LINENO; consulte o journal; transacao interrompida sofre rollback" >&2' ERR
source_psql() { PGUSER="${BI_SOURCE_USER:-${PGUSER:-$(id -un)}}" psql -X -w -v ON_ERROR_STOP=1 -d "$SOURCE_DB" "$@"; }
target_psql() { PGUSER="${BI_TARGET_USER:-${PGUSER:-$(id -un)}}" psql -X -w -v ON_ERROR_STOP=1 -d "$TARGET_DB" "$@"; }
SOURCE_DB="${BI_SOURCE_DB:-brasil-saas}"
TARGET_DB="${BI_TARGET_DB:-brasil_saas_bi}"
if [[ "$SOURCE_DB" == "$TARGET_DB" || "$TARGET_DB" != "brasil_saas_bi" ]]; then
    echo "BI ETL: destino deve ser brasil_saas_bi, diferente da origem" >&2; exit 1
fi
echo "BI ETL: iniciando snapshot somente leitura e carga atomica"
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

source_psql -f "$WORKDIR/export.sql"

# A substituicao so ocorre apos extracao bem-sucedida de TODAS as fontes.
# Se uma carga falhar, o ROLLBACK preserva a versao anterior do BI.
cat > "$WORKDIR/import.sql" <<SQL
BEGIN;
CREATE TEMP TABLE stg_financeiro (LIKE bi_reporting.financeiro) ON COMMIT DROP;
CREATE TEMP TABLE stg_producao (LIKE bi_reporting.producao) ON COMMIT DROP;
CREATE TEMP TABLE stg_vendas (LIKE bi_reporting.vendas) ON COMMIT DROP;
CREATE TEMP TABLE stg_servicos (LIKE bi_reporting.servicos) ON COMMIT DROP;
\\copy stg_financeiro (empresa_id,tipo,valor_saldo,data_vencimento,status) FROM '$WORKDIR/financeiro.csv' WITH (FORMAT csv, HEADER true)
\\copy stg_producao (empresa_id,status,data_referencia,data_fim) FROM '$WORKDIR/producao.csv' WITH (FORMAT csv, HEADER true)
\\copy stg_vendas (empresa_id,tipo,status,data_referencia,valor_total) FROM '$WORKDIR/vendas.csv' WITH (FORMAT csv, HEADER true)
\\copy stg_servicos (empresa_id,status,valor_total,data_abertura,data_fechamento,data_previsao) FROM '$WORKDIR/servicos.csv' WITH (FORMAT csv, HEADER true)
-- MVCC: leitores continuam vendo a carga anterior ate COMMIT, sem ACCESS EXCLUSIVE.
-- Lock transacional tambem protege cargas em hosts diferentes; falha gera rollback.
DO \$\$BEGIN
  IF NOT pg_try_advisory_xact_lock(197, 775) THEN
    RAISE EXCEPTION 'Outra carga BI esta em execucao';
  END IF;
END\$\$;
DELETE FROM bi_reporting.financeiro;
DELETE FROM bi_reporting.producao;
DELETE FROM bi_reporting.vendas;
DELETE FROM bi_reporting.servicos;
INSERT INTO bi_reporting.financeiro (empresa_id,tipo,valor_saldo,data_vencimento,status) SELECT empresa_id,tipo,valor_saldo,data_vencimento,status FROM stg_financeiro;
INSERT INTO bi_reporting.producao (empresa_id,status,data_referencia,data_fim) SELECT empresa_id,status,data_referencia,data_fim FROM stg_producao;
INSERT INTO bi_reporting.vendas (empresa_id,tipo,status,data_referencia,valor_total) SELECT empresa_id,tipo,status,data_referencia,valor_total FROM stg_vendas;
INSERT INTO bi_reporting.servicos (empresa_id,status,valor_total,data_abertura,data_fechamento,data_previsao) SELECT empresa_id,status,valor_total,data_abertura,data_fechamento,data_previsao FROM stg_servicos;
SELECT 'financeiro' AS area,count(*) FROM bi_reporting.financeiro
UNION ALL SELECT 'producao',count(*) FROM bi_reporting.producao
UNION ALL SELECT 'vendas',count(*) FROM bi_reporting.vendas
UNION ALL SELECT 'servicos',count(*) FROM bi_reporting.servicos;
COMMIT;
SQL

target_psql -f "$WORKDIR/import.sql"

echo "BI ETL: concluido em $((SECONDS - STARTED))s; quatro areas publicadas atomicamente"
