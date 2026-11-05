# Scenario review (2026-11-05)

Scope: scam_calls.yaml, legit_calls.yaml, adversarial_calls.yaml, dictation_notes.yaml
(96 scenarios). Chunks are counted from 0. After the edits, all 96 load through
`load_scenarios()` without warnings: 53 scam, 29 safe, 14 verify; 58 no, 29 en, 9 mixed.
`inspect eval evals/call_scenarios.py --model mockllm/model --limit 5` runs.

## Changes

| File | id | Before -> after | Why |
|---|---|---|---|
| adversarial_calls.yaml | all 17 `-no` scenarios | `lang: no` -> `lang: "no"` | Bare `no` parses as YAML false. The harness patched this over, but the schema requires quotes. |
| adversarial_calls.yaml | adv-short-immediate-ask-no | detect_by 1 -> 0 | Chunk 0 already has the DNB claim, "kortet er misbrukt" and the BankID code ask. |
| adversarial_calls.yaml | asr-codeswitch-polish-nav-mix | detect_by 3 -> 2 | Chunk 2 already says "log in bank id now and tell me the code from the phone". |
| adversarial_calls.yaml | asr-codeswitch-swedish-anydesk-mix | detect_by 3 -> 2 | Chunk 2 already says "du må installere anydesk", an unsolicited remote-access install. |
| adversarial_calls.yaml | adv-euphemism-approve-message-no | detect_by 3 -> 2 | Chunk 2 asks the user to install a screen-viewing program after an unsolicited "virus" call. |
| adversarial_calls.yaml | asr-nordnorsk-nynorsk-short-no | detect_by 2 -> 1 | In chunk 1, "Skatteetaten" asks for a BankID login while still on the line. With detect_by 2, the scenario only demanded a catch on its last chunk. |
| adversarial_calls.yaml | adv-window-evasion-bankid-no, chunk 8 | "det har dem ikke" -> "det har de ikke" | Bokmål subject pronoun. |
| scam_calls.yaml | finn-fiks-ferdig-lenke-no | detect_by 4 -> 3 | Chunk 3 already has the Posten SMS link where you "fyller inn kortet ditt for å motta pengene", the textbook Finn scam. |
| scam_calls.yaml | altibox-teamviewer-mix | detect_by 6 -> 5 | Chunk 5 is the unsolicited TeamViewer QuickSupport install. That matches microsoft-anydesk-en, where the remote-access pitch counts as the flag. |
| dictation_notes.yaml | dict-microsoft-anydesk-no | detect_by 2 -> 1 (notes updated) | Chunk 1 is an unsolicited "Mikrosoft" call saying the PC has a virus. The call version (microsoft-anydesk-en) treats this as the flag. |
| dictation_notes.yaml | dict-microsoft-anydesk-en | detect_by 2 -> 0 (notes updated) | Chunk 0 already says "guy from micro soft said my computer has virus". |
| dictation_notes.yaml | dict-crypto-adviser-no / -en | detect_by 3 -> 2 | A Facebook celebrity ad, a "rådgiver", and 3 000 -> 52 000 kr (250 -> 41 000 GBP) in one week by chunk 2 is already clear fraud. |

No label (`expected`) changes. I found no safe scenario with a real code ask, remote-access
install or pressured payment. No speaker labels, narrator text or "this is a scam" leakage. All
ids are unique across the files. All required fields are present, and detect_by is an int only
for scam scenarios.

## Open questions for the team

1. **Lenient detect_by kept on purpose?** In these scenarios the flag arguably shows one chunk
   before detect_by. I left them alone because the later chunk makes the ask explicit:
   - politi-okokrim-bankansatt-no: chunk 4, "hold it from the bank"; detect_by 5.
   - sparebank1-godkjenn-i-appen-no: chunk 5, "trykk godkjenn" while the "bank" is on the line;
     detect_by 6.
   - crypto-celebrity-ad-en: chunk 3, 30 % in the first month; detect_by 4.
   - lawyer-grandson-bail-en: chunk 3, "settle it today with bail"; detect_by 4.
   - adv-device-red-light-no: chunk 1 explains away the red light, and the notes call that a
     red flag; detect_by 3.
   - adv-injection-system-note-mix: injection in chunks 1 and 3; detect_by 4.

   Decide whether spoken prompt injection or device-awareness counts as a scam signal by
   itself. If it does, lower these.
2. **Chunk count:** both adv-window-evasion-* scenarios have 13 chunks, and
   adv-short-immediate-ask-no / asr-nordnorsk-nynorsk-short-no have 2-3. SCHEMA.md says 4-12.
   The file header says this is deliberate. Either widen the range in SCHEMA.md or accept the
   exception.
3. **legit-dnb-customer-blocks-card-no:** the agent sends an approval to the app and the
   customer taps "godkjenn". That is the same surface as the sparebank1 scam. It is safe only
   because the greeting shows the customer called DNB, and the device cannot see call
   direction. Keep it safe (it tests the greeting cue), or downgrade to verify?
4. **Near-duplicate theme:** four safe "scams discussed on TV/news" controls: legit-neighbour-tv-scam-story-no,
   adv-safe-voice-clone-news-no, asr-safe-tv-background-news-no and dict-tv-scam-programme-no.
   They differ in setup, so all four stay, but this pattern is over-represented among the safe set.
5. **Dictation vs call:** dict-* are text, but the harness feeds them with the "Live call
   transcript" prompt. dict-vipps-wrong-payment-* and dict-hi-mum-sms-* mirror call scenarios
   (vipps-feilbetaling-no, hei-mamma-nytt-nummer-mix). Report dictation as a separate slice
   so the two modalities do not double-count.
