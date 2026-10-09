# Per-model specs shared by verify_model.py, export_model.py and the turntable runs (pure python).
import atlas_layouts as al

SPECS = {
    "rukia_sealed": dict(
        layout=al.RUKIA_SEALED,
        material="rukia_sealed_atlas",
        objects={
            "rukia_sealed_drawn": dict(budget=3000, target=1800, bbox_min=(-0.031, -0.036, 0.0), bbox_max=(0.031, 0.0864, 0.9785), tol=0.003, origin=(0, 0, 0)),
            "rukia_sealed_sheathed": dict(budget=2500, target=2000, bbox_min=(-0.031, -0.036, 0.0), bbox_max=(0.031, 0.1131, 1.0024), tol=0.003, origin=(0, 0, 0)),
        },
        empties={"grip_hand": (0, 0, 0.19), "tip": (0, 0.0864, 0.9785)},
        export_objects=["rukia_sealed_sheathed", "rukia_sealed_drawn"],
        export_empties=["grip_hand", "tip"],
        turntable=[dict(label="drawn, full", objs=["rukia_sealed_drawn"], z0=-0.03, z1=1.0, w=160, h=640, cy=0.03),
                   dict(label="sheathed, full", objs=["rukia_sealed_sheathed"], z0=-0.03, z1=1.03, w=160, h=640, cy=0.05),
                   dict(label="drawn, hilt and tsuba close-up", objs=["rukia_sealed_drawn"], z0=-0.01, z1=0.33, w=320, h=320)],
    ),
}
