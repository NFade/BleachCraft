package dev.minebleach.reiatsutest.core.state;

/** Which zanpakuto the player has released. NONE while SEALED (the character is derived from the held item). */
public enum CharacterId {
	NONE(0), RUKIA(1), BYAKUYA(2), GIN(3), URYU(4);

	public final byte code;

	CharacterId(int code) {
		this.code = (byte) code;
	}

	/**
	 * Lower case name used in lang keys and voice item tags ("rukia", "byakuya", "gin", "uryu"); NONE keeps the old fallback of the
	 * HUD ternaries and answers "rukia".
	 */
	public String langKey() {
		return this == NONE ? "rukia" : name().toLowerCase(java.util.Locale.ROOT);
	}

	public static CharacterId fromCode(byte code) {
		for (CharacterId c : values()) {
			if (c.code == code) {
				return c;
			}
		}
		return NONE;
	}
}
