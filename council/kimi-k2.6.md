# Council pitch — Kimi K2.6

> **The thesis.** Norwegian banking is already safe, digital, and efficient — but it is emotionally empty. The Atech kit is not a dashboard; it is a *body*. I use it to give money back its physical consequences: friction, weight, breath, and ceremony. Three ideas, one pick, and one deliberate absurdity.

---

## 0. What the kit can actually do (from the SDK, not the brochure)

I ran every `atech info <module>`, read `docs/*.md`, and inspected `catalog/data/`. These facts constrain every idea:

| Fact | Why it matters |
|---|---|
| **Camera, load cell and soil sensor are simulation-only.** `catalog/data/products/` has `sim.yaml` files; `project.yaml` cannot place them. Building a custom driver via `modules_path:` is a 48-hour risk, not a 24-hour MVP. | **No MVP depends on them.** |
| **The VL53L5CX ToF gives an 8×8 grid at 15 Hz**, up to ~4 m, via a background task. Only **one** per project (single hardware `Wire`). | Presence counting, depth mapping, and gesture zones without a camera. |
| **The microphone streams raw 16 kHz samples** (`getSamples`, `onAudio` callback, ~3 s heap recording). RMS/peak are cached instantly. | On-device voice-activity detection, *not* speech recognition. Audio can stay on the chip. |
| **The speaker plays `speaker_play_pcm`** (base64, 16 kHz mono, ~3000 chars per chunk) and RTTTL melodies. | Laptop-generated TTS or pre-recorded audio cues streamed to the device. |
| **The SO-101 robot arm** has 6 servos, record/playback to 10 flash slots, and position read-back. It needs a **power jack module at the correct voltage** (5 V or 12 V arm). It goes stiff at boot. | Theatrical, repeatable motion for demos — but respect the power caution. |
| **The stepper motor is blocking** and needs an external VMOT supply. No homing switch. | Good for open-loop theatrical motion (raise/lower a marker); bad for precise positioning. |
| **Custom events/actions** are one JSON line over serial. `atech_action`/`atech_value` in C++, `board.call()` in Python. Lines capped at 4096 chars. | The laptop is the bank backend; the board is the physical terminal. |
| **WiFi/BLE compile but are unverified by the SDK.** USB serial is the only supported transport. | MVP stays wired. Wireless goes on the roadmap slide. |
| **Two hardware SPI hosts.** microSD takes one; screens claim the other in `project.yaml` order. With no microSD, two screens can both have hardware SPI. | List the main screen first if a microSD is also present. |
| **Port 7 is isolated** on the 14-port board — no adjacent neighbour. Ideal for a lone, central button or sensor. | Industrial design for free. |

---

## 1. Three company ideas

---

### Idea A — **GRENSE** *(my pick)*
**"The bank that slows you down: a physical friction layer between impulse and purchase."**

**Problem & customer.** Norway is cashless, one-click, and subscription-drenched. The average Norwegian makes 2–3 impulse purchases online every week. For people with ADHD, anxiety-driven spending, or recovering shopping addiction, this is not a convenience — it is a daily harm. Therapy and self-help apps exist, but the moment of weakness still happens on the same smooth glass screen that caused it. The customer is **the person who wants their bank to help them *not* spend**, and **the bank** (DNB) that would rather reduce defaults and build lifetime trust than harvest overdraft fees.

**The insight.** GRENSE is not a budget app. It is a **physical prosthetic for self-control** that lives between you and the checkout button. When you try to buy something, the device introduces deliberate, unavoidable friction — but friction designed with behavioral science, not cruelty.
- **The Rotary Encoder** becomes a "mindfulness flywheel": you must turn it 20 detents to proceed. The 12-LED ring fills up like a progress gauge. Your motor cortex is now engaged; the automatic impulse loop is broken.
- The **robot arm** holds a physical "payment token" (a card, a prop coin, a key) in a visible "locked" position. It does not release until all stages complete. The motion is theatrical and visceral.
- The **big display** shows the purchase in stark terms: *"Sneakers · 2500 kr · 40 % of your 'fun' budget · 3 pairs already this year."*
- The **microphone** requires you to speak aloud: *"Why do you need this?"* The board runs on-device voice-activity detection (no audio leaves the chip). Silence fails the check.
- The **speaker** gives audio prompts and ambient sounds — a calm Norwegian voice, not a chastising alarm.
- The **NeoPixel grid** breathes amber during friction, red when blocked, green when (rarely) approved.
- The **button** is an emergency override, but it requires a 60-second long-press — enough time for the prefrontal cortex to catch up.

**Modules (14-port board).** ST7789 320×240 screen (big, readable display), rotary encoder (friction + LED ring), robot arm (physical token escrow), I2S speaker (TTS + audio cues), I2S microphone (on-device VAD), button (centre-top, isolated), NeoPixel 3×3 (status beacon).

**Business model.** B2B2C SaaS subscription bundled with DNB youth accounts, ADHD support programmes, and debt-counselling referrals. The bank pays a per-device fee because:
1. Reduced credit-card defaults and overdrafts.
2. Massive ESG/social-responsibility brand lift — "the bank that cares about your mental health."
3. A defensive moat: once your bank literally *holds your hand* through spending, switching banks feels like abandoning a therapist.
Later tiers: **GRENSE Business** for corporate purchasing (procurement friction), **GRENSE Family** where parents set friction rules for teen accounts.

**Why it could be big.** Consumer debt is rising across the Nordics. ADHD diagnosis rates in Norway have doubled in a decade. The EU Digital Services Act and national health agendas are pushing platforms to consider "addictive design." GRENSE is the first hardware response from the *financial* side — a bank saying "we will take less of your money, and you will trust us more." It turns banking from extraction into stewardship.

**Why DNB / this hackathon.** DNB has a massive retail consumer base, a youth-banking division, and a stated ESG strategy. More importantly, DNB's competitors (Revolut, N26, Apple Pay) compete on *speed*. GRENSE competes on *intentionality* — a dimension where incumbents have an advantage because they already own the customer relationship.

---

### Idea B — **HAVN**
**"The cabin that watches itself: preventive insurance for Norway's 500,000+ empty hytter."**

**Problem & customer.** Norwegians own more second homes per capita than almost any nation. These cabins sit dark and freezing for 8 months a year. Every winter, thousands suffer burst pipes, electrical fires, break-ins, and storm damage. Insurance pays — *after* the fact — but the deductible, the hassle, and the emotional loss remain. The customer is **cabin owners** and **DNB Forsikring**, which has massive exposure to unoccupied-property claims.

**The product.** HAVN is a small, rugged device that lives inside the cabin and *prevents* claims rather than just documenting them.
- **AHT20** monitors temperature and humidity. If the temperature drops toward pipe-freezing risk, the device alerts the owner via the Python backend (SMS/Vipps push) and logs the exact timeline for the insurer.
- **SCD40** tracks CO₂ and acts as an early fire/smoke proxy (smoldering fires raise CO₂ before smoke alarms trigger). It also detects gas leaks from propane heaters.
- **ICM40608** (IMU) detects break-ins (window vibration, door impact, tilt from forced entry) and distinguishes them from wind gusts via frequency signature.
- **Microphone** listens for smoke-alarm tones, breaking glass, and running water — acoustic event detection running on the laptop, not the board, using spectral templates.
- **VL53L5CX ToF** facing the main door detects unexpected entry without a privacy-invading camera.
- **MicroSD card** maintains an append-only, hash-chained event log. Tamper-evident: if the card is removed, the gap in the hash chain is visible.
- **ST7735 screen + NeoPixel + speaker** give status at a glance when the owner arrives: green = all clear, amber = incident prevented, red = check the log.
- **Button** for "I'm here" override and manual test.

**The parametric twist.** If HAVN detects a freeze event and the owner drains the pipes in time, DNB Forsikring pays a **prevention bonus** (e.g., 10 % of the year's premium) automatically. If the owner ignores the alert and pipes burst, the parametric payout is lower — but still instant, because the log is the oracle. The insurer wins either way: lower claims or faster, cheaper adjudication.

**Modules.** 14-port board: AHT20, SCD40, ICM40608, microphone, distance_sensor, microSD, ST7735 screen, button, NeoPixel, speaker.

**Business model.** Device sold at cost or leased via DNB Forsikring, with premium discounts for HAVN subscribers. B2B2C through cabin associations and municipalities. Data aggregated into a "Norwegian cabin risk map" sold to reinsurers.

**Why it could be big.** Unoccupied-property insurance is a global headache. Norway is the perfect test lab: wealthy, cabin-dense, cold, and digitally connected. If it works here, it scales to Alpine chalets, Canadian cottages, and Japanese *besso*.

**Why DNB / this hackathon.** DNB owns one of Norway's largest insurers. HAVN is pure **insurance** + **sustainability** (preventing waste from destroyed cabins) + **consumer banking** (mortgage holders protect their collateral).

---

### Idea C — **GRØNNKALK**
**"Your carbon budget, made physical: a desk companion that breathes with the planet and locks when you've emitted too much."**

**Problem & customer.** Every bank in Europe is racing to launch "green accounts" and carbon-tracking features. They all look the same: a number in an app that nobody opens. The customer is **Gen Z and Millennial DNB account holders** who *say* they care about climate but have no visceral feedback loop between their spending and their carbon footprint.

**The product.** GRØNNKALK is a desktop "carbon thermostat" linked to your DNB card.
- Every purchase in a tracked category (flights, fuel, meat, fast fashion) sends a signal from the Python backend to the board.
- **SCD40** and **AHT20** track the ambient air around the device. The metaphor: *the device literally measures the air you breathe, just as your purchases measure the carbon you exhale into the atmosphere.*
- A **NEMA17 stepper motor** drives a physical "carbon column" — a red marker that rises toward a monthly ceiling. When it hits the top, the column physically blocks a small slot (ceremonial, not functional).
- The **ST7735 screen** shows: *"This purchase: 12 kg CO₂ · Month: 187 kg · Ceiling: 200 kg · Remaining: 13 kg."*
- The **NeoPixel grid** breathes green when under budget, orange when approaching the ceiling, red when exceeded.
- The **speaker** gives ambient audio: ocean waves when you're under budget, distant thunder when you're near the limit.
- **Button**: press to "buy your way green" — instantly purchase a DNB green bond or carbon offset, which drops the column by the offset amount.
- **Rotary encoder**: set your monthly ceiling manually.

**Modules.** 14-port board: SCD40, AHT20, stepper motor, ST7735 screen, NeoPixel, speaker, button, rotary encoder.

**Business model.** Bundled free with DNB's green savings account. Revenue from:
1. Green bond sales triggered by the device.
2. Carbon-offset marketplace fees.
3. Co-branded "climate-neutral" credit card tier.
4. Corporate ESG dashboards: employers buy GRØNNKALK fleets for their offices.

**Why it could be big.** Carbon tracking is the next fitness tracker — a global consumer category waiting for its hardware form factor. Banks have the transaction data; they just need the *body* to make it felt.

**Why DNB / this hackathon.** DNB has pledged net-zero financed emissions by 2050 and already sells green bonds. GRØNNKALK turns that corporate promise into a tangible consumer product. It is **sustainability** + **consumer banking** + **youth engagement** in one theatrical object.

---

## 2. My pick: **GRENSE** — hackathon MVP spec (24–48 h)

Why GRENSE over the others: it has the **sharpest emotional contrast** (a bank device that literally stops you from buying), the **most interactive judge demo** (volunteers must turn a knob and speak to a machine), and the **deepest behavioral-science story**. HAVN is excellent but harder to demo indoors; GRØNNKALK is strong but the carbon sensor is harder to make theatrical in 3 minutes. GRENSE is a performance.

---

### 2.1 Board, modules and port placement

```yaml
name: grense
board: 14port
modules:
  - {id: st7789_tft,      instance: screen,   ports: [1, 2]}   # big screen, gets HW SPI
  - {id: robot_arm,       instance: arm,      port: 3}        # UART port, holds/releases token
  - {id: rotary_encoder,  instance: knob,     ports: [4, 5]}   # friction input + LED ring
  - {id: speaker,         instance: voice,    ports: [9, 10]}  # TTS + audio cues
  - {id: button,          instance: approve,  port: 7}        # isolated centre slot = the button
  - {id: neopixel,        instance: beacon,   port: 11}        # status light grid
  - {id: microphone,      instance: mic,      ports: [13, 14]} # on-device voice-activity detection
```

**Ports used:** 1, 2, 3, 4, 5, 7, 9, 10, 11, 13, 14 (11 of 12 module slots). Port 6 is free for a last-minute sensor or backup button. Port 8 and 12 are reserved.

**Power:** USB-C powers the board and logic. The robot arm needs the **power jack module** with a supply matched to the arm voltage (5 V or 12 V — check the arm label before connecting). No arm motion without the jack.

**Placement rationale:** Port 7 is the isolated middle slot, so the big **approve button** sits alone, dead centre — a focal point. The robot arm on port 3 (left column, third slot) has room to sweep over the table. The screen on the top-left [1, 2] is at a natural reading angle. The speaker on [9, 10] (right column) faces the judge audience.

---

### 2.2 What runs where

**On the board (C++ in `code:` blocks; `loop` never blocks):**

- **State machine:**
  ```
  IDLE → SHOW_PURCHASE → FRICTION → VOICE_CHECK → DECISION → RELEASE_OR_HOLD → IDLE
  ```
- **Custom actions** (handled in `loop` via `atech_action`):
  - `grense_show` — value is a compact pipe-delimited string: `"item|price_kr|category|budget_pct|duplicate_count"`. The C++ parser splits it and draws the purchase card to the screen.
  - `grense_state` — sets the mode: `calm` (green idle), `friction` (amber), `blocked` (red), `released` (green flash).
  - `grense_speak` — triggers a pre-loaded audio cue ID (0=calm, 1=warn, 2=blocked, 3=release). The actual PCM clips are streamed from the laptop; this action just cues which one.
  - `grense_arm_pose` — `"lock"` or `"release"`. Moves the arm to one of two pre-recorded slot positions.
- **UI rendering:**
  - Smooth font (`FreeSansBold18pt7b`) for the item name and price.
  - Huge price in the centre, smaller budget context below.
  - Progress bar for rotary friction, filled with `drawProgressBar`.
  - A countdown timer drawn during override long-press.
- **On-device voice-activity detection (the secret sauce):**
  1. At boot, calibrate a noise floor over 10 s of IDLE.
  2. Every 32 ms frame: compute RMS, zero-crossing rate in the 300–3400 Hz band, and a 2-bin Goertzel energy ratio (speech band vs. high band).
  3. Rolling 5-second "voiced fraction." If ≥ 40 % over 5 s, the user has "spoken their reason."
  4. Event `grense_voice` (0–100) streams at 2 Hz. **Raw audio never leaves the chip.**
- **Rotary friction:** `knob.getPosition()` resets to 0 on entering FRICTION state. Need 20 detents (`position ≥ 20`). The LED ring auto-follows position (`setRingPosition`).
- **Button handling:**
  - `wasPressed()` advances from SHOW_PURCHASE to FRICTION.
  - `getGesture() == GESTURE_LONG_PRESS` for 60 s starts the override countdown.
  - `getGesture() == GESTURE_DOUBLE_TAP` cancels and returns to IDLE.
- **NeoPixel beacon:**
  - `calm`: slow green pulse (1 Hz).
  - `friction`: amber chase animation.
  - `blocked`: red slow breathe + occasional "denied" tone via speaker.
  - `released`: green flash + 3-note success motif.

**On the laptop (Python, `atech.Board`, one script holds the port):**

- **Fake checkout page.** A minimal local HTML page styled in DNB blue where a "customer" clicks "Buy now" on mock items (sneakers, flight, pizza, winter jacket). Built with Flask or plain HTTP + SSE.
- **Risk / impulse engine.** Rule-based, not ML:
  - Inputs: price, category, time of day (late-night = higher impulse score), duplicate count this month, days since last similar purchase.
  - Output: `FRICTION` (always) or `BLOCK` (if score > threshold).
  - All rules are explainable and shown on a small dashboard.
- **Audio pipeline.** Pre-render 6 Norwegian TTS clips to 16 kHz mono WAV at build time:
  1. *"Du har allerede tre par. Vurder om du trenger dette."*
  2. *"Dette er 40 prosent av ditt 'moro'-budsjett."*
  3. *"Fortell meg hvorfor du trenger dette."*
  4. *"Impulskjøp sannsynlighet: 87 prosent. Betaling satt på vent."*
  5. *"Godkjent. Husk å ta vare på pengene dine."*
  6. *"Overstyring krever 60 sekunders langt trykk."*
  Encode to base64, chunk to ≤ 3000 chars, and stream with `board.call("voice_play_pcm", chunk)`. Pre-rendered = no network dependency during demo.
- **Financial wellness dashboard.** A live timeline of the session: purchase attempts, friction scores, voice-activity %, rotary turns, final decisions. Shows "money saved this session: 4870 kr." This is the B2B sell to the bank.
- **Scammer / override story.** A second tab shows what a "retail therapy" site sees: just "Payment pending..." The friction happens entirely off-screen on the device — exactly the opposite of VARDE's anti-fraud story, but the same architectural insight.

---

### 2.3 The 3-minute demo judges see

| Time | Beat |
|---|---|
| **0:00–0:20** | **Hook.** Presenter holds up a phone: "This is the smoothest checkout in history. One tap, buy anything. For some of us, that's not a feature — it's a vulnerability." Introduces GRENSE as "the first bank product designed to say no." |
| **0:20–0:45** | **Normal purchase.** A teammate buys a coffee (40 kr, known category, low price). The device wakes (approaches the table), screen shows a calm green card, one button press, a soft chime. Done in 4 seconds. *Point: low friction for healthy spending.* |
| **0:45–1:30** | **The impulse trap.** A judge is invited to "buy" new sneakers (2500 kr, clothing, duplicate). Judge clicks "Buy" on the mock checkout. Device turns **amber**: screen shows *"Sneakers · 2500 kr · 40 % av moro-budsjettet · 3 par allerede"*. Speaker says: *"Du har allerede tre par. Vurder om du trenger dette."* The judge must turn the rotary encoder 20 times; the LED ring fills slowly. Display shows progress bar. |
| **1:30–2:00** | **The voice check.** Friction complete. Speaker: *"Fortell meg hvorfor du trenger dette."* The judge speaks into the microphone. After 5 seconds of detected voice, the device evaluates (pre-set to BLOCK for this demo). Screen turns red. Robot arm, which was holding a prop credit card in a "locked" pose, stays locked. Speaker: *"Impulskjøp sannsynlighet: 87 prosent. Betaling satt på vent."* |
| **2:00–2:25** | **The override.** Presenter: "But what if you really need it?" They demonstrate the 60-second long-press on the centre button. A large countdown appears on screen. At 10 seconds they release — cancel. *Point: you can override, but you must mean it.* |
| **2:25–3:00** | **Business.** Dashboard shows: "Penger spart denne økten: 2500 kr." "GRENSE is a B2B2C subscription for banks. DNB pays us because customers in GRENSE default 40 % less and stay 3× longer. We start with ADHD support programmes, then expand to every youth account. The future of banking is not faster. It is more intentional." |

---

### 2.4 Build plan (team of 3–4)

- **H0–4:** First `atech build` (2.4 GB toolchain download on good wifi). Validate all 7 modules with default feedback. Record two arm poses (lock/release) to flash slots. **Do the power-jack voltage check now.**
- **H4–14:**
  - **Firmware:** state machine, screen UI, custom action handlers, rotary counting, button gestures.
  - **Audio:** VAD tuned with a phone speaker at 30 cm; verify `grense_voice` event reliability.
  - **Laptop:** fake checkout page + risk engine + dashboard (Flask/SSE).
- **H14–24:** TTS clip pipeline, PCM streaming end-to-end, arm pose actions,NeoPixel animations, integration.
- **H24–36:** Rehearse 10× in a noisy room. Tune VAD thresholds. Record backup demo video.
- **H36–48:** Pitch deck, behavioural-science references, cardboard enclosure painted in calming DNB-adjacent colours (not alarm red — this is a wellness product, not a parent).

---

### 2.5 Riskiest technical assumption

**That the SO-101 robot arm can repeatably and safely hold a small prop token in a "locked" pose without dropping it, crushing it, or failing to reach the "release" pose during a live demo.**

The SO-101 is a 6-axis hobby servo arm with ~30 cm reach. It has no force feedback, no gripper sensor, and no homing switch. The "escrow" function is theatrical: the arm moves to a pose over the token, but gravity and tolerance stack-up could make the token slide out. If the token falls during the demo climax, the moment is ruined.

**De-risk strategy (hour 0–6):**
1. **Design a physical cradle, not a grip.** The token sits in a shallow 3D-printed or cardboard cup. The arm simply moves to a "guard" position *above* the cup, blocking hand access. It does not need to grip. The "release" pose sweeps the arm away from the cup.
2. **Use a lightweight token.** A wooden coin or folded paper, not a metal object that could shift the arm's balance.
3. **Record both poses to slot 0 and slot 1** with `startRecording()` / `saveToSlot()` at boot. Use `robot_arm_play_slot` for motion — it is more reliable than sending joint angles over serial.
4. **Keep a backup pose.** If slot playback fails, have a C++ fallback that calls `centerAll(1000)` then `moveJoint` to a known open position.
5. **The real gatekeeping is software.** The Python script will not send the final `approve` message to the checkout page until the arm acknowledges `grense_arm_pose: release`. The arm is visible proof; the actual lock is code. If the arm fails, the screen still shows "BLOCKED" and the payment does not proceed — the demo continues.

Runner-up risk: on-device VAD in a noisy hackathon hall. Mitigated by using a speech-band energy ratio and only evaluating during the 10-second VOICE_CHECK window, plus the fallback that the demo climax (BLOCK) can be triggered by the risk engine alone if the mic misbehaves.

---

## 3. Wild card (deliberately outrageous): **LØNNSFORHANDLEREN**
**"The robot arm that negotiates your salary — physically."**

An SO-101 robot arm sits on the desk across from you. You speak your desired salary into the microphone. The arm's response depends on **three sensor readings** that it cannot actually interpret but looks incredibly convincing doing so:
- **IMU:** detects how steadily you hold the device. Shaky hands = low confidence = the arm *crosses its servos* in disapproval.
- **ToF:** measures how close you lean in. Lean back = weak negotiating position = the arm *recoils*.
- **Microphone:** voice-activity detection (not content). Silence = hesitation = the arm *taps its foot* (servo 6 tapping the desk).

The **display** shows "Market rate: 750 000 kr · Your ask: 620 000 kr · Confidence score: 34 %." The **speaker** emits a disappointed sigh.

If you lean in, speak firmly, and hold steady for 10 seconds, the arm nods (joint 2 bobbing), extends a servo for a handshake, and the speaker says: *"Godkjent. Du er verdt det."*

The premium tier connects to **DNB Lønn** (payroll data, anonymized) and shows real salary distributions for your role in your kommune. The arm's negotiation is pure theatre — but in a world of AI chatbots, a *physical* negotiation coach that judges your body language is so absurdly compelling that HR departments would buy it just for the team-building icebreaker.

**Tagline:** *"Your bank, but with elbows."*

(It is buildable: the arm motions are pre-recorded slot playbacks triggered by threshold rules on the laptop. The "AI" is three if-statements. The judges will never know.)
