package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;

/**
 * Life rules of the persistent field anchor of the Rukia bankai (VFX_STORYBOARD 5.1 / 5.2): an anchor of kind FIELD with
 * {@code p0 = FIELD_RUKIA_BANKAI} is created at the release point when effect 2 is broadcast; it carries the crystals and the
 * frost ground that stay where the bankai was released, and it ends as soon as its owner is no longer in the Rukia bankai.
 */
public final class FieldAnchorPolicy {
	/** {@code p0} of a FIELD anchor: the standing frost field of a Rukia bankai. */
	public static final float FIELD_RUKIA_BANKAI = 2.0f;
	/** The owner state is checked every this many server ticks. */
	public static final int CHECK_INTERVAL_TICKS = 10;

	private FieldAnchorPolicy() {
	}

	/** Effect ids that create a field anchor. */
	public static boolean spawnsOn(int effectId) {
		return effectId == EffectIds.RUKIA_BANKAI_RELEASE;
	}

	/** True while the anchor of that owner has to stay. */
	public static boolean keep(boolean ownerAlive, boolean ownerOnline, CharacterId character, ZanpakutoState state) {
		return ownerAlive && ownerOnline && character == CharacterId.RUKIA && state == ZanpakutoState.BANKAI;
	}

	/** True when a check is due at this anchor age (every {@link #CHECK_INTERVAL_TICKS}). */
	public static boolean due(int ageTicks) {
		return ageTicks > 0 && ageTicks % CHECK_INTERVAL_TICKS == 0;
	}
}
