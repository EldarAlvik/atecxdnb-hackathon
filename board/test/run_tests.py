"""Build and run the host-side firmware tests, and check that the board and the
mock normalise family words the same way.

    uv run python board/test/run_tests.py

Needs g++ on PATH (MSYS2 mingw64 on Windows, any g++ elsewhere).
"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

here = Path(__file__).resolve().parent
sys.path.insert(0, str(here.parent.parent / "mock"))
from mock_board import normalise  # noqa: E402

SAMPLES = [
    "Blå Elefant!", "  BLÅ   elefant ", "ÆØÅ æøå", "Hei, det er meg -- Kari.",
    "Straße groß", "Crème brûlée", "naïve café", "x²+y² = z²", "tab\tseparated\twords",
    "émoji 😀 here", "100% sikker", "ÀÉÎÕÜ", "÷ × signs", "a-b_c.d", "",
]


def main() -> int:
    gxx = shutil.which("g++")
    if not gxx:
        print("g++ not found", file=sys.stderr)
        return 2
    with tempfile.TemporaryDirectory() as tmp:
        exe = Path(tmp) / "test_words.exe"
        subprocess.run([gxx, "-std=c++17", "-O1", "-Wall", "-Wno-unused-function", "-o", str(exe), str(here / "test_words.cpp")],
                       check=True)
        rc = subprocess.run([str(exe)]).returncode
        out = subprocess.run([str(exe), "--norm"], input="\n".join(SAMPLES) + "\n",
                             capture_output=True, encoding="utf-8").stdout.splitlines()
    bad = 0
    for text, board in zip(SAMPLES, out):
        mock = normalise(text)
        if board != mock:
            bad += 1
            print(f"FAIL normalise({text!r}): board={board!r} mock={mock!r}")
    if len(out) != len(SAMPLES):
        bad += 1
        print(f"FAIL board printed {len(out)} lines for {len(SAMPLES)} samples")
    print("PASS board and mock normalise the same" if not bad else f"{bad} normalise mismatches")
    return 1 if (rc or bad) else 0


if __name__ == "__main__":
    sys.exit(main())
