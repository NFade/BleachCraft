package dev.minebleach.reiatsutest.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * First person held pose numbers (B4 polish): sword lift and fist position in the hand frame, forearm roll. They come from
 * the manifest ({@code draw.held}, {@code draw.stow.arm_roll_*}); in a development environment the file
 * {@code <game dir>/pose_override.json} replaces any of them and is re-read when it changes (checked a few times a second), so
 * a pose can be tuned without restarting the game or reloading resources:
 * {@code {"lift": [x, y, z], "arm": [x, y, z], "roll_stow": 80, "roll_held": 0}} (all keys optional, hand frame blocks of the
 * right-handed numbers, degrees).
 */
public final class HeldPose {
	public record Values(float[] lift, float[] arm, float rollStow, float rollHeld) {
	}

	private static final boolean DEV = FabricLoader.getInstance().isDevelopmentEnvironment();
	private static volatile JsonObject override;
	private static long lastCheckNanos;
	private static long lastModified = Long.MIN_VALUE;

	private HeldPose() {
	}

	/** Re-reads the override file when it changed (dev environment only). */
	public static void poll() {
		if (!DEV) {
			return;
		}
		long now = System.nanoTime();
		if (now - lastCheckNanos < 150_000_000L) {
			return;
		}
		lastCheckNanos = now;
		try {
			Path f = FabricLoader.getInstance().getGameDir().resolve("pose_override.json");
			if (!Files.exists(f)) {
				override = null;
				lastModified = Long.MIN_VALUE;
				return;
			}
			long lm = Files.getLastModifiedTime(f).toMillis() ^ Files.size(f);
			if (lm != lastModified) {
				lastModified = lm;
				override = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
				ReiatsuTest.LOGGER.info("[pose] override {}", override);
			}
		} catch (Exception e) {
			ReiatsuTest.LOGGER.warn("[pose] cannot read pose_override.json: {}", e.toString());
		}
	}

	private static float[] vec(JsonObject o, String key, float[] def) {
		if (o == null || !o.has(key)) {
			return def;
		}
		JsonArray a = o.getAsJsonArray(key);
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	public static Values of(ItemManifest man) {
		JsonObject o = override;
		ItemManifest.Held h = man.held;
		float rs = man.stow != null ? man.stow.armRollStow() : 0f;
		float rh = man.stow != null ? man.stow.armRollHeld() : 0f;
		if (o != null && o.has("roll_stow")) {
			rs = o.get("roll_stow").getAsFloat();
		}
		if (o != null && o.has("roll_held")) {
			rh = o.get("roll_held").getAsFloat();
		}
		return new Values(vec(o, "lift", h.lift()), vec(o, "arm", h.arm()), rs, rh);
	}
}
