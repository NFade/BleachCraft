"""R15 round 3: replaces the fletching of uryu_fx/uryu_arrow.obj with three small swept vanes.

The old fletching was three chevron planes through the arrow axis (0.08 wide at the tail), which read as a big angular six pointed star, most of all in
first person where the tail is close to the camera. The shaft and the head (vertices 1..41 and the faces before the vane quads) are kept; the vane
vertices 42..65, their 6 quads and nothing else are rewritten: 3 vanes (120 degrees apart) of one quad per side, root 0.004 from the axis, outer edge 0.013.
The tail of the shaft (y = 0.02) is the nock; the vanes start at y = 0.028, so nothing sticks out behind the nock.
usage: python -I tools/patch_uryu_arrow_fletching.py   (idempotent: rebuilds from the first 41 vertices and the first 20 faces)
"""
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PATH = ROOT / "mod/src/main/resources/assets/reiatsu_test/models/obj/uryu_fx/uryu_arrow.obj"

KEEP_V, KEEP_F = 41, 20
R0, R1 = 0.004, 0.013
Y_ROOT_TOP, Y_ROOT_BOT, Y_OUT_TOP, Y_OUT_BOT = 0.125, 0.030, 0.085, 0.028
VT_A, VT_B = 18, 16  # the two palette points the old vanes used


def main():
    lines = PATH.read_bytes().decode("ascii").replace("\r\n", "\n").split("\n")
    verts = [l for l in lines if l.startswith("v ")][:KEEP_V]
    faces = [l for l in lines if l.startswith("f ")][:KEEP_F]
    normals = [l for l in lines if l.startswith("vn ")][:18]  # the 18 the old file had; the vane normals are appended
    texcoords = [l for l in lines if l.startswith("vt ")]
    head = [l for l in lines if l.startswith("#") or l.startswith("o ")]
    new_v, new_vn, new_f = [], [], []
    for k in range(3):
        ang = math.radians(120.0 * k)
        dx, dz = math.cos(ang), math.sin(ang)
        pts = [(R0, Y_ROOT_TOP), (R0, Y_ROOT_BOT), (R1, Y_OUT_BOT), (R1, Y_OUT_TOP)]
        idx = []
        for r, y in pts:
            new_v.append("v %.6f %.6f %.6f" % (r * dx, y, r * dz))
            idx.append(KEEP_V + len(new_v))
        n = (-dz, 0.0, dx)  # perpendicular to the radial direction, in the horizontal plane
        for sign, order in ((1, idx), (-1, list(reversed(idx)))):
            new_vn.append("vn %.4f %.4f %.4f" % (sign * n[0], 0.0, sign * n[2]))
            ni = len(normals) + len(new_vn)
            tv = VT_A if (k + (0 if sign == 1 else 1)) % 2 == 0 else VT_B
            new_f.append("f " + " ".join("%d/%d/%d" % (i, tv, ni) for i in order))
    out = list(head) + verts + new_v + normals + new_vn + texcoords + ["s 0"] + faces + new_f
    PATH.write_bytes(("\r\n".join(out) + "\r\n").encode("ascii"))
    print("vertices", len(verts) + len(new_v), "faces", len(faces) + len(new_f))


if __name__ == "__main__":
    main()
