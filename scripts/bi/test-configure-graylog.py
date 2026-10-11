import base64
import contextlib
import importlib.util
import io
import json
import os
import pathlib
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("graylog_setup", pathlib.Path(__file__).with_name("configure-graylog.py"))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class Handler(BaseHTTPRequestHandler):
    mode = "graylog"
    def log_message(self, *args): pass
    def do_GET(self):
        if self.mode == "redirect":
            self.send_response(302); self.send_header("Location", "http://example.invalid/"); self.end_headers(); return
        authorized = self.headers.get("Authorization") == "Basic " + base64.b64encode(b"private-test-token:token").decode()
        if not authorized:
            self.send_response(403); self.end_headers(); self.wfile.write(b"denied"); return
        self.send_response(200); self.end_headers()
        body = {"version": "7.1.9", "node_id": "test-node"} if self.mode == "graylog" else {"hello": "other app"}
        self.wfile.write(json.dumps(body).encode())


class SetupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        Handler.mode = "graylog"
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True); self.thread.start()
        self.url = f"http://127.0.0.1:{self.server.server_port}/api/"
    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join(); self.temp.cleanup()

    def test_discovers_custom_port_without_returning_secrets(self):
        config = self.root / "server.conf"
        config.write_text("password_secret=secret-must-not-leak\nmongodb_uri=mongodb://admin:secret@db\nhttp_bind_address=0.0.0.0:19000\nhttp_external_uri=http://admin:secret@host:9000/\n")
        with patch.object(setup, "command", return_value=""):
            result = setup.discover(str(config))
        self.assertIn("http://127.0.0.1:19000/api/", result["candidates"])
        self.assertNotIn("secret", json.dumps(result)); self.assertIsNone(result["web_url"])

    def test_safe_urls_keep_subpath_and_reject_credentials_or_properties_injection(self):
        self.assertEqual("https://host/graylog/api/", setup.safe_url("https://host/graylog/", api=True))
        self.assertEqual("https://host/graylog/", setup.safe_url("https://host/graylog/api/"))
        for value in ["file:///etc/passwd", "http://u:password@host/", "http://host/?token=x", "http://host/#secret", "http://host/\nkey=x", "http://host/\\"]:
            with self.assertRaises(ValueError): setup.safe_url(value)

    def test_authenticated_identity_and_failed_auth(self):
        self.assertTrue(setup.probe(self.url, "private-test-token")["authenticated"])
        self.assertFalse(setup.probe(self.url, "wrong")["authenticated"])

    def test_rejects_other_app_and_redirect(self):
        Handler.mode = "other"
        self.assertFalse(setup.probe(self.url, "private-test-token")["verified_graylog"])
        Handler.mode = "redirect"
        self.assertEqual(302, setup.probe(self.url, "private-test-token")["http_status"])

    def test_refuses_plaintext_credentials_to_remote_host(self):
        with self.assertRaises(ValueError): setup.probe("http://192.168.2.10:9000/api/", "private-test-token")

    def test_atomic_write_is_idempotent_and_keeps_backup(self):
        path = self.root / "graylog.properties"
        self.assertTrue(setup.atomic_write(path, "one")); self.assertFalse(setup.atomic_write(path, "one"))
        self.assertTrue(setup.atomic_write(path, "two"))
        self.assertEqual("one", next(self.root.glob("*.bak-*")).read_text())
        self.assertEqual(0o644, path.stat().st_mode & 0o777)

    def test_rejects_symlink_destination(self):
        target = self.root / "original"; target.write_text("keep")
        link = self.root / "link"; link.symlink_to(target)
        with self.assertRaises(ValueError): setup.atomic_write(link, "overwrite")
        self.assertEqual("keep", target.read_text())

    def test_apply_saves_no_credentials_and_failure_preserves_previous_config(self):
        token = self.root / "token"; token.write_text("private-test-token"); token.chmod(0o600)
        output = self.root / "graylog.properties"
        args = ["--apply", "--non-interactive", "--api-url", self.url, "--token-file", str(token), "--output", str(output)]
        stdout, stderr = io.StringIO(), io.StringIO()
        with patch.object(setup, "command", return_value=""), contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            self.assertEqual(0, setup.main(args))
            before = output.read_text()
            self.assertEqual(0, setup.main(args))
            self.assertEqual([], list(self.root.glob("*.bak-*")))
            token.write_text("wrong")
            self.assertEqual(2, setup.main(args))
        self.assertEqual(before, output.read_text())
        self.assertNotIn("private-test-token", before + stdout.getvalue() + stderr.getvalue())
        self.assertIn("brasil-saas.bi.graylog.configured=true", before)

    def test_private_token_file_permissions_required(self):
        token = self.root / "token"; token.write_text("private-test-token"); token.chmod(0o644)
        with patch.object(setup, "command", return_value=""), self.assertRaises(ValueError):
            setup.main(["--token-file", str(token), "--non-interactive"])


if __name__ == "__main__": unittest.main()
