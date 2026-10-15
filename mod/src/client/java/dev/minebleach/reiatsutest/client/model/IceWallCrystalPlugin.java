package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.obj.AxisMapper;
import dev.minebleach.reiatsutest.core.obj.EmissiveMask;
import dev.minebleach.reiatsutest.core.obj.ObjMesh;
import dev.minebleach.reiatsutest.core.obj.ObjParser;
import dev.minebleach.reiatsutest.core.world.IceCrystalField;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * R11: the crystal look of the {@code ice_wall} block. The block stays a plain block of the server (collision, strength, journal rollback are real blocks);
 * only its model is replaced here: this plugin reads the OBJ pieces made in Blender ({@code models/obj/rukia_ice_wall/}, built by
 * {@code blender/scripts/build_rukia_ice_wall.py}) and the glow mask, and answers the model id of the block state with an {@link IceWallCrystalModel}. The model
 * picks and bends the pieces per block position when the chunk is built ({@code core/world/IceCrystalField}), so everybody who loads the chunk sees the
 * same wall, also those who joined later. If anything is missing the resolver answers null and the plain JSON cube ({@code models/block/ice_wall.json})
 * is drawn instead.
 */
public final class IceWallCrystalPlugin {
	/** The model id of the block state {@code ice_wall} (blockstates/ice_wall.json). */
	public static final Identifier MODEL_ID = ReiatsuTest.id("block/ice_wall");
	public static final String TEXTURE = "block/ice_wall_crystal";
	public static final String GLOW_TEXTURE = "block/ice_wall_crystal_glow";

	private IceWallCrystalPlugin() {
	}

	/** One face of a piece: corners in the Blender frame of the cell, V already flipped (image space), triangles repeat corner 2. */
	public record PieceQuad(float[][] pos, float[][] uv, boolean glow) {
	}

	/** All pieces: shell and core quads by piece name. */
	public record Data(Map<String, List<PieceQuad>> shells, Map<String, List<PieceQuad>> cores, int quadCount, double loadMillis) {
	}

	public static void register() {
		PreparableModelLoadingPlugin.register(IceWallCrystalPlugin::load, (data, ctx) -> {
			if (data == null) {
				return;
			}
			ReiatsuTest.LOGGER.info("[ice wall] crystal model: {} pieces, {} quads in {} ms", data.shells().size(), data.quadCount(), String.format("%.2f", data.loadMillis()));
			ctx.resolveModel().register(rc -> MODEL_ID.equals(rc.id()) ? new IceWallCrystalModel(data) : null);
		});
	}

	private static CompletableFuture<Data> load(ResourceManager rm, Executor executor) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return loadAll(rm);
			} catch (Exception ex) {
				ReiatsuTest.LOGGER.error("[ice wall] the crystal model failed to load, the plain cube is drawn instead: {}", ex.toString(), ex);
				return null;
			}
		}, executor);
	}

	private static Data loadAll(ResourceManager rm) throws IOException {
		long t0 = System.nanoTime();
		EmissiveMask mask = readMask(rm, "textures/" + GLOW_TEXTURE + ".png");
		Map<String, List<PieceQuad>> shells = new LinkedHashMap<>();
		Map<String, List<PieceQuad>> cores = new LinkedHashMap<>();
		int total = 0;
		for (String piece : IceCrystalField.PIECES) {
			List<PieceQuad> shell = readObject(rm, IceCrystalField.objectName(piece, false), mask);
			shells.put(piece, shell);
			total += shell.size();
			if (IceCrystalField.CORE_PIECES.contains(piece)) {
				List<PieceQuad> core = readObject(rm, IceCrystalField.objectName(piece, true), mask);
				cores.put(piece, core);
				total += core.size();
			}
		}
		return new Data(shells, cores, total, (System.nanoTime() - t0) / 1.0e6);
	}

	/** Parses one OBJ object into quads of the Blender frame of the cell (the OBJ is object local: nothing is added). */
	static List<PieceQuad> readObject(ResourceManager rm, String object, EmissiveMask mask) throws IOException {
		String src = "models/obj/" + IceCrystalField.MODEL + "/" + object + ".obj";
		try (InputStream in = read(rm, src)) {
			return toQuads(ObjParser.parse(in, src), mask);
		}
	}

	/** Mesh to quads in the Blender frame (also used by the tests). */
	public static List<PieceQuad> toQuads(ObjMesh mesh, EmissiveMask mask) {
		List<PieceQuad> out = new ArrayList<>(mesh.faceCount());
		for (ObjMesh.Face f : mesh.faces) {
			float[][] pos = new float[4][];
			float[][] uv = new float[4][];
			for (int c = 0; c < 4; c++) {
				int k = Math.min(c, f.corners() - 1);
				int vi = f.v()[k];
				pos[c] = AxisMapper.objToBlender(mesh.positions[vi * 3], mesh.positions[vi * 3 + 1], mesh.positions[vi * 3 + 2]);
				int ti = f.vt()[k];
				uv[c] = new float[] {mesh.uvs[ti * 2], AxisMapper.flipV(mesh.uvs[ti * 2 + 1])};
			}
			out.add(new PieceQuad(pos, uv, mask != null && mask.anyEmissive(uv)));
		}
		return out;
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
