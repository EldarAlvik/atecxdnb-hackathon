"""Run every eval task against every model in models.yaml, then write the reports.

    uv run python evals/run.py                         # all models, all tasks
    uv run python evals/run.py --models haiku luna     # only models whose id contains one of these
    uv run python evals/run.py --tasks call_scenarios  # only some tasks
    uv run python evals/run.py --limit 4 --epochs 3    # quick check / repeat for variance

Logs go to logs/<run-name>/ and are reusable: rerunning the same command
with the same --name resumes and skips work that already finished.
Models whose API key is missing are skipped with a warning.
"""

from __future__ import annotations

import argparse
import os
import sys
from datetime import datetime
from pathlib import Path

import yaml
from inspect_ai import eval_set

HERE = Path(__file__).parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE))
import providers  # noqa: E402,F401  (.env, codex + NTNU providers)

TASKS = {
    "scam_detection": f"{HERE / 'scam_eval.py'}@scam_detection",
    "call_scenarios": f"{HERE / 'call_scenarios.py'}@call_scenarios",
    "call_scenarios_midway": f"{HERE / 'call_scenarios.py'}@call_scenarios_midway",
    "safe_word": f"{HERE / 'scam_eval.py'}@safe_word",
    "safe_word_isolated": f"{HERE / 'scam_eval.py'}@safe_word_isolated",
}
NEEDS_TOOLS = {"safe_word", "safe_word_isolated"}
KEY_FOR_PROVIDER = {"anthropic": "ANTHROPIC_API_KEY", "openai": "OPENAI_API_KEY"}


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--models", nargs="*", help="substring filter on model ids")
    p.add_argument("--tasks", nargs="*", help="substring filter on task names")
    p.add_argument("--limit", type=int, help="samples per task (for a quick test)")
    p.add_argument("--epochs", type=int, default=1, help="repeat each sample N times")
    p.add_argument("--name", default=datetime.now().strftime("%Y%m%d-%H%M"), help="log folder name")
    p.add_argument("--max-connections", type=int, default=4)
    args = p.parse_args()

    roster = yaml.safe_load((HERE / "models.yaml").read_text(encoding="utf-8"))["models"]
    if args.models:
        roster = [m for m in roster if any(f in m["model"] for f in args.models)]
    usable = []
    for m in roster:
        key = KEY_FOR_PROVIDER.get(m["model"].split("/")[0])
        if key and not os.environ.get(key):
            print(f"skipping {m['model']}: {key} is not set (.env)")
            continue
        usable.append(m)
    tasks = {k: v for k, v in TASKS.items() if not args.tasks or any(f in k for f in args.tasks)}
    if not usable or not tasks:
        sys.exit("Nothing to run: check --models / --tasks filters and .env keys.")

    log_dir = ROOT / "logs" / args.name
    # eval_set wants one task list for all its models, so models that can't
    # run tool tasks (Codex) get their own group.
    groups = {
        "tools": ([m["model"] for m in usable if m.get("tools", True)], list(tasks)),
        "no-tools": (
            [m["model"] for m in usable if not m.get("tools", True)],
            [t for t in tasks if t not in NEEDS_TOOLS],
        ),
    }
    for group, (models, names) in groups.items():
        if not models or not names:
            continue
        print(f"[{group}] {len(models)} models x {len(names)} tasks -> {log_dir / group}")
        eval_set(
            tasks=[tasks[n] for n in names],
            model=models,
            log_dir=str(log_dir / group),
            limit=args.limit,
            epochs=args.epochs,
            max_connections=args.max_connections,
            retry_attempts=2,
            fail_on_error=False,
            # lets models be added to an existing run later
            log_dir_allow_dirty=True,
        )

    from report import build_report

    build_report(log_dir)
    if "call_scenarios" in tasks:
        import subprocess

        subprocess.run([sys.executable, str(HERE / "report_calls.py"), "--log-dir", str(log_dir)], check=False)


if __name__ == "__main__":
    main()
