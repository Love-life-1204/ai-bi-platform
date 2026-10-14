"""复用现有数据库测试，仅向本次创建的随机测试库写入。"""
import json
import os
from pathlib import Path
import secrets
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT / "infrastructure/scripts"), str(ROOT / "infrastructure/tests")]
import db
from test_initialization import InitializationTests
from test_mysql import DatabaseTests
from test_clickhouse import ClickHouseTests

def main():
    details = json.loads((ROOT / "testing/reports/evidence/database-details.json").read_text(encoding="utf-8"))
    if not details["mysql_local"] or not details["clickhouse_local"]:
        raise RuntimeError("只允许已确认本机数据库的独立临时库测试")
    output = {}
    def run(name, test_class):
        result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(test_class))
        output[name] = dict(tests=result.testsRun, failures=len(result.failures), errors=len(result.errors), skipped=len(result.skipped))
    run("initialization", InitializationTests)
    database = "aibi_qa_" + secrets.token_hex(8)
    previous = os.environ.get("MYSQL_DATABASE")
    with db.connect(False) as conn, conn.cursor() as cur:
        cur.execute(f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs")
        try:
            os.environ["MYSQL_DATABASE"] = database
            db.migrate_mysql()
            run("mysql", DatabaseTests)
        finally:
            if previous is None:
                os.environ.pop("MYSQL_DATABASE", None)
            else:
                os.environ["MYSQL_DATABASE"] = previous
            assert database.startswith("aibi_qa_") and len(database) == 24
            cur.execute(f"DROP DATABASE `{database}`")
    run("clickhouse", ClickHouseTests)
    (ROOT / "testing/reports/evidence/database-test-counts.json").write_text(json.dumps(output, indent=2), encoding="utf-8")
    return int(any(x["failures"] or x["errors"] for x in output.values()))

if __name__ == "__main__":
    raise SystemExit(main())
