#!/usr/bin/env python3
"""Build the presentation plugin against pinned Graylog sources; never deploy."""
import argparse
import os
import pathlib
import shutil
import subprocess
import sys

GRAYLOG_COMMIT = '002c047579cebd085973470afe5cb7170073c8da'  # 7.1.9


def run(args, cwd=None, env=None):
    subprocess.run(args, cwd=cwd, env=env, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--graylog-source', type=pathlib.Path,
                        default=pathlib.Path.home() / '.cache/brasil-saas-graylog-bi/graylog2-server')
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parents[2]
    source = args.graylog_source.resolve()
    if not source.exists():
        source.parent.mkdir(parents=True, exist_ok=True)
        run(['git', 'clone', '--depth', '1', '--branch', '7.1.9', '--filter=blob:none', '--sparse',
             'https://github.com/Graylog2/graylog2-server.git', str(source)])
        run(['git', 'sparse-checkout', 'set', 'graylog2-web-interface'], cwd=source)
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source, text=True).strip()
    if commit != GRAYLOG_COMMIT:
        raise ValueError('Fontes Graylog diferentes do commit fixado de 7.1.9; use outra pasta')
    web = source / 'graylog2-web-interface'
    env = {**os.environ, 'PUPPETEER_SKIP_DOWNLOAD': 'true', 'npm_lifecycle_event': 'build',
           'GRAYLOG_WEB_SRC': str(web), 'TS_NODE_COMPILER_OPTIONS': '{"module":"CommonJS"}'}
    run(['npx', '--yes', 'yarn@1.22.22', 'install', '--frozen-lockfile', '--non-interactive'], cwd=web, env=env)
    run([str(web / 'node_modules/.bin/webpack'), '--config', 'webpack.vendor.ts'], cwd=web, env=env)
    plugin = root / 'integrations/graylog-bi'
    run([str(web / 'node_modules/.bin/webpack'), '--config', str(plugin / 'webpack.config.cjs')], cwd=plugin, env=env)
    # No clean here: Maven packaging includes the previously generated web bundle.
    run(['mvn', '-B', '-ntp', 'verify'], cwd=plugin)
    print('JAR gerado:', plugin / 'target/brasil-saas-graylog-bi-1.0.0.jar')
    print('Nenhum servico reiniciado ou plugin instalado.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError):
        print('Build nao concluido. Verifique Git, Node 22+, Java 21, Maven e o erro acima.', file=sys.stderr)
        sys.exit(1)
