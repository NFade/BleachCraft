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

	// ---- Gin (60 to 69): release events, then one id per ability (design/GIN.md)
	public static final int GIN_SHIKAI_RELEASE = 60;
	public static final int GIN_BANKAI_RELEASE = 61;
	// ---- Uryu (70 to 79): release events, then one id per ability (design/URYU.md)
	public static final int URYU_SHIKAI_RELEASE = 70;
	public static final int URYU_BANKAI_RELEASE = 71;
	/**
	 * Uryu, S-U4: the KEY PRESS of an ability that is aimed at its release (Licht Regen 76, Sternenstaub 77). The effect event of the ability
	 * itself comes 0.6 s / 0.8 s later, at the release. x y z = the caster's feet, d = his look, params = {pre-aim x, y, z (the point he looks
	 * at now), the effect id of the ability that will follow, seconds until the release}. Visual only; the client may gather flechettes.
	 */
	public static final int URYU_CAST_START = 78;

	private EffectIds() {
	}

	public static int shikaiRelease(CharacterId c) {
		return c == CharacterId.GIN ? GIN_SHIKAI_RELEASE : c == CharacterId.URYU ? URYU_SHIKAI_RELEASE
				: c == CharacterId.BYAKUYA ? BYAKUYA_SHIKAI_RELEASE : RUKIA_SHIKAI_RELEASE;
	}

	public static int bankaiRelease(CharacterId c) {
		return c == CharacterId.GIN ? GIN_BANKAI_RELEASE : c == CharacterId.URYU ? URYU_BANKAI_RELEASE
				: c == CharacterId.BYAKUYA ? BYAKUYA_BANKAI_RELEASE : RUKIA_BANKAI_RELEASE;
	}
}
