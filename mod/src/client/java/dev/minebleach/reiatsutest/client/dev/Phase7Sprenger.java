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
import dev.minebleach.reiatsutest.client.fx.FxDelayed;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxParticles;
import dev.minebleach.reiatsutest.client.fx.FxShapes;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.UryuFx;
import dev.minebleach.reiatsutest.client.fx.UryuFxStats;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.fx.SprengerFxPlan;
import dev.minebleach.reiatsutest.core.uryu.BindEnd;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Harness scenarios of U-FX 4 (URYU_VFX_STORYBOARD 2.7, 2.8 and section 6), started from {@link Phase7Uryu#steps()}:
 * {@code gradlew runPhase7 -Phold=uryu_sprenger[,uryu_sprenger_live,uryu_sprenger_f16]}. Screenshots {@code run/screenshots/p7_04_sprenger_*.png}. Nothing
 * here was run by the author (the game must not be launched from the author's tree); the numbers are the storyboard's, the checks are loose on purpose.
 *
 * <ul>
 * <li>{@code uryu_sprenger}: the messages of the server (effect event 73, TRAP, BIND at 0.8 s, END(EXPLOSION) + BURST at 2.0 s) are fed to the client
 * on the FX clock ({@link FxDelayed}), two zombies stand in the pentagon (centre (0.5, 8.5), radius 3), and the clock freezes exactly {@code t} ms
 * after the TRAP: {@code p7_04_sprenger_t{0,220,550,800,1200,1750,2000,2050,2300,3000}.png} from the ground (first person, 8 blocks away),
 * {@code p7_04_sprenger_top_t*} from above (14 blocks high), {@code p7_04_sprenger_night_t*} at midnight and {@code p7_04_sprenger_fab_t*} in Fabulous.
 * The variants: {@code p7_04_sprenger_early_t*} (BURST early = true at 1.2 s: the Focus 3 arrow), {@code p7_04_sprenger_cancel_t*} (END CANCEL at
 * 1.0 s: the daggers sink, no fence, no explosion), {@code p7_04_sprenger_break_t*} (END DAMAGE of the first zombie at 1.1 s: the chains snap, the
 * other stays rooted until the explosion). The checks count daggers, curtains, roots and chains.</li>
 * <li>{@code uryu_sprenger_live}: the real ability (state shikai, key H at a floor point 8 blocks ahead, two real zombies that the server roots):
 * free running shots at about 0.3, 1.2 and 2.2 s; checks that the client got the TRAP, BIND and BURST packets.</li>
 * <li>{@code uryu_sprenger_f16}: the F16 frame test (three bound zombies and the explosion in a loop, 400 frames after 90 warm-up): mean and 1 percent low.</li>
 * </ul>
 */
final class Phase7Sprenger {
	/** Pentagon centre (feet level of the flat world: the floor surface is y = -60). */
	private static final double CX = 0.5;
	private static final double CY = -60.0;
	private static final double CZ = 8.5;
	private static final double R = UryuBalance.SPRENGER_RADIUS;
	/** Zombie positions (x, z) inside the pentagon. */
	private static final double[][] ZOMBIES = {{-0.5, 9.3}, {1.7, 7.8}, {0.5, 10.2}};
	private static final String ZOMBIE = "summon minecraft:zombie %.1f -60 %.1f {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],"
			+ "Health:200f,Attributes:[{id:\"minecraft:generic.max_health\",base:200}],ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}";
	private static final int[] MS = {0, 220, 550, 800, 1200, 1750, 2000, 2050, 2300, 3000};
	private static final int[] TOP_MS = {550, 1200, 2000, 2300};

	private enum Mode {
		NORMAL, EARLY, CANCEL, BREAK
	}

	private static int[] zids = new int[0];
	private static final List<Double> FRAME_MS = new ArrayList<>();
	private static boolean recording;
	private static boolean hooked;
	private static long lastFrameNanos;
	private static long perfFrames0;

	private Phase7Sprenger() {
	}

	static void steps() {
		if (wants("uryu_sprenger")) {
			emulated();
		}
		if (wants("uryu_sprenger_live")) {
			live();
		}
		if (wants("uryu_sprenger_f16")) {
			f16();
		}
	}

	// ============================================================================================================ scene

	private static void clearAll() {
		FxClock.unfreeze();
		FxTimelines.setFreezeAt(-1);
		FxTimelines.clear();
		FxDelayed.clear();
		ScreenFx.clear();
		FxShapes.clear();
		FxGlowBatch.clear();
		FxParticles.clearAll();
		UryuFxStats.sprengerReset();
	}

	/** {@code view}: ground (first person, 8 blocks from the centre), top (14 blocks up, looking down at the centre), back (third person). */
	private static void scene(String name, String view, boolean night, GraphicsMode gm, int zombies) {
		step("sprenger " + name + ": scene", 44, () -> {
			clearAll();
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(70);
			Phase6Harness.graphics(gm);
			cmd("difficulty easy", "gamerule doMobSpawning false", "gamerule mobGriefing false", "time set " + (night ? "midnight" : "noon"), "kill @e[type=minecraft:zombie]",
					"kill @e[type=minecraft:item]", "gamemode creative @s");
			switch (view) {
				case "top" -> {
					mc.options.setPerspective(Perspective.FIRST_PERSON);
					// a stone block to stand on 13 blocks above the floor, 7.8 blocks south of the centre, looking down at 62 degrees: the centre is in the middle of the picture
					cmd("setblock 0 -48 1 minecraft:stone", "tp @s 0.5 -47 0.7 0 62");
				}
				case "back" -> {
					mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
					cmd("tp @s 0.5 -60 0.5 0 10");
				}
				default -> {
					mc.options.setPerspective(Perspective.FIRST_PERSON);
					cmd("tp @s 0.5 -60 0.5 0 12");
				}
			}
			String[] z = new String[zombies];
			for (int i = 0; i < zombies; i++) {
				z[i] = String.format(Locale.ROOT, ZOMBIE, ZOMBIES[i][0], ZOMBIES[i][1]);
			}
			if (zombies > 0) {
				cmd(z);
			}
		});
		step("sprenger " + name + ": zombies", 10, () -> zids = zombieIds());
	}

	private static int[] zombieIds() {
		List<ZombieEntity> list = mc.world.getEntitiesByClass(ZombieEntity.class, new Box(CX - 6, CY - 2, CZ - 6, CX + 6, CY + 4, CZ + 6), z -> true);
		int[] ids = new int[list.size()];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = list.get(i).getId();
		}
		return ids;
	}

	private static void cleanup(String name) {
		step("sprenger " + name + ": cleanup", 4, () -> {
			clearAll();
			cmd("kill @e[type=minecraft:zombie]", "setblock 0 -48 1 minecraft:air", "reiatsu state sealed uryu", "time set noon");
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
			Phase6Harness.graphics(GraphicsMode.FANCY);
		});
	}

	// ============================================================================================================ the server's messages

	private static int tick() {
		return (int) (mc.world.getTime() & 0x7FFFFFFF);
	}

	/**
	 * What the server sends for one pentagon, on the FX clock (a hair before the exact times, so that a freeze at exactly 800 / 2000 ms already shows
	 * the first frame): effect event 73 and TRAP now, BIND at 0.8 s, then per mode the ending.
	 */
	private static void fire(Mode mode) {
		Vec3d feet = mc.player.getPos();
		Vec3d look = mc.player.getRotationVec(1.0F);
		int tick = tick();
		UryuFx.play(mc, new EffectEventS2C(73, mc.player.getId(), 7300, feet.x, feet.y, feet.z, (float) look.x, (float) look.y, (float) look.z, -1, tick,
				new float[] {(float) CX, (float) CY, (float) CZ}));
		UryuFx.onArrows(new UryuArrowsS2C(UryuArrowsS2C.TRAP, tick, new int[0], new float[] {(float) CX, (float) CY, (float) CZ, (float) R, UryuBalance.SPRENGER_EXPLODE_AT}));
		final int[] ids = zids.clone();
		FxDelayed.after(SprengerFxPlan.BIND_AT - 0.0005, () -> {
			if (ids.length > 0) {
				UryuFx.onArrows(UryuArrowsS2C.bind(tick, ids, CX, CY, CZ, UryuBalance.SPRENGER_BIND_TICKS));
			}
		});
		switch (mode) {
			case NORMAL -> FxDelayed.after(SprengerFxPlan.EXPLODE_AT - 0.0005, () -> explode(ids, false));
			case EARLY -> FxDelayed.after(1.2, () -> explode(ids, true));
			case CANCEL -> FxDelayed.after(1.0, () -> {
				if (ids.length > 0) {
					for (int id : ids) {
						UryuFx.onArrows(UryuArrowsS2C.end(tick, new int[] {id}, CX, CY, CZ, BindEnd.CANCEL));
					}
				}
			});
			case BREAK -> {
				FxDelayed.after(1.1, () -> {
					if (ids.length > 0) {
						UryuFx.onArrows(UryuArrowsS2C.end(tick, new int[] {ids[0]}, CX, CY, CZ, BindEnd.DAMAGE));
					}
				});
				FxDelayed.after(SprengerFxPlan.EXPLODE_AT - 0.0005, () -> explode(ids, false));
			}
		}
	}

	/** The explosion as the server sends it: END(EXPLOSION) for every bound one, then BURST. */
	private static void explode(int[] ids, boolean early) {
		int tick = tick();
		for (int id : ids) {
			UryuFx.onArrows(UryuArrowsS2C.end(tick, new int[] {id}, CX, CY, CZ, BindEnd.EXPLOSION));
		}
		UryuFx.onArrows(new UryuArrowsS2C(UryuArrowsS2C.BURST, tick, new int[0], new float[] {(float) CX, (float) CY, (float) CZ, (float) R, early ? 1f : 0f}));
	}

	// ============================================================================================================ one shot

	private static void shot(String name, int ms, Mode mode) {
		step(name + " (arm)", 2, () -> {
			clearAll();
			zids = zombieIds();
			FxClock.freezeIn(ms / 1000.0);
		});
		step(name + " (cast)", 1, () -> fire(mode));
		stepUntil(name + " (frozen)", 2, 20 * 14, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			ReiatsuTest.LOGGER.info("[phase7] SHOT {} t {} ms: pentagons {} daggers {} curtains {} roots {} full {} chains {} traps {} bursts {} cancels {} binds {} glow {} shapes {} particles {}", name, ms,
					UryuFxStats.sprengerLive(), UryuFxStats.daggersDrawn(), UryuFxStats.sprengerCurtains(), UryuFxStats.roots(), UryuFxStats.rootsFull(), UryuFxStats.rootChains(),
					UryuFxStats.sprengerTraps(), UryuFxStats.sprengerBursts(), UryuFxStats.sprengerCancels(), UryuFxStats.binds(), FxGlowBatch.lastDrawn, FxShapes.lastDrawn, FxParticles.live());
			checks(name, ms, mode);
		});
		step(name + " (release)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
		});
	}

	/** What must be on screen at the frozen moment (the rows of the storyboard). */
	private static void checks(String name, int ms, Mode mode) {
		final boolean exploded = mode == Mode.EARLY ? ms >= 1200 : mode == Mode.CANCEL ? false : ms >= 2000;
		final boolean cancelled = mode == Mode.CANCEL && ms >= 1000;
		final boolean standing = !exploded && !cancelled;
		final boolean roots = zids.length > 0 && ms >= 850 && !exploded && !cancelled;
		cCheck(name + ": the TRAP packet started one pentagon (" + UryuFxStats.sprengerTraps() + ")", () -> UryuFxStats.sprengerTraps() == 1 ? null : "traps " + UryuFxStats.sprengerTraps());
		if (standing && ms >= 300) {
			cCheck(name + ": five daggers stand (" + UryuFxStats.daggersDrawn() + ")", () -> UryuFxStats.daggersDrawn() == 5 ? null : "daggers " + UryuFxStats.daggersDrawn());
		}
		if (standing && ms >= 850) {
			cCheck(name + ": the fence of five curtains is up (" + UryuFxStats.sprengerCurtains() + ")", () -> UryuFxStats.sprengerCurtains() >= 5 ? null : "curtains " + UryuFxStats.sprengerCurtains());
		}
		if (roots) {
			cCheck(name + ": the bound zombies have roots (" + UryuFxStats.roots() + ", full " + UryuFxStats.rootsFull() + ", chains " + UryuFxStats.rootChains() + ")",
					() -> UryuFxStats.roots() >= zids.length && UryuFxStats.rootsFull() >= (mode == Mode.BREAK && ms >= 1130 ? 1 : 2) && UryuFxStats.rootChains() >= 5
							? null : "roots " + UryuFxStats.roots() + " full " + UryuFxStats.rootsFull() + " chains " + UryuFxStats.rootChains());
		}
		if (exploded) {
			cCheck(name + ": the explosion was played (" + UryuFxStats.sprengerBursts() + ")", () -> UryuFxStats.sprengerBursts() == 1 ? null : "bursts " + UryuFxStats.sprengerBursts());
			cCheck(name + ": the daggers are gone", () -> UryuFxStats.daggersDrawn() == 0 ? null : "daggers " + UryuFxStats.daggersDrawn());
		}
		if (mode == Mode.CANCEL && ms >= 1050) {
			cCheck(name + ": the cancel was seen and there was no explosion (cancels " + UryuFxStats.sprengerCancels() + ", bursts " + UryuFxStats.sprengerBursts() + ")",
					() -> UryuFxStats.sprengerCancels() >= 1 && UryuFxStats.sprengerBursts() == 0 ? null : "cancels " + UryuFxStats.sprengerCancels() + " bursts " + UryuFxStats.sprengerBursts());
		}
		if (ms >= 100) {
			cCheck(name + ": the figure writes glow sprites (" + FxGlowBatch.lastDrawn + ")", () -> FxGlowBatch.lastDrawn >= 10 ? null : "glow drawn " + FxGlowBatch.lastDrawn);
		}
	}

	// ============================================================================================================ scenario: emulated packets

	private static void emulated() {
		step("sprenger: setup", 20, () -> mc.options.hudHidden = true);
		scene("ground", "ground", false, GraphicsMode.FANCY, 2);
		for (int ms : MS) {
			shot("04_sprenger_t" + ms, ms, Mode.NORMAL);
		}
		scene("back", "back", false, GraphicsMode.FANCY, 2);
		for (int ms : new int[] {550, 1200, 2000}) {
			shot("04_sprenger_back_t" + ms, ms, Mode.NORMAL);
		}
		scene("top", "top", false, GraphicsMode.FANCY, 2);
		for (int ms : TOP_MS) {
			shot("04_sprenger_top_t" + ms, ms, Mode.NORMAL);
		}
		scene("night", "ground", true, GraphicsMode.FANCY, 2);
		for (int ms : new int[] {550, 1200, 1750, 2000, 2300}) {
			shot("04_sprenger_night_t" + ms, ms, Mode.NORMAL);
		}
		scene("fabulous", "ground", true, GraphicsMode.FABULOUS, 2);
		for (int ms : new int[] {1200, 2050}) {
			shot("04_sprenger_fab_t" + ms, ms, Mode.NORMAL);
		}
		scene("early", "ground", false, GraphicsMode.FANCY, 2);
		for (int ms : new int[] {1100, 1230, 1300, 1500}) {
			shot("04_sprenger_early_t" + ms, ms, Mode.EARLY);
		}
		scene("early top", "top", false, GraphicsMode.FANCY, 2);
		shot("04_sprenger_early_top_t1300", 1300, Mode.EARLY);
		scene("cancel", "ground", false, GraphicsMode.FANCY, 2);
		for (int ms : new int[] {950, 1050, 1150, 1400}) {
			shot("04_sprenger_cancel_t" + ms, ms, Mode.CANCEL);
		}
		scene("break", "ground", false, GraphicsMode.FANCY, 2);
		for (int ms : new int[] {1050, 1130, 1250, 1600}) {
			shot("04_sprenger_break_t" + ms, ms, Mode.BREAK);
		}
		cleanup("emulated");
	}

	// ============================================================================================================ scenario: the real ability

	private static void live() {
		scene("live", "ground", false, GraphicsMode.FANCY, 0);
		step("sprenger live: zombies and state", 10, () -> {
			// the real zombies are rooted by the server, so they stand where the pentagon will be
			cmd(String.format(Locale.ROOT, ZOMBIE, 0.0, 9.0), String.format(Locale.ROOT, ZOMBIE, 1.4, 8.2), "item replace entity @s hotbar.0 with reiatsu_test:uryu_cross", "reiatsu cooldowns clear",
					"reiatsu state shikai uryu", "reiatsu full", "tp @s 0.5 -60 0.5 0 11.5");
			Phase6Harness.selectSlot(0);
			UryuFxStats.sprengerReset();
		});
		step("sprenger live: cast (key H)", 3, () -> Phase6Harness.press(ReiatsuKeys.SLOTS[1]));
		step("sprenger live: shot a (about 0.3 s)", 3, () -> Phase7Uryu.shot7("04_sprenger_live_a"));
		step("sprenger live: wait for the bind", 14, () -> { });
		step("sprenger live: shot b (about 1.2 s)", 8, () -> Phase7Uryu.shot7("04_sprenger_live_b"));
		step("sprenger live: wait for the explosion", 20, () -> { });
		step("sprenger live: shot c (about 2.2 s)", 3, () -> Phase7Uryu.shot7("04_sprenger_live_c"));
		step("sprenger live: aftermath", 40, () -> { });
		step("sprenger live: checks", 2, () -> {
			ReiatsuTest.LOGGER.info("[phase7] sprenger live: traps {} binds {} bursts {} cancels {}", UryuFxStats.sprengerTraps(), UryuFxStats.binds(), UryuFxStats.sprengerBursts(),
					UryuFxStats.sprengerCancels());
			cCheck("sprenger live: the client got the TRAP and the BURST (traps " + UryuFxStats.sprengerTraps() + ", bursts " + UryuFxStats.sprengerBursts() + ")",
					() -> UryuFxStats.sprengerTraps() >= 1 && UryuFxStats.sprengerBursts() >= 1 ? null : "traps " + UryuFxStats.sprengerTraps() + " bursts " + UryuFxStats.sprengerBursts());
			cCheck("sprenger live: the client got the BIND of the zombies (" + UryuFxStats.binds() + ")", () -> UryuFxStats.binds() >= 1 ? null : "no BIND packet");
		});
		cleanup("live");
	}

	// ============================================================================================================ F16 (performance)

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

	private static void f16() {
		scene("f16", "back", false, GraphicsMode.FANCY, 3);
		step("f16: load", 4, () -> {
			hookFrames();
			zids = zombieIds();
		});
		step("f16: cycles", 1, () -> {
			// a pentagon every 2.6 s with three bound zombies and the explosion, for the whole measurement
			for (int c = 0; c < 20; c++) {
				FxDelayed.after(c * 2.6, () -> fire(Mode.NORMAL));
			}
		});
		step("f16: warm up", 90, () -> { });
		step("f16: measure", 1, () -> {
			FRAME_MS.clear();
			FxClient.Stats.reset();
			perfFrames0 = FxClock.frame;
			lastFrameNanos = 0;
			recording = true;
		});
		stepUntil("f16: wait", 5, 20 * 40, () -> { }, () -> FxClock.frame - perfFrames0 >= 400);
		step("f16: result", 3, () -> {
			recording = false;
			List<Double> f = new ArrayList<>(FRAME_MS);
			if (f.size() < 30) {
				cCheck("F16: enough frames", () -> "only " + f.size());
				return;
			}
			double mean = f.stream().mapToDouble(Double::doubleValue).average().orElse(0);
			double[] sorted = f.stream().mapToDouble(Double::doubleValue).sorted().toArray();
			double p99 = sorted[(int) Math.min(sorted.length - 1, Math.floor(sorted.length * 0.99))];
			ReiatsuTest.LOGGER.info(String.format(Locale.ROOT,
					"[phase7] PERF F16 (Sprenger, 3 bound zombies, explosion loop): %d frames, mean %.2f ms = %.0f FPS, 1%% low %.2f ms = %.0f FPS; reiatsu_fx mean %.3f ms max %.3f ms; glow drawn %d, particles %d, shapes %d",
					f.size(), mean, 1000 / mean, p99, 1000 / p99, FxClient.Stats.meanMs(), FxClient.Stats.maxMs, FxGlowBatch.lastDrawn, FxParticles.live(), FxShapes.lastDrawn));
			cCheck("F16: reiatsu_fx mean " + String.format(Locale.ROOT, "%.3f", FxClient.Stats.meanMs()) + " ms <= 4 ms", () -> FxClient.Stats.meanMs() <= 4.0 ? null : "mean " + FxClient.Stats.meanMs());
			cCheck("F16: mean " + String.format(Locale.ROOT, "%.0f", 1000 / mean) + " FPS >= 60", () -> 1000 / mean >= 60 ? null : "mean fps " + 1000 / mean);
			cCheck("F16: 1% low " + String.format(Locale.ROOT, "%.0f", 1000 / p99) + " FPS >= 45", () -> 1000 / p99 >= 45 ? null : "1% low " + 1000 / p99);
		});
		cleanup("f16");
	}
}
