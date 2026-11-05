# Scam Call Guardian: market sizing, unit economics, business model, regulation, roadmap

Research date: 2026-11-05. Desk research over about 10 minutes, so treat this as a first pass.

- Figures marked **[A]** are **assumptions** made by the analyst. Everything else has an inline source.
- Figures marked **[V]** come from secondary sources and should be checked against the primary source before anyone outside the team sees them.
- Currency assumptions **[A]**: 1 USD = 10.5 NOK, 1 EUR = 11.5 NOK.

---

## 0. Corrections to the brief

| Brief said | Finding |
|---|---|
| DNB has ~2.1M personal customers | That figure dates from about 2022 ([WBA profile](https://archive.worldbenchmarkingalliance.org/publication/financial-system/2022/companies/dnb)). DNB now reports **2.4 million retail customers** and 240,000 corporate customers ([DNB About us](https://m.dnb.no/en/about-us)). I found no public split by age. |
| Haiku price $0.10 / $0.50 per M tokens | Used as given. I did not verify it against Anthropic's pricing page. |
| Deepgram price | Nova-3 streaming lists at **$0.0077/min monolingual** and **$0.0092/min multilingual**. Temporary promo rates were $0.0048 and $0.0058, and the Growth tier is about $0.0042 with a $4k+/yr prepaid commitment [V] ([activepieces](https://www.activepieces.com/blog/deepgram-pricing-2026-per-minute-rates-and-add-ons), [convertaudiototext](https://convertaudiototext.com/blog/deepgram-nova-3-explained), [diyai](https://diyai.io/ai-tools/speech-to-text/deepgram-pricing-2026/)). Confirm on deepgram.com/pricing. |
| Fraud losses | Finanstilsynet reports **NOK 962M** defrauded in 2025, down from about NOK 1,227M in 2024 ([Finanstilsynet H2 2025](https://www.finanstilsynet.no/publikasjoner-og-analyser/svindel-og-svindelstatistikk/2025/h2/svindelstatistikk-andre-halvar-2025/)). Banks also stopped NOK 1,517M of attempted fraud in H1 2025 alone ([Finanstilsynet H1 2025](https://www.finanstilsynet.no/publikasjoner-og-analyser/svindel-og-svindelstatistikk/2025/h1/svindelstatistikk-forste-halvar-2025/)). |
| Average loss per fraud | For fake-police and fake-bank-employee fraud, Økokrim puts the average at **about NOK 200,000** per case, with some victims losing more than NOK 1M. The data is from around 2022 ([Økokrim report](https://okokrim.custompublish.com/getfile.php/5151932.2528.lzwiim7wqsjank/Politibedrageri+-+offentlig+rapport_nett.pdf)). |

---

## 1. Market sizing

### 1.1 Norway inputs

| Metric | Value | Source |
|---|---|---|
| Population, 1 Jan 2026 | 5,627,400 | [SSB befolkning](https://www.ssb.no/befolkning/folketall/statistikk/befolkning) |
| 67+ | 959,633 (674,694 aged 67-79, 239,357 aged 80-89, 45,582 aged 90+) | SSB, same table |
| 80+ | 284,939 | SSB, same table |
| 75+ | **≈535,000 [A]**: 80+ plus an estimated ≈250,000 aged 75-79. SSB's headline table has no 75 cut, so pull table 07459 to replace this. | estimate |
| 65+ | **≈1.08M [A]**: 67+ plus about 2 × 62k cohorts | estimate |
| Home-dwelling 67+ living alone | 34% (29% at 67-79, 71% at 90+), 2023 | [SSB "1 av 3 eldre bor alene"](https://www.ssb.no/befolkning/artikler-og-publikasjoner/1-av-3-eldre-bor-alene) |
| 80+ living alone | 53% (2019, latest found; two in three women) | same SSB article |
| Lonely elderly with no family nearby | 1 in 4 of those living alone | [SSB](https://www.ssb.no/sosiale-forhold-og-kriminalitet/levekar/artikler/1-av-4-aleneboende-eldre-har-ingen-familie-i-naerheten) |
| Landline vs mobile | Norway had **no PSTN/ISDN subscriptions at all by end-2022**, so all fixed voice is IP ([Nkom ekommarkedet 2022](https://nkom.no/statistikk/statiske-rapporter-og-analyser/_/attachment/download/cba9f5c7-a034-42a4-bb66-ff87a5b5685d:8712419ee263a709792d3ee7fbb32300c83fa5d3/Ekommarkedet%20i%20Norge%202022.pdf)). I found no Norwegian 75+ mobile-use statistic. For comparison, Denmark reports 86% of 75-89-year-olds online ([digst.dk](https://en.digst.dk/numbers-and-statistics/)). **[A]** Most Norwegian 75+ take calls on a mobile phone. | |

The landline finding matters for the product. The ESP32-S3 has only BLE, with no Bluetooth Classic and therefore no HFP, so the device cannot tap a mobile call directly. It must pick up the call acoustically, which means the call has to be on speaker or the phone held next to the device. That is the biggest adoption risk. Measure it in the pilot.

### 1.2 Household conversion

The device is bought per household, not per person.

- **67+ in Norway:** 959,633 × 34% = 326,275 single-person households. The remaining 633,358 people live mostly as couples, so ÷2 = 316,679 households. Total **≈643k households**, which is 0.67 households per person.
- **75+ in Norway:** the share living alone is **45% [A]**, taken between the 29% and 53% bands. That gives 535k × 45% = 240,750 single households, plus (535k − 240,750) / 2 = 147,125 couple households, for a total **≈388k households** (0.725 per person).
- **Product-fit filter [A]: 70%.** The household needs Wi-Fi or broadband and a place for the device next to the phone. It must take calls on speaker or near the device. And a relative or a carer must be willing to set it up.

Pricing basis: B2C NOK 149/month including VAT, which is **NOK 1,788/yr** (see section 2).

### 1.3 TAM / SAM / SOM

| Layer | Norway | Nordics (NO+SE+DK+FI+IS) | EU-27 |
|---|---|---|---|
| Base population | 67+ = 959,633 | 65+ ≈ 5.99M: SE ≈ 2.22M (20.8% × 10.66M, derived, [TE/Eurostat](https://tradingeconomics.com/sweden/proportion-of-population-aged-65-over-eurostat-data.html)), DK ≈ 1.27M (UN est., [World Economics](https://www.worldeconomics.com/Population-65Plus-Size/Denmark.aspx)), FI 1,347,494 ([Statistics Finland](https://stat.fi/en/news/Finland-in-Figures-2026-ageing-Finland-is-visible-in-numbers)), NO ≈ 1.08M [A], IS ≈ 0.07M [A]. Cross-check: the Nordic 65+ share is 21% ([Nordic Statistics](https://nordicstatistics.org/news/the-nordic-population-2025/)) | 65+ = 450.6M × 22.0% = **99.1M**. 80+ is 6% = 27.0M ([Eurostat](https://ec.europa.eu/eurostat/statistics-explained/index.php/Population_structure_and_ageing)) |
| **TAM** (all elderly households) | 643k households × NOK 1,788 = **NOK 1.15bn/yr** | 5.99M × 0.67 = 4.0M households × €155 = **€624M/yr** | 99.1M × 0.67 = 66.4M households × €120/yr (€10/mo EU price [A]) = **€8.0bn/yr** |
| **SAM** (75+, product fit) | 388k × 70% = **272k households** × 1,788 = **NOK 486M/yr** | 75+ ≈ 49% of 65+ [A] (the Norwegian ratio) = 2.94M × 0.725 × 70% = **1.49M households**, **€232M/yr** | 75+ ≈ 10% of population [A] = 45.1M × 0.725 × **50%** fit [A] (adds legal fit: two-party-consent states like DE, see section 4) = **16.3M households**, **€1.96bn/yr** |
| **SOM** | 5% of SAM by year 3 [A] = **13.6k households**, **NOK 24M ARR** | Norway's 13.6k plus 1% of the rest of the Nordic SAM (1.22M), so ≈12.2k, for **≈26k households**, **≈€4.0M ARR** by year 5 [A] | 0.1% of SAM by year 5 [A] = **16k households**, **≈€2M ARR** |

**DNB-specific SAM [A]:** DNB's 2.4M retail customers are about 43% of the population including minors. Assume DNB holds about 33% of 75+ banking relationships. Then DNB's slice of the Norwegian SAM is about 272k × 0.33 ≈ **90k households**. A "vulnerable customer" programme reaching the top 10% risk tier would be about **9k households**.

**What fraud is worth in this market [A]:** Suppose 30% of the NOK 962M in 2025 losses came from phone-initiated fraud against people 67+. That is about NOK 289M/yr, or **about NOK 300 per 67+ person per year** on average. The real risk is concentrated in people over 80 who live alone, have previously been targeted, or have cognitive decline. The bank case only works if the device is aimed at that high-risk segment (section 2.5).

---

## 2. Unit economics

### 2.1 Cost per call

**Haiku** (price as given in the brief):

- The device sends one request every 6 s, so a 10-minute call makes 100 requests.
- Each request carries ≈1,000 input tokens: a 650-token transcript window (2,400 bytes) plus a 350-token system prompt.
- Input: 100 × 1,000 = 100,000 tokens × $0.10/M = **$0.0100**.
- Output: 100 × 30 = 3,000 tokens × $0.50/M = **$0.0015**.
- Haiku total: **$0.0115 per 10-minute call**, or **$0.00115/min**.
- Prompt caching does not help. The 350-token system prompt is below the minimum cacheable length, and the rolling window changes on every request.

**Deepgram Nova-3 streaming:** the price is per minute of audio, and the device bills the whole call.

| Scenario | STT $/min | 10-minute call | 5-minute call |
|---|---|---|---|
| A: multilingual list (no+en code-switching) | 0.0092 | $0.092 + $0.0115 = **$0.1035 (NOK 1.09)** | $0.0518 (NOK 0.54) |
| B: monolingual `no` list | 0.0077 | $0.0885 (NOK 0.93) | $0.0443 (NOK 0.47) |
| C: Growth tier [V] | ~0.0042 | $0.0535 (NOK 0.56) | $0.0268 (NOK 0.28) |

Speech-to-text is about 89% of the variable cost. Haiku cost hardly matters, so a 3-second evaluation cadence would cost almost nothing extra. Two things can cut STT minutes:

- Stream only calls from unknown numbers. This needs caller-ID from the phone app.
- Drop to sampling mode after about 3 minutes of green.

Together these could plausibly cut STT minutes by 40-60% [A].

### 2.2 Cost per user per month

The typical user in the brief makes 60 calls × 5 minutes = 300 minutes a month.

| Line | Scenario A | Scenario C |
|---|---|---|
| Deepgram (300 min) | $2.76 | $1.26 |
| Haiku (60 calls × $0.00575) | $0.345 | $0.345 |
| **Cloud AI subtotal** | **$3.105 ≈ NOK 32.6** | **$1.605 ≈ NOK 16.9** |
| Backend, DB, push, logs [A] | NOK 3 | NOK 3 |
| Support / customer success, at scale [A] | NOK 15 | NOK 10 |
| Billing / payment fees [A] | NOK 3 | 0 (invoiced to bank) |
| Device amortisation: NOK 600 landed over 36 months [A] | NOK 17 | NOK 17 |
| **All-in cost per user-month** | **≈NOK 71** | **≈NOK 47** |

A heavy user making 120 calls × 8 minutes (960 min) costs $9.94, about **NOK 104/month** for cloud AI alone under scenario A. A fair-use cap or call gating is therefore needed.

### 2.3 Hardware BOM

All figures are **[A]**, at 1k units, in NOK.

| Part | NOK |
|---|---|
| ESP32-S3 module (Wi-Fi/BLE, 8MB flash, PSRAM) | 50-70 |
| MEMS mic, ideally 2 (e.g. I2S) + analog front-end | 25-50 |
| 1.3-2.0" TFT display | 40-60 |
| Traffic-light LEDs + buzzer/speaker for an audible alert | 15-25 |
| PCB + assembly + test | 60-100 |
| Enclosure (injection moulded in volume, 3D-printed in the pilot) | 30-80 |
| USB-C PSU + cable | 30-40 |
| Packaging, quick-start guide (large print) | 20-30 |
| **BOM + assembly** | **≈270-455** |
| Landed cost including freight, duty, and amortised CE/RED certification (≈NOK 150-250k one-off) | **≈450-650** |

At 10k units, expect about NOK 200-300 BOM.

### 2.4 Proposed price points

| Channel | Price | Contribution |
|---|---|---|
| **B2C, sold to adult children** | **NOK 149/month including VAT** (NOK 119 ex VAT), device included, 12-month term. Alternative: NOK 990 device + NOK 99/month | (119 − 71) / 119 = **40%**. About 54% with call gating |
| **Bank-subsidised** (DNB gives it to vulnerable customers) | **NOK 99/user-month** ex VAT at ≥1k units. **NOK 79** at ≥10k. Or NOK 1,190 per user-year | (99 − 47) / 99 = **53%**. At 79: 41%. Note that DNB cannot deduct input VAT because financial services are VAT-exempt, so its real cost is ×1.25 |
| **Municipal welfare tech** (kommune velferdsteknologi) | **NOK 129/month ex VAT** per unit as an add-on to the trygghetsalarm (safety alarm) portfolio. Kommunes already charge alarm users NOK 250-550/month, e.g. Lørenskog NOK 468, Bærum NOK 550/250 by income, Porsgrunn NOK 360 ([Lørenskog](https://lorenskog.kommune.no/_f/p71/i491728c8-a54a-4ba6-833d-5f01bd7e6349/kommunale-gebyrer-og-brukerbetalinger-helse-og-omsorg-2025.pdf), [Bærum](https://www.baerum.kommune.no/globalassets/tjenester/helse-og-omsorg/pris/prisliste-2025-helse--og-omsorgstjenester.pdf), [Porsgrunn](https://www.porsgrunn.kommune.no/media/5203/soeknad-om-trygghetsalarm.pdf)) | ≈45-60% |

### 2.5 Payback when one average fraud is prevented

Average loss: NOK 200,000 (Økokrim, ~2022).

- **Family (B2C):** one prevented loss equals 200,000 / 1,788 ≈ **112 years** of subscription. It pays for itself the first time it works.
- **Bank at NOK 1,188/user-year:** break-even needs one prevented NOK 200k loss per **≈168 users per year** (0.6% annual incidence). Handling a fraud case costs the bank roughly NOK 10k [A] (customer service, investigation, complaints), which moves break-even to about 1 per 160.
- **Reality check [A]:** the average expected loss is about NOK 300 per 67+ person per year (section 1.3). Population-wide distribution therefore loses money on prevented losses alone. Targeting a segment with 5-10× average risk brings the bank to roughly break-even. The rest of the case has to come from reputation, regulatory positioning, and care cost.

---

## 3. Business models and go-to-market

| Model | Why they pay | Watch-outs | Priority |
|---|---|---|---|
| **Bank partnership (DNB)** | (1) **Refund liability.** Under finansavtaleloven § 4-30 the bank is liable for unauthorised transactions, and the customer's share is capped at NOK 12,000 even when grossly negligent, unless the customer acted intentionally ([FinKN practice / HR-2022-1752-A](https://www.uib.no/sites/w3.uib.no/files/attachments/udsen_og_kjorven_.pdf)) [V]. (2) **The PSR deal of Nov 2025** makes payment service providers refund impersonation fraud in full when the fraudster posed as the payer's own bank. It also makes a provider fully liable if its fraud prevention is inadequate ([William Fry](https://www.williamfry.com/knowledge/psr-psd3-eu-payment-services-legislation-is-agreed/), [Finologee](https://finologee.com/psr-what-the-provisional-political-agreement-signals-for-psps/)). It is an EEA-relevant regulation, but Norway's incorporation timeline is unknown [V]. (3) Reputation: DNB already runs fraud campaigns with the police and Telenor ([DNB](https://www.dnb.no/dnbnyheter/no/samfunn/slik-blir-stadig-flere-eldre-svindlet)). (4) Customer-care and fraud-handling cost. (5) ESG/"vulnerable customers" reporting | Bank procurement and outsourcing rules (Finanstilsynet ICT/outsourcing, DORA) are a heavy vendor-due-diligence burden. The bank will want white-label | **1: pilot partner** |
| **Telco bundle** (Telenor, Telia, Ice) | Telcos already block >100M scam calls ([Telenor via SecurityWorldMarket](https://www.securityworldmarket.com/no/Nyheter/Bedriftsnyheter/stopper-over-100-millioner-svindeltelefoner1)). This adds in-call protection on top of network blocking. They own the billing relationship with seniors. The PSR may give telcos cooperation duties | Telcos may prefer a network-side or app solution, and a hardware SKU adds friction | 2 |
| **Insurance** (contents insurance (innboforsikring) with ID-theft/fraud cover) | Lower claims, and a differentiator for senior policies | Claims data is needed to prove loss reduction, so insurers come after the bank pilot | 3 |
| **Municipalities** (Helsedirektoratet's welfare tech programme, KS) | Fits the "trygt hjemme" (safe at home) agenda. Alarm response centres are already a delivery channel | Slow public procurement. Sold to municipal health services, it may drift toward health-information-security norm (Normen) requirements. It is not core health care, so position it as "trygghetsteknologi" (safety technology) | 3 (from year 2) |
| **Direct to adult children** (B2C) | Emotional purchase. One prevented loss pays for >100 years of subscription | High customer acquisition cost. Needs the elder's genuine consent. Support load | 2 (runs in parallel; validates willingness to pay) |

**Recommended sequence:**
1. DNB pilot. This gives evidence and data on detection and prevented loss.
2. DNB white-label rollout to a targeted high-risk segment, plus B2C in parallel.
3. Telco or insurer distribution.
4. Municipal framework agreements.
5. Nordics through Nordic banks: Nordea, Danske, Handelsbanken.

---

## 4. Regulation and risks

### 4.1 Norwegian criminal law on listening to calls

- **Straffeloven § 205** ("Krenkelse av retten til privat kommunikasjon") criminalises secretly listening to or recording communication **between others**. A party to a call may record or process their own conversation without telling the other party, so Norway is effectively one-party consent ([recordinglaw.com](https://www.recordinglaw.com/no/world-laws/world-recording-laws/norway-recording-laws/) [V]). Verify the wording on Lovdata.
- **Implications:**
  - The **elder must be the knowing user**, because their consent is what makes it lawful.
  - If an adult child installs it **without the elder's knowledge or understanding** (for example with dementia), the device listens to communication "between others", which is a § 205 risk.
  - Onboarding needs a consent step in the elder's own words, and the device needs a visible "listening" indicator.
  - If the elder lacks capacity, a guardian (verge) or health-law framework is needed.
- **EU expansion:** some states require all-party consent or criminalise intercepting the "non-public spoken word" (e.g. Germany StGB § 201). That is why the EU SAM uses a 50% fit factor.

### 4.2 GDPR (personopplysningsloven)

- **Roles:** the company is controller. In a bank white-label it may be joint controller or processor for DNB, which must be decided. Deepgram and Anthropic are processors, so **Art. 28 data processing agreements** are needed with both. The elder's household exemption (Art. 2(2)(c)) does **not** cover the company.
- **Data subjects:**
  - The elder. Legal basis: contract (6(1)(b)) or consent.
  - **The caller**, including scammers, family, GPs, and bank staff. Legal basis: legitimate interests (6(1)(f)), which needs a documented balancing test that relies on minimisation: no audio stored, transcript held only in volatile memory, and only the verdict, a short reason, and a timestamp persisted.
- **Special categories:** calls with doctors will incidentally contain health data (Art. 9).
  - Do not persist transcripts.
  - Strip quotes from the "reason" text shown in the app.
  - **Do not do speaker identification.** Voiceprints would be biometric Art. 9 data.
- **Art. 14 information to callers:** an announcement before each call is impractical. Rely on Art. 14(5)(b), disproportionate effort, plus public information, and document the reasoning in the DPIA.
- **DPIA (Art. 35): required.** The processing uses new technology, monitors communications systematically, involves vulnerable data subjects, and covers non-users. Consider prior consultation with Datatilsynet and its AI regulatory sandbox.
- **Transfers / residency:**
  - Deepgram's **EU endpoint `api.eu.deepgram.com` has been generally available since Jan 2026**. Full in-region processing also requires `mip_opt_out=true` ([Deepgram](https://deepgram.com/learn/deepgram-eu-endpoint-now-generally-available), [Your data](https://developers.deepgram.com/trust-security/your-data)).
  - Anthropic's first-party API `inference_geo` only offers "us"/"global". To keep inference in the EU, route through **AWS Bedrock EU or Google Vertex EU regions**, and confirm the Haiku model is available there. Otherwise rely on the EU-US DPF/SCCs plus a transfer impact assessment, and sign a zero-data-retention agreement ([Claude docs, data residency](https://platform.claude.com/docs/de/manage-claude/data-residency), [sonomos](https://sonomos.ai/blog/claude-eu-data-residency-2026/)) [V].
- **Retention:** set event-history TTLs, and give the elder access and deletion through the app.

### 4.3 EU AI Act

Status in Norway: **not yet incorporated into the EEA**. A Norwegian AI act (KI-lov) bill is planned for spring 2027 ([Tek.no/NTB](https://www.tu.no/nyhetsstudio/nye-eu-regler-for-ki-norge-og-eoes-henger-ennaa-langt-etter/127027)). EU sales are bound now.

- **Prohibited (Art. 5):** no. It does not manipulate, and it does not recognise emotions at work or in education.
- **High-risk (Annex III):** probably not.
  - Point 5(b) on creditworthiness explicitly **excludes systems used to detect financial fraud**.
  - It is not a safety component of a product under Annex I.
  - It does not decide access to essential services.
  - Avoid medical claims like "dementia aid" (see 4.4).
- **Transparency (Art. 50):**
  - The elder must know they are dealing with an AI system and that warnings are AI-generated and can be wrong.
  - If the product markets "stress or emotion detection", Art. 50(3) duties for emotion recognition apply. Don't do it.
- The GPAI obligations belong to Anthropic. The company is a provider of a downstream system, so it should keep technical documentation and an evaluation record anyway, because the bank's vendor due diligence will ask for it.

### 4.4 Product, cybersecurity, and procurement rules

The RED, CRA and EAA dates in this section come from the analyst's knowledge. I did not source them in this session, so treat them as [V].

- **Radio Equipment Directive cybersecurity delegated act (EN 18031)** has been mandatory since 1 Aug 2025 for internet-connected radio equipment. It requires secure credential storage, secure updates, and protected communications. **The hackathon build would fail it.**
- **Cyber Resilience Act:** vulnerability reporting duties from Sep 2026, full obligations from Dec 2027.
- **CE (RED/EMC/LVD/RoHS)** is needed before any device is shipped to non-employees.
- **Medical Device Regulation:** stay out by having no medical intended purpose.
- **Municipal procurement:** public procurement law (anskaffelsesloven) applies, with tenders above the threshold. Use **innovation partnerships** or pilots under the national welfare tech programme (Helsedirektoratet/KS). Expect requirements for accessibility, information security, and integration with alarm response centres.
- **Accessibility:**
  - Universal design duty under likestillings- og diskrimineringsloven.
  - The European Accessibility Act covers DNB's banking services, and the companion app is borderline.
  - Design rules: never rely on colour alone (deuteranopia makes red and green hard to tell apart), so use icon + colour + **sound** + large text. Write warnings in plain Norwegian. Add a physical "call my family" button.

### 4.5 Must change before a pilot (blocking)

1. **TLS certificate verification on.** Pin the backend certificate or CA bundle.
2. **No vendor API keys in device flash.**
   - The device talks only to our backend, using per-device credentials (X.509 or a device token).
   - The backend proxies Deepgram and Anthropic, or mints short-lived Deepgram tokens.
   - Turn on ESP32-S3 **flash encryption + secure boot v2**.
3. **EU endpoints.** Use `api.eu.deepgram.com` with `mip_opt_out=true`. Send Claude through an EU-region provider (Bedrock or Vertex EU), or document the transfer basis and sign zero data retention.
4. Signed OTA updates, a remote kill switch, and a device revocation list.
5. Persist no transcripts. Redact logs. Delete event history automatically.
6. A DPIA, Art. 28 agreements, a privacy notice, and a consent flow for the elder, plus a guardian flow.
7. Fail-safe UX. When the network or API is down, show "not protected" (grey or blinking) instead of a false green.
8. An evaluation harness with a held-out Norwegian scam and legitimate-call set. The `evals/` folder already exists to build on.

### 4.6 Other key risks

- **False reassurance.** A green light during a novel scam is worse than having no device. Use "no warning", not "safe".
- **False alarms.** These erode trust and cause legitimate calls (bank, doctor) to be hung up.
- **Acoustic capture.** The call must be on speaker, so mobile handset calls are the gap (section 1.1).
- **Adversarial callers.** Prompt injection spoken in the call ("ignore your instructions") can steer the model.
- **Vendor dependency and price.** Promotional STT pricing ends.

---

## 5. 12-month roadmap: hackathon to DNB pilot (Nov 2026 to Oct 2027)

| Month | Workstream | Exit criteria |
|---|---|---|
| **M0-1** | Security hardening (section 4.5, items 1-4). Backend proxy. EU endpoints. Start the DPIA. Letter of intent with DNB's fraud and customer-care teams | No secrets on the device. Pen-test of the device and backend by an external party |
| **M1-3** | **Evaluation dataset.** About 300 scripted Norwegian/English scam calls (fake police, fake bank, "safe account", investment, grandchild), modelled on DNB and Økokrim case typologies. About 1,000 legitimate calls (family, GP, real DNB calls), with consent. Offline benchmark of prompt and cadence. Choose the STT tier | Lab detection ≥90% red/yellow. Red on legitimate calls ≤1% |
| **M2-4** | Alpha with 25 households (employees' parents). Industrial design v1. CE/RED pre-scan. Data processing agreements signed. Elder consent flow with accessibility testing using SUS and seniors | ≥80% weekly active. SUS ≥70. Acoustic capture works in ≥90% of real calls |
| **M4-5** | Pilot design with DNB. **500 households** of DNB customers aged 75+, invited through the fraud team (previously targeted) and customer care, with a **matched control group** from DNB's fraud data. Plus **"mystery caller" red-team calls**: about 2 per household, consented and scripted, to measure detection in the field | DPIA signed off. Pilot agreement. DNB vendor due diligence (outsourcing/ICT) passed |
| **M5-6** | Build 600 units (CE-marked). Logistics. Install support (phone + home visit option) | Units delivered. Onboarding NPS ≥40 |
| **M6-12** | **6-month pilot.** Monthly KPI reviews. Model and prompt iterations behind feature flags | See KPIs below |
| **M12** | Go/no-go for a DNB rollout to a targeted segment (5-10k), B2C launch, telco/insurer talks | Business case using measured incidence |

**Pilot KPIs:**

| KPI | Definition | Target |
|---|---|---|
| Detection rate (recall) | Share of scam calls (red-team calls plus DNB-confirmed attempts) that reach yellow or red | ≥90% yellow-or-red. ≥75% red |
| False alarm rate | Red on legitimate calls | ≤0.5% of calls and ≤1 per household per month. Yellow ≤3% |
| Time-to-warning | Seconds from the first scam indicator (e.g. "safe account", BankID code request) to red | Median ≤20 s, p90 ≤45 s. Always **before** a code or payment request is completed |
| Prevented loss | DNB-confirmed attempted frauds where the customer did not pay × the amount attempted, vs the control group | Report in NOK. Target ≥ the pilot cost (500 × NOK 1,188 ≈ **NOK 0.6M**) [A] |
| Availability | Share of call minutes with an active analysis path | ≥99% |
| Retention / acceptance | Households still active at the end of the pilot | ≥80% |
| Cost per user-month | Cloud AI plus backend | ≤NOK 35 |

**Statistical caveat [A]:** at about 1-5% annual real-attack incidence, 500 households over 6 months gives only about 3-12 real events. That is too few to estimate prevented loss precisely. The red-team calls carry the detection KPIs, and real events serve as supporting evidence. Larger-scale proof requires the 5-10k rollout.

---

## Key numbers

- **TAM:** Norway ≈643k households (NOK 1.15bn/yr). Nordics ≈4.0M households (€624M/yr). EU-27 ≈66M households (€8.0bn/yr).
- **SAM:** Norway ≈272k households (NOK 486M/yr). Nordics ≈1.49M (€232M). EU ≈16M (€2.0bn).
- **SOM:** Norway ≈13.6k households, NOK 24M ARR by year 3. Nordics ≈26k (€4M ARR) by year 5.
- **Cost per 10-minute call:** ≈$0.10 (NOK 1.09) at multilingual list prices, of which Haiku is $0.0115. ≈$0.054 on the Growth tier.
- **Cloud AI cost per user-month** (60 × 5 min): NOK 33 at list price, NOK 17 on Growth. **All-in** ≈NOK 71 (B2C) or ≈NOK 47 (bank).
- **Price:** B2C NOK 149/month including VAT. Bank NOK 99 (79 at ≥10k) per user-month. Municipality NOK 129/month.
- **Payback:** one prevented NOK 200k fraud = 112 subscription-years. A bank breaks even at 1 prevented fraud per ~160-170 users per year.
