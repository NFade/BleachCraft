package dev.minebleach.reiatsutest.core.state;

/** Voice command ids of the transitions (design/VOICE_PHRASES.md); ability ids live in {@link AbilityId}. */
public final class CommandIds {
	public static final String RUKIA_SHIKAI_RELEASE = "rukia.shikai.release";
	public static final String RUKIA_BANKAI_RELEASE = "rukia.bankai.release";
	public static final String BYAKUYA_SHIKAI_RELEASE = "byakuya.shikai.release";
	public static final String BYAKUYA_BANKAI_RELEASE = "byakuya.bankai.release";
	public static final String SEAL = "common.seal";

	private CommandIds() {
	}

	/** Voice command of a transition; null for BASE (the draw has no voice command, it is the key or a right click). */
	public static String forTransition(CharacterId character, ZanpakutoState target) {
		return switch (target) {
			case BASE -> null;
			case SHIKAI -> character == CharacterId.BYAKUYA ? BYAKUYA_SHIKAI_RELEASE : RUKIA_SHIKAI_RELEASE;
			case BANKAI -> character == CharacterId.BYAKUYA ? BYAKUYA_BANKAI_RELEASE : RUKIA_BANKAI_RELEASE;
			case SEALED -> SEAL;
		};
	}
}
