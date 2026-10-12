"""PKI de teste descartavel. Nao toca /etc, cluster PostgreSQL ou PKI do operador."""
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('cert', ROOT / 'configurar_certificado_bi.py')
cert = importlib.util.module_from_spec(spec); spec.loader.exec_module(cert)


class EasyRsaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.pki = Path(cls.tmp.name) / 'pki'
        cls.easyrsa = ROOT.parent.parent / 'certs/easyrsa'
        env = cert.clean_env(); env.update(EASYRSA_PKI=str(cls.pki), EASYRSA_BATCH='1', EASYRSA_REQ_CN='CA DE TESTE DESCARTAVEL')
        subprocess.run([cls.easyrsa, 'init-pki'], env=env, check=True, capture_output=True, timeout=60)
        subprocess.run([cls.easyrsa, 'build-ca', 'nopass'], env=env, check=True, capture_output=True, timeout=60)
        cls.args = SimpleNamespace(pki=cls.pki, easyrsa=cls.easyrsa)
    @classmethod
    def tearDownClass(cls): cls.tmp.cleanup()

    def test_emissao_idempotente_cn_e_pk8_der(self):
        ca_before = hashlib.sha256((self.pki / 'ca.crt').read_bytes()).hexdigest()
        crt, key = cert.ensure_identity(self.args)
        first = crt.read_bytes(), key.read_bytes()
        again, again_key = cert.ensure_identity(self.args)
        self.assertEqual(first, (again.read_bytes(), again_key.read_bytes()))
        self.assertEqual(ca_before, hashlib.sha256((self.pki / 'ca.crt').read_bytes()).hexdigest())
        self.assertFalse((self.pki / 'issued/sa.crt').exists())
        der = cert.run(['openssl', 'pkcs8', '-topk8', '-outform', 'DER', '-in', key, '-nocrypt'], binary=True)
        path = self.pki / 'private/fixture.pk8'; path.write_bytes(der)
        pub = cert.run(['openssl', 'pkey', '-inform', 'DER', '-in', path, '-pubout'])
        self.assertEqual(cert.run(['openssl', 'x509', '-in', crt, '-noout', '-pubkey']), pub)

    def test_recusa_par_incompleto_sem_regenerar_ca(self):
        cert.ensure_identity(self.args)
        key = self.pki / f'private/{cert.ROLE}.key'
        saved = key.read_bytes(); key.unlink()
        try:
            with self.assertRaises(RuntimeError): cert.ensure_identity(self.args)
            self.assertFalse(key.exists())
        finally: key.write_bytes(saved)


if __name__ == '__main__': unittest.main()
