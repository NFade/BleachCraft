package dev.minebleach.reiatsutest.core.book;

/**
 * One substituted value of a page text ({@code %1$s}, {@code %2$s}, ... in lang order). Numbers are read from the balance
 * configs at display time ({@link BookFacts}) so the book never goes stale; a {@link Kind#KEY} is a translation key of a key
 * binding, shown by the client as the player's current binding.
 */
public record BookArg(Kind kind, String ref) {
	public enum Kind {
		/** Translation key of a key binding, for example {@code key.reiatsu_test.draw}. */
		KEY,
		/** Reiatsu cost of the ability or kido spell with command id {@code ref}. */
		COST,
		/** Cooldown in seconds of the ability or kido spell with command id {@code ref}. */
		COOLDOWN,
		/** Reiatsu cost of the shikai release. */
		RELEASE_COST,
		/** Length of the bankai in seconds. */
		BANKAI_SECONDS,
		/** Reach in blocks of the kido spell with command id {@code ref}. */
		KIDO_RANGE,
		/** Damage in hit points at the centre of the kido spell with command id {@code ref}. */
		KIDO_DAMAGE
	}

	public static BookArg key(String translationKey) {
		return new BookArg(Kind.KEY, translationKey);
	}

	public static BookArg cost(String commandId) {
		return new BookArg(Kind.COST, commandId);
	}

	public static BookArg cooldown(String commandId) {
		return new BookArg(Kind.COOLDOWN, commandId);
	}

	public static BookArg releaseCost() {
		return new BookArg(Kind.RELEASE_COST, "");
	}

	public static BookArg bankaiSeconds() {
		return new BookArg(Kind.BANKAI_SECONDS, "");
	}

	public static BookArg kidoRange(String commandId) {
		return new BookArg(Kind.KIDO_RANGE, commandId);
	}

	public static BookArg kidoDamage(String commandId) {
		return new BookArg(Kind.KIDO_DAMAGE, commandId);
	}
}
