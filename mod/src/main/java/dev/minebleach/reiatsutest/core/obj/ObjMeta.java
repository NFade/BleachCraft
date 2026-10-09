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

	public static ObjMeta parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		ObjMeta m = new ObjMeta();
		fill(root.getAsJsonObject("empties"), m.empties);
		fill(root.getAsJsonObject("objects"), m.objects);
		return m;
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
