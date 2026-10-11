package dev.minebleach.reiatsutest.core.voice;

import java.util.List;

/**
 * One POST of the bridge page: recognised text, whether the recogniser says it is final, the recognition language, the
 * utterance index and the epoch milliseconds at which the browser received the result (latency logging).
 * {@code utt} is -1 when absent. {@code alternatives} are the lower ranked hypotheses of the same final result
 * (Web Speech {@code maxAlternatives}), best first, without the main text; empty when the page sent none.
 */
public record VoiceMessage(String text, boolean isFinal, String lang, long utt, long clientTimeMs, List<String> alternatives) {
	public VoiceMessage {
		alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
	}

	public VoiceMessage(String text, boolean isFinal, String lang, long utt, long clientTimeMs) {
		this(text, isFinal, lang, utt, clientTimeMs, List.of());
	}
}
