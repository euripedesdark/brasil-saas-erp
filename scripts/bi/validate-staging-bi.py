#!/usr/bin/env python3
"""Read-only authenticated validation restricted to the isolated staging port."""
import datetime
import json
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = 'http://127.0.0.1:18081'
AREAS = {'financeiro', 'producao', 'vendas', 'servicos'}


def request(path, token=None, body=None, empresa=None):
    headers = {'Accept': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    if empresa is not None: headers['X-Empresa-Id'] = str(empresa)
    data = json.dumps(body).encode() if body else None
    if data: headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, data=data, headers=headers)
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs): return None
    try:
        with urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect()).open(req, timeout=20) as response:
            payload = response.read(1024 * 1024)
            try: parsed = json.loads(payload)
            except (ValueError, UnicodeError): parsed = None
            return response.status, parsed
    except urllib.error.HTTPError as error:
        error.close(); return error.code, None
    except (OSError, urllib.error.URLError): return None, None


def valid_summary(body, expected):
    data = body.get('data') if isinstance(body, dict) else None
    areas = data.get('areas') if isinstance(data, dict) else None
    if not isinstance(areas, list) or len(areas) != len(expected): return False
    if {area.get('codigo') for area in areas if isinstance(area, dict)} != set(expected): return False
    return all(isinstance(area, dict) and isinstance(area.get('indicadores'), list)
               and len(area['indicadores']) == 3 for area in areas)


def validate(settings):
    # Never authenticate if this configuration points at the production database.
    url = settings.get('SPRING_DATASOURCE_URL', settings.get('DB_URL', ''))
    parsed = urllib.parse.urlsplit(url.removeprefix('jdbc:'))
    database = parsed.path.rsplit('/', 1)[-1]
    if database != 'brasil_saas_staging' or parsed.hostname not in {'127.0.0.1', 'localhost', '::1'} or parsed.port != 15432:
        raise ValueError('Banco nao identificado como PostgreSQL staging isolado na porta 15432')
    password = settings.get('STAGING_ADMIN_PASSWORD')
    if not password: raise ValueError('Credencial de staging ausente')
    report = {'staging_only': True, 'checks': {}}
    status, login = request('/api/auth/login', body={'username': 'staging-admin', 'password': password, 'provider': 'POSTGRES'})
    token = login.get('data', {}).get('accessToken') if isinstance(login, dict) and isinstance(login.get('data'), dict) else None
    report['login_http'] = status
    if status != 200 or not isinstance(token, str) or not token:
        report['passed'] = False; return report
    status, me = request('/api/auth/me', token)
    empresa = me.get('data', {}).get('empresaId') if isinstance(me, dict) and isinstance(me.get('data'), dict) else None
    today = datetime.date.today().isoformat()
    period = '?' + urllib.parse.urlencode({'inicio': today, 'fim': today})
    for area in sorted(AREAS):
        status, body = request('/api/bi/resumo/' + area + period, token, empresa=empresa)
        report['checks'][area] = {'http': status, 'contract': status == 200 and valid_summary(body, {area})}
    status, body = request('/api/bi/resumo' + period, token, empresa=empresa)
    report['checks']['resumo'] = {'http': status, 'contract': status == 200 and valid_summary(body, AREAS)}
    status, _ = request('/api/bi/resumo/vendas' + period)
    report['checks']['anonymous_rejected'] = {'http': status, 'contract': status in (401, 403)}
    status, _ = request('/api/bi/resumo/area-invalida' + period, token, empresa=empresa)
    report['checks']['invalid_area_rejected'] = {'http': status, 'contract': status in (400, 422)}
    status, body = request('/api/bi/integracao/graylog', token, empresa=empresa)
    data = body.get('data') if isinstance(body, dict) else None
    report['graylog_connection_http'] = status
    report['graylog_registered'] = bool(isinstance(data, dict) and data.get('configured'))
    report['passed'] = all(check['contract'] for check in report['checks'].values())
    return report


def main():
    env = pathlib.Path.home() / '.local/share/brasil-saas-staging/staging.env'
    if env.stat().st_mode & 0o077: raise ValueError('Arquivo de staging deve ser privado')
    settings = dict(line.split('=', 1) for line in env.read_text().splitlines() if '=' in line and not line.startswith('#'))
    result = validate(settings)
    print(json.dumps(result, indent=2))  # No token, credentials, company ID or financial values.
    return 0 if result['passed'] else 2


if __name__ == '__main__':
    try: sys.exit(main())
    except (OSError, ValueError):
        print('Validacao indisponivel: confira ambiente privado, banco isolado e servico de staging.', file=sys.stderr)
        sys.exit(1)
