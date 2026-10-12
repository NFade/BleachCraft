package dev.minebleach.reiatsutest.core.voice;

import java.util.List;

/**
 * What the client bridge sends to the server (packet {@code voice_text}): the text the browser recognised, whether it is final,
 * the recognition language, the utterance index of the page and up to {@link VoiceWire#MAX_ALTERNATIVES} lower ranked
 * hypotheses. {@code reqId} is echoed in the answer. Never audio, never a command id: the server decides what the text means.
 */
public record VoiceRequest(int protocol, int reqId, String text, boolean isFinal, String lang, long utt, List<String> alts) {
	public VoiceRequest {
		text = text == null ? "" : text;
		lang = lang == null ? "" : lang;
		alts = alts == null ? List.of() : List.copyOf(alts);
	}

	public static VoiceRequest of(int reqId, String text, boolean isFinal, String lang, long utt, List<String> alts) {
		return new VoiceRequest(VoiceWire.PROTOCOL, reqId, text, isFinal, lang, utt, alts);
	}
}
