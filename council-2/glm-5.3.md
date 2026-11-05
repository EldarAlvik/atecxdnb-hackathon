# Council round 2 — GLM-5.3: agent-native companies on the Atech kit

*Independence rule respected: this file is the only council file I read besides BRIEF.md. Everything below is grounded in the actual `atech` SDK, which I explored this session (`list-modules`, `info` on every module the team has, the shipped guide).*

## 0. What the SDK actually supports (verified, and it shapes every idea)

These facts decide what's buildable in 24–48h:

- **The board and a laptop are a natural agent loop.** The board streams sensor events as JSON lines; the laptop sends actions with `board.call()`, which blocks until the board confirms (`ActionRejected` if it can't do it). An LLM agent on the laptop literally cannot claim it acted when it didn't — the SDK's ack semantics are a free guardrail.
- **Real voice is feasible.** `speaker_play_pcm` accepts base64 16 kHz mono PCM in chunks (~3000 base64 chars per action) and each action confirms once its chunk has played — so laptop-side TTS streams naturally as sequential confirmed calls. The mic records up to ~3 s (`startRecording()`/`getRecording()`) — push-to-talk + Whisper STT is a small pipeline, not research.
- **Climate and motion stream for free:** AHT20 pushes temperature + humidity every 2 s; the IMU pushes shake spikes (>0.8 g), tilt and 6-way orientation every second, with a 100 Hz background cache for vibration signatures; the mic pushes RMS level every 250 ms.
- **Physical control is precise:** steppers do absolute microstep positioning (all moves blocking, no homing — reset at a known reference); DC motors take −255..255 with hardware PWM for the first four motors. Caution from the docs: **steppers need an external VMOT supply (12–24 V); without it, calls succeed but nothing moves** — the classic silent failure. Budget for a bench supply.
- **Rich physical input:** the rotary knob has 18 detents, a push button (with tap/double/long-press gestures) and a **12-LED NeoPixel ring** that firmware can drive; buttons detect tap/double/triple/long-press and V2.0 buttons have their own RGB light.
- **The screen is a real UI surface:** 320×240 (big screen) or 160×80, canvas mode, with shipped helpers (`drawCard`, `ListView`, `drawProgressBar`, `StatusMessage`, smooth fonts). But **screens accept no laptop actions** — the dashboard must be drawn by firmware, driven by custom `ui_*` actions the laptop sends (the guide documents the custom action/event pattern). Same for the stepper: no built-in laptop action, so `valve_close` must be a custom action handler in `code:` that calls `stepper.moveTo()` and confirms.
- **Wi-Fi/BLE exist on the ESP32-S3** and compile fine in custom firmware code (the SDK generates nothing for them, but the guide blesses HTTP/MQTT from your own `code:`). For the MVP the laptop is the bridge; board-direct cloud calls are the post-MVP path. Safety logic must live on-chip either way.

---

## 1. Three company ideas

### 1.1 DRYP — the agent that shuts the water before the insurer wakes up

**One-line pitch:** The first 60 seconds of a water leak, done by an agent: it detects, shuts the valve, dries, drafts the claim and books the plumber — insurance that costs less because it *starts* sooner.

**Problem & customer.** Water damage is the most expensive home-insurance category in Norway — industry figures put it at billions of kroner per year with average claims deep in six figures. The cost curve is pure time: a burst inlet discovered in 90 seconds is a mop and a gasket; discovered after a weekend it's a gutted bathroom and a rental apartment for three months. Worse: Norway has ~400k cabins that sit empty all winter with pipes that freeze and burst, sometimes running for *days* before anyone comes. Customers: homeowners with appliances, and the cabin owner — distributed through DNB Forsikring as a "claims-prevention kit".

**The agent's role — perceive → decide → act.**
- *Perceives:* humidity rate-of-rise (AHT20, 2 s cadence), water hiss (mic RMS pattern), appliance vibration signatures (IMU 100 Hz cache — it knows the washing machine's walk vs. the dishwasher's hum vs. silence), time of day, weather, presence (voice).
- *Decides on its own:* classifies the event (shower steam vs. washer overflow vs. pipe burst vs. rain through an open basement window), runs the escalation ladder, shuts the valve after an audible 10-second countdown, starts the drying fan, raises the incident flag, notifies the family on Vipps, drafts the claim with an estimate, monitors drying progress over the following days.
- *Asks the human:* "Is someone showering?" before deciding to shut; the actual claim filing (a physical signature); approving and paying for the emergency plumber; reopening the water — *never* done alone.
- *Tools it calls:* DNB claims API (file/update, saksnummer), a plumber marketplace API (availability, price, booking), Vipps alerts to family, weather API, its own learned home model (humidity baselines per hour-of-day, appliance signatures).
- *Where it runs:* **reflex arc on-chip in C++** — thresholds, valve control, alarm, latching; works with Wi-Fi and laptop dead. Perception/features on the laptop, reasoning + voice in a cloud LLM over Wi-Fi. Post-MVP: ESP32 Wi-Fi talks straight to a DNB edge service; the reflex arc never leaves the chip.

**Kit parts.** Stepper = the valve (hero actuator). 2 DC motors = drying fan + physical red incident flag. AHT20 taped at the "pipe joint". Mic = hiss detection + push-to-talk. Speaker = voice + alarm chord. Big screen = incident dashboard, claim draft, countdown. Knob = scroll the claim/plumber options; its 12-LED ring = ambient status (calm green ↔ amber countdown ↔ red incident). Buttons = red cancel / green sign. IMU = appliance signatures + tamper. Temperature also feeds a "frozen pipe risk" warning for the cabin tier.

**Business model.** Hardware (target NOK ~1,500) + NOK 49/mo agent subscription, bundled into DNB Forsikring policies as a premium-discount instrument (like alarm discounts). Insurer economics: on instrumented homes the target is a double-digit-percent cut in water-claims severity, because mitigation starts in minutes. Secondary: take-rate on plumber marketplace bookings, fleet risk data that reprices underwriting, a NOK 29/mo *hytte* tier for the winter-empty-cabin market. The same chassis extends to temperature (fire-adjacent), humidity (mold) and rental portfolios.

**Why it could be big.** Every Nordic insurer has the same loss curve, and prevention is a category no bank has owned. An agent that *physically acts* on the biggest claims category is infrastructure, not a gadget: insurers would underwrite its distribution the way they did smoke alarms. Cabin fleets, property managers and SMEs (hårfrisør/restaurant back rooms) are expansion markets.

**Why it fits DNB.** DNB sells insurance and holds the claims machinery; the agent becomes the front end of claims (filed in minute one, evidence pre-collected — photos, sensor trace, timeline). Sustainability: prevention beats repair — no rip-out, no rebuild, no waste. And it's a trust product no app can copy: *it acts on your home while you're not there.*

---

### 1.2 Bestevenn — the bank branch that lives in grandma's hallway

**One-line pitch:** A voice-first companion for Norway's seniors that catches the scam call while it's happening, reads bills aloud, and calls for help when she falls — the branch that closed, reopened as an object on her table.

**Problem & customer.** Branches are closing across Norway; the customers who lose most are seniors — the bank's most loyal, least app-native, and the #1 target of "Microsoft-support" and romance scams. One successful scam costs her savings *and* her trust in the bank, and the family watches helplessly from Vipps screenshots. Customers: seniors 70+ and their adult children — a two-sided sale where the children pay.

**The agent's role.**
- *Perceives:* voice (push-to-talk button → 3 s recording → STT), IMU (a fall is sustained tilt + stillness; a tap is "I'm here"), button presses, calendar/due dates.
- *Decides on its own:* recognizes scam patterns in whatever she just experienced ("someone from the bank asked me to move money to a safe account" → it calmly explains no bank will ever ask, and proposes a **48-hour cooling-off lock** on outbound transfers); fall detection with an escalation ladder (spoken check-in → call daughter → alarm); flags anomalous bills and Vipps requests; reads due dates aloud, one thing at a time.
- *Asks the human:* activating any lock (her long-press on the physical button — the signature), any payment ever, and "shall I call your daughter?" before involving family. It never alerts family behind her back except in a fall emergency.
- *Tools:* DNB open-banking (accounts, payee flags, temporary transfer lock), Vipps (family alerts), calendar, one-tap handoff to the fraud line.
- *Where it runs:* fall alarm and cancel logic **on-chip** (must work offline); STT/reasoning/TTS cloud over Wi-Fi; family alerts from the laptop/API.

**Kit parts.** Mic + speaker (voice-first interface — no login, no menus). Big screen: one card at a time, huge smooth-font text. Knob to scroll (no mistouches; the 12-LED ring as a gentle "trust dial"). Buttons: TALK / HELP / CANCEL. IMU: falls, and the knock-tap easter egg. Motors: a **physical red flag that rises on the shelf** when a scam is suspected (visible to family visiting), a small waving hand for greeting, and a **motorized shutter that physically covers the mic hole in privacy mode** — privacy you can see. LEDs: state at a glance.

**Business model.** NOK 99/mo "family safety" tier that covers grandma's and the children's accounts; hardware subsidized inside DNB's premium/senior packages; municipality welfare-tech procurement (trygghetsteknologi is a national priority area with real budgets); insurer cross-sell on falls.

**Why it could be big.** Aging Norway, welfare-tech budgets, and *every* bank's vulnerable-customer problem. Unique two-sided acquisition: the senior and her children, who inherit the family's finances, in the same sale. The branch-closure story flips from a reputational liability into "the branch came home."

**Why it fits DNB.** Trust as a product; fraud losses down; a concrete answer to regulator expectations on vulnerable customers; and the most emotionally resonant brand story a Norwegian bank could tell at exactly the moment its branch narrative is under pressure.

---

### 1.3 Ukepeng — the shelf robot that turns allowance into a ritual

**One-line pitch:** The family money agent with a body: kids split their weekly allowance by turning a real dial, watch a real arm move it between SPEND / SAVE / GIVE, and get coached by an agent that knows their goals — while every kroner still needs a parent's Vipps approval.

**Problem & customer.** Cash disappeared and allowance became an invisible Vipps number; kids never *feel* scarcity, waiting or trade-offs, and personal finance is now in the national curriculum with no tool families actually use. Parents end up as the nag-in-the-middle. Customers: families with kids 6–14; buyer = parents, user = kids.

**The agent's role.**
- *Perceives:* knob turns (the allocation), kid/parent button presses, voice ("I want the bike from Sportslageret"), shakes (piggy-bank shake easter egg), even room temperature (sustainability mini-game: it notices 25 °C by an open window and prices it).
- *Decides on its own:* the coaching (projections, save-streak challenges, "put in 30 this week and the bike lands by your birthday"), goal price lookups and price alerts, celebration choreography, the weekly report it sends to parents.
- *Asks the human:* every money move — the kid proposes a split, the agent counters, the parent approves the Vipps request. Rule changes (raise allowance, edit goals) go through the parent key.
- *Tools:* Vipps pocket-money API, DNB youth savings account, price lookups for goals, calendar (birthdays as deadlines), a classroom mode for schools.
- *Where it runs:* ritual + celebration reflexes **on-chip** (works offline); coaching/voice in the cloud.

**Kit parts.** Knob = the allocation dial. Four motors: an allocation arm that physically slides beads along three SPEND/SAVE/GIVE tracks, a celebration carousel, a goal flag. Screen = goals and faces. Speaker = coach voice + fanfare. Buttons = kid key / parent key. IMU = shake. LEDs = progress. Temperature = the mini-game.

**Business model.** Hardware + NOK 39/mo family tier inside DNB family banking; retailer partnerships on goal price-alerts; a schools program (CSR + acquisition). The real product: a customer acquired at 7 instead of at 27.

**Why it could be big.** ~1M Norwegian kids in the target age, and banks globally fight for Gen-Z at 27 instead of at 7. Physical ritual creates habit and brand love that apps churn out of; Vipps culture makes it natively Norwegian and Nordic-exportable.

**Why it fits DNB.** Youth program, Vipps, curriculum alignment, "bedre med penger" as a brand promise — and it demos *viscerally*: motors spinning a celebration is the stuff judges film.

---

## 2. My pick and the 24–48h hackathon MVP: **DRYP**

**Why this one.** All three ideas have bodies, but only DRYP's body *acts on the world causally* — turning a valve is something no app can do, and the act is worth six figures. The business case is the tightest (insurance has a quantifiable loss curve), the demo is the most visceral (water, a spinning wheel, a signed claim), and with this kit the entire loop runs on confirmed SDK actions within 48 hours. Bonus: the chassis is ~80% of Bestevenn (voice + IMU + screen), so the team can pivot at hour 24 without new hardware.

### 2.1 Hardware setup

Mechanical mock-up: a painted PVC pipe section on a board, with a 3D-printed valve wheel on the stepper shaft at a visible "joint"; a wet sponge at the joint as the leak trigger (tip a cup of water on it); the AHT20 taped right at the joint. Bring a **12–24 V bench supply for VMOT** — without it the stepper silently does nothing.

Module plan (14-port board; `atech validate` arbitrates final placement):

```
modules:
  - {id: microphone,      instance: mic,     ports: [1, 2]}   # hiss detection + push-to-talk (hold red button)
  - {id: speaker,         instance: spk,     ports: [3, 4]}   # agent voice (PCM chunks) + alarm chord
  - {id: st7789_tft,      instance: ui,      ports: [5, 6]}   # incident dashboard, claim draft, countdown
  - {id: rotary_encoder,  instance: knob,    ports: [7, 8]}   # scroll options; push = select; ring = status
  - {id: stepper_motor,   instance: valve,   ports: [9,10]}   # THE VALVE (external VMOT!)
  - {id: aht20,           instance: th,      port: 11}        # leak sensor at the "joint"
  - {id: icm40608,        instance: imu,     port: 12}        # appliance signatures + tamper
  - {id: button,          instance: red,     port: 13}        # cancel / push-to-talk / privacy
  - {id: button,          instance: green,   port: 14}        # signature (long-press = sign)
```

If the team's four motors are DC rather than stepper: valve = DC with a timed quarter-turn on a big visible wheel (a mock valve doesn't need absolute position), freeing two ports for the flag motor and fan; if ports run out, the fan is the first casualty (it's cosmetic in a demo). V2.0 buttons' RGB lights double as status lights; a NeoPixel 3×3 grid, if present, slots in as a state bar.

Firmware responsibilities (all in `code:`, non-blocking `EveryTimer` patterns, no `delay()`): sensor cadence, the reflex arc, a custom `ui_show` action that renders a card/list/progress bar from a JSON payload (screens have no built-in laptop actions), custom `valve_close`/`valve_open` actions (stepper has no built-in actions), fan/flag/LED state actions, and a custom `rec_clip` event that ships the push-to-talk recording as base64.

### 2.2 Agent architecture

**Three layers:**

1. **Reflex arc — on-chip, works offline.** Rolling ΔRH/min from the 2 s AHT20 events; `LEAK_SUSPECT` on threshold breach for two consecutive samples; if the laptop is absent for >60 s during a confirmed event, the chip closes the valve anyway (safety beats connectivity). Valve-closed is latching: firmware reopens only on the `valve_open` action. Alarm chord + red LED + ring pattern are pure firmware.
2. **Perception & orchestration — laptop (Python).** `Board.connect()`; consume `board.events()` for `th_temperature`/`th_humidity`, IMU shake/tilt, mic level; feature extraction (humidity trajectory, mic variance, IMU energy window). Push-to-talk: red button down → `mic.startRecording()` → release → ship `rec_clip` → Whisper (nb-NO). Voice out: agent reply → TTS (16 kHz mono WAV) → ~150 ms PCM chunks → sequential `speaker_play_pcm` calls (each confirms when its chunk has played → continuous speech). Board actions dispatched with `board.call()` — ack or `ActionRejected`, nothing in between.
3. **Reasoning — cloud LLM over Wi-Fi.** Any strong tool-calling model; persona "Vaktmesteren" — calm, dry humour, fact-forward. The escalation *policy* lives in the system prompt and, more importantly, in the tool layer:
   - `shut_valve(reason)` → firmware action; allowed autonomously inside policy (after the spoken countdown).
   - `start_fan()`, `raise_flag()` → firmware actions.
   - `ask(text, options, timeout)` → screen card + voice; the knob selects.
   - `draft_claim(cause, estimate, evidence)` → mock DNB Forsikring API; returns `draft_id` + adjuster estimate.
   - `file_claim(draft_id)` → **requires a green long-press within a window**; the tool otherwise returns `needs_signature`. Enforced in code, not in the LLM's politeness.
   - `book_plumber(slot)` → mock supplier API; requires explicit approval.
   - `notify_family(msg)` → mock Vipps; autonomous.
   - `weather()`, `recall(incident)` → context and memory.

**Memory.** On-chip `Preferences`/LittleFS: hour-of-day humidity baseline (learned during setup) + incident diary. Laptop SQLite: full event log. The agent's "home model" in prompt context: appliance vibration signatures, plumbing notes, past incidents — seeded with a fake two-week history so the demo agent has something to remember.

**Guardrails.** (1) **Two-lane authority:** physical-safety lane (valve/fan/alarm) is autonomous inside policy; the money lane (claims, bookings, payments) is human-signature-only, gated in the tool layer. (2) **Announce-then-act:** any autonomous valve close has a 10 s spoken countdown, red pulsing LED, and a cancel window — the flight-attendant rule. (3) **Template layer:** claim text is assembled from structured tool outputs; the LLM cannot invent amounts, policy numbers or saksnummer; numbers come only from tools. (4) **Signature is physical:** money actions need a 700 ms long-press on green (the SDK's gesture API detects it for free). (5) **Reopen needs a human:** `valve_open` is never called by the agent unprompted. (6) **Offline determinism:** if the LLM or Wi-Fi stalls, Layer 0 rules alone still protect the house.

**Board vs laptop vs cloud:** board = sensing, thresholds, actuation, UI rendering, voice playback, latching; laptop = event ingestion, features, STT/TTS pipelines, agent loop, tool dispatch, mock APIs (DNB claims, plumber, Vipps), SQLite; cloud = LLM + TTS/STT. Post-MVP path: the guide blesses Wi-Fi in custom firmware, so board-direct HTTPS to a DNB edge service removes the laptop; the reflex arc stays on-chip forever.

### 2.3 Three-minute judge demo script

- **0:00** — Idle state. Screen: "ALT TØRT · 21.4 °C · 41 % RH", ring calm green. Laptop screen behind shows the live 2 s event stream (`atech monitor --json`). "This is DRYP. It's watching a pipe joint under a sink."
- **0:20** — Tip the cup onto the sponge at the "joint". Ring goes amber; DRYP speaks: *"Fuktigheten stiger fort under vasken. Er det noen som dusjer? Jeg venter 30 sekunder."* (Perceive → ask.)
- **0:50** — No answer; humidity still climbing, mic hears hiss, IMU says the washer is off. DRYP: *"Dette ser ut som en lekkasje. Jeg stenger vannet om ti sekunder — trykk rød for å avbryte."* Countdown on screen, red LED pulsing. **Stepper visibly turns the wheel 90°.** Alarm chord; flag motor raises the red flag. Screen: "VANNET STENGT · 14:32".
- **1:20** — DRYP: *"Vannet er av. Jeg tror det er tilføringsrøret på vaskemaskinen — vibrasjonssignaturen mangler i dag. Jeg har laget et kravutkast til DNB Forsikring: estimert 12 400 kr. Skroll med knotten."* Knob scrolls the claim card on screen.
- **1:40** — Presenter taps green once — DRYP refuses: *"Jeg trenger signaturen din."* Long-press: *"Signert. Kravet er sendt — sak 8471, takstmann kontakter deg innen i morgen."* (Guardrail demonstrated, not just claimed.)
- **2:00** — DRYP: *"Jeg fant en nød-rørlegger: RørBlixt, i morgen 08:00, 1 150 kr. Godkjenne?"* Knob to "Godkjenn", push: booked. *"Varslet familien på Vipps."*
- **2:20** — Wrap: what it decided alone (classified, shut, drafted, notified) vs. what needed a human (claim, booking); the money rule ("signatur, aldri autopilot"); the numbers ("caught in 90 seconds: a NOK 2 000 repair instead of a NOK 150 000+ claim"); the offline rule ("pull the USB — the reflex arc still shuts the valve").
- **2:50** — Closing hook: *"The same box watches 400 000 Norwegian cabins all winter."* → premium-discount business model, fleet data to risk models. Q&A.

### 2.4 Riskiest assumption and the hour-1 de-risk

**The risk:** that humidity alone separates a leak from ordinary life (shower, cooking, laundry) *fast enough to act on*. Everything else is plumbing (of the software kind).

**Hour 1 — the wet-sponge test.** Stream `atech monitor --json`, record 120 s of baseline, then press a wet cloth/sponge to the AHT20 and log the trajectory. Compute ΔRH/min vs. the baseline noise floor; pick the trigger as ~4× noise sustained over two consecutive 2 s samples. If RH is too slow or flat at demo scale, OR in the mic RMS channel (water hiss; level events already stream every 250 ms). Decision within the hour: the final trigger expression, written down.

Same hour, two more silent-failure checks: **(a)** voice out — one TTS sentence → PCM chunks → speaker; verify the chunk cadence is gapless (each call confirms when played, so just enlarge chunks to ~150–250 ms if gaps appear); **(b)** stepper torque — bench supply on VMOT, quarter-turn on the printed wheel (per the SDK docs, missing VMOT looks exactly like success).

**Rough build order:** hour 1 de-risk → firmware skeleton (sensors + UI renderer + valve action; the default feedback build already proves all inputs in `atech check`) → laptop agent loop + mock APIs → TTS/STT pipeline → policy + tools → demo script rehearsal with a stopwatch. Two people on firmware/mechanics, two on agent/pipelines, one on demo script and mocks.

---

## 3. Wild card: GRIS — the piggy bank with its own bank account (and its own overdraft)

**One-line pitch:** A financial Tamagotchi for adults whose health bar is your savings rate — and when you don't feed it, it doesn't just die: it *survives*, taking micro-overdrafts from DNB in its own name, and you inherit the pig's debt.

**The concept.** GRIS lives on your shelf. Fed via Vipps, it hums around (four motors = legs, neck, a little tail), chirps through the speaker, shows its face on the screen. Its health is literally your savings rate: skip a week of round-ups and it visibly slumps, LEDs go red, it plays a dying RTTTL waltz, and — the twist — the agent that *is* the pig reads out your last three impulse purchases in a small, hurt voice, itemized. Then it "borrows" to survive, and you get a squeaky itemized debt notice: you don't just owe the bank, you owe the pig. Save again and there's a full revival ceremony: carousel spin, fanfare, rainbow ring. Compliance would never, ever allow a pet with credit — which is exactly why no bank has tried the one commitment device behavioral economics says actually works: consequences you can *hear*.

**The agent's role.** It's the pet's brain: a full persona LLM that reads your transaction feed (read-only), sends Vipps payment requests to you, knows it's payday (calendar), buys itself a tiny umbrella with your money when it rains (weather), listens (mic: it sighs audibly when you say "jeg skal bare…"), and knows when you pick it up (IMU) — and puts itself back down, dejected. It decides alone: its mood, its commentary, its guilt-trips, its little life. It asks a human: everything that moves money — feeding is *always* your Vipps approval.

**Kit parts.** Four motors (slump, walk, play-dead drama), speaker (squeaky voice + death rattle), mic (it hears your excuses), IMU (pickup detection), screen (face + health bar), knob (feed/walk/play dial), buttons (feed / play / confess), LEDs (mood), even the temperature sensor (it gets cold and *prices your heating* while it's at it).

**DNB angle (the joke that's real).** The pet's "overdraft" is actually your spare-change buffer, and the revival fee lands in your own savings account — GRIS is a commitment device wearing a debt costume. Gen-Z savings gamification with the most-photographed fintech object at Innovation Week. Pitch line: *Tamagotchi meets Vipps meets overdraft.*
