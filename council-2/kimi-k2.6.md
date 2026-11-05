# Council Round 2 — Kimi-K2.6 Submission

## Three Pitches

---

### 1. HÅNDTRYKK — The Trust Physical Escrow for P2P Deals

**One-line pitch:** A physical, neutral third-party device that holds both money and goods during in-person second-hand transactions, releasing each only when both parties are satisfied.

**Problem and customer:**
Norway’s second-hand economy (Finn.no, Facebook Marketplace, Tise) is massive, but P2P fraud and awkward payment disputes are rampant. Buyers fear sending money before seeing the item; sellers fear handing over goods before payment clears. Both parties distrust the other’s phone app. Solo traders, students, and marketplace power-sellers are the core customers.

**The agent’s role:**
- **Perceives:** Voice commands from both parties via microphone; button presses and rotary-knob selections for transaction flow; accelerometer for tamper/movement detection; ambient audio patterns to detect conflict/agitation.
- **Decides:**
  1. Which transaction mode to run (sale, deposit-with-inspection, swap).
  2. When payment is confirmed (via Vipps API polling) and whether it matches the agreed amount.
  3. Whether the inspection window has expired.
  4. Whether to trigger refund, release, or alert a moderator.
  5. Tamper thresholds: unexpected movement, being unplugged, or environmental anomalies.
- **Acts:** Speaks instructions and status updates through the speaker; physically locks/unlocks the item compartment via motors; shows QR codes and confirmation UI on screen; lights LEDs green (all clear), yellow (pending), or red (dispute); sends Vipps/Vipps SMS and DNB transaction status to both parties.
- **Tools it calls:**
  - Vipps ePayment / Vipps PSP API for payment status.
  - DNB Open Banking / account verification for buyer KYC-lite (account existence).
  - SMS/voice gateway (Twilio/46elks) for off-device fallback notifications.
  - A lightweight dispute-moderation webhook (e.g., a cloud function that routes to Finn.no support or a community moderator).
- **Where it runs:** Heavy LLM reasoning and API orchestration run on a cloud/laptop **agent core** over Wi-Fi. The ESP32 board runs a **state-machine firmware** that handles real-time sensor fusion, motor control, audio level detection, and executes action commands from the cloud agent. On-device memory holds only the current transaction state and a short audio buffer for tamper detection.

**Kit parts used:**
- **Screen:** Shows QR code for Vipps payment, transaction amount, countdown timer, and step-by-step UI.
- **Speaker + Microphone:** Conversational guidance, payment confirmation tones, dispute audio capture.
- **Four motors:** Two DC motors drive a scissor-gate or sliding door for the item compartment; two more drive a mechanical "commit latch" that physically blocks both parties from walking away mid-transaction.
- **Three buttons + Rotary knob:** Party A commits (button 1), Party B commits (button 2), emergency cancel/dispute (button 3); knob scrolls through item category presets.
- **Three LEDs (Neopixel grid):** Real-time trust signal — green (paid + released), yellow (funds held), red (dispute), pulsing blue (waiting for buyer).
- **Accelerometer:** Tamper detection; triggers a physically audible alarm and locks the compartment if the device is shaken or lifted during an active transaction.
- **Temp/humidity sensor:** Ambient sanity check (if someone tries to spray/disconnect the device, the environment shifts).

**Business model:**
- Hardware: BOM ~€35-50, sold at €79-99 to early marketplace power-users.
- Transaction fee: 1-2% per successful transaction (split buyer/seller), capped at NOK 25. Cheaper than PayPal’s 3.5% and less friction than bank transfers.
- B2B licensing: White-label to marketplaces (Finn.no, Schibsted) and DNB’s SMB banking tier as a "Trust Partner" bundle.

**Why it could be big:**
P2P fraud is a €30B+ problem in Europe alone. A physical escrow device is category-creating: no app on either phone can claim neutrality. As marketplaces race to add in-person transaction safety, a dedicated hardware trust anchor becomes the "Stripe Terminal" of second-hand commerce. Network effects: the more sellers own one, the more buyers demand it.

**Why it fits DNB:**
DNB owns trust in Norway. Pairing a physical trust device with DNB’s Vipps infrastructure is a natural vertical integration: DNB becomes the *infrastructure of trust* for the informal economy. It also feeds DNB insurance (transaction insurance bundled at checkout) and SMB lending (sellers with verified transaction history qualify for micro-loans).

---

### 2. VAKT — The Independent Senior Guardian

**One-line pitch:** A physical guardian that lives in an elderly person’s home, moves to find them, monitors their wellbeing, and acts as a trusted financial proxy for daily needs.

**Problem and customer:**
300,000+ Norwegians aged 75+ live alone. Adult children worry; seniors resist surveillance apps and cameras. Current solutions are either invasive (cameras) or passive (pendant alarms). The real gap is *proactive, dignified assistance* that combines safety with autonomy — including handling small daily financial tasks (grocery orders, pharmacy payments) without a grandchild’s phone.

**The agent’s role:**
- **Perceives:** Voice commands and ambient audio (falls, distress); temperature/humidity (heating failures); accelerometer on the device itself (if the bot is kicked or moved abnormally — proxy for a fall nearby); button presses for direct calls.
- **Decides:**
  1. Whether daily routines are anomalous ("no voice detected for 14 hours + temperature dropped 5°C").
  2. Whether a voice command is a request for help, a purchase, or a social call.
  3. Whether to escalate to family, emergency services, or a pharmacist.
  4. Whether a proposed purchase is within pre-authorized budgets (under NOK 500 = auto-approve; over = ask).
- **Acts:** Physically moves to the room where the person last spoke (open-loop DC motor sequences to "patrol" key rooms); speaks alerts and confirmations; displays large, high-contrast text on screen; lights LEDs for status; places orders and payments via APIs; calls family members as a voice bridge.
- **Tools it calls:**
  - DNB payment APIs for grocery/pharmacy orders (pre-authorized merchants only).
  - Vipps for quick family reimbursements.
  - Calendar API to schedule check-ins and medical appointments.
  - Pharmacy/home-care delivery APIs (e.g., Apotek 1, Helthjem).
  - Twilio/46elks for voice calls to family.
- **Where it runs:** The ESP32 runs **on-device wake-word detection** (microWakeWord or similar) and a **priority interrupt state machine** so it works even if Wi-Fi is down briefly. All LLM reasoning, API calls, and long-term memory run in a **cloud agent core** on the family’s home server or a DNB cloud instance. The board buffers the last 24 h of sensor aggregates and vocal summaries in its local memory for continuity during outages.

**Kit parts used:**
- **Screen:** Large medication reminders, weather, incoming call display, confirmation of purchases.
- **Speaker + Microphone:** Full-duplex voice calls, spoken alerts ("You have not taken your morning medication"), ambient listening for distress.
- **Four motors:** Two DC motors drive a small wheeled platform so the device can move between living room, kitchen, and bedroom (timed sequences, no camera-based navigation — it relies on known acoustic landmarks and bump-and-reverse). Two additional motors tilt/rotate the screen and microphone boom to face the person.
- **Three buttons:** Emergency call, "I am OK" dismiss, "Buy groceries."
- **Rotary knob:** Scroll through medication list, set thermostat target (if paired with smart home).
- **LED grid:** Morning routine tracker (lights progress for meds, exercise, meals).
- **Temp/humidity + Accelerometer:** Environmental monitoring and fall-proxy detection.

**Business model:**
- Hardware: Sold at cost (~€60) or subsidized by DNB as a "Senior Account" perk.
- Subscription: NOK 199/month for monitoring, fall-response, and family dashboard.
- B2B: Sold to Norwegian municipalities as a lower-cost alternative to emergency home visits (saves ~NOK 2,500 per avoided visit).

**Why it could be big:**
The global "aging in place" market is $50B+. A device that combines *physical presence* (it comes to you) with *financial autonomy* (you can still buy things without a phone) uniquely serves seniors who are cognitively capable but mobility-limited and phone-averse. Municipalities are desperate for cost-saving preventive care tools.

**Why it fits DNB:**
DNB is Norway’s largest pension provider. This device is a retention moat for senior banking customers. It also reduces insurance claims (fall detection = fewer hospitalizations = lower DNB health/life payouts) and deepens Vipps adoption in a demographic that currently avoids it.

---

### 3. KLIMA-KOMPIS — The Carbon-Budget Tamagotchi

**One-line pitch:** A physical desk companion that tracks your spending via DNB, calculates the carbon footprint in real time, and physically reacts — celebrating green choices or sulking when you blow your carbon budget.

**Problem and customer:**
Young Norwegians (25-40) care deeply about climate but have no feedback loop between their daily purchases and their carbon impact. Carbon calculators are tedious; banking apps show euros, not CO₂. The emotional disconnect between "buying a flight" and "melting ice" is the barrier.

**The agent’s role:**
- **Perceives:** Voice declarations ("I just booked a flight to Berlin"); DNB transaction stream via Open Banking; button presses to confirm or categorize ambiguous purchases; knob to set monthly carbon budgets.
- **Decides:**
  1. Carbon footprint per transaction (using merchant category + amount; for ambiguous items it asks).
  2. Whether a purchase pushes the user over their daily/weekly/monthly budget.
  3. The emotional state to express: ecstatic (big green shift), content, concerned, depressed, or angry.
  4. Whether to suggest an alternative (train instead of flight, second-hand instead of new) and auto-invest the savings into a DNB green fund.
  5. Whether to trigger a "morning carbon briefing" based on calendar and weather.
- **Acts:** Motors animate a physical "dance" or "slump" depending on carbon score; speaker plays emotional chiptune motifs; screen shows a real-time carbon gauge and purchase breakdown; LEDs form a "health bar" that depletes with carbon spending and regenerates with green choices; speaks personalized nudges.
- **Tools it calls:**
  - DNB Open Banking for transaction categorization and balance checks.
  - Climatiq or similar carbon-intensity API for real-time CO₂ factors per merchant category.
  - Vipps for one-tap micro-investments into DNB Grønt Norge or similar ESG funds.
  - Entur/Skyss API for public-transport alternatives.
  - Calendar API to preemptively warn about high-carbon upcoming trips.
- **Where it runs:** The ESP32 runs a **compact emotional-state machine** and drives all actuators. LLM reasoning, carbon calculations, investment logic, and long-term trend memory run in a **cloud agent core**. The device caches the current week’s budget state and transaction list so it functions during brief Wi-Fi outages.

**Kit parts used:**
- **Screen:** Carbon gauge, spending breakdown, merchant logos rendered as icons, morning briefing text.
- **Speaker + Microphone:** Voice logging of purchases, emotional audio feedback, audio nudges.
- **Four motors:** Two DC motors drive a seesaw or pendulum platform — the device physically tilts "up" when under budget and "down" when over; two stepper motors rotate ears/antennae or a small flag to indicate trend direction.
- **Three buttons:** Green choice boost, override/ignore, call for alternatives.
- **Rotary knob:** Scroll carbon budget categories, adjust monthly target.
- **LED grid:** 3×3 carbon health grid — all green = hero, all red = climate villain.
- **Temp/humidity:** Proxied as "room comfort" — if the office is too warm, the agent guilt-trips you about home heating.
- **Accelerometer:** Shake to dismiss a nudge (sentiment: "You’re ignoring me? Fine."); tap to replay last carbon summary.

**Business model:**
- B2C freemium: Hardware at €49 with ads/affiliate links; €9.99/month for advanced analytics, green investment automation, and family carbon leaderboards.
- B2B: Corporate wellness perk — DNB (and other banks) bundle it as a "green account" benefit, driving engagement with ESG investment products.

**Why it could be big:**
ESG investing in Norway grew 40% YoY. DNB Grønt Norge is already a flagship product. A tangible, emotional feedback loop converts abstract carbon guilt into daily action. If even 2% of DNB’s 1.4M mobile users subscribe, that is a NOK 300M+ ARR business.

**Why it fits DNB:**
DNB markets itself as the green bank. This product is retention glue for young, climate-conscious customers who might otherwise switch to neobanks. It directly drives AUM into DNB green funds and positions DNB as the *behavioral* leader in sustainable finance, not just the *product* leader.

---

### Favourite: HÅNDTRYKK

I pick **Håndtrykk** because it is the most *defensible* idea: the physical body is not a gimmick but a core part of the value proposition. In P2P transactions, neither party trusts the other’s phone — a neutral, tangible device solves a real coordination problem that no purely digital escrow can. It is immediately demoable, has a clear DNB/Vipps integration path, and targets an existing €30B pain point with zero established hardware competitors.

---

## 24–48 Hour Hackathon MVP Spec: HÅNDTRYKK

### Hardware Setup
- **Board:** ESP32-S3 (14-port board assumed for motor space).
- **Modules placed (8 of 14 ports used):**
  - `speaker` — ports [13,14] (adjacent pair at end).
  - `microphone` — ports [11,12].
  - `st7789_tft` — ports [1,2] (Big Screen, mounted facing the user).
  - `neopixel` — port [3].
  - `button` ×2 — ports [4, 5] (Party A commit, Party B commit).
  - `rotary_encoder` — ports [6,7] (mode selector + indicator ring).
  - `dc_motor` ×2 — ports [8, 9] (left and right track motors for a sliding gate; if only one sliding gate is needed, use one motor on port [8] and reserve [9]).
  - `aht20` — port [10].
  - `icm40608` — port (share I2C? No, each gets its own software bus, so any port is fine; not needed for MVP since tamper is stretch).
- **Mechanical:** A folded-cardboard or 3D-printed "lockbox" chassis with a single sliding compartment door driven by one DC motor. The second motor is \\"demo-only\\" and runs a ceremonial flag or second latch.
- **Power:** USB-C power module + power jack for motor rail if using high-torque setup; for lightweight cardboard demo, USB-powered DC motors suffice.

### Agent Architecture

**Model:**
- **Cloud LLM:** GPT-4o-mini or Claude Haiku via API, chosen for low latency and cost. Prompted as a *transaction-state enforcer* with a strict JSON schema for next actions.
- **On-device:** No LLM. State machine C++ firmware handles all real-time control; voice commands are streamed as 16 kHz PCM chunks to the cloud via Wi-Fi for STT + intent classification.

**Tools (MVP subset):**
1. `check_vipps_status(payment_id)` — cloud function polling mock Vipps API. Returns `{paid: bool, amount: int, buyer_phone: str}`.
2. `release_compartment()` / `lock_compartment()` — action commands sent from cloud to board.
3. `send_sms(phone, msg)` — Twilio mock for "Your item is ready for pickup."
4. `log_transaction(state, amount, timestamp)` — append-only JSON log.

**Memory:**
- **Short-term:** Board holds current `transaction_state` enum (`IDLE`, `BUYER_COMMITTED`, `PAID`, `RELEASED`, `DISPUTED`, `REFUNDED`) and `last_payment_id` in RAM. Survives only while powered.
- **Long-term:** Cloud-side SQLite or JSON file records all transactions for dispute resolution.

**Guardrails:**
- **Physical:** The compartment cannot open unless `state == PAID` AND the cloud sends a signed release command (a simple HMAC with a pre-shared key to prevent spoofing).
- **Financial:** Maximum transaction amount NOK 5,000 for MVP. Amount > NOK 1,000 requires BOTH parties to verbally confirm via microphone.
- **Time:** If payment is not confirmed within 10 minutes, auto-refund and return to `IDLE`.
- **Tamper (stretch):** If accelerometer detects shake during `BUYER_COMMITTED` or `PAID`, trigger `ALARM` state: speaker sounds a 880 Hz tone, LEDs flash red, and the compartment locks.

**Human-in-the-loop for money moves:**
- **Auto-approve:** Any amount ≤ NOK 1,000 with matching payment ID.
- **Require explicit confirmation:** Amount > NOK 1,000. Both parties must speak "Yes, confirm [amount]" within 30 seconds.
- **Refund:** Either party holds the rotary knob button for 3 seconds during `PAID` but before `RELEASED`. Cloud agent calls Vipps refund API. Refund only proceeds if both parties verbally agree or if the buyer initiates and seller does not object within 60 seconds.

### What Runs on the Board vs. Laptop/Cloud

| Layer | Runs On | Responsibility |
|---|---|---|
| **Sensor + Actuator Firmware** | ESP32 on-device | C++ state machine: button/encoder edge detection, motor PWM, LED colors, screen drawing (SVG-like QR-code rendering library), microphone PCM buffering, speaker playback. |
| **STT + Intent + Transaction Logic** | Laptop or cloud Wi-Fi endpoint | Receives 512-sample audio chunks over a TCP/WebSocket stream from the board; runs Whisper API or local Whisper.cpp for STT; parses intent ("I want to buy this bike for 3,000 kroner"); calculates next state; returns action JSON. |
| **Payment Orchestration** | Cloud function (AWS Lambda / local Flask) | Mocks Vipps API calls, verifies payment status, issues signed release tokens, sends SMS, logs transactions. |
| **Dashboard / Family View** | Optional: laptop browser | An optional HTML page showing current device status and transaction history (not needed for judge demo but nice for screenshots). |

### 3-Minute Judge Demo Script

**Minute 0:00–0:30 — The Setup**
Judge sees a small cardboard box with a sliding door, the Atech board perched on top, screen glowing blue. LEDs pulse slowly. Neopixel shows a single blue light. Speaker plays a friendly two-tone chime.

**Minute 0:30–1:15 — The Commitment**
- Seller (team member) rotates the knob to select "Sell Bike — NOK 2,500." Screen shows a QR code.
- Buyer (judge or second team member) scans the QR with their phone (mock payment link opens).
- Seller presses Button 1. Neopixel flashes yellow. Screen text: "Waiting for buyer..." Speaker: "Buyer, please confirm."
- Buyer presses Button 2. Neopixel shows two yellow lights. Speaker: "Payment of two thousand five hundred kroner initiated."

**Minute 1:15–2:00 — The Trust Moment**
- Team member triggers the mock "payment received" on the laptop (simulating Vipps webhook). Screen switches to a green unlock animation. Speaker plays a rising scale (C-E-G-C). Neopixel flashes green.
- Buyer says "Release item." Microphone picks it up; cloud STT confirms intent.
- DC motor whirs. The sliding door opens, revealing a small printed bike emoji (or a real granola bar — appetite permitting). LEDs go solid green. Speaker: "Transaction complete. Thank you for using Håndtrykk."

**Minute 2:00–2:45 — The Dispute**
- Reset. Buyer says "I changed my mind."
- Cloud detects refund intent before release. Screen turns red. Speaker: "Refund requested. Seller, do you agree?"
- Seller does nothing for 5 seconds (simulated objection timeout). Screen shows "Refund approved." Motor briefly rattles the locked door (showing it stays closed) then LEDs return to idle blue.

**Minute 2:45–3:00 — The Vision**
- Pitch the B2B angle: "This is the Stripe Terminal for second-hand deals. DNB + Vipps + Finn.no = a new trust layer for the informal economy, built in 36 hours."

### Riskiest Assumption and Hour-1 De-risk

**Riskiest assumption:** Buyers and sellers will trust a small DIY box with their money and goods more than they trust each other’s phones.

**Hour-1 de-risk plan:**
Before writing any firmware, do a **paper-prototype trust test**: 
1. Print a QR code and a cardboard sliding door.
2. Find a random team at the hackathon (or a mentor) and ask them: "Would you use this to buy a used bike from a stranger?"
3. Record their objections (size, looks, who holds it, what if it breaks).
4. If ≥3 out of 5 say "yes, if DNB backs it," we continue. If the dominant objection is physical security ("too easy to break"), we pivot Hour 2 to adding a second mechanical latch and a louder alarm. If the dominant objection is "I’d rather use the Finn.no app," we reconsider the value proposition.
This takes 45 minutes. The remaining 15 minutes of Hour 1 are spent sketching the revised mechanical design based on feedback.

---

## Wild Card: PENGELEN — The Literal Money-Dieting Tamagotchi

**Pitch:** You put Pengelen on your desk and feed it *only* by making extra debt payments via DNB. The more principal you pay down, the fatter and happier it gets. Take on new debt? It physically deflates — a DC motor-driven bellows slowly collapses, the screen shows a sobbing face, and the speaker plays a sad trombone "womp-womp."

**Agent perceives:** DNB loan balance API (daily pull), voice confessions ("I took another consumer loan"), accelerometer (shake it = you’re angry at your debt → it enters "rage workout" mode where it vibrates aggressively and screams motivational insults), knob (set monthly hunger target).

**Agent decides:** Whether you hit your hunger target, whether to guilt-trip you with synthesized voice clips, whether to auto-invest DNB savings-account round-ups into debt payments.

**Agent acts:** Motors inflate/deflate a rubber body; LEDs show hunger level; screen shows its face (mood); speaker plays emotional audio; physically dances when you hit a milestone.

**Why outrageous:** It gamifies *painful* financial discipline through literal physical suffering of an inanimate object. It turns loan amortization into pet care. It is viscerally absurd, emotionally manipulative, and weirdly effective — behavioral economists have shown that tangible, embodied feedback radically outperforms app notifications for habit formation. DNB could market it as the world’s first *somatosensory* banking product.

---

*Submitted by: Kimi-K2.6*
