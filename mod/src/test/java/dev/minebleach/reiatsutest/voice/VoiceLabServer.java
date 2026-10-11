package dev.minebleach.reiatsutest.voice;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.core.voice.GateContext;
import dev.minebleach.reiatsutest.core.voice.PhraseBook;
import dev.minebleach.reiatsutest.core.voice.PhraseMatcher;
import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import dev.minebleach.reiatsutest.core.voice.VoiceMessage;
import dev.minebleach.reiatsutest.core.voice.VoiceProcessor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Development lab, not part of the mod: the real bridge ({@link VoiceHttp} + transport) and the real matcher with NO game
 * behind it. The page is read from {@code voice-bridge/index.html} on every request (edit and reload). Every message is
 * matched in the ungated strict mode, so whatever command the recogniser's text means is reported (no state, item or
 * reiatsu rules, no debounce); nothing is fired anywhere. Used to check "speech engine output -> HTTP -> Java matcher"
 * with the synthetic fixtures of {@code tools/gen_voice_fixtures.ps1} and by the manual microphone checklist
 * ({@code docs/VOICE_QA.md}) when no world is open.
 *
 * <p>Run: {@code gradlew voiceLab} (optional {@code -Pport=47831}); stop with Ctrl+C.
 */
public final class VoiceLabServer {
	private VoiceLabServer() {
	}

	public static void main(String[] args) throws Exception {
		int port = args.length > 0 ? Integer.parseInt(args[0]) : 47831;
		Path pagePath = Paths.get("..", "voice-bridge", "index.html");
		VoiceConfig cfg = VoiceConfig.defaults();
		cfg.debounceMs = 0;
		cfg.globalGapMs = 0;
		cfg.consumedTextMs = 0;
		cfg.maxRequestsPerSecond = 200;
		PhraseBook book = PhraseBook.loadBundled();
		PhraseMatcher matcher = new PhraseMatcher(book, cfg.matcherConfig());
		VoiceProcessor proc = new VoiceProcessor(matcher, cfg, System::currentTimeMillis);

		VoiceBackend backend = new VoiceBackend() {
			@Override
			public String onVoice(VoiceMessage msg, long receivedNanos) {
				VoiceProcessor.Decision d = proc.process(msg, GateContext.ungated());
				JsonObject o = new JsonObject();
				o.addProperty("matched", d.fired());
				o.addProperty("command", d.commandId());
				o.addProperty("confidence", Math.round(d.confidence() * 1000.0) / 1000.0);
				o.addProperty("reason", d.outcome().wire());
				o.addProperty("text", d.normalized());
				if (d.fired()) {
					o.addProperty("result", "LAB_NOT_APPLIED");
					o.addProperty("accepted", true);
					o.addProperty("latencyMs", Math.round((System.nanoTime() - receivedNanos) / 10_000.0) / 100.0);
				}
				System.out.printf(Locale.ROOT, "[lab] \"%s\" (%s, final=%s) -> %s %s%n", msg.text(), msg.lang(), msg.isFinal(),
						d.outcome().wire(), d.commandId());
				return o.toString();
			}

			@Override
			public String status() {
				return "{\"ok\":true,\"world\":true,\"player\":\"lab\",\"state\":\"LAB\",\"held\":\"none\",\"say\":[]}";
			}
		};
		VoiceHttp http = new VoiceHttp(cfg, backend, () -> {
			try {
				return Files.readAllBytes(pagePath);
			} catch (java.io.IOException e) {
				return "page not found".getBytes(StandardCharsets.UTF_8);
			}
		});
		VoiceEndpoint ep = VoiceEndpoint.create(http);
		int bound = ep.start(port, 0);
		System.out.println("[lab] voice bridge lab on http://127.0.0.1:" + bound + "/  (transport " + ep.kind() + "), no game attached");
		System.out.println("[lab] open it in Chrome or Edge; Ctrl+C to stop");
		Thread.currentThread().join();
	}
}
