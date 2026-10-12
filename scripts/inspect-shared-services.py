#!/usr/bin/env python3
"""Diagnostico local somente leitura; nao imprime cmdline, URI ou credenciais."""
import json
import pathlib
import pwd
import re
import subprocess


def option(arguments, name):
    for index, argument in enumerate(arguments):
        if argument == name and index + 1 < len(arguments):
            return arguments[index + 1]
        if argument.startswith(name + '='):
            return argument.split('=', 1)[1]
    return None


def processes(proc=pathlib.Path('/proc')):
    records = []
    for directory in proc.iterdir():
        if not directory.name.isdigit():
            continue
        try:
            name = (directory / 'comm').read_text().strip()
            if name not in ('java', 'mongod'):
                continue
            arguments = (directory / 'cmdline').read_bytes().decode(errors='replace').split('\0')
            kind = 'MongoDB' if name == 'mongod' else 'Java (aplicacao nao identificada)'
            if name == 'java':
                command = ' '.join(arguments)
                if 'org.graylog.datanode' in command:
                    kind = 'Graylog Data Node'
                elif 'org.graylog2.bootstrap.Main' in command or re.search(r'/graylog[^ ]*\.jar', command):
                    kind = 'Graylog Server'
                elif re.search(r'brasil[-_]saas|brasilcloud', command, re.I):
                    kind = 'ERP'
                elif 'astral' in command.lower():
                    kind = 'Astral'
            status = (directory / 'status').read_text()
            uid = int(re.search(r'^Uid:\s+(\d+)', status, re.M).group(1))
            rss = re.search(r'^VmRSS:\s+(\d+)', status, re.M)
            record = {'pid': int(directory.name), 'tipo': kind,
                      'usuario': pwd.getpwuid(uid).pw_name,
                      'rss_mib': round(int(rss.group(1)) / 1024, 1) if rss else None,
                      'cgroup': (directory / 'cgroup').read_text().strip()}
            if name == 'mongod':
                record.update(config=option(arguments, '--config') or option(arguments, '-f'),
                              dbpath=option(arguments, '--dbpath'), port=option(arguments, '--port'))
            records.append(record)
        except (OSError, KeyError, AttributeError, ValueError):
            continue
    return sorted(records, key=lambda item: item['pid'])


def main():
    records = processes()
    print(json.dumps({'processos': records,
                      'mongod_encontrados': sum(r['tipo'] == 'MongoDB' for r in records),
                      'graylog_servers_encontrados': sum(r['tipo'] == 'Graylog Server' for r in records)},
                     indent=2, ensure_ascii=False))
    for command in (['systemctl', 'show', 'mongod', 'graylog-server', 'graylog-datanode',
                     '-p', 'LoadState', '-p', 'MainPID', '-p', 'ActiveState'], ['free', '-m']):
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=10)
            print(result.stdout.strip() or 'Diagnostico indisponivel: ' + command[0])
        except (OSError, subprocess.TimeoutExpired):
            print('Diagnostico indisponivel:', command[0])
    print('Nenhum processo parado; verificar dados e consumidores antes de consolidar MongoDB.')


if __name__ == '__main__':
    main()
