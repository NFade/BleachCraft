package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.core.obj.ObjGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parsed OBJ data of every item manifest, kept after the item models are baked so that later effect renderers
 * (petal swarm, ice crystals, ice shell, shards, ground hilt, ...) can address meshes by item and object name. Objects
 * listed in a manifest's {@code objects} but not used by any state are loaded and only kept here.
 */
public final class ObjModelRegistry {
	private static volatile Map<String, ObjModelData> data = Map.of();

	private ObjModelRegistry() {
	}

	static void set(Map<String, ObjModelData> byItem) {
		data = byItem;
	}

	/** Data of one item manifest ({@code zanpakuto/<item>.json}), or null. */
	public static ObjModelData get(String item) {
		return data.get(item);
	}

	/**
	 * Quads of an object in its LOCAL frame (the object origin of the Blender file = (0,0,0), i.e. the hinge, the base
	 * or the centre as documented in the meta). The model-space quads carry the layout offset of the object and the
	 * grip shift; this removes both. Returns an empty list if the item or object is unknown.
	 */
	public static List<ObjGeometry.Quad> localQuads(String item, String object) {
		ObjModelData d = data.get(item);
		if (d == null || !d.quads().containsKey(object)) {
			return List.of();
		}
		float[] o = d.metaOf(object).originModel(object);
		List<ObjGeometry.Quad> out = new ArrayList<>();
		for (ObjGeometry.Quad q : d.quads().get(object)) {
			float[][] pos = new float[4][];
			for (int c = 0; c < 4; c++) {
				pos[c] = new float[] {q.pos()[c][0] - o[0], q.pos()[c][1] - o[1], q.pos()[c][2] - o[2]};
			}
			out.add(new ObjGeometry.Quad(pos, q.uv(), q.nrm(), q.triangle()));
		}
		return out;
	}
}
