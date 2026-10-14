"""Blockbench drops the per-keyframe "easing" field on save. Copy it back from a reference .bbmodel by (bone, channel, time).

  python -I tools/bbmodel_restore_easing.py edited.bbmodel reference.bbmodel --animation uryu_bow_draw [--default INOUTSINE]

Keys with no match in the reference get --default (their interpolation is kept). Rewrites `edited` in place and lists the unmatched keys.
"""
import argparse
import json


def keys(path, anim):
    d = json.load(open(path, encoding="utf-8"))
    a = [x for x in d["animations"] if x["name"] == anim][0]
    return d, a


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("edited")
    ap.add_argument("reference")
    ap.add_argument("--animation", required=True)
    ap.add_argument("--default", default="INOUTSINE")
    args = ap.parse_args()
    _, ref = keys(args.reference, args.animation)
    table = {}
    for an in ref["animators"].values():
        for k in an.get("keyframes", []):
            if k.get("easing"):
                table[(an["name"], k["channel"], round(k["time"], 3))] = k["easing"]
    d, a = keys(args.edited, args.animation)
    unmatched = []
    for an in a["animators"].values():
        for k in an.get("keyframes", []):
            key = (an["name"], k["channel"], round(k["time"], 3))
            if key in table:
                k["easing"] = table[key]
            else:
                k["easing"] = args.default
                unmatched.append(key)
    json.dump(d, open(args.edited, "w", encoding="utf-8"), indent=1)
    print("restored", len(table), "easing values, defaulted", len(unmatched))
    for u in unmatched:
        print("  default:", u)


if __name__ == "__main__":
    main()
