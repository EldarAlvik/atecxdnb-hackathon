# Council pitch — Opus 5.5

> **The thesis.** Banking went fully digital, and two things got lost along the way: **a place to check things that the attacker can't see**, and **money you can feel**. The Atech kit is cheap, modular hardware with a screen, a knob, a mic, a speaker and some sensors. I use it to bring those two things back.

---

## 0. What the kit can actually do (from the SDK, not the brochure)

I read `atech list-modules`, `atech info <module>`, `atech guide`, `docs/*.md` and `catalog/data/`. These facts shape every idea below:

| Fact | Why it matters |
|---|---|
| **Camera, load cell and soil sensor are sim-only.** `catalog/data/products/` has only `sim.yaml` for them, and the load cell is "coming soon". `project.yaml` cannot place them. | **Don't build an MVP on them.** Using one means writing a custom module driver (`modules_path:`). That is a hackathon-killer. |
| **One VL53L5CX ToF per project** (it uses the chip's only hardware `Wire`). It gives an 8x8 grid at 15 Hz and reads up to ~4 m. | Privacy-safe "presence and how many bodies" sensing, with no camera. |
| **The microphone gives raw 16 kHz samples** (`getSamples`, an `onAudio` callback, up to ~3 s recording) plus RMS and peak. | You can do on-device voice-activity detection, so **no audio has to leave the device**. |
| **The speaker accepts `speaker_play_pcm`** (base64, 16 kHz mono, about 3000 chars per chunk). It also plays RTTTL. | The laptop can generate speech (TTS) and stream it to the device, so the device can *talk*. |
| **The IMU** runs at 100 Hz and has `wasShaken()`, tilt and a 6-way orientation. | Gestures like shake, flip and pick-up. |
| **Your own events and actions** work by printing one JSON line, or by checking `atech_action == "x"` and then calling `atechDone()`. Lines are ≤ 4096 chars. | The laptop acts as the "bank backend", and the board acts as a trusted terminal. |
| **The stepper and robot arm are real**, but the stepper calls block and the arm needs a power jack at the correct voltage (5 V vs 12 V arm!). | Fine for theatrical demos. Respect the power caution. |
| **WiFi and BLE compile but are unverified by the SDK.** Only USB serial is supported. | The MVP stays on USB. WiFi goes on the roadmap slide. |

---

## 1. Three company ideas

### Idea A — **VARDE** *(my pick)*
**"The bank's second screen: a little device on the kitchen table that scammers can't see, can't screen-share and can't talk their way past."**

**Problem & customer.** In Norway the fraudster no longer hacks the bank. They hack the customer. A typical attack goes like this: a call from "DNB security", a request to move money to a "safe account", and often a request to install a remote-desktop app so the scammer can **watch and steer the victim's screen**. Every warning the bank shows on the phone or in the netbank is shown *on the device the scammer is looking at*, and the scammer just says "ignore that, it's standard." The worst-hit customers are elderly people, newly widowed people and people new to digital banking. The paying customer is **the bank**, which carries the reimbursement cost, the complaints, the regulatory heat and the brand damage.

**The insight.** Norway already put a bank hardware token in nearly every drawer: the old BankID **kodebrikke**. VARDE is *kodebrikke 2.0 that fights back*. It is an **out-of-band, un-screen-shareable trust display** with **coercion sensing**:
- It shows the *real* payee and amount in huge type, on a screen that no remote-desktop app can reach.
- It asks one plain-language question that the scammer can't hear or coach: *"Who asked you to make this payment?"* You answer with the knob: Me / Someone from the bank / Police / Family on SMS / Someone I met online.
- It listens for **an ongoing conversation during the confirmation** (on-device voice-activity detection, and no audio ever leaves the device). Someone talking to you *while* you approve a large first-time payment is a strong social-engineering signal.
- The 8x8 ToF counts bodies in front of it. "Two people at the screen during a big transfer" is an elder-abuse and pressure signal, picked up without a camera.
- When risk is high, the device **speaks** in a calm DNB voice: *"DNB will never ask you to move money to a safe account. Hang up. This payment is paused for 24 hours."* Then it starts a **physical cooling-off**: the payment can only be released by a long-press *after* the timer, or by a trusted family contact.

**Modules (14-port board).** ST7789 320x240 screen, rotary encoder (with its LED ring), I2S microphone, I2S speaker, button (the big "approve"), NeoPixel 3x3 (traffic-light beacon), VL53L5CX ToF (presence and body count), IMU (detects pick-up and "flip face-down to cancel").

**Business model.** B2B2C. The bank issues VARDE free to at-risk segments: 70+, customers whose accounts have been flagged, people who opt in through family-mandate products. The bank pays a **hardware cost plus a SaaS fee per active device per month**. Later tiers:
1. **VARDE Business** sits on the SMB finance desk and catches CEO fraud and invoice-redirection ("the supplier changed its bank account").
2. **Licensing the coercion-signal API** to other Nordic banks and to Vipps-style wallets.
3. **Insurers** pay for it as a loss-prevention device.

**Why it could be big.** APP / impersonation fraud is one of the fastest-growing loss categories in Europe, and regulation is pushing the losses onto banks. The UK has had mandatory APP reimbursement since Oct 2024, and the EU's payment-services reform is moving toward bank liability for bank-impersonation scams. *(Team: pull current Norwegian figures from Finans Norge / DNB's own fraud reports for the slide. I am deliberately not quoting numbers from memory.)* When the bank is liable, a ~NOK 500 device that stops a single NOK 200 000 scam pays for hundreds of devices. Every bank in the world has this problem, and almost none has a hardware channel the customer actually trusts.

**Why DNB / this hackathon.** Fraud is DNB's most public consumer-trust topic. VARDE is about *trust*, *consumer banking* and *protecting vulnerable customers*. Those are the bank's own words. It also uses the kit for what it's uniquely good at: a cheap, modular **physical** channel that no phone app can be.

---

### Idea B — **TYNGDE** ("weight")
**"Make digital money heavy again: a smart piggy bank for kids growing up in a cashless country."**

**Problem & customer.** Norwegian kids may never handle cash. Pocket money arrives through an app, and spending is a tap. Money with no physical feedback is abstract, and abstract money is hard to learn. The customer is **parents** (through DNB youth accounts), and **the bank**, which wins a customer at age 7 and keeps them for 70 years.

**The product.** A desk "pig" linked to the child's DNB youth account:
- **Shake it to hear your balance.** The IMU detects the shake, and the speaker plays a coin rattle whose *density and pitch scale with the balance*. A rich pig sounds rich. An empty pig sounds sad and tinny. This is the signature moment.
- **A savings tower.** A NEMA17 stepper drives a lead screw that physically *raises a column* toward the goal the kid set with the rotary knob ("Lego set: 899 kr"). Spend money with your card, and the tower physically drops while you watch.
- **Allowance day.** When pocket money arrives, the NeoPixel grid fills up coin by coin and the pig plays a jingle.
- **Wave to peek.** A hand-wave over the ToF shows "days until goal" and "what that candy cost you in Lego-percent" on the ST7735 screen.
- **Parent quests.** A parent approves a chore in the app, the kid presses the button on the pig to "collect", and the money moves.

**Modules.** IMU, speaker, stepper motor, rotary encoder, NeoPixel, ST7735 screen, ToF, button (14-port board).

**Business model.** Sold at cost or bundled with a DNB youth account plus a small family subscription. Revenue comes from account acquisition and lifetime value, a premium "quest" content tier, and co-branded editions.

**Why big.** Every cashless economy (the Nordics first, then the UK, NL, China) has this financial-literacy gap. It's a hardware-led acquisition funnel for the most valuable customer cohort there is: future adults.

**DNB fit.** Youth banking, financial literacy, and the family as the unit of customer relationship. It's also an incredibly charming demo.

---

### Idea C — **ATTEST**
**"Payments that release when physics agrees: sensor-attested escrow and parametric cargo insurance."**

**Problem & customer.** In trade finance and cargo, payment and insurance depend on paper and trust. *Did the salmon stay cold? Was the pallet dropped? Was the crate opened in transit?* Disputes take months, and insurance claims are adversarial. The customers are **exporters and importers, logistics firms and cargo insurers**. DNB is a leading global shipping bank, and seafood is one of Norway's biggest exports.

**The product.** A matchbox-sized ATTEST puck rides inside the shipment and keeps a **tamper-evident, hash-chained log**:
- AHT20 records **cold-chain** temperature and humidity excursions.
- SCD40 tracks **CO2**, which shows the respiration and ripening of produce and live-cargo stress (a real reefer-container metric).
- The IMU records **shock, drop and tilt** events.
- The ToF facing the lid or door detects **"container opened"** without a camera.
- The microSD card holds an append-only log where each line carries the hash of the previous one.
- The ST7735 screen and NeoPixel give the receiver a one-glance verdict at delivery. The receiver presses the button to sign.

At handover, the log is the oracle. **The bank's escrow automatically releases payment if every condition held.** If one failed, a **parametric insurance** payout triggers automatically, without a claims adjuster.

**Modules.** 8-port board (a compact puck): AHT20, SCD40, IMU, ToF, microSD, ST7735 screen, button. Battery and LTE are roadmap items (the kit has no cellular).

**Business model.** A per-shipment attestation fee plus device leasing. The bank earns on the escrow and trade-finance product, and insurers pay for lower loss ratios and zero-touch claims.

**Why big.** Global cold-chain and cargo-insurance losses are enormous. "Programmable money conditioned on the physical world" is the honest version of what blockchain "smart contracts" promised. Here the trust comes from the bank, not a token.

**DNB fit.** Shipping, seafood, insurance and sustainability (less spoiled food) all in one product. It fits DNB's corporate banking DNA, not just consumer banking.

---

## 2. My pick: **VARDE** — hackathon MVP spec (24–48 h)

Why VARDE over the others: it carries **the strongest emotional story in 3 minutes**, the **clearest buyer (the bank)** and **the deepest DNB fit**. Every module earns its place, and none of them needs sim-only hardware. TYNGDE is the backup if the team wants maximum charm.

### 2.1 Board, modules and port placement (✅ checked with `atech validate`: `ok`)

```yaml
name: varde
board: 14port
modules:
  - {id: st7789_tft,      instance: screen, ports: [1, 2]}    # big trusted display (gets the HW SPI host)
  - {id: rotary_encoder,  instance: knob,   ports: [3, 4]}    # answer the question; LED ring = countdown
  - {id: microphone,      instance: mic,    ports: [5, 6]}    # on-device voice-activity detection
  - {id: button,          instance: approve, port: 7}         # isolated top-middle slot = the one big button
  - {id: speaker,         instance: voice,  ports: [9, 10]}   # spoken warnings (PCM from laptop) + chimes
  - {id: neopixel,        instance: beacon, port: 11}         # green / amber / red status
  - {id: distance_sensor, instance: tof,    port: 13}         # wake on approach + body count (8x8)
  - {id: icm40608,        instance: imu,    port: 14}         # picked up? flip face-down = cancel
```

Port 7 is the isolated middle slot, so the **approve button sits alone, dead centre**. That is good industrial design for free. Power comes over USB-C only, because there is no arm or stepper and so no power jack is needed. The mic uses I2S_NUM_1 and the speaker uses I2S_NUM_0, so they don't clash.

### 2.2 What runs where

**On the board (C++ in `code:` blocks; keep `loop` non-blocking):**
- **A small state machine:** `IDLE → SHOW_TX → ASK → (VERIFY | HOLD) → DONE`.
- **Custom actions from the laptop:**
  - `varde_show` carries a compact `amount|payee|iban_tail|new_payee|risk` string. A pipe-delimited string avoids JSON parsing on the device. ArduinoJson via `libraries:` also works.
  - `varde_state` sets the mode (verify / hold / ok).
  - `varde_timer` sets the cooling-off seconds.
- **UI:** the transaction card is drawn with a smooth `FreeSansBold18pt7b` font. The amount is huge and the payee large. A **"NEW RECIPIENT"** banner appears when relevant. The rotary list for the question uses the bundled `ListView`/`drawList`, and the LED ring shows the countdown.
- **On-device VAD (the secret sauce):**
  1. A ~15 s noise-floor calibration at boot.
  2. Per 32 ms frame: RMS above the floor + 6 dB, plus a zero-crossing rate in the speech band, plus a crude 2-bin Goertzel energy ratio (~300–3400 Hz vs. high band).
  3. A rolling "voiced fraction" over the last 10 s, sent as an event `varde_voice` (0–100) at 4 Hz.
  4. **Raw audio never leaves the chip.** That is a privacy claim you can put on the slide.
- **Presence and body count:** threshold the ToF grid at about 1.2 m. Connected-component count on the 8x8 occupancy gives the `varde_people` event (0/1/2+). Also: wake the screen when someone approaches.
- **Gestures:**
  - Long-press approve for 2 s = confirm (`getGesture() == GESTURE_LONG_PRESS`).
  - IMU `face_down` = cancel.
  - Knob press = select an answer.
  - These emit `varde_answer` and `varde_decision` events.
- **Beacon:** green when calm, amber pulse while asking, red slow-breathe on hold.

**On the laptop (Python, `atech.Board`, one process holds the port):**
- **Fake netbank.** A tiny local web page or CLI where the "customer" types payee and amount. It must look like DNB-blue enough to be convincing.
- **Risk engine.** Rule-based, with explainable weights. Inputs: new payee, amount vs. the history in a JSON mock, payee name containing a "safe/sikker/konto" pattern, time of day, plus the **device signals** (voiced fraction during the ASK phase, the knob answer, people count, how long until approval).
  - Outputs: `APPROVE` / `VERIFY` / `HOLD_24H`.
  - Rule example: "someone from the bank/police asked me" → always `HOLD`.
- **Voice.** Pre-render 3–4 TTS clips (Norwegian and English) to 16 kHz mono WAV at build time, chunk them into base64, and stream them with `board.call("voice_play_pcm", chunk)`. Pre-render, so there is no network at demo time.
- **Bank-side dashboard.** A live timeline of signals with an explanation ("Paused: new payee, NOK 49 000, active conversation 82%, answer = 'Someone from the bank'") and a "Family contact notified" card.
- **Scammer's-eye view.** A second browser window that shows only what remote-desktop sees: "Payment pending…". This proves the scammer is blind to VARDE.

### 2.3 The 3-minute demo judges see

| Time | Beat |
|---|---|
| **0:00–0:25** | **Hook.** Hold up an old BankID kodebrikke: "Norway once put one of these in every drawer. Today's scammer doesn't hack DNB. They call your mother, say they're DNB, and *watch her screen* through a remote-desktop app. Every warning you show her, they see too." Put VARDE on the table. |
| **0:25–0:55** | **Normal life is frictionless.** A teammate pays rent of 9 800 kr to a known payee. VARDE wakes as they lean in (ToF), glows green, and shows a big clean card. A 2 s long-press, a soft chime, done. *Point to make: under 5 seconds, no questions.* |
| **0:55–2:05** | **The scam, live. Invite a judge to play "Mor".** A teammate on speakerphone: *"Hei, this is Kari from DNB security, your account is compromised, move 49 000 to this safe account, I'll stay on the line."* The judge enters the payment in the fake netbank. VARDE turns **amber**: "NEW RECIPIENT · 49 000 kr · Who asked you to do this?" The judge, coached by "Kari", twists to *Someone from the bank*. Meanwhile the dashboard shows "conversation detected 80%+". VARDE goes **red** and **speaks**: *"DNB will never ask you to move money. Hang up now. This payment is paused for 24 hours."* The LED ring starts a countdown. |
| **2:05–2:30** | **The scammer is blind.** Switch to the scammer's-eye window: the netbank just says "Pending". "Kari" can't see the question, the warning or the countdown. Show the bank dashboard's explainable decision and "Daughter notified". |
| **2:30–3:00** | **Business.** "The bank is increasingly liable for this fraud. One stopped scam pays for hundreds of VARDEs. We start with 70+ customers, then CFO desks (invoice fraud), then license the coercion-signal API to other banks." Closing line: **"Kodebrikke protected the login. VARDE protects the person."** |

### 2.4 Build plan (a team of 3–4)

- **H0–4:** First `atech build` (2.4 GB toolchain download, so do it on good wifi *immediately*). Then placement, `atech check`, and default-feedback smoke test of all 8 modules.
- **H4–14:**
  - **Firmware dev:** state machine, screen UI, custom actions.
  - **Audio dev:** VAD + `varde_voice` event, tuned with a phone on speaker at 30 cm.
  - **Laptop dev:** fake netbank + risk engine + dashboard (Flask or plain HTML + SSE).
- **H14–24:** TTS clips and PCM streaming, ToF body count, IMU flip-cancel, end-to-end integration.
- **H24–36:** Rehearse the demo 10× in a *noisy* room, tune thresholds, record a backup video.
- **H36–48:** Pitch deck, real fraud statistics, polish (a 3D-printed or cardboard "cairn" enclosure; *varde* means cairn, the stone marker that guides you home in the mountains).

### 2.5 Riskiest technical assumption

**That on-device voice-activity detection can reliably tell "someone is talking to you on speakerphone" apart from a loud hackathon hall.** A plain RMS level will fail there. A false "conversation detected" on the normal payment ruins the frictionless story, and a miss on the scam ruins the climax.

**De-risk early (hour 4–10):**
1. Calibrate the noise floor at boot and during IDLE.
2. Use a speech-band energy ratio + zero-crossing rate, not just RMS.
3. Require a *sustained* voiced fraction (≥ 60% over 10 s) and **only evaluate during the ASK window**.
4. Put the phone speaker 20–40 cm from the mic in the demo choreography.
5. **Make the engine degrade gracefully.** The knob answer "Someone from the bank" alone forces `HOLD`, so the demo climax never depends on the mic, and the mic only *adds* confidence on the dashboard.

Runner-up risk: PCM streaming over serial. Each `speaker_play_pcm` chunk blocks until it has played, so a 4 s clip is about 60 acked calls. If that stutters, fall back to an RTTTL alarm plus big on-screen text, with the clip played from the laptop's speakers.

---

## 3. Wild card (deliberately outrageous): **BUDSJETTARMEN** — "The Budget Arm"

An **SO-101 robot arm** sits on your desk with read access to your DNB account. When you've blown your monthly "fun" budget and the ToF sees your hand reaching for your phone **after 23:00** (the hour of impulse shopping), the arm reaches over, **gently flips your phone face-down** and holds it there. Meanwhile the speaker plays a deeply disappointed sigh and the screen shows "That's 3 pizzas this week, Eldar."

The premium tier adds a stepper-driven **card vault**. You slot your physical card in at payday, and the drawer **time-locks** it until your budget recovers. It's a cooling-off period with actual torque. Marketing tagline: *"Impulse control as a service. Your bank, but with hands."*

(It is genuinely buildable: `robot_arm_play_slot` replays a recorded "flip" motion. Power caution: match the supply to the arm, 12 V for the 12 V arm and 5 V for the 5 V arm.)
