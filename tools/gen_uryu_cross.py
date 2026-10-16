"""Generates uryu_cross.obj (R14): a Quincy cross on a chain, hanging from the hand.

OBJ space (y up, like the other uryu meshes): the cross lies in the YZ plane (thin in x). The origin is the grip: the chain starts there and the
cross hangs below it (negative y). Palette points of uryu_diffuse (v flipped): 1 light silver, 2 dark steel, 3 mid silver, 4 white.
"""
import math
import sys

VT = [(0.234375, 0.203125), (0.171875, 0.203125), (0.203125, 0.203125), (0.265625, 0.203125)]
LIGHT, DARK, MID, WHITE = 1, 2, 3, 4

verts = []
norms = []
faces = []  # (list of (vi, ti, ni))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def norm(v):
    l = math.sqrt(v[0] ** 2 + v[1] ** 2 + v[2] ** 2) or 1.0
    return (v[0] / l, v[1] / l, v[2] / l)


def add_vert(p):
    verts.append(p)
    return len(verts)


def add_norm(n):
    norms.append(n)
    return len(norms)


CENTER = [0.0, 0.0, 0.0]


def tri(p0, p1, p2, t):
    n = norm(cross(sub(p1, p0), sub(p2, p0)))
    c = ((p0[0] + p1[0] + p2[0]) / 3 - CENTER[0], (p0[1] + p1[1] + p2[1]) / 3 - CENTER[1], (p0[2] + p1[2] + p2[2]) / 3 - CENTER[2])
    if n[0] * c[0] + n[1] * c[1] + n[2] * c[2] < 0:
        p1, p2 = p2, p1  # wind so that the normal points away from the piece
        n = (-n[0], -n[1], -n[2])
    ni = add_norm(n)
    faces.append([(add_vert(p0), t, ni), (add_vert(p1), t, ni), (add_vert(p2), t, ni)])


def quad(p0, p1, p2, p3, t):
    tri(p0, p1, p2, t)
    tri(p0, p2, p3, t)


def prism(poly, x0, x1, t_front, t_side):
    """Convex polygon in (y, z) extruded from x0 to x1. poly is counter clockwise seen from +x."""
    n = len(poly)
    cy = sum(p[0] for p in poly) / n
    cz = sum(p[1] for p in poly) / n
    CENTER[:] = [(x0 + x1) / 2, cy, cz]
    for i in range(n):
        a = poly[i]
        b = poly[(i + 1) % n]
        tri((x1, cy, cz), (x1, a[0], a[1]), (x1, b[0], b[1]), t_front)
        tri((x0, cy, cz), (x0, b[0], b[1]), (x0, a[0], a[1]), t_front)
        quad((x0, a[0], a[1]), (x0, b[0], b[1]), (x1, b[0], b[1]), (x1, a[0], a[1]), t_side)


def box(cx, cy, cz, sx, sy, sz, t_front, t_side):
    """Axis aligned box, sx along x (thickness), sy along y, sz along z."""
    poly = [(cy - sy / 2, cz - sz / 2), (cy - sy / 2, cz + sz / 2), (cy + sy / 2, cz + sz / 2), (cy + sy / 2, cz - sz / 2)]
    # counter clockwise seen from +x: (y,z) with y up and z to the left... the orientation only flips the shading normal, fixed by the winding check below
    prism(poly, cx - sx / 2, cx + sx / 2, t_front, t_side)


def ring(cy, r_out, r_in, x0, x1, segs, t_front, t_side):
    """A flat ring in the YZ plane."""
    for i in range(segs):
        a0 = 2 * math.pi * i / segs
        a1 = 2 * math.pi * (i + 1) / segs
        o0 = (cy + r_out * math.cos(a0), r_out * math.sin(a0))
        o1 = (cy + r_out * math.cos(a1), r_out * math.sin(a1))
        i0 = (cy + r_in * math.cos(a0), r_in * math.sin(a0))
        i1 = (cy + r_in * math.cos(a1), r_in * math.sin(a1))
        CENTER[:] = [(x0 + x1) / 2, (o0[0] + o1[0] + i0[0] + i1[0]) / 4, (o0[1] + o1[1] + i0[1] + i1[1]) / 4]
        quad((x1, o0[0], o0[1]), (x1, o1[0], o1[1]), (x1, i1[0], i1[1]), (x1, i0[0], i0[1]), t_front)
        quad((x0, o0[0], o0[1]), (x0, i0[0], i0[1]), (x0, i1[0], i1[1]), (x0, o1[0], o1[1]), t_front)
        quad((x0, o0[0], o0[1]), (x0, o1[0], o1[1]), (x1, o1[0], o1[1]), (x1, o0[0], o0[1]), t_side)
        quad((x0, i0[0], i0[1]), (x1, i0[0], i0[1]), (x1, i1[0], i1[1]), (x0, i1[0], i1[1]), t_side)


# ------------------------------------------------------------------ chain: from the grip down, links alternate between the YZ and the XY plane
LINKS = 6
STEP = 0.0125
for i in range(LINKS):
    y = -0.010 - i * STEP
    if i % 2 == 0:
        ring_y = y
        # a link standing in the YZ plane: a small flat ring
        ring(ring_y, 0.0105, 0.0058, -0.0028, 0.0028, 8, DARK, MID)
    else:
        # a link standing in the XY plane: a box seen edge on from the front (a bar with a hole is the same silhouette)
        box(0.0, y, 0.0, 0.0105, 0.0185, 0.0042, DARK, MID)

# ------------------------------------------------------------------ the Quincy pentacle: five rods from a hub, a thin ring through them, a loop on the top rod
ROD = 0.064  # hub centre to rod tip
RING_R = 0.048  # radius of the ring
BAIL_R = 0.0075
TOP_TIP = -0.010 - LINKS * STEP - 0.001 - 2 * BAIL_R  # y of the tip of the top rod
BAIL_Y = TOP_TIP + BAIL_R * 0.9
CY = TOP_TIP - ROD  # hub centre
ring(BAIL_Y, BAIL_R, BAIL_R - 0.0028, -0.0025, 0.0025, 8, LIGHT, MID)
T = 0.0034  # half thickness of a rod


def rod(angle):
    """A rod from the hub to its tip at `angle` (radians, 0 = +z, pi/2 = +y): a shaft, a collar and a pointed cap."""
    uy, uz = math.sin(angle), math.cos(angle)
    py, pz = -uz, uy
    def pt(t, w):
        return (CY + uy * t + py * w, uz * t + pz * w)
    sh_w = 0.0032
    stations = [(0.0, sh_w), (ROD - 0.020, sh_w), (ROD - 0.017, 0.0046), (ROD - 0.009, 0.0046), (ROD - 0.0035, 0.0024), (ROD, 0.0010)]
    for i in range(len(stations) - 1):
        t0, w0 = stations[i]
        t1, w1 = stations[i + 1]
        poly = [pt(t0, -w0), pt(t0, w0), pt(t1, w1), pt(t1, -w1)]
        prism(order_ccw(poly), -T, T, LIGHT if i in (0, 3) else MID, MID if i not in (2,) else DARK)


def order_ccw(poly):
    cy = sum(p[0] for p in poly) / len(poly)
    cz = sum(p[1] for p in poly) / len(poly)
    return sorted(poly, key=lambda p: math.atan2(p[1] - cz, p[0] - cy))


# hub: a small octagon with a bright star-dot on both faces
prism(order_ccw([(CY + 0.0075 * math.sin(2 * math.pi * k / 8), 0.0075 * math.cos(2 * math.pi * k / 8)) for k in range(8)]), -T - 0.0006, T + 0.0006, MID, DARK)
for sgn in (1, -1):
    x = sgn * (T + 0.0009)
    quad((x, CY + 0.0028, 0.0), (x, CY, 0.0028 * sgn), (x, CY - 0.0028, 0.0), (x, CY, -0.0028 * sgn), WHITE)
for k in range(5):
    rod(math.pi / 2 + k * 2 * math.pi / 5)
# the thin ring that runs through the rods
ring(CY, RING_R + 0.0016, RING_R - 0.0016, -0.0016, 0.0016, 40, MID, DARK)


# ------------------------------------------------------------------ write
def check_winding():
    """Flip a triangle whose normal looks into the solid (we only have flat shaded convex pieces: the normal must point away from the piece centre)."""


out = ["# R14 Quincy cross on a chain (tools/gen_uryu_cross.py)", "o uryu_cross"]
for v in verts:
    out.append("v %.6f %.6f %.6f" % v)
for t in VT:
    out.append("vt %.6f %.6f" % t)
for n in norms:
    out.append("vn %.4f %.4f %.4f" % n)
out.append("s 0")
for f in faces:
    out.append("f " + " ".join("%d/%d/%d" % c for c in f))
path = sys.argv[1] if len(sys.argv) > 1 else "uryu_cross.obj"
with open(path, "w", newline="\n") as fh:
    fh.write("\n".join(out) + "\n")
print("verts", len(verts), "faces", len(faces), "y range", min(v[1] for v in verts), max(v[1] for v in verts), "z range", min(v[2] for v in verts), max(v[2] for v in verts))
