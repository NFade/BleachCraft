package dev.minebleach.reiatsutest.core.book;

import dev.minebleach.reiatsutest.core.state.CharacterId;

/**
 * The spellbooks (B3): one per zanpakuto plus a shared kido book. {@code id} is the item path in the mod namespace and the
 * middle part of the lang keys {@code book.reiatsu_test.<id>.page.N}.
 */
public enum SpellbookId {
	SODE_NO_SHIRAYUKI("book_sode_no_shirayuki", CharacterId.RUKIA),
	SENBONZAKURA("book_senbonzakura", CharacterId.BYAKUYA),
	/** Kido is castable by everyone in every state, so it has no zanpakuto. */
	KIDO("book_kido", CharacterId.NONE);

	public final String id;
	/** Whose zanpakuto this book describes; NONE for the kido book. */
	public final CharacterId character;

	SpellbookId(String id, CharacterId character) {
		this.id = id;
		this.character = character;
	}

	public static SpellbookId fromId(String id) {
		for (SpellbookId b : values()) {
			if (b.id.equals(id)) {
				return b;
			}
		}
		return null;
	}
}
