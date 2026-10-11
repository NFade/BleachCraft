package dev.minebleach.reiatsutest.core.book;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.CommandIds;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structure of the spellbooks (B3): which page describes which command, in which states those commands work and which
 * values the page text needs. The texts themselves are in the lang files under {@code book.reiatsu_test.<id>.page.N}
 * (body) and {@code ...page.N.title} (N is 1 based). Pure logic, no Minecraft.
 */
public final class SpellbookCatalog {
	private static final String K_DRAW = "key.reiatsu_test.draw";
	private static final String K_RELEASE = "key.reiatsu_test.release";
	private static final String K_BANKAI = "key.reiatsu_test.bankai";
	private static final String K_SEAL = "key.reiatsu_test.seal";
	private static final String[] K_ABILITY = {"key.reiatsu_test.ability_1", "key.reiatsu_test.ability_2",
			"key.reiatsu_test.ability_3"};
	private static final String[] K_BANKAI_ABILITY = {"key.reiatsu_test.bankai_ability_1", "key.reiatsu_test.bankai_ability_2",
			"key.reiatsu_test.bankai_ability_3"};
	private static final String[] K_KIDO = {"key.reiatsu_test.kido_1", "key.reiatsu_test.kido_2", "key.reiatsu_test.kido_3"};

	private static final Set<ZanpakutoState> SEALED = Set.of(ZanpakutoState.SEALED);
	private static final Set<ZanpakutoState> BASE = Set.of(ZanpakutoState.BASE);
	private static final Set<ZanpakutoState> SHIKAI = Set.of(ZanpakutoState.SHIKAI);
	private static final Set<ZanpakutoState> BANKAI = Set.of(ZanpakutoState.BANKAI);
	private static final Set<ZanpakutoState> DRAWN = Set.of(ZanpakutoState.BASE, ZanpakutoState.SHIKAI, ZanpakutoState.BANKAI);
	private static final Set<ZanpakutoState> ANY = Set.of(ZanpakutoState.values());

	private static final Map<SpellbookId, List<BookPage>> BOOKS = new EnumMap<>(SpellbookId.class);

	static {
		BOOKS.put(SpellbookId.SODE_NO_SHIRAYUKI, List.of(
				intro(),
				release(CommandIds.RUKIA_SHIKAI_RELEASE),
				ability(AbilityId.TSUKISHIRO, SHIKAI, K_ABILITY[0]),
				ability(AbilityId.HAKUREN, SHIKAI, K_ABILITY[1]),
				ability(AbilityId.SHIRAFUNE, SHIKAI, K_ABILITY[2]),
				bankaiRelease(CommandIds.RUKIA_BANKAI_RELEASE),
				ability(AbilityId.ABSOLUTE_ZERO, BANKAI, K_BANKAI_ABILITY[0]),
				seal()));
		BOOKS.put(SpellbookId.SENBONZAKURA, List.of(
				intro(),
				release(CommandIds.BYAKUYA_SHIKAI_RELEASE),
				ability(AbilityId.MODE_ATTACK, SHIKAI, K_ABILITY[0]),
				ability(AbilityId.MODE_BARRIER, SHIKAI, K_ABILITY[1]),
				bankaiRelease(CommandIds.BYAKUYA_BANKAI_RELEASE),
				ability(AbilityId.SCATTER, BANKAI, K_BANKAI_ABILITY[0]),
				ability(AbilityId.HAKUTEIKEN, BANKAI, K_BANKAI_ABILITY[1]),
				// Senkei is in the phrase table but disabled in the balance (STATE_MACHINE): a page without numbers.
				new BookPage(List.of(AbilityId.SENKEI.commandId), BANKAI, List.of(BookArg.key(K_BANKAI_ABILITY[2]))),
				seal()));
		List<BookPage> kido = new ArrayList<>();
		kido.add(new BookPage(List.of(), SEALED, List.of(BookArg.key(K_KIDO[0]), BookArg.key(K_KIDO[1]),
				BookArg.key(K_KIDO[2]))));
		for (KidoId k : KidoId.values()) {
			kido.add(new BookPage(List.of(k.commandId), ANY, List.of(BookArg.key(K_KIDO[k.slot]), BookArg.cost(k.commandId),
					BookArg.cooldown(k.commandId), BookArg.kidoRange(k.commandId), BookArg.kidoDamage(k.commandId))));
		}
		BOOKS.put(SpellbookId.KIDO, List.copyOf(kido));
	}

	private SpellbookCatalog() {
	}

	/** Introduction: draw, release, bankai and seal keys. Opened when the player is sheathed. */
	private static BookPage intro() {
		return new BookPage(List.of(), SEALED, List.of(BookArg.key(K_DRAW), BookArg.key(K_RELEASE), BookArg.key(K_BANKAI),
				BookArg.key(K_SEAL)));
	}

	private static BookPage release(String commandId) {
		return new BookPage(List.of(commandId), BASE, List.of(BookArg.key(K_RELEASE), BookArg.releaseCost()));
	}

	private static BookPage bankaiRelease(String commandId) {
		return new BookPage(List.of(commandId), SHIKAI, List.of(BookArg.key(K_BANKAI), BookArg.bankaiSeconds()));
	}

	private static BookPage ability(AbilityId id, Set<ZanpakutoState> states, String keyTranslation) {
		return new BookPage(List.of(id.commandId), states,
				List.of(BookArg.key(keyTranslation), BookArg.cost(id.commandId), BookArg.cooldown(id.commandId)));
	}

	private static BookPage seal() {
		return new BookPage(List.of(CommandIds.SEAL), DRAWN, List.of(BookArg.key(K_SEAL)));
	}

	public static List<BookPage> pages(SpellbookId book) {
		return BOOKS.get(book);
	}

	public static int pageCount(SpellbookId book) {
		return BOOKS.get(book).size();
	}

	/** Lang key of the body of page {@code index} (0 based; the key number is 1 based). */
	public static String pageKey(SpellbookId book, int index) {
		return "book.reiatsu_test." + book.id + ".page." + (index + 1);
	}

	public static String titleKey(SpellbookId book, int index) {
		return pageKey(book, index) + ".title";
	}

	/** Translation key of the item name of the book. */
	public static String itemKey(SpellbookId book) {
		return "item.reiatsu_test." + book.id;
	}

	/**
	 * The page a book opens at for the given state: the first page whose states contain it (so SEALED opens the
	 * introduction, BASE the release page, SHIKAI the first dance / mode, BANKAI the first bankai ability). 0 when none fits.
	 */
	public static int pageForState(SpellbookId book, ZanpakutoState state) {
		List<BookPage> pages = BOOKS.get(book);
		for (int i = 0; i < pages.size(); i++) {
			if (pages.get(i).states().contains(state)) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * Opening page for a player: the state-matching page when the player's released zanpakuto belongs to this book (or the
	 * book is the kido book), otherwise the first page.
	 */
	public static int openingPage(SpellbookId book, CharacterId playerCharacter,
			ZanpakutoState state) {
		boolean mine = book.character == CharacterId.NONE || book.character == playerCharacter
				|| state == ZanpakutoState.SEALED;
		return mine ? pageForState(book, state) : 0;
	}
}
