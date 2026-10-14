from __future__ import annotations

import argparse
import json

from app.evaluation.semantic_eval import evaluate_fixture


def main() -> int:
    parser = argparse.ArgumentParser(description="运行 P2 高级语义检索独立评测")
    parser.add_argument("fixture", help="评测 JSON 路径")
    args = parser.parse_args()
    report = evaluate_fixture(args.fixture)
    print(json.dumps(report.to_dict(), ensure_ascii=False, indent=2))
    return 0 if report.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
