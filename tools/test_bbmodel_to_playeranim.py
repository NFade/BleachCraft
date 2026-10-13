"""Unit tests for bbmodel_to_playeranim and slash_solver (run: python -I tools/test_bbmodel_to_playeranim.py)."""
import json
import math
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel_to_playeranim as c  # noqa: E402
import slash_solver as s  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANIM_JSON = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "reiatsu_test",
                         "player_animations", "senbon_slash_rl.json")
BBMODEL = os.path.join(ROOT, "design", "anim", "player_game_rig.bbmodel")


def model(bone, keys, length=1.0, channel="rotation"):
    return {"animations": [{"name": "a", "length": length, "animators": {
        "u1": {"name": bone, "keyframes": [
            {"channel": channel, "time": t, "interpolation": i,
             "data_points": [{"x": str(x), "y": str(y), "z": str(z)}]}
            for (t, i, (x, y, z)) in keys]}}}]}


def first(out, part, axis, tick=0):
    for m in out["emote"]["moves"]:
        if m["tick"] == tick and part in m and axis in m[part]:
            return m[part][axis]
    raise AssertionError("no %s.%s at tick %d" % (part, axis, tick))


class AxisSigns(unittest.TestCase):
    def test_arm_forward_90_is_pitch_minus_half_pi(self):
        out = c.convert(model("rightArm", [(0, "linear", (90, 0, 0)), (0.5, "linear", (0, 0, 0))]), "a")
        self.assertAlmostEqual(first(out, "rightArm", "pitch"), -math.pi / 2, places=5)
        self.assertEqual(first(out, "rightArm", "yaw"), 0)
        self.assertEqual(first(out, "rightArm", "roll"), 0)

    def test_blockbench_turn_left_is_negative_yaw(self):
        out = c.convert(model("torso", [(0, "linear", (0, 30, 0)), (0.5, "linear", (0, 0, 0))]), "a")
        self.assertAlmostEqual(first(out, "torso", "yaw"), -math.radians(30), places=5)

    def test_roll_sign_kept(self):
        out = c.convert(model("rightArm", [(0, "linear", (0, 0, -12.6)), (0.5, "linear", (0, 0, 0))]), "a")
        self.assertAlmostEqual(first(out, "rightArm", "roll"), math.radians(-12.6), places=5)

    def test_matches_katana_idle_numbers(self):
        # pose_two_handed_katana: rightArm pitch -0.5336 (~30.6 deg forward), leftArm yaw +0.8803 (turned right)
        self.assertAlmostEqual(c.bb_rotation_to_pa(30.6, 0, 0)[0], -0.5341, places=3)
        self.assertAlmostEqual(c.bb_rotation_to_pa(0, -50.4, 0)[1], 0.8796, places=3)


class Positions(unittest.TestCase):
    def test_right_arm_offsets_become_absolute_game_positions(self):
        # Blockbench right is +X, game right is -X; up is +Y vs down +Y; Z unchanged. Rest is (-5, 2, 0).
        out = c.convert(model("rightArm", [(0, "linear", (1, 2, 3)), (0.5, "linear", (0, 0, 0))], channel="position"), "a")
        self.assertEqual(first(out, "rightArm", "x"), -6.0)
        self.assertEqual(first(out, "rightArm", "y"), 0.0)
        self.assertEqual(first(out, "rightArm", "z"), 3.0)

    def test_zero_offset_is_the_vanilla_pivot(self):
        out = c.convert(model("leftArm", [(0, "linear", (0, 0, 0)), (0.5, "linear", (0, 0, 0))], channel="position"), "a")
        self.assertEqual((first(out, "leftArm", "x"), first(out, "leftArm", "y"), first(out, "leftArm", "z")), (5.0, 2.0, 0.0))

    def test_item_rotation_is_passed_through_in_game_degrees(self):
        out = c.convert(model("rightItem", [(0, "linear", (-30, 10, 5)), (0.5, "linear", (0, 0, 0))]), "a")
        self.assertAlmostEqual(first(out, "rightItem", "pitch"), math.radians(-30), places=5)
        self.assertAlmostEqual(first(out, "rightItem", "yaw"), math.radians(10), places=5)
        self.assertAlmostEqual(first(out, "rightItem", "roll"), math.radians(5), places=5)

    def test_item_position_is_passed_through_in_game_px(self):
        out = c.convert(model("rightItem", [(0, "linear", (0.5, -4.2, 1.0)), (0.5, "linear", (0, 0, 0))], channel="position"), "a")
        self.assertEqual((first(out, "rightItem", "x"), first(out, "rightItem", "y"), first(out, "rightItem", "z")), (0.5, -4.2, 1.0))

    def test_preview_prop_is_ignored(self):
        m = model("sword_prop", [(0, "linear", (1, 2, 3))])
        m["animations"][0]["animators"]["u2"] = model("head", [(0, "linear", (0, 1, 0)), (0.5, "linear", (0, 0, 0))])["animations"][0]["animators"]["u1"]
        out = c.convert(m, "a")
        self.assertTrue(all("sword_prop" not in mv for mv in out["emote"]["moves"]))


class Structure(unittest.TestCase):
    def test_ticks_and_header(self):
        out = c.convert(model("torso", [(0, "linear", (0, 0, 0)), (0.4, "catmullrom", (0, 10, 0)),
                                        (0.8, "catmullrom", (0, 0, 0))], length=1.1), "a")
        e = out["emote"]
        self.assertEqual((e["beginTick"], e["endTick"], e["stopTick"]), (0, 16, 26))
        self.assertIs(e["degrees"], False)
        self.assertEqual(e["isLoop"], "false")
        self.assertAlmostEqual(first(out, "torso", "yaw", 8), -math.radians(10), places=5)
        self.assertEqual({m["easing"] for m in e["moves"] if m["tick"] == 8}, {"INOUTSINE"})

    def test_one_axis_per_move_like_reference_format(self):
        out = c.convert(model("head", [(0, "linear", (1, 2, 3)), (0.5, "linear", (0, 0, 0))]), "a")
        for m in out["emote"]["moves"]:
            parts = [k for k, v in m.items() if isinstance(v, dict)]
            self.assertEqual(len(parts), 1)
            self.assertEqual(len(m[parts[0]]), 1)
            self.assertEqual(m["turn"], 0)

    def test_end_tick_override(self):
        out = c.convert(model("head", [(0, "linear", (0, 0, 0)), (1.0, "linear", (0, 5, 0))]), "a", end_tick=12)
        self.assertEqual(out["emote"]["endTick"], 12)
        self.assertEqual(out["emote"]["stopTick"], 22)


class Strictness(unittest.TestCase):
    def test_scale_channel_rejected(self):
        with self.assertRaises(c.ConvertError):
            c.convert(model("head", [(0, "linear", (0, 0, 1))], channel="scale"), "a")

    def test_unmapped_bone_rejected(self):
        with self.assertRaises(c.ConvertError):
            c.convert(model("p_chest", [(0, "linear", (0, 0, 1))]), "a")

    def test_molang_rejected(self):
        m = model("head", [(0, "linear", (0, 0, 0))])
        m["animations"][0]["animators"]["u1"]["keyframes"][0]["data_points"][0]["x"] = "math.sin(query.anim_time)"
        with self.assertRaises(c.ConvertError):
            c.convert(m, "a")

    def test_missing_animation(self):
        with self.assertRaises(c.ConvertError):
            c.convert(model("head", [(0, "linear", (0, 0, 0))]), "nope")


class SlashSolver(unittest.TestCase):
    """The arms must really hold one sword: forward kinematics of what the solver emits."""

    def rows(self):
        return s.table()

    def test_straight_right_arm_reaches_the_grip(self):
        for r in self.rows():
            R = s.rot(r["rightArm"]["rot"])
            hand = s.add(s.add(s.SHOULDER["rightArm"], r["rightArm"]["pos"]), s.mv(R, (0.0, -s.ARM_REACH, 0.0)))
            self.assertLess(s.norm(s.sub(hand, r["hand_r"])), 1e-6, "tick %d" % r["tick"])

    def test_straight_left_arm_reaches_the_pommel_hand(self):
        for r in self.rows():
            R = s.rot(r["leftArm"]["rot"])
            hand = s.add(s.add(s.SHOULDER["leftArm"], r["leftArm"]["pos"]), s.mv(R, (0.0, -s.ARM_REACH, 0.0)))
            self.assertLess(s.norm(s.sub(hand, r["hand_l"])), 1e-6, "tick %d" % r["tick"])

    def test_hands_are_one_hilt_apart_along_the_blade(self):
        for r in self.rows():
            d = s.sub(r["hand_r"], r["hand_l"])
            self.assertAlmostEqual(s.norm(d), s.HAND_GAP, places=6)
            self.assertAlmostEqual(s.dot(s.unit(d), r["blade"]), 1.0, places=6)

    def test_shoulders_stay_close_to_the_body(self):
        for r in self.rows():
            self.assertLess(abs(r["shift_r"]), s.MAX_SHIFT, "right tick %d" % r["tick"])
            self.assertLess(abs(r["shift_l"]), s.MAX_SHIFT, "left tick %d" % r["tick"])

    def test_cut_is_level_at_the_contact_tick(self):
        contact = s.CONTACT_TICK
        r = [x for x in self.rows() if x["tick"] == contact][0]
        self.assertLess(abs(math.degrees(math.asin(r["blade"][1]))), 20.0)          # roughly level
        self.assertGreater(-r["blade"][2], 0.8)                                       # pointing forward

    def test_body_stays_small_and_blade_makes_the_arc(self):
        rows = self.rows()
        yaws = [r["torso"][1] for r in rows]
        leans = [r["torso"][0] for r in rows]
        self.assertLess(max(abs(y) for y in yaws), 30.0)       # user: body and head moved too much
        self.assertLess(max(abs(v) for v in leans), 12.0)
        self.assertLess(yaws.index(min(yaws)), yaws.index(max(yaws)))   # right to left
        az = [math.degrees(math.atan2(-r["blade"][0], -r["blade"][2])) for r in rows]
        self.assertGreater(max(az) - min(az), 170.0)           # user: bigger amplitude of the blade

    def test_edge_leads_the_cut(self):
        """From the start of the strike to the end of the follow-through the cutting edge points along the tip's motion."""
        rows = self.rows()
        tip = lambda r: s.add(r["hand_r"], s.mul(r["blade"], s.BLADE_LEN))
        for i in range(s.CONTACT_TICK - 4, s.CONTACT_TICK + 4):
            v = s.mul(s.sub(tip(rows[i + 1]), tip(rows[i - 1])), 0.5)
            v = s.sub(v, s.mul(rows[i]["blade"], s.dot(v, rows[i]["blade"])))
            if s.norm(v) < 1.0:
                continue
            self.assertGreater(s.dot(rows[i]["edge"], s.unit(v)), 0.8, "tick %d" % i)

    def test_edge_is_perpendicular_to_the_blade(self):
        for r in self.rows():
            self.assertAlmostEqual(s.dot(r["edge"], r["blade"]), 0.0, places=6)

    def test_tip_is_fastest_just_before_the_contact(self):
        rows = self.rows()
        tip = lambda r: s.add(r["hand_r"], s.mul(r["blade"], s.BLADE_LEN))
        speed = [s.norm(s.sub(tip(rows[i + 1]), tip(rows[i - 1]))) / 2 for i in range(1, len(rows) - 1)]
        strike = speed[:s.CONTACT_TICK + 1]
        peak = strike.index(max(strike)) + 1
        self.assertIn(peak, range(s.CONTACT_TICK - 4, s.CONTACT_TICK + 1))
        recovery = speed[s.CONTACT_TICK + 5:]
        self.assertLessEqual(max(recovery), 1.1 * max(strike))        # the way back is not faster than the cut

    def test_legs_step_into_the_cut_and_stay_small(self):
        rows = self.rows()
        r_leg = [r["rLeg"] for r in rows]
        l_leg = [r["lLeg"] for r in rows]
        self.assertGreater(max(r_leg), 15.0)                                   # lunge with the lead (right) foot
        self.assertLess(min(l_leg), -8.0)                                      # the rear foot goes back
        self.assertLess(max(max(abs(v) for v in r_leg), max(abs(v) for v in l_leg)), 30.0)
        self.assertLessEqual(r_leg.index(max(r_leg)), s.CONTACT_TICK + 1)       # the front foot is planted at the contact

    def test_torso_crosses_the_centre_before_the_blade(self):
        rows = self.rows()
        yaw_cross = next(i for i, r in enumerate(rows) if r["torso"][1] >= 0.0 and i > 5)
        blade_cross = next(i for i, r in enumerate(rows)
                           if i > 5 and math.degrees(math.atan2(-r["blade"][0], -r["blade"][2])) >= 0.0)
        self.assertLessEqual(yaw_cross, blade_cross)                          # chain: body first, blade last
        self.assertLessEqual(blade_cross, s.CONTACT_TICK)

    def test_last_tick_is_the_ready_pose(self):
        rows = self.rows()
        self.assertLess(s.norm(s.sub(rows[0]["hand_r"], rows[-1]["hand_r"])), 1e-6)
        self.assertLess(s.norm(s.sub(rows[0]["blade"], rows[-1]["blade"])), 1e-6)
        self.assertLess(s.norm(s.sub(rows[0]["torso"], rows[-1]["torso"])), 1e-6)
        self.assertEqual((rows[0]["rLeg"], rows[0]["lLeg"]), (rows[-1]["rLeg"], rows[-1]["lLeg"]))

    def test_head_only_nods(self):
        """Only a small pitch nod is keyed, yaw/roll stay with the camera (a keyed head axis replaces the vanilla value)."""
        keys = [k for k in s.keyframes(self.rows()) if k["bone"] == "head"]
        self.assertGreaterEqual(len(keys), 3)
        for k in keys:
            self.assertEqual(k["value"][1:], [0.0, 0.0])
            self.assertLessEqual(abs(k["value"][0]), 6.0)

    def test_body_tracks_use_real_easing_names(self):
        for bone, keys in s.TRACKS.items():
            for _tick, _value, ease in keys:
                self.assertTrue(ease is None or ease in s.EASE, "%s %s" % (bone, ease))
            ticks = [k[0] for k in keys]
            self.assertEqual(ticks, sorted(set(ticks)), bone)
        # Better Combat's rule: the torso strike segment is eased in and out, the wind-up and recovery too
        self.assertEqual([k[2] for k in s.TRACKS["torso"]][1:3], ["INOUTSINE", "INOUTQUAD"])
        self.assertEqual(s.TRACKS["torso"][-1][2], "INOUTSINE")



class CommittedAnimation(unittest.TestCase):
    def test_committed_json_matches_source(self):
        if not (os.path.exists(BBMODEL) and os.path.exists(ANIM_JSON)):
            self.skipTest("animation files not present")
        with open(BBMODEL, encoding="utf-8") as f:
            expect = c.convert(json.load(f), "senbon_slash_rl", end_tick=s.END_TICK)
        with open(ANIM_JSON, encoding="utf-8") as f:
            self.assertEqual(json.load(f), expect)

    def test_head_has_pitch_keys_only(self):
        if not os.path.exists(ANIM_JSON):
            self.skipTest("animation file not present")
        with open(ANIM_JSON, encoding="utf-8") as f:
            moves = json.load(f)["emote"]["moves"]
        head_axes = {ax for m in moves if "head" in m for ax in m["head"]}
        self.assertEqual(head_axes, {"pitch"})

    def test_values_are_sane(self):
        if not os.path.exists(ANIM_JSON):
            self.skipTest("animation file not present")
        with open(ANIM_JSON, encoding="utf-8") as f:
            d = json.load(f)
        e = d["emote"]
        self.assertLess(e["endTick"], e["stopTick"])
        for m in e["moves"]:
            for k, v in m.items():
                if isinstance(v, dict):
                    self.assertIn(k, {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg", "rightItem"})
                    for ax, val in v.items():
                        self.assertIn(ax, {"pitch", "yaw", "roll", "x", "y", "z"})
                        limit = 2 * math.pi if ax in ("pitch", "yaw", "roll") else 16.0
                        self.assertLess(abs(val), limit)



def _find_jars():
    home = os.environ.get("GRADLE_USER_HOME", "D:/gradle-home")
    import glob
    pa = glob.glob(home + "/caches/modules-2/files-2.1/maven.modrinth/playeranimator/2.0.4*/*/playeranimator-2.0.4*.jar")
    gs = glob.glob(home + "/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1/*/gson-2.10.1.jar")
    return (pa[0], gs[0]) if pa and gs else None


class PlayerAnimatorHeadless(unittest.TestCase):
    """Evaluate the committed JSON with the REAL playerAnimator classes (tools/pa_sample/PaSample.java) and compare with
    what the solver intended: easing, signs and positions as the game will see them."""

    rows_by_tick = None

    @classmethod
    def setUpClass(cls):
        import shutil
        import subprocess
        jars = _find_jars()
        java = shutil.which("java") or (os.path.join(os.environ["JAVA_HOME"], "bin", "java") if os.environ.get("JAVA_HOME") else None)
        if not (jars and java and os.path.exists(ANIM_JSON)):
            raise unittest.SkipTest("java, playerAnimator jar or the animation is missing")
        out = subprocess.run([java, "-cp", os.pathsep.join(jars), os.path.join(ROOT, "tools", "pa_sample", "PaSample.java"),
                              ANIM_JSON, "body,head,rightArm,leftArm,rightLeg,leftLeg,rightItem", "1"],
                             capture_output=True, text=True, timeout=180)
        if out.returncode != 0:
            raise unittest.SkipTest("sampler failed: " + out.stderr[-300:])
        cls.v = {}
        for line in out.stdout.splitlines():
            parts = line.split(",")
            if len(parts) == 6 and parts[0].replace(".", "", 1).isdigit():
                cls.v[(float(parts[0]), parts[1], parts[2])] = tuple(float(x) for x in parts[3:])

    def test_torso_and_legs_follow_the_eased_tracks_between_ticks_too(self):
        worst = 0.0
        for half in range(0, 2 * s.END_TICK + 1):
            tick = half / 2.0
            lean, yaw = s.eval_track("torso", tick)
            pitch, yw, _ = self.v[(tick, "body", "ROTATION")]
            worst = max(worst, abs(pitch + math.radians(lean)), abs(yw + math.radians(yaw)))
            for bone, part in (("rightLeg", "rightLeg"), ("leftLeg", "leftLeg"), ("head", "head")):
                want = s.eval_track(bone, tick)[0]
                got = self.v[(tick, part, "ROTATION")][0]
                worst = max(worst, abs(got + math.radians(want)))
        self.assertLess(worst, 2e-3)       # radians; float32 plus the half tick rounding of the game's player

    def test_hands_land_on_the_grip_with_the_real_player(self):
        for r in s.table():
            t = float(r["tick"])
            for arm, key in (("rightArm", "hand_r"), ("leftArm", "hand_l")):
                pitch, yaw, roll = (math.degrees(a) for a in self.v[(t, arm, "ROTATION")])
                pos = self.v[(t, arm, "POSITION")]
                R = s.rot((pitch, yaw, roll))
                hand_game = s.add(pos, s.mv(R, (0.0, s.ARM_REACH, 0.0)))
                hand_bb = (-hand_game[0], 24.0 - hand_game[1], hand_game[2])
                self.assertLess(s.norm(s.sub(hand_bb, r[key])), 5e-3, "%s tick %d" % (arm, r["tick"]))

    def test_head_yaw_and_roll_are_left_to_the_camera(self):
        for tick in range(0, s.END_TICK + 1):
            _, yaw, roll = self.v[(float(tick), "head", "ROTATION")]
            self.assertEqual((yaw, roll), (0.0, 0.0))     # unkeyed axes fall back to the value passed in (0 here)


if __name__ == "__main__":
    unittest.main()
