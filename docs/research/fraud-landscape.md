# Fraud landscape: phone/social-engineering fraud (Norway first)

Research for "Scam Call Guardian" (ATEC x DNB hackathon). Compiled 2026-11-05 in a ~10 min desk sweep.
FX used for brackets, approximate 2025 averages: EUR 1 ≈ NOK 11.7, USD 1 ≈ NOK 10.4. All conversions are mine, rounded.
"Unverified" = I only found it in secondary or aggregator sources, or could not open the primary source. DNB's own site (dnb.no/dnbnyheter) blocked automated fetches, so DNB figures come from search snippets of DNB pages and are marked where relevant.

## 1. Norway: losses

### 1.1 Bank-reported fraud on payments (Finanstilsynet, gross losses, NOK million)

Covers account transfers and card payments reported by banks. Gross: includes losses borne by customer, bank or merchant. It does not split by channel (phone vs SMS vs web).

| Year | Total loss | Social manipulation (payer tricked into paying) | Account transfers | Cards | Fraud prevented by banks |
|---|---|---|---|---|---|
| 2022 | ~610 (≈ €52M) *unverified, secondary* | n/a | 395 | n/a | n/a |
| 2023 | 928.7 (≈ €79M / $89M) | 468 | 647.9 | 280.8 | 2,072 |
| 2024 | 1,227.4 (≈ €105M / $118M), +32% y/y | 667 (≈54% of total) | 790.1 | 437.3 | 2,964 |
| 2025 | 962 (≈ €82M / $92M), −265 y/y | 514 (H1 204 + H2 310, ≈53%) | 622 (238 + 384) | 340 (166 + 174) | 3,537 (1,517 + 2,020) |

Sources: 2023/2024 from [Finanstilsynet H2 2024](https://www.finanstilsynet.no/publikasjoner-og-analyser/svindel-og-svindelstatistikk/2024/h2/svindelstatistikk-andre-halvar-2024/) (full-year figures summed from half-years). 2025 from [Finanstilsynet H2 2025](https://www.finanstilsynet.no/publikasjoner-og-analyser/svindel-og-svindelstatistikk/2025/h2/svindelstatistikk-andre-halvar-2025/). 2022 from [digi.no](https://digi.no/nyhetsstudio/43019) (secondary).

Key reading of the H2 2025 numbers ([Finanstilsynet, 2026](https://www.finanstilsynet.no/publikasjoner-og-analyser/svindel-og-svindelstatistikk/2025/h2/svindelstatistikk-andre-halvar-2025/)):
- Losses turned up again in H2 2025: 558 MNOK vs 404 in H1 2025.
- Social manipulation was 310 MNOK in H2 2025, ≈52% of all fraud. Within account transfers, payer-manipulated losses were 282 MNOK, up ≈68% half-on-half. Fraudster-initiated (unauthorised-type) transfers were only 101 MNOK.
- So the growth is in *authorised* push-payment fraud, where the victim is talked into paying. That is the scenario a live-call warning targets.

### 1.2 Wider estimates
| Figure | Value | Source |
|---|---|---|
| Fraud proceeds from police-reported fraud, 2024 | NOK 2.13 bn (≈ €182M / $205M), 27,904 completed fraud offences reported, +18% y/y | Økokrim-based figures via [Finans Norge / Økokrim presentation](https://www.finansnorge.no/contentassets/67158a92e52744de98fe4b7ad9da96f8/merete-klingenberg-og-pal-lonseth-okokrim.pdf.pdf) and [Politiet næringslivsnyhetsbrev, May 2026](https://www.politiet.no/globalassets/lokale-blokker/nordland/nyhetsbrev-naringslivskontakten-mai-2026.pdf). Numbers seen in search snippets, PDF not opened: *unverified* |
| Finans Norge's figure for fraud losses 2024 | "om lag 1.2 mrd. kroner" | [Finans Norge to Stortinget, Apr 2026](https://www.finansnorge.no/contentassets/07a8760849a64401a348b6eeecc4ca7a/innspill-til-finanskomiteen-om-prop.-39-l-2025-2026.pdf) |
| Økokrim on dark figures | Calculations "baserer seg kun på anmeldte bedragerier ... grunn til å tro at mørketallene er store" (based only on reported frauds; there is reason to believe the dark figures are large) | [Økokrim-based analysis](https://www.finansnorge.no/contentassets/67158a92e52744de98fe4b7ad9da96f8/merete-klingenberg-og-pal-lonseth-okokrim.pdf.pdf) (2025) |

### 1.3 Split by scam type (Norway)
No official Norwegian split by channel (phone/vishing, safe account, BankID code phishing, romance, "hi mum") was found. Finanstilsynet only splits by "manipulated vs fraudster-initiated" and by payment type. What exists:

| Type | Data point | Source |
|---|---|---|
| Investment fraud (DNB customers, 2024) | 967 customers targeted, 166 MNOK attempted (≈ €14M). 37% of DNB fraud victims were hit by investment fraud. Average attack ≈ 170,000 NOK (≈ €14.5k) | DNB "Finansiell trygghet – hvem kan du stole på?" (Feb 2025), via search snippet of [dnb.no](https://www.dnb.no/dnbnyheter/no/samfunn/ser-brutal-svindeltrend---eldre-kvinner-utsatt): *unverified* (page blocked) |
| Investment fraud trend 2025 | DNB saw a "markant økning" (marked increase) in investment fraud across all ages and expects investment fraud and AI misuse to rise further in 2026 | [DNB, 14 Jan 2026](https://www.dnb.no/dnbnyheter/no/samfunn/dnb-har-aldri-stoppet-flere-svindelforsok-) (snippet) |
| "Safe account" / home-visit ("Olga") fraud | DNB: ~20 MNOK attempted in such cases in one year, ~10 MNOK stopped. Case: elderly Asker woman taken to Dubai for ~2 weeks, several MNOK lost | [DNB](https://www.dnb.no/dnbnyheter/no/samfunn/slik-ble-eldre-dame-lurt-med-til-dubai-og-svindlet) (year unclear: *unverified*) |
| Fake police / bank calls | Oslo police warn that scammers call elderly people posing as police or bank, and that women are hit especially hard. No NOK total published | [TU/NTB, 2026](https://www.tu.no/nyhetsstudio/politiet-advarer-svindlere-utgir-seg-for-aa-vaere-politi-eller-bank/126628). [Politidirektoratet warning, Jul 2025](https://www.politiet.no/nyheter-og-presse/politidirektoratet/nyhet/2025-07-17/advarsel-om-svindelforsok) |
| Phone + SMS pre-contact + home pickup | Nordea: callers spoof police/bank numbers, keep victims on the line "for many hours", sometimes send couriers to collect cards/phones. Nordea's fraud cases "doubled in one year" | [Nordea, Apr 2024](https://www.nordea.com/no/nyheter/eldre-personer-utsatt-for-malrettet-svindel) |
| "Hei mamma/pappa" | Described and warned about by Finans Norge and DNB. No NOK totals found | [Finans Norge svindelbrosjyre 2025](https://finansnorge.no/siteassets/dokumenter/publikasjoner/svindelbrosjyre-2025---norsk-versjon.pdf), [DNB](https://www.dnb.no/dnbnyheter/no/samfunn/hei-mamma-hei-pappa-svindel) |
| Romance fraud | DNB campaign case ("Cecilie"). No NOK totals found | [DNB](https://www.dnb.no/dnbnyheter/no/samfunn/det-er-tinder-swindlere-overalt---mange-blir-lurt) |
| Organised phone fraud vs elderly | 5 Danes charged in a Norwegian elder-phone-fraud case, some linked to Loyal To Familia (per NRK) | [sn.dk](https://www.sn.dk/art6474478/danmark/112/fem-danskere-tiltalt-i-norsk-sag-om-aeldresvindel) (2025/26) |

## 2. Who gets hit
- **Elderly women are the named high-risk group.** DNB statistics: women aged ~50–80 are most exposed to investment/romance-type fraud ([DNB, 2022](https://www.dnb.no/dnbnyheter/no/samfunn/ser-brutal-svindeltrend---eldre-kvinner-utsatt), snippet). DNB also reported a rise in "safe account" fraud aimed specifically at elderly women. In 2026 the Oslo police said the same about fake police/bank calls ([TU/NTB](https://www.tu.no/nyhetsstudio/politiet-advarer-svindlere-utgir-seg-for-aa-vaere-politi-eller-bank/126628)).
- **Why elderly:** DNB (Mar 2026) says scammers target older people because they traditionally have high trust in banks and authorities ([DNB](https://www.dnb.no/dnbnyheter/no/samfunn/derfor-er-eldre-ekstra-utsatt-for-svindel), snippet).
- **DNB mass warning:** DNB sent an SMS to ~800,000 customers about higher fraud risk aimed at adults and the elderly, and asked relatives to help ([Ground News summary of Aftenposten](https://ground.news/article/dnb-warns-800-000-customers-of-increased-risk-of-fraud), date unknown: *unverified*).
- **Average loss per victim:** DNB investment-fraud average attack ≈ 170,000 NOK (2024, *unverified*, see 1.3). No official Norwegian average per victim across all fraud found.
- **Share of losses borne by elderly (Norway):** not published. *Gap.*
- **Underreporting:** Økokrim says dark figures are large (above). The police citizen survey [Innbyggerundersøkelsen 2025](https://www.politiet.no/globalassets/tall-og-fakta/innbyggerundersokelsen/2025/innbyggerundersokelsen-2025.pdf) (22,817 respondents) covers fraud reporting, but I could not extract the fraud reporting rate. *Unverified.* OsloMet's [Nasjonal trygghetsundersøkelse](https://www.oslomet.no/forskning/forskningsnyheter/morketall-kriminalitetsstatistikk-norge) (2023) finds "store mørketall" (large dark figures) in the crime statistics generally.

## 3. Trends 2024–2026

### 3.1 DNB-specific
| Metric | 2022 | 2024 | 2025 | Source |
|---|---|---|---|---|
| Fraud attempted against DNB + customers | ~1.2 bn NOK | 2.5 bn NOK | 3.3 bn NOK (≈ €282M / $317M), +30% / +~800 MNOK | [DNB 14 Jan 2026](https://www.dnb.no/dnbnyheter/no/samfunn/dnb-har-aldri-stoppet-flere-svindelforsok-), [DNB 2023](https://www.dnb.no/dnbnyheter/no/samfunn/slik-bekjemper-dnb-okonomisk-kriminalitet) (snippets) |
| Stopped by DNB | ~1.0 bn | 2.1 bn (~84%) | ~3.0 bn (≈ €256M), ~90% | same |
| Implied not stopped | ~0.2 bn | ~0.4 bn | ~0.3 bn (my calculation, not a DNB-stated loss figure) | derived |
| Fraud cases handled | n/a | n/a | 9,970 | DNB 2025 report (snippet) |
| Suspicious-activity reports to Økokrim | n/a | n/a | 4,666 | DNB 2025 report (snippet) |
| Staff on financial crime | ~600 daily, ~9,000 trained (Feb 2023) | n/a | n/a | [DNB, Feb 2023](https://www.dnb.no/dnbnyheter/no/samfunn/slik-bekjemper-dnb-okonomisk-kriminalitet) (dated) |
- DNB's 2026 report "Finansiell trygghet: Når virkeligheten utfordres" (14 Jan 2026) highlights growing use of AI and deepfakes by fraudsters ([DNB](https://www.dnb.no/dnbnyheter/no/samfunn/ki-svindel-og-bedrageriforsok-skyter-i-vaeret-), snippet).
- DNB has published no refund totals for customers. *Gap.*
- DNB campaigns: joint DNB + police + Telenor warnings on elder fraud ([DNB](https://www.dnb.no/dnbnyheter/no/samfunn/slik-blir-stadig-flere-eldre-svindlet)). "Politiet vil aldri spørre om BankID" ([DNB](https://www.dnb.no/dnbnyheter/no/samfunn/politiet-vil-aldri-sporre-om-bankid)). "Hei mamma" warnings.

### 3.2 Spoofing, AI voice, impersonation
- **Spoofing fell sharply after operator data sharing (Dec 2024).** Telia Norge saw 44M spoofing calls in 2025 vs >90M in 2024 ([Telia/NTB, 16 Jan 2026](https://kommunikasjon.ntb.no/pressemelding/18774365/telia-norges-svindelrapport-2025-stoppet-nesten-100-millioner-svindelforsok-ser-okning-i-malrettede-angrep)). Scammers then moved to spoofed Swedish and Danish numbers ([tek.no](https://www.tek.no/nyheter/nyhet/i/jQgEl0/nytt-digitalt-skjold-mot-mobilsvindel-fungerer)).
- **Shift to targeted attacks:** Telia describes personalised SMS that use the victim's name, victims picked by age and location, and pre-written scripts. It also says AI improves the scammers' language and imitation of tone ([Telia, Jan 2026](https://kommunikasjon.ntb.no/pressemelding/18774365/telia-norges-svindelrapport-2025-stoppet-nesten-100-millioner-svindelforsok-ser-okning-i-malrettede-angrep)).
- **AI voice cloning (Norway):** no Norwegian incidence statistics found. *Gap.* Nordic experts report cases where AI was used to make the caller's voice sound like a relative ([securityworldmarket DK](https://www.securityworldmarket.com/dk/Nyheder/Kommentarer/kunstig-intelligens-oger-risikoen-for-svindelopkald-massivt)).
- **AI voice cloning (international):**
  - Starling Bank (UK): 28% of UK adults say they were targeted by an AI voice-clone scam in the past year, and 46% had never heard of them. Survey of 3,000+ adults, probably 2024 ([The Nightly/CNN](https://thenightly.com.au/business/wealth/this-bank-says-millions-of-people-could-be-targeted-by-ai-voice-cloning-scams-c-16107398)). *Date unverified.*
  - McAfee (2023, 7 countries): 25% have experienced an AI voice scam, and 77% of victims lost money ([BusinessWire](https://www.businesswire.com/news/home/20230501005587/en)).
  - Gallup / Stop Scams Alliance (US, 2026 survey): 12% of successful 2025 scams involved AI or deepfakes, likely an undercount ([letsdatascience](https://letsdatascience.com/news/survey-finds-ai-used-in-12-of-2025-scams-96535686)). *Secondary.*
- **Police impersonation abroad (for context):** Germany 2025 fake-police losses were €49.5M, up from €30.1M ([Nordkurier/BKA](https://www.nordkurier.de/panorama/schaden-durch-falsche-polizisten-stark-gestiegen-4767812)).

## 4. Liability: who pays

### 4.1 Norway today (finansavtaleloven 2020)
| Situation | Customer pays | Source |
|---|---|---|
| Unauthorised transaction, ordinary negligence | max 450 NOK (≈ €38) | finansavtaleloven § 4-30, via [bytt.no](https://www.bytt.no/bank/sparekonto/a-onay/banksvindel-du-ma-sjelden-dekke-mer-enn-450-kroner-selv) |
| Unauthorised, gross negligence (grov uaktsomhet) | max 12,000 NOK (≈ €1,025) | same. [DNB](https://www.dnb.no/dnbnyheter/no/samfunn/da-ma-du-betale-inntil-12-000-kroner-i-egenandel) |
| Intent, or knowingly breaching obligations | full loss | same |
| **Authorised** payment (victim tricked into paying, e.g. investment, romance, many vishing cases) | normally **full loss**: the payment counts as authorised, so the § 4-30 caps don't apply | general rule. Finanstilsynet's "social manipulation" bucket is ≈53% of losses (2025) |
- Finansklagenemnda (FinKN) practice: giving away BankID codes over the phone or installing remote-access software (AnyDesk) at a scammer's request is normally gross negligence, so the customer pays up to 12,000 NOK ([bytt.no](https://www.bytt.no/artikler/forbruker/a-wnnw/svindlet-med-bankid-her-gar-grensen-for-grov-uaktsomhet), e.g. [FinKN 2022-198](https://publisering.finkn.no/api/statements/openPDF/2022-198)). The bank carries the burden of proof ([FinKN 2023-717](https://publisering.finkn.no/api/statements/openPDF/2023-717)).
- In "safe account" cases FinKN has several times found the transactions were *not validly authorised*, which puts the loss on the bank ([bytt.no](https://www.bytt.no/artikler/forbruker/a-nnjy/tomt-konto-og-avvist-av-banken-slik-slar-du-tilbake-mot-urimelige-svindelkrav)).
- FinKN fraud complaints: 834 in 2025, up from 612 in 2024. Consumers won fully or partly in 35.5% ([bytt.no](https://www.bytt.no/artikler/forbruker/a-nnjy/tomt-konto-og-avvist-av-banken-slik-slar-du-tilbake-mot-urimelige-svindelkrav)). *Secondary, unverified against FinKN's own statistics.*
- Policy: Stortinget passed changes to finansforetaksloven on 8 June 2026 (Prop. 39 L 2025–2026), including confidentiality rules for anti-fraud information sharing between banks ([regjeringen.no](https://www.regjeringen.no/no/dep/fin/navigasjonssider/snarvei-nyheter/id2008081/), [høring](https://www.regjeringen.no/no/dokumenter/horing-finansforetaks-informasjonsdeling-for-a-bekjempe-okonomisk-kriminalitet/id3118997/)). I found no Norwegian proposal to shift APP/impersonation losses to banks. *Unverified whether one exists.*

### 4.2 EU PSR / PSD3 (EEA-relevant: Norway will incorporate via the EEA Agreement)
- **Bank-impersonation refund:** a consumer tricked by someone posing as *their own* PSP or its staff must be refunded in full if they report to police and notify the PSP. Exceptions are fraud or gross negligence by the consumer. Scope excludes impersonation of police or authorities, investment fraud and romance fraud ([Finologee](https://finologee.com/psr-what-the-provisional-political-agreement-signals-for-psps/), [Taylor Wessing, Nov 2025](https://www.taylorwessing.com/zh-hant/insights-and-events/insights/2025/11/eu-lawmakers-strike-a-deal-on-payments-reforms)).
- Process: 15 business days to investigate a suspended refund. A refusal must be written and point to ADR. A receiving PSP that ignored obvious fraud signals can be made to bear the loss (Finologee, above).
- **Status:**
  - Provisional political agreement in Nov 2025.
  - Final compromise texts on 23 Apr 2026 ([William Fry](https://www.williamfry.com/knowledge/psd3-psr-final-compromise-texts-are-published/)).
  - ECON approval on 5 May 2026 ([Sopra Steria](https://www.soprasteria.com/insights/details/payment-services-regulation-psr-in-2026--the-state-of-play-for-banks)).
  - Council first-reading position item approved by Coreper on 28 Oct 2026 ([Council doc ST-14536-2026](https://data.consilium.europa.eu/doc/document/ST-14536-2026-INIT/en/pdf)).
  - **Publication in the Official Journal is not confirmed as of early Nov 2026** (*unverified*). Rules apply 21 months after entry into force, so realistically 2028 in the EU, and later in Norway after EEA incorporation.
- **Implication for the pitch:** even after the PSR, police-impersonation, investment and romance scams stay with the victim. Prevention during the call is the only protection for those.
- EU context: EEA payment fraud was €4.2bn in 2024, up from €3.5bn (+17%). Credit-transfer fraud was €2.5bn. Payer manipulation was more than half of credit-transfer fraud value, and **users bore ~85% of credit-transfer fraud losses** ([EBA-ECB joint report, 15 Dec 2025](https://www.eba.europa.eu/publications-and-media/press-releases/joint-eba-ecb-report-payment-fraud-strong-authentication-remains-effective-fraudsters-are-adapting)).

## 5. Telco measures (Norway)
| Measure | Detail | Source |
|---|---|---|
| Nkom anti-spoofing push | Nkom asked Telenor, Telia and Ice to cooperate. A shared filter for spoofed Norwegian numbers started Nov/Dec 2024 | [digi.no](https://digi.no/nyhetsstudio/telia-stoppet-30-millioner-svindelsamtaler-i-foerste-kvartal/75955), [Nkom spoofing measures PDF](https://nkom.no/aktuelt/digital-svindel-ber-mobilselskapene-ta-ytterligere-grep/_/attachment/download/891704ef-40ba-4663-9a9e-5b92ee07f661:f5f09a9cb31779ef8633857021952d7a1b075b15/Tiltak%20for%20motvirking%20av%20spoofing.PDF) |
| Telia Norge 2025 | 95.1M scam calls blocked (slightly down from 2024). Spoofing 44M (2024: >90M). Q4 2025: 11.5M blocked. Wangiri/robocalls (UK investment-scam numbers) up strongly. "Svindelsperre" service launched Mar 2025 | [Telia/NTB, 16 Jan 2026](https://kommunikasjon.ntb.no/pressemelding/18774365/telia-norges-svindelrapport-2025-stoppet-nesten-100-millioner-svindelforsok-ser-okning-i-malrettede-angrep) |
| Telia filter on invalid foreign numbers | Blocks calls whose number has the wrong length or is not assigned to an operator | [tek.no](https://www.tek.no/nyheter/nyhet/i/3M9GrL/telia-filter-stopper-svindel-anrop-fra-utlandet) |
| Telenor 2024 | 54.5M scam calls and 64.1M scam SMS blocked | search snippet, [securityworldmarket](https://www.securityworldmarket.com/no/Nyheter/Bedriftsnyheter/telenor-stoppet-22-milliarder-hendelser1): *unverified* |
| Telenor caller-ID protection | Flags foreign calls that show a Norwegian number and hides the number (Telenor customers only) | [tek.no](https://www.tek.no/nyheter/nyhet/i/qAmm70/telenors-nye-sikkerhetssystem-skal-redusere-telefonsvindel) |
| Telenor police-fraud spike (2023) | 1.1M unwanted calls caught in one week vs ~400k in a normal week | [tek.no, 15 Nov 2023](https://www.tek.no/nyheter/nyhet/i/abR2m2/telenor-registrerer-enorm-oekning-i-politisvindel) |
- Limit: network filters stop spoofed and bulk calls. They cannot judge the *content* of a call from a valid number, or from a scammer the victim already trusts (call-back, WhatsApp, a "bank employee" on a real mobile number). That is the gap Scam Call Guardian addresses.

## 6. Nordics / EU / global context
| Region | Figure | Source |
|---|---|---|
| Sweden | Criminal fraud proceeds 5.7 bn SEK in 2025. Phone-fraud proceeds fell from 712 MSEK (2023) to ~302 MSEK (2025). Elder fraud "still a major problem" | [Polisen, Jan 2026](https://polisen.se/aktuellt/nyheter/nationell/2026/januari/brottsvinsterna-for-telefonbedragerier-fortsatter-att-minska/) |
| Nordics | ~260,000 digital frauds reported in 2023 across the Nordics, large dark figures | [digi.no](https://digi.no/nyhetsstudio/56183) |
| EEA | €4.2bn payment fraud 2024 (+17%). Users bore ~85% of credit-transfer losses | [EBA/ECB, Dec 2025](https://www.eba.europa.eu/publications-and-media/press-releases/joint-eba-ecb-report-payment-fraud-strong-authentication-remains-effective-fraudsters-are-adapting) |
| UK | 2025: APP losses £576.4M (+19%), 248,070 cases. Personal APP losses £500.8M. Total fraud £1.28bn. Investment scams £222M (+40%). Impersonation losses −12%. Banks reimbursed 61% under the mandatory reimbursement regime | UK Finance Annual Fraud Report 2026, via [inkl](https://www.inkl.com/news/ps576-4m-lost-to-authorised-push-payment-scams-in-2025-as-ai-manipulates-victims), [Financial Planning Today](https://www.financialplanningtoday.co.uk/news/crooks-steal-£1-3bn-through-payment-fraud) (secondary) |
| US | 2024: people 60+ filed 147,127 IC3 complaints with $4.885bn lost (+43% losses, +46% complaints vs 2023). The most affected age group | FBI IC3 2024, via [AARP](https://www.aarp.org/money/scams-fraud/fbi-report-fraud-2024.html) |
| Global | GASA/Feedzai 2025 (46,000 adults, 42 countries): ~$442bn lost in 12 months. 57% met a scam, 23% lost money | [GASA](https://gasa.org/knowledge-base/reports/global-state-of-scams-2025), [Feedzai](https://www.feedzai.com/blog/gasa-global-state-of-scams-report/) (survey estimate) |

## 7. Gaps / not verified
- No official Norwegian loss split for vishing vs SMS vs BankID phishing vs romance vs "hi mum".
- No Norwegian loss data by age. The elderly share is qualitative only (DNB, police).
- DNB refund totals and current anti-fraud headcount (600 is a 2023 figure).
- Kripos / Politiets trusselvurdering 2025–26 figures on phone fraud: not found in this sweep.
- Fraud reporting rate in Innbyggerundersøkelsen 2025: PDF not parsed.
- PSR Official Journal publication date.
- Bits AS, NSM, Forbrukerrådet and SSB: not covered in this sweep.
