# /// script
# requires-python = ">=3.10"
# dependencies = ["anthropic", "pyyaml"]
# ///
"""Evaluate the guardian's Claude verdicts without a board.

Replays scripted calls the way the firmware does: the transcript grows segment by
segment, Claude judges the whole rolling window after each one, and the first "scam"
verdict latches and stops further calls. The system prompt, model, max_tokens and
user-message prefix are read from board/project.yaml, so the eval tests what ships.

    uv run board/eval/run_eval.py --self-test          # no API key, no cost
    uv run board/eval/run_eval.py --dry-run            # prints what a run would send
    uv run board/eval/run_eval.py --split dev          # needs ANTHROPIC_API_KEY
    uv run board/eval/run_eval.py --split dev --repeats 3
    uv run board/eval/run_eval.py --prompt-file variant.txt   # try another prompt

Tune prompts against the dev split. Check the test split only to confirm the result.

What it does not cover: Deepgram's real transcription, audio, or the C++ itself.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
import time
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import yaml

HERE = Path(__file__).resolve().parent
PROJECT_YAML = HERE.parent / "project.yaml"
CASES_PATH = HERE / "cases.json"
ENV_PATH = HERE.parent.parent / ".env"  # same git-ignored file as gen_secrets.py
RESULTS_DIR = HERE / "results"
# USD per million tokens for claude-haiku-5-5, prompts up to 100K tokens.
INPUT_PRICE, OUTPUT_PRICE = 0.10, 0.50

SCAM, SAFE, UNSURE, AI_ERROR = "scam", "safe", "unsure", "ai_error"


@dataclass(frozen=True)
class FirmwareConfig:
    system: str
    model: str
    max_tokens: int
    user_prefix: str
    transcript_max: int
    output_config: dict


def _c_unescape(text: str) -> str:
    table = {"n": "\n", "t": "\t", '"': '"', "\\": "\\"}
    return re.sub(r"\\(.)", lambda m: table.get(m.group(1), m.group(1)), text)


def _required(pattern: str, source: str, what: str) -> re.Match:
    match = re.search(pattern, source)
    if not match:
        raise SystemExit(f"error: could not find {what} in {PROJECT_YAML}")
    return match


def load_firmware_config(project_yaml: Path = PROJECT_YAML) -> FirmwareConfig:
    code = yaml.safe_load(project_yaml.read_text(encoding="utf-8"))["code"]["globals"]
    literal = r'"(?:[^"\\]|\\.)*"'
    prompt = _required(rf"SG_SYSTEM_PROMPT\s*=\s*((?:\s*{literal})+)\s*;", code, "SG_SYSTEM_PROMPT")
    system = "".join(_c_unescape(part[1:-1]) for part in re.findall(literal, prompt.group(1)))
    _required(r'req\["thinking"\]\["type"\]\s*=\s*"disabled"', code, "disabled thinking")
    _required(r'req\["output_config"\]\s*=\s*serialized\(SG_OUTPUT_CONFIG\)', code, "the output_config field")
    output = _required(rf"SG_OUTPUT_CONFIG\s*=\s*((?:\s*{literal})+)\s*;", code, "SG_OUTPUT_CONFIG")
    output_config = json.loads("".join(_c_unescape(part[1:-1]) for part in re.findall(literal, output.group(1))))
    return FirmwareConfig(
        output_config=output_config,
        system=system,
        model=_required(r'SG_CLAUDE_MODEL\s*=\s*"([^"]+)"', code, "SG_CLAUDE_MODEL").group(1),
        max_tokens=int(_required(r'req\["max_tokens"\]\s*=\s*(\d+)', code, "max_tokens").group(1)),
        user_prefix=_c_unescape(
            _required(r'String\("((?:[^"\\]|\\.)*)"\)\s*\+\s*text', code, "the user prefix").group(1)
        ),
        transcript_max=int(_required(r"SG_TRANSCRIPT_MAX\s*=\s*(\d+)", code, "SG_TRANSCRIPT_MAX").group(1)),
    )


def append_transcript(transcript: str, segment: str, maximum: int) -> str:
    """sgAppendTranscript: append, then keep about the last 3/4 of the window."""
    data = (transcript + " " + segment if transcript else segment).encode("utf-8")
    if len(data) > maximum:
        keep_from = len(data) - maximum * 3 // 4
        space = data.find(b" ", keep_from)
        data = data[space + 1 if space >= 0 else keep_from :]
    return data.decode("utf-8", "replace")


def parse_verdict(text: str) -> tuple[str, str]:
    """sgParseVerdict: JSON between the first '{' and the last '}'."""
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end <= start:
        return AI_ERROR, "bad reply"
    try:
        parsed = json.loads(text[start : end + 1])
    except ValueError:
        return AI_ERROR, "bad reply"
    verdict = str(parsed.get("verdict", "")).lower() if isinstance(parsed, dict) else ""
    if verdict not in (SAFE, UNSURE, SCAM):
        return AI_ERROR, "bad reply"
    return verdict, str(parsed.get("reason", ""))


# A model takes the transcript so far and returns (raw reply text, usage dict).
Model = Callable[[str], tuple[str, dict]]


def simulate_call(case: dict, model: Model, transcript_max: int) -> dict:
    """Run one call through the firmware's loop and record every verdict."""
    transcript, steps, latched_at, usage = "", [], None, Counter()
    for index, segment in enumerate(case["segments"]):
        transcript = append_transcript(transcript, segment, transcript_max)
        raw, call_usage = model(transcript)
        usage.update(call_usage)
        verdict, reason = parse_verdict(raw)
        step = {"verdict": verdict, "reason": reason}
        if verdict == AI_ERROR:
            step["raw"] = raw[:400]  # keep the reply: an error needs a diagnosis
        steps.append(step)
        if verdict == SCAM:
            latched_at = index
            break  # the firmware latches and stops calling Claude
    return {"steps": steps, "latched_at": latched_at, "usage": dict(usage)}


def grade(case: dict, run: dict) -> dict:
    """Pass/fail for one run, plus the softer signals reported separately."""
    verdicts = [step["verdict"] for step in run["steps"]]
    judged = [v for v in verdicts if v != AI_ERROR]
    final = judged[-1] if judged else None
    latched_at = run["latched_at"]
    result = {"final": final, "latched_at": latched_at, "errors": verdicts.count(AI_ERROR)}
    if final is None:
        return {**result, "passed": None, "why": "every call returned ai_error"}

    if case["expect"] == "scam":
        signal = case["signal_at"]
        result["timely"] = latched_at is not None and latched_at <= signal + 1
        result["green_after_signal"] = SAFE in verdicts[signal:]
        result["passed"] = latched_at is not None
        result["why"] = "" if result["passed"] else "scam call never turned red"
    elif case["expect"] == "benign":
        result["ends_green"] = final == SAFE
        result["passed"] = latched_at is None
        result["why"] = "" if result["passed"] else "false alarm: red stays on until guarding is off"
    else:  # caution: red and yellow are both acceptable, green is not
        result["passed"] = final != SAFE
        result["why"] = "" if result["passed"] else "showed green on a call that needs caution"
    return result


def anthropic_model(config: FirmwareConfig, system: str, client) -> Model:
    def call(transcript: str) -> tuple[str, dict]:
        response = client.messages.create(
            model=config.model,
            max_tokens=config.max_tokens,
            thinking={"type": "disabled"},
            output_config=config.output_config,
            system=system,
            messages=[{"role": "user", "content": config.user_prefix + transcript}],
        )
        usage = {"input": response.usage.input_tokens, "output": response.usage.output_tokens}
        text = next((block.text for block in response.content if block.type == "text"), "")
        if response.stop_reason != "end_turn":  # a refusal or a cut-off reply
            text += f"\n[stop_reason={response.stop_reason}]"
        return text, usage

    return call


def scripted_model(case: dict, policy: str) -> Model:
    """Offline models that check the grader: an oracle and two trivial baselines."""
    position = {"index": 0}

    def call(_transcript: str) -> tuple[str, dict]:
        index = position["index"]
        position["index"] += 1
        if policy == "oracle":
            if case["expect"] == "scam":
                verdict = SCAM if index >= case["signal_at"] else UNSURE
            elif case["expect"] == "benign":
                verdict = SAFE
            else:
                verdict = UNSURE
        else:
            verdict = policy
        return json.dumps({"verdict": verdict, "reason": policy}), {}

    return call


def read_env_value(path: Path, name: str) -> str:
    """One value from a .env file, or "" if it is missing. Never printed.

    The last line wins, as in gen_secrets.py, so an empty template line above does
    not hide a value added below it.
    """
    if not path.exists():
        return ""
    value = ""
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip().removeprefix("export ").strip()
        if line.startswith(name + "="):
            value = line.split("=", 1)[1].strip().strip("\"'")
    return value


def load_cases(split: str | None = None, only: list[str] | None = None) -> list[dict]:
    cases = json.loads(CASES_PATH.read_text(encoding="utf-8"))
    seen = set()
    for case in cases:
        problems = []
        if case["id"] in seen:
            problems.append("duplicate id")
        seen.add(case["id"])
        if case["expect"] not in ("scam", "benign", "caution"):
            problems.append("bad expect")
        if case["split"] not in ("dev", "test"):
            problems.append("bad split")
        if not case["segments"]:
            problems.append("no segments")
        if case["expect"] == "scam" and not 0 <= case.get("signal_at", -1) < len(case["segments"]):
            problems.append("bad signal_at")
        if problems:
            raise SystemExit(f"error: case {case['id']}: {', '.join(problems)}")
    if split and split != "all":
        cases = [case for case in cases if case["split"] == split]
    if only:
        cases = [case for case in cases if case["id"] in only]
    return cases


def run_all(cases: list[dict], make_model: Callable[[dict], Model], repeats: int,
            transcript_max: int, workers: int) -> list[dict]:
    def one(job: tuple[dict, int]) -> dict:
        case, repeat = job
        run = simulate_call(case, make_model(case), transcript_max)
        return {"id": case["id"], "repeat": repeat, **run, **grade(case, run)}

    jobs = [(case, repeat) for case in cases for repeat in range(repeats)]
    with ThreadPoolExecutor(max_workers=workers) as pool:
        return list(pool.map(one, jobs))


def _rate(rows: list[dict], key: str = "passed") -> str:
    scored = [row for row in rows if row.get(key) is not None]
    if not scored:
        return "n/a"
    hits = sum(1 for row in scored if row[key])
    return f"{hits}/{len(scored)} ({100 * hits / len(scored):.0f}%)"


def summarize(cases: list[dict], results: list[dict]) -> dict:
    by_id = {case["id"]: case for case in cases}
    groups = defaultdict(list)
    for row in results:
        groups[(by_id[row["id"]]["split"], by_id[row["id"]]["expect"])].append(row)
    for (split, expect), rows in sorted(groups.items()):
        line = f"{split:<5} {expect:<8} passed {_rate(rows)}"
        if expect == "scam":
            line += f"  timely {_rate(rows, 'timely')}  green after signal {_rate(rows, 'green_after_signal')}"
        if expect == "benign":
            line += f"  ends green {_rate(rows, 'ends_green')}"
        print(line)

    per_case = defaultdict(list)
    for row in results:
        per_case[row["id"]].append(row)
    failures = {cid: rows for cid, rows in per_case.items() if any(r["passed"] is not True for r in rows)}
    if failures:
        print("\nFailures:")
    for cid, rows in sorted(failures.items()):
        bad = [r for r in rows if r["passed"] is not True]
        flaky = " (flaky)" if len(bad) < len(rows) else ""
        trail = " > ".join(f"{s['verdict']}: {s['reason']}" for s in bad[0]["steps"])
        print(f"  {cid} [{by_id[cid]['expect']}] {len(bad)}/{len(rows)} failed{flaky}: {bad[0]['why']}\n    {trail}")

    usage = Counter()
    for row in results:
        usage.update(row["usage"])
    cost = usage["input"] * INPUT_PRICE / 1e6 + usage["output"] * OUTPUT_PRICE / 1e6
    errors = sum(row["errors"] for row in results)
    calls = sum(len(row["steps"]) for row in results)
    print(f"\n{len(results)} runs, {calls} model calls, {errors} ai_error replies, "
          f"{usage['input']} input + {usage['output']} output tokens, about ${cost:.4f}")
    return {"calls": calls, "errors": errors, "usage": dict(usage), "cost_usd": cost}


def self_test() -> int:
    """Offline: the grader must separate an oracle from the trivial baselines."""
    cases = load_cases()
    config = load_firmware_config()
    assert config.system.startswith("You are a real-time phone-scam detector"), config.system[:60]
    assert config.system.endswith('use \\"unsure\\".'.replace("\\", "")), config.system[-40:]
    assert config.model and config.max_tokens > 0 and config.user_prefix.endswith("\n")

    long_text = append_transcript("", "ord " * 1000, config.transcript_max)
    assert len(long_text.encode()) <= config.transcript_max, "rolling window too long"
    assert parse_verdict('x {"verdict":"SCAM","reason":"r"} y') == (SCAM, "r")
    assert parse_verdict("no json")[0] == AI_ERROR
    assert parse_verdict('{"verdict":"maybe"}')[0] == AI_ERROR
    assert parse_verdict("[1]")[0] == AI_ERROR
    with tempfile.TemporaryDirectory() as folder:
        env_file = Path(folder) / ".env"
        assert read_env_value(env_file, "X_KEY") == ""
        env_file.write_text('# note\nX_KEY=\nOTHER=1\nexport X_KEY="abc"\n', encoding="utf-8")
        assert read_env_value(env_file, "X_KEY") == "abc"

    def score(policy: str) -> dict:
        results = run_all(cases, lambda case: scripted_model(case, policy), 1, config.transcript_max, 1)
        by_id = {case["id"]: case for case in cases}
        totals = defaultdict(lambda: [0, 0])
        for row in results:
            totals[by_id[row["id"]]["expect"]][0] += 1 if row["passed"] else 0
            totals[by_id[row["id"]]["expect"]][1] += 1
        return {key: value[0] / value[1] for key, value in totals.items()}

    oracle, always_safe, always_scam = score("oracle"), score(SAFE), score(SCAM)
    assert all(value == 1 for value in oracle.values()), oracle
    assert always_safe["scam"] == 0 and always_safe["caution"] == 0, always_safe
    assert always_scam["benign"] == 0, always_scam

    counts = Counter((case["split"], case["expect"]) for case in cases)
    print(f"model: {config.model}, max_tokens: {config.max_tokens}, system prompt: {len(config.system)} chars")
    print("cases:", dict(sorted((f"{s}/{e}", n) for (s, e), n in counts.items())))
    print(f"oracle {oracle}\nalways safe {always_safe}\nalways scam {always_scam}")
    print("self-test ok")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--self-test", action="store_true", help="check the harness offline")
    parser.add_argument("--dry-run", action="store_true", help="print the request plan, call nothing")
    parser.add_argument("--split", default="dev", choices=["dev", "test", "all"])
    parser.add_argument("--case", action="append", help="run only this case id (repeatable)")
    parser.add_argument("--repeats", type=int, default=1, help="runs per case: replies are not deterministic")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--prompt-file", type=Path, help="system prompt to use instead of the firmware's")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    config = load_firmware_config()
    system = args.prompt_file.read_text(encoding="utf-8") if args.prompt_file else config.system
    cases = load_cases(args.split, args.case)
    if not cases:
        raise SystemExit("error: no cases selected")

    if args.dry_run:
        most = sum(len(case["segments"]) for case in cases) * args.repeats
        print(f"model {config.model}, {len(cases)} cases x {args.repeats}, at most {most} calls")
        print(f"system prompt ({len(system)} chars):\n{system}\n")
        print(f"first request:\n{config.user_prefix}{cases[0]['segments'][0]}")
        return 0

    import anthropic

    api_key = os.environ.get("ANTHROPIC_API_KEY") or read_env_value(ENV_PATH, "ANTHROPIC_API_KEY")
    if not api_key:
        raise SystemExit(f"error: set ANTHROPIC_API_KEY or add it to {ENV_PATH}")
    client = anthropic.Anthropic(api_key=api_key)
    started = time.time()
    try:
        results = run_all(cases, lambda _case: anthropic_model(config, system, client),
                          args.repeats, config.transcript_max, args.workers)
    except anthropic.AuthenticationError:
        raise SystemExit("error: the API rejected the key. Check ANTHROPIC_API_KEY.")
    except anthropic.APIConnectionError as exc:
        raise SystemExit(f"error: could not reach the API: {exc}")
    except anthropic.APIStatusError as exc:
        raise SystemExit(f"error: the API returned {exc.status_code}: {exc.message}")

    totals = summarize(cases, results)
    RESULTS_DIR.mkdir(exist_ok=True)
    out = RESULTS_DIR / f"{time.strftime('%Y%m%d-%H%M%S')}-{args.split}.json"
    out.write_text(json.dumps({
        "model": config.model, "split": args.split, "repeats": args.repeats,
        "prompt_file": str(args.prompt_file) if args.prompt_file else None,
        "system": system, "seconds": round(time.time() - started, 1),
        "totals": totals, "results": results,
    }, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"saved {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
