package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.press;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.sCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.selectSlot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.shot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;
import static dev.minebleach.reiatsutest.client.dev.Phase6Swarm.f;
import static dev.minebleach.reiatsutest.client.dev.Phase6Swarm.perfWindow;
import static dev.minebleach.reiatsutest.client.dev.Phase6Swarm.resetWorldFx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.BankaiBladeRenderer;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxScene;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.RowsFx;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.server.ZanpakutoManager;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.Perspective;

/** Phase 6 step 4 scenarios: Byakuya bankai rows (6.1), night grade, static row buffer, ground hilt and ripples, frame cost F3 / F4 / F4b. */
final class Phase6Rows {
	private Phase6Rows() {
	}

	/** Back to SHIKAI with a full bar, rows and swarm of earlier shots gone. */
	static void prepBankai() {
		resetWorldFx();
		cmd("item replace entity @s hotbar.1 with " + Phase6Harness.BYAKUYA, "reiatsu cooldowns clear", "reiatsu state sealed");
		selectSlot(1);
	}

	/** Real path: BASE, R (swarm), G (rows). The shot is armed on the bankai timeline (the swarm timeline is long over). */
	private static void bankaiShot(String name, int ms, String view) {
		step(name + " (prep)", 50, () -> {
			prepBankai();
		});
		step(name + " (base)", 30, () -> {
			cmd("reiatsu state base byakuya", "reiatsu full");
			Phase6Swarm.view(view);
		});
		step(name + " (R)", 1, () -> press(ReiatsuKeys.RELEASE));
		step(name + " (full)", 30, () -> cmd("reiatsu full", "reiatsu cooldowns clear"));
		Phase6Swarm.eventShot(name, ms, () -> press(ReiatsuKeys.BANKAI));
	}

	/** One release, several frozen moments in a row (the clock is frozen to each time in turn). */
	static void bankaiSeries(String prefix, int[] ms, String view) {
		step(prefix + " (prep)", 50, Phase6Rows::prepBankai);
		step(prefix + " (base)", 30, () -> {
			cmd("reiatsu state base byakuya", "reiatsu full");
			Phase6Swarm.view(view);
		});
		step(prefix + " (R)", 1, () -> press(ReiatsuKeys.RELEASE));
		step(prefix + " (full)", 40, () -> cmd("reiatsu full", "reiatsu cooldowns clear"));
		step(prefix + " (arm first)", 2, () -> FxTimelines.setFreezeAt(ms[0]));
		step(prefix + " (G)", 1, () -> press(ReiatsuKeys.BANKAI));
		step(prefix + " (G retry)", 5, () -> {
			if (RowsFx.states().isEmpty()) {
				ReiatsuTest.LOGGER.warn("[phase6] bankai key press was not seen, pressing again");
				press(ReiatsuKeys.BANKAI);
			}
		});
		stepUntil(prefix + " (frozen 0)", 2, 20 * 12, () -> { }, () -> FxClock.frozen);
		step(prefix + " (shot 0)", 3, () -> shot(prefix + "_t" + ms[0]));
		for (int k = 1; k < ms.length; k++) {
			final int kk = k;
			final double dt = (ms[k] - ms[k - 1]) / 1000.0;
			step(prefix + " (advance " + ms[k] + ")", 1, () -> {
				FxTimelines.setFreezeAt(-1);
				FxClock.freezeIn(dt);
			});
			stepUntil(prefix + " (frozen " + ms[k] + ")", 2, 20 * 20, () -> { }, () -> FxClock.frozen);
			step(prefix + " (shot " + ms[k] + ")", 3, () -> shot(prefix + "_t" + ms[kk]));
		}
		step(prefix + " (release clock)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
		});
	}

	static void steps() {
		step("rows: setup (noon, creative)", 30, () -> {
			cmd("gamemode creative @s", "time set noon", "tp @s 0.5 -60 0.5 0 12", "kill @e[type=pig]");
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
			mc.options.hudHidden = true;
			FxConfig.reduceMotion = false;
			FxConfig.bankaiBladeCount = 200;
			FxScene.requireAnchor = true;
			resetWorldFx();
		});
		// the release seen from the front: the corridor runs away behind the player
		bankaiSeries("04_rows", new int[] {0, 180, 350, 600, 1000, 1500, 2200, 2800}, "front");
		step("rows: wait for the static buffer", 20, () -> { });
		stepUntil("rows: static buffer active", 20, 20 * 20, () -> { }, () -> BankaiBladeRenderer.lastStatic);
		step("rows: static shot prep", 4, () -> { });
		step("rows: S1/S2 checks", 2, () -> {
			cCheck("rows anchor entity seen on the client (" + FxScene.rowsAnchors + ")", () -> FxScene.rowsAnchors == 1 ? null : "anchors " + FxScene.rowsAnchors);
			cCheck("row state exists (" + RowsFx.states().size() + ")", () -> RowsFx.states().size() == 1 ? null : "states " + RowsFx.states().size());
			cCheck("static buffer built once", () -> BankaiBladeRenderer.vboBuilds >= 1 ? null : "builds " + BankaiBladeRenderer.vboBuilds);
		});
		step("server check", 3, () -> sCheck("S1 rows anchor alive on the server (1)", p -> ZanpakutoManager.anchorCount(p) == 1 ? null : "anchors " + ZanpakutoManager.anchorCount(p)));
		Phase6Swarm.clockShot("04_rows_settled_front", 0.3);
		step("rows: high side view (tower 20 blocks up)", 20, () -> {
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			mc.player.getAbilities().flying = true;
			mc.player.sendAbilitiesUpdate();
			cmd("tp @s -14.5 -41 -22.5 -90 32");
		});
		step("rows: wait chunks", 30, () -> { });
		Phase6Swarm.clockShot("04_tower_settled", 0.3);
		step("rows: tower night", 20, () -> cmd("time set midnight"));
		Phase6Swarm.clockShot("04_tower_settled_night", 0.3);
		step("rows: far view (tips at 100 blocks)", 20, () -> {
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("tp @s 0.5 -58 105 180 3");
		});
		step("rows: wait far chunks", 40, () -> { });
		Phase6Swarm.clockShot("04_tips_100_night", 0.3);
		step("rows: noon far", 20, () -> cmd("time set noon"));
		Phase6Swarm.clockShot("04_tips_100_noon", 0.3);
		step("rows: ground view", 20, () -> {
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("tp @s 0.5 -60 -4.5 180 4");
		});
		Phase6Swarm.clockShot("04_corridor_ground_noon", 0.3);
		step("rows: ground night", 20, () -> cmd("time set midnight"));
		Phase6Swarm.clockShot("04_corridor_ground_night", 0.3);
		step("rows: seal (V) back to the player", 20, () -> {
			cmd("tp @s 0.5 -60 0.5 0 12", "time set noon");
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
		});
		step("rows: seal arm", 4, () -> FxTimelines.setFreezeAt(-1));
		step("rows: V", 1, () -> press(ReiatsuKeys.SEAL));
		step("rows: sink freeze", 1, () -> FxClock.freezeIn(0.35));
		stepUntil("rows: sink frozen", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step("rows: sink shot", 3, () -> shot("04_rows_seal_t350"));
		step("rows: sink release", 2, FxClock::unfreeze);
		step("rows: sealed", 90, () -> { });
		step("server check", 3, () -> sCheck("rows anchor gone after the seal", p -> ZanpakutoManager.anchorCount(p) == 0 ? null : "anchors " + ZanpakutoManager.anchorCount(p)));
		step("rows: client cleared", 2, () -> cCheck("row state removed after the seal (" + RowsFx.states().size() + ")", () -> RowsFx.states().isEmpty() ? null : "states " + RowsFx.states().size()));
		// N = 1000
		step("rows 1000: config", 10, () -> {
			FxConfig.bankaiBladeCount = 1000;
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
		});
		bankaiSeries("04_rows1000", new int[] {1000, 1500, 2200, 2800}, "front");
		step("rows 1000: tower", 20, () -> {
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("tp @s -26.5 -36 -40.5 -90 28");
		});
		step("rows 1000: wait chunks", 30, () -> { });
		Phase6Swarm.clockShot("04_tower1000_t2800_live", 0.2);
		step("rows 1000: back, noon", 20, () -> {
			FxConfig.bankaiBladeCount = 200;
			cmd("tp @s 0.5 -60 0.5 0 12", "reiatsu state sealed");
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
			resetWorldFx();
		});
	}

	/** Fabulous graphics check of the rows and the night grade. */
	static void fabulousSteps() {
		step("rows fab: Fabulous", 40, () -> {
			Phase6Harness.graphics(GraphicsMode.FABULOUS);
			FxConfig.bankaiBladeCount = 200;
		});
		step("rows fab: settle", 60, () -> { });
		bankaiSeries("04_fab_rows", new int[] {1500, 2800}, "front");
		step("rows fab: back to Fancy", 40, () -> {
			Phase6Harness.graphics(GraphicsMode.FANCY);
			cmd("reiatsu state sealed", "tp @s 0.5 -60 0.5 0 12");
			resetWorldFx();
		});
	}

	/** F3, F4 (VBO on / off), F4b (1000 rising), Medium and Low variants. */
	static void perfSteps() {
		step("rows perf: setup", 30, () -> {
			mc.options.hudHidden = true;
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
			mc.options.getMaxFps().setValue(260);
			cmd("time set noon", "tp @s 0.5 -60 0.5 0 12");
			FxScene.requireAnchor = true;
		});
		for (int n : new int[] {200, 1000}) {
			final int nn = n;
			step("rows perf " + n + ": config", 2, () -> {
				FxConfig.bankaiBladeCount = nn;
				BankaiBladeRenderer.useVbo = true;
				prepBankai();
			});
			step("rows perf " + n + ": prep", 50, () -> { });
			step("rows perf " + n + ": base", 30, () -> cmd("reiatsu state base byakuya", "reiatsu full"));
			step("rows perf " + n + ": R", 1, () -> press(ReiatsuKeys.RELEASE));
			step("rows perf " + n + ": full", 40, () -> cmd("reiatsu full", "reiatsu cooldowns clear"));
			step("rows perf " + n + ": G", 1, () -> {
				press(ReiatsuKeys.BANKAI);
			});
			step("rows perf " + n + ": G retry", 5, () -> {
				if (RowsFx.states().isEmpty()) {
					press(ReiatsuKeys.BANKAI);
				}
			});
			if (n == 1000) {
				// F4b: while rising (about 1.0 to 2.8 s after the release, the window starts at the eruption)
				step("rows perf 1000: wait eruption", 16, () -> { });
				perfWindow("F4b 1000 blades rising (CPU path, 1.7 s from the first eruption)", 150, 0, 1.7, r -> ReiatsuTest.LOGGER.info("[phase6] F4b cpu vertices {}", r[6]));
			}
			stepUntil("rows perf " + n + ": static", 20, 20 * 40, () -> { }, () -> BankaiBladeRenderer.lastStatic);
			step("rows perf " + n + ": settle", 40, () -> { });
			perfWindow("F" + (n == 200 ? 3 : 4) + " " + n + " blades held, static buffer ON", 400, r -> {
				cCheck("F" + (nn == 200 ? 3 : 4) + " " + nn + " blades VBO: mean >= 60 fps (" + f(r[2]) + ")", () -> r[2] >= 60 ? null : "fps " + r[2]);
				cCheck("F" + (nn == 200 ? 3 : 4) + " " + nn + " blades VBO: 1% low >= 45 fps (" + f(r[3]) + ")", () -> r[3] >= 45 ? null : "low " + r[3]);
			});
			step("rows perf " + n + ": VBO off", 2, () -> BankaiBladeRenderer.useVbo = false);
			step("rows perf " + n + ": settle off", 40, () -> { });
			perfWindow("F" + (n == 200 ? 3 : 4) + " " + n + " blades held, static buffer OFF (CPU path)", 400, r -> cCheck(
					"F" + (nn == 200 ? 3 : 4) + " " + nn + " blades CPU path: cpu verts " + (int) r[6] + " <= 200k", () -> r[6] <= 200_000 ? null : "verts " + r[6]));
			step("rows perf " + n + ": VBO on again", 2, () -> BankaiBladeRenderer.useVbo = true);
			step("rows perf " + n + ": seal", 60, () -> {
				press(ReiatsuKeys.SEAL);
				resetWorldFx();
			});
		}
		step("rows perf: Medium and Low (blades 100 / 50)", 2, () -> {
			FxConfig.bankaiBladeCount = 100;
			FxConfig.effectQuality = 0.5f;
			prepBankai();
		});
		step("rows perf M: prep", 50, () -> { });
		step("rows perf M: base", 30, () -> cmd("reiatsu state base byakuya", "reiatsu full"));
		step("rows perf M: R", 1, () -> press(ReiatsuKeys.RELEASE));
		step("rows perf M: full", 40, () -> cmd("reiatsu full"));
		step("rows perf M: G", 1, () -> press(ReiatsuKeys.BANKAI));
		stepUntil("rows perf M: static", 20, 20 * 40, () -> { }, () -> BankaiBladeRenderer.lastStatic);
		step("rows perf M: settle", 40, () -> { });
		perfWindow("F3 100 blades held (Medium)", 300, null);
		step("rows perf: restore", 2, () -> {
			FxConfig.bankaiBladeCount = 200;
			FxConfig.effectQuality = 1.0f;
			mc.options.getMaxFps().setValue(120);
			cmd("reiatsu state sealed");
			resetWorldFx();
		});
	}
}
