package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.core.voice.VoiceProcessor.Decision;
import dev.minebleach.reiatsutest.core.voice.VoiceProcessor.Outcome;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class VoiceProcessorTest {
	private final AtomicLong now = new AtomicLong(1_000_000);
	private final GateContext rukiaShikai = GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, false);
	private final GateContext rukiaShikaiFull = GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, true);
	private final GateContext byakuyaBase = GateContext.of(CharacterId.BYAKUYA, ZanpakutoState.BASE, true);

	private VoiceProcessor processor(VoiceConfig cfg) {
		return new VoiceProcessor(new PhraseMatcher(VoiceTestSupport.book(), cfg.matcherConfig()), cfg, now::get);
	}

	private static VoiceMessage fin(String text, long utt) {
		return new VoiceMessage(text, true, "en-US", utt, 0);
	}

	private static VoiceMessage interim(String text, long utt) {
		return new VoiceMessage(text, false, "en-US", utt, 0);
	}

	@Test
	void aFinalResultFires() {
		VoiceProcessor p = processor(VoiceConfig.defaults());
		Decision d = p.process(fin("Hakuren", 1), rukiaShikai);
		assertTrue(d.fired());
		assertEquals("rukia.shikai.hakuren", d.commandId());
		assertTrue(d.confidence() >= 0.99);
	}

	@Test
	void ordinarySpeechAndEmptyTextNeverFire() {
		VoiceProcessor p = processor(VoiceConfig.defaults());
		assertEquals(Outcome.NO_MATCH, p.process(fin("I found a diamond", 1), rukiaShikai).outcome());
		assertEquals(Outcome.EMPTY, p.process(fin("   ", 2), rukiaShikai).outcome());
		assertEquals(Outcome.EMPTY, p.process(fin("盆栽", 3), rukiaShikai).outcome());
	}

	@Test
	void interimResultsAreIgnoredByDefault() {
		VoiceProcessor p = processor(VoiceConfig.defaults());
		for (int i = 0; i < 4; i++) {
			assertEquals(Outcome.INTERIM_IGNORED, p.process(interim("hakuren", 1), rukiaShikai).outcome());
		}
		assertTrue(p.process(fin("hakuren", 1), rukiaShikai).fired(), "the final result still fires");
	}

	@Test
	void sameCommandIsDebouncedForOneAndAHalfSecondsEvenAcrossUtterances() {
		VoiceProcessor p = processor(VoiceConfig.defaults());
		assertTrue(p.process(fin("hakuren", 1), rukiaShikai).fired());
		now.addAndGet(600);
		Decision again = p.process(fin("tsugi no mai hakuren", 2), rukiaShikai);
		assertEquals(Outcome.DEBOUNCED, again.outcome());
		now.addAndGet(1000); // 1600 ms after the first one
		assertTrue(p.process(fin("hakuren", 3), rukiaShikai).fired());
	}

	@Test
	void theSameFinalDeliveredTwiceFiresOnce() {
		VoiceProcessor p = processor(VoiceConfig.defaults());
		assertTrue(p.process(fin("hakuren", 7), rukiaShikai).fired());
		now.addAndGet(20);
		assertEquals(Outcome.DEBOUNCED, p.process(fin("hakuren", 7), rukiaShikai).outcome());
		now.addAndGet(2000);
		assertEquals(Outcome.DEBOUNCED, p.process(fin("hakuren", 7), rukiaShikai).outcome(), "one command per utterance index");
	}

	@Test
	void anyTwoCommandsNeedAGapOf300Ms() {
		VoiceProcessor p = processor(VoiceConfig.defaults());
		assertTrue(p.process(fin("hakuren", 1), rukiaShikai).fired());
		now.addAndGet(100);
		Decision tooSoon = p.process(fin("shirafune", 2), rukiaShikai);
		assertEquals(Outcome.DEBOUNCED, tooSoon.outcome());
		assertEquals("rukia.shikai.shirafune", tooSoon.commandId());
		now.addAndGet(250);
		assertTrue(p.process(fin("shirafune", 3), rukiaShikai).fired());
	}

	@Test
	void consumedTextIsStrippedSoTheTailOfOneSentenceDoesNotFireTwice() {
		VoiceProcessor p = processor(VoiceConfig.defaults());
		assertTrue(p.process(fin("hakuren", 1), rukiaShikai).fired());
		now.addAndGet(400);
		// the recogniser repeats the text and extends it: only "shirafune" is new
		Decision d = p.process(fin("hakuren shirafune", 2), rukiaShikai);
		assertTrue(d.fired());
		assertEquals("rukia.shikai.shirafune", d.commandId());
		now.addAndGet(400);
		assertEquals(Outcome.DEBOUNCED, p.process(fin("shirafune", 3), rukiaShikai).outcome(), "repeat of the text just acted on");
		now.addAndGet(5000); // after the consumed-text window the same words are a fresh request
		assertTrue(p.process(fin("hakuren", 4), rukiaShikai).fired());
	}

	@Test
	void aWrongStateIsReportedAsGatedOutAndDoesNotStartTheDebounce() {
		VoiceProcessor p = processor(VoiceConfig.defaults());
		Decision d = p.process(fin("hakuren", 1), GateContext.of(CharacterId.RUKIA, ZanpakutoState.SEALED, true));
		assertEquals(Outcome.GATED_OUT, d.outcome());
		assertEquals("rukia.shikai.hakuren", d.commandId());
		now.addAndGet(10);
		assertTrue(p.process(fin("hakuren", 2), rukiaShikai).fired(), "a gated-out attempt must not block the real one");
		// "bankai" is valid for two commands in other contexts: still explained as gated out
		assertEquals(Outcome.GATED_OUT, p.process(fin("bankai", 3), GateContext.of(CharacterId.RUKIA, ZanpakutoState.SEALED, true)).outcome());
		assertEquals(Outcome.NO_MATCH, p.process(fin("mae", 4), GateContext.of(CharacterId.RUKIA, ZanpakutoState.SEALED, true)).outcome());
	}

	@Test
	void aWeakWordInsideASentenceIsReported() {
		VoiceProcessor p = processor(VoiceConfig.defaults());
		Decision d = p.process(fin("the seal is broken", 1), rukiaShikai);
		assertEquals(Outcome.WEAK_NOT_WHOLE_UTTERANCE, d.outcome());
		assertNull(d.commandId());
	}

	@Test
	void resetForgetsTheDebounce() {
		VoiceProcessor p = processor(VoiceConfig.defaults());
		assertTrue(p.process(fin("hakuren", 1), rukiaShikai).fired());
		p.reset();
		assertTrue(p.process(fin("hakuren", 1), rukiaShikai).fired());
	}

	// ------------------------------------------------------------------ interim handling

	private VoiceProcessor interimProcessor() {
		VoiceConfig cfg = VoiceConfig.defaults();
		cfg.interimEnabled = true;
		return processor(cfg);
	}

	@Test
	void anEnabledInterimNeedsTwoStableConsecutiveResultsOfTheSameUtterance() {
		VoiceProcessor p = interimProcessor();
		assertEquals(Outcome.INTERIM_IGNORED, p.process(interim("hakuren", 5), rukiaShikai).outcome());
		Decision second = p.process(interim("hakuren", 5), rukiaShikai);
		assertTrue(second.fired(), second.toString());
		// the final of that utterance must not fire again
		assertEquals(Outcome.DEBOUNCED, p.process(fin("hakuren", 5), rukiaShikai).outcome());
	}

	@Test
	void aChangingInterimHypothesisNeverFires() {
		VoiceProcessor p = interimProcessor();
		assertEquals(Outcome.INTERIM_IGNORED, p.process(interim("hakuren", 5), rukiaShikai).outcome());
		assertEquals(Outcome.INTERIM_IGNORED, p.process(interim("shirafune", 5), rukiaShikai).outcome(), "a different command");
		assertEquals(Outcome.INTERIM_IGNORED, p.process(interim("hakuren", 5), rukiaShikai).outcome(), "stability starts over");
		assertEquals(Outcome.NO_MATCH, p.process(interim("some other words", 5), rukiaShikai).outcome());
		assertEquals(Outcome.INTERIM_IGNORED, p.process(interim("hakuren", 5), rukiaShikai).outcome(), "a miss in between resets");
	}

	@Test
	void interimsFromDifferentUtterancesDoNotCombine() {
		VoiceProcessor p = interimProcessor();
		assertEquals(Outcome.INTERIM_IGNORED, p.process(interim("hakuren", 5), rukiaShikai).outcome());
		assertEquals(Outcome.INTERIM_IGNORED, p.process(interim("hakuren", 6), rukiaShikai).outcome());
	}

	@Test
	void bigMovesNeverFireFromAnInterimResult() {
		VoiceProcessor p = interimProcessor();
		for (int i = 0; i < 5; i++) {
			assertFalse(p.process(interim("bankai", 1), rukiaShikaiFull).fired(), "bankai from an interim");
		}
		GateContext bankai = GateContext.of(CharacterId.BYAKUYA, ZanpakutoState.BANKAI, true);
		for (String phrase : new String[] {"hakuteiken", "petal storm", "senkei"}) {
			for (int i = 0; i < 5; i++) {
				assertFalse(p.process(interim(phrase, 2), bankai).fired(), phrase + " from an interim");
			}
		}
		for (int i = 0; i < 5; i++) {
			assertFalse(p.process(interim("absolute zero", 3), GateContext.of(CharacterId.RUKIA, ZanpakutoState.BANKAI, true)).fired());
		}
	}

	@Test
	void weakWordsNeverFireFromAnInterimResult() {
		VoiceProcessor p = interimProcessor();
		for (int i = 0; i < 5; i++) {
			assertFalse(p.process(interim("chire", 1), byakuyaBase).fired(), "lone Chire from an interim");
			assertFalse(p.process(interim("seal", 2), rukiaShikai).fired(), "seal from an interim");
		}
		// the final still works
		assertTrue(p.process(fin("chire", 1), byakuyaBase).fired());
	}

	@Test
	void aSafeButUnsureInterimIsNotEnough() {
		VoiceConfig cfg = VoiceConfig.defaults();
		cfg.interimEnabled = true;
		VoiceProcessor p = processor(cfg);
		// "hakurem" is one letter off: confidence below the 0.97 interim threshold
		PhraseMatcher.Result r = p.matcher().match("hakurem", rukiaShikai);
		assertTrue(r.matched());
		assertTrue(r.confidence() < cfg.interimThreshold, "test premise: " + r.confidence());
		for (int i = 0; i < 4; i++) {
			assertFalse(p.process(interim("hakurem", 9), rukiaShikai).fired());
		}
		assertTrue(p.process(fin("hakurem", 9), rukiaShikai).fired(), "but the final fires at the normal threshold");
	}

	@Test
	void theConfidenceThresholdIsAConfigValue() {
		VoiceConfig strict = VoiceConfig.defaults();
		strict.threshold = 0.99;
		VoiceProcessor p = processor(strict);
		assertEquals(Outcome.NO_MATCH, p.process(fin("hakurem", 1), rukiaShikai).outcome());
		assertTrue(p.process(fin("hakuren", 2), rukiaShikai).fired());
	}
}
