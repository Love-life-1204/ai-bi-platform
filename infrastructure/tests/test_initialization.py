"""在随机临时库验证最终 SQL 与初始化保护，不重建用户现有业务库。"""
import os
from pathlib import Path
import secrets
import sys
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'scripts'))
from db import BASELINE, config, connect, migrate_mysql, mysql_tables, schema_sql, statements


class InitializationTests(unittest.TestCase):
    def test_final_baseline_and_guards(self):
        database = 'aibi_init_test_'+secrets.token_hex(8)
        with connect(False) as admin, admin.cursor() as cur:
            cur.execute(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs')
            try:
                with patch.dict(os.environ, MYSQL_DATABASE=database):
                    with connect() as conn, conn.cursor() as q:
                        # 非空且没有可信历史的库必须拒绝，原对象不能被覆盖。
                        q.execute('CREATE TABLE sentinel (id INT PRIMARY KEY)')
                        with self.assertRaisesRegex(RuntimeError, 'not empty'):
                            migrate_mysql()
                        q.execute('SHOW TABLES LIKE %s', ('sentinel',))
                        self.assertIsNotNone(q.fetchone())
                        q.execute('DROP TABLE sentinel')
                    migrate_mysql()
                    migrate_mysql()
                    with connect() as conn, conn.cursor() as q:
                        q.execute('SELECT COUNT(*) FROM sys_permission')
                        self.assertEqual(q.fetchone()[0], 26)
                        q.execute("SHOW TABLES LIKE 'bi_sync_dead_letter'")
                        self.assertIsNotNone(q.fetchone())
                        q.execute("SHOW TABLES LIKE 'bi_sync_staged_row'")
                        self.assertIsNotNone(q.fetchone())
                        q.execute("SHOW COLUMNS FROM bi_batch LIKE 'deduplicated_rows'")
                        self.assertEqual(q.fetchone()[0], 'deduplicated_rows')
                        q.execute("SELECT success FROM infrastructure_schema_history WHERE version='V002'")
                        self.assertEqual(q.fetchone()[0], 1)
                        q.execute("SHOW COLUMNS FROM bi_metric LIKE 'metric_unit'")
                        self.assertEqual(q.fetchone()[0], 'metric_unit')
                        q.execute("SELECT success FROM infrastructure_schema_history WHERE version='V003'")
                        self.assertEqual(q.fetchone()[0], 1)
                        q.execute('SELECT checksum FROM infrastructure_schema_history WHERE version=%s', (BASELINE,))
                        digest = q.fetchone()[0]
                        q.execute('UPDATE infrastructure_schema_history SET checksum=%s WHERE version=%s', ('0'*64,BASELINE))
                        with self.assertRaisesRegex(RuntimeError, 'changed or incomplete'):
                            migrate_mysql()
                        q.execute('UPDATE infrastructure_schema_history SET checksum=%s, success=0 WHERE version=%s', (digest,BASELINE))
                        with self.assertRaisesRegex(RuntimeError, 'changed or incomplete'):
                            migrate_mysql()
                        q.execute('UPDATE infrastructure_schema_history SET success=1 WHERE version=%s', (BASELINE,))
                # 基线表全部存在，前向迁移只追加受版本历史保护的结构。
                cur.execute(f'SELECT table_name FROM information_schema.tables WHERE table_schema=%s', (database,))
                actual = {row[0] for row in cur.fetchall()}
                self.assertTrue(set(mysql_tables()).issubset(actual))
            finally:
                assert database.startswith('aibi_init_test_') and len(database)==31
                cur.execute(f'DROP DATABASE `{database}`')

    def test_clickhouse_final_inventory(self):
        sql = statements(schema_sql('clickhouse'))
        self.assertEqual(sum(s.startswith('CREATE DATABASE') for s in sql), 4)
        self.assertEqual(sum(s.startswith('CREATE TABLE') for s in sql), 8)


if __name__ == '__main__':
    unittest.main(verbosity=2)
