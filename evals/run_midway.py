"""Run the join-midway variants on the non-GPT models.

    uv run python evals/run_midway.py [--name full-20261105] [--limit N]
"""

import argparse
import sys
from pathlib import Path

from inspect_ai import eval_set

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
import providers  # noqa: E402,F401
from call_scenarios import call_scenarios_midway  # noqa: E402

MODELS = [
    "anthropic/claude-haiku-5-5",
    "openai-api/ntnu/deepseek-ai/DeepSeek-V4.1-Flash-1017-instant",
    "openai-api/ntnu/Qwen/Qwen3.8-27B-FP8-instant",
]
VARIANTS = [
    dict(join=0.5, tell_model=False),
    dict(join=0.75, tell_model=False),
    dict(join=0.75, tell_model=True),
]

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--name", default="full-20261105")
    ap.add_argument("--limit", type=int)
    args = ap.parse_args()
    log_dir = HERE.parent / "logs" / args.name / "midway"
    eval_set(
        tasks=[call_scenarios_midway(**v) for v in VARIANTS],
        model=MODELS,
        log_dir=str(log_dir),
        limit=args.limit,
        max_connections=6,
        retry_attempts=2,
        fail_on_error=False,
        log_dir_allow_dirty=True,
    )
    import subprocess

    subprocess.run([sys.executable, str(HERE / "report_calls.py"), "--log-dir", str(log_dir)], check=False)
