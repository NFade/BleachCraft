package dev.minebleach.reiatsutest.core.voice;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.core.voice.VoiceFeedback.Verdict;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Server side of the online voice path: validates one {@link VoiceRequest} of one player and turns it into a decision.
 * The recognition itself (phrase matching, gating by state / reiatsu / sword in hand, Rage, interim safety, debounce) is
 * unchanged: a {@link VoiceProcessor} per player under the {@link GateContext} the host reports for that player. The client
 * sends text only, so it cannot do more than say a phrase: a fired command goes through {@link Host#execute}, the same server
 * path as the key bindings.
 *
 * <p>Per player: a processor (debounce, global gap, consumed text), the last language (a switch starts a new session) and a
 * sliding one second window for {@code voice.maxRequestsPerSecond}. Nothing is shared between players. Not thread safe: call
 * it from the server thread.
 */
public final class VoiceIngest {
	/** What the ingest needs from the game, identified by the player's UUID. */
	public interface Host {
		/** Gate of the player right now, or null when the player has no session (cannot be gated). */
		GateContext gate(UUID player);

		/**
		 * Runs a recognised command for exactly this player through the server path of the key bindings. Returns its result, or
		 * null for an unknown command id. {@code reqId} lets the host tell the client which command is about to run.
		 */
		ResultCode execute(UUID player, String commandId, int reqId);

		/** Name of the player's zanpakuto state after the command, for the answer. */
		String stateName(UUID player);

		/** The release phrase was heard with the sword still sheathed: tell the player to draw first. */
		default void hintNotDrawn(UUID player) {
		}
	}

	/** The answer for the player plus an optional log line ({@code info} = log at INFO, otherwise debug). */
	public record Handled(VoiceFeedback feedback, String log, boolean info) {
	}

	private static final class PlayerState {
		final VoiceProcessor proc;
		final ArrayDeque<Long> recent = new ArrayDeque<>();
		String lastLang;

		PlayerState(VoiceProcessor proc) {
			this.proc = proc;
		}

		/** Counts the request and says whether it is within {@code perSecond} in the last second. */
		boolean admit(long nowNanos, int perSecond) {
			while (!recent.isEmpty() && nowNanos - recent.peekFirst() > 1_000_000_000L) {
				recent.pollFirst();
			}
			if (recent.size() >= perSecond) {
				return false;
			}
			recent.addLast(nowNanos);
			return true;
		}
	}

	private final VoiceConfig cfg;
	private final PhraseMatcher matcher;
	private final Host host;
	private final LongSupplier clockMs;
	private final LongSupplier nanos;
	private final Map<UUID, PlayerState> players = new HashMap<>();

	public VoiceIngest(VoiceConfig cfg, PhraseMatcher matcher, Host host) {
		this(cfg, matcher, host, System::currentTimeMillis, System::nanoTime);
	}

	public VoiceIngest(VoiceConfig cfg, PhraseMatcher matcher, Host host, LongSupplier clockMs, LongSupplier nanos) {
		this.cfg = cfg;
		this.matcher = matcher;
		this.host = host;
		this.clockMs = clockMs;
		this.nanos = nanos;
	}

	public PhraseMatcher matcher() {
		return matcher;
	}

	public VoiceConfig config() {
		return cfg;
	}

	/** Drops the state of a player who left. */
	public void forget(UUID player) {
		players.remove(player);
	}

	public void clear() {
		players.clear();
	}

	public int trackedPlayers() {
		return players.size();
	}

	private static Handled reject(VoiceRequest req, String reason, String log) {
		return new Handled(VoiceFeedback.rejected(req.reqId(), reason), log, false);
	}

	public Handled handle(UUID player, String name, VoiceRequest req) {
		if (!cfg.enabled) {
			return reject(req, "disabled", null); // the server switched voice off: nothing is looked at, nothing runs
		}
		if (req.protocol() != VoiceWire.PROTOCOL) {
			return reject(req, "bad_protocol", "[voice] " + name + " sent protocol " + req.protocol() + ", expected " + VoiceWire.PROTOCOL);
		}
		PlayerState ps = players.computeIfAbsent(player, k -> new PlayerState(new VoiceProcessor(matcher, cfg, clockMs)));
		if (!ps.admit(nanos.getAsLong(), cfg.maxRequestsPerSecond)) {
			return reject(req, "rate_limited", null);
		}
		String text = req.text();
		if (text.isBlank()) {
			return new Handled(new VoiceFeedback(req.reqId(), Verdict.IGNORED, null, "", "empty", -1, 0, ""), null, false);
		}
		if (text.length() > VoiceWire.MAX_TEXT_CHARS) {
			return reject(req, "too_long", "[voice] " + name + " sent " + text.length() + " characters, limit " + VoiceWire.MAX_TEXT_CHARS);
		}
		GateContext ctx = host.gate(player);
		if (ctx == null) {
			return reject(req, "no_session", null);
		}
		List<String> alts = new ArrayList<>();
		for (String a : req.alts()) {
			if (alts.size() < VoiceWire.MAX_ALTERNATIVES && a != null && !a.isBlank() && a.length() <= VoiceWire.MAX_TEXT_CHARS) {
				alts.add(a);
			}
		}
		String lang = req.lang().length() > VoiceWire.MAX_LANG_CHARS ? req.lang().substring(0, VoiceWire.MAX_LANG_CHARS) : req.lang();
		String prevLang = ps.lastLang;
		ps.lastLang = lang;
		if (prevLang != null && !prevLang.equals(lang)) {
			ps.proc.reset(); // a language switch starts a new recognition session
		}
		VoiceMessage msg = new VoiceMessage(text, req.isFinal(), lang, req.utt(), 0, alts);
		VoiceProcessor.Decision d = ps.proc.process(msg, ctx);
		int permille = (int) Math.round(d.confidence() * 1000.0);

		if (d.fired()) {
			ResultCode r = host.execute(player, d.commandId(), req.reqId());
			String state = host.stateName(player);
			if (r == null) {
				return new Handled(new VoiceFeedback(req.reqId(), Verdict.NO_MATCH, d.commandId(), d.normalized(), "unknown_command", -1,
						permille, state), "[voice] " + name + " \"" + text + "\" -> unknown command " + d.commandId(), true);
			}
			Verdict v = r == ResultCode.OK ? Verdict.OK : Verdict.DENIED;
			String log = String.format(Locale.ROOT, "[voice] %s \"%s\" (%s, final=%s) key='%s' via %s -> %s conf=%.3f => %s state=%s", name, text,
					lang, req.isFinal(), d.normalized(), d.detail(), d.commandId(), d.confidence(), r.name(), state);
			return new Handled(new VoiceFeedback(req.reqId(), v, d.commandId(), d.normalized(), "fired", r.code(), permille, state), log, true);
		}
		String state = host.stateName(player);
		VoiceProcessor.Outcome o = d.outcome();
		String reason = o.wire();
		switch (o) {
			case GATED_OUT, AMBIGUOUS -> {
				if (o == VoiceProcessor.Outcome.GATED_OUT && d.commandId() != null && d.commandId().endsWith(".shikai.release")
						&& ctx.state() == ZanpakutoState.SEALED && ctx.held() != CharacterId.NONE) {
					host.hintNotDrawn(player); // the release phrase works from the drawn base form only
				}
				return new Handled(new VoiceFeedback(req.reqId(), Verdict.GATED_OUT, d.commandId(), d.normalized(), reason, -1, permille, state),
						"[voice] " + name + " \"" + text + "\" -> " + reason + " " + d.commandId(), req.isFinal());
			}
			case DEBOUNCED, INTERIM_IGNORED -> {
				// DEBOUNCED is the Web Speech API repeating a final that already worked: the HUD keeps the first result
				return new Handled(new VoiceFeedback(req.reqId(), Verdict.IGNORED, d.commandId(), d.normalized(), reason, -1, permille, state),
						o == VoiceProcessor.Outcome.DEBOUNCED ? "[voice] " + name + " \"" + text + "\" -> " + reason : null, false);
			}
			default -> {
				// NO_MATCH, EMPTY (a final the normaliser could not read), WEAK_NOT_WHOLE_UTTERANCE
				String log = req.isFinal()
						? "[voice] " + name + " heard \"" + text + "\" (" + lang + ") key='" + d.normalized() + "' alts=" + alts + " -> " + reason : null;
				return new Handled(new VoiceFeedback(req.reqId(), Verdict.NO_MATCH, d.commandId(), d.normalized(), reason, -1, permille, state),
						log, true);
			}
		}
	}
}
