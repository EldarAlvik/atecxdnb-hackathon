import socket
import threading

import pytest

import simboard
from atech import ActionRejected

BLINK = "examples/blink"


def test_declared_actions_are_acked_and_recorded():
    board, fw = simboard.local(BLINK)
    board.call("grid_fill", {"r": 255, "g": 0, "b": 0})
    assert fw.state["grid_fill"] == {"r": 255, "g": 0, "b": 0}


def test_unknown_action_is_rejected():
    board, _ = simboard.local(BLINK)
    with pytest.raises(ActionRejected):
        board.call("does_not_exist")


def test_button_press_reaches_host():
    board, fw = simboard.local(BLINK)
    fw.press("button")
    events = board.events(keys=["button"])
    assert [next(events).value, next(events).value] == [1, 0]
    assert board.latest("button").type == "button"


def test_custom_handler_can_answer_with_events():
    board, fw = simboard.local(BLINK)

    @fw.on("ping")
    def _(value):
        fw.emit("pong", value, type="state")

    assert board.query("ping", "hi", reply="pong") == "hi"


def test_boot_report_lists_modules():
    board, _ = simboard.local(BLINK)
    modules = {m["instance"] for m in board.diagnostics()["modules"]}
    assert modules == {"button", "grid"}


def test_tcp_server_round_trip(monkeypatch):
    # The same wiring serve() uses, without its interactive console.
    port = 7791
    started = threading.Event()
    fw = simboard.SimFirmware(BLINK)
    srv = socket.create_server(("127.0.0.1", port))

    def run():
        started.set()
        sock, _ = srv.accept()
        fw.write = lambda line: sock.sendall(line.encode() + b"\n")
        fw.boot()
        buf = b""
        while True:
            chunk = sock.recv(4096)
            if not chunk:
                return
            buf += chunk
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                fw.receive(line.decode())

    threading.Thread(target=run, daemon=True).start()
    started.wait()
    monkeypatch.setenv("ATECH_SIM", f"127.0.0.1:{port}")
    with simboard.connect() as board:
        board.call("grid_clear")
        fw.press("button")
        assert next(board.events(keys=["button"])).value == 1
    srv.close()
