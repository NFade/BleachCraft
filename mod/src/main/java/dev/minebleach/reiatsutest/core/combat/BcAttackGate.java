package dev.minebleach.reiatsutest.core.combat;

import dev.minebleach.reiatsutest.core.state.ZanpakutoState;

/**
 * When the Better Combat strike of a zanpakuto is switched off (BC5 decision 1). Pure rule; the server glue
 * ({@code server/BcCombatGate}) feeds it the player facts and writes the result into Better Combat's API flag.
 *
 * <ul>
 * <li>Sword sheathed (SEALED, the sword sits in the hip scabbard, the hand is empty): no BC strike. The player stands as usual.</li>
 * <li>Block held: no BC strike ({@link BlockRules#allowsAttack}); this also covers the server side, which BC checks only through the flag.</li>
 * <li>Drawn (BASE, SHIKAI, BANKAI) and not blocking: the BC strike works.</li>
 * <li>Anything that is not a zanpakuto in the main hand: the flag is not ours to set, so it is cleared.</li>
 * </ul>
 */
public final class BcAttackGate {
	private BcAttackGate() {
	}

	public static boolean attacksDisabled(boolean holdsZanpakuto, ZanpakutoState state, boolean blocking) {
		if (!holdsZanpakuto) {
			return false;
		}
		return state == ZanpakutoState.SEALED || !BlockRules.allowsAttack(blocking);
	}
}
