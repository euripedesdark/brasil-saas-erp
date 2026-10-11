-- Camada analitica seletiva, sem copiar todo o banco ou alterar tabelas historicas.
-- Views locais usam o PostgreSQL existente. Nao sao uma replica fisica nem um ETL.
CREATE SCHEMA IF NOT EXISTS brasil_saas_dl;

CREATE VIEW brasil_saas_dl.v_bi_financeiro AS
SELECT empresa_id, tipo, valor_saldo, data_vencimento, status
FROM brasil_saas.bc_fin_titulo WHERE deleted_at IS NULL;

CREATE VIEW brasil_saas_dl.v_bi_producao AS
SELECT empresa_id, status, CAST(COALESCE(data_inicio, created_at) AS date) AS data_referencia,
       CAST(data_fim AS date) AS data_fim
FROM brasil_saas.bc_prod_ordem WHERE deleted_at IS NULL;

CREATE VIEW brasil_saas_dl.v_bi_vendas AS
SELECT empresa_id, tipo, status, data_emissao AS data_referencia, valor_total
FROM brasil_saas.bc_ven_pedido WHERE deleted_at IS NULL;

CREATE VIEW brasil_saas_dl.v_bi_servicos AS
SELECT empresa_id, status, valor_total,
       CAST(abertura_at AS date) AS data_abertura,
       CAST(fechamento_at AS date) AS data_fechamento,
       CAST(previsao_at AS date) AS data_previsao
FROM brasil_saas.bc_srv_ordem_servico WHERE deleted_at IS NULL;
