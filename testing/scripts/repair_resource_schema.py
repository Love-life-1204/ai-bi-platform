"""演练 D07 的新增依赖；仅允许本轮登记的空业务测试库，绝不连接业务库写入。"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'infrastructure/scripts'))
sys.path.insert(0, str(ROOT/'testing/scripts'))
import db
import repair_database


def definitions():
    text = (ROOT/'backend/src/main/java/com/aibi/platform/identity/service/ResourceActionCatalog.java').read_text(encoding='utf-8')
    return [(int(i), code, name, resource, action) for i, code, name, resource, action in
            re.findall(r'new Definition\((\d+), "([^"]+)", "([^"]+)", "([^"]+)", "([^"]+)"\)', text)]


def verify_snapshot_table(cur):
    cur.execute("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bi_ingestion_auth_snapshot' ORDER BY ORDINAL_POSITION")
    assert list(cur.fetchall()) == [('tenant_id','bigint unsigned','NO'), ('task_type','varchar(32)','NO'),
            ('task_id','bigint unsigned','NO'), ('snapshot_json','json','NO'), ('created_at','datetime(3)','NO')]
    cur.execute("SHOW INDEX FROM bi_ingestion_auth_snapshot")
    indexes = cur.fetchall()
    assert [(row[2], row[4]) for row in indexes] == [('PRIMARY','tenant_id'),('PRIMARY','task_type'),('PRIMARY','task_id')]
    cur.execute("SELECT COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bi_ingestion_auth_snapshot' AND REFERENCED_TABLE_NAME IS NOT NULL")
    assert list(cur.fetchall()) == [('tenant_id','sys_tenant','id')]
    cur.execute("SELECT TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bi_ingestion_auth_snapshot'")
    assert cur.fetchone()[0] == 'utf8mb4_0900_as_cs'


def run(name, apply):
    # Source identity is read-only. The sole write target is the explicitly owned random test DB.
    with db.connect(False) as source:
        repair_database.require_owned(name, source)
    expected = json.loads((repair_database.OUT/'jdbc-isolation.json').read_text(encoding='utf-8'))['database']
    if name != expected:
        raise RuntimeError('Only the registered new-schema JDBC test database is allowed; backup restore clone excluded')
    previous = os.environ.get('MYSQL_DATABASE')
    os.environ['MYSQL_DATABASE'] = name
    try:
        with db.connect() as conn, conn.cursor() as cur:
            cur.execute('SELECT DATABASE()')
            if cur.fetchone()[0] != name:
                raise RuntimeError('Isolation target mismatch')
            if apply:
                cur.execute('SELECT GET_LOCK(%s,0)', (name+':resource-repair',))
                if cur.fetchone()[0] != 1:
                    raise RuntimeError('Another isolated schema repair is running')
            cur.execute('SELECT version,checksum,success FROM infrastructure_schema_history ORDER BY version')
            original_history = cur.fetchall()
            steps = []
            if apply:
                cur.execute('''CREATE TABLE IF NOT EXISTS repair_schema_history (
                    version VARCHAR(64) PRIMARY KEY, checksum CHAR(64) NOT NULL,
                    state VARCHAR(16) NOT NULL, updated_at DATETIME(3) NOT NULL)''')
            for filename in ['R002__resource_action_permissions.sql','R003__ingestion_authorization_snapshot.sql']:
                path = ROOT/'infrastructure/sql/mysql_repairs'/filename
                checksum = hashlib.sha256(path.read_bytes()).hexdigest()
                version = filename.split('__')[0]
                ledger = None
                if apply:
                    cur.execute('SELECT checksum,state FROM repair_schema_history WHERE version=%s', (version,))
                    ledger = cur.fetchone()
                    if ledger and ledger[0] != checksum:
                        raise RuntimeError('Repair checksum changed; append a new migration')
                if version == 'R002':
                    expected_rows = definitions()
                    assert len(expected_rows) == 18
                    cur.execute("SELECT id,permission_code,permission_name,resource_type,action_code FROM sys_permission WHERE id BETWEEN 100 AND 115 OR permission_code IN ("+','.join(['%s']*18)+") ORDER BY id", tuple(row[1] for row in expected_rows))
                    existing = list(cur.fetchall())
                    expected_rows.sort()
                    prerequisites = [row for row in expected_rows if row[0] < 100]
                    if existing not in (prerequisites, expected_rows):
                        raise RuntimeError('Permission ID/code collision, missing V002 prerequisite, or partial dictionary; refuse overwrite')
                    needs_apply = existing == prerequisites
                else:
                    cur.execute("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bi_ingestion_auth_snapshot'")
                    needs_apply = cur.fetchone()[0] == 0
                    if not needs_apply:
                        verify_snapshot_table(cur)
                if ledger and ledger[1] == 'APPLIED' and needs_apply:
                    raise RuntimeError('Repair ledger and schema disagree')
                if apply:
                    cur.execute("INSERT INTO repair_schema_history VALUES (%s,%s,'PENDING',UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE updated_at=UTC_TIMESTAMP(3)", (version, checksum))
                    if needs_apply:
                        for statement in db.statements(path.read_text(encoding='utf-8')):
                            cur.execute(statement)
                    if version == 'R003':
                        verify_snapshot_table(cur)
                    cur.execute("UPDATE repair_schema_history SET state='APPLIED',updated_at=UTC_TIMESTAMP(3) WHERE version=%s AND checksum=%s", (version, checksum))
                steps.append({'version':version,'sha256':checksum,'was_missing':needs_apply,'applied':apply})
            cur.execute('SELECT version,checksum,success FROM infrastructure_schema_history ORDER BY version')
            assert cur.fetchall() == original_history
            report = {'time':dt.datetime.now(dt.timezone.utc).isoformat(), 'database':name,
                      'mode':'apply_isolated' if apply else 'readonly_audit', 'steps':steps,
                      'original_migration_history_unchanged':True, 'business_database_modified':False}
            label = 'd07-resource-schema-applied' if apply else 'd07-resource-schema-audit'
            output = repair_database.OUT/(label+'.json')
            with output.open('x', encoding='utf-8') as out:
                json.dump(report,out,ensure_ascii=False,indent=2)
            print(json.dumps(report,ensure_ascii=False))
    finally:
        if previous is None:
            os.environ.pop('MYSQL_DATABASE',None)
        else:
            os.environ['MYSQL_DATABASE'] = previous


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--database', required=True)
    parser.add_argument('--apply-isolated', action='store_true')
    args = parser.parse_args()
    run(args.database,args.apply_isolated)
