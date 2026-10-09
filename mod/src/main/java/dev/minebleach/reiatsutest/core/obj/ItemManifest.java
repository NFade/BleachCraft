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
	public record ObjectDef(String diffuse, String emissive, String model, boolean translucent) {
		public ObjectDef(String diffuse, String emissive) {
			this(diffuse, emissive, null, false);
		}
	}

	/**
	 * A chain of identical hinged segments built from one OBJ object: {@code count} copies of {@code segment} hang one
	 * after another from {@code root} (Blender world coordinates of the first hinge) every {@code pitch} metres along -Z,
	 * optionally closed by {@code tip}. A state lists it in {@code dynamic} as {@code "@<name>"}.
	 */
	public record Chain(String segment, int count, String tip, float[] root, float pitch) {
	}

	/** One animated segment: the OBJ object and, for chain members, the hinge in Blender coordinates (null = the object origin). */
	public record DynSeg(String object, float[] hingeBlender) {
	}

	/** hand = first/third person, other = ground/fixed/head, dynamic = per-frame animated segments (hand + other). */
	public record StateDef(List<String> hand, List<String> other, List<String> dynamic, String icon, ArmPose arm) {
	}

	/**
	 * First person arm pose, in the item's model space (blocks, origin = grip_hand): {@code axis} = direction from the
	 * shoulder toward the fist (the arm's long axis), {@code roll} = degrees about it, {@code grip} = where the fist centre
	 * sits relative to the grip (blocks), {@code anchorPx} = extra shift in arm-local pixels, {@code scale} = arm size factor; {@code vanilla} = ignore axis/roll/grip/scale and draw the arm exactly like the vanilla empty hand (only {@code anchorPx} nudges it). Right-hand values;
	 * the left hand mirrors x and the roll.
	 */
	public record ArmPose(float[] axis, float roll, float[] grip, float[] anchorPx, float scale, boolean vanilla) {
	}

	public String model;
	public ArmPose firstPersonArm; // null = the item draws no first person arm
	public final Map<String, Chain> chains = new LinkedHashMap<>();
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
			m.firstPersonArm = arm(root.getAsJsonObject("first_person_arm"));
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("objects").entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			m.objects.put(e.getKey(), new ObjectDef(o.get("diffuse").getAsString(),
					o.has("emissive") ? o.get("emissive").getAsString() : null,
					o.has("model") ? o.get("model").getAsString() : null,
					o.has("translucent") && o.get("translucent").getAsBoolean()));
		}
		if (root.has("chains")) {
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("chains").entrySet()) {
				JsonObject c = e.getValue().getAsJsonObject();
				m.chains.put(e.getKey(), new Chain(c.get("segment").getAsString(), c.get("count").getAsInt(),
						c.has("tip") ? c.get("tip").getAsString() : null, vec(c, "root", new float[3]),
						c.get("pitch").getAsFloat()));
			}
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("states").entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			m.states.put(e.getKey(), new StateDef(list(o, "hand"), list(o, "other"), list(o, "dynamic"),
					o.has("icon") ? o.get("icon").getAsString() : null,
					o.has("arm") ? arm(o.getAsJsonObject("arm")) : null));
		}
		return m;
	}

	private static ArmPose arm(JsonObject a) {
		return new ArmPose(vec(a, "axis", new float[] {-1, 0, 0}), a.has("roll") ? a.get("roll").getAsFloat() : 0f,
				vec(a, "grip", new float[3]), vec(a, "anchor_px", new float[3]), a.has("scale") ? a.get("scale").getAsFloat() : 1f,
				a.has("vanilla") && a.get("vanilla").getAsBoolean());
	}

	/** Arm pose of a state: its own {@code arm} override, else the item default (may be null = no arm). */
	public ArmPose armPose(String state) {
		StateDef d = states.get(state);
		return d != null && d.arm() != null ? d.arm() : firstPersonArm;
	}

	/** The animated segments of a state in chain order; {@code "@name"} entries expand to their chain. */
	public List<DynSeg> dynamicSegments(StateDef def) {
		List<DynSeg> out = new ArrayList<>();
		for (String entry : def.dynamic()) {
			if (!entry.startsWith("@")) {
				out.add(new DynSeg(entry, null));
				continue;
			}
			Chain c = chains.get(entry.substring(1));
			if (c == null) {
				throw new IllegalArgumentException("unknown chain " + entry);
			}
			int n = c.count();
			for (int k = 0; k < n; k++) {
				out.add(new DynSeg(c.segment(), new float[] {c.root()[0], c.root()[1], c.root()[2] - c.pitch() * k}));
			}
			if (c.tip() != null) {
				out.add(new DynSeg(c.tip(), new float[] {c.root()[0], c.root()[1], c.root()[2] - c.pitch() * n}));
			}
		}
		return out;
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
