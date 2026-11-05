# Council pitch — Fable 5.1

*ATEC x DNB hackathon. Everything below was checked against the installed `atech` SDK (1.0.0a12): `atech list-modules`, `atech info <module>`, `atech guide`, `catalog/data/boards/14port.yaml`, `docs/protocol.md`.*

## What the kit is actually good at (and not)

Before the ideas, the constraints that shaped them:

- **Every sensor is non-blocking and cached** by a FreeRTOS task (IMU 100 Hz, ToF 15 Hz, mic RMS every 250 ms, CO2 every 5 s). The board can run real-time UI while the laptop does the thinking.
- **ToF is an 8x8 depth grid**, not a single beam: 64 zones to 4 m. That is a privacy-preserving "camera" (shapes, presence, an item lifted from a shelf) without any video.
- **IMU gives tilt, 6-way orientation, shake and raw axes.** "Flip it face down" is a free, unambiguous gesture.
- **Speaker plays tones/RTTTL on board, and the laptop can stream 16 kHz PCM** via `speaker_play_pcm` (base64 chunks, <=3000 chars each). Mic can record ~3 s to RAM and the speaker can play it back with `writeSamples`.
- **The protocol is JSON-lines over USB serial**, with custom events (`Serial.printf`) and custom actions (`atech_action` + `atechDone()`). WiFi/BLE compile but the SDK generates nothing for them.
- **Camera, load cell and soil sensor are sim-only products**: no firmware driver. Do not build a pitch on them.
- **Robot arm needs an external 12 V/5 V supply in the power jack**, torque goes on at boot, and there is a known UART line-swap gotcha. Great for spectacle, bad for a 48-hour critical path.
- **Board choice:** the 14-port board has 12 usable slots, two UART ports (3, 4), one isolated top-centre port (7, ideal for a room-facing sensor), and 2-port modules must sit on adjacent pairs `[1,2] [2,3] [3,4] [4,5] [5,6] [9,10] [10,11] [13,14]`.

---

## Idea 1 — **Vakt** · a physical cooling-off device against phone scams

**One line:** A small bank-issued object on the kitchen table that turns a flagged transfer into a physical ritual the scammer on the phone cannot see, coach, or click through.

**Problem and customer.** Impersonation fraud ("it's DNB security / the police, move your money to a safe account") hits older customers hardest, and it happens in the one channel the bank is blind to: a live phone call. Every digital control (SMS code, app confirm, BankID) is defeated because the victim is being coached in real time. Finans Norge has reported Norwegian fraud losses in the order of a billion NOK per year (verify the current figure before pitching). Customer: the bank, on behalf of its enrolled vulnerable customers and their families.

**How it works.** The bank's fraud engine flags a transfer (new payee, high amount, customer in a protected segment). Instead of an SMS, the transfer goes to *held* and the device wakes:

1. Ring and button pulse red. Big screen: "Vent litt." Speaker plays a message **recorded by a family member on the device itself**: "Mormor, det er Jonas. Hvis noen er på telefonen nå og ber deg om dette, legg på og ring meg."
2. Knob menu: *Who asked you to do this?* → Nobody / Family / The bank called / The police called / Someone else. "Bank" or "police" is an instant decline plus "DNB ringer aldri og ber deg flytte penger", and the trusted contact gets a notification.
3. "Nobody" path: dial the amount on the knob to within 10 %. A coached victim often does not know the amount they are sending.
4. Flip the device face down (IMU) for a cooling-off period. Flip back, long-press to release. Green.
5. The ToF grid logs how many bodies were in front of the device during the ritual (a "courier" scam variant), and the mic logs only sound level, never audio, so the bank can see the decision was made during an active conversation.

**Modules:** 14-port board · ST7789 320x240 · speaker · I2S mic (level only) · button (RGB) · rotary encoder with LED ring · IMU · VL53L5CX ToF. Laptop: mock core banking, fraud rules, trusted-contact push, decision log.

**Business model.** Bank pays. Hardware at a few hundred NOK per enrolled customer plus a per-customer monthly fee, against an average impersonation-fraud loss that is tens of thousands of NOK. Sold to Nordic banks first, then to telcos and insurers as the "physical channel" for high-risk approvals. Family plan upsell: the trusted contact's app.

**Why it could be big.** Regulation is moving liability onto banks: the UK mandated reimbursement of authorised push-payment fraud in October 2024, and the EU's Payment Services Regulation extends bank liability for bank-impersonation fraud. Once the bank pays for the loss, prevention hardware has a direct ROI and a motivated buyer. An ageing population makes the segment grow.

**DNB fit.** DNB has run national campaigns on scams against the elderly and bears the reimbursement decisions. This is the only idea in the set where the hardware *is* the security property: an out-of-band channel in physical space, powered by trust in a recorded voice rather than a code.

---

## Idea 2 — **Tillitshylla** · self-checkout and shrinkage insurance for unattended shops

**One line:** A depth-sensing shelf guard plus a payment screen that lets Norway's thousands of honesty-box farm stands, cabin kiosks and office fridges run like real shops, with Vipps payments and insurance against the people who don't pay.

**Problem and customer.** Unattended retail is everywhere in Norway (gårdsbutikk, strawberry stands, hyttekiosk, climbing-gym snack shelves) and runs on a cash tin and hope. Owners have no idea what their shrinkage is, cannot stock-manage, and cannot get insurance on it. Customer: small producers and micro-retailers; the channel is the bank's SMB and merchant-acquiring book.

**How it works.** The ToF 8x8 grid looks down a shelf; each zone's depth jump means an item was lifted. The display shows a dynamic Vipps QR with the price of what was just taken and a friendly running total. Pay and the grid turns green with a chime; walk away unpaid and nothing dramatic happens, but the event is logged with a timestamp and presence trace, and the shelf's **honesty rate** is shown on the display ("97 % av kundene betalte denne uka, takk!"). Social proof measurably raises payment rates. The microSD keeps a local ledger when offline; the laptop or a Pi reconciles against payments.

**Modules:** 14-port board · VL53L5CX ToF (port 7, facing the shelf) · ST7789 display · speaker · NeoPixel 3x3 as traffic light · button for "I paid cash" · microSD · AHT20 (cold-chain log for dairy and berries). Optional load cell when its driver ships.

**Business model.** Hardware lease per shelf, a small cut on each payment, and a parametric **shrinkage insurance** product: the bank's insurer pays out automatically when logged unpaid removals exceed a threshold, because the sensor data is the claim. Data product: footfall-to-payment conversion for every merchant on the platform.

**Why it could be big.** Unattended retail is growing worldwide (micro-markets, 24/7 convenience boxes), and the missing piece is not payments but *trust measurement*. The same box works in any office fridge or hotel minibar. Depth, not video, means it is GDPR-light and welcome in places a camera is not.

**DNB fit.** Payments (Vipps grew out of DNB), SMB banking, and a genuinely new insurance line built on hardware-attested data. Very Norwegian story for the stage.

---

## Idea 3 — **Takt** · pay-per-use financing for green equipment, metered by the machine itself

**One line:** A sensor puck that turns a heat pump, EV charger, industrial dishwasher or e-bike fleet into a usage-metered asset, so the bank can finance it on a pay-as-you-use plan and verify that it exists, runs and saves what was promised.

**Problem and customer.** Small businesses and housing cooperatives want green equipment but balk at the upfront cost; banks want to lend against it but cannot see the asset after the money leaves. Leasing today is a flat monthly fee divorced from use, and "green loan" claims are unverified. Customer: SMBs and borettslag on the borrowing side; the bank's lending and ESG reporting on the other.

**How it works.** The puck is strapped to the asset. The IMU's vibration signature says whether it is running (and whether it is being moved: collateral walking off), AHT20 reads supply/return air or room temperature deltas for a heat pump, the CO2 sensor doubles as an occupancy proxy. Run-hours and a derived kWh estimate are logged to microSD and streamed to the laptop, which turns them into a usage-based instalment and an ESG line item. The display shows the owner their live "this month" cost and CO2 saved. If instalments stop, the bank can degrade the service (the demo uses a DC motor as the machine) rather than repossess, the model that made pay-as-you-go solar work in East Africa.

**Modules:** 14-port board · IMU · AHT20 · SCD40 · microSD · ST7735 160x80 · button · DC motor (stand-in for the financed machine) · NeoPixel.

**Business model.** The bank earns lending margin on assets it could not previously finance, plus a per-asset telemetry fee; manufacturers and installers pay to be in the program because it closes sales. Verified usage data becomes the audit trail for green-bond reporting.

**Why it could be big.** Embedded, usage-based financing is how expensive durable goods will be sold to the long tail of SMBs. Whoever owns the trusted meter owns the credit relationship.

**DNB fit.** SMB lending, sustainability reporting with real data instead of self-declaration, and a defensible answer to "how do you know the green loan was green?"

---

## My pick: **Vakt**, and the 24-48 h MVP

Why this one: the demo is theatre with a villain, the hardware is the point rather than decoration, every module used has a stable SDK driver, and the business case has a regulatory tailwind a DNB judge will recognise immediately.

### Board and placement (14-port)

| Port(s) | Module | Instance | Role |
|---|---|---|---|
| 7 (top-centre, isolated) | `distance_sensor` | `room` | Faces the user; presence and body count during the ritual |
| 1 | `button` | `ok` | Long-press to release; RGB shows state |
| 2 | `icm40608` | `imu` | Face-down cooling-off gesture |
| 3, 4 | `microphone` | `mic` | Sound level only (conversation in progress?); records the family voice clip at enrolment |
| 5, 6 | `rotary_encoder` | `knob` | Menu and "dial the amount" |
| 9, 10 | `st7789_tft` | `screen` | Big screen, upright on the right column (rotation 3 is the default) |
| 13, 14 | `speaker` | `spk` | Voice clip, chimes |
| 8 | USB-C (reserved) | | Power and serial from the laptop |

Port 11 stays free. No microSD, so the display gets a hardware SPI host. Mic is on I2S_NUM_1 and speaker on I2S_NUM_0, so they coexist. Check it with `atech validate` and `atech view` before flashing.

```yaml
name: vakt
board: 14port
modules:
  - {id: st7789_tft,      instance: screen, ports: [9, 10]}
  - {id: speaker,         instance: spk,    ports: [13, 14]}
  - {id: microphone,      instance: mic,    ports: [3, 4]}
  - {id: rotary_encoder,  instance: knob,   ports: [5, 6]}
  - {id: button,          instance: ok,     port: 1}
  - {id: icm40608,        instance: imu,    port: 2}
  - {id: distance_sensor, instance: room,   port: 7}
feedback: false
```

### What runs where

**On the board (C++ in `code:`), a small state machine:** `IDLE → CHALLENGE → WHO_ASKED → DIAL_AMOUNT → COOLING → RELEASE → DONE/DECLINED`.

- Accepts one custom action `challenge` with JSON `{"amount":48000,"payee":"Ukjent konto","cool_s":60}`; replies with `atechDone()`.
- Emits custom events: `vakt_step` (string) on every transition, `vakt_decision` (`approved` / `declined_bank_call` / `declined_police_call` / `declined_timeout` / `escalated`), and `vakt_context` once per second during a challenge: `{"bodies":n,"sound":rms,"face_down":bool}` where `bodies` is a count of ToF zone clusters closer than ~1.5 m.
- Enrolment action `record_voice`: `mic.startRecording()` for 3 s, keep the buffer, play it back with `spk.writeSamples()` on every challenge. This keeps the voice fully on-device and avoids streaming over serial.
- Screen uses the shipped UI helpers (`drawTitleBar`, `drawList`, `drawProgressBar`, a FreeSansBold font) redrawn every 50 ms; knob ring colour follows state.
- Never blocks: timers via `millis()`, gestures via `ok.getGesture()` and `knob.getGesture()` called in exactly one place each.

**On the laptop (Python, `atech.Board`):**

- `bank.py`: a tiny FastAPI app that is the fake "DNB app": a payee list, a transfer form, and a fraud rule (new payee + amount above threshold + customer flagged as protected ⇒ `held`). On `held` it calls `board.call("challenge", {...})` and then consumes `board.events(keys=["vakt_step","vakt_decision","vakt_context"])`.
- `dashboard`: a single web page the judges see on the projector: transfer status, the live decision trail with timestamps, the context stream (bodies present, sound level, face-down), and a "trusted contact" phone mock that gets a push on decline/escalate.
- Fallback audio path: if on-device recording is flaky, pre-render the voice clip as WAV and stream it with `speaker_play_pcm` in ~2.2 KB base64 chunks, one `board.call` after the other.

### Demo script (3 minutes)

| Time | Beat |
|---|---|
| 0:00 | Hook: "Every digital control fails when the victim is being coached live on the phone. We moved the last step into the physical world." Device sits idle, ring breathing softly. |
| 0:25 | A judge is handed a script and plays the scammer: "This is DNB security, your account is compromised, move 48 000 kr to this safe account." The "victim" starts the transfer in the mock app. |
| 0:50 | Fraud rule holds the transfer. Device wakes: ring red, screen "Vent litt", speaker plays the grandchild's recorded voice. The room notices. |
| 1:15 | Knob menu "Hvem ba deg om dette?" The victim, still on the call, picks "Banken ringte". Instant decline; screen: "DNB ringer aldri og ber deg flytte penger." The trusted-contact phone on the projector buzzes with the decline and the context trail. |
| 1:45 | Reset. Legitimate case: the user initiates the same transfer, picks "Ingen", dials 48 000 on the knob, flips the device face-down. Screen shows the cooling-off bar (shortened to 10 s for the demo), knob ring counts down. Flip up, long-press, green chime, transfer released. |
| 2:25 | Dashboard: both decisions side by side, with hardware-attested context (one body present, sound level high during the first attempt). "This is a signed decision log the bank can stand behind." |
| 2:45 | Close: who pays and why now (bank liability shift, UK reimbursement rule, EU PSR), unit cost vs. average loss, go-to-market via DNB's existing protected-customer program. |

### Riskiest technical assumption

**That a 3 W I2S speaker driven by a 3-second, 16 kHz mono clip recorded on a MEMS mic will be intelligible to a room of judges.** The whole emotional payload depends on hearing a real family member's voice. Mitigations, in order: record the clip in a quiet room at enrolment and normalise the gain on the laptop before `speaker_play_pcm`; fall back to a studio-quality WAV pre-rendered on the laptop and streamed in chunks; if streaming throughput or the ack-per-chunk latency stutters, fall back to RTTTL chimes plus the message in large type on the 320x240 screen, which still carries the demo. Test speaker audio in the first two hours, before anything else.

Second-order risks: the ToF "bodies" count will be a heuristic (cluster zones under 1.5 m) and should be shown as context, never as a hard rule; and the fraud engine is a mock, which is fine, but say so, and show how a real bank would trigger the same `challenge` from its existing risk engine.

---

## Wild card — **Kortklipper'n**

The SO-101 robot arm as your personal budget enforcer. Link it to your spending categories. Three months over budget in "Uteliv"? The arm picks up your physical debit card from its cradle, holds it up to the webcam so your friends in the group chat can watch, and ceremonially snips it with a pair of scissors mounted on the gripper, while the speaker plays a funeral march in RTTTL and the 3x3 grid flashes red. DNB mails a new card the next morning, with the fee charged to the "Uteliv" category. Social accountability, physical consequence, a replacement-card revenue line, and the only hackathon demo where the judges flinch. (Needs the 12 V supply in the power jack, the `swap_lines: true` fix for V1.0 arm modules, and a legal team with a sense of humour.)
