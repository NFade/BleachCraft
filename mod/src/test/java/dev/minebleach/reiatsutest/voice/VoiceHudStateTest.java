package dev.minebleach.reiatsutest.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	@Test
	void queryParamsAreParsed() {
		assertEquals("hearing", VoiceHttp.queryParam("/status?mic=hearing", "mic"));
		assertEquals("on", VoiceHttp.queryParam("/status?x=1&mic=on&y=2", "mic"));
		assertEquals("", VoiceHttp.queryParam("/status?mic=", "mic") == null ? "" : VoiceHttp.queryParam("/status?mic=", "mic"));
		assertNull(VoiceHttp.queryParam("/status", "mic"));
		assertNull(VoiceHttp.queryParam("/status?other=1", "mic"));
	}
}
