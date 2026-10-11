package dev.minebleach.reiatsutest.core.kido;

/**
 * The kido spells (B5). Unlike {@link dev.minebleach.reiatsutest.core.state.AbilityId} they belong to no zanpakuto and no
 * state: any living player can cast them, sealed, drawn or released, with any sword or with empty hands. The byte code is
 * shared with the {@code cast_kido} payload and with the cooldown sync (it must not collide with an ability code, which are
 * 1 to 9), the command id is the one of design/VOICE_PHRASES.md.
 */
public enum KidoId {
	/** Hado no 4, Byakurai: a thin white lightning beam, hitscan. */
	BYAKURAI(101, "kido.byakurai", "byakurai", 0, 50),
	/** Hado no 31, Shakkaho: a red fireball that flies to the first thing it meets and bursts there. */
	SHAKKAHO(102, "kido.shakkaho", "shakkaho", 1, 51),
	/** Bakudo no 1, Sai: a short range binding, the target cannot move for a moment. */
	SAI(103, "kido.sai", "sai", 2, 52);

	public final byte code;
	public final String commandId;
	/** Short lower case name: config key, lang key suffix. */
	public final String key;
	/** Key slot 0..2 (the three kido keys). */
	public final int slot;
	/** Id of the {@code effect_event} this spell broadcasts. */
	public final int effectId;

	KidoId(int code, String commandId, String key, int slot, int effectId) {
		this.code = (byte) code;
		this.commandId = commandId;
		this.key = key;
		this.slot = slot;
		this.effectId = effectId;
	}

	/** Returns null for unknown codes (malformed packets). */
	public static KidoId fromCode(byte code) {
		for (KidoId k : values()) {
			if (k.code == code) {
				return k;
			}
		}
		return null;
	}

	public static KidoId fromCommandId(String commandId) {
		for (KidoId k : values()) {
			if (k.commandId.equals(commandId)) {
				return k;
			}
		}
		return null;
	}

	public static KidoId forSlot(int slot) {
		for (KidoId k : values()) {
			if (k.slot == slot) {
				return k;
			}
		}
		return null;
	}

	public static KidoId fromEffectId(int effectId) {
		for (KidoId k : values()) {
			if (k.effectId == effectId) {
				return k;
			}
		}
		return null;
	}
}
