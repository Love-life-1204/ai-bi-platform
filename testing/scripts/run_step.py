"""运行独立验收步骤，保留原始输出、退出码与北京时间。"""
import argparse
import datetime as dt
import json
import os
import pathlib
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "testing/reports/evidence"


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser()
    parser.add_argument("name")
    parser.add_argument("--cwd", default=".")
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command
    if command and command[0] == "--":
        command = command[1:]
    executable = ROOT / args.cwd / command[0]
    if executable.is_file():
        command[0] = str(executable.resolve())
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    started = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat()
    begin = time.monotonic()
    log_path = EVIDENCE / (args.name + ".log")
    try:
        with log_path.open("w", encoding="utf-8") as output:
            process = subprocess.Popen(command, cwd=ROOT / args.cwd, stdout=output,
                                       stderr=subprocess.STDOUT,
                                       env=dict(os.environ, PYTHONIOENCODING="utf-8"))
            try:
                code = process.wait(timeout=args.timeout)
            except subprocess.TimeoutExpired:
                subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                               stdout=output, stderr=subprocess.STDOUT)
                code = 124
        status = "PASS" if code == 0 else "FAIL"
    except OSError as exc:
        code, status = None, "BLOCKED"
        log_path.write_text(str(exc), encoding="utf-8")
    result = dict(name=args.name, started=started, seconds=round(time.monotonic()-begin, 2),
                  cwd=args.cwd, command=command, exit_code=code, status=status,
                  log=str(log_path.relative_to(ROOT)))
    (EVIDENCE / (args.name + ".json")).write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))
    print(log_path.read_text(encoding="utf-8", errors="replace")[-5000:])
    return 0 if code == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
