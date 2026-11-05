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
