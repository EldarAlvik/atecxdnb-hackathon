"""Turn Inspect logs into a model comparison: accuracy (per language and
category), price, speed, limitations, and every failed case.

    uv run python evals/report.py                 # newest folder in logs/
    uv run python evals/report.py logs/20261105-1400

Writes report.md and failures.csv into the log folder and prints the summary.
"""

from __future__ import annotations

import csv
import statistics
import sys
from collections import defaultdict
from pathlib import Path

import yaml
from inspect_ai.log import EvalLog, list_eval_logs, read_eval_log

HERE = Path(__file__).parent
OWN_TASKS = {"scam_detection", "safe_word", "safe_word_isolated"}
ROOT = HERE.parent


# ---------------------------------------------------------------------------
# Prices ($ per million tokens)
# ---------------------------------------------------------------------------


def load_prices() -> dict[str, dict[str, float]]:
    """Map Inspect model string -> {"input", "output"} in $/M tokens, from models.yaml."""
    roster = yaml.safe_load((HERE / "models.yaml").read_text(encoding="utf-8"))["models"]
    return {e["model"]: e["price"] for e in roster if "price" in e}


def sample_cost(sample, prices) -> float | None:
    total = 0.0
    for model, usage in (sample.model_usage or {}).items():
        if usage.total_cost is not None:
            total += usage.total_cost
            continue
        price = prices.get(model)
        if price is None:
            return None
        # cache reads/writes priced at the full input rate: an upper bound
        inp = (
            usage.input_tokens
            + (usage.input_tokens_cache_read or 0)
            + (usage.input_tokens_cache_write or 0)
        )
        total += inp * price["input"] / 1e6 + usage.output_tokens * price["output"] / 1e6
    return total


# ---------------------------------------------------------------------------
# Log loading
# ---------------------------------------------------------------------------


def latest_logs(log_dir: Path) -> list[EvalLog]:
    """Newest successful-or-not log per (model, task)."""
    newest: dict[tuple[str, str], EvalLog] = {}
    for info in list_eval_logs(str(log_dir)):
        log = read_eval_log(info)
        if log.eval.task.split("/")[-1] not in OWN_TASKS:
            continue  # call_scenarios has its own report (report_calls.py)
        key = (log.eval.model, log.eval.task)
        if key not in newest or log.eval.created > newest[key].eval.created:
            newest[key] = log
    return list(newest.values())


def pct(xs) -> str:
    xs = list(xs)
    return f"{100 * sum(xs) / len(xs):.0f}%" if xs else "–"


def short_model(m: str) -> str:
    for prefix in ("anthropic/", "openai-api/ntnu/"):
        m = m.removeprefix(prefix)
    return m


# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------


def build_report(log_dir: Path | str) -> Path:
    log_dir = Path(log_dir)
    logs = latest_logs(log_dir)
    if not logs:
        sys.exit(f"No logs in {log_dir}")
    prices = load_prices()

    # rows[model][task] -> list of per-sample dicts
    rows: dict[str, dict[str, list[dict]]] = defaultdict(lambda: defaultdict(list))
    failures: list[dict] = []
    for log in logs:
        model, task_name = log.eval.model, log.eval.task.split("/")[-1]
        for s in log.samples or []:
            score = next(iter(s.scores.values()), None) if s.scores else None
            values = score.value if score and isinstance(score.value, dict) else {}
            row = {
                "id": s.id,
                "epoch": s.epoch,
                "lang": s.metadata.get("lang"),
                "category": s.metadata.get("category", s.metadata.get("scenario")),
                "difficulty": s.metadata.get("difficulty"),
                "target": s.target if isinstance(s.target, str) else ",".join(s.target),
                "answer": score.answer if score else None,
                "values": values,
                "error": str(s.error.message)[:200] if s.error else None,
                "seconds": s.total_time,
                "working": s.working_time,
                "cost": sample_cost(s, prices),
                "out_tokens": sum(u.output_tokens for u in (s.model_usage or {}).values()),
            }
            rows[model][task_name].append(row)
            if row["error"] or not values.get("correct"):
                failures.append(
                    {
                        "model": short_model(model),
                        "task": task_name,
                        "case": s.id,
                        "lang": row["lang"],
                        "expected": row["target"],
                        "got": row["answer"],
                        "why": row["error"] or (score.explanation if score else ""),
                        "output": (s.output.completion if s.output else "")[:400].replace("\n", " "),
                    }
                )

    md: list[str] = [f"# Scam-guard model comparison\n\nLogs: `{log_dir}`\n"]

    # --- overview -----------------------------------------------------------
    md.append("## Overview\n")
    md.append(
        "| Model | Device verdict acc | NO | EN | Missed scams | False alarms | Safe word (memory) "
        "| Safe word (isolated) | Leaks | $ / 1000 checks | Median s | p95 s | Errors |"
    )
    md.append("|" + "---|" * 13)
    overview = []
    for model, tasks in rows.items():
        scam = tasks.get("scam_detection", [])
        mem = tasks.get("safe_word", [])
        iso = tasks.get("safe_word_isolated", [])
        ok = [r for r in scam if not r["error"]]
        everything = [r for t in tasks.values() for r in t]
        scam_costs = [r["cost"] for r in scam if r["cost"] is not None]
        secs = sorted(r["seconds"] for r in scam if r["seconds"] and not r["error"])
        sw = [r for r in mem + iso if not r["error"]]
        acc = sum(r["values"].get("correct", 0) for r in ok) / len(ok) if ok else 0
        overview.append(
            (
                -acc,
                f"| {short_model(model)} "
                f"| {pct(r['values']['correct'] for r in ok)} "
                f"| {pct(r['values']['correct'] for r in ok if r['lang'] == 'no')} "
                f"| {pct(r['values']['correct'] for r in ok if r['lang'] == 'en')} "
                f"| {sum(r['values']['missed_scam'] for r in ok)} "
                f"| {sum(r['values']['false_alarm'] for r in ok)} "
                f"| {pct(r['values'].get('correct', 0) for r in mem if not r['error'])} "
                f"| {pct(r['values'].get('correct', 0) for r in iso if not r['error'])} "
                f"| {sum(1 - r['values'].get('no_leak', 1) for r in sw)} "
                f"| {'$%.3f' % (1000 * statistics.mean(scam_costs)) if scam_costs else '?'} "
                f"| {'%.1f' % statistics.median(secs) if secs else '–'} "
                f"| {'%.1f' % secs[min(len(secs) - 1, int(0.95 * len(secs)))] if secs else '–'} "
                f"| {sum(1 for r in everything if r['error'])} |",
            )
        )
    md += [line for _, line in sorted(overview)]
    md.append(
        "\n*Device verdict acc*: the board would show the right light (safe / unsure / scam). "
        "*Missed scams*: scams the board would show green, the dangerous error. "
        "*False alarms*: harmless calls the board would latch red. "
        "*Safe word*: saved the word, gave the right verdict, never leaked it, never let a caller change it. "
        "*$ / 1000 checks* and seconds are for one scam check. "
        "Cost uses the list prices in models.yaml; cache tokens are priced at the input rate, so it is an upper bound.\n"
    )

    # --- limitations ----------------------------------------------------------
    md.append("## Limitations per model\n")
    md.append("| Model | Unparseable reply (board shows AI err) | Wrong language | Never called verify tool | Saved word wrong | Let caller change word | Errors (API/refusal/timeouts) |")
    md.append("|" + "---|" * 7)
    for model, tasks in rows.items():
        scam = [r for r in tasks.get("scam_detection", []) if not r["error"]]
        sw = [r for t in ("safe_word", "safe_word_isolated") for r in tasks.get(t, []) if not r["error"]]
        errs = [r["error"] for t in tasks.values() for r in t if r["error"]]
        err_kinds = ", ".join(sorted({e.split(":")[0][:40] for e in errs})) or "–"
        md.append(
            f"| {short_model(model)} "
            f"| {sum(1 - r['values'].get('valid_json', 1) for r in scam + sw)} "
            f"| {sum(1 - r['values'].get('right_language', 1) for r in scam)} "
            f"| {sum(1 - r['values'].get('used_verify_tool', 1) for r in sw)} "
            f"| {sum(1 - r['values'].get('saved', 1) for r in sw)} "
            f"| {sum(1 - r['values'].get('word_unchanged', 1) for r in sw)} "
            f"| {len(errs)} {('(' + err_kinds + ')') if errs else ''} |"
        )

    # --- per category -------------------------------------------------------
    cats = sorted({r["category"] for t in rows.values() for r in t.get("scam_detection", [])})
    if cats:
        md.append("\n## Scam accuracy by category\n")
        md.append("| Model | " + " | ".join(cats) + " |")
        md.append("|" + "---|" * (len(cats) + 1))
        for model, tasks in rows.items():
            scam = [r for r in tasks.get("scam_detection", []) if not r["error"]]
            md.append(
                f"| {short_model(model)} | "
                + " | ".join(pct(r["values"]["correct"] for r in scam if r["category"] == c) for c in cats)
                + " |"
            )

    # --- hardest cases --------------------------------------------------------
    by_case: dict[tuple[str, str], list[int]] = defaultdict(list)
    for tasks in rows.values():
        for task_name, rs in tasks.items():
            for r in rs:
                by_case[(task_name, str(r["id"]))].append(0 if r["error"] else r["values"].get("correct", 0))
    hard = sorted(by_case.items(), key=lambda kv: sum(kv[1]) / len(kv[1]))[:15]
    md.append("\n## Hardest cases (share of models that got it right)\n")
    md.append("| Task | Case | Right |")
    md.append("|---|---|---|")
    for (task_name, case), xs in hard:
        md.append(f"| {task_name} | {case} | {pct(xs)} |")

    # --- failures -----------------------------------------------------------
    md.append(f"\n## Every failed case ({len(failures)})\n")
    md.append("Full list with model output in `failures.csv`. Open a log with `uv run inspect view` to see the whole conversation.\n")
    md.append("| Model | Task | Case | Expected | Got | Why |")
    md.append("|---|---|---|---|---|---|")
    for f in sorted(failures, key=lambda f: (f["task"], f["case"], f["model"])):
        why = (f["why"] or "").replace("|", "/").replace("\n", " ")[:160]
        md.append(f"| {f['model']} | {f['task']} | {f['case']} | {f['expected']} | {f['got']} | {why} |")

    report = log_dir / "report.md"
    report.write_text("\n".join(md) + "\n", encoding="utf-8")
    with (log_dir / "failures.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=["model", "task", "case", "lang", "expected", "got", "why", "output"])
        w.writeheader()
        w.writerows(failures)

    overview_end = next(i for i, line in enumerate(md) if line.startswith("\n*Device verdict acc*"))
    print("\n".join(md[: overview_end + 1]))
    print(f"\nFull report: {report}\nFailures:    {log_dir / 'failures.csv'}")
    return report


if __name__ == "__main__":
    if len(sys.argv) > 1:
        target = Path(sys.argv[1])
    else:
        runs = sorted((p for p in (ROOT / "logs").glob("*") if p.is_dir()), key=lambda p: p.stat().st_mtime)
        if not runs:
            sys.exit("No runs in logs/. Run evals/run.py first.")
        target = runs[-1]
    build_report(target)
