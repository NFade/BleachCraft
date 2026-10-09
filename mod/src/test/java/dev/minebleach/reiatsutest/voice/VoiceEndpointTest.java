package dev.minebleach.reiatsutest.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The two transports on real loopback sockets, ephemeral ports. Both must behave identically. */
class VoiceEndpointTest {
	private static final String GOOD = "{\"text\":\"Hakuren\",\"final\":true,\"lang\":\"en-US\",\"utt\":1,\"t\":1}";

	private VoiceHttp http;
	private VoiceHttpTest.FakeBackend backend;
	private VoiceConfig cfg;

	private VoiceEndpoint endpoint(String kind) {
		backend = new VoiceHttpTest.FakeBackend();
		cfg = VoiceConfig.defaults();
		cfg.maxRequestsPerSecond = 1000;
		http = new VoiceHttp(cfg, backend, () -> "<html>bridge</html>".getBytes(StandardCharsets.UTF_8));
		return kind.equals("jdk") ? new JdkVoiceEndpoint(http) : new SocketVoiceEndpoint(http);
	}

	private static String raw(int port, String request) throws IOException {
		return raw(port, request.getBytes(StandardCharsets.ISO_8859_1), null);
	}

	/** Writes the bytes, optionally more bytes after them, and returns everything the server sends until it closes. */
	private static String raw(int port, byte[] head, byte[] tail) throws IOException {
		try (Socket s = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port)) {
			s.setSoTimeout(5000);
			OutputStream out = s.getOutputStream();
			out.write(head);
			if (tail != null) {
				try {
					out.write(tail);
				} catch (IOException e) {
					// the server may answer and close before the whole body is written (413): fine
				}
			}
			out.flush();
			ByteArrayOutputStream all = new ByteArrayOutputStream();
			InputStream in = s.getInputStream();
			byte[] buf = new byte[4096];
			try {
				int n;
				while ((n = in.read(buf)) >= 0) {
					all.write(buf, 0, n);
				}
			} catch (IOException e) {
				// reset after the response was sent
			}
			return all.toString(StandardCharsets.UTF_8);
		}
	}

	private static String post(int port, String host, String body, String extraHeaders) throws IOException {
		byte[] b = body.getBytes(StandardCharsets.UTF_8);
		return raw(port, ("POST /voice HTTP/1.1\r\nHost: " + host + "\r\nContent-Type: application/json\r\nContent-Length: " + b.length
				+ "\r\nConnection: close\r\n" + extraHeaders + "\r\n").getBytes(StandardCharsets.ISO_8859_1), b);
	}

	private static int statusOf(String response) {
		return Integer.parseInt(response.substring(9, 12));
	}

	@ParameterizedTest
	@ValueSource(strings = {"jdk", "socket"})
	void postGetAndPreflightWork(String kind) throws Exception {
		VoiceEndpoint e = endpoint(kind);
		int port = e.start(0, 0);
		try {
			assertTrue(port > 0);
			assertEquals(port, http.port());
			HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
			HttpResponse<String> r = c.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/voice"))
					.header("Content-Type", "application/json").header("Origin", "http://localhost:3000")
					.POST(HttpRequest.BodyPublishers.ofString(GOOD)).build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(200, r.statusCode());
			assertTrue(r.body().contains("rukia.shikai.hakuren"));
			assertEquals("http://localhost:3000", r.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
			assertEquals(1, backend.messages.size());

			HttpResponse<String> status = c.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/status")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(200, status.statusCode());
			assertTrue(status.body().contains("SHIKAI"));

			HttpResponse<String> page = c.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals("<html>bridge</html>", page.body());

			HttpResponse<String> pre = c.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/voice"))
					.header("Origin", "null").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(204, pre.statusCode());
			assertEquals("null", pre.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
		} finally {
			e.stop();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jdk", "socket"})
	void rejectsInvalidAndHostileRequests(String kind) throws Exception {
		VoiceEndpoint e = endpoint(kind);
		int port = e.start(0, 0);
		try {
			String lh = "127.0.0.1:" + port;
			assertEquals(200, statusOf(post(port, lh, GOOD, "")));
			// invalid
			assertEquals(400, statusOf(post(port, lh, "not json at all", "")));
			assertEquals(400, statusOf(post(port, lh, "{\"text\":42}", "")));
			// oversized: 10 KB against the 4 KB limit; the server must answer 413 and never hand it to the game
			int before = backend.messages.size();
			assertEquals(413, statusOf(post(port, lh, "{\"text\":\"" + "x".repeat(10_000) + "\"}", "")));
			assertEquals(before, backend.messages.size());
			// a huge one may cost the client its answer (connection reset), but must never reach the game or hurt the server
			try {
				post(port, lh, "{\"text\":\"" + "x".repeat(300_000) + "\"}", "");
			} catch (RuntimeException | IOException ignored) {
				// acceptable
			}
			assertEquals(before, backend.messages.size());
			// non-loopback Host / DNS rebinding
			assertEquals(403, statusOf(post(port, "evil.example:" + port, GOOD, "")));
			assertEquals(403, statusOf(post(port, "192.168.0.10:" + port, GOOD, "")));
			assertEquals(403, statusOf(post(port, "127.0.0.1:" + (port + 1), GOOD, "")));
			// foreign website
			assertEquals(403, statusOf(post(port, lh, GOOD, "Origin: https://evil.example\r\n")));
			assertEquals(before, backend.messages.size(), "none of the hostile requests reached the game");
			// wrong method / path
			assertEquals(405, statusOf(raw(port, "GET /voice HTTP/1.1\r\nHost: " + lh + "\r\nConnection: close\r\n\r\n")));
			assertEquals(404, statusOf(raw(port, "GET /etc/passwd HTTP/1.1\r\nHost: " + lh + "\r\nConnection: close\r\n\r\n")));
			// the server survived all of that
			assertEquals(200, statusOf(post(port, lh, GOOD, "")));
		} finally {
			e.stop();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jdk", "socket"})
	void aChunkedBodyIsNeverTrustedBeyondTheLimit(String kind) throws Exception {
		VoiceEndpoint e = endpoint(kind);
		int port = e.start(0, 0);
		try {
			String big = "{\"text\":\"" + "z".repeat(20_000) + "\"}";
			String chunked = "POST /voice HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nContent-Type: application/json\r\n"
					+ "Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n" + Integer.toHexString(big.length()) + "\r\n" + big + "\r\n0\r\n\r\n";
			int status = statusOf(raw(port, chunked));
			// the JDK server enforces the limit while reading (413); the socket fallback does not support chunked bodies (411)
			assertEquals(kind.equals("jdk") ? 413 : 411, status);
			assertTrue(backend.messages.isEmpty());
		} finally {
			e.stop();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jdk", "socket"})
	void listensOnLoopbackOnly(String kind) throws Exception {
		VoiceEndpoint e = endpoint(kind);
		int port = e.start(0, 0);
		try {
			List<InetAddress> lan = new ArrayList<>();
			for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (ni.isUp() && !ni.isLoopback()) {
					for (InetAddress a : Collections.list(ni.getInetAddresses())) {
						if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
							lan.add(a);
						}
					}
				}
			}
			Assumptions.assumeFalse(lan.isEmpty(), "this machine has no non-loopback IPv4 address to probe");
			for (InetAddress a : lan) {
				assertThrows(IOException.class, () -> {
					try (Socket s = new Socket()) {
						s.connect(new InetSocketAddress(a, port), 1500);
					}
				}, "reachable from " + a + " - the bridge must be loopback only");
			}
			// and it is reachable on 127.0.0.1
			try (Socket s = new Socket()) {
				s.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port), 1500);
			}
		} finally {
			e.stop();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jdk", "socket"})
	void stopsCleanlyAndTheSamePortCanBeReusedRightAway(String kind) throws Exception {
		int port;
		try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByAddress(new byte[] {127, 0, 0, 1}))) {
			port = probe.getLocalPort();
		}
		// like leaving and re-entering a world: stop, start again on the very same port, many times
		for (int i = 0; i < 6; i++) {
			VoiceEndpoint e = endpoint(kind);
			assertEquals(port, e.start(port, 0), "round " + i);
			assertEquals(200, statusOf(post(port, "127.0.0.1:" + port, GOOD, "")));
			e.stop();
			e.stop(); // idempotent
			assertThrows(ConnectException.class, () -> {
				try (Socket s = new Socket()) {
					s.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port), 1000);
				}
			}, "port still open after stop, round " + i);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jdk", "socket"})
	void aBusyPortFallsBackToTheNextOne(String kind) throws Exception {
		try (ServerSocket blocker = new ServerSocket(0, 1, InetAddress.getByAddress(new byte[] {127, 0, 0, 1}))) {
			int busy = blocker.getLocalPort();
			VoiceEndpoint e = endpoint(kind);
			int got;
			try {
				got = e.start(busy, 4);
			} catch (IOException ex) {
				Assumptions.abort("no free port next to " + busy);
				return;
			}
			try {
				assertNotEquals(busy, got);
				assertTrue(got > busy && got <= busy + 4, "got " + got + " for busy " + busy);
				assertEquals(got, http.port(), "Host check follows the real port");
				assertEquals(200, statusOf(post(got, "127.0.0.1:" + got, GOOD, "")));
			} finally {
				e.stop();
			}
			VoiceEndpoint noSearch = endpoint(kind);
			assertThrows(IOException.class, () -> noSearch.start(busy, 0), "no search range: report the failure");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jdk", "socket"})
	void survivesParallelRequests(String kind) throws Exception {
		VoiceEndpoint e = endpoint(kind);
		int port = e.start(0, 0);
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < 40; i++) {
				results.add(pool.submit(() -> statusOf(post(port, "127.0.0.1:" + port, GOOD, ""))));
			}
			for (Future<Integer> f : results) {
				assertEquals(200, f.get());
			}
			assertEquals(40, backend.messages.size());
		} finally {
			pool.shutdownNow();
			e.stop();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jdk", "socket"})
	void aSlowOrBrokenClientDoesNotBlockOthers(String kind) throws Exception {
		VoiceEndpoint e = endpoint(kind);
		int port = e.start(0, 0);
		try (Socket stall1 = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
				Socket stall2 = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port)) {
			stall1.getOutputStream().write("POST /voice HTTP/1.1\r\nHost: x".getBytes(StandardCharsets.ISO_8859_1)); // never finishes
			stall2.getOutputStream().write("garbage\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
			stall2.getOutputStream().flush();
			long t0 = System.nanoTime();
			assertEquals(200, statusOf(post(port, "127.0.0.1:" + port, GOOD, "")));
			assertTrue((System.nanoTime() - t0) / 1_000_000 < 2500, "a legitimate request waited for the stalled ones");
		} finally {
			e.stop();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jdk", "socket"})
	void localLatencyOfOneRoundTripIsSmall(String kind) throws Exception {
		VoiceEndpoint e = endpoint(kind);
		int port = e.start(0, 0);
		try {
			HttpClient c = HttpClient.newHttpClient();
			HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/voice")).header("Content-Type", "text/plain")
					.POST(HttpRequest.BodyPublishers.ofString(GOOD)).build();
			for (int i = 0; i < 10; i++) {
				c.send(req, HttpResponse.BodyHandlers.ofString());
			}
			long t0 = System.nanoTime();
			int n = 50;
			for (int i = 0; i < n; i++) {
				assertEquals(200, c.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
			}
			double ms = (System.nanoTime() - t0) / 1e6 / n;
			System.out.printf("[voice] %s endpoint: mean POST round trip %.2f ms over loopback (budget 10 ms in the ADR)%n", kind, ms);
			assertTrue(ms < 25, kind + " round trip " + ms + " ms");
		} finally {
			e.stop();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jdk"})
	void theFactoryPicksTheJdkServerWhenTheModuleIsThere(String kind) {
		endpoint(kind);
		assertEquals("jdk", VoiceEndpoint.create(http).kind());
		assertFalse(ModuleLayer.boot().findModule("jdk.httpserver").isEmpty());
	}
}
