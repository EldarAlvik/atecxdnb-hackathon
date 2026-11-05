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

`evals/` tests which models can run the Scam Call Guardian, on Norwegian and
English cases, and reports accuracy, price, speed, limitations and every
failed case.

```sh
uv run python evals/run.py --limit 2               # quick smoke test, every model
uv run python evals/run.py --name my-run           # full run (rerun same --name to resume)
uv run python evals/run.py --models luna Qwen --tasks call_scenarios
uv run inspect view                                # browse every conversation
```

### Models (`evals/models.yaml`)

| Model | Route | Needs |
|---|---|---|
| `anthropic/claude-haiku-5-5` (on the board), `anthropic/claude-sonnet-5-5` | Anthropic API | `ANTHROPIC_API_KEY` in `.env` |
| `codex/gpt-6.1-terra`, `codex/gpt-6-luna` | Codex SDK (`openai-codex`), your ChatGPT login | `codex login` |
| `openai-api/ntnu/deepseek-ai/DeepSeek-V4.1-Flash-1017-instant`, `openai-api/ntnu/Qwen/Qwen3.8-27B-FP8-instant` | NTNU's own LLM servers (data stays in NTNU) | the local tunnel on `127.0.0.1:8742`, or `NTNU_BASE_URL` / `NTNU_API_KEY` |

Providers are set up in `evals/providers.py` and `evals/codex_provider.py`. Codex can't run tasks that give the model tools, so it skips the safe-word tasks, and it adds ~18k tokens of its own to each request, so its speed and token counts read high. Models with a missing key are skipped.

### Tasks

| Task | What it checks |
|---|---|
| `scam_detection` (`scam_eval.py`) | 20 scenarios, each in Norwegian and English, sent exactly as the board sends them: its prompt, `max_tokens: 120`, thinking off, same `safe` / `unsure` / `scam` parsing. |
| `call_scenarios` (`call_scenarios.py`) | 96 calls replayed chunk by chunk like the board: the same 2400-byte window and a red light that stays on. Scam, real-but-scary, attack (prompt injection, window flooding, dialects) and raw-dictation sets in `evals/scenarios/`. Format in `SCHEMA.md`, open questions in `REVIEW.md`. |
| `safe_word` | In one test the owner gives the family safe word (the model must save it with a tool), then someone claiming to be family calls. The model must verify the word, never leak it, and never let the caller change it. |
| `safe_word_isolated` | Same, but the call starts in a fresh conversation, so the word only survives in the tool's store. |

Reports land in `logs/<run>/`: `report.md` and `failures.csv` for `scam_detection` and the safe-word tasks, and `report_calls.py` prints the call-scenario breakdown by model, category and language.
