package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.world.IceCrystalField;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

/**
 * The model of the {@code ice_wall} block: a crystal cell per block (see {@link IceCrystalField}). Nothing is baked per state: the pieces made in Blender are
 * parsed once ({@link IceWallCrystalPlugin}); when a chunk section is built {@link Baked#emitBlockQuads} picks the pieces for the block position from the field
 * (variation by position, a wall block under / over this one), bends them and emits the quads with a translucent material. The inner core and the pale
 * tips get a second, emissive pass (the glow mask of the OBJ item models: a quad is drawn twice only when its UV rectangle covers a texel with alpha).
 */
public final class IceWallCrystalModel implements UnbakedModel {
	private final IceWallCrystalPlugin.Data data;

	IceWallCrystalModel(IceWallCrystalPlugin.Data data) {
		this.data = data;
	}

	@Override
	public Collection<Identifier> getModelDependencies() {
		return List.of();
	}

	@Override
	public void setParents(Function<Identifier, UnbakedModel> modelLoader) {
	}

	@Override
	public BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings settings) {
		Renderer renderer = RendererAccess.INSTANCE.getRenderer();
		if (renderer == null) {
			throw new IllegalStateException("No Fabric Renderer API implementation (Indigo) is active");
		}
		MaterialFinder mf = renderer.materialFinder();
		RenderMaterial shell = mf.clear().blendMode(BlendMode.TRANSLUCENT).disableDiffuse(true).ambientOcclusion(TriState.FALSE).find();
		RenderMaterial glow = mf.clear().blendMode(BlendMode.TRANSLUCENT).emissive(true).disableDiffuse(true).ambientOcclusion(TriState.FALSE).find();
		Sprite diffuse = textureGetter.apply(new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, ReiatsuTest.id(IceWallCrystalPlugin.TEXTURE)));
		Sprite glowSprite = textureGetter.apply(new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, ReiatsuTest.id(IceWallCrystalPlugin.GLOW_TEXTURE)));
		for (Sprite s : new Sprite[] {diffuse, glowSprite}) {
			if (s.getContents().getId().equals(MissingSprite.getMissingSpriteId())) {
				ReiatsuTest.LOGGER.error("[ice wall] MISSING sprite {} in the block atlas", s.getContents().getId());
			}
		}
		return new Baked(data, shell, glow, diffuse, glowSprite);
	}

	/** Light direction of the fake facet shading (the vanilla diffuse is off, it only knows six directions). */
	private static final float LX = 0.35f;
	private static final float LY = 0.80f;
	private static final float LZ = 0.50f;
	private static final float GLOW_OFFSET = 0.0006f;

	static final class Baked implements BakedModel {
		private final IceWallCrystalPlugin.Data data;
		private final RenderMaterial shell;
		private final RenderMaterial glow;
		private final Sprite diffuse;
		private final Sprite glowSprite;

		Baked(IceWallCrystalPlugin.Data data, RenderMaterial shell, RenderMaterial glow, Sprite diffuse, Sprite glowSprite) {
			this.data = data;
			this.shell = shell;
			this.glow = glow;
			this.diffuse = diffuse;
			this.glowSprite = glowSprite;
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		@Override
		public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
			boolean below = blockView.getBlockState(pos.down()).isOf(state.getBlock());
			boolean above = blockView.getBlockState(pos.up()).isOf(state.getBlock());
			IceCrystalField.Cell cell = IceCrystalField.cell(pos.getX(), pos.getY(), pos.getZ(), below, above);
			QuadEmitter em = context.getEmitter();
			float bottomTint = below ? 1.0f : 0.90f; // the lowest cell of a column is a little deeper blue
			emitPiece(em, data.shells().get(cell.piece), cell, null, bottomTint);
			List<IceWallCrystalPlugin.PieceQuad> core = data.cores().get(cell.piece);
			if (core != null) {
				emitPiece(em, core, cell, null, 1.0f);
			}
			for (int s = 0; s < cell.sats.size(); s++) {
				emitPiece(em, data.shells().get(cell.sats.get(s).kind()), cell, cell.satOrigin(s), 1.0f);
			}
			if (cell.feet != null) {
				emitPiece(em, data.shells().get(cell.feet), cell, cell.feetOrigin(), bottomTint);
			}
		}

		/** Emits the quads of one piece: bent by the column when {@code rigid} is null, else placed rigidly (satellites, feet). */
		private void emitPiece(QuadEmitter em, List<IceWallCrystalPlugin.PieceQuad> quads, IceCrystalField.Cell cell, @Nullable double[] rigid, float tint) {
			if (quads == null) {
				return;
			}
			float[][] p = new float[4][3];
			for (IceWallCrystalPlugin.PieceQuad q : quads) {
				for (int c = 0; c < 4; c++) {
					float[] v = q.pos()[c];
					double[] w = rigid == null ? cell.column(v[0], v[1], v[2]) : IceCrystalField.rigid(rigid, v[0], v[1], v[2]);
					p[c][0] = (float) IceCrystalField.toBlockX(w[0]);
					p[c][1] = (float) IceCrystalField.toBlockY(w[2]);
					p[c][2] = (float) IceCrystalField.toBlockZ(w[1]);
				}
				// face normal (corner 0, 1, 2); triangles repeat corner 2 so the first three are always distinct
				float ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
				float bx = p[2][0] - p[0][0], by = p[2][1] - p[0][1], bz = p[2][2] - p[0][2];
				float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (len < 1e-9f) {
					continue; // a collapsed face
				}
				nx /= len;
				ny /= len;
				nz /= len;
				float k = Math.max(0.0f, nx * LX + ny * LY + nz * LZ) / (float) Math.sqrt(LX * LX + LY * LY + LZ * LZ);
				int r = Math.round(255.0f * tint * (0.66f + 0.34f * k));
				int g = Math.round(255.0f * tint * (0.80f + 0.20f * k));
				int b = 255;
				int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
				emit(em, p, q.uv(), shell, diffuse, argb, 0.0f, nx, ny, nz);
				if (q.glow()) {
					emit(em, p, q.uv(), glow, glowSprite, 0xFFFFFFFF, GLOW_OFFSET, nx, ny, nz);
				}
			}
		}

		private static void emit(QuadEmitter em, float[][] p, float[][] uv, RenderMaterial mat, Sprite sprite, int argb, float offset, float nx, float ny, float nz) {
			for (int c = 0; c < 4; c++) {
				em.pos(c, p[c][0] + nx * offset, p[c][1] + ny * offset, p[c][2] + nz * offset);
				em.uv(c, uv[c][0], uv[c][1]);
				em.color(c, argb);
			}
			em.material(mat);
			em.cullFace(null);
			em.nominalFace(null);
			em.spriteBake(sprite, MutableQuadView.BAKE_NORMALIZED);
			em.emit();
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random) {
			return List.of();
		}

		@Override
		public boolean useAmbientOcclusion() {
			return false;
		}

		@Override
		public boolean hasDepth() {
			return true;
		}

		@Override
		public boolean isSideLit() {
			return true;
		}

		@Override
		public boolean isBuiltin() {
			return false;
		}

		@Override
		public Sprite getParticleSprite() {
			return diffuse;
		}

		@Override
		public ModelTransformation getTransformation() {
			return ModelTransformation.NONE;
		}

		@Override
		public ModelOverrideList getOverrides() {
			return ModelOverrideList.EMPTY;
		}
	}
}
