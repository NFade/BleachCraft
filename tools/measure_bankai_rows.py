"""Measure the blade rows of refs/byakuya_bankai_3.png (anime ep.52) with a ground-plane pinhole model.

Run:  python -I tools/measure_bankai_rows.py [image]   (prints a JSON with the fitted numbers)

Model: camera looks horizontally, eye height Hc above the floor. A floor point at depth Z and lateral X projects to
    y = yh + f*Hc/Z ,   x = xc + f*X/Z .
Byakuya (BODY blocks tall) fixes the scale: Hc = (feet_y - yh) / (body_px / BODY). Blade bases are the lowest bright pixel
of each blade. 1/(y - yh) is proportional to depth Z, so a uniform pitch is linear in it; yh is fitted by that linearity.
The focal length f is NOT measurable from one frame (only depth RATIOS are), so the absolute pitch is given for a
horizontal field of view (default 50 deg, sensitivity printed for 35..70).
"""
import json
import math
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

BODY = 1.8  # Byakuya in blocks (player height)
BODY_PX = (487.0, 785.0)  # hair top, feet (measured by eye on the 1249x937 frame)
XC = 1249 / 2.0


def blades(path, min_width=20):
	"""Blades as column runs of bright low-saturation pixels: (x_center, y_base, y_top, width). Only the near blades, which stand apart."""
	im = np.asarray(Image.open(path).convert("RGB")).astype(float)
	mx, mn = im.max(axis=2), im.min(axis=2)
	sat = (mx - mn) / np.maximum(mx, 1)
	mask = (mx > 110) & (sat < 0.35)
	mask[540:, 500:740] = False  # Byakuya himself
	on = mask.sum(axis=0) > 40
	out, s = [], None
	for x in range(mask.shape[1] + 1):
		cur = x < mask.shape[1] and on[x]
		if cur and s is None:
			s = x
		if not cur and s is not None:
			sub = mask[:, s:x]
			cnt = sub.sum(axis=1)
			rows = np.nonzero(cnt >= 3)[0]
			if x - s >= min_width:
				out.append(((s + x - 1) / 2.0, int(rows.max()), int(rows.min()), x - s))
			s = None
	return out


def fit(path, hfov_deg=50.0):
	bl = blades(path)
	left = sorted([b for b in bl if b[0] < XC], key=lambda b: -b[1])
	right = sorted([b for b in bl if b[0] >= XC], key=lambda b: -b[1])
	# vanishing point: bases and tops of each row lie on lines through it; least squares intersection of the four lines
	rows_ab = []
	for row in (left, right):
		xs = np.array([b[0] for b in row])
		for k in (1, 2):
			ys = np.array([b[k] for b in row], dtype=float)
			m, c = np.polyfit(xs, ys, 1)
			rows_ab.append((m, c))
	A = np.array([[-m, 1.0] for m, c in rows_ab])
	rhs = np.array([c for m, c in rows_ab])
	vp, *_ = np.linalg.lstsq(A, rhs, rcond=None)
	global XC
	xc_vp, yh = float(vp[0]), float(vp[1])
	res0 = {"vp": (round(xc_vp, 1), round(yh, 1))}
	px_per_block_body = (BODY_PX[1] - BODY_PX[0]) / BODY
	hc = (BODY_PX[1] - yh) / px_per_block_body
	f = (1249 / 2.0) / math.tan(math.radians(hfov_deg) / 2)
	res = {**res0, "yh": yh, "eye_height_blocks": round(hc, 2), "n_left": len(left), "n_right": len(right), "hfov_deg": hfov_deg}
	zb = f * hc / (BODY_PX[1] - yh)
	res["byakuya_depth_blocks"] = round(zb, 2)
	for name, row in (("left", left), ("right", right)):
		pts = []
		for x, yb, yt, _ in row:
			s = (yb - yh) / hc  # px per block at this depth
			z = f * hc / (yb - yh)
			pts.append({"x_block": round((x - xc_vp) / s, 2), "z_block": round(z, 2), "height_blocks": round((yb - yt) / s, 2), "base_y": yb})
		res[name] = pts
	allp = res["left"] + res["right"]
	res["half_width_median"] = round(float(np.median([abs(p["x_block"]) for p in allp[:12]])), 2)
	zs = sorted(p["z_block"] for p in res["left"])
	res["pitch_mean_near8"] = round(float(np.mean(np.diff(zs)[:8])), 2) if len(zs) > 8 else None
	res["first_blade_behind_feet"] = round(zs[0] - zb, 2) if zs else None
	res["blade_height_blocks_first"] = res["left"][0]["height_blocks"] if res["left"] else None
	return res


if __name__ == "__main__":
	p = sys.argv[1] if len(sys.argv) > 1 else "D:/MineBleach/refs/byakuya_bankai_3.png"
	out = {"main": fit(p)}
	out["fov_sensitivity"] = {str(v): {k: r[k] for k in ("pitch_mean_near8", "first_blade_behind_feet")} for v in (35, 50, 70) for r in [fit(p, v)]}
	print(json.dumps({k: (v if k != "main" else {kk: vv for kk, vv in v.items() if kk not in ("left", "right")}) for k, v in out.items()}, indent=1))
	
