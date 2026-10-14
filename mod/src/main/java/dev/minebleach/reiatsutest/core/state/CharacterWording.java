package dev.minebleach.reiatsutest.core.state;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per character wording of the shared state words and denial texts (pure lookup, no Minecraft types).
 *
 * <p>Uryu is a Quincy: for him BASE / SHIKAI / BANKAI are shown as Heilig Bogen / Blut Arterie / Quincy: Vollstaendig and the sword is
 * a bow. The internal names ({@link ZanpakutoState}, ability ids, command ids) never change; only the lang key a screen asks for does.
 * {@code key(base, character)} answers {@code base + "." + character.langKey()} when that character has a variant of the key and
 * the base key itself otherwise, so the strings of the other characters stay as they are. Every variant listed here must exist in
 * both lang files ({@code CharacterWordingTest} checks it).
 */
public final class CharacterWording {
	private static final String H = "hud.reiatsu_test.";
	private static final String M = "message.reiatsu_test.";

	/** The base keys that have a "base.uryu" variant in the lang files. */
	private static final List<String> QUINCY = List.of(
			H + "state.base", H + "state.shikai", H + "state.bankai",
			H + "hint.draw", H + "hint.release", H + "hint.draw.short", H + "hint.release.short",
			H + "bankai_ended", H + "bankai_kanji", H + "shikai_time",
			H + "denied.bankai_needs_shikai", H + "denied.not_in.shikai", H + "denied.not_in.bankai", H + "denied.item",
			M + "denied.item", M + "denied.not_drawn", M + "denied.shikai_time",
			M + "voice.cmd.shikai", M + "voice.cmd.bankai");

	private static final Map<CharacterId, Set<String>> VARIANTS = Map.of(CharacterId.URYU, Set.copyOf(QUINCY));

	private CharacterWording() {
	}

	/** The lang key to use for {@code baseKey} while {@code who} is the character; the base key when who has no variant of it. */
	public static String key(String baseKey, CharacterId who) {
		Set<String> v = who == null ? null : VARIANTS.get(who);
		return v != null && v.contains(baseKey) ? baseKey + "." + who.langKey() : baseKey;
	}

	/** The lang key of the word for a state ("hud.reiatsu_test.state.shikai"), in the wording of the character. */
	public static String stateKey(CharacterId who, ZanpakutoState state) {
		return key(H + "state." + state.name().toLowerCase(java.util.Locale.ROOT), who);
	}

	/** Characters that have variants. */
	public static Set<CharacterId> characters() {
		return VARIANTS.keySet();
	}

	/** The base keys {@code who} has a variant of (empty for the others). */
	public static Set<String> variantsOf(CharacterId who) {
		Set<String> v = VARIANTS.get(who);
		return v == null ? Set.of() : v;
	}
}
