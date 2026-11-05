"""Run every eval task against every model in models.yaml, then write the report.

    uv run python evals/run.py                         # all models, all tasks
    uv run python evals/run.py --models haiku gemini   # only models whose id contains one of these
    uv run python evals/run.py --limit 4 --epochs 3    # quick check / repeat for variance

Logs go to logs/<run-name>/ and are reusable: rerunning the same command
resumes and skips work that already finished (Inspect eval-set).
"""

from __future__ import annotations

import argparse
import sys
from datetime import datetime
from pathlib import Path

import yaml
from dotenv import load_dotenv
from inspect_ai import eval_set

HERE = Path(__file__).parent
ROOT = HERE.parent
TASKS = [
    f"{HERE / 'scam_eval.py'}@scam_detection",
    f"{HERE / 'scam_eval.py'}@safe_word",
    f"{HERE / 'scam_eval.py'}@safe_word_isolated",
]


def main() -> None:
    load_dotenv(ROOT / ".env")
    p = argparse.ArgumentParser()
    p.add_argument("--models", nargs="*", help="substring filter on model ids")
    p.add_argument("--tasks", nargs="*", help="substring filter on task names")
    p.add_argument("--limit", type=int, help="samples per task (for a quick test)")
    p.add_argument("--epochs", type=int, default=1, help="repeat each sample N times")
    p.add_argument("--name", default=datetime.now().strftime("%Y%m%d-%H%M"), help="log folder name")
    p.add_argument("--max-connections", type=int, default=4)
    args = p.parse_args()

    models = [m["model"] for m in yaml.safe_load((HERE / "models.yaml").read_text(encoding="utf-8"))["models"]]
    if args.models:
        models = [m for m in models if any(f in m for f in args.models)]
    tasks = [t for t in TASKS if not args.tasks or any(f in t.split("@")[1] for f in args.tasks)]
    if not models or not tasks:
        sys.exit("Nothing to run: check --models / --tasks filters.")

    log_dir = ROOT / "logs" / args.name
    print(f"{len(models)} models x {len(tasks)} tasks -> {log_dir}")
    eval_set(
        tasks=tasks,
        model=models,
        log_dir=str(log_dir),
        limit=args.limit,
        epochs=args.epochs,
        max_connections=args.max_connections,
        retry_attempts=2,
        fail_on_error=False,
    )

    from report import build_report  # noqa: E402  (same folder)

    build_report(log_dir)


if __name__ == "__main__":
    sys.path.insert(0, str(HERE))
    main()
