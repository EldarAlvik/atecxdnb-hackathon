"""Mock Scam Call Guardian board: the HTTP and WebSocket API of docs/protocol.md.

    uv run --group mock python mock/mock_board.py                  # scripted calls
    uv run --group mock python mock/mock_board.py --replay mock/recordings/scam_call.jsonl
    uv run --group mock python mock/mock_board.py --token abc --port 8787 --ws-port 8788

The Android emulator reaches it at 10.0.2.2. Turning guarding on (POST /guard or
the app) plays a call: a recording given with --replay (the output of
`uv run atech monitor --json`, or raw serial lines), else the built-in scripts in
turn: a scam call, an ordinary call, a family call with a security word.

Test hooks, no token needed:
    POST /mock/offline?seconds=20     stop answering (the app should warn)
    POST /mock/scenario?name=scam     start a call now (scam | benign | family | replay)
    POST /mock/low_memory?on=1        report degraded features
"""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import os
import random
import secrets
import time
from collections import deque
from pathlib import Path

from aiohttp import WSMsgType, web

FW = "2.0.0-mock"
PROTO = 2
DEVICE = "guardian-m0c4"
ALL_EVENT_KEYS = ["guard", "wifi", "ws", "transcript", "verdict", "health", "word_match",
                  "setup", "config", "words", "degraded", "error"]
LOG_EXCLUDED = {"health"}
MAX_MEMBERS = 8
RING = 500

# ---------------------------------------------------------------- words


def normalise(text: str) -> str:
    """Same rules as the firmware: lower case, letters/digits only, single spaces."""
    out = []
    for ch in text.lower():
        o = ord(ch)
        if ("a" <= ch <= "z") or ("0" <= ch <= "9") or (0xDF <= o <= 0xFF and o != 0xF7):
            out.append(ch)
        else:
            out.append(" ")
    return " ".join("".join(out).split())


def word_hash(salt: bytes, norm: str) -> str:
    return hashlib.sha256(salt + norm.encode("utf-8")).hexdigest()


# ---------------------------------------------------------------- scripted calls

SCRIPTS = {
    "scam": [
        ("Hei, dette er Kari fra sikkerhetsavdelingen i DNB.", "no", None),
        ("Vi har oppdaget mistenkelig aktivitet på kontoen din.", "no",
         ("unsure", "Bank claims account issue")),
        ("For å sikre pengene dine må du bekrefte med BankID nå.", "no",
         ("scam", "Ber om BankID-kode, typisk svindel")),
        ("Ikke fortell dette til noen, det haster.", "no", None),
    ],
    "benign": [
        ("Hi, it's Anna from the dentist.", "en", None),
        ("I'm calling to confirm your appointment next Tuesday at ten.", "en",
         ("safe", "Ordinary appointment reminder")),
        ("Please call us back if the time does not suit you.", "en",
         ("safe", "Ordinary appointment reminder")),
    ],
    "family": [
        ("Hei, det er meg, jeg har mistet telefonen min.", "no",
         ("unsure", "Ukjent nummer, sier det er familie")),
        ("[family word], du vet det er meg.", "no", "WORD"),
        ("Kan du ringe meg tilbake på dette nummeret i kveld?", "no",
         ("safe", "Familieord bekreftet, vanlig samtale")),
    ],
}


class Board:
    def __init__(self, token: str, replay: Path | None, offline_after_setup: float = 15):
        self.token = token
        self.replay = replay
        self.offline_after_setup = offline_after_setup
        self.started = time.time()
        self.seq = 0
        self.ring: deque[dict] = deque(maxlen=RING)
        self.clients: set[web.WebSocketResponse] = set()
        self.guarding = False
        self.verdict = "none"
        self.reason = ""
        self.latched = False
        self.transcript = ""
        self.word_match = None
        self.stt = "closed"
        self.ai_calls = 0
        self.ai_ms = 0
        self.config = {
            "mic_gain": 8, "ai_interval_s": 6, "language": "multi", "red_latch": True,
            "events": list(ALL_EVENT_KEYS), "screen_brightness": 100,
        }
        self.members: dict[int, dict] = {}
        self.next_member_id = 1
        self.offline_until = 0.0
        self.low_memory = False
        self.bad_tokens = 0
        self.locked_until = 0.0
        self.call_task: asyncio.Task | None = None
        self.script_cycle = ["scam", "benign", "family"]
        self.script_index = 0

    # ---------------------------------------------------------- events

    def emit(self, key: str, value, event_type: str = "state") -> dict:
        self.seq += 1
        ev = {"type": "event", "payload": {
            "event_type": event_type, "key": key, "value": value, "source": "guardian",
            "seq": self.seq, "ts": int(time.time())}}
        if key in self.config["events"]:
            if key not in LOG_EXCLUDED:
                self.ring.append(ev)
            text = json.dumps(ev, ensure_ascii=False)
            for ws in list(self.clients):
                asyncio.ensure_future(self._send(ws, text))
        print(f"  #{self.seq} {key}: {json.dumps(value, ensure_ascii=False)[:110]}", flush=True)
        return ev

    async def _send(self, ws: web.WebSocketResponse, text: str) -> None:
        try:
            await ws.send_str(text)
        except Exception:
            self.clients.discard(ws)

    # ---------------------------------------------------------- state

    def heap(self) -> dict:
        free = (28000 if self.low_memory else 118000 if self.guarding else 214000) + random.randint(-3000, 3000)
        return {"free": free, "min": min(free, 92000), "max_block": min(free - 30000, 69620)}

    def degraded(self) -> list[str]:
        return ["ws_server", "http"] if self.low_memory else []

    def status(self) -> dict:
        tail = self.transcript[-300:]
        return {
            "device": DEVICE, "fw": FW, "proto": PROTO,
            "uptime_s": int(time.time() - self.started), "time": int(time.time()),
            "guarding": self.guarding, "verdict": self.verdict, "reason": self.reason,
            "latched": self.latched, "transcript": tail, "word_match": self.word_match,
            "wifi": {"ssid": "MockNet", "rssi": -52 + random.randint(-3, 3),
                     "ip": "10.0.2.2", "source": "saved"},
            "stt": self.stt,
            "ai": {"calls": self.ai_calls, "last_ms": self.ai_ms, "last_http": 200 if self.ai_calls else 0},
            "heap": self.heap(), "degraded": self.degraded(), "last_seq": self.seq,
            "members": len(self.members),
        }

    def member_view(self, m: dict) -> dict:
        return {"id": m["id"], "name": m["name"], "has_word": bool(m.get("hash")), "updated": m["updated"]}

    async def set_guard(self, on: bool) -> None:
        if on == self.guarding:
            return
        self.guarding = on
        if on:
            self.verdict, self.reason, self.latched = "none", "", False
            self.transcript, self.word_match = "", None
            self.emit("guard", "on")
            self.call_task = asyncio.create_task(self.run_call())
        else:
            if self.call_task:
                self.call_task.cancel()
            self.emit("guard", "off")
            if self.stt == "connected":
                self.emit("ws", "closing")
            self.stt = "closed"
            self.emit("ws", "closed")

    async def connect_stt(self) -> None:
        self.stt = "connecting"
        self.emit("ws", "connecting")
        await asyncio.sleep(1.2)
        self.stt = "connected"
        self.emit("ws", "connected")

    def add_text(self, text: str, lang: str | None) -> None:
        self.transcript = (self.transcript + " " + text).strip()[-2400:]
        v = {"text": text}
        if lang:
            v["lang"] = lang
        self.emit("transcript", v)

    def set_verdict(self, verdict: str, reason: str) -> None:
        if self.latched:
            return
        ms = random.randint(900, 2400)
        self.ai_calls += 1
        self.ai_ms = ms
        self.verdict, self.reason = verdict, reason
        if verdict == "scam" and self.config["red_latch"]:
            self.latched = True
        self.emit("verdict", {"verdict": verdict, "reason": reason, "latched": self.latched,
                              "http": 200, "ms": ms, "chars": len(self.transcript),
                              "word_match": self.word_match is not None})

    async def run_call(self, name: str | None = None) -> None:
        try:
            await self.connect_stt()
            if name is None:
                name = "replay" if self.replay else self.script_cycle[self.script_index % 3]
                self.script_index += 1
            if name == "replay" and self.replay:
                await self.play_recording(self.replay)
            else:
                await self.play_script(name)
        except asyncio.CancelledError:
            pass

    async def play_script(self, name: str) -> None:
        for text, lang, outcome in SCRIPTS[name]:
            await asyncio.sleep(random.uniform(2.5, 4.5))
            if not self.guarding:
                return
            self.add_text(text, lang)
            if outcome == "WORD":
                m = next((m for m in self.members.values() if m.get("hash")), None)
                if m:
                    self.word_match = {"member_id": m["id"], "member": m["name"]}
                    self.emit("word_match", dict(self.word_match))
            elif outcome:
                await asyncio.sleep(random.uniform(1.0, 2.5))
                self.set_verdict(*outcome)

    async def play_recording(self, path: Path) -> None:
        """Re-emit a capture with its original timing (gaps capped at 6 s)."""
        last_at = None
        for raw in path.read_text(encoding="utf-8").splitlines():
            raw = raw.strip()
            if not raw:
                continue
            try:
                rec = json.loads(raw)
            except json.JSONDecodeError:
                continue
            if rec.get("type") == "event" and "payload" in rec:  # raw serial line
                p = rec["payload"]
                key, value, etype, at = p.get("key"), p.get("value"), p.get("event_type", "state"), None
            else:                                                 # atech monitor --json
                key, value, etype, at = rec.get("key"), rec.get("value"), rec.get("type", "state"), rec.get("received_at")
            if key in (None, "guard", "health", "startup"):
                continue
            if at is not None and last_at is not None:
                await asyncio.sleep(max(0.05, min(6.0, at - last_at)))
            elif at is None:
                await asyncio.sleep(0.8)
            last_at = at if at is not None else last_at
            if not self.guarding:
                return
            if key == "ws":
                self.stt = {"connected": "connected", "connecting": "connecting"}.get(value, "closed")
                if value in ("connecting", "connected"):
                    continue   # already done by connect_stt
            if key == "transcript" and isinstance(value, dict):
                self.add_text(value.get("text", ""), value.get("lang"))
                continue
            if key == "verdict" and isinstance(value, dict):
                self.set_verdict(value.get("verdict", "unsure"), value.get("reason", ""))
                continue
            self.emit(key, value, etype if etype in ("state", "sensor", "log") else "state")

    async def health_loop(self) -> None:
        while True:
            await asyncio.sleep(5 if self.guarding else 30)
            if time.time() < self.offline_until:
                continue
            h = self.heap()
            self.emit("health", {
                "guarding": self.guarding, "free_heap": h["free"], "min_free_heap": h["min"],
                "max_block": h["max_block"], "audio_buf": random.randint(0, 3200),
                "audio_sent": int((time.time() - self.started) * 32000) if self.guarding else 0,
                "audio_dropped": 0, "ai_calls": self.ai_calls, "ai_ms": self.ai_ms,
                "mic_level": random.randint(200, 2500) if self.guarding else random.randint(5, 60),
                "ws_clients": len(self.clients)}, "sensor")


# ---------------------------------------------------------------- HTTP


def json_response(data, status=200):
    return web.json_response(data, status=status, dumps=lambda d: json.dumps(d, ensure_ascii=False))


def make_app(board: Board, ws_only: bool = False) -> web.Application:
    @web.middleware
    async def gate(request: web.Request, handler):
        if time.time() < board.offline_until:
            # The board is "rebooting": drop the connection like a dead host would.
            if request.transport:
                request.transport.close()
            raise web.HTTPServiceUnavailable()
        if request.path.startswith("/mock/"):
            return await handler(request)
        now = time.time()
        if now < board.locked_until:
            return json_response({"error": "too_many_attempts"}, 429)
        auth = request.headers.get("Authorization", "")
        tok = auth[7:] if auth.startswith("Bearer ") else request.query.get("token", "")
        if not secrets.compare_digest(tok.encode(), board.token.encode()):
            board.bad_tokens += 1
            if board.bad_tokens >= 10:
                board.bad_tokens = 0
                board.locked_until = now + 30
            return json_response({"error": "unauthorized"}, 401)
        board.bad_tokens = 0
        if board.low_memory and not ws_only and request.path != "/status":
            return json_response({"error": "low_memory"}, 503)
        return await handler(request)

    async def body(request: web.Request) -> dict:
        try:
            data = await request.json()
        except Exception:
            raise web.HTTPBadRequest(text='{"error":"bad json"}', content_type="application/json")
        if not isinstance(data, dict):
            raise web.HTTPBadRequest(text='{"error":"expected an object"}', content_type="application/json")
        return data

    async def status(_):
        return json_response(board.status())

    async def guard(request):
        data = await body(request)
        if not isinstance(data.get("on"), bool):
            return json_response({"error": "on must be true or false"}, 400)
        await board.set_guard(data["on"])
        return json_response({"guarding": board.guarding})

    async def events(request):
        try:
            since = int(request.query.get("since", "0"))
            limit = max(1, min(200, int(request.query.get("limit", "100"))))
        except ValueError:
            return json_response({"error": "since and limit must be integers"}, 400)
        newer = [e for e in board.ring if e["payload"]["seq"] > since]
        page = newer[:limit]
        oldest = board.ring[0]["payload"]["seq"] if board.ring else board.seq + 1
        return json_response({"events": page, "last_seq": board.seq, "oldest_seq": oldest,
                              "more": len(newer) > limit})

    async def get_config(_):
        return json_response(board.config)

    async def post_config(request):
        data = await body(request)
        new = dict(board.config)
        checks = {
            "mic_gain": lambda v: isinstance(v, int) and not isinstance(v, bool) and 1 <= v <= 64,
            "ai_interval_s": lambda v: isinstance(v, int) and not isinstance(v, bool) and 3 <= v <= 60,
            "language": lambda v: v in ("multi", "no", "en"),
            "red_latch": lambda v: isinstance(v, bool),
            "events": lambda v: isinstance(v, list) and all(k in ALL_EVENT_KEYS for k in v),
            "screen_brightness": lambda v: isinstance(v, int) and not isinstance(v, bool) and 10 <= v <= 100,
        }
        for k, v in data.items():
            if k not in checks:
                return json_response({"error": f"unknown setting {k}"}, 400)
            if not checks[k](v):
                return json_response({"error": f"bad value for {k}"}, 400)
            new[k] = v
        changed = [k for k in data if board.config.get(k) != new[k]]
        board.config = new
        if changed:
            board.emit("config", {"changed": changed})
        return json_response(board.config)

    async def get_words(_):
        return json_response({"members": [board.member_view(m) for m in board.members.values()],
                              "max_members": MAX_MEMBERS})

    def set_word(m: dict, word: str):
        norm = normalise(word)
        n = len(norm.split())
        if not (3 <= len(norm.encode()) <= 48) or not (1 <= n <= 4):
            return "word must be 1-4 words and 3-48 bytes"
        salt = os.urandom(16)
        m["salt"], m["hash"], m["nwords"] = salt.hex(), word_hash(salt, norm), n
        return None

    async def post_words(request):
        data = await body(request)
        now = int(time.time())
        if "id" in data:
            m = board.members.get(data["id"]) if isinstance(data["id"], int) else None
            if not m:
                return json_response({"error": "no such member"}, 404)
            if "name" in data:
                name = str(data["name"]).strip()
                if not (1 <= len(name.encode()) <= 24):
                    return json_response({"error": "name must be 1-24 bytes"}, 400)
                m["name"] = name
                board.emit("words", {"action": "rename", "id": m["id"], "name": name})
            if data.get("clear"):
                m.pop("salt", None); m.pop("hash", None); m.pop("nwords", None)
                board.emit("words", {"action": "clear", "id": m["id"], "name": m["name"]})
            elif "word" in data:
                err = set_word(m, str(data["word"]))
                if err:
                    return json_response({"error": err}, 400)
                board.emit("words", {"action": "set", "id": m["id"], "name": m["name"]})
            m["updated"] = now
            return json_response(board.member_view(m))
        name = str(data.get("name", "")).strip()
        if not (1 <= len(name.encode()) <= 24):
            return json_response({"error": "name must be 1-24 bytes"}, 400)
        if len(board.members) >= MAX_MEMBERS:
            return json_response({"error": "at most 8 members"}, 400)
        m = {"id": board.next_member_id, "name": name, "updated": now}
        if data.get("word"):
            err = set_word(m, str(data["word"]))
            if err:
                return json_response({"error": err}, 400)
        board.next_member_id += 1
        board.members[m["id"]] = m
        board.emit("words", {"action": "add", "id": m["id"], "name": name})
        return json_response(board.member_view(m))

    async def delete_words(request):
        try:
            mid = int(request.query.get("id", ""))
        except ValueError:
            return json_response({"error": "id must be an integer"}, 400)
        m = board.members.pop(mid, None)
        if not m:
            return json_response({"error": "no such member"}, 404)
        board.emit("words", {"action": "remove", "id": mid, "name": m["name"]})
        return json_response({"deleted": mid})

    async def setup(_):
        async def reboot():
            await asyncio.sleep(0.5)
            await board.set_guard(False)
            for ws in list(board.clients):
                await ws.close()
            board.offline_until = time.time() + board.offline_after_setup
        asyncio.create_task(reboot())
        return json_response({"setup": True})

    async def mock_offline(request):
        board.offline_until = time.time() + float(request.query.get("seconds", "20"))
        for ws in list(board.clients):
            await ws.close()
        return json_response({"offline_until": board.offline_until})

    async def mock_scenario(request):
        name = request.query.get("name", "scam")
        if name not in (*SCRIPTS, "replay"):
            return json_response({"error": "name must be scam, benign, family or replay"}, 400)
        await board.set_guard(False)
        board.guarding = True
        board.verdict, board.reason, board.latched = "none", "", False
        board.transcript, board.word_match = "", None
        board.emit("guard", "on")
        board.call_task = asyncio.create_task(board.run_call(name))
        return json_response({"scenario": name})

    async def mock_low_memory(request):
        board.low_memory = request.query.get("on", "1") == "1"
        if board.low_memory:
            board.emit("degraded", {"feature": "ws_server", "reason": "low_heap", "free_heap": 28000})
        return json_response({"low_memory": board.low_memory})

    async def websocket(request):
        if board.low_memory or len(board.clients) >= 2:
            ws = web.WebSocketResponse()
            await ws.prepare(request)
            await ws.close(code=1013, message=b"busy")
            return ws
        ws = web.WebSocketResponse(heartbeat=15)
        await ws.prepare(request)
        board.clients.add(ws)
        await ws.send_str(json.dumps({"type": "hello", "device": DEVICE, "fw": FW,
                                      "proto": PROTO, "last_seq": board.seq}))
        try:
            async for msg in ws:
                if msg.type == WSMsgType.ERROR:
                    break
        finally:
            board.clients.discard(ws)
        return ws

    app = web.Application(middlewares=[gate])
    if ws_only:
        app.router.add_get("/", websocket)
        app.router.add_get("/ws", websocket)
        return app
    app.router.add_get("/status", status)
    app.router.add_post("/guard", guard)
    app.router.add_get("/events", events)
    app.router.add_get("/config", get_config)
    app.router.add_post("/config", post_config)
    app.router.add_get("/words", get_words)
    app.router.add_post("/words", post_words)
    app.router.add_delete("/words", delete_words)
    app.router.add_post("/setup", setup)
    app.router.add_post("/mock/offline", mock_offline)
    app.router.add_post("/mock/scenario", mock_scenario)
    app.router.add_post("/mock/low_memory", mock_low_memory)
    return app


async def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8787)
    ap.add_argument("--ws-port", type=int, default=8788)
    ap.add_argument("--token", default="mock-token")
    ap.add_argument("--replay", type=Path, help="capture from `uv run atech monitor --json`")
    args = ap.parse_args()

    board = Board(args.token, args.replay)
    board.emit("startup", {"fw": FW, "reason": "POWERON", "free_heap": 214000, "mode": "normal", "log_events": 0})
    board.emit("wifi", {"status": "connected", "code": 3, "ip": "10.0.2.2", "rssi": -52, "source": "saved"})

    http_runner = web.AppRunner(make_app(board))
    ws_runner = web.AppRunner(make_app(board, ws_only=True))
    await http_runner.setup()
    await ws_runner.setup()
    await web.TCPSite(http_runner, args.host, args.port).start()
    await web.TCPSite(ws_runner, args.host, args.ws_port).start()
    print(f"mock board {DEVICE}: http://{args.host}:{args.port}  ws://{args.host}:{args.ws_port}/  "
          f"token={args.token}" + (f"  replay={args.replay}" if args.replay else ""), flush=True)
    await board.health_loop()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
