package dev.minebleach.reiatsutest.core.voice;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;

/**
 * What the server knows about the player at the moment an utterance arrives (VOICE_PHRASES 5.3): zanpakuto state, the
 * character of the zanpakuto in the main hand and whether a bankai release is possible right now (the Rage gate: bankai needs
 * 80 Rage; the reiatsu bar does not matter any more, mech2 task 3). Commands whose state or item do not fit are not considered at all.
 * {@link #ungated()} is the strictest test mode: every command is considered, but variants that are only valid when a bankai is
 * possible (see {@link PhraseBook.Variant#requiresBankaiReady()}) are not.
 */
public record GateContext(boolean gated, CharacterId held, ZanpakutoState state, boolean bankaiReady) {
	private static final GateContext UNGATED = new GateContext(false, CharacterId.NONE, ZanpakutoState.SEALED, false);

	public static GateContext of(CharacterId held, ZanpakutoState state, boolean bankaiReady) {
		return new GateContext(true, held == null ? CharacterId.NONE : held, state, bankaiReady);
	}

	public static GateContext ungated() {
		return UNGATED;
	}
}
