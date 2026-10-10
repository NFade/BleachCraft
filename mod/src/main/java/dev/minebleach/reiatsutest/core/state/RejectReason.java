package dev.minebleach.reiatsutest.core.state;

public enum RejectReason {
	NOT_IN_STATE(ResultCode.DENIED_STATE),
	/** Shikai requested from SEALED: the sword must be drawn (BASE) first. */
	NOT_DRAWN(ResultCode.DENIED_NOT_DRAWN),
	WRONG_ITEM(ResultCode.DENIED_ITEM),
	NOT_ENOUGH_REIATSU(ResultCode.DENIED_REIATSU),
	BANKAI_NOT_FULL(ResultCode.DENIED_REIATSU),
	ON_COOLDOWN(ResultCode.COOLDOWN),
	RELEASE_LOCK(ResultCode.COOLDOWN),
	TRANSITION_LOCK(ResultCode.COOLDOWN),
	GCD(ResultCode.COOLDOWN),
	SETTLE_LOCK(ResultCode.COOLDOWN),
	RATE_LIMITED(ResultCode.RATE_LIMIT),
	/** A replayed or out-of-order clientSeq (transport duplicate). Silent, like the rate limit. */
	STALE_SEQ(ResultCode.RATE_LIMIT),
	DEAD_OR_SPECTATOR(ResultCode.DENIED_STATE),
	/** Shunpo: the path ahead is shorter than the minimum or has no safe landing. */
	NO_ROOM(ResultCode.BLOCKED);

	private final ResultCode wire;

	RejectReason(ResultCode wire) {
		this.wire = wire;
	}

	public ResultCode toWire() {
		return wire;
	}
}
