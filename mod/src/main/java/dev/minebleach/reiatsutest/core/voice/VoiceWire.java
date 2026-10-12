package dev.minebleach.reiatsutest.core.voice;

/**
 * Limits and version of the voice packets (client bridge to server, server to client). The browser recognises speech on the
 * player's own PC; only text travels to the server. Everything here is a bound the server enforces on an untrusted client.
 */
public final class VoiceWire {
	/** Protocol version of {@link VoiceRequest}; the server answers another version with {@code bad_protocol}. */
	public static final int PROTOCOL = 1;
	/** Longest accepted text (and alternative) in characters. */
	public static final int MAX_TEXT_CHARS = 200;
	/** At most this many alternatives are used; the rest is dropped. */
	public static final int MAX_ALTERNATIVES = 3;
	/** Longest language tag that is kept. */
	public static final int MAX_LANG_CHARS = 16;
	/**
	 * The decoder reads up to this many characters, so that a text slightly over {@link #MAX_TEXT_CHARS} is answered with
	 * {@code too_long} instead of a decode error (which would disconnect the sender). Far bigger input still fails to decode.
	 */
	public static final int DECODE_MAX_TEXT_CHARS = 1024;
	/** The decoder refuses a packet that claims more alternatives than this. */
	public static final int DECODE_MAX_ALTERNATIVES = 8;

	private VoiceWire() {
	}
}
