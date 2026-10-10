package dev.minebleach.reiatsutest.core.state;

/**
 * Zanpakuto states. SEALED = sheathed (the sword is in the scabbard), BASE = drawn base form (sealed-drawn, the blade is out
 * but not released), SHIKAI and BANKAI as before. Order matters: the ordinal is the wire code (attachment, request payload).
 */
public enum ZanpakutoState {
	SEALED, BASE, SHIKAI, BANKAI;

	public byte code() {
		return (byte) ordinal();
	}

	/** Out of range codes decode to SEALED. */
	public static ZanpakutoState fromCode(byte code) {
		ZanpakutoState[] v = values();
		return code >= 0 && code < v.length ? v[code] : SEALED;
	}
}
