package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.core.obj.EmissiveMask;
import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import dev.minebleach.reiatsutest.core.obj.ObjGeometry;
import dev.minebleach.reiatsutest.core.obj.ObjMeta;
import java.util.Map;
import net.minecraft.util.Identifier;

/** Everything the async loader reads for one item: manifest, meta, model-space quads and emissive masks. */
public record ObjModelData(
		String itemName,
		Identifier modelId,
		ItemManifest manifest,
		ObjMeta meta,
		Map<String, java.util.List<ObjGeometry.Quad>> quads,
		Map<String, EmissiveMask> masks,
		double loadMillis) {
}
