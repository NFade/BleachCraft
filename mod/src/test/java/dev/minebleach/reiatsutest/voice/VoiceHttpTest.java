package dev.minebleach.reiatsutest.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import dev.minebleach.reiatsutest.core.voice.VoiceMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Request rules of the bridge, independent of the transport (see VoiceEndpointTest for real sockets). */
class VoiceHttpTest {
	static final int PORT = 47999;

	static final class FakeBackend implements VoiceBackend {
		// handlers run on several server threads: the recording lists must be thread safe
		final List<VoiceMessage> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
		final List<Long> receivedAt = new java.util.concurrent.CopyOnWriteArrayList<>();
		boolean explode;

		@Override
		public String onVoice(VoiceMessage message, long receivedNanos) {
			if (explode) {
				throw new IllegalStateException("secret internals");
			}
			messages.add(message);
			receivedAt.add(receivedNanos);
			return "{\"matched\":true,\"command\":\"rukia.shikai.hakuren\"}";
		}

		@Override
		public String status() {
			return "{\"ok\":true,\"state\":\"SHIKAI\"}";
		}
	}

	private final FakeBackend backend = new FakeBackend();
	private final AtomicLong nanos = new AtomicLong(5_000_000_000L);
	private VoiceConfig cfg = VoiceConfig.defaults();
	private byte[] page = "<html>page</html>".getBytes(StandardCharsets.UTF_8);

	private VoiceHttp http() {
		VoiceHttp h = new VoiceHttp(cfg, backend, () -> page, nanos::get);
		h.setPort(PORT);
		return h;
	}

	private static VoiceHttp.Request req(String method, String path, String body, String... headers) {
		Map<String, String> h = new HashMap<>();
		h.put("host", "127.0.0.1:" + PORT);
		for (int i = 0; i < headers.length; i += 2) {
			h.put(headers[i].toLowerCase(), headers[i + 1]);
		}
		byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
		return new VoiceHttp.Request(method, path, h, new ByteArrayInputStream(b), body == null ? -1 : b.length);
	}

	private static String body(VoiceHttp.Response r) {
		return new String(r.body(), StandardCharsets.UTF_8);
	}

	private static final String GOOD = "{\"text\":\"Hakuren\",\"final\":true,\"lang\":\"ja-JP\",\"utt\":4,\"t\":1700000000000}";

	@Test
	void aValidPostReachesTheBackendWithAllFields() {
		VoiceHttp.Response r = http().handle(req("POST", "/voice", GOOD, "Content-Type", "application/json"));
		assertEquals(200, r.status());
		assertTrue(body(r).contains("rukia.shikai.hakuren"));
		assertEquals("application/json; charset=utf-8", r.headers().get("Content-Type"));
		assertEquals(1, backend.messages.size());
		VoiceMessage m = backend.messages.get(0);
		assertEquals("Hakuren", m.text());
		assertTrue(m.isFinal());
		assertEquals("ja-JP", m.lang());
		assertEquals(4, m.utt());
		assertEquals(1700000000000L, m.clientTimeMs());
		assertEquals(5_000_000_000L, backend.receivedAt.get(0), "receive time is taken at entry");
	}

	@Test
	void optionalFieldsDefault() {
		assertEquals(200, http().handle(req("POST", "/voice", "{\"text\":\"bankai\"}")).status());
		VoiceMessage m = backend.messages.get(0);
		assertFalse(m.isFinal());
		assertEquals("", m.lang());
		assertEquals(-1, m.utt());
		assertEquals(0, m.clientTimeMs());
	}

	@Test
	void unicodeTextSurvives() {
		assertEquals(200, http().handle(req("POST", "/voice", "{\"text\":\"舞え、袖白雪 Мае\",\"final\":true}", "Content-Type", "text/plain;charset=UTF-8")).status());
		assertEquals("舞え、袖白雪 Мае", backend.messages.get(0).text());
	}

	@Test
	void malformedBodiesAreBadRequests() {
		String[] bad = {"", "not json", "[1,2]", "{}", "{\"text\":5}", "{\"text\":null}", "{\"text\":[\"a\"]}", "{\"text\":\"a\",\"utt\":\"x\"}",
			"{\"text\":\"a\",\"final\":{}}", "{\"text\":\"" + "a".repeat(1001) + "\"}"};
		for (String b : bad) {
			VoiceHttp.Response r = http().handle(req("POST", "/voice", b));
			assertEquals(400, r.status(), "body: " + (b.length() > 40 ? b.substring(0, 40) : b));
			assertTrue(body(r).contains("error"));
		}
		assertTrue(backend.messages.isEmpty(), "nothing reached the game");
	}

	@Test
	void oversizedBodiesAreRefused() {
		cfg.maxBodyBytes = 512;
		String big = "{\"text\":\"" + "x".repeat(600) + "\"}";
		assertEquals(413, http().handle(req("POST", "/voice", big)).status(), "declared length");
		// no Content-Length (chunked): the limit is enforced while reading
		VoiceHttp.Request undeclared = new VoiceHttp.Request("POST", "/voice", Map.of("host", "127.0.0.1:" + PORT),
				new ByteArrayInputStream(big.getBytes(StandardCharsets.UTF_8)), -1);
		assertEquals(413, http().handle(undeclared).status(), "undeclared length");
		String exact = "{\"text\":\"" + "y".repeat(512 - 11) + "\"}";
		assertEquals(512, exact.length());
		assertEquals(200, http().handle(req("POST", "/voice", exact)).status(), "exactly at the limit is fine");
		assertEquals(1, backend.messages.size());
	}

	@Test
	void onlyLoopbackHostsOfTheBoundPortAreAccepted() {
		VoiceHttp h = http();
		for (String ok : new String[] {"127.0.0.1:" + PORT, "localhost:" + PORT, "LOCALHOST:" + PORT, "[::1]:" + PORT}) {
			assertEquals(200, h.handle(req("POST", "/voice", GOOD, "Host", ok)).status(), ok);
		}
		for (String bad : new String[] {"evil.com", "evil.com:" + PORT, "127.0.0.1", "127.0.0.1:1", "localhost.evil.com:" + PORT,
			"127.0.0.1.evil.com:" + PORT, "192.168.1.5:" + PORT, "0.0.0.0:" + PORT, ""}) {
			assertEquals(403, h.handle(req("POST", "/voice", GOOD, "Host", bad)).status(), "Host: " + bad);
		}
		Map<String, String> none = new HashMap<>();
		assertEquals(403, h.handle(new VoiceHttp.Request("POST", "/voice", none, new ByteArrayInputStream(GOOD.getBytes()), GOOD.length())).status(),
				"missing Host (DNS rebinding guard)");
		assertEquals(403, h.handle(req("GET", "/status", null, "Host", "evil.com")).status(), "also for the status page");
		assertEquals(1 + 3, backend.messages.size());
	}

	@Test
	void foreignWebsitesAreRefusedByOrigin() {
		VoiceHttp h = http();
		for (String origin : new String[] {"https://evil.com", "http://evil.com", "http://127.0.0.1.evil.com", "https://127.0.0.1:" + PORT,
			"http://user@127.0.0.1:" + PORT, "ftp://localhost", "file://", "http://localhost.evil.com", "nonsense", "http://"}) {
			VoiceHttp.Response r = h.handle(req("POST", "/voice", GOOD, "Origin", origin));
			assertEquals(403, r.status(), "Origin: " + origin);
			assertNull(r.headers().get("Access-Control-Allow-Origin"), "no CORS grant for " + origin);
		}
		assertTrue(backend.messages.isEmpty());
	}

	@Test
	void localPagesGetCorsAndFileUrlsDependOnTheSwitch() {
		VoiceHttp h = http();
		for (String origin : new String[] {"http://127.0.0.1:" + PORT, "http://localhost:3000", "http://[::1]:8080", "http://127.0.0.1"}) {
			VoiceHttp.Response r = h.handle(req("POST", "/voice", GOOD, "Origin", origin));
			assertEquals(200, r.status(), origin);
			assertEquals(origin, r.headers().get("Access-Control-Allow-Origin"));
			assertEquals("Origin", r.headers().get("Vary"));
		}
		// a page opened from file:// sends Origin: null
		assertEquals("null", h.handle(req("POST", "/voice", GOOD, "Origin", "null")).headers().get("Access-Control-Allow-Origin"));
		cfg.allowNullOrigin = false;
		assertEquals(403, http().handle(req("POST", "/voice", GOOD, "Origin", "null")).status());
		// no Origin (curl, the mod's own tools): allowed, no CORS header needed
		VoiceHttp.Response plain = http().handle(req("POST", "/voice", GOOD));
		assertEquals(200, plain.status());
		assertNull(plain.headers().get("Access-Control-Allow-Origin"));
	}

	@Test
	void preflight() {
		VoiceHttp.Response r = http().handle(req("OPTIONS", "/voice", null, "Origin", "null", "Access-Control-Request-Method", "POST"));
		assertEquals(204, r.status());
		assertEquals(0, r.body().length);
		assertEquals("null", r.headers().get("Access-Control-Allow-Origin"));
		assertTrue(r.headers().get("Access-Control-Allow-Methods").contains("POST"));
		assertEquals("Content-Type", r.headers().get("Access-Control-Allow-Headers"));
		assertEquals("true", r.headers().get("Access-Control-Allow-Private-Network"));
		VoiceHttp.Response evil = http().handle(req("OPTIONS", "/voice", null, "Origin", "https://evil.com"));
		assertEquals(403, evil.status());
		assertNull(evil.headers().get("Access-Control-Allow-Origin"));
		assertTrue(backend.messages.isEmpty());
	}

	@Test
	void routesAndMethods() {
		VoiceHttp h = http();
		assertEquals(405, h.handle(req("GET", "/voice", null)).status());
		assertEquals(405, h.handle(req("PUT", "/voice", GOOD)).status());
		assertEquals(405, h.handle(req("POST", "/status", GOOD)).status());
		assertEquals(404, h.handle(req("GET", "/nothing", null)).status());
		assertEquals(404, h.handle(req("POST", "/", GOOD)).status());
		VoiceHttp.Response st = h.handle(req("GET", "/status?x=1", null));
		assertEquals(200, st.status());
		assertTrue(body(st).contains("SHIKAI"));
		assertEquals(415, h.handle(req("POST", "/voice", GOOD, "Content-Type", "application/x-www-form-urlencoded")).status());
		assertEquals(415, h.handle(req("POST", "/voice", GOOD, "Content-Type", "text/html")).status());
		assertEquals(200, h.handle(req("POST", "/voice", GOOD, "Content-Type", "Application/JSON; charset=utf-8")).status());
		assertEquals(200, h.handle(req("POST", "/voice?foo=bar", GOOD)).status(), "a query string is ignored");
	}

	@Test
	void theBridgePageIsServedWithAStrictCsp() {
		VoiceHttp h = http();
		VoiceHttp.Response r = h.handle(req("GET", "/", null));
		assertEquals(200, r.status());
		assertEquals("<html>page</html>", body(r));
		assertTrue(r.headers().get("Content-Type").startsWith("text/html"));
		String csp = r.headers().get("Content-Security-Policy");
		assertTrue(csp.contains("default-src 'none'") && csp.contains("connect-src 'self'"), csp);
		assertFalse(csp.contains("http"), "the page may not reach other hosts: " + csp);
		assertEquals(200, h.handle(req("GET", "/index.html", null)).status());
		cfg.servePage = false;
		assertEquals(404, http().handle(req("GET", "/", null)).status());
		cfg.servePage = true;
		page = null;
		assertEquals(404, http().handle(req("GET", "/", null)).status(), "no page bundled");
	}

	@Test
	void rateLimit() {
		cfg.maxRequestsPerSecond = 5;
		VoiceHttp h = http();
		for (int i = 0; i < 5; i++) {
			assertEquals(200, h.handle(req("POST", "/voice", GOOD)).status());
			nanos.addAndGet(10_000_000L);
		}
		assertEquals(429, h.handle(req("POST", "/voice", GOOD)).status());
		nanos.addAndGet(1_000_000_000L);
		assertEquals(200, h.handle(req("POST", "/voice", GOOD)).status(), "the window slid");
		assertEquals(200, h.handle(req("GET", "/status", null)).status(), "status polling is not rate limited");
	}

	@Test
	void backendFailuresAreNotLeaked() {
		backend.explode = true;
		VoiceHttp.Response r = http().handle(req("POST", "/voice", GOOD));
		assertEquals(500, r.status());
		assertFalse(body(r).contains("secret"));
	}

	@Test
	void everyResponseForbidsCachingAndSniffing() {
		VoiceHttp.Response r = http().handle(req("POST", "/voice", GOOD));
		assertEquals("no-store", r.headers().get("Cache-Control"));
		assertEquals("nosniff", r.headers().get("X-Content-Type-Options"));
	}
}
