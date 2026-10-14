"""Launch only an owned local test JVM and dedicated Vite/browser process. No business DB."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'testing/reports/evidence/autonomous-20261014'


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def main(label):
    if not re.fullmatch(r'[a-z][a-z0-9-]{3,63}', label):
        raise RuntimeError('Invalid evidence label')
    api_port = free_port()
    web_port = free_port()
    while web_port == api_port:
        web_port = free_port()
    jar = ROOT / 'backend/target/ai-bi-backend-0.1.0-SNAPSHOT.jar'
    with (OUT / (label + '-environment.json')).open('x', encoding='utf-8') as evidence:
        json.dump({'backend_jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                   'api_port': api_port, 'web_port': web_port, 'profile': 'test',
                   'external_datasource_and_messaging_enabled': False}, evidence, indent=2)
    with (OUT / (label + '-server.log')).open('xb') as log:
        process = subprocess.Popen([r'D:\Java\bin\java.exe', '-jar',
                                    str(ROOT / 'backend/target/ai-bi-backend-0.1.0-SNAPSHOT.jar'),
                                    '--spring.profiles.active=test', '--server.address=127.0.0.1',
                                    f'--server.port={api_port}', '--app.datasource.enabled=false',
                                    '--app.messaging.enabled=false'], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        try:
            base = f'http://127.0.0.1:{api_port}'
            for _ in range(120):
                try:
                    with urllib.request.urlopen(base + '/api/system/status', timeout=2) as response:
                        if response.status == 200:
                            break
                except OSError:
                    pass
                if process.poll() is not None:
                    raise RuntimeError('Owned test JVM failed to start')
                time.sleep(.25)
            else:
                raise RuntimeError('Owned test JVM startup timeout')
            result = subprocess.run(['node', './node_modules/@playwright/test/cli.js', 'test',
                                     'e2e/resource-id-live.spec.ts', '--config=playwright.repair.config.ts'],
                                    cwd=ROOT / 'frontend',
                                    env={**os.environ, 'REPAIR_API_BASE': base, 'REPAIR_WEB_PORT': str(web_port),
                                         'REPAIR_BROWSER_LABEL': label})
            return result.returncode
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--label', required=True)
    sys.exit(main(parser.parse_args().label))
