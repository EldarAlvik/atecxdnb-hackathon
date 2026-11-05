# Council round 2 brief: agent-native companies on the Atech kit

You are one member of a "council" of AI models pitching startup ideas for a hackathon team. Other models get this same brief independently.

**Independence rule:** do NOT open, list or read any other file in `council/` or `council-2/` except this BRIEF.md. Your ideas must be your own.

## Context
- Event: the ATEC x DNB hackathon. DNB is Norway's largest bank (banking, payments, Vipps, insurance, SMB lending). Atech makes a modular ESP32-S3 hardware kit. The team wants to build a company and a hackathon MVP on the kit. A DNB / fintech / trust / insurance / sustainability / consumer-banking angle is a plus.
- The repo (your working directory) has the `atech` Python SDK in `.venv`. You can explore it (`.venv/Scripts/atech.exe list-modules`, `atech info <module>`, `atech guide`, `.venv/Lib/site-packages/atech/docs/*.md`) but don't modify existing files.

## The kit the team actually has
- ESP32-S3 board with **Wi-Fi and Bluetooth**
- Speaker, microphone
- **Four motors**
- Humidity + temperature sensor
- Accelerometer
- Three buttons, three LEDs
- A screen
- A rotary knob (rotation button)

Design only around these parts.

## Hard requirement: an AI agent is core to the product
Every idea must have an **AI agent integrated into it**. That means an LLM-driven agent that perceives the world through the kit's sensors (voice, motion, climate, buttons, knob), reasons, and takes actions: speaking, moving motors, showing things on screen, and calling external tools and APIs such as a bank API, Vipps, calendars, insurers or suppliers. It must not be a chatbot bolted onto a gadget. Explain what the agent decides on its own, what it asks the human about, which tools it calls, and why the physical body makes the agent better than a phone app.

## Task
1. Pitch **3 bold, creative company ideas**. For each: name, one-line pitch, problem and customer, the agent's role (perceive → decide → act, tools it calls, where it runs: on-device vs. cloud over Wi-Fi), which kit parts it uses, business model, why it could be big, and why it fits DNB.
2. Pick your favourite and spec a **24–48h hackathon MVP**: hardware setup, the agent architecture (model, tools, memory, guardrails, human-in-the-loop for money moves), what runs on the board vs. laptop/cloud, a 3-minute judge demo script, and the riskiest assumption plus how to de-risk it in hour 1.
3. One deliberately outrageous **wild card**.

## Output
Write markdown to `council-2/<your-file>.md` (only that file), then reply with a short summary of your three ideas and your pick.
