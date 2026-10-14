"""Unit tests of tools/gen_uryu_poses.py (run: python -I tools/test_gen_uryu_poses.py).

The poses are checked on the rig itself (forward kinematics of what the generator emits) and through the repository's exporter."""
import json
import math
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel_to_playeranim as exporter  # noqa: E402
import gen_uryu_poses as g  # noqa: E402
import slash_solver as s  # noqa: E402

ANIMS = [f() for f in g.ANIMS]
BY = {a.name: a for a in ANIMS}
MODEL = g.build_bbmodel(ANIMS)
EXPORTED = g.export_all(MODEL, ANIMS, g.OUT_DIR, write=False)


def sample(out, part, axis, tick):
    """Value of a move track at a tick, interpolated like playerAnimator does (easing of the segment that ends at the key)."""
    keys = sorted((m["tick"], m[part][axis], m["easing"]) for m in out["emote"]["moves"] if part in m and axis in m[part])
    if not keys:
        return None
    if tick <= keys[0][0]:
        return keys[0][1]
    for (t0, v0, _e0), (t1, v1, e1) in zip(keys, keys[1:]):
        if t0 <= tick <= t1:
            return v0 + (v1 - v0) * g.ease_u(e1, (tick - t0) / float(t1 - t0))
    return keys[-1][1]


class Rig(unittest.TestCase):
    def test_all_checks_pass(self):
        self.assertEqual(g.checks(ANIMS), [])

    def test_torso_yaw_stays_under_60_and_shoulders_near_the_body(self):
        for a in ANIMS:
            for t in range(a.end + 1):
                p = a.pose(t)
                self.assertLessEqual(abs(p["yaw"]), 60.0 + 1e-9, "%s %d" % (a.name, t))
                for side in a.arm_keys:
                    self.assertLessEqual(p[side]["shift"], g.MAX_SHIFT, "%s %d %s" % (a.name, t, side))

    def test_full_draw_string_hand_is_on_the_cheek_anchor(self):
        p = BY["draw"].pose(20)
        self.assertLess(s.norm(s.sub(p["l"]["fist"], g.ANCHOR)), 0.05)

    def test_full_draw_arrow_line_points_along_the_line_of_fire(self):
        p = BY["draw"].pose(20)
        arrow = s.unit(s.sub(p["r"]["fist"], p["l"]["fist"]))
        self.assertLess(abs(math.degrees(math.atan2(-arrow[0], -arrow[2]))), 12.0)
        self.assertLess(abs(math.degrees(math.asin(arrow[1]))), 8.0)

    def test_bow_arm_points_forward_at_full_draw_whatever_the_chest_does(self):
        # the chest is turned 55 degrees to the left, the bow arm is chest relative -55: in the entity frame it points straight ahead
        for t in (8, 12, 20):
            d = BY["draw"].pose(t)["r"]["dir"]
            self.assertAlmostEqual(d[0], 0.0, delta=0.06)   # interpolated chest relative, a few degrees of drift at most
            self.assertLess(d[2], -0.95)

    def test_draw_length_is_eight_to_nine_pixels(self):
        p = BY["draw"].pose(20)
        self.assertTrue(7.5 < s.norm(s.sub(p["r"]["fist"], p["l"]["fist"])) < 9.5)

    def test_release_flicks_the_string_hand_backwards_along_the_arrow(self):
        a, b = BY["release"].pose(0), BY["release"].pose(1)
        self.assertGreater(b["l"]["fist"][2], a["l"]["fist"][2] + 4.0)

    def test_every_pose_ends_where_the_next_begins(self):
        # the animations that return to the idle end exactly on it (first key = idle of the next one)
        for name in g.ENDS_IN_IDLE:
            a = BY[name]
            last = a.pose(a.end)
            self.assertAlmostEqual(last["yaw"], g.IDLE_TORSO[1], places=6, msg=name)
            for k, v in zip(last["r"]["rel"], g.IDLE_R):
                self.assertAlmostEqual(k, v, places=6, msg=name)

    def test_crossed_wrists_are_stacked_not_on_top_of_each_other(self):
        p = BY["blut"].pose(6)
        self.assertGreater(abs(p["r"]["fist"][1] - p["l"]["fist"][1]), 2.5)
        # and they really cross: the right fist is left of the left fist
        self.assertLess(p["r"]["fist"][0], p["l"]["fist"][0])

    def test_vollstandig_arms_go_above_the_horizontal_and_the_body_floats_two_pixels(self):
        p = BY["vollstandig"].pose(13)
        self.assertGreater(p["r"]["fist"][1], p["r"]["pivot"][1])
        self.assertAlmostEqual(p["lift"], 2.0, places=3)

    def test_seele_cuts_right_to_left_then_back(self):
        a = BY["seele"]
        x3 = a.pose(3)["l"]["dir"][0]
        x8 = a.pose(8)["l"]["dir"][0]
        self.assertGreater(x3 * -1, 0.3)                 # the arm points to the model's left at the first cut (x < 0 = left)
        self.assertGreater(x8, 0.3)                      # and to the right at the back hand cut

    def test_sprenger_throws_alternate_three_right_two_left(self):
        a = BY["sprenger"]
        tops = []
        for t in range(0, 21):
            p = a.pose(t)
            for side in ("r", "l"):
                if p[side]["fist"][1] > 28.0:
                    tops.append((t, side))
        # one top per throw: ticks 2 (R) 6 (L) 10 (R) 14 (L) 18 (R)
        firsts = {}
        for t, side in tops:
            firsts.setdefault(t // 4, side)
        self.assertEqual([firsts[i] for i in range(5)], ["r", "l", "r", "l", "r"])


class Export(unittest.TestCase):
    def test_every_animation_is_exported_with_its_end_tick(self):
        for a in ANIMS:
            out = EXPORTED["uryu_" + a.name]
            self.assertEqual(out["emote"]["endTick"], a.end)
            self.assertEqual(out["emote"]["stopTick"], a.end + 10 if a.length * 20 <= a.end + 10 else int(round(a.length * 20)))
            self.assertIs(out["emote"]["degrees"], False)

    def test_exported_arm_values_are_the_solved_pose_in_game_axes(self):
        # pitch = -rx, yaw = -ry, roll = +rz of the entity frame Euler, position = rest - offset (x, y) and + offset (z)
        for name in ("draw", "release", "blut", "vollstandig"):
            a, out = BY[name], EXPORTED["uryu_" + name]
            for t in range(0, a.end + 1, 1):
                p = a.pose(t)
                for side, part in (("r", "rightArm"), ("l", "leftArm")):
                    rx, ry, rz = p[side]["root"]
                    base = sample(out, part, "pitch", t)
                    # compare modulo the Euler branch: the rotation matrices must be the same
                    pa = (sample(out, part, "pitch", t), sample(out, part, "yaw", t), sample(out, part, "roll", t))
                    R_out = s.rot((-math.degrees(pa[0]), -math.degrees(pa[1]), math.degrees(pa[2])))
                    R_pose = s.rot((rx, ry, rz))
                    for i in range(3):
                        for j in range(3):
                            self.assertAlmostEqual(R_out[i][j], R_pose[i][j], delta=0.0015, msg="%s %s tick %d" % (name, part, t))
                    off = p[side]["offset"]
                    rest = exporter.REST_GAME[part]
                    self.assertAlmostEqual(sample(out, part, "x", t), rest[0] - off[0], delta=0.02)
                    self.assertAlmostEqual(sample(out, part, "y", t), rest[1] - (off[1] + p["lift"]), delta=0.02)
                    self.assertAlmostEqual(sample(out, part, "z", t), rest[2] + off[2], delta=0.02)

    def test_head_is_pitch_only_and_follows_the_storyboard_sign_fix(self):
        out = EXPORTED["uryu_vollstandig"]
        heads = [m for m in out["emote"]["moves"] if "head" in m]
        self.assertTrue(heads and all(set(m["head"]) == {"pitch"} for m in heads))
        # the crouch gather looks DOWN (storyboard +10 as nod): game pitch is positive when looking down
        self.assertAlmostEqual(sample(out, "head", "pitch", 4), math.radians(10.0), places=5)
        # the burst looks up
        self.assertLess(sample(out, "head", "pitch", 13), 0.0)

    def test_torso_lean_is_converted_to_the_exporter_convention(self):
        out = EXPORTED["uryu_vollstandig"]
        # lean forward 12 at tick 4: Blockbench rx = -12 -> game pitch +12 degrees (the top of the body tips forward)
        self.assertAlmostEqual(sample(out, "torso", "pitch", 4), math.radians(12.0), places=5)
        # the arch at 13 leans back
        self.assertLess(sample(out, "torso", "pitch", 13), 0.0)

    def test_body_tracks_keep_their_easing_names(self):
        out = EXPORTED["uryu_draw"]
        eases = {m["easing"] for m in out["emote"]["moves"] if "torso" in m}
        self.assertIn("INOUTSINE", eases)
        out = EXPORTED["uryu_blut"]
        self.assertIn("OUTBACK", {m["easing"] for m in out["emote"]["moves"] if "torso" in m})

    def test_no_pop_on_the_start_and_the_end(self):
        # the first key of every animation that starts from the idle is the idle, the last key of those that end in it too
        for name in ("draw", "seele", "blut", "vollstandig", "licht_regen", "sternenstaub", "wings", "sprenger"):
            out = EXPORTED["uryu_" + name]
            self.assertAlmostEqual(sample(out, "torso", "yaw", 0), -math.radians(6.0), places=5, msg=name)
            for axis in ("pitch", "yaw", "roll"):
                self.assertAlmostEqual(sample(out, "rightArm", axis, 0), sample(EXPORTED["uryu_idle"], "rightArm", axis, 0), places=3, msg=name)

    def test_idle_keys_only_the_bow_arm_and_the_torso(self):
        out = EXPORTED["uryu_idle"]
        parts = {k for m in out["emote"]["moves"] for k in m if isinstance(m[k], dict)}
        self.assertEqual(parts, {"torso", "rightArm"})

    def test_deterministic(self):
        again = g.export_all(g.build_bbmodel([f() for f in g.ANIMS]), [f() for f in g.ANIMS], g.OUT_DIR, write=False)
        self.assertEqual(json.dumps(again, sort_keys=True), json.dumps(EXPORTED, sort_keys=True))

    def test_bbmodel_has_the_bow_prop_and_all_animations(self):
        names = [a["name"] for a in MODEL["animations"]]
        self.assertEqual(names, ["uryu_" + a.name for a in ANIMS])
        self.assertIn("bow_prop", [grp["name"] for grp in MODEL["groups"]])
        self.assertNotIn("sword_prop", [grp["name"] for grp in MODEL["groups"]])

    def test_the_repo_exporter_accepts_every_animation(self):
        for a in ANIMS:
            exporter.convert(MODEL, "uryu_" + a.name, end_tick=a.end)


class Math(unittest.TestCase):
    def test_nearest_euler_keeps_the_branch(self):
        self.assertEqual(g.nearest_euler((100.0, -80.0, 0.0), (100.0, -80.0, 0.0)), (100.0, -80.0, 0.0))
        e = g.nearest_euler((170.0, 0.0, 170.0), (-10.0, 180.0 - 0.0, -10.0))
        for a, b in zip(e, (170.0, 0.0, 170.0)):
            self.assertLess(abs(a - b), 30.0)

    def test_hand_key_reaches_its_target_with_the_shoulder_shift(self):
        lean, yaw = -2.0, 55.0
        rel, extra = g.arm_key("hand", g.ANCHOR, "l", lean, yaw)
        Rt = g.torso_matrix(lean, yaw)
        sh = g.shoulder_world("l", lean, yaw)
        pivot = s.add(sh, s.mv(Rt, extra))
        d = s.mv(s.mm(Rt, s.rot(rel)), (0.0, -1.0, 0.0))
        fist = s.add(pivot, s.mul(d, g.ARM_REACH))
        self.assertLess(s.norm(s.sub(fist, g.ANCHOR)), 1e-6)

    def test_easing_endpoints(self):
        for name, f in g.EASE.items():
            if name in ("CONSTANT",):
                continue
            self.assertAlmostEqual(f(0.0), 0.0, places=6, msg=name)
            self.assertAlmostEqual(f(1.0), 1.0, places=6, msg=name)


if __name__ == "__main__":
    unittest.main()
