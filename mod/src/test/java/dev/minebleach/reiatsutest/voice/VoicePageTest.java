package dev.minebleach.reiatsutest.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The bridge page is served by the mod under a strict CSP (connect-src 'self'): it may not pull anything from outside.
 * Static checks of {@code voice-bridge/index.html}; the behaviour of its session controller is tested without a browser by
 * {@code tests/voice/session_core.test.mjs} (node), and against a real Chrome by {@code tools/voice_lab_e2e.mjs}.
 */
class VoicePageTest {
	private static final Path PAGE = Paths.get("..", "voice-bridge", "index.html");

	private static String page() throws IOException {
		return Files.readString(PAGE, StandardCharsets.UTF_8);
	}

	@Test
	void pageHasNoExternalResources() throws IOException {
		String html = page();
		assertFalse(Pattern.compile("<script[^>]+src=", Pattern.CASE_INSENSITIVE).matcher(html).find(), "no external script");
		assertFalse(Pattern.compile("<link[^>]+href=", Pattern.CASE_INSENSITIVE).matcher(html).find(), "no stylesheet or font link");
		List<String> foreign = new ArrayList<>();
		Matcher m = Pattern.compile("https?://[^\\s\"'<>)]+").matcher(html);
		while (m.find()) {
			if (!m.group().matches("https?://(127\\.0\\.0\\.1|localhost)([:/*].*)?")) {
				foreign.add(m.group());
			}
		}
		assertEquals(List.of(), foreign, "only loopback URLs may appear in the page");
		assertTrue(html.contains("default-src 'none'"), "meta CSP present");
	}

	@Test
	void sessionControllerAndStatsAreDelimitedForTheNodeTests() throws IOException {
		String html = page();
		for (String k : new String[] {"SESSION-CORE", "STATS", "PTTKEY"}) {
			assertTrue(html.contains("===== " + k + "-BEGIN") && html.contains("===== " + k + "-END"), "markers " + k);
		}
	}

	@Test
	void pageKeepsTheWireProtocol() throws IOException {
		String html = page();
		// the Java side parses exactly these fields (VoiceHttp#parse); the mic states feed VoiceHudState#contact
		for (String field : new String[] {"text:", "final:", "lang:", "utt:", "t: Date.now()"}) {
			assertTrue(html.contains(field), "POST body field " + field);
		}
		assertTrue(html.contains("/voice") && html.contains("/status?mic="));
		assertFalse(html.contains("localStorage.setItem(\"reiatsu.text"), "recognised words are never stored");
	}

	/** v4: the watchdog, the status line and the background-proof heartbeat are part of the page (behaviour: session_core.test.mjs). */
	@Test
	void pageHasWatchdogStatusLineAndHeartbeat() throws IOException {
		String html = page();
		assertTrue(html.contains("id=\"recStatus\"") && html.contains("id=\"recCount\""), "recognition status line with the restart counter");
		assertTrue(html.contains("onRestart") && html.contains("rz.tick()"), "watchdog hook and heartbeat tick");
		assertTrue(html.contains("keepAlive: true"), "the page never gives up on transient errors");
		// the heartbeat is a blob worker: the strict CSP must allow exactly that and nothing wider
		assertTrue(html.contains("worker-src blob:"), "CSP allows the blob heartbeat worker");
		assertFalse(html.contains("worker-src *") || html.contains("script-src *"), "no wide CSP");
	}

	@Test
	void pageSizeStaysReasonable() throws IOException {
		assertTrue(Files.size(PAGE) < 120_000, "single page, no assets: " + Files.size(PAGE));
	}
}
