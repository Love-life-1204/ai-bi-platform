"""当前环境与迁移差异只读审计；不执行数据库DDL/DML，不输出秘密。"""
import datetime as dt
import hashlib
import json
from pathlib import Path
import socket
import subprocess
import sys
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'infrastructure/scripts'))
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
import db

OUT = ROOT / 'testing/reports/evidence/autonomous-20261014'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    config = db.config()
    ch = urlparse(config.get('CLICKHOUSE_HTTP_URL', 'http://127.0.0.1:8123'))
    report = {'audited_at': dt.datetime.now(dt.timezone.utc).isoformat(),
              'operations': 'read_only', 'execution_host': socket.gethostname(),
              'endpoints': {'mysql': {'host': config.get('MYSQL_HOST', '127.0.0.1'),
                          'port': int(config.get('MYSQL_PORT', '3306')),
                          'database': config.get('MYSQL_DATABASE', 'ai_bi_core')},
                          'clickhouse': {'host': ch.hostname, 'port': ch.port or 8123}},
              'versions': {}, 'databases': {}}
    for name, command in {'java': [r'D:\Java\bin\java.exe', '-version'],
                          'maven': [r'D:\Maven\bin\mvn.cmd', '-version'],
                          'node': ['node', '--version'], 'npm': ['npm.cmd', '--version'],
                          'python': [sys.executable, '--version'], 'docker': ['docker', '--version'],
                          'k6': ['k6', 'version']}.items():
        try:
            result = subprocess.run(command, capture_output=True, timeout=20)
            report['versions'][name] = {'exit_code': result.returncode,
                'output': (result.stdout + result.stderr).decode('utf-8', errors='replace')[:1500]}
        except (OSError, subprocess.TimeoutExpired) as exc:
            report['versions'][name] = {'unavailable': type(exc).__name__}
    try:
        with db.connect() as conn, conn.cursor() as cur:
            cur.execute('START TRANSACTION READ ONLY')
            cur.execute('SELECT DATABASE(), @@hostname, @@port, VERSION()')
            mysql = {'identity': cur.fetchone()}
            cur.execute('SELECT version,script,checksum,success,installed_at FROM infrastructure_schema_history ORDER BY version')
            mysql['history'] = cur.fetchall()
            cur.execute('SELECT table_name,column_name,ordinal_position,column_type,is_nullable,column_default,extra '
                        'FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position')
            mysql['columns'] = cur.fetchall()
            cur.execute('SELECT table_name,index_name,non_unique,seq_in_index,column_name,sub_part '
                        'FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index')
            mysql['indexes'] = cur.fetchall()
            cur.execute('SELECT table_name,constraint_name,column_name,referenced_table_name,referenced_column_name '
                        'FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() ORDER BY table_name,constraint_name,ordinal_position')
            mysql['constraints'] = cur.fetchall()
            actual = {row[0] for row in mysql['columns']}
            mysql['missing_tables'] = sorted(set(db.mysql_tables()) - actual)
            mysql['table_definitions'] = {}
            for table in sorted(actual):
                if not table.replace('_', '').isalnum():
                    raise RuntimeError('Unsafe table name')
                cur.execute('SHOW CREATE TABLE `' + table + '`')
                mysql['table_definitions'][table] = cur.fetchone()[1]
            conn.rollback()
            report['databases']['mysql'] = mysql
    except Exception as exc:
        report['databases']['mysql'] = {'blocked': type(exc).__name__}
    try:
        report['databases']['clickhouse'] = {'identity': db.clickhouse('SELECT hostName(), version() FORMAT TabSeparated')}
    except Exception as exc:
        report['databases']['clickhouse'] = {'blocked': type(exc).__name__}
    report['migration_hashes'] = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in [ROOT/'infrastructure/sql/mysql.sql', *db.migration_files(db.MYSQL_MIGRATIONS)]}
    (OUT/'environment-and-schema-audit.json').write_text(json.dumps(report, ensure_ascii=False, indent=2,
                                                                  default=str)+'\n', encoding='utf-8')
    summary = {'versions': report['versions'], 'endpoints': report['endpoints'],
               'mysql': {k:v for k,v in report['databases']['mysql'].items() if k in ('identity','missing_tables','blocked')},
               'clickhouse': report['databases']['clickhouse']}
    print(json.dumps(summary, ensure_ascii=False, indent=2, default=str))


if __name__ == '__main__': main()
