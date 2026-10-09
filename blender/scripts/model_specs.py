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
                   dict(label="drawn, hilt and tsuba close-up", objs=["rukia_sealed_drawn"], z0=-0.01, z1=0.33, w=320, h=320),
                   dict(label="drawn, tsuba from above (camera elevation 50 deg)", objs=["rukia_sealed_drawn"], z0=0.19, z1=0.33, w=320, h=320, elev=50.0)],
    ),

    "rukia_shikai": dict(
        layout=al.RUKIA_SHIKAI,
        material="rukia_shikai_atlas",
        objects=dict(
            [("rukia_shikai_blade", dict(budget=3000, target=2000, bbox_min=(-0.044, -0.044, -0.010), bbox_max=(0.044, 0.044, 1.0355), tol=0.003, origin=(0, 0, 0)))] +
            [("rukia_shikai_ribbon_%02d" % n, dict(budget=12, target=12, origin=(0, 0, -0.010 - 0.25 * (n - 1)),
                                                   bbox_min=(-(0.040 - 0.012 * (n - 1) / 10) / 2, -0.001, -0.010 - 0.25 * n),
                                                   bbox_max=((0.040 - 0.012 * (n - 1) / 10) / 2, 0.001, -0.010 - 0.25 * (n - 1)), tol=0.0006))
             for n in range(1, 11)]),
        empties={"grip_hand": (0, 0, 0.19), "ribbon_root": (0, 0, -0.010), "tip": (0, 0.038, 1.0355)},
        export_objects=["rukia_shikai_blade"] + ["rukia_shikai_ribbon_%02d" % n for n in range(1, 11)],
        export_empties=["grip_hand", "ribbon_root", "tip"],
        turntable=[dict(label="blade and hilt, full", objs=["rukia_shikai_blade"], z0=-0.05, z1=1.05, w=170, h=640, cy=0.02),
                   dict(label="hilt, snowflake tsuba and knot close-up", objs=["rukia_shikai_blade"], z0=-0.03, z1=0.33, w=320, h=320),
                   dict(label="snowflake tsuba from above (camera elevation 50 deg)", objs=["rukia_shikai_blade"], z0=0.17, z1=0.33, w=320, h=320, elev=50.0),
                   dict(label="ribbon (10 segments, rest pose)", objs=["rukia_shikai_blade"] + ["rukia_shikai_ribbon_%02d" % n for n in range(1, 11)],
                        z0=-2.6, z1=0.1, w=96, h=640)],
    ),
}
