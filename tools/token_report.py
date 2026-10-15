"""Read-only token report for Claude Code sessions that worked on this project (no network, nothing is modified).

  python -I tools/token_report.py --keyword minebleach [--since 2026-10-01] [--min-hits 20] [--top 10]

It scans ~/.claude/projects/**/<session>.jsonl (and <session>/subagents/*.jsonl), keeps the sessions that mention the keyword at least
--min-hits times, and prints: usage per session, totals by cost type, estimated content by category, the most expensive tool results,
cache misses (a turn that re-writes most of the context, usually after a pause longer than 5 minutes) and the biggest subagents.

Exact numbers: input / output / cache read / cache write (from the logs). ESTIMATES: content tokens (3.6 chars per token for ASCII, 1.8 otherwise, 1600 per image).
Cost units are relative to the input price: cache read 0.1, cache write 1.25, output 5 (model prices differ, the ratios are typical).
"""
import argparse
import collections
import json
import os
import re
import time
from datetime import datetime

REM = re.compile(r"<system-reminder>.*?</system-reminder>", re.S)
IMG_TOK = 1600


def est(text):
	if not text:
		return 0.0
	a = len(text.encode("ascii", "ignore"))
	return a / 3.6 + (len(text) - a) / 1.8


def wcost(u):
	return u["i"] + 1.25 * u["cw"] + 0.1 * u["cr"] + 5.0 * u["o"]


def group_of(name):
	if name.startswith("mcp__"):
		return "MCP " + name.split("__")[1][:12]
	return name


def tkey(name, inp):
	if not isinstance(inp, dict):
		return name
	if name == "Read":
		return "Read %s" % inp.get("file_path")
	if name in ("Bash", "PowerShell"):
		return "%s %s" % (name, (inp.get("command") or "")[:100])
	return "%s %s" % (name, json.dumps(inp, ensure_ascii=False)[:80])


def parse_ts(t):
	try:
		return datetime.fromisoformat(t.replace("Z", "+00:00")).timestamp()
	except Exception:
		return None


def analyze(path):
	usage, order, tmap, results = {}, [], {}, []
	model_of = {}
	cats, imgs = collections.Counter(), collections.Counter()
	turn, ts_of, tools_of = 0, {}, collections.defaultdict(list)
	first_user = None
	with open(path, "r", encoding="utf-8", errors="replace") as fh:
		for line in fh:
			if not line.strip():
				continue
			try:
				o = json.loads(line)
			except Exception:
				continue
			m = o.get("message")
			if not isinstance(m, dict):
				continue
			c = m.get("content")
			if m.get("role") == "assistant":
				mid = m.get("id") or o.get("uuid")
				u = m.get("usage") or {}
				new = {"i": u.get("input_tokens", 0) or 0, "o": u.get("output_tokens", 0) or 0,
				       "cw": u.get("cache_creation_input_tokens", 0) or 0, "cr": u.get("cache_read_input_tokens", 0) or 0}
				if mid not in usage:
					model_of[mid] = m.get("model") or "?"
					usage[mid] = new
					order.append(mid)
					turn += 1
					ts_of[mid] = (turn, o.get("timestamp"))
				else:
					for k in new:
						usage[mid][k] = max(usage[mid][k], new[k])
				for b in c if isinstance(c, list) else []:
					t = b.get("type")
					if t == "text":
						cats["assistant text"] += est(b.get("text", ""))
					elif t == "thinking":
						cats["assistant thinking"] += est(b.get("thinking", ""))
					elif t == "tool_use":
						key = tkey(b.get("name", "?"), b.get("input"))
						tmap[b.get("id")] = (b.get("name", "?"), key)
						cats["assistant tool calls (inputs)"] += est(json.dumps(b.get("input"), ensure_ascii=False))
						tools_of[mid].append(key[:80])
			elif m.get("role") == "user":
				for b in [{"type": "text", "text": c}] if isinstance(c, str) else (c or []):
					t = b.get("type")
					if t == "text":
						txt = b.get("text", "")
						cats["system reminders/notifications"] += est("".join(REM.findall(txt)))
						tk = est(REM.sub("", txt))
						cats["user pasted prompts (>3k tok)" if tk > 3000 else "user text"] += tk
						if first_user is None:
							first_user = len(txt)
					elif t == "image":
						imgs["user images"] += 1
						cats["images (est)"] += IMG_TOK
					elif t == "tool_result":
						name, key = tmap.get(b.get("tool_use_id"), ("?", "?"))
						rc, tk, ni = b.get("content"), 0.0, 0
						if isinstance(rc, str):
							tk = est(rc)
						elif isinstance(rc, list):
							for x in rc:
								if x.get("type") == "text":
									tk += est(x.get("text", ""))
								elif x.get("type") == "image":
									ni += 1
						if ni:
							imgs["tool images"] += ni
							cats["images (est)"] += ni * IMG_TOK
						cats["tool results: " + group_of(name)] += tk
						results.append((turn, group_of(name), key, tk + ni * IMG_TOK))
	tot = {"i": 0, "o": 0, "cw": 0, "cr": 0}
	ctxs, misses, prev_t = [], [], None
	for idx, mid in enumerate(order):
		u = usage[mid]
		for k in tot:
			tot[k] += u[k]
		ctx = u["i"] + u["cw"] + u["cr"]
		ctxs.append(ctx)
		t = parse_ts(ts_of[mid][1] or "")
		if idx > 3 and ctx > 50000 and u["cr"] < 0.2 * ctx:
			misses.append((u["cw"], ts_of[mid][0], (t - prev_t) if (t and prev_t) else None))
		prev_t = t
	carry = collections.Counter()
	big = []
	for tn, g, key, tk in results:
		cc = tk * max(0, turn - tn) * 0.1
		carry[g] += cc
		big.append((cc, tk, tn, g, key[:100]))
	big.sort(reverse=True)
	tt = sorted(((wcost(usage[m]), ts_of[m][0], ctxs[i], usage[m]["o"], usage[m]["cw"], tools_of.get(m, [])[:2]) for i, m in enumerate(order)), reverse=True)
	by_model = collections.Counter()
	msgs_model = collections.Counter()
	for mid in order:
		by_model[model_of[mid]] += wcost(usage[mid])
		msgs_model[model_of[mid]] += 1
	return {"by_model": dict(by_model), "msgs_model": dict(msgs_model), "turns": turn, "usage": tot, "weighted": wcost(tot), "max_ctx": max(ctxs) if ctxs else 0, "cats": dict(cats), "imgs": dict(imgs),
	        "carry": dict(carry), "big": big[:8], "top_turns": tt[:8], "misses": misses, "first_user": first_user}


def find_sessions(root, keyword, min_hits, since):
	kw = keyword.lower().encode()
	out = []
	for dp, dn, fn in os.walk(root):
		if "subagents" in dp.replace("\\", "/").split("/"):
			continue
		for f in fn:
			if not f.endswith(".jsonl"):
				continue
			p = os.path.join(dp, f)
			if since and os.path.getmtime(p) < since:
				continue
			with open(p, "rb") as fh:
				hits = fh.read().lower().count(kw)
			if hits >= min_hits:
				out.append(p)
	return sorted(out, key=os.path.getmtime)


def K(x):
	return "%.0fk" % (x / 1e3)


def M(x):
	return "%.2fM" % (x / 1e6)


def main():
	ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	ap.add_argument("--root", default=os.path.expanduser("~/.claude/projects"))
	ap.add_argument("--keyword", required=True)
	ap.add_argument("--min-hits", type=int, default=20)
	ap.add_argument("--since", help="YYYY-MM-DD, by file modification time")
	ap.add_argument("--top", type=int, default=10)
	a = ap.parse_args()
	since = time.mktime(time.strptime(a.since, "%Y-%m-%d")) if a.since else None
	paths = find_sessions(a.root, a.keyword, a.min_hits, since)
	print("sessions with '%s' (>= %d hits): %d" % (a.keyword, a.min_hits, len(paths)))
	allr, rows = [], []
	for p in paths:
		r = analyze(p)
		subs = []
		sd = p[:-6] + "/subagents"
		if os.path.isdir(sd):
			subs = [analyze(sd + "/" + f) for f in sorted(os.listdir(sd)) if f.endswith(".jsonl")]
		rows.append((os.path.basename(p)[:8], r, subs))
		allr += [r] + subs
	print("\n%-9s %5s %7s %9s %7s %6s %6s %s" % ("session", "turns", "maxctx", "cacheR", "output", "wt_M", "subs", "subs wt_M"))
	for lab, r, subs in rows:
		print("%-9s %5d %7s %9s %7s %6.1f %6d %.1f" % (lab, r["turns"], K(r["max_ctx"]), K(r["usage"]["cr"]), K(r["usage"]["o"]), r["weighted"] / 1e6, len(subs), sum(s["weighted"] for s in subs) / 1e6))
	tot = collections.Counter()
	for r in allr:
		for k, v in r["usage"].items():
			tot[k] += v
	W = tot["i"] + 1.25 * tot["cw"] + 0.1 * tot["cr"] + 5 * tot["o"]
	if not W:
		return
	print("\nTOTAL cost units %s: cache read %.0f%%, cache write %.0f%%, output %.0f%%, input %.0f%%" % (M(W), 100 * 0.1 * tot["cr"] / W, 100 * 1.25 * tot["cw"] / W, 100 * 5 * tot["o"] / W, 100 * tot["i"] / W))
	subw = sum(s["weighted"] for _, _, subs in rows for s in subs)
	print("main sessions %.0f%%, subagents %.0f%%" % (100 * (W - subw) / W, 100 * subw / W))
	bm, mm = collections.Counter(), collections.Counter()
	for r in allr:
		bm.update(r["by_model"])
		mm.update(r["msgs_model"])
	print("by model (cost units, assistant messages): " + ", ".join("%s %.0f%% (%d)" % (k, 100 * v / W, mm[k]) for k, v in bm.most_common(5)))
	cats = collections.Counter()
	for r in allr:
		for k, v in r["cats"].items():
			cats[k] += v
	tc = sum(cats.values()) or 1
	print("\nESTIMATED content added to the context (%s tokens):" % M(tc))
	for k, v in cats.most_common(10):
		print("  %-34s %8s %5.1f%%" % (k, K(v), 100 * v / tc))
	img = collections.Counter()
	for r in allr:
		img.update(r["imgs"])
	print("  images:", dict(img))
	carry = collections.Counter()
	for r in allr:
		carry.update(r["carry"])
	tcar = sum(carry.values()) or 1
	print("\nCarry cost of tool results (tokens x remaining turns x 0.1), by tool:")
	for k, v in carry.most_common(6):
		print("  %-26s %8s %5.1f%%" % (k, M(v), 100 * v / tcar))
	miss_n = sum(len(r["misses"]) for r in allr)
	miss_cw = sum(m[0] for r in allr for m in r["misses"])
	gaps5 = sum(1 for r in allr for m in r["misses"] if m[2] and m[2] > 300)
	print("\nCache misses: %d turns re-wrote most of the context (%s cache-write tokens = %.0f%% of cost units); %d after a pause over 5 min" % (miss_n, M(miss_cw), 100 * 1.25 * miss_cw / W, gaps5))
	bb = sorted(((b[0], b[1], b[2], b[3], b[4], lab) for lab, r, subs in rows for r in [r] + subs for b in r["big"]), reverse=True)
	print("\nTop %d tool results by carry cost:" % a.top)
	for c, tk, tn, g, key, lab in bb[:a.top]:
		print("  %s turn %3d %-12s %6s tok carry %.2fM  %s" % (lab, tn, g[:12], K(tk), c / 1e6, key[:70]))
	subs_all = sorted((s for _, _, subs in rows for s in subs), key=lambda s: -s["weighted"])
	print("\nSubagents: %d, top by cost units:" % len(subs_all))
	for s in subs_all[:a.top]:
		print("  turns %3d  %.2fM  maxctx %s  prompt chars %s" % (s["turns"], s["weighted"] / 1e6, K(s["max_ctx"]), s["first_user"]))


if __name__ == "__main__":
	main()
