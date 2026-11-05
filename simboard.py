"""A fake Atech board for working without hardware.

It speaks the firmware's wire protocol (line-delimited JSON: events and acks out,
actions in), so host code written against `atech.Board` runs unchanged.

Two terminals:

    uv run python -m simboard serve examples/blink      # the fake board + a console
    ATECH_SIM=1 uv run python examples/blink/host.py    # your laptop code

In the console, `press button` presses the button, `emit <key> <value>` sends any
event, `help` lists the rest. Every action your code sends is printed with the
answer the board gave.

In host code, use `simboard.connect()` instead of `Board.connect()`: it returns the
fake board when ATECH_SIM is set (`1`, or `host:port`) and the real one otherwise.

For tests, `simboard.local(project_dir)` gives you a `(board, firmware)` pair in one
process, no server needed.

What the fake board accepts: every action `atech describe` lists for the project,
plus the names passed with `--accept` (comma separated, or `*` for anything), plus
anything a `sim.py` next to `project.yaml` registers. A `sim.py` mimics the
firmware's custom behaviour in Python:

    def setup(fw):
        @fw.on("guard")
        def guard(value):
            fw.emit("guard", value, type="state")
            if value == "on":
                fw.emit("ws", "connected", type="state")

A handler that returns False or raises is answered with ok:false.
It does NOT run the C++ in `code:`. To check that, use `atech build` (compiles
offline) and `atech view` (draws the board in 3D).
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import socket
import sys
import threading
from pathlib import Path
from typing import Any, Callable, Optional

from atech import Board, Project
from atech.runtime.transport import _Inbox

DEFAULT_PORT = 7777
# Event types the SDK knows: sensor, button, state, log.
_BUTTON_MODULES = {"button"}


# ------------------------------------------------------------------ firmware


class SimFirmware:
    """The board side: answers actions, emits events. Writes wire lines to `write`."""

    def __init__(self, project_dir: Optional[str | Path] = None, accept: str = "") -> None:
        self.project: Optional[Project] = None
        self.actions: dict[str, str] = {}  # key -> value type
        self.events: dict[str, str] = {}  # key -> event type
        self.modules: list[dict] = []
        self.state: dict[str, Any] = {}  # last value of every action received
        self._handlers: dict[str, Callable[[Any], Any]] = {}
        self._accept = {a.strip() for a in accept.split(",") if a.strip()}
        self.write: Callable[[str], None] = lambda line: None
        self.log: Callable[[str], None] = lambda text: None

        if project_dir is not None:
            self._load_project(Path(project_dir))

    def _load_project(self, path: Path) -> None:
        self.project = Project.load(path)
        for key, value_type, _desc, _handled in self.project.actions():
            self.actions[key] = value_type
        from jinja2 import Template

        specs = self.project.module_specs()
        for m in self.project.modules:
            for ev in specs[m.module_id].events:
                key = Template(ev.key).render(instance=m.instance)
                self.events[key] = "button" if m.module_id in _BUTTON_MODULES else "sensor"
        self.modules = [
            {"instance": m.instance, "module": m.module_id, "ok": True, "detectable": False}
            for m in self.project.modules
        ]
        sim_py = path / "sim.py"
        if sim_py.exists():
            spec = importlib.util.spec_from_file_location(f"sim_{path.name}", sim_py)
            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)
            if hasattr(mod, "setup"):
                mod.setup(self)

    def on(self, key: str) -> Callable:
        """Decorator: run `fn(value)` when the host sends action `key`."""

        def register(fn: Callable[[Any], Any]) -> Callable[[Any], Any]:
            self._handlers[key] = fn
            return fn

        return register

    def emit(self, key: str, value: Any = None, *, type: Optional[str] = None) -> None:
        """Send an event to the host, as the firmware would."""
        event_type = type or self.events.get(key, "sensor")
        payload = {"event_type": event_type, "key": key, "value": value}
        self.write(json.dumps({"type": "event", "payload": payload}))

    def press(self, instance: str) -> None:
        """Press and release a button: event 1, then 0."""
        self.emit(instance, 1, type="button")
        self.emit(instance, 0, type="button")

    def boot(self) -> None:
        payload = {"reset_reason": "POWERON", "free_heap": 245000, "modules": self.modules}
        self.write(json.dumps({"type": "boot", "payload": payload}))

    def receive(self, line: str) -> None:
        """Handle one wire line from the host."""
        try:
            msg = json.loads(line)
            key = msg["action"]
        except (ValueError, KeyError, TypeError):
            return
        value = _parse_value(msg.get("value", ""))
        ok, error = True, None
        if key in self._handlers:
            try:
                if self._handlers[key](value) is False:
                    ok, error = False, "rejected by sim.py"
            except Exception as exc:  # a broken handler must not kill the board
                ok, error = False, f"{type(exc).__name__}: {exc}"
        elif not (key in self.actions or key in self._accept or "*" in self._accept):
            ok, error = False, "unknown action"
        if ok:
            self.state[key] = value
        self.log(f"<- {key} {json.dumps(value)}  {'ok' if ok else 'REJECTED: ' + error}")
        ack: dict[str, Any] = {"action": key, "ok": ok}
        if error:
            ack["error"] = error
        self.write(json.dumps({"type": "ack", "payload": ack}))


def _parse_value(text: Any) -> Any:
    """The firmware gets every value as a string; give handlers the decoded value."""
    if not isinstance(text, str):
        return text
    try:
        return json.loads(text)
    except ValueError:
        return text


# ---------------------------------------------------------------- transports


class _LineTransport(_Inbox):
    """Shared bits: Board-facing API on top of something that carries lines."""

    def boot_report(self, *, reset: bool = True, timeout: float = 5.0) -> Optional[dict]:
        if self._boot is None:
            self._boot_event.wait(timeout)
        return self._boot


class LocalTransport(_LineTransport):
    """Host and fake firmware in the same process (for tests)."""

    def __init__(self, firmware: SimFirmware) -> None:
        super().__init__()
        self.firmware = firmware
        firmware.write = lambda line: self._handle_line(line.encode())
        firmware.boot()

    def send(self, action) -> None:
        from atech.runtime.transport import encode_action

        self._acks.clear(action.key)
        self.firmware.receive(encode_action(action).decode())

    def close(self) -> None:
        pass


class TcpTransport(_LineTransport):
    """Host side of `python -m simboard serve`."""

    def __init__(self, host: str = "127.0.0.1", port: int = DEFAULT_PORT) -> None:
        super().__init__()
        try:
            self._sock = socket.create_connection((host, port), timeout=3)
        except OSError as exc:
            raise ConnectionError(
                f"no fake board at {host}:{port}. Start one with "
                f"`uv run python -m simboard serve <project_dir>`. ({exc})"
            ) from None
        self._sock.settimeout(None)
        self._closed = False
        threading.Thread(target=self._read_loop, daemon=True).start()

    def _read_loop(self) -> None:
        buf = b""
        while not self._closed:
            try:
                chunk = self._sock.recv(4096)
            except OSError:
                break
            if not chunk:
                break
            buf += chunk
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                self._handle_line(line)

    def send(self, action) -> None:
        from atech.runtime.transport import encode_action

        self._acks.clear(action.key)
        self._sock.sendall(encode_action(action))

    def close(self) -> None:
        self._closed = True
        try:
            self._sock.close()
        except OSError:
            pass


# ------------------------------------------------------------------ host API


def connect(port: Optional[str] = None) -> Board:
    """The fake board when ATECH_SIM is set, else the real one on USB."""
    sim = os.environ.get("ATECH_SIM", "").strip()
    if sim and sim != "0":
        host, _, tcp_port = sim.rpartition(":") if ":" in sim else ("", "", "")
        return Board(TcpTransport(host or "127.0.0.1", int(tcp_port or DEFAULT_PORT)))
    return Board.connect(port=port)


def local(project_dir: Optional[str | Path] = None, accept: str = "") -> tuple[Board, SimFirmware]:
    """A board and its fake firmware in this process. For tests."""
    fw = SimFirmware(project_dir, accept=accept)
    return Board(LocalTransport(fw)), fw


# -------------------------------------------------------------------- server

HELP = """commands:
  press <instance>             press + release a button
  emit <key> <value> [type]    send an event (value: number, JSON or text; type: sensor|button|state|log)
  state                        last value of every action received
  list                         the project's events and actions
  boot                         send the boot report again
  help, quit"""


def serve(project_dir: Optional[str], port: int, accept: str) -> None:
    fw = SimFirmware(project_dir, accept=accept)
    lock = threading.Lock()
    client: dict[str, Optional[socket.socket]] = {"sock": None}

    def write(line: str) -> None:
        with lock:
            sock = client["sock"]
            if sock is None:
                print("   (no host connected, event dropped)")
                return
            try:
                sock.sendall(line.encode() + b"\n")
            except OSError:
                client["sock"] = None

    fw.write = write
    fw.log = lambda text: print(f"   {text}", flush=True)

    def handle(sock: socket.socket) -> None:
        buf = b""
        while True:
            try:
                chunk = sock.recv(4096)
            except OSError:
                break
            if not chunk:
                break
            buf += chunk
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                fw.receive(line.decode("utf-8", "replace"))
        with lock:
            if client["sock"] is sock:
                client["sock"] = None
        print("   host disconnected", flush=True)

    def accept_loop(server: socket.socket) -> None:
        while True:
            sock, addr = server.accept()
            with lock:
                busy = client["sock"] is not None
                if not busy:
                    client["sock"] = sock
            if busy:  # like a serial port: one program at a time
                sock.close()
                print(f"   refused a second host from {addr[0]} (one program per board)", flush=True)
                continue
            print(f"   host connected from {addr[0]}:{addr[1]}", flush=True)
            fw.boot()
            threading.Thread(target=handle, args=(sock,), daemon=True).start()

    server = socket.create_server(("127.0.0.1", port))
    threading.Thread(target=accept_loop, args=(server,), daemon=True).start()

    name = fw.project.name if fw.project else "(no project)"
    print(f"fake board for {name} on 127.0.0.1:{port}")
    print(f"run your host code with ATECH_SIM={port if port != DEFAULT_PORT else 1}")
    _print_list(fw)
    print(HELP)
    while True:
        try:
            line = input("sim> ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if not line:
            continue
        cmd, *args = line.split(maxsplit=3)
        if cmd in ("quit", "exit"):
            break
        elif cmd == "press" and args:
            fw.press(args[0])
        elif cmd == "emit" and len(args) >= 2:
            fw.emit(args[0], _parse_value(args[1]), type=args[2] if len(args) > 2 else None)
        elif cmd == "state":
            for key, value in fw.state.items():
                print(f"   {key} = {json.dumps(value)}")
        elif cmd == "list":
            _print_list(fw)
        elif cmd == "boot":
            fw.boot()
        else:
            print(HELP)


def _print_list(fw: SimFirmware) -> None:
    print("events:  " + (", ".join(f"{k} ({t})" for k, t in fw.events.items()) or "none"))
    print("actions: " + (", ".join(fw.actions) or "none (use --accept for custom ones)"))


def main(argv: Optional[list[str]] = None) -> None:
    ap = argparse.ArgumentParser(prog="python -m simboard", description=__doc__.split("\n\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("serve", help="Run a fake board with an interactive console.")
    p.add_argument("project_dir", nargs="?", help="Folder with project.yaml (optional).")
    p.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"TCP port (default {DEFAULT_PORT}).")
    p.add_argument("--accept", default="", help="Extra action names to accept, comma separated, or '*'.")
    args = ap.parse_args(argv)
    if args.cmd == "serve":
        serve(args.project_dir, args.port, args.accept)


if __name__ == "__main__":
    sys.exit(main())
