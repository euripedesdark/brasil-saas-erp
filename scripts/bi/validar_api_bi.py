#!/usr/bin/env python3
"""Validacao REST local somente leitura, com duas sessoes administrativas reais."""
import json
import os
import sys
from urllib.error import HTTPError
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen

AREAS = ('financeiro', 'producao', 'vendas', 'servicos')


def main():
    base = os.environ.get('BI_BASE_URL', 'http://127.0.0.1:8080').rstrip('/')
    address = urlparse(base)
    if address.scheme not in ('http', 'https') or address.hostname not in ('localhost', '127.0.0.1', '::1') or address.username or address.password or address.query or address.fragment:
        raise ValueError('Execute no servidor com BI_BASE_URL local, sem credenciais na URL')
    tokens = [os.environ.get('BI_TOKEN_EMPRESA_A'), os.environ.get('BI_TOKEN_EMPRESA_B')]
    if not all(tokens): raise ValueError('Defina BI_TOKEN_EMPRESA_A e BI_TOKEN_EMPRESA_B, sem imprimir tokens')
    period = urlencode({'inicio': os.environ.get('BI_INICIO', '2026-01-01'), 'fim': os.environ.get('BI_FIM', '2026-12-31')})

    def request(path, token=None, empresa=None):
        headers = {'Accept': 'application/json'}
        if token: headers['Authorization'] = 'Bearer ' + token
        if empresa: headers['X-Empresa-Id'] = str(empresa)
        # Nao segue redirect para nao encaminhar Authorization a outro host.
        import urllib.request
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, hdrs, newurl): return None
        try:
            with urllib.request.build_opener(NoRedirect).open(Request(base + path, headers=headers), timeout=15) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            return error.code, None

    companies = []
    for token in tokens:
        status, body = request('/api/auth/me', token)
        if status != 200 or not body or not body.get('data', {}).get('empresaId'):
            raise ValueError('Sessao administrativa sem empresa valida')
        companies.append(body['data']['empresaId'])
    if companies[0] == companies[1]: raise ValueError('As sessoes devem pertencer a empresas diferentes')
    status, _ = request('/api/bi/resumo?' + period)
    if status not in (401, 403): raise ValueError('Resumo permitiu acesso anonimo')
    for index, token in enumerate(tokens):
        status, body = request('/api/bi/resumo?' + period, token, companies[index])
        if status != 200 or not body or not valid_summary(body, AREAS): raise ValueError('Resumo indisponivel ou contrato incorreto')
        # O header pode ser rejeitado ou ignorado; nunca pode selecionar outra empresa.
        status_spoof, spoof = request('/api/bi/resumo?' + period + '&empresaId=' + str(companies[1-index]), token, companies[1-index])
        if status_spoof not in (400, 403) and (status_spoof != 200 or spoof != body):
            raise ValueError('Tentativa de trocar empresa alterou resposta; investigar ou repetir com ETL estabilizado')
        for area in AREAS:
            status, selected = request('/api/bi/resumo/' + area + '?' + period, token, companies[index])
            if status != 200 or not valid_summary(selected, (area,)): raise ValueError('Consulta seletiva falhou: ' + area)
        status, _ = request('/api/bi/resumo/area-invalida?' + period, token, companies[index])
        if status != 400: raise ValueError('Area invalida nao rejeitada')
        status, _ = request('/api/bi/resumo?inicio=2026-02-01&fim=2026-01-01', token, companies[index])
        if status != 400: raise ValueError('Periodo invertido nao rejeitado')
        print(f'REST sessao {index + 1}: quatro areas, evolucao, periodo e troca de empresa validados')
    print('REST: acesso anonimo bloqueado; nenhuma credencial ou metrica impressa')


def valid_summary(body, areas):
    entries = (body or {}).get('data', {}).get('areas', [])
    if len(entries) != len(areas) or {entry.get('codigo') for entry in entries} != set(areas): return False
    return all(isinstance(entry.get('indicadores'), list) and len(entry['indicadores']) >= 3
               and isinstance(entry.get('evolucao'), list) for entry in entries)


if __name__ == '__main__':
    try: main()
    except Exception as error:
        # Evita imprimir resposta HTTP ou tokens em erros de bibliotecas.
        print('REST BI: FALHOU (' + type(error).__name__ + '). Confira sessoes, URL local e journal do ERP.', file=sys.stderr)
        sys.exit(1)
