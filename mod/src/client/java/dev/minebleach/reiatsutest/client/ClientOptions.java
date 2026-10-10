package dev.minebleach.reiatsutest.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.ReiatsuTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client options file {@code config/reiatsu_test_client.json}: {@code {"show_first_person_hand": true}}. Created with the
 * defaults on first start. {@code -Dreiatsu.hand=false} overrides it for dev runs.
 */
public final class ClientOptions {
	/** Draw the player arm gripping the zanpakuto in first person. */
	public static volatile boolean showFirstPersonHand = true;

	/** Multiplier applied on top of the tuned first person display scale (config key first_person_scale_multiplier). */
	public static volatile float firstPersonScaleMultiplier = 1.0f;

	/** Duration of the draw / sheathe animation of the sealed zanpakuto in seconds; 0 = no animation (config key draw_animation_seconds). */
	public static volatile float drawSeconds = 0.45f;

	private ClientOptions() {
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("reiatsu_test_client.json");
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
			} else {
				Files.writeString(file, "{\n  \"show_first_person_hand\": true,\n  \"first_person_scale_multiplier\": 1.0,\n  \"draw_animation_seconds\": 0.45\n}\n");
			}
		} catch (IOException | RuntimeException e) {
			ReiatsuTest.LOGGER.warn("cannot read {}: {}", file, e.toString());
		}
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
