package dev.minebleach.reiatsutest.core.state;

/** Voice command ids of the transitions (design/VOICE_PHRASES.md); ability ids live in {@link AbilityId}. */
public final class CommandIds {
	public static final String RUKIA_SHIKAI_RELEASE = "rukia.shikai.release";
	public static final String RUKIA_BANKAI_RELEASE = "rukia.bankai.release";
	public static final String BYAKUYA_SHIKAI_RELEASE = "byakuya.shikai.release";
	public static final String BYAKUYA_BANKAI_RELEASE = "byakuya.bankai.release";
	public static final String GIN_SHIKAI_RELEASE = "gin.shikai.release";
	public static final String GIN_BANKAI_RELEASE = "gin.bankai.release";
	public static final String URYU_SHIKAI_RELEASE = "uryu.shikai.release";
	public static final String URYU_BANKAI_RELEASE = "uryu.bankai.release";
	public static final String SEAL = "common.seal";

	private CommandIds() {
	}

	/** Voice command of a transition; null for BASE (the draw has no voice command, it is the key; a right click blocks since the parry work). */
	public static String forTransition(CharacterId character, ZanpakutoState target) {
		return switch (target) {
			case BASE -> null;
			case SHIKAI -> character == CharacterId.GIN ? GIN_SHIKAI_RELEASE : character == CharacterId.URYU ? URYU_SHIKAI_RELEASE
					: character == CharacterId.BYAKUYA ? BYAKUYA_SHIKAI_RELEASE : RUKIA_SHIKAI_RELEASE;
			case BANKAI -> character == CharacterId.GIN ? GIN_BANKAI_RELEASE : character == CharacterId.URYU ? URYU_BANKAI_RELEASE
					: character == CharacterId.BYAKUYA ? BYAKUYA_BANKAI_RELEASE : RUKIA_BANKAI_RELEASE;
			case SEALED -> SEAL;
		};
	}
}
