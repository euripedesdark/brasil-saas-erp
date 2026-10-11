#!/usr/bin/env python3
"""Discover local Graylog and register its connection in the ERP control plane."""
import argparse
import base64
import datetime
import getpass
import ipaddress
import json
import os
import pathlib
import re
import ssl
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request

CONFIG_PATHS = ("/etc/graylog/server/server.conf", "/etc/graylog-server/server.conf",
                "/usr/share/graylog/data/config/graylog.conf")
ALLOWED_SETTINGS = {"http_bind_address", "http_publish_uri", "http_external_uri", "http_enable_tls"}
AREAS = ["financeiro", "producao", "vendas", "servicos"]


def command(args):
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=8)
        return result.stdout.strip() if result.returncode == 0 else ""
    except (OSError, subprocess.TimeoutExpired):
        return ""


def safe_url(value, api=False):
    if "\\" in value or any(c.isspace() or ord(c) < 32 for c in value):
        raise ValueError("Endereco HTTP invalido")
    parsed = urllib.parse.urlsplit(value)
    if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("Use endereco HTTP/HTTPS sem credenciais, query ou fragmento")
    try:
        parsed.port
    except ValueError:
        raise ValueError("Porta HTTP invalida") from None
    path = parsed.path.rstrip("/")
    if path.endswith("/api"):
        path = path[:-4]
    if api:
        path += "/api"
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, path + "/", "", ""))


def bind_url(value, tls=False):
    value = value.strip()
    if value.startswith("0.0.0.0:"):
        value = "127.0.0.1:" + value.split(":", 1)[1]
    if value.startswith("[::]:"):
        value = "[::1]:" + value.rsplit(":", 1)[1]
    return safe_url(("https://" if tls else "http://") + value, api=True)


def discover(config_file=None):
    paths = [pathlib.Path(config_file)] if config_file else [pathlib.Path(p) for p in CONFIG_PATHS]
    # Read only the command metadata to locate -f/--configfile; never print it.
    if not config_file:
        start = command(["systemctl", "show", "graylog-server", "-p", "ExecStart", "--value"])
        for match in re.finditer(r"(?:--configfile[= ]|-f\s+)([^\s;]+)", start):
            paths.append(pathlib.Path(match.group(1).strip('"')))
    settings, source = {}, None
    unreadable = []
    for path in dict.fromkeys(paths):
        try:
            lines = path.read_text().splitlines()
        except PermissionError:
            unreadable.append(str(path)); continue
        except FileNotFoundError:
            continue
        for line in lines:
            if line.lstrip().startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            if key.strip() in ALLOWED_SETTINGS:
                settings[key.strip()] = value.strip()
        source = str(path)
        break
    candidates = []
    tls = settings.get("http_enable_tls", "false").lower() == "true"
    if settings.get("http_bind_address"):
        try:
            candidates.append(bind_url(settings["http_bind_address"], tls))
        except ValueError:
            pass
    for key in ("http_publish_uri", "http_external_uri"):
        if settings.get(key):
            try:
                candidates.append(safe_url(settings[key], api=True))
            except ValueError:
                pass
    # Docker metadata only; do not inspect environment variables or credentials.
    container_ids = command(["docker", "ps", "--filter", "name=graylog", "--format", "{{.ID}}"])
    for container in container_ids.splitlines():
        ports = command(["docker", "inspect", "--format", "{{json .NetworkSettings.Ports}}", container])
        try:
            for binding in json.loads(ports or "{}").get("9000/tcp") or []:
                host = binding.get("HostIp", "127.0.0.1")
                if host in ("", "0.0.0.0"): host = "127.0.0.1"
                if host == "::": host = "::1"
                if ":" in host: host = "[" + host + "]"
                candidates.append(bind_url(host + ":" + binding["HostPort"]))
        except (ValueError, KeyError, TypeError):
            pass
    candidates.append("http://127.0.0.1:9000/api/")
    version = command(["rpm", "-q", "--qf", "%{VERSION}", "graylog-server"])
    if not version:
        version = command(["dpkg-query", "-W", "-f=${Version}", "graylog-server"])
    web = settings.get("http_external_uri")
    if web:
        try: web = safe_url(web)
        except ValueError: web = None
    state = command(["systemctl", "is-active", "graylog-server"])
    if state not in {"active", "inactive", "failed", "activating", "deactivating"}: state = "unknown"
    return {"config_source": source, "unreadable_config": unreadable,
            "service_state": state,
            "package_version": version or None, "candidates": list(dict.fromkeys(candidates)),
            "web_url": web}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Never forward an API token to another origin.


def probe(api_url, token=None, ca_file=None, login=None):
    context = ssl.create_default_context(cafile=ca_file)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(),
                                         urllib.request.HTTPSHandler(context=context))
    headers = {"Accept": "application/json", "X-Requested-By": "brasil-saas-bi-setup"}
    if token or login:
        host = urllib.parse.urlsplit(api_url).hostname
        local = host == "localhost"
        try: local = local or ipaddress.ip_address(host).is_loopback
        except ValueError: pass
        if api_url.startswith("http:") and not local:
            raise ValueError("Para enviar token fora do localhost, use HTTPS")
        credential = token + ":token" if token else login[0] + ":" + login[1]
        headers["Authorization"] = "Basic " + base64.b64encode(credential.encode()).decode()
    try:
        response = opener.open(urllib.request.Request(api_url + "system", headers=headers), timeout=5)
    except urllib.error.HTTPError as error:
        return {"http_status": error.code, "authenticated": False,
                "verified_graylog": bool(error.headers.get("X-Graylog-Node-ID"))}
    except (urllib.error.URLError, TimeoutError, OSError):
        return {"http_status": None, "authenticated": False, "verified_graylog": False}
    with response:
        try:
            body = json.loads(response.read(1024 * 1024))
        except (ValueError, UnicodeError):
            body = {}
        verified = isinstance(body, dict) and bool(re.fullmatch(r"[A-Za-z0-9._+-]{1,80}", str(body.get("version", "")))) and bool(body.get("node_id"))
        return {"http_status": response.status, "authenticated": verified,
                "verified_graylog": verified, "api_version": body.get("version") if verified else None}


def atomic_write(path, content):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_symlink():
        raise ValueError("Destino de configuracao nao pode ser link simbolico")
    if path.exists() and path.read_text() == content:
        return False
    if path.exists():
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        backup = path.with_name(path.name + ".bak-" + stamp)
        fd = os.open(backup, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as stream: stream.write(path.read_text())
    fd, temp = tempfile.mkstemp(dir=path.parent, prefix=".graylog-")
    try:
        with os.fdopen(fd, "w") as stream: stream.write(content)
        os.chmod(temp, 0o644)  # Contains connection metadata only, never credentials.
        os.replace(temp, path)
    finally:
        if os.path.exists(temp): os.unlink(temp)
    return True


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Salvar conexao validada para o ERP")
    parser.add_argument("--config-file", help="Caminho alternativo do server.conf")
    parser.add_argument("--api-url", help="Endereco alternativo da API Graylog")
    parser.add_argument("--web-url", help="Endereco do Graylog acessivel no navegador")
    parser.add_argument("--token-file", help="Arquivo privado com token; nunca passe token na linha de comando")
    parser.add_argument("--login", action="store_true", help="Pedir usuario e senha Graylog no terminal, sem salvar")
    parser.add_argument("--ca-file", help="CA de confianca para HTTPS privado")
    parser.add_argument("--non-interactive", action="store_true")
    parser.add_argument("--output", default="/etc/brasil-saas/bi-graylog.properties")
    args = parser.parse_args(argv)
    if args.login and (args.token_file or args.non_interactive or not sys.stdin.isatty()):
        raise ValueError("Login exige terminal interativo e nao pode ser combinado com arquivo de token")
    login = None
    discovery = discover(args.config_file)
    candidates = [safe_url(args.api_url, api=True)] if args.api_url else discovery["candidates"]
    token = None
    if args.token_file:
        path = pathlib.Path(args.token_file)
        if path.stat().st_mode & 0o077:
            raise ValueError("Arquivo de token deve ter permissao 600")
        token = path.read_text().strip()
        if not token: raise ValueError("Arquivo de token vazio")
    results = []
    selected = None
    for url in candidates:
        result = probe(url, token, args.ca_file)
        results.append({"api_url": url, **result})
        if result["authenticated"]:
            selected = results[-1]; break
    if not selected and args.apply and not args.non_interactive and sys.stdin.isatty():
        accessible = [item for item in results if item["http_status"] in (401, 403)]
        if accessible:
            url = accessible[0]["api_url"]
            print("API encontrada, mas exige autenticacao:", url)
            if args.login:
                username = input("Usuario Graylog: ").strip()
                password = getpass.getpass("Senha Graylog (nao sera exibida nem salva): ")
                login = (username, password) if username and password else None
            else:
                token = getpass.getpass("Token Graylog (nao sera exibido nem salvo): ").strip()
            if token or login:
                result = probe(url, token, args.ca_file, login)
                if result["authenticated"]: selected = {"api_url": url, **result}
    report = {"discovery": discovery, "checks": results,
              "configured": False, "presentation_ready": False,
              "areas": AREAS, "output": str(args.output)}
    if selected:
        web = args.web_url or discovery["web_url"] or selected["api_url"]
        web = safe_url(web)
        report.update({"api_url": selected["api_url"], "web_url": web,
                       "api_version": selected.get("api_version"), "authenticated": True})
        if args.apply:
            properties = "\n".join([
                "# Gerado por scripts/bi/configure-graylog.py; sem credenciais.",
                "brasil-saas.bi.graylog.configured=true",
                "brasil-saas.bi.graylog.api-url=" + selected["api_url"],
                "brasil-saas.bi.graylog.web-url=" + web,
                "brasil-saas.bi.graylog.version=" + str(selected.get("api_version") or "unknown"),
                ""])
            report["changed"] = atomic_write(args.output, properties)
            report["configured"] = True
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if args.apply and not report["configured"]:
        print("Configuracao nao gravada: acesso autenticado ao Graylog nao foi validado.", file=sys.stderr)
        return 2
    if report["configured"]:
        print("Conexao registrada. O ERP le o arquivo no proximo inicio do JAR atualizado.")
        print("Apresentacao com consultas diretas ainda exige o adaptador Graylog; nao foi instalada por este script.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError) as error:
        # Error bodies and exceptions may contain secrets: output only fixed guidance.
        print("Falha na configuracao. Confira URLs, permissoes, token e CA; nenhum segredo foi gravado.", file=sys.stderr)
        raise SystemExit(1)
