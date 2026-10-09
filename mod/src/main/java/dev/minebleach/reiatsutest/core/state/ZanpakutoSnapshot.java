package dev.minebleach.reiatsutest.core.state;

/** 1:1 with the synced {@code reiatsu_test:zanpakuto} attachment record (ADR section 3). */
public record ZanpakutoSnapshot(CharacterId character, ZanpakutoState state, long stateSinceTick,
		ShikaiMode shikaiMode, long bankaiEndTick) {
	public static ZanpakutoSnapshot sealed() {
		return new ZanpakutoSnapshot(CharacterId.NONE, ZanpakutoState.SEALED, 0L, ShikaiMode.IDLE, 0L);
	}
}
