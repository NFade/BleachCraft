package dev.minebleach.reiatsutest.client.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.net.ClientNet;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.RequestSource;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.server.VoiceControl;
import dev.minebleach.reiatsutest.server.ZanpakutoManager;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.GraphicsMode;
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

/**
 * Dev-only verification harness for phase 5 (voice). Enabled ONLY by {@code -Dreiatsu.phase5=true} (Gradle task
 * {@code runPhase5}). Creates a flat world, then a script thread talks to the REAL HTTP bridge of the running
 * integrated server exactly like the browser page does (POST /voice, GET /status, GET /), checks state transitions on the
 * server and on the client, and measures the latency from "POST received by the bridge" to "transition applied"
 * (and, on the client side, to the attachment change and to the effect_event) over 20 runs.
 */
public final class Phase5Harness {
	private static final String WORLD = "reiatsu_phase5";
	private static final String P = "[phase5] ";
	private static final String RUKIA = "reiatsu_test:sode_no_shirayuki";
	private static final String BYAKUYA = "reiatsu_test:senbonzakura";
	private static final int RUNS = 20;

	private static MinecraftClient mc;
	private static long totalTicks;
	private static int stage; // 0 waiting for the title, 1 world created, 2 script running, 3 finished
	private static final AtomicBoolean scriptDone = new AtomicBoolean();
	private static final List<String> FAILS = new CopyOnWriteArrayList<>();
	private static int passes;
	private static long utt = 1000;

	private Phase5Harness() {
	}

	public static void init() {
		ReiatsuTest.LOGGER.info(P + "ENABLED (-Dreiatsu.phase5=true)");
		ClientTickEvents.END_CLIENT_TICK.register(Phase5Harness::tick);
	}

	// ---------------------------------------------------------------- tick driver (world creation, then hands over)

	private static void tick(MinecraftClient client) {
		mc = client;
		totalTicks++;
		if (totalTicks > 20 * 60 * 6) {
			ReiatsuTest.LOGGER.error(P + "global watchdog fired, stopping");
			finish();
			client.scheduleStop();
			return;
		}
		switch (stage) {
			case 0 -> {
				if (client.getOverlay() == null && client.currentScreen != null
						&& (client.currentScreen instanceof TitleScreen || totalTicks > 600)) {
					bootstrapWorld();
					stage = 1;
				}
			}
			case 1 -> {
				if (client.world != null && client.player != null && client.currentScreen == null && client.getServer() != null
						&& !client.getServer().getPlayerManager().getPlayerList().isEmpty() && totalTicks % 20 == 0) {
					stage = 2;
					Thread t = new Thread(Phase5Harness::script, "phase5-script");
					t.setDaemon(true);
					t.start();
				}
			}
			case 2 -> {
				if (scriptDone.get()) {
					stage = 3;
					finish();
					client.scheduleStop();
				}
			}
			default -> { }
		}
	}

	// ---------------------------------------------------------------- script thread helpers

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

	private static String base() {
		return "http://127.0.0.1:" + VoiceControl.get().port();
	}

	private static JsonObject post(String text, String lang, boolean fin) throws Exception {
		long u = ++utt;
		String body = new com.google.gson.Gson().toJson(java.util.Map.of("text", text, "final", fin, "lang", lang, "utt", u, "t", System.currentTimeMillis()));
		HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(base() + "/voice")).timeout(Duration.ofSeconds(4))
				.header("Content-Type", "text/plain;charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
		o.addProperty("_http", r.statusCode());
		ReiatsuTest.LOGGER.info(P + "POST '{}' -> {}", text, o);
		return o;
	}

	private static JsonObject get(String path) throws Exception {
		HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(4)).GET().build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		JsonObject o = new JsonObject();
		try {
			o = JsonParser.parseString(r.body()).getAsJsonObject();
		} catch (RuntimeException e) {
			o.addProperty("raw", r.body());
		}
		o.addProperty("_http", r.statusCode());
		return o;
	}

	private static <T> T onServer(Function<ServerPlayerEntity, T> fn) throws Exception {
		MinecraftServer srv = mc.getServer();
		CompletableFuture<T> f = new CompletableFuture<>();
		srv.execute(() -> {
			try {
				f.complete(fn.apply(srv.getPlayerManager().getPlayerList().get(0)));
			} catch (Throwable t) {
				f.completeExceptionally(t);
			}
		});
		return f.get(5, TimeUnit.SECONDS);
	}

	private static void serverCommands(String... commands) throws Exception {
		onServer(p -> {
			MinecraftServer srv = p.getServer();
			ServerCommandSource src = srv.getCommandSource().withEntity(p).withPosition(p.getPos()).withRotation(p.getRotationClient())
					.withWorld(p.getServerWorld());
			for (String c : commands) {
				srv.getCommandManager().executeWithPrefix(src, c);
			}
			return null;
		});
	}

	private static StateMachine sm(ServerPlayerEntity p) {
		return ZanpakutoManager.machine(p);
	}

	private static ZanpakutoState serverState() throws Exception {
		return onServer(p -> sm(p).state());
	}

	/** The draw (SEALED to BASE) through the same server path as the J key and the right click, then waits out the transition lock. */
	private static void drawSword() throws Exception {
		onServer(p -> ZanpakutoManager.performTransition(p, ZanpakutoState.BASE, RequestSource.KEY, StateMachine.SERVER_SEQ));
		Thread.sleep(700);
	}

	private static ZanpakutoState clientState() {
		return ClientState.zanpakuto().zanpakutoState();
	}

	private static boolean waitUntil(BooleanSupplier cond, long timeoutMs) throws InterruptedException {
		long end = System.nanoTime() + timeoutMs * 1_000_000L;
		while (System.nanoTime() < end) {
			try {
				if (cond.getAsBoolean()) {
					return true;
				}
			} catch (RuntimeException ignored) {
				// client state not ready yet
			}
			Thread.sleep(1);
		}
		return false;
	}

	private static void check(String name, boolean ok, String detail) {
		if (ok) {
			passes++;
			ReiatsuTest.LOGGER.info(P + "CHECK PASS {}", name);
		} else {
			FAILS.add(name + ": " + detail);
			ReiatsuTest.LOGGER.error(P + "CHECK FAIL {}: {}", name, detail);
		}
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
	}

	private static boolean matched(JsonObject o) {
		return o.has("matched") && o.get("matched").getAsBoolean();
	}

	private static void selectSlot(int slot) {
		mc.execute(() -> {
			mc.player.getInventory().selectedSlot = slot;
			mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
		});
	}

	// ---------------------------------------------------------------- the script

	private static void script() {
		try {
			run();
		} catch (Throwable t) {
			ReiatsuTest.LOGGER.error(P + "script crashed", t);
			FAILS.add("script crashed: " + t);
		} finally {
			scriptDone.set(true);
		}
	}

	private static void run() throws Exception {
		serverCommands("gamerule doDaylightCycle false", "gamerule doMobSpawning false", "time set noon", "weather clear",
				"gamemode creative @s", "item replace entity @s hotbar.0 with " + RUKIA, "item replace entity @s hotbar.1 with " + BYAKUYA,
				"tp @s 0.5 -60 0.5 0 20");
		selectSlot(0);
		Thread.sleep(1500);
		ReiatsuTest.LOGGER.info(P + "bridge at {} transport={}", VoiceControl.get().url(), VoiceControl.get().transport());

		// ---- V1 bridge, status and page
		check("V1 bridge is up", VoiceControl.get().port() > 0, "no port");
		JsonObject st = get("/status");
		check("V1 GET /status", st.get("_http").getAsInt() == 200 && "SEALED".equals(str(st, "state")) && "rukia".equals(str(st, "held")),
				st.toString());
		check("V1 status lists no phrase while the sword is sheathed", st.has("say") && st.getAsJsonArray("say").isEmpty(), st.toString());
		JsonObject page = get("/");
		check("V1 GET / serves the bridge page", page.get("_http").getAsInt() == 200 && str(page, "raw") != null && str(page, "raw").contains("Reiatsu Voice Bridge"),
				page.toString().substring(0, Math.min(200, page.toString().length())));

		// ---- V1b B4 step 3: with the sword still in the scabbard the release phrase is gated out (draw first)
		JsonObject r = post("sode no shirayuki", "en-US", true);
		check("V1b release phrase while SEALED: gated out, nothing happens", !matched(r) && "gated_out".equals(str(r, "reason")), r.toString());
		check("V1b state still SEALED", serverState() == ZanpakutoState.SEALED, "server " + serverState());
		drawSword();
		check("V1b draw: server BASE", serverState() == ZanpakutoState.BASE, "server " + serverState());
		check("V1b draw: client BASE", waitUntil(() -> clientState() == ZanpakutoState.BASE, 1000), "client " + clientState());
		st = get("/status");
		check("V1b status says BASE", "BASE".equals(str(st, "state")), st.toString());
		check("V1b status lists the release phrase for the drawn base form", st.has("say") && st.getAsJsonArray("say").toString().contains("rukia.shikai.release"),
				st.toString());

		// ---- V2 a misheard release phrase (ASR error) in BASE with Rukia's sword
		r = post("my sode no shirayuki", "en-US", true);
		check("V2 release by voice: matched + accepted", matched(r) && "rukia.shikai.release".equals(str(r, "command")) && "OK".equals(str(r, "result")), r.toString());
		check("V2 server state SHIKAI", serverState() == ZanpakutoState.SHIKAI, "server " + serverState());
		check("V2 client state SHIKAI (attachment sync)", waitUntil(() -> clientState() == ZanpakutoState.SHIKAI, 1000), "client " + clientState());

		// ---- V3 ordinary speech does nothing
		r = post("I found a diamond", "en-US", true);
		check("V3 neutral speech: no match", !matched(r) && "no_match".equals(str(r, "reason")), r.toString());
		r = post("the seal is broken", "en-US", true);
		check("V3 weak word in a sentence: ignored", !matched(r), r.toString());
		check("V3 state unchanged", serverState() == ZanpakutoState.SHIKAI, "server " + serverState());

		// ---- V4 voice cannot bypass the reiatsu rule: bankai needs a full bar, the bar is at 85
		Thread.sleep(700); // past the 10 tick transition lock of the release
		r = post("Bankai, Hakka no Togame", "en-US", true);
		check("V4 bankai without a full bar is denied by the server", matched(r) && "DENIED_REIATSU".equals(str(r, "result")), r.toString());
		check("V4 state still SHIKAI", serverState() == ZanpakutoState.SHIKAI, "server " + serverState());

		// ---- V5 ability by voice, cooldown not bypassed, debounce
		Thread.sleep(1600);
		int before = onServer(p -> sm(p).reiatsu().value());
		int effects = ClientNet.EFFECT_EVENTS.get();
		r = post("tsukishiro", "ja-JP", true);
		check("V5 ability by voice accepted", matched(r) && "OK".equals(str(r, "result")) && "rukia.shikai.tsukishiro".equals(str(r, "command")), r.toString());
		int after = onServer(p -> sm(p).reiatsu().value());
		check("V5 reiatsu charged by the normal rules (-25.0 plus regen)", before - after >= 200 && before - after <= 260, "before " + before + " after " + after);
		check("V5 effect_event reached the client", waitUntil(() -> ClientNet.EFFECT_EVENTS.get() > effects, 1000), "no effect event");
		r = post("tsukishiro", "ja-JP", true);
		check("V5 immediate repeat is debounced", !matched(r) && "debounced".equals(str(r, "reason")), r.toString());
		Thread.sleep(1700);
		r = post("tsukishiro", "ja-JP", true);
		check("V5 repeat after the debounce hits the 12 s cooldown (voice does not bypass it)", matched(r) && "COOLDOWN".equals(str(r, "result")), r.toString());

		// ---- V6 seal
		Thread.sleep(400);
		r = post("seal", "en-US", true);
		check("V6 seal by voice", matched(r) && "common.seal".equals(str(r, "command")) && "OK".equals(str(r, "result")), r.toString());
		check("V6 server SEALED", serverState() == ZanpakutoState.SEALED, "server " + serverState());
		check("V6 client SEALED", waitUntil(() -> clientState() == ZanpakutoState.SEALED, 1000), "client " + clientState());

		// ---- V7 wrong item / wrong state
		selectSlot(1);
		Thread.sleep(300);
		r = post("sode no shirayuki", "en-US", true);
		check("V7 Rukia's phrase with Byakuya's sword in hand: gated out", !matched(r) && "gated_out".equals(str(r, "reason")), r.toString());
		selectSlot(0);
		Thread.sleep(300);
		r = post("hakuren", "en-US", true);
		check("V7 ability phrase while sealed: gated out", !matched(r) && "gated_out".equals(str(r, "reason")), r.toString());

		// ---- V8 Byakuya: Chire means three things, "bank eye" only with shikai + full bar
		selectSlot(1);
		serverCommands("reiatsu full");
		Thread.sleep(2500); // release lock of the seal above
		drawSword();
		r = post("Chire, Senbonzakura", "ru-RU", true);
		check("V8 Chire Senbonzakura releases (drawn base form)", matched(r) && "byakuya.shikai.release".equals(str(r, "command")) && "OK".equals(str(r, "result")), r.toString());
		Thread.sleep(400);
		r = post("bank eye", "en-US", true);
		check("V8 'bank eye' with a bar below full is NOT bankai", !matched(r), r.toString());
		onServer(p -> {
			sm(p).devSetReiatsu(1000);
			ZanpakutoManager.applyExternal(p, List.of());
			return null;
		});
		Thread.sleep(1700);
		r = post("bank eye", "en-US", true);
		check("V8 'bank eye' with shikai + full bar + sword is bankai", matched(r) && "byakuya.bankai.release".equals(str(r, "command")) && "OK".equals(str(r, "result")),
				r.toString());
		check("V8 server BANKAI", serverState() == ZanpakutoState.BANKAI, "server " + serverState());
		check("V8 client BANKAI", waitUntil(() -> clientState() == ZanpakutoState.BANKAI, 1000), "client " + clientState());
		Thread.sleep(400);
		r = post("Chire", "en-US", true);
		check("V8 lone Chire in bankai is the petal storm, rejected by the 44-tick settle lock (not bypassed)",
				matched(r) && "byakuya.bankai.scatter".equals(str(r, "command")) && "COOLDOWN".equals(str(r, "result")), r.toString());
		Thread.sleep(2300);
		r = post("petal storm", "en-US", true);
		check("V8 petal storm after the settle time", matched(r) && "byakuya.bankai.scatter".equals(str(r, "command")) && "OK".equals(str(r, "result")), r.toString());
		Thread.sleep(1700);
		r = post("запечатать", "ru-RU", true);
		check("V8 seal in Russian", matched(r) && "common.seal".equals(str(r, "command")) && "OK".equals(str(r, "result")), r.toString());

		// ---- V9 interim results are ignored by default
		Thread.sleep(2200);
		r = post("Chire Senbonzakura", "en-US", false);
		check("V9 interim result ignored (default config)", !matched(r) && "interim_ignored".equals(str(r, "reason")), r.toString());
		check("V9 state unchanged by the interim", serverState() == ZanpakutoState.SEALED, "server " + serverState());

		// ---- V10 hostile requests never reach the game
		HttpResponse<String> evil = HTTP.send(HttpRequest.newBuilder(URI.create(base() + "/voice")).header("Origin", "https://evil.example")
				.header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"Chire Senbonzakura\",\"final\":true}")).build(),
				HttpResponse.BodyHandlers.ofString());
		check("V10 foreign Origin refused (403) and nothing happens", evil.statusCode() == 403 && serverState() == ZanpakutoState.SEALED,
				evil.statusCode() + " " + serverState());

		// ---- V11 latency: 20 releases by voice
		selectSlot(0);
		Thread.sleep(500);
		List<Double> game = new ArrayList<>();
		List<Double> clientState = new ArrayList<>();
		List<Double> clientEffect = new ArrayList<>();
		List<Double> roundTrip = new ArrayList<>();
		for (int i = 0; i < RUNS; i++) {
			onServer(p -> {
				StateMachine m = sm(p);
				ZanpakutoManager.applyExternal(p, m.devSetState(ZanpakutoState.BASE, CharacterId.RUKIA));
				m.devSetReiatsu(1000);
				ZanpakutoManager.applyExternal(p, List.of());
				return null;
			});
			if (!waitUntil(() -> clientState() == ZanpakutoState.BASE, 1000)) {
				ReiatsuTest.LOGGER.warn(P + "client did not see BASE before run {}", i);
			}
			Thread.sleep(1650); // clears the per-command debounce of the previous run
			int ev = ClientNet.EFFECT_EVENTS.get();
			final long[] seen = new long[2];
			long t0 = System.nanoTime();
			Thread watcher = new Thread(() -> {
				try {
					waitUntil(() -> clientState() == ZanpakutoState.SHIKAI, 2000);
					seen[0] = System.nanoTime();
					waitUntil(() -> ClientNet.EFFECT_EVENTS.get() > ev, 2000);
					seen[1] = System.nanoTime();
				} catch (InterruptedException ignored) {
					// stopping
				}
			}, "phase5-watch");
			watcher.start();
			JsonObject reply = post(i % 2 == 0 ? "Mae, Sode no Shirayuki" : "sode no shirayuki", i % 2 == 0 ? "ja-JP" : "en-US", true);
			long t1 = System.nanoTime();
			watcher.join(3000);
			boolean good = matched(reply) && "OK".equals(str(reply, "result"));
			check("V11 run " + (i + 1) + " accepted", good, reply.toString());
			if (good) {
				game.add(reply.get("latencyMs").getAsDouble());
				roundTrip.add((t1 - t0) / 1e6);
				clientState.add(seen[0] == 0 ? Double.NaN : (seen[0] - t0) / 1e6);
				clientEffect.add(seen[1] == 0 ? Double.NaN : (seen[1] - t0) / 1e6);
			}
		}
		String summary = String.format(Locale.ROOT,
				"LATENCY over %d runs (ms): bridge-received->transition-applied mean %.2f max %.2f | POST round trip mean %.2f max %.2f | "
						+ "POST sent->client sees SHIKAI mean %.2f max %.2f | POST sent->client receives effect_event mean %.2f max %.2f",
				game.size(), mean(game), max(game), mean(roundTrip), max(roundTrip), mean(clientState), max(clientState), mean(clientEffect), max(clientEffect));
		ReiatsuTest.LOGGER.info(P + summary);
		try {
			Files.writeString(new File(mc.runDirectory, "phase5_latency.txt").toPath(), summary + System.lineSeparator()
					+ "game=" + game + System.lineSeparator() + "client_state=" + clientState + System.lineSeparator()
					+ "client_effect=" + clientEffect + System.lineSeparator() + "round_trip=" + roundTrip + System.lineSeparator());
		} catch (IOException e) {
			ReiatsuTest.LOGGER.warn(P + "could not write the latency file: {}", e.toString());
		}
		check("V11 all 20 runs measured", game.size() == RUNS, "measured " + game.size());
		check("V11 mean latency within the 400 ms budget", mean(game) <= 400 && mean(clientEffect) <= 400,
				"game " + mean(game) + " clientEffect " + mean(clientEffect));
		check("V11 max latency within the 400 ms budget", max(game) <= 400 && max(clientEffect) <= 400,
				"game " + max(game) + " clientEffect " + max(clientEffect));
		mc.execute(() -> ScreenshotRecorder.saveScreenshot(mc.runDirectory, "p5_end.png", mc.getFramebuffer(), msg -> { }));
		Thread.sleep(500);
	}

	private static double mean(List<Double> v) {
		return v.stream().filter(d -> !d.isNaN()).mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
	}

	private static double max(List<Double> v) {
		return v.stream().filter(d -> !d.isNaN()).mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
	}

	private static void finish() {
		ReiatsuTest.LOGGER.info(P + "SUMMARY passes={} fails={}", passes, FAILS.size());
		for (String f : FAILS) {
			ReiatsuTest.LOGGER.error(P + "FAILED {}", f);
		}
		ReiatsuTest.LOGGER.info(P + "RESULT {}", FAILS.isEmpty() ? "ALL PASS" : "FAILURES");
	}

	// ---------------------------------------------------------------- world

	private static void bootstrapWorld() {
		var o = mc.options;
		o.pauseOnLostFocus = false;
		o.getViewDistance().setValue(6);
		o.getSimulationDistance().setValue(5);
		o.getMaxFps().setValue(120);
		o.getEnableVsync().setValue(false);
		o.getShowAutosaveIndicator().setValue(false);
		o.getGraphicsMode().setValue(GraphicsMode.FANCY);
		o.getGuiScale().setValue(3);
		deleteWorld();
		mc.createIntegratedServerLoader().createAndStart(
				WORLD,
				new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.EASY, true, new GameRules(), DataConfiguration.SAFE_MODE),
				new GeneratorOptions(12345L, false, false),
				registries -> registries.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value().createDimensionsRegistryHolder(),
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
