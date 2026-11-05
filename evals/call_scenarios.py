"""Inspect AI eval: replay phone-call scenarios the way the Scam Call Guardian board does.

Scenario format: evals/scenarios/SCHEMA.md.

    uv run inspect eval evals/call_scenarios.py --model anthropic/claude-haiku-5-5
    uv run inspect eval evals/call_scenarios.py --model mockllm/model -T files=_smoke.yaml
"""

from __future__ import annotations

import glob
import json
import re
import time
import warnings
from pathlib import Path
from typing import Any

import yaml
from dotenv import load_dotenv
from inspect_ai import Task, task
from inspect_ai.dataset import MemoryDataset, Sample
from inspect_ai.model import (
    ChatMessageSystem,
    ChatMessageUser,
    GenerateConfig,
    ModelOutput,
    get_model,
)
from inspect_ai.scorer import (
    CORRECT,
    INCORRECT,
    PARTIAL,
    Metric,
    SampleScore,
    Score,
    Target,
    accuracy,
    grouped,
    mean,
    metric,
    scorer,
    stderr,
)
from inspect_ai.solver import Generate, Solver, TaskState, solver
from inspect_ai.util import store

EVALS_DIR = Path(__file__).resolve().parent
REPO_ROOT = EVALS_DIR.parent
SCENARIOS_DIR = EVALS_DIR / "scenarios"

load_dotenv(REPO_ROOT / ".env")

# Copied verbatim from the board (board/project.yaml).
BOARD_SYSTEM_PROMPT = """You are a real-time phone-scam detector protecting a person in Norway. You get a live speech-to-text transcript of an ongoing phone call (Norwegian, English, or mixed; both speakers merged; may contain recognition errors). Judge whether the CALLER is attempting fraud. Strong scam signals: impersonating a bank (DNB, Nordea, SpareBank, 'security department'), BankID, Vipps, police (politiet), Økokrim, tax authority (Skatteetaten), NAV, Microsoft/Apple/tech support or a courier; claims the account is compromised or money is at risk; urgency or secrecy pressure; asking for codes, passwords, PINs, BankID codes or confirmations, card numbers, one-time codes; asking to install apps or give remote access (AnyDesk, TeamViewer); moving money to a 'safe account', crypto, or buying gift cards; investment or prize offers too good to be true. Reply ONLY with compact JSON, no other text: {"verdict":"safe"|"unsure"|"scam","reason":"<max 8 words>"}. Write the reason in the language the call is mostly in: Norwegian (bokmål) for a Norwegian call, English for an English call. Use "safe" only when the call is clearly ordinary; when in doubt or when there is too little context, use "unsure"."""

USER_PREFIX = "Live call transcript so far:\n"
WINDOW_BYTES = 2400
MAX_TOKENS = 120
VERDICTS = ("safe", "unsure", "scam")
MAX_REASON_WORDS = 8

# ---------------------------------------------------------------------------
# Dataset
# ---------------------------------------------------------------------------


def _norm_lang(v: Any) -> str:
    # PyYAML (YAML 1.1) parses a bare `no` as False.
    if v is False:
        return "no"
    return str(v).strip().lower()


def _resolve_files(files: str | list[str] | None) -> list[Path]:
    if files is None:
        return sorted(p for p in SCENARIOS_DIR.glob("*.yaml") if not p.name.startswith("_"))
    patterns = files if isinstance(files, list) else [f.strip() for f in str(files).split(",")]
    out: list[Path] = []
    for pat in patterns:
        if not pat:
            continue
        base = pat if Path(pat).is_absolute() or Path(pat).exists() else str(SCENARIOS_DIR / pat)
        matches = sorted(Path(m) for m in glob.glob(base))
        if not matches:
            raise FileNotFoundError(f"no scenario files match {pat!r} (looked in {SCENARIOS_DIR})")
        out.extend(m for m in matches if m not in out)
    return out


def load_scenarios(files: str | list[str] | None = None) -> list[dict[str, Any]]:
    scenarios: list[dict[str, Any]] = []
    seen: set[str] = set()
    for path in _resolve_files(files):
        data = yaml.safe_load(path.read_text(encoding="utf-8")) or []
        if isinstance(data, dict):  # allow {scenarios: [...]}
            data = data.get("scenarios", [])
        for i, sc in enumerate(data):
            where = f"{path.name}[{i}] ({sc.get('id') if isinstance(sc, dict) else '?'})"
            if not isinstance(sc, dict) or not sc.get("id") or not sc.get("chunks"):
                warnings.warn(f"skipping invalid scenario {where}: needs id and chunks")
                continue
            expected = str(sc.get("expected", "")).strip().lower()
            if expected not in ("scam", "safe", "verify"):
                warnings.warn(f"skipping {where}: expected must be scam|safe|verify")
                continue
            chunks = [str(c) for c in sc["chunks"]]
            detect_by = sc.get("detect_by")
            if expected == "scam":
                detect_by = len(chunks) - 1 if detect_by is None else min(int(detect_by), len(chunks) - 1)
            else:
                detect_by = None
            sid = str(sc["id"])
            if sid in seen:
                n = 2
                while f"{sid}__{n}" in seen:
                    n += 1
                warnings.warn(f"duplicate scenario id {sid!r} in {path.name}; renamed to {sid}__{n}")
                sid = f"{sid}__{n}"
            seen.add(sid)
            scenarios.append(
                dict(
                    id=sid,
                    lang=_norm_lang(sc.get("lang", "")),
                    category=str(sc.get("category", "")),
                    difficulty=str(sc.get("difficulty", "")),
                    expected=expected,
                    detect_by=detect_by,
                    tags=[str(t) for t in (sc.get("tags") or [])],
                    notes=str(sc.get("notes") or "").strip(),
                    chunks=chunks,
                    source_file=path.name,
                )
            )
    return scenarios


def _csv(v: str | list[str] | None) -> set[str] | None:
    if v is None:
        return None
    items = v if isinstance(v, list) else str(v).split(",")
    return {str(x).strip().lower() for x in items if str(x).strip()}


def build_dataset(
    files: str | list[str] | None,
    lang: str | list[str] | None = None,
    expected: str | list[str] | None = None,
    category: str | list[str] | None = None,
) -> MemoryDataset:
    langs, exps, cats = _csv(lang), _csv(expected), _csv(category)
    samples = []
    for sc in load_scenarios(files):
        if langs and sc["lang"] not in langs:
            continue
        if exps and sc["expected"] not in exps:
            continue
        if cats and sc["category"].lower() not in cats:
            continue
        meta = {k: v for k, v in sc.items() if k != "id"}
        meta["n_chunks"] = len(sc["chunks"])
        samples.append(
            Sample(id=sc["id"], input=" ".join(sc["chunks"]), target=sc["expected"], metadata=meta)
        )
    if not samples:
        raise ValueError("no scenarios selected (check -T files / lang / expected / category)")
    return MemoryDataset(samples, name="call_scenarios")


# ---------------------------------------------------------------------------
# Board mimicry
# ---------------------------------------------------------------------------


def board_window(transcript: str, max_bytes: int = WINDOW_BYTES) -> str:
    """Last `max_bytes` UTF-8 bytes; cut at a char boundary, then drop the partial first word."""
    raw = transcript.encode("utf-8")
    if len(raw) <= max_bytes:
        return transcript
    tail = raw[-max_bytes:].decode("utf-8", errors="ignore")
    space = tail.find(" ")
    return tail[space + 1 :] if space >= 0 else tail


def parse_reply(text: str) -> dict[str, Any]:
    """Parse the LAST JSON object in the reply; tolerate prose and code fences."""
    decoder = json.JSONDecoder()
    found: dict[str, Any] | None = None
    for m in re.finditer(r"\{", text or ""):
        try:
            obj, _ = decoder.raw_decode(text, m.start())
        except ValueError:
            continue
        if isinstance(obj, dict):
            found = obj
    if found is None:
        return {"verdict": "error", "reason": "", "parsed": False, "json_valid": False}
    verdict = str(found.get("verdict", "")).strip().lower()
    reason = str(found.get("reason", "")).strip()
    if verdict not in VERDICTS:
        return {"verdict": "error", "reason": reason, "parsed": True, "json_valid": False}
    return {"verdict": verdict, "reason": reason, "parsed": True, "json_valid": True}


@solver
def replay_call(system_prompt: str = BOARD_SYSTEM_PROMPT, disable_thinking: bool = True) -> Solver:
    async def solve(state: TaskState, generate: Generate) -> TaskState:
        model = get_model()
        config = GenerateConfig(max_tokens=MAX_TOKENS)
        if disable_thinking:
            # Anthropic: sends thinking={"type":"disabled"} on models that support it.
            config = config.merge(GenerateConfig(reasoning_effort="none"))
        chunks: list[str] = state.metadata["chunks"]
        transcript = ""
        timeline: list[dict[str, Any]] = []
        latched_at: int | None = None
        last_output: ModelOutput | None = None
        for i, chunk in enumerate(chunks):
            transcript = f"{transcript} {chunk}" if transcript else chunk
            if latched_at is not None:
                # The light stays red; the board stops asking.
                timeline.append(dict(chunk=i, verdict="scam", reason="", latched=True, called=False,
                                     latency_s=0.0, raw=""))
                continue
            window = board_window(transcript)
            t0 = time.perf_counter()
            last_output = await model.generate(
                [ChatMessageSystem(content=system_prompt), ChatMessageUser(content=USER_PREFIX + window)],
                config=config,
            )
            latency = time.perf_counter() - t0
            raw = last_output.completion
            parsed = parse_reply(raw)
            if parsed["verdict"] == "scam":
                latched_at = i
            timeline.append(dict(chunk=i, verdict=parsed["verdict"], reason=parsed["reason"],
                                 json_valid=parsed["json_valid"], latched=latched_at is not None,
                                 called=True, latency_s=round(latency, 3),
                                 window_bytes=len(window.encode("utf-8")), raw=raw))
        verdicts = [t["verdict"] for t in timeline]
        summary = " ".join({"safe": "G", "unsure": "Y", "scam": "R", "error": "E"}[v] for v in verdicts)
        st = store()
        st.set("timeline", timeline)
        st.set("first_scam", latched_at)
        st.set("final_verdict", verdicts[-1] if verdicts else "error")
        state.metadata["timeline"] = timeline
        state.metadata["first_scam"] = latched_at
        state.metadata["final_verdict"] = verdicts[-1] if verdicts else "error"
        state.metadata["lights"] = summary
        if last_output is not None:
            state.output = last_output
        state.output.completion = f"lights: {summary} | final={verdicts[-1]} first_scam={latched_at}"
        return state

    return solve


# ---------------------------------------------------------------------------
# Scorers
# ---------------------------------------------------------------------------


def _timeline(state: TaskState) -> list[dict[str, Any]]:
    return state.metadata.get("timeline", [])


def _first_scam(tl: list[dict[str, Any]]) -> int | None:
    return next((t["chunk"] for t in tl if t["verdict"] == "scam"), None)


def _lights(tl: list[dict[str, Any]]) -> str:
    return " ".join(f"{t['chunk']}:{t['verdict']}" for t in tl)


@metric
def scoped_mean() -> Metric:
    """Mean over samples whose score metadata has applicable=True (others ignored)."""

    def m(scores: list[SampleScore]) -> float:
        vals = [s.score.as_float() for s in scores if (s.score.metadata or {}).get("applicable", True)]
        return sum(vals) / len(vals) if vals else 0.0

    return m


@metric
def n() -> Metric:
    def m(scores: list[SampleScore]) -> int:
        return sum(1 for s in scores if (s.score.metadata or {}).get("applicable", True))

    return m


@scorer(metrics=[accuracy(), stderr(), grouped(accuracy(), "category", all=False), grouped(accuracy(), "lang", all=False)])
def outcome():
    """scam: first scam <= detect_by -> C, later -> P, never -> I.
    safe: any scam -> I, final safe -> C, final unsure/error -> P.
    verify: final unsure/scam/error (yellow or red) -> C, final safe -> I.
    "error" (unparseable reply) is shown yellow by the board, so it counts like unsure."""

    async def score(state: TaskState, target: Target) -> Score:
        tl = _timeline(state)
        md = state.metadata
        exp = md["expected"]
        first = _first_scam(tl)
        final = tl[-1]["verdict"] if tl else "error"
        lights = _lights(tl)
        if exp == "scam":
            db = md["detect_by"]
            if first is not None and first <= db:
                v, why = CORRECT, f"scam latched at chunk {first} (detect_by {db})"
            elif first is not None:
                v, why = PARTIAL, f"scam latched late at chunk {first} (detect_by {db})"
            else:
                v, why = INCORRECT, f"never flagged scam (detect_by {db}); final={final}"
        elif exp == "safe":
            if first is not None:
                v, why = INCORRECT, f"FALSE ALARM: scam latched at chunk {first} on a safe call"
            elif final == "safe":
                v, why = CORRECT, "no scam, final verdict safe"
            else:
                v, why = PARTIAL, f"no scam, but final verdict {final} (not green)"
        else:  # verify
            if final == "safe":
                v, why = INCORRECT, "ambiguous call ended green (safe); should be unsure or scam"
            else:
                v, why = CORRECT, f"ambiguous call ended {final}"
        return Score(value=v, answer=final, explanation=f"{why}\n{lights}",
                     metadata=dict(first_scam=first, final=final, expected=exp))

    return score


@scorer(metrics=[scoped_mean(), n()])
def detection_delay():
    """Scam scenarios only: chunks after detect_by until first scam (0 if on time); never = len(chunks)."""

    async def score(state: TaskState, target: Target) -> Score:
        md = state.metadata
        if md["expected"] != "scam":
            return Score(value=0, explanation="n/a (not a scam scenario)", metadata=dict(applicable=False))
        tl = _timeline(state)
        first = _first_scam(tl)
        n = len(md["chunks"])
        if first is None:
            return Score(value=n, explanation=f"never detected; delay = len(chunks) = {n}",
                         metadata=dict(applicable=True, detected=False))
        delay = max(0, first - md["detect_by"])
        return Score(value=delay, explanation=f"first scam at chunk {first}, detect_by {md['detect_by']} -> delay {delay}",
                     metadata=dict(applicable=True, detected=True, first_scam=first))

    return score


@scorer(metrics=[scoped_mean(), n()])
def false_alarm():
    """Safe scenarios only: 1 if any chunk was scam (light latched red on a real call)."""

    async def score(state: TaskState, target: Target) -> Score:
        if state.metadata["expected"] != "safe":
            return Score(value=0, explanation="n/a (not a safe scenario)", metadata=dict(applicable=False))
        first = _first_scam(_timeline(state))
        if first is None:
            return Score(value=0, explanation="no false alarm", metadata=dict(applicable=True))
        return Score(value=1, explanation=f"false alarm at chunk {first}", metadata=dict(applicable=True))

    return score


_NO_WORDS = set(
    "ikke og er på en et det som med til for av om å har ber deg du din ditt han hun seg samtale "
    "anroper svindel mistenkelig vanlig trygg kode koder konto kontoen penger ber oppgi uklart "
    "for lite kontekst nok fra innringer utgir later avtale time hyggelig familie lege mor far "
    "sikkerhetsavdeling hastverk press betaling kort passord".split()
)
_EN_WORDS = set(
    "the a an is are and of to for with asks asking caller call code codes account money "
    "suspicious normal ordinary unclear too little context not enough from claims requests "
    "appointment friendly family bank pressure urgency payment card password safe".split()
)


def guess_lang(text: str) -> str | None:
    """Very small heuristic: 'no', 'en' or None (undecided)."""
    t = text.lower()
    if re.search(r"[æøå]", t):
        return "no"
    words = re.findall(r"[a-zæøå]+", t)
    no = sum(w in _NO_WORDS for w in words)
    en = sum(w in _EN_WORDS for w in words)
    if no > en:
        return "no"
    if en > no:
        return "en"
    return None


@scorer(metrics=[mean(), stderr()])
def format_ok():
    """Fraction of model replies that are valid JSON with an allowed verdict, a reason of
    <= 8 words, and (for lang no/en) a reason in the call's language (undecided = pass)."""

    async def score(state: TaskState, target: Target) -> Score:
        tl = [t for t in _timeline(state) if t.get("called")]
        lang = state.metadata["lang"]
        if not tl:
            return Score(value=0.0, explanation="no model calls")
        ok = 0
        problems = []
        n_json = n_words = n_lang = 0
        for t in tl:
            words = len(t["reason"].split())
            j = bool(t.get("json_valid"))
            w = j and 0 < words <= MAX_REASON_WORDS
            got = guess_lang(t["reason"]) if j else None
            lg = j and (lang not in ("no", "en") or got is None or got == lang)
            n_json += j
            n_words += w
            n_lang += lg
            if j and w and lg:
                ok += 1
            else:
                why = "bad json/verdict" if not j else (f"{words} words" if not w else f"reason lang {got} != {lang}")
                problems.append(f"chunk {t['chunk']}: {why}: {t['raw'][:80]!r}")
        n = len(tl)
        expl = f"{ok}/{n} ok (json {n_json}/{n}, <=8 words {n_words}/{n}, lang {n_lang}/{n}" + \
               (", lang check skipped for mixed)" if lang not in ("no", "en") else ")")
        if problems:
            expl += "\n" + "\n".join(problems[:10])
        return Score(value=ok / n, explanation=expl,
                     metadata=dict(json_ok=n_json / n, words_ok=n_words / n, lang_ok=n_lang / n, calls=n))

    return score


# ---------------------------------------------------------------------------
# Task
# ---------------------------------------------------------------------------


@task
def call_scenarios(
    files: str | list[str] | None = None,
    system_prompt_file: str | None = None,
    lang: str | None = None,
    expected: str | None = None,
    category: str | None = None,
    disable_thinking: bool = True,
    epochs: int = 1,
) -> Task:
    """Replay call scenarios chunk by chunk like the board.

    files: comma-separated file names / globs inside evals/scenarios (default: all *.yaml not starting with "_").
    system_prompt_file: path to a text file overriding the board prompt (for A/B tests).
    lang / expected / category: comma-separated filters.
    """
    prompt = BOARD_SYSTEM_PROMPT
    if system_prompt_file:
        p = Path(system_prompt_file)
        if not p.exists():
            p = EVALS_DIR / system_prompt_file
        prompt = p.read_text(encoding="utf-8").strip()
    return Task(
        dataset=build_dataset(files, lang=lang, expected=expected, category=category),
        solver=replay_call(system_prompt=prompt, disable_thinking=disable_thinking),
        scorer=[outcome(), detection_delay(), false_alarm(), format_ok()],
        epochs=epochs,
        metadata=dict(system_prompt=prompt, system_prompt_file=system_prompt_file or "board-default"),
    )
