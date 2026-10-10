package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.obj.AxisMapper;
import dev.minebleach.reiatsutest.core.obj.EmissiveMask;
import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import dev.minebleach.reiatsutest.core.obj.ObjGeometry;
import dev.minebleach.reiatsutest.registry.ReleaseState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

/**
 * Unbaked OBJ item model (ADR section 1, pipeline step 3). Depends on the plain JSON {@code _display} model for
 * the display transforms, resolves sprites from the block atlas, and bakes one {@link Mesh} per
 * (state, context group, pass).
 */
public final class ObjItemUnbakedModel implements UnbakedModel {
	private static final float GLOW_OFFSET = 0.0005f; // R1.2: push the coplanar glow pass out along the normal

	private final ObjModelData data;
	private final Identifier displayId;
	private UnbakedModel display;

	public ObjItemUnbakedModel(ObjModelData data) {
		this.data = data;
		this.displayId = Identifier.of(data.manifest().displayModel);
	}

	@Override
	public Collection<Identifier> getModelDependencies() {
		return List.of(displayId);
	}

	@Override
	public void setParents(Function<Identifier, UnbakedModel> modelLoader) {
		this.display = modelLoader.apply(displayId);
	}

	private static Identifier spriteId(String texture) {
		return ReiatsuTest.id("item/" + texture);
	}

	@Override
	public BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings settings) {
		long t0 = System.nanoTime();
		Renderer renderer = RendererAccess.INSTANCE.getRenderer();
		if (renderer == null) {
			throw new IllegalStateException("No Fabric Renderer API implementation (Indigo) is active");
		}
		MeshBuilder mb = renderer.meshBuilder();
		MaterialFinder mf = renderer.materialFinder();
		RenderMaterial cutout = mf.clear().blendMode(BlendMode.CUTOUT).find();
		RenderMaterial translucent = mf.clear().blendMode(BlendMode.TRANSLUCENT).find();
		RenderMaterial glow = mf.clear().emissive(true).disableDiffuse(true).blendMode(BlendMode.TRANSLUCENT).find();

		Map<String, Sprite> sprites = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		java.util.function.Function<String, Sprite> sprite = tex -> sprites.computeIfAbsent(tex, k -> {
			Sprite s = textureGetter.apply(new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, spriteId(k)));
			if (s.getContents().getId().equals(MissingSprite.getMissingSpriteId())) {
				missing.add(k);
			}
			return s;
		});

		ItemManifest man = data.manifest();
		BakedStates states = new BakedStates();
		int[] quadCount = {0};
		for (ReleaseState rs : ReleaseState.values()) {
			ItemManifest.StateDef def = man.states.get(rs.asString());
			if (def == null) {
				def = man.states.get("sealed");
			}
			StateMeshes sm = new StateMeshes();
			sm.handEmpty = def.hand().isEmpty();
			sm.handBase = buildBase(mb, def.hand(), sprite, cutout, translucent, NO_SHIFT, quadCount);
			sm.handGlow = buildGlow(mb, def.hand(), sprite, glow, NO_SHIFT, quadCount);
			sm.otherBase = buildBase(mb, def.other(), sprite, cutout, translucent, NO_SHIFT, quadCount);
			sm.otherGlow = buildGlow(mb, def.other(), sprite, glow, NO_SHIFT, quadCount);
			for (ItemManifest.DynSeg ds : man.dynamicSegments(def)) {
				String seg = ds.object();
				float[] origin = data.metaOf(seg).originModel(seg);
				float[] shift = new float[3];
				float[] hinge = origin;
				if (ds.hingeBlender() != null) { // chain member: the same OBJ object moved to its own hinge
					hinge = AxisMapper.blenderToModel(ds.hingeBlender(), data.metaOf(seg).empties.getOrDefault("grip_hand", new float[3]));
					for (int i = 0; i < 3; i++) {
						shift[i] = hinge[i] - origin[i];
					}
				}
				sm.dynamic.add(buildBase(mb, List.of(seg), sprite, cutout, translucent, shift, quadCount));
				sm.dynamicGlow.add(buildGlow(mb, List.of(seg), sprite, glow, shift, quadCount));
				sm.dynamicHinges.add(hinge);
			}
			if (def.icon() != null) {
				sm.icon = buildIcon(mb, sprite.apply(def.icon()), cutout);
			}
			states.byState[rs.ordinal()] = sm;
		}
		if (man.draw != null) {
			// B4 step 2: the scabbard is its own mesh (drawn by the scabbard renderer in the left hand / on the hip); the sealed
			// hand mesh is the bare sword. No shift: both share the model space of the grip.
			List<String> sayaObjs = List.of(man.draw.saya());
			states.saya = buildBase(mb, sayaObjs, sprite, cutout, translucent, NO_SHIFT, quadCount);
			states.sayaGlow = buildGlow(mb, sayaObjs, sprite, glow, NO_SHIFT, quadCount);
			states.sayaMeta = data.metaOf(man.draw.saya());
		}

		ModelTransformation transformation = display instanceof JsonUnbakedModel j ? j.getTransformations() : ModelTransformation.NONE;
		Sprite particle = sprite.apply(man.objects.values().iterator().next().diffuse());
		ReiatsuTest.LOGGER.info("[spike] baked {} : quads={} sprites={} missing={} bake={} ms (parse {} ms) display={}",
				data.modelId(), quadCount[0], sprites.keySet(), missing,
				String.format("%.2f", (System.nanoTime() - t0) / 1.0e6), String.format("%.2f", data.loadMillis()),
				display == null ? "null" : display.getClass().getSimpleName());
		if (!missing.isEmpty()) {
			ReiatsuTest.LOGGER.error("[spike] MISSING sprites in atlas: {}", missing);
		}
		return new ObjItemBakedModel(states, transformation, particle, man);
	}

	private static final float[] NO_SHIFT = new float[3];

	private Mesh buildBase(MeshBuilder mb, List<String> objects, Function<String, Sprite> sprite, RenderMaterial mat,
			RenderMaterial translucentMat, float[] shift, int[] count) {
		QuadEmitter em = mb.getEmitter();
		for (String obj : objects) {
			ItemManifest.ObjectDef def = data.manifest().objects.get(obj);
			Sprite sp = sprite.apply(def.diffuse());
			for (ObjGeometry.Quad q : data.quads().get(obj)) {
				emit(em, q, sp, def.translucent() ? translucentMat : mat, 0xFFFFFFFF, 0f, shift);
				count[0]++;
			}
		}
		return mb.build();
	}

	/** Emissive overlay: only quads whose UV rectangle covers a texel with alpha &gt; 0; null when empty. */
	private Mesh buildGlow(MeshBuilder mb, List<String> objects, Function<String, Sprite> sprite, RenderMaterial mat,
			float[] shift, int[] count) {
		QuadEmitter em = mb.getEmitter();
		int n = 0;
		for (String obj : objects) {
			ItemManifest.ObjectDef def = data.manifest().objects.get(obj);
			if (def.emissive() == null) {
				continue;
			}
			EmissiveMask mask = data.masks().get(def.emissive());
			Sprite sp = sprite.apply(def.emissive());
			for (ObjGeometry.Quad q : data.quads().get(obj)) {
				if (mask.anyEmissive(q.uv())) {
					emit(em, q, sp, mat, data.manifest().emissiveTint, GLOW_OFFSET, shift);
					n++;
					count[0]++;
				}
			}
		}
		Mesh m = mb.build();
		return n == 0 ? null : m;
	}

	/**
	 * R1.1 mitigation (spike): entity shaders still apply directional diffuse to the emissive pass, so side faces glow
	 * at about 50%. Default ON (disable with {@code -Dreiatsu.glowNormal=none}): glow quads get the model-space +Y normal (the blade axis, which
	 * points up in most display contexts) so they are lit like top faces. Offset still uses the real face normal.
	 */
	private static final boolean GLOW_NORMAL_UP = !"none".equals(System.getProperty("reiatsu.glowNormal"));

	private static void emit(QuadEmitter em, ObjGeometry.Quad q, Sprite sprite, RenderMaterial mat, int argb, float offset,
			float[] shift) {
		for (int c = 0; c < 4; c++) {
			float[] n = q.nrm()[c];
			float[] p = q.pos()[c];
			em.pos(c, p[0] + shift[0] + n[0] * offset, p[1] + shift[1] + n[1] * offset, p[2] + shift[2] + n[2] * offset);
			em.uv(c, q.uv()[c][0], q.uv()[c][1]);
			if (GLOW_NORMAL_UP && offset > 0f) {
				em.normal(c, 0f, 1f, 0f);
			} else {
				em.normal(c, n[0], n[1], n[2]);
			}
			em.color(c, argb);
		}
		em.material(mat);
		em.cullFace(null);
		em.nominalFace(null);
		em.spriteBake(sprite, MutableQuadView.BAKE_NORMALIZED);
		em.emit();
	}

	/** Flat GUI icon: front and back quads in the z=0.5 plane (ADR section 1). */
	private static Mesh buildIcon(MeshBuilder mb, Sprite sprite, RenderMaterial mat) {
		QuadEmitter em = mb.getEmitter();
		// front (+Z normal), CCW seen from +Z; V down in sprite space
		float[][] pos = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
		float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
		for (int c = 0; c < 4; c++) {
			em.pos(c, pos[c][0], pos[c][1], 0.5005f).uv(c, uv[c][0], uv[c][1]).normal(c, 0, 0, 1).color(c, 0xFFFFFFFF);
		}
		em.material(mat).cullFace(null).nominalFace(null).spriteBake(sprite, MutableQuadView.BAKE_NORMALIZED).emit();
		// back (-Z normal): reversed winding, mirrored U so it reads correctly from behind
		float[][] pos2 = {{1, 0}, {0, 0}, {0, 1}, {1, 1}};
		float[][] uv2 = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
		for (int c = 0; c < 4; c++) {
			em.pos(c, pos2[c][0], pos2[c][1], 0.4995f).uv(c, uv2[c][0], uv2[c][1]).normal(c, 0, 0, -1).color(c, 0xFFFFFFFF);
		}
		em.material(mat).cullFace(null).nominalFace(null).spriteBake(sprite, MutableQuadView.BAKE_NORMALIZED).emit();
		return mb.build();
	}

	/** Baked meshes for one release state. */
	static final class StateMeshes {
		boolean handEmpty;
		Mesh handBase;
		Mesh handGlow;
		Mesh otherBase;
		Mesh otherGlow;
		Mesh icon;
		final List<Mesh> dynamic = new ArrayList<>();
		/** Emissive overlay per dynamic segment (null entries where the segment does not glow). */
		final List<Mesh> dynamicGlow = new ArrayList<>();
		final List<float[]> dynamicHinges = new ArrayList<>();
	}

	static final class BakedStates {
		final StateMeshes[] byState = new StateMeshes[ReleaseState.values().length];
		/** Scabbard (item with a {@code draw} manifest entry) in model space, null otherwise. */
		Mesh saya;
		Mesh sayaGlow;
		dev.minebleach.reiatsutest.core.obj.ObjMeta sayaMeta;
	}
}
