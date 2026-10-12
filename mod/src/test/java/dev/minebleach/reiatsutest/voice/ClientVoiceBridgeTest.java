package dev.minebleach.reiatsutest.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.core.voice.GateContext;
import dev.minebleach.reiatsutest.core.voice.PhraseBook;
import dev.minebleach.reiatsutest.core.voice.PhraseMatcher;
import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import dev.minebleach.reiatsutest.core.voice.VoiceFeedback;
import dev.minebleach.reiatsutest.core.voice.VoiceFeedback.Verdict;
import dev.minebleach.reiatsutest.core.voice.VoiceIngest;
import dev.minebleach.reiatsutest.core.voice.VoiceMessage;
import dev.minebleach.reiatsutest.core.voice.VoiceRequest;
import dev.minebleach.reiatsutest.core.voice.VoiceWire;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The client bridge without a game: a fake link plays the network, and an end to end test wires two bridges (two players) over
 * real loopback HTTP to one in-process server side ({@link VoiceIngest}).
 */
class ClientVoiceBridgeTest {
	private static final long NO_RECEIPT = System.nanoTime();

	/** A scripted link: records what would go to the server and answers as the test says. */
	static final class FakeLink implements ClientVoiceBridge.Link {
		boolean inWorld = true;
		ClientVoiceBridge.Status status = new ClientVoiceBridge.Status(true, true, "Haru", ZanpakutoState.SHIKAI, CharacterId.RUKIA, false);
		final List<VoiceRequest> sent = new CopyOnWriteArrayList<>();
		ClientVoiceBridge bridge;
		java.util.function.Function<VoiceRequest, VoiceFeedback> answer = r -> null;

		@Override
		public boolean inWorld() {
			return inWorld;
		}

		@Override
		public void send(VoiceRequest request) {
			sent.add(request);
			VoiceFeedback fb = answer.apply(request);
			if (fb != null) {
				bridge.onFeedback(fb);
			}
		}

		@Override
		public ClientVoiceBridge.Status status() {
			return inWorld ? status : ClientVoiceBridge.Status.NOT_IN_WORLD;
		}
	}

	private FakeLink link;
	private ClientVoiceBridge bridge;

	@BeforeEach
	void setUp() {
		VoiceHudState.reset();
		link = new FakeLink();
		bridge = new ClientVoiceBridge(link, PhraseBook.loadBundled(), VoiceConfig.defaults(), System::currentTimeMillis, 200);
		link.bridge = bridge;
		bridge.setEndpoint(47821, "jdk");
	}

	@AfterEach
	void tearDown() {
		VoiceHudState.reset();
	}

	private static VoiceMessage msg(String text, boolean fin) {
		return new VoiceMessage(text, fin, "en-US", 5, 0);
	}

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void aFiredCommandIsAnsweredInTheFormatOfThePage() {
		link.answer = r -> new VoiceFeedback(r.reqId(), Verdict.OK, "rukia.shikai.hakuren", "hakuren", "fired", ResultCode.OK.code(), 1000, "SHIKAI");
		JsonObject o = json(bridge.onVoice(msg("Hakuren", true), System.nanoTime()));
		assertTrue(o.get("matched").getAsBoolean());
		assertTrue(o.get("accepted").getAsBoolean());
		assertEquals("rukia.shikai.hakuren", o.get("command").getAsString());
		assertEquals("OK", o.get("result").getAsString());
		assertEquals("fired", o.get("reason").getAsString());
		assertEquals("SHIKAI", o.get("state").getAsString());
		assertEquals(1.0, o.get("confidence").getAsDouble(), 1e-9);
		assertTrue(o.has("latencyMs"));
		// the text went to the server as a plain request, nothing else
		assertEquals(1, link.sent.size());
		VoiceRequest r = link.sent.get(0);
		assertEquals("Hakuren", r.text());
		assertTrue(r.isFinal());
		assertEquals("en-US", r.lang());
		assertEquals(5, r.utt());
		assertEquals(VoiceWire.PROTOCOL, r.protocol());
		// HUD of the player: accepted
		VoiceHudState h = VoiceHudState.get();
		assertEquals(VoiceHudState.Result.ACCEPTED, h.result());
		assertEquals("Hakuren", h.finalText());
		assertEquals("rukia.shikai.hakuren", h.commandId());
		assertEquals(0, bridge.pendingCount());
	}

	@Test
	void aDeniedCommandShowsTheServersReason() {
		link.answer = r -> new VoiceFeedback(r.reqId(), Verdict.DENIED, "byakuya.bankai.release", "bankai", "fired",
				ResultCode.DENIED_REIATSU.code(), 990, "SHIKAI");
		JsonObject o = json(bridge.onVoice(msg("Bankai", true), System.nanoTime()));
		assertTrue(o.get("matched").getAsBoolean());
		assertFalse(o.get("accepted").getAsBoolean());
		assertEquals("DENIED_REIATSU", o.get("result").getAsString());
		assertEquals(VoiceHudState.Result.DENIED_REIATSU, VoiceHudState.get().result());
	}

	@Test
	void nonMatchesAndGatedPhrasesReachTheHudOnlyForFinals() {
		link.answer = r -> new VoiceFeedback(r.reqId(), Verdict.NO_MATCH, null, "salt", "no_match", -1, 0, "BASE");
		JsonObject o = json(bridge.onVoice(msg("pass the salt", true), System.nanoTime()));
		assertFalse(o.get("matched").getAsBoolean());
		assertEquals("no_match", o.get("reason").getAsString());
		assertEquals(VoiceHudState.Result.NO_MATCH, VoiceHudState.get().result());

		VoiceHudState.reset();
		link.answer = r -> new VoiceFeedback(r.reqId(), Verdict.GATED_OUT, "rukia.shikai.hakuren", "hakuren", "gated_out", -1, 1000, "BASE");
		bridge.onVoice(msg("hakuren", false), System.nanoTime());
		assertEquals(VoiceHudState.Result.NONE, VoiceHudState.get().result(), "an interim gated result is not a verdict");
		assertEquals("hakuren", VoiceHudState.get().interim(), "the interim text is shown");
		bridge.onVoice(msg("hakuren", true), System.nanoTime());
		assertEquals(VoiceHudState.Result.GATED, VoiceHudState.get().result());
	}

	@Test
	void ignoredAnswersLeaveTheHudAlone() {
		link.answer = r -> new VoiceFeedback(r.reqId(), Verdict.IGNORED, "rukia.shikai.hakuren", "hakuren", "debounced", -1, 1000, "SHIKAI");
		JsonObject o = json(bridge.onVoice(msg("hakuren", true), System.nanoTime()));
		assertFalse(o.get("matched").getAsBoolean());
		assertEquals("debounced", o.get("reason").getAsString());
		assertEquals(VoiceHudState.Result.NONE, VoiceHudState.get().result());
	}

	@Test
	void textsAreCheckedBeforeTheyLeaveThePc() {
		link.answer = r -> new VoiceFeedback(r.reqId(), Verdict.NO_MATCH, null, "", "no_match", -1, 0, "");
		assertEquals("too_long", json(bridge.onVoice(msg("x".repeat(VoiceWire.MAX_TEXT_CHARS + 1), true), NO_RECEIPT)).get("reason").getAsString());
		assertEquals("empty", json(bridge.onVoice(msg("  ", true), NO_RECEIPT)).get("reason").getAsString());
		assertTrue(link.sent.isEmpty());
		// alternatives: at most three, none blank or too long
		List<String> alts = List.of("a", " ", "b", "x".repeat(VoiceWire.MAX_TEXT_CHARS + 1), "c", "d");
		bridge.onVoice(new VoiceMessage("hakuren", true, "en-US", 1, 0, alts), NO_RECEIPT);
		assertEquals(List.of("a", "b", "c"), link.sent.get(0).alts());
	}

	@Test
	void notInAWorldNothingIsSent() {
		link.inWorld = false;
		assertEquals("no_world", json(bridge.onVoice(msg("hakuren", true), NO_RECEIPT)).get("reason").getAsString());
		assertTrue(link.sent.isEmpty());
	}

	@Test
	void aSilentServerGivesServerBusyAndALateAnswerStillUpdatesTheHud() {
		JsonObject o = json(bridge.onVoice(msg("hakuren", true), System.nanoTime())); // nobody answers, waits 200 ms
		assertEquals("server_busy", o.get("reason").getAsString());
		assertEquals(1, bridge.pendingCount());
		int id = link.sent.get(0).reqId();
		bridge.onFeedback(new VoiceFeedback(id, Verdict.OK, "rukia.shikai.hakuren", "hakuren", "fired", ResultCode.OK.code(), 1000, "SHIKAI"));
		assertEquals(VoiceHudState.Result.ACCEPTED, VoiceHudState.get().result());
		assertEquals(0, bridge.pendingCount());
		// an answer nobody asked for is dropped quietly
		bridge.onFeedback(new VoiceFeedback(9999, Verdict.OK, "x", "", "fired", 0, 0, ""));
		assertEquals(0, bridge.pendingCount());
	}

	@Test
	void theServerAnnouncesTheCommandBeforeItsActionResult() {
		assertNull(VoiceHudState.pendingCommand(2000));
		bridge.onFeedback(VoiceFeedback.pending(1, "common.seal"));
		assertEquals("common.seal", VoiceHudState.pendingCommand(2000));
		assertEquals(0, bridge.pendingCount(), "a pending notice is not an answer");
	}

	@Test
	void aServerThatRefusesVoiceIsReportedOnceAndInTheStatus() {
		List<String> notices = new ArrayList<>();
		bridge.setNotice(notices::add);
		link.answer = r -> VoiceFeedback.rejected(r.reqId(), "disabled");
		JsonObject o = json(bridge.onVoice(msg("hakuren", true), System.nanoTime()));
		assertFalse(o.get("matched").getAsBoolean());
		assertEquals("disabled", o.get("reason").getAsString());
		bridge.onVoice(msg("hakuren", true), System.nanoTime());
		assertEquals(List.of("disabled"), notices);
		JsonObject st = json(bridge.status());
		assertTrue(st.get("world").getAsBoolean());
		assertFalse(st.get("serverVoice").getAsBoolean());
		assertEquals("rejected", st.getAsJsonObject("last").get("verdict").getAsString());
	}

	@Test
	void statusListsThePhrasesThatWorkRightNow() {
		JsonObject st = json(bridge.status());
		assertTrue(st.get("ok").getAsBoolean());
		assertTrue(st.get("world").getAsBoolean());
		assertEquals("Haru", st.get("player").getAsString());
		assertEquals("SHIKAI", st.get("state").getAsString());
		assertEquals("rukia", st.get("held").getAsString());
		assertEquals(47821, st.get("port").getAsInt());
		assertEquals("en-US", st.get("defaultLanguage").getAsString());
		assertTrue(st.getAsJsonArray("say").toString().contains("rukia.shikai.hakuren"), st.toString());
		assertFalse(st.getAsJsonArray("say").toString().contains("byakuya"), "only what the sword in hand can do");

		link.status = new ClientVoiceBridge.Status(true, true, "Haru", ZanpakutoState.SEALED, CharacterId.NONE, false);
		JsonObject sealed = json(bridge.status());
		assertTrue(sealed.getAsJsonArray("say").isEmpty() || !sealed.getAsJsonArray("say").toString().contains("hakuren"));
		assertEquals("none", sealed.get("held").getAsString());
	}

	@Test
	void statusWithoutAWorldOrWithoutServerSupport() {
		link.inWorld = false;
		JsonObject none = json(bridge.status());
		assertFalse(none.get("world").getAsBoolean());
		link.inWorld = true;
		link.status = new ClientVoiceBridge.Status(true, false, "Haru", null, null, false);
		JsonObject plain = json(bridge.status());
		assertFalse(plain.get("world").getAsBoolean());
		assertEquals("server_without_voice", plain.get("reason").getAsString());
		// before the first state sync there is a player but no state
		link.status = new ClientVoiceBridge.Status(true, true, "Haru", null, null, false);
		JsonObject early = json(bridge.status());
		assertTrue(early.get("world").getAsBoolean());
		assertFalse(early.has("state"));
	}

	@Test
	void theMicStateOfThePageReachesTheHud() {
		bridge.contact("hearing");
		assertEquals(VoiceHudState.Mic.HEARING, VoiceHudState.get().mic());
		bridge.contact("on");
		assertEquals(VoiceHudState.Mic.LISTENING, VoiceHudState.get().mic());
	}

	// ------------------------------------------------------------------ end to end: two players, real loopback HTTP

	/** The server side for two players in one process; the "network" is an executor, answers arrive on another thread. */
	static final class Server implements VoiceIngest.Host {
		final Map<UUID, GateContext> gates = new HashMap<>();
		final List<String> executed = new CopyOnWriteArrayList<>();
		final VoiceIngest ingest;
		final ExecutorService serverThread = Executors.newSingleThreadExecutor();

		Server(VoiceConfig cfg) {
			ingest = new VoiceIngest(cfg, new PhraseMatcher(PhraseBook.loadBundled(), cfg.matcherConfig()), this);
		}

		@Override
		public GateContext gate(UUID player) {
			return gates.get(player);
		}

		@Override
		public ResultCode execute(UUID player, String commandId, int reqId) {
			executed.add(player + ":" + commandId);
			return ResultCode.OK;
		}

		@Override
		public String stateName(UUID player) {
			return gates.get(player).state().name();
		}
	}

	private static final class Player implements ClientVoiceBridge.Link {
		final UUID id = UUID.randomUUID();
		final Server server;
		final String name;
		ClientVoiceBridge bridge;
		VoiceEndpoint endpoint;
		int port;

		Player(Server server, String name) {
			this.server = server;
			this.name = name;
		}

		@Override
		public boolean inWorld() {
			return true;
		}

		@Override
		public void send(VoiceRequest request) {
			server.serverThread.execute(() -> {
				VoiceIngest.Handled h = server.ingest.handle(id, name, request);
				bridge.onFeedback(h.feedback());
			});
		}

		@Override
		public ClientVoiceBridge.Status status() {
			GateContext g = server.gates.get(id);
			return new ClientVoiceBridge.Status(true, true, name, g.state(), g.held(), g.reiatsuFull());
		}

		void start(VoiceConfig cfg) throws IOException {
			bridge = new ClientVoiceBridge(this, PhraseBook.loadBundled(), cfg, System::currentTimeMillis, 2000);
			VoiceHttp http = new VoiceHttp(cfg, bridge, VoicePage::bytes);
			endpoint = VoiceEndpoint.create(http);
			port = endpoint.start(0, 0);
			bridge.setEndpoint(port, endpoint.kind());
		}

		String post(String text) throws Exception {
			String body = "{\"text\":\"" + text + "\",\"final\":true,\"lang\":\"en-US\",\"utt\":" + utt.incrementAndGet() + ",\"t\":1}";
			HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/voice")).timeout(Duration.ofSeconds(5))
					.header("Content-Type", "text/plain;charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			assertEquals(200, r.statusCode());
			return r.body();
		}

		final AtomicInteger utt = new AtomicInteger();
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

	@Test
	void twoPlayersOverRealHttpEachRunTheirOwnCommands() throws Exception {
		VoiceConfig serverCfg = VoiceConfig.defaults();
		VoiceConfig clientCfg = VoiceConfig.defaults();
		Server server = new Server(serverCfg);
		Player a = new Player(server, "Alice");
		Player b = new Player(server, "Bob");
		server.gates.put(a.id, GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, false));
		server.gates.put(b.id, GateContext.of(CharacterId.BYAKUYA, ZanpakutoState.BASE, false));
		try {
			a.start(clientCfg);
			b.start(clientCfg);
			assertTrue(a.port != b.port);
			JsonObject ra = json(a.post("Hakuren"));
			assertTrue(ra.get("matched").getAsBoolean(), ra.toString());
			assertEquals("rukia.shikai.hakuren", ra.get("command").getAsString());
			assertEquals("SHIKAI", ra.get("state").getAsString());
			// Bob says the same word with the wrong sword: nothing runs for him, and Alice's command stays hers
			JsonObject rb = json(b.post("Hakuren"));
			assertFalse(rb.get("matched").getAsBoolean(), rb.toString());
			assertEquals("gated_out", rb.get("reason").getAsString());
			JsonObject rb2 = json(b.post("chire senbonzakura"));
			assertTrue(rb2.get("matched").getAsBoolean(), rb2.toString());
			assertEquals("BASE", rb2.get("state").getAsString());
			assertEquals(List.of(a.id + ":rukia.shikai.hakuren", b.id + ":byakuya.shikai.release"), server.executed);
			// the status of each bridge is its own player's
			assertEquals("Alice", json(get(a.port, "/status")).get("player").getAsString());
			assertEquals("Bob", json(get(b.port, "/status")).get("player").getAsString());
			assertTrue(get(a.port, "/").contains("<"), "the page is served by the bridge");
		} finally {
			for (Player p : new Player[] {a, b}) {
				if (p.endpoint != null) {
					p.endpoint.stop();
				}
			}
			server.serverThread.shutdownNow();
		}
	}

	@Test
	void theBridgeKeepsTheLoopbackGuardsOfTheOldBridge() throws Exception {
		Server server = new Server(VoiceConfig.defaults());
		Player a = new Player(server, "Alice");
		server.gates.put(a.id, GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, false));
		try {
			a.start(VoiceConfig.defaults());
			// a web page of another site must not drive the bridge
			HttpResponse<String> evil = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + a.port + "/voice"))
					.header("Origin", "https://evil.example").header("Content-Type", "text/plain")
					.POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"hakuren\",\"final\":true}")).build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(403, evil.statusCode());
			assertTrue(server.executed.isEmpty());
		} finally {
			a.endpoint.stop();
			server.serverThread.shutdownNow();
		}
	}

	@Test
	void theBridgeStartsAndStopsWithTheConnection() throws Exception {
		Server server = new Server(VoiceConfig.defaults());
		Player a = new Player(server, "Alice");
		server.gates.put(a.id, GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, false));
		try {
			a.start(VoiceConfig.defaults());
			int port = a.port;
			assertTrue(port > 0);
			assertTrue(get(port, "/status").contains("\"ok\":true"));
			a.endpoint.stop(); // leaving the server
			assertThrows(IOException.class, () -> get(port, "/status"), "the port is released");
			a.endpoint.stop(); // stopping twice is harmless
			a.start(VoiceConfig.defaults()); // joining again starts a fresh bridge
			assertTrue(get(a.port, "/status").contains("\"ok\":true"));
		} finally {
			a.endpoint.stop();
			server.serverThread.shutdownNow();
		}
	}

	@Test
	void theBridgeUsesTheNextPortWhenTheFirstIsBusy() throws Exception {
		VoiceConfig cfg = VoiceConfig.defaults();
		cfg.port = 47990;
		cfg.portSearch = 4;
		Server server = new Server(cfg);
		Player a = new Player(server, "Alice");
		Player b = new Player(server, "Bob");
		a.bridge = new ClientVoiceBridge(a, PhraseBook.loadBundled(), cfg, System::currentTimeMillis, 100);
		b.bridge = new ClientVoiceBridge(b, PhraseBook.loadBundled(), cfg, System::currentTimeMillis, 100);
		VoiceEndpoint e1 = VoiceEndpoint.create(new VoiceHttp(cfg, a.bridge, VoicePage::bytes));
		VoiceEndpoint e2 = VoiceEndpoint.create(new VoiceHttp(cfg, b.bridge, VoicePage::bytes));
		try {
			int p1;
			try {
				p1 = e1.start(cfg.port, cfg.portSearch);
			} catch (IOException busy) {
				org.junit.jupiter.api.Assumptions.abort("test ports are busy on this machine");
				return;
			}
			int p2 = e2.start(cfg.port, cfg.portSearch);
			assertTrue(p2 > p1 && p2 <= cfg.port + cfg.portSearch, "second client on the same PC takes the next port: " + p1 + " " + p2);
		} finally {
			e1.stop();
			e2.stop();
			server.serverThread.shutdownNow();
		}
	}

	private static String get(int port, String path) throws Exception {
		try {
			return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(3)).GET().build(),
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
		} catch (ConnectException e) {
			throw new IOException(e);
		}
	}
}
