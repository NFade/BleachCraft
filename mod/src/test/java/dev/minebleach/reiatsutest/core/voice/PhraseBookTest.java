package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Schema and consistency of {@code voice/phrases.json}, the generated phrase table (tools/gen_voice_phrases.py). */
class PhraseBookTest {
	private final PhraseBook book = VoiceTestSupport.book();

	@Test
	void hasThe14CommandsOfTheDesign() {
		assertEquals(14, book.commands().size());
		assertEquals(1, book.commands().stream().filter(PhraseBook.Command::optional).count(), "only senkei is optional");
		assertTrue(book.command("byakuya.bankai.senkei").optional());
		assertFalse(book.fillerKeys().isEmpty());
	}

	@Test
	void everyCommandIsWellFormed() {
		Set<String> origins = Set.of("C", "T", "G", "M", "X", "A");
		for (PhraseBook.Command c : book.commands()) {
			assertTrue(c.id().matches("[a-z]+(\\.[a-z_]+){2}") || c.id().equals("common.seal"), c.id());
			assertFalse(c.states().isEmpty(), c.id());
			assertFalse(c.say().isBlank(), c.id());
			assertTrue(c.variants().size() >= 8, c.id() + " has only " + c.variants().size() + " variants");
			Set<String> seen = new HashSet<>();
			for (PhraseBook.Variant v : c.variants()) {
				assertTrue(origins.contains(v.origin()), c.id() + " origin " + v.origin());
				assertFalse(v.key().isEmpty(), v.text());
				assertEquals(v.key(), String.join(" ", v.tokens()));
				assertEquals(v.key().replace(" ", ""), v.compact());
				assertTrue(seen.add(v.text() + "|" + v.requiresFullReiatsu()), c.id() + " duplicate variant " + v.text());
			}
		}
	}

	@Test
	void weakVariantsAreTheOnesTheDesignCallsWeak() {
		// every variant of the seal is whole-utterance only; the lone words "Chire" / "Scatter" too
		for (PhraseBook.Variant v : book.command("common.seal").variants()) {
			assertTrue(v.weak(), "seal variant " + v.text());
		}
		for (String id : new String[] {"byakuya.shikai.release", "byakuya.bankai.scatter"}) {
			for (PhraseBook.Variant v : book.command(id).variants()) {
				boolean lone = v.tokens().size() == 1 && (v.key().equals("cire") || v.key().equals("scater"));
				if (lone) {
					assertTrue(v.weak(), id + " lone word " + v.text());
				}
			}
		}
		// names of the abilities are strong
		assertFalse(book.command("rukia.shikai.hakuren").variants().stream().anyMatch(v -> v.weak() && v.text().equals("Hakuren")));
	}

	@Test
	void contextGatedVariantsExistOnlyForTheBankaiReleases() {
		for (PhraseBook.Command c : book.commands()) {
			long gated = c.variants().stream().filter(PhraseBook.Variant::requiresFullReiatsu).count();
			if (c.id().endsWith(".bankai.release")) {
				assertTrue(gated >= 3, c.id() + " gated aliases " + gated);
				c.variants().stream().filter(PhraseBook.Variant::requiresFullReiatsu)
						.forEach(v -> assertEquals("A", v.origin(), v.text()));
			} else {
				assertEquals(0, gated, c.id());
			}
		}
		assertTrue(book.command("rukia.bankai.release").variants().stream()
				.anyMatch(v -> v.requiresFullReiatsu() && v.key().equals("bank e") && v.weak()));
	}

	@Test
	void statesAndItemsAgreeWithTheStateMachine() {
		for (PhraseBook.Command c : book.commands()) {
			AbilityId a = AbilityId.fromCommandId(c.id());
			if (a != null) {
				assertEquals(Set.of(a.requiredState), c.states(), c.id());
				assertEquals(a.character, c.item(), c.id());
			}
		}
		assertEquals(Set.of(ZanpakutoState.SEALED), book.command("rukia.shikai.release").states());
		assertEquals(Set.of(ZanpakutoState.SHIKAI), book.command("rukia.bankai.release").states());
		assertEquals(Set.of(ZanpakutoState.SHIKAI, ZanpakutoState.BANKAI), book.command("common.seal").states());
		assertEquals(CharacterId.NONE, book.command("common.seal").item());
		assertEquals(CharacterId.RUKIA, book.command("rukia.bankai.release").item());
		assertEquals(CharacterId.BYAKUYA, book.command("byakuya.shikai.release").item());
	}

	@Test
	void earlyOkSetIsExactlyTheCheapCommands() {
		Set<String> safe = new HashSet<>();
		for (PhraseBook.Command c : book.commands()) {
			if (c.interimSafe()) {
				safe.add(c.id());
			}
		}
		assertEquals(Set.of("rukia.shikai.release", "rukia.shikai.tsukishiro", "rukia.shikai.hakuren", "rukia.shikai.shirafune",
				"byakuya.shikai.release", "byakuya.shikai.mode_attack", "byakuya.shikai.mode_barrier", "common.seal"), safe);
	}

	/** The json is generated from the markdown: every phrase of the design tables must be in it (catches a stale json). */
	@Test
	void containsEveryPhraseOfTheDesignDocument() throws IOException {
		Assumptions.assumeTrue(Files.isRegularFile(VoiceTestSupport.VOICE_MD), "design/VOICE_PHRASES.md not reachable");
		Pattern section = Pattern.compile("^### 1\\.\\d+ `([a-z_.]+)`");
		Map<String, Set<String>> expected = new LinkedHashMap<>();
		String current = null;
		for (String line : Files.readAllLines(VoiceTestSupport.VOICE_MD, StandardCharsets.UTF_8)) {
			Matcher m = section.matcher(line);
			if (m.find()) {
				current = m.group(1);
				expected.put(current, new HashSet<>());
				continue;
			}
			if (line.startsWith("## ")) {
				current = null;
			}
			if (current == null || !line.startsWith("|")) {
				continue;
			}
			String[] c = line.substring(1, line.length() - 1).split("[|]", -1);
			if (c.length == 4 && c[2].trim().matches("`[CTGMXA]`")) {
				expected.get(current).add(c[1].trim().replace(" (weak: whole utterance)", ""));
			}
		}
		assertEquals(14, expected.size());
		for (Map.Entry<String, Set<String>> e : expected.entrySet()) {
			PhraseBook.Command c = book.command(e.getKey());
			assertNotNull(c, e.getKey());
			Set<String> have = new HashSet<>();
			c.variants().forEach(v -> have.add(v.text()));
			for (String phrase : e.getValue()) {
				assertTrue(have.contains(phrase), e.getKey() + " is missing '" + phrase + "' (run tools/gen_voice_phrases.py)");
			}
		}
	}

	// ------------------------------------------------------------------ schema errors are reported, not swallowed

	@Test
	void rejectsABrokenSchema() {
		assertThrows(IllegalArgumentException.class, () -> PhraseBook.parse("{\"version\":2,\"fillers\":[],\"commands\":[]}"));
		assertThrows(IllegalArgumentException.class, () -> PhraseBook.parse("{\"version\":1,\"fillers\":[]}"));
		String ok = "{\"version\":1,\"fillers\":[],\"commands\":[{\"id\":\"a.b.c\",\"states\":[\"SEALED\"],\"item\":\"any\",\"say\":\"x\","
				+ "\"variants\":[{\"text\":\"foo\",\"origin\":\"C\"}]}]}";
		assertEquals(1, PhraseBook.parse(ok).commands().size());
		assertThrows(IllegalArgumentException.class, () -> PhraseBook.parse(ok.replace("SEALED", "FLYING")));
		assertThrows(IllegalArgumentException.class, () -> PhraseBook.parse(ok.replace("\"any\"", "\"nobody\"")));
		assertThrows(IllegalArgumentException.class, () -> PhraseBook.parse(ok.replace("\"foo\"", "\"???\"")), "normalises to nothing");
		assertThrows(IllegalArgumentException.class, () -> PhraseBook.parse(ok.replace("\"origin\":\"C\"}", "\"origin\":\"C\",\"requires\":[\"moon\"]}")));
		assertThrows(IllegalArgumentException.class, () -> PhraseBook.parse(ok.replace("\"variants\":[{\"text\":\"foo\",\"origin\":\"C\"}]", "\"variants\":[]")));
		String twice = ok.substring(0, ok.length() - 2) + "," + ok.substring(ok.indexOf("{\"id\""));
		assertThrows(IllegalArgumentException.class, () -> PhraseBook.parse(twice), "duplicate id");
	}

	@Test
	void theTableIsFastEnough() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<VoiceTestSupport.Positive> all = VoiceTestSupport.positives();
		GateContext ctx = GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, true);
		for (int i = 0; i < 3; i++) { // warm up the JIT
			for (VoiceTestSupport.Positive p : all) {
				m.match(p.text(), ctx);
			}
		}
		long t0 = System.nanoTime();
		int n = 0;
		for (int i = 0; i < 5; i++) {
			for (VoiceTestSupport.Positive p : all) {
				m.match(p.text(), ctx);
				n++;
			}
		}
		double meanMs = (System.nanoTime() - t0) / 1e6 / n;
		System.out.printf("[voice] matcher mean time %.3f ms over %d matches (budget 5 ms)%n", meanMs, n);
		assertTrue(meanMs < 5.0, "matcher mean " + meanMs + " ms");
	}
}
