package dev.minebleach.reiatsutest.core.state;

/** Which zanpakuto the player has released. NONE while SEALED (the character is derived from the held item). */
public enum CharacterId {
	NONE(0), RUKIA(1), BYAKUYA(2);

	public final byte code;

	CharacterId(int code) {
		this.code = (byte) code;
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
