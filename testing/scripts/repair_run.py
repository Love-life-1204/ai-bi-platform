"""自主修复证据：保留本轮基线，运行命令并保存真实退出码。"""
import argparse
import datetime as dt
import difflib
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
RUN = ROOT / 'testing/reports/evidence/autonomous-20261014'
SKIP = {'.git', '.venv', 'node_modules', 'target', 'dist', '__pycache__', '.idea',
        '.pytest_cache', 'test-results', 'playwright-report', 'evidence'}


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def source_files():
    for area in ('backend', 'frontend', 'ai-service', 'infrastructure', 'testing/scripts', 'testing/plans'):
        for folder, dirs, names in os.walk(ROOT / area):
            dirs[:] = [d for d in dirs if d not in SKIP]
            for name in names:
                p = Path(folder) / name
                if name.startswith('.env') or p.suffix in ('.pyc', '.sqlite3', '.jar', '.png', '.zip'):
                    continue
                yield p


def init():
    RUN.mkdir(parents=True, exist_ok=True)
    if (RUN / 'baseline.json').exists():
        print('Baseline already exists; preserved')
        return
    files = []
    for p in source_files():
        rel = p.relative_to(ROOT)
        dest = RUN / 'before' / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(p, dest)
        files.append({'path': rel.as_posix(), 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()})
    status = subprocess.check_output(['git', 'status', '--short'], cwd=ROOT)
    (RUN / 'git-status-before.txt').write_bytes(status)
    (RUN / 'tracked-before.patch').write_bytes(subprocess.check_output(['git', 'diff', '--binary'], cwd=ROOT))
    write_json(RUN / 'baseline.json', {'created_at': dt.datetime.now(dt.timezone.utc).isoformat(),
               'git_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
               'files': files})
    print('Preserved baseline:', len(files), 'files')


def run(label, cwd, command):
    RUN.mkdir(parents=True, exist_ok=True)
    log = RUN / (label + '.log')
    meta = RUN / (label + '.json')
    if log.exists() or meta.exists():
        raise RuntimeError('Evidence label already exists; use a new attempt label')
    started = dt.datetime.now(dt.timezone.utc).isoformat()
    source_hashes = {}
    for area in ('backend/src', 'frontend/src', 'ai-service/app'):
        for folder, dirs, names in os.walk(ROOT / area):
            dirs[:] = [d for d in dirs if d not in SKIP]
            for name in names:
                p = Path(folder) / name
                source_hashes[p.relative_to(ROOT).as_posix()] = hashlib.sha256(p.read_bytes()).hexdigest()
    begin = time.monotonic()
    with log.open('wb') as out:
        try:
            process = subprocess.run(command, cwd=ROOT / cwd, stdout=out, stderr=subprocess.STDOUT,
                                     env={**os.environ, 'PYTHONIOENCODING': 'utf-8'})
            code = process.returncode
        except OSError as exc:
            out.write(str(exc).encode('utf-8'))
            code = 127
    write_json(meta, {'label': label, 'command': command, 'cwd': str(ROOT / cwd),
                      'started_at': started, 'finished_at': dt.datetime.now(dt.timezone.utc).isoformat(),
                      'duration_seconds': round(time.monotonic() - begin, 3), 'exit_code': code,
                      'git_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                      'source_sha256_at_start': source_hashes})
    report_dir = ROOT / cwd / 'target/surefire-reports'
    if report_dir.exists():
        dest = RUN / (label + '-junit')
        dest.mkdir(exist_ok=True)
        for p in report_dir.glob('TEST-*.xml'):
            if p.stat().st_mtime >= time.time() - (time.monotonic() - begin) - 2:
                shutil.copyfile(p, dest / p.name)
    print(json.dumps({'label': label, 'exit_code': code, 'seconds': round(time.monotonic()-begin, 3)}))
    print(log.read_text(encoding='utf-8', errors='replace')[-6000:])
    return code


def changes():
    before = {x['path']: x['sha256'] for x in json.loads((RUN / 'baseline.json').read_text(encoding='utf-8'))['files']}
    manifest = RUN / 'owned-files.json'
    if not manifest.exists():
        raise RuntimeError('Explicit owned-files manifest required; do not mix concurrent workspace edits into repair patch')
    owned = set(json.loads(manifest.read_text(encoding='utf-8'))['files'])
    result, patch, unattributed = [], [], []
    for p in source_files():
        rel = p.relative_to(ROOT).as_posix()
        sha = hashlib.sha256(p.read_bytes()).hexdigest()
        if before.get(rel) == sha:
            continue
        if rel not in owned:
            unattributed.append({'path': rel, 'after_sha256': sha})
            continue
        result.append({'path': rel, 'before_sha256': before.get(rel), 'after_sha256': sha})
        old = RUN / 'before' / rel
        try:
            a = old.read_text(encoding='utf-8-sig').splitlines(keepends=True) if old.exists() else []
            b = p.read_text(encoding='utf-8-sig').splitlines(keepends=True)
            patch.extend(difflib.unified_diff(a, b, fromfile='a/'+rel, tofile='b/'+rel))
        except UnicodeError:
            pass
    write_json(RUN / 'source-changes.json', result)
    write_json(RUN / 'unattributed-workspace-changes.json', unattributed)
    (RUN / 'repair.patch').write_text(''.join(patch), encoding='utf-8')
    print('Changed or added:', len(result), 'files')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['init', 'run', 'changes'])
    parser.add_argument('--label')
    parser.add_argument('--cwd', default='.')
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if args.action == 'init': init()
    elif args.action == 'changes': changes()
    else:
        command = args.command[1:] if args.command and args.command[0] == '--' else args.command
        raise SystemExit(run(args.label, args.cwd, command))
