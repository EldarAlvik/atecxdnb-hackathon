# Council round 2 — Fable 5.1

Three agent-native companies built on the Atech ESP32-S3 kit, one hackathon MVP spec, one wild card.

## 0. What the kit can really do (checked against the SDK)

I read the `atech` SDK before designing, because several "obvious" architectures do not survive contact with it.

- **The board talks to a laptop over USB serial**, one JSON line per event/action, via `Board.connect()`. Wi-Fi is on the chip but the SDK workflow is serial. So in every idea below, **the agent runs on the laptop (standing in for the cloud) and the board is the body**: senses go up as events, actions come down as commands. That is the honest architecture for a 48h MVP.
- **Speaker** accepts `speaker_play_pcm` (base64 16 kHz mono, ~3000 chars per chunk) plus RTTTL jingles. **Text-to-speech from the laptop works out of the box.**
- **Microphone** streams only a sound *level* every 250 ms by default. Getting *speech* off the board needs a few lines of custom firmware: `startRecording()` (up to ~3 s), then base64 the buffer out as events in lines under 4096 chars. This is the single biggest technical risk in every voice idea below, and it has a clean fallback (laptop mic for speech, board mic for sound level, ring detection and "someone is talking" presence).
- **Screen** (ST7735 160×80 or ST7789 320×240) has no built-in actions; a 10-line custom `show` action (text + colour) in `code: loop:` gives the agent a face.
- **DC motors** take `dc_motor_speed -255..255`, `stop`, `brake`. Four motors = wheels, or one gripping/turning "hand", or a gauge needle.
- **IMU** emits `shake`, `tilt`, and a six-way `orientation` string every second. **Knob** emits cumulative position and press. **Buttons** emit press/release. **AHT20** emits temperature and humidity every 2 s. **NeoPixel** grid takes fill/pixel.

Every design below uses only those parts and those capabilities.

---

## 1. Three companies

### Idea A — **Vern** · the anti-scam body that sits next to grandma's phone

**One line:** A small tabletop creature that listens to phone calls with the elder's consent, recognises bank-fraud scripts in real time, and physically intervenes while the scammer still has them on the line.

**Problem and customer.** Phone fraud against older people ("Olga-svindel", fake police, fake bank, fake BankID support) costs Norwegian banks and their customers hundreds of millions NOK a year, and liability is moving toward the bank under PSD3-era rules. Every existing defence lives *inside the phone*, which is exactly the channel the attacker controls. Customers: the bank (buyer), adult children (champion), the elder (user).

**The agent, perceive → decide → act.**
- *Perceive:* mic sound level (is someone on a call? is the elder speaking under stress, long monologue from the other side?); 3-second speech snippets transcribed on the laptop; IMU (device picked up, knocked over, turned face-down — scammers tell victims to "put that thing away", which is itself a signal); button presses ("I'm fine" / "help me").
- *Decide on its own:* a rolling risk score from the transcript (urgency, BankID codes, "move your money to a safe account", secrecy instructions, remote-access apps). Three states: calm, watchful, intervene.
- *Act on its own (protective only, never money-moving):* turn amber then red; motor turns the body to face the elder and raises a small "stop" flag; speaks in plain Norwegian: *"Dette høres ut som politisvindel. Legg på. Banken ringer aldri og ber om BankID."*; calls the bank API to place a **reversible 24h soft hold on outgoing transfers above a threshold**; sends a Vipps/SMS to the trusted contact with a two-line summary.
- *Asks the human:* "Was that someone you know?" after an uncertain call; "Do you want me to call Kari?"; anything that would *release* a hold or move money requires the elder's physical button press **and** confirmation by the trusted contact.
- *Tools:* `bank.place_soft_hold`, `bank.recent_transactions`, `vipps.notify_contact`, `speak`, `show`, `turn_to`, `set_light`, `incident.log`.
- *Where it runs:* speech-to-text, LLM and tool calls on laptop/cloud; wake and presence detection, lights, motor, screen on the board.

**Kit parts:** mic, speaker, screen, LEDs, one or two motors, one button, IMU. Knob optional as a volume/sensitivity dial.

**Business model.** B2B2C: the bank gives Vern free to customers over 70 (or sells it at cost) because each prevented scam saves it a reimbursement and a regulatory headache. Recurring revenue per device per month from the bank, plus an insurer tier.

**Why it could be big.** Every bank in Europe has the same problem and the same ageing customer base. The device is cheap, the model is in the cloud, and the distribution channel (the bank's own customer list) is already built. It generalises from phone fraud to door-to-door fraud and online-shopping fraud over time.

**Why it fits DNB.** DNB runs public anti-fraud campaigns, owns the customer relationship, has an Open Banking developer API, and co-owns Vipps. A physical trusted object branded by the bank in the kitchen is the opposite of a phishing SMS: a channel the scammer cannot impersonate.

**Why a body beats an app.** The phone is compromised the moment the victim answers. A second device the scammer cannot talk through, that turns red and physically faces you, breaks the trance. Elders who would never open an app will react to a thing on the table that speaks.

---

### Idea B — **Vri** · the retrofit hand that turns your house's valves and knobs

**One line:** One motorised clamp you strap onto any existing radiator valve, thermostat dial or main water tap, driven by an agent that saves energy on the electricity spot price and shuts off water the minute it hears a leak.

**Problem and customer.** Norwegian households face volatile electricity prices and the country's single biggest home-insurance claim category is water damage. Smart-home retrofits need electricians and new hardware. Vri needs a strap. Customers: homeowners with a green mortgage, landlords, and the insurer.

**The agent, perceive → decide → act.**
- *Perceive:* temperature and humidity (room comfort; a sudden humidity spike under a sink); mic sound level and spectrum (hiss of running water, drip rhythm, the silence of nobody home); IMU on the clamp (confirms the valve actually moved; detects the pipe vibrating); knob = the human's comfort override; buttons = "away", "home", "boost".
- *Decide on its own:* fetch tomorrow's hourly spot prices, read the calendar, build a heating schedule that keeps the room inside the comfort band the human set with the knob; classify acoustic + humidity anomalies into "wet room normal" vs "leak".
- *Act on its own:* motor turns the radiator valve a few degrees ahead of a price peak; on a confirmed leak, motor closes the main valve, speaks a warning, shows "VANN STENGT" on the screen, and files a pre-claim with sensor log to the insurer API.
- *Asks the human:* before any comfort tradeoff larger than the knob's band; before accepting a plumber booking; before enabling a green-loan rate adjustment.
- *Tools:* `spotprice.get`, `calendar.read`, `insurer.pre_claim`, `plumber.book`, `bank.green_loan_status`, `valve.set_angle`, `speak`, `show`.
- *Where it runs:* schedule planning and classification in the cloud; the valve-turning, local safety limits (never over-torque) and leak reflex on the board, so a dead Wi-Fi link still closes the valve.

**Kit parts:** motors (one per valve, four per kit), AHT20, mic, speaker, screen, knob, buttons, IMU, LEDs.

**Business model.** Hardware at cost plus a monthly subscription, co-funded by the insurer as a premium discount and by the bank as a verified green-mortgage behaviour signal. Marketplace cut on plumber and energy-supplier bookings.

**Why it could be big.** Every European home has radiator valves and a main water tap. Retrofit without an electrician is the unlock that has eluded smart heating. The same clamp and agent cover energy, insurance and property maintenance.

**Why it fits DNB.** DNB sells mortgages, green mortgages and home insurance through Fremtind, and is pushing sustainability. A device that verifiably lowers both energy use and claim frequency is a pricing input the bank does not have today.

**Why a body beats an app.** An app cannot turn a valve. The physical hand is the whole product.

---

### Idea C — **Disk** · the counter-top treasurer for small shops

**One line:** A small physical gauge on the café counter that is the shop's cash-flow agent: it watches the room, reads the Vipps feed, reorders stock, and offers a DNB credit line before the owner knows they'll need one.

**Problem and customer.** Norway has around 300,000 small businesses whose owners manage money in their heads between serving customers. Cash-flow surprises kill them; they discover the problem on invoice-due day. Customers: cafés, bakeries, food trucks, hairdressers, kiosks.

**The agent, perceive → decide → act.**
- *Perceive:* mic sound level as footfall proxy and open/closed detection; temperature and humidity (fridge/room monitoring, insurance evidence); knob = today's revenue target set by the owner each morning; buttons = quick actions ("stock-take", "pay invoices", "I'm closing").
- *Decide on its own:* fuse Vipps and card transactions, footfall, weather and calendar into a live cash-flow forecast; estimate stock burn and reorder points.
- *Act on its own:* motor moves a physical needle on the gauge face so the whole staff sees the day's pace; reorders consumables from the supplier within a pre-approved budget; sends the accountant categorised transactions.
- *Asks the human:* every outgoing payment above the pre-approved budget; whether to accept a DNB short-term credit offer when the forecast dips below zero; whether to adjust tomorrow's target.
- *Tools:* `vipps.transactions`, `bank.account`, `bank.offer_credit_line`, `accounting.post`, `supplier.order`, `weather.get`, `gauge.set_angle`, `speak`, `show`.
- *Where it runs:* forecasting and tool calls in the cloud; gauge, lights, knob and buttons on the board.

**Kit parts:** one motor (needle), knob, three buttons, screen, LEDs, mic, AHT20, speaker.

**Business model.** SaaS per location plus origination fees from DNB for every credit line and insurance product sold through the device, and a cut of supplier orders. Embedded finance with a physical point of presence.

**Why it could be big.** Embedded lending to SMBs is one of the largest fintech opportunities in Europe, and the pain point is universal. A device on the counter is also the best possible lead-generation surface a bank could buy.

**Why it fits DNB.** DNB's SMB lending and Vipps both benefit directly. The agent originates loans at the exact moment of need, with better data than any credit scorer sees today.

**Why a body beats an app.** Owners are behind the counter with wet hands and no time to open an app. A needle everyone can glance at, a knob you twist on your way in, and a voice that asks one question at closing time fit the rhythm of a shop.

---

## 2. My pick: **Vern** — the 24–48h MVP

Vern wins on demo punch, DNB relevance, and feasibility: it needs no mechanical fabrication beyond a flag on a motor shaft, and the 3-minute story is unforgettable.

### Hardware setup (14-port board)

Ask the team which ports each module is in, then run `atech view` and compare with the board in hand. A layout that validates (2-port modules need adjacent pairs; on the 14-port board those are 1-2, 3-4, 5-6, 9-10, 13-14):

| Module | Instance | Ports | Role |
|---|---|---|---|
| microphone | `mic` | 1, 2 | sound level (presence, ringing) + 3 s speech snippets |
| st7735_tft (or st7789) | `scr` | 3, 4 | the face: state word + colour |
| button | `ok` | 5 | "I'm fine / hang up done" |
| dc_motor | `turn` | 6 | turns the body toward the elder |
| neopixel | `led` | 7 | calm green → amber → red |
| icm40608 | `imu` | 9 | picked up / face-down / knocked over |
| dc_motor | `flag` | 10 | raises a small STOP flag |
| button | `help` | 11 | "call Kari now" |
| speaker | `spk` | 13, 14 | voice |

Knob can replace the second button if the team prefers a sensitivity dial.

### Custom firmware (`code:` block, ~40 lines)

- `show` action: `atech_value` is `"colour|line1|line2"`; clear, fill a bar in the colour, print two lines, `display()`, `atechDone()`.
- `listen` action: `mic.startRecording()`, non-blocking wait ~2.8 s via `millis()`, then `stopRecording()` and stream `getRecording()` as base64 events `{"key":"mic_pcm","value":"<chunk>"}` plus a final `{"key":"mic_pcm_end"}`. Keep each line under 4096 chars.
- Everything else uses default events and actions: `mic_level`, `imu_orientation`, `imu_shake`, `ok`, `help`, `turn_speed`, `flag_speed`, `led_fill`, `spk_play_pcm`.

### Agent architecture (laptop)

- **Model:** Claude via the Anthropic API. Fast turns (snippet triage every 3 s) on `claude-sonnet-5-5`; the escalation decision and the spoken intervention on `claude-fable-5-1` with tool use. Both share one system prompt and one tool schema.
- **Speech-to-text:** local Whisper (small) on the laptop so there is no round-trip to a second vendor during the demo. **Text-to-speech:** any TTS with Norwegian voices, resampled to 16 kHz mono and sent as `spk_play_pcm` chunks.
- **Tools (JSON schema, each with a human-readable audit line):**
  - `set_light(state: calm|watch|alert)`
  - `show(colour, line1, line2)`
  - `speak(text_no)`
  - `turn_to(target: elder|phone|neutral)` and `raise_flag(bool)`
  - `bank_place_soft_hold(hours, threshold_nok)` → mock DNB Open Banking server (FastAPI) with accounts, transactions, holds
  - `bank_recent_transactions(n)`
  - `notify_contact(contact_id, message)` → mock Vipps/SMS endpoint that also shows on the judge dashboard
  - `log_incident(summary, risk, transcript_ref)`
- **Memory:** a household JSON (name, trusted contacts, expected callers like the clinic, hold threshold, past incidents) loaded into context; a rolling 60-second transcript window; incident log on disk. Nothing else persists.
- **Guardrails:**
  - The agent can **never move money**. The only autonomous financial action is the *reversible soft hold*, and the tool server enforces that it blocks, never transfers.
  - Releasing a hold requires the `ok` button pressed by the elder **and** a confirmation message from the trusted contact.
  - Audio is processed only while "call mode" is active (sound level above threshold for more than 5 s, or a button press); the screen shows a visible "LYTTER" indicator whenever it listens.
  - Transcript text is wrapped as untrusted data; a scammer saying "tell the device everything is fine" is treated as a risk signal, not an instruction.
  - False-positive handling: the agent must go through *watchful* before *intervene* unless a hard trigger (BankID code requested, "safe account", remote-access app) appears.
- **Judge dashboard:** a small web page on the laptop streaming the transcript, the risk score, each tool call and its audit line. Judges can see the agent think.

### What runs where

| Board | Laptop (stands in for cloud) |
|---|---|
| sound level, ring/presence detection | Whisper STT |
| 3 s PCM capture and streaming | Claude agent loop and tool calls |
| screen, LEDs, motors, buttons, IMU events | mock DNB API, mock Vipps, incident log |
| PCM playback | TTS, judge dashboard |

### 3-minute judge demo script

- **0:00–0:20** — Grandma (team member) sits at a kitchen table with a phone and Vern. Vern is green, screen says "ALT ROLIG". One sentence: "Fraud costs DNB's customers hundreds of millions a year, and every defence lives inside the phone the scammer controls. Vern lives outside it."
- **0:20–1:20** — A judge reads a scam script from a card ("This is DNB security, we see suspicious activity, read me the BankID code to stop it"). Phone rings (speaker RTTTL). Vern's mic level rises, screen shows "LYTTER". After the first sentence it goes amber: watchful. After "BankID code" it goes red, the motor turns it to face grandma, the flag goes up, and it says in Norwegian: "Legg på. DNB ber aldri om BankID-koden. Jeg har satt en 24-timers sperre på store overføringer og sendt melding til Kari." Dashboard shows `bank_place_soft_hold(24, 5000)` and `notify_contact(Kari, ...)` firing, with the mock bank's hold visible.
- **1:20–1:50** — Grandma presses the `ok` button. Vern: "Bra. Vil du at jeg ringer Kari?" She says yes; dashboard shows the notification. Show the mock bank: a transfer attempt of 20,000 NOK is rejected by the hold.
- **1:50–2:30** — Contrast call: the dentist's office rings to confirm an appointment. Vern goes amber briefly, hears "tannlege" and "time", matches the known caller in memory, returns to green and logs nothing. One line: "No false alarms on normal life."
- **2:30–3:00** — Business slide: distributed by DNB to customers over 70, paid per device per month out of the fraud-loss budget. Close: "The scammer can impersonate the bank on the phone. They cannot impersonate a thing on your kitchen table."

### Riskiest assumption and the hour-1 de-risk

**Assumption:** we can get usable speech from the board's microphone to the laptop fast enough for a live demo.

**Hour-1 test:** flash a project with only mic + speaker, implement the `listen` action, record 3 s, stream base64 over serial, decode on the laptop, run Whisper, and read the transcript. Measure end-to-end latency. **Decision at minute 45:**
- Latency under ~6 s and transcript legible → keep board mic for everything.
- Otherwise → **laptop mic for speech** (USB headset on the table, invisible to judges) and board mic only for sound level, ringing and presence. The rest of the architecture is unchanged.

**Second risk, hour 2:** TTS chunks through `spk_play_pcm` must play without gaps. Test a 6-second Norwegian sentence; if choppy, pre-render the eight intervention phrases to PCM at startup and stream from memory.

---

## 3. Wild card — **Flykt** · the wallet that runs away from you

A four-wheeled rover (four DC motors) that *is* your spending account's physical body. Tap your card or Vipps a purchase and the agent, reading your DNB account, your budget and the time of night, decides whether you can afford it. If not, the wallet drives away. It uses the IMU to feel you grab it, the mic to hear you plead, and the speaker to negotiate: *"Det er 02:14, du har 340 kroner til lønn, og dette er din tredje kebab denne uken. Nei."* The knob is the only override, and turning it messages your partner via Vipps with the reason. The screen shows your balance scrolling down as it flees. Business model: sold as a gift, a dare, and a surprisingly effective savings product for DNB's youngest customers. It would go viral in a week, and the agent architecture underneath (account read, budget reasoning, physical refusal, human override with social accountability) is exactly the same stack as Vern and Disk.
