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

    "byakuya_sealed": dict(
        layout=al.BYAKUYA_SEALED,
        material="byakuya_sealed_atlas",
        objects={
            "byakuya_sealed_drawn": dict(budget=3200, target=1900, bbox_min=(-0.028, -0.046, 0.0), bbox_max=(0.028, 0.0864, 0.9785), tol=0.003, origin=(0, 0, 0)),
            "byakuya_sealed_sheathed": dict(budget=2800, target=2300, bbox_min=(-0.028, -0.046, 0.0), bbox_max=(0.028, 0.1131, 1.0024), tol=0.003, origin=(0, 0, 0)),
        },
        empties={"grip_hand": (0, 0, 0.19), "tip": (0, 0.0864, 0.9785)},
        export_objects=["byakuya_sealed_sheathed", "byakuya_sealed_drawn"],
        export_empties=["grip_hand", "tip"],
        turntable=[dict(label="drawn, full", objs=["byakuya_sealed_drawn"], z0=-0.03, z1=1.0, w=160, h=640, cy=0.03),
                   dict(label="sheathed, full", objs=["byakuya_sealed_sheathed"], z0=-0.03, z1=1.03, w=160, h=640, cy=0.05),
                   dict(label="drawn, hilt and tsuba close-up", objs=["byakuya_sealed_drawn"], z0=-0.01, z1=0.33, w=320, h=320),
                   dict(label="drawn, tsuba from above (camera elevation 50 deg)", objs=["byakuya_sealed_drawn"], z0=0.19, z1=0.33, w=320, h=320, elev=50.0)],
    ),

    "byakuya_shikai": dict(
        layout=al.BYAKUYA_SHIKAI,
        material="byakuya_shikai_atlas",
        objects={
            "byakuya_shikai_hilt": dict(budget=2000, target=1400, bbox_min=(-0.028, -0.046, 0.0), bbox_max=(0.028, 0.046, 0.285), tol=0.001, origin=(0, 0, 0)),
            "byakuya_shikai_petal": dict(budget=20, target=16, bbox_min=(0.198, -0.009, 0.0), bbox_max=(0.202, 0.017, 0.120), tol=0.0006, origin=(0.20, 0, 0)),
            "byakuya_shikai_shard": dict(budget=12, target=8, bbox_min=(0.2985, -0.011, 0.0303), bbox_max=(0.3015, 0.009, 0.0833), tol=0.0006, origin=(0.30, 0, 0.05)),
        },
        empties={"grip_hand": (0, 0, 0.19), "tang_tip": (0, 0, 0.285)},
        export_objects=["byakuya_shikai_hilt", "byakuya_shikai_petal", "byakuya_shikai_shard"],
        export_empties=["grip_hand", "tang_tip"],
        turntable=[dict(label="hilt, full", objs=["byakuya_shikai_hilt"], z0=-0.02, z1=0.31, w=320, h=320),
                   dict(label="hilt, tsuba and habaki close-up", objs=["byakuya_shikai_hilt"], z0=0.19, z1=0.31, w=320, h=320, cy=0.0),
                   dict(label="hilt, tsuba from above (camera elevation 50 deg)", objs=["byakuya_shikai_hilt"], z0=0.19, z1=0.33, w=320, h=320, elev=50.0),
                   dict(label="petal blade (0.12 m, real size), camera elevation 0", objs=["byakuya_shikai_petal"], z0=-0.006, z1=0.126, w=200, h=400, cx=0.20, cy=0.004),
                   dict(label="petal blade from above (camera elevation 50 deg)", objs=["byakuya_shikai_petal"], z0=-0.006, z1=0.126, w=200, h=400, cx=0.20, cy=0.004, elev=50.0),
                   dict(label="shard (0.05 m, real size)", objs=["byakuya_shikai_shard"], z0=0.025, z1=0.09, w=320, h=320, cx=0.30, cy=0.0)],
    ),
}
