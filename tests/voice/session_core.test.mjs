// Tests for the recognition session controller embedded in voice-bridge/index.html (no browser, no microphone).
// Run: node --test tests/voice/session_core.test.mjs
// The controller and the stats helper are cut out of the page between their BEGIN/END markers and driven with a fake
// SpeechRecognition and a manual clock, so the race and fallback rules are checked deterministically.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const html = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "..", "..", "voice-bridge", "index.html"), "utf8");
function cut(name) {
  const m = html.match(new RegExp("/\\* =====\\s*" + name + "-BEGIN[\\s\\S]*?\\*/([\\s\\S]*?)/\\* =====\\s*" + name + "-END"));
  assert.ok(m, "markers " + name + " present");
  return m[1];
}
const { createRecognizer, createStats } = new Function(cut("SESSION-CORE") + "\n" + cut("STATS") + "\nreturn {createRecognizer, createStats};")();

// ---------------------------------------------------------------- fakes
function makeWorld(opts = {}) {
  const w = { t: 0, timers: [], sessions: [], events: [], finals: [], interims: [], notices: [], states: [], errors: [], fatals: [], inputs: [], loud: false, nextTimer: 1 };
  w.advance = (ms) => {
    const end = w.t + ms;
    for (;;) {
      const due = w.timers.filter((x) => x.at <= end).sort((a, b) => a.at - b.at || a.id - b.id)[0];
      if (!due) break;
      w.timers.splice(w.timers.indexOf(due), 1);
      w.t = due.at;
      due.fn();
    }
    w.t = end;
  };
  class FakeSR {
    constructor() {
      this.started = false; this.aborted = false; this.stopped = false; this.trackArg = undefined;
      this.silentAbort = !!opts.noEndOnAbort; this.processLocally = undefined;
      w.sessions.push(this);
    }
    start(track) {
      if (opts.startThrowsOnTrack && track) { const e = new Error("x"); e.name = "InvalidStateError"; throw e; }
      if (opts.startThrows) { const e = new Error("x"); e.name = opts.startThrows; throw e; }
      this.started = true; this.trackArg = track;
    }
    abort() { this.aborted = true; if (!this.silentAbort) w.timers.push({ at: w.t + 5, id: w.nextTimer++, fn: () => this.onend && this.onend() }); }
    stop() { this.stopped = true; w.timers.push({ at: w.t + 20, id: w.nextTimer++, fn: () => this.onend && this.onend() }); }
    // test helpers
    audio() { this.onstart && this.onstart(); this.onaudiostart && this.onaudiostart(); }
    say(text, isFinal, index = 0, prior = []) {
      const results = prior.concat([Object.assign([{ transcript: text }], { isFinal })]);
      this.onresult && this.onresult({ resultIndex: index, results });
    }
    fail(code) { this.onerror && this.onerror({ error: code }); }
    end() { this.onend && this.onend(); }
  }
  w.SR = FakeSR;
  w.env = {
    SR: FakeSR,
    now: () => w.t,
    setTimeout: (fn, ms) => { const id = w.nextTimer++; w.timers.push({ at: w.t + ms, id, fn }); return id; },
    clearTimeout: (id) => { const i = w.timers.findIndex((x) => x.id === id); if (i >= 0) w.timers.splice(i, 1); },
    checkLocal: opts.local ? (l) => Promise.resolve(opts.local[l] || "unavailable") : undefined,
    probeTrack: opts.track ? () => opts.track !== "unsupported" : undefined,
    openInput: opts.track ? () => {
      const inp = { gain: 0, closed: false, track: { id: "trk" + w.inputs.length }, setGain(v) { inp.gain = v; }, close() { inp.closed = true; } };
      w.inputs.push(inp); return inp;
    } : undefined
  };
  w.hooks = {
    onEvent: (n, d) => w.events.push([n, d]),
    onState: (s) => w.states.push(s),
    onNotice: (m) => w.notices.push(m),
    onInterim: (t) => w.interims.push(t),
    onFinal: (t, m) => w.finals.push([t, m]),
    onError: (c) => w.errors.push(c),
    onFatal: (c) => w.fatals.push(c),
    loudSince: () => w.loud
  };
  w.hooks.onRestart = (i) => (w.restartsSeen = w.restartsSeen || []).push(i);
  w.rz = createRecognizer(w.env, w.hooks, opts.cfg);
  w.live = () => w.sessions.filter((s) => s.started && !s.aborted && !s.stopped && !s.ended);
  w.ev = (n) => w.events.filter((e) => e[0] === n).map((e) => e[1]);
  return w;
}
const tick = async () => { await Promise.resolve(); await Promise.resolve(); };

// ---------------------------------------------------------------- always listening
test("always mode: start, results are delivered, a natural end restarts at once", () => {
  const w = makeWorld();
  w.rz.start("always");
  w.advance(0);
  assert.equal(w.sessions.length, 1);
  w.sessions[0].audio();
  w.sessions[0].say("chire", false);
  w.sessions[0].say("chire senbonzakura", true);
  assert.deepEqual(w.interims, ["chire"]);
  assert.equal(w.finals[0][0], "chire senbonzakura");
  w.sessions[0].end();           // Chrome ends continuous sessions now and then
  w.advance(0);
  assert.equal(w.sessions.length, 2, "restarted without delay");
  w.advance(100);
  w.sessions[1].audio();
  const ses = w.ev("session");
  assert.equal(ses.length, 2);
  assert.equal(ses[1].gapMs, 100, "dead time between sessions is measured");
});

test("race: language change waits for the old session's end before starting the new one", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  w.sessions[0].audio();
  w.rz.setLang("ru-RU");
  assert.equal(w.sessions.length, 1, "no overlap: the replacement is queued");
  assert.ok(w.sessions[0].aborted);
  assert.ok(w.rz.status().draining);
  w.advance(5);                  // the fake emits end 5 ms after abort
  assert.equal(w.sessions.length, 2);
  assert.equal(w.sessions[1].lang, "ru-RU");
  assert.equal(w.ev("drain")[0].how, "end");
});

test("race: an engine that never sends end is waited for at most 1 s", () => {
  const w = makeWorld({ noEndOnAbort: true });
  w.rz.start("always"); w.advance(0);
  w.sessions[0].audio();
  w.rz.setLang("ja-JP");
  w.advance(999);
  assert.equal(w.sessions.length, 1, "still waiting at 999 ms");
  w.advance(1);
  assert.equal(w.sessions.length, 2, "started at the 1 s cap");
  assert.equal(w.ev("drain")[0].how, "timeout");
});

test("race: results and errors of a retired session never reach the page", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  const old = w.sessions[0]; old.audio();
  w.rz.setLang("ru-RU"); w.advance(5);
  old.say("stale words", true); old.fail("network");
  assert.equal(w.finals.length, 0);
  assert.equal(w.errors.length, 0);
});

test("race: several quick language switches end with exactly one live session", () => {
  const w = makeWorld({ noEndOnAbort: true });
  w.rz.start("always"); w.advance(0);
  for (const l of ["ru-RU", "ja-JP", "en-US", "ru-RU"]) { w.rz.setLang(l); w.advance(50); }
  w.advance(1100);
  const live = w.sessions.filter((s) => s.started && !s.aborted);
  assert.equal(live.length, 1);
  assert.equal(live[0].lang, "ru-RU");
});

test("stop cancels a pending restart", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  w.sessions[0].audio(); w.sessions[0].end();
  w.rz.stop(); w.advance(5000);
  assert.equal(w.sessions.length, 1);
  assert.equal(w.states.at(-1), "off");
});

// ---------------------------------------------------------------- errors and fallback
test("on-device recognition: used when installed, dropped for the language after an error", async () => {
  const w = makeWorld({ local: { "en-US": "available" } });
  w.rz.setOptions({ local: true });
  w.rz.start("always");
  await tick(); w.advance(0);
  assert.equal(w.sessions.length, 1);
  assert.equal(w.sessions[0].processLocally, true);
  w.sessions[0].audio();
  w.sessions[0].fail("language-not-supported");
  w.advance(10);
  assert.equal(w.sessions.length, 2);
  assert.equal(w.sessions[1].processLocally, false, "browser service fallback");
  assert.ok(w.notices.length >= 1);
  assert.deepEqual(w.rz.status().localFailed, { "en-US": true });
});

test("on-device recognition: a language pack that is not installed is never requested", async () => {
  const w = makeWorld({ local: { "ja-JP": "downloadable" } });
  w.rz.setOptions({ local: true }); w.rz.setLang("ja-JP");
  w.rz.start("always"); await tick(); w.advance(0);
  assert.equal(w.sessions[0].processLocally, false);
});

test("on-device check that never answers cannot block the start (1 s cap)", () => {
  const w = makeWorld();
  w.env.checkLocal = () => new Promise(() => {});
  w.rz.setOptions({ local: true });
  w.rz.start("always");
  w.advance(999); assert.equal(w.sessions.length, 0);
  w.advance(2); assert.equal(w.sessions.length, 1);
});

test("permission denied is fatal and does not restart", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  w.sessions[0].fail("not-allowed"); w.sessions[0].end();
  w.advance(10000);
  assert.deepEqual(w.fatals, ["not-allowed"]);
  assert.equal(w.sessions.length, 1);
});

test("network errors back off 250, 500, 1000 ... and give up after 6 in a row", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  const gaps = [];
  for (let i = 0; i < 6 && !w.fatals.length; i++) {
    const s = w.sessions.at(-1);
    s.fail("network"); const before = w.t; s.end();
    if (w.fatals.length) break;
    let d = 0; const n = w.sessions.length;
    while (w.sessions.length === n && d < 10000) { w.advance(50); d += 50; }
    gaps.push(d);
  }
  assert.deepEqual(gaps.slice(0, 3), [250, 500, 1000]);
  assert.deepEqual(w.fatals, ["too-many-failures"]);
});

test("a session that dies before any audio does not spin: it counts as a failure", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  w.sessions[0].end();
  assert.equal(w.rz.status().failStreak, 1);
  w.advance(100); assert.equal(w.sessions.length, 1, "backoff, not an instant respawn");
  w.advance(200); assert.equal(w.sessions.length, 2);
});

test("a constructor/start failure is reported and retried, not thrown", () => {
  const w = makeWorld({ startThrows: "InvalidStateError" });
  w.rz.start("always");
  assert.equal(w.errors.length, 1);
  w.advance(300);
  assert.ok(w.sessions.length >= 2);
});

// ---------------------------------------------------------------- push to talk, prepared session
test("hold to talk with a muted track: press opens the gain, idle words are never kept", () => {
  const w = makeWorld({ track: true });
  w.rz.setOptions({ prewarm: true });
  w.rz.start("ptt"); w.advance(0);
  const s = w.sessions[0];
  assert.equal(s.trackArg.id, "trk0", "recognition runs on the supplied track");
  assert.equal(w.inputs[0].gain, 0, "muted until pressed");
  s.audio();
  assert.equal(w.states.at(-1), "ready");
  s.say("idle chatter", true);
  assert.equal(w.finals.length, 0);
  w.advance(300);
  assert.ok(w.rz.press());
  assert.equal(w.inputs[0].gain, 1);
  const ev = w.ev("arm")[0];
  assert.equal(ev.readyOnPress, true);
  s.say("chire", false, 1, [Object.assign([{ transcript: "idle chatter" }], { isFinal: true })]);
  s.say("chire senbonzakura", true, 1, [Object.assign([{ transcript: "idle chatter" }], { isFinal: true })]);
  assert.equal(w.finals.length, 1);
  assert.equal(w.finals[0][0], "chire senbonzakura", "the idle result before the press is skipped");
  assert.equal(w.finals[0][1].pressToReadyMs, 0);
  assert.equal(w.finals[0][1].readyOnPress, true);
});

test("hold to talk: release mutes after the tail, the final ends the press and the next session is prepared", () => {
  const w = makeWorld({ track: true });
  w.rz.setOptions({ prewarm: true });
  w.rz.start("ptt"); w.advance(0);
  const s = w.sessions[0]; s.audio();
  w.rz.press(); w.advance(100);
  s.say("bankai", false);
  w.rz.release();
  assert.equal(w.inputs[0].gain, 1, "still open during the 500 ms tail");
  w.advance(500);
  assert.equal(w.inputs[0].gain, 0);
  s.say("bankai", true);
  assert.equal(w.finals.length, 1);
  assert.equal(w.rz.status().pressed, false);
  assert.ok(s.aborted, "the used session is retired");
  w.advance(10);
  assert.equal(w.sessions.length, 2, "next session prepared");
  assert.equal(w.sessions[1].prepared === undefined, true);
  assert.equal(w.inputs[1].gain, 0);
});

test("hold to talk: pressing while the prepared session is still starting counts press-to-ready", () => {
  const w = makeWorld({ track: true });
  w.rz.setOptions({ prewarm: true });
  w.rz.start("ptt"); w.advance(0);
  w.advance(100);
  w.rz.press();
  assert.equal(w.ev("arm")[0].readyOnPress, false);
  w.advance(180);
  w.sessions[0].audio();
  w.sessions[0].say("x y", true);
  assert.equal(w.finals[0][1].pressToReadyMs, 180);
  assert.equal(w.finals[0][1].readyOnPress, false);
});

test("hold to talk: speech already under way when pressed gets a fresh session", () => {
  const w = makeWorld();            // plain microphone, no track
  w.rz.start("ptt"); w.advance(0);
  const s = w.sessions[0]; s.audio();
  s.onspeechstart(); s.say("hello there how", false);
  w.rz.press();
  assert.ok(s.aborted, "the half-heard sentence cannot leak into the command");
  w.advance(5);
  assert.equal(w.sessions.length, 2);
  w.sessions[1].audio();
  w.sessions[1].say("chire", true);
  assert.equal(w.finals[0][0], "chire");
});

test("hold to talk without a track: release calls stop() and the final ends the press", () => {
  const w = makeWorld();
  w.rz.start("ptt"); w.advance(0);
  const s = w.sessions[0]; s.audio();
  w.rz.press(); w.rz.release(); w.advance(500);
  assert.ok(s.stopped);
  s.say("seal", true);
  assert.equal(w.finals[0][0], "seal");
});

test("hold to talk: no final within 1.5 s promotes the last interim, once", () => {
  const w = makeWorld({ track: true });
  w.rz.setOptions({ prewarm: true });
  w.rz.start("ptt"); w.advance(0);
  const s = w.sessions[0]; s.audio();
  w.rz.press(); s.say("absolute zero", false);
  w.rz.release(); w.advance(500 + 1500);
  assert.equal(w.finals.length, 1);
  assert.equal(w.finals[0][0], "absolute zero");
  assert.equal(w.finals[0][1].promoted, true);
});

test("pre-warm falls back to the plain microphone when the track overload is missing", () => {
  const w = makeWorld({ track: "unsupported" });
  w.rz.setOptions({ prewarm: true });
  w.rz.start("ptt"); w.advance(0);
  assert.equal(w.sessions[0].trackArg, undefined);
  assert.equal(w.rz.status().trackOk, false);
});

test("pre-warm falls back when start(track) throws, without losing the session", () => {
  const w = makeWorld({ track: true, startThrowsOnTrack: true });
  w.rz.setOptions({ prewarm: true });
  w.rz.start("ptt"); w.advance(0);
  assert.equal(w.live().length, 1);
  assert.equal(w.live()[0].trackArg, undefined);
  assert.equal(w.rz.status().trackOk, false);
  assert.ok(w.notices.length >= 1);
});

test("pre-warm: an error on the track path switches to the plain microphone", () => {
  const w = makeWorld({ track: true });
  w.rz.setOptions({ prewarm: true });
  w.rz.start("ptt"); w.advance(0);
  w.sessions[0].audio(); w.sessions[0].fail("audio-capture");
  w.advance(10);
  assert.equal(w.rz.status().trackOk, false);
  assert.equal(w.live().length, 1);
  assert.equal(w.live()[0].trackArg, undefined);
});

test("pre-warm: sound but no text for a whole press disables the track for good", () => {
  const w = makeWorld({ track: true });
  w.rz.setOptions({ prewarm: true });
  w.rz.start("ptt"); w.advance(0);
  w.sessions[0].audio();
  w.rz.press(); w.loud = true;
  w.rz.release(); w.advance(500 + 1500);
  assert.equal(w.rz.status().trackOk, false);
  assert.ok(w.notices.some((n) => /no text/.test(n)));
  w.advance(10);
  assert.equal(w.live().at(-1).trackArg, undefined, "the next prepared session uses the plain microphone");
});

test("press before start or in always mode does nothing", () => {
  const w = makeWorld();
  assert.equal(w.rz.press(), false);
  w.rz.start("always"); w.advance(0);
  assert.equal(w.rz.press(), false);
});

// ---------------------------------------------------------------- stats
test("stats keep the last 30 records, numbers only", () => {
  const st = createStats(30);
  for (let i = 0; i < 45; i++) st.addUtt({ id: i, endToFinalMs: 100 + i, rttMs: 3, text: undefined });
  assert.equal(st.utts.length, 30);
  assert.equal(st.utts[0].id, 15);
  const row = st.rows().find((r) => r.key === "endToFinalMs");
  assert.equal(row.n, 30);
  assert.equal(row.median, 129.5);
  assert.equal(row.p90, 141);
  assert.equal(row.last, 144);
  const json = st.exportJson();
  assert.ok(!/"text"|"words"|"transcript"/.test(json), "no words in the export");
  st.clear(); assert.equal(st.utts.length, 0);
});

// ---------------------------------------------------------------- v4: watchdog, quick-death back-off, keepAlive, health
test("watchdog: a session that went silent after audio (no event, no end) is replaced after 25 s", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  w.sessions[0].audio();
  w.advance(24000);
  assert.equal(w.sessions.length, 1, "still waiting");
  w.advance(3000);
  const dog = w.ev("watchdog");
  assert.equal(dog.length, 1);
  assert.equal(dog[0].why, "idle");
  assert.ok(w.sessions[0].aborted, "the stalled session is aborted");
  w.advance(1500);
  assert.equal(w.sessions.length, 2, "a replacement was started");
  assert.equal(w.rz.status().restarts, 1);
  assert.equal(w.restartsSeen[0].why, "idle");
  assert.ok(w.states.includes("restarting"));
});

test("watchdog: events keep a session alive (results, speech edges, sound edges)", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  w.sessions[0].audio();
  for (let i = 0; i < 6; i++) { w.advance(15000); w.sessions[0].onsoundstart && w.sessions[0].onsoundstart(); }
  w.advance(15000); w.sessions[0].say("hmm", false);
  w.advance(20000);
  assert.equal(w.ev("watchdog").length, 0);
  assert.equal(w.sessions.length, 1);
});

test("watchdog: a start that never reaches audio is replaced (8 s once the page has heard audio, 45 s for the very first start)", () => {
  const first = makeWorld();
  first.rz.start("always"); first.advance(10000);
  assert.equal(first.sessions.length, 1, "the first start may wait for the permission prompt");
  first.advance(36000);
  assert.equal(first.ev("watchdog")[0].why, "start-timeout");

  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  w.sessions[0].audio(); w.sessions[0].say("x", true); w.sessions[0].end(); w.advance(0);
  assert.equal(w.sessions.length, 2);
  w.advance(7000); assert.equal(w.ev("watchdog").length, 0);
  w.advance(2000); assert.equal(w.ev("watchdog")[0].why, "start-timeout");
});

test("watchdog: loud microphone but a silent recogniser for 7 s counts as deaf; quiet microphone does not", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  w.sessions[0].audio();
  w.advance(20000);
  assert.equal(w.ev("watchdog").length, 0, "silence is normal");
  w.loud = true;
  w.advance(1500);
  assert.equal(w.ev("watchdog")[0].why, "deaf");
});

test("watchdog: restarts are at least 3 s apart and a held press is never interrupted", () => {
  const w = makeWorld({ cfg: { idleTimeoutMs: 1500, watchdogGapMs: 3000 } });
  w.rz.start("always"); w.advance(0);
  w.sessions[0].audio();
  w.advance(2600); // first restart at ~2 s
  assert.equal(w.ev("watchdog").length, 1);
  w.advance(100); w.sessions.at(-1).audio();
  w.advance(1600); // stalled again after 1.5 s, but only ~2.2 s after the last restart
  assert.equal(w.ev("watchdog").length, 1, "gap respected");
  w.advance(2000);
  assert.equal(w.ev("watchdog").length, 2);

  const p = makeWorld({ cfg: { idleTimeoutMs: 1500 } });
  p.rz.start("ptt"); p.advance(0);
  p.sessions[0].audio();
  p.rz.press();
  p.advance(20000);
  assert.equal(p.ev("watchdog").length, 0, "no restart under a held press");
});

test("tick(): a retry timer the browser made late is run by hand, a late drain too", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  w.sessions[0].end();                         // died before audio: retry in 250 ms
  w.timers.length = 0;                         // the throttled tab never fires it
  w.advance(400); w.rz.tick();
  assert.equal(w.sessions.length, 2, "tick started the retry");

  const d = makeWorld({ noEndOnAbort: true });
  d.rz.start("always"); d.advance(0); d.sessions[0].audio();
  d.rz.setLang("ru-RU");
  d.timers.length = 0;                         // the drain timer is lost too
  d.advance(900); d.rz.tick();
  assert.equal(d.sessions.length, 1, "still draining before the 1 s cap");
  d.advance(300); d.rz.tick();
  assert.equal(d.sessions.length, 2, "tick completed the drain");
  assert.equal(d.sessions[1].lang, "ru-RU");
});

test("quick deaths with audio but no result back off (the old code reset the back-off on audio and spun at 4 sessions a second)", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  const gaps = [];
  for (let i = 0; i < 5; i++) {
    const s = w.sessions.at(-1);
    s.audio(); w.advance(100);
    s.fail("network"); s.end();
    let d = 0; const n = w.sessions.length;
    while (w.sessions.length === n && d < 20000) { w.advance(50); d += 50; }
    gaps.push(d);
  }
  assert.ok(gaps[4] > gaps[0] * 2, "delay grows: " + gaps);
  assert.ok(w.rz.status().quickStreak >= 4);
  const s = w.sessions.at(-1); s.audio(); s.say("ok", true);
  assert.equal(w.rz.status().quickStreak, 0, "a result clears it");
});

test("a normal end (the service closes a quiet session after a few seconds) still restarts at once", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  for (let i = 0; i < 4; i++) {
    const s = w.sessions.at(-1);
    s.audio(); w.advance(8000); s.fail("no-speech"); s.end();
    w.advance(0);
    assert.equal(w.sessions.length, i + 2, "restarted without delay after a no-speech end");
  }
  assert.equal(w.fatals.length, 0);
});

test("keepAlive: transient errors never end the recognition, the retry slows down to 10 s and recovers on a result", () => {
  const w = makeWorld({ cfg: { keepAlive: true } });
  w.rz.start("always"); w.advance(0);
  for (let i = 0; i < 9; i++) {
    const s = w.sessions.at(-1);
    s.fail("network"); s.end();
    let d = 0; const n = w.sessions.length;
    while (w.sessions.length === n && d < 20000) { w.advance(50); d += 50; }
    if (i >= 7) assert.ok(d >= 9000 && d <= 10500, "slow retry, got " + d);
  }
  assert.deepEqual(w.fatals, []);
  assert.equal(w.rz.status().degraded, true);
  assert.equal(w.ev("degraded").length, 1);
  const s = w.sessions.at(-1); s.audio(); s.say("back", false);
  assert.equal(w.rz.status().degraded, false);
  assert.equal(w.rz.status().failStreak, 0);
});

test("health: per-language counters of sessions, results, errors and watchdog restarts (numbers only)", () => {
  const w = makeWorld();
  w.rz.setLang("ru-RU");
  w.rz.start("always"); w.advance(0);
  w.sessions[0].audio(); w.sessions[0].say("a", false);
  w.sessions[0].fail("no-speech"); w.sessions[0].end(); w.advance(0);
  w.sessions[1].audio(); w.advance(30000);       // stall: watchdog
  const h = w.rz.health();
  assert.equal(h["ru-RU"].sessions >= 2, true);
  assert.equal(h["ru-RU"].results, 1);
  assert.equal(h["ru-RU"].errors["no-speech"], 1);
  assert.equal(h["ru-RU"].restarts, 1);
  assert.ok(!JSON.stringify(h).includes('"a"'), "no words in the counters");
});

test("stop() cancels the watchdog: nothing starts afterwards", () => {
  const w = makeWorld();
  w.rz.start("always"); w.advance(0);
  w.sessions[0].audio();
  w.rz.stop();
  w.advance(120000);
  assert.equal(w.sessions.length, 1);
  assert.equal(w.ev("watchdog").length, 0);
  assert.equal(w.timers.length, 0);
});

// ---------------------------------------------------------------- the page itself
test("page lint: no external requests, no script src, no eval, strict CSP, markers present", () => {
  assert.ok(!/<script[^>]+src=/i.test(html), "no external script");
  assert.ok(!/<link[^>]+href=/i.test(html), "no linked stylesheet or font");
  assert.ok(!/\beval\s*\(|new Function\s*\(/.test(html.replace(/\/\* =====[\s\S]*?\*\//g, "")), "no eval in page code");
  const urls = [...html.matchAll(/https?:\/\/[^\s"'<>)]+/g)].map((m) => m[0]).filter((u) => !/^https?:\/\/(127\.0\.0\.1|localhost)(:|\/|\*|$)/.test(u));
  assert.deepEqual(urls, [], "only loopback URLs");
  assert.match(html, /Content-Security-Policy[^>]*default-src 'none'/);
  for (const k of ["SESSION-CORE", "STATS"]) assert.ok(html.includes("===== " + k + "-BEGIN") && html.includes("===== " + k + "-END"));
  assert.ok(!/localStorage\.setItem\([^)]*(text|transcript)/i.test(html), "no words persisted");
});
