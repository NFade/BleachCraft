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

	private EffectIds() {
	}

	public static int shikaiRelease(CharacterId c) {
		return c == CharacterId.BYAKUYA ? BYAKUYA_SHIKAI_RELEASE : RUKIA_SHIKAI_RELEASE;
	}

	public static int bankaiRelease(CharacterId c) {
		return c == CharacterId.BYAKUYA ? BYAKUYA_BANKAI_RELEASE : RUKIA_BANKAI_RELEASE;
	}
}
