// Drives the voice bridge page in a real Chrome/Edge (own throw-away profile) to check "speech engine -> page -> bridge".
// No test framework, no npm packages (Node 22+: global fetch and WebSocket).
//
//   node tools/voice_lab_e2e.mjs fixtures [--url http://127.0.0.1:47831] [--wav-dir tests/voice/audio] [--send 1] [--out run.json]
//       Opens <url>/?lab=1, hands every WAV of the folder (+ manifest.json) to the lab's file box, runs it and prints the table.
//       The audio is synthetic speech from tools/gen_voice_fixtures.ps1; Chrome sends it to its recognition service.
//
//   node tools/voice_lab_e2e.mjs mic --wav tests/voice/audio/en-US_byakuya.shikai.release_1.wav [--lang en-US] [--mode always|ptt] [--prewarm 1] [--seconds 25]
//       Starts the page with Chrome's FAKE microphone playing that WAV (--use-file-for-fake-audio-capture, looped with a pause so the phrase repeats; --gap seconds of silence) so the whole page
//       (Start button, session, meter, post, latency table) runs without a person. Prints the latency table and the log.
//
//   node tools/voice_lab_e2e.mjs ptt --wav <file> [--lang en-US] [--rounds 5] [--prewarm 1]
//       Hold to talk with the muted-track pre-warm, fed by a synthetic microphone (getUserMedia replaced by a Web Audio
//       stream that plays the WAV on demand). Chrome's real recogniser, the page's session controller, meter and stats run.
//
// Common: --chrome <exe> (default: Chrome, else Edge), --port 9333 (debug port), --pos 300,-1080 (window position, second monitor),
//         --keep 1 (leave the browser open).
import { spawn } from "node:child_process";
import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

const argv = process.argv.slice(2);
const cmd = argv.shift();
const opt = {};
for (let i = 0; i < argv.length; i += 2) opt[argv[i].replace(/^--/, "")] = argv[i + 1];
const url = (opt.url || "http://127.0.0.1:47831").replace(/\/$/, "");
const dbg = parseInt(opt.port || "9333", 10);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function findBrowser() {
  if (opt.chrome) return opt.chrome;
  for (const p of ["C:/Program Files/Google/Chrome/Application/chrome.exe", "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe", "C:/Program Files/Microsoft/Edge/Application/msedge.exe"]) if (existsSync(p)) return p;
  throw new Error("no Chrome or Edge found; pass --chrome");
}

class Cdp {
  constructor(ws) { this.ws = ws; this.id = 0; this.pending = new Map(); ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && this.pending.has(d.id)) { this.pending.get(d.id)(d); this.pending.delete(d.id); } }; }
  static async connect(wsUrl) { const ws = new WebSocket(wsUrl); await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; }); return new Cdp(ws); }
  send(method, params = {}) { const id = ++this.id; return new Promise((res, rej) => { this.pending.set(id, (d) => d.error ? rej(new Error(method + ": " + d.error.message)) : res(d.result)); this.ws.send(JSON.stringify({ id, method, params })); }); }
  async eval(expr) { const r = await this.send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true }); if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails.exception || r.exceptionDetails)); return r.result.value; }
}

async function launch(extra) {
  const profile = mkdtempSync(join(tmpdir(), "voice-e2e-"));
  const exe = findBrowser();
  const args = [`--user-data-dir=${profile}`, `--remote-debugging-port=${dbg}`, "--no-first-run", "--no-default-browser-check", "--use-fake-ui-for-media-stream",
    "--autoplay-policy=no-user-gesture-required", `--window-position=${opt.pos || "300,-1080"}`, "--window-size=1100,950", ...extra, "about:blank"];
  const child = spawn(exe, args, { stdio: "ignore", detached: false });
  let tabs = null;
  for (let i = 0; i < 60 && !tabs; i++) { await sleep(500); try { tabs = await (await fetch(`http://127.0.0.1:${dbg}/json`)).json(); } catch { /* not up yet */ } }
  if (!tabs) throw new Error("browser debug port did not open");
  const page = tabs.find((t) => t.type === "page");
  const cdp = await Cdp.connect(page.webSocketDebuggerUrl);
  await cdp.send("Runtime.enable"); await cdp.send("Page.enable"); await cdp.send("DOM.enable");
  const close = async () => { if (opt.keep === "1") return; try { await cdp.send("Browser.close"); } catch { /* gone */ } child.kill(); await sleep(800); try { rmSync(profile, { recursive: true, force: true }); } catch { /* locked, harmless */ } };
  return { cdp, close, exe };
}

async function waitFor(cdp, expr, ms, what) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) { if (await cdp.eval(expr)) return true; await sleep(300); }
  throw new Error("timeout waiting for " + what);
}

async function fixtures() {
  const dir = resolve(opt["wav-dir"] || "tests/voice/audio");
  const files = readdirSync(dir).filter((f) => /\.(wav|json)$/i.test(f)).map((f) => join(dir, f).replace(/\\/g, "/"));
  if (!files.length) throw new Error("no fixtures in " + dir + " (run tools/gen_voice_fixtures.ps1)");
  const { cdp, close, exe } = await launch([]);
  try {
    await cdp.send("Page.navigate", { url: `${url}/?lab=1` });
    await waitFor(cdp, "document.readyState==='complete' && !!document.getElementById('fxFiles')", 15000, "page");
    const doc = await cdp.send("DOM.getDocument");
    const inp = await cdp.send("DOM.querySelector", { nodeId: doc.root.nodeId, selector: "#fxFiles" });
    await cdp.send("DOM.setFileInputFiles", { files, nodeId: inp.nodeId });
    if (opt.send !== "0") await cdp.eval("document.getElementById('fxSend').click(), true");
    await cdp.eval("document.getElementById('capRun').click(), true");
    await waitFor(cdp, "document.querySelectorAll('#capTable tbody tr').length>6", 20000, "capability table");
    await cdp.eval("document.getElementById('fxRun').click(), true");
    await waitFor(cdp, "/^done/.test(document.getElementById('fxMsg').textContent)", 20 * 60 * 1000, "fixture run");
    const out = await cdp.eval(`JSON.stringify({
      caps: [...document.querySelectorAll('#capTable tbody tr')].map(r => [...r.children].map(c => c.textContent)),
      rows: [...document.querySelectorAll('#fxTable tbody tr')].map(r => [...r.children].map(c => c.textContent + (c.className ? ' ['+c.className+']' : ''))),
      msg: document.getElementById('fxMsg').textContent })`);
    const res = JSON.parse(out);
    res.browser = exe;
    console.log("capabilities:"); res.caps.forEach((r) => console.log("  " + r.join(": ")));
    console.log("\nfile | lang | heard | start->audio ms | end->final ms | expect | game");
    res.rows.forEach((r) => console.log(r.join(" | ")));
    console.log("\n" + res.msg);
    if (opt.out) writeFileSync(opt.out, JSON.stringify(res, null, 1));
  } finally { await close(); }
}

// PCM WAV (RIFF, 16 bit) plus `sec` seconds of silence at the end: the fake microphone loops the file, so the phrase repeats
function padWav(path, sec, outPath) {
  const b = readFileSync(path);
  let p = 12, dataAt = -1, dataLen = 0, byteRate = 32000;
  while (p + 8 <= b.length) {
    const id = b.toString("ascii", p, p + 4), len = b.readUInt32LE(p + 4);
    if (id === "fmt ") byteRate = b.readUInt32LE(p + 16);
    if (id === "data") { dataAt = p + 8; dataLen = Math.min(len, b.length - dataAt); break; }
    p += 8 + len + (len & 1);
  }
  if (dataAt < 0) throw new Error("no data chunk in " + path);
  const pad = Buffer.alloc(Math.round(sec * byteRate) & ~1);
  const out = Buffer.concat([b.subarray(0, dataAt), b.subarray(dataAt, dataAt + dataLen), pad]);
  out.writeUInt32LE(out.length - 8, 4); out.writeUInt32LE(dataLen + pad.length, dataAt - 4);
  writeFileSync(outPath, out);
}

async function mic() {
  const src = resolve(opt.wav || "");
  if (!existsSync(src)) throw new Error("--wav file missing");
  const wav = join(mkdtempSync(join(tmpdir(), "voice-wav-")), "loop.wav").split("\\").join("/");
  padWav(src, parseFloat(opt.gap || "5"), wav);
  const q = new URLSearchParams({ lab: "0", lang: opt.lang || "en-US", mode: opt.mode || "always", prewarm: opt.prewarm || "0", meter: opt.meter || "1" });
  const { cdp, close } = await launch(["--use-fake-device-for-media-stream", `--use-file-for-fake-audio-capture=${wav}`]);
  try {
    await cdp.send("Page.navigate", { url: `${url}/?${q}` });
    await waitFor(cdp, "document.readyState==='complete'", 15000, "page");
    await cdp.eval("document.getElementById('toggle').click(), true");
    if (opt.mode === "ptt") {
      await sleep(parseInt(opt.hold_after || "3500", 10));
      await cdp.eval("document.getElementById('ptt').dispatchEvent(new PointerEvent('pointerdown',{pointerId:1,bubbles:true})), true");
      await sleep(parseInt(opt.hold || "6000", 10));
      await cdp.eval("document.getElementById('ptt').dispatchEvent(new PointerEvent('pointerup',{pointerId:1,bubbles:true})), true");
    }
    await sleep(parseInt(opt.seconds || "25", 10) * 1000);
    const out = JSON.parse(await cdp.eval(`JSON.stringify({
      heard: document.getElementById('heard').textContent, reply: ['rCmd','rConf','rRes','rLat','rRtt'].map(i => document.getElementById(i).textContent),
      log: [...document.querySelectorAll('#log li')].map(l => l.textContent),
      stats: [...document.querySelectorAll('#statsTable tbody tr')].map(r => [...r.children].map(c => c.textContent).join(' | ')),
      raw: window.VoiceStats ? JSON.stringify(window.VoiceStats()) : null,
      err: document.getElementById('micError').textContent, notice: document.getElementById('notice').textContent,
      dbg: window.__voiceDebug ? window.__voiceDebug.status() : null, lvl: window.__voiceDebug ? window.__voiceDebug.lvl() : null })`));
    console.log(JSON.stringify(out, null, 1));
  } finally { await close(); }
}

// ptt: hold-to-talk with the muted-track pre-warm, fed by a SYNTHETIC microphone: getUserMedia is replaced (before the page
// scripts run) by a Web Audio stream that plays the WAV when asked. The real Chrome recogniser listens through start(track),
// the page's own meter, session controller, post and latency table all run for real.
const FAKE_MIC = (b64, gapSec) => `(() => {
  const AC = window.AudioContext, ctx = new AC();
  const bin = atob("${b64}"), arr = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
  const dest = ctx.createMediaStreamDestination();
  let buf = null, src = null;
  const ready = ctx.decodeAudioData(arr.buffer).then((b) => { buf = b; window.__fakeMicMs = b.duration * 1000; });
  window.__fakeMicRestart = async () => { await ready; if (ctx.state !== "running") await ctx.resume(); if (src) { try { src.stop(); } catch (e) {} } src = ctx.createBufferSource(); src.buffer = buf; src.connect(dest); src.start(); return window.__fakeMicMs; };
  navigator.mediaDevices.getUserMedia = async () => { if (ctx.state !== "running") await ctx.resume(); return dest.stream; };
})();`;

async function ptt() {
  const src = resolve(opt.wav || "");
  if (!existsSync(src)) throw new Error("--wav file missing");
  const rounds = parseInt(opt.rounds || "5", 10);
  const q = new URLSearchParams({ lang: opt.lang || "en-US", mode: "ptt", prewarm: opt.prewarm || "1", meter: "1" });
  const { cdp, close } = await launch([]);
  try {
    await cdp.send("Page.addScriptToEvaluateOnNewDocument", { source: FAKE_MIC(readFileSync(src).toString("base64")) });
    await cdp.send("Page.navigate", { url: `${url}/?${q}` });
    await waitFor(cdp, "document.readyState==='complete'", 15000, "page");
    await cdp.eval("document.getElementById('toggle').click(), true");
    const results = [];
    for (let r = 0; r < rounds; r++) {
      await waitFor(cdp, "window.__voiceDebug.status().hasSession && document.getElementById('mic').className.includes('ready')", 15000, "prepared session (round " + (r + 1) + ")");
      await sleep(parseInt(opt.idle || "1500", 10));
      const before = await cdp.eval("window.VoiceStats().utterances.length");
      await cdp.eval("document.getElementById('ptt').dispatchEvent(new PointerEvent('pointerdown',{pointerId:1,bubbles:true})), true");
      await sleep(400);
      const ms = await cdp.eval("window.__fakeMicRestart()");
      if (opt.shot && r === 0) { await sleep(700); const s = await cdp.send("Page.captureScreenshot", { format: "png" }); writeFileSync(opt.shot, Buffer.from(s.data, "base64")); await sleep(Math.max(0, ms - 400)); }
      else await sleep(ms);
      await sleep(300);
      await cdp.eval("document.getElementById('ptt').dispatchEvent(new PointerEvent('pointerup',{pointerId:1,bubbles:true})), true");
      try { await waitFor(cdp, `window.VoiceStats().utterances.length>${before}`, 9000, "utterance"); results.push("ok"); } catch { results.push("no utterance"); }
      await sleep(600);
    }
    const out = JSON.parse(await cdp.eval(`JSON.stringify({
      rounds: ${JSON.stringify(0)}, log: [...document.querySelectorAll('#log li')].map(l => l.textContent),
      stats: [...document.querySelectorAll('#statsTable tbody tr')].map(r => [...r.children].map(c => c.textContent).join(' | ')),
      raw: window.VoiceStats(), err: document.getElementById('micError').textContent, notice: document.getElementById('notice').textContent,
      dbg: window.__voiceDebug.status() })`));
    out.rounds = results;
    console.log(JSON.stringify(out, null, 1));
    if (opt.out) writeFileSync(opt.out, JSON.stringify(out, null, 1));
  } finally { await close(); }
}

try {
  if (cmd === "ptt") await ptt();
  else if (cmd === "fixtures") await fixtures();
  else if (cmd === "mic") await mic();
  else { console.error("usage: node tools/voice_lab_e2e.mjs fixtures|mic|ptt [options] (see the header of this file)"); process.exitCode = 2; }
} catch (e) { console.error("FAILED: " + e.message); process.exitCode = 1; }
process.exit(process.exitCode || 0);
