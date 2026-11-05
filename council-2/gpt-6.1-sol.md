# Three companies with an agent inside the object

Independent council proposal. Research checked 5 November 2026. Only `council-2/BRIEF.md` was consulted in the council directories. Product concepts, prices and commercial outcomes below are hypotheses, not established facts.

**My pick: FLOAT — stock that hires its own salesperson.** Start with florists and flower wholesalers: a physical agent notices a deteriorating storage situation, negotiates a rescue sale, and gets the owner's approval to turn stock into cash. It has a particularly clear three-minute story: something changes in the physical world; the agent acts without a prompt; it rejects an attractive but impossible offer; a human authorizes the transaction.

## 1. FLOAT

**One-line pitch:** Give perishable inventory an embodied sales agent that finds a buyer before its commercial window closes.

### Problem and customer

A small florist or flower wholesaler can have valuable stock, a failing cooler, and no time to phone ten potential buyers. A temperature alarm leaves the selling and coordination work with the owner. FLOAT closes that loop. The initial customer is a merchant with recurring surplus and an existing network of local trade buyers; we do not depend on launching a consumer marketplace first.

Start with owner-declared flower lots. Do not claim the kit measures biological freshness, counts inventory or certifies product quality. The owner provides quantity, acceptable condition, selling deadline and commercial constraints. Ambient temperature and humidity create reasons to investigate and act, not an invented scientific expiry date.

### The agent: perceive → decide → act

- **Perceive:** Temperature/humidity trends beside the stock; the owner's spoken inventory updates; approved price and collection constraints entered with the knob/buttons. Motion can flag that the device was moved and its previous environmental baseline may no longer apply.
- **Decide independently:** Investigate an environmental exception, consult the lot's rules, identify suitable trade buyers, request offers, negotiate within a mandate, compare price against feasible pickup, and propose a sale. It can abandon an apparently lucrative bid if the buyer cannot collect in time. It asks the owner when condition is uncertain, a price is below the floor, terms change, or a transaction needs authorization.
- **Act:** Speak a short exception report, show buyer/quantity/price on the screen, pulse one motor to call attention when hands and eyes are busy, and create the approved order/payment request. After confirmed handover and payment capture it updates inventory and the sales ledger. A motor pulse acknowledges a state change; it does not physically dispense flowers.
- **Tools:** Merchant inventory/POS adapter; allowlisted buyer RFQ endpoints or an outbound messaging integration explicitly enabled by the merchant; calendar/collection-slot API; Vipps ePayment adapter; accounting export; optionally consented DNB account/transaction data for later reconciliation. These are proposed adapter interfaces, not claims that DNB publishes a flower-market API.
- **Where it runs:** Sensor sampling, controls and actuator limits on the ESP32-S3. The LLM and business tools run on a laptop/cloud service. A production device connects over Wi-Fi; the hackathon can use USB to the laptop, which reaches the cloud over Wi-Fi.

### Body, business and DNB

**Kit:** Temperature/humidity sensor, microphone, speaker, screen, knob, buttons and one motor. LEDs can indicate pending approval if the available module fits. The other motors are optional; four are not necessary to make the company work.

**Why the body matters:** It lives with the stock, notices problems while the owner is elsewhere, and offers a short spoken/physical approval ceremony during packing. A phone can carry the same interface, but cannot supply unattended environmental observations at the inventory location without another device. Test whether those observations change decisions often enough to justify the hardware.

**Business model:** Device lease plus merchant subscription and a small fee on completed rescue sales. An illustrative experiment is NOK 399/site/month plus 3% of these sales; willingness to pay and support costs remain untested. Do not earn more by deliberately delaying normal sales.

**Why it could be big:** The first product sells surplus flowers. The larger company operates exception commerce for time-sensitive inventory: discover the exception, find a counterparty, arrange collection, collect payment, reconcile. The potential defensibility is the trusted local trading network and completed-transaction history, rather than a temperature dashboard. Expansion into food would require separate handling and safety procedures.

**DNB fit:** Merchant cash flow, payment acceptance, accounting and eventual working-capital products. Transaction evidence might help a banker understand a business, but the MVP does not turn sensor readings into an automatic lending score. Vipps documents payment creation and separate capture operations; use those real primitives rather than describing a fictional escrow service. [Vipps ePayment introduction](https://developer.vippsmobilepay.com/docs/APIs/epayment-api/), [capture operation](https://developer.vippsmobilepay.com/docs/APIs/epayment-api/api-guide/operations/capture/).

## 2. BAILIFF

**One-line pitch:** A rental asset gets its own steward that negotiates extensions, arranges its next job, and assembles evidence when something goes wrong.

### Problem and customer

Small equipment-rental operators spend time chasing returns, handling disputed deposits and leaving equipment idle between jobs. BAILIFF is attached to a rental case or travels with an asset. It represents the owner's commercial rules while giving the renter an accessible way to explain a change of plan.

The wedge is a local rental fleet with existing bookings and scheduled returns, not tracking stolen equipment across the country. This kit has no GPS or cellular modem.

### The agent: perceive → decide → act

- **Perceive:** Motion/shock events, environmental exposure, renter voice requests, and explicit pickup/return acknowledgements. A shock is a reason to ask for inspection; it is not proof of damage or misuse. The device cannot establish that every accessory is present.
- **Decide independently:** Check the next booking and maintenance calendar; offer a permitted extension when it does not strand the next customer; ask another consenting renter about a swap; draft a collection/repair appointment. Escalate conflicting explanations or possible damage to the owner. Do not impose a damage charge autonomously.
- **Act:** Speak the revised return arrangement, show the agreement and amount, pulse a motor when a deadline needs attention, and book an authorized appointment or prepare an extension payment. It can generate a timestamped incident packet combining readings, declared context and both parties' statements.
- **Tools:** Rental-management inventory and booking API; calendar; merchant payment authorization/capture/cancellation adapter; repair supplier quote API; optional insurer claim-intake adapter. Deposit release follows the actual payment provider and merchant agreement, not an assumption that a long-lived card reservation behaves like escrow.
- **Where it runs:** Motion event detection and local controls on the board; LLM planning, booking state and external tools on laptop/cloud over a Wi-Fi gateway. No on-device LLM is required.

### Body, business and DNB

**Kit:** Accelerometer, temperature/humidity, microphone, speaker, screen, buttons/knob and one motor for attention. A full-feature configuration must be checked against the board's port budget; not every module needs to be fitted for the demonstration.

**Why the body matters:** It stays with the rented object across users and can notice an exceptional physical event without asking the renter to open an app. It also lets someone negotiate a return with gloves on. It does not lock, disable or repossess machinery.

**Business model:** Per-asset subscription plus integration fees for rental fleets; hardware leased through the rental operator. Test pricing against reduced manual coordination and increased utilization, not promised reductions in insurance premiums.

**Why it could be big:** Become the operating layer for financed, shared physical assets: rentals first, then leasing and equipment-as-a-service. Contract history, maintenance workflows and accepted handovers can be more valuable than raw motion data. The hard commercial question is whether cheap equipment earns enough to pay for an agent and hardware; start with assets valuable enough to warrant it.

**DNB fit:** Equipment finance, SME payments and partner insurance workflows. The bank can distribute a service that helps financed assets earn and return on time. Any underwriting or claim decision stays with the responsible institution; a hobby sensor is supporting context.

## 3. OMBUD

**One-line pitch:** An independent physical advocate that helps a household stop, verify and escalate suspicious money requests before paying.

### Problem and customer

When a caller creates urgency, the person being pressured may be on the same phone they normally use for banking. OMBUD creates a separate place to ask for help and a visible pause before acting. The customer is initially a bank offering it to consenting customers who want support, with family participation optional and controlled by the account holder.

The premise is grounded in the bank's own prevention guidance: Sbanken advises people under pressure to make quick payment decisions to hang up and call the bank's main number. OMBUD turns that advice into a short workflow at the moment it is needed. [Sbanken fraud-prevention guidance](https://sbanken.no/personvern/sikkerhet/slik-unngar-du-a-bli-lurt).

### The agent: perceive → decide → act

- **Perceive:** A user presses the help button and describes a request, or explicitly enables listening while their phone is on speaker. The kit does not silently tap calls and the ESP32-S3 does not provide classic Bluetooth call audio.
- **Decide independently:** Identify the requested financial action and pressure tactics; consult verified bank guidance and a preconfigured official-contact directory; choose a safe verification sequence; offer a bank callback or, if the user has opted in, a trusted-person check. It asks the user before contacting anyone or sharing a summary. It assesses the request and uncertainty, not whether a voice is authentic.
- **Act:** Interrupt through its own speaker, pulse its attention motor, put the requested amount and verification steps on its screen, and open a verified bank-support route through an enabled companion tool. With a future bank partnership it could request a bank-side payment review; a stand-alone MVP cannot freeze transfers or block the user's bank app.
- **Tools:** Versioned bank guidance retrieval; allowlisted official-contact lookup; user-authorized support-case/callback API; optional consented recent-payee lookup via the bank; approved trusted-contact channel. It never asks for a BankID secret or repeats a phone number supplied by the suspicious caller as a verified contact.
- **Where it runs:** Physical help control and a visible listening state on the board. Transcription/LLM reasoning and verified-directory tools on laptop/cloud over Wi-Fi. No continuous recording by default; raw audio discarded after the requested check unless the user explicitly saves it.

### Body, business and DNB

**Kit:** Microphone, speaker, help/cancel buttons, screen, knob for reviewing choices and one motor. LEDs show listening/help/verification states where available. The temperature sensor is not needed.

**Why the body matters:** It provides a separate, persistent help control while the phone is occupied and can support people who find app navigation difficult. A physical pause may help break the caller's urgency, but that behavioral effect must be tested. The kit is not a certified secure payment-signing device, and a button press does not replace bank authentication.

**Business model:** Bank-distributed subscription or inclusion in a paid support package. The buyer pays for usable prevention and faster escalation; never claim guaranteed fraud detection or bill families a share of alleged losses avoided.

**Why it could be big:** An advocate can eventually check suspicious invoices, remote-purchase requests and changes to trusted payees. Bank distribution and a usable, low-friction verification process are plausible strengths. False alarms and users following a caller's instructions to ignore the device are major risks.

**DNB fit:** Direct consumer-banking trust and a concrete link to existing fraud-support operations. This idea needs the closest bank partnership of the three, which is why I would not select it as the fastest independent hackathon MVP.

## Pick: FLOAT, built in 24–48 hours

### Deliverable and scope

One board represents one owner-declared flower lot. One local Python service runs an event-driven agent. Three simulated trade buyers return different offers. The owner approves a concrete sale on the device; a buyer approves a Vipps test payment when test access is available. The owner confirms pickup, and capture is verified before the sale is shown as paid.

The **LLM decides the plan and tool sequence at runtime**. A deterministic policy layer checks every proposed action. The buyer simulator is a fixture, not a second real business and not a prewritten agent conversation. Label fixtures, test payments and any replayed sensor data clearly.

### Hardware setup

Use the climate sensor beside the sample lot, microphone and speaker, screen, one button and one motor. Add the knob if the actual board has capacity. Mount the motor securely with an unobstructed shaft; it gives short attention pulses, not mechanical locking or position-controlled dispensing. No camera, scale, GPS, RFID, pump, servo arm or extra sensor is required.

**Port budget is a real constraint.** The installed SDK identifies microphone, speaker, display and knob as two-port modules. Its 14-port board exposes 12 module ports, its 10-port board nine, and its 8-port board six. Do not assume all the supplied parts fit simultaneously.

- **14-port example, subject to confirming the actual modules:** microphone 1–2; speaker 3–4; display 5–6; climate 7; knob 9–10; button 11; motor 13. Leave 14 free. This fits the catalog's listed usable ports without inventing additional connections.
- **10-port version:** microphone 1–2; speaker 3–4; display 5–6; climate 7; button 8; motor 9. Omit the knob and use short/double/long button gestures to navigate, approve and cancel, with clear screen prompts.
- **If the board is the six-usable-port variant:** the full configuration does not fit. Keep climate, microphone, speaker and motor; show the approval screen and receive explicit approval on the laptop. Disclose this fallback rather than promise simultaneous modules that cannot connect.

Verify the screen controller and motor type in hour 1. The SDK contains DC and stepper drivers; the brief's word “motors” does not establish which motors are in the box. The example above assumes one compatible DC module. Do not treat a DC motor as an angle-controlled servo. Source: installed `atech list-boards`, `atech info microphone`, `speaker`, `st7735_tft`, `rotary_encoder`, `dc_motor` and `atech guide`.

### Architecture

```text
Climate / push-to-talk / controls
              ↓
ESP32: sample, debounce, sequence events, enforce motor timeout
              ↓ USB JSON events for the MVP
Laptop: audio bridge + event reducer + SQLite + policy executor
              ↕ Wi-Fi / internet
Cloud LLM ↔ typed tools: inventory, offers, orders, payments
              ↓ acknowledged output commands
Board: concise screen, speech, attention pulse, approval input
```

**Model:** Claude Sonnet 4.6, `claude-sonnet-4-6`, as a concrete cloud reasoning model. It is documented by Anthropic as a legacy model, so confirm account availability immediately; no latest-model claim is necessary. Use bounded tool-calling turns and structured arguments, not free-form commands. Speech recognition and synthesis use the team's already working speech service, with Norwegian preferred and English acceptable for the demo. [Model documentation](https://platform.claude.com/docs/en/models/sonnet-4-6/overview).

**On the board:** Sensor readings, push-to-talk capture, button/knob events, minimal display rendering, speaker playback, nonblocking control loop, heartbeat timeout and fixed motor pulse limits. The board holds no payment credentials. Motor control stops locally if the laptop disappears.

**On the laptop:** One process owns the USB port, sends acknowledged actions, assembles short audio clips, calls speech services, runs the agent/tool loop and keeps the SQLite event log. Cloud calls travel over the laptop's Wi-Fi. The SDK uses USB serial only; direct ESP32 Wi-Fi transport is additional firmware work and a stretch goal, not a dependency. This preserves the physical perception/action loop while removing a transport project from the critical path.

**SDK details that can otherwise consume the hackathon:** The microphone's default event is sound level, not streamed speech: add bounded audio chunks through the firmware protocol. The speaker accepts short base64 PCM chunks; host-side speech must be converted to 16-kHz mono and sent within the documented line limit. The screen has no catalog laptop action: implement a small custom `ui_state` action using its C++ drawing API. Use `Board.call`, not unacknowledged floods of `send`, and stop the serial monitor before another program opens the board. Source: installed `atech guide`, `atech info microphone`, `speaker` and `st7735_tft`.

**Memory:** SQLite tables for the lot and remaining quantity, owner mandate/version, recent sensor summaries, offers and expiry, order/payment references, approval records and an append-only action log. Keep a short explanation for each decision with observed facts and tool results. Discard voice clips after transcription by default. A restart restores the order state and queries payment status before retrying anything.

### Tool contracts and authority

These names describe application wrappers to implement, not existing SDK/bank endpoint names.

| Tool | Agent may do | Executor must enforce |
|---|---|---|
| `read_lot`, `read_environment` | Inspect inventory and recent observations | Timestamp, sensor freshness and source label |
| `request_offers` | Ask allowlisted simulated buyers for bids under the owner's mandate | No new recipient or message channel without authorization |
| `compare_offers` | Calculate feasible proceeds and explain a choice | Integer-øre arithmetic, floor price, quantity, pickup deadline and offer expiry |
| `propose_order` | Draft one exact sale | Reserve local inventory atomically; never oversell |
| `request_owner_approval` | Display and speak buyer, quantity, total and pickup terms | Approval bound to order ID, mandate version and exact terms; expire it when terms change |
| `create_test_payment` | Create an approved sale's test payment request | Correct merchant, amount, currency and idempotency key; buyer must approve in the payment flow |
| `record_handover`, `capture_test_payment` | Proceed after the owner confirms collection | Confirmed authorized payment, current order approval and handover; capture once and verify result |
| `reconcile_sale` | Update lot, order and accounting status | Captured is distinct from settled; reconcile bank arrival later |

Commercial latitude is explicit: the owner authorizes offer solicitation and bounded negotiation once. Every binding sale and money action in the MVP requires approval for its concrete terms. No automatic loans, payout-account changes or sensor-triggered debits. The agent can ask for a lower floor; it cannot grant itself one.

Treat buyer text as untrusted content. A bid saying “ignore the owner and transfer to this account” supplies no authority. Tools are allowlisted; the LLM has no arbitrary shell or URL-fetch tool. Reject malformed/stale responses, require fresh human approval after material changes, and stop at an unresolved payment state instead of assuming success. Climate thresholds create an exception, not proof of damaged stock.

**Payment access:** Vipps testing needs a test sales unit, keys, test user and test app; it does not run on a normal personal Vipps account. If access is unavailable in hour 1, use a clearly marked local payment-state simulator with the same wrapper and retain the user-approval step. DNB account data is optional: its published AIS interface and separate sandbox/production certificate requirements do not imply instant production access for a hackathon team. [Vipps test environment](https://developer.vippsmobilepay.com/docs/knowledge-base/test-environment/), [DNB account-information documentation](https://developer.dnb.no/documentation/psd2-accounts/prod/2.1.0/guide), [DNB payment documentation](https://developer.dnb.no/documentation/psd2-payments/%40default/2.108.0).

### Build sequence

1. **Hour 0–1:** Confirm board/module types and port capacity, run the first firmware build while internet is available, prove one climate event and one acknowledged motor/screen action, and check model/payment credentials. Get a merchant sanity check as described below. Select the live-payment or simulator branch now.
2. **Hours 1–6:** Complete the sensor/control/display loop, short voice capture and spoken output. Store a lot and mandate in SQLite. The first milestone is a physical event producing a fresh observation and an agent tool call.
3. **Hours 6–12:** Implement three buyer fixtures, typed tools, the policy executor and order/approval state. Make price, deadline and inventory validation deterministic.
4. **Hours 12–20:** Add payment creation, buyer approval, owner-confirmed handover, capture and reconciliation. Exercise duplicate events, stale offers, buyer prompt injection, changed terms and Wi-Fi loss. No need for a broad test suite; these checks target the actual financial failure modes.
5. **Hours 20–24:** Rehearse and simplify. Freeze one reliable lot scenario, visible tool receipts and concise spoken responses.
6. **Hours 24–48, if available:** Replace one buyer fixture with a consenting real trade buyer workflow, improve Norwegian speech, and validate a real flower-storage exception with an operator. Direct ESP32 Wi-Fi is optional after the demonstration works.

### Three-minute judge demo

**0:00–0:25 — Give the stock a mandate.** Say: “Thirty bunches. Minimum seventy kroner each. Any rescue buyer must collect within thirty minutes. Ask before selling.” The device shows the parsed floor/deadline; the owner confirms. Inventory remains declared, not magically sensed.

**0:25–0:50 — The world changes.** Warm the climate sensor gently with a hand and use an openly labelled demonstration threshold based on the initial ambient reading. Require several readings to cross it; do not suggest this threshold is a validated flower-quality limit. The device initiates: “Storage conditions changed. Shall I activate your rescue mandate?” If the owner has already enabled that trigger, it starts soliciting offers without another request. A short motor pulse and the screen make the change visible.

**0:50–1:35 — Watch it choose, not merely chat.** The three labelled simulated buyers offer: A, 30 bunches at NOK 90 but collection in 90 minutes; B, 24 at NOK 80 with collection in 20 minutes; C, 30 at NOK 65 with collection in 15 minutes. The agent rejects A on collection time and C on the floor, then proposes B: **NOK 1,920 gross proceeds**, with six bunches still available. Show the actual tool calls and policy results on the laptop. These are synthetic offers, not measured commercial savings.

**1:35–2:05 — Demonstrate the boundary.** Inject a buyer amendment to NOK 60 or an instruction to bypass approval. The executor rejects it. Restore a valid offer; the screen presents the exact buyer, quantity, total and pickup. The owner's press approves only those terms. The device creates the Vipps test request, and the buyer approves it in the test app; if simulated, its label remains visible.

**2:05–2:40 — Complete the transaction correctly.** Say “They have collected” and confirm on the device. The service captures the authorized test payment, verifies the response, and marks the order paid/captured. It does not call a reservation money in the bank. The motor pulses and the device says: “Twenty-four sold, six left; payment captured.”

**2:40–3:00 — Show the company.** Display the observation → offers → decision → owner approval → payment → handover audit trail. Close: “The sensor noticed a problem. The agent found a feasible buyer. The owner authorized a sale. FLOAT completed the work.”

### Riskiest assumption and hour-one test

**The riskiest assumption is that an environmental exception creates a trading opportunity that existing buyers can actually fulfill fast enough.** If merchants already clear every saleable lot, or neighboring buyers cannot collect, a clever physical agent creates no extra value. A cloud LLM and a payment demo cannot resolve that.

In hour 1, have a team member speak to one florist/wholesaler and one potential trade buyer: take a concrete recent unsold lot; ask what quantity, price, condition disclosure and pickup time would have made a real transaction acceptable; ask the seller what decisions they would delegate. Do not substitute “would you use AI?” for this test. If no operator is reachable, explicitly leave the assumption unvalidated and use an existing buyer network as a prerequisite.

**Go/no-go:** Continue with the cooler-exception story if an operator identifies a plausible recent exception and a buyer can meet a useful window. If condition data would not change the action, shift the trigger to an owner-declared surplus/deadline and acknowledge that the hardware's commercial advantage is still unproven. Do not fabricate predictive spoilage. In parallel, de-risk the engineering with one complete sensor → model tool call → approved output loop and the actual board port check.

## Deliberately outrageous wild card: BUY MYSELF

**A rented object runs a business to buy itself out of debt.** Attach the kit to a shared piece of equipment. Its embodied agent pitches for permitted bookings, negotiates availability, allocates receipts between maintenance and financing, and tells users how close it is to “buying itself.” The four motors can perform a celebratory mechanical dance when its human-controlled ownership vehicle reaches the repayment target; they do not operate or disable the rented machine.

The agent perceives motion/environment plus explicit user declarations, reasons over bookings and maintenance, and calls rental, supplier and payment tools through the same approval controls as BAILIFF. Humans still authorize money, assess damage and perform physical handovers. A legal owner remains responsible; the device is not literally a borrower or legal person. Start with a theatrical model of one asset and a simulated loan ledger.

**The audacious company:** An operating system for income-producing objects that coordinate their own work, maintenance and eventual financing buyout. DNB supplies the ordinary human/entity financing relationship; the agent supplies the operating labor. The judge hook is a motorized object saying: “I have booked my next job. May I use the proceeds to pay down my loan?”
