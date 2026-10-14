"""仅读取本机数据库版本和迁移历史，不执行迁移或业务写入。"""
import json
from pathlib import Path
import sys
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "infrastructure/scripts"))
import db

config = db.config()
result = {"mysql_local":config.get("MYSQL_HOST", "127.0.0.1") in {"127.0.0.1", "localhost", "::1"},
          "clickhouse_local":urlparse(config.get("CLICKHOUSE_HTTP_URL", "http://127.0.0.1:8123")).hostname in {"127.0.0.1", "localhost", "::1"},
          "expected_mysql_tables":len(db.mysql_tables()), "expected_mysql_foreign_keys":len(db.mysql_foreign_keys())}
if result["mysql_local"]:
    try:
        with db.connect() as conn, conn.cursor() as cur:
            cur.execute("SELECT VERSION()")
            result["mysql_version"] = cur.fetchone()[0]
            cur.execute("SELECT version,success FROM infrastructure_schema_history ORDER BY version")
            result["mysql_history"] = cur.fetchall()
            cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()")
            actual = {r[0] for r in cur.fetchall()}
            result["missing_tables"] = sorted(set(db.mysql_tables()) - actual)
    except Exception as exc:
        result["mysql_error_type"] = type(exc).__name__
        result["mysql_error_code"] = exc.args[0] if exc.args and isinstance(exc.args[0], int) else None
if result["clickhouse_local"]:
    try:
        result["clickhouse_version"] = db.clickhouse("SELECT version()")
    except Exception as exc:
        result["clickhouse_error_type"] = type(exc).__name__
print(json.dumps(result, ensure_ascii=False, indent=2))
(ROOT / "testing/reports/evidence/database-details.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
