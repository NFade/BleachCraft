package dev.minebleach.reiatsutest.core.state;

public enum ZanpakutoState {
	SEALED, SHIKAI, BANKAI;

	public byte code() {
		return (byte) ordinal();
	}

	/** Out of range codes decode to SEALED. */
	public static ZanpakutoState fromCode(byte code) {
		ZanpakutoState[] v = values();
		return code >= 0 && code < v.length ? v[code] : SEALED;
	}
}
