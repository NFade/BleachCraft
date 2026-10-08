#!/usr/bin/env python3
"""Build contact sheets from private reference images (Phase 1b).

Usage: python -I contact_sheet.py <refs_dir> <out_dir>

Writes one sheet per group (<group>.png) plus an overview (_all_small.png)
into <out_dir>. Only reads from <refs_dir>.
"""
import math
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

GROUPS = [
    "rukia_sealed", "rukia_shikai", "rukia_bankai",
    "byakuya_sealed", "byakuya_shikai", "byakuya_bankai",
]
EXTS = ("png", "jpg", "gif", "webp")

BG = (0x20, 0x20, 0x20)
TITLE_BG = (0x30, 0x30, 0x30)
WHITE = (0xFF, 0xFF, 0xFF)
ERR = (0xFF, 0x80, 0x80)

# Group sheet: 4 columns of 480x360 cells, 12 px gaps, 22 px margins -> 2000 px wide.
CELL_W, CELL_H = 480, 360
COLS = 4
GAP = 12
MARGIN = 22
LABEL_H = 34
TITLE_H = 64

# Overview: one row per group, up to 6 thumbnails, 200 px tall.
SMALL_H = 200
SMALL_SLOT_W = 320
SMALL_PER_ROW = 6
SMALL_ROW_PAD = 14
SMALL_LABEL_W = 240

try:
    RESAMPLE = Image.Resampling.LANCZOS
except AttributeError:  # older Pillow
    RESAMPLE = Image.LANCZOS


def load_fonts():
    arial = r"C:\Windows\Fonts\arial.ttf"
    try:
        return {
            "label": ImageFont.truetype(arial, 18),
            "row": ImageFont.truetype(arial, 22),
            "title": ImageFont.truetype(arial, 28),
        }
    except OSError:
        return {
            "label": ImageFont.load_default(size=18),
            "row": ImageFont.load_default(size=22),
            "title": ImageFont.load_default(size=28),
        }


def load_first_frame(path):
    """Open an image, take frame 0, and flatten alpha onto BG. Returns RGB."""
    with Image.open(path) as im:
        try:
            im.seek(0)
        except EOFError:
            pass
        has_alpha = im.mode in ("RGBA", "LA", "PA") or "transparency" in im.info
        frame = im.convert("RGBA") if has_alpha else im.convert("RGB")
    if frame.mode == "RGBA":
        bg = Image.new("RGBA", frame.size, BG + (255,))
        bg.alpha_composite(frame)
        frame = bg
    return frame.convert("RGB")


def fit(img, box_w, box_h):
    """Scale to fit inside box_w x box_h, keeping aspect ratio."""
    w, h = img.size
    s = min(box_w / w, box_h / h)
    size = (max(1, round(w * s)), max(1, round(h * s)))
    return img.resize(size, RESAMPLE)


def paste_centered(canvas, img, box):
    x0, y0, x1, y1 = box
    f = fit(img, x1 - x0, y1 - y0)
    canvas.paste(f, (x0 + (x1 - x0 - f.width) // 2, y0 + (y1 - y0 - f.height) // 2))


def collect(refs, group):
    """Names of <group>_<n>.<ext> files in refs, sorted by numeric n."""
    pat = re.compile(r"^%s_(\d+)\.(%s)$" % (re.escape(group), "|".join(EXTS)),
                     re.IGNORECASE)
    found = []
    for name in os.listdir(refs):
        m = pat.match(name)
        if m and os.path.isfile(os.path.join(refs, name)):
            found.append((int(m.group(1)), name))
    return [name for _, name in sorted(found)]


def load_group(refs, group):
    """List of (name, RGB image or None, error text)."""
    items = []
    for name in collect(refs, group):
        try:
            items.append((name, load_first_frame(os.path.join(refs, name)), ""))
        except Exception as exc:  # keep going, report at the end
            items.append((name, None, "%s: %s" % (type(exc).__name__, exc)))
    return items


def build_group_sheet(group, items, out_dir, fonts):
    rows = max(1, math.ceil(len(items) / COLS))
    width = 2 * MARGIN + COLS * CELL_W + (COLS - 1) * GAP
    pitch = CELL_H + LABEL_H + GAP
    height = TITLE_H + MARGIN + (rows - 1) * pitch + CELL_H + LABEL_H + MARGIN

    canvas = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(canvas)
    draw.rectangle([0, 0, width, TITLE_H], fill=TITLE_BG)
    n_failed = sum(1 for _, img, _ in items if img is None)
    title = "%s   |   %d images" % (group, len(items))
    if n_failed:
        title += "   |   %d failed to load" % n_failed
    draw.text((MARGIN, TITLE_H // 2), title, font=fonts["title"], fill=WHITE, anchor="lm")

    for i, (name, img, _err) in enumerate(items):
        r, c = divmod(i, COLS)
        x0 = MARGIN + c * (CELL_W + GAP)
        y0 = TITLE_H + MARGIN + r * pitch
        cell = (x0, y0, x0 + CELL_W, y0 + CELL_H)
        draw.rectangle(cell, fill=BG)
        if img is not None:
            paste_centered(canvas, img, cell)
        else:
            draw.text(((cell[0] + cell[2]) // 2, (cell[1] + cell[3]) // 2),
                      "LOAD FAILED", font=fonts["label"], fill=ERR, anchor="mm")
        stem = os.path.splitext(name)[0]
        draw.text((x0 + CELL_W // 2, y0 + CELL_H + LABEL_H // 2), stem,
                  font=fonts["label"], fill=WHITE, anchor="mm")

    path = os.path.join(out_dir, group + ".png")
    canvas.save(path, "PNG", optimize=True)
    return path


def build_overview(all_items, out_dir, fonts):
    row_pitch = SMALL_H + 2 * SMALL_ROW_PAD
    width = (MARGIN + SMALL_LABEL_W + SMALL_PER_ROW * SMALL_SLOT_W
             + (SMALL_PER_ROW - 1) * GAP + MARGIN)
    height = MARGIN + len(all_items) * row_pitch + MARGIN

    canvas = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(canvas)
    for r, (group, items) in enumerate(all_items):
        y0 = MARGIN + r * row_pitch
        mid = y0 + row_pitch // 2
        draw.text((MARGIN, mid - 12), group, font=fonts["row"], fill=WHITE, anchor="lm")
        draw.text((MARGIN, mid + 16), "%d images" % len(items), font=fonts["label"],
                  fill=WHITE, anchor="lm")
        shown = [it for it in items if it[1] is not None][:SMALL_PER_ROW]
        for c, (_name, img, _err) in enumerate(shown):
            x0 = MARGIN + SMALL_LABEL_W + c * (SMALL_SLOT_W + GAP)
            top = y0 + SMALL_ROW_PAD
            paste_centered(canvas, img, (x0, top, x0 + SMALL_SLOT_W, top + SMALL_H))

    path = os.path.join(out_dir, "_all_small.png")
    canvas.save(path, "PNG", optimize=True)
    return path


def main(argv):
    if len(argv) != 3:
        print("usage: contact_sheet.py <refs_dir> <out_dir>", file=sys.stderr)
        return 2
    refs = os.path.abspath(argv[1])
    out_dir = os.path.abspath(argv[2])
    os.makedirs(out_dir, exist_ok=True)
    fonts = load_fonts()

    all_items = []
    for group in GROUPS:
        items = load_group(refs, group)
        all_items.append((group, items))
        path = build_group_sheet(group, items, out_dir, fonts)
        n_ok = sum(1 for _, img, _ in items if img is not None)
        print("%s: %d found, %d loaded -> %s" % (group, len(items), n_ok, path))
        for name, img, err in items:
            if img is None:
                print("  FAILED %s: %s" % (name, err))

    print("overview -> " + build_overview(all_items, out_dir, fonts))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
