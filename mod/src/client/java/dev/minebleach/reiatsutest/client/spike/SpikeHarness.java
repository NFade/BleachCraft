package dev.minebleach.reiatsutest.client.spike;

import com.mojang.blaze3d.platform.GlDebugInfo;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.model.ObjItemBakedModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/**
 * Dev-only visual verification harness for spike 2b. Enabled ONLY by {@code -Dreiatsu.spike=true}
 * (see the {@code runSpike} Gradle task). Creates a flat creative world, builds test scenes, cycles through the
 * checklist views, takes screenshots into {@code run/screenshots}, logs frame times, then stops the client.
 */
public final class SpikeHarness {
	private static final String WORLD = "reiatsu_spike";
	private static final String SPIKE = "reiatsu_test:spike_item";
	private static final String SHIKAI = SPIKE + "[reiatsu_test:release_state=\"shikai\"]";
	private static final String P = "[spike-harness] ";

	private static MinecraftClient mc;
	private static final List<Step> STEPS = new ArrayList<>();
	private static int stepIndex = -1;
	private static int ticksInStep;
	private static boolean stepStarted;
	private static long totalTicks;

	// frame-time recording
	private static String perfLabel;
	private static int perfWarmup;
	private static int perfTarget;
	private static long perfLast;
	private static final List<Double> PERF_MS = new ArrayList<>();
	private static boolean perfDone = true;
	private static long perfEmitNanos0;
	private static long perfEmitCalls0;

	private record Step(String name, int minTicks, int timeout, Runnable action, BooleanSupplier until) {
	}

	private SpikeHarness() {
	}

	public static void init() {
		ReiatsuTest.LOGGER.info(P + "ENABLED (-Dreiatsu.spike=true)");
		ClientTickEvents.END_CLIENT_TICK.register(SpikeHarness::tick);
		HudRenderCallback.EVENT.register((ctx, tc) -> onFrame());
		String tune = System.getProperty("reiatsu.spike.tune");
		if (tune != null && !tune.isBlank()) {
			buildTuneSteps(Path.of(tune));
		} else {
			buildSteps();
		}
	}

	// ---------------------------------------------------------------- scheduling

	private static void step(String name, int minTicks, Runnable action) {
		STEPS.add(new Step(name, minTicks, 0, action, null));
	}

	private static void stepUntil(String name, int minTicks, int timeout, Runnable action, BooleanSupplier until) {
		STEPS.add(new Step(name, minTicks, timeout, action, until));
	}

	private static void tick(MinecraftClient client) {
		mc = client;
		totalTicks++;
		if (totalTicks > 20 * 60 * 14) {
			ReiatsuTest.LOGGER.error(P + "global watchdog fired, stopping");
			client.scheduleStop();
			return;
		}
		if (stepIndex < 0) {
			// wait for the title screen (startup overlay finished)
			if (client.getOverlay() == null && client.currentScreen != null) {
				if (client.currentScreen instanceof TitleScreen || totalTicks > 600) {
					stepIndex = 0;
				}
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
			ReiatsuTest.LOGGER.error(P + "step '{}' timed out waiting for its condition", s.name());
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

	private static MinecraftServer server() {
		return mc.getServer();
	}

	/** Runs commands on the integrated server thread as the (op) player. Feedback goes to the log. */
	private static void cmd(String... commands) {
		MinecraftServer srv = server();
		if (srv == null) {
			ReiatsuTest.LOGGER.error(P + "no integrated server for commands {}", Arrays.toString(commands));
			return;
		}
		srv.execute(() -> {
			List<ServerPlayerEntity> players = srv.getPlayerManager().getPlayerList();
			if (players.isEmpty()) {
				ReiatsuTest.LOGGER.error(P + "no player on server");
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

	private static void shot(String name) {
		String file = "spike_" + name + ".png";
		ScreenshotRecorder.saveScreenshot(mc.runDirectory, file, mc.getFramebuffer(), msg -> { });
		ReiatsuTest.LOGGER.info(P + "screenshot {}", file);
	}

	private static void selectSlot(int slot) {
		mc.player.getInventory().selectedSlot = slot;
		mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
	}

	private static void view(double x, double y, double z, float yaw, float pitch) {
		cmd(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private static void setGraphics(GraphicsMode mode) {
		mc.options.getGraphicsMode().setValue(mode);
		// vanilla's cycling callback does this after setValue (GameOptions#graphicsMode); setValue alone leaves
		// the transparency post processor unloaded and crashes Fabulous rendering.
		if (mc.world != null) {
			mc.worldRenderer.reload();
		}
	}

	private static boolean worldReady() {
		return mc.world != null && mc.player != null && mc.currentScreen == null && mc.getServer() != null
				&& !server().getPlayerManager().getPlayerList().isEmpty();
	}

	// ---------------------------------------------------------------- frame timing

	private static void onFrame() {
		if (perfDone) {
			return;
		}
		long now = System.nanoTime();
		if (perfWarmup > 0) {
			perfWarmup--;
			perfLast = now;
			if (perfWarmup == 0) {
				perfEmitNanos0 = ObjItemBakedModel.EMIT_NANOS.get();
				perfEmitCalls0 = ObjItemBakedModel.EMIT_CALLS.get();
			}
			return;
		}
		PERF_MS.add((now - perfLast) / 1.0e6);
		perfLast = now;
		if (PERF_MS.size() >= perfTarget) {
			perfDone = true;
			report();
		}
	}

	private static void startPerf(String label) {
		perfLabel = label;
		PERF_MS.clear();
		perfWarmup = 90;
		perfTarget = 400;
		perfDone = false;
	}

	private static void report() {
		double sum = 0;
		for (double d : PERF_MS) {
			sum += d;
		}
		List<Double> sorted = new ArrayList<>(PERF_MS);
		sorted.sort(Comparator.naturalOrder());
		double mean = sum / sorted.size();
		double p50 = sorted.get(sorted.size() / 2);
		double p95 = sorted.get((int) (sorted.size() * 0.95));
		long calls = ObjItemBakedModel.EMIT_CALLS.get() - perfEmitCalls0;
		long nanos = ObjItemBakedModel.EMIT_NANOS.get() - perfEmitNanos0;
		ReiatsuTest.LOGGER.info(P + String.format(java.util.Locale.ROOT,
				"PERF %-34s frames=%d mean=%.3f ms (%.1f fps)  median=%.3f ms  p95=%.3f ms  emitItemQuads: %d calls, %.2f us/call, %.3f ms/frame",
				perfLabel, sorted.size(), mean, 1000.0 / mean, p50, p95, calls,
				calls == 0 ? 0.0 : nanos / 1000.0 / calls, nanos / 1.0e6 / sorted.size()));
	}

	// ---------------------------------------------------------------- the script

	private static void buildSteps() {
		// ---- A. boot: options, fresh flat creative world
		step("bootstrap world", 2, SpikeHarness::bootstrapWorld);
		stepUntil("wait for world", 40, 20 * 120, () -> { }, SpikeHarness::worldReady);

		step("setup commands", 20, () -> {
			cmd("gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
					"time set noon", "weather clear", "gamemode creative @s",
					"item replace entity @s hotbar.0 with " + SPIKE,
					"item replace entity @s hotbar.1 with " + SHIKAI,
					"item replace entity @s hotbar.2 with minecraft:diamond_sword",
					// checklist 8: exact /give component syntax (feedback is logged by the server thread)
					"give @s " + SHIKAI,
					"tp @s 0.5 -60 0.5 0 20");
		});
		step("hide chat/hud noise", 5, () -> {
			mc.options.hudHidden = false;
			mc.inGameHud.getChatHud().clear(true);
		});

		// ---- B. daytime views (checklist 5, 6, 8, 9)
		step("fp right sealed", 30, () -> {
			selectSlot(0);
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			view(0.5, -60, 0.5, 0, 20);
		});
		step("shot fp right sealed", 5, () -> shot("01_fp_right_sealed"));
		step("shot fp right sealed t2", 7, () -> shot("01b_fp_right_sealed_t2"));
		step("vanilla compare in hand", 25, () -> selectSlot(2));
		step("shot fp right diamond sword (reference)", 5, () -> shot("01c_fp_right_vanilla_sword_ref"));
		step("fp left sealed", 30, () -> {
			selectSlot(0);
			mc.options.getMainArm().setValue(Arm.LEFT);
		});
		step("shot fp left sealed", 5, () -> shot("02_fp_left_sealed"));
		step("back to right arm, tp back", 30, () -> {
			mc.options.getMainArm().setValue(Arm.RIGHT);
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		});
		step("shot tp back right", 5, () -> shot("03_tp_back_right_sealed"));
		step("tp front", 30, () -> mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
		step("shot tp front right", 5, () -> shot("04_tp_front_right_sealed"));
		step("shot tp front right t2 (ribbon)", 9, () -> shot("04b_tp_front_ribbon_t2"));
		step("shot tp front right t3 (ribbon)", 9, () -> shot("04c_tp_front_ribbon_t3"));
		step("left arm third person front", 30, () -> mc.options.getMainArm().setValue(Arm.LEFT));
		step("shot tp front left", 5, () -> shot("04d_tp_front_left_sealed"));
		step("spawn armor stands (profile views of the third-person hand transform)", 40, () -> {
			mc.options.getMainArm().setValue(Arm.RIGHT);
			cmd(stand(20.5, 90f), stand(30.5, 270f), stand(40.5, 180f));
		});
		step("view stand right profile", 40, () -> {
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			selectSlot(5);
			view(20.5, -60, 0.9, 0, 0);
		});
		step("shot stand right profile", 5, () -> shot("03b_tp_stand_right_profile"));
		step("view stand left profile", 40, () -> view(30.5, -60, 0.9, 0, 0));
		step("shot stand left profile", 5, () -> shot("03c_tp_stand_left_profile"));
		step("view stand front", 40, () -> view(40.5, -60, 0.9, 0, 0));
		step("shot stand front", 5, () -> shot("03d_tp_stand_front"));
		step("kill stands, back to origin", 30, () -> {
			cmd("kill @e[type=minecraft:armor_stand]");
			view(0.5, -60, 0.5, 0, 20);
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
		});
		step("shikai in hand, tp front", 30, () -> {
			mc.options.getMainArm().setValue(Arm.RIGHT);
			selectSlot(1);
		});
		step("shot tp front shikai", 5, () -> shot("08a_tp_front_right_shikai"));
		step("fp shikai", 30, () -> mc.options.setPerspective(Perspective.FIRST_PERSON));
		step("shot fp shikai", 5, () -> shot("08b_fp_right_shikai"));

		// dropped item + item frames
		step("spawn dropped items + frames", 40, () -> {
			selectSlot(0);
			cmd("fill -3 -60 3 3 -57 3 minecraft:stone",
					"summon minecraft:item 0.5 -59.5 2.2 {Item:{id:\"" + SPIKE + "\",count:1},NoGravity:1b,PickupDelay:32767s,Age:-32768s,Motion:[0.0,0.0,0.0]}",
					"summon minecraft:item -0.8 -59.5 2.2 {Item:{id:\"" + SPIKE + "\",count:1,components:{\"reiatsu_test:release_state\":\"shikai\"}},NoGravity:1b,PickupDelay:32767s,Age:-32768s,Motion:[0.0,0.0,0.0]}",
					"summon minecraft:item_frame -1 -58 2 {Facing:2b,Item:{id:\"" + SPIKE + "\",count:1}}",
					"summon minecraft:item_frame 0 -58 2 {Facing:2b,Item:{id:\"" + SPIKE + "\",count:1,components:{\"reiatsu_test:release_state\":\"shikai\"}}}",
					"summon minecraft:item_frame 1 -58 2 {Facing:2b,Item:{id:\"minecraft:diamond_sword\",count:1}}",
					"tp @s 0.5 -60 0.5 0 35");
		});
		step("shot dropped item", 30, () -> shot("05_dropped_items"));
		step("look at item frames", 30, () -> view(0.5, -60, 0.5, 0, 5));
		step("shot item frames", 5, () -> shot("06_item_frames"));
		step("shot item frames t2 (ribbon in frame)", 9, () -> shot("06b_item_frames_t2"));

		// GUI (checklist 5: GUI shows the flat icon)
		step("gui scale 4 + hotbar", 25, () -> {
			mc.options.getGuiScale().setValue(4);
			mc.onResolutionChanged();
			view(0.5, -60, 0.5, 0, 5);
		});
		step("shot hotbar", 15, () -> shot("07a_gui_hotbar"));
		step("open inventory", 5, () -> mc.setScreen(new InventoryScreen(mc.player)));
		step("shot inventory", 10, () -> shot("07b_gui_inventory"));
		step("close inventory", 5, () -> {
			mc.setScreen(null);
			mc.options.getGuiScale().setValue(0);
			mc.onResolutionChanged();
		});

		// ---- C. F3+T reload (checklist 10)
		reloadSteps();

		// ---- D. performance (checklist 11)
		perfSteps();

		// ---- E. dark room emissive (checklist 7)
		darkRoomSteps();

		// ---- finish
		step("finish", 5, () -> {
			ReiatsuTest.LOGGER.info(P + "ALL STEPS DONE, stopping client");
			mc.scheduleStop();
		});
	}

	private static CompletableFuture<Void> reloadFuture;

	/** Resource reload finished and the loading overlay has faded out (otherwise screenshots show the splash). */
	private static boolean reloadFinished() {
		return reloadFuture != null && reloadFuture.isDone() && mc.getOverlay() == null;
	}

	private static void reloadSteps() {
		Path[] display = new Path[1];
		String[] original = new String[1];
		step("F3+T equivalent: reload resources", 5, () -> {
			selectSlot(0);
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			view(0.5, -60, 0.5, 0, 20);
			reloadFuture = mc.reloadResources();
		});
		stepUntil("wait reload 1", 20, 20 * 60, () -> { }, SpikeHarness::reloadFinished);
		step("shot after reload", 15, () -> shot("10a_after_reload"));
		step("edit _display.json (fp right: scale 0.9 -> 1.8, rot x -45 -> -15) and reload", 5, () -> {
			try {
				display[0] = FabricLoader.getInstance().getModContainer(ReiatsuTest.MOD_ID).orElseThrow()
						.findPath("assets/reiatsu_test/models/item/spike_item_display.json").orElseThrow();
				original[0] = Files.readString(display[0]);
				com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(original[0]).getAsJsonObject();
				com.google.gson.JsonObject fp = root.getAsJsonObject("display").getAsJsonObject("firstperson_righthand");
				fp.add("scale", com.google.gson.JsonParser.parseString("[1.8, 1.8, 1.8]"));
				fp.add("rotation", com.google.gson.JsonParser.parseString("[-15, 180, 0]"));
				String edited = root.toString();
				Files.writeString(display[0], edited);
				ReiatsuTest.LOGGER.info(P + "edited {}", display[0]);
			} catch (Exception e) {
				ReiatsuTest.LOGGER.error(P + "cannot edit display json", e);
			}
			reloadFuture = mc.reloadResources();
		});
		stepUntil("wait reload 2", 20, 20 * 60, () -> { }, SpikeHarness::reloadFinished);
		step("shot after display edit", 15, () -> shot("10b_after_display_edit"));
		step("restore _display.json and reload", 5, () -> {
			try {
				Files.writeString(display[0], original[0]);
			} catch (IOException e) {
				ReiatsuTest.LOGGER.error(P + "cannot restore display json", e);
			}
			reloadFuture = mc.reloadResources();
		});
		stepUntil("wait reload 3", 20, 20 * 60, () -> { }, SpikeHarness::reloadFinished);
		step("shot after restore", 15, () -> shot("10c_after_restore"));
	}

	private static void perfSteps() {
		step("perf setup: clear frames, wall of 64 frames prepared", 30, () -> {
			cmd("kill @e[type=minecraft:item_frame]", "kill @e[type=minecraft:item]",
					"fill -3 -60 3 3 -57 3 minecraft:air", "fill -5 -60 12 4 -50 12 minecraft:stone");
			selectSlot(5); // empty hand
			view(0.5, -60, 0.5, 0, -18);
		});
		step("P0 start (empty hand, no frames)", 30, () -> startPerf("P0 empty hand, no frames"));
		stepUntil("P0 wait", 5, 20 * 60, () -> { }, () -> perfDone);
		step("P1 hold item", 30, () -> selectSlot(0));
		step("P1 start", 5, () -> startPerf("P1 item in hand, no frames"));
		stepUntil("P1 wait", 5, 20 * 60, () -> { }, () -> perfDone);
		step("P1s hold item (shikai, no ribbon)", 30, () -> selectSlot(1));
		step("P1s start", 5, () -> startPerf("P1s shikai in hand, no frames"));
		stepUntil("P1s wait", 5, 20 * 60, () -> { }, () -> perfDone);
		step("spawn 64 frames", 10, () -> {
			String[] cmds = new String[64];
			int i = 0;
			for (int x = -4; x <= 3; x++) {
				for (int y = -59; y <= -52; y++) {
					cmds[i++] = String.format(java.util.Locale.ROOT,
							"summon minecraft:item_frame %d %d 11 {Facing:2b,Item:{id:\"%s\",count:1}}", x, y, SPIKE);
				}
			}
			cmd(cmds);
			selectSlot(5);
		});
		step("P3 settle", 40, () -> shot("11a_64_frames_view"));
		step("P3 start (empty hand, 64 frames)", 5, () -> startPerf("P3 empty hand, 64 item frames"));
		stepUntil("P3 wait", 5, 20 * 60, () -> { }, () -> perfDone);
		step("P2 hold item", 30, () -> selectSlot(0));
		step("P2 start", 5, () -> startPerf("P2 item in hand, 64 item frames"));
		stepUntil("P2 wait", 5, 20 * 60, () -> { }, () -> perfDone);
		step("replace frames with vanilla diamond sword (control)", 10, () -> {
			String[] cmds = new String[65];
			cmds[0] = "kill @e[type=minecraft:item_frame]";
			int i = 1;
			for (int x = -4; x <= 3; x++) {
				for (int y = -59; y <= -52; y++) {
					cmds[i++] = String.format(java.util.Locale.ROOT,
							"summon minecraft:item_frame %d %d 11 {Facing:2b,Item:{id:\"minecraft:diamond_sword\",count:1}}", x, y);
				}
			}
			cmd(cmds);
			selectSlot(5);
		});
		step("P4 settle", 40, () -> { });
		step("P4 start (empty hand, 64 vanilla sword frames)", 5, () -> startPerf("P4 empty hand, 64 vanilla sword frames"));
		stepUntil("P4 wait", 5, 20 * 60, () -> { }, () -> perfDone);
		step("cleanup frames", 10, () -> cmd("kill @e[type=minecraft:item_frame]"));
	}

	private static void darkRoomSteps() {
		step("go to dark room site", 40, () -> {
			view(100.5, -60, 0.5, 0, 0);
			selectSlot(0);
			mc.options.setPerspective(Perspective.FIRST_PERSON);
		});
		step("build sealed stone room (light 0), midnight", 40, () -> {
			cmd("fill 96 -61 -4 105 -53 5 minecraft:stone hollow", "time set midnight",
					"tp @s 100.5 -60 0.5 0 15");
		});
		step("room settle", 50, () -> { });
		step("shot dark fancy fp (sealed)", 5, () -> shot("09a_dark_fancy_fp_sealed"));
		step("dark fancy vanilla reference", 30, () -> selectSlot(2));
		step("shot dark fancy vanilla sword", 5, () -> shot("09b_dark_fancy_fp_vanilla_sword_ref"));
		step("dark fancy tp front", 30, () -> {
			selectSlot(0);
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
		});
		step("shot dark fancy tp front", 5, () -> shot("09c_dark_fancy_tp_front"));
		step("fabulous fp", 5, () -> {
			setGraphics(GraphicsMode.FABULOUS);
			mc.options.setPerspective(Perspective.FIRST_PERSON);
		});
		step("fabulous settle", 80, () -> { });
		step("shot dark fabulous fp (sealed)", 5, () -> shot("09d_dark_fabulous_fp_sealed"));
		step("fabulous vanilla ref", 30, () -> selectSlot(2));
		step("shot dark fabulous vanilla sword", 5, () -> shot("09e_dark_fabulous_fp_vanilla_sword_ref"));
		step("fabulous tp front", 30, () -> {
			selectSlot(0);
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
		});
		step("shot dark fabulous tp front", 5, () -> shot("09f_dark_fabulous_tp_front"));
		step("fabulous perf in dark room", 30, () -> {
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			selectSlot(0);
		});
		step("PF start", 5, () -> startPerf("PF fabulous, item in hand"));
		stepUntil("PF wait", 5, 20 * 60, () -> { }, () -> perfDone);
		step("back to fancy", 5, () -> setGraphics(GraphicsMode.FANCY));
	}

	// ---------------------------------------------------------------- tuning mode

	/**
	 * Tune mode ({@code -Preiatsu.tune=<json>} on the Gradle command line): for every candidate in the file, writes its
	 * {@code display} object into spike_item_display.json (build output), reloads resources, moves the camera to the
	 * candidate's view and takes a screenshot {@code spike_tune_<name>.png}. Candidate:
	 * {@code {"name":"..", "view":"fp|fp_left|side_r|side_l|front|ground|frame|gui", "display":{...}}}.
	 */
	private static void buildTuneSteps(Path file) {
		com.google.gson.JsonArray cands;
		try {
			cands = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject()
					.getAsJsonArray("candidates");
		} catch (Exception e) {
			ReiatsuTest.LOGGER.error(P + "cannot read tune file {}", file, e);
			cands = new com.google.gson.JsonArray();
		}
		Path[] display = new Path[1];
		String[] original = new String[1];
		step("tune: bootstrap world", 2, SpikeHarness::bootstrapWorld);
		stepUntil("tune: wait for world", 40, 20 * 120, () -> { }, SpikeHarness::worldReady);
		step("tune: setup", 20, () -> {
			cmd("gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
					"time set noon", "weather clear", "gamemode creative @s",
					"item replace entity @s hotbar.0 with " + SPIKE,
					"item replace entity @s hotbar.1 with " + SHIKAI,
					// right-profile stand (x=20), left-profile stand (x=30), front-facing stand (x=40)
					stand(20.5, 90f), stand(30.5, 270f), stand(40.5, 180f),
					"summon minecraft:item 50.5 -59.2 2.6 {Item:{id:\"" + SPIKE + "\",count:1},NoGravity:1b,PickupDelay:32767s,Age:-32768s,Motion:[0.0,0.0,0.0]}",
					"fill 58 -60 3 64 -57 3 minecraft:stone",
					"summon minecraft:item_frame 60 -59 2 {Facing:2b,Item:{id:\"" + SPIKE + "\",count:1}}",
					"tp @s 0.5 -60 0.5 0 0");
			try {
				display[0] = FabricLoader.getInstance().getModContainer(ReiatsuTest.MOD_ID).orElseThrow()
						.findPath("assets/reiatsu_test/models/item/spike_item_display.json").orElseThrow();
				original[0] = Files.readString(display[0]);
			} catch (Exception e) {
				ReiatsuTest.LOGGER.error(P + "cannot locate display json", e);
			}
		});
		for (com.google.gson.JsonElement el : cands) {
			com.google.gson.JsonObject c = el.getAsJsonObject();
			String name = c.get("name").getAsString();
			String view = c.get("view").getAsString();
			step("tune " + name + ": write display + reload", 2, () -> {
				try {
					com.google.gson.JsonObject root = new com.google.gson.JsonObject();
					root.addProperty("gui_light", "front");
					com.google.gson.JsonObject tex = new com.google.gson.JsonObject();
					tex.addProperty("particle", "reiatsu_test:item/spike_diffuse");
					root.add("textures", tex);
					root.add("display", c.getAsJsonObject("display"));
					Files.writeString(display[0], root.toString());
				} catch (Exception e) {
					ReiatsuTest.LOGGER.error(P + "cannot write display json", e);
				}
				reloadFuture = mc.reloadResources();
			});
			stepUntil("tune " + name + ": wait reload", 10, 20 * 60, () -> { }, SpikeHarness::reloadFinished);
			step("tune " + name + ": view " + view, 25, () -> {
				selectSlot(c.has("slot") ? c.get("slot").getAsInt() : 0);
				mc.options.getMainArm().setValue(view.equals("fp_left") ? Arm.LEFT : Arm.RIGHT);
				mc.options.setPerspective(Perspective.FIRST_PERSON);
				switch (view) {
					case "fp", "fp_left" -> view(0.5, -60, 0.5, 0, 20);
					case "side_r" -> view(20.5, -60, 0.9, 0, 0);
					case "side_l" -> view(30.5, -60, 0.9, 0, 0);
					case "front" -> view(40.5, -60, 0.9, 0, 0);
					case "ground" -> view(50.5, -60, 0.2, 0, 18);
					case "frame" -> view(60.5, -60, 0.5, 0, 0);
					default -> view(0.5, -60, 0.5, 0, 20);
				}
			});
			step("tune " + name + ": shot", 2, () -> shot("tune_" + name));
		}
		step("tune: restore display json", 2, () -> {
			try {
				if (display[0] != null && original[0] != null) {
					Files.writeString(display[0], original[0]);
				}
			} catch (IOException e) {
				ReiatsuTest.LOGGER.error(P + "cannot restore display json", e);
			}
		});
		step("tune: finish", 5, () -> mc.scheduleStop());
	}

	private static String stand(double x, float yaw) {
		return String.format(java.util.Locale.ROOT,
				"summon minecraft:armor_stand %.1f -60 3.5 {ShowArms:1b,NoGravity:1b,Rotation:[%.1ff,0.0f],"
						+ "Pose:{RightArm:[-20.0f,0.0f,0.0f],LeftArm:[-20.0f,0.0f,0.0f]},"
						+ "HandItems:[{id:\"" + SPIKE + "\",count:1},{id:\"" + SPIKE + "\",count:1}]}",
				x, yaw);
	}

	private static void bootstrapWorld() {
		var o = mc.options;
		o.pauseOnLostFocus = false;
		o.getViewDistance().setValue(6);
		o.getSimulationDistance().setValue(5);
		o.getGamma().setValue(0.0);
		o.getMaxFps().setValue(260);
		o.getEnableVsync().setValue(false);
		o.getShowAutosaveIndicator().setValue(false);
		o.getBobView().setValue(false);
		o.getChatVisibility().setValue(net.minecraft.network.message.ChatVisibility.HIDDEN);
		setGraphics(GraphicsMode.FANCY);
		logEnvironment();
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

	// ---------------------------------------------------------------- misc

	private static void logEnvironment() {
		ModuleLayer boot = ModuleLayer.boot();
		ReiatsuTest.LOGGER.info(P + "ENV java={} vendor={} jdk.httpserver present={} java.desktop present={}",
				System.getProperty("java.version"), System.getProperty("java.vendor"),
				boot.findModule("jdk.httpserver").isPresent(), boot.findModule("java.desktop").isPresent());
		try {
			ReiatsuTest.LOGGER.info(P + "ENV gpu={} gl={} cpu={}", GlDebugInfo.getRenderer(), GlDebugInfo.getVersion(),
					GlDebugInfo.getCpuInfo());
		} catch (Throwable t) {
			ReiatsuTest.LOGGER.warn(P + "cannot read GL info: {}", t.toString());
		}
		ReiatsuTest.LOGGER.info(P + "ENV window={}x{} env={}", mc.getWindow().getFramebufferWidth(),
				mc.getWindow().getFramebufferHeight(), FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT);
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
