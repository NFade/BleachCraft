package dev.minebleach.reiatsutest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.book.BookArg;
import dev.minebleach.reiatsutest.core.book.BookFacts;
import dev.minebleach.reiatsutest.core.book.BookPage;
import dev.minebleach.reiatsutest.core.book.BookPager;
import dev.minebleach.reiatsutest.core.book.SpellbookCatalog;
import dev.minebleach.reiatsutest.core.book.SpellbookCatalog.Availability;
import dev.minebleach.reiatsutest.core.book.SpellbookId;
import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Spellbooks (B3): phrase coverage, lang keys, placeholders, pagination and availability. */
class SpellbookTest {
	private static final Path ASSETS = Paths.get("src", "main", "resources", "assets", "reiatsu_test");
	private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+)\\$s");
	private static final Pattern JAPANESE = Pattern.compile("[\\u3040-\\u30FF\\u4E00-\\u9FFF]");

	private static JsonObject json(String relative) throws IOException {
		return JsonParser.parseString(Files.readString(ASSETS.resolve(relative), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static Map<String, String> lang(String file) throws IOException {
		Map<String, String> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : json("lang/" + file).entrySet()) {
			out.put(e.getKey(), e.getValue().getAsString());
		}
		return out;
	}

	/** id to {say, states} of the commands of the phrase table. */
	private static Map<String, JsonObject> phrases() throws IOException {
		Map<String, JsonObject> out = new HashMap<>();
		JsonArray cmds = json("voice/phrases.json").getAsJsonArray("commands");
		for (JsonElement e : cmds) {
			out.put(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject());
		}
		return out;
	}

	@Test
	void everyPhraseCommandIsInSomeBookAndBack() throws IOException {
		Set<String> table = new TreeSet<>(phrases().keySet());
		table.removeIf(id -> id.startsWith("gin.") || id.startsWith("uryu.")); // gin-uryu: their books come with the gameplay steps
		Set<String> books = SpellbookCatalog.allCommandIds();
		Set<String> missing = new TreeSet<>(table);
		missing.removeAll(books);
		Set<String> unknown = new TreeSet<>(books);
		unknown.removeAll(table);
		assertTrue(missing.isEmpty(), "phrases.json commands not in any book: " + missing);
		assertTrue(unknown.isEmpty(), "book commands not in phrases.json: " + unknown);
	}

	@Test
	void pageStatesMatchThePhraseTable() throws IOException {
		Map<String, JsonObject> table = phrases();
		for (SpellbookId b : SpellbookId.values()) {
			for (BookPage p : SpellbookCatalog.pages(b)) {
				for (String id : p.commandIds()) {
					Set<ZanpakutoState> expected = new HashSet<>();
					for (JsonElement s : table.get(id).getAsJsonArray("states")) {
						expected.add(ZanpakutoState.valueOf(s.getAsString()));
					}
					assertEquals(expected, p.states(), b.id + " / " + id + ": states differ from phrases.json");
				}
			}
		}
	}

	@Test
	void everyPageKeyExistsInBothLanguagesAndNoPageKeyIsOrphaned() throws IOException {
		for (String file : new String[] {"en_us.json", "ru_ru.json"}) {
			Map<String, String> l = lang(file);
			Set<String> expected = new HashSet<>();
			for (SpellbookId b : SpellbookId.values()) {
				assertNotNull(l.get(SpellbookCatalog.itemKey(b)), file + ": item name of " + b.id);
				for (int i = 0; i < SpellbookCatalog.pageCount(b); i++) {
					String body = SpellbookCatalog.pageKey(b, i);
					String title = SpellbookCatalog.titleKey(b, i);
					assertNotNull(l.get(body), file + ": missing " + body);
					assertNotNull(l.get(title), file + ": missing " + title);
					assertFalse(l.get(body).isBlank(), file + ": empty " + body);
					assertFalse(l.get(title).isBlank(), file + ": empty " + title);
					expected.add(body);
					expected.add(title);
				}
			}
			Set<String> orphans = new TreeSet<>();
			for (String k : l.keySet()) {
				if (k.startsWith("book.reiatsu_test.book_") && !expected.contains(k)) {
					orphans.add(k);
				}
			}
			assertTrue(orphans.isEmpty(), file + ": page keys without a catalog page: " + orphans);
			for (String k : new String[] {"itemGroup.reiatsu_test", "tooltip.reiatsu_test.book.open",
					"tooltip.reiatsu_test.book.numbers", "book.reiatsu_test.works_in", "book.reiatsu_test.any_state",
					"book.reiatsu_test.locked.state", "book.reiatsu_test.locked.character"}) {
				assertNotNull(l.get(k), file + ": missing " + k);
			}
			for (ZanpakutoState s : ZanpakutoState.values()) {
				assertNotNull(l.get(SpellbookCatalog.stateKey(s)), file + ": state name " + s);
			}
		}
	}

	@Test
	void placeholdersMatchTheArguments() throws IOException {
		for (String file : new String[] {"en_us.json", "ru_ru.json"}) {
			Map<String, String> l = lang(file);
			for (SpellbookId b : SpellbookId.values()) {
				for (int i = 0; i < SpellbookCatalog.pages(b).size(); i++) {
					BookPage p = SpellbookCatalog.pages(b).get(i);
					Matcher m = PLACEHOLDER.matcher(l.get(SpellbookCatalog.pageKey(b, i)));
					Set<Integer> used = new HashSet<>();
					while (m.find()) {
						used.add(Integer.parseInt(m.group(1)));
					}
					String where = file + " " + SpellbookCatalog.pageKey(b, i);
					for (int n : used) {
						assertTrue(n >= 1 && n <= p.args().size(), where + ": %" + n + "$s has no argument");
					}
					// An argument that the text never shows would be a dead value: every one is used at least once.
					for (int n = 1; n <= p.args().size(); n++) {
						assertTrue(used.contains(n), where + ": argument " + n + " is not used");
					}
				}
			}
		}
	}

	@Test
	void everyNumericArgumentResolvesAndKeysAreNamed() throws IOException {
		BalanceConfig balance = BalanceConfig.defaults();
		KidoConfig kido = KidoConfig.defaults();
		Map<String, String> en = lang("en_us.json");
		for (SpellbookId b : SpellbookId.values()) {
			for (BookPage p : SpellbookCatalog.pages(b)) {
				for (BookArg a : p.args()) {
					if (a.kind() == BookArg.Kind.KEY) {
						assertNotNull(en.get(a.ref()), "key binding name " + a.ref());
					} else {
						assertNotNull(BookFacts.resolve(a, balance, kido), b.id + ": cannot resolve " + a);
					}
				}
			}
		}
		assertEquals("25", BookFacts.tenths(250));
		assertEquals("1.5", BookFacts.tenths(15));
		assertEquals("1.25", BookFacts.number(1.25), "r8: Byakurai deals 1.25 hp, the book must not round it to 1.3");
		assertEquals("2", BookFacts.number(2.0));
		assertEquals("12", BookFacts.ticksToSeconds(240));
		assertEquals("7.5", BookFacts.ticksToSeconds(150));
	}

	@Test
	void everyCommandPageShowsItsPhraseAndJapaneseWriting() throws IOException {
		Map<String, JsonObject> table = phrases();
		for (String file : new String[] {"en_us.json", "ru_ru.json"}) {
			Map<String, String> l = lang(file);
			for (SpellbookId b : SpellbookId.values()) {
				for (int i = 0; i < SpellbookCatalog.pages(b).size(); i++) {
					BookPage p = SpellbookCatalog.pages(b).get(i);
					String text = l.get(SpellbookCatalog.pageKey(b, i));
					for (String id : p.commandIds()) {
						String say = table.get(id).get("say").getAsString();
						assertTrue(text.toLowerCase().contains(say.toLowerCase()),
								file + " " + SpellbookCatalog.pageKey(b, i) + " does not show the phrase \"" + say + "\"");
						assertTrue(JAPANESE.matcher(text).find(),
								file + " " + SpellbookCatalog.pageKey(b, i) + " has no Japanese writing of the phrase");
					}
				}
			}
		}
	}

	@Test
	void bookStructure() {
		assertEquals(3, SpellbookId.values().length);
		for (SpellbookId b : SpellbookId.values()) {
			assertEquals(b, SpellbookId.fromId(b.id));
			assertTrue(SpellbookCatalog.pageCount(b) >= 2);
			// The first page of every book is an introduction.
			assertFalse(SpellbookCatalog.pages(b).get(0).hasCommands());
		}
		assertEquals(Set.of("rukia.shikai.release"), Set.copyOf(SpellbookCatalog.pages(SpellbookId.SODE_NO_SHIRAYUKI).get(1)
				.commandIds()));
		// Both zanpakuto books and the kido book carry the seal / kido ids they should.
		Set<String> all = SpellbookCatalog.allCommandIds();
		assertTrue(all.contains("kido.byakurai") && all.contains("kido.shakkaho") && all.contains("kido.sai"));
		assertTrue(all.contains("common.seal"));
	}

	@Test
	void pagerIsClamped() {
		assertEquals(0, BookPager.clamp(-3, 5));
		assertEquals(4, BookPager.clamp(9, 5));
		assertEquals(0, BookPager.clamp(3, 0));
		assertEquals(1, BookPager.next(0, 5));
		assertEquals(4, BookPager.next(4, 5));
		assertEquals(0, BookPager.prev(0, 5));
		assertEquals(2, BookPager.prev(3, 5));
		assertTrue(BookPager.hasNext(3, 5));
		assertFalse(BookPager.hasNext(4, 5));
		assertFalse(BookPager.hasPrev(0, 5));
		assertTrue(BookPager.hasPrev(1, 5));
		assertFalse(BookPager.hasPrev(0, 0));
		// Walking forward then back through every book visits every page once and stays in range.
		for (SpellbookId b : SpellbookId.values()) {
			int n = SpellbookCatalog.pageCount(b);
			int page = 0;
			for (int i = 0; i < n + 3; i++) {
				page = BookPager.next(page, n);
				assertTrue(page >= 0 && page < n);
			}
			assertEquals(n - 1, page);
			for (int i = 0; i < n + 3; i++) {
				page = BookPager.prev(page, n);
				assertTrue(page >= 0 && page < n);
			}
			assertEquals(0, page);
		}
	}

	@Test
	void openingPageFollowsTheState() {
		SpellbookId rukia = SpellbookId.SODE_NO_SHIRAYUKI;
		assertEquals(0, SpellbookCatalog.openingPage(rukia, CharacterId.NONE, ZanpakutoState.SEALED));
		assertEquals(1, SpellbookCatalog.openingPage(rukia, CharacterId.RUKIA, ZanpakutoState.BASE));
		assertEquals(2, SpellbookCatalog.openingPage(rukia, CharacterId.RUKIA, ZanpakutoState.SHIKAI));
		assertEquals(6, SpellbookCatalog.openingPage(rukia, CharacterId.RUKIA, ZanpakutoState.BANKAI));
		// The other zanpakuto is released: the book opens at the start.
		assertEquals(0, SpellbookCatalog.openingPage(rukia, CharacterId.BYAKUYA, ZanpakutoState.SHIKAI));
		// Kido works for everyone: it opens at the state page whoever the player is.
		assertEquals(1, SpellbookCatalog.openingPage(SpellbookId.KIDO, CharacterId.BYAKUYA, ZanpakutoState.SHIKAI));
		for (SpellbookId b : SpellbookId.values()) {
			for (CharacterId c : CharacterId.values()) {
				for (ZanpakutoState s : ZanpakutoState.values()) {
					int page = SpellbookCatalog.openingPage(b, c, s);
					assertEquals(page, BookPager.clamp(page, SpellbookCatalog.pageCount(b)));
				}
			}
		}
	}

	@Test
	void availabilityMutesPagesOfOtherStates() {
		SpellbookId rukia = SpellbookId.SODE_NO_SHIRAYUKI;
		// The introduction is always readable.
		assertEquals(Availability.AVAILABLE, SpellbookCatalog.availability(rukia, 0, CharacterId.RUKIA, ZanpakutoState.BANKAI));
		// Page 3 (Tsukishiro) needs shikai.
		assertEquals(Availability.AVAILABLE, SpellbookCatalog.availability(rukia, 2, CharacterId.RUKIA, ZanpakutoState.SHIKAI));
		assertEquals(Availability.WRONG_STATE, SpellbookCatalog.availability(rukia, 2, CharacterId.RUKIA, ZanpakutoState.BASE));
		assertEquals(Availability.WRONG_STATE, SpellbookCatalog.availability(rukia, 2, CharacterId.NONE, ZanpakutoState.SEALED));
		// The other zanpakuto is drawn or released.
		assertEquals(Availability.WRONG_ZANPAKUTO,
				SpellbookCatalog.availability(rukia, 2, CharacterId.BYAKUYA, ZanpakutoState.SHIKAI));
		// Kido is available in every state with any zanpakuto.
		for (int i = 0; i < SpellbookCatalog.pageCount(SpellbookId.KIDO); i++) {
			for (ZanpakutoState s : ZanpakutoState.values()) {
				CharacterId c = s == ZanpakutoState.SEALED ? CharacterId.NONE : CharacterId.BYAKUYA;
				assertEquals(Availability.AVAILABLE, SpellbookCatalog.availability(SpellbookId.KIDO, i, c, s));
			}
		}
	}
}
