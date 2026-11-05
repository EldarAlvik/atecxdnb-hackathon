"""Exercise a board (the mock or the real one) through docs/protocol.md.

    uv run --group mock python mock/smoke_test.py                       # mock on localhost
    uv run --group mock python mock/smoke_test.py --host guardian.local --port 80 --ws-port 81 --token <hex>

Leaves the board as it found it: guarding off, test member removed, config restored.
Pass --call to also run a guarded call and wait for a verdict over the WebSocket.
"""
import argparse
import asyncio
import json
import sys

import aiohttp


async def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8787)
    ap.add_argument("--ws-port", type=int, default=8788)
    ap.add_argument("--token", default="mock-token")
    ap.add_argument("--call", action="store_true", help="guard on and wait for a verdict")
    args = ap.parse_args()
    base = f"http://{args.host}:{args.port}"
    hdr = {"Authorization": f"Bearer {args.token}"}
    failures = 0

    def check(name, ok, detail=""):
        nonlocal failures
        print(("PASS " if ok else "FAIL ") + name + (f"  ({detail})" if detail and not ok else ""))
        failures += 0 if ok else 1

    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=20)) as s:
        async with s.get(base + "/status") as r:
            check("no token -> 401", r.status == 401, r.status)
        async with s.get(base + "/status", headers={"Authorization": "Bearer nope"}) as r:
            check("bad token -> 401", r.status == 401, r.status)
        async with s.get(base + "/status", headers=hdr) as r:
            st = await r.json()
            check("GET /status", r.status == 200 and st.get("proto") == 2, st)
            print(f"     heap free={st['heap']['free']} max_block={st['heap']['max_block']} degraded={st['degraded']}")

        async with s.get(base + "/config", headers=hdr) as r:
            cfg = await r.json()
            check("GET /config", r.status == 200 and "mic_gain" in cfg, cfg)
        async with s.post(base + "/config", headers=hdr, json={"mic_gain": 99}) as r:
            check("POST /config bad value -> 400", r.status == 400, r.status)
        async with s.post(base + "/config", headers=hdr, json={"mic_gain": cfg["mic_gain"] % 64 + 1}) as r:
            new = await r.json()
            check("POST /config", r.status == 200 and new["mic_gain"] == cfg["mic_gain"] % 64 + 1, new)
        async with s.post(base + "/config", headers=hdr, json={"mic_gain": cfg["mic_gain"]}) as r:
            check("config restored", r.status == 200)

        async with s.post(base + "/words", headers=hdr, json={"name": "Smoke Test", "word": "Blå Elefant!"}) as r:
            m = await r.json()
            check("POST /words add", r.status == 200 and m.get("has_word") is True, m)
        mid = m.get("id")
        async with s.get(base + "/words", headers=hdr) as r:
            text = await r.text()
            check("GET /words never returns the word", "elefant" not in text.lower() and r.status == 200, text)
        async with s.post(base + "/words", headers=hdr, json={"id": mid, "word": "x"}) as r:
            check("POST /words too short -> 400", r.status == 400, r.status)
        async with s.post(base + "/words", headers=hdr, json={"id": mid, "clear": True}) as r:
            m2 = await r.json()
            check("POST /words clear", r.status == 200 and m2.get("has_word") is False, m2)
        async with s.delete(base + f"/words?id={mid}", headers=hdr) as r:
            check("DELETE /words", r.status == 200, r.status)
        async with s.delete(base + f"/words?id={mid}", headers=hdr) as r:
            check("DELETE /words again -> 404", r.status == 404, r.status)

        async with s.get(base + "/events?since=0&limit=5", headers=hdr) as r:
            ev = await r.json()
            seqs = [e["payload"]["seq"] for e in ev["events"]]
            check("GET /events", r.status == 200 and seqs == sorted(seqs) and len(seqs) <= 5, ev)
            text = json.dumps(ev, ensure_ascii=False).lower()
            check("events never contain the word", "elefant" not in text)

        ws_url = f"ws://{args.host}:{args.ws_port}/?token={args.token}"
        try:
            async with s.ws_connect(f"ws://{args.host}:{args.ws_port}/?token=nope") as ws:
                msg = await ws.receive(timeout=5)
                check("WS bad token closed", msg.type in (aiohttp.WSMsgType.CLOSE, aiohttp.WSMsgType.CLOSED,
                                                           aiohttp.WSMsgType.CLOSING, aiohttp.WSMsgType.ERROR), msg)
        except aiohttp.WSServerHandshakeError as e:
            check("WS bad token refused", e.status in (400, 401, 403), e.status)
        async with s.ws_connect(ws_url, heartbeat=20) as ws:
            hello = json.loads((await ws.receive(timeout=5)).data)
            check("WS hello", hello.get("type") == "hello", hello)
            if args.call:
                async with s.post(base + "/guard", headers=hdr, json={"on": True}) as r:
                    check("POST /guard on", (await r.json()).get("guarding") is True)
                verdict = None
                try:
                    while verdict is None:
                        msg = await ws.receive(timeout=90)
                        e = json.loads(msg.data)["payload"]
                        print(f"     ws #{e['seq']} {e['key']}: {json.dumps(e['value'], ensure_ascii=False)[:90]}")
                        if e["key"] == "verdict":
                            verdict = e["value"]
                except asyncio.TimeoutError:
                    pass
                check("verdict over WS", verdict is not None)
                async with s.post(base + "/guard", headers=hdr, json={"on": False}) as r:
                    check("POST /guard off", (await r.json()).get("guarding") is False)
    print("OK" if not failures else f"{failures} FAILED")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
