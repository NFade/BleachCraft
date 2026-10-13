# Atlas layouts + palettes for the Gin / Uryu models (pure python, shared by gu_build.py (Blender) and gu_paint.py (system python)).
# Pixel coordinates: origin top-left. Rect = (x, y, w, h). Both atlases are 128 x 128.
# UV convention of gu_build.uv_rect(): u, v in 0..1 over the rect, v = 0 at the TOP row of the rect (image space).
ATLAS = 128

# ---- Gin (gin_shinso_atlas) ---------------------------------------------------------------------
GIN = {
    "wrap":    (0, 0, 36, 80),     # tsuka lacing: x = around the 12 sides (3 px each), y = along the grip (0 = kashira end)
    "blade":   (40, 0, 88, 8),     # both flat sides: x = along the blade, y = edge (top row) -> spine (bottom row); kissaki at the right end
    "spine":   (40, 10, 88, 3),    # spine (mune) strip
    "saya":    (40, 16, 88, 12),   # saya lacquer: x = along, y = around the 8 sides
    "ray":     (0, 84, 88, 8),     # gin_shinso_ray: x = along (0 = base, 1 = tip), y = across
}
# 4-px tone cells: shadow, base, light, highlight. Faces are assigned a cell from their normal (see gu_build.pick_cell)
GIN_CELLS = {
    "metal": [(0, 100, 4, 4), (4, 100, 4, 4), (8, 100, 4, 4), (12, 100, 4, 4)],     # silver-grey steel (tsuba swirls, fittings)
}

# ---- Uryu (uryu_atlas) --------------------------------------------------------------------------
URYU = {
    "bow":     (0, 0, 110, 12),    # x = along the bow (z -0.55 .. +0.55), y = around the 6 sides
    "shaft":   (0, 14, 80, 8),     # arrow shaft: x = along (tail -> head), y = around the 4 sides
    "string":  (0, 24, 40, 4),     # bow string strip
}
URYU_CELLS = {
    "reishi": [(0, 100, 4, 4), (4, 100, 4, 4), (8, 100, 4, 4), (12, 100, 4, 4)],     # pale blue reishi (arrowhead, fins, seele)
    "silver": [(20, 100, 4, 4), (24, 100, 4, 4), (28, 100, 4, 4), (32, 100, 4, 4)],  # Quincy cross pendant silver
}

PAL = {
    # Gin
    "wrap_lace": "#8EC3E8", "wrap_hi": "#B4DBF4", "wrap_lo": "#5E96C0", "wrap_win": "#DCEBF5", "wrap_win_lo": "#A9C6DC",
    "metal": ["#4F5663", "#7C8493", "#A9B2C2", "#E4EAF4"],
    "steel": "#B9C3D2", "steel_hi": "#D1D9E8", "steel_edge": "#EEF3FA", "steel_lo": "#8E99AB", "steel_temper": "#E4EAF4",
    "spine": "#7A8496",
    "saya": "#232630", "saya_hi": "#3C4150", "saya_lo": "#171921",
    "ray": "#F4F8FF", "ray_core": "#FFFFFF", "ray_edge": "#BFE4FF",
    # Uryu
    "reishi": ["#6FA8D8", "#A9D8F5", "#D8F0FF", "#FFFFFF"],
    "silver": ["#6B7686", "#B9C3D2", "#E4EAF4", "#FFFFFF"],
    "bow": "#A9D8F5", "bow_hi": "#D8F0FF", "bow_lo": "#6FA8D8", "bow_core": "#FFFFFF",
    "shaft": "#C4E6FA", "shaft_hi": "#FFFFFF", "shaft_lo": "#8EC4EC",
    "string": "#7FD0FF",
}
# tone intensity of the emissive map (RGBA: rgb white, alpha = intensity), per tone cell
EMIT_CELLS = {"reishi": [0.45, 0.70, 0.90, 1.00], "silver": [0, 0, 0, 0], "metal": [0, 0, 0, 0]}
