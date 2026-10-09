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
	/** {@code model} = the Blender model (folder under models/obj) the object comes from; null = the manifest's default. */
	public record ObjectDef(String diffuse, String emissive, String model) {
		public ObjectDef(String diffuse, String emissive) {
			this(diffuse, emissive, null);
		}
	}

	/** hand = first/third person, other = ground/fixed/head, dynamic = per-frame animated segments (hand + other). */
	public record StateDef(List<String> hand, List<String> other, List<String> dynamic, String icon) {
	}

	/**
	 * First person arm pose, in the item's model space (blocks, origin = grip_hand): {@code axis} = direction from the
	 * shoulder toward the fist (the arm's long axis), {@code roll} = degrees about it, {@code grip} = where the fist centre
	 * sits relative to the grip (blocks), {@code anchorPx} = extra shift in arm-local pixels, {@code scale} = arm size factor. Right-hand values;
	 * the left hand mirrors x and the roll.
	 */
	public record ArmPose(float[] axis, float roll, float[] grip, float[] anchorPx, float scale) {
	}

	public String model;
	public ArmPose firstPersonArm; // null = the item draws no first person arm
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
		if (root.has("first_person_arm")) {
			JsonObject a = root.getAsJsonObject("first_person_arm");
			m.firstPersonArm = new ArmPose(vec(a, "axis", new float[] {-1, 0, 0}), a.has("roll") ? a.get("roll").getAsFloat() : 0f,
					vec(a, "grip", new float[3]), vec(a, "anchor_px", new float[3]), a.has("scale") ? a.get("scale").getAsFloat() : 1f);
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("objects").entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			m.objects.put(e.getKey(), new ObjectDef(o.get("diffuse").getAsString(),
					o.has("emissive") ? o.get("emissive").getAsString() : null,
					o.has("model") ? o.get("model").getAsString() : null));
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("states").entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			m.states.put(e.getKey(), new StateDef(list(o, "hand"), list(o, "other"), list(o, "dynamic"),
					o.has("icon") ? o.get("icon").getAsString() : null));
		}
		return m;
	}

	private static float[] vec(JsonObject o, String key, float[] def) {
		if (!o.has(key)) {
			return def;
		}
		JsonArray a = o.getAsJsonArray(key);
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	/** Blender model folder of an object: its own {@code model} field, else the manifest default. */
	public String modelOf(String object) {
		ObjectDef d = objects.get(object);
		return d != null && d.model() != null ? d.model() : model;
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
