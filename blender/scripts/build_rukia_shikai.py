# Phase 3 steps 2-3: detailed rukia_shikai (geometry + UVs into the 256x256 atlas). Re-runnable; builds from scratch.
# Objects: rukia_shikai_blade (hilt, pommel knot, snowflake tsuba, habaki, blade; one mesh) and rukia_shikai_ribbon_01..10
# (12-tri trapezoid prisms, origin = hinge, mesh along local -Z, rotation 0). Gate B: B1, B2, B5, B6, B7, B8.
import sys, os, math, importlib
SD = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts"
if SD not in sys.path:
    sys.path.insert(0, SD)
import bb_common as bb, atlas_layouts as al, detail_common as dc
for _m in (bb, al, dc):
    importlib.reload(_m)
from mathutils import Vector

MODEL = "rukia_shikai"
L = al.RUKIA_SHIKAI
OUT = os.path.join(dc.EXPORT, MODEL)
os.makedirs(OUT, exist_ok=True)

bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")
mat = dc.atlas_material(MODEL, os.path.join(OUT, MODEL + "_diffuse.png"), os.path.join(OUT, MODEL + "_emissive.png"),
                        tint="#BFE4FF", strength=1.0, cutout=True)

# ---- rukia_shikai_blade
mb = dc.MB()
dc.build_hilt(mb, L)
# Gate B B8: knot block 14 (X) x 8 (Y) x 10 mm below the kashira (z -0.010..0), 1 mm chamfer on the free end and the vertical edges
dc.cprism(mb, 0.007, 0.004, 0.001, 0.0, -0.010, 0.001, L["knot_side"], L["knot_bottom"], 500.0, k0=3)
# Gate B B5: snowflake tsuba 88 mm, rim 7, hub 36, 48 segment outline, six windows with rounded corners, 0.8 mm rim bevel
outline, wins = al.snowflake_shapes()
dc.plate(mb, outline, wins, 0.250, 0.256, 0.0008, L, L["tsuba_front"], L["tsuba_back"], L["tsuba_rim"], L["tsuba_win"],
         al.SHIKAI_TSUBA_PX_PER_M)
dc.cprism(mb, 0.005, 0.016, 0.001, 0.256, 0.284, 0.0, L["habaki_side"], L["habaki_top"], 430.0, k0=3)
apex = dc.blade_mesh(mb, L, 0.284, 1.036 - 0.284, 0.028, 0.020, 0.009, 0.004, 0.070, 0.008, al.BLADE_RINGS_BODY, al.SHIKAI_BLADE_PX_PER_M)
sword = mb.finish("rukia_shikai_blade", mat, exp)

# ---- ribbon: 10 segments of exactly 0.25 m (Gate B B6: no overlap), 60 -> 44 mm wide (Gate C C3), 2 mm thick, 12 tris each (B7)
SEG, W0, W1, T, ROOT = 0.25, 0.060, 0.044, 0.002, -0.010    # Gate C C3: 60 -> 44 mm (was 40 -> 28)
width = lambda d: W0 + (W1 - W0) * d / (10 * SEG)
ribbons = []
for n in range(1, 11):
    cell = al.ribbon_cell(n)
    d0 = (n - 1) * SEG
    w0, w1 = width(d0), width(d0 + SEG)
    m2 = dc.MB()
    A = [m2.v(Vector(p)) for p in ((-w0 / 2, T / 2, 0), (w0 / 2, T / 2, 0), (w1 / 2, T / 2, -SEG), (-w1 / 2, T / 2, -SEG))]
    B = [m2.v(Vector(p)) for p in ((-w0 / 2, -T / 2, 0), (w0 / 2, -T / 2, 0), (w1 / 2, -T / 2, -SEG), (-w1 / 2, -T / 2, -SEG))]
    uvq = [cell.uv(0.0, 0.0), cell.uv(0.0, 1.0), cell.uv(1.0, 1.0), cell.uv(1.0, 0.0)]    # x -w/2 -> row bottom, x +w/2 -> top
    # corner order A: 0 (-,start) 1 (+,start) 2 (+,end) 3 (-,end)
    uv_c = [cell.uv(0.0, 0.0), cell.uv(0.0, 1.0), cell.uv(1.0, 1.0), cell.uv(1.0, 0.0)]
    m2.face([A[0], A[1], A[2], A[3]], uv_c)                      # front (+Y)
    m2.face([B[0], B[1], B[2], B[3]], uv_c)                      # back (-Y), same cell (double sided strip)
    # side walls collapse on the bottom / top edge row, end walls on the last / first texel column (swallow tail notch is transparent there)
    m2.face([A[0], A[3], B[3], B[0]], [cell.uv(0.0, 0.0), cell.uv(1.0, 0.0), cell.uv(1.0, 0.0), cell.uv(0.0, 0.0)])
    m2.face([A[1], A[2], B[2], B[1]], [cell.uv(0.0, 1.0), cell.uv(1.0, 1.0), cell.uv(1.0, 1.0), cell.uv(0.0, 1.0)])
    m2.face([A[0], A[1], B[1], B[0]], [cell.uv(0.02, 0.5)] * 4)
    m2.face([A[3], A[2], B[2], B[3]], [cell.uv(0.98, 0.5)] * 4)
    o = m2.finish("rukia_shikai_ribbon_%02d" % n, mat, exp)
    o.location = (0.0, 0.0, ROOT - (n - 1) * SEG)
    ribbons.append(o)

emp = dc.set_empties(exp, [("grip_hand", (0, 0, 0.19)), ("ribbon_root", (0, 0, ROOT)), ("tip", tuple(apex))])
result = {"apex": tuple(apex), "tris": {o.name: sum(len(p.vertices) - 2 for p in o.data.polygons) for o in [sword] + ribbons}}
