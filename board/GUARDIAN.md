# Scam Call Guardian

Runs entirely on the Atech ESP32-S3 board: the microphone streams the call audio
to Deepgram (live speech-to-text, Norwegian + English), and every ~6 s with new
text the board asks Claude Haiku (`claude-haiku-5-5`) whether the call looks like
a scam. The verdict lights a traffic light and shows on the screen.

## Hardware (14port board)

| Module | Port(s) |
|---|---|
| Microphone | 5, 6 |
| Button | 7 |
| Light grid: green (safe) | 9 |
| Light grid: yellow (unsure / AI error) | 10 |
| Light grid: red (scam) | 11 (the slot just above the Reset button) |
| Screen 160x80 | 13, 14 |

Port 12 is the Reset button on this board and cannot hold a module. If a colour
lights the wrong grid, swap the `port:` numbers of `led_green` / `led_yellow` /
`led_red` in `project.yaml`.

## Secrets

Put these in `.env` in the repo root (git-ignored):

```
ANTHROPIC_API_KEY=...
DEEPGRAM_API_KEY=...
WIFI_SSID=...
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

- **Button** toggles guarding. When idle, the screen says "Press button to guard"
  and the lights are off.
- **Guarding:** WiFi connects ("WiFi..."), then the Deepgram stream ("CONNECT"),
  then "LISTENING" with a breathing blue yellow-grid. Each verdict lights one grid:
  green = safe, yellow = unsure, red = scam. **Scam stays red until guarding is
  turned off.** "AI err" (yellow) means the Claude call failed; it retries.
- **Green expires:** a safe verdict older than 20 s turns yellow ("No recent check")
  until the next verdict, so a pause in the call also shows yellow.
- **Auto-off:** guarding stops after 5 minutes without speech (30 minutes after a
  scam verdict) and always after 2 hours.
- Screen: verdict, `W+/W-` (WiFi) and `STT+/STT-` (Deepgram), Claude's reason,
  and the last words of the transcript (æøå shown as ae/o/a).

From the laptop (one program on the port at a time):

```bash
uv run atech send guard on         # or off / toggle
uv run atech send gain 8           # mic digital gain 1..64 (default 8)
uv run atech monitor --key verdict # or transcript, health, wifi, ws, guard, error
uv run python board/guardian_test.py   # guard on, speak test lines (macOS say or Windows TTS), print events
```

## Events (JSON lines over USB serial)

| key | value |
|---|---|
| `guard` | `"on"` / `"off"` |
| `wifi` | `{status, code, ip, rssi}` |
| `ws` | `connecting` / `connected` / `closing` / `closed` / `disconnected` |
| `transcript` | `{text, lang}` per final Deepgram segment |
| `verdict` | `{verdict, reason, latched, http, ms, chars}` (+ `detail` on errors) |
| `health` | `{free_heap, min_free_heap, max_block, audio_sent, audio_dropped, ai_calls, ai_ms, mic_level, ...}` every 5 s |
| `error`, `ws_msg`, `wifi_retry`, `auto_off` | log lines |

## Caveats

- TLS is **not verified** (`setInsecure()` for Deepgram and Anthropic). That's fine
  for a hackathon, not for production. Pin the CA certs before real use.
- API keys are compiled into the flash.
- Latency: Deepgram finals arrive about 0.3-1 s after speech. Claude is called at
  most every 6 s, and a call takes about 1-3 s over TLS. So expect a verdict
  2-10 s after the telling sentence.
