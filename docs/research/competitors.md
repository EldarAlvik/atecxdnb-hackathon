# Competitive landscape: Scam Call Guardian

Research date: 2026-11-05. Time-boxed (~10 min) desk research using web search and vendor pages. Claims marked **(unverified)** could not be confirmed from a primary source in the time available. Vendor performance numbers are self-reported.

**The user we are designing for:** an 80-year-old in Norway who takes calls on a home phone or a basic mobile, speaks Norwegian, does not install apps, and is the classic target of "trygg konto" (safe account) and fake-police/fake-bank calls.

---

## 1. Key findings

1. **No mass-market product does live, in-call scam detection in Norwegian.** Google's Gemini Nano Scam Detection for calls is the closest analogue. It runs only on Pixel 9+ (and Galaxy S26 in some markets), and it covers 12 countries plus the US. Norway is not one of them ([Google support](https://support.google.com/phoneapp/answer/15654065?hl=en)).
2. **Norwegian telcos block at the network level before the call connects.** They do nothing during the conversation. Telenor (Nummervarsel, powered by Hiya), Telia (Svindelsperre) and Ice all filter numbers. Spoofing of Norwegian numbers has largely stopped, but single-use foreign numbers still get through ([bytt.no](https://bytt.no/artikler/forbruker/a-wnlx/hvilke-mobiloperatorer-har-svindelfilter-inkludert-i-2026), [securityworldmarket / Telenor](https://www.securityworldmarket.com/no/Nyheter/Bedriftsnyheter/stor-svindelanropsbolge-mot-norge)).
3. **Banks verify "is it really us calling?"** but do not judge calls from police, family or "Microsoft". Monzo, CommBank, Revolut and Nationwide have this, and so does Google's Android "verified financial calls". I found no evidence of a DNB "ringer banken deg nå" in-app call status. **This is an open gap and an opportunity for DNB.**
4. **Elderly call-blocker hardware such as trueCall is whitelist-based and contains no AI.** It is mature, cheap and UK-centric, and councils buy it for vulnerable adults.
5. **AI startups aimed at seniors appeared in 2026.** They include SeniorSafe, Scammer Guardian (a landline device planned for late 2026), OnGuardAI and ScamBlocker. All are English-only and US/UK/Canada-focused.

---

## 2. On-device phone features

### Google Pixel / Android Scam Detection (calls)
- **What it does:** On-device models listen to calls from unknown numbers in real time and warn the user with an audio cue, vibration and an on-screen "likely scam" banner. Pixel 9+ uses Gemini Nano. Audio never leaves the device. The feature is opt-in and off by default ([Engadget](https://www.engadget.com/mobile/smartphones/googles-live-scam-detection-for-phone-calls-is-now-out-for-pixel-devices-143017096.html), [TechRepublic](https://www.techrepublic.com/article/news-google-gemini-scam-detection-android-samsung-expansion/)).
- **Countries:** Pixel 6+ is US only. Pixel 9+ adds Australia, Canada, France, Germany, India, Ireland, Italy, Japan, Mexico, Singapore, Spain and the UK. You need a SIM from a listed country. **Norway is not listed** ([Google support](https://support.google.com/phoneapp/answer/15654065?hl=en)).
- **Languages:** Not listed officially. It launched English-only, and Japanese localization was reported in progress ([jetstream.blog](https://jetstream.blog/?p=212054)). There is **no evidence of Norwegian**.
- **Price:** Free on supported Pixels.
- **Related Android features:** "Verified financial calls" asks the bank's app whether a bank call is genuine and ends spoofed calls automatically. It needs Android 11+ and a participating bank app, and the list of participating banks is not published ([Google support](https://support.google.com/android/answer/17080867)). Scam Detection in Google Messages covers 20+ countries. In 2026 it was extended to Gboard ([Android Authority](https://androidauthority.com/pixel-scam-detection-google-messages-global-3711361)).
- **Gaps for our user:** It requires a recent Pixel or Galaxy flagship. It does not work on landlines, in Norway or in Norwegian. It protects only the handset it runs on. There is no family loop.

### Samsung Galaxy
- **What it does:** Galaxy S26 ships Google's Scam Detection for calls on-device. It launched in the US in English in Feb 2026, and wider rollout followed with One UI 9 (Sept 2026) ([TechRepublic](https://www.techrepublic.com/article/news-google-gemini-scam-detection-android-samsung-expansion/), [Sammyfans](https://www.sammyfans.com/2026/09/29/samsung-expands-on-device-ai-scam-detection-with-one-ui-9-stable-update/amp/)). Leaked firmware lists US, UK, KR, AU, BR, IN, CA, DE, ES, FR, IT and JP for the foldables, with no Nordics ([WebProNews](https://www.webpronews.com/samsungs-next-foldables-will-screen-your-calls-for-scams-but-only-if-you-live-in-the-right-country/)) **(unverified)**.
- **Gaps for our user:** Same as Pixel. It needs a flagship phone, and Norway and Norwegian are not supported.

### Apple iOS 26: Call Screening, Live Voicemail, Silence Unknown Callers
- **What it does:** Call Screening answers unknown numbers, asks the caller for their name and reason, and shows a transcript before you pick up. Live Voicemail transcribes voicemail as it is being left (since iOS 17). **Neither analyses the conversation for scam content once you answer** ([MacRumors](https://macrumors.com/guide/ios-26-phone-app)).
- **Languages/regions:** Availability depends on the language and region pairing (English, German, Spanish, Portuguese, Japanese, Korean, Chinese and Italian beta are reported). I found **no evidence of Norwegian** ([pasqualepillitteri.it](https://pasqualepillitteri.it/en/news/16737/iphone-call-screening-unknown-callers), [Apple Community](https://communities.apple.com/de/thread/256136093)).
- **Gaps for our user:** Screening happens before the call is answered. A scammer who states a plausible name ("Politiet", "DNB") gets through, and after that there is no protection.

---

## 3. Consumer apps

| App | What it does | Real-time in call? | Price | Platform | Norwegian? |
|---|---|---|---|---|---|
| **Truecaller** (AI Call Scanner) | Caller ID and spam DB. Call Scanner: tap during a call, it records a few seconds and says "AI voice" or "human". It detects voice clones, not scam *content* | Partial (manual trigger, voice-clone only) | Premium subscription. Launch was US/Android only ([BusinessWire](https://www.businesswire.com/news/home/20240528009351/en/Introducing-The-World%E2%80%99s-First-AI-Call-Scanner-by-Truecaller-The-Fastest-Most-Accurate-AI-Voice-Scam-Detection-System)) | Android (iOS was planned) | Caller ID yes. Scanner: no evidence |
| **Hiya** (AI Phone) | Caller ID and spam. AI Phone screens unknown callers with an AI assistant, plus real-time deepfake-voice alerts ([BusinessWire](https://businesswire.com/news/home/20250128511230/en/Hiya-Launches-First-AI-Call-Assistant-That-Stops-Live-and-Deepfake-Scams-in-Real-Time)) | Yes for deepfake voice. Screening happens pre-answer | Free app. AI Phone premium $9.99/mo | iOS/Android | Hiya powers Telenor Nummervarsel in Norway. AI Phone language support for Norwegian is unknown |
| **Norton Genie / Norton 360** | Scam checks for SMS, email, web and screenshots. Deepfake Protection covers English YouTube/Facebook video on Windows AI PCs ([Barchart/Norton PR](https://www.barchart.com/story/news/35863879/norton-expands-ai-powered-scam-protection-features-globally)) | No (no live-call audio) | Bundled. Norton 360 Standard is $39.99 first year, then $94.99 ([Norton](https://us.norton.com/feature/ai-scam-protection)) | Win/Mac/iOS/Android | No evidence for call features |
| **McAfee Scam Detector** | Text, email and video scam detection, with deepfake *video* detection ([McAfee PR](https://www.mcafee.com/en-us/newsroom/press-releases/2025/mcafee-launches-ai-powered-scam-detector-to-stop-scams-before-they-strike.html)) | No | Included in McAfee+ / Total Protection plans | Multi | No evidence |
| **Bitdefender Scamio** | Chatbot: paste a link, text, screenshot or QR code and ask "is this a scam?" ([TechRadar](https://www.techradar.com/pro/mcafee-is-now-bundling-its-scam-detector-with-all-its-antivirus-plans-here-are-others-that-are-actually-totally-free)) | No (after the fact, user-initiated) | Free | Web, WhatsApp, Messenger, Discord | Language list not found |
| **Trend Micro ScamCheck** | Scam check plus spam/scam call blocking in some regions ([KSL test](https://www.ksl.com/article/51440941)) | Number blocking only | $1.99/mo or $19.99/yr | iOS/Android | Call blocking is region-dependent |
| **Aura** (Call Assistant) | An AI answers unknown callers, screens them and forwards legitimate calls ([Aura](https://www.aura.com/help/what-is-call-protection)) | Pre-answer screening | ~$3.99/line/mo, or in Family plans ([Aura](https://aura.com/learn/best-spam-call-blocker)) | iOS/Android, US | No (US only) |

**Common gap:** They all need a smartphone, an app install and some tech literacy. Most check numbers, or check content only *before* the call or *after* the fact. Only Hiya and Truecaller look at audio in the call, and both look for synthetic voice, not for a human scammer's script. None of them works on a landline, and none supports Norwegian conversation.

---

## 4. Telcos

### Norway
- **Common "digital shield" (Nov 2024):** Telenor, Telia and Ice, with Nkom and Økokrim, blocked over 80 million scam attempts in 2025. Telenor alone blocked 37.9M scam calls and 43.5M SMS in 2025 ([bytt.no](https://bytt.no/artikler/forbruker/a-wnlx/hvilke-mobiloperatorer-har-svindelfilter-inkludert-i-2026), [Finans Norge 2026 report](https://www.finansnorge.no/siteassets/dokumenter/publikasjoner/norske-bankers-bekjempelse-av-svindel---status-og-tiltak-for-2026.pdf)).
- **Telenor Nummervarsel:** A network-level warning powered by Hiya. It flags likely fraud, numbers with high call volume and calls from abroad, and now shows the caller's name. It is included in all Telenor subscriptions with no app needed ([BusinessWire](https://www.businesswire.com/news/home/20250207338388/en/telenor-norway-launches-nationwide-call-protection-service-to-safeguard-consumers-against-spam-and-fraud), [tek.no](https://www.tek.no/nyheter/nyhet/i/Pd4mo0/telenor-lanserer-ny-telefonfunksjon-som-viser-hvem-som-ringer)).
- **Telia Svindelsperre:** Blocks invalid and wangiri foreign numbers by checking prefixes against numbering plans. It was upgraded in March 2026, blocked about 100M calls in 2025 and 8.4M in Q1 2026 ([pulse.bot/Telecompaper](https://www.pulse.bot/telecom/news/telia-norway-upgrades-svindelsperre-scam-blocker-9a4aa922-01f2-46e4-a453-fb636d07ef72/), [tek.no](https://www.tek.no/nyheter/nyhet/i/XMj45o/telia-blokkerer-ugyldige-utenlandske-numre-som-foerste-i-verden)). It is rule-based, not AI.
- **Ice:** Described as having "AI detection" plus the SpamShield SMS filter ([bytt.no](https://bytt.no/artikler/forbruker/a-wnlx/hvilke-mobiloperatorer-har-svindelfilter-inkludert-i-2026)). Details **(unverified)**.
- **Where they fall short:** They all work on the *number*, before the call connects. Telenor says waves from single-use foreign numbers (for example Dutch numbers in Aug 2025) look like normal traffic and are hard to block ([securityworldmarket](https://www.securityworldmarket.com/no/Nyheter/Bedriftsnyheter/stor-svindelanropsbolge-mot-norge)). Once a scammer is talking, nothing in the network hears "move your money to a safe account". Banks themselves ask telcos for more anti-spoofing measures against calls from abroad (Finans Norge 2026 report, section 8).

### Abroad
- **O2 "Daisy" (UK):** An AI "granny" that answers scam calls and wastes scammers' time. It ran over 1,000 conversations of up to 40 to 50 minutes. It was a marketing stunt that won awards, and there was no announced scale-up ([Marketing Week](https://www.marketingweek.com/o2-ai-granny-scammers-public/), [TechRadar](https://www.techradar.com/computing/artificial-intelligence/daisy-the-ai-grandma-has-just-been-unleashed-on-scammers-here-are-the-satisfying-results)). It does not protect the person who is on the call.
- **BT / Vodafone (UK/EU):** Network spam filtering, BT Call Protect and similar services. They are number-based, the same model as the Norwegian telcos. *(Not researched in depth in this pass.)*
- **Westpac and Optus (AU):** A bank-telco verification system that confirms bank calls ([iTWire](https://itwire.com/business-it-news/security/westpac,-optus-launch-verification-system-to-fight-scams)).

---

## 5. Banks and payment ID

| Bank / scheme | Feature | Covers | Gap |
|---|---|---|---|
| **Monzo (UK)** | "Call status" in the app shows whether Monzo staff are on the phone with you right now ([Monzo](https://monzo.com/help/monzo-fraud-category/monzo-call-status)) | Bank impersonation only | Needs a smartphone and internet. Does not cover fake police or fake family |
| **CommBank (AU)** CallerCheck | The bank pushes a security message to the app during a call to prove it is them ([CommBank](https://www.commbank.com.au/support/security/callercheck.html)) | Bank impersonation | Same |
| **Revolut** (Jan 2026) | In-app banner warns when a call is not from Revolut ([Irish Times](https://www.irishtimes.com/business/2026/01/13/revolut-adds-new-in-app-security-features-to-combat-scam-calls)) | Bank impersonation | Same |
| **Nationwide (UK)** Call checker | Customer can check whether a call claiming to be Nationwide is genuine ([Which?](https://www.which.co.uk/news/article/new-nationwide-call-checker-tool-how-is-your-bank-fighting-fraud-aMLGG7M1ASS7)) | Bank impersonation | Same |
| **Nordea** | The Nordea ID/codes app identifies you to customer service (FI and NO). In Finland it can verify that an incoming call is really from Nordea ([Nordea FI](https://www.nordea.fi/en/private-banking/our-services/daily-banking/getting-started-with-nordea-codes-app.html)) | Bank impersonation | Norwegian rollout of incoming-call verification **(unverified)** |
| **DNB** | Customer centre identifies callers with BankID ("sikker samtale"). Warnings and advice: call 915 04 800 ([DNB](https://www.dnb.no/dnbnyheter/no/samfunn/kundesenteret-i-dnb-identifiserer-kundene-med-bankid)). **I found no in-app "is DNB calling you now" feature** | Inbound customer calls | No live protection for the customer on an outbound scam call |
| **SpareBank 1** | Warnings about "trygg konto" scams ([SpareBank 1 / Mynewsdesk](https://www.mynewsdesk.com/no/sparebank-1/pressreleases/svindlere-manipulerer-deg-til-aa-overfoere-penger-banken-advarer-3267998)) | Awareness | No in-call feature found |
| **Danske Bank** | No in-app call verification found. Spoofing warnings ([Danske](https://danskebank.dk/en/personal/help/log-on-user-id-password-and--code-card/new-security-solution-in-danske-mobile-banking)) | None found | |
| **BankID / Vipps** | The BankID app shows clearer context on what you are approving. "Sikker samtale" for calls the customer starts ([BankID FAQ](https://bankid.no/faq/sikker-samtale)). Vipps sends scam warnings to users ([digi.no](https://digi.no/nyhetsstudio/43012)) | Authentication | No BankID block during a call found. Victims of "trygg konto" scams authorise the transfers themselves |
| **Norwegian banks jointly** | Finans Norge's 2026 report calls manipulation fraud ("trygg konto", fake police with home visits, often aimed at elderly and lonely people) the top challenge for 2026. AI voice and deepfakes are a growing threat. Priorities: transaction monitoring, data sharing with telcos, anti-spoofing ([Finans Norge](https://www.finansnorge.no/siteassets/dokumenter/publikasjoner/norske-bankers-bekjempelse-av-svindel---status-og-tiltak-for-2026.pdf)) | | Bank measures act on the *transaction*, after manipulation has already happened |

**Takeaway:** Call-status features answer one question: is this caller really my bank? The fake-police "trygg konto" script defeats them, because the caller says they are the police, or says the bank itself is compromised. Transaction monitoring acts after the victim has already been persuaded. DNB has *neither* a call-status feature nor an in-call intervention. Scam Call Guardian could be pitched as part of a DNB call-safety offering.

---

## 6. Elder-specific hardware and services

- **trueCall (UK):** A box between the phone line and the handset. It lets trusted callers through, makes unknown callers announce themselves, and blocks the rest. Carers can manage it remotely (trueCall Care). Prices: TrueCall VI is £119.99 at RNIB, trueCall Care is about £10.25/mo financed plus a £25/yr online service after year 1 ([RNIB](https://shop.rnib.org.uk/products/truecall-vi-stop-nuisance-calls), [Essential Aids](https://www.essentialaids.com/household/handy-items/truecall-care-nuisance-call-blocker.html)). UK councils and National Trading Standards hand units out free to vulnerable adults ([Surrey CC](https://www.surreycc.gov.uk/community/news/categories/trading-standards/call-blockers), [Northumberland](https://www.northumberland.gov.uk/News/2023/Jun/Council-protects-vulnerable-adults-with-scam-call.aspx)). **No AI and no content analysis.** It is UK analog-landline hardware, and a trusted or spoofed number gets through.
- **CPR Call Blocker (UK):** A blocklist/allowlist landline box with a block button, typically £30 to £60 **(price unverified, no current source found)**. It has the same limitations as trueCall.
- **SeniorSafe (Sentien, US, launched June 2026):** AI that "detects when an ongoing conversation poses a risk to the senior". The iPhone app includes landline protection, and the company claims 99% **(company claim)** ([Eastern Progress](https://www.easternprogress.com/sentien-unveils-game-changing-ai-platform-seniorsafe---stops-both-human-and-ai-deepfake/article_43448250-8be0-5b5c-a3ef-a0ac9cd77286.html)). **This is the closest conceptual match** to Scam Call Guardian (in-conversation risk). It is US and English only.
- **Scammer Guardian (Ohio, founded 2025):** An AI answers unknown calls. Apps for iPhone and Android, and a **landline device in development for late 2026**. Built on GPT-4o, with family/adult-children positioning ([Scammer Guardian](https://scammer-guardian.pages.dev/for-adult-children), [vs CPR](https://scammer-guardian.pages.dev/vs/cpr-call-blocker)). It screens before the call, in English.
- **OnGuardAI (Montréal):** Monitors calls in real time and **sends an SMS to the user and a family member** when fraud patterns appear. Claims it stores no data after the call ([BetaKit](https://betakit.com/?p=408872)). It overlaps closely with our family-alert idea. It is a smartphone app.
- **ScamBlocker (UK, Jan 2026):** Real-time call screening plus a "digital landline" with UK area codes ([Product Hunt](https://www.producthunt.com/products/scamblocker/launches)).
- UK company building an AI product for elderly targets after fraud-related deaths ([Today's Wills & Probate](https://todayswillsandprobate.co.uk/company-creates-cutting-edge-ai-product-to-protect-elderly-targets-of-phone-scammers-after-fraud-incidents-lead-to-death-and-residential-care/)). Details are thin.

---

## 7. Enterprise voice-fraud and deepfake startups (B2B, the bank side)

- **Pindrop (Pulse):** Deepfake and synthetic-voice detection for contact centres. It claims a 2-second detection window and 150 ms response, and offers a "deepfake warranty" on 3-year contracts ([Speech Tech Mag](https://www.speechtechmag.com/Articles/News/Speech-Technology-News/Pindrop-Launches-Pulse-to-Help-Detect-Audio-Deepfakes-162643.aspx), [Help Net Security](https://helpnetsecurity.com/?p=292638)). No consumer product found.
- **Reality Defender:** Real-time deepfake scoring on contact-centre audio streams (NICE, Twilio, Five9, Zoom). A bank case study reports 1.7M calls analysed and about 960 synthetic calls flagged. Gartner named it a "Market Shaper" in June 2026 ([Reality Defender](https://realitydefender.com/insights/real-time-deepfake-detection-in-call-centers), [case study](https://www.realitydefender.com/case-study/how-a-global-bank-mitigates-contact-center-deepfake-threats-with-reality-defender)).
- **Hiya:** The B2B network-level call-protection engine behind Telenor Norway and Telenor Sweden, plus its consumer AI Phone ([briefglance](https://briefglance.com/companies/hiya-inc/pulses/30988)).

**Relevance:** These protect the *bank's* inbound line from voice-clone fraudsters posing as customers. They do not protect the customer from a human scammer. They are potential technology partners (voice-clone scoring), not direct competitors.

---

## 8. Comparison table

Columns: **RT** = analyses the conversation in real time. **NO** = Norwegian language. **LL** = works with landlines and basic phones. **Fam** = family/carer loop. **Content** = judges what is *said*, not just the number or the voice.

| Product | Type | RT | Content | NO | LL | Fam | Price | Main gap for an 80-year-old Norwegian on a landline |
|---|---|---|---|---|---|---|---|---|
| **Scam Call Guardian** | Bedside device + family app | Yes (~6 s cadence) | Yes (LLM) | Yes (Deepgram NO + EN) | Yes (speakerphone/room mic) | Yes (history, safe words) | TBD (hardware ~ESP32 BOM + STT/LLM per minute) | Needs speakerphone. Cloud audio raises a privacy question. Unproven accuracy |
| Google Pixel Scam Detection | On-device OS | Yes | Yes | **No** | No | No | Free with Pixel 9+ | Not available in Norway. Needs a new Pixel |
| Samsung (Google SD) | On-device OS | Yes | Yes | **No** | No | No | Free with S26 | Same |
| Apple Call Screening | OS | Pre-answer only | Caller's stated reason | **No** | No | No | Free | Ends once you answer |
| Truecaller AI Call Scanner | App | Manual, voice-clone only | No | No | No | No | Premium | Detects only AI voices, not human scammers |
| Hiya AI Phone | App | Deepfake voice | Screening only | Unknown | No | No | $9.99/mo | Same |
| Norton / McAfee / Bitdefender / Trend | Security apps | No | Text/web/video | Unclear | No | Partial (family plans) | ~$20 to $95/yr | No live call audio |
| Aura Call Assistant | App | Pre-answer | Screening | No (US) | No | Family plan | ~$3.99/line/mo | US only |
| Telenor Nummervarsel / Telia Svindelsperre / Ice | Network | Pre-connect | Number only | n/a | Mobile yes. Fixed **(unverified)** | No | Included | Misses single-use or legit-looking numbers. Nothing during the call |
| O2 Daisy | Scambaiting bot | n/a | n/a | No | n/a | No | n/a | Does not protect a victim on the call |
| Monzo / CommBank / Revolut call status | Bank app | Status only | No | n/a (not in NO) | No | No | Free | Bank impersonation only. Smartphone only |
| DNB / Nordea / SpareBank 1 / Danske | Bank | No in-call feature found | No | Yes (warnings) | n/a | DNB/Nordea have fullmakt but no call alerts | Free | Acts at transaction time, after manipulation |
| Android verified financial calls | OS + bank app | Pre-connect | No | Unknown bank list | No | No | Free | Bank impersonation only |
| trueCall / CPR | Landline box | Pre-answer | No | No (UK) | **Yes** | trueCall Care: yes | £60 to £120 + £25/yr | Allowlist only. No AI. Not sold in Norway |
| SeniorSafe (Sentien) | AI app + landline | Yes (claimed) | Yes (claimed) | No | Yes (claimed) | Yes | Unknown | US/English |
| Scammer Guardian | AI screening app, landline device planned | Pre-answer | Screening | No | Planned late 2026 | Yes | Unknown | English. Device not shipped |
| OnGuardAI | App | Yes | Patterns | No (CA) | No | **Yes (SMS to family)** | Unknown | Smartphone app, English/French |
| Pindrop / Reality Defender | B2B contact-centre | Yes | Voice synthesis | Language-agnostic for synthesis | n/a | n/a | Enterprise | Protects the bank's inbound line, not the elderly customer |

---

## 9. Positioning

### Where Scam Call Guardian is differentiated
1. **Norwegian, in the conversation.** No shipping product analyses the *content* of a live Norwegian phone conversation. Google and Samsung cover 13 countries and Norway is not one of them. Telcos stop at the number. Banks act at the transaction.
2. **Phone-agnostic hardware.** It works with any phone, including a landline or an old mobile on speaker, and nothing needs installing on the protected person's phone. trueCall is the only comparable hardware, and it has no intelligence.
3. **Catches the scripts that beat other defences.** "Trygg konto", fake police and fake bank (where call-status features fail), and scammers on fresh single-use numbers (where telco filters fail). The LLM judges *intent* ("move money", "don't tell anyone", "read me the BankID code"), so it does not depend on number reputation or voice synthesis.
4. **Family loop built in:** event history plus safe words for "family emergency" or grandchild calls. Only OnGuardAI and trueCall Care have something similar, and neither is in Norway.
5. **A natural DNB distribution channel and data story.** DNB has no call-status feature, and Finans Norge names manipulation fraud the top threat for 2026. A DNB-branded device could later tie into the bank's side, for example "DNB is not calling you" or holding a large transfer while the device is red.
6. **Simple, glanceable UX:** traffic light plus a short reason. The red latch matches how elderly users process warnings.

### Where it is weak
- **Speakerphone requirement and acoustics.** Many elderly people hold the handset to their ear, so the device may hear only one side of the call. Without a line tap or handset adapter, hearing both sides is not guaranteed.
- **Latency.** A 6-second cadence plus STT and LLM time means a warning lands about 6 to 10 s after a red-flag phrase. That is fine for long "trygg konto" calls, but weaker in the first seconds.
- **Privacy and regulation.** Streaming room audio to US cloud STT and LLM providers (Deepgram, Anthropic) creates GDPR and Datatilsynet issues, including consent from the *caller* side. Google's on-device story is a strong counter-message. A bank channel will demand an EU data-residency answer.
- **Accuracy is unproven.** False reds on legitimate calls from the bank, doctor or NAV will erode trust quickly, and so will misses. We need a Norwegian eval set.
- **Ongoing cost.** Per-minute STT and LLM spend, plus hardware logistics, support, battery and Wi-Fi setup at an 80-year-old's home.
- **The warning alone may not stop a manipulated victim.** Scammers tell victims to ignore warnings. The family alert and bank integration matter more than the light.

### Realistic threats
1. **Google ships Norwegian live Scam Detection.** This is the biggest threat. Google adds countries at Pixel Drops (12 countries plus the US by 2026) and has extended to Samsung. A Nordic expansion within 12 to 24 months is plausible. Mitigation: we still win on landlines and old phones (most of the target segment does not own a Pixel 9 or S26), plus the family loop and the bank tie-in.
2. **Telenor plus Hiya add in-network AI.** Hiya already has real-time voice AI and is Telenor's engine. A network-side "live scam warning" would need no device. Lawful-interception and privacy rules make content analysis in the network hard, but not impossible as an opt-in service.
3. **Norwegian banks copy the call-status model**, adding a DNB/Nordea "ringer vi deg nå?" check, or join Android verified financial calls. This is cheap for them, and it covers the bank-impersonation slice.
4. **Startups expanding.** SeniorSafe, Scammer Guardian (landline device) or OnGuardAI could localise to the Nordics, and trueCall could add AI.
5. **Apple adds in-call analysis** to iOS (on-device Apple Intelligence). This would affect the iPhone-owning adult children more than landline users.

---

**Six-line summary**
1. No shipping product judges the *content* of a live Norwegian phone call. Google and Samsung's Gemini Nano detection skips Norway, telcos filter only numbers, and banks act only at the transaction.
2. Scam Call Guardian's edge: phone-agnostic (landline or old mobile), Norwegian, intent-based detection of "trygg konto" and fake-police scripts, with a family loop (history, safe words).
3. DNB has no "is the bank calling you?" feature (Monzo, CommBank and Revolut do), and Finans Norge names manipulation fraud the top 2026 threat. That makes DNB a credible channel and integration partner.
4. Closest analogues are US/English startups (SeniorSafe, OnGuardAI, Scammer Guardian's planned landline box) and trueCall's AI-free UK call blocker. None is in Norway.
5. Weaknesses: speakerphone and one-sided audio, 6 to 10 s latency, cloud audio and GDPR, unproven accuracy, ongoing STT and LLM cost.
6. Biggest threat: Google adds Norway or Norwegian to on-device Scam Detection, or Telenor and Hiya add in-network AI. Defensible wedge: landline and older-phone users, family oversight, bank integration.
