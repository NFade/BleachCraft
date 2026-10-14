"""Reproject a blade row layout onto refs/byakuya_bankai_3.png (anime ep.52) and report the pixel error of the bases.

Run:  python -I tools/overlay_bankai_rows.py [--preset book|dense|anime|r7|r9] [--half 3.25] [--start -0.85] [--pitch 1.5]
                                              [--height 4.95] [--fov 50] [--per-side 14] [--out path.png] [--image path.png]

The camera is the fit of tools/measure_bankai_rows.py (vanishing point, eye height from Byakuya, focal length for --fov).
A blade of side s (-1 left, +1 right), index k (0 = nearest) stands at along = start - pitch * k, across = s * half, so its depth from the
camera is Z = Zb - along (Zb = depth of Byakuya, the rows run behind him, away from the camera) and
    x = vpx + f * across / Z,   y_base = vpy + f * eye / Z,   y_top = vpy + f * (eye - height) / Z.
The camera is shifted sideways by the measured asymmetry of the two rows (about 0.2 blocks). The error is the distance in pixels between the predicted and the measured base of the nearest 8 blades per side. The focal length is not
measurable from one frame, so the script also prints the field of view (and error) that makes the given pitch fit best: for a preset
that is the lens in which the preset would look exactly like the frame.
"""
import argparse
import math
import os
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import measure_bankai_rows as mb  # noqa: E402

PRESETS = {  # key: (perSide, halfWidth, pitch, startAhead), same numbers as BladeRowPresets.java
	"dense": (16, 3.25, 1.0, -0.85),
	"book": (14, 3.25, 1.5, -0.85),
	"anime": (12, 3.25, 2.1, -0.85),
	"r7": (10, 7.0, 1.0, -2.0),
	"r9": (12, 3.7, 2.0, -1.0),
}
IMG_W = 1249.0
NEAR = 8


def camera(path, fov):
	res = mb.fit(path, fov)
	vpx, vpy = res["vp"][0], res["yh"]
	eye = res["eye_height_blocks"]
	f = (IMG_W / 2.0) / math.tan(math.radians(fov) / 2.0)
	zb = f * eye / (mb.BODY_PX[1] - vpy)
	return vpx, vpy, eye, f, zb, res


def predict(layout, cam, fov_f=None, shift=0.0):
	"""Predicted (x, y_base, y_top) per side, nearest first. layout = (perSide, half, pitch, start, height)."""
	vpx, vpy, eye, f, zb, _ = cam
	if fov_f is not None:
		f = fov_f
		zb = f * eye / (mb.BODY_PX[1] - vpy)
	per, half, pitch, start, height = layout
	out = {}
	for s in (-1, 1):
		pts = []
		for k in range(per):
			along = start - pitch * k
			z = zb - along
			pts.append((vpx + f * (s * half - shift) / z, vpy + f * eye / z, vpy + f * (eye - height) / z))
		out[s] = pts
	return out


def errors(pred, meas_bases):
	"""meas_bases: {side: [(x, y_base)]} nearest first. Mean Euclid distance over the nearest NEAR blades per side, plus x and y parts."""
	d = []
	dx = []
	dy = []
	for s in (-1, 1):
		for k in range(min(NEAR, len(meas_bases[s]), len(pred[s]))):
			mx, my = meas_bases[s][k]
			px, py, _ = pred[s][k]
			d.append(math.hypot(px - mx, py - my))
			dx.append(abs(px - mx))
			dy.append(abs(py - my))
	return float(np.mean(d)), float(np.mean(dx)), float(np.mean(dy)), len(d)


def main():
	ap = argparse.ArgumentParser()
	ap.add_argument("--preset", default="book")
	ap.add_argument("--half", type=float)
	ap.add_argument("--start", type=float)
	ap.add_argument("--pitch", type=float)
	ap.add_argument("--height", type=float, default=4.95)
	ap.add_argument("--per-side", type=int)
	ap.add_argument("--fov", type=float, default=50.0)
	ap.add_argument("--image", default="D:/MineBleach/refs/byakuya_bankai_3.png")
	ap.add_argument("--out", default=os.path.join(tempfile.gettempdir(), "bankai_overlay.png"))
	a = ap.parse_args()
	per, half, pitch, start = PRESETS.get(a.preset, PRESETS["book"])
	per = a.per_side or per
	half = a.half if a.half is not None else half
	pitch = a.pitch if a.pitch is not None else pitch
	start = a.start if a.start is not None else start
	layout = (per, half, pitch, start, a.height)

	cam = camera(a.image, a.fov)
	res = cam[5]
	bl = mb.blades(a.image)
	xc = IMG_W / 2.0
	meas = {
		-1: [(b[0], b[1]) for b in sorted([b for b in bl if b[0] < xc], key=lambda b: -b[1])],
		1: [(b[0], b[1]) for b in sorted([b for b in bl if b[0] >= xc], key=lambda b: -b[1])],
	}
	# the camera of the shot is not exactly on the row axis (left rows 3.45 out, right 3.0): lateral camera shift, blocks, camera this far right (+) of the row axis
	shift = -(float(np.mean([p["x_block"] for p in res["left"][:NEAR]])) + float(np.mean([p["x_block"] for p in res["right"][:NEAR]]))) / 2.0
	pred = predict(layout, cam, None, shift)
	e, ex, ey, n = errors(pred, meas)
	print("layout per_side=%d half=%.2f start=%.2f pitch=%.2f height=%.2f  fov=%.0f" % (*layout, a.fov))
	print("camera vp=(%.1f, %.1f) eye=%.2f f=%.0f px Zb=%.2f lateral shift %.2f blocks" % (cam[0], cam[1], cam[2], cam[3], cam[4], shift))
	print("mean base error (nearest %d per side, %d blades): %.1f px  (x %.1f, y %.1f)" % (NEAR, n, e, ex, ey))
	best = None
	for fov10 in range(200, 1300, 5):
		fov = fov10 / 10.0
		f = (IMG_W / 2.0) / math.tan(math.radians(fov) / 2.0)
		err = errors(predict(layout, cam, f, shift), meas)[0]
		if best is None or err < best[1]:
			best = (fov, err)
	print("best fitting field of view for this pitch: %.1f deg, mean base error %.1f px" % best)

	im = Image.open(a.image).convert("RGB")
	dr = ImageDraw.Draw(im)
	for s in (-1, 1):
		for k, (x, yb, yt) in enumerate(pred[s]):
			col = (255, 60, 60) if k < NEAR else (255, 170, 60)
			dr.line([(x, yb), (x, yt)], fill=(255, 220, 0), width=1)
			dr.ellipse([x - 4, yb - 4, x + 4, yb + 4], outline=col, width=2)
			dr.ellipse([x - 3, yt - 3, x + 3, yt + 3], outline=(60, 160, 255), width=2)
		for k, (x, y) in enumerate(meas[s][:NEAR]):
			dr.line([(x - 6, y - 6), (x + 6, y + 6)], fill=(0, 255, 0), width=2)
			dr.line([(x - 6, y + 6), (x + 6, y - 6)], fill=(0, 255, 0), width=2)
	dr.text((10, 10), "red/blue circles: predicted bases/tops, green x: measured bases", fill=(255, 255, 255))
	dr.text((10, 24), "%s  pitch %.2f  half %.2f  start %.2f  fov %.0f  err %.1f px" % (a.preset, pitch, half, start, a.fov, e), fill=(255, 255, 255))
	im.save(a.out)
	print("overlay written to", a.out)


if __name__ == "__main__":
	main()
