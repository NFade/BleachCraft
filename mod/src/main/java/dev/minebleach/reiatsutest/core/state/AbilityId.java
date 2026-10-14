package dev.minebleach.reiatsutest.core.state;

/**
 * Abilities (STATE_MACHINE section 4). The byte code is shared with the {@code cast_ability} payload; the command id is
 * the one of design/VOICE_PHRASES.md.
 */
public enum AbilityId {
	TSUKISHIRO(1, "rukia.shikai.tsukishiro", CharacterId.RUKIA, ZanpakutoState.SHIKAI),
	HAKUREN(2, "rukia.shikai.hakuren", CharacterId.RUKIA, ZanpakutoState.SHIKAI),
	SHIRAFUNE(3, "rukia.shikai.shirafune", CharacterId.RUKIA, ZanpakutoState.SHIKAI),
	MODE_ATTACK(5, "byakuya.shikai.mode_attack", CharacterId.BYAKUYA, ZanpakutoState.SHIKAI),
	MODE_BARRIER(6, "byakuya.shikai.mode_barrier", CharacterId.BYAKUYA, ZanpakutoState.SHIKAI),
	SCATTER(7, "byakuya.bankai.scatter", CharacterId.BYAKUYA, ZanpakutoState.BANKAI),
	HAKUTEIKEN(8, "byakuya.bankai.hakuteiken", CharacterId.BYAKUYA, ZanpakutoState.BANKAI),
	SENKEI(9, "byakuya.bankai.senkei", CharacterId.BYAKUYA, ZanpakutoState.BANKAI),
	/**
	 * Rukia bankai, first slot: a wall of ice where she looks (R11 T5, variant W; it was a thrust of ice spikes, the byte code 10 is unchanged).
	 * Her second bankai slot is empty since mech2 task 4: Absolute Zero was the same effect as Tsukishiro and is replaced by the passive frost
	 * territory (not an ability: {@code server/BankaiField}). Code 4 stays unused.
	 */
	ICE_WALL(10, "rukia.bankai.ice_wall", CharacterId.RUKIA, ZanpakutoState.BANKAI),
	/** Byakuya shikai, third slot: the petal cage around the aimed target or point (round 6). */
	CAGE(11, "byakuya.shikai.cage", CharacterId.BYAKUYA, ZanpakutoState.SHIKAI);

	public final byte code;
	public final String commandId;
	public final CharacterId character;
	public final ZanpakutoState requiredState;

	AbilityId(int code, String commandId, CharacterId character, ZanpakutoState requiredState) {
		this.code = (byte) code;
		this.commandId = commandId;
		this.character = character;
		this.requiredState = requiredState;
	}

	/** Returns null for unknown codes (malformed packets). */
	public static AbilityId fromCode(byte code) {
		for (AbilityId a : values()) {
			if (a.code == code) {
				return a;
			}
		}
		return null;
	}

	public static AbilityId fromCommandId(String commandId) {
		for (AbilityId a : values()) {
			if (a.commandId.equals(commandId)) {
				return a;
			}
		}
		return null;
	}

	/** Ability slot (0..2) of this ability for its character and state: Z / H / B. */
	public int slot() {
		return switch (this) {
			case TSUKISHIRO, ICE_WALL, MODE_ATTACK, SCATTER -> 0;
			case HAKUREN, MODE_BARRIER, HAKUTEIKEN -> 1;
			case SHIRAFUNE, SENKEI, CAGE -> 2;
		};
	}

	/** Resolves slot + character + state to an ability (client side key mapping); null when the slot is empty. */
	public static AbilityId forSlot(CharacterId character, ZanpakutoState state, int slot) {
		for (AbilityId a : values()) {
			if (a.character == character && a.requiredState == state && a.slot() == slot) {
				return a;
			}
		}
		return null;
	}
}
