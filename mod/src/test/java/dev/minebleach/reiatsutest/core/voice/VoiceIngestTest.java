package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.core.voice.VoiceFeedback.Verdict;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** The server side of online voice: several players at once, each with their own state, all gates on the server. */
class VoiceIngestTest {
	static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	/** A fake game: one gate per player, every executed command recorded with the player it ran for. */
	static final class FakeHost implements VoiceIngest.Host {
		final Map<UUID, GateContext> gates = new HashMap<>();
		final List<String> executed = new ArrayList<>();
		final List<UUID> hints = new ArrayList<>();
		ResultCode result = ResultCode.OK;
		boolean unknown;

		@Override
		public GateContext gate(UUID player) {
			return gates.get(player);
		}

		@Override
		public ResultCode execute(UUID player, String commandId, int reqId) {
			executed.add(player.getLeastSignificantBits() + ":" + commandId);
			return unknown ? null : result;
		}

		@Override
		public String stateName(UUID player) {
			GateContext g = gates.get(player);
			return g == null ? "" : g.state().name();
		}

		@Override
		public void hintNotDrawn(UUID player) {
			hints.add(player);
		}
	}

	private final AtomicLong ms = new AtomicLong(1_000_000);
	private final AtomicLong ns = new AtomicLong(5_000_000_000L);
	private final FakeHost host = new FakeHost();
	private final VoiceConfig cfg = VoiceConfig.defaults();
	private int req;

	private VoiceIngest ingest() {
		return new VoiceIngest(cfg, new PhraseMatcher(VoiceTestSupport.book(), cfg.matcherConfig()), host, ms::get, ns::get);
	}

	private VoiceRequest fin(String text) {
		return VoiceRequest.of(++req, text, true, "en-US", req, List.of());
	}

	private VoiceRequest finAlts(String text, String... alts) {
		return VoiceRequest.of(++req, text, true, "en-US", req, List.of(alts));
	}

	private VoiceRequest interim(String text, long utt) {
		return new VoiceRequest(VoiceWire.PROTOCOL, ++req, text, false, "en-US", utt, List.of());
	}

	private static GateContext rukiaShikai() {
		return GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, false);
	}

	private static GateContext byakuyaBase() {
		return GateContext.of(CharacterId.BYAKUYA, ZanpakutoState.BASE, false);
	}

	@Test
	void aCommandRunsForTheSenderOnly() {
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		host.gates.put(B, byakuyaBase());
		VoiceFeedback fa = in.handle(A, "A", fin("Hakuren")).feedback();
		assertEquals(Verdict.OK, fa.verdict());
		assertEquals("rukia.shikai.hakuren", fa.commandId());
		assertEquals(List.of(A.getLeastSignificantBits() + ":rukia.shikai.hakuren"), host.executed, "executed for A and nobody else");
		// B holds the Byakuya sword in BASE: the phrase of A means nothing there, and B's own release phrase runs for B
		VoiceFeedback fb = in.handle(B, "B", fin("Hakuren")).feedback();
		assertEquals(Verdict.GATED_OUT, fb.verdict());
		assertEquals(1, host.executed.size());
		VoiceFeedback fb2 = in.handle(B, "B", fin("chire senbonzakura")).feedback();
		assertEquals(Verdict.OK, fb2.verdict());
		assertEquals(List.of(A.getLeastSignificantBits() + ":rukia.shikai.hakuren", B.getLeastSignificantBits() + ":byakuya.shikai.release"),
				host.executed);
	}

	@Test
	void debounceAndGlobalGapAreKeptPerPlayer() {
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		host.gates.put(B, rukiaShikai());
		assertEquals(Verdict.OK, in.handle(A, "A", fin("Hakuren")).feedback().verdict());
		// the same instant for the other player: not debounced by the command of A
		assertEquals(Verdict.OK, in.handle(B, "B", fin("Hakuren")).feedback().verdict());
		// A again right away: debounced, B's second one as well, independently
		ms.addAndGet(100);
		VoiceFeedback again = in.handle(A, "A", fin("Hakuren")).feedback();
		assertEquals(Verdict.IGNORED, again.verdict());
		assertEquals("debounced", again.reason());
		assertEquals(Verdict.IGNORED, in.handle(B, "B", fin("Hakuren")).feedback().verdict());
		assertEquals(2, host.executed.size());
		ms.addAndGet(2000);
		assertEquals(Verdict.OK, in.handle(A, "A", fin("Hakuren")).feedback().verdict());
		assertEquals(3, host.executed.size());
	}

	@Test
	void theRateLimitIsPerPlayerAndSlides() {
		cfg.maxRequestsPerSecond = 3;
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		host.gates.put(B, rukiaShikai());
		for (int i = 0; i < 3; i++) {
			assertEquals(Verdict.NO_MATCH, in.handle(A, "A", fin("I found a diamond " + i)).feedback().verdict());
		}
		VoiceFeedback blocked = in.handle(A, "A", fin("Hakuren")).feedback();
		assertEquals(Verdict.REJECTED, blocked.verdict());
		assertEquals("rate_limited", blocked.reason());
		assertTrue(host.executed.isEmpty(), "a rate limited text never runs");
		// B is a different player: own window
		assertEquals(Verdict.OK, in.handle(B, "B", fin("Hakuren")).feedback().verdict());
		// a second later A is admitted again
		ns.addAndGet(1_100_000_000L);
		assertEquals(Verdict.OK, in.handle(A, "A", fin("Hakuren")).feedback().verdict());
	}

	@Test
	void whenVoiceIsDisabledNothingIsLookedAtAndNothingRuns() {
		cfg.enabled = false;
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		VoiceFeedback f = in.handle(A, "A", fin("Hakuren")).feedback();
		assertEquals(Verdict.REJECTED, f.verdict());
		assertEquals("disabled", f.reason());
		assertTrue(host.executed.isEmpty());
		assertEquals(0, in.trackedPlayers(), "no state is built for a disabled server");
	}

	@Test
	void textLengthAndEmptyTextAreChecked() {
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		String limit = "a".repeat(VoiceWire.MAX_TEXT_CHARS);
		assertEquals(Verdict.NO_MATCH, in.handle(A, "A", fin(limit)).feedback().verdict(), "exactly the limit is read");
		VoiceFeedback tooLong = in.handle(A, "A", fin(limit + "a")).feedback();
		assertEquals(Verdict.REJECTED, tooLong.verdict());
		assertEquals("too_long", tooLong.reason());
		// a long text that starts with a command phrase must not fire either
		assertEquals("too_long", in.handle(A, "A", fin("hakuren " + "x".repeat(300))).feedback().reason());
		assertEquals(Verdict.IGNORED, in.handle(A, "A", fin("   ")).feedback().verdict());
		assertEquals(Verdict.IGNORED, in.handle(A, "A", fin("")).feedback().verdict());
		assertTrue(host.executed.isEmpty());
	}

	@Test
	void atMostThreeAlternativesAreUsed() {
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		// the fourth alternative is dropped: nothing fires
		VoiceFeedback four = in.handle(A, "A", finAlts("I found a diamond", "some noise", "more noise", "still noise", "hakuren")).feedback();
		assertEquals(Verdict.NO_MATCH, four.verdict());
		assertTrue(host.executed.isEmpty());
		// the third one is used
		VoiceFeedback three = in.handle(A, "A", finAlts("I found a diamond", "some noise", "more noise", "hakuren")).feedback();
		assertEquals(Verdict.OK, three.verdict());
		assertEquals("rukia.shikai.hakuren", three.commandId());
		// an alternative over the length limit is ignored
		ms.addAndGet(5000);
		VoiceFeedback longAlt = in.handle(A, "A", finAlts("I found a diamond", "x".repeat(VoiceWire.MAX_TEXT_CHARS + 1), "hakuren")).feedback();
		assertEquals(Verdict.OK, longAlt.verdict());
		ms.addAndGet(5000);
		assertEquals(Verdict.OK, in.handle(A, "A", finAlts("I found a diamond", " ", "", "hakuren")).feedback().verdict());
	}

	@Test
	void interimResultsAreIgnoredByDefaultAndTheFinalStillFires() {
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		for (int i = 0; i < 3; i++) {
			ns.addAndGet(200_000_000L);
			VoiceFeedback f = in.handle(A, "A", interim("hakuren", 7)).feedback();
			assertEquals(Verdict.IGNORED, f.verdict());
			assertEquals("interim_ignored", f.reason());
		}
		assertTrue(host.executed.isEmpty());
		assertEquals(Verdict.OK, in.handle(A, "A", new VoiceRequest(VoiceWire.PROTOCOL, ++req, "hakuren", true, "en-US", 7, List.of())).feedback().verdict());
	}

	@Test
	void aRefusedCommandIsReportedAsDeniedWithItsResultCode() {
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		host.result = ResultCode.DENIED_REIATSU;
		VoiceFeedback f = in.handle(A, "A", fin("Hakuren")).feedback();
		assertEquals(Verdict.DENIED, f.verdict());
		assertEquals(ResultCode.DENIED_REIATSU.code(), f.resultCode());
		assertEquals("rukia.shikai.hakuren", f.commandId());
		assertEquals("SHIKAI", f.state());
	}

	@Test
	void anUnknownCommandIdAnswersNoMatch() {
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		host.unknown = true;
		VoiceFeedback f = in.handle(A, "A", fin("Hakuren")).feedback();
		assertEquals(Verdict.NO_MATCH, f.verdict());
		assertEquals("unknown_command", f.reason());
	}

	@Test
	void wrongProtocolAndMissingSessionAreRefused() {
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		VoiceFeedback bad = in.handle(A, "A", new VoiceRequest(VoiceWire.PROTOCOL + 1, 1, "hakuren", true, "en-US", 1, List.of())).feedback();
		assertEquals("bad_protocol", bad.reason());
		VoiceFeedback none = in.handle(B, "B", fin("hakuren")).feedback();
		assertEquals(Verdict.REJECTED, none.verdict());
		assertEquals("no_session", none.reason());
		assertTrue(host.executed.isEmpty());
	}

	@Test
	void theReleasePhraseWithTheSheathedSwordHintsTheRightPlayer() {
		VoiceIngest in = ingest();
		host.gates.put(A, GateContext.of(CharacterId.RUKIA, ZanpakutoState.SEALED, false));
		host.gates.put(B, GateContext.of(CharacterId.NONE, ZanpakutoState.SEALED, false));
		assertEquals(Verdict.GATED_OUT, in.handle(A, "A", fin("sode no shirayuki")).feedback().verdict());
		assertEquals(List.of(A), host.hints);
		assertEquals(Verdict.GATED_OUT, in.handle(B, "B", fin("sode no shirayuki")).feedback().verdict());
		assertEquals(List.of(A), host.hints, "no sword in hand: no hint");
		assertTrue(host.executed.isEmpty());
	}

	@Test
	void forgettingAPlayerClearsTheirState() {
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		assertEquals(Verdict.OK, in.handle(A, "A", fin("Hakuren")).feedback().verdict());
		ms.addAndGet(100);
		assertEquals(Verdict.IGNORED, in.handle(A, "A", fin("Hakuren")).feedback().verdict());
		in.forget(A);
		assertEquals(0, in.trackedPlayers());
		assertEquals(Verdict.OK, in.handle(A, "A", fin("Hakuren")).feedback().verdict());
	}

	@Test
	void logLinesNameThePlayer() {
		VoiceIngest in = ingest();
		host.gates.put(A, rukiaShikai());
		VoiceIngest.Handled fired = in.handle(A, "Haru", fin("Hakuren"));
		assertNotNull(fired.log());
		assertTrue(fired.log().startsWith("[voice] Haru "), fired.log());
		VoiceIngest.Handled heard = in.handle(A, "Haru", fin("pass me the salt"));
		assertTrue(heard.log().contains("heard \"pass me the salt\""), heard.log());
		assertNull(in.handle(A, "Haru", interim("pass me the salt", 9)).log(), "interim noise is not logged");
	}
}
