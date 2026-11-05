"""Laptop side of the blink example: every button press cycles the grid colour.

    uv run python examples/blink/host.py              # real board on USB
    ATECH_SIM=1 uv run python examples/blink/host.py  # fake board (python -m simboard serve examples/blink)
"""
import simboard

COLOURS = [
    {"r": 255, "g": 0, "b": 0},
    {"r": 0, "g": 255, "b": 0},
    {"r": 0, "g": 0, "b": 255},
]


def main() -> None:
    with simboard.connect() as board:
        board.call("grid_clear")
        print("connected; press the button", flush=True)
        presses = 0
        for event in board.events(keys=["button"]):
            if event.value == 1:
                colour = COLOURS[presses % len(COLOURS)]
                board.call("grid_fill", colour)
                presses += 1
                print(f"press {presses}: grid -> {colour}", flush=True)


if __name__ == "__main__":
    main()
