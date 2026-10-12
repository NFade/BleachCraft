package dev.minebleach.reiatsutest.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.core.voice.GateContext;
import dev.minebleach.reiatsutest.core.voice.PhraseBook;
import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import dev.minebleach.reiatsutest.core.voice.VoiceFeedback;
import dev.minebleach.reiatsutest.core.voice.VoiceMessage;
import dev.minebleach.reiatsutest.core.voice.VoiceRequest;
import dev.minebleach.reiatsutest.core.voice.VoiceWire;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The backend of the player's own loopback bridge (online voice, same scheme as vox-arcana): the browser page of THIS player
 * posts what it recognised, the bridge forwards the text to the server the player is connected to ({@link Link#send}) and
 * answers the page with the server's verdict ({@link #onFeedback}). The bridge decides nothing: no phrase matching, no command,
 * no state rules live here; the server does all of it and the client can only say a phrase. The same path serves a dedicated
 * server, a LAN world and single player.
 *
 * <p>No Minecraft types: the game side is behind {@link Link}, so the bridge is tested without a game. The page-facing JSON
 * (fields {@code matched, command, confidence, reason, text, result, accepted, latencyMs, state}; {@code /status}) is the format
 * of the single-player bridge before online voice, so the page needs no change.
 */
public final class ClientVoiceBridge implements VoiceBackend {
	/** The game connection as the bridge sees it. All methods may be called from any thread and must not block for long. */
	public interface Link {
		/** True while this client is in a world (connected to a server). */
		boolean inWorld();

		/**
		 * Hands the request to the network thread. Never blocks. When the request cannot be sent (server without the voice
		 * packet, left the world meanwhile) the link answers with a {@code rejected} {@link VoiceFeedback} instead.
		 */
		void send(VoiceRequest request);

		/** Current local view for {@code /status} (may wait briefly for the client thread), or null when not in a world. */
		Status status();
	}

	/** {@code state} / {@code held} are null before the first sync from the server. */
	public record Status(boolean inWorld, boolean serverVoice, String player, ZanpakutoState state, CharacterId held, boolean reiatsuFull) {
		public static final Status NOT_IN_WORLD = new Status(false, false, null, null, null, false);
	}

	private static final class Pending {
		final String text;
		final boolean isFinal;
		final long createdMs;
		final CompletableFuture<VoiceFeedback> reply = new CompletableFuture<>();

		Pending(String text, boolean isFinal, long createdMs) {
			this.text = text;
			this.isFinal = isFinal;
			this.createdMs = createdMs;
		}
	}

	private static final long PENDING_KEEP_MS = 15_000;

	private final Link link;
	private final PhraseBook book;
	private final VoiceConfig cfg;
	private final LongSupplier wallMs;
	private final long waitMs;
	private final AtomicInteger seq = new AtomicInteger();
	private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
	private volatile int port = -1;
	private volatile String transport = "off";
	private volatile boolean serverVoiceOff;
	private volatile String lastHeard = "";
	private volatile String lastVerdict = "";
	private volatile String lastCommand;
	private volatile String lastReason = "";
	private volatile long lastAtMs;
	private volatile Consumer<String> notice = r -> { };
	private volatile boolean noticeGiven;

	public ClientVoiceBridge(Link link, PhraseBook book, VoiceConfig cfg, LongSupplier wallMs, long waitMs) {
		this.link = link;
		this.book = book;
		this.cfg = cfg;
		this.wallMs = wallMs;
		this.waitMs = waitMs;
	}

	/** Where the bridge listens (set once the endpoint is bound; shown by {@code /status}). */
	public void setEndpoint(int port, String transport) {
		this.port = port;
		this.transport = transport;
	}

	/** Called at most once with {@code disabled} or {@code no_server_support} when the server cannot take voice (chat hint). */
	public void setNotice(Consumer<String> notice) {
		this.notice = notice == null ? r -> { } : notice;
	}

	public int pendingCount() {
		return pending.size();
	}

	// ------------------------------------------------------------------ VoiceBackend (HTTP threads)

	@Override
	public void contact(String mic) {
		VoiceHudState.contact(mic);
	}

	@Override
	public String onVoice(VoiceMessage msg, long receivedNanos) {
		VoiceHudState.contact(null);
		if (!link.inWorld()) {
			return error("no_world");
		}
		String text = msg.text();
		if (text.isBlank()) {
			return error("empty");
		}
		if (text.length() > VoiceWire.MAX_TEXT_CHARS) {
			return error("too_long");
		}
		if (!msg.isFinal()) {
			VoiceHudState.interim(text);
		}
		List<String> alts = new ArrayList<>();
		for (String a : msg.alternatives()) {
			if (alts.size() < VoiceWire.MAX_ALTERNATIVES && !a.isBlank() && a.length() <= VoiceWire.MAX_TEXT_CHARS) {
				alts.add(a);
			}
		}
		long now = wallMs.getAsLong();
		pending.values().removeIf(p -> now - p.createdMs > PENDING_KEEP_MS);
		int id = seq.updateAndGet(i -> i >= Integer.MAX_VALUE - 1 ? 1 : i + 1);
		Pending p = new Pending(text, msg.isFinal(), now);
		pending.put(id, p);
		link.send(VoiceRequest.of(id, text, msg.isFinal(), msg.lang(), msg.utt(), alts));
		try {
			VoiceFeedback fb = p.reply.get(waitMs, TimeUnit.MILLISECONDS);
			return replyJson(fb, receivedNanos);
		} catch (TimeoutException e) {
			return error("server_busy"); // the entry stays: a late answer still updates the HUD
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return error("server_busy");
		} catch (ExecutionException e) {
			return error("internal");
		}
	}

	@Override
	public String status() {
		Status s = link.status();
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		if (s == null || !s.inWorld()) {
			o.addProperty("world", false);
			return o.toString();
		}
		if (!s.serverVoice()) {
			o.addProperty("world", false);
			o.addProperty("reason", "server_without_voice");
			return o.toString();
		}
		o.addProperty("world", true);
		o.addProperty("port", port);
		o.addProperty("transport", transport);
		o.addProperty("defaultLanguage", cfg.defaultLanguage);
		JsonArray langs = new JsonArray();
		cfg.languages.forEach(langs::add);
		o.add("languages", langs);
		o.addProperty("serverVoice", !serverVoiceOff);
		if (s.player() != null) {
			o.addProperty("player", s.player());
		}
		if (s.state() != null) {
			CharacterId held = s.held() == null ? CharacterId.NONE : s.held();
			GateContext ctx = GateContext.of(held, s.state(), s.reiatsuFull());
			o.addProperty("state", s.state().name());
			o.addProperty("held", held.name().toLowerCase(Locale.ROOT));
			o.addProperty("reiatsuFull", ctx.reiatsuFull());
			JsonArray say = new JsonArray();
			for (PhraseBook.Command c : book.commands()) {
				if (c.validIn(ctx) && !c.optional()) {
					JsonObject e = new JsonObject();
					e.addProperty("id", c.id());
					e.addProperty("say", c.say());
					say.add(e);
				}
			}
			o.add("say", say);
		}
		if (!lastVerdict.isEmpty()) {
			JsonObject last = new JsonObject();
			last.addProperty("text", lastHeard);
			last.addProperty("verdict", lastVerdict);
			last.addProperty("command", lastCommand);
			last.addProperty("reason", lastReason);
			last.addProperty("ageMs", Math.max(0, wallMs.getAsLong() - lastAtMs));
			o.add("last", last);
		}
		return o.toString();
	}

	// ------------------------------------------------------------------ server answers (client thread)

	/** The server's answer (or a local rejection from the link). Updates the HUD and releases the waiting HTTP thread. */
	public void onFeedback(VoiceFeedback fb) {
		if (fb.verdict() == VoiceFeedback.Verdict.PENDING) {
			VoiceHudState.setPending(fb.commandId());
			return;
		}
		Pending p = pending.remove(fb.reqId());
		if (p == null) {
			return; // unknown or expired
		}
		lastHeard = p.text;
		lastVerdict = fb.verdict().name().toLowerCase(Locale.ROOT);
		lastCommand = fb.commandId();
		lastReason = fb.reason();
		lastAtMs = wallMs.getAsLong();
		if (fb.verdict() == VoiceFeedback.Verdict.REJECTED) {
			if (fb.reason().equals("disabled") || fb.reason().equals("no_server_support")) {
				serverVoiceOff = true;
				if (!noticeGiven) {
					noticeGiven = true;
					notice.accept(fb.reason());
				}
			}
		} else {
			serverVoiceOff = false;
		}
		hud(p, fb);
		p.reply.complete(fb);
	}

	private static void hud(Pending p, VoiceFeedback fb) {
		switch (fb.verdict()) {
			case OK, DENIED -> VoiceHudState.result(p.text, fb.commandId(), VoiceHudState.fromResultCode(fb.resultCode()));
			case GATED_OUT -> {
				if (p.isFinal) {
					VoiceHudState.result(p.text, fb.commandId(), VoiceHudState.Result.GATED);
				}
			}
			case NO_MATCH -> {
				if (p.isFinal) {
					VoiceHudState.result(p.text, fb.commandId(), VoiceHudState.Result.NO_MATCH);
				}
			}
			default -> { }
		}
	}

	private String replyJson(VoiceFeedback fb, long receivedNanos) {
		JsonObject o = new JsonObject();
		boolean fired = fb.verdict().fired();
		o.addProperty("matched", fired);
		o.addProperty("command", fb.commandId());
		o.addProperty("confidence", fb.confidencePermille() / 1000.0);
		o.addProperty("reason", fb.reason());
		o.addProperty("text", fb.heard());
		if (fired) {
			o.addProperty("result", ResultCode.fromCode((byte) fb.resultCode()).name());
			o.addProperty("accepted", fb.verdict() == VoiceFeedback.Verdict.OK);
			double latencyMs = (System.nanoTime() - receivedNanos) / 1_000_000.0;
			o.addProperty("latencyMs", Math.round(latencyMs * 100.0) / 100.0);
		}
		o.addProperty("state", fb.state());
		return o.toString();
	}

	private static String error(String reason) {
		JsonObject o = new JsonObject();
		o.addProperty("matched", false);
		o.addProperty("reason", reason);
		return o.toString();
	}
}
