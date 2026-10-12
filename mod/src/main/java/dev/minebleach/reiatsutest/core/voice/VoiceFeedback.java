package dev.minebleach.reiatsutest.core.voice;

/**
 * The server's answer to one {@link VoiceRequest} (packet {@code voice_feedback}). {@code heard} is the normalised key the
 * matcher saw, {@code reason} the processor outcome or the rejection reason (wire names such as {@code no_match},
 * {@code gated_out}, {@code debounced}, {@code rate_limited}), {@code resultCode} the {@code ResultCode} byte of a fired command
 * (-1 when nothing fired), {@code confidencePermille} 0..1000.
 *
 * <p>{@link Verdict#PENDING} is sent once before the command runs, so the client knows which command the following
 * {@code action_result} belongs to; the final answer follows.
 */
public record VoiceFeedback(int reqId, Verdict verdict, String commandId, String heard, String reason, int resultCode,
		int confidencePermille, String state) {
	public enum Verdict {
		/** A command fired and the server accepted it. */
		OK,
		/** Nothing recognised (or nothing usable). */
		NO_MATCH,
		/** A command was recognised but does not fit the state or the sword in hand. */
		GATED_OUT,
		/** A command fired but the server refused it (reiatsu, rage, cooldown, state, item, ...). */
		DENIED,
		/** Dropped on purpose: interim result, debounce, empty text. */
		IGNORED,
		/** The command is about to run (sent before it, see above). */
		PENDING,
		/** The request was refused as a whole: voice disabled, rate limit, too long, bad protocol, no session. */
		REJECTED;

		public byte code() {
			return (byte) ordinal();
		}

		public static Verdict fromCode(int code) {
			Verdict[] v = values();
			return code >= 0 && code < v.length ? v[code] : REJECTED;
		}

		/** True when a command was started (accepted or refused by the state machine). */
		public boolean fired() {
			return this == OK || this == DENIED;
		}
	}

	public VoiceFeedback {
		verdict = verdict == null ? Verdict.REJECTED : verdict;
		heard = heard == null ? "" : heard;
		reason = reason == null ? "" : reason;
		state = state == null ? "" : state;
		confidencePermille = Math.max(0, Math.min(1000, confidencePermille));
	}

	public static VoiceFeedback rejected(int reqId, String reason) {
		return new VoiceFeedback(reqId, Verdict.REJECTED, null, "", reason, -1, 0, "");
	}

	public static VoiceFeedback pending(int reqId, String commandId) {
		return new VoiceFeedback(reqId, Verdict.PENDING, commandId, "", "pending", -1, 0, "");
	}
}
