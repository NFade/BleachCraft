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
 * The Better Combat idle stance of the katana preset is off for every zanpakuto ({@link #poseSuppressed}); the third person pose
 * layer draws the stance instead.
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

	/**
	 * True when the Better Combat idle stance (the katana pose) must NOT be shown. tp_pose (2026-10-11): always for a zanpakuto in
	 * the main hand. The stance of our swords is drawn by the third person pose layer ({@code client/model/TpPoseLayer}, solver
	 * {@code TpPose}): sheathed sword on the hip, the draw, the upright two handed hold, the block across the chest, all from one
	 * source, so the stance of Better Combat would only fight it. The state and the block flag are kept for the call sites and the
	 * tests, they no longer matter. Anything that is not a zanpakuto keeps whatever Better Combat decides.
	 */
	public static boolean poseSuppressed(boolean holdsZanpakuto, ZanpakutoState state, boolean blocking) {
		return holdsZanpakuto;
	}
}
