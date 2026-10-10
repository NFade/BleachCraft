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

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.BankaiBladeRenderer;
import dev.minebleach.reiatsutest.client.fx.FxClient;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxParticles;
import dev.minebleach.reiatsutest.client.fx.FxScene;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.PetalSwarmRenderer;
import dev.minebleach.reiatsutest.client.fx.RowsFx;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.SwarmFx;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.server.ZanpakutoManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.Perspective;

/** Phase 6 step 3 scenarios: Byakuya shikai swarm (release / Chire, attack mode, dome) and the swarm frame cost (F1). Also the shared frame statistics. */
final class Phase6Swarm {
	private Phase6Swarm() {
	}

	// ------------------------------------------------------------------------------------------ frame statistics

	/** Frame times recorded at WorldRenderEvents.END while active: mean, 1 percent low, and our own renderer cost. */
	static final class Frames {
		private static boolean registered;
		private static boolean active;
		private static long last;
		private static final List<Double> TIMES = new ArrayList<>();
		static int skip;

		static void begin(int warmup) {
			if (!registered) {
				registered = true;
				WorldRenderEvents.END.register(ctx -> {
					long now = System.nanoTime();
					if (active && last != 0) {
						if (skip > 0) {
							skip--;
						} else {
							TIMES.add((now - last) / 1.0e6);
						}
					}
					last = now;
				});
			}
			TIMES.clear();
			skip = warmup;
			active = true;
			last = 0;
			FxClient.Stats.reset();
			PetalSwarmRenderer.resetStats();
			BankaiBladeRenderer.resetStats();
		}

		static int count() {
			return TIMES.size();
		}

		static void end() {
			active = false;
		}

		/** {mean ms, p99 ms, mean fps, 1 percent low fps}. */
		static double[] result() {
			if (TIMES.isEmpty()) {
				return new double[] {0, 0, 0, 0};
			}
			double[] a = TIMES.stream().mapToDouble(Double::doubleValue).toArray();
			double mean = Arrays.stream(a).average().orElse(0);
			Arrays.sort(a);
			double p99 = a[Math.min(a.length - 1, (int) Math.ceil(a.length * 0.99) - 1)];
			return new double[] {mean, p99, 1000.0 / mean, 1000.0 / p99};
		}
	}

	/** Measures N frames after the warm-up and logs one line; {@code check} gets the numbers. */
	static void perfWindow(String name, int frames, java.util.function.Consumer<double[]> check) {
		step("perf " + name + ": begin", 1, () -> Frames.begin(90));
		stepUntil("perf " + name + ": wait", 5, 20 * 60, () -> { }, () -> Frames.count() >= frames);
		step("perf " + name + ": result", 2, () -> {
			Frames.end();
			double[] r = Frames.result();
			double petals = PetalSwarmRenderer.frames == 0 ? 0 : PetalSwarmRenderer.sumMs / PetalSwarmRenderer.frames;
			double blades = BankaiBladeRenderer.frames == 0 ? 0 : BankaiBladeRenderer.sumMs / BankaiBladeRenderer.frames;
			ReiatsuTest.LOGGER.info("[phase6] FPS {}: mean {} ms ({} fps), p99 {} ms (1% low {} fps), reiatsu_fx glow {} ms, petals {} ms (verts {}, petals {}, near {}), blades {} ms (cpu verts {} blades {}, static verts {}, static {}), live particles {} glow {}",
					name, f(r[0]), f(r[2]), f(r[1]), f(r[3]), f(FxClient.Stats.meanMs()), f(petals), PetalSwarmRenderer.lastVertices, PetalSwarmRenderer.lastPetals,
					PetalSwarmRenderer.lastNear, f(blades), BankaiBladeRenderer.lastCpuVertices, BankaiBladeRenderer.lastCpuBlades, BankaiBladeRenderer.lastStaticVertices,
					BankaiBladeRenderer.lastStatic, FxParticles.live(), FxGlowBatch.live());
			if (check != null) {
				check.accept(new double[] {r[0], r[1], r[2], r[3], petals, blades, PetalSwarmRenderer.lastVertices + BankaiBladeRenderer.lastCpuVertices});
			}
		});
	}

	static String f(double v) {
		return String.format(java.util.Locale.ROOT, "%.2f", v);
	}

	// ------------------------------------------------------------------------------------------ helpers

	static void resetWorldFx() {
		FxClock.unfreeze();
		FxTimelines.clear();
		FxTimelines.setFreezeAt(-1);
		ScreenFx.clear();
		SwarmFx.clear();
		RowsFx.clear();
	}

	static void toBase(boolean full) {
		cmd("item replace entity @s hotbar.1 with " + Phase6Harness.BYAKUYA, "reiatsu cooldowns clear", "reiatsu state base byakuya", "reiatsu full");
		selectSlot(1);
	}

	/** Frozen shot at {@code ms} of the first running timeline after {@code trigger} ran. */
	static void eventShot(String name, int ms, Runnable trigger) {
		step(name + " (arm)", 2, () -> FxTimelines.setFreezeAt(ms));
		step(name + " (go)", 1, trigger);
		stepUntil(name + " (frozen)", 2, 20 * 12, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> shot(name));
		step(name + " (release clock)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
		});
	}

	/** Frozen shot {@code seconds} of FX time from now (idle swarm and the like). */
	static void clockShot(String name, double seconds) {
		step(name + " (run)", 1, () -> FxClock.freezeIn(seconds));
		stepUntil(name + " (frozen)", 2, 20 * 20, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> shot(name));
		step(name + " (release clock)", 2, FxClock::unfreeze);
	}

	static void view(String kind) {
		switch (kind) {
			case "front" -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
				cmd("tp @s 0.5 -60 0.5 0 12");
			}
			case "side" -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				cmd("tp @s 0.5 -60 0.5 90 8");
			}
			case "top" -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				cmd("tp @s 0.5 -60 0.5 0 55");
			}
			default -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				cmd("tp @s 0.5 -60 0.5 0 12");
			}
		}
	}

	// ------------------------------------------------------------------------------------------ the scenario

	static void steps() {
		step("sw: setup (third person back, noon)", 30, () -> {
			cmd("gamemode creative @s", "time set noon", "tp @s 0.5 -60 0.5 0 12", "kill @e[type=pig]");
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			mc.options.hudHidden = true;
			FxConfig.reduceMotion = false;
			FxScene.requireAnchor = true;
			resetWorldFx();
			ZanpakutoManager.class.getName();
		});
		// release shots (each from BASE with a real R press)
		for (int ms : new int[] {100, 300, 500}) {
			final int m = ms;
			step("sw release t" + ms + ": base", 30, () -> {
				resetWorldFx();
				toBase(true);
			});
			eventShot("03_swarm_t" + ms, ms, () -> press(ReiatsuKeys.RELEASE));
		}
		step("sw: server anchor after the release", 6, () -> { });
		step("server check", 3, () -> sCheck("S1 swarm anchor spawned by the release (1 alive)", p -> ZanpakutoManager.anchorCount(p) == 1 ? null : "anchors " + ZanpakutoManager.anchorCount(p)));
		step("sw: client states", 2, () -> {
			cCheck("client swarm state exists (" + SwarmFx.states().size() + ")", () -> SwarmFx.states().size() == 1 ? null : "states " + SwarmFx.states().size());
			cCheck("anchor entity seen by the client", () -> FxScene.swarmAnchors == 1 ? null : "anchors " + FxScene.swarmAnchors);
			cCheck("petals drawn " + PetalSwarmRenderer.lastPetals + " of " + SwarmFx.petalCount(), () -> PetalSwarmRenderer.lastPetals >= SwarmFx.petalCount() * 0.85 ? null : "drawn " + PetalSwarmRenderer.lastPetals);
		});
		step("sw: let the release finish", 60, () -> { });
		clockShot("03_swarm_idle_t3000", 3.0);
		// the glitter strip: four frames 0.08 s apart
		for (int k = 0; k < 4; k++) {
			clockShot("03_swarm_glint_g" + k, k == 0 ? 0.2 : 0.08);
		}
		step("sw: front view", 20, () -> view("front"));
		clockShot("03_swarm_front_idle", 0.5);
		step("sw: side view", 20, () -> view("side"));
		clockShot("03_swarm_side_idle", 0.5);
		step("sw: night", 20, () -> {
			cmd("time set midnight");
			view("back");
		});
		clockShot("03_swarm_night_idle", 0.5);
		step("sw: noon", 20, () -> cmd("time set noon"));
		// attack mode with a pig at the aim (S5 hit sparks)
		step("sw attack: pig at the aim", 20, () -> {
			SwarmFx.hits = 0;
			cmd("summon pig 0.5 -60 8.2 {NoAI:1b,Silent:1b,Invulnerable:0b}", "reiatsu cooldowns clear", "reiatsu full");
			view("back");
		});
		for (int ms : new int[] {300, 500, 1000}) {
			eventShot("03_attack_t" + ms, ms, () -> press(ReiatsuKeys.SLOTS[0]));
			step("sw attack t" + ms + ": reset", 70, () -> cmd("reiatsu cooldowns clear", "reiatsu full"));
		}
		step("sw attack: hit sparks", 2, () -> {
			cCheck("S5 HIT feedback received for the damaged pig (" + SwarmFx.hits + ")", () -> SwarmFx.hits > 0 ? null : "hits " + SwarmFx.hits);
			cmd("kill @e[type=pig]");
		});
		// dome
		for (int ms : new int[] {300, 600, 1500, 5000}) {
			step("sw dome t" + ms + ": prep", 40, () -> cmd("reiatsu cooldowns clear", "reiatsu full"));
			eventShot("03_dome_t" + ms, ms, () -> press(ReiatsuKeys.SLOTS[1]));
			step("sw dome t" + ms + ": wait", ms >= 5000 ? 140 : 20, () -> { });
		}
		step("sw: dome top view", 20, () -> {
			cmd("reiatsu cooldowns clear", "reiatsu full");
			view("top");
		});
		eventShot("03_dome_top_t1500", 1500, () -> press(ReiatsuKeys.SLOTS[1]));
		step("sw: seal", 40, () -> view("back"));
		step("sw: V", 1, () -> press(ReiatsuKeys.SEAL));
		step("sw: sealed", 60, () -> { });
		step("server check", 3, () -> sCheck("swarm anchor gone after the seal", p -> ZanpakutoManager.anchorCount(p) == 0 ? null : "anchors " + ZanpakutoManager.anchorCount(p)));
		step("sw: client states cleared after the seal", 2, () -> cCheck("client swarm state removed (" + SwarmFx.states().size() + ")", () -> SwarmFx.states().isEmpty() ? null : "states " + SwarmFx.states().size()));
	}

	/** F1: 1000 petals idle (and Medium, Low). Uses the real release. */
	static void perfSteps() {
		step("sw perf: setup", 30, () -> {
			mc.options.hudHidden = true;
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("time set noon", "tp @s 0.5 -60 0.5 0 12");
			mc.options.getMaxFps().setValue(260);
			resetWorldFx();
			toBase(true);
		});
		step("sw perf: R", 1, () -> press(ReiatsuKeys.RELEASE));
		step("sw perf: settle", 100, () -> { });
		perfWindow("F0 baseline-ish (swarm 1000 idle counted as F1) High", 400, r -> {
			cCheck("F1 swarm 1000: mean >= 60 fps (" + f(r[2]) + ")", () -> r[2] >= 60 ? null : "fps " + r[2]);
			cCheck("F1 swarm 1000: 1% low >= 45 fps (" + f(r[3]) + ")", () -> r[3] >= 45 ? null : "low " + r[3]);
			cCheck("F1 petals CPU <= 4 ms (" + f(r[4]) + ")", () -> r[4] <= 4.0 ? null : "ms " + r[4]);
		});
		step("sw perf: Medium (500 petals)", 2, () -> {
			FxConfig.effectQuality = 0.5f;
			resetWorldFx();
			toBase(true);
		});
		step("sw perf: R medium", 30, () -> press(ReiatsuKeys.RELEASE));
		step("sw perf: settle medium", 100, () -> { });
		perfWindow("F1 swarm 500 (Medium)", 300, null);
		step("sw perf: Low (250 petals)", 2, () -> {
			FxConfig.effectQuality = 0.25f;
			resetWorldFx();
			toBase(true);
		});
		step("sw perf: R low", 30, () -> press(ReiatsuKeys.RELEASE));
		step("sw perf: settle low", 100, () -> { });
		perfWindow("F1 swarm 250 (Low)", 300, null);
		step("sw perf: restore", 2, () -> {
			FxConfig.effectQuality = 1.0f;
			mc.options.getMaxFps().setValue(120);
			cmd("reiatsu state sealed");
			resetWorldFx();
		});
	}

	// ------------------------------------------------------------------------------------------ live console commands (steps 3 and 4)

	/**
	 * Console lines of the swarm and rows tuning: {@code swrel ms name}, {@code swatk ms name}, {@code swdome ms name},
	 * {@code bk prefix view ms,ms,...}, {@code tp x y z yaw pitch}, {@code persp first|back|front}, {@code blades n}, {@code vbo on|off},
	 * {@code sealv}, {@code clearfx}, {@code quality q}. Returns false for an unknown command.
	 */
	static boolean console(String[] w) {
		switch (w[0]) {
			case "swrel" -> {
				step("console: swarm reset", 30, () -> {
					resetWorldFx();
					toBase(true);
				});
				eventShot(w[2], Integer.parseInt(w[1]), () -> press(ReiatsuKeys.RELEASE));
			}
			case "swatk" -> {
				step("console: swarm attack prep", 30, () -> cmd("reiatsu cooldowns clear", "reiatsu full"));
				eventShot(w[2], Integer.parseInt(w[1]), () -> press(ReiatsuKeys.SLOTS[0]));
				step("console: after attack", 60, () -> { });
			}
			case "swdome" -> {
				step("console: swarm dome prep", 30, () -> cmd("reiatsu cooldowns clear", "reiatsu full"));
				eventShot(w[2], Integer.parseInt(w[1]), () -> press(ReiatsuKeys.SLOTS[1]));
				step("console: after dome", 40, () -> { });
			}
			case "swidle" -> clockShot(w[2], Double.parseDouble(w[1]));
			case "bk" -> {
				String[] parts = w[3].split(",");
				int[] ms = new int[parts.length];
				for (int i = 0; i < ms.length; i++) {
					ms[i] = Integer.parseInt(parts[i]);
				}
				Phase6Rows.bankaiSeries(w[1], ms, w[2]);
			}
			case "bkshot" -> clockShot(w[2], Double.parseDouble(w[1]));
			case "tp" -> step("console: tp", 30, () -> cmd("tp @s " + w[1] + " " + w[2] + " " + w[3] + " " + w[4] + " " + w[5]));
			case "persp" -> step("console: perspective", 4, () -> mc.options.setPerspective(w[1].equals("first") ? Perspective.FIRST_PERSON
					: w[1].equals("front") ? Perspective.THIRD_PERSON_FRONT : Perspective.THIRD_PERSON_BACK));
			case "blades" -> step("console: blades", 2, () -> FxConfig.bankaiBladeCount = Integer.parseInt(w[1]));
			case "quality" -> step("console: quality", 2, () -> FxConfig.effectQuality = Float.parseFloat(w[1]));
			case "vbo" -> step("console: vbo", 2, () -> BankaiBladeRenderer.useVbo = w[1].equals("on"));
			case "sealv" -> {
				step("console: V", 1, () -> press(ReiatsuKeys.SEAL));
				step("console: sealed", 80, () -> resetWorldFx());
			}
			case "clearfx" -> step("console: clear fx", 20, () -> {
				resetWorldFx();
				cmd("reiatsu state sealed");
			});
			default -> {
				return false;
			}
		}
		return true;
	}
}
