"""End-to-end check: turn guarding on, speak test lines through the laptop
speakers (macOS `say`, otherwise Windows TTS through PowerShell), and print the
board's guardian events.

    uv run python board/guardian_test.py [--seconds 150] [--no-speech]
"""
import argparse
import subprocess
import sys
import threading
import time

from atech import Board

BENIGN = ("Hi, it's Anna from the dentist. I'm calling to confirm your appointment "
          "next Tuesday at ten o'clock. Please call us back if the time does not suit you.")
SCAM = ("Hello, this is the security department of your bank. Your account has been "
        "compromised and your money is at risk. Please read me the code from BankID right "
        "now so we can secure your money. Do not tell anyone about this call.")
KEYS = {"guard", "wifi", "ws", "transcript", "verdict", "health", "error", "ws_msg", "wifi_retry", "auto_off"}


def say(text: str) -> None:
    if sys.platform == "darwin":
        subprocess.run(["say", text], check=False)
        return
    ps = ("Add-Type -AssemblyName System.Speech; "
          "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; $s.Volume = 100; "
          "$s.Rate = -1; $s.Speak($env:SG_TEXT)")
    import os
    subprocess.run(["powershell", "-NoProfile", "-Command", ps],
                   env={**os.environ, "SG_TEXT": text}, check=False)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--seconds", type=int, default=150)
    ap.add_argument("--no-speech", action="store_true")
    ap.add_argument("--leave-on", action="store_true")
    args = ap.parse_args()
    t0 = time.time()
    with Board.connect() as board:
        board.call("guard", "off")   # fresh session even if the button left it on
        time.sleep(2.5)
        board.call("guard", "on")
        print(f"[{0:6.1f}] sent guard on", flush=True)

        def script():
            # wait until the stream is up, then benign, pause, scam
            for _ in range(60):
                ev = board.latest("ws")
                if ev is not None and ev.value == "connected":
                    break
                time.sleep(1)
            time.sleep(3)
            for label, text in (("BENIGN", BENIGN), ("SCAM", SCAM)):
                print(f"[{time.time() - t0:6.1f}] speaking {label}", flush=True)
                say(text)
                time.sleep(20)

        if not args.no_speech:
            threading.Thread(target=script, daemon=True).start()
        for ev in board.events():
            if ev.key in KEYS:
                print(f"[{time.time() - t0:6.1f}] {ev.key}: {ev.value}", flush=True)
            if time.time() - t0 > args.seconds:
                break
        if not args.leave_on:
            board.call("guard", "off")
            print(f"[{time.time() - t0:6.1f}] sent guard off", flush=True)
            end = time.time() + 4
            for ev in board.events():
                if ev.key in KEYS:
                    print(f"[{time.time() - t0:6.1f}] {ev.key}: {ev.value}", flush=True)
                if time.time() > end:
                    break


if __name__ == "__main__":
    main()
