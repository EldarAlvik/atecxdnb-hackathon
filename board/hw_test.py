"""Test the board's local API over Wi-Fi while watching its memory over USB.

    uv run --group mock python board/hw_test.py            # API smoke test + memory
    uv run --group mock python board/hw_test.py --call     # also a guarded call (speak to it)
    uv run --group mock python board/hw_test.py --idle 60  # only watch health for 60 s

Gets a fresh device token over USB (the `api_token` action; this logs the phone
out, pair it again afterwards), finds the board's IP from its `wifi` event or
/status, runs mock/smoke_test.py against it, and prints the lowest free heap and
largest free block the board reported meanwhile.
"""
import argparse
import subprocess
import sys
import threading
import time
from pathlib import Path

from atech import Board

ROOT = Path(__file__).resolve().parent.parent


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--call", action="store_true")
    ap.add_argument("--idle", type=int, default=0, help="only watch health events for N seconds")
    ap.add_argument("--host", help="board IP or name (default: from its wifi event)")
    args = ap.parse_args()

    lows = {"free_heap": None, "max_block": None, "min_free_heap": None}
    seen = []
    stop = threading.Event()

    with Board.connect() as board:
        def watch():
            for ev in board.events():
                if stop.is_set():
                    return
                if ev.key in ("health", "degraded", "error", "startup", "wifi", "verdict", "word_match"):
                    seen.append(ev)
                    print(f"  [usb] {ev.key}: {ev.value}", flush=True)
                if ev.key == "health" and isinstance(ev.value, dict):
                    for k in lows:
                        v = ev.value.get(k)
                        if v is not None and (lows[k] is None or v < lows[k]):
                            lows[k] = v

        threading.Thread(target=watch, daemon=True).start()
        if args.idle:
            time.sleep(args.idle)
        else:
            token = board.query("api_token", reply="api_token", timeout=5)
            host = args.host
            for _ in range(30):
                if host:
                    break
                wifi = board.latest("wifi")
                if wifi is not None and isinstance(wifi.value, dict) and wifi.value.get("ip"):
                    host = wifi.value["ip"]
                    break
                time.sleep(1)
            if not host:
                host = "guardian.local"
            print(f"board at {host}, running the smoke test", flush=True)
            cmd = [sys.executable, str(ROOT / "mock" / "smoke_test.py"), "--host", host,
                   "--port", "80", "--ws-port", "81", "--token", token]
            if args.call:
                cmd.append("--call")
            rc = subprocess.run(cmd).returncode
            time.sleep(6)                                # one more health event
        stop.set()
    print(f"lowest free_heap={lows['free_heap']} max_block={lows['max_block']} "
          f"min_free_heap(ever)={lows['min_free_heap']}")
    degraded = [e.value for e in seen if e.key == "degraded"]
    if degraded:
        print(f"degraded: {degraded}")
    return 0 if args.idle else rc


if __name__ == "__main__":
    sys.exit(main())
