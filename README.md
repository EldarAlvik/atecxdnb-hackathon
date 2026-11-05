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

## Working without the board (fake board)

`simboard.py` is a fake board that speaks the same JSON protocol as the real
firmware. Laptop code that uses `simboard.connect()` runs the same against either.

```sh
# terminal 1: the fake board, with a console (press button, emit <key> <value>, state, help)
uv run python -m simboard serve examples/blink

# terminal 2: your laptop code, pointed at the fake board
ATECH_SIM=1 uv run python examples/blink/host.py        # bash
$env:ATECH_SIM=1; uv run python examples/blink/host.py  # PowerShell
```

Leave `ATECH_SIM` unset and the same code talks to the real board on USB.

- The fake board accepts every action `atech describe` lists for the project. Custom
  actions from your `code:` block need `--accept name1,name2` (or `--accept '*'`),
  or a `sim.py` next to `project.yaml` that mimics the firmware in Python (see the
  docstring at the top of `simboard.py`).
- It does **not** run the C++ in `code:`. Check that part offline with
  `uv run atech build <dir>` (compiles; the first build downloads ~2.4 GB of
  toolchain) and `uv run atech view <dir>` (the board in 3D in the browser).
- In tests, `board, fw = simboard.local("examples/blink")` gives you both sides in
  one process. See `tests/test_simboard.py`; run them with `uv run pytest`.

## With the real board

```sh
uv run atech upload examples/blink   # build + flash
uv run python examples/blink/host.py
```

`uv run atech guide` is the full SDK guide (also good to hand to a coding agent).
