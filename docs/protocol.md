# Scam Call Guardian protocol

How the board (`board/project.yaml`), the Android app (`android/`) and the mock
board (`mock/`) talk to each other. Version **2**. The firmware reports it as
`proto` in `/status` and in the WebSocket `hello`.

There are three links:

| Link | Used for | Security |
|---|---|---|
| BLE GATT | First setup: Wi-Fi details in, device token out | LE Secure Connections, 6-digit passkey shown on the board's screen |
| HTTP, port 80 | Status, settings, family words, event history | `Authorization: Bearer <token>` |
| WebSocket, port 81 | Live events | `?token=<token>` |
| USB serial | Everything, for the laptop (`atech monitor`) | Physical access |

The board is found on the LAN as `guardian.local` and through DNS-SD as service
type `_guardian._tcp`.

---

## 1. BLE setup

### When the board advertises

- At boot, when no Wi-Fi network is saved in NVS.
- After the button is held for **5 s**. The board saves a flag and restarts into
  setup mode, so BLE always starts from a clean heap.
- After `POST /setup` (same as the long press).

Setup mode ends when the app sends `done`, 60 s after a successful pairing, after
**10 minutes** without success, or on a short button press (cancel). The board
then shuts BLE down: `NimBLEDevice::deinit(true)` followed by
`esp_bt_controller_mem_release()`, which hands the controller's RAM back to the
heap until the next restart. Guarding is not available in setup mode.

If setup mode ends without a new network and nothing is saved, the board uses the
Wi-Fi compiled in from `.env` (`SG_WIFI_SSID`), if there is one.

### GATT layout

Advertised name `Guardian-XXXX` (last two MAC bytes, hex). The advertisement
carries the service UUID.

| What | UUID | Properties |
|---|---|---|
| Service | `47a10001-8b3c-4d2e-9f1a-5c6d7e8f9a0b` | |
| `command` | `47a10002-8b3c-4d2e-9f1a-5c6d7e8f9a0b` | write (encrypted, authenticated) |
| `status` | `47a10003-8b3c-4d2e-9f1a-5c6d7e8f9a0b` | read, notify (encrypted, authenticated) |
| `networks` | `47a10004-8b3c-4d2e-9f1a-5c6d7e8f9a0b` | read (encrypted, authenticated) |

Every characteristic needs an encrypted, MITM-protected link. The first access
makes Android show its pairing dialog; the user types the 6-digit passkey the board
shows on its screen. The passkey is random for every setup session. The app should
request an MTU of 247 after connecting. All values are UTF-8 JSON, at most 512
bytes (long reads and writes are fine).

### Commands (write to `command`)

```json
{"op":"scan"}
{"op":"wifi","ssid":"Home","pass":"secret123"}
{"op":"pair"}
{"op":"done"}
```

| op | What the board does |
|---|---|
| `scan` | Scans for Wi-Fi networks (2-4 s). Then `status` = `scanned` and `networks` holds the list. |
| `wifi` | Tries to join `ssid` with `pass` (empty `pass` = open network) for up to 20 s. On success it saves both in NVS, makes a new device token and notifies `connected`. On failure nothing is saved. |
| `pair` | Like `wifi` but with the network already saved. Use it to pair a new phone without typing the password again. Fails with `no_saved_wifi` if none is saved. |
| `done` | The app has stored the token. The board stops BLE and starts the guardian. |

### `status` notifications

```json
{"state":"ready","device":"guardian-ab12","fw":"2.0.0","proto":2,"saved_ssid":"Home"}
{"state":"scanning"}
{"state":"scanned","count":7}
{"state":"connecting","ssid":"Home"}
{"state":"connected","ssid":"Home","ip":"192.168.0.57","host":"guardian.local","port":80,"ws_port":81,"device":"guardian-ab12","token":"<64 hex chars>"}
{"state":"failed","error":"auth"}
{"state":"bye"}
```

`error` is one of `no_ssid` (network not found), `auth` (wrong password),
`timeout`, `no_saved_wifi`, `bad_request`.

`saved_ssid` is empty when no network is saved. The password is never sent back.

`networks` (read after `scanned`), strongest first, at most 12:

```json
[{"ssid":"Home","rssi":-48,"secure":true},{"ssid":"Guest","rssi":-71,"secure":false}]
```

### The device token

- 32 random bytes from the hardware RNG, sent as 64 lowercase hex characters, once,
  in the `connected` notification.
- The board stores only `SHA-256(token)` in NVS, so the token cannot be read back.
- Every successful `wifi` or `pair` makes a new token and the old one stops working.
  One phone holds the token at a time.
- The app stores it encrypted with an Android Keystore key.

---

## 2. HTTP API (port 80)

Every request needs `Authorization: Bearer <token>`. Answers are JSON
(`application/json; charset=utf-8`).

| Status | When |
|---|---|
| `401` | Missing or wrong token: `{"error":"unauthorized"}` |
| `429` | 10 wrong tokens in a row: refused for 30 s |
| `400` | Bad JSON, a bad value, or a body over 1 KB: `{"error":"<what is wrong>"}` |
| `500` | The board could not save the change (nothing changed): `{"error":"could not save"}` |
| `404` | Unknown path or member |
| `503` | The board is low on memory and refused the request: `{"error":"low_memory"}` |

### `GET /status`

```json
{
  "device": "guardian-ab12", "fw": "2.0.0", "proto": 2,
  "uptime_s": 5120, "time": 1762352000,
  "guarding": true,
  "verdict": "unsure", "reason": "Too little context", "latched": false,
  "transcript": "...last ~300 characters of the call...",
  "word_match": null,
  "wifi": {"ssid": "Home", "rssi": -52, "ip": "192.168.0.57", "source": "saved"},
  "stt": "connected",
  "ai": {"calls": 12, "last_ms": 1430, "last_http": 200},
  "heap": {"free": 151230, "min": 98012, "max_block": 69620},
  "degraded": [],
  "last_seq": 1832,
  "members": 3
}
```

- `verdict`: `none` | `safe` | `unsure` | `scam` | `ai_error`.
- `time`: Unix seconds from NTP, `0` until the clock is set.
- `word_match`: `null` or `{"member_id":2,"member":"Mom"}` for the current call.
- `wifi.source`: `saved` (NVS) or `fallback` (compiled-in `.env`).
- `stt`: `connected` | `connecting` | `closed`.
- `degraded`: features the board switched off because memory ran low (see 6).

### `POST /guard`

Body `{"on":true}` or `{"on":false}`. Answer: `{"guarding":true}`. Same as the
button.

### `GET /events?since=<seq>&limit=<n>`

Events with `seq > since`, oldest first. `since` defaults to `0`, `limit` to 100
(max 200).

```json
{"events":[<event>, ...], "last_seq":1832, "oldest_seq":1333, "more":false}
```

`more:true` means more events are waiting after the last one returned; ask again
with `since` = the last `seq` you got. If `since < oldest_seq - 1`, events were lost
from the ring buffer. The app should note the gap.

### `GET /config`, `POST /config`

`GET` returns every setting. `POST` takes any subset, checks all of them, saves
them in NVS and returns the full config. If one value is bad, nothing is saved.

```json
{
  "mic_gain": 8,
  "ai_interval_s": 6,
  "language": "multi",
  "red_latch": true,
  "events": ["guard","wifi","ws","transcript","verdict","health","word_match","setup","config","words","degraded","error"],
  "screen_brightness": 100
}
```

| Key | Type | Range | Effect |
|---|---|---|---|
| `mic_gain` | int | 1-64 | Digital gain on the microphone audio |
| `ai_interval_s` | int | 3-60 | Minimum gap between Claude verdicts |
| `language` | string | `multi`, `no`, `en` | Deepgram language. `multi` = Norwegian and English. Takes effect on the next guard-on |
| `red_latch` | bool | | `true`: a scam verdict stays red until guarding stops |
| `events` | string[] | keys from section 4 | Which event keys go to the WebSocket and the event log. USB serial always gets every event |
| `screen_brightness` | int | 10-100 (%) | The screen's backlight is wired to always on, so this dims the drawn colours |

### `GET /words`

```json
{"members":[{"id":1,"name":"Mom","has_word":true,"updated":1762352000},
            {"id":2,"name":"Ola","has_word":false,"updated":0}],
 "max_members":8}
```

The words are never returned, logged or sent in an event.

### `POST /words`

| Body | Effect |
|---|---|
| `{"name":"Mom","word":"blue elephant"}` | Add a member, with or without `word` |
| `{"id":1,"name":"Mother"}` | Rename |
| `{"id":1,"word":"red fox"}` | Set or change the word |
| `{"id":1,"clear":true}` | Remove the word, keep the member |

Returns the member (`{"id":1,"name":"Mom","has_word":true,"updated":...}`).

Rules: name 1-24 bytes; word 1-4 words, 3-48 bytes after normalising. Normalising
means lower case (æøå too), dropping punctuation and collapsing spaces, so `Blå
Elefant!` and `blå elefant` are the same word. At most 8 members.

### `DELETE /words?id=<id>`

Removes the member and the word. Returns `{"deleted":1}`.

### `POST /setup`

Answers `{"setup":true}`, then restarts the board into BLE setup mode.

---

## 3. WebSocket (port 81)

`ws://guardian.local:81/?token=<token>`. The token goes in the query string: the
board does not read an `Authorization` header on the upgrade. A wrong token is
refused: either the upgrade fails with HTTP 401 (the mock), or the socket is closed
right after it opens (the board). Clients must handle both. The board
takes at most **2** clients; a third is closed.

The board's first message:

```json
{"type":"hello","device":"guardian-ab12","fw":"2.0.0","proto":2,"last_seq":1832}
```

Then every event as one text message, in the same JSON as on USB serial. The board
sends WebSocket pings every 15 s and drops a client that does not answer within 10 s.
Messages from the client are ignored. To catch up after a reconnect, call
`GET /events?since=<last seq you have>`.

---

## 4. Events

One shape everywhere (USB serial, WebSocket, `/events`):

```json
{"type":"event","payload":{"event_type":"state","key":"verdict","value":{...},"source":"guardian","seq":1832,"ts":1762352000}}
```

- `seq`: increases by one per event, kept across restarts (it continues from the
  event log). Events filtered out by `events` still use up a number, so gaps are
  normal.
- `ts`: Unix seconds, `0` while the clock is not set.
- `event_type`: `state` | `sensor` | `log`.

| key | event_type | value |
|---|---|---|
| `startup` | state | `{"fw":"2.0.0","reason":"POWERON","free_heap":181000,"mode":"normal"\|"setup","log_events":412}` |
| `guard` | state | `"on"` / `"off"` |
| `wifi` | state | `{"status":"connected"\|"down"\|"no_ssid"\|"failed","code":3,"ip":"...","rssi":-52,"source":"saved"\|"fallback"}` |
| `ws` | state | `"connecting"` / `"connected"` / `"closing"` / `"closed"` / `"disconnected"` (the Deepgram stream) |
| `transcript` | state | `{"text":"...","lang":"no"}`. A spoken family word appears as `[family word]` |
| `verdict` | state | `{"verdict":"safe"\|"unsure"\|"scam"\|"ai_error","reason":"...","latched":false,"http":200,"ms":1430,"chars":512,"word_match":false}` (+ `detail` on errors) |
| `word_match` | state | `{"member_id":1,"member":"Mom"}`. Once per member per call |
| `health` | sensor | `{"guarding":true,"free_heap":...,"min_free_heap":...,"max_block":...,"audio_sent":...,"audio_dropped":...,"ai_calls":...,"ai_ms":...,"mic_level":...,"ws_clients":1}` |
| `setup` | state | `{"state":"advertising"\|"paired"\|"connecting"\|"connected"\|"failed"\|"done"\|"timeout"\|"cancelled","error":"..."}`. Never contains the password or token |
| `config` | state | `{"changed":["mic_gain"]}` |
| `words` | state | `{"action":"add"\|"rename"\|"set"\|"clear"\|"remove","id":1,"name":"Mom"}` |
| `degraded` | state | `{"feature":"ws_server","reason":"low_heap","free_heap":31000}` |
| `error` | log | text |
| `ws_msg` | log | text (Deepgram messages) |
| `wifi_retry` | log | SSID being retried |

`health` is sent every 5 s while guarding and every 30 s otherwise. It goes to the
WebSocket but **not** to the event log, so it cannot push the call history out.

---

## 5. Discovery

- mDNS host name `guardian` (so `guardian.local`).
- DNS-SD service `_guardian._tcp` on port 80 with TXT records `id=guardian-ab12`,
  `ws=81`, `proto=2`, `fw=2.0.0`.
- The app resolves the service with Android NSD and keeps the board whose `id`
  matches the paired device. If it cannot be found, it tries the IP address
  from the `connected` notification. Two boards on one network both answer to
  `guardian.local`, so the app goes by `id`, not by host name.

---

## 6. Memory and degraded features

The ESP32-S3 has about 350 KB of free heap at boot, and the two TLS connections
(Deepgram and Anthropic) need most of it. The firmware checks the heap and gives up
features in this order rather than crash:

1. Below 40 KB free: new WebSocket clients are refused (`degraded` `ws_server`);
   below 24 KB the connected ones are dropped too.
2. Below 30 KB free: HTTP requests get `503 low_memory` (`degraded` `http`).
3. The event log skips writes while the largest free block is below 8 KB
   (`degraded` `event_log`).
4. A Claude call is skipped (an `error` event) when a new TLS session would not fit
   (under 45 KB free or no 20 KB block).

A feature comes back when memory recovers, and `degraded` in `/status` lists those
that are off right now.

---

## 7. Security notes

- The local HTTP and WebSocket traffic is not encrypted: anyone on the home
  network can read it, including words being set. The token protects against
  changes, not against snooping. The ESP32 does not have the RAM for a local TLS
  server alongside the two TLS client connections.
- Family words are stored as `SHA-256(salt ‖ normalised word)` with a random 16-byte
  salt per member. Short words can be guessed from a flash dump; pick a phrase.
- The spoken word itself goes to Deepgram as audio (unavoidable). The board
  replaces it with `[family word]` before it reaches the transcript, Claude, the
  events or the log. A phrase split over two Deepgram segments is not matched.
