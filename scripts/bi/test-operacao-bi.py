import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('cert', ROOT / 'configurar_certificado_bi.py')
cert = importlib.util.module_from_spec(spec); spec.loader.exec_module(cert)


class HbaTest(unittest.TestCase):
    def test_preserva_regras_operacionais_e_bloqueia_fallback(self):
        original = '# existente\nlocal all postgres peer\nhost all all 127.0.0.1/32 scram-sha-256\n'
        updated = cert.managed_hba(original)
        self.assertTrue(updated.endswith(original))
        self.assertEqual(updated, cert.managed_hba(updated))
        self.assertIn('hostssl brasil_saas_bi brasil_saas_bi_reader ::1/128 cert clientcert=verify-full', updated)
        self.assertLess(updated.index('cert clientcert'), updated.index('scram-sha-256'))
        self.assertIn('host brasil_saas_bi brasil_saas_bi_reader 127.0.0.1/32 reject', updated)
        self.assertNotIn(' sa ', updated)

    def test_recusa_bloco_incompleto_ou_duplicado(self):
        for text in (cert.BEGIN + '\n', cert.END + '\n', cert.managed_hba('') * 2):
            with self.assertRaises(RuntimeError): cert.managed_hba(text)

    def test_nao_herda_senha_ou_servico_libpq(self):
        from unittest.mock import patch
        with patch.dict(os.environ, {'PGPASSWORD': 'fixture', 'PGSERVICE': 'operacional', 'JAVA_TOOL_OPTIONS': 'fixture'}):
            env = cert.clean_env()
        self.assertNotIn('PGPASSWORD', env); self.assertNotIn('PGSERVICE', env)
        self.assertNotIn('JAVA_TOOL_OPTIONS', env)


class EtlTest(unittest.TestCase):
    def test_lock_manual_impede_duas_cargas(self):
        import fcntl
        with tempfile.TemporaryDirectory() as tmp:
            lock = Path(tmp) / 'etl.lock'
            with lock.open('w') as handle:
                fcntl.flock(handle, fcntl.LOCK_EX)
                result = subprocess.run(['bash', ROOT / 'sincronizar_reporting.sh'],
                    env={**os.environ, 'BI_ETL_LOCK_FILE': str(lock)}, capture_output=True, text=True, timeout=5)
            self.assertEqual(0, result.returncode)
            self.assertIn('ja em execucao', result.stdout)
            self.assertNotIn('iniciando snapshot', result.stdout)

    def simulate(self, failure):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            # Simula SOMENTE o contrato de processo. Nao prova rollback PostgreSQL.
            psql = base / 'psql'
            psql.write_text('''#!/usr/bin/env python3
import os, pathlib, sys
name = 'export' if sys.argv[sys.argv.index('-d')+1] == 'brasil-saas' else 'import'
pathlib.Path(os.environ['CAPTURE'], name + '.sql').write_text(pathlib.Path(sys.argv[-1]).read_text())
sys.exit(4 if os.environ['FAILURE'] == name else 0)
'''); psql.chmod(0o755)
            env = {**os.environ, 'PATH': str(base) + ':' + os.environ['PATH'], 'CAPTURE': tmp,
                   'FAILURE': failure, 'BI_ETL_LOCK_FILE': str(base / 'etl.lock')}
            result = subprocess.run(['bash', ROOT / 'sincronizar_reporting.sh'], env=env,
                                    capture_output=True, text=True, timeout=10)
            files = {name: (base / name).read_text() for name in ('export.sql', 'import.sql') if (base / name).exists()}
            return result, files

    def test_falha_na_origem_nao_importa_destino(self):
        result, files = self.simulate('export')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual({'export.sql'}, set(files))
        self.assertIn('READ ONLY', files['export.sql'])
        self.assertIn('REPEATABLE READ', files['export.sql'])

    def test_carga_preserva_transacao_e_mvcc(self):
        result, files = self.simulate('')
        self.assertEqual(0, result.returncode)
        sql = files['import.sql']
        self.assertTrue(sql.startswith('BEGIN;'))
        self.assertIn('COMMIT;', sql)
        self.assertIn('pg_try_advisory_xact_lock', sql)
        self.assertIn('DO $$BEGIN', sql)
        self.assertNotIn('TRUNCATE', sql)
        for area in ('financeiro', 'producao', 'vendas', 'servicos'):
            self.assertIn(f'DELETE FROM bi_reporting.{area}', sql)
        self.assertIn('quatro areas publicadas', result.stdout)

    def test_falha_na_importacao_nao_declara_sucesso(self):
        result, _ = self.simulate('import')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('quatro areas publicadas', result.stdout)
        self.assertIn('erro na linha', result.stderr)


if __name__ == '__main__': unittest.main()
