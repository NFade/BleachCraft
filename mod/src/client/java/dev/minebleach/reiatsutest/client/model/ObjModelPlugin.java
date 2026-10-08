package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.obj.EmissiveMask;
import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import dev.minebleach.reiatsutest.core.obj.ObjGeometry;
import dev.minebleach.reiatsutest.core.obj.ObjMesh;
import dev.minebleach.reiatsutest.core.obj.ObjMeta;
import dev.minebleach.reiatsutest.core.obj.ObjParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * Registers the OBJ item models: an async loader reads manifests, OBJ, meta and emissive masks from the resource
 * manager (ADR section 1, model pipeline steps 1 and 2).
 */
public final class ObjModelPlugin {
	/** Dev-harness switch: log every id the resolver sees (ADR section 7 diagnosis step 1). */
	public static final boolean LOG_ALL_IDS = Boolean.getBoolean("reiatsu.spike");

	private ObjModelPlugin() {
	}

	public static void register() {
		PreparableModelLoadingPlugin.register(ObjModelPlugin::load, (data, ctx) -> {
			ReiatsuTest.LOGGER.info("[spike] model loading plugin: {} OBJ item model(s) prepared", data.size());
			ctx.resolveModel().register(rc -> {
				Identifier id = rc.id();
				if (LOG_ALL_IDS && ReiatsuTest.MOD_ID.equals(id.getNamespace())) {
					ReiatsuTest.LOGGER.info("[spike] resolver sees id {}", id);
				}
				ObjModelData d = data.get(id);
				return d == null ? null : new ObjItemUnbakedModel(d);
			});
		});
	}

	private static CompletableFuture<Map<Identifier, ObjModelData>> load(ResourceManager rm, Executor executor) {
		return CompletableFuture.supplyAsync(() -> {
			Map<Identifier, ObjModelData> out = new LinkedHashMap<>();
			Map<Identifier, Resource> manifests = rm.findResources("zanpakuto", id ->
					ReiatsuTest.MOD_ID.equals(id.getNamespace()) && id.getPath().endsWith(".json"));
			for (Map.Entry<Identifier, Resource> e : manifests.entrySet()) {
				String path = e.getKey().getPath();
				String itemName = path.substring("zanpakuto/".length(), path.length() - ".json".length());
				try {
					ObjModelData d = loadOne(rm, itemName, e.getValue());
					out.put(d.modelId(), d);
				} catch (Exception ex) {
					ReiatsuTest.LOGGER.error("[spike] failed to load OBJ item model {}: {}", itemName, ex.toString(), ex);
				}
			}
			return out;
		}, executor);
	}

	private static ObjModelData loadOne(ResourceManager rm, String itemName, Resource manifestRes) throws IOException {
		long t0 = System.nanoTime();
		ItemManifest manifest;
		try (InputStream in = manifestRes.getInputStream()) {
			manifest = ItemManifest.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
		String base = "models/obj/" + manifest.model + "/";
		ObjMeta meta;
		try (InputStream in = read(rm, base + manifest.model + "_meta.json")) {
			meta = ObjMeta.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
		Map<String, List<ObjGeometry.Quad>> quads = new HashMap<>();
		int total = 0;
		for (String object : manifest.objects.keySet()) {
			String src = base + object + ".obj";
			try (InputStream in = read(rm, src)) {
				ObjMesh mesh = ObjParser.parse(in, src);
				for (String w : mesh.warnings) {
					ReiatsuTest.LOGGER.warn("[spike] {}", w);
				}
				List<ObjGeometry.Quad> q = ObjGeometry.toModelSpace(mesh, meta, object);
				quads.put(object, q);
				total += q.size();
			}
		}
		Map<String, EmissiveMask> masks = new HashMap<>();
		for (ItemManifest.ObjectDef def : manifest.objects.values()) {
			if (def.emissive() != null && !masks.containsKey(def.emissive())) {
				masks.put(def.emissive(), readMask(rm, "textures/item/" + def.emissive() + ".png"));
			}
		}
		double ms = (System.nanoTime() - t0) / 1.0e6;
		ReiatsuTest.LOGGER.info("[spike] parsed '{}' : {} objects, {} quads, {} emissive masks in {} ms",
				itemName, quads.size(), total, masks.size(), String.format("%.2f", ms));
		return new ObjModelData(itemName, ReiatsuTest.id("item/" + itemName), manifest, meta, quads, masks, ms);
	}

	private static InputStream read(ResourceManager rm, String path) throws IOException {
		Identifier id = ReiatsuTest.id(path);
		return rm.getResource(id).orElseThrow(() -> new IOException("missing resource " + id)).getInputStream();
	}

	private static EmissiveMask readMask(ResourceManager rm, String path) throws IOException {
		try (InputStream in = read(rm, path)) {
			BufferedImage img = ImageIO.read(in);
			if (img == null || !img.getColorModel().hasAlpha()) {
				throw new IOException(path + ": emissive texture must be RGBA (A = intensity)");
			}
			byte[] alpha = new byte[img.getWidth() * img.getHeight()];
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					alpha[y * img.getWidth() + x] = (byte) (img.getRGB(x, y) >>> 24);
				}
			}
			return new EmissiveMask(img.getWidth(), img.getHeight(), alpha);
		}
	}
}
