package dev.minebleach.reiatsutest.core.state;

/** The {@code action_result} byte of ADR section 3. */
public enum ResultCode {
	OK, DENIED_STATE, DENIED_ITEM, DENIED_REIATSU, COOLDOWN, RATE_LIMIT;

	public byte code() {
		return (byte) ordinal();
	}

	public static ResultCode fromCode(byte code) {
		ResultCode[] v = values();
		return code >= 0 && code < v.length ? v[code] : DENIED_STATE;
	}
}
