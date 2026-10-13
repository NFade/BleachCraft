#!/usr/bin/env python3
"""Plot the strike curves (what the game evaluates) to design/results/slash_curves.png.

Top: body tracks with their easing names (sparse keys, real playerAnimator easing). Middle: blade azimuth/elevation and
tip speed (baked per tick, the arms are IK-bound). Bottom: phases. Matplotlib is only needed here.
"""
import math
import os
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import slash_solver as s  # noqa: E402

PHASES = [(0, 4, "wind-up\nease in-out", "#dde6f5"), (4, 12, "strike\nease in (accelerates)", "#f7e3d3"),
          (12, 17, "hit end / follow-through\nease out", "#e3f3df"), (17, 20, "recovery\nease in-out", "#e9e1f3")]


def shade(ax, label=False):
    for a, b, name, col in PHASES:
        ax.axvspan(a, b, color=col, alpha=0.7, lw=0)
        if label:
            ax.text((a + b) / 2, 1.02, name, transform=ax.get_xaxis_transform(), ha="center", va="bottom", fontsize=8)
    ax.axvline(s.CONTACT_TICK, color="#cc3333", lw=1, ls="--")
    ax.set_xlim(0, s.END_TICK)
    ax.grid(alpha=0.3)


def main():
    ticks = [i / 4.0 for i in range(0, 4 * s.END_TICK + 1)]
    rows = s.table()
    fig, axes = plt.subplots(3, 1, figsize=(12, 11), sharex=True)

    ax = axes[0]
    shade(ax, label=True)
    for bone, comp, label, col in (("torso", 1, "torso yaw (deg)", "#c0392b"), ("torso", 0, "torso lean (deg, - = forward)", "#e67e22"),
                                   ("rightLeg", 0, "right leg (deg, + = forward)", "#2471a3"),
                                   ("leftLeg", 0, "left leg", "#7fb3d5"), ("head", 0, "head nod (deg)", "#27ae60")):
        ax.plot(ticks, [s.eval_track(bone, t)[comp] for t in ticks], color=col, label=label, lw=2)
        for tick, _v, ease in s.TRACKS[bone]:
            ax.plot([tick], [s.eval_track(bone, tick)[comp]], "o", color=col, ms=5)
    # easing names on the torso track
    keys = s.TRACKS["torso"]
    for (t0, v0, _), (t1, v1, ease) in zip(keys, keys[1:]):
        ax.annotate(ease, ((t0 + t1) / 2, s.eval_track("torso", (t0 + t1) / 2)[1]), fontsize=7, color="#c0392b",
                    xytext=(0, 8), textcoords="offset points", ha="center")
    ax.set_ylabel("body tracks (dots = keyframes)")
    ax.legend(loc="lower left", fontsize=8, ncol=2)

    ax = axes[1]
    shade(ax)
    az = [math.degrees(math.atan2(-r["blade"][0], -r["blade"][2])) for r in rows]
    el = [math.degrees(math.asin(r["blade"][1])) for r in rows]
    ax.plot([r["tick"] for r in rows], az, color="#8e44ad", lw=2, label="blade azimuth (deg, + = left)")
    ax.plot([r["tick"] for r in rows], el, color="#16a085", lw=2, label="blade elevation (deg)")
    ax.set_ylabel("blade (baked every tick)")
    ax.legend(loc="upper left", fontsize=8)

    ax = axes[2]
    shade(ax)
    tip = lambda r: s.add(r["hand_r"], s.mul(r["blade"], s.BLADE_LEN))
    sp = [s.norm(s.mul(s.sub(tip(rows[i + 1]), tip(rows[i - 1])), 0.5)) for i in range(1, len(rows) - 1)]
    ax.plot([r["tick"] for r in rows][1:-1], sp, color="#d35400", lw=2, marker="o", ms=4, label="blade tip speed (px per tick)")
    ax.set_ylabel("speed")
    ax.set_xlabel("tick (20 per second), red dashed = contact / Better Combat tipping point (upswing 0.6)")
    ax.legend(loc="upper left", fontsize=8)

    out = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "design", "results", "slash_curves.png"))
    fig.tight_layout()
    fig.savefig(out, dpi=110)
    print("wrote", out)


if __name__ == "__main__":
    main()
