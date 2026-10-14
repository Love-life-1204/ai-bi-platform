"""核对交付链接与源码变更，记录临时库清理状态。"""
import difflib
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
E = ROOT / "testing/reports/evidence"
before = {x["path"]:x["sha256"] for x in json.loads((E / "source-before.json").read_text(encoding="utf-8"))}
changes = []
for folder in ("frontend/src", "backend/src", "ai-service/app", "ai-service/tests"):
    for path in (ROOT / folder).rglob("*"):
        if not path.is_file() or "__pycache__" in path.parts:
            continue
        relative = path.relative_to(ROOT).as_posix()
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if before.get(relative) != digest:
            changes.append(dict(path=relative, before_sha256=before.get(relative), after_sha256=digest))
(E / "source-changes.json").write_text(json.dumps(changes, ensure_ascii=False, indent=2), encoding="utf-8")
patch = []
for saved, current in {"client.ts":"frontend/src/api/client.ts", "config.py":"ai-service/app/core/config.py",
                       "delegation.py":"ai-service/app/security/delegation.py", "models.py":"ai-service/app/schemas/models.py"}.items():
    patch.extend(difflib.unified_diff((E / "source-before-fixes" / saved).read_text(encoding="utf-8").splitlines(True),
                                    (ROOT / current).read_text(encoding="utf-8").splitlines(True), fromfile="before/"+current, tofile="after/"+current))
(E / "verified-fixes.patch").write_text("".join(patch), encoding="utf-8")
broken = []
for document in (ROOT / "testing/reports").glob("*.md"):
    for target in re.findall(r"\]\(([^)]+)\)", document.read_text(encoding="utf-8")):
        if not target.startswith(("http", "#")) and not (document.parent / target).exists():
            broken.append(dict(document=document.name, target=target))
assert not broken, broken
assert len(list((ROOT / "testing/reports").glob("[0-9][0-9]-*.md"))) == 11
sys.path.insert(0, str(ROOT / "infrastructure/scripts"))
import db
restore = json.loads((E / "backup-drill/restore-evidence.json").read_text(encoding="utf-8-sig"))
with db.connect(False) as conn, conn.cursor() as cur:
    cur.execute("SELECT schema_name FROM information_schema.schemata WHERE schema_name IN (%s,%s)",
                (restore["sourceDatabase"], restore["restoreDatabase"]))
    remaining = cur.fetchall()
assert not remaining, "本轮备份临时库未清理"
result = dict(reports=11, links="PASS", changed_source_files=changes, backup_temporary_databases_remaining=0,
              decision="NO-GO", unresolved_defects=6)
(E / "delivery-details.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
manifest = [{"path":p.relative_to(ROOT).as_posix(),"sha256":hashlib.sha256(p.read_bytes()).hexdigest()}
            for p in (ROOT / "testing").rglob("*") if p.is_file() and "__pycache__" not in p.parts
            and p.name != "artifact-sha256.json"]
(E / "artifact-sha256.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(result, ensure_ascii=False, indent=2))
