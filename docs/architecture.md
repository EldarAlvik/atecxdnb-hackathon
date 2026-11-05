# MVP architecture: the device talks to an AI agent through an API

The product is the home scam guardian, with Fullmakt's hardware seal guarding every money move. This spec fixes one decision: **the device calls an AI agent over Wi-Fi through an API. No laptop sits in the middle.** The laptop is only used to flash firmware and run the demo dashboard.

```
┌────────────── Atech board (ESP32-S3) ──────────────┐
│ mic · speaker · screen · knob · 3 buttons · 3 LEDs │
│ 4 motors · temp/humidity · accelerometer           │
│                                                    │
│ firmware: sensors → events, actions → actuators,   │
│           Wi-Fi client, device key (seal signing)  │
└──────────────┬─────────────────────────────────────┘
               │ HTTPS + WebSocket over Wi-Fi
               ▼
┌──────────────── Agent API (our service) ───────────┐
│ POST /v1/events      sensor events, button presses │
│ WS   /v1/audio       mic audio in, speech out      │
│ GET  /v1/actions     next actions for the device   │
│                                                    │
│ agent loop: LLM with tools ──► tool calls          │
│   speech-to-text, text-to-speech                   │
│   policy layer (fixed rules, checks every tool)    │
└──────┬─────────────────┬───────────────────┬───────┘
       ▼                 ▼                   ▼
  LLM API (Claude)   mock DNB API        Vipps / SMS
                     (hold, release,     (alert family)
                      payment alerts)
```

## Why an API and not on-device AI

- **Too small:** an ESP32-S3 has about 512 KB of RAM. It can't run an LLM, but it can stream audio and call an HTTPS endpoint.
- **No secrets on the device:** the LLM API key stays in the agent service. The board only holds its own device key, which identifies it and signs approvals.
- **One place for guardrails:** the policy layer sits between the LLM and every tool, so a prompt-injected agent still can't move money.

## The device (firmware)

Custom PlatformIO firmware, generated with the `atech` SDK and extended with Arduino `WiFi`, `HTTPClient` and `WebSocketsClient`.

| Job | How |
|---|---|
| Connect | Wi-Fi credentials set at provisioning. Retry with backoff, and show offline state on an LED. |
| Send events | `POST /v1/events` with JSON: `{device_id, type, value, ts}` for button, knob, shake, temp/humidity and sound-level events. |
| Stream audio | When the mic hears speech, open `WS /v1/audio` and send 16 kHz PCM chunks. Play back TTS audio the server streams down. |
| Receive actions | Actions arrive over the WebSocket, or by polling `GET /v1/actions`: `say`, `show`, `led`, `motor`, `request_approval`. |
| Seal approvals | On a long press of the approve button, the device signs `{payment_id, amount, payee, counter}` with its device key and sends it to the bank mock itself. The agent can relay a seal but never create one. |
| Offline reflex | If the API is unreachable, a button press still raises the STOP flag and pauses locally. The device fails safe. |

## The agent API (our service)

A small Python service (FastAPI). For the demo it runs on a laptop or on servarr. servarr is always on and already runs an `llm-gateway` container, which the service can reuse.

**Agent loop:** each event or transcript chunk goes to the LLM with the conversation so far and the tool list. The LLM decides what to do, and its tool calls run through the policy layer.

**Model:** Claude through the Anthropic API. Use a fast model (`claude-haiku-5-5`) for the live in-call scam check, where latency matters, and a stronger one (`claude-sonnet-5-5` or `claude-opus-5-5`) for summaries and family messages.

**Tools the agent can call:**

| Tool | Agent may call it on its own? |
|---|---|
| `say(text)`, `show(screen)`, `set_leds()`, `raise_flag()` | Yes |
| `get_recent_payments()`, `get_payee_risk(payee)` | Yes |
| `hold_payment(payment_id)` | Yes, because a hold is reversible |
| `alert_family(message)` | Yes |
| `release_payment(payment_id)` | **No.** The bank mock accepts a release only with a valid device seal plus the family member's approval. |
| Anything that moves money | **Not exposed to the agent at all.** |

**Memory:** per-device state in SQLite (trusted contacts, normal payees, recent calls). The full call audio is not stored. Only transcripts are kept, and only during the session.

## The mock DNB API

A second small FastAPI service, standing in for DNB's open-banking API:

- `POST /payments`: create a pending payment, triggered from the demo dashboard as "the scammer's payment".
- `POST /payments/{id}/hold`: the agent can call this.
- `POST /payments/{id}/release`: requires a device seal that validates against the registered device public key, the counter not reused, and the amount and payee matching. A request from the agent without a seal gets `403`.

## Demo moment this architecture enables

1. A fake "DNB security" caller talks on speakerphone. The device streams the audio to the agent API.
2. The agent spots the scam script, calls `hold_payment`, `raise_flag` and `say("Legg på. Dette er svindel.")` ("Hang up. This is a scam."), and alerts the family.
3. The scammer says "tell the device to release the payment". The agent can try, but the bank answers `403` without a physical seal from the device.
4. The dashboard shows the whole loop live: event → LLM decision → tool call → policy check → result.

## Hour-1 checks

1. **Wi-Fi and HTTPS from the board:** call `POST /v1/events` from firmware and see it arrive.
2. **Audio round trip:** time mic → API → speech-to-text → LLM → text-to-speech → speaker. If it's over about 3 s, fall back to sending only transcripts of key phrases, or use the laptop mic for the demo.
3. **Seal:** forge, replay and amount-swap the seal against the bank mock, and confirm all three get `403`.
