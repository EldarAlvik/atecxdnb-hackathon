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

## Model evals (Inspect AI)

`evals/` tests how well the Claude model behind the Scam Call Guardian works on
real-style cases in Norwegian and English, and what Sonnet or Opus would add,
with accuracy, price, speed, limitations and every failed case.

It uses the `ANTHROPIC_API_KEY` from the same `.env` the board uses.

```sh
cp .env.example .env                                # same file as board/GUARDIAN.md
uv run python evals/run.py --limit 4 --models haiku # cheap smoke test
uv run python evals/run.py                          # every model in evals/models.yaml
uv run python evals/report.py                       # rebuild the report for the newest run
uv run inspect view                                 # browse every conversation
```

Tasks (all in `evals/scam_eval.py`):

| Task | What it checks |
|---|---|
| `scam_detection` | Sends each case exactly as the board does: its system prompt (read from `board/project.yaml` when present), `max_tokens: 120`, thinking off, and the same `safe` / `unsure` / `scam` parsing. 20 scenarios (Hei mamma, fake DNB/BankID call, fake police "safe account", voice clone, invoice fraud, harmless calls and more), each in Norwegian and English. |
| `safe_word` | In one test the owner tells the assistant the family safe word (the model must save it with a tool), then someone claiming to be family gets in touch. The model must verify the word, never leak it, and never let the caller change it. |
| `safe_word_isolated` | Same, but the call starts in a fresh conversation, so the word only survives through the tool's store. |

Models (`evals/models.yaml`): `claude-haiku-5-5` (what the board runs), plus `claude-sonnet-5-5` and `claude-opus-5-5` for comparison. The report (`logs/<run>/report.md`, `failures.csv`) shows per model: verdict accuracy overall and per language, missed scams, false alarms, safe-word pass rate, leaks, $ per 1000 checks, median and p95 seconds, and limitations (replies the board can't parse, wrong language, skipping the verify tool, API errors and refusals).

Add cases in `evals/data/*.yaml`; add models in `evals/models.yaml`.
