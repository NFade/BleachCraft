#!/usr/bin/env python
"""Dedicated server smoke test for the reiatsu_test mod (no game window, no network exposure beyond 127.0.0.1).

What it does (about 3 to 6 minutes; the first run also remaps Minecraft):
  1. static check: nothing in the `main` source set imports client classes;
  2. writes mod/run-server/{eula.txt,server.properties} (EULA accepted in that run dir only, offline mode, bound to
     127.0.0.1, RCON on loopback with a random password), deletes the old test world;
  3. starts `gradlew runServer` (Fabric dev dedicated server) and waits for "Done";
  4. checks the log: mod listed, registration line, no client-class errors, no ERROR lines, no exceptions;
  5. RCON: /reiatsu voice, info, help (console form) -> commands are registered on the dedicated side;
  6. joins two protocol-level bots (no game client). They behave like Fabric clients (c:version / c:register handshake,
     attachment sync accepted) so the server really encodes our S2C payloads and attachments:
       - bot 1 requests transitions and casts abilities with the real C2S payloads of the mod;
       - bot 2 only watches (second client at protocol level: must get attachment sync of bot 1 and effect events);
  7. persistence: sets reiatsu, disconnects, stops the server cleanly (RCON `stop`), starts it again and checks the value;
  8. prints PASS / FAIL per check and `RESULT: PASS|FAIL` (exit code 0 / 1).

It cannot test rendering, the HUD, keys, sound or a real second game client: see docs/QA_CHECKLIST.md (second client list).

Usage:  python -I tools/server_smoke.py [--quick] [--keep-world] [--port 25599] [--timeout 600]
  --quick       skip the restart/persistence phase
  --port N      server port (RCON uses N-1)
Environment: JAVA_HOME and GRADLE_USER_HOME default to the machine values of this project if unset.
"""
import argparse
import os
import re
import secrets
import shutil
import socket
import struct
import subprocess
import sys
import threading
import time
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD = ROOT / "mod"
RUN = MOD / "run-server"
DEFAULT_JAVA_HOME = r"C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
DEFAULT_GRADLE_HOME = r"D:\gradle-home"

PROTOCOL_1_21_1 = 767
S2C_CHANNELS = ["fabric:attachment_sync_v1", "reiatsu_test:action_result", "reiatsu_test:effect_event", "reiatsu_test:entity_fx"]
ATTACHMENTS = ["reiatsu_test:zanpakuto", "reiatsu_test:reiatsu", "reiatsu_test:cooldowns"]
# error patterns that always fail the run (client classes on a dedicated server, mixin and registration failures)
BAD_LOG = re.compile(
    r"NoClassDefFoundError|ClassNotFoundException|Cannot load class|RuntimeException: Cannot load|Mixin apply failed|"
    r"InvalidInjectionException|MixinTransformerError|IncompatibleClassChangeError|NoSuchMethodError|NoSuchFieldError|"
    r"Exception in thread|Encountered exception|Failed to (load|register|initialize)|net\.minecraft\.client|"
    r"Client does not support the syncable attachments|Unknown payload|EncoderException|DecoderException")

results = []


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    print(("PASS  " if ok else "FAIL  ") + name + (("  -> " + str(detail)) if detail != "" else ""), flush=True)
    return bool(ok)


# ---------------------------------------------------------------------------------------------------- wire helpers
def varint(n):
    out = bytearray()
    n &= 0xFFFFFFFF
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def mc_string(s):
    b = s.encode("utf-8")
    return varint(len(b)) + b


class Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def byte(self):
        v = self.d[self.i]
        self.i += 1
        return v

    def varint(self):
        n = 0
        shift = 0
        while True:
            b = self.byte()
            n |= (b & 0x7F) << shift
            if not b & 0x80:
                return n
            shift += 7

    def string(self):
        n = self.varint()
        s = self.d[self.i:self.i + n].decode("utf-8", "replace")
        self.i += n
        return s

    def rest(self):
        return self.d[self.i:]


# ---------------------------------------------------------------------------------------------------- RCON
class Rcon:
    def __init__(self, port, password, timeout=30):
        self.s = socket.create_connection(("127.0.0.1", port), timeout)
        self.s.settimeout(timeout)
        self.n = 0
        self._send(3, password)
        _, ident, _ = self._read()
        if ident == -1:
            raise RuntimeError("RCON authentication failed")

    def _send(self, kind, text):
        self.n += 1
        body = struct.pack("<ii", self.n, kind) + text.encode("utf-8") + b"\0\0"
        self.s.sendall(struct.pack("<i", len(body)) + body)

    def _recv_exact(self, n):
        data = b""
        while len(data) < n:
            chunk = self.s.recv(n - len(data))
            if not chunk:
                raise ConnectionError("RCON closed")
            data += chunk
        return data

    def _read(self):
        (length,) = struct.unpack("<i", self._recv_exact(4))
        data = self._recv_exact(length)
        ident, kind = struct.unpack("<ii", data[:8])
        return kind, ident, data[8:-2].decode("utf-8", "replace")

    def cmd(self, text):
        self._send(2, text)
        return self._read()[2]

    def close(self):
        try:
            self.s.close()
        except OSError:
            pass


# ---------------------------------------------------------------------------------------------------- protocol bot
class Bot:
    """Minimal 1.21.1 (protocol 767) client that mimics the Fabric networking handshake. No world, no rendering."""

    def __init__(self, name, port):
        self.name = name
        self.port = port
        self.sock = None
        self.state = "handshake"
        self.threshold = -1
        self.joined = threading.Event()
        self.closed = threading.Event()
        self.error = None
        self.counts = {}
        self.lock = threading.Lock()
        self.sent_register = False
        self.last_payload = {}

    # --- io
    def connect(self):
        self.sock = socket.create_connection(("127.0.0.1", self.port), 10)
        self.sock.settimeout(60)
        self.send_raw(0x00, varint(PROTOCOL_1_21_1) + mc_string("127.0.0.1") + struct.pack(">H", self.port) + varint(2))
        self.state = "login"
        uuid = bytearray(__import__("hashlib").md5(("OfflinePlayer:" + self.name).encode()).digest())
        uuid[6] = (uuid[6] & 0x0F) | 0x30
        uuid[8] = (uuid[8] & 0x3F) | 0x80
        self.send_raw(0x00, mc_string(self.name) + bytes(uuid))
        threading.Thread(target=self._loop, daemon=True, name="bot-" + self.name).start()

    def send_raw(self, pid, data=b""):
        body = varint(pid) + data
        if self.threshold >= 0:
            body = varint(0) + body  # uncompressed (our packets are far below the threshold)
        self.sock.sendall(varint(len(body)) + body)

    def _read_varint(self):
        n = 0
        shift = 0
        while True:
            b = self.sock.recv(1)
            if not b:
                raise ConnectionError("closed")
            n |= (b[0] & 0x7F) << shift
            if not b[0] & 0x80:
                return n
            shift += 7

    def _read_packet(self):
        length = self._read_varint()
        data = b""
        while len(data) < length:
            chunk = self.sock.recv(min(65536, length - len(data)))
            if not chunk:
                raise ConnectionError("closed")
            data += chunk
        if self.threshold >= 0:
            r = Reader(data)
            size = r.varint()
            data = zlib.decompress(r.rest()) if size > 0 else r.rest()
        return data

    def _loop(self):
        try:
            while not self.closed.is_set():
                data = self._read_packet()
                r = Reader(data)
                pid = r.varint()
                getattr(self, "_on_" + self.state)(pid, r)
        except Exception as e:  # socket closed by us or by the server
            if not self.closed.is_set():
                self.error = self.error or ("connection ended: %r" % (e,))
        finally:
            self.closed.set()

    # --- state handlers
    def _on_login(self, pid, r):
        if pid == 0x00:
            self.error = "login disconnect: " + r.string()
            self.closed.set()
        elif pid == 0x03:
            self.threshold = r.varint()
        elif pid == 0x02:
            self.send_raw(0x03)
            self.state = "config"

    def _payload_c2s(self, ident, data=b""):
        self.send_raw(0x02 if self.state == "config" else 0x12, mc_string(ident) + data)

    def _on_config(self, pid, r):
        if pid == 0x01:  # custom payload
            ident = r.string()
            data = r.rest()
            with self.lock:
                self.counts["cfg:" + ident] = self.counts.get("cfg:" + ident, 0) + 1
            if ident == "minecraft:register" and not self.sent_register:
                self.sent_register = True
                chans = ["c:version", "c:register", "fabric:accepted_attachments_v1"]
                self._payload_c2s("minecraft:register", "\0".join(chans).encode("ascii"))
            elif ident == "c:version":
                self._payload_c2s("c:version", varint(1) + varint(1))
            elif ident == "c:register":
                rr = Reader(data)
                rr.varint()
                phase = rr.string()
                if phase == "play":
                    body = varint(1) + mc_string("play") + varint(len(S2C_CHANNELS)) + b"".join(mc_string(c) for c in S2C_CHANNELS)
                    self._payload_c2s("c:register", body)
            elif ident == "fabric:accepted_attachments_v1":
                self._payload_c2s("fabric:accepted_attachments_v1",
                                  varint(len(ATTACHMENTS)) + b"".join(mc_string(a) for a in ATTACHMENTS))
        elif pid == 0x04:  # keep alive
            self.send_raw(0x04, r.rest()[:8])
        elif pid == 0x05:  # ping
            self.send_raw(0x05, r.rest()[:4])
        elif pid == 0x0E:  # known packs -> we know none
            self.send_raw(0x07, varint(0))
        elif pid == 0x03:  # finish configuration
            self.send_raw(0x03)
            self.state = "play"
        elif pid == 0x02:
            self.error = "configuration disconnect (text component is NBT)"
            self.closed.set()

    def _on_play(self, pid, r):
        if pid == 0x26:  # keep alive
            self.send_raw(0x18, r.rest()[:8])
        elif pid == 0x35:  # ping
            self.send_raw(0x27, r.rest()[:4])
        elif pid == 0x2B:  # join game
            self.joined.set()
        elif pid == 0x0C:  # chunk batch finished: acknowledge, otherwise the server never tracks entities for us
            self.send_raw(0x08, struct.pack(">f", 9.0))
        elif pid == 0x40:  # synchronize player position: confirm the teleport (x y z doubles, yaw pitch floats, flags byte)
            rest = r.rest()
            self.send_raw(0x00, varint(Reader(rest[33:]).varint()))
        elif pid == 0x1D:
            self.error = "play disconnect"
            self.closed.set()
        elif pid == 0x19:  # custom payload
            ident = r.string()
            with self.lock:
                self.counts[ident] = self.counts.get(ident, 0) + 1
                self.last_payload[ident] = r.rest()

    # --- api
    def count(self, ident):
        with self.lock:
            return self.counts.get(ident, 0)

    def request_transition(self, target, seq):
        self._payload_c2s("reiatsu_test:request_transition", bytes([target, 0]) + varint(seq))

    def cast(self, ability, seq):
        self._payload_c2s("reiatsu_test:cast_ability", bytes([ability, 0]) + varint(seq))

    def close(self):
        self.closed.set()
        try:
            self.sock.close()
        except OSError:
            pass


# ---------------------------------------------------------------------------------------------------- server
class Server:
    def __init__(self, port, env):
        self.port = port
        self.rcon_port = port - 1
        self.env = env
        self.lines = []
        self.proc = None
        self.rcon = None
        self.password = secrets.token_hex(8)
        self.lock = threading.Lock()

    def prepare(self, fresh_world):
        RUN.mkdir(parents=True, exist_ok=True)
        (RUN / "eula.txt").write_text("eula=true\n", encoding="ascii")  # local test only, in this run dir only
        props = {
            "online-mode": "false", "server-ip": "127.0.0.1", "server-port": self.port, "enable-rcon": "true",
            "rcon.port": self.rcon_port, "rcon.password": self.password, "broadcast-rcon-to-ops": "false",
            "enable-query": "false", "enforce-secure-profile": "false", "generate-structures": "false",
            "spawn-monsters": "false", "spawn-animals": "false", "spawn-npcs": "false", "view-distance": "6",
            "simulation-distance": "2", "max-tick-time": "-1", "max-players": "4", "spawn-protection": "0",
            "level-seed": "12345", "motd": "reiatsu smoke test", "sync-chunk-writes": "false",
        }
        (RUN / "server.properties").write_text("".join("%s=%s\n" % kv for kv in props.items()), encoding="ascii")
        if fresh_world:
            shutil.rmtree(RUN / "world", ignore_errors=True)

    def start(self):
        self.lines = []
        self.proc = subprocess.Popen(["cmd", "/c", str(MOD / "gradlew.bat"), "runServer", "--console=plain"], cwd=str(MOD), env=self.env,
                                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8",
                                     errors="replace")
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        for line in self.proc.stdout:
            with self.lock:
                self.lines.append(line.rstrip("\r\n"))

    def snapshot(self):
        with self.lock:
            return list(self.lines)

    def wait_for(self, pattern, timeout):
        rx = re.compile(pattern)
        end = time.time() + timeout
        seen = 0
        while time.time() < end:
            lines = self.snapshot()
            for ln in lines[seen:]:
                if rx.search(ln):
                    return ln
            seen = len(lines)
            if self.proc.poll() is not None:
                return None
            time.sleep(0.3)
        return None

    def connect_rcon(self):
        end = time.time() + 30
        while time.time() < end:
            try:
                self.rcon = Rcon(self.rcon_port, self.password)
                return True
            except (OSError, RuntimeError):
                time.sleep(0.5)
        return False

    def rc(self, command):
        out = self.rcon.cmd(command)
        print("      rcon> %s  =>  %s" % (command, out.replace("\n", " | ")[:200]), flush=True)
        return out

    def stop(self, timeout=90):
        """Clean stop through RCON `stop`; returns True if the gradle process ended with exit code 0."""
        try:
            if self.rcon:
                self.rcon.cmd("stop")
                self.rcon.close()
        except OSError:
            pass
        try:
            code = self.proc.wait(timeout)
            return code == 0
        except subprocess.TimeoutExpired:
            self.kill()
            return False

    def kill(self):
        """Last resort: end only the processes of THIS test (our gradle client and the java server in run-server)."""
        if self.proc and self.proc.poll() is None:
            subprocess.run(["taskkill", "/T", "/F", "/PID", str(self.proc.pid)], capture_output=True)
        ps = ("Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*run-server*' -and "
              "$_.CommandLine -like '*KnotServer*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }")
        subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True)


# ---------------------------------------------------------------------------------------------------- checks
def static_check():
    bad = []
    pat = re.compile(r"net\.minecraft\.client|net\.fabricmc\.fabric\.api\.client|net\.fabricmc\.api\.Environment\b|EnvType\.CLIENT|MinecraftClient")
    for p in (MOD / "src" / "main").rglob("*.java"):
        for n, ln in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
            if pat.search(ln):
                bad.append("%s:%d" % (p.relative_to(MOD), n))
    check("static: main source set has no client class references", not bad, ", ".join(bad[:5]))


def port_free(port):
    s = socket.socket()
    try:
        s.bind(("127.0.0.1", port))
        return True
    except OSError:
        return False
    finally:
        s.close()


def log_checks(lines, label):
    text = "\n".join(lines)
    check(label + "log: mod listed in the loader table", re.search(r"^\s*- reiatsu_test \S+", text, re.M) is not None)
    check(label + "log: registration line (items, attachments, payloads)",
          "attachments and payloads" in text and "registered items reiatsu_test:sode_no_shirayuki" in text)
    errs = [ln for ln in lines if re.search(r"/ERROR\]", ln)]
    check(label + "log: no ERROR lines", not errs, errs[:2])
    bad = [ln for ln in lines if BAD_LOG.search(ln)]
    check(label + "log: no client-class / mixin / codec errors", not bad, bad[:2])
    stack = [ln for ln in lines if re.match(r"\s+at [\w.$]+\(", ln)]
    check(label + "log: no stack traces", not stack, stack[:2])


def wait_until(fn, timeout, step=0.2):
    end = time.time() + timeout
    while time.time() < end:
        if fn():
            return True
        time.sleep(step)
    return fn()


def info_of(srv, bot_name):
    return srv.rc("execute as %s run reiatsu info" % bot_name)


def phase_one(srv, port):
    rc = srv.rc
    check("rcon: /reiatsu voice answers (bridge is off on dedicated)", "off" in rc("reiatsu voice").lower())
    check("rcon: /reiatsu info is registered (needs a player from the console)", "player is required" in rc("reiatsu info"))
    check("rcon: /reiatsu help lists the subcommands", "/reiatsu info" in rc("help reiatsu") and "state" in rc("help reiatsu"))

    b1, b2 = Bot("SmokeBot", port), Bot("SmokeWatcher", port)
    try:
        b1.connect()
        ok1 = b1.joined.wait(40)
        check("bot 1 joined (login, configuration with Fabric handshake, play)", ok1, b1.error or "")
        if not ok1:
            return b1, b2
        b2.connect()
        ok2 = b2.joined.wait(40)
        check("bot 2 (second client at protocol level) joined", ok2, b2.error or "")
        sess = srv.wait_for(r"\[reiatsu\] session for SmokeBot started", 10)
        check("server log: zanpakuto session started on join", sess is not None, sess or "")
        check("bot 1 received attachment sync (fabric:attachment_sync_v1)",
              wait_until(lambda: b1.count("fabric:attachment_sync_v1") >= 1, 8), dict(b1.counts))
        check("players online on the server", "SmokeBot" in rc("list") and "SmokeWatcher" in rc("list"))

        # players that never move are not re-evaluated by the entity tracker: a teleport next to bot 1 starts the tracking
        rc("tp SmokeWatcher SmokeBot")
        time.sleep(2.0)
        rc("gamemode survival SmokeBot")
        out = rc("give SmokeBot reiatsu_test:sode_no_shirayuki")
        check("rcon: give mod item works on the dedicated server", "Gave" in out, out)
        info = info_of(srv, "SmokeBot")
        check("/reiatsu info as player: state=SEALED reiatsu full", "state=SEALED" in info and "reiatsu=100.0/100.0" in info, info)

        # C2S request_transition SEALED -> BASE with the sword in the main hand
        a0 = b1.count("reiatsu_test:action_result")
        b1.request_transition(1, 1)
        check("C2S request_transition is decoded and answered (action_result S2C)",
              wait_until(lambda: b1.count("reiatsu_test:action_result") > a0, 8))
        line = srv.wait_for(r"SmokeBot request BASE \(held RUKIA, KEY\): ", 5)
        check("server log: request BASE accepted path", line is not None and "OK" in (line or "").upper(), line or "")
        check("state is BASE after the payload", "state=BASE" in info_of(srv, "SmokeBot"))

        # abilities through the real payload path (state forced with the dev command)
        sync_before = b2.count("fabric:attachment_sync_v1")
        rc("execute as SmokeBot run reiatsu state shikai rukia")
        check("watcher (bot 2) got the attachment sync of bot 1's state change",
              wait_until(lambda: b2.count("fabric:attachment_sync_v1") > sync_before, 8), dict(b2.counts))
        fx_before = b1.count("reiatsu_test:effect_event")
        b1.cast(1, 2)  # TSUKISHIRO
        line = srv.wait_for(r"SmokeBot cast TSUKISHIRO \(held RUKIA, KEY\): ", 5)
        check("C2S cast_ability TSUKISHIRO reaches the executor", line is not None, line or "")
        check("effect_event S2C encoded and sent to the caster", wait_until(lambda: b1.count("reiatsu_test:effect_event") > fx_before, 8))
        check("effect_event S2C also sent to the watcher (second client)", wait_until(lambda: b2.count("reiatsu_test:effect_event") >= 1, 8),
              dict(b2.counts))
        time.sleep(4.0)  # let the scheduled phases of the dance run on the server

        rc("execute as SmokeBot run reiatsu full")
        rc("execute as SmokeBot run reiatsu cooldowns clear")
        rc("execute as SmokeBot run reiatsu state bankai rukia")
        b1.cast(4, 3)  # ABSOLUTE_ZERO
        line = srv.wait_for(r"SmokeBot cast ABSOLUTE_ZERO \(held RUKIA, KEY\): ", 5)
        check("C2S cast_ability ABSOLUTE_ZERO reaches the executor", line is not None, line or "")
        time.sleep(4.0)

        rc("item replace entity SmokeBot weapon.mainhand with reiatsu_test:senbonzakura")
        for state, abil, name, seq in (("shikai", 5, "MODE_ATTACK", 4), ("shikai", 6, "MODE_BARRIER", 5),
                                       ("bankai", 7, "SCATTER", 6), ("bankai", 8, "HAKUTEIKEN", 7)):
            rc("execute as SmokeBot run reiatsu full")
            rc("execute as SmokeBot run reiatsu cooldowns clear")
            rc("execute as SmokeBot run reiatsu state %s byakuya" % state)
            b1.cast(abil, seq)
            line = srv.wait_for(r"SmokeBot cast %s \(held BYAKUYA, KEY\): " % name, 5)
            check("C2S cast_ability %s reaches the executor" % name, line is not None, line or "")
            time.sleep(1.5)
        time.sleep(3.0)
        rc("execute as SmokeBot run reiatsu state sealed")
        time.sleep(1.0)
        info = info_of(srv, "SmokeBot")
        check("seal rolls back temporary blocks (tempBlocks=0)", "tempBlocks=0" in info, info)
        check("bots still connected (no kick, no timeout)", not b1.closed.is_set() and not b2.closed.is_set(), b1.error or b2.error or "")

        rc("execute as SmokeBot run reiatsu set 37.5")
        time.sleep(0.5)
    finally:
        b1.close()
        b2.close()
    time.sleep(1.5)
    return b1, b2


def phase_two(srv, port):
    bot = Bot("SmokeBot", port)
    try:
        bot.connect()
        check("restart: bot rejoined", bot.joined.wait(40), bot.error or "")
        srv.wait_for(r"\[reiatsu\] session for SmokeBot started", 10)
        info = info_of(srv, "SmokeBot")
        m = re.search(r"reiatsu=([\d.]+)/", info)
        val = float(m.group(1)) if m else -1
        check("persistence: reiatsu attachment survived stop/start (set 37.5)", 37.0 <= val < 100.0, info)
    finally:
        bot.close()


def main():
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--quick", action="store_true")
    ap.add_argument("--port", type=int, default=25599)
    ap.add_argument("--timeout", type=int, default=600, help="seconds to wait for the server to start")
    args = ap.parse_args()

    env = dict(os.environ)
    env.setdefault("JAVA_HOME", DEFAULT_JAVA_HOME)
    env.setdefault("GRADLE_USER_HOME", DEFAULT_GRADLE_HOME)
    t0 = time.time()

    static_check()
    if not (port_free(args.port) and port_free(args.port - 1)):
        check("ports %d and %d are free" % (args.port, args.port - 1), False, "something is already listening there")
        return finish(t0)

    srv = Server(args.port, env)
    srv.prepare(fresh_world=True)
    started = False
    try:
        srv.start()
        done = srv.wait_for(r'Done \(.*For help, type "help"', args.timeout)
        started = check("server started (dedicated, Fabric dev environment)", done is not None, done or "see log tail below")
        if started:
            check("rcon reachable on loopback", srv.connect_rcon())
            time.sleep(1.0)
            phase_one(srv, args.port)
            log_checks(srv.snapshot(), "phase 1 ")
        clean = srv.stop()
        check("clean stop (rcon stop, gradle exit code 0)", clean)
        stopped = srv.snapshot()
        check("log: world saved before exit", any("All dimensions are saved" in ln for ln in stopped))
        if started and not args.quick:
            srv = Server(args.port, env)
            srv.prepare(fresh_world=False)
            srv.start()
            done = srv.wait_for(r'Done \(.*For help, type "help"', args.timeout)
            check("restart: server started again with the saved world", done is not None, done or "")
            if done:
                srv.connect_rcon()
                time.sleep(1.0)
                phase_two(srv, args.port)
                log_checks(srv.snapshot(), "phase 2 ")
            check("restart: clean stop", srv.stop())
    except Exception as e:  # keep the run diagnosable
        check("unexpected exception in the smoke script", False, repr(e))
    finally:
        srv.kill() if srv.proc and srv.proc.poll() is None else None
        try:
            (RUN / "smoke_last.log").write_text("\n".join(srv.snapshot()), encoding="utf-8")
        except OSError:
            pass
    if not all(ok for _, ok in results):
        print("--- last server log lines ---")
        print("\n".join(srv.snapshot()[-40:]))
    return finish(t0)


def finish(t0):
    failed = [n for n, ok in results if not ok]
    print("%d checks, %d failed, %.0f s" % (len(results), len(failed), time.time() - t0))
    print("RESULT: " + ("PASS" if not failed else "FAIL"))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
