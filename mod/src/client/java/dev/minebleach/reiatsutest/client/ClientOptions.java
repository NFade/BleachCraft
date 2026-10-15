package dev.minebleach.reiatsutest.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.config.ConfigFileIo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client options file {@code config/reiatsu_test_client.json}: {@code {"show_first_person_hand": true, ..., "voice": {"client": {"enabled": true, "port": 47821}}}}.
 * Created with the defaults on first start; a missing {@code voice} section is added to an existing file. {@code -Dreiatsu.hand=false}
 * overrides the hand option for dev runs. The {@code voice.client} section configures the player's own loopback voice bridge
 * (online voice); the server-side voice settings stay in {@code config/reiatsu_test.json}.
 */
public final class ClientOptions {
	/** Draw the player arm gripping the zanpakuto in first person. */
	public static volatile boolean showFirstPersonHand = true;

	/** Multiplier applied on top of the tuned first person display scale (config key first_person_scale_multiplier). */
	public static volatile float firstPersonScaleMultiplier = 1.0f;

	/** Duration of the draw / sheathe animation of the sealed zanpakuto in seconds; 0 = no animation (config key draw_animation_seconds). */
	public static volatile float drawSeconds = 0.45f;

	/**
	 * {@code uryu.bowHand} ({@code right} | {@code left}, default right): the hand of Uryu's bow. The bow is the MAIN hand item (vanilla pull), so the left
	 * hand version is the vanilla option Main Hand: Left, which mirrors every Uryu pose (third and first person); this key only documents the wish and logs
	 * a hint once when it is {@code left} and the main hand is right (U-FX 11, design/results/uryu_poses.md).
	 */
	public static volatile String uryuBowHand = "right";

	/** {@code voice.client.enabled}: start the local voice bridge on joining any server. */
	public static volatile boolean voiceClientEnabled = true;
	/** {@code voice.client.port}: loopback port of the bridge (the next {@link #voiceClientPortSearch} ports are tried when busy). */
	public static volatile int voiceClientPort = dev.minebleach.reiatsutest.core.voice.VoiceConfig.DEFAULT_PORT;
	public static volatile int voiceClientPortSearch = 4;

	private ClientOptions() {
	}

	private static JsonObject voiceDefaults() {
		JsonObject client = new JsonObject();
		client.addProperty("enabled", true);
		client.addProperty("port", dev.minebleach.reiatsutest.core.voice.VoiceConfig.DEFAULT_PORT);
		JsonObject voice = new JsonObject();
		voice.add("client", client);
		return voice;
	}

	/** Reads {@code voice.client.*}; anything missing or out of range keeps its default. */
	static void readVoice(JsonObject root) {
		if (!root.has("voice") || !root.get("voice").isJsonObject() || !root.getAsJsonObject("voice").has("client")
				|| !root.getAsJsonObject("voice").get("client").isJsonObject()) {
			return;
		}
		JsonObject c = root.getAsJsonObject("voice").getAsJsonObject("client");
		if (c.has("enabled")) {
			voiceClientEnabled = c.get("enabled").getAsBoolean();
		}
		if (c.has("port")) {
			int p = c.get("port").getAsInt();
			voiceClientPort = p < 1024 || p > 65535 ? dev.minebleach.reiatsutest.core.voice.VoiceConfig.DEFAULT_PORT : p;
		}
		if (c.has("portSearch")) {
			voiceClientPortSearch = Math.max(0, Math.min(20, c.get("portSearch").getAsInt()));
		}
	}

	public static void load() {
		ClientConfigFile.migrate(); // R11 review fix 8: stamp / migrate the client file before anyone reads it
		Path file = ClientConfigFile.file();
		synchronized (ClientConfigFile.lock()) {
			loadLocked(file);
		}
		applyDevProperties();
	}

	private static void loadLocked(Path file) {
		try {
			if (Files.exists(file)) {
				JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				if (o.has("show_first_person_hand")) {
					showFirstPersonHand = o.get("show_first_person_hand").getAsBoolean();
				}
				if (o.has("first_person_scale_multiplier")) {
					firstPersonScaleMultiplier = Math.max(0.1f, Math.min(4f, o.get("first_person_scale_multiplier").getAsFloat()));
				}
				if (o.has("draw_animation_seconds")) {
					drawSeconds = Math.max(0f, Math.min(3f, o.get("draw_animation_seconds").getAsFloat()));
				}
				if (o.has("uryu.bowHand")) {
					uryuBowHand = "left".equalsIgnoreCase(o.get("uryu.bowHand").getAsString()) ? "left" : "right";
				}
				readVoice(o);
				if (!o.has("voice")) {
					// older file without the voice section: add it with the defaults, keep everything else as it is
					o.add("voice", voiceDefaults());
					ConfigFileIo.writeJson(file, o);
				}
			} else {
				JsonObject o = new JsonObject();
				o.addProperty("show_first_person_hand", true);
				o.addProperty("first_person_scale_multiplier", 1.0);
				o.addProperty("draw_animation_seconds", 0.45);
				o.addProperty("uryu.bowHand", "right");
				o.add("voice", voiceDefaults());
				ClientConfigFile.stampFresh(o);
				ConfigFileIo.writeJson(file, o);
			}
		} catch (IOException | RuntimeException e) {
			ReiatsuTest.LOGGER.warn("cannot read {}: {}", file, e.toString());
		}
	}

	private static void applyDevProperties() {
		String mult = System.getProperty("reiatsu.fpScale");
		if (mult != null) {
			firstPersonScaleMultiplier = Float.parseFloat(mult);
		}
		String draw = System.getProperty("reiatsu.drawSeconds");
		if (draw != null) {
			drawSeconds = Float.parseFloat(draw);
		}
		String prop = System.getProperty("reiatsu.hand");
		if (prop != null) {
			showFirstPersonHand = Boolean.parseBoolean(prop);
		}
	}
}
