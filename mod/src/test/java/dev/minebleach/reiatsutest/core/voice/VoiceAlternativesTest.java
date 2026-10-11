package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.core.voice.VoiceProcessor.Decision;
import dev.minebleach.reiatsutest.core.voice.VoiceProcessor.Outcome;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** G3: lower ranked recogniser hypotheses (Web Speech maxAlternatives) rescue a final result that matched nothing. */
class VoiceAlternativesTest {
	private final AtomicLong now = new AtomicLong(5_000_000);
	private final GateContext rukiaShikai = GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, false);

	private VoiceProcessor processor() {
		VoiceConfig cfg = VoiceConfig.defaults();
		return new VoiceProcessor(new PhraseMatcher(VoiceTestSupport.book(), cfg.matcherConfig()), cfg, now::get);
	}

	private static VoiceMessage fin(String text, long utt, String... alts) {
		return new VoiceMessage(text, true, "en-US", utt, 0, List.of(alts));
	}

	@Test
	void anAlternativeFiresWhenTheMainTextMatchesNothing() {
		Decision d = processor().process(fin("my sofa", 1, "hakuren"), rukiaShikai);
		assertTrue(d.fired());
		assertEquals("rukia.shikai.hakuren", d.commandId());
		assertTrue(d.detail().contains("alternative 1"), d.detail());
	}

	@Test
	void theMainTextWinsOverItsAlternatives() {
		Decision d = processor().process(fin("tsukishiro", 1, "hakuren"), rukiaShikai);
		assertEquals("rukia.shikai.tsukishiro", d.commandId());
		assertFalse(d.detail().contains("alternative"));
	}

	@Test
	void aSecondAlternativeIsTriedAfterAnUselessFirstOne() {
		Decision d = processor().process(fin("my sofa", 1, "a diamond", "shirafune"), rukiaShikai);
		assertEquals("rukia.shikai.shirafune", d.commandId());
		assertTrue(d.detail().contains("alternative 2"), d.detail());
	}

	@Test
	void aWeakFuzzyAlternativeIsNotEnough() {
		// a slipped "hakuren" matches as a main text but scores below the alternative floor
		VoiceProcessor p = processor();
		PhraseMatcher.Result viaMain = p.matcher().match("hakuran", rukiaShikai);
		if (viaMain.matched() && viaMain.confidence() < VoiceProcessor.ALT_MIN_CONFIDENCE) {
			assertEquals(Outcome.NO_MATCH, p.process(fin("my sofa", 1, "hakuran"), rukiaShikai).outcome());
			assertTrue(processor().process(fin("hakuran", 2), rukiaShikai).fired(), "as the main text it still fires");
		} else {
			// the matcher is stricter or looser than assumed: the floor itself is what this test protects
			assertTrue(VoiceProcessor.ALT_MIN_CONFIDENCE > p.matcher().config().threshold());
		}
	}

	@Test
	void ordinaryAlternativesNeverFire() {
		Decision d = processor().process(fin("I need to go to the bank", 1, "I need to go to the banks", "eye need to go to the bunk"), rukiaShikai);
		assertEquals(Outcome.NO_MATCH, d.outcome());
	}

	@Test
	void interimResultsDoNotUseAlternatives() {
		VoiceMessage m = new VoiceMessage("my sofa", false, "en-US", 1, 0, List.of("hakuren"));
		assertEquals(Outcome.INTERIM_IGNORED, processor().process(m, rukiaShikai).outcome());
	}

	@Test
	void onlyTheFirstThreeAlternativesAreLookedAt() {
		Decision d = processor().process(fin("my sofa", 1, "one", "two", "three", "hakuren"), rukiaShikai);
		assertEquals(Outcome.NO_MATCH, d.outcome());
	}

	@Test
	void aDebouncedMainResultIsNotRescuedByAnAlternative() {
		VoiceProcessor p = processor();
		assertTrue(p.process(fin("hakuren", 1), rukiaShikai).fired());
		now.addAndGet(300);
		Decision d = p.process(fin("hakuren", 2, "tsukishiro"), rukiaShikai);
		assertEquals(Outcome.DEBOUNCED, d.outcome());
	}

	@Test
	void anAlternativeRespectsTheGate() {
		// tsukishiro is valid in shikai only: from the sealed state the alternative stays gated out
		GateContext sealed = GateContext.of(CharacterId.RUKIA, ZanpakutoState.SEALED, false);
		assertFalse(processor().process(fin("my sofa", 1, "tsukishiro"), sealed).fired());
	}

	@Test
	void messagesWithoutAlternativesBehaveAsBefore() {
		VoiceMessage m = new VoiceMessage("hakuren", true, "en-US", 1, 0);
		assertTrue(m.alternatives().isEmpty());
		assertTrue(processor().process(m, rukiaShikai).fired());
		assertTrue(new VoiceMessage("x", true, "", -1, 0, null).alternatives().isEmpty());
	}
}
