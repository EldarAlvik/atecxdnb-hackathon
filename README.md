<img src="assets/icon.svg" width="64" height="64" alt="">

# atecxdnb hackathon

Created in [T3 Code](https://t3.codes).

## Setup

Requires [uv](https://docs.astral.sh/uv/).

```sh
uv sync          # creates .venv with Python 3.12 and installs deps
uv run main.py
```

Without uv:

```sh
python -m venv .venv
pip install -r requirements.txt   # or: pip install --pre atech
```

`atech` is only published as a pre-release (`1.0.0a12`), so plain `pip install atech` fails — use `--pre`.

## Call-scenario eval (Inspect AI)

`evals/call_scenarios.py` replays each scenario in `evals/scenarios/*.yaml` (format: `evals/scenarios/SCHEMA.md`) chunk by chunk, exactly like the board: cumulative transcript, last 2400 bytes, the board's system prompt, `max_tokens=120`, thinking off, a fresh conversation per call, and the first `scam` latches. Put `ANTHROPIC_API_KEY` in `.env` at the repo root.

```sh
# all scenario files (those starting with "_" are skipped)
uv run inspect eval evals/call_scenarios.py --model anthropic/claude-haiku-5-5
uv run inspect eval evals/call_scenarios.py --model anthropic/claude-sonnet-5-5

# harness check without an API key
uv run inspect eval evals/call_scenarios.py --model mockllm/model -T files=_smoke.yaml
```

`-T` options:
- `files=scam_calls.yaml,legit_*.yaml`: file names or globs in `evals/scenarios`
- `system_prompt_file=prompts/v2.txt`: use another prompt (for A/B tests)
- `lang=no`, `expected=scam,verify`, `category=bank_impersonation`: filters, comma-separated
- `epochs=3`: repeat each scenario
- `disable_thinking=false`

Scorers: `outcome` (C/P/I per SCHEMA, broken down by category and language), `detection_delay` (scam scenarios: chunks after `detect_by`), `false_alarm` (safe scenarios: any red), `format_ok` (JSON, verdict, reason of 8 words or fewer, reason language).

Browse per-chunk timelines (sample metadata `timeline` / store) with `uv run inspect view`, and compare models with `uv run python evals/report_calls.py`. The report reads the newest log per model in `logs/` and prints outcome, scam recall, false-alarm rate and mean detection delay, broken down by category, language and expected label.
