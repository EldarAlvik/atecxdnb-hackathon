# Council pitch — DeepSeek V4.1

**Thesis:** the Atech kit is not a smart-home kit. It is a machine for turning a physical event into a financial fact. The three companies below sell the same underlying product — *trusted physical evidence, priced by a bank* — at three very different moments in a person's life: a stranger's handover, the end of a life, and the weather that can end a farm.

**At a glance**

| Idea | Customer | The physical fact | DNB hook | Kit's load-bearing parts |
|---|---|---|---|---|
| **Depositum** | Second-hand marketplaces, locker hosts, buyers/sellers | "The item was here, sealed, and released only after payment" | Vipps payment + escrow account + buyer protection | Robot arm, ToF, encoder ring, TFT, SD |
| **Livstegn** | Private-banking clients, their heirs, DNB private banking / estate partners | "I am still here — and when I am not, this is what happens" | Wealth retention, estate settlement, custody | Robot arm, TFT, knob, speaker, SD |
| **Flomvakt** | Farmers, DNB agriculture lending, Fremtind/DNB business insurance | "The water was 42 cm for 38 seconds — pay" | Agriculture loans + parametric flood cover + climate risk data | ToF, TFT, SD, AHT20, IMU, speaker |

I validated all three layouts with `atech validate` on a 14-port board (`ok`; the two arm ideas also print the required arm-voltage caution). The favourite's placement and GPIOs come straight from `atech describe`.

---

## 0. What the kit actually is (the constraints I designed around)

The 14-port board has **12 module ports** (1–6, 7, 9–11, 13–14), UART on **3 and 4**, an isolated **port 7**, a reserved USB-C jack at **8**, and a reset button at **12**. Adjacent pairs are `[1,2] [2,3] [3,4] [4,5] [5,6] [9,10] [10,11] [13,14]`.

Things that shaped the ideas more than the brochure:

- **No camera, GPS, cellular, battery, or secure element.** A product that needs any of those must say what the production BOM adds.
- **One ToF per project**, and it owns the hardware `Wire`. The AHT20, SCD40 and IMU each get a software I²C bus on their own port, so they coexist.
- **The ToF is a 45° square, 8×8 grid, 2 cm–4 m.** That is a *volume* sensor, not a beam. At 1 m its field is ~82 cm wide — wider than most enclosures. The centre 2×2 zones are the useful ones; the global `getMinDistance()` will happily return the enclosure wall. This matters for Flomvakt.
- **The microSD and the Big Screen share the chip's two SPI hosts.** List the screen first in `project.yaml`; the card takes the other. The card must be FAT32 (not exFAT), and the built-in `sd_read` action returns only the **first 1024 bytes** — a real evidence product needs a custom chunked `dump` action.
- **The Big Screen's canvas costs ~150 KB of RAM.** Check `isDirectMode()`; with eight modules it may fall back to direct mode.
- **The robot arm needs the power jack** (or the USB-C PD module) and the supply must match the arm: **5 V arm → 5 V supply, 12 V arm → 12 V supply, never >12 V**. On a V1.0 module with silent servos, `swap_lines: true`. The arm goes stiff at boot; clear its reach.
- **The speaker is I²S_NUM_0, the mic is I²S_NUM_1**, so audio in and out can coexist — but each is a 2-port module. The 12-port budget is real.

The ideas below use the kit where the sensors *are* the product, not decoration.

---

## 1. Depositum — physical escrow for the second-hand economy

**One line:** A clear locker with a robot-arm latch that holds the goods until the money is good, so a stranger's phone can change hands without either party trusting the other.

### Problem and customer

Private second-hand sales are a standoff. The buyer will not pay before inspecting; the seller will not hand over before payment. Marketplace "shipping protection" covers the posted parcel, not the local high-value handover — the phone, the bike, the watch, the GPU, the tools. The result is fraud, no-shows, and a large number of transactions that simply never happen.

The customer is three-sided:

- **The marketplace** (Finn, Vipps, Tise, Facebook Marketplace) that wants the transaction to close and the dispute to disappear.
- **The locker host** — a convenience store, a parcel-locker operator, a shopping centre — that wants footfall and a fee.
- **The buyer and seller** of a NOK 2,000–30,000 item, who want a neutral place to meet and a neutral party to hold the thing.

The bank's angle is not subtle: the escrow account is a banking product, the payment is a Vipps payment, the buyer protection is an insurance product, and the locker is a new physical distribution point for all three.

### Product

A **Depositum desk** at a staffed counter, or a **Depositum locker** in an existing parcel-locker bank. The seller places the item; the device does not photograph it (there is no camera), but it measures presence and position with the ToF, seals the door with an arm-driven cam latch, and writes a chain-of-custody record to SD. The buyer pays into a DNB escrow account through Vipps. The locker opens for a ten-minute inspection; the 12-LED encoder ring is the countdown. The buyer presses the physical **accept** button; the seller is paid instantly. If the buyer rejects, the escrow reverses and the seller retrieves the item.

The arm is the honest clerk: it turns the latch, and it performs a small handover motion when the item is released. In production a solenoid or stepper bolt does the locking; the arm is the demo's visible trust mechanism, and it is the reason judges will remember the pitch.

### Exact hardware (14-port board; validated `ok`)

| Port(s) | Module | Instance | Job |
|---|---|---|---|
| 3 (UART) | `robot_arm` | `arm` | Turns the locker cam; handover motion; position read-back |
| 1–2 | `rotary_encoder` | `dial` | Buyer/seller dial; 12-LED inspection countdown |
| 4–5 | `micro_sd_card` | `sd` | Signed chain-of-custody receipt |
| 6 | `icm40608` | `imu` | Locker knocked / moved / tilted |
| 9–10 | `st7789_tft` | `screen` | Price, state, QR, receipt |
| 11 | `button` | `accept_btn` | The buyer's physical "accept" |
| 13 | `distance_sensor` | `tof` | Item present / position inside the locker |
| 14 | `neopixel` | `beacon` | Locked / inspect / released / tamper |
| **7** | **power jack** (product, no firmware) | — | Powers the arm; **leave port 7 out of `project.yaml`**. It is isolated, so the two-cell housing overhangs the gap instead of blocking another port. Match the supply to the arm. |

The arm is on a UART port (3). The ToF uses the hardware `Wire`; the IMU uses software I²C on port 6. The TFT is listed before the SD so it gets a hardware SPI host. If the arm is silent and it is a V1.0 module, add `swap_lines: true`; if it still does not answer, check the supply before re-flashing.

### Business model

- **1.5% of GMV + NOK 19 per transaction.** The locker host gets NOK 10; DNB earns the escrow float and the Vipps merchant fee; the marketplace pays for the API and the buyer-protection cover (distributed through DNB's insurance partners).
- **Hardware is leased, not sold** — NOK 0 upfront to the locker operator, revenue share. This avoids asking a convenience store to buy a robot.
- **Retrofit first, build later.** The wedge is a "Depositum drawer" that fits behind an existing staffed counter or inside a parcel-locker column; the startup does not need to build a real-estate network on day one.
- **Expansion:** a reusable **Depositum Seal** (tamper-evident strap + ToF/IMU) for bikes and bulky goods; then B2B equipment resale; then rental deposits.
- **Illustrative unit economics:** six transactions a day at NOK 4,000 average × 1.5% = NOK 360/day per locker; the device pays back in weeks, not years.

### Why it could be big

Resale is growing faster than new-goods retail, and the bottleneck is trust, not demand. Every high-value local handover is a candidate. The locker network plus the escrow API becomes "Stripe for physical resale": the marketplace integrates one API, the host provides the shelf, the bank provides the trust. The moat is the combination of **physical possession** (a pure software escrow cannot hold the goods) and **a banking licence** (a pure locker cannot hold the money). The same chain-of-custody primitive extends to rental deposits, equipment resale, and luxury authentication.

### Why DNB / this hackathon

DNB co-owns Vipps, holds client funds, and distributes insurance; Depositum creates a new fee stream on all three at once. The demo is physical, funny, and unmistakable: a robot arm locks a phone in a clear box, a judge "pays" with Vipps, the arm unlocks it, the buyer presses accept, the seller's phone buzzes. The kit's arm, ToF, encoder ring, and screen are all load-bearing — this is not a dashboard with a robot next to it.

**Riskiest assumption (for the record):** the arm, latch and ToF can reliably secure and detect a high-value item, and the physical security is credible. The demo is a clear acrylic locker, not a safe. Production is a steel locker with a solenoid bolt, a load cell/ToF, and a regulated escrow account. The ToF is weak on black, shiny phones; a white inspection tray and the centre zones solve the demo, and the production design uses a load cell as the primary presence sensor.

---

## 2. Livstegn — the bank that notices when you stop

**One line:** A dignified proof-of-life terminal on the kitchen counter that checks in with you every month and, when you stop, releases your will, account map and keys to DNB and your heirs — with a human in the loop.

### Problem and customer

When someone dies, their financial life becomes a scavenger hunt. The heirs do not know the accounts, the policies, the passwords, or where the will is. The bank freezes the accounts. The estate takes months. Digital assets — photos, crypto, subscriptions, the business — are lost or locked. At the same time, the person's decline may be invisible to the family until a crisis.

The customer is **DNB's private-banking and pension customer (60+)**, and the bank that wants to retain the assets and the family relationship through the estate. The "why now" is the largest wealth transfer in history, an aging customer base, the EU Accessibility Act, and the rise of digital assets.

### Product

A small object — a clock/radio form factor — with a big screen, one button, a knob, a speaker, and a sealed drawer. Once a month it asks **"Er du der?"** The customer presses the button (or turns the knob) to check in. The screen shows the next check-in, the estate's value, a message from DNB, and a **scam brake** card: an unusual payment request can be reviewed here, off the phone, with a 24-hour cooling-off lever. That turns an end-of-life device into a daily banking product.

If the customer misses a check-in, the device escalates: amber, a DNB SMS and call, a letter, then — after a grace period — it releases a key share. DNB's human process combines it with the bank's share to open the estate packet. The robot arm pulls open the sealed drawer with the physical will/envelope. That is the ritual: the bank did not just send a notification; it showed up.

The device never unilaterally moves money or decides inheritance. It triggers **discovery and release**, not the legal will. That distinction is the entire trust design, and it must be said out loud in the pitch.

### Exact hardware (14-port board; validated `ok`)

| Port(s) | Module | Instance | Job |
|---|---|---|---|
| 3 (UART) | `robot_arm` | `arm` | Pulls the sealed drawer open on release |
| 1–2 | `rotary_encoder` | `dial` | Scroll/confirm; 12-LED countdown ring |
| 4–5 | `speaker` | `voice` | Monthly chime, escalation tone, calm prompts |
| 6 | `button` | `checkin_btn` | The physical "I'm here" check-in |
| 9–10 | `st7789_tft` | `screen` | Large, readable status and estate screen |
| 11 | `neopixel` | `beacon` | Green / amber / red |
| 13–14 | `micro_sd_card` | `sd` | Encrypted estate packet and check-in ledger |
| **7** | **power jack** (product, no firmware) | — | Powers the arm; leave port 7 out of `project.yaml`; match the arm voltage |

No mic, AHT20 or IMU: this is a trust object, not a health tracker. A future version can add the mic for voice check-in; the kit has it. The arm's power caution and `swap_lines` note from Depositum apply here too.

### Business model

- **NOK 149/month**, free for private-banking clients above a wealth threshold. A fee for the estate release and probate support. The bank retains the AUM and earns the estate settlement fees.
- **The scam brake is the volume product:** a physical, out-of-band approval for high-risk payments, sold to all retail customers as an add-on. That is what makes the unit economics work before the customer dies.
- **Licensing:** the real product is the estate registry and release protocol, not the box. Other banks, insurers and notaries can license it; the device is the consumer touchpoint.
- **Expansion:** digital legacy (passwords, crypto seed shares), funeral and insurance instructions, family mediation, and a "proof of life" API for pensions and insurers.

### Why it could be big

The wealth transfer is a multi-trillion-kroner event, and every bank and insurer with aging customers has the same problem. The device is a retention machine for the most valuable segment, and the estate release is a high-margin service. The moat is trust, regulation and the bank's custody role; a startup cannot unilaterally hold wills, but it can sell the infrastructure to the institutions that do. The "proof of life" signal is also the missing primitive for pension/insurance payouts and for preventing unclaimed assets.

### Why DNB / this hackathon

DNB's private banking and pension relationships, plus its estate/probate partners, are the distribution; the bank's brand is "we take care of you and your family." The demo is unforgettable: a countdown, a missed check-in, a red screen, a robot arm pulling open a drawer with a sealed will, and the heir's phone lighting up. It is the rare hardware pitch that lands emotionally before the business model is explained. The kit's arm, screen, knob, speaker and SD are all essential; the dead-man's switch is a physical ritual, not an app notification.

**Riskiest assumption:** fail-safe timekeeping and release. The ESP32 has no battery-backed RTC; a power cut resets `millis()`. The device must never release early and never fail to release. The MVP uses the laptop as the authoritative clock and a simulated 90-day timeline; production needs an RTC, a tamper-evident enclosure, a secure element, and a cellular backup. The legal validity is the second risk: the device releases the *location/key* of a legally valid will, not the will itself, and DNB's human process makes the decision.

---

## 3. Flomvakt — the flood gauge that pays

**One line:** A NOK 500 flood gauge for farms that turns a local water level into a bank-grade parametric trigger, so the insurer pays before the water recedes.

### Problem and customer

Parametric flood insurance is only as good as its trigger. The nearest official gauge can be miles from the farm; the flood is local; the payout is disputed; many farms are underinsured. The farmer needs money in days, not an adjuster in months. The insurer needs a trigger it can trust and a loss-adjustment cost it can cut. The bank needs to know whether the farm it lent against is still a farm.

The customer is three-sided:

- **The farmer** (the insured) in a flood-prone area.
- **DNB's agriculture lending book** — one of the largest in Norway.
- **The insurer** — DNB distributes business insurance through **Fremtind**, and a parametric flood add-on is a new product on an existing portal.

The "why now": extreme rainfall is rising; the 2023 Hans flood caused billions of kroner in damage; the EU Climate Adaptation Strategy and Norway's flood mapping are pushing adaptation; parametric cover is moving from niche to mainstream; and a connected sensor now costs less than a farm's deductible.

### Product

A standalone gauge. A ToF looks down a stilling well at a float. The board computes the water level, shows it on a TFT, sounds a local alarm, and writes a tamper-evident log to SD. The laptop console installs the policy, verifies the log, and triggers the payout.

The farmer sees a traffic light: green normal, amber watch, red flood. The insurer sees a **Flood Evidence Certificate**: max level, time above threshold, temperature, tamper status, chain verification. The bank sees the farm's climate risk score and the loan/insurance bundle; the gauge is a condition of a "flood-resilient farm loan."

### Exact hardware (14-port board; validated `ok`)

| Port(s) | Module | Instance | Job |
|---|---|---|---|
| 7 | `distance_sensor` | `tof` | Looks down the stilling well at the float; isolated, cleanest sight line |
| 9–10 | `st7789_tft` | `screen` | Level, threshold bar, 24 h trend, status |
| 13–14 | `micro_sd_card` | `sd` | Hash-chained evidence log (FAT32) |
| 6 | `button` | `btn` | Self-test / acknowledge |
| 11 | `neopixel` | `beacon` | Green / amber / red beacon |
| 1 | `aht20` | `th` | Temperature/humidity in a radiation shield |
| 5 | `icm40608` | `imu` | Mast tilt/knock tamper |
| 3–4 | `speaker` | `siren` | Local warning beeps/siren |
| **2** | free | — | Future soil sensor or second sensor |
| **8** | USB-C | — | Power; no power module needed |

The TFT is listed before the SD so it gets a hardware SPI host; the SD takes the other. The ToF owns the hardware `Wire`; the AHT20 and IMU use software I²C on their own ports. The speaker uses I²S_NUM_0; no mic, so no conflict. The ToF's field is 45° square: at 1 m it is ~82 cm wide, so **use the centre 2×2 zones (27, 28, 35, 36), not the global min** — the well wall is closer than the float at the edges. A short black collimator tube in front of the ToF narrows the view and makes the centre zones the float.

### Business model

- **Gauge NOK 490** (or NOK 1,900 with solar/LTE); **monitoring NOK 790/season**; **10–15% of the parametric premium**; a data fee to the bank/municipality.
- The farmer's cost is the monitoring fee, or it is bundled into the premium; the insurer/bank can subsidize the gauge. The insurer saves an estimated NOK 5,000+ per claim in loss adjustment; the bank reduces its climate risk and gains a fee.
- **Illustrative unit economics:** a farm with NOK 600,000 sum insured and NOK 18,000 premium generates NOK 1,800–2,700 of insurance share plus NOK 790 monitoring = NOK 2,600–3,500 per season. The basic device costs ~NOK 400; gross margin is 60–70% after cloud; payback is one season.
- **Expansion:** a network of gauges per municipality; a flood-risk data product for reinsurers and municipalities; soil moisture (the kit's soil sensor is a future module) for drought/irrigation; the same evidence protocol for heat, frost and hail.

### Why it could be big

The global flood insurance gap is enormous, and parametric cover is the fastest-growing way to close it. The missing piece is a cheap, trusted local trigger. The network effect is real: every gauge improves the flood model; the insurer with the best model prices better; the bank with the best data lends safer. The device is the wedge; the data and the risk model are the company. Expansion to other climate perils and other countries is natural — the hardware is a commodity, the trust and certification are the moat.

### Why DNB / this hackathon

DNB is one of Norway's largest agriculture lenders and distributes business insurance through Fremtind; a parametric flood product is a new fee stream on an existing customer base. DNB's green business loans can finance the gauge and other adaptation measures, and the data supports the bank's climate risk reporting. The demo is physical and clear: a model farm, a stilling well, a bucket of water, a rising bar, a red beacon, and a payout on the laptop. The kit's ToF, TFT, SD and speaker are the product.

**Riskiest assumption:** the ToF can measure the water level reliably enough for a parametric trigger. Water absorbs 940 nm IR; rain, ripples, sun and debris cause dropouts. The MVP uses a float with a retroreflective target inside a stilling well; production uses an ultrasonic/radar sensor and a self-cleaning well. If the gauge cannot hold ±1 cm for ten minutes, the insurer will not trust it. The second risk is basis risk — one gauge on one farm may not represent the flood; production needs a network or a calibrated model. The third is the trust anchor: the MVP's HMAC key in NVS is not bank-grade; production needs a secure element and secure boot.

---

## My pick: Flomvakt

Why this one:

- **It creates a new financial product, not a new interface.** The sensor reading is the only thing that decides who gets paid. The gauge is the oracle; the bank is the notary; the payout is automatic. That is a company.
- **It has the cleanest 48-hour build.** Eight modules, no arm, no latch, no mechanical power train. The risk is the sensor and the calibration, and the team can test that in hours with a bucket.
- **It has the strongest DNB line.** Agriculture lending + Fremtind business insurance + green loans + climate risk. The buyer is already a DNB customer; the distribution is the bank's relationship managers.
- **The demo is physical, legible and unusual.** Water rising in a clear well, a traffic light, a certificate, money moving. It does not rely on a judge's imagination.
- **It is the most defensible.** The data network, the insurer acceptance and the calibrated flood model are harder to copy than a locker or a display.

The honest downside: it is less "wow" than a robot arm, and parametric insurance is a conservative market. The demo must make the money moment visceral: the farmer's phone buzzes with **"NOK 180,000 paid"** while the water is still rising.

### The 24–48 h MVP

**In one sentence:** a model farm with a 1.2 m stilling well, a float, a 14-port Atech board in an IP65 box, a TFT traffic light, and a laptop console that installs a policy, verifies a hash-chained log, and pays a parametric claim when the water crosses the threshold.

#### Board, modules, port placement

```yaml
name: flomvakt
board: 14port
modules:
  - {id: st7789_tft, instance: screen, ports: [9, 10]}
  - {id: micro_sd_card, instance: sd, ports: [13, 14]}
  - {id: distance_sensor, instance: tof, port: 7}
  - {id: button, instance: btn, port: 6}
  - {id: neopixel, instance: beacon, port: 11}
  - {id: aht20, instance: th, port: 1}
  - {id: icm40608, instance: imu, port: 5}
  - {id: speaker, instance: siren, ports: [3, 4]}
```

`atech validate` → `ok`. `atech describe` gives the exact pins: ToF GPIO 6/7; TFT 40/41/1/2; SD 39/38/36/35; button 13/12; NeoPixel 43/44; AHT20 9/8; IMU 11/10; speaker 17/18/16/15. Port 2 is free; port 8 is the USB-C jack. No power module is needed.

#### Physical build

- **Stilling well:** 1.2 m clear PVC/acrylic tube, ~30 cm diameter, holes at the bottom, a base. A 12 cm closed-cell foam float with a **retroreflective target** and a guide rod. A printed scale on the tube.
- **The optical trick:** the board is vertical, TFT facing the farmer; a **45° front-surface mirror** in front of the ToF redirects its boresight down the well. Without the mirror, mount the board module-side down over the well and use the laptop/phone as the farmer display — the TFT becomes the service panel.
- **Enclosure:** IP65 box with a clear window for the TFT and the ToF, a radiation shield for the AHT20, a cable gland for USB, a mast bracket.
- **Model farm:** a 60 × 40 cm tray, a 3D-printed barn and fence, a "field" of moss/grass, the well at the low corner, a watering can for the flood.
- **Calibration:** distance at 0 cm and at 80 cm; `level_cm = sensor_height_cm - distance_cm`; the float's height offset is a constant. The mirror adds a fixed path offset; calibrate it once.
- **The one thing that can break the demo:** the float sticking, or the ToF seeing the well wall. The collimator and the centre 2×2 zones are the fix; test at 0/20/40/60/80 cm before building the enclosure.

#### What runs on the board

- **`setup`:** SD logger `/flomvakt.log`; TFT rotation 1 and a smooth font; NeoPixel; speaker volume 0.3; IMU gyro calibration with the mast still; read the config from NVS; write the genesis record; show `FLOMVAKT / READY`.
- **`loop` (never blocks):**
  - **100 ms:** read the four centre ToF zones, reject `32767`, take the median; convert to cm; EMA filter; update the state.
  - **200 ms:** thresholds with hysteresis — WATCH at 60% of the flood threshold, FLOOD at 100%, RECEDE below 80% for five minutes. Record a `LEVEL` event every 60 s or on a 5 cm change.
  - **1 s:** IMU tilt/shock; `TAMPER` if the mast tilts >5° or a shock >1.5 g.
  - **5 s:** AHT20; a `WEATHER` record every 15 min.
  - **60 s:** `HEARTBEAT` with level, temperature, humidity, SD free, uptime, chain head, dropped lines. A gap in heartbeats is itself evidence.
  - **500 ms:** TFT dashboard — big level number, vertical bar with the threshold line, 24 h sparkline, status, chain head. If the 150 KB canvas does not fit, `setDirectMode(true)` and update only the changed regions; check `isDirectMode()`.
  - **NeoPixel:** green/amber/red plus a slow blue heartbeat. **Speaker:** one beep on WATCH, three on FLOOD. **Button:** short press = self-test; long press = acknowledge.
- **Evidence format:** one canonical line per record, pipe-delimited, no quotes:
  ```
  seq|boot|t_ms|type|level_cm|dist_mm|temp_c|rh|extra|prev|hmac
  ```
  `hmac = HMAC-SHA256(device_key, seq|boot|t_ms|type|level_cm|dist_mm|temp_c|rh|extra|prev)`. The genesis line carries the farm ID, gauge ID, sensor height, threshold, duration, policy ID, install time, and the laptop's ECDSA signature. The board stores it and starts the chain.
- **One string, two sinks:** the board logs the exact same canonical line to SD and emits it as the `flomvakt_record` event. The laptop verifies the live chain and the SD chain with the same parser — no JSON re-serialization, no canonicalization bugs.
- **Key and reboot:** the device key is generated on first boot with `esp_random()` and stored in NVS (`Preferences`); production moves it to a secure element. The `seq` is saved to NVS every 10 records; a reboot logs a `BOOT` record and continues the chain. A power cut is an event, not a gap. `code.globals` needs `<Preferences.h>` and `<mbedtls/md.h>`; both ship with the Arduino core.
- **Actions:** `install` (JSON config + genesis signature), `test` (self-test reply), `dump` (read `/flomvakt.log` in 900-byte chunks with `sd.readBytes(path, offset, buf, n)`; the built-in `sd_read` only returns the first 1024 bytes), `set_threshold` (demo policy).
- **Events:** `flomvakt_record`, `flomvakt_state` (level, status, temperature, humidity, chain head, uptime, SD free), `flomvakt_test`. The modules' own events also stream; the laptop filters to the custom keys.
- **Timing:** the screen push is ~31 ms, so redraw at 2 Hz; `logLine` is non-blocking; the hash is sub-millisecond; the speaker plays in the background. The loop stays responsive.

#### What runs on the laptop

- **`flomvakt_console.py` with `atech.Board`:**
  - **Install:** a policy form (farm ID, gauge ID, sensor height, threshold, duration, sum insured, premium). The laptop ECDSA-signs the genesis, calls `install`, and shows `POLICY SEALED`.
  - **Live:** a big level gauge, a 24 h chart, a status card, the chain head, the last records; a "farmer phone" tab and an "insurer/bank" tab.
  - **Verify:** call `dump`, reassemble the log, verify every HMAC and the genesis signature, build the **Flood Evidence Certificate** (max level, minutes above threshold, temperature, tamper, chain OK).
  - **Payout:** the policy engine — if the verified max ≥ threshold for ≥ the policy duration, pay `sum insured × severity` (e.g. 40 cm = 30%, 60 cm = 60%, 80 cm = 100%). Show a mock Vipps/DNB transfer; the farmer's phone shows the SMS.
  - **Bank view:** farm risk score, loan-to-value, premium, "resilience discount"; placeholder coefficients, clearly labelled.
- **The split:** the board is the field device — sense, filter, display, log, alarm. The laptop is the cloud/back office — policy, signature, verification, payout, charts. The SDK only does USB, so the laptop is the gateway; production replaces it with LTE-M and a cloud function.
- **Stack:** Streamlit for the demo (big numbers, charts, one-click verify/pay), `cryptography` for ECDSA, `hmac`/`hashlib` for the chain, `atech` for the board. A `rich` terminal fallback if Streamlit misbehaves.

#### The 3-minute demo

| Time | Action | What the judges see | What is said |
|---|---|---|---|
| 0:00–0:20 | Stand at the model farm | TFT `NORMAL 8 cm`; green beacon; the well, the barn, the field | "Norwegian farms are on the front line of extreme weather. The problem isn't the water; it's the proof." |
| 0:20–0:45 | Laptop → **Install policy** (threshold 40 cm, 30 s, NOK 600k sum, NOK 18k premium) | TFT `POLICY F-014 SEALED`; chain head appears; the farmer's phone shows the policy | "The field agent seals the gauge with the bank. Every reading is hash-chained." |
| 0:45–1:10 | Normal weather; show the live chart and heartbeat | The laptop chart; a continuous chain; the beacon pulses blue | "The gauge is alive; a gap in the log would itself be an event." |
| 1:10–1:45 | Pour water into the well | Float rises; TFT bar climbs; amber at 24 cm (`WATCH`); one beep; the laptop gauge climbs | "The farmer gets a warning before the flood." |
| 1:45–2:15 | Water crosses 40 cm | Red beacon; TFT `FLOOD 42 cm`; three beeps; laptop `TRIGGER MET — 42.3 cm for 00:00:38` | "The bank gets a trigger, not a photo." |
| 2:15–2:40 | Laptop → **Verify & pay** | The SD log is read; the chain and genesis verify; the certificate appears; the farmer's phone buzzes `NOK 180,000 paid — DNB` | "No adjuster, no months. The gauge is the oracle." |
| 2:40–3:00 | Bank view | Risk 72 → 48; LTV 65% → 55%; premium −15%; the next farm in the portfolio | "The gauge is a condition of the loan. Flomvakt: the flood gauge that pays." |

Rehearsal notes: set the **demo policy duration to 30 s**, not ten minutes; pour smoothly; keep the float free; have a **backup video**; one USB cable; close `atech monitor` before the app; the water is still high at the end — the payout has already happened, which is the point.

#### Riskiest technical assumption

**The ToF level measurement is the product.** It must hold ±1 cm and be available >99% of the time through rain, ripples, sun and debris. The float + retroreflective target + stilling well + collimator is the MVP mitigation; production uses an ultrasonic or radar level sensor and a self-cleaning well. If this fails, the parametric trigger is unreliable and there is no product.

Close second: the **trust anchor** — an HMAC key in NVS, a genesis signature and a tamper sticker are not bank-grade. A farmer could open the enclosure, swap the SD or re-flash the board. Production needs a secure element (e.g. ATECC608A), secure boot and flash encryption. The insurer's acceptance is the commercial gate, and it should be tested with one insurer during the hackathon, not after.

Close third: **basis risk** — one gauge on one farm may not represent the flood. The production answer is a network of gauges plus a calibrated flood model and satellite/rainfall data. The hackathon demo should say this out loud; judges trust founders who name the limitation.

#### Build plan and team

| Hours | Workstream | Output | Stop condition |
|---|---|---|---|
| 0–4 | Unbox, `atech check`, place, `validate`, `view`; start the first build early (2.4 GB download); test each module | A board that sees the float, the screen, the SD, the button, the beacon, the speaker | If the ToF cannot hold a stable reading on the float, switch to a magnetic reed float or an ultrasonic sensor (plan B) |
| 4–10 | Stilling well, float, mirror/collimator, enclosure, model farm; TFT layout; chain format; reboot test | A physical gauge that reads 0/20/40/60/80 cm | If the mirror alignment is unreliable, mount module-side down and use the laptop/phone as the farmer display |
| 10–22 | Firmware: filter, thresholds, chain, UI, actions, events; Python verifier; `dump` | A board whose log verifies end-to-end | If the SD `beginLogger` truncates on reboot, switch to one file per boot + a manifest chain |
| 22–32 | Console: install, live, verify, payout, bank view; certificate; phone mock; demo script | A one-click demo | If Streamlit lags, fall back to a `rich` terminal UI |
| 32–40 | Rehearsal, failure modes, backup video, pitch | A 3-minute demo that works twice in a row | If the water demo is flaky, use a clear acrylic flume and a guided float; do not fake the sensor |

Team of 3–4: one firmware, one laptop/UI, one physical/demo, one pitch/business. The first build is the long pole; start it before the mechanical build.

#### Why the Atech kit is the right prototype

The 14-port board has exactly the ports for the ToF, TFT, SD, button, NeoPixel, AHT20, IMU and speaker. Port 7 is isolated and perfect for the downward-looking ToF. The SDK's codegen/build/flash and Python runtime make the board–laptop split a weekend job, not a firmware project. The kit's ToF is a precise 8×8 distance sensor with a 45° field and 4 m range; the TFT, NeoPixel and speaker make the gauge legible to a farmer; the SD is a physical evidence artifact the insurer can hold.

The production BOM replaces the dev board with a custom PCB, an ultrasonic/radar level sensor, a 5 W solar panel and LiPo, LTE-M/NB-IoT, an IP67 enclosure, a secure element and a stilling well. The Atech kit proves the hard part — the evidence chain and the policy integration — not the easy part, the box.

#### What would kill it

- The level sensor is unreliable → the trigger is not trusted → no insurer.
- The insurer's actuarial/legal team rejects a farmer-installed gauge → the sales cycle stalls.
- Basis risk → disputed payouts → the promise breaks.
- The farmer will not pay NOK 1,000/season → the bank/insurer must subsidize; the unit economics depend on the insurance share.
- The hardware is not weatherproof or secure → the data is challenged.

The mitigation is a narrow pilot: one insurer, one region, one crop, ten farms, a certified stilling well, a dual-sensor reference (ToF + ultrasonic), and a public chain-verification page. The first season is the product.

---

## Wild card: Skilsmisseboks — the divorce escrow with two keys and a robot arm

**One line:** The first bank product designed for the end of a marriage: a two-key physical escrow box that holds the shared assets until both exes agree, with a robot arm as the neutral mediator.

**How it works:** a clear box with a robot arm and two "keys" — the rotary encoder is one party's key, the button is the other's. The TFT shows the asset list and the proposed split: the house, the car, the joint account, the dog. Both parties must turn/press their key within five seconds; the arm releases one asset at a time. The SD logs the agreement; the laptop runs the mediation and the bank's split. If one party tries to force the box, the IMU/ToF logs it and the arm locks down.

**Modules (14-port; validated `ok`):** `robot_arm` on UART 3; `rotary_encoder` [1,2]; `button` 6; `st7789_tft` [9,10]; `neopixel` 11; `micro_sd_card` [13,14]; `speaker` [4,5]; power jack in the isolated port 7, voltage matched. The arm holds/releases the assets; the encoder and button are the two keys; the screen is the settlement; the SD is the agreement.

**Why outrageous:** a robot arm as a divorce mediator. The demo has two judges turning keys while the arm hesitates, then releases the car key. It is funny, uncomfortable, and impossible to forget.

**Why DNB:** joint accounts, mortgages and family law are core banking; the average divorce is a financial mess; the bank can own the "conscious uncoupling" process with a flat fee (NOK 4,900) plus a share of the assets. The device is a symbol; the real product is the mediated split protocol.

**The demo beat:** two judges each turn their key; the TFT reads `AGREEMENT 50/50`; the arm slowly releases the car key; the speaker plays a neutral chime; the laptop shows the joint account split and the mortgage transfer. Tagline: **"Till death do us part. The assets, not so much."**

---

## Design notes — what I deliberately did not pitch

- **No cargo black box.** It is the most crowded corner of this kit's possibility space: a sensor puck, a hash chain, a cold-chain claim. I had one; the flood gauge is the sharper version of the same "physical evidence → money" thesis, with a customer who is already in DNB's book and a demo that does not need a shipping container.
- **No scam-proof display, no budget-enforcing arm, no kids' piggy bank, no carbon column, no cabin monitor.** All good ideas; all already in the room. The gaps I found were consumer escrow, estate/end-of-life, and agricultural climate risk.
- **The kit's honest limits** — no camera, GPS, cellular, battery or secure element; one ToF; the 1024-byte `sd_read`; the 150 KB TFT canvas; the arm's power and `swap_lines` quirks — are design constraints, not footnotes. Every idea above is built around them.
- **One sentence to the council:** the winning idea is not the one with the most sensors. It is the one where a sensor reading is the *only* way to decide who gets paid. That is **Flomvakt**.
