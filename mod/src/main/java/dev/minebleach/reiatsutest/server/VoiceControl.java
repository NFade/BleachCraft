package dev.minebleach.reiatsutest.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.RequestSource;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.state.TransitionResult;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.core.voice.GateContext;
import dev.minebleach.reiatsutest.core.voice.PhraseBook;
import dev.minebleach.reiatsutest.core.voice.PhraseMatcher;
import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import dev.minebleach.reiatsutest.core.voice.VoiceMessage;
import dev.minebleach.reiatsutest.core.voice.VoiceProcessor;
import dev.minebleach.reiatsutest.registry.ModItems;
import dev.minebleach.reiatsutest.voice.VoiceBackend;
import dev.minebleach.reiatsutest.voice.VoiceEndpoint;
import dev.minebleach.reiatsutest.voice.VoiceHttp;
import dev.minebleach.reiatsutest.voice.VoiceHudState;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Voice control glue (phase 5): owns the loopback HTTP bridge for the lifetime of an integrated server, turns each
 * posted text into a decision on the server thread and, when a command fires, calls the SAME paths the key bindings use
 * ({@link ZanpakutoManager#performTransition} / {@link ZanpakutoManager#performCast}). Voice therefore cannot bypass the
 * state machine, cooldowns or reiatsu rules. The player is the integrated-server owner (single player); a dedicated
 * server does not start the bridge (the microphone is on the player's PC, see LOG.md).
 */
public final class VoiceControl implements VoiceBackend {
	private static final VoiceControl INSTANCE = new VoiceControl();
	private static final long SERVER_WAIT_MS = 1500;

	private MinecraftServer server;
	private VoiceConfig cfg;
	private VoiceEndpoint endpoint;
	private PhraseMatcher matcher;
	private int port = -1;
	private volatile byte[] pageBytes;
	private String lastError;
	private final Map<UUID, VoiceProcessor> processors = new HashMap<>();
	private final Map<UUID, String> lastLang = new HashMap<>();

	private VoiceControl() {
	}

	public static VoiceControl get() {
		return INSTANCE;
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(INSTANCE::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> INSTANCE.stop());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> INSTANCE.announce(handler.player));
	}

	// ------------------------------------------------------------------ lifecycle

	private synchronized void start(MinecraftServer srv) {
		stop();
		VoiceHudState.reset();
		server = srv;
		lastError = null;
		cfg = ModConfig.loadVoice();
		if (!cfg.enabled) {
			ReiatsuTest.LOGGER.info("[voice] disabled in config");
			return;
		}
		if (srv.isDedicated()) {
			ReiatsuTest.LOGGER.info("[voice] dedicated server: the voice bridge only runs in single player (integrated server)");
			return;
		}
		try {
			matcher = new PhraseMatcher(PhraseBook.loadBundled(), cfg.matcherConfig());
			VoiceHttp http = new VoiceHttp(cfg, this, this::page);
			endpoint = VoiceEndpoint.create(http);
			port = endpoint.start(cfg.port, cfg.portSearch);
			ReiatsuTest.LOGGER.info("[voice] bridge listening on http://127.0.0.1:{}/ (transport {})", port, endpoint.kind());
		} catch (IOException | RuntimeException e) {
			lastError = e.toString();
			ReiatsuTest.LOGGER.error("[voice] bridge could not start: {}", e.toString());
			stopEndpoint();
		}
	}

	private synchronized void stop() {
		stopEndpoint();
		processors.clear();
		lastLang.clear();
		server = null;
	}

	private void stopEndpoint() {
		if (endpoint != null) {
			endpoint.stop();
			ReiatsuTest.LOGGER.info("[voice] bridge stopped (port {})", port);
		}
		endpoint = null;
		port = -1;
	}

	public synchronized int port() {
		return port;
	}

	public synchronized String url() {
		return port < 0 ? null : "http://127.0.0.1:" + port + "/";
	}

	public synchronized String transport() {
		return endpoint == null ? "off" : endpoint.kind();
	}

	/** Chat line for the world owner: the link to open in the browser, or why the bridge is off. */
	private synchronized void announce(ServerPlayerEntity p) {
		if (server == null || !server.isHost(p.getGameProfile()) || cfg == null || !cfg.enabled || server.isDedicated()) {
			return;
		}
		if (port >= 0) {
			String url = url();
			Text link = Text.literal(url).setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
					.withUnderline(true).withColor(Formatting.AQUA));
			p.sendMessage(Text.translatable("message.reiatsu_test.voice.ready", link), false);
		} else if (lastError != null) {
			p.sendMessage(Text.translatable("message.reiatsu_test.voice.failed", lastError), false);
		}
	}

	private byte[] page() {
		byte[] b = pageBytes;
		if (b == null) {
			try (InputStream in = VoiceControl.class.getResourceAsStream("/assets/reiatsu_test/voice/index.html")) {
				b = in == null ? null : in.readAllBytes();
			} catch (IOException e) {
				b = null;
			}
			pageBytes = b;
		}
		return b;
	}

	// ------------------------------------------------------------------ VoiceBackend (HTTP threads)

	@Override
	public void contact(String mic) {
		VoiceHudState.contact(mic);
	}

	@Override
	public String onVoice(VoiceMessage message, long receivedNanos) {
		VoiceHudState.contact(null);
		MinecraftServer srv;
		synchronized (this) {
			srv = server;
		}
		if (srv == null) {
			return error("no_world");
		}
		return callOnServerThread(srv, () -> handle(message, receivedNanos), "{\"matched\":false,\"reason\":\"server_busy\"}");
	}

	@Override
	public String status() {
		MinecraftServer srv;
		synchronized (this) {
			srv = server;
		}
		if (srv == null) {
			JsonObject o = new JsonObject();
			o.addProperty("ok", true);
			o.addProperty("world", false);
			return o.toString();
		}
		return callOnServerThread(srv, this::statusOnServerThread, "{\"ok\":true,\"world\":true,\"busy\":true}");
	}

	private static String callOnServerThread(MinecraftServer srv, java.util.function.Supplier<String> work, String onTimeout) {
		CompletableFuture<String> f = new CompletableFuture<>();
		AtomicBoolean abandoned = new AtomicBoolean();
		srv.execute(() -> {
			if (abandoned.get()) {
				return; // the HTTP side gave up: never run a stale command late
			}
			try {
				f.complete(work.get());
			} catch (RuntimeException e) {
				ReiatsuTest.LOGGER.error("[voice] handling failed", e);
				f.complete(error("internal"));
			}
		});
		try {
			return f.get(SERVER_WAIT_MS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			abandoned.set(true);
			return onTimeout;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			abandoned.set(true);
			return onTimeout;
		} catch (ExecutionException e) {
			return error("internal");
		}
	}

	private static String error(String reason) {
		JsonObject o = new JsonObject();
		o.addProperty("matched", false);
		o.addProperty("reason", reason);
		return o.toString();
	}

	// ------------------------------------------------------------------ server thread

	private ServerPlayerEntity hostPlayer() {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (server.isHost(p.getGameProfile())) {
				return p;
			}
		}
		return null;
	}

	private String statusOnServerThread() {
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		o.addProperty("world", true);
		o.addProperty("port", port);
		o.addProperty("transport", transport());
		o.addProperty("defaultLanguage", cfg.defaultLanguage);
		JsonArray langs = new JsonArray();
		cfg.languages.forEach(langs::add);
		o.add("languages", langs);
		ServerPlayerEntity p = hostPlayer();
		StateMachine sm = p == null ? null : ZanpakutoManager.machine(p);
		if (p != null) {
			o.addProperty("player", p.getName().getString());
		}
		if (sm != null) {
			CharacterId held = ModItems.characterOf(p.getMainHandStack());
			GateContext ctx = GateContext.of(held, sm.state(), sm.reiatsu().isFull());
			o.addProperty("state", sm.state().name());
			o.addProperty("held", held.name().toLowerCase(Locale.ROOT));
			o.addProperty("reiatsuFull", ctx.reiatsuFull());
			JsonArray say = new JsonArray();
			for (PhraseBook.Command c : matcher.book().commands()) {
				if (c.validIn(ctx) && !c.optional()) {
					JsonObject s = new JsonObject();
					s.addProperty("id", c.id());
					s.addProperty("say", c.say());
					say.add(s);
				}
			}
			o.add("say", say);
		}
		return o.toString();
	}

	private String handle(VoiceMessage msg, long receivedNanos) {
		ServerPlayerEntity p = server == null ? null : hostPlayer();
		StateMachine sm = p == null ? null : ZanpakutoManager.machine(p);
		if (sm == null) {
			return error(p == null ? "no_player" : "no_session");
		}
		CharacterId held = ModItems.characterOf(p.getMainHandStack());
		GateContext ctx = GateContext.of(held, sm.state(), sm.reiatsu().isFull());
		VoiceProcessor proc = processors.computeIfAbsent(p.getUuid(), k -> new VoiceProcessor(matcher, cfg, System::currentTimeMillis));
		String prevLang = lastLang.put(p.getUuid(), msg.lang());
		if (prevLang != null && !prevLang.equals(msg.lang())) {
			proc.reset(); // a language switch starts a new recognition session
		}
		VoiceProcessor.Decision d = proc.process(msg, ctx);
		if (!msg.isFinal()) {
			VoiceHudState.interim(msg.text());
		}
		JsonObject o = new JsonObject();
		o.addProperty("matched", d.fired());
		o.addProperty("command", d.commandId());
		o.addProperty("confidence", Math.round(d.confidence() * 1000.0) / 1000.0);
		o.addProperty("reason", d.outcome().wire());
		o.addProperty("text", d.normalized());
		if (d.fired()) {
			TransitionResult r = dispatch(p, d.commandId());
			VoiceHudState.result(msg.text(), d.commandId(), r == null ? VoiceHudState.Result.NO_MATCH : hudResult(r));
			long appliedNanos = System.nanoTime();
			double latencyMs = (appliedNanos - receivedNanos) / 1_000_000.0;
			o.addProperty("result", r == null ? "UNKNOWN_COMMAND" : r.code().name());
			o.addProperty("accepted", r != null && r.ok());
			o.addProperty("latencyMs", Math.round(latencyMs * 100.0) / 100.0);
			if (msg.clientTimeMs() > 0) {
				o.addProperty("browserToAppliedMs", System.currentTimeMillis() - msg.clientTimeMs());
			}
			ReiatsuTest.LOGGER.info("[voice] \"{}\" ({}, final={}) -> {} conf={} => {} state={} in {} ms", msg.text(), msg.lang(),
					msg.isFinal(), d.commandId(), o.get("confidence"), o.get("result").getAsString(), sm.state(),
					String.format(Locale.ROOT, "%.2f", latencyMs));
		} else if (d.outcome() == VoiceProcessor.Outcome.GATED_OUT || d.outcome() == VoiceProcessor.Outcome.AMBIGUOUS) {
			if (msg.isFinal()) {
				VoiceHudState.result(msg.text(), d.commandId(), VoiceHudState.Result.GATED);
			}
			ReiatsuTest.LOGGER.info("[voice] \"{}\" -> {} {}", msg.text(), d.outcome().wire(), d.commandId());
			if (d.outcome() == VoiceProcessor.Outcome.GATED_OUT && d.commandId() != null && d.commandId().endsWith(".shikai.release")
					&& sm.state() == ZanpakutoState.SEALED && held != CharacterId.NONE) {
				// the release phrase works from the drawn base form only: tell the player to draw first
				p.sendMessage(Text.translatable("message.reiatsu_test.denied.not_drawn"), true);
			}
		} else {
			if (msg.isFinal() && d.outcome() != VoiceProcessor.Outcome.EMPTY) {
				VoiceHudState.result(msg.text(), d.commandId(), VoiceHudState.Result.NO_MATCH);
			}
			ReiatsuTest.LOGGER.debug("[voice] \"{}\" -> {}", msg.text(), d.outcome().wire());
		}
		o.addProperty("state", sm.state().name());
		return o.toString();
	}

	private static VoiceHudState.Result hudResult(TransitionResult r) {
		return switch (r.code()) {
			case OK -> VoiceHudState.Result.ACCEPTED;
			case DENIED_REIATSU -> VoiceHudState.Result.DENIED_REIATSU;
			case DENIED_ITEM -> VoiceHudState.Result.DENIED_ITEM;
			case DENIED_NOT_DRAWN -> VoiceHudState.Result.DENIED_NOT_DRAWN;
			case COOLDOWN -> VoiceHudState.Result.COOLDOWN;
			case DENIED_STATE, RATE_LIMIT, BLOCKED -> VoiceHudState.Result.DENIED_STATE;
		};
	}

	/** Maps a command id to the server path the key bindings use. Null for an unknown id. */
	private TransitionResult dispatch(ServerPlayerEntity p, String id) {
		TransitionResult r;
		if (id.endsWith(".shikai.release")) {
			announceHeard(p, "message.reiatsu_test.voice.cmd.shikai");
			r = ZanpakutoManager.performTransition(p, ZanpakutoState.SHIKAI, RequestSource.VOICE, StateMachine.SERVER_SEQ);
		} else if (id.endsWith(".bankai.release")) {
			announceHeard(p, "message.reiatsu_test.voice.cmd.bankai");
			r = ZanpakutoManager.performTransition(p, ZanpakutoState.BANKAI, RequestSource.VOICE, StateMachine.SERVER_SEQ);
		} else if (id.equals("common.seal")) {
			announceHeard(p, "message.reiatsu_test.voice.cmd.seal");
			r = ZanpakutoManager.performTransition(p, ZanpakutoState.SEALED, RequestSource.VOICE, StateMachine.SERVER_SEQ);
		} else {
			AbilityId a = AbilityId.fromCommandId(id);
			if (a == null) {
				return null;
			}
			announceHeard(p, "ability.reiatsu_test." + a.commandId);
			r = ZanpakutoManager.performCast(p, a, RequestSource.VOICE, StateMachine.SERVER_SEQ);
		}
		return r;
	}

	private static void announceHeard(ServerPlayerEntity p, String nameKey) {
		p.sendMessage(Text.translatable("message.reiatsu_test.voice.heard", Text.translatable(nameKey)), true);
	}
}
