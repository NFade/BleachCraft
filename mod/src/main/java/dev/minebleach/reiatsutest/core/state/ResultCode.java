package dev.minebleach.reiatsutest.core.state;

/** The {@code action_result} byte of ADR section 3. */
public enum ResultCode {
	OK, DENIED_STATE, DENIED_ITEM, DENIED_REIATSU, COOLDOWN, RATE_LIMIT,
	/** A release was requested while the sword is still in the scabbard (draw it first). */
	DENIED_NOT_DRAWN,
	/** Shunpo refused because the path ahead is blocked or has no safe landing (no cost, no cooldown). */
	BLOCKED;

	public byte code() {
		return (byte) ordinal();
	}

	public static ResultCode fromCode(byte code) {
		ResultCode[] v = values();
		return code >= 0 && code < v.length ? v[code] : DENIED_STATE;
	}
}
