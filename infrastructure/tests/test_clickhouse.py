"""ClickHouse 真库回归，用临时测试库，不向正式分析表写样例。"""
from pathlib import Path
import json
import secrets
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'scripts'))
from db import clickhouse, ROOT, statements


SALES_NET_SQL = """
-- 参数只允许由可信 QueryCompiler 提供；本示例不是独立授权边界。
-- 先过滤发布快照，再按业务键取最新，最后按当前属性执行 RLS / 删除 / 指标过滤。
SELECT row_data.5 AS currency,
       sum(row_data.6 - row_data.7) AS net_sales_minor_units
FROM
(
    SELECT source_id, order_id,
           argMax(tuple(is_deleted, region_id, department_id, order_status,
                        currency, paid_minor_units, refunded_minor_units, business_time),
                  tuple(source_version, publish_seq, row_event_key)) AS row_data
    FROM ai_bi_dwd.sales_order_event
    WHERE tenant_id = {tenant_id:UInt64}
      AND dataset_id = {dataset_id:UInt64}
      AND generation_id = {generation_id:UInt64}
      AND publish_seq <= {committed_seq:UInt64}
    GROUP BY source_id, order_id
)
WHERE row_data.1 = 0
  AND row_data.2 IN {allowed_regions:Array(String)}
  AND row_data.4 IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')
  AND row_data.8 >= {start_at:DateTime64(3, 'UTC')}
  AND row_data.8 < {end_at:DateTime64(3, 'UTC')}
GROUP BY row_data.5
"""


class ClickHouseTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.database = 'aibi_test_'+secrets.token_hex(8)
        clickhouse(f'CREATE DATABASE {cls.database}')
        ddl = (ROOT/'sql/clickhouse.sql').read_text(encoding='utf-8')
        table_ddl = next(s for s in statements(ddl) if 'CREATE TABLE IF NOT EXISTS ai_bi_dwd.sales_order_event' in s)
        clickhouse(table_ddl.replace('ai_bi_dwd', cls.database))
        cls.sql = SALES_NET_SQL.replace('ai_bi_dwd', cls.database).strip()
        common = dict(tenant_id=1,dataset_id=10,source_id=100,generation_id=1,publish_seq=1,batch_id=1,source_version=1,is_deleted=0,region_id='EAST',department_id='D1',customer_id='C1',order_status='PAID',currency='CNY',paid_minor_units=10000,refunded_minor_units=0,immutable_partition_date='2026-10-01',business_time='2026-10-01 00:00:00',row_hash='a'*64)
        rows = []
        def row(order, **kw):
            data = dict(common, order_id=order, business_key=order, row_event_key=order+'-1')
            data.update(kw)
            rows.append(data)
        row('A')
        row('A')  # 同事件重放不能重复计算。
        row('B', paid_minor_units=20000, refunded_minor_units=5000,order_status='PARTIALLY_REFUNDED')
        row('C', paid_minor_units=30000, order_status='CANCELLED')
        row('A',tenant_id=2, paid_minor_units=999999)  # 相同业务 ID 的另一租户。
        row('A',publish_seq=2,source_version=2,row_event_key='A-2',paid_minor_units=90000,region_id='NORTH')
        row('B',publish_seq=3,source_version=2,row_event_key='B-2',is_deleted=1)
        row('A',generation_id=2,paid_minor_units=5000)
        clickhouse(f'INSERT INTO {cls.database}.sales_order_event FORMAT JSONEachRow\n'+'\n'.join(json.dumps(r) for r in rows))

    @classmethod
    def tearDownClass(cls):
        # 只允许删除本次随机生成、固定前缀的测试库。
        assert cls.database.startswith('aibi_test_') and len(cls.database)==26
        clickhouse(f'DROP DATABASE {cls.database} SYNC')

    def revenue(self, seq=1, generation=1, tenant=1, regions="['EAST']"):
        return clickhouse(self.sql+' FORMAT JSONEachRow', {'param_tenant_id':tenant,'param_dataset_id':10,'param_generation_id':generation,'param_committed_seq':seq,'param_allowed_regions':regions,'param_start_at':'2026-10-01 00:00:00','param_end_at':'2026-11-01 00:00:00'})

    def test_refund_cancel_replay_and_unpublished(self):
        self.assertEqual(json.loads(self.revenue())['net_sales_minor_units'], '25000')

    def test_rls_after_latest_version(self):
        self.assertEqual(json.loads(self.revenue(seq=2))['net_sales_minor_units'], '15000')

    def test_delete_after_deduplication(self):
        self.assertEqual(self.revenue(seq=3), '')

    def test_generation_switch(self):
        self.assertEqual(json.loads(self.revenue(generation=2))['net_sales_minor_units'], '5000')

    def test_tenant_isolation(self):
        self.assertEqual(json.loads(self.revenue(tenant=2))['net_sales_minor_units'], '999999')


if __name__ == '__main__':
    unittest.main(verbosity=2)
