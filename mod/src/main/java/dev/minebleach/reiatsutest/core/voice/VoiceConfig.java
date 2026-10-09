package dev.minebleach.reiatsutest.core.voice;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code "voice"} section of {@code config/reiatsu_test.json}. Plain fields with defaults (Gson fills what the file
 * has); {@link #sanitize()} clamps everything to sane ranges so a hand-edited file cannot break the bridge.
 */
public final class VoiceConfig {
	public static final int DEFAULT_PORT = 47821;
	private static final Gson GSON = new Gson();

	/** Master switch: when false the HTTP bridge is not started. */
	public boolean enabled = true;
	/** Loopback port of the bridge; if busy the next {@link #portSearch} ports are tried. */
	public int port = DEFAULT_PORT;
	public int portSearch = 4;
	/** Confidence floor of a final result (ADR: 0.86). */
	public double threshold = 0.86;
	/** Required lead of the best command over the second best one. */
	public double margin = 0.03;
	/** Let a very confident, stable interim result fire the early-OK commands (never bankai or other big moves). */
	public boolean interimEnabled = false;
	public double interimThreshold = 0.97;
	/** The same command cannot fire again within this time (accepted or rejected by the server). */
	public int debounceMs = 1500;
	/** Minimum gap between any two commands. */
	public int globalGapMs = 300;
	/** A message that starts with the text of a command fired this recently has that prefix removed. */
	public int consumedTextMs = 1500;
	/** Accept the Origin "null" of a page opened from file://. */
	public boolean allowNullOrigin = true;
	public int maxBodyBytes = 4096;
	public int maxRequestsPerSecond = 20;
	/** Speech languages offered by the page. */
	public List<String> languages = new ArrayList<>(List.of("ja-JP", "en-US", "ru-RU"));
	public String defaultLanguage = "en-US";
	/** Serve the bridge page on GET / (same origin, stable microphone permission). */
	public boolean servePage = true;

	public static VoiceConfig defaults() {
		return new VoiceConfig();
	}

	/** Reads the "voice" object of the root config (missing = defaults). */
	public static VoiceConfig fromRoot(JsonObject root) {
		VoiceConfig c = root != null && root.has("voice") && root.get("voice").isJsonObject()
				? GSON.fromJson(root.getAsJsonObject("voice"), VoiceConfig.class) : new VoiceConfig();
		if (c == null) {
			c = new VoiceConfig();
		}
		return c.sanitize();
	}

	/** Writes this config as the "voice" object of the root (other keys are left alone). */
	public void writeInto(JsonObject root) {
		root.add("voice", GSON.toJsonTree(this));
	}

	public VoiceConfig sanitize() {
		port = port < 1024 || port > 65535 ? DEFAULT_PORT : port;
		portSearch = Math.max(0, Math.min(20, portSearch));
		threshold = clamp(threshold, 0.5, 1.0);
		margin = clamp(margin, 0.0, 0.5);
		interimThreshold = clamp(interimThreshold, 0.5, 1.0);
		debounceMs = Math.max(0, Math.min(60_000, debounceMs));
		globalGapMs = Math.max(0, Math.min(10_000, globalGapMs));
		consumedTextMs = Math.max(0, Math.min(60_000, consumedTextMs));
		maxBodyBytes = Math.max(256, Math.min(65_536, maxBodyBytes));
		maxRequestsPerSecond = Math.max(1, Math.min(1000, maxRequestsPerSecond));
		if (languages == null || languages.isEmpty()) {
			languages = new ArrayList<>(List.of("ja-JP", "en-US", "ru-RU"));
		}
		if (defaultLanguage == null || !languages.contains(defaultLanguage)) {
			defaultLanguage = languages.get(0);
		}
		return this;
	}

	public PhraseMatcher.Config matcherConfig() {
		return new PhraseMatcher.Config(threshold, margin);
	}

	private static double clamp(double v, double lo, double hi) {
		return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}
}
