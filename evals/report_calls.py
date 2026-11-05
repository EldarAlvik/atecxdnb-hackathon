"""Summarise the latest call_scenarios log per model.

    uv run python evals/report_calls.py [--log-dir logs]
"""

from __future__ import annotations

import argparse
from collections import defaultdict

from inspect_ai.log import list_eval_logs, read_eval_log

OUTCOME = {"C": 1.0, "P": 0.5, "I": 0.0}


def stats(rows: list[dict]) -> dict[str, str]:
    def avg(xs):
        return f"{sum(xs) / len(xs):.2f}" if xs else "-"

    scam = [r for r in rows if r["expected"] == "scam"]
    safe = [r for r in rows if r["expected"] == "safe"]
    return {
        "n": str(len(rows)),
        "outcome": avg([r["outcome"] for r in rows]),
        "scam_recall": avg([1.0 if r["first_scam"] is not None else 0.0 for r in scam]),
        "on_time": avg([1.0 if r["outcome"] == 1.0 else 0.0 for r in scam]),
        "false_alarm": avg([r["false_alarm"] for r in safe]),
        "delay": avg([r["delay"] for r in scam]),
        "format": avg([r["format"] for r in rows]),
        "not_green": avg([r["not_green"] for r in rows if r.get("not_green") is not None]),
    }


def table(title: str, groups: dict[str, list[dict]]) -> None:
    cols = ["n", "outcome", "scam_recall", "on_time", "false_alarm", "delay", "format", "not_green"]
    width = max([len(title)] + [len(k) for k in groups]) + 2
    print(f"{title:<{width}}" + "".join(f"{c:>12}" for c in cols))
    for key in sorted(groups):
        s = stats(groups[key])
        print(f"{key:<{width}}" + "".join(f"{s[c]:>12}" for c in cols))
    print()


def rows_from_log(path: str) -> list[dict]:
    log = read_eval_log(path)
    out = []
    for s in log.samples or []:
        sc, md = s.scores or {}, s.metadata or {}
        if "outcome" not in sc:
            continue
        out.append(
            dict(
                id=s.id,
                lang=md.get("lang", "?"),
                category=md.get("category", "?"),
                expected=md.get("expected", "?"),
                red_flag=(
                    "before join" if md.get("flag_before_join") else "after join"
                ) if "flag_before_join" in md else "-",
                outcome=OUTCOME.get(str(sc["outcome"].value), 0.0),
                first_scam=(sc["outcome"].metadata or {}).get("first_scam"),
                false_alarm=float(sc["false_alarm"].value) if "false_alarm" in sc else 0.0,
                delay=float(sc["detection_delay"].value) if "detection_delay" in sc else 0.0,
                format=float(sc["format_ok"].value) if "format_ok" in sc else 0.0,
                not_green=(1.0 if str(sc["not_green"].value) == "C" else 0.0) if "not_green" in sc else None,
            )
        )
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--log-dir", default="logs")
    args = ap.parse_args()

    latest: dict[str, str] = {}  # model -> newest log path (list is newest first)
    for info in list_eval_logs(args.log_dir):
        log = read_eval_log(info.name, header_only=True)
        task_name = log.eval.task.split("/")[-1]
        if not task_name.startswith("call_scenarios") or log.status != "success":
            continue
        variant = task_name
        if task_name == "call_scenarios_midway":
            args_ = log.eval.task_args or {}
            variant += f"(join={args_.get('join', 0.5)}, tell_model={args_.get('tell_model', False)})"
        latest.setdefault(f"{variant} | {log.eval.model}", info.name)
    if not latest:
        print(f"no successful call_scenarios logs in {args.log_dir}")
        return

    by_model = {m: rows_from_log(p) for m, p in latest.items()}
    for m, p in latest.items():
        print(f"{m}: {p}")
    print()
    table("model", by_model)
    for m, rows in by_model.items():
        print(f"=== {m}")
        fields = ["category", "lang", "expected"]
        if any(r["red_flag"] != "-" for r in rows):
            fields.append("red_flag")
        for field in fields:
            g: dict[str, list[dict]] = defaultdict(list)
            for r in rows:
                g[str(r[field])].append(r)
            table(field, g)


if __name__ == "__main__":
    main()
