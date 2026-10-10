package dev.minebleach.reiatsutest.core.obj;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code <model>_meta.json} (ADR section 5). Values are Blender world coordinates (Z-up). */
public final class ObjMeta {
	public final Map<String, float[]> empties = new LinkedHashMap<>();
	public final Map<String, float[]> objects = new LinkedHashMap<>();
	/** Draw-from-scabbard data of the sealed models (null for every other model). */
	public BladeAxis bladeAxis;

	/**
	 * {@code blade_axis} of the sealed models (Blender coordinates): {@code origin} = centre of the saya mouth,
	 * {@code direction} = tip direction of the sheathed blade, {@code drawDirection} = the way the hilt travels while the
	 * blade comes out, {@code bladeLength} / {@code clearTravel} = travel after which the whole blade has left the saya,
	 * {@code arcCenter} / {@code arcRadius} / {@code arcAxis} = the sori arc the blade slides along (rotate the drawn sword
	 * about the axis through the centre by {@code travel / radius} radians).
	 */
	public record BladeAxis(float[] origin, float[] direction, float[] drawDirection, float bladeLength, float clearTravel,
			float[] arcCenter, float arcRadius, float[] arcAxis) {
	}

	public static ObjMeta parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		ObjMeta m = new ObjMeta();
		fill(root.getAsJsonObject("empties"), m.empties);
		fill(root.getAsJsonObject("objects"), m.objects);
		if (root.has("blade_axis")) {
			JsonObject b = root.getAsJsonObject("blade_axis");
			JsonObject arc = b.getAsJsonObject("sori_arc");
			m.bladeAxis = new BladeAxis(vec(b, "origin"), vec(b, "direction"), vec(b, "draw_direction"),
					b.get("blade_length").getAsFloat(), b.get("clear_travel").getAsFloat(), vec(arc, "center"),
					arc.get("radius").getAsFloat(), vec(arc, "rotation_axis"));
		}
		return m;
	}

	private static float[] vec(JsonObject o, String key) {
		var a = o.getAsJsonArray(key);
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	private static void fill(JsonObject o, Map<String, float[]> out) {
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			var a = e.getValue().getAsJsonArray();
			out.put(e.getKey(), new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()});
		}
	}

	/** Grip in OBJ axes. */
	public float[] gripObj() {
		float[] g = empties.get("grip_hand");
		return g == null ? new float[3] : AxisMapper.blenderToObj(g); // effect-only models (bankai) have no grip: origin
	}

	/** Object origin in OBJ axes (zero if the object is not listed). */
	public float[] originObj(String object) {
		float[] o = objects.get(object);
		return o == null ? new float[3] : AxisMapper.blenderToObj(o);
	}

	/** Object origin in model space (hinge positions for animated segments). */
	public float[] originModel(String object) {
		return AxisMapper.objToModel(originObj(object), gripObj());
	}
}
