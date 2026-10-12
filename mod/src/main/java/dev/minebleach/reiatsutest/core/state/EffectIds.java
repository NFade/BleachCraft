package dev.minebleach.reiatsutest.core.state;

/** Effect ids broadcast in {@code effect_event} (STATE_MACHINE sections 2 and 4). Abilities carry theirs in AbilitySpec. */
public final class EffectIds {
	public static final int RUKIA_SHIKAI_RELEASE = 1;
	public static final int RUKIA_BANKAI_RELEASE = 2;
	public static final int BYAKUYA_SHIKAI_RELEASE = 3;
	public static final int BYAKUYA_BANKAI_RELEASE = 4;
	public static final int SEAL = 10;
	public static final int BANKAI_END = 11;
	/** Shunpo (B4 step 5): x y z = start, dx dy dz = vector to the end, params = {distance, character code}. */
	public static final int SHUNPO = 40;
	/**
	 * Block feedback (BC5b): sent to the defender and the players tracking them when a zanpakuto block takes a melee hit. x y z = the
	 * defender, dx dz = horizontal vector from the defender to the attacker (dy = 0), target = id of the attacker, params =
	 * {1 parried or 0 blocked with reduced damage, damage the hit would have dealt}.
	 */
	public static final int BLOCK_FEEDBACK = 41;

	/**
	 * Territory freeze of the Rukia bankai (round 6), sent when the field starts: x y z = the centre (her feet), params =
	 * {radius, wave speed in blocks per second, maximum duration in ticks}. The client runs the ring of snow, mist and crackling.
	 */
	public static final int FROST_FIELD = 42;
	/** The field is thawing (the bankai ended): x y z = the centre, params = {radius, thaw speed in blocks per second}. */
	public static final int FROST_THAW = 43;

	/**
	 * The bankai of the caster is over (mech2 task 2), sent to the caster and everybody tracking him by the ONE server cleanup that every
	 * end reason runs (timer, seal, death, logout, dimension change, dev reset). x y z = where he stands, params = {trigger ordinal,
	 * character code}. Clients drop every bankai effect of that owner at once ({@code client/fx/BankaiEnd}).
	 */
	public static final int BANKAI_CLEANUP = 44;

	private EffectIds() {
	}

	public static int shikaiRelease(CharacterId c) {
		return c == CharacterId.BYAKUYA ? BYAKUYA_SHIKAI_RELEASE : RUKIA_SHIKAI_RELEASE;
	}

	public static int bankaiRelease(CharacterId c) {
		return c == CharacterId.BYAKUYA ? BYAKUYA_BANKAI_RELEASE : RUKIA_BANKAI_RELEASE;
	}
}
