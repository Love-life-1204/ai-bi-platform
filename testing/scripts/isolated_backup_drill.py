"""复用备份脚本并对恢复数据补充真值验证，源和目标均为本轮临时库。"""
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "infrastructure/scripts"))
import db

def main():
    details = json.loads((ROOT / "testing/reports/evidence/database-details.json").read_text(encoding="utf-8"))
    assert details["mysql_local"]
    suffix = secrets.token_hex(8)
    source, target = "aibi_qa_backup_"+suffix, "aibi_restore_qa_"+suffix
    started = time.monotonic()
    with db.connect(False) as connection, connection.cursor() as cur:
        cur.execute(f"CREATE DATABASE `{source}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs")
        try:
            old = os.environ.get("MYSQL_DATABASE")
            os.environ["MYSQL_DATABASE"] = source
            try:
                db.migrate_mysql()
            finally:
                if old is None:
                    os.environ.pop("MYSQL_DATABASE", None)
                else:
                    os.environ["MYSQL_DATABASE"] = old
            cur.execute(f"CREATE TABLE `{source}`.qa_orders (id INT PRIMARY KEY, tenant_id INT, paid DECIMAL(18,2), refund DECIMAL(18,2), status VARCHAR(32))")
            cur.execute(f"INSERT INTO `{source}`.qa_orders VALUES (1,1,100,0,'PAID'),(2,1,200,50,'PAID'),(3,1,300,0,'CANCELLED'),(4,2,9999,0,'PAID')")
            evidence = ROOT / "testing/reports/evidence/backup-drill"
            command = ["pwsh", "-NoProfile", "-File", str(ROOT / "scripts/p1/Invoke-BackupRestoreDrill.ps1"),
                       "-SourceDatabase", source, "-RestoreDatabase", target, "-KeepRestoreDatabase", "-EvidenceDirectory", str(evidence)]
            completed = subprocess.run(command, cwd=ROOT, timeout=180)
            if completed.returncode:
                raise RuntimeError("既有备份恢复脚本执行失败")
            cur.execute(f"SELECT * FROM `{source}`.qa_orders ORDER BY id")
            original = cur.fetchall()
            cur.execute(f"SELECT * FROM `{target}`.qa_orders ORDER BY id")
            assert cur.fetchall() == original, "恢复行数据不一致"
            cur.execute(f"SELECT SUM(paid-refund) FROM `{target}`.qa_orders WHERE tenant_id=1 AND status='PAID'")
            assert str(cur.fetchone()[0]) == "250.00"
            result = dict(status="PASS", assertions=3, source_scope="本轮临时完整 schema + 4 条人工合成订单",
                seconds=round(time.monotonic()-started, 3), production_rto_rpo="未验证",
                checks=["既有脚本的表数量一致", "四行及 Decimal 数据完全一致", "租户 1 净销售额 250.00"])
            (ROOT / "testing/reports/evidence/backup-verification.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
            print(json.dumps(result, ensure_ascii=False))
        finally:
            assert source == "aibi_qa_backup_"+suffix and target == "aibi_restore_qa_"+suffix
            cur.execute(f"DROP DATABASE IF EXISTS `{target}`")
            cur.execute(f"DROP DATABASE `{source}`")

if __name__ == "__main__":
    main()
