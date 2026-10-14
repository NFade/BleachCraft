package dev.minebleach.reiatsutest.core.kido;

import dev.minebleach.reiatsutest.core.state.CharacterId;

/**
 * Who may cast kido. Uryu is a Quincy and has none (for now; what takes its place is open): not with his cross in hand and not
 * while his state is active. Pure rule shared by the server cast path, the client key, the kido HUD cells and the voice gate.
 * Every other character, and a player with no zanpakuto, keeps the three spells.
 */
public final class KidoAccess {
	private KidoAccess() {
	}

	/** True when a player with this character (held item or released character) may cast kido. */
	public static boolean allowed(CharacterId character) {
		return character != CharacterId.URYU;
	}

	/** True when neither the held item's character nor the released character is a kido-less one. */
	public static boolean allowed(CharacterId held, CharacterId released) {
		return allowed(held) && allowed(released);
	}

	/** True for the voice command ids of the kido ({@code kido.byakurai}, ...). */
	public static boolean isKidoCommand(String commandId) {
		return commandId != null && commandId.startsWith("kido.");
	}
}
