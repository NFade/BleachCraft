package dev.minebleach.reiatsutest.core.state;

/** Where a request came from; wire bytes 0 and 1. The machine treats both identically. */
public enum RequestSource {
	KEY, VOICE;

	public byte code() {
		return (byte) ordinal();
	}

	public static RequestSource fromCode(byte code) {
		return code == 1 ? VOICE : KEY;
	}
}
