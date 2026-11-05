# Mock board

A stand-in for the board's local API (`docs/protocol.md`), for developing the app
without hardware. The Android emulator cannot use BLE or see the LAN, so point the
app's developer setting at the mock (`10.0.2.2`, port 8787, WebSocket 8788, token
`mock-token`).

```bash
uv run --group mock python mock/mock_board.py                     # scripted calls
uv run --group mock python mock/mock_board.py --replay mock/recordings/scam_call.jsonl
uv run --group mock python mock/smoke_test.py --call              # check it (or a real board)
```

Each guard-on plays one call: the `--replay` file, or the built-in scripts in turn
(scam, ordinary, family call with a security word).

## Recordings

Capture a real call from the board on USB, then replay it:

```bash
uv run atech monitor --json > mock/recordings/my_call.jsonl     # Ctrl+C when done
uv run --group mock python mock/mock_board.py --replay mock/recordings/my_call.jsonl
```

Replay keeps the original timing (gaps capped at 6 s) and gives the events new
`seq` numbers. Raw serial lines (`{"type":"event","payload":...}`) work too.
`scam_call.jsonl` is written by hand in the board's event format, until a real
capture replaces it.

## Test hooks (no token)

| Request | Effect |
|---|---|
| `POST /mock/offline?seconds=20` | Stop answering, to test the "board offline" warning |
| `POST /mock/scenario?name=scam` | Start a call now: `scam`, `benign`, `family`, `replay` |
| `POST /mock/low_memory?on=1` | Report degraded features, refuse WebSockets and most requests |
