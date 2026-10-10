"""本地数据库迁移入口；密码仅从环境/.env 读取，永不打印连接配置。"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import sys
import urllib.request
import base64

ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
BASELINE = 'BASELINE_001'


def schema_sql(engine):
    return (ROOT/'sql'/f'{engine}.sql').read_text(encoding='utf-8')


def mysql_tables():
    return re.findall(r'^CREATE TABLE `([a-z_]+)`', schema_sql('mysql'), re.MULTILINE)


def legacy_checksums(engine):
    return dict(re.findall(r'^-- legacy (\S+) ([a-f0-9]{64})$', schema_sql(engine), re.MULTILINE))


def validate_mysql_layout(cur):
    """接管旧历史前检查对象完整性；不修改任何业务表。"""
    cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'infrastructure_schema_history'")
    if {r[0] for r in cur.fetchall()} != set(mysql_tables()):
        raise RuntimeError('Existing table inventory differs from the final baseline')
    cur.execute('SELECT constraint_name FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()')
    expected = set(re.findall(r'ADD CONSTRAINT `(fk_\d+)`', schema_sql('mysql')))
    if {r[0] for r in cur.fetchall()} != expected:
        raise RuntimeError('Existing foreign keys differ from the final baseline')


def config():
    result = {}
    path = ROOT/'.env'
    if path.exists():
        for line in path.read_text(encoding='utf-8-sig').splitlines():
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                value = value.strip()
                if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                    value = value[1:-1]
                result[key.strip()] = value
    result.update(os.environ)
    return result


def connect(database=True):
    import pymysql
    c = config()
    name = c.get('MYSQL_DATABASE', 'ai_bi_core')
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,63}', name):
        raise ValueError('Invalid MYSQL_DATABASE')
    conn = pymysql.connect(host=c.get('MYSQL_HOST', '127.0.0.1'), port=int(c.get('MYSQL_PORT', '3306')),
                          user=c.get('MYSQL_USER', 'root'), password=c.get('MYSQL_PASSWORD', ''),
                          database=name if database else None, charset='utf8mb4',
                          autocommit=True, connect_timeout=8, read_timeout=120, write_timeout=120)
    with conn.cursor() as cur:
        cur.execute("SET time_zone = '+00:00'")
        cur.execute("SET SESSION sql_mode = 'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'")
    return conn


def statements(sql):
    """分割本项目纯 DDL/DML，保护引号与注释；不支持存储过程 DELIMITER。"""
    result, buf, quote, i = [], [], None, 0
    while i < len(sql):
        c = sql[i]
        if quote:
            buf.append(c)
            if c == '\\' and i+1 < len(sql):
                i += 1
                buf.append(sql[i])
            elif c == quote:
                if i+1 < len(sql) and sql[i+1] == quote:
                    i += 1
                    buf.append(sql[i])
                else:
                    quote = None
        elif sql[i:i+2] == '--':
            i = sql.find('\n', i)
            if i < 0:
                break
            buf.append('\n')
        elif c in "'\"`":
            quote = c
            buf.append(c)
        elif c == ';':
            if ''.join(buf).strip():
                result.append(''.join(buf).strip())
            buf = []
        else:
            buf.append(c)
        i += 1
    if quote:
        raise ValueError('Unclosed SQL quote')
    if ''.join(buf).strip():
        result.append(''.join(buf).strip())
    return result


def migrate_mysql():
    name = config().get('MYSQL_DATABASE', 'ai_bi_core')
    with connect(False) as conn, conn.cursor() as cur:
        cur.execute('SELECT VERSION()')
        version = cur.fetchone()[0]
        numeric = tuple(map(int, version.split('-')[0].split('.')[:3]))
        if 'MariaDB' in version or numeric < (8, 0, 16):
            raise RuntimeError('Requires MySQL >= 8.0.16 with enforced CHECK constraints')
        cur.execute('SELECT GET_LOCK(%s, 30)', ('aibi_migrate_'+name,))
        if cur.fetchone()[0] != 1:
            raise RuntimeError('Migration lock unavailable')
        try:
            cur.execute(f'CREATE DATABASE IF NOT EXISTS `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs')
            cur.execute(f'USE `{name}`')
            cur.execute("""CREATE TABLE IF NOT EXISTS infrastructure_schema_history (
              version VARCHAR(32) PRIMARY KEY, script VARCHAR(200) NOT NULL,
              checksum CHAR(64) NOT NULL, success TINYINT NOT NULL DEFAULT 0,
              installed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
            ) ENGINE=InnoDB""")
            path = ROOT/'sql/mysql.sql'
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            cur.execute('SELECT version, checksum, success FROM infrastructure_schema_history')
            history = {v:(checksum,success) for v,checksum,success in cur.fetchall()}
            old = history.get(BASELINE)
            if old:
                if old != (digest, 1):
                    raise RuntimeError('Final baseline changed or incomplete; inspect and forward-repair, never auto-replay DDL')
                print('SKIP mysql.sql')
            elif history:
                expected = {v:(checksum,1) for v,checksum in legacy_checksums('mysql').items()}
                if history != expected:
                    raise RuntimeError('Legacy history is incomplete or modified; refusing baseline adoption')
                validate_mysql_layout(cur)
                cur.execute('INSERT INTO infrastructure_schema_history(version,script,checksum,success) VALUES(%s,%s,%s,1)', (BASELINE,path.name,digest))
                print('ADOPTED mysql.sql: legacy history verified, no business DDL executed')
            else:
                cur.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'infrastructure_schema_history'")
                if cur.fetchone()[0]:
                    raise RuntimeError('Database is not empty and has no recognized history; refusing initialization')
                # MySQL DDL 隐式提交；先记录失败态，崩溃后禁止盲目重放。
                cur.execute('INSERT INTO infrastructure_schema_history(version,script,checksum) VALUES(%s,%s,%s)', (BASELINE,path.name,digest))
                for statement in statements(schema_sql('mysql')):
                    cur.execute(statement)
                cur.execute('UPDATE infrastructure_schema_history SET success=1 WHERE version=%s', (BASELINE,))
                print('APPLIED mysql.sql')
            print(f'MySQL {version}: migration complete')
        finally:
            cur.execute('SELECT RELEASE_LOCK(%s)', ('aibi_migrate_'+name,))


def clickhouse(sql, params=None):
    from urllib.parse import urlencode
    c = config()
    url = c.get('CLICKHOUSE_HTTP_URL', 'http://127.0.0.1:8123').rstrip('/')
    query = {'wait_end_of_query': '1', **(params or {})}
    req = urllib.request.Request(url+'/?'+urlencode(query), data=sql.encode('utf-8'), method='POST')
    credentials = c.get('CLICKHOUSE_USER','bi_migrator')+':'+c.get('CLICKHOUSE_PASSWORD','')
    req.add_header('Authorization', 'Basic '+base64.b64encode(credentials.encode()).decode())
    with urllib.request.urlopen(req, timeout=120) as response:
        return response.read().decode('utf-8').strip()


def migrate_clickhouse():
    # 基线使用 IF NOT EXISTS；只支持首次建库和相同版本重复执行。
    # 单点执行，MySQL advisory lock 保护并发迁移，CH 日志用于 checksum 核验。
    with connect() as conn, conn.cursor() as cur:
        cur.execute("SELECT GET_LOCK('aibi_clickhouse_migrate',30)")
        if cur.fetchone()[0] != 1:
            raise RuntimeError('ClickHouse migration lock unavailable')
        try:
            clickhouse('CREATE DATABASE IF NOT EXISTS ai_bi_ops')
            clickhouse('CREATE TABLE IF NOT EXISTS ai_bi_ops.schema_history (version String, checksum String, installed_at DateTime64(3,\'UTC\')) ENGINE=MergeTree ORDER BY version')
            path = ROOT/'sql/clickhouse.sql'
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            old = clickhouse('SELECT DISTINCT checksum FROM ai_bi_ops.schema_history WHERE version={v:String} FORMAT TabSeparated', {'param_v':BASELINE})
            if old:
                if old != digest:
                    raise RuntimeError('clickhouse.sql: checksum mismatch')
                print('SKIP clickhouse.sql')
            else:
                legacy = clickhouse('SELECT DISTINCT version,checksum FROM ai_bi_ops.schema_history FORMAT TabSeparated')
                if legacy:
                    actual = dict(line.split('\t') for line in legacy.splitlines())
                    if actual != legacy_checksums('clickhouse'):
                        raise RuntimeError('Unrecognized ClickHouse legacy history')
                # 初始 DDL 为 IF NOT EXISTS，中断后可以补齐未创建的对象。
                for statement in statements(schema_sql('clickhouse')):
                    clickhouse(statement)
                clickhouse('INSERT INTO ai_bi_ops.schema_history VALUES ({v:String},{c:String},now64(3))', {'param_v':BASELINE,'param_c':digest})
                print('APPLIED clickhouse.sql')
        finally:
            cur.execute("SELECT RELEASE_LOCK('aibi_clickhouse_migrate')")


def status():
    with connect() as conn, conn.cursor() as cur:
        cur.execute('SELECT version, script, success FROM infrastructure_schema_history ORDER BY version')
        for row in cur.fetchall():
            print(*row)
        cur.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'infrastructure_schema_history'")
        print('Business tables:', cur.fetchone()[0])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['migrate-mysql','migrate-clickhouse','status'])
    args = parser.parse_args()
    try:
        {'migrate-mysql':migrate_mysql, 'migrate-clickhouse':migrate_clickhouse, 'status':status}[args.action]()
    except Exception as exc:
        # 不输出配置、密码或完整连接 URL。
        print(f'ERROR {type(exc).__name__}: {exc}', file=sys.stderr)
        sys.exit(1)
