"""只读环境、源码与设计清单；不记录密钥内容。"""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "testing/reports/evidence"
OUT.mkdir(parents=True, exist_ok=True)
skip = {".git", "node_modules", ".venv", "target", "dist", ".tmp", "artifacts",
        ".idea", "testing", "__pycache__", ".pytest_cache", ".m2", "test-results", "playwright-report"}
files = []
for folder, dirs, names in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in skip]
    for name in names:
        path = Path(folder) / name
        if name == ".env" or name.endswith((".sqlite3", ".pyc")):
            continue
        files.append({"path": path.relative_to(ROOT).as_posix(),
                      "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
(OUT / "source-before.json").write_text(json.dumps(files, ensure_ascii=False, indent=2), encoding="utf-8")
versions = {}
for name, command in {
    "jdk": [r"D:\Java\bin\java.exe", "-version"],
    "maven": [r"D:\Maven\bin\mvn.cmd", "-version"],
    "node": ["node", "--version"], "npm": ["npm.cmd", "--version"],
    "system_python": ["python", "--version"],
    "ai_python": [str(ROOT / "ai-service/.venv/Scripts/python.exe"), "--version"],
    "infra_python": [str(ROOT / "infrastructure/.venv/Scripts/python.exe"), "--version"],
    "docker": ["docker", "version"], "k6": ["k6", "version"],
}.items():
    try:
        p = subprocess.run(command, capture_output=True, text=True, timeout=30, errors="replace")
        versions[name] = dict(exit_code=p.returncode, output=p.stdout+p.stderr)
    except (OSError, subprocess.TimeoutExpired) as exc:
        versions[name] = {"blocked": str(exc)}
ports = {}
for name, port in {"mysql":3306, "clickhouse":8123, "redis":6379, "rabbitmq":5672,
                   "backend":8080, "ai":8091, "frontend":4173}.items():
    with socket.socket() as sock:
        sock.settimeout(1)
        ports[name] = dict(host="127.0.0.1", port=port, listening=sock.connect_ex(("127.0.0.1", port)) == 0)
env_keys = {}
for module in ("infrastructure", "backend", "ai-service", "frontend"):
    path = ROOT / module / ".env"
    env_keys[module] = {"exists":path.exists(), "keys":re.findall(r"(?m)^([A-Z][A-Z0-9_]*)=", path.read_text(encoding="utf-8") if path.exists() else "")}
inventory = dict(versions=versions, loopback_ports=ports, env_files=env_keys,
                 git_head=subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip())
(OUT / "environment-details.json").write_text(json.dumps(inventory, ensure_ascii=False, indent=2), encoding="utf-8")
for path in (ROOT / "docs").glob("*.docx"):
    with zipfile.ZipFile(path) as z:
        tree = ET.fromstring(z.read("word/document.xml"))
        paragraphs = ["".join(p.itertext()) for p in tree.iter("{http://schemas.openxmlformats.org/wordprocessingml/2006/main}p")]
        (OUT / (path.stem + ".txt")).write_text("\n".join(paragraphs), encoding="utf-8")
print(json.dumps(inventory, ensure_ascii=False, indent=2))
print("Source files fingerprinted:", len(files))
