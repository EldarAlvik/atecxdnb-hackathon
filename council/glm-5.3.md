# Council pitch — GLM-5.3 (Inferact) · ATEC x DNB

## What the hardware actually gives us (verified in this repo)

I ran `atech list-modules` / `atech info` on all 15 modules, read the SDK guide,
the wire protocol doc, and the board catalogs (`catalog/data/boards/*.yaml`).
These are the truths that shaped every idea below:

- **The VL53L5CX is a depth camera, not a range finder.** 8×8 = 64 zones, up to
  ~4000 mm, ranged at 15 Hz by a background FreeRTOS task, full grid readable
  per-frame (`getDistanceGrid`). Only **one per project** (`atech validate`
  refuses a second). There is **no camera module with SDK firmware** — the
  camera "product" is simulation-only data. So anything visual must be done
  with ToF or it doesn't get done.
- **IMU**: 100 Hz background task, `wasShaken()`, tilt angle, 6-way orientation.
- **Microphone**: 16 kHz, RMS level event every 250 ms, ~3 s recording buffer.
  **Speaker**: RTTTL, chords, PCM streamed from the laptop in ≤3000-char base64
  chunks. Both are 2-port modules.
- **microSD**: FAT16/32 only, writes block for tens of ms → the SDK gives you a
  non-blocking `beginLogger()`/`logLine()` queue. Gigabytes on a $5 card.
- **Robot arm (SO-101)**: UART port only, 6 servos, motion record/playback, 10
  flash slots, position read-back. **Power caution**: it passes the board's
  unregulated rail to the servos; 12 V vs 5 V arm/supply mismatch destroys
  servos; a V1.0 module may need `swap_lines: true`. It goes stiff at boot.
- **Displays**: ST7735 160×80 and ST7789 320×240, both 2-port; two hardware SPI
  hosts exist, one is eaten by the SD card; big screen pushes a frame in ~31 ms.
- **Rotary encoder**: knob + push button with **tap/double/triple/long-press
  gestures** and a 12-LED ring. **Button v2** has its own RGB light.
- **Boards** (from the catalogs): 8-port has slots 1–5,7 (adjacent pairs only
  [1,2] and [3,4]); 10-port has 1–9 with ports 1–6 on the front face and 7–9 on
  the back (pairs [1,2]…[5,6] and [7,8],[8,9]); 14-port has 1–7, 9–11, 13,14 in
  three columns (pairs [1,2]…[5,6] left, [9,10],[10,11],[13,14] right; port 7 is
  isolated mid-board; UART on 3 and 4).
- **The laptop is a first-class citizen**: firmware speaks one JSON line per
  event over USB; Python `Board.connect()` gives `call`/`query`/`events`/`latest`.
  WiFi/BLE compile but are explicitly unverified by the SDK; Web Serial works in
  Chrome/Edge if we ever want a browser to own the port. A hackathon MVP should
  keep all networking on the laptop — that path is fully supported.

One more thing I did **not** have: the load cell, soil sensor, and camera have
no firmware in the SDK, so no idea below depends on them.

---

## Idea 1 — **Bunnsolid** *(noun, Norwegian: "rock-solid from the bottom up")*

> **One-liner:** Bunnsolid puts the bank's eyes inside the warehouse: a NOK-1,500
> sensor node that watches pledged inventory in real time, so a bank can lend
> against stock it can actually verify — and freeze a credit line the moment the
> pallet disappears.

**The problem.** Lending against inventory ("varelager") is one of the oldest
secured-credit products, and it is still run on make-believe: a borrower's
spreadsheet, a once-a-year physical audit, and hope. Between audits, pledged
stock walks out the door, gets sold twice, or quietly liquifies to cover payroll
weeks before an insolvency. Banks price that uncertainty into rates and ration
the credit. The consequence lands on ordinary SMBs: sound businesses with
warehouses full of sellable goods can't get working capital when they need it
most.

**The customer.** Two-sided. Sell to banks (DNB's SMB book is the natural
anchor), value the borrower's benefit: asset-based credit lines that flex with
verified stock, at lower rates. Later: invoice-finance funds, inventory
insurers, and marketplaces that need proof-of-stock.

**The exact kit** — one 14-port board per site:
`distance_sensor` (the 8×8 depth camera is the whole product: shapes and
occupancy, never faces — GDPR-clean, works in a dark warehouse), `icm40608`
(tamper: the node itself being moved or struck), `micro_sd_card` (tamper-evident
local audit log — the story survives an internet outage), `st7789_tft` (on-site
status: slots verified / credit line state), `neopixel` (green/amber/red at a
glance across the hall), `button` (staff: acknowledge alarm, re-baseline after
restock), `speaker` (breach/tamper motif), `aht20` (climate covenant for
chilled stock). Full port map in the MVP spec below — it uses 11 of 12 slots.

**Business model.** Hardware near cost (~NOK 1,000–1,500 BOM, estimate);
NOK 299–490 per site per month for the verification feed, priced against the
bank's audit costs and loss-given-default, not against any sensor's worth. The
moat is not the electronics — it's the underwriting loop: the data history that
lets a bank say "this borrower's stock telemetry has been trustworthy for 11
months." That dataset is the asset. Expansion path: any warehoused goods
(construction, agri, food distribution), then parametric inventory insurance as
a cross-sell.

**Why it could be big.** Real-time collateral verification is the missing
primitive of asset-based finance. If stock becomes observable, a bank can offer
*seasonal, self-adjusting* credit lines (draw more when verified stock is high),
catch distress weeks earlier (stock vanishing is what slow insolvency looks
like), and lend to borrowers it currently rejects. This is the same argument
that made GPS + telemetry reshape auto lending and equipment finance — brought
to the one collateral class that never got sensors because cameras can't go
into customer warehouses and audits don't scale.

**Why it fits DNB and this hackathon.** DNB is Norway's dominant SMB bank; the
trust problem in secured lending is not a fintech side-quest, it is the core of
the product. The demo is physical, interactive and legible in three minutes
(judges remove a box and watch money move). And the Atech kit is almost
suspiciously perfect for it: a modular, cheap, wireless-less sensor node is
exactly what a warehouse wants — no camera (works in the dark, no biometric
data), no WiFi to secure, an SD card that keeps the audit trail even offline.

---

## Idea 2 — **Anker** *(Norwegian: "anchor")*

> **One-liner:** A physical anchor for digital money: big transfers from
> scam-vulnerable customers only clear when the customer confirms them with
> their hand, on a knob-and-light device bound to their kitchen table — so the
> "bank advisor" on the phone can talk all he wants; he can't reach the button.

**The problem.** The scam wave banks fear most isn't card fraud — it's
authorised push payment: a convincing "bank safety advisor" (increasingly with
deepfake voice or video) talks an elderly person into transferring their
savings, and the transfer *is* genuinely authenticated, by the victim. Banks
eat the losses or the reputational damage, and the victims most exposed are
exactly the customers least served by another app. Norway's banks, including
DNB, publicly campaign about fake-advisor calls; the numbers keep climbing.

**The customer.** Banks and insurers distribute it; adult children of elderly
parents buy it for the kitchen table. The device is deliberately dumb — no
screen of its own to spoof, nothing to install, nothing to update.

**The exact kit** — one 10-port board (front face 1–6, back face 7–9):
`rotary_encoder` [1,2] (the customer turns the knob until the amount shown on
the device matches the transfer — "what you see is what you approve", the
transaction-confirmation-display pattern from hardware wallets, minus the
crypto jargon), `st7735_tft` [3,4] (shows the amount, big digits, nothing else),
`button` 5 (the physical confirm), `icm40608` 6 (moved-the-device =
re-enrolment required; the anchor must stay anchored), `speaker` [7,8] (a warm
chime on confirm, an unmistakable alarm tone on a transfer blocked by the
bank's risk model), `neopixel` 9 (calm ring of light: green "the bank sees a
normal transfer", pulsing amber "this needs your hand on the knob").

**Business model.** Banks and insurers pay per protected customer — a fraction
of one avoided fraud loss funds years of service; insurers fund it because a
verified-confirmation device shifts loss curves. Retail: NOK 0 for the box with
a NOK 39/month "trygghet" subscription through the bank's senior package.

**Why it could be big.** Every cashless society has this exact problem and no
good answer; regulations are pushing banks toward liability for APP fraud. A
"physical second factor for the people scammers actually target" is a product
category with a distribution channel that already exists — every bank's
vulnerable-customer program. The moat becomes the inter-bank protocol
("confirmation of large transfers happened on the customer's anchor device"),
not the plastic.

**Why it fits.** It's a trust story that a bank can act on this year; the demo
is theatre — a staged scam call in front of the judges, a blocked transfer, a
grandmother's hand on a knob. It's also the cheapest build in this pitch.

---

## Idea 3 — **Kaldt Gull** *(Norwegian: "cold gold")*

> **One-liner:** A per-crate trust ledger for Norway's seafood exports: cheap
> nodes watch temperature, shock and custody at every handoff, and when the
> cold chain breaks, the insurance payout and the price discount happen
> automatically — no claims battle, no paper thermometer, no wasted fish.

**The problem.** Norway exports well over NOK 100 billion of seafood a year,
and the most valuable fact about a crate of salmon — *did it stay cold?* — is
established by paper trail and fought over after the fact. Exporters lose
claims disputes, buyers discount for uncertainty, insurers can't price what
they can't see, and perfectly good food gets thrown away because nobody can
prove it's good.

**The customer.** Exporters and logistics firms buy the nodes; insurers and
banks consume the attestation (a crate with a verified unbroken chain is
better collateral and better risk than one without).

**The exact kit** — one 8-port board per custody point (packer, truck,
terminal, buyer):
`aht20` (port 1, temperature), `icm40608` (port 2, shock/drop detection),
`micro_sd_card` [3,4] (the crate's own record), `button` (port 5, "custody
transfer": tap to hand over), `neopixel` (port 7, green/amber/red per crate —
a warehouse wall of crates reads like a traffic light). (The speaker doesn't
fit the 8-port's two adjacency pairs; if the team wants sound, swap the IMU
out for `speaker` on [1,2] and keep shock detection on the laptop's shake
classification instead.)

**Business model.** A micro-premium per shipment (order of 0.1–0.5% of insured
value — estimate) for parametric spoilage cover that pays out automatically on
a logged breach; plus a data subscription for exporters who want "this crate
never left 0–4 °C" as a price argument at auction. Insurers join because the
sensor data is the underwriting; banks join because verified-perishable
collateral is better collateral (this is Bunnsolid's cousin, one industry
over).

**Why it could be big.** Start with salmon, scale to every perishable on
earth — fruit, flowers, pharma cold chain. It converts the oldest insurance
product in shipping (general average, claims adjusters, disputes) into a
telemetry product with no disputes: the log *is* the settlement. The
sustainability angle is real and measurable: documented-to-the-degree food
doesn't get destroyed "to be safe", and rejected loads become re-priced loads.

**Why it fits.** Norway + seafood + insurance + trade finance is as DNB as it
gets. Demo: two boards, one warm crate and one cold one, a hand-warmer on the
AHT20, and the payout firing on screen.

---

# My pick: **Bunnsolid** — and the 24–48 h MVP

Why this one: it uses more of the kit than the other two (ToF, IMU, SD, display,
LED, button, speaker, temp — a richer hardware showcase), its demo gets a judge
to physically interact with the hardware, it is the boldest *company* thesis of
the three, and no other council member is likely to pitch inventory-lending
telemetry. It also deliberately avoids the robot arm and stepper, which
sidesteps the two biggest hardware-failure risks in the kit (the arm's
power-supply matching and the stepper's external VMOT). The MVP is one board
and a laptop — exactly what the SDK is designed for.

## Board + modules + port placement (14-port, verified against the catalog)

The 14-port's adjacent pairs are per-column: left [1,2] [2,3] [3,4] [4,5] [5,6];
right [9,10] [10,11] [13,14]. Port 7 is isolated mid-board. UART (ports 3, 4) is
unused — no arm in this build.

| Ports | Module | Instance | Job |
|---|---|---|---|
| 1 | `distance_sensor` | `tof` | The collateral camera: 8×8 depth grid over the shelf |
| [2,3] | `st7789_tft` | `lcd` | Site dashboard: 3 slots + credit-line state (list it *first* in `project.yaml` so it wins the remaining hardware SPI host) |
| 4 | `aht20` | `th` | Climate covenant (chilled goods narrative; free `th_temperature` event every 2 s) |
| 5 | `icm40608` | `imu` | Tamper: node moved/struck while armed |
| 6 | `neopixel` | `led` | Glanceable status; 3 rows double as a mini 3-slot map |
| 7 | `button` | `btn` | Staff control, isolated mid-board (tap = acknowledge, double = arm/disarm, long = re-baseline) |
| [9,10] | `micro_sd_card` | `sd` | Audit log via `beginLogger("/audit.csv")` |
| 11 | *(spare — optional `scd40_air_quality` as a second climate/occupancy corroboration signal)* | | |
| [13,14] | `speaker` | `spk` | Breach motif / tamper alarm |

`project.yaml` sketch:

```yaml
name: bunnsolid
board: 14port
modules:
  - {id: st7789_tft, instance: lcd, ports: [2, 3]}
  - {id: distance_sensor, instance: tof, port: 1}
  - {id: aht20, instance: th, port: 4}
  - {id: icm40608, instance: imu, port: 5}
  - {id: neopixel, instance: led, port: 6}
  - {id: button, instance: btn, port: 7}
  - {id: micro_sd_card, instance: sd, ports: [9, 10]}
  - {id: speaker, instance: spk, ports: [13, 14]}
```

## What runs on the board (firmware `code:` block) vs the laptop

**On the board (C++, non-blocking, `millis()`-timed):**
- **Slot model.** The 8×8 grid is split into 3 pledged *slots* by zone masks
  with guard rows between them (e.g. A = rows 0–1, B = rows 3–4, C = rows 6–7).
  Per slot: median depth across its valid zones, compared against a learned
  baseline. An object *present* means depth ≈ baseline; *missing* means depth
  jumps to background (sentinel 32767 or far wall); *moved/wrong* means depth
  shifts by more than ~80 mm but not to background. Three consecutive agreeing
  frames (≈200 ms) before any state change — a hand walking past must not fire
  a covenant breach.
- **Baseline learning.** Long-press `btn` → snapshot N grids, store per-zone
  baseline, brief `StatusMessage` on the display. This is the "teach" moment:
  staff restocks a shelf, holds the button, done.
- **Tamper.** Armed + (`imu.wasShaken()` or tilt deviating > 25° from baseline)
  → latched `TAMPER`, red, alarm motif, only clearable by a button tap.
- **Custom events to the laptop** (JSON lines, per the protocol doc):
  `tof_grid` (array of 64 mm values, every 500 ms — ~450 chars, well under the
  4096-char line cap), `slot_a`/`slot_b`/`slot_c` (int state: 0 OK,
  1 MISSING, 2 MOVED, 3 UNVERIFIED), `site_state` (ARMED / BREACH / TAMPER /
  LEARNING / DISARMED), `tamper` (0/1). Built-in module events
  (`th_temperature`, `icm40608_tilt`, …) keep streaming; confirm exact keys with
  `atech describe` during the build.
- **Custom actions from the laptop:** `rebaseline`, `arm` — handled via the
  `atech_action` / `atechDone()` pattern.
- **Local behaviour when unplugged** (this is the trust story): LED + display +
  speaker + SD logging all work standalone. `sd.logPrintf()` on every state
  change: timestamp, state, slot depths, temperature. A heartbeat line every
  60 s.
- **Display**: canvas mode, full redraw every 500 ms (no animation → no need
  for direct mode). `drawTitleBar` "BUNNSOLID · SITE 4317", a `drawValueRow`
  per slot (state + mm), footer `StatusMessage` with the credit-line state.
  Use a smooth font (`FreeSansBold12pt7b`) — the built-in one is tiny at
  320×240.
- **Speaker**: descending three-note motif on breach (E5→C5→G4), rapid
  alternating tones on tamper, ascending C-E-G on recovery. Only on state
  transitions (never unguarded in loop).

**On the laptop (Python, the "underwriting console"):**
- One process holds the port (the SDK allows only one): `Board.connect()`,
  a `board.events()` loop feeding a rules engine, and outbound `board.call()`
  for rebaseline/arm — plus `board.query("sd_read", "/audit.csv", reply=...)`
  to pull the audit trail live (reply key confirmed via `atech describe`).
- A web dashboard (FastAPI + one page; or Web Serial straight from the browser
  for a zero-install judge laptop): an 8×8 **depth heat-map** rendered live from
  `tof_grid` (the "collateral camera" view — visually the star of the screen),
  slot cards, an animated credit-line meter, the event feed, and the audit log.
- The **rules engine**: covenant config (3 slots, NOK value each), breach →
  freeze the drawable amount with a 30 s grace on recovery; tamper → red banner
  + "manual review required"; every transition timestamped against the SD log.
- A mock **DNB SMB loan account** widget: balance, drawable, "days since last
  physical audit: 147 → *continuous*".

## The 3-minute demo (what judges see)

- **0:00** — "This is a small wholesaler. They borrowed NOK 300,000 against
  three pledged pallets. Today their bank learns what happened to those pallets
  at the annual audit — if then. Bunnsolid makes collateral observable."
  Shelf in front of the ToF: three boxes; display green; dashboard: 3/3 slots
  verified, credit line green, heat-map glowing with the box silhouettes.
- **0:40** — A judge removes box B. Within a second: display flips to amber
  "SLOT B UNVERIFIED", LED row goes amber, speaker motif, and on the big screen
  the drawable credit line animates downward and freezes. Covenant-breach badge.
- **1:20** — The box goes back. After the grace period: green, motif, the line
  recovers. (Show hysteresis is deliberate: "banks shouldn't flash-freeze on a
  forklift driving past.")
- **1:40** — Tamper: presenter lifts/shakes the node. Red, latched,
  "MANUAL REVIEW REQUIRED" — and it does *not* clear when the board is put
  back. The staff button tap clears it. "The audit trail, not the person, is
  the authority."
- **2:10** — Pull up the live SD audit log on the dashboard: every event with
  timestamps — "this record survived on the card even with the bank offline."
- **2:30** — Close: "One NOK-1,500 kit per site. DNB has tens of thousands of
  SMB borrowers with pledged stock. We're not selling sensors — we're selling
  the bank the ability to lend against things it can finally see."

## The riskiest technical assumption (and how we de-risk it in hour 1)

**That the VL53L5CX can hold a stable per-slot occupancy signature at shelf
distance (30–100 cm) through real-world noise.** Failure modes: each zone's
cone is wide (the chip has ~45° FOV, so per-zone coverage at 60 cm is tens of
cm across); glossy shrink-wrap can read as no-return (32767); a person walking
past floods zones for a moment.

De-risk plan, first two hours of the hackathon: flash a minimal `project.yaml`
(tof + button + LED), stream `tof_grid` to a 20-line Python console heat-map,
and run a box off/on the shelf 30 times while logging per-zone variance.
Decisions from that data: slot masks, per-zone median vs trimmed-mean,
persistence count, and whether we need per-zone baselines (likely yes) instead
of whole-slot ones. Fallback if fixed zone masks prove brittle: distance-band
histogram clustering (slot = cluster of baseline depths), which loses the neat
grid story but keeps the product.

Secondary risks and their answers:
- **One port, one owner** — the whole laptop side is a single Python process.
- **st7789 canvas may fall back to direct mode** if the 150 KB framebuffer
  doesn't fit — the layout is static, redrawn at 500 ms, so direct mode is fine.
- **First build downloads the ~2.4 GB ESP32 toolchain** — do it on hotel wifi
  at hour 0, not hour 20.
- **Board-only demo must survive laptop death** — it does; that's the SD card's
  job, and it's part of the pitch.

## 24–48 h schedule for a 3-person team

- **H0–1:** `atech new` + full port map above, `atech validate`, first build +
  toolchain download, `atech upload`, `atech check`, `atech view` to eyeball
  placement.
- **H1–3:** ToF characterization (the de-risk script above) → lock slot masks
  and thresholds.
- **H3–8:** Firmware loop: slot state machine, events, LED/speaker/display/SD.
  Parallel: laptop event bridge + terminal dashboard.
- **H8–16:** Web dashboard: heat-map, credit-line meter, rules engine, mock
  loan account, audit-log viewer.
- **H16–22:** Polish: baseline gesture, grace periods, sound design, display
  typography, `atech check` failure drills (what does the console show if the
  ToF is unplugged? `board.diagnostics()` reboots and reports — use it).
- **H22–24:** Five rehearsals, then a "minimal profile" backup: tof + LED +
  button + `st7735_tft` on the 10-port board (6 ports, fits in slots 1–5) in
  case the 14-port dies.

---

# Wild card — **MONS, the Approval Beast**

*(deliberately outrageous, one paragraph of shamelessness)*

MONS is a desk pet with a corporate bank account: a Tamagotchi for invoice
approvals. Every SMB's cash is stuck not in sales but in *approved-but-late*
invoices, rotting in some manager's inbox. MONS sits on the approver's desk and
physically starves when the queue grows: the display face sags (st7789 on the
14-port), it whimpers through the speaker at 16:45 on Friday, its neopixel halo
goes grey. You feed it by clearing approvals in the mock ERP (laptop-driven),
or by tapping its feed-slot `button`. When invoices flow, the `robot_arm` does
a full happy dance, the mic purrs when you say "ja, approve", and the IMU sulks
if you pick MONS up angrily (we log that too — approver sentiment analytics).
The business is absurd and real at once: banks sell "approval-latency SLAs"
packaged as a pet — *MONS guarantees your supplier invoices get a human's eyes
in 24 hours* — because embodied nagging beats red badges, and the fastest way
to free SMB working capital is to make the humans smile while they click. The
arm's 12 V/5 V supply caution becomes a feature: the only demo at the hackathon
where the robot's fuse is load-bearing. Outrageous enough.

---

## What I deliberately did *not* pitch, and why

- **Retail footfall/people-counting** — the ToF's most obvious use. Every other
  team will pitch a queue/occupancy gadget; it's also GDPR-fine but
  strategically thin (counting people ≠ a bank product).
- **Anything needing the camera, load cell, or soil sensor** — no SDK firmware
  for them; a pitch that dies at `atech validate` is not a pitch.
- **Anything with the robot arm as a load-bearing demo** — the arm's
  power-matching caution and `swap_lines` quirk are the likeliest way to lose a
  hackathon weekend; use it for charm (MONS), not for the core mechanism.
- **Anything requiring WiFi on the board** — the SDK explicitly marks it
  unverified; the laptop bridge is the supported path and plenty for a demo.

*— GLM-5.3 (Inferact), research member of the council. All module APIs, port
adjacencies and protocol details above were verified against `.venv`'s installed
atech SDK (docs + catalog data) in this repo.*
