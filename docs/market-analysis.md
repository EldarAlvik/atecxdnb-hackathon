# Scam Call Guardian: market analysis

ATEC x DNB hackathon, 5 November 2026. Built on three desk-research notes in `docs/research/` (fraud landscape, competitors, sizing/business/regulation). No new field research.

Conventions:
- **[A]** = analyst assumption. **[V]** = secondary source or unconfirmed, check before external use. Everything else has an inline source.
- FX: 1 USD = 10.5 NOK, 1 EUR = 11.5 NOK [A]. The fraud-landscape note used 10.4 and 11.7. The difference is under 2% and changes no conclusion; this document uses the sizing note's rates so the unit economics stay consistent.
- Disagreements between the notes are flagged as **Conflict:** with the figure chosen.

---

## 1. Executive summary

- **The loss pool is real and has shifted to manipulation.** Norwegian banks reported NOK 962M in fraud losses in 2025 (NOK 1,227M in 2024). About 53% (NOK 514M) was "social manipulation", where the victim is talked into paying, and that bucket grew about 68% half-on-half in H2 2025 ([Finanstilsynet H2 2025](https://www.finanstilsynet.no/publikasjoner-og-analyser/svindel-og-svindelstatistikk/2025/h2/svindelstatistikk-andre-halvar-2025/)). DNB alone saw NOK 3.3bn attempted in 2025, up 30% (DNB, [V]).
- **The victim usually pays the bill.** Authorised payments fall outside the NOK 450 / 12,000 caps in finansavtaleloven, and even the coming EU PSR refund covers only bank impersonation, not fake police, investment or romance fraud. In the EEA, users bore about 85% of credit-transfer fraud losses ([EBA/ECB, Dec 2025](https://www.eba.europa.eu/publications-and-media/press-releases/joint-eba-ecb-report-payment-fraud-strong-authentication-remains-effective-fraudsters-are-adapting)). Prevention during the call is the only protection for most of this loss.
- **Nobody judges the content of a live Norwegian call today.** Telcos filter numbers before connect, banks act at the transaction, and Google's on-device Scam Detection covers 12 countries plus the US, not Norway ([Google](https://support.google.com/phoneapp/answer/15654065?hl=en)). The closest products (SeniorSafe, OnGuardAI) are English-only and North American.
- **Market (Norway, 75+, product fit):** SAM of about 272k households, NOK 486M/yr at B2C price; TAM of 643k households (67+), NOK 1.15bn/yr; SOM of 13.6k households and NOK 24M ARR by year 3 [A]. Nordic SAM about 1.49M households (EUR 232M/yr); EU-27 SAM about 16M households (EUR 2.0bn/yr).
- **Unit economics work at list prices.** A 10-minute call costs about USD 0.10 (NOK 1.09), 89% of it speech-to-text. A typical user (300 call-minutes/month) costs NOK 33 in cloud AI and about NOK 71 all-in, or NOK 47 on a bank contract with committed STT pricing. Proposed prices: NOK 149/month B2C, NOK 99 (79 at 10k+) per user-month to a bank, NOK 129 to municipalities. Contribution margin 40-53%.
- **The bank's direct P&L case is thin; the targeted case is not.** Average expected loss is about NOK 300 per 67+ person per year [A]. Society breaks even at one prevented NOK 200k fraud per roughly 135-170 users per year. DNB's own P&L, which carries only a minority of authorised-fraud losses, needs roughly one per 27 users [A]. So DNB should fund it for a high-risk segment (previously targeted, 80+, living alone) and for reputation and PSR readiness, not as a mass give-away.
- **The biggest product risk is acoustic, not AI.** The ESP32-S3 has no Bluetooth Classic, so it cannot tap a mobile call. It only hears both sides if the call is on speaker. Most Norwegians 75+ take calls on a mobile held to the ear [A]. This has to be measured in the alpha before anything else.
- **Recommendation: go, as a DNB-backed pilot, after a security and privacy rebuild.** Harden the device (no API keys in flash, TLS verification, EU endpoints), use the 96-scenario Inspect AI suite as the pre-pilot gate, then run a 500-household DNB pilot with a matched control group and red-team calls. Decide on rollout at month 12 against four KPIs: detection rate, false-alarm rate, time-to-warning, prevented loss.

---

## 2. The problem

### 2.1 Losses and trend

| Year | Total bank-reported loss (NOK M) | Social manipulation (NOK M) | Fraud prevented by banks (NOK M) |
|---|---|---|---|
| 2023 | 928.7 | 468 | 2,072 |
| 2024 | 1,227.4 (+32%) | 667 (54%) | 2,964 |
| 2025 | 962 | 514 (53%) | 3,537 |

Source: Finanstilsynet [H2 2024](https://www.finanstilsynet.no/publikasjoner-og-analyser/svindel-og-svindelstatistikk/2024/h2/svindelstatistikk-andre-halvar-2024/) and [H2 2025](https://www.finanstilsynet.no/publikasjoner-og-analyser/svindel-og-svindelstatistikk/2025/h2/svindelstatistikk-andre-halvar-2025/). Finans Norge's "about NOK 1.2bn" for 2024 matches ([Finans Norge, Apr 2026](https://www.finansnorge.no/contentassets/07a8760849a64401a348b6eeecc4ca7a/innspill-til-finanskomiteen-om-prop.-39-l-2025-2026.pdf)).

The 2025 dip is not a recovery. Losses rose from NOK 404M in H1 to 558M in H2, and payer-manipulated account transfers rose about 68% half-on-half to NOK 282M. Fraudster-initiated transfers were only NOK 101M. The growth is in authorised push-payment fraud.

These are floor figures. Police-reported fraud proceeds were NOK 2.13bn in 2024 (Økokrim figures, [V]), and Økokrim says the dark figures are large ([Finans Norge/Økokrim](https://www.finansnorge.no/contentassets/67158a92e52744de98fe4b7ad9da96f8/merete-klingenberg-og-pal-lonseth-okokrim.pdf.pdf)).

DNB view (search snippets of DNB pages, all [V]): attempted fraud against DNB and its customers was about NOK 1.2bn in 2022, 2.5bn in 2024 and 3.3bn in 2025; DNB stopped about 90% in 2025 and handled 9,970 fraud cases ([DNB, Jan 2026](https://www.dnb.no/dnbnyheter/no/samfunn/dnb-har-aldri-stoppet-flere-svindelforsok-)). DNB expects investment fraud and AI misuse to grow further in 2026.

### 2.2 What manipulation fraud looks like

No official Norwegian split by channel exists. The known scripts are:
- **Fake police / fake bank ("trygg konto").** Caller spoofs or claims police or bank, says the account is compromised, gets the victim to move money or read out BankID codes, sometimes sends a courier. Nordea reports victims kept on the line "for many hours" ([Nordea, 2024](https://www.nordea.com/no/nyheter/eldre-personer-utsatt-for-malrettet-svindel)). Økokrim puts the average loss at about NOK 200,000 per case, with some above NOK 1M (data from about 2022, [Økokrim](https://okokrim.custompublish.com/getfile.php/5151932.2528.lzwiim7wqsjank/Politibedrageri+-+offentlig+rapport_nett.pdf)).
- **Investment fraud.** 37% of DNB fraud victims in 2024, average attack about NOK 170,000 (DNB, [V]).
- **"Hei mamma"** and **romance fraud**: warned about, no NOK totals.

**Conflict:** average loss per case. Økokrim says NOK 200k (fake police/bank, about 2022). DNB says NOK 170k (investment fraud, 2024, unverified). This document uses **NOK 200k** because it is the phone-impersonation figure and the better-sourced one; the payback math is shown to be robust to the lower figure.

### 2.3 Who is hit

- Older women are the named high-risk group for investment, romance and "safe account" fraud ([DNB](https://www.dnb.no/dnbnyheter/no/samfunn/ser-brutal-svindeltrend---eldre-kvinner-utsatt), [V]), and Oslo police said the same about fake police/bank calls in 2026 ([TU/NTB](https://www.tu.no/nyhetsstudio/politiet-advarer-svindlere-utgir-seg-for-aa-vaere-politi-eller-bank/126628)).
- DNB explains why: older people have high trust in banks and authorities ([DNB, Mar 2026](https://www.dnb.no/dnbnyheter/no/samfunn/derfor-er-eldre-ekstra-utsatt-for-svindel), [V]).
- 34% of home-dwelling 67+ live alone, 53% of 80+ ([SSB](https://www.ssb.no/befolkning/artikler-og-publikasjoner/1-av-3-eldre-bor-alene)). One in four elderly people living alone has no family nearby ([SSB](https://www.ssb.no/sosiale-forhold-og-kriminalitet/levekar/artikler/1-av-4-aleneboende-eldre-har-ingen-familie-i-naerheten)).
- Gap: there is no Norwegian loss split by age.

### 2.4 Why phone social engineering beats existing controls

| Control | What it stops | Why the scam call gets through |
|---|---|---|
| Telco filters (Telenor Nummervarsel, Telia Svindelsperre, shared anti-spoofing since Dec 2024) | Spoofed Norwegian numbers, invalid foreign numbers, bulk calls. Telia spoofing calls fell from >90M (2024) to 44M (2025) ([Telia/NTB](https://kommunikasjon.ntb.no/pressemelding/18774365/telia-norges-svindelrapport-2025-stoppet-nesten-100-millioner-svindelforsok-ser-okning-i-malrettede-angrep)) | Judges the number, not the conversation. Single-use foreign numbers look like normal traffic ([securityworldmarket](https://www.securityworldmarket.com/no/Nyheter/Bedriftsnyheter/stor-svindelanropsbolge-mot-norge)). Scammers moved to Swedish/Danish numbers ([tek.no](https://www.tek.no/nyheter/nyhet/i/jQgEl0/nytt-digitalt-skjold-mot-mobilsvindel-fungerer)) |
| Bank transaction monitoring | Unusual transfers; DNB stopped about 90% of attempts | Acts after the victim is persuaded, and the victim often overrides warnings or re-tries |
| BankID / SCA | Unauthorised access | The victim authenticates the transfer themselves |
| Bank call-status apps (Monzo, Revolut, CommBank) | Bank impersonation | Not in Norway; useless when the caller claims to be police |
| Awareness campaigns | General risk | Scripts are personalised, use the victim's name and AI-polished language (Telia, Jan 2026) |

**Conflict:** telco block volumes. Telia reports 95.1M scam calls blocked in 2025; a Telecompaper summary says about 100M; bytt.no says the three operators together blocked "over 80 million" scam attempts, which cannot be true if Telia alone blocked 95M. This document uses the operators' own figures (Telia 95.1M, Telenor 37.9M calls in 2025) and drops the joint figure.

---

## 3. Customer segments and jobs-to-be-done

| Segment | Persona | Job to be done | Pays? |
|---|---|---|---|
| **Protected elderly person** | Ingrid, 82, widow in Bærum. Takes calls on a basic mobile or the speaker of an old smartphone. Trusts "the bank" and "the police". Does not install apps. | "Tell me, while I'm still on the phone, if this person is trying to trick me, without making me feel stupid." | Rarely. Must consent and must keep the device plugged in. |
| **Worried adult child** | Erik, 52, lives in Trondheim, calls his mother weekly, has read about "trygg konto" cases. Android phone, does the setup. | "Know that Mum is protected, and hear about it fast if something happens, without spying on her." | Yes. B2C buyer at NOK 149/month. |
| **Bank (DNB)** | Head of fraud prevention, plus customer-care and ESG leads. 2.4M retail customers ([DNB](https://m.dnb.no/en/about-us)). Measured on losses, complaints and reputation. | "Stop manipulation before the customer authorises the payment, show regulators and press that we protect vulnerable customers, and be ready for PSR." | Yes, for a targeted segment, white-label. |
| **Municipality (velferdsteknologi)** | Welfare-technology coordinator in a mid-sized kommune running trygghetsalarm for a few thousand residents. | "Keep older residents safe at home with tools that fit our alarm-central workflow and procurement rules." | Yes, as an add-on. Users already pay NOK 250-550/month for alarms ([Lørenskog](https://lorenskog.kommune.no/_f/p71/i491728c8-a54a-4ba6-833d-5f01bd7e6349/kommunale-gebyrer-og-brukerbetalinger-helse-og-omsorg-2025.pdf), [Bærum](https://www.baerum.kommune.no/globalassets/tjenester/helse-og-omsorg/pris/prisliste-2025-helse--og-omsorgstjenester.pdf), [Porsgrunn](https://www.porsgrunn.kommune.no/media/5203/soeknad-om-trygghetsalarm.pdf)). Slow procurement. |
| **Telco** | Product manager for senior plans at Telenor/Telia/Ice. Already blocks tens of millions of calls a year and owns the billing relationship. | "Add protection during the call that our network cannot provide, as a paid add-on or a retention feature." | Possibly, as a bundle. May prefer a network-side solution. |
| **Insurer** | Product owner for innboforsikring with ID-theft/fraud cover. | "Reduce fraud claims and differentiate senior policies." | Later. Needs claims evidence from the bank pilot first. |

---

## 4. Market size

The device is sold per household. Price basis is B2C NOK 149/month incl. VAT = NOK 1,788/yr (EUR 155). EU uses EUR 10/month = EUR 120/yr [A]. Note TAM/SAM values are at consumer price incl. VAT; net revenue is 20% lower ex VAT, and bank pricing is lower again (section 6).

### 4.1 Inputs

- Norway 67+: 959,633; 80+: 284,939 ([SSB](https://www.ssb.no/befolkning/folketall/statistikk/befolkning), 1 Jan 2026).
- Norway 75+: about 535,000 [A] (80+ plus an estimated 250k aged 75-79; replace with SSB table 07459).
- Households: 67+ is 959,633 × 34% alone = 326k single households, plus 633k ÷ 2 = 317k couple households = **643k** (0.67 per person). 75+ uses 45% living alone [A], giving **388k** households (0.725 per person).
- Product-fit filter [A]: 70% in the Nordics (broadband, a place next to the phone, speaker use, a relative to set up). EU uses 50% to also account for all-party-consent countries such as Germany.

### 4.2 TAM / SAM / SOM

| Layer | Norway | Nordics (NO, SE, DK, FI, IS) | EU-27 |
|---|---|---|---|
| Base | 67+ = 959,633 | 65+ ≈ 5.99M (SE 2.22M, DK 1.27M, FI 1.35M, NO 1.08M [A], IS 0.07M [A]; sources in sizing note) | 65+ = 450.6M × 22% = 99.1M ([Eurostat](https://ec.europa.eu/eurostat/statistics-explained/index.php/Population_structure_and_ageing)) |
| **TAM** (all elderly households) | 643k × NOK 1,788 = **NOK 1.15bn/yr** | 5.99M × 0.67 = 4.0M × EUR 155 = **EUR 624M/yr** | 99.1M × 0.67 = 66.4M × EUR 120 = **EUR 8.0bn/yr** |
| **SAM** (75+, product fit) | 388k × 70% = **272k households**, × 1,788 = **NOK 486M/yr** | 75+ ≈ 49% of 65+ [A] = 2.94M × 0.725 × 70% = **1.49M households**, **EUR 232M/yr** | 75+ ≈ 10% of 451M [A] = 45.1M × 0.725 × 50% = **16.3M households**, **EUR 1.96bn/yr** |
| **SOM** | 5% of SAM by year 3 [A] = **13.6k households**, **NOK 24M ARR** | Norway 13.6k + 1% of rest-of-Nordic SAM (1.22M) = 12.2k, so **≈26k households**, **≈EUR 4.0M ARR** by year 5 [A] | 0.1% of SAM by year 5 [A] = **16k households**, **≈EUR 2M ARR** |

**DNB slice [A]:** if DNB holds about 33% of 75+ banking relationships, its share of the Norwegian SAM is 272k × 0.33 ≈ **90k households**. A top-10% risk tier is about **9k households**, which is the realistic first rollout.

**Value at stake, as a sanity check [A]:** if 30% of 2025's NOK 962M losses were phone-initiated fraud against people 67+, that is NOK 289M/yr, about NOK 300 per 67+ person. The SAM at consumer price (NOK 486M) exceeds this, which says the market will be bought on fear, care and reputation, and on concentrated risk, not on average expected loss. Treat the SOM, not the SAM, as the planning number.

---

## 5. Competitive landscape

### 5.1 Comparison

RT = analyses the call in real time. Content = judges what is said, not the number or voice. NO = Norwegian. LL = works with landlines/basic phones. Fam = family loop.

| Product | RT | Content | NO | LL | Fam | Price | Main gap for our user |
|---|---|---|---|---|---|---|---|
| **Scam Call Guardian** | Yes, ~6 s cadence | Yes (LLM) | Yes | Yes, if on speaker | Yes | NOK 99-149/mo | Speakerphone needed, cloud audio, unproven accuracy |
| Google Pixel / Samsung Scam Detection | Yes | Yes | No | No | No | Free on Pixel 9+ / S26 | Not in Norway, needs a new flagship |
| Apple Call Screening | Pre-answer | Stated reason | No | No | No | Free | Stops once answered |
| Truecaller / Hiya AI Phone | Voice-clone only | No | Unknown | No | No | Premium, Hiya USD 9.99/mo | Detects synthetic voice, not human scammers |
| Norton, McAfee, Bitdefender, Trend | No | Text/web | Unclear | No | Partial | ~USD 20-95/yr | No live call audio |
| Telenor / Telia / Ice filters | Pre-connect | Number only | n/a | Mobile yes | No | Included | Nothing during the call |
| Bank call-status (Monzo, Revolut, CommBank) | Status only | No | Not in NO | No | No | Free | Bank impersonation only; DNB has none |
| trueCall / CPR (UK) | Pre-answer | No | No | Yes | trueCall Care | GBP 60-120 + GBP 25/yr | Allowlist, no AI, not sold in Norway |
| SeniorSafe (US) | Yes (claimed) | Yes (claimed) | No | Yes (claimed) | Yes | Unknown | US, English |
| OnGuardAI (CA) | Yes | Patterns | No | No | SMS to family | Unknown | Smartphone app, EN/FR |
| Pindrop / Reality Defender | Yes | Voice synthesis | Language-agnostic | n/a | n/a | Enterprise | Protects the bank's line, not the customer |

Sources: [Google](https://support.google.com/phoneapp/answer/15654065?hl=en), [MacRumors](https://macrumors.com/guide/ios-26-phone-app), [Truecaller](https://www.businesswire.com/news/home/20240528009351/en/Introducing-The-World%E2%80%99s-First-AI-Call-Scanner-by-Truecaller-The-Fastest-Most-Accurate-AI-Voice-Scam-Detection-System), [Hiya](https://businesswire.com/news/home/20250128511230/en/Hiya-Launches-First-AI-Call-Assistant-That-Stops-Live-and-Deepfake-Scams-in-Real-Time), [bytt.no](https://bytt.no/artikler/forbruker/a-wnlx/hvilke-mobiloperatorer-har-svindelfilter-inkludert-i-2026), [Monzo](https://monzo.com/help/monzo-fraud-category/monzo-call-status), [RNIB/trueCall](https://shop.rnib.org.uk/products/truecall-vi-stop-nuisance-calls), [SeniorSafe](https://www.easternprogress.com/sentien-unveils-game-changing-ai-platform-seniorsafe---stops-both-human-and-ai-deepfake/article_43448250-8be0-5b5c-a3ef-a0ac9cd77286.html), [OnGuardAI](https://betakit.com/?p=408872), [Reality Defender](https://realitydefender.com/insights/real-time-deepfake-detection-in-call-centers). Vendor accuracy claims are self-reported.

### 5.2 Positioning statement

For older Norwegians who take calls on any phone, and for the adult children and banks who worry about them, Scam Call Guardian is a phone-agnostic bedside device that listens to the conversation in Norwegian and warns with a traffic light while the call is still going. Unlike telco filters, it judges what the caller says, not the number. Unlike bank call-status features, it catches fake police and fake family, not only fake bank staff. Unlike Google's Scam Detection, it works in Norway, on old phones, and keeps the family in the loop.

### 5.3 Threats

1. **Google ships Norwegian live Scam Detection (high likelihood, 12-24 months [A]).** Google adds countries at Pixel Drops and has extended to Samsung. Impact: removes the "nobody does Norwegian" claim for flagship owners. Defence: our segment mostly does not own a Pixel 9 or S26 [A]; we keep landline/old-phone users, the family loop and the bank tie-in. Google being on-device also sets the privacy bar we will be compared against.
2. **Telenor + Hiya add in-network content AI (medium).** Hiya already powers Telenor Nummervarsel and has real-time voice AI. A network service needs no device. Lawful-interception and privacy rules make it hard but possible as opt-in. Defence: partner with a telco rather than compete (section 7).
3. **DNB or Nordea build "ringer vi deg nå?" call status (high, cheap).** This covers the bank-impersonation slice. It is complementary: the device can show "DNB is not calling you" as one input.
4. **US/UK startups localise (medium).** SeniorSafe, Scammer Guardian (landline box planned for late 2026) and OnGuardAI could add Nordic languages; trueCall could add AI. Defence: Norwegian eval data and a DNB reference customer.
5. **Apple adds in-call analysis (low-medium).** Mostly hits the adult-child segment's own phones.

---

## 6. Business model and unit economics

### 6.1 Cost per call

LLM: `claude-haiku-5-5` at USD 0.10 / 0.50 per million input/output tokens. The sizing note used this price "as given"; I checked it against Anthropic's current model table on 5 Nov 2026 and it holds. Sensitivity: on Haiku 4.5 (USD 1 / 5) the LLM line would be 10× higher, roughly matching STT. The board runs Haiku with thinking disabled and `max_tokens` 120 (`evals/scenarios/SCHEMA.md`); leaving thinking on would raise output tokens.

Per 10-minute call: 100 requests × ~1,000 input tokens (650-token transcript window + 350-token system prompt) = 100k tokens × USD 0.10/M = USD 0.0100, plus 3k output tokens × USD 0.50/M = USD 0.0015. **Haiku = USD 0.0115.** Prompt caching does not help: the system prompt is below the cacheable minimum and the window changes each request.

| Scenario | Deepgram Nova-3 USD/min | 10-min call total | NOK |
|---|---|---|---|
| A: multilingual list price | 0.0092 | 0.092 + 0.0115 = **USD 0.104** | 1.09 |
| B: monolingual Norwegian list | 0.0077 | USD 0.089 | 0.93 |
| C: Growth tier [V] (USD 4k+/yr prepaid) | ~0.0042 | USD 0.054 | 0.56 |

Deepgram prices are from secondary pricing summaries [V] ([activepieces](https://www.activepieces.com/blog/deepgram-pricing-2026-per-minute-rates-and-add-ons)); confirm on deepgram.com/pricing.

### 6.2 Cost per user-month

Typical user: 60 calls × 5 min = 300 min/month [A].

| Line (NOK) | B2C (scenario A) | Bank contract (scenario C) |
|---|---|---|
| Deepgram | 29.0 | 13.2 |
| Haiku | 3.6 | 3.6 |
| Backend, DB, push, logs [A] | 3 | 3 |
| Support at scale [A] | 15 | 10 |
| Payment fees [A] | 3 | 0 |
| Device NOK 600 landed over 36 months [A] | 17 | 17 |
| **All-in** | **≈71** | **≈47** |

A heavy user (960 min/month) costs about NOK 104 in cloud AI alone, so a fair-use cap or call gating is required. Hardware: BOM plus assembly NOK 270-455 at 1k units, landed NOK 450-650 including amortised CE/RED certification of NOK 150-250k [A]; NOK 200-300 BOM at 10k units [A].

### 6.3 Pricing per channel

| Channel | Price | Contribution |
|---|---|---|
| B2C (sold to adult children) | NOK 149/month incl. VAT (119 ex VAT), device included, 12-month term; or NOK 990 device + 99/month | (119 − 71) / 119 = **40%**; ~54% with call gating [A] |
| Bank white-label | NOK 99/user-month ex VAT at ≥1k units, NOK 79 at ≥10k | (99 − 47) / 99 = **53%**; 41% at NOK 79. DNB cannot deduct VAT, so its real cost is ×1.25 |
| Municipality | NOK 129/month ex VAT as trygghetsalarm add-on | ~45-60% |
| Telco / insurer | Wholesale, priced near the bank tier [A] | ~40-50% [A] |

### 6.4 Payback

- **Family:** one prevented NOK 200k loss = 112 years of B2C subscription. At NOK 170k it is still 95 years. The emotional and financial logic is clear.
- **Society:** at the bank's real cost of NOK 1,485/user-year (1,188 × 1.25 VAT), one prevented NOK 200k fraud per **≈135 users per year** pays for the programme (0.7% incidence); about 1 per 115 at NOK 170k.
- **DNB's own P&L [A]:** for authorised fraud the bank carries only part of the loss. Using the EEA figure that banks bore about 15% of credit-transfer losses (EBA/ECB), plus NOK 10k fraud-handling cost [A], DNB avoids roughly 200k × 15% + 10k = **NOK 40k** per prevented case. Break-even is then about **one prevented case per 27 users per year (3.7% incidence)**.
- **Incidence anchor [A, derived]:** DNB handled 9,970 fraud cases in 2025 against 2.4M retail customers, about 0.4% per customer per year (all ages, all types). A previously-targeted 80+ segment plausibly runs at 5-10× that, i.e. 2-4%, which is close to DNB's break-even.

**Conflict:** the sizing note says the bank break-even is one prevented loss per about 160-168 users, which assumes the bank bears the whole NOK 200k. The fraud-landscape note shows that for authorised payments the customer normally bears the loss. This document uses the lower bank-borne figure: DNB's direct case only works for a narrow high-risk segment, and the rest of the case is reputation, customer outcome, care cost and PSR readiness.

### 6.5 Where cost can be cut

1. **Gate by caller.** Stream only calls from unknown numbers or numbers not in the family's contact list. Needs caller ID from the phone, which the BLE-only device does not have today.
2. **Sampling after green.** Drop to periodic sampling after about 3 minutes of green. Items 1 and 2 together: 40-60% fewer STT minutes [A].
3. **Committed STT pricing.** Growth tier roughly halves STT cost [V].
4. **Monolingual model** where calls are Norwegian-only: -16% STT.
5. **Smaller LLM window.** Haiku is already only ~11% of variable cost, so this matters little. The cadence could even drop to 3 s for faster warnings at almost no cost.
6. **Hardware at volume**: NOK 600 to ~350 landed at 10k units [A] saves about NOK 7/user-month.

---

## 7. Go-to-market

### 7.1 Why DNB first

- **Liability.** Under finansavtaleloven § 4-30 the customer pays at most NOK 450 (ordinary negligence) or NOK 12,000 (gross negligence) for *unauthorised* transactions. Most phone manipulation leads to *authorised* payments, which the customer bears in full ([bytt.no](https://www.bytt.no/bank/sparekonto/a-onay/banksvindel-du-ma-sjelden-dekke-mer-enn-450-kroner-selv), [DNB](https://www.dnb.no/dnbnyheter/no/samfunn/da-ma-du-betale-inntil-12-000-kroner-i-egenandel)). But the line is contested: in several "safe account" cases Finansklagenemnda found the payments were not validly authorised, putting the loss on the bank ([bytt.no](https://www.bytt.no/artikler/forbruker/a-nnjy/tomt-konto-og-avvist-av-banken-slik-slar-du-tilbake-mot-urimelige-svindelkrav)). FinKN fraud complaints rose from 612 (2024) to 834 (2025), with consumers winning fully or partly in 35.5% [V]. Every prevented case is also a complaint not filed.
- **PSR.** The EU PSR (political agreement Nov 2025, Council position Oct 2026) makes the PSP refund consumers who were tricked by someone impersonating *their own bank*, and can make a receiving PSP that ignored fraud signals bear the loss ([Finologee](https://finologee.com/psr-what-the-provisional-political-agreement-signals-for-psps/), [William Fry](https://www.williamfry.com/knowledge/psd3-psr-final-compromise-texts-are-published/)). It excludes police impersonation, investment and romance fraud. EU application is realistically 2028 and later in Norway via the EEA [V]. **Conflict:** the sizing note describes the PSR as making a provider "fully liable if its fraud prevention is inadequate"; the fraud-landscape note describes the narrower receiving-PSP rule. This document uses the narrower reading. Either way, a bank that can show in-call prevention is better placed.
- **Reputation and policy.** Finans Norge names manipulation fraud ("trygg konto", fake police, often aimed at elderly and lonely people) the top challenge for 2026 ([Finans Norge](https://www.finansnorge.no/siteassets/dokumenter/publikasjoner/norske-bankers-bekjempelse-av-svindel---status-og-tiltak-for-2026.pdf)). DNB already runs joint campaigns with the police and Telenor and sent a fraud warning SMS to about 800,000 customers [V]. DNB has no in-app "is DNB calling you?" feature, so this is a visible gap.
- **Distribution and data.** DNB can identify high-risk customers (previously targeted, flagged by the fraud team) and measure prevented loss against a control group. No other channel can.

### 7.2 Pilot design

- **Population:** 500 DNB customers aged 75+, invited through the fraud team (previously targeted) and customer care, with a matched control group from DNB's fraud data.
- **Duration:** 6 months, after a 25-household alpha (employees' parents).
- **Measurement:** about 2 consented, scripted "mystery caller" red-team calls per household for detection KPIs; real attempted frauds as supporting evidence. At 1-5% annual incidence, 500 households over 6 months yields only 3-12 real events [A], too few to measure prevented loss precisely.
- **Integration (phase 2):** "DNB is not calling you" signal into the device; optional hold on large transfers while the device is red.
- **Commercial:** white-label, NOK 99/user-month, pilot cost about NOK 0.6M (500 × 1,188) [A].
- **Hurdles:** DNB vendor due diligence under Finanstilsynet ICT/outsourcing rules and DORA.

### 7.3 After DNB

1. **B2C in parallel** (from month 12): sold to adult children, validates willingness to pay, high acquisition cost.
2. **Telco bundle** (Telenor, Telia, Ice): pitched as the in-call layer on top of network blocking. Telenor + Hiya is both a partner and a threat.
3. **Insurers:** after pilot data exists, as a premium discount or included device on senior innboforsikring.
4. **Municipalities** (from year 2): add-on to trygghetsalarm through alarm centrals, via innovation partnerships or the national welfare-technology programme. Position as safety technology, not health technology.
5. **Nordics** via Nordea, Danske Bank and Handelsbanken; Swedish phone-fraud proceeds fell from SEK 712M (2023) to about 302M (2025) ([Polisen](https://polisen.se/aktuellt/nyheter/nationell/2026/januari/brottsvinsterna-for-telefonbedragerier-fortsatter-att-minska/)), so Sweden is a smaller near-term pull than Norway.

---

## 8. Regulation, privacy and trust

| Area | What applies | What it means for us |
|---|---|---|
| **Straffeloven § 205** | Criminalises secretly listening to communication *between others*. A party may process their own call, so Norway is effectively one-party consent ([recordinglaw.com](https://www.recordinglaw.com/no/world-laws/world-recording-laws/norway-recording-laws/), [V], check Lovdata) | The elder must be the knowing user. A relative installing it without the elder's understanding (e.g. dementia) is a § 205 risk. Needs consent in the elder's own words, a visible "listening" indicator, and a guardian (verge) route |
| **GDPR / DPIA** | Company is controller (joint controller or processor in a bank white-label, to be decided). Deepgram and Anthropic are processors needing Art. 28 agreements. Callers are data subjects under legitimate interest. Doctor calls bring Art. 9 health data | DPIA required (new technology, systematic monitoring, vulnerable subjects, non-users). No audio or transcript persisted; store only verdict, short reason and timestamp; no speaker identification (biometrics). Art. 14(5)(b) reasoning for callers documented. Consider Datatilsynet's AI sandbox |
| **EU AI Act** | Not yet in the EEA; Norwegian KI-lov bill planned spring 2027 ([TU/NTB](https://www.tu.no/nyhetsstudio/nye-eu-regler-for-ki-norge-og-eoes-henger-ennaa-langt-etter/127027)). Probably not high-risk: Annex III 5(b) excludes fraud detection | Art. 50 transparency: the elder must know warnings are AI-generated and can be wrong. Do not market emotion or stress detection. Keep technical documentation and eval records anyway; DNB due diligence will ask |
| **RED cybersecurity (EN 18031)** | Mandatory since 1 Aug 2025 for connected radio equipment [V] | **The hackathon build fails it**: secure credential storage, secure updates and protected communications are needed. CE (RED/EMC/LVD/RoHS) before any unit reaches a non-employee. CRA reporting from Sep 2026, full from Dec 2027 [V] |
| **Data residency** | Deepgram EU endpoint `api.eu.deepgram.com` GA since Jan 2026, with `mip_opt_out=true` ([Deepgram](https://deepgram.com/learn/deepgram-eu-endpoint-now-generally-available)). Anthropic first-party API offers "us"/"global" only ([Claude docs](https://platform.claude.com/docs/de/manage-claude/data-residency), [V]) | Route Haiku through an EU region on Bedrock or Vertex if Haiku 5.5 is available there [V], or rely on DPF/SCCs with a transfer impact assessment and zero data retention. A bank will demand an EU answer |
| **Other** | MDR (stay out: no medical purpose); anskaffelsesloven for municipalities; universal design duty; European Accessibility Act for DNB's services | Never colour alone: icon + colour + sound + large text, plain Norwegian, a physical "call my family" button |

### 8.1 Must change before a pilot (blocking)

1. TLS certificate verification on, backend certificate pinned.
2. No vendor API keys in device flash. Per-device credentials; backend proxies Deepgram/Anthropic or mints short-lived tokens. ESP32-S3 flash encryption and secure boot v2.
3. EU endpoints for STT and LLM, or a documented transfer basis with zero retention.
4. Signed OTA, remote kill switch, device revocation list.
5. No persisted transcripts, redacted logs, automatic deletion of event history.
6. DPIA, Art. 28 agreements, privacy notice, elder consent flow and guardian flow.
7. Fail-safe UX: "not protected" (grey or blinking) when network or API is down, never a false green. Show "no warning", not "safe".
8. Pass the Inspect AI gate (section 11), with a held-out Norwegian set.

---

## 9. Risks and mitigations

| Risk | Likelihood / impact | Mitigation |
|---|---|---|
| **Adoption / speakerphone problem.** ESP32-S3 is BLE-only (no HFP), so the device hears both sides only on speaker. Norway has no PSTN lines left ([Nkom](https://nkom.no/statistikk/statiske-rapporter-og-analyser/_/attachment/download/cba9f5c7-a034-42a4-bb66-ff87a5b5685d:8712419ee263a709792d3ee7fbb32300c83fa5d3/Ekommarkedet%20i%20Norge%202022.pdf)); most 75+ use mobiles at the ear [A] | High / high | Measure capture rate in the alpha (target ≥90% of real calls). Evaluate a hardware revision with a Bluetooth Classic hands-free chip so the device becomes the call's audio endpoint [A]. Onboarding that makes speaker use the habit; a stand that holds the phone |
| **False alarms** on bank, doctor, NAV calls, causing hang-ups and loss of trust | Medium / high | 24 legit look-alike scenarios in the suite now, expand to ~1,000 consented legit calls. Target red ≤0.5% of calls. Yellow tier for "verify" |
| **False reassurance / misses**, including spoken prompt injection | Medium / high | Never show "safe"; 20 adversarial scenarios in the suite; latch red; family alert on red |
| **Latency.** 6 s cadence + STT + LLM ≈ 6-10 s after a red-flag phrase | Medium / medium | Fine for long "trygg konto" calls; cut cadence to 3 s at negligible cost (6.5); KPI median ≤20 s |
| **Warning ignored.** Scammers tell victims to ignore warnings | Medium / high | Family push alert, safe-word check, later DNB transfer hold while red |
| **Cost.** Promotional STT pricing ends; heavy users | Medium / medium | Fair-use cap, call gating, committed STT contract; LLM is only ~11% of variable cost |
| **Accuracy unproven.** No field data; suite is replayed transcripts, so it skips acoustic capture | High / high | Inspect AI gate, then red-team calls in the pilot; publish results to DNB |
| **Big-tech entry** (Google Norwegian, Telenor/Hiya) | High / medium | Own the landline/old-phone segment, the family loop and the bank integration; consider partnering with Telenor |
| **Legal / privacy** (§ 205, GDPR, residency) | Medium / high | Section 8.1 before any home install; Datatilsynet sandbox |
| **Bank procurement drag** (DORA, outsourcing) | High / medium | Start due diligence in month 0; B2C channel as fallback revenue |

---

## 10. SWOT

| | Helpful | Harmful |
|---|---|---|
| **Internal** | **Strengths:** only product judging live Norwegian call content; works with any phone; family loop and safe words; low variable cost (~NOK 33/user-month cloud); an existing 96-scenario NO/EN eval suite | **Weaknesses:** speakerphone dependency; cloud audio to US vendors; accuracy unproven in the field; hackathon security fails RED; hardware logistics and support for 80-year-olds |
| **External** | **Opportunities:** manipulation fraud is 53% of losses and rising; DNB has no call-status feature; PSR raises bank liability; municipalities already pay for safety tech; Nordic and EU expansion through banks | **Threats:** Google adds Norwegian; Telenor + Hiya in-network AI; banks build cheap call-status; US/UK startups localise; bank's direct P&L case is thin |

---

## 11. Recommendation and 12-month roadmap

**Recommendation:** proceed, with DNB as pilot partner and a narrow target segment (previously targeted, 80+, living alone). Do not pitch population-wide distribution: the average expected loss does not cover it. Pitch it as DNB's in-call layer next to transaction monitoring, with B2C as a parallel test of willingness to pay. Solve acoustic capture and security before scaling anything.

### 11.1 Pre-pilot measurement: the Inspect AI suite

The suite in `evals/` replays calls the way the board sees them: chunked, unlabelled, noisy transcripts sent to Haiku every ~6 s, with the red latch. It holds 96 scenarios: 22 scam calls, 24 legitimate look-alikes, 20 adversarial calls and 30 broken-dictation cases, in Norwegian and English. Each scam has a `detect_by` chunk, so it already measures detection rate, false alarms and time-to-warning (in chunks × 6 s) offline, for any prompt or model change. Two smoke-run logs exist; full-suite results are not yet reported here.

Limits: 24 legit scenarios cannot show a ≤1% false-alarm rate (one false red is 4%), and the suite bypasses the microphone. It is the gate for the classifier, not proof of the product. Expand it to about 300 scam and 1,000 legit calls in months 1-3, with a held-out Norwegian split.

### 11.2 Roadmap (Nov 2026 to Oct 2027)

| Month | Work | Exit criteria |
|---|---|---|
| M0-1 | Security items 1-4 (section 8.1); backend proxy; EU endpoints; DPIA started; LOI with DNB fraud and customer care | No secrets on device; external pen test passed |
| M1-3 | Full Inspect AI runs on every change; expand dataset; choose STT tier and cadence | Suite gate: scam recall ≥90% yellow/red, ≥75% red by `detect_by`; zero red on legit set |
| M2-4 | Alpha, 25 households; industrial design v1; CE/RED pre-scan; consent flow tested with seniors | Acoustic capture ≥90% of real calls; ≥80% weekly active; SUS ≥70 |
| M4-5 | DNB pilot design; vendor due diligence; DPIA signed off | Pilot agreement signed |
| M5-6 | Build 600 CE-marked units; install support | Onboarding NPS ≥40 |
| M6-12 | 6-month pilot, 500 households + control; monthly KPI review; prompt/model changes behind flags, each gated on the suite | KPIs below |
| M12 | Go/no-go: DNB rollout to 5-10k high-risk households, B2C launch, telco/insurer talks | Business case on measured incidence |

### 11.3 KPIs

| KPI | Definition | Pilot target |
|---|---|---|
| Detection rate | Scam calls (red-team + DNB-confirmed) reaching yellow or red | ≥90% yellow/red, ≥75% red |
| False-alarm rate | Red on legitimate calls | ≤0.5% of calls, ≤1 per household per month; yellow ≤3% |
| Time-to-warning | Seconds from first scam indicator to red | Median ≤20 s, p90 ≤45 s, always before a code or payment is given |
| Prevented loss | DNB-confirmed attempts not paid × amount, vs control | ≥ pilot cost (NOK 0.6M) [A] |
| Availability | Call minutes with an active analysis path | ≥99% |
| Retention | Households active at pilot end | ≥80% |
| Cost | Cloud AI + backend per user-month | ≤NOK 35 |

---

## 12. Sources

Fraud and liability
- https://www.finanstilsynet.no/publikasjoner-og-analyser/svindel-og-svindelstatistikk/2024/h2/svindelstatistikk-andre-halvar-2024/
- https://www.finanstilsynet.no/publikasjoner-og-analyser/svindel-og-svindelstatistikk/2025/h2/svindelstatistikk-andre-halvar-2025/
- https://www.finansnorge.no/contentassets/07a8760849a64401a348b6eeecc4ca7a/innspill-til-finanskomiteen-om-prop.-39-l-2025-2026.pdf
- https://www.finansnorge.no/contentassets/67158a92e52744de98fe4b7ad9da96f8/merete-klingenberg-og-pal-lonseth-okokrim.pdf.pdf
- https://www.finansnorge.no/siteassets/dokumenter/publikasjoner/norske-bankers-bekjempelse-av-svindel---status-og-tiltak-for-2026.pdf
- https://okokrim.custompublish.com/getfile.php/5151932.2528.lzwiim7wqsjank/Politibedrageri+-+offentlig+rapport_nett.pdf
- https://www.dnb.no/dnbnyheter/no/samfunn/dnb-har-aldri-stoppet-flere-svindelforsok-
- https://www.dnb.no/dnbnyheter/no/samfunn/ser-brutal-svindeltrend---eldre-kvinner-utsatt
- https://www.dnb.no/dnbnyheter/no/samfunn/derfor-er-eldre-ekstra-utsatt-for-svindel
- https://www.dnb.no/dnbnyheter/no/samfunn/da-ma-du-betale-inntil-12-000-kroner-i-egenandel
- https://www.nordea.com/no/nyheter/eldre-personer-utsatt-for-malrettet-svindel
- https://www.tu.no/nyhetsstudio/politiet-advarer-svindlere-utgir-seg-for-aa-vaere-politi-eller-bank/126628
- https://www.bytt.no/bank/sparekonto/a-onay/banksvindel-du-ma-sjelden-dekke-mer-enn-450-kroner-selv
- https://www.bytt.no/artikler/forbruker/a-nnjy/tomt-konto-og-avvist-av-banken-slik-slar-du-tilbake-mot-urimelige-svindelkrav
- https://finologee.com/psr-what-the-provisional-political-agreement-signals-for-psps/
- https://www.williamfry.com/knowledge/psd3-psr-final-compromise-texts-are-published/
- https://www.eba.europa.eu/publications-and-media/press-releases/joint-eba-ecb-report-payment-fraud-strong-authentication-remains-effective-fraudsters-are-adapting
- https://polisen.se/aktuellt/nyheter/nationell/2026/januari/brottsvinsterna-for-telefonbedragerier-fortsatter-att-minska/

Telcos and competitors
- https://kommunikasjon.ntb.no/pressemelding/18774365/telia-norges-svindelrapport-2025-stoppet-nesten-100-millioner-svindelforsok-ser-okning-i-malrettede-angrep
- https://www.tek.no/nyheter/nyhet/i/jQgEl0/nytt-digitalt-skjold-mot-mobilsvindel-fungerer
- https://www.securityworldmarket.com/no/Nyheter/Bedriftsnyheter/stor-svindelanropsbolge-mot-norge
- https://bytt.no/artikler/forbruker/a-wnlx/hvilke-mobiloperatorer-har-svindelfilter-inkludert-i-2026
- https://support.google.com/phoneapp/answer/15654065?hl=en
- https://macrumors.com/guide/ios-26-phone-app
- https://www.businesswire.com/news/home/20240528009351/en/Introducing-The-World%E2%80%99s-First-AI-Call-Scanner-by-Truecaller-The-Fastest-Most-Accurate-AI-Voice-Scam-Detection-System
- https://businesswire.com/news/home/20250128511230/en/Hiya-Launches-First-AI-Call-Assistant-That-Stops-Live-and-Deepfake-Scams-in-Real-Time
- https://monzo.com/help/monzo-fraud-category/monzo-call-status
- https://shop.rnib.org.uk/products/truecall-vi-stop-nuisance-calls
- https://www.easternprogress.com/sentien-unveils-game-changing-ai-platform-seniorsafe---stops-both-human-and-ai-deepfake/article_43448250-8be0-5b5c-a3ef-a0ac9cd77286.html
- https://betakit.com/?p=408872
- https://realitydefender.com/insights/real-time-deepfake-detection-in-call-centers
- https://m.dnb.no/en/about-us

Sizing, pricing and regulation
- https://www.ssb.no/befolkning/folketall/statistikk/befolkning
- https://www.ssb.no/befolkning/artikler-og-publikasjoner/1-av-3-eldre-bor-alene
- https://www.ssb.no/sosiale-forhold-og-kriminalitet/levekar/artikler/1-av-4-aleneboende-eldre-har-ingen-familie-i-naerheten
- https://nkom.no/statistikk/statiske-rapporter-og-analyser/_/attachment/download/cba9f5c7-a034-42a4-bb66-ff87a5b5685d:8712419ee263a709792d3ee7fbb32300c83fa5d3/Ekommarkedet%20i%20Norge%202022.pdf
- https://ec.europa.eu/eurostat/statistics-explained/index.php/Population_structure_and_ageing
- https://www.activepieces.com/blog/deepgram-pricing-2026-per-minute-rates-and-add-ons
- https://lorenskog.kommune.no/_f/p71/i491728c8-a54a-4ba6-833d-5f01bd7e6349/kommunale-gebyrer-og-brukerbetalinger-helse-og-omsorg-2025.pdf
- https://www.baerum.kommune.no/globalassets/tjenester/helse-og-omsorg/pris/prisliste-2025-helse--og-omsorgstjenester.pdf
- https://www.porsgrunn.kommune.no/media/5203/soeknad-om-trygghetsalarm.pdf
- https://www.recordinglaw.com/no/world-laws/world-recording-laws/norway-recording-laws/
- https://deepgram.com/learn/deepgram-eu-endpoint-now-generally-available
- https://platform.claude.com/docs/de/manage-claude/data-residency
- https://www.tu.no/nyhetsstudio/nye-eu-regler-for-ki-norge-og-eoes-henger-ennaa-langt-etter/127027

Nordic 65+ population inputs (Sweden, Denmark, Finland) and further secondary sources are listed in `docs/research/sizing-business-regulation.md`, `docs/research/fraud-landscape.md` and `docs/research/competitors.md`.
