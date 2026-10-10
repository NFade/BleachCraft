package dev.minebleach.reiatsutest.client.hud;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;

/** Translation lookups cached per language (the HUD asks for the same few strings every frame). */
final class HudText {
	private static final Map<String, String> CACHE = new HashMap<>();
	private static String lang = "";
	private static int generation;

	private HudText() {
	}

	private static void check() {
		String l = MinecraftClient.getInstance().options.language;
		if (!l.equals(lang)) {
			lang = l;
			CACHE.clear();
			generation++;
		}
	}

	/** Changes whenever the language does (cache keys of composed texts include it). */
	static int generation() {
		check();
		return generation;
	}

	static String tr(String key, Object... args) {
		check();
		String ck = args.length == 0 ? key : key + java.util.Arrays.toString(args);
		String v = CACHE.get(ck);
		if (v == null) {
			v = I18n.translate(key, args);
			if (CACHE.size() > 256) {
				CACHE.clear();
			}
			CACHE.put(ck, v);
		}
		return v;
	}
}
