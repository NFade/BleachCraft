package dev.minebleach.reiatsutest.client.fx;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.ReiatsuTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client FX and HUD options (VFX_STORYBOARD 1.6, 7.11, 8.2) in {@code config/reiatsu_test_client.json}. A missing key is
 * added with its default, unknown keys (and {@code show_first_person_hand}) are kept; a file that does not parse is never
 * overwritten. {@code fxTier} LOW / MEDIUM / HIGH sets the quality dependent keys from the tier table 8.2 (the individual
 * keys in the file are then ignored), CUSTOM keeps them as written. Dev override for any key:
 * {@code -Dreiatsu.fx.<key>=value} (harness).
 */
public final class FxConfig {
	public enum Tier {
		LOW(0.25f, 400, 1024, 50, 100, 16, 16, 64, 6, 16, 1, 100, 16.0, 0.0),
		MEDIUM(0.5f, 800, 2048, 100, 200, 28, 32, 128, 9, 24, 2, 200, 24.0, 12.0),
		HIGH(1.0f, 1500, 4096, 200, 300, 40, 48, 256, 12, 32, 4, 400, 32.0, 16.0);

		public final float quality;
		public final int maxFxParticles;
		public final int maxGlowSprites;
		public final int bankaiBladeCount;
		public final int nearPetalCount;
		public final int maxCrystals;
		public final int maxIceShells;
		public final int maxShardMeshes;
		public final int decalRings;
		public final int decalSegments;
		public final int dustPerBlade;
		public final int dustCap;
		public final double bladeLodDistance;
		public final double bladeEmissiveDistance;

		Tier(float quality, int maxFxParticles, int maxGlowSprites, int bankaiBladeCount, int nearPetalCount, int maxCrystals,
				int maxIceShells, int maxShardMeshes, int decalRings, int decalSegments, int dustPerBlade, int dustCap,
				double bladeLodDistance, double bladeEmissiveDistance) {
			this.quality = quality;
			this.maxFxParticles = maxFxParticles;
			this.maxGlowSprites = maxGlowSprites;
			this.bankaiBladeCount = bankaiBladeCount;
			this.nearPetalCount = nearPetalCount;
			this.maxCrystals = maxCrystals;
			this.maxIceShells = maxIceShells;
			this.maxShardMeshes = maxShardMeshes;
			this.decalRings = decalRings;
			this.decalSegments = decalSegments;
			this.dustPerBlade = dustPerBlade;
			this.dustCap = dustCap;
			this.bladeLodDistance = bladeLodDistance;
			this.bladeEmissiveDistance = bladeEmissiveDistance;
		}
	}

	public enum TierSetting { LOW, MEDIUM, HIGH, CUSTOM }

	// FX (1.6)
	public static volatile TierSetting fxTier = TierSetting.HIGH;
	public static volatile float effectQuality = 1.0f;
	public static volatile int maxPetals = 3000;
	public static volatile int bankaiBladeCount = 200;
	public static volatile double bladeLodDistance = 32;
	public static volatile int nearPetalCount = 300;
	public static volatile double emissiveMultiplier = 1.0;
	public static volatile boolean reduceMotion = false;
	public static volatile double wingScale = 1.0;
	public static volatile int maxFxParticles = 1500;
	public static volatile int maxGlowSprites = 4096;
	public static volatile int maxIceShells = 48;
	public static volatile int maxCrystals = 40;
	public static volatile int maxShardMeshes = 256;
	public static volatile double bladeCullDistance = 160;
	public static volatile double screenFxScale = 1.0;
	public static volatile double shakeScale = 1.0;
	public static volatile double fxSoundVolume = 1.0;
	public static volatile double glowIntensity = 1.0;
	public static volatile double gradeStrength = 1.0;
	public static volatile boolean decals = true;
	public static volatile boolean photosensitiveSafe = false;
	public static volatile int maxConcurrentFx = 8;
	// HUD (7.11)
	public static volatile boolean hud = true;
	public static volatile boolean hudCompact = false;
	public static volatile boolean titleCards = true;
	public static volatile boolean crosshairPips = true;
	public static volatile boolean voiceHud = true;
	public static volatile boolean voiceToastSound = true;
	public static volatile boolean cooldownReadySound = true;

	private FxConfig() {
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("reiatsu_test_client.json");
	}

	/** The tier table row in force (HIGH values for CUSTOM). */
	public static Tier tier() {
		return switch (fxTier) {
			case LOW -> Tier.LOW;
			case MEDIUM -> Tier.MEDIUM;
			default -> Tier.HIGH;
		};
	}

	public static void load() {
		Path file = file();
		JsonObject root = new JsonObject();
		boolean writable = true;
		try {
			if (Files.isRegularFile(file)) {
				JsonElement el = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				if (el.isJsonObject()) {
					root = el.getAsJsonObject();
				} else {
					writable = false;
				}
			}
		} catch (IOException | RuntimeException e) {
			ReiatsuTest.LOGGER.warn("[fxconfig] {} is not readable ({}), using defaults and leaving it alone", file, e.toString());
			writable = false;
		}
		boolean added = false;
		Map<String, Object> defaults = defaults();
		for (Map.Entry<String, Object> d : defaults.entrySet()) {
			if (!root.has(d.getKey())) {
				put(root, d.getKey(), d.getValue());
				added = true;
			}
		}
		apply(root);
		if (added && writable) {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root) + System.lineSeparator(),
						StandardCharsets.UTF_8);
			} catch (IOException e) {
				ReiatsuTest.LOGGER.warn("[fxconfig] could not write {}: {}", file, e.toString());
			}
		}
		ReiatsuTest.LOGGER.info("[fxconfig] tier {} quality {} particles {} glow {} reduceMotion {} hud {}", fxTier, effectQuality,
				maxFxParticles, maxGlowSprites, reduceMotion, hud);
	}

	private static Map<String, Object> defaults() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("fxTier", "HIGH");
		m.put("effectQuality", 1.0);
		m.put("maxPetals", 3000);
		m.put("bankaiBladeCount", 200);
		m.put("bladeLodDistance", 32.0);
		m.put("nearPetalCount", 300);
		m.put("emissiveMultiplier", 1.0);
		m.put("reduceMotion", false);
		m.put("wingScale", 1.0);
		m.put("maxFxParticles", 1500);
		m.put("maxGlowSprites", 4096);
		m.put("maxIceShells", 48);
		m.put("maxCrystals", 40);
		m.put("maxShardMeshes", 256);
		m.put("bladeCullDistance", 160.0);
		m.put("screenFxScale", 1.0);
		m.put("shakeScale", 1.0);
		m.put("fxSoundVolume", 1.0);
		m.put("glowIntensity", 1.0);
		m.put("gradeStrength", 1.0);
		m.put("decals", true);
		m.put("photosensitiveSafe", false);
		m.put("maxConcurrentFx", 8);
		m.put("hud", true);
		m.put("hudCompact", false);
		m.put("titleCards", true);
		m.put("crosshairPips", true);
		m.put("voiceHud", true);
		m.put("voiceToastSound", true);
		m.put("cooldownReadySound", true);
		return m;
	}

	private static void put(JsonObject o, String k, Object v) {
		if (v instanceof Boolean b) {
			o.addProperty(k, b);
		} else if (v instanceof Number n) {
			o.addProperty(k, n);
		} else {
			o.addProperty(k, String.valueOf(v));
		}
	}

	private static void apply(JsonObject root) {
		// dev overrides first (they win over the file and over the tier)
		for (String key : defaults().keySet()) {
			String prop = System.getProperty("reiatsu.fx." + key);
			if (prop != null) {
				root.addProperty(key, prop);
			}
		}
		fxTier = parseTier(str(root, "fxTier", "HIGH"));
		effectQuality = (float) num(root, "effectQuality", 1.0);
		maxPetals = (int) num(root, "maxPetals", 3000);
		bankaiBladeCount = Math.min(1000, (int) num(root, "bankaiBladeCount", 200));
		bladeLodDistance = num(root, "bladeLodDistance", 32);
		nearPetalCount = (int) num(root, "nearPetalCount", 300);
		emissiveMultiplier = num(root, "emissiveMultiplier", 1.0);
		reduceMotion = bool(root, "reduceMotion", false);
		wingScale = num(root, "wingScale", 1.0);
		maxFxParticles = (int) num(root, "maxFxParticles", 1500);
		maxGlowSprites = (int) num(root, "maxGlowSprites", 4096);
		maxIceShells = (int) num(root, "maxIceShells", 48);
		maxCrystals = (int) num(root, "maxCrystals", 40);
		maxShardMeshes = (int) num(root, "maxShardMeshes", 256);
		bladeCullDistance = num(root, "bladeCullDistance", 160);
		screenFxScale = num(root, "screenFxScale", 1.0);
		shakeScale = num(root, "shakeScale", 1.0);
		fxSoundVolume = num(root, "fxSoundVolume", 1.0);
		glowIntensity = num(root, "glowIntensity", 1.0);
		gradeStrength = num(root, "gradeStrength", 1.0);
		decals = bool(root, "decals", true);
		photosensitiveSafe = bool(root, "photosensitiveSafe", false);
		maxConcurrentFx = Math.max(1, (int) num(root, "maxConcurrentFx", 8));
		hud = bool(root, "hud", true);
		hudCompact = bool(root, "hudCompact", false);
		titleCards = bool(root, "titleCards", true);
		crosshairPips = bool(root, "crosshairPips", true);
		voiceHud = bool(root, "voiceHud", true);
		voiceToastSound = bool(root, "voiceToastSound", true);
		cooldownReadySound = bool(root, "cooldownReadySound", true);
		if (fxTier != TierSetting.CUSTOM) {
			Tier t = tier();
			effectQuality = t.quality;
			maxFxParticles = t.maxFxParticles;
			maxGlowSprites = t.maxGlowSprites;
			bankaiBladeCount = t.bankaiBladeCount;
			nearPetalCount = t.nearPetalCount;
			maxCrystals = t.maxCrystals;
			maxIceShells = t.maxIceShells;
			maxShardMeshes = t.maxShardMeshes;
			bladeLodDistance = t.bladeLodDistance;
		}
	}

	private static TierSetting parseTier(String s) {
		try {
			return TierSetting.valueOf(s.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return TierSetting.HIGH;
		}
	}

	private static String str(JsonObject o, String k, String d) {
		try {
			return o.has(k) ? o.get(k).getAsString() : d;
		} catch (RuntimeException e) {
			return d;
		}
	}

	private static double num(JsonObject o, String k, double d) {
		try {
			return o.has(k) ? o.get(k).getAsDouble() : d;
		} catch (RuntimeException e) {
			return d;
		}
	}

	private static boolean bool(JsonObject o, String k, boolean d) {
		try {
			return o.has(k) ? o.get(k).getAsBoolean() : d;
		} catch (RuntimeException e) {
			return d;
		}
	}
}
