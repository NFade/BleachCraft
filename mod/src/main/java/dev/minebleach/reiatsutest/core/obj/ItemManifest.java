package dev.minebleach.reiatsutest.core.obj;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code zanpakuto/<item>.json}: maps (state, context group) to OBJ objects, texture names and the emissive tint.
 * Pure Java so it can be unit tested without Minecraft.
 */
public final class ItemManifest {
	public record ObjectDef(String diffuse, String emissive) {
	}

	/** hand = first/third person, other = ground/fixed/head, dynamic = per-frame animated segments (hand + other). */
	public record StateDef(List<String> hand, List<String> other, List<String> dynamic, String icon) {
	}

	public String model;
	public String displayModel;
	public int emissiveTint = 0xFFFFFFFF;
	public final Map<String, ObjectDef> objects = new LinkedHashMap<>();
	public final Map<String, StateDef> states = new LinkedHashMap<>();

	public static ItemManifest parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		ItemManifest m = new ItemManifest();
		m.model = root.get("model").getAsString();
		m.displayModel = root.get("display_model").getAsString();
		if (root.has("emissive_tint")) {
			m.emissiveTint = (int) Long.parseLong(root.get("emissive_tint").getAsString(), 16);
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("objects").entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			m.objects.put(e.getKey(), new ObjectDef(o.get("diffuse").getAsString(),
					o.has("emissive") ? o.get("emissive").getAsString() : null));
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("states").entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			m.states.put(e.getKey(), new StateDef(list(o, "hand"), list(o, "other"), list(o, "dynamic"),
					o.has("icon") ? o.get("icon").getAsString() : null));
		}
		return m;
	}

	private static List<String> list(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		if (o.has(key)) {
			JsonArray a = o.getAsJsonArray(key);
			a.forEach(x -> out.add(x.getAsString()));
		}
		return out;
	}
}
