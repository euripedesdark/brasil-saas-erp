-- Executar SOMENTE no banco brasil_saas_bi, como administrador.
-- Estrutura de leitura dos quatro indicadores atuais; nao altera as nove tabelas do datalake.
-- As tabelas de staging sao preenchidas pelo processo ETL, nunca pelo endpoint HTTP.
BEGIN;
CREATE SCHEMA IF NOT EXISTS bi_reporting;
CREATE TABLE IF NOT EXISTS bi_reporting.financeiro (
    empresa_id bigint NOT NULL,
    tipo varchar(20),
    valor_saldo numeric(18,2),
    data_vencimento date,
    status varchar(60)
);
CREATE TABLE IF NOT EXISTS bi_reporting.producao (
    empresa_id bigint NOT NULL,
    status varchar(60),
    data_referencia date,
    data_fim date
);
CREATE TABLE IF NOT EXISTS bi_reporting.vendas (
    empresa_id bigint NOT NULL,
    tipo varchar(40),
    status varchar(60),
    data_referencia date,
    valor_total numeric(18,2)
);
CREATE TABLE IF NOT EXISTS bi_reporting.servicos (
    empresa_id bigint NOT NULL,
    status varchar(60),
    valor_total numeric(18,2),
    data_abertura date,
    data_fechamento date,
    data_previsao date
);
CREATE INDEX IF NOT EXISTS idx_bi_fin_empresa_data ON bi_reporting.financeiro(empresa_id,data_vencimento);
CREATE INDEX IF NOT EXISTS idx_bi_prod_empresa_data ON bi_reporting.producao(empresa_id,data_referencia);
CREATE INDEX IF NOT EXISTS idx_bi_vend_empresa_data ON bi_reporting.vendas(empresa_id,data_referencia);
CREATE INDEX IF NOT EXISTS idx_bi_serv_empresa_datas ON bi_reporting.servicos(empresa_id,data_abertura,data_fechamento);
CREATE SCHEMA IF NOT EXISTS brasil_saas_dl;
CREATE OR REPLACE VIEW brasil_saas_dl.v_bi_financeiro AS SELECT * FROM bi_reporting.financeiro;
CREATE OR REPLACE VIEW brasil_saas_dl.v_bi_producao AS SELECT * FROM bi_reporting.producao;
CREATE OR REPLACE VIEW brasil_saas_dl.v_bi_vendas AS SELECT * FROM bi_reporting.vendas;
CREATE OR REPLACE VIEW brasil_saas_dl.v_bi_servicos AS SELECT * FROM bi_reporting.servicos;
GRANT USAGE ON SCHEMA bi_reporting,brasil_saas_dl TO brasil_saas_bi_reader,brasil_saas_bi_etl;
GRANT SELECT ON ALL TABLES IN SCHEMA bi_reporting TO brasil_saas_bi_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA brasil_saas_dl TO brasil_saas_bi_reader;
GRANT SELECT,INSERT,UPDATE,DELETE,TRUNCATE ON ALL TABLES IN SCHEMA bi_reporting TO brasil_saas_bi_etl;
COMMIT;
