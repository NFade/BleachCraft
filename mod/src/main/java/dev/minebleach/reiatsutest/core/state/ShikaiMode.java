package dev.minebleach.reiatsutest.core.state;

/** Byakuya shikai swarm mode (the synced snapshot carries it for every character). */
public enum ShikaiMode {
	IDLE, ATTACK, BARRIER;

	public byte code() {
		return (byte) ordinal();
	}

	public static ShikaiMode fromCode(byte code) {
		ShikaiMode[] v = values();
		return code >= 0 && code < v.length ? v[code] : IDLE;
	}
}
