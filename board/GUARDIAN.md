# Scam Call Guardian

Runs entirely on the Atech ESP32-S3 board: the microphone streams the call audio
to Deepgram (live speech-to-text, Norwegian + English), and every ~6 s with new
text the board asks Claude Haiku (`claude-haiku-5-5`) whether the call looks like
a scam. The verdict lights one light grid in green, yellow or red and shows on
the screen.

## Hardware (14port board)

| Module | Port(s) |
|---|---|
| Microphone | 5, 6 |
| Button | 7 |
| Light grid (green safe, yellow unsure / AI error, red scam) | 10 |
| Screen 160x80 | 13, 14 |

Port 12 is the Reset button on this board and cannot hold a module. The light
grid must sit in port 10; to move it, change the `port:` of `led` in
`project.yaml`.

## Secrets

Put these in `.env` in the repo root (git-ignored):

```
ANTHROPIC_API_KEY=...
DEEPGRAM_API_KEY=...
WIFI_SSID=...           # optional fallback; the app sets the real network over BLE
WIFI_PASSWORD=          # empty = open network
```

Then generate the firmware header (re-run whenever `.env` changes):

```bash
uv run python board/gen_secrets.py
```

This writes `board/secrets.h` (git-ignored). `project.yaml` includes it as
`"../../secrets.h"`, relative to the generated `board/build/src/main.cpp`. The
keys end up in the firmware image on the board's flash: anyone holding the board
can read them, so use keys you can revoke.

## Build, flash, use

```bash
uv run python board/gen_secrets.py
uv run atech validate board
uv run atech upload board          # builds and flashes
uv run atech check
```

- **Button**: a short press toggles guarding (about 0.6 s after you let go).
  **Four quick presses**, or holding it **5 s**, restart the board into BLE setup
  mode. When idle, the screen says "Press button to guard",
  shows the IP, and the lights are off.
- **Guarding:** WiFi connects ("WiFi..."), then the Deepgram stream ("CONNECT"),
  then "LISTENING" with the light breathing blue. Each verdict sets its colour:
  green = safe, yellow = unsure, red = scam. **Scam stays red until guarding is
  turned off.** "AI err" (yellow) means the Claude call failed; it retries.
- Screen: verdict, `W+/W-` (WiFi) and `STT+/STT-` (Deepgram), Claude's reason,
  and the last words of the transcript (æøå shown as ae/o/a).

From the laptop (one program on the port at a time):

```bash
uv run atech send guard on         # or off / toggle
uv run atech send gain 8           # mic digital gain 1..64 (default 8)
uv run atech monitor --key verdict # or transcript, health, wifi, ws, guard, error
uv run python board/guardian_test.py   # guard on, speak test lines via Windows TTS, print events
```

## Phone app, setup and the local API (v2)

The full protocol is in [`docs/protocol.md`](../docs/protocol.md); the Android app
is in `android/`, a mock board in `mock/`.

- **Setup mode** starts when no Wi-Fi is saved, after four quick presses or a 5 s
  button hold, or after
  `POST /setup` / `atech send setup` (`atech send setup_cancel` leaves it). The screen shows `SETUP` and a 6-digit code;
  the blue breathe is faster. The app finds `Guardian-XXXX` over BLE, pairs with
  that code, sends the Wi-Fi name and password, and gets a device token back. The
  board saves the network in NVS and restarts without BLE. A short press cancels
  setup (the board then uses the saved or the `.env` network). BLE only ever runs
  in a setup boot: every other boot gives the BT memory back to the heap.
- **Local API** once Wi-Fi is up: `http://guardian.local/` (port 80) and
  `ws://guardian.local:81/?token=...`. Every request needs the token.
- **Family words**: salted SHA-256 hashes in NVS, matched on the board. A spoken
  word becomes `[family word]` in the transcript, Claude is told that the caller
  said a verified word for that member, and a `word_match` event goes out.
- **Settings** (`/config`, NVS): mic gain, AI interval, language, red latch, event
  keys sent to the app and the log, screen brightness.
- **Event log**: the last 400-500 events in LittleFS (`/events?since=`), kept across
  restarts. `health` is not logged.
- Code: `project.yaml` holds the call pipeline; `guardian/sg_*.h` the new parts.

Testing without and with the phone:

```bash
uv run python board/test/run_tests.py                # word matching on the PC (g++)
uv run --group mock python board/hw_test.py          # API smoke test + free heap, board on USB and Wi-Fi
uv run --group mock python board/hw_test.py --call   # ... plus a guarded call
uv run atech monitor --key health                    # memory: free_heap, max_block
```

`hw_test.py` gets a new token over USB (`api_token` action), which logs the phone
out: pair it again with a 5 s hold afterwards.

## Events (JSON lines over USB serial)

| key | value |
|---|---|
| `guard` | `"on"` / `"off"` |
| `wifi` | `{status, code, ip, rssi}` |
| `ws` | `connecting` / `connected` / `closing` / `closed` / `disconnected` |
| `transcript` | `{text, lang}` per final Deepgram segment |
| `verdict` | `{verdict, reason, latched, http, ms, chars}` (+ `detail` on errors) |
| `health` | `{free_heap, min_free_heap, max_block, audio_sent, audio_dropped, ai_calls, ai_ms, mic_level, ...}` every 5 s |
| `word_match`, `setup`, `config`, `words`, `degraded`, `startup` | see docs/protocol.md |
| `error`, `ws_msg`, `wifi_retry` | log lines |

Every event also has `seq` (increasing, kept across restarts) and `ts` (Unix
seconds once NTP has set the clock, else 0).

## Caveats

- TLS is **not verified** (`setInsecure()` for Deepgram and Anthropic). That's fine
  for a hackathon, not for production. Pin the CA certs before real use.
- API keys are compiled into the flash.
- The local API is plain HTTP on the home network: the token stops strangers from
  changing things, but anyone on the network can read the traffic, including a
  family word while it is being set. There is no RAM for a local TLS server next to
  the two TLS clients.
- Someone on the home network can still tie up the local API: the WebSocket library
  reads handshake headers without a length limit, and the HTTP server reads a
  declared body (in 1 KB chunks, without keeping it) for up to 5 s. The call
  pipeline runs on other tasks and keeps working.
- The Deepgram TLS handshake uses the WebSocket library's own timeouts; the Claude
  one is capped at 12 s.
- Latency: Deepgram finals arrive about 0.3-1 s after speech. Claude is called at
  most every 6 s, and a call takes about 1-3 s over TLS. So expect a verdict
  2-10 s after the telling sentence.
