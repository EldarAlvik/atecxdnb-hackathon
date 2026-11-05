# Call-scenario schema (Inspect AI)

These scenarios replay a phone call the way the Scam Call Guardian board sees it,
so the eval tests the real product loop rather than a single message.

How the board works (board/project.yaml on branch t3/android-scam-guardian):
- Deepgram nova-3 streams speech-to-text. Both speakers are merged into ONE text
  stream: no speaker labels, no punctuation guarantees, recognition errors happen
  (Norwegian words misheard, numbers spelled out, names mangled).
- About every 6 s, if new text arrived, the board sends Claude Haiku
  (`claude-haiku-5-5`, max_tokens 120, thinking disabled) the system prompt below
  and one user message: "Live call transcript so far:\n" + rolling window
  (last 2400 bytes).
- Reply must be compact JSON {"verdict":"safe"|"unsure"|"scam","reason":"<max 8 words>"},
  reason in the call's language (bokmål for Norwegian calls, English for English).
- The first "scam" verdict LATCHES: the light stays red for the rest of the call.

So a scenario is a list of `chunks`. Chunk i is the new final transcript text
that arrived during window i (~6 s of speech, roughly 10-25 words). The harness
calls the model after each chunk with the cumulative transcript.

## Fields

```yaml
- id: bank-bankid-code-no            # unique, kebab-case, suffix -no / -en / -mix
  lang: "no"                         # "no" | en | mixed  (quote "no": bare no is YAML false)
  category: bank_impersonation       # see list below
  difficulty: easy                   # easy | medium | hard
  expected: scam                     # scam | safe | verify
  detect_by: 3                       # scam only: 0-based chunk index by which the
                                     #   first "scam" verdict must appear. null otherwise.
  tags: [bankid, urgency]            # free-form, for slicing the report
  notes: >                           # one or two lines: why this label, what makes it hard
    Caller claims DNB security, asks for BankID code in chunk 3.
  chunks:
    - "hei det er martin fra dnb sin sikkerhetsavdeling"
    - "..."
```

`expected`:
- `scam`   final verdict must be scam, first scam at or before `detect_by`.
- `safe`   no chunk may be scam (a latched false alarm ruins a real call);
           final verdict should be safe (unsure is a partial pass).
- `verify` genuinely ambiguous: unsure or scam are both fine, safe is a fail.

Write chunks as ASR output: lowercase mostly, sparse punctuation, merged speakers,
occasional misrecognitions. 4-12 chunks per scenario. Norwegian = bokmål unless the
scenario is about dialect/nynorsk.

Categories: bank_impersonation, authority_impersonation (police, Økokrim,
Skatteetaten, NAV), tech_support, family_impersonation, voice_clone, investment,
romance, delivery, marketplace, invoice_fraud, prize_lottery, insurance,
utility_energy, telecom, charity, legit_bank, legit_authority, legit_family,
legit_business, legit_everyday, adversarial, asr_noise.
