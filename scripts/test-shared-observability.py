#!/usr/bin/env python3
"""Testa reutilizacao/conflito com comandos simulados; nao opera servicos reais."""
import os
import pathlib
import subprocess
import tempfile
import unittest

HELPER = pathlib.Path(__file__).with_name('shared-observability.sh')


class SharedServicesTest(unittest.TestCase):
    def run_case(self, processes, loaded, active, kind='mongo'):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            commands = {
                'pgrep': '#!/bin/bash\nfor ((i=0;i<${COUNT};i++)); do echo $((100+i)); done\n',
                'systemctl': '''#!/bin/bash
case "$1" in
 show) echo "$LOAD" ;;
 is-active) [[ "$ACTIVE" == yes ]] ;;
 start) echo start >> "$CALLS" ;;
 *) echo "unexpected $1" >> "$CALLS"; exit 99 ;;
esac
''',
            }
            for name, content in commands.items():
                file = root / name
                file.write_text(content); file.chmod(0o755)
            calls = root / 'calls'
            environment = dict(os.environ, PATH=f'{root}:' + os.environ['PATH'],
                               COUNT=str(processes), LOAD=loaded, ACTIVE=active, CALLS=str(calls))
            result = subprocess.run(['bash', '-c',
                'set -Eeuo pipefail; source "$1"; reuse_observability_service "$2" "$3"',
                'test', str(HELPER), kind, 'mongod.service' if kind == 'mongo' else 'graylog-server.service'],
                env=environment, capture_output=True, text=True)
            return result.returncode, calls.read_text() if calls.exists() else ''

    def test_active_instance_is_not_restarted(self):
        self.assertEqual((0, ''), self.run_case(1, 'loaded', 'yes'))

    def test_unmanaged_instance_blocks_provisioning(self):
        self.assertEqual((2, ''), self.run_case(1, 'not-found', 'no'))

    def test_duplicate_mongo_and_graylog_block_provisioning(self):
        for kind in ('mongo', 'graylog'):
            self.assertEqual((2, ''), self.run_case(2, 'loaded', 'yes', kind))

    def test_stopped_unit_is_started_once(self):
        # Simulacao mantém is-active falso e comprova propagacao da falha.
        self.assertEqual((2, 'start\n'), self.run_case(0, 'loaded', 'no'))

    def test_absent_service_is_distinct_from_conflict(self):
        self.assertEqual((1, ''), self.run_case(0, 'not-found', 'no'))


if __name__ == '__main__':
    unittest.main()
