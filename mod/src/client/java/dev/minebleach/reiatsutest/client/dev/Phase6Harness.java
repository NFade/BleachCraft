package dev.minebleach.reiatsutest.client.dev;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.fx.FxClient;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxMath;
import dev.minebleach.reiatsutest.client.fx.FxParticles;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.GlowSprite;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.hud.ReiatsuHud;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.client.net.ClientNet;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.server.ZanpakutoManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-only visual verification harness for phase 6 (FX and HUD): {@code gradlew runPhase6 [-Phold=a,b] [-PfreezeAt=ms]
 * [-Pfx=key=value,...]}. Flat creative world like phase 4, real key presses and server commands, screenshots into
 * {@code run/screenshots/p6_*.png}, hard checks logged as {@code CHECK PASS/FAIL}, stops the client itself.
 * Scenarios (comma list in {@code -Phold}, default {@code all}): {@code atlas}, {@code grade}, {@code perf}, {@code hud},
 * {@code release}. Everything is deterministic: FX time is frozen at exact moments ({@link FxClock#freezeIn}), never
 * by waiting.
 */
public final class Phase6Harness {
	private static final String WORLD = "reiatsu_phase6";
	private static final String P = "[phase6] ";
	static final String RUKIA = "reiatsu_test:sode_no_shirayuki";
	static final String BYAKUYA = "reiatsu_test:senbonzakura";

	static MinecraftClient mc;
	private static final List<Step> STEPS = new ArrayList<>();
	private static int stepIndex = -1;
	private static int ticksInStep;
	private static boolean stepStarted;
	private static long totalTicks;
	private static final List<String> FAILS = new CopyOnWriteArrayList<>();
	private static int passes;

	record Step(String name, int minTicks, int timeout, Runnable action, BooleanSupplier until) {
	}

	private Phase6Harness() {
	}

	public static void init() {
		ReiatsuTest.LOGGER.info(P + "ENABLED (-Dreiatsu.phase6=true)");
		ClientTickEvents.END_CLIENT_TICK.register(Phase6Harness::tick);
		buildSteps();
	}

	// ---------------------------------------------------------------- engine

	private static int insertAt = -1;
	/** Console mode: no global watchdog, the script is fed from run/p6_cmd.txt. */
	static boolean consoleMode;

	private static void add(Step s) {
		if (insertAt >= 0) {
			STEPS.add(insertAt++, s);
		} else {
			STEPS.add(s);
		}
	}

	/** Steps added until {@link #endInsert()} run right after the current step (used by the console). */
	static void beginInsert() {
		insertAt = stepIndex + 1;
	}

	static void endInsert() {
		insertAt = -1;
	}

	static void step(String name, int minTicks, Runnable action) {
		add(new Step(name, minTicks, 0, action, null));
	}

	static void stepUntil(String name, int minTicks, int timeout, Runnable action, BooleanSupplier until) {
		add(new Step(name, minTicks, timeout, action, until));
	}

	private static void tick(MinecraftClient client) {
		mc = client;
		totalTicks++;
		if (!consoleMode && totalTicks > 20 * 60 * 60) {
			ReiatsuTest.LOGGER.error(P + "global watchdog fired, stopping");
			finish();
			client.scheduleStop();
			return;
		}
		if (stepIndex < 0) {
			if (client.getOverlay() == null && client.currentScreen != null
					&& (client.currentScreen instanceof TitleScreen || totalTicks > 600)) {
				stepIndex = 0;
			}
			return;
		}
		if (stepIndex >= STEPS.size()) {
			return;
		}
		Step s = STEPS.get(stepIndex);
		if (!stepStarted) {
			ReiatsuTest.LOGGER.info(P + "step {}/{}: {}", stepIndex + 1, STEPS.size(), s.name());
			try {
				s.action().run();
			} catch (Throwable t) {
				ReiatsuTest.LOGGER.error(P + "step '{}' threw", s.name(), t);
				FAILS.add("step threw: " + s.name() + " " + t);
			}
			stepStarted = true;
			ticksInStep = 0;
			return;
		}
		ticksInStep++;
		boolean timeUp = ticksInStep >= s.minTicks();
		boolean cond = s.until() == null || safe(s.until());
		boolean timedOut = s.until() != null && s.timeout() > 0 && ticksInStep >= s.timeout();
		if (timedOut && !cond) {
			ReiatsuTest.LOGGER.error(P + "step '{}' timed out waiting for its condition (fx clock now={} frozen={} target={} dt={} frame={} paused={})", s.name(),
					FxClock.now, FxClock.frozen, FxClock.freezeTargetForDebug(), FxClock.dt, FxClock.frame, mc.isPaused());
			FAILS.add("timeout: " + s.name());
		}
		if ((timeUp && cond) || timedOut) {
			stepIndex++;
			stepStarted = false;
		}
	}

	private static boolean safe(BooleanSupplier b) {
		try {
			return b.getAsBoolean();
		} catch (Throwable t) {
			return false;
		}
	}

	// ---------------------------------------------------------------- helpers

	static MinecraftServer server() {
		return mc.getServer();
	}

	static void cmd(String... commands) {
		MinecraftServer srv = server();
		srv.execute(() -> {
			List<ServerPlayerEntity> players = srv.getPlayerManager().getPlayerList();
			if (players.isEmpty()) {
				return;
			}
			ServerPlayerEntity p = players.get(0);
			ServerCommandSource src = srv.getCommandSource().withEntity(p).withPosition(p.getPos())
					.withRotation(p.getRotationClient()).withWorld(p.getServerWorld());
			for (String c : commands) {
				try {
					srv.getCommandManager().executeWithPrefix(src, c);
				} catch (Throwable t) {
					ReiatsuTest.LOGGER.error(P + "command '{}' failed", c, t);
				}
			}
		});
	}

	static void shot(String name) {
		String file = "p6_" + name + ".png";
		ScreenshotRecorder.saveScreenshot(mc.runDirectory, file, mc.getFramebuffer(), msg -> { });
		ReiatsuTest.LOGGER.info(P + "screenshot {}", file);
	}

	static void selectSlot(int slot) {
		mc.player.getInventory().selectedSlot = slot;
		mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
	}

	static void view(double x, double y, double z, float yaw, float pitch) {
		cmd(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	static void press(KeyBinding k) {
		ReiatsuKeys.press(k);
	}

	static boolean worldReady() {
		return mc.world != null && mc.player != null && mc.currentScreen == null && mc.getServer() != null
				&& !server().getPlayerManager().getPlayerList().isEmpty();
	}

	static void record(String name, String failDetail) {
		if (failDetail == null) {
			passes++;
			ReiatsuTest.LOGGER.info(P + "CHECK PASS {}", name);
		} else {
			FAILS.add(name + ": " + failDetail);
			ReiatsuTest.LOGGER.error(P + "CHECK FAIL {}: {}", name, failDetail);
		}
	}

	static void cCheck(String name, Supplier<String> fn) {
		String fail;
		try {
			fail = fn.get();
		} catch (Throwable t) {
			fail = t.toString();
		}
		record(name, fail);
	}

	static void sCheck(String name, Function<ServerPlayerEntity, String> fn) {
		MinecraftServer srv = server();
		srv.execute(() -> {
			String fail;
			try {
				fail = fn.apply(srv.getPlayerManager().getPlayerList().get(0));
			} catch (Throwable t) {
				fail = t.toString();
			}
			record(name, fail);
		});
	}

	/** Sets the window framebuffer size (dev monitor), then the GUI scale; the pipeline needs a few ticks to settle. */
	static void windowSize(int w, int h, int guiScale) {
		long handle = mc.getWindow().getHandle();
		GLFW.glfwSetWindowSize(handle, w, h);
		mc.options.getGuiScale().setValue(guiScale);
		mc.onResolutionChanged();
	}

	static void graphics(GraphicsMode mode) {
		mc.options.getGraphicsMode().setValue(mode);
		mc.worldRenderer.reload(); // setValue alone does not rebuild the transparency post processor (the cycling callback does)
	}

	static void finish() {
		ReiatsuTest.LOGGER.info(P + "SUMMARY passes={} fails={}", passes, FAILS.size());
		for (String f : FAILS) {
			ReiatsuTest.LOGGER.error(P + "FAILED {}", f);
		}
		ReiatsuTest.LOGGER.info(P + "RESULT {}", FAILS.isEmpty() ? "ALL PASS" : "FAILURES");
	}

	/**
	 * Screenshot at an exact FX time: {@code arm} triggers the effect, then the FX clock freezes {@code afterSeconds} of FX time later; the
	 * shot is taken once the clock is really frozen, then the clock is released.
	 */
	static void shotAt(String name, double afterSeconds, Runnable arm) {
		step(name + " (arm)", 1, () -> {
			FxClock.unfreeze();
			arm.run();
			FxClock.freezeIn(afterSeconds);
		});
		stepUntil(name + " (frozen)", 2, 20 * 20, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> shot(name));
		step(name + " (release)", 2, FxClock::unfreeze);
	}

	static int stepIndexForConsole() {
		return stepIndex;
	}

	/** True only when the scenario is named in the hold list ("all" does not count). */
	static boolean explicit(String scenario) {
		for (String s : System.getProperty("reiatsu.fx.hold", "all").split(",")) {
			if (s.trim().equalsIgnoreCase(scenario)) {
				return true;
			}
		}
		return false;
	}

	static boolean wants(String scenario) {
		String hold = System.getProperty("reiatsu.fx.hold", "all");
		if (hold.isBlank() || hold.equals("all")) {
			return true;
		}
		for (String s : hold.split(",")) {
			if (s.trim().equalsIgnoreCase(scenario)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the script

	private static void buildSteps() {
		step("bootstrap world", 2, Phase6Harness::bootstrapWorld);
		stepUntil("wait for world", 40, 20 * 120, () -> { }, Phase6Harness::worldReady);
		step("setup", 30, () -> {
			cmd("gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
					"time set noon", "weather clear", "difficulty peaceful", "gamemode creative @s",
					"item replace entity @s hotbar.0 with " + RUKIA,
					"item replace entity @s hotbar.1 with " + BYAKUYA,
					"tp @s 0.5 -60 0.5 0 0");
			ClientNet.RESULTS.clear();
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
			mc.inGameHud.getChatHud().clear(true);
		});
		if (wants("atlas")) {
			Phase6Fx.atlasSteps();
		}
		if (wants("grade")) {
			Phase6Fx.gradeSteps();
		}
		if (wants("perf")) {
			Phase6Fx.perfSteps();
		}
		if (wants("hud")) {
			Phase6Hud.steps(true, true);
		} else if (wants("huddetail")) {
			Phase6Hud.steps(false, true);
		} else if (wants("hudperf")) {
			Phase6Hud.setupPerf();
		} else if (wants("hudlayouts")) {
			Phase6Hud.steps(true, false);
		}
		if (wants("release") || wants("relshots") || wants("relnight") || wants("relflash") || wants("aura") || wants("seal") || wants("draw")
				|| wants("relfab") || wants("relperf")) {
			Phase6Release.steps();
		}
		if (wants("swarm")) {
			Phase6Swarm.steps();
		}
		if (wants("rows")) {
			Phase6Rows.steps();
		}
		if (wants("rowsfab")) {
			Phase6Rows.fabulousSteps();
		}
		if (wants("swarmperf")) {
			Phase6Swarm.perfSteps();
		}
		if (wants("rowsperf")) {
			Phase6Rows.perfSteps();
		}
		if (explicit("console")) {
			Phase6Release.consoleStart();
		}
		step("finish", 10, () -> {
			finish();
			mc.scheduleStop();
		});
	}

	private static void bootstrapWorld() {
		var o = mc.options;
		o.pauseOnLostFocus = false;
		o.getViewDistance().setValue(8);
		o.getSimulationDistance().setValue(6);
		o.getGamma().setValue(0.0);
		o.getMaxFps().setValue(120);
		o.getEnableVsync().setValue(false);
		o.getShowAutosaveIndicator().setValue(false);
		o.getBobView().setValue(false);
		o.getChatVisibility().setValue(net.minecraft.network.message.ChatVisibility.HIDDEN);
		o.getGraphicsMode().setValue(GraphicsMode.FANCY);
		o.getGuiScale().setValue(3);
		deleteWorld();
		mc.createIntegratedServerLoader().createAndStart(
				WORLD,
				new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
						DataConfiguration.SAFE_MODE),
				new GeneratorOptions(12345L, false, false),
				registries -> registries.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value()
						.createDimensionsRegistryHolder(),
				mc.currentScreen);
	}

	private static void deleteWorld() {
		File dir = new File(mc.runDirectory, "saves/" + WORLD);
		if (!dir.exists()) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir.toPath())) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		} catch (IOException e) {
			ReiatsuTest.LOGGER.warn(P + "could not delete old world: {}", e.toString());
		}
	}
}
