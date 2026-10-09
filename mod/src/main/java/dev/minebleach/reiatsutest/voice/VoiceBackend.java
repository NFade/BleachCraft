package dev.minebleach.reiatsutest.voice;

import dev.minebleach.reiatsutest.core.voice.VoiceMessage;

/** What the HTTP bridge needs from the game side; implemented by the server glue, faked in tests. */
public interface VoiceBackend {
	/**
	 * Handles one recognised text. Blocking is allowed (the glue hops to the server thread and waits briefly). Returns the
	 * JSON reply body. {@code receivedNanos} is {@link System#nanoTime()} at the moment the request reached the bridge.
	 */
	String onVoice(VoiceMessage message, long receivedNanos);

	/** JSON for {@code GET /status}. */
	String status();
}
