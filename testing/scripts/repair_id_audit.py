"""Read-only BIGINT resource-ID range audit. Never changes source or migration history."""
import datetime as dt
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'infrastructure/scripts'))
import db

ID_COLUMNS = set('id tenant_id user_id member_id workspace_id session_id conversation_id '
                 'task_id dataset_id dataset_version_id metric_id metric_version_id field_id '
                 'dataset_field_id parent_id role_id permission_id principal_id asset_id asset_ref_id '
                 'owner_member_id actor_user_id actor_member_id approver_member_id support_user_id '
                 'requester_member_id creator_member_id recipient_member_id datasource_id '
                 'source_id source_object_id source_column_id object_file_id error_file_id '
                 'batch_id last_batch_id generation_id active_generation current_version_id '
                 'sync_job_id run_id order_id subscription_id report_id plan_version_id chart_id '
                 'chart_version_id dashboard_id dashboard_version_id rule_id policy_id'.split())


def main():
    destination = ROOT / 'testing/reports/evidence/autonomous-20261014/d09-mysql-id-range-audit.json'
    if destination.exists():
        raise RuntimeError('Do not overwrite existing evidence')
    with db.connect() as connection, connection.cursor() as cursor:
        cursor.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ')
        cursor.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
        cursor.execute('SELECT DATABASE(),@@hostname,@@port,VERSION()')
        identity = cursor.fetchone()
        cursor.execute("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS "
                       "WHERE TABLE_SCHEMA=DATABASE() AND DATA_TYPE='bigint' ORDER BY TABLE_NAME,ORDINAL_POSITION")
        columns = cursor.fetchall()
        fields = []
        for table, name, column_type, nullable in columns:
            if name not in ID_COLUMNS:
                continue
            if not all(re.fullmatch(r'[A-Za-z0-9_]+', value) for value in (table, name)):
                raise RuntimeError('Unsafe SQL identifier')
            cursor.execute(f'SELECT MIN(`{name}`),MAX(`{name}`),COUNT(`{name}`),'
                           f'SUM(`{name}`>9223372036854775807),SUM(`{name}`<=0) FROM `{table}`')
            low, high, count, overflow, nonpositive = cursor.fetchone()
            fields.append({'table': table, 'column': name, 'sql_type': column_type, 'nullable': nullable,
                           'count': count, 'minimum': None if low is None else str(low),
                           'maximum': None if high is None else str(high),
                           'exceeds_java_long': int(overflow or 0), 'nonpositive': int(nonpositive or 0)})
        connection.rollback()
    report = {'audited_at': dt.datetime.now(dt.timezone.utc).isoformat(), 'operations': 'read_only',
              'identity': identity, 'fields': fields,
              'overflow_columns': [field for field in fields if field['exceeds_java_long']],
              'unclassified_bigint_columns': [dict(zip(('table', 'column', 'sql_type', 'nullable'), row))
                                             for row in columns if row[1] not in ID_COLUMNS],
              'compatibility': 'Positive IDs through signed Long MAX emit canonical decimal strings; '
                               'legacy exact integer JSON tokens remain accepted. No numeric fallback release.'}
    with destination.open('x', encoding='utf-8') as output:
        json.dump(report, output, ensure_ascii=False, indent=2)
        output.write('\n')
    print(json.dumps({'audited_id_columns': len(fields), 'overflow_columns': len(report['overflow_columns']),
                      'unclassified_bigint_columns': len(report['unclassified_bigint_columns'])}))


if __name__ == '__main__':
    main()
