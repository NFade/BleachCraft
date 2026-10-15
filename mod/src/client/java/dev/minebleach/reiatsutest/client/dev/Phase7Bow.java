package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.wants;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxClient;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxDelayed;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxParticles;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.UryuArrowRenderer;
import dev.minebleach.reiatsutest.client.fx.UryuBowFx;
import dev.minebleach.reiatsutest.client.fx.UryuFx;
import dev.minebleach.reiatsutest.client.model.DrawEvents;
import dev.minebleach.reiatsutest.core.fx.UryuArrowLook;
import dev.minebleach.reiatsutest.core.fx.UryuDrawLight;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.Perspective;

/**
 * Harness scenarios of U-FX 2 (URYU_VFX_STORYBOARD 2.1 to 2.4 and section 6), started from {@link Phase7Uryu#steps()}:
 * {@code gradlew runPhase7 -Phold=<scenario>[,<scenario>]}. Screenshots {@code run/screenshots/p7_02_*.png}. Nothing here was run by the
 * author (the game must not be launched from the author's tree); the numbers are the storyboard's, the checks are loose on purpose.
 *
 * <ul>
 * <li>{@code uryu_bow}: the bow forms from Reishi. The local player is in BASE with the bow; the draw event is fired by hand and the FX clock
 * freezes exactly {@code t} ms later: {@code p7_02_bowform_t{0,100,180,300,380,450}.png} (third person from the front) and
 * {@code p7_02_bowform_fp_t*} (first person: the wheel stays off the crosshair), the night versions {@code p7_02_bowform_night_t*}, the reverse
 * {@code p7_02_bowform_rev_t{50,150,250}.png}.</li>
 * <li>{@code uryu_draw}: the real draw (the use key held): {@code p7_02_draw_p{0,25,50,100}.png} (third person from the front, plus
 * {@code p7_02_draw_back_p*} and first person {@code p7_02_draw_fp_p*}), full draw with Focus 3 ({@code p7_02_draw_f3_p100}) and with Reduce
 * Motion ({@code p7_02_draw_rm_p100}). The shot is taken the tick the draw time is reached, so p is within one tick; the log line says the real p.</li>
 * <li>{@code uryu_arrow}: a basic arrow flown by the same packets the server sends (ARROWS every 50 ms, IMPACT), on the FX clock:
 * {@code p7_02_arrow_{t50,t300}_{noon,midnight}.png} (Focus 0 and 3), {@code p7_02_hit_f0/f3.png}, {@code p7_02_hit_block.png} (floor scar), and
 * {@code uryu_arrow_live} shoots a real arrow at a zombie.</li>
 * <li>{@code uryu_focus}: the Focus feedback by FOCUS packets: {@code p7_02_focus_{0,1,2,3}.png} (third person and first person), the pop
 * {@code p7_02_focus_pop} and the reset burst {@code p7_02_focus_loss}, Reduce Motion and tier Low at Focus 3.</li>
 * <li>{@code uryu_f12}: the F12 frame test (Focus 3, bow drawn, four arrows in flight in a loop, 400 frames after 90 warm-up) with mean and 1 percent low.</li>
 * </ul>
 */
final class Phase7Bow {
	private static final String ZOMBIE = "summon minecraft:zombie %.1f -60 %.1f {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],"
			+ "Health:200f,Attributes:[{id:\"minecraft:generic.max_health\",base:200}]}";
	private static int nextArrowId = 9100;
	private static final List<Double> FRAME_MS = new ArrayList<>();
	private static boolean recording;
	private static long lastFrameNanos;
	private static boolean hooked;
	private static long perfFrames0;

	private Phase7Bow() {
	}

	static void steps() {
		if (wants("uryu_bow")) {
			bow();
		}
		if (wants("uryu_draw")) {
			draw();
		}
		if (wants("uryu_arrow")) {
			arrow();
		}
		if (wants("uryu_arrow_live")) {
			arrowLive();
		}
		if (wants("uryu_focus")) {
			focus();
		}
		if (wants("uryu_f12")) {
			f12();
		}
	}

	// ============================================================================================================ common

	private static void clearAll() {
		FxClock.unfreeze();
		FxTimelines.setFreezeAt(-1);
		FxTimelines.clear();
		FxDelayed.clear();
		ScreenFx.clear();
		UryuArrowRenderer.clear();
		FxGlowBatch.clear();
		FxParticles.clearAll();
		mc.options.useKey.setPressed(false);
	}

	/** The local player as Uryu in BASE with the bow in slot 0, standing at (0.5, 0.5) looking along +z; {@code view} first / front / back. */
	private static void scene(String name, String view, boolean night) {
		step(name + ": scene", 40, () -> {
			clearAll();
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(70);
			Phase6Harness.graphics(GraphicsMode.FANCY);
			switch (view) {
				case "fp" -> mc.options.setPerspective(Perspective.FIRST_PERSON);
				case "back" -> mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				default -> mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
			}
			cmd("tp @s 0.5 -60 0.5 0 0", "difficulty easy", "gamerule doMobSpawning false", "gamerule mobGriefing false", "time set " + (night ? "midnight" : "noon"),
					"kill @e[type=minecraft:zombie]", "item replace entity @s hotbar.0 with reiatsu_test:uryu_cross");
			Phase6Harness.selectSlot(0);
			cmd("reiatsu cooldowns clear", "reiatsu state base uryu", "reiatsu full");
		});
		step(name + ": settle", 24, () -> { });
	}

	private static void focusPacket(int stacks) {
		UryuFx.onArrows(new UryuArrowsS2C(UryuArrowsS2C.FOCUS, 0, new int[] {stacks, mc.player.getId()}, new float[0]));
	}

	private static void cleanup(String name) {
		step(name + ": cleanup", 4, () -> {
			clearAll();
			focusPacket(0);
			FxConfig.reduceMotion = false;
			cmd("kill @e[type=minecraft:zombie]", "reiatsu state sealed uryu", "time set noon");
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
		});
	}

	// ============================================================================================================ bow forming (2.1)

	private static void formShot(String name, int ms, boolean reverse) {
		step(name + " (arm)", 2, () -> {
			FxClock.unfreeze();
			FxDelayed.clear();
			UryuFx.onDraw(reverse ? DrawEvents.Phase.SHEATHE_START : DrawEvents.Phase.DRAW_START, mc.player, true);
			FxClock.freezeIn(ms / 1000.0);
		});
		stepUntil(name + " (frozen)", 2, 20 * 15, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			ReiatsuTest.LOGGER.info("[phase7] SHOT {} t {} ms: form sprites {} formsStarted {} formAge {}", name, ms, UryuBowFx.lastFormSprites, UryuBowFx.formsStarted,
					String.format(Locale.ROOT, "%.3f", UryuBowFx.formAge(mc.player.getId())));
			if (ms >= 100 && ms <= 450 && !reverse) {
				cCheck(name + ": the forming layers draw glow sprites (" + UryuBowFx.lastFormSprites + ")", () -> UryuBowFx.lastFormSprites >= 3 ? null : "sprites " + UryuBowFx.lastFormSprites);
			}
			if (!reverse && ms < 450) {
				cCheck(name + ": the item side still forms (age " + UryuBowFx.formAge(mc.player.getId()) + ")", () -> UryuBowFx.formAge(mc.player.getId()) >= 0 ? null : "no forming age");
			}
		});
		step(name + " (release)", 2, FxClock::unfreeze);
	}

	private static void bow() {
		for (String view : new String[] {"front", "fp"}) {
			for (boolean night : new boolean[] {false, true}) {
				if (view.equals("fp") && night) {
					continue;
				}
				scene("bow " + view + (night ? " night" : ""), view, night);
				String pre = "02_bowform_" + (view.equals("fp") ? "fp_" : night ? "night_" : "");
				for (int ms : new int[] {0, 100, 180, 300, 380, 450}) {
					formShot(pre + "t" + ms, ms, false);
				}
			}
		}
		scene("bow reverse", "front", false);
		for (int ms : new int[] {50, 150, 250}) {
			formShot("02_bowform_rev_t" + ms, ms, true);
		}
		cleanup("bow");
	}

	// ============================================================================================================ the draw (2.2)

	private static void drawShot(String name, double p, boolean rm) {
		final int ticks = Math.max(1, (int) Math.ceil(UryuDrawLight.ticksForPower(p)));
		step(name + " (hold)", 1, () -> {
			FxConfig.reduceMotion = rm;
			mc.options.useKey.setPressed(true);
		});
		stepUntil(name + " (drawn)", 1, 20 * 6, () -> { }, () -> mc.player.isUsingItem() && mc.player.getItemUseTime() >= ticks + (p >= 1.0 ? 6 : 0));
		step(name + " (shot)", 1, () -> {
			Phase7Uryu.shot7(name);
			double real = dev.minebleach.reiatsutest.core.uryu.BowDraw.powerAt(mc.player.getItemUseTime());
			ReiatsuTest.LOGGER.info("[phase7] SHOT {} target p {} real p {} (use time {}): draw sprites {} focus {}", name, p, String.format(Locale.ROOT, "%.3f", real),
					mc.player.getItemUseTime(), UryuBowFx.lastDrawSprites, UryuFx.focus);
			if (p >= 0.25) {
				cCheck(name + ": the draw light writes glow sprites (" + UryuBowFx.lastDrawSprites + ")", () -> UryuBowFx.lastDrawSprites >= 3 ? null : "sprites " + UryuBowFx.lastDrawSprites);
			}
			mc.options.useKey.setPressed(false);
			FxConfig.reduceMotion = false;
		});
		step(name + " (let go)", 24, () -> { });
	}

	private static void draw() {
		for (String view : new String[] {"front", "back", "fp"}) {
			scene("draw " + view, view, false);
			String pre = "02_draw_" + (view.equals("front") ? "" : view + "_");
			for (double p : new double[] {0.0, 0.25, 0.5, 1.0}) {
				drawShot(pre + "p" + (int) Math.round(p * 100), p, false);
			}
		}
		scene("draw focus 3", "front", false);
		step("draw focus 3: stacks", 30, () -> focusPacket(3));
		drawShot("02_draw_f3_p100", 1.0, false);
		scene("draw rm", "front", false);
		drawShot("02_draw_rm_p100", 1.0, true);
		cleanup("draw");
	}

	// ============================================================================================================ the arrow (2.3)

	/** The packets the server sends for one arrow: ARROWS every 50 ms, the IMPACT, then an empty list. */
	private static void flight(int focus, double[] from, double[] dir, int lastTick, int impactType, boolean ground) {
		final int id = nextArrowId++;
		final double v = UryuArrowLook.launchSpeed(1.0, focus);
		final int kind = UryuArrowLook.BASIC;
		for (int k = 0; k <= lastTick; k++) {
			final int step = k;
			FxDelayed.after(k * 0.05, () -> {
				float[] d = {(float) (from[0] + dir[0] * v * step), (float) (from[1] + dir[1] * v * step), (float) (from[2] + dir[2] * v * step), (float) (dir[0] * v), (float) (dir[1] * v),
						(float) (dir[2] * v), focus | (kind << 2)};
				UryuFx.onArrows(new UryuArrowsS2C(UryuArrowsS2C.ARROWS, step, new int[] {id}, d));
			});
		}
		if (impactType >= 0) {
			final double[] hit = {from[0] + dir[0] * v * lastTick, from[1] + dir[1] * v * lastTick, from[2] + dir[2] * v * lastTick};
			FxDelayed.after(lastTick * 0.05 + 0.002, () -> {
				UryuFx.onArrows(new UryuArrowsS2C(UryuArrowsS2C.IMPACT, lastTick, new int[0], new float[] {(float) hit[0], (float) hit[1], (float) hit[2], impactType, kind}));
				UryuFx.onArrows(new UryuArrowsS2C(UryuArrowsS2C.ARROWS, lastTick + 1, new int[0], new float[0]));
			});
		}
	}

	private static void arrowShot(String name, int ms, int focus, boolean hit) {
		step(name + " (arm)", 2, () -> {
			FxClock.unfreeze();
			FxDelayed.clear();
			UryuArrowRenderer.clear();
			FxGlowBatch.clear();
			FxParticles.clearAll();
			focusPacket(focus);
			double[] eye = {mc.player.getX(), mc.player.getEyeY() - 0.1, mc.player.getZ() + 0.6};
			if (hit) {
				flight(focus, eye, new double[] {0, 0, 1}, 4, dev.minebleach.reiatsutest.net.UryuArrowsS2C.IMPACT_ENTITY, false);
			} else {
				flight(focus, eye, new double[] {0, 0, 1}, 20, -1, false);
			}
			FxClock.freezeIn(ms / 1000.0);
		});
		stepUntil(name + " (frozen)", 2, 20 * 15, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			ReiatsuTest.LOGGER.info("[phase7] SHOT {} t {} ms focus {}: arrows live {} drawn {} glow {} muzzles {} hits {}", name, ms, focus, UryuArrowRenderer.live(),
					UryuArrowRenderer.lastDrawn, UryuArrowRenderer.lastGlow, dev.minebleach.reiatsutest.client.fx.UryuFxStats.muzzles(),
					dev.minebleach.reiatsutest.client.fx.UryuFxStats.hits());
			if (!hit) {
				cCheck(name + ": the arrow is live and drawn (" + UryuArrowRenderer.live() + " / " + UryuArrowRenderer.lastDrawn + ")",
						() -> UryuArrowRenderer.live() == 1 && UryuArrowRenderer.lastDrawn == 1 ? null : "live " + UryuArrowRenderer.live() + " drawn " + UryuArrowRenderer.lastDrawn);
			}
		});
		step(name + " (release)", 2, FxClock::unfreeze);
	}

	private static void arrow() {
		for (boolean night : new boolean[] {false, true}) {
			scene("arrow" + (night ? " night" : ""), "back", night);
			String tag = night ? "midnight" : "noon";
			for (int ms : new int[] {50, 300}) {
				arrowShot("02_arrow_t" + ms + "_" + tag, ms, 0, false);
			}
			arrowShot("02_arrow_f3_t300_" + tag, 300, 3, false);
		}
		scene("hit", "back", false);
		step("hit: zombie", 6, () -> cmd(String.format(Locale.ROOT, ZOMBIE, 0.5, 2.7)));
		arrowShot("02_hit_f0", 230, 0, true);
		arrowShot("02_hit_f3", 230, 3, true);
		// a block hit on the floor (the scar): the arrow comes from above, straight down
		step("hit block: arm", 2, () -> {
			FxClock.unfreeze();
			FxDelayed.clear();
			UryuArrowRenderer.clear();
			focusPacket(0);
			final int id = nextArrowId++;
			final double v = UryuArrowLook.launchSpeed(1.0, 0);
			for (int k = 0; k <= 3; k++) {
				final int step = k;
				FxDelayed.after(k * 0.05, () -> UryuFx.onArrows(new UryuArrowsS2C(UryuArrowsS2C.ARROWS, step, new int[] {id},
						new float[] {0.5f, (float) (-53.0 - v * step), 4.5f, 0f, (float) -v, 0f, UryuArrowLook.BASIC << 2})));
			}
			FxDelayed.after(3 * 0.05 + 0.002, () -> UryuFx.onArrows(new UryuArrowsS2C(UryuArrowsS2C.IMPACT, 3, new int[0],
					new float[] {0.5f, -60.0f, 4.5f, UryuArrowsS2C.IMPACT_BLOCK, UryuArrowLook.BASIC})));
			FxClock.freezeIn(0.25);
		});
		stepUntil("hit block (frozen)", 2, 20 * 15, () -> { }, () -> FxClock.frozen);
		step("hit block (shot)", 3, () -> Phase7Uryu.shot7("02_hit_block"));
		step("hit block (release)", 2, FxClock::unfreeze);
		cleanup("arrow");
	}

	private static void arrowLive() {
		scene("arrow live", "back", false);
		step("arrow live: zombie", 6, () -> cmd(String.format(Locale.ROOT, ZOMBIE, 0.5, 12.5)));
		step("arrow live: draw", 1, () -> mc.options.useKey.setPressed(true));
		stepUntil("arrow live: full", 1, 20 * 6, () -> { }, () -> mc.player.isUsingItem() && mc.player.getItemUseTime() >= 22);
		step("arrow live: release", 1, () -> mc.options.useKey.setPressed(false));
		step("arrow live: flight shot", 4, () -> Phase7Uryu.shot7("02_arrow_live_a"));
		step("arrow live: hit shot", 4, () -> Phase7Uryu.shot7("02_arrow_live_b"));
		step("arrow live: wait", 30, () -> { });
		step("arrow live: checks", 2, () -> {
			cCheck("arrow live: a muzzle and a hit were played (" + dev.minebleach.reiatsutest.client.fx.UryuFxStats.muzzles() + " / "
					+ dev.minebleach.reiatsutest.client.fx.UryuFxStats.hits() + ")",
					() -> dev.minebleach.reiatsutest.client.fx.UryuFxStats.muzzles() >= 1 && dev.minebleach.reiatsutest.client.fx.UryuFxStats.hits() >= 1 ? null
							: "muzzles " + dev.minebleach.reiatsutest.client.fx.UryuFxStats.muzzles() + " hits " + dev.minebleach.reiatsutest.client.fx.UryuFxStats.hits());
		});
		cleanup("arrow live");
	}

	// ============================================================================================================ Focus (2.4)

	private static void focusShot(String name, int stacks, String view) {
		step(name + " (stacks)", 50, () -> focusPacket(stacks));
		step(name + " (shot)", 2, () -> {
			Phase7Uryu.shot7(name);
			ReiatsuTest.LOGGER.info("[phase7] SHOT {} stacks {}: focus sprites {} fx focus {}", name, stacks, dev.minebleach.reiatsutest.client.fx.UryuFxStats.focusSprites(), UryuFx.focus);
			if (stacks >= 2) {
				cCheck(name + ": the layers write glow sprites (" + dev.minebleach.reiatsutest.client.fx.UryuFxStats.focusSprites() + ")",
						() -> dev.minebleach.reiatsutest.client.fx.UryuFxStats.focusSprites() >= 2 ? null : "sprites " + dev.minebleach.reiatsutest.client.fx.UryuFxStats.focusSprites());
			}
		});
	}

	private static void focus() {
		for (String view : new String[] {"front", "fp"}) {
			scene("focus " + view, view, true);
			String pre = view.equals("fp") ? "02_focus_fp_" : "02_focus_";
			for (int s = 0; s <= 3; s++) {
				focusShot(pre + s, s, view);
			}
		}
		scene("focus pop", "front", true);
		step("focus pop: arm", 2, () -> {
			FxClock.unfreeze();
			focusPacket(0);
		});
		step("focus pop: gain", 2, () -> {
			focusPacket(1);
			FxClock.freezeIn(0.07);
		});
		stepUntil("focus pop (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step("focus pop (shot)", 3, () -> {
			Phase7Uryu.shot7("02_focus_pop");
			cCheck("focus pop: a pop was played (" + dev.minebleach.reiatsutest.client.fx.UryuFxStats.pops() + ")", () -> dev.minebleach.reiatsutest.client.fx.UryuFxStats.pops() >= 1 ? null : "no pop");
		});
		step("focus pop: release", 2, FxClock::unfreeze);
		step("focus loss: set 3", 40, () -> focusPacket(3));
		step("focus loss: arm", 2, () -> {
			focusPacket(0);
			FxClock.freezeIn(0.08);
		});
		stepUntil("focus loss (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step("focus loss (shot)", 3, () -> {
			Phase7Uryu.shot7("02_focus_loss");
			cCheck("focus loss: a reset burst was played (" + dev.minebleach.reiatsutest.client.fx.UryuFxStats.losses() + ")",
					() -> dev.minebleach.reiatsutest.client.fx.UryuFxStats.losses() >= 1 ? null : "no burst");
		});
		step("focus loss: release", 2, FxClock::unfreeze);
		// Reduce Motion and tier Low at Focus 3
		step("focus rm: set", 50, () -> {
			FxConfig.reduceMotion = true;
			focusPacket(3);
		});
		step("focus rm: shot", 2, () -> Phase7Uryu.shot7("02_focus_rm_3"));
		step("focus low: set", 50, () -> {
			FxConfig.reduceMotion = false;
			FxConfig.effectQuality = 0.25f;
		});
		step("focus low: shot", 2, () -> {
			Phase7Uryu.shot7("02_focus_low_3");
			FxConfig.effectQuality = 1.0f;
		});
		cleanup("focus");
	}

	// ============================================================================================================ F12 (performance)

	private static void hookFrames() {
		if (hooked) {
			return;
		}
		hooked = true;
		WorldRenderEvents.START.register(ctx -> {
			if (!recording) {
				return;
			}
			long now = System.nanoTime();
			if (lastFrameNanos != 0) {
				FRAME_MS.add((now - lastFrameNanos) / 1.0e6);
			}
			lastFrameNanos = now;
		});
	}

	private static void f12() {
		scene("f12", "back", false);
		step("f12: load", 4, () -> {
			hookFrames();
			focusPacket(3);
			mc.options.useKey.setPressed(true);
		});
		step("f12: warm up", 90, () -> { });
		step("f12: measure", 1, () -> {
			FRAME_MS.clear();
			FxClient.Stats.reset();
			perfFrames0 = FxClock.frame;
			lastFrameNanos = 0;
			recording = true;
		});
		// four arrows in flight all the time: a new volley of four every second
		step("f12: volleys", 1, () -> {
			for (int v = 0; v < 8; v++) {
				final int n = v;
				FxDelayed.after(v * 1.0, () -> {
					for (int i = 0; i < 4; i++) {
						double[] from = {mc.player.getX() + (i - 1.5) * 0.4, mc.player.getEyeY() - 0.1, mc.player.getZ() + 0.6};
						flight(i % 2 == 0 ? 3 : 1, from, new double[] {0, 0, 1}, 18, -1, false);
					}
				});
			}
		});
		stepUntil("f12: wait", 5, 20 * 40, () -> { }, () -> FxClock.frame - perfFrames0 >= 400);
		step("f12: result", 3, () -> {
			recording = false;
			mc.options.useKey.setPressed(false);
			List<Double> f = new ArrayList<>(FRAME_MS);
			if (f.size() < 30) {
				cCheck("F12: enough frames", () -> "only " + f.size());
				return;
			}
			double mean = f.stream().mapToDouble(Double::doubleValue).average().orElse(0);
			double[] sorted = f.stream().mapToDouble(Double::doubleValue).sorted().toArray();
			double p99 = sorted[(int) Math.min(sorted.length - 1, Math.floor(sorted.length * 0.99))];
			ReiatsuTest.LOGGER.info(String.format(Locale.ROOT,
					"[phase7] PERF F12 (Focus 3, bow drawn, 4 arrows in flight): %d frames, mean %.2f ms = %.0f FPS, 1%% low %.2f ms = %.0f FPS; reiatsu_fx mean %.3f ms max %.3f ms; glow drawn %d, particles %d, arrows drawn %d",
					f.size(), mean, 1000 / mean, p99, 1000 / p99, FxClient.Stats.meanMs(), FxClient.Stats.maxMs, FxGlowBatch.lastDrawn, FxParticles.live(), UryuArrowRenderer.lastDrawn));
			cCheck("F12: reiatsu_fx mean " + String.format(Locale.ROOT, "%.3f", FxClient.Stats.meanMs()) + " ms <= 4 ms", () -> FxClient.Stats.meanMs() <= 4.0 ? null : "mean " + FxClient.Stats.meanMs());
			cCheck("F12: mean " + String.format(Locale.ROOT, "%.0f", 1000 / mean) + " FPS >= 60", () -> 1000 / mean >= 60 ? null : "mean fps " + 1000 / mean);
			cCheck("F12: 1% low " + String.format(Locale.ROOT, "%.0f", 1000 / p99) + " FPS >= 45", () -> 1000 / p99 >= 45 ? null : "1% low " + 1000 / p99);
		});
		cleanup("f12");
	}
}
