# Scam Call Guardian: Android app

The companion app for the board (`board/`). It follows `docs/protocol.md` (version 2): BLE setup,
then the board's local HTTP API, WebSocket and event log over Wi-Fi.

Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, coroutines/Flow, OkHttp,
kotlinx.serialization. minSdk 26, targetSdk/compileSdk 36.

## Build

JDK 17 and the Android SDK (platform 36). Put `sdk.dir=...` in `android/local.properties`
(git-ignored), then:

```bash
cd android
./gradlew test assembleDebug          # unit tests + app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The library versions are the newest that still build with compileSdk 36 (Compose BOM 2026.06.01,
core 1.18, lifecycle 2.10, OkHttp 5.4). Newer releases need compileSdk 37.

## Run against the mock board (emulator)

The emulator has no Bluetooth and cannot see the LAN, so use the mock on the host:

```bash
uv run --group mock python mock/mock_board.py     # from the repo root
```

In the app's setup screen choose **Enter host and token by hand**. Debug builds prefill
`10.0.2.2`, `8787`, `8788`, `mock-token`. Other developer options on that screen (debug builds):

- **Simulate Bluetooth**: a fake board walks through the whole BLE setup (scan, pair, Wi-Fi
  scan, join, token, `done`) and hands over the mock at 10.0.2.2. Password `wrong` gives `auth`,
  network `Nowhere` gives `no_ssid`, password `slow` gives `timeout`.
- **Use a simulated board**: an in-process fake (`MockBoardClient`) that plays calls, no mock needed.

Mock test hooks: `POST /mock/scenario?name=scam|benign|family`, `POST /mock/offline?seconds=40`.

## How it fits together

| Package | What |
|---|---|
| `board` | Protocol models, event parsing, `BoardClient` (+ `HttpBoardClient`, `MockBoardClient`), config and word rules, `LocalDns` |
| `ble` | `BoardProvisioner` (+ `BleBoardProvisioner`, `FakeBoardProvisioner`): the BLE setup GATT client |
| `discovery` | NSD lookup of `_guardian._tcp` by TXT `id` |
| `data` | DataStore settings, `TokenCipher` (Keystore AES-GCM), Room event log, `EventSyncer` |
| `monitor` | `BoardMonitor` (WebSocket, /status polling, sync, offline detection, alerts), `MonitorService`, notifications |
| `ui` | Onboarding, dashboard, family words, events, settings |

- **Live updates.** `BoardMonitor` keeps the WebSocket open and reconnects with backoff (1 s up to
  15 s). It polls `GET /status` every 15 s, or every 5 s while the WebSocket is down. Any answer
  counts as contact. After 30 s without one the board is *offline*: a notification is shown, and
  NSD looks for the board again in case its IP changed.
- **Events.** Every logged event from the WebSocket goes into Room. `GET /events?since=<cursor>`
  (paged with `more`) runs on start and after every reconnect. The cursor only moves through
  `/events`, never through live events, so a reconnect cannot skip anything. `since < oldest_seq
  - 1` is stored as a gap and shown in the feed. A board whose `last_seq` drops below the cursor
  (log reset) starts a new epoch.
- **Background.** A phone call happens in the dialer, not in this app, so alerts must arrive while
  the app is in the background. A foreground service of type `connectedDevice` runs while a board
  is paired and the app is open, and keeps running in the background when **Keep watching in the
  background** is on (the default). WorkManager's 15-minute minimum interval is far too slow for
  this.

## Security and privacy

- **Device token**: encrypted with AES-256-GCM under a non-exportable Android Keystore key. Only
  the ciphertext is in DataStore. "Forget board" deletes the key. Backup and device transfer are off.
- **Family words**: typed into a password field (no suggestions, not saved in instance state, the
  dialog is `FLAG_SECURE`) and sent only to the board's `POST /words`. Never stored, cached or
  logged on the phone. There is no logging interceptor and no `Log` call with request bodies.
- **Cleartext HTTP**: the board has no TLS (protocol section 7). A network security config can only
  allow cleartext per host name, not per address range. So the app always requests the fixed
  name `guardian-board.local` (cleartext is allowed for `*.local` only), and `LocalDns` resolves
  that name to the board's address, refusing anything that is not private, link-local or loopback.
  Everything else stays HTTPS-only.
