import importlib.util
import json
import os
import pathlib
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('installer', pathlib.Path(__file__).with_name('install-graylog-bi.py'))
installer = importlib.util.module_from_spec(spec); spec.loader.exec_module(installer)


class InstallerTest(unittest.TestCase):
    def jar(self, path, scripts=None, bundle=True):
        filename = 'plugin.' + installer.PLUGIN + '.js'
        with zipfile.ZipFile(path, 'w') as jar:
            jar.writestr('META-INF/services/org.graylog2.plugin.Plugin', installer.PLUGIN)
            jar.writestr(installer.PLUGIN.replace('.', '/') + '.class', b'fixture')
            jar.writestr('plugin.' + installer.PLUGIN + '.module.json', json.dumps({'files': {'js': scripts if scripts is not None else [filename]}}))
            if bundle: jar.writestr(filename, 'fixture')

    def test_expected_bundle_and_plugin_identity(self):
        with tempfile.TemporaryDirectory() as folder:
            path = pathlib.Path(folder) / 'plugin.jar'; self.jar(path)
            self.assertEqual(len(installer.validate_jar(path)), 64)

    def test_reject_missing_or_external_bundle(self):
        with tempfile.TemporaryDirectory() as folder:
            path = pathlib.Path(folder) / 'plugin.jar'
            for scripts, bundle in [(None, False), ([], True), (['https://evil.example/x.js'], True), (['../plugin.js'], True)]:
                self.jar(path, scripts, bundle)
                with self.assertRaises(ValueError): installer.validate_jar(path)

    def test_atomic_idempotent_install_and_private_backup(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = pathlib.Path(folder); jar = directory / 'input.jar'; self.jar(jar)
            backups = directory / 'backups'
            self.assertTrue(installer.install(jar, directory, backups))
            target = directory / installer.NAME; self.assertEqual(target.stat().st_mode & 0o777, 0o644)
            self.assertFalse(installer.install(jar, directory, backups)); self.assertFalse(backups.exists())
            target.write_bytes(b'previous')
            self.assertTrue(installer.install(jar, directory, backups))
            saved = list(backups.iterdir()); self.assertEqual(len(saved), 1)
            self.assertEqual(saved[0].read_bytes(), b'previous'); self.assertEqual(saved[0].stat().st_mode & 0o777, 0o600)

    def test_reject_symlink_destination(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = pathlib.Path(folder); jar = directory / 'input.jar'; self.jar(jar)
            (directory / installer.NAME).symlink_to(jar)
            with self.assertRaises(ValueError): installer.install(jar, directory, directory / 'backups')

    def test_config_preserves_credentials_port_and_file_permissions(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = pathlib.Path(folder); config = directory / 'server.conf'; backup = directory / 'backups'
            original = ('# Configura\xe7ao\npassword_secret = PRIVATE\nmongodb_uri = mongodb://user:pass@localhost/graylog\n'
                        'http_bind_address = 0.0.0.0:9002\nhttp_publish_uri = http://192.168.2.10:9002/\n'
                        'http_external_uri = http://wrong/\n').encode('latin-1')
            config.write_bytes(original); os.chmod(config, 0o640)
            values = {'plugin_dir': '/usr/share/graylog-server/plugin', 'http_external_uri': 'http://192.168.2.10:9002/'}
            self.assertTrue(installer.configure_file(config, values, backup))
            result = config.read_bytes()
            for line in original.splitlines():
                if not line.startswith(b'http_external_uri'): self.assertIn(line, result)
            self.assertEqual(config.stat().st_mode & 0o777, 0o640)
            self.assertEqual(next(backup.iterdir()).read_bytes(), original)
            self.assertFalse(installer.configure_file(config, values, backup)); self.assertEqual(len(list(backup.iterdir())), 1)

    def test_config_rejects_unrelated_settings_and_symlink(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = pathlib.Path(folder); config = directory / 'server.conf'; config.write_text('password_secret = PRIVATE\n')
            with self.assertRaises(ValueError): installer.configure_file(config, {'mongodb_uri': 'other'}, directory / 'backup')
            link = directory / 'link'; link.symlink_to(config)
            with self.assertRaises(ValueError): installer.configure_file(link, {'plugin_dir': '/plugin'}, directory / 'backup')


if __name__ == '__main__': unittest.main()
