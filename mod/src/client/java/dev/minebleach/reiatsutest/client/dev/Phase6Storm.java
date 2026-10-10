package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.press;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.sCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.shot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;
import static dev.minebleach.reiatsutest.client.dev.Phase6Swarm.f;
import static dev.minebleach.reiatsutest.client.dev.Phase6Swarm.resetWorldFx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.BankaiBladeRenderer;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxScene;
import dev.minebleach.reiatsutest.client.fx.FxShapes;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.HakuteikenFx;
import dev.minebleach.reiatsutest.client.fx.RowsFx;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.StormFx;
import dev.minebleach.reiatsutest.client.fx.StormRenderer;
import dev.minebleach.reiatsutest.client.fx.SwarmFx;
import dev.minebleach.reiatsutest.client.fx.WingHaloRenderer;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.fx.RowsPhase;
import dev.minebleach.reiatsutest.server.ZanpakutoManager;
import net.minecraft.client.option.Perspective;

/**
 * Phase 6 steps 5 and 8 scenarios: the Byakuya petal storm (6.2, {@code -Phold=storm}) and Shukei: Hakuteiken (6.3, {@code -Phold=hakuteiken}), with
 * the frame cost windows ({@code stormperf}). Everything goes through the real path (R, G, then U for the scatter, I for Hakuteiken), the FX clock
 * is frozen at exact moments, screenshots are {@code p6_05_storm_t*.png} and {@code p6_08_hakuteiken_t*_<view>.png}. The same scenarios are
 * available live in the console ({@code stm}, {@code hak}, {@code rowsup}; see {@link #console}).
 */
final class Phase6Storm {
	private Phase6Storm() {
	}

	// ------------------------------------------------------------------------------------------ helpers

	private static void setup(String name) {
		step(name + ": setup (noon, creative, third person)", 30, () -> {
			cmd("gamemode creative @s", "time set noon", "tp @s 0.5 -60 0.5 0 12", "kill @e[type=pig]");
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			mc.options.hudHidden = true;
			FxConfig.reduceMotion = false;
			FxConfig.bankaiBladeCount = 200;
			FxScene.requireAnchor = true;
			resetWorldFx();
		});
	}

	/** Real path to standing rows: BASE, R (swarm), G (rows), then wait for the static buffer. {@code view} as {@link Phase6Swarm#view}. */
	static void standRows(String prefix, String view) {
		step(prefix + " (prep)", 50, Phase6Rows::prepBankai);
		step(prefix + " (base)", 30, () -> {
			cmd("reiatsu state base byakuya", "reiatsu full");
			Phase6Swarm.view(view);
		});
		step(prefix + " (R)", 1, () -> press(ReiatsuKeys.RELEASE));
		step(prefix + " (full)", 40, () -> cmd("reiatsu full", "reiatsu cooldowns clear"));
		step(prefix + " (G)", 1, () -> press(ReiatsuKeys.BANKAI));
		step(prefix + " (G retry)", 5, () -> {
			if (RowsFx.states().isEmpty()) {
				ReiatsuTest.LOGGER.warn("[phase6] bankai key press was not seen, pressing again");
				press(ReiatsuKeys.BANKAI);
			}
		});
		stepUntil(prefix + " (static buffer)", 20, 20 * 40, () -> { }, () -> BankaiBladeRenderer.lastStatic);
		step(prefix + " (settle)", 20, () -> cmd("reiatsu full", "reiatsu cooldowns clear"));
	}

	/** Frozen shot at {@code ms} of the first running timeline after {@code trigger}; {@code checks} runs while the clock is still frozen. */
	static void shotChecked(String name, int ms, Runnable trigger, Runnable afterTrigger, Runnable checks) {
		step(name + " (arm)", 2, () -> FxTimelines.setFreezeAt(ms));
		step(name + " (go)", 1, trigger);
		step(name + " (after)", 1, afterTrigger == null ? () -> { } : afterTrigger);
		stepUntil(name + " (frozen)", 2, 20 * 14, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> shot(name));
		step(name + " (checks)", 1, checks == null ? () -> { } : checks);
		step(name + " (release clock)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
		});
	}

	/** Waits until the storm and Hakuteiken states are gone and the rows are whole (static buffer drawn), then refills the bar and the cooldowns. */
	private static void waitSettled(String name) {
		stepUntil(name + " (effect over)", 10, 20 * 16, () -> { }, () -> StormFx.states().isEmpty() && HakuteikenFx.states().isEmpty()
				&& RowsFx.states().values().stream().noneMatch(RowsFx.State::phaseBusy) && BankaiBladeRenderer.lastStatic);
		step(name + " (reset)", 20, () -> cmd("reiatsu cooldowns clear", "reiatsu full", "kill @e[type=pig]"));
	}

	private static void castScatter() {
		press(ReiatsuKeys.BANKAI_SLOTS[0]);
	}

	private static void castHakuteiken() {
		press(ReiatsuKeys.BANKAI_SLOTS[1]);
	}

	private static RowsFx.State rows() {
		return RowsFx.states().values().stream().findFirst().orElse(null);
	}

	// ------------------------------------------------------------------------------------------ step 5: storm

	static void stormSteps() {
		setup("storm");
		standRows("05_storm", "far");
		step("storm: pig at the aim", 20, () -> {
			SwarmFx.hits = 0;
			cmd("summon pig 0.5 -60 23.6 {NoAI:1b,Silent:1b}", "reiatsu cooldowns clear", "reiatsu full");
		});
		for (int ms : new int[] {300, 800, 1500, 2000, 2500, 4800}) {
			final int m = ms;
			Runnable checks = null;
			if (ms == 800) {
				checks = () -> {
					cCheck("storm state exists (" + StormFx.states().size() + ")", () -> StormFx.states().size() == 1 ? null : "states " + StormFx.states().size());
					cCheck("rows phase 32 set on the client", () -> rows() != null && rows().phase == RowsPhase.SCATTERED ? null : "phase " + (rows() == null ? "no rows" : rows().phase));
					cCheck("tornado petals drawn at 0.8 s (" + StormRenderer.lastPetals + ")", () -> StormRenderer.lastPetals >= 300 ? null : "drawn " + StormRenderer.lastPetals);
					cCheck("blades are being cut (CPU path, " + BankaiBladeRenderer.lastCpuBlades + " drawn)", () -> !BankaiBladeRenderer.lastStatic ? null : "static buffer still drawn");
					sCheck("S3 rows anchor phase 32 on the server", p -> ZanpakutoManager.rowsPhase(p) == RowsPhase.SCATTERED ? null : "phase " + ZanpakutoManager.rowsPhase(p));
				};
			} else if (ms == 1500) {
				checks = () -> {
					cCheck("storm petals drawn at 1.5 s (" + StormRenderer.lastPetals + " of " + (StormFx.states().isEmpty() ? 0 : StormFx.states().values().iterator().next().storm.m) + ")",
							() -> !StormFx.states().isEmpty() && StormRenderer.lastPetals >= 0.7 * StormFx.states().values().iterator().next().storm.m ? null : "drawn " + StormRenderer.lastPetals);
					cCheck("every blade is gone at 1.5 s", () -> rows() != null && rows().fraction(0) == 0 && rows().fraction(rows().layout.n - 1) == 0 ? null : "fractions "
							+ (rows() == null ? "no rows" : rows().fraction(0) + " / " + rows().fraction(rows().layout.n - 1)));
					cCheck("storm CPU cost " + f(StormRenderer.lastMs) + " ms <= 4 ms", () -> StormRenderer.lastMs <= 4.0 ? null : "ms " + StormRenderer.lastMs);
				};
			} else if (ms == 2500) {
				checks = () -> cCheck("impact at the server aim reached (" + StormFx.impacts + ")", () -> StormFx.impacts >= 1 ? null : "impacts " + StormFx.impacts);
			} else if (ms == 4800) {
				checks = () -> cCheck("rows regrow at 4.8 s (phase still busy: " + (rows() != null && rows().phaseBusy()) + ")",
						() -> rows() != null && rows().phaseBusy() && rows().fraction(0) > 0 ? null : "no regrowth");
			}
			shotChecked("05_storm_t" + ms, m, Phase6Storm::castScatter, null, checks);
			waitSettled("storm t" + ms);
		}
		step("storm: S5 hit sparks", 2, () -> cCheck("S5 HIT feedback received for the damaged pig (" + SwarmFx.hits + ")", () -> SwarmFx.hits > 0 ? null : "hits " + SwarmFx.hits));
		// the same storm from the side and from above (the helix radius 5 at h 8, the stream)
		step("storm: pig again", 10, () -> cmd("summon pig 0.5 -60 23.6 {NoAI:1b,Silent:1b}"));
		step("storm: side view", 20, () -> Phase6Swarm.view("farside"));
		for (int ms : new int[] {1500, 2000}) {
			shotChecked("05_storm_side_t" + ms, ms, Phase6Storm::castScatter, null, null);
			waitSettled("storm side t" + ms);
		}
		step("storm: top view", 20, () -> Phase6Swarm.view("fartop"));
		for (int ms : new int[] {1000, 1500}) {
			shotChecked("05_storm_top_t" + ms, ms, Phase6Storm::castScatter, null, null);
			waitSettled("storm top t" + ms);
		}
		step("storm: night", 20, () -> {
			cmd("time set midnight");
			Phase6Swarm.view("far");
		});
		for (int ms : new int[] {800, 2000, 2500}) {
			shotChecked("05_storm_night_t" + ms, ms, Phase6Storm::castScatter, null, null);
			waitSettled("storm night t" + ms);
		}
		step("storm: back", 10, () -> cmd("time set noon", "kill @e[type=pig]", "reiatsu state sealed"));
	}

	/** F2 and F5: frame cost during the storm (3000 petals, rows dissolving) and Hakuteiken (2000 condensing petals, wings). */
	static void perfSteps() {
		step("storm perf: setup", 30, () -> {
			mc.options.hudHidden = true;
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			mc.options.getMaxFps().setValue(260);
			cmd("time set noon", "tp @s 0.5 -60 0.5 0 4");
			FxScene.requireAnchor = true;
			FxConfig.bankaiBladeCount = 200;
		});
		standRows("storm perf", "far");
		step("storm perf: cast", 1, Phase6Storm::castScatter);
		Phase6Swarm.perfWindow("F2 storm 3000 petals + dissolving rows (5.5 s)", 200, 0, 5.5, r -> {
			cCheck("F2 storm: mean >= 60 fps (" + f(r[2]) + ")", () -> r[2] >= 60 ? null : "fps " + r[2]);
			cCheck("F2 storm: 1% low >= 45 fps (" + f(r[3]) + ")", () -> r[3] >= 45 ? null : "low " + r[3]);
		});
		waitSettled("storm perf");
		step("storm perf: Hakuteiken cast", 1, Phase6Storm::castHakuteiken);
		Phase6Swarm.perfWindow("F5 Hakuteiken 2000 petals + wings (4.2 s)", 150, 0, 4.0, r -> {
			cCheck("F5 Hakuteiken: mean >= 60 fps (" + f(r[2]) + ")", () -> r[2] >= 60 ? null : "fps " + r[2]);
			cCheck("F5 Hakuteiken: 1% low >= 45 fps (" + f(r[3]) + ")", () -> r[3] >= 45 ? null : "low " + r[3]);
		});
		waitSettled("hak perf");
		step("storm perf: restore", 2, () -> {
			mc.options.getMaxFps().setValue(120);
			cmd("reiatsu state sealed");
			resetWorldFx();
		});
	}

	// ------------------------------------------------------------------------------------------ step 8: Hakuteiken

	/** Turn the owner right after the cast so the camera in front sees him from the front ({@code deg} off the line) while the line goes elsewhere. */
	private static Runnable turn(double deg) {
		return () -> cmd("tp @s 0.5 -60 0.5 " + deg + " 10");
	}

	static void hakSteps() {
		setup("hak");
		standRows("08_hak", "far");
		step("hak: pig at the end of the line", 20, () -> {
			SwarmFx.hits = 0;
			cmd("summon pig 0.5 -60 23.6 {NoAI:1b,Silent:1b}", "reiatsu cooldowns clear", "reiatsu full");
		});
		int[] mss = {0, 500, 1000, 1500, 1530, 1800, 2600};
		// front: the owner turns 25 degrees after the cast (the line passes beside the camera), three quarter: 70 degrees
		String[] views = {"front", "threeq"};
		double[] turns = {25, 70};
		for (int v = 0; v < views.length; v++) {
			final int vv = v;
			step("hak " + views[v] + ": perspective", 20, () -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
				cmd("tp @s 0.5 -60 0.5 0 4");
			});
			for (int ms : mss) {
				final int m = ms;
				Runnable checks = hakChecks(ms, views[v]);
				shotChecked("08_hakuteiken_t" + ms + "_" + views[v], m, Phase6Storm::castHakuteiken, turn(turns[v]), checks);
				step("hak " + views[v] + " t" + ms + ": face the line again", 2, () -> cmd("tp @s 0.5 -60 0.5 0 4"));
				waitSettled("hak " + views[v] + " t" + ms);
			}
		}
		// back view along the line (the line seen from behind the owner)
		step("hak: back view", 20, () -> {
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("tp @s 0.5 -60 0.5 0 4");
		});
		for (int ms : new int[] {1000, 1530, 1800}) {
			shotChecked("08_hakuteiken_back_t" + ms, ms, Phase6Storm::castHakuteiken, null, null);
			waitSettled("hak back t" + ms);
		}
		// night and the rows re-forming
		step("hak: night", 20, () -> cmd("time set midnight"));
		for (int ms : new int[] {1000, 1530}) {
			shotChecked("08_hakuteiken_night_t" + ms, ms, Phase6Storm::castHakuteiken, null, null);
			waitSettled("hak night t" + ms);
		}
		step("hak: noon, rows re-forming (3.6 s)", 20, () -> cmd("time set noon"));
		shotChecked("08_hakuteiken_reform_t3600", 3600, Phase6Storm::castHakuteiken, null, () -> cCheck("rows re-forming at 3.6 s (busy " + (rows() != null && rows().phaseBusy()) + ")",
				() -> rows() != null && rows().phaseBusy() ? null : "not busy"));
		waitSettled("hak reform");
		// reduce motion: no impact frame (replaced by a short flash)
		step("hak RM: on", 4, () -> FxConfig.reduceMotion = true);
		step("hak RM: impact frames before", 1, () -> impactBase = ScreenFx.impactFrames);
		shotChecked("08_hakuteiken_rm_t1530", 1530, Phase6Storm::castHakuteiken, null, () -> cCheck("reduce motion: no impact frame (" + ScreenFx.impactFrames + " vs " + impactBase + ")",
				() -> ScreenFx.impactFrames == impactBase ? null : "impact frames " + ScreenFx.impactFrames));
		waitSettled("hak RM");
		step("hak RM: off", 4, () -> {
			FxConfig.reduceMotion = false;
			impactBase = ScreenFx.impactFrames;
		});
		shotChecked("08_hakuteiken_impactframe_t1530", 1530, Phase6Storm::castHakuteiken, null, () -> cCheck("normal: exactly one impact frame (" + ScreenFx.impactFrames + " vs " + impactBase + ")",
				() -> ScreenFx.impactFrames == impactBase + 1 ? null : "impact frames " + ScreenFx.impactFrames));
		waitSettled("hak impact frame");
		step("hak: S5 hit sparks", 2, () -> cCheck("S5 HIT feedback received for the damaged pig (" + SwarmFx.hits + ")", () -> SwarmFx.hits > 0 ? null : "hits " + SwarmFx.hits));
		step("hak: back", 10, () -> cmd("kill @e[type=pig]", "reiatsu state sealed"));
	}

	private static int impactBase;

	private static Runnable hakChecks(int ms, String view) {
		if (!view.equals("front")) {
			return null;
		}
		return switch (ms) {
			case 500 -> () -> {
				cCheck("Hakuteiken state exists", () -> HakuteikenFx.states().size() == 1 ? null : "states " + HakuteikenFx.states().size());
				cCheck("rows phase 33 set on the client", () -> rows() != null && rows().phase == RowsPhase.CONSUMED ? null : "phase " + (rows() == null ? "no rows" : rows().phase));
				cCheck("condensing petals drawn at 0.5 s (" + StormRenderer.lastCondensed + ")", () -> StormRenderer.lastCondensed >= 200 ? null : "drawn " + StormRenderer.lastCondensed);
				sCheck("S3 rows anchor phase 33 on the server", p -> ZanpakutoManager.rowsPhase(p) == RowsPhase.CONSUMED ? null : "phase " + ZanpakutoManager.rowsPhase(p));
			};
			case 1000 -> () -> {
				cCheck("wings drawn at 1.0 s (fan " + f(WingHaloRenderer.lastFan) + ")", () -> WingHaloRenderer.lastWingsDrawn && Math.abs(WingHaloRenderer.lastFan - 1.0) < 0.2 ? null : "wings "
						+ WingHaloRenderer.lastWingsDrawn + " fan " + WingHaloRenderer.lastFan);
				cCheck("halo drawn at 1.0 s", () -> WingHaloRenderer.lastHaloDrawn ? null : "no halo");
				cCheck("wing and halo mesh cost " + f(WingHaloRenderer.lastMs) + " ms <= 1 ms (verts " + WingHaloRenderer.lastVertices + ")", () -> WingHaloRenderer.lastMs <= 1.0 ? null : "ms " + WingHaloRenderer.lastMs);
			};
			case 1530 -> () -> {
				cCheck("strike line drawn at 1.53 s (" + FxShapes.lastDrawn + " quads)", () -> FxShapes.lastDrawn > 0 ? null : "no shapes");
				cCheck("S4 line length " + (HakuteikenFx.states().isEmpty() ? 0 : f(HakuteikenFx.states().values().iterator().next().lineLength)) + " <= 28",
						() -> !HakuteikenFx.states().isEmpty() && HakuteikenFx.states().values().iterator().next().lineLength <= 28.001 && HakuteikenFx.states().values().iterator().next().lineLength > 1
								? null : "line length");
			};
			case 1800 -> () -> cCheck("burst reached (" + HakuteikenFx.bursts + ")", () -> HakuteikenFx.bursts >= 1 ? null : "bursts " + HakuteikenFx.bursts);
			default -> null;
		};
	}

	// ------------------------------------------------------------------------------------------ console

	/**
	 * Live console lines: {@code rowsup [view]} (real R and G), {@code stm ms name [view]} (a frozen storm shot), {@code hak ms name [turnDeg] [view]}
	 * (a frozen Hakuteiken shot, the owner turns {@code turnDeg} after the cast), {@code stmwait} (waits for the effects to be over).
	 */
	static boolean console(String[] w) {
		switch (w[0]) {
			case "rowsup" -> standRows("console rows", w.length > 1 ? w[1] : "far");
			case "stmwait" -> waitSettled("console");
			case "stm" -> {
				step("console: storm prep", 2, () -> {
					if (w.length > 3) {
						Phase6Swarm.view(w[3]);
					}
					cmd("reiatsu cooldowns clear", "reiatsu full");
				});
				shotChecked(w[2], Integer.parseInt(w[1]), Phase6Storm::castScatter, null, null);
				waitSettled("console storm");
			}
			case "hak" -> {
				double deg = w.length > 3 ? Double.parseDouble(w[3]) : 0;
				step("console: hak prep", 2, () -> {
					if (w.length > 4) {
						Phase6Swarm.view(w[4]);
					}
					cmd("reiatsu cooldowns clear", "reiatsu full");
				});
				shotChecked(w[2], Integer.parseInt(w[1]), Phase6Storm::castHakuteiken, deg == 0 ? null : turn(deg), null);
				if (deg != 0) {
					step("console: face the line again", 2, () -> cmd("tp @s 0.5 -60 0.5 0 4"));
				}
				waitSettled("console hak");
			}
			default -> {
				return false;
			}
		}
		return true;
	}
}
