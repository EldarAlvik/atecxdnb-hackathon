# Three agent-native companies on the Atech kit

*Council round 2 — deepseek-v4.1*

**Thesis.** The kit is not a sensor board. It is a *body*: a thing with its own clock, in one specific room, that can hear, speak, notice air and motion, be turned by a hand, and push a motor. Every idea below starts from the same question — **what should a bank's body be for?** — and answers it differently: a place where a human says yes to money (Fullmakt), a place where risk is physically sensed (Holdbar), a place where money is physically made (Bua).

| | Who pays | The agent's job | The kit's role | Risk |
|---|---|---|---|---|
| **Fullmakt** | SMBs, then households | Spend within a mandate; negotiate; refuse itself | Trusted display + trusted gesture + trusted clock | Adoption: is hardware consent *necessary*? |
| **Holdbar** | Small food businesses | Keep food legal; price the loss; build a credit record | The sensor is *inside the fridge* | Sales: compliance is cheap, hardware isn't |
| **Bua** | Clubs, markets, farm gates | Sell, price, call orders, close the books | It drives to the customer | Hardware: 4WD chassis in 48h |

**My pick: Fullmakt.** It is the only one of the three where the hardware is not a nicer interface but an *irreducible part of the trust model*, and the only one whose demo has a punchline a judge can verify in thirty seconds.

---

## 1. Fullmakt — the human-intent security module for agents that spend money

> **One line:** the agent does the work, physics gives the permission, and the bank can prove which human saw what and said yes.

### The problem
Software is starting to buy things. Renewal agents, procurement copilots, "find me a cheaper insurer" assistants — the models are ready; the *authorisation* is not. Today there are exactly two options, and both are broken:

1. **Give the agent a card.** One prompt injection from an invoice PDF and it's gone. Nobody sane does this with a business account.
2. **Make a human tap "Approve" in an app.** This is what every company does now, and it is precisely the vector that invoice fraud and CEO fraud live in. The approval is rendered by the same phone, the same OS, the same browser and often the same session as the thing being approved. It is a rubber stamp with a UI.

The result: agents are being built with read-only access and a "human will click it" step, which means the agent saves you the thinking and none of the work. And DNB's structural fear — being a dumb pipe underneath someone else's agent — is exactly this layer.

### The product
A device that sits on the desk of the person who owns the money. It is deliberately stupid: it cannot be talked into anything, because it cannot be talked to. It does four things, and nothing else:

- **Shows** the proposal on a display whose frame is hashed and signed — *trusted display*.
- **Takes** the consent as a physical gesture whose completion is signed — *trusted gesture*.
- **Counts** with a monotonic counter that survives reboots — *trusted clock*.
- **Refuses**, with a motor that physically locks the knob, when the mandate doesn't allow it — *trusted no*.

The token it mints is over `(proposal_id, amount, payee, frame_hash, gesture, counter, timestamp)`. The bank moves money only on a valid token. The agent can *relay* that token. It can never *mint* one.

### The agent's role

**It perceives.** Not the weather — the room. Knob detents (how far a human actually turned), buttons (hold / why / kill), the microphone (the owner saying "hvorfor er dette dyrere?" and "nei"), the accelerometer (the device was picked up or moved — which voids a live mandate). Plus its own generated sense of time, which is the one thing a phone app never has on behalf of a counterparty.

**It decides alone:**
- Maintains a rolling 90-day cost model per supplier and finds the creep — the insurance that went up 11%, the two overlapping SaaS seats, the "support" line item that appeared.
- Negotiates: drafts and sends the renewal email, gets three quotes, compares against the contract it has indexed.
- **Sets its own friction.** Each proposal carries a risk weight, and the weight sets how many detents the human must turn. Cheap recurring bill, in-mandate: it doesn't ask at all. New beneficiary, changed account number, amount above the mandate: maximum friction, and the knob won't turn until the checks pass.
- **Vetoes itself.** It can decline to propose: a duplicate invoice, an unpriced clause, a mismatch it can't resolve. The most important agent action in this product is *not asking*.
- Decides when a discrepancy needs an outside fact (registry lookup, prior invoice, contract clause) before a human is even bothered.

**It asks the human about:** anything above the mandate floor, any new beneficiary, any annual or irreversible commitment, any change to the mandate itself, and any proposal it cannot cite a source for.

**It calls:** read-only PSD2 account information (balances, transactions); `propose_payment` (creates a *proposal object*, not a payment); the mandate service (limits, allowlist, velocity, cooling-off); Enhetsregisteret's open API for org-number verification (`data.brreg.no/enhetsregisteret/api/enheter/<orgnr>` — real, free, returns name, address, employees, and a `konkurs` flag); IMAP search over the invoice inbox; a vector index over the firm's contracts; supplier email; calendar (cash-flow timing); the audit ledger.

**Where it runs:** the reasoning is a cloud LLM with tool use and JSON-schema-constrained proposals. The device runs a presentation state machine, the gesture, and HMAC. **No model on the board, by design** — a trust anchor that can be persuaded is not a trust anchor.

### Why the body beats a phone app
1. **Separation of trust domains.** The agent that proposes must not live in the same domain as the thing that approves. A phone cannot provide this, because the agent, the browser, the invoice PDF and the approval all share it. This device has no inbound channel except signed mandate updates.
2. **Proof of what was displayed.** The signed payload contains a hash of the exact framebuffer the human saw. If someone disputes "the screen said 4 840", the bank can prove what it said. No app can make that claim, because the app is the thing that could be lying.
3. **Presence and dual control.** Corporate money needs two people. Two physical objects that must both seal is a governance primitive; a phone can be handed across a table, a bolted desk object cannot.
4. **Bandwidth of doubt.** A screen flattens risk into text, and text gets skimmed — that is the whole failure mode of approval UIs. Effort is a channel: the agent can make approval cost more exactly when it is less sure — more detents, a lock that will not release, in the production version a knob that physically drags. You cannot skim a knob that fights you.
5. **It is a shared object.** It sits in the open; the ring goes amber and the whole office sees it. A notification is private and ignorable. A thing on the desk creates social accountability.

Banks already ship physical authenticators — code cards, Digipass, corporate tokens — because they worked out decades ago that a phone is the wrong trust domain. This is the same argument, extended to agents.

### Business model
- **Device:** 249 kr + 39 kr/mo, or free on a business account. Hardware is the moat, not the margin.
- **Flow:** 8–15 bps on agent-authorised volume. For scale: EU interchange is capped at 0.20% for debit, so 10 bps is half a debit card and still vastly more than a bank transfer earns.
- **Enterprise AP:** per-seat SaaS for the two-person, multi-mandate version.
- **The real prize:** license the mandate/attestation protocol to other banks. "BankID proves who you are. Fullmakt proves what you allowed." Whoever owns that primitive owns agentic payment flow.

### Why it could be big
Every agent that can spend will need an authorisation primitive a human can trust, and there will be one or two of them, not twenty — authority is a natural monopoly. This is a category, not a feature. It also gets more valuable as models get more capable: the more an agent can do, the more the constraint is worth.

### Why it fits DNB
DNB already owns the two pieces this needs: **BankID** (the national eID, i.e. the identity layer) and **Vipps** (co-owned by DNB, i.e. the payment layer), plus the corporate relationships that would buy it on day one. Fullmakt is the third layer, and it is the one that keeps DNB in the middle of agentic commerce instead of underneath it. It is also a natural wedge into SMB accounts payable, which is where the switching costs live.

---

## 2. Holdbar — the agent in the cold room

> **One line:** it keeps the food legal, the insurance claim honest, and the loan cheap.

### The problem
Every food business in Norway has a legal duty to run *internkontroll*: temperature logs, deviation reports, HACCP documentation. In practice it is a paper form filled in at 16:00 with this morning's numbers, because the person doing it is also frying something. Then a compressor dies on a Friday night, 40 000 kr of stock is lost, the kitchen closes, and the insurance claim becomes an argument about a form that was filled in by hand.

The customer: the small kitchen — café, canteen, bakery, catering kitchen, food truck, supermarket deli, farm shop. Well over half a million enterprises in Norway, and the food ones are the ones with a legal logging duty and a real loss event.

### The agent's role
**It perceives** the actual air, in the actual cold room: temperature and humidity every few seconds, the sound of the compressor (a failing one has a signature, and a door left open sounds like a room), the accelerometer (door slams, equipment vibration, someone moved the unit), buttons for staff attestation ("sjekket", "kastet"), the knob to scroll the discard list.

**It decides alone:** classifies an excursion instead of just alarming on a threshold — duration, thermal mass, product category, whether the door was open. "22 minutes to 7.4 °C with the door closed: the ham is fine, the milk isn't." Prices the loss from the day's takings. Prepares the claim with a verified timeline. Compiles the monthly documentation. Dispatches a technician above a spend threshold. Stays quiet below it, because an alarm that cries wolf gets unplugged.

**It acts:** a spoken instruction in Norwegian ("lukk døra, kast melka"), an alarm tone that cuts through a kitchen, the log and the deviation on the screen, a green or red status you can read from the pass, and a motor that flips a physical flag so the state is visible to someone who is not looking at a screen.

**It asks the human about:** the discard decision — food-safety liability needs a human signature, given physically on the device — and the technician spend, and filing the claim.

**It calls:** Mattilsynet-facing documentation export, supplier/technician booking, DNB's insurance claim endpoint, accounting, Vipps takings (to value the stock at risk), a weather API (a 30 °C day explains the excursion), and DNB's underwriting endpoint.

**Where it runs:** the board senses, logs, renders and buffers 30 days locally (kitchens have bad WiFi); the cloud agent does the reasoning; the laptop is only a development bridge.

**Kit parts:** `aht20` (temperature and humidity — the whole point), `microphone` (compressor health and door-open sound), `icm40608` (door slams, someone moving the unit), `rotary_encoder` (scroll the discard list, press to attest), `st7735_tft` (the log, the deviation, the signature), `speaker`, one `button`, and one `dc_motor` for the physical flag. That is 12/12 ports — this idea also cannot take four motors, which is why the motors argue for Bua.

### Why the body beats a phone app
It has to be at 4 °C, where phones fog, freeze and get left on the counter. The staff have gloves on. Nobody is looking at a screen. The alarm has to be audible across a kitchen, and the sensor has to be *in* the fridge — a phone app cannot log a temperature it never sees. This is the least arguable "why hardware" of the three, and the weakest "why motors" (a pop-up flag is about all it earns; the panel is honest about that).

### Business model
399 kr/mo per site — compliance is a must-have and cheap next to a closed kitchen. Then insurance commission on business-interruption cover priced with the data. Then the real prize: **verified operations as a credit asset.** Six months of uninterrupted cold chain, opening hours and takings is a better credit signal for a two-year-old catering firm than a tax return, and DNB is the lender that would hold it.

### Why it could be big
It becomes the operating system of the small kitchen: compliance → insurance → payments → lending. Each step is a bigger revenue pool than the last, and each one is unlocked by the same sensor.

### Why it fits DNB
Insurance (DNB Forsikring) plus SMB lending plus the account relationship. No one else can price the risk with the data they would own. This is the idea that most directly turns a gadget into *underwriting advantage*, which is what a bank actually monetises.

---

## 3. Bua — the agent that is a shop

> **One line:** it drives to the queue, prices what it sells, calls the order, takes the Vipps payment, and hands the treasurer finished books.

### The problem
Selling things in the physical world still requires a till, a card reader, a phone and an adult. So an enormous amount of commerce simply doesn't happen: the football club selling waffles at a tournament with a cash box and a Vipps number written on cardboard; the farm gate with an honesty box; the flea market; the office pop-up; the festival stall. The seller is a twelve-year-old volunteer or a tired parent, and the treasurer loses a Sunday reconstructing the day from a paper tally. Norway has roughly twelve thousand sports clubs alone, and the voluntary sector runs on exactly this.

### The agent's role
**It perceives** the crowd (microphone RMS — where the people are), what is being taken (the knob, and a press to confirm), motion (accelerometer: it has been picked up, it is stuck, wheel slip — and crude odometry, counting wheel revolutions to know where it is), and the fixture list from the calendar. Port honesty again: four motors, a knob, a screen and a speaker spend ten of the twelve ports, so the driving build has **two ports left** — microphone, or climate sensor plus accelerometer, or climate sensor plus a button. The demo build takes the microphone; a counter-top version takes the other two.

**Kit parts:** four `dc_motor` (drive), `rotary_encoder` (choose item and quantity, press to confirm), `st7735_tft` (menu and payment), `speaker` (calling orders), plus two of `{microphone, aht20, icm40608, button}`.

**It decides alone:** where to go next (the loudest corner of the pitch), dynamic pricing near the end (half price at 17:00 beats throwing it away — and it's a sustainability story), when to stop selling, when to upsell, which items to restock from the wholesaler, and the day's reconciliation. It detects its own fraud cases: a payment that doesn't match an order, a refund pattern that doesn't make sense.

**It asks the human about:** price changes outside a band, refunds, opening and closing the till, anything over 500 kr.

**It calls:** Vipps eCom (payment requests plus webhooks), inventory and supplier ordering, weather, calendar, club accounting export, and Frivillighetsregisteret to verify the club.

**Where it runs:** drive, sensing and payment display on the board; pricing and demand reasoning in the cloud; the local ledger on the board so a dropped WiFi connection doesn't lose a sale.

### Why the body beats a phone app
It is at the crowd instead of in a pocket. It calls "ordre 42" across a noisy pitch — a phone cannot. A volunteer with batter on their hands can operate a knob and a big button. It moves to the queue instead of waiting for the queue to find it. This is the idea where the four motors are genuinely load-bearing, and the only one where the *body* is the product rather than the trust anchor.

### Business model
1.2% of GMV plus 399 kr/mo, sold through clubs (a treasurer who gets their Sunday back is the best salesperson you can hire). Then any pop-up retail. The endgame is agent-run retail as a category — vending machines are a large, stagnant, cash-era business that has never had dynamic pricing or a sales assistant.

### Why it could be big
It drops the cost of running a point of sale to near zero, and then lets software do the selling. That expands the set of places where commerce can happen, which is a bigger prize than taking share in the places it already does.

### Why it fits DNB
Vipps needs merchant acceptance beyond e-commerce, and micro-merchants are the segment nobody can afford to acquire by hand — a device can. A club that takes its first 20 000 kr through Bua is a DNB business customer with a treasurer, a board and a bank account.

---

## The pick: Fullmakt, and the 48-hour MVP

### Why this one
Holdbar is the most sellable and Bua is the most fun, but Fullmakt is the only one where the agent and the hardware are doing *the same job from two sides*: the agent expands what can be delegated, the device bounds it. That is the thing banks will actually have to buy, and it is the thing this kit can genuinely demonstrate — because the demo's central claim ("the agent cannot pay by itself") is a thirty-second, checkable fact rather than a story about the future.

### Hardware setup (validated placement, 14-port board)

The kit has twelve usable ports on the 14-port board, and the full sensor set plus four motors does not fit. I checked this with `atech validate` rather than guessing; the build below is 12/12:

| Port(s) | Module | Job |
|---|---|---|
| 1, 2 | `rotary_encoder` | The consent gesture. Its 12-LED ring is a free physical progress bar: we drive it with `setRingPosition()` so it shows *our* count of detents, not the raw knob position, and the ring visibly closes as you turn. |
| 3, 4 | `st7735_tft` 160×80 | The proposal card: amount, payee, three checks, countdown. (If you have the Big Screen, `st7789_tft` 320×240 is the same API and worth the room.) |
| 5, 6 | `speaker` | It speaks the proposal out loud. |
| 9, 10 | `microphone` | Push-to-talk: "hvorfor er dette dyrere?" |
| 7 | `dc_motor` | **The pawl.** A cam that swings an arm into the knob's detent path. This is the physical *no*. |
| 11, 13, 14 | `button` ×3 | Hold · Hvorfor? · Kill. Their RGB lights are the "three LEDs" — green/amber/red state, visible across a desk. |

The trade-off is real and worth saying out loud to judges: **with the microphone in, there is exactly one port left for a motor.** The three-motor variant (pawl + budget gauge + stamp) also validates at 11/12 if you drop the microphone — that is the build to switch to if voice-in turns out to be flaky. The four motors belong to Bua, and saying so is more convincing than pretending you can have everything.

Mechanics: the pawl is a 3D-printed arm on the motor shaft. Drive it at PWM ~120 for 300 ms, then `brake()`; do not leave it powered against the stop. If the whole mechanism fails, the fallback is the ring turning red and the device refusing to seal — the demo survives, it just loses its click.

### The agent architecture

```
        cloud                      laptop                       board
  ┌───────────────┐        ┌──────────────────┐        ┌──────────────────┐
  │ planner LLM   │ tools  │ bridge.py        │  USB   │ state machine    │
  │ invoice parse │◄──────►│ policy engine    │◄──────►│ trusted display  │
  └──────┬────────┘        │ mandate service  │  JSON  │ trusted gesture  │
         │ AIS, read-only  │ (FastAPI+SQLite) │        │ HMAC + counter   │
         ▼                 └───┬──────────▲───┘        └────────┬─────────┘
   mock bank ledger ◄──────────┘          └──── /attest, WiFi ──┘
                        money moves only on a valid device seal
```

The laptop can *relay* a seal over USB if the venue WiFi dies. It can never *mint* one — and the only arrow that moves money comes out of the mandate service, which is the only thing that checks a seal.

- **Model:** a frontier model for the planner with tool use; a cheap model for invoice extraction and classification. Proposals are JSON-schema-constrained, so the agent cannot emit a "just pay it" fudge.
- **Board vs laptop vs cloud:** the board owns presentation, gesture, the frame hash, the HMAC and the counter. The laptop owns the bridge, the policy engine and the demo. The cloud owns reasoning and negotiation. Bias all failure handling so the device is dumb and the server is strict.
- **Memory:** SQLite. `counterparties` (contract terms, the last three invoices, who approved them, prior account numbers), `proposals`, `mandate`, `audit` (hash-chained), `preferences` learned from the human ("never before 09:00", "always call the plumber first"). Contracts and email indexed for retrieval — **no proposal without a citation.**
- **Signing:** MVP uses an HMAC-SHA256 key in `Preferences` plus a monotonic counter; production moves the key into the S3's eFuse-backed HMAC peripheral with secure boot and flash encryption. Say this out loud; it shows you know the difference between a demo and a product.

### Guardrails for money moves
1. The agent holds **no payment credential** — read-only account information plus a proposal tool. It cannot pay, structurally.
2. Limits live **server-side in the mandate**: per-transaction ceiling, monthly ceiling, beneficiary allowlist, category allowlist, velocity.
3. **New beneficiary** → always human, always full 12/12, always a registry check against the registered address.
4. **Above the ceiling** → dial-through *plus* a second device (two-person). For a sole trader, an explicit override with a 60-second cooling-off shown as a countdown on screen.
5. The agent's risk score sets **friction only**. The policy engine sets **limits**. An agent must never be able to raise its own limit, however persuasive its reasoning.
6. **Citation rule:** invoice id, contract clause, or quote — or no proposal.
7. **Motion veto:** if the accelerometer says the device moved during a live mandate, all seals freeze until re-armed.
8. **Abstention is a first-class action.** The "Hvorfor?" button must be able to end in "I don't know — call them", and that must feel like a success, not a failure.
9. **Log what the human saw**, not what the agent intended: the frame hash is part of the attestation.

### Events and actions I would define
Custom events: `fm_state` (idle/presenting/dialing/sealed/locked), `fm_detents`, `fm_sealed` (payload: proposal id, counter, hex signature), `fm_moved`, `fm_ptt` (start/stop). Custom actions: `fm_present` (JSON: amount, payee, three checks, required detents, ttl), `fm_lock`, `fm_unlock`, `fm_reflash`, with `atechFail("no frame")` when the display didn't render — a seal over an unrendered frame is exactly the failure this product exists to prevent.

### The 3-minute judge demo

**0:00** — Ola, twelve-person plumbing firm. Device on the table, ring breathing green. "This is the thing that lets an agent spend his money."

**0:15** — Read the screen: *"Forhandlet ned forsikringen −3 400/år · kansellerte 2 duplikater · betalte strøm 1 890 kr (innenfor fullmakt)"*. Speaker, one line: *"Fire ting gjort mens du snakket."* This is the anti-chatbot beat: nobody prompted it.

**0:40** — An invoice arrives: 48 400 kr from Nordvik Rør AS. The screen fills with three checks. Amount vs contract: **red** (contract says 32 000 plus materials). Account number: **red** (changed two days ago). Registry: **amber**.

**1:05** — The agent speaks: *"Jeg stopper en faktura fra Nordvik. Kontonummeret er byttet."* It **refuses to propose**. The motor clicks and the knob locks. Physical no.

**1:25** — Ola presses *Hvorfor?*; the device reads the evidence aloud. He calls the supplier; the real account is confirmed; he presses the third button; the agent re-runs the registry check. Third check goes green.

**1:50** — The amount is above the mandate, so: twelve detents. He turns; the ring fills; at twelve the pawl releases with a click and the screen shows the seal. *"Signert. Tolv av tolv."*

**2:10** — **The punchline.** From the laptop, with everything the agent has — its session, its token, its policy engine — call `/pay` → **403, no device attestation**. Then the device's own WiFi POST lands → **200 paid**. "A compromised laptop can ask. It cannot answer."

**2:30** — Show the attestation: amount, payee, frame hash, gesture 12/12, counter 00417. "The bank can prove what was on the screen and who turned the knob. You cannot phish that."

**2:45** — "Every agent that spends money will need a place where a human says yes. We think that place should be DNB."

Fallbacks: if venue WiFi dies, the seal is relayed over USB — still unforgeable, just slower, and say so. If the LLM stalls, a recorded trace plays, labelled as a replay. If the pawl jams, the ring goes red and refuses.

### Riskiest assumption, and the hour-1 kill test
**The assumption:** that a physical attestation is *necessary* — that a phone-based approval is genuinely insufficient, and that our host really cannot forge or replay a seal.

**Hour 1, in this order, before any UI:**
1. Firmware renders an amount, hashes the canvas, HMACs the tuple with an NVS key, prints the hex.
2. A thirty-line server that rejects any `/pay` without a valid HMAC.
3. **Attack it three ways:** forged signature, replayed signature, signature with the amount swapped. All three must fail. Replay is the one that will pass on the first try — the monotonic counter is the fix, and it must be proven at hour 1, not hour 40.

If two of the three attacks succeed after a day of trying, the pitch is theatre and we should switch to Holdbar. That is the honest test.

**Second assumption:** that the three-minute story lands without a real bank. De-risk by making the mock mandate service enforce *real* crypto rather than stub it — then the demo's claim is literally true of the system on the table.

### Build plan with cut lines
- **0–2h:** `atech check`, all modules answering, first `build` started early (the toolchain is a 2.4 GB download — do it on good wifi before you need it).
- **2–6h:** firmware state machine, screen layout, ring progress, tones. `feedback: false`.
- **6–12h:** bridge + mock mandate service + the hour-1 attack test hardened. **Cut line: no mechanics before this works.**
- **12–18h:** the agent: tools, citation rule, negotiation email, risk weight → detents.
- **18–24h:** the WiFi attestation path (or USB relay), frame hash, counter across reboots.
- **24–34h:** mechanics — pawl only. If it isn't working by hour 34, ship the ring-only refusal.
- **34–44h:** speech out (Piper → 16 kHz mono → base64 PCM chunks; a 2-second sentence is ~28 actions, each ~70 ms, so keep utterances under two seconds) and speech in if it works.
- **44–48h:** rehearse ten times, record a backup video, write the pitch.

**What we deliberately will not build:** real PSD2, real Vipps, a second device, four motors, and a live negotiation call.

---

## Wildcard: Testament — the agent that becomes your executor

A box on the shelf with a weekly ritual: turn the knob to say *still here*. Inside is the will, the letters, the passwords, the account list. The motor keeps it locked.

When the knob stops turning, the agent starts a **ladder, not a cliff**: a text, a call, a neighbour knocking, a human at the bank — thirty days of grace before anything is irreversible, because the false-positive failure mode (declaring a living man dead while he's in Alicante) is both a catastrophe and a comedy sketch.

Then it does the work nobody wants: identifies the heirs, notifies them one at a time with your recorded message in your own voice, pays the last bills, cancels the subscriptions, values the estate, files with the tax office, and on the last step the motor opens the box.

**Why it's not just a stunt:** probate in Norway takes months of paperwork across banks, insurers and the tax office, and roughly forty thousand people a year go through it. Banks already hold wills in safe custody — DNB is already in this business and it is entirely undigitised. 49 kr/mo, sold alongside life insurance; estate administration fees on the back end; and the estate's money stays with DNB for a year.

**Why it needs a body:** the whole promise is *"it only opens when I am gone"*, and that is a mechanical promise, not a software one. A dead-man's switch you can see, with a lock you can hear, is the only version of this that a person would actually trust in their house.

**Why it's outrageous:** it is a machine that knows when you die, and its best-case behaviour is to do nothing for forty years. The pitch writes itself, and so does the black comedy — which, for a bank, is the rare product that is both compassionate and unforgettable.

---

## Notes on the kit (measured, not assumed)

- **Twelve usable ports** on the 14-port board (1–7, 9–11, 13, 14); 2-port modules need an adjacent pair. I ran `atech validate` on each layout I would actually build: Fullmakt 12/12, Fullmakt-without-mic 11/12, Holdbar 12/12, Bua-with-mic 12/12, Bua-with-climate-sensor 12/12. No idea here can have four motors *and* a microphone *and* a screen; only Bua can take four motors at all.
- **The catalog's LED module is a 3×3 NeoPixel grid — nine LEDs, not three.** If the brief's "three LEDs" means three singles, the cheaper move is three `button` modules with their built-in RGB lights: three presses *and* three lights for three ports, instead of four ports for three buttons plus a grid. That saved port is exactly the one the Fullmakt build spends on its motor. Worth knowing before you lay out the board.
- **Serial is the transport, not WiFi.** The SDK only speaks USB serial, one JSON line per event/action, lines capped at 4096 characters. WiFi works in firmware but nothing in the SDK carries it — so the honest architecture is board ↔ laptop over USB, laptop ↔ cloud over the network, and *one* deliberate exception: the device's own signed POST, because that exception is the product.
- **Speech is chunked, not streamed by the board.** `speaker_play_pcm` takes at most ~3000 base64 characters per action and blocks until that chunk has played: 2250 bytes = 1125 samples ≈ 70 ms of audio. One second of speech is fourteen actions. Plan for two-second utterances.
- **The microphone is not a speech recogniser.** It gives RMS level, peaks, up to ~3 s of recording in a heap buffer, and a callback for gapless samples. Getting text out means streaming PCM to the laptop and running ASR there. That is a real risk, which is why Fullmakt's core loop works with buttons alone and the voice is a bonus.
- **The first build downloads a 2.4 GB toolchain.** Do it before you need it.
