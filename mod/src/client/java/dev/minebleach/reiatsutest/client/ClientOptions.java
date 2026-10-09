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
			} else {
				Files.writeString(file, "{\n  \"show_first_person_hand\": true\n}\n");
			}
		} catch (IOException | RuntimeException e) {
			ReiatsuTest.LOGGER.warn("cannot read {}: {}", file, e.toString());
		}
		String prop = System.getProperty("reiatsu.hand");
		if (prop != null) {
			showFirstPersonHand = Boolean.parseBoolean(prop);
		}
	}
}
