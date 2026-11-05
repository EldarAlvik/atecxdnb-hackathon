# Three companies hiding inside the Atech kit

Independent council contribution — GPT-6 Astra, 5 November 2026.

**My pick: UNBUILD — make a building's second life finance its dismantling.** The hackathon demo turns a physical rack of reclaimed components into a changing borrowing limit. Remove an item and the available credit falls. Accept a verified batch and a simulated loan pays the contractor.

The business models, prices and financing terms below are hypotheses to test. Hardware capabilities come from the installed SDK; financial transactions in the proposed demos are explicitly simulated.

## 1. UNBUILD

**One-line pitch:** Working capital for taking buildings apart carefully, backed by buyer commitments and evidence of the materials actually recovered.

### Problem and customer

A demolition contractor gets paid to clear a site. Recovering useful components requires extra labour, sorting and storage before a buyer pays. Our hypothesis: that cash-flow gap can make destructive demolition win even when intact components have resale value.

Start with a reclamation operator recovering one repeatable, inspectable category, such as raised-access floor panels. Obtain a buyer commitment and an independent condition assessment. Advance money as accepted components enter a monitored rack; reconcile the advance when the buyer pays. The operator buys the service, a financing partner supplies capital, and the property owner benefits from recovery rather than disposal.

**The unusual bet:** a building awaiting demolition contains future inventory that can help pay for its own careful dismantling. UNBUILD connects the purchase order, inspection, physical custody and financing transaction.

### Exact kit

One **`14port` ESP32-S3 board**:

- `distance_sensor` — VL53L5CX, port **7**, observes occupied locations in a small rack.
- `icm40608` — IMU, port **3**, flags movement of the sensor fixture.
- `button` — port **4**, records an operator's explicit batch-acceptance action.
- `neopixel` — port **5**, shows eligible / review / unavailable.
- `st7789_tft` — ports **9–10**, shows accepted quantity and available advance.

The prototype uses a fixed four-bay miniature rack. Human inspection supplies identity, grade and price. Depth measurements supply occupancy evidence; they do not identify materials, determine structural fitness or prove ownership.

### Business model and scale

Lease the monitoring station and charge per accepted batch or financed transaction. Initially sell workflow software to reclamation operators and their finance partners rather than lending from the startup's own balance sheet.

The expansion is from one component category to a network of buyers, inspectors and recovery operators. The defensible asset would be actual recovery yields, acceptance rates, time to sale and realised prices tied to financing outcomes. More reliable resale evidence could make future projects easier to finance.

The underlying waste stream is substantial: construction and demolition generate more than a third of EU waste. That establishes the scale of the material flow, not the size of UNBUILD's addressable credit market. [European Commission](https://environment.ec.europa.eu/topics/waste-and-recycling/construction-and-demolition-waste_en)

### Why DNB and this hackathon

DNB already offers green business financing; UNBUILD proposes an evidence layer for a specific reuse workflow. This is a potential partnership fit, not a claim that the proposed loan qualifies for an existing green product. [DNB Green Business Loans](https://www.dnb.no/en/business/financing/business-loans/green-loans)

Judges can understand the bank's decision by moving a physical object. Atech provides the live evidence, and DNB provides a plausible capital and settlement partner.

**Commercial kill question:** will a real buyer commit to a sufficiently predictable price, and will that commitment plus inspection and custody evidence justify an advance? A reliable sensor cannot solve an absent resale market.

## 2. Quiet Dividend

**One-line pitch:** Construction sites pre-fund a neighbourhood compensation pot that pays residents when agreed disturbance budgets are exceeded.

### Problem and customer

The developer receives the upside from construction while nearby residents absorb disrupted work, sleep and daily life. Complaints are reactive and disputed. Sell a voluntary disturbance agreement to developers and infrastructure contractors: publish the schedule, fund a compensation reserve and provide residents with a visible settlement record.

The company would measure agreed acoustic indicators at agreed locations and calculate compensation under the contract. It is a commercial benefit alongside the project's existing obligations; it does not purchase permission to break noise limits.

Oslo's own guidance identifies construction noise as a recurring issue and directs affected residents to the responsible developer or contractor. [Oslo municipality](https://www.oslo.kommune.no/gate-transport-og-parkering/stoy/stoykilder/)

### Exact kit

One **`14port` board per measurement station**: `microphone` **1–2**, `icm40608` **3**, `button` **4**, `neopixel` **5**, `st7789_tft` **9–10**, and `micro_sd_card` **13–14**. The IMU flags station movement; the card stores aggregate readings and agreement events. A real deployment would need multiple stations, including a reference near the worksite.

The hackathon version uses a single station and an explicitly artificial RMS threshold. A sustained sound makes a resident's simulated compensation balance rise. Firmware retains aggregate levels, not speech recordings.

**Technical honesty:** the SDK microphone reports RMS amplitude, not calibrated dBA. Calibration, frequency weighting, placement and source attribution are additional work. A clap near one microphone is not evidence that a contractor caused a compensable event.

### Business model and scale

Charge a project setup fee, station rental and subscription for measurement, resident administration and settlement. Keep compensation funds separate from platform revenue; avoid earning more merely because a site becomes noisier.

Start with a developer willing to trial the service on one project and a resident association willing to co-design the agreement. Expansion could cover roadworks, rail maintenance and event venues. The larger company is infrastructure for measurable neighbourhood benefits and contractual compensation.

### Why DNB and this hackathon

DNB could provide accounts, guarantees and payment services for the pre-funded reserve. Atech makes the invisible external cost tangible: noise becomes a visible financial obligation. No bank API is needed to demonstrate the mechanism.

**Commercial kill question:** will a developer pay enough for a better resident relationship to cover administration and measurement costs? The difficult technical problem is separating construction noise from unrelated or deliberately generated noise.

## 3. Piecework

**One-line pitch:** Small businesses buy completed robotic work; robots are financed and repaid against verified output.

### Problem and customer

A small manufacturer may need packing capacity but hesitate to buy a robot whose real throughput is uncertain. Piecework combines equipment financing, a repeatable work cell and task-level acceptance: the business pays for accepted work, while an equipment provider takes defined installation and performance risk.

Start with one repetitive task in a controlled fixture: transferring a known part into a packaging nest. The first customer is a small production business; the distribution partner is a robot integrator that already installs and services cells.

### Exact kit

One **`14port` board**: `robot_arm` on UART port **3**, `distance_sensor` **7**, `button` **1**, `neopixel` **2**, and `st7789_tft` **9–10**. Leave port **4** clear for arm-module clearance and port **6** available for a power-jack module; the latter has no firmware manifest entry.

The SO-101 moves a foam part between fixed nests. The ToF observes destination occupancy before and after a commanded cycle. Python queries joint positions, checks the occupancy transition and emits one billable demo task. A failed placement earns nothing; replaying the same task ID earns nothing again.

The arm requires a supply matching its specific **5 V or 12 V variant**. Before anyone powers or flashes an arm build, confirm the arm variant and supply label; the kit passes the supply rail through without regulation. Software button cancellation is a demo control, not a certified emergency stop.

### Business model and scale

The customer pays per accepted unit, with a negotiated minimum commitment. Piecework takes a software/service margin; a finance partner funds equipment, and the integrator handles installation and maintenance. Payment allocation can split each receipt among service costs and equipment repayment.

The scale opportunity is a repeatable financing and acceptance contract across many small work cells. The moat would be measured utilisation, failure rates and maintenance economics for specific task/equipment combinations. Avoid underwriting arbitrary robots before one cell has proven unit economics.

### Why DNB and this hackathon

DNB already leases equipment, including production machinery. Piecework proposes an output-linked commercial layer around that capability, rather than assuming such a DNB product already exists. [DNB Equipment Leasing](https://www.dnb.no/en/business/financing/leasing/equipment?la=EN)

The live demonstration is memorable: a robot completes a job and its repayment counter advances. An empty motion does not earn money.

**Commercial kill question:** who absorbs idle time and rejected work? The SDK can confirm joint positions, but neither those positions nor one depth sensor establish industrial product quality or prevent a colluding operator from recycling the same part.

## Favourite: UNBUILD — 24–48 hour MVP

### The narrow promise

Demonstrate this chain end to end:

**Buyer commitment → human-accepted batch → observed inventory → borrowing limit → one simulated disbursement.**

Use four labelled matte blocks representing four reclaimed panels. A fixed rack assigns each block a bay. A laptop contains a fictional buyer order and a pre-approved price. The operator plays the inspector and explicitly accepts the batch. No AI identification, automatic structural grading or real money is required.

This is an inventory-backed advance demonstration. The longer-term company can finance earlier recovery milestones only after inspection, buyer and credit relationships exist.

### Physical build and port placement

| Board location | Module / instance | Purpose |
|---|---|---|
| 3 | `icm40608` / `imu` | Detect movement of the fixture relative to its calibrated pose |
| 4 | `button` / `accept` | Record the operator's batch acceptance |
| 5 | `neopixel` / `status` | Show eligibility and review states |
| 7 | `distance_sensor` / `rack` | Observe four regions of the 8×8 grid |
| 9–10 | `st7789_tft` / `screen` | Show count, state and available advance |
| 8 | Built-in USB-C, reserved | USB data and power connection |
| 12 | Reset, reserved | Never place a module here |
| 1, 2, 6, 11, 13, 14 | Unused | No additional hardware needed |

Use a cardboard or foam-board stand to fix the whole board facing a four-bay target. Start around 25–40 cm away and choose bay dimensions from the measured field of view. Mounting must keep the sensor's optical path clear and the button accessible; the laptop is the audience-facing display if the on-board screen faces the rack. Use high-contrast depth steps, not colour classification. Needed accessories are a USB data cable, stand, tape and four matte blocks.

Placement-only manifest for the eventual build:

```yaml
name: unbuild
board: 14port
feedback: false
modules:
  - {id: icm40608, instance: imu, port: 3}
  - {id: button, instance: accept, port: 4}
  - {id: neopixel, instance: status, port: 5}
  - {id: distance_sensor, instance: rack, port: 7}
  - {id: st7789_tft, instance: screen, ports: [9, 10]}
```

The proposed layout passed the installed SDK's in-memory placement validator. It has **not** been compiled, flashed or tested on physical hardware. This file is the research specification, not a completed implementation.

### Board responsibilities

1. Run the SDK-generated C++ firmware. The ToF driver already captures an 8×8 grid at approximately 15 Hz; the IMU polls at 100 Hz.
2. Every 200 ms, use `rack.getDistanceGrid(int16_t[64])` to emit a custom JSON event named **`rack_frame`** containing the 64 depths, emission sequence and board uptime. Guard startup with `rack_ok` and `rack.isReady()`. Keep the event below the protocol's 4096-character line limit.
3. Emit the existing `accept`, `imu_shake`, `imu_tilt` and `imu_orientation` events. Detecting movement invalidates the current calibration and requires operator review. Pressing the button may itself shake the stand, so record acceptance first and wait for a stable post-press observation before making credit available.
4. Implement a new **`unbuild_status`** action in `code.loop` to update the on-board amount, state and LEDs, acknowledging with `atechDone()`. This is application code to write: the display has **no built-in laptop action**. The existing `status_fill` action is available for LED feedback.
5. Use `millis()` scheduling rather than blocking delays. Redraw the TFT at roughly 5 Hz, not every loop. If laptop heartbeats cease for two seconds, show **OFFLINE / NO NEW ADVANCE**.

The default distance event only reports the nearest zone every 500 ms; it cannot count four rack positions. The custom grid event is essential.

### Laptop responsibilities

Use one Python process with `from atech import Board` and one `Board.connect()` session. Consume events in a worker, send commands sequentially with `board.call()`, and keep UI refreshes outside the serial reader. A simple local desktop dashboard is sufficient; no remote machine, cloud inference or mobile app is needed.

- **Calibrate:** capture the empty rack, then each occupied bay. Select four separated regions of the grid and estimate a robust depth for each. Treat zero, negative and implausible readings as unknown; the built-in minimum-distance sentinel `32767` means no object in range. Use calibrated depth thresholds with hysteresis and one second of stable observations.
- **Accept:** the operator selects a pre-entered batch and price, verifies labels and condition, then presses the physical button. Store that human assertion separately from measured occupancy. A steady hand or substituted object could fool occupancy detection; human acceptance and custody procedures remain necessary.
- **Calculate:** `borrowing_limit = eligible_present_items × accepted_unit_price × advance_fraction`. Demo terms are four items, NOK 1,000 each and a 70% advance fraction: maximum NOK 2,800. These numbers are illustrative, not a proposed lending offer.
- **Disburse:** require an explicit laptop confirmation. In one SQLite transaction, record lender cash decreasing, contractor cash increasing and the corresponding loan receivable. Store a unique disbursement ID so a repeated click cannot pay twice. Show all balances as **SIMULATED NOK**.
- **Reconcile:** before disbursement, missing items reduce the limit. Afterwards, removal creates an exception and freezes further advances; it does not magically recover money already paid. Replacing a previously funded item does not create new financing eligibility.
- **Explain:** display observed occupancy, inspector acceptance, borrowing limit, amount already drawn and remaining availability as distinct fields. Unknown readings, moved fixtures and lost USB communication prohibit a new draw.

Keep a trace of the batch, operator confirmation, calibration version, observations and transaction ID. A hash of the trace can identify the exact evidence used; it does not make an unsecured sensor tamper-proof.

**Freshness limitation discovered in the driver:** `isReady()` means at least one frame has arrived. The public getter exposes neither a capture timestamp nor a frame counter, and a running board can keep reporting cached data after a sensor fault. Emission timestamps prove host communication, not fresh ranging. Before any real financial use, extend a custom driver to report capture freshness and sensor status. For the hackathon, show this limitation and perform a live empty/occupied challenge before accepting the batch.

### Three-minute judge demo

**0:00–0:25 — The business problem.** Show four miniature reclaimed panels beside an empty rack. “A buyer wants these for NOK 4,000. Recovering them takes labour today. Can we fund the contractor against accepted stock?” The purchase order exists, but available credit is zero.

**0:25–1:00 — Physical evidence.** Place the four blocks in their bays. The dashboard fills four occupancy tiles. Perform the inspection role and press Accept. After the readings settle, the screen shows a NOK 2,800 borrowing limit and green LEDs.

**1:00–1:30 — Let the judge challenge it.** Invite the judge to remove one block before disbursement. Available credit falls to NOK 2,100. Return it and wait for stability; the limit recovers. Explain that the sensor verifies presence within this fixture, while identity and quality were human-approved.

**1:30–2:00 — The bank transaction.** Confirm the NOK 2,800 simulated advance. The lender's cash falls from NOK 10,000 to NOK 7,200; contractor cash rises from zero to NOK 2,800; loan outstanding becomes NOK 2,800. Click again: no duplicate transfer.

**2:00–2:35 — The uncomfortable case.** Remove one financed block. The system shows a NOK 700 collateral shortfall and freezes further advances. The paid contractor balance stays unchanged. This demonstrates an actual exception workflow rather than pretending a sensor can reverse a payment.

**2:35–3:00 — The company.** “Our first customer is a reclamation operator with a committed buyer. We help its bank finance verified recovery milestones. The long-term asset is the evidence of what buildings yield and what those components actually sell for.”

### Build sequence and acceptance gates

- **Hours 0–4:** install/download the first PlatformIO toolchain, validate the manifest, build and flash, inspect `atech check`, then test the jig. The SDK warns the initial compiler download occupies about 2.4 GB. Do this before depending on venue Wi-Fi.
- **Hours 4–12:** implement grid events, calibration, four occupancy states and loss-of-communication handling. Independently build the fictional order, acceptance and ledger screens.
- **Hours 12–24:** connect the hardware to credit calculation, add disbursement deduplication, and rehearse the complete demo. This is the minimum deliverable.
- **Hours 24–36:** improve sensor fault visibility, exception explanations, screen readability and fixture stability. Validate sensor capture freshness if time permits; do not conceal its absence.
- **Hours 36–48:** repeated live trials, reset procedure and pitch practice. Keep a clearly labelled recorded demonstration as backup, never represent recorded telemetry as live.

Acceptance checks: all bay combinations behave consistently; brief hand occlusion cannot directly trigger a draw; a moved fixture requires recalibration; USB loss prevents a draw; pressing Accept repeatedly does not mint new inventory; a repeated disbursement request leaves balances unchanged; removing funded inventory raises an exception without rewriting history.

### Riskiest technical assumption and fallback

**The 8×8 ToF grid must reliably distinguish four occupied bays in the actual fixture and venue lighting.** Resolution, reflections, bay boundaries and hands entering the field may defeat an attractive-looking layout.

Test this before polishing the dashboard: exercise all 16 occupancy combinations and repeat add/remove cycles with independent human labels. Seek no incorrect stable counts in a 50-cycle rehearsal, while recognising that this is a demo gate, not evidence of production reliability.

If four bays are unreliable by hour four, switch to **one deliberately constrained inspection bay**, scanning operator-labelled items one at a time. Preserve the order → acceptance → advance demonstration, but drop the claim of continuous multi-item collateral monitoring. That fallback must be labelled honestly; it cannot detect later removal from an unobserved store.

## Wild card: The Robot That Buys Itself

An SO-101 runs a tiny paid service—sorting game pieces or stamping cards. Each completed job sends simulated revenue to three pots: electricity/service, equipment debt and a replacement robot fund. When the debt counter reaches zero, it throws itself a retirement party; when the replacement fund fills, it submits an order for its successor.

**Pitch:** “The first bank account whose customer arrives in a shipping crate.” Use `robot_arm`, `distance_sensor`, `button`, `neopixel` and `st7789_tft` on Piecework's board layout. A human-owned company remains the actual account holder. The outrageous startup thesis is a fleet that finances its own expansion from verified work—not a claim that a robot can legally own a bank account.

## Research notes and boundaries

Inspected `atech list-modules`, `list-boards --json`, `guide`, and `info` for all 15 runtime modules, plus bundled documentation, board manifests, product metadata and relevant driver source. Validated all three proposed module layouts in memory without generating project files. No hardware or VM was accessed.

- [SDK guide](../.venv/Lib/site-packages/atech/docs/guide.md): code generation, USB-only SDK transport, independent software I²C buses, power rules and build workflow.
- [USB protocol](../.venv/Lib/site-packages/atech/docs/protocol.md): events, custom actions, acknowledgements, single serial owner and line limits.
- [14-port board](../.venv/Lib/site-packages/atech/catalog/data/boards/14port.yaml): reserved ports, legal adjacent pairs and UART ports 3/4.
- [ToF module](../.venv/Lib/site-packages/atech/catalog/data/modules/distance_sensor/module.yaml) and [driver](../.venv/Lib/site-packages/atech/catalog/data/modules/distance_sensor/vl53l5cx.cpp): 64-zone access, default nearest-distance event, one hardware bus and cached-reading limitation.
- [Microphone module](../.venv/Lib/site-packages/atech/catalog/data/modules/microphone/module.yaml): RMS events and audio sampling; no claim of calibrated sound-pressure measurement.
- [Simulation guide](../.venv/Lib/site-packages/atech/docs/sim_assets.md): exported assets include placeholder physics; a simulated ToF's default single ray is not an 8×8 sensor test.

Camera, load cell and soil sensor appear as product simulation assets but have no bundled firmware modules. None of these pitches depends on pretending those products have working runtime APIs. The proposed bank integrations, industrial assurance and market demand remain to be validated with partners.
