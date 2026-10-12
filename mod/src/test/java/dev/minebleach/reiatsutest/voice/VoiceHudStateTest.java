package dev.minebleach.reiatsutest.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The voice HUD record (S6): mic mapping of the bridge page state and the /status?mic= query parsing. */
class VoiceHudStateTest {
	@Test
	void pageMicMapsToHudMic() {
		VoiceHudState.reset();
		assertEquals(VoiceHudState.Mic.OFF, VoiceHudState.get().mic());
		assertEquals(0, VoiceHudState.get().lastContactMs());
		VoiceHudState.contact("on");
		assertEquals(VoiceHudState.Mic.LISTENING, VoiceHudState.get().mic());
		assertTrue(VoiceHudState.get().lastContactMs() > 0);
		VoiceHudState.contact("hearing");
		assertEquals(VoiceHudState.Mic.HEARING, VoiceHudState.get().mic());
		VoiceHudState.contact("error");
		assertEquals(VoiceHudState.Mic.ERROR, VoiceHudState.get().mic());
		VoiceHudState.contact("");
		assertEquals(VoiceHudState.Mic.IDLE, VoiceHudState.get().mic());
		VoiceHudState.contact(null); // a /voice post: keeps the state, but the bridge has been reached
		assertEquals(VoiceHudState.Mic.IDLE, VoiceHudState.get().mic());
	}

	@Test
	void interimAndResultAdvanceTheSequence() {
		VoiceHudState.reset();
		long s0 = VoiceHudState.get().seq();
		VoiceHudState.interim("chire sen");
		assertEquals("chire sen", VoiceHudState.get().interim());
		assertEquals(VoiceHudState.Mic.HEARING, VoiceHudState.get().mic());
		VoiceHudState.result("chire senbonzakura", "byakuya.shikai.release", VoiceHudState.Result.ACCEPTED);
		VoiceHudState s = VoiceHudState.get();
		assertEquals("", s.interim());
		assertEquals("chire senbonzakura", s.finalText());
		assertEquals(VoiceHudState.Result.ACCEPTED, s.result());
		assertEquals(VoiceHudState.Mic.LISTENING, s.mic()); // hearing ends with the result
		assertTrue(s.seq() >= s0 + 2);
		VoiceHudState.reset();
	}

	/** v4: the HUD indicator is wanted as soon as the bridge runs (dim, crossed out), and "connected" ends 6 s after the last contact. */
	@Test
	void indicatorIsWantedWheneverTheBridgeRunsAndGoesOffAfterSixSeconds() {
		VoiceHudState.reset();
		assertFalse(VoiceHudState.get().indicatorWanted(), "no bridge, no contact: hidden (dedicated server, voice disabled)");
		VoiceHudState.setBridgeUp(true);
		assertTrue(VoiceHudState.get().indicatorWanted(), "bridge up, no page yet: shown as not connected");
		assertEquals(VoiceHudState.Mic.OFF, VoiceHudState.get().effectiveMic(System.currentTimeMillis()));
		VoiceHudState.contact("on");
		long t = VoiceHudState.get().lastContactMs();
		assertEquals(VoiceHudState.Mic.LISTENING, VoiceHudState.get().effectiveMic(t + 100));
		assertEquals(VoiceHudState.Mic.LISTENING, VoiceHudState.get().effectiveMic(t + VoiceHudState.CONTACT_TIMEOUT_MS));
		assertEquals(VoiceHudState.Mic.OFF, VoiceHudState.get().effectiveMic(t + VoiceHudState.CONTACT_TIMEOUT_MS + 1), "page gone");
		VoiceHudState.contact("restarting"); // the page's watchdog state: connected, not listening
		assertEquals(VoiceHudState.Mic.IDLE, VoiceHudState.get().effectiveMic(VoiceHudState.get().lastContactMs()));
		VoiceHudState.setBridgeUp(false);
		assertTrue(VoiceHudState.get().indicatorWanted(), "a page has been in touch: still shown");
		VoiceHudState.reset();
		assertFalse(VoiceHudState.bridgeUp());
		assertFalse(VoiceHudState.get().indicatorWanted());
	}

	@Test
	void queryParamsAreParsed() {
		assertEquals("hearing", VoiceHttp.queryParam("/status?mic=hearing", "mic"));
		assertEquals("on", VoiceHttp.queryParam("/status?x=1&mic=on&y=2", "mic"));
		assertEquals("", VoiceHttp.queryParam("/status?mic=", "mic") == null ? "" : VoiceHttp.queryParam("/status?mic=", "mic"));
		assertNull(VoiceHttp.queryParam("/status", "mic"));
		assertNull(VoiceHttp.queryParam("/status?other=1", "mic"));
	}
}
