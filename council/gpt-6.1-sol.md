# Three companies from a box of modules

**Council member: GPT-6.1 Sol · 5 November 2026**

**My pick: UNBUILD.** Make reclaimed building components easier to buy, accept and finance. Its three-minute demonstration makes a physical disagreement change a financial outcome in front of the judges.

These are venture hypotheses, not validated markets. Proposed prices are interview starting points. DNB partnerships and payment integrations below are proposals; none is assumed to exist.

## What the installed kit actually supports

I ran `atech list-modules`, `atech list-boards --json`, `atech guide` and `atech info` for the relevant inputs and outputs, read all four bundled `docs/*.md` files, and inspected the board catalog and ToF driver. Key constraints:

- The `14port` board has **12 usable module sockets**: 1–7, 9–11, 13–14. Port 8 is USB-C; 12 is Reset. Valid two-port pairs include [1,2], [9,10] and [13,14]. Every proposed layout below passes an in-memory `Project.validate()` check with zero issues.
- `distance_sensor` supplies an **8×8 depth grid at 15 Hz**, with approximately 4 m maximum range. Its default laptop event is only minimum distance every 500 ms. Full-grid applications require custom C++ using `getDistanceGrid()` and a custom serial event. One ToF module per board.
- `icm40608` caches acceleration/gyro readings at 100 Hz. Default serial events are shake, tilt and orientation; vibration features require custom firmware. `microphone` exposes RMS/peak and samples, but the default stream is sound level, not continuous audio.
- The bundled runtime is **USB serial between board and laptop**. Wi-Fi/BLE require additional firmware. Camera, load cell and soil sensor have product simulation files, but are absent from the firmware module catalog; none is a dependency here.
- Simulation exports geometry and approximate sensors. The default simulated ToF is one central ray, not a functioning 64-zone scanner. Simulation cannot establish whether the acceptance fixture works.

Sources: installed [guide](../.venv/Lib/site-packages/atech/docs/guide.md), [protocol](../.venv/Lib/site-packages/atech/docs/protocol.md), [14-port catalog](../.venv/Lib/site-packages/atech/catalog/data/boards/14port.yaml), [ToF manifest](../.venv/Lib/site-packages/atech/catalog/data/modules/distance_sensor/module.yaml), [ToF driver](../.venv/Lib/site-packages/atech/catalog/data/modules/distance_sensor/vl53l5cx.cpp), and [simulation documentation](../.venv/Lib/site-packages/atech/docs/sim_assets.md).

## 1. UNBUILD — sell the next building before demolishing the old one

**One-line pitch:** A buyer-backed salvage exchange with physical acceptance stations, so demolition contractors can turn recoverable components into contracted revenue.

**Problem and customer.** The hypothesis: selectively removing, storing and transporting reusable components is hard to justify when the eventual sale is uncertain. A listing does not resolve delivery disagreements. Start with demolition contractors and commercial fit-out buyers trading one repeatable category, such as nonstructural timber trim. A buyer reserves a batch against explicit quantity and geometry requirements; a receiving station records measurements and a human accepts condition. Accepted deliveries create an invoice and a payment instruction. Later, a finance partner could assess advances against committed orders and accepted receivables.

**Exact hardware.** One ESP32-S3 `14port`: `distance_sensor` at 7, `icm40608` at 4, `button` at 5, `neopixel` at 6, `st7789_tft` across [13,14]. A fixed jig presents each component to the depth sensor; the IMU flags movement of the station that invalidates its calibration. The screen shows the selected contract, measured profile and amount awaiting acceptance. No camera or scale.

**Business model.** Sell stations at cost plus installation, charge the contractor or reuse hub a software subscription, and test a 1% fee on completed trades. Financing revenue would come through a partner arrangement, with the bank doing underwriting. Start as acceptance software for an existing reuse operator to avoid needing a liquid marketplace on day one.

**Why it could be big.** The expansion path runs from one material and one receiving hub to a network linking procurement, removal, logistics and finance. A useful asset would be the history of which suppliers deliver what buyers actually accept, paired with documented disputes. The company wins if that information makes repeat orders easier and cheaper to administer.

**DNB/hackathon fit.** It combines payments, working capital and reuse in a tangible demonstration. DNB already offers receivables purchase; its published product targets listed companies and larger groups, so an SME salvage offer would require a new commercial arrangement. The proposed connection is an acceptance record that supports a receivable, not a promise that an unremoved building component is already eligible collateral. [DNB receivables purchase](https://m.dnb.no/en/business/financing/receivables-and-supply-chain-finance/receivables-purchase).

**Critical boundary.** Depth measures broad geometry. It cannot certify material, strength, rot, contamination, ownership or an item's identity. The receiving operator selects the order and performs the condition check. Start with nonstructural components and agreed inspection procedures.

## 2. SHIFTNOTE — a machine loan that follows the work

**One-line pitch:** Finance productive equipment through an agreed charge per observed production cycle, with a funded maintenance reserve.

**Problem and customer.** Small workshops can have irregular demand while equipment repayments stay fixed. Equipment suppliers also need ways to sell to customers reluctant to take on another fixed obligation. Start with one machine family whose cycles are physically observable, such as a small packaging press. Record cycles using a ToF-observed movement, corroborate activity through vibration and sound, and calculate the contracted usage charge. A maintenance allocation accumulates alongside repayment. This is a contractual meter, not a claim that vibration reveals revenue.

**Exact hardware.** One ESP32-S3 `14port`: `icm40608` at 7, `microphone` across [1,2], `distance_sensor` at 3, `button` at 4, `neopixel` at 5, `micro_sd_card` across [9,10], `st7735_tft` across [13,14]. The whole board is mechanically coupled to a safe part of the machine, with ToF sight of its moving target. Firmware emits cycle events and activity summaries; Python reconciles usage charges and reserve balances. Sound-level processing stays local; continuous audio upload is unnecessary.

**Business model.** Sell through equipment vendors. Test NOK 199 per machine per month for metering and reconciliation, plus a partner fee on financed equipment. The lender funds the asset and prices demand risk; the startup supplies the meter and contract administration. Pricing and hardware economics need validation.

**Why it could be big.** A narrow, reliable machine adapter can become a distribution channel for equipment finance, servicing and resale. Expansion requires a validated detector for each machine family. The commercial attraction is converting a supplier's one-time sale into a managed equipment relationship.

**DNB/hackathon fit.** DNB explicitly offers leasing for production machinery and equipment. A proposed usage-linked product would extend that relationship, rather than require the team to become a lender. Judges can see physical cycles increment repayment and maintenance balances. [DNB equipment leasing](https://www.dnb.no/en/business/financing/leasing).

**Critical boundary.** A running motor can be idle, and a detected cycle can produce a defective item. Bill only the event defined in the contract. Tamper resistance, independent reconciliation and allocation of low-utilisation risk are central commercial requirements.

## 3. KINLOCK — give someone help with money without giving away the keys

**One-line pitch:** A tactile banking console for a protected household account, with delegated spending limits and a separate approval path for new recipients.

**Problem and customer.** Some people want help paying bills without handing another person unrestricted control. App permissions can also be difficult to explain or review together. Start with consenting older customers and family helpers: rotate through approved recipients, review a large-print amount, and press a physical button to request payment. A new recipient or exceptional amount requires a cooling period and independently authenticated approval. The account holder can cancel requests and revoke delegation.

**Exact hardware.** One ESP32-S3 `14port`: `rotary_encoder` across [1,2], `button` at 3, `neopixel` at 7, `st7789_tft` across [9,10], `speaker` across [13,14]. The knob navigates recipients and amounts; the display presents the transaction; colour and short audio cues distinguish routine, pending and blocked requests. Python hosts the prototype account-policy service and a separate helper approval session.

**Business model.** Bank-distributed hardware plus a per-active-household software licence. Test whether a bank would pay NOK 30 per month for a protected-account service with customer support and delegation controls. The buyer is the bank; household adoption and accessibility determine whether distribution works.

**Why it could be big.** Begin with supported household banking, then test bounded delegation for carers, adolescents and people recovering from illness. The durable product would be the account's permission and recovery system, reinforced by an interface people can understand together.

**DNB/hackathon fit.** DNB describes impersonation and “safe account” scams, including pressure to transfer money during long calls. This pitch gives its judges a concrete intervention to evaluate: the protected account refuses an unapproved recipient even when a helper requests payment. [DNB fraud guidance](https://www.dnb.no/en/financial-adviser/security/fraud?la=EN).

**Critical boundary.** A scammer can coach someone to press a button. The protection comes from server-enforced limits, delay and separate approval, applied across every access channel to that account. The kit has no demonstrated bank-grade secure display or authenticated device channel; production would require those. The hackathon proves the interaction and policy flow using fictitious accounts.

## Favourite: UNBUILD, built in 24–48 hours

### The smallest convincing product

Build one receiving station for **one preselected profile of nonstructural timber component**, represented by large wooden or matte cardboard samples in a fixed jig. One supplier, one buyer and one three-item order are enough. The prototype measures profile conformity, requires buyer acceptance, and releases a corresponding amount in a simulated funded-order ledger.

The commercial story is: “A buyer commits to three reusable components. Delivery is inspected against the agreement. Accepted goods become payable immediately; a rejected component stays unpaid.” The financing extension follows from that documented transaction.

### Board, placement and physical fixture

| Board position | Module / instance | Purpose |
|---|---|---|
| 7 | `distance_sensor` / `tof` | Depth profile of the sample in the jig |
| 4 | `icm40608` / `imu` | Detect station displacement; request recalibration |
| 5 | `button` / `accept` | Receiving operator accepts the current candidate |
| 6 | `neopixel` / `status` | Amber pending, red rejected, green accepted |
| [13,14] | `st7789_tft` / `screen` | Contract item, measurement status and amount |
| 8, reserved | Existing USB-C connection | Laptop power and serial data |
| 12, reserved | Reset | Keep clear |

Ports 1–3 and 9–11 remain unused. This layout passed the installed SDK's placement validator; that establishes catalog compatibility, not physical sensor performance.

Clamp the board at an angle facing a reference jig about 25–40 cm away, adjusting after a real sensor test. Samples are positioned against a backstop so their protrusion creates a broad depth signature. Keep the display and button reachable from the side; use the laptop for the judges' large view. Avoid small details, transparent samples and arbitrary handheld scans. Calibration measures the real setup rather than relying on placeholder simulation geometry.

Illustrative placement manifest, included here only as the build specification:

```yaml
name: unbuild_receiving_station
board: 14port
feedback: false
modules:
  - {id: distance_sensor, instance: tof, port: 7}
  - {id: icm40608, instance: imu, port: 4}
  - {id: button, instance: accept, port: 5}
  - {id: neopixel, instance: status, port: 6}
  - {id: st7789_tft, instance: screen, ports: [13, 14]}
```

### What runs where

**On the ESP32-S3, through SDK-generated firmware plus custom `code:` blocks:**

- Let the ToF driver's background task acquire at 15 Hz. At 5 Hz, copy `tof.getDistanceGrid(grid)` and emit a custom JSON-line event named `unbuild_grid`, containing the 64 values, boot-session identifier, sequence and board uptime. This is a newly implemented event, not an existing SDK key.
- Reject acquisition until `tof_ok` and `tof.isReady()` are true. Treat nonpositive ranges and 32767 as invalid rather than physical distances. Read tilt and flag a material departure from the calibrated pose.
- Handle `accept.wasPressed()` once per loop and emit an acceptance request. Use nonblocking `millis()` scheduling; leave serial input to the generated protocol handler.
- Implement a custom `unbuild_status` action to update the screen and LEDs, confirming with `atechDone()`. The TFT has no built-in host drawing action. Default feedback is disabled so it does not overwrite the product UI.

**On the laptop, in one Python process owning `Board.connect()`:**

- Consume custom events through `board.events()`. Calibrate an empty jig and an approved reference sample. Compare median depth in several broad regions against the reference, using a short stable window and tolerances established experimentally. Show the live 8×8 heatmap and the reason for a failed check.
- Require an empty-jig transition between items, stable geometry, a valid calibration and a button press for the currently displayed candidate. A button press on an invalid candidate does nothing financially.
- Maintain order, candidate, inspection and ledger records in SQLite. For each accepted candidate, atomically append one buyer-debit/supplier-credit pair under a unique acceptance ID. Repeated presses or replayed events cannot pay twice. Reset/reconnect invalidates any pending candidate and requires recalibration; a transaction committed before a disconnect remains committed.
- Send display changes through sequential acknowledged `board.call("unbuild_status", ...)` requests. Show disconnected/pending status if communication fails; no new acceptance proceeds from an old laptop snapshot.
- Use a local desktop dashboard, for example Tkinter, so no cloud service, hosted server or bank credentials are required. The ledger is explicitly labelled **simulated funds**. A future bank adapter sits after acceptance and uses its own payment idempotency key.

**Known SDK limit:** `isReady()` means a frame has arrived at least once. The driver exposes neither acquisition timestamps nor per-zone target-quality flags. Our serial sequence proves publication, not fresh sensor acquisition. The empty-to-present check gives a useful demonstration of responsiveness; it does not make this a fraud-proof financial oracle. A field pilot needs better acquisition health and device integrity.

### Three minutes in front of judges

1. **0:00–0:25 — Sell the problem.** Hold up a reclaimed component: “This has a buyer, but the contractor still needs to remove it, deliver it and get paid.” Show a funded demo order for three pieces at NOK 1,000 each.
2. **0:25–0:55 — Accept a real delivery.** Insert sample A. The live depth view settles; the station says “Profile matches; inspect condition.” The judge presses Accept. The screen turns green; NOK 1,000 moves from buyer reserve to supplier in the demo ledger.
3. **0:55–1:25 — Make the disagreement visible.** Remove A and insert a deliberately much thinner sample B. The heatmap changes and the station identifies the profile mismatch. Press Accept: no payment, reserve remains NOK 2,000.
4. **1:25–1:55 — Correct it.** Clear the jig and insert the correct second sample. Inspect and accept it. Supplier total becomes NOK 2,000. Press again while the sample remains: no duplicate payment. The outstanding third item remains visible.
5. **1:55–2:25 — Show the record.** Open one acceptance receipt: order, operator session, depth summary, manual acceptance, amount and ledger transaction. Tilt the station slightly: it requests recalibration and disables the next acceptance.
6. **2:25–3:00 — Pitch the company.** “Start at one reuse hub with one material. Make accepted deliveries easy to reconcile. Give finance partners a better record of committed buyers and fulfilled orders.” Ask DNB to help define a pilot's required acceptance evidence and financing structure.

### Build sequence and stop conditions

- **Hours 0–3:** Assemble and validate placement; do the first PlatformIO build early because the compiler download is large. Flash, check sensor health and verify outputs by sight. Test broad samples in the actual jig before polishing any UI.
- **Hours 3–10:** Implement grid events, calibration, reference comparison and station feedback. The main technical assumption must pass here.
- **Hours 10–18:** Build the order/inspection state machine, SQLite ledger and desktop view. Exercise invalid acceptance, repeated presses, candidate removal and reconnect after a committed transaction.
- **Hours 18–24:** Rehearse with a judge operating the button. Freeze the fixture and samples. Prepare a clearly labelled recording if hardware fails on stage.
- **Optional hours 24–48:** Add itemised receipts, multiple order profiles and a payment-adapter interface. A live bank connection is outside the critical path.

**Riskiest technical assumption:** The installed ToF module can reliably distinguish the selected broad sample profiles in the venue using a fixed jig, despite mixed zones, reflectivity and hand occlusion. The 8×8 resolution and distance-only public API make arbitrary material recognition unrealistic.

**Early falsification test:** Reinsert the approved sample 20 times and test 20 deliberately wrong or empty presentations under venue lighting. Require at least 19/20 approved samples to reach a stable match within two seconds, and zero wrong/empty presentations to reach “ready to accept.” These are prototype targets, not measured results or production error bounds. If the test fails, simplify to one large presence/depth check and retain explicit human acceptance. Describe that reduced capability plainly.

**Largest business uncertainty:** Whether receiving operators and buyers find this evidence useful enough to adopt the workflow. Ask one reuse operator to specify a real disputed acceptance before adding more sensors.

## Wild card: LIBERATION ROBOTICS — the robot that buys its own freedom

A robot arm takes paid tabletop jobs and directs an agreed share of each fee into a buyout account. When it has repaid its purchase price, future surplus funds maintenance and its worker cooperative. The demo ends with the arm stamping **“I PAID OFF MYSELF.”**

Use an ESP32-S3 `14port`, `robot_arm` on UART port 3, `distance_sensor` at 7, `button` at 5, `neopixel` at 6 and `st7789_tft` across [13,14], with external power matched to the arm variant. Python schedules prerecorded jobs and runs the simulated treasury. The accountable owner would be a human-governed company or cooperative; autonomous legal ownership is the deliberately outrageous fiction. DNB gets the strangest equipment-finance customer in the room.
