# Three companies for an agent that lives in the physical world

**Pick: Lifeboat — an emergency operating partner for small businesses.** It notices an incident, works out an affordable recovery plan, and coordinates the response after the owner approves the commitments. The memorable demo is a florist whose cold room starts warming while tomorrow's wedding orders are waiting.

These are independent proposals based on the brief, the installed Atech SDK, and the primary documentation linked below. Prices and customer demand are hypotheses to test. No other council files were consulted. No VM access, hardware flashing, real payments, or external bookings were performed.

## 1. Lifeboat

**One-line pitch:** Give every small business an emergency COO with a body, a budget, and a network of businesses that can help.

### Problem and first customer

A florist with a warming cold room has several problems at once: protect stock, preserve tomorrow's orders, find temporary storage, arrange transport, pay for help, and document what happened. An alarm leaves all that coordination to the owner. Start with independent florists and their perishable inventory; expand only after proving one recovery workflow.

### The agent: perceive → decide → act

- **Perceive:** Temperature and humidity trends from the kit, the owner's explanation through push-to-talk, and a budget set with the knob. An initial setup associates this particular device with this particular stock room. The sensor measures local air conditions, not flower quality or the refrigeration system's internal state.
- **Decide independently:** Investigate sustained excursions; retrieve the inventory and tomorrow's commitments; identify missing information; obtain nonbinding storage and courier quotes; compare feasible rescue plans against the owner's cash reserve and deadline. If a supplier becomes unavailable, replan. The LLM handles changing constraints and tool selection; ordinary code checks money and deadlines.
- **Ask the human:** Is the door intentionally open? Is the stock actually in this room? Can a staff member transport it? Does the owner approve the named supplier, total price, and cancellation terms? Ask again if those terms change.
- **Act:** Speak a short recommendation, show the next action on screen, and coordinate an approved reservation, transport order, calendar task, and payment request. Assemble an incident timeline for the owner to review before sharing with an insurer. A motor gives a brief visible activity cue; the screen carries the actual status.
- **Tools:** Inventory and order lookup; balance and cash-reserve lookup; supplier availability and quote APIs; booking, calendar, payment-request, and incident-report adapters. Supplier and insurer integrations are proposed partnerships, not assumed public APIs.
- **Where it runs:** Sensor filtering, controls, display, and bounded motor commands on the ESP32-S3. LLM planning and integrations on a laptop/cloud service reached over Wi-Fi. The MVP can use the documented USB SDK bridge to a Wi-Fi-connected laptop.

**Kit parts:** Temperature/humidity sensor, screen, knob, one confirmation button, microphone, speaker, and optionally one motor. The other motors, buttons, LEDs, and accelerometer are unnecessary for the first version. Use a subset that fits the actual board.

**Why the body matters:** It lives where the stock is, observes while the owner's phone is elsewhere, and lets a colleague answer a short operational question at the scene. That is the source of context and action, not a decorative shell for a chat window.

### Company and DNB fit

Test a subscription around **NOK 399 per location/month plus hardware**, with any supplier referral fees disclosed. The customer buys coordination and response time, not an insurance promise. Start with a florist association or wholesaler that can recruit both locations and backup-storage partners.

The large-company path is a recovery network: more participating merchants create more nearby storage, transport, replacement equipment, and available capacity. The defensible assets would be successful recovery workflows, reliable partners, and consented operational history. Validate the network density before claiming a network effect.

For DNB, this connects business banking to the moment an SMB needs it: show what recovery it can afford, initiate an approved payment through an appropriate integration, and prepare an optional financing enquiry if cash is insufficient. Credit decisions stay with the lender. Insurance partners could receive better incident documentation; neither coverage nor reduced premiums is assumed.

## 2. Mandate

**One-line pitch:** A physical authority console that lets small businesses hire AI purchasing agents without handing them unrestricted spending power.

### Problem and first customer

A bakery, salon, or small workshop may want an agent to source supplies and arrange maintenance, but the owner needs to understand what authority it has and stop it immediately. A long settings page is a poor shared interface for that responsibility.

### The agent: perceive → decide → act

- **Perceive:** Staff describe a need through the kit microphone; the knob supplies a hard spending ceiling; buttons select approve, revise, or cancel. The screen makes the current mandate visible to whoever is working that shift.
- **Decide independently:** Turn the need into a purchasing specification, check the approved supplier list and upcoming obligations, request quotes, identify ambiguous substitutions, and return with the best eligible proposal. When a delivery date slips, decide whether to seek alternatives or ask the owner to relax a constraint.
- **Ask the human:** Clarify product requirements and approve each binding order in the first product. In a later, explicitly delegated mode, the owner could authorize a narrowly specified recurring purchase envelope; anything outside it returns for approval.
- **Act:** Speak the tradeoff, display supplier/item/total/deadline, call the approved order adapter, and follow up on delivery. The physical cancel input revokes pending authority in the gateway. It cannot magically reverse an order already placed.
- **Tools:** Supplier catalogues, quotations, purchase orders, accounting budgets, delivery tracking, and payment requests. The gateway, rather than the LLM, enforces approved vendors, maximum totals, expiry, and single-use approvals.
- **Where it runs:** Immediate controls and status rendering on the board; purchasing agent and policy gateway on the laptop/cloud over Wi-Fi. No bank credentials on the kit.

**Kit parts:** Microphone, speaker, knob, screen, and up to three buttons and LEDs as ports permit. Motors and environmental sensors are not required. Do not pretend a motorized physical lock exists with these parts.

**Why the body matters:** It provides a persistent, shared place to see and adjust delegated authority, especially for staff who do not each have a purchasing app. Physical interaction can improve comprehension; the kit is not a secure signing device, and a button press is not bank authentication.

### Company and DNB fit

Test **NOK 199–499 per business/month**, plus hardware, for the console and approval gateway. The entry market is owner-operated businesses with several staff but no procurement department. The expansion is a vendor-neutral authority and audit service used by many purchasing agents, with the physical console as the initial distribution wedge.

DNB could distribute the service with business accounts and payment integrations. The key research question is whether owners understand and trust the physical mandate more than an equally clear phone interface. If they do not, the hardware advantage disappears. The software must also prove it reliably blocks unauthorized tool execution, rather than relying on the model to obey a prompt.

## 3. Return Address

**One-line pitch:** A rental item that helps organize its own handover, return, repair, and next booking.

### Problem and first customer

Small event-equipment rental businesses lose time arranging collections and arguing over what happened between checkout and return. Start with a kit attached to a reusable equipment case: lighting, audio, or exhibition gear. The agent represents the rental workflow, with its role and access disclosed to both parties.

### The agent: perceive → decide → act

- **Perceive:** Motion and orientation changes, local temperature/humidity, spoken handover notes, and explicit button confirmations. These can trigger questions but cannot prove damage, item identity, contents, location, or who caused a shock.
- **Decide independently:** Notice an unusual event, ask a timely contextual question, check the rental contract and upcoming reservation, and compare extension, courier return, and replacement options. If the customer needs another day, evaluate the next renter's schedule and replacement availability before offering an extension.
- **Ask the human:** Confirm what is in the case and its visible condition; obtain approval for fees, extensions, pickups, or any proposed settlement. A dispute goes to a person. Sensor data never becomes an automatic damage charge.
- **Act:** Give spoken return instructions, show the confirmed deadline, reserve a permitted return slot, and prepare an evidence bundle separating measurements from each party's statements. A brief motor spin can signal an upcoming collection; it does not move the case or operate a lock.
- **Tools:** Rental inventory, booking calendar, courier quotes, contract lookup, merchant payment/refund adapters, and repair estimates. No GPS, camera, lock, cellular radio, or autonomous vehicle is assumed.
- **Where it runs:** Local event summaries and controls on the board; cloud LLM and rental integrations over Wi-Fi. Offline events can be buffered in internal flash, but live coordination waits for reconnection.

**Kit parts:** Accelerometer, temperature/humidity sensor, microphone, speaker, screen, a handover button, and optional motor. A smaller port-limited version uses the screen and button instead of voice.

**Why the body matters:** The agent accompanies the rented case and is present at handover. Both parties can interact with the same record at the same physical object. The device provides useful context, not tamper-proof evidence; authenticated party identity comes from the rental service.

### Company and DNB fit

Charge rental operators a **per-active-case subscription**, with an optional disclosed fee for arranging a return. Win by reducing coordination time and improving utilization before claiming fewer damage disputes.

The company could become a shared handover and recovery layer for rented assets, expanding through rental-management integrations. DNB fit is merchant settlement, financing rental fleets, and insurer-ready incident records. A merchant authorization is not an escrow account, and no indefinite deposit hold is assumed. Vipps documents separate authorization, capture, cancellation, and refund operations with applicable timing constraints. [Vipps payment concepts](https://developer.vippsmobilepay.com/docs/APIs/epayment-api/api-guide/concepts/)

## Favourite: Lifeboat, built in 24–48 hours

### The exact MVP promise

**A physical temperature event starts a real LLM/tool loop. Changing the available supplier or spending limit changes its recovery plan. The system cannot commit money before approval.**

One business, one stock room, one incident type, two possible storage suppliers, one courier, one payment adapter. No autonomous lending, insurance adjudication, stock-quality diagnosis, or refrigeration control.

### Hardware and transport

The minimum working body is the temperature/humidity module, screen, knob, and one button: six module-port positions using the SDK's documented sizes. Confirm board type, occupied power positions, and adjacent-port availability before assigning pins or flashing. The catalogue's nominal board size is not its count of usable module ports.

On a suitably available 14-port board, add microphone, speaker, and one DC motor: eleven module-port positions in total, including four adjacent pairs. Do not connect every supplied module just because it is in the box. No extra electronic components are needed.

- Temperature/humidity readings arrive every two seconds. The laptop records a rolling baseline and detects a sustained deviation using demo-configured thresholds and hysteresis.
- The knob edits an incident budget. A button selects an answer or confirms the exact currently displayed proposal; a long press cancels pending work.
- Display short, paginated text: incident, supplier, total NOK, expected timing, and approval state. Use the installed screen's actual dimensions.
- Speaker delivers short synthesized explanations if fitted. The SDK accepts chunked base64 16-bit mono PCM at 16 kHz; serialize acknowledged calls and keep cancellation responsive between chunks.
- Microphone speech requires custom capture/transport. Its stock event reports sound level, not a transcript. Use short push-to-talk recordings and copy audio out through a bounded custom protocol; never print from the audio callback. If this is not working by the time box, use on-screen choices and the knob. The agent remains autonomous without speech recognition.
- Motor motion is optional feedback only. Use a low-speed, short pulse with a firmware timeout, and do not treat open-loop DC rotation as a calibrated pointer, valve, or lock.

Start with **board → USB serial → one laptop bridge → hosted LLM over laptop Wi-Fi**. The SDK documents that path. Direct ESP32 Wi-Fi is a second-day improvement requiring custom firmware; the SDK does not supply a Wi-Fi transport. Bluetooth is BLE, not Bluetooth audio.

The production placement is in the stock room with appropriate power, enclosure, and validated radio coverage. For judging, put the kit on the table. Show a real change from a hand held near the sensor; label the florist incident and accelerated thresholds as a demonstration, not a real cold-chain assessment.

### Agent architecture

**Model:** GPT-4.1 through a hosted API, assuming the team's credentials provide access; keep the model identifier configurable. Use strict tool schemas and a bounded sequential tool loop. Structured arguments reduce parsing errors, but do not establish the truth of a supplier quote or authorize a transaction. [OpenAI function-calling documentation](https://developers.openai.com/api/docs/guides/function-calling)

**Board:** Nonblocking sensor acquisition, input events, display, short audio chunks, motor stop timer, and a visible disconnected state. No LLM or bank secrets.

**Laptop:** A single Python process owns `Board.connect()`, handles events, runs the policy/state machine, executes tools, persists SQLite records, and calls the cloud model. Use acknowledged `board.call()` for outputs. A local review screen shows actual tool inputs/results and whether each adapter is simulated.

**Memory:** Device-to-location assignment; owner-defined temperature range; inventory manifest; business cash reserve; approved suppliers; incident budget; recent sensor samples; structured observations; proposal versions; owner approvals; tool results and transaction status. Mark every fact with provenance: sensor, owner assertion, external response, or fixture. Raw voice need not be retained after transcription.

**Proposed application tools** — these are wrapper designs, not claimed vendor endpoints:

- `read_business_context(incident_id)` returns stock, deadlines, available cash, reserve, and configured policy.
- `get_recovery_quotes(requirements)` returns structured supplier IDs, capacity, ETA, total price, expiry, and cancellation terms.
- `check_plan(plan)` deterministically checks cost, inventory capacity, time constraints, budget, and reserve.
- `present_proposal(plan)` creates a versioned proposal and displays the exact commitment.
- `request_booking(proposal_id, approval_id)` and `request_payment(...)` require a valid matching approval outside the model.
- `get_transaction_status(reference)` resolves uncertain outcomes before any retry.
- `draft_incident_report(incident_id)` creates a local report for owner review.

Mock suppliers and banking data are enough for the complete loop. If hackathon-issued credentials are already usable, add one sandbox integration. DNB publishes payment-initiation documentation describing sandbox and production access; that does not establish that this team has permission or credentials. [DNB payment-initiation guide](https://developer.dnb.no/documentation/psd2-payments/prod/2.91.0/guide)

For a Vipps demonstration, the participating merchant/test sales unit creates the payment request and the owner approves in the appropriate app flow. Lifeboat cannot debit an arbitrary bank account through a generic Vipps tool. Authorization and capture are distinct; display payment progress from adapter responses, not from what the LLM says. [Vipps payment concepts](https://developer.vippsmobilepay.com/docs/APIs/epayment-api/api-guide/concepts/)

### Guardrails and human control

1. The model can investigate, calculate through tools, seek quotes, and draft without approval. It cannot place a paid order, accept credit, submit an insurance claim, or send private reports on its own.
2. Approval binds to a proposal hash containing supplier, items, total, currency, timing, and cancellation terms. It expires and is single-use. Any material change invalidates it. A knob-set ceiling alone is not permission to spend.
3. The gateway checks approval, quote validity, arithmetic, available budget, and recipient allowlist on every committing tool call. Supplier descriptions are untrusted data and cannot alter those rules.
4. Money uses integer minor units. Stable idempotency keys and status lookup prevent duplicate payment requests or bookings after a timeout. An unknown result stays unknown until reconciled.
5. For multiple commitments, prefer reversible reservations, define the order, and release a reservation if the other leg fails. If compensation is unavailable, show the risk before approval; do not claim an atomic booking across unrelated suppliers.
6. An offline model produces a visible unresolved incident and local alert, not an invented recovery. Firmware stops motor output on timeout. Sensor uncertainty or missing inventory triggers a question, never a fabricated loss estimate.
7. Payment-provider authentication remains separate from the kit's approval interface. The MVP button records consent within a supervised demo; it does not establish who the operator is in production.

### Three-minute judge demo

**0:00–0:25 — Stakes.** “This florist has tomorrow's wedding stock in a room that is warming. The owner is serving customers.” Show the real sensor reading and a visibly labelled fictional inventory of NOK 18,000. Set a NOK 900 incident budget with the knob.

**0:25–0:50 — Physical trigger.** Warm the sensor gently with a nearby hand. Once the accelerated demo threshold trips, the agent asks whether this is an intentional door-opening event. The judge selects “No.” The event and response appear in the log.

**0:50–1:20 — Independent work.** The agent reads the order deadline and obtains two fixture-backed quotes: storage plus courier at NOK 1,100, or nearby storage for NOK 500 with staff transport. Code rejects the first as over budget. The agent asks whether a staff member can drive, then proposes the second if the answer is yes.

**1:20–1:50 — Prove it can adapt.** Mark the cheaper supplier unavailable in the demo control. The agent must obtain another plan rather than recite the earlier recommendation. It may propose the NOK 1,100 option but cannot execute it. Let the judge either increase the ceiling or refuse. Refusal leaves an unresolved incident, with no booking.

**1:50–2:25 — Human commitment.** If the judge raises the budget, display the fresh supplier, total, timing, and terms. The separate confirmation button approves that proposal. Show a test payment flow only if credentials work; otherwise show a conspicuously labelled simulated transaction. No “paid” state before a tool confirms it.

**2:25–3:00 — Outcome and company.** Show the booked recovery steps, approved total, remaining budget, and incident report. Replay the confirmation event and demonstrate that it creates no duplicate order. Close: “We sell a coordinated response to a business emergency, beginning with independent florists.” Describe preserved stock value as potential exposure, not measured savings.

### Delivery schedule and acceptance checks

- **Hour 1:** Validate the central assumption below; identify the board and confirm a sensor-to-screen round trip. Start any first compiler download early; the SDK warns it can be substantial.
- **Hours 1–5:** Minimum firmware, laptop bridge, live temperature trigger, knob budget, proposal display, confirmation/cancel handling.
- **Hours 5–12:** Real model calls, business-context fixture, supplier tools, deterministic plan checks, and the unavailable-supplier replanning branch.
- **Hours 12–20:** Approval binding, idempotency, transaction-status handling, incident report, and the judge script.
- **Hours 20–24:** Rehearse the two judge choices; make all fixture labels visible. Verify budget refusal, changed-quote invalidation, duplicate approval, and disconnected-model behavior.
- **Hours 24–48, only after the loop works:** Board speech input/output, a usable payment sandbox, direct Wi-Fi, and an actual operator interview. Each is optional; none should replace the core end-to-end rehearsal.

Success means one live physical trigger, one real LLM selecting tools, a changed external constraint producing a changed plan, and an enforceable approval boundary. A scripted monologue over a hard-coded checkout is not success.

### Riskiest assumption and hour-one de-risking

**The risk is that this is merely an alarm with impressive narration: the sensor and available business context may not support a useful recovery decision.**

In the first hour, create three plausible florist scenarios: intentional door opening; warming room with affordable nearby storage; warming room with no affordable recovery. Give the agent the same sensor pattern but different explicitly sourced business context. Run the actual model against fixture tools, with an operator or hackathon mentor playing the owner.

Pass only if it distinguishes investigation from commitment, identifies missing facts, offers a feasible plan when one exists, and declines to book when none fits. It must ask materially different questions as the context changes. Ask the operator whether the chosen intervention would help and whether the inventory/deadline data is realistically available. Without an actual florist, label this a preliminary workflow test rather than customer validation. If the loop fails, narrow to “find temporary storage after owner-confirmed refrigeration trouble” before adding integrations.

## Deliberately outrageous wild card: The Building That Hires Its Own Caretaker

Give a housing cooperative a physical agent representing the shared building budget. Residents speak to it, vote with buttons, and adjust a maintenance reserve with the knob. It combines those inputs with local temperature/humidity observations, requests contractor quotes, proposes maintenance priorities, and recruits neighboring buildings into joint purchasing rounds. Four motors can perform a short collective “celebration” after an approved project; they do not repair the building.

The cloud LLM chooses what to investigate, which quotes to request, and when to bring a proposal to a vote. A separate service enforces board authority, spending limits, and the required human approvals before booking or payment. Tools connect to maintenance calendars, supplier quotes, budgets, and payment requests. The kit's sensor describes its own location, not the whole building.

The outrageous company thesis: buildings become persistent purchasing clients with institutional memory, and people govern them through a tangible civic interface. Sell the service to cooperatives and property managers. DNB could supply the banking relationship for their reserves and approved payments. The building is not a legal person; the cooperative owns the account and remains responsible. The first prototype needs only the kit, a laptop, and simulated contractors.

## Hardware evidence checked locally

Read-only inspection used `.venv/Scripts/atech.exe guide`, `list-modules`, `list-boards`, and `info` for `aht20`, `microphone`, `speaker`, `dc_motor`, `rotary_encoder`, `icm40608`, and `button`.

The constraints above follow those installed SDK descriptions: USB-only host transport by default; custom Wi-Fi/BLE support; two-port microphone, speaker, knob, and screen; microphone sound-level events plus raw-sample APIs; chunked speaker PCM; open-loop DC speed control; and a stock accelerometer event stream that provides coarse motion/orientation rather than forensic shock recording. Module identity, physical placement, board variant, and usable power remain to be confirmed on the team's actual kit.
