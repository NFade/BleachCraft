package dev.minebleach.reiatsutest.core.voice;

import dev.minebleach.reiatsutest.core.voice.PhraseBook.Command;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Per player pipeline between the HTTP bridge and the state machine: filters interim results, strips already consumed
 * text, runs the {@link PhraseMatcher} under a {@link GateContext}, and applies the debounce rules. It decides only
 * whether a command fires; cooldown, reiatsu and state rules stay with the server (a rejected command still counts for
 * the debounce window). Not thread safe: call it from one thread (the server thread).
 *
 * <p>Rules (ADR section 4, VOICE_PHRASES 5.4 and 5.5):
 * <ul>
 *   <li>final results fire at {@code threshold}; interim results are ignored unless {@code interimEnabled}, and then
 *       only an early-OK command, via a non-weak variant, with confidence at least {@code interimThreshold}, seen on
 *       two consecutive interims of the same utterance;</li>
 *   <li>one command per utterance index; same command debounce {@code debounceMs}; any command {@code globalGapMs};</li>
 *   <li>consumed text: for {@code consumedTextMs} after a fire, a message starting with the same key tokens has them
 *       removed (the Web Speech API repeats finals); an empty remainder is dropped.</li>
 * </ul>
 */
public final class VoiceProcessor {
	public enum Outcome {
		FIRED, NO_MATCH, GATED_OUT, AMBIGUOUS, DEBOUNCED, INTERIM_IGNORED, WEAK_NOT_WHOLE_UTTERANCE, EMPTY;

		public String wire() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** {@code commandId} is set for FIRED, and for GATED_OUT / DEBOUNCED / INTERIM_IGNORED when a command was recognised. */
	public record Decision(Outcome outcome, String commandId, double confidence, String normalized, String detail) {
		public boolean fired() {
			return outcome == Outcome.FIRED;
		}
	}

	private final PhraseMatcher matcher;
	private final VoiceConfig cfg;
	private final LongSupplier clockMs;
	private final Map<String, Long> lastFiredMs = new HashMap<>();
	private long lastAnyFiredMs = Long.MIN_VALUE / 2;
	private long firedUtt = Long.MIN_VALUE;
	private List<String> consumed = List.of();
	private long consumedAtMs = Long.MIN_VALUE / 2;
	private String pendingInterimCommand;
	private long pendingInterimUtt = Long.MIN_VALUE;

	public VoiceProcessor(PhraseMatcher matcher, VoiceConfig cfg, LongSupplier clockMs) {
		this.matcher = matcher;
		this.cfg = cfg;
		this.clockMs = clockMs;
	}

	public PhraseMatcher matcher() {
		return matcher;
	}

	/** Forgets debounce and consumed text (new recognition session, language switch, player respawn). */
	public void reset() {
		lastFiredMs.clear();
		lastAnyFiredMs = Long.MIN_VALUE / 2;
		firedUtt = Long.MIN_VALUE;
		consumed = List.of();
		pendingInterimCommand = null;
	}

	public Decision process(VoiceMessage msg, GateContext ctx) {
		long now = clockMs.getAsLong();
		String key = Normalizer.normalize(msg.text());
		if (key.isEmpty()) {
			return new Decision(Outcome.EMPTY, null, 0.0, key, "");
		}
		if (!msg.isFinal() && !cfg.interimEnabled) {
			return new Decision(Outcome.INTERIM_IGNORED, null, 0.0, key, "interim results are off");
		}
		if (msg.utt() >= 0 && msg.utt() == firedUtt) {
			return new Decision(Outcome.DEBOUNCED, null, 0.0, key, "utterance already acted on");
		}
		List<String> tokens = Normalizer.tokens(key);
		if (!consumed.isEmpty() && now - consumedAtMs <= cfg.consumedTextMs && tokens.size() >= consumed.size()
				&& tokens.subList(0, consumed.size()).equals(consumed)) {
			tokens = new ArrayList<>(tokens.subList(consumed.size(), tokens.size()));
			if (tokens.isEmpty()) {
				return new Decision(Outcome.DEBOUNCED, null, 0.0, key, "repeat of the text just acted on");
			}
			key = String.join(" ", tokens);
		}
		PhraseMatcher.Result r = matcher.matchKey(key, ctx);
		switch (r.status()) {
			case NO_MATCH -> {
				pendingInterimCommand = null;
				// would some command match if the state and item did not matter? then say so (useful feedback)
				PhraseMatcher.Result any = matcher.matchKey(key, GateContext.ungated());
				if (any.matched() || any.status() == PhraseMatcher.Status.AMBIGUOUS) {
					return new Decision(Outcome.GATED_OUT, any.commandId(), any.confidence(), key, "valid in another state or item");
				}
				return new Decision(Outcome.NO_MATCH, null, 0.0, key, "");
			}
			case WEAK_NOT_WHOLE -> {
				pendingInterimCommand = null;
				return new Decision(Outcome.WEAK_NOT_WHOLE_UTTERANCE, null, 0.0, key, "");
			}
			case AMBIGUOUS -> {
				pendingInterimCommand = null;
				return new Decision(Outcome.AMBIGUOUS, null, r.confidence(), key, "");
			}
			default -> { }
		}
		Command cmd = r.command();
		if (!msg.isFinal()) {
			boolean ok = cmd.interimSafe() && !r.variant().weak() && r.confidence() >= cfg.interimThreshold;
			if (!ok) {
				pendingInterimCommand = null;
				return new Decision(Outcome.INTERIM_IGNORED, cmd.id(), r.confidence(), key, "not safe for an interim result");
			}
			boolean stable = cmd.id().equals(pendingInterimCommand) && msg.utt() == pendingInterimUtt;
			pendingInterimCommand = cmd.id();
			pendingInterimUtt = msg.utt();
			if (!stable) {
				return new Decision(Outcome.INTERIM_IGNORED, cmd.id(), r.confidence(), key, "waiting for a second interim");
			}
		}
		Long last = lastFiredMs.get(cmd.id());
		if (last != null && now - last < cfg.debounceMs) {
			return new Decision(Outcome.DEBOUNCED, cmd.id(), r.confidence(), key, "same command " + (now - last) + " ms ago");
		}
		if (now - lastAnyFiredMs < cfg.globalGapMs) {
			return new Decision(Outcome.DEBOUNCED, cmd.id(), r.confidence(), key, "another command " + (now - lastAnyFiredMs) + " ms ago");
		}
		lastFiredMs.put(cmd.id(), now);
		lastAnyFiredMs = now;
		firedUtt = msg.utt();
		consumed = Normalizer.tokens(key);
		consumedAtMs = now;
		pendingInterimCommand = null;
		return new Decision(Outcome.FIRED, cmd.id(), r.confidence(), key, r.variant().text());
	}
}
