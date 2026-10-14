"""真库集成回归；数据均在事务中回滚，不污染已初始化数据库。"""
from pathlib import Path
import re
import secrets
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'scripts'))
from db import connect, mysql_foreign_keys, mysql_tables
import pymysql


class DatabaseTests(unittest.TestCase):
    def setUp(self):
        self.conn = connect()
        self.conn.begin()
        self.cur = self.conn.cursor()
        self.next_id = secrets.randbits(52) + 100000
        self.tag = str(self.next_id)
        self.user = self.insert('sys_user', email=self.tag+'@example.invalid', display_name='测试用户')
        self.ta = self.insert('sys_tenant', tenant_code='test_a_'+self.tag, tenant_name='甲')
        self.tb = self.insert('sys_tenant', tenant_code='test_b_'+self.tag, tenant_name='乙')
        self.ma = self.insert('sys_tenant_member', tenant_id=self.ta, user_id=self.user, member_name='甲成员')
        self.mb = self.insert('sys_tenant_member', tenant_id=self.tb, user_id=self.user, member_name='乙成员')
        self.wa = self.insert('sys_workspace', tenant_id=self.ta, workspace_code='main', workspace_name='空间', owner_member_id=self.ma)
        self.wb = self.insert('sys_workspace', tenant_id=self.tb, workspace_code='main', workspace_name='空间', owner_member_id=self.mb)
        self.insert('sys_workspace_member', tenant_id=self.ta, workspace_id=self.wa, member_id=self.ma)
        self.insert('sys_workspace_member', tenant_id=self.tb, workspace_id=self.wb, member_id=self.mb)

    def tearDown(self):
        self.conn.rollback()
        self.cur.close()
        self.conn.close()

    def insert(self, table, **data):
        self.next_id += 1
        data['id'] = self.next_id
        fields = ','.join('`'+k+'`' for k in data)
        self.cur.execute(f'INSERT INTO `{table}` ({fields}) VALUES ({",".join(["%s"]*len(data))})', tuple(data.values()))
        return data['id']

    def reject(self, code, func):
        with self.assertRaises(pymysql.MySQLError) as cm:
            func()
        self.assertEqual(cm.exception.args[0], code, cm.exception)

    def dataset(self, code='sales', tenant=None, workspace=None, member=None):
        return self.insert('bi_dataset', tenant_id=tenant or self.ta, workspace_id=workspace or self.wa,
                           dataset_code=code, dataset_name='销售', owner_member_id=member or self.ma)

    def test_final_schema(self):
        tables = mysql_tables()
        foreign_keys = mysql_foreign_keys()
        self.assertEqual(len(tables), len(set(tables)))
        self.assertEqual(len(foreign_keys), len(set(foreign_keys)))
        self.cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'infrastructure_schema_history'")
        self.assertEqual(set(tables), {x[0] for x in self.cur.fetchall()})
        self.cur.execute("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()")
        self.assertEqual(self.cur.fetchone()[0], len(foreign_keys))

    def test_shared_user_and_same_workspace_code(self):
        self.cur.execute('SELECT COUNT(*) FROM sys_tenant_member WHERE user_id=%s', (self.user,))
        self.assertEqual(self.cur.fetchone()[0], 2)

    def test_tenant_code_is_globally_unique(self):
        self.reject(1062, lambda: self.insert(
            'sys_tenant', tenant_code='test_a_'+self.tag, tenant_name='重复租户'))

    def test_tenant_basic_info_update_uses_optimistic_lock(self):
        statement = '''
            UPDATE sys_tenant
               SET tenant_name=%s, timezone=%s, default_currency=%s,
                   lock_version=lock_version+1
             WHERE id=%s AND lock_version=%s AND deleted_at IS NULL
        '''
        self.cur.execute(statement, ('修改后企业', 'UTC', 'USD', self.ta, 0))
        self.assertEqual(self.cur.rowcount, 1)
        self.cur.execute(statement, ('旧版本覆盖', 'UTC', 'USD', self.ta, 0))
        self.assertEqual(self.cur.rowcount, 0)
        self.cur.execute('''
            SELECT tenant_code, tenant_name, timezone, default_currency, lock_version
              FROM sys_tenant WHERE id=%s
        ''', (self.ta,))
        self.assertEqual(
            self.cur.fetchone(),
            ('test_a_'+self.tag, '修改后企业', 'UTC', 'USD', 1))

    def test_tenant_freeze_unfreeze_uses_optimistic_lock(self):
        self.cur.execute('''
            UPDATE sys_tenant
               SET tenant_status='FROZEN', lock_version=lock_version+1
             WHERE id=%s AND tenant_status='ACTIVE' AND lock_version=0
        ''', (self.ta,))
        self.assertEqual(self.cur.rowcount, 1)
        self.cur.execute('''
            UPDATE sys_tenant
               SET tenant_status='ACTIVE', lock_version=lock_version+1
             WHERE id=%s AND tenant_status='FROZEN' AND lock_version=1
        ''', (self.ta,))
        self.assertEqual(self.cur.rowcount, 1)
        self.cur.execute('SELECT tenant_status, lock_version FROM sys_tenant WHERE id=%s', (self.ta,))
        self.assertEqual(self.cur.fetchone(), ('ACTIVE', 2))

    def test_cross_tenant_workspace_rejected(self):
        self.reject(1452, lambda: self.dataset(workspace=self.wb))

    def test_cross_tenant_member_rejected(self):
        self.reject(1452, lambda: self.insert('sys_workspace_member', tenant_id=self.ta, workspace_id=self.wa, member_id=self.mb))

    def test_cross_tenant_role_rejected(self):
        role = self.insert('sys_role', tenant_id=self.tb, role_code='analyst', role_name='分析员')
        self.reject(1452, lambda: self.insert('sys_member_role', tenant_id=self.ta, member_id=self.ma, role_id=role))

    def test_wrong_asset_current_version_rejected(self):
        first, second = self.dataset('one'), self.dataset('two')
        version = self.insert('bi_dataset_version', tenant_id=self.ta, dataset_id=second, version_no=1, definition_json='{}', definition_hash='a'*64)
        self.reject(1452, lambda: self.cur.execute('UPDATE bi_dataset SET current_version_id=%s WHERE id=%s', (version,first)))
        self.cur.execute('UPDATE bi_dataset SET current_version_id=%s WHERE id=%s', (version,second))
        self.assertEqual(self.cur.rowcount, 1)

    def test_duplicate_batch_sequence_rejected(self):
        dataset = self.dataset()
        self.insert('bi_batch', tenant_id=self.ta, dataset_id=dataset, batch_code='b1', generation_id=1, publish_seq=1)
        self.reject(1062, lambda: self.insert('bi_batch', tenant_id=self.ta, dataset_id=dataset, batch_code='b2', generation_id=1, publish_seq=1))

    def test_publication_batch_must_match_snapshot(self):
        dataset = self.dataset()
        batch = self.insert('bi_batch', tenant_id=self.ta, dataset_id=dataset, batch_code='b1', generation_id=1, publish_seq=1)
        self.reject(1452, lambda: self.insert('bi_dataset_publication', tenant_id=self.ta, dataset_id=dataset, active_generation=2, committed_publish_seq=1, last_batch_id=batch))
        self.insert('bi_dataset_publication', tenant_id=self.ta, dataset_id=dataset, active_generation=1, committed_publish_seq=1, last_batch_id=batch)

    def test_quota_check_and_atomic_reservation(self):
        balance = self.insert('saas_quota_balance', tenant_id=self.ta, quota_code='AI_REQUEST', window_start='2026-10-01', window_end='2026-11-01', quota_limit=10)
        self.cur.execute('UPDATE saas_quota_balance SET reserved_amount=reserved_amount+7,lock_version=lock_version+1 WHERE id=%s AND used_amount+reserved_amount+7<=quota_limit', (balance,))
        self.assertEqual(self.cur.rowcount, 1)
        self.cur.execute('UPDATE saas_quota_balance SET reserved_amount=reserved_amount+7 WHERE id=%s AND used_amount+reserved_amount+7<=quota_limit', (balance,))
        self.assertEqual(self.cur.rowcount, 0)
        self.reject(3819, lambda: self.cur.execute('UPDATE saas_quota_balance SET used_amount=11 WHERE id=%s', (balance,)))
        self.insert('saas_quota_ledger', tenant_id=self.ta, quota_balance_id=balance, business_event_key='reserve-1', reservation_key='r1', event_type='RESERVE', amount=7, occurred_at='2026-10-09')
        self.reject(1062, lambda: self.insert('saas_quota_ledger', tenant_id=self.ta, quota_balance_id=balance, business_event_key='reserve-1', reservation_key='r1', event_type='RESERVE', amount=7, occurred_at='2026-10-09'))

    def test_commercial_runtime_state_is_durable_and_unique(self):
        self.cur.execute('''
            INSERT INTO sys_commercial_state
                (namespace_code, state_key, value_type, payload_json)
            VALUES ('quotas', %s, 'QuotaBalance', %s)
        ''', (self.tag, '{"limit":100,"used":1}'))
        self.reject(1062, lambda: self.cur.execute('''
            INSERT INTO sys_commercial_state
                (namespace_code, state_key, value_type, payload_json)
            VALUES ('quotas', %s, 'QuotaBalance', %s)
        ''', (self.tag, '{"limit":100,"used":2}')))
        self.cur.execute('''
            UPDATE sys_commercial_state SET payload_json=%s
             WHERE namespace_code='quotas' AND state_key=%s
        ''', ('{"limit":100,"used":2}', self.tag))
        self.cur.execute('''
            SELECT JSON_EXTRACT(payload_json, '$.used')
              FROM sys_commercial_state
             WHERE namespace_code='quotas' AND state_key=%s
        ''', (self.tag,))
        self.assertEqual(int(self.cur.fetchone()[0]), 2)

    def test_outbox_inbox_deduplication(self):
        self.insert('sys_inbox_dedup', tenant_id=self.ta, consumer_code='worker', event_key='event1', processed_at='2026-10-09')
        self.reject(1062, lambda: self.insert('sys_inbox_dedup', tenant_id=self.ta, consumer_code='worker', event_key='event1', processed_at='2026-10-09'))
        self.insert('sys_inbox_dedup', tenant_id=self.tb, consumer_code='worker', event_key='event1', processed_at='2026-10-09')

    def test_soft_delete_keeps_business_code_reserved(self):
        self.cur.execute('UPDATE sys_workspace SET deleted_at=UTC_TIMESTAMP(3) WHERE id=%s', (self.wa,))
        self.reject(1062, lambda: self.insert('sys_workspace', tenant_id=self.ta, workspace_code='main', workspace_name='重复', owner_member_id=self.ma))

    def test_parent_delete_restricted(self):
        self.reject(1451, lambda: self.cur.execute('DELETE FROM sys_tenant WHERE id=%s', (self.ta,)))

    def test_permission_dictionary(self):
        self.cur.execute('SELECT permission_code FROM sys_permission')
        codes = {row[0] for row in self.cur.fetchall()}
        self.assertEqual(len(codes), 26)
        self.assertTrue({
            'report:export', 'ai:ask', 'audit:read', 'subscription:manage',
            'import:manage', 'sync:manage', 'quality:manage'
        }.issubset(codes))


if __name__ == '__main__':
    unittest.main(verbosity=2)
