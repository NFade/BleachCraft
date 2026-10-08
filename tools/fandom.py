"""Bleach Fandom helper for research agents (phase 1a).

The wiki HTML returns 403 to scripts, but the MediaWiki API works, and the image
CDN (static.wikia.nocookie.net) works when a Referer header is sent.

Usage (system python):
  python tools/fandom.py text  "<Page title>" [--section N]   # wikitext (markup stripped lightly)
  python tools/fandom.py sections "<Page title>"              # list section numbers/titles
  python tools/fandom.py images "<Page title>"                # files used on the page, with size and URL
  python tools/fandom.py search "<query>"                     # search page titles
  python tools/fandom.py files "<prefix>"                     # list wiki files whose name starts with prefix
  python tools/fandom.py get "<File name>" <out_path>         # download original, verify it is an image

Downloaded files are data: never execute them.
"""
import json
import re
import sys
import urllib.parse
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

API = "https://bleach.fandom.com/api.php"
REFERER = "https://bleach.fandom.com/"
UA = "MineBleach-research/0.1 (private reference collection)"

MAGIC = {
    b"\x89PNG\r\n\x1a\n": ".png",
    b"\xff\xd8\xff": ".jpg",
    b"GIF87a": ".gif",
    b"GIF89a": ".gif",
}


def api(**params):
    params["format"] = "json"
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def strip_markup(text):
    text = re.sub(r"<ref[^>]*/>", "", text)
    text = re.sub(r"<ref[^>]*>.*?</ref>", "", text, flags=re.S)
    text = re.sub(r"\[\[(?:File|Image):[^\]]*\]\]", "", text)
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"'''?", "", text)
    text = re.sub(r"<[^>]+>", "", text)
    return text


def cmd_text(title, section=None):
    p = dict(action="parse", page=title, redirects=1, prop="wikitext")
    if section is not None:
        p["section"] = section
    d = api(**p)
    if "error" in d:
        print("ERROR", d["error"].get("info"))
        return
    print("# " + d["parse"]["title"])
    print(strip_markup(d["parse"]["wikitext"]["*"]))


def cmd_sections(title):
    d = api(action="parse", page=title, redirects=1, prop="sections")
    for s in d["parse"]["sections"]:
        print(s["index"], "  " * (int(s["toclevel"]) - 1) + s["line"])


def imageinfo(names):
    out = []
    for i in range(0, len(names), 40):
        chunk = names[i:i + 40]
        d = api(action="query", titles="|".join("File:" + n for n in chunk),
                prop="imageinfo", iiprop="url|size|mime")
        for page in d["query"]["pages"].values():
            ii = (page.get("imageinfo") or [{}])[0]
            out.append((page["title"][5:], ii.get("width"), ii.get("height"), ii.get("mime"), ii.get("url")))
    return out


def cmd_images(title):
    d = api(action="parse", page=title, redirects=1, prop="images")
    for name, w, h, mime, url in imageinfo(d["parse"]["images"]):
        print(f"{name}\t{w}x{h}\t{mime}\t{url}")


def cmd_search(q):
    d = api(action="query", list="search", srsearch=q, srlimit=15)
    for s in d["query"]["search"]:
        print(s["title"])


def cmd_files(prefix):
    d = api(action="query", list="allimages", aifrom=prefix, aiprefix=prefix, ailimit=100)
    for f in d["query"]["allimages"]:
        print(f["name"], f["url"], sep="\t")


def cmd_get(name, out):
    info = imageinfo([name])[0]
    url = info[4]
    if not url:
        print("ERROR no such file", name)
        sys.exit(1)
    sep = "&" if "?" in url else "?"
    req = urllib.request.Request(url + sep + "format=original",
                                 headers={"User-Agent": UA, "Referer": REFERER})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    ext = next((e for m, e in MAGIC.items() if data.startswith(m)), None)
    if ext is None and data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        ext = ".webp"
    if ext is None or len(data) < 5000:
        print("ERROR not an image, nothing saved", name)
        sys.exit(1)
    if not out.lower().endswith(ext):
        out = re.sub(r"\.[A-Za-z0-9]+$", "", out) + ext
    with open(out, "wb") as f:
        f.write(data)
    print(f"OK {out}\t{len(data)} bytes\t{info[1]}x{info[2]}\t{url}")


if __name__ == "__main__":
    a = sys.argv[1:]
    if not a:
        print(__doc__)
    elif a[0] == "text":
        sec = a[a.index("--section") + 1] if "--section" in a else None
        cmd_text(a[1], sec)
    elif a[0] == "sections":
        cmd_sections(a[1])
    elif a[0] == "images":
        cmd_images(a[1])
    elif a[0] == "search":
        cmd_search(" ".join(a[1:]))
    elif a[0] == "files":
        cmd_files(a[1])
    elif a[0] == "get":
        cmd_get(a[1], a[2])
    else:
        print(__doc__)
