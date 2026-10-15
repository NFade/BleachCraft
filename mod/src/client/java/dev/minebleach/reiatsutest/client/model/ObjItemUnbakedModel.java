package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.obj.AxisMapper;
import dev.minebleach.reiatsutest.core.obj.EmissiveMask;
import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import dev.minebleach.reiatsutest.core.obj.ObjGeometry;
import dev.minebleach.reiatsutest.core.obj.ObjMeta;
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
			if (rs == ReleaseState.SEALED && man.draw != null) {
				// plan G-glow: the drawn blade's pink glow shell (ReleaseHold / ShikaiDissolve), the same quads pushed out along the normal
				sm.bladeShell = buildShell(mb, def.hand(), sprite, glow, quadCount);
			}
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
			// plan G-glow: blade axis in model space (saya mouth to tip) for the dissolve front; left null when the meta has none
			ObjMeta bm = data.metaOf(man.draw.blade());
			float[] grip = bm == null ? null : bm.empties.get("grip_hand");
			float[] tip = bm == null ? null : bm.empties.get("tip");
			if (grip != null && tip != null && bm.bladeAxis != null) {
				float[] tipM = AxisMapper.blenderToModel(tip, grip);
				float[] mouthM = AxisMapper.blenderToModel(bm.bladeAxis.origin(), grip);
				float dx = tipM[0] - mouthM[0];
				float dy = tipM[1] - mouthM[1];
				float dz = tipM[2] - mouthM[2];
				float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (len > 1e-4f) {
					states.bladeBase = mouthM;
					states.bladeDir = new float[] {dx / len, dy / len, dz / len};
					states.bladeLen = len;
				}
			}
			ItemManifest.StateDef sealedDef = man.states.get("sealed");
			if (states.bladeBase != null && sealedDef != null) {
				// plan G-glow round 2: the drawn blade cut into small fragments (they break off one by one in the dissolve)
				states.shards = buildShards(sealedDef.hand(), sprite, glow, cutout, translucent, states.bladeBase, states.bladeDir, states.bladeLen);
			}
		}

		ItemManifest.ObjectDef pinkDef = man.objects.get("byakuya_sealed_drawn_pink");
		if (pinkDef != null) {
			List<String> pinkObjs = List.of("byakuya_sealed_drawn_pink");
			states.pinkSword = buildBase(mb, pinkObjs, sprite, cutout, translucent, NO_SHIFT, quadCount);
			states.pinkSwordGlow = buildGlow(mb, pinkObjs, sprite, glow, NO_SHIFT, quadCount);
			states.pinkSwordFade = buildBase(mb, pinkObjs, sprite, translucent, translucent, NO_SHIFT, quadCount);
			states.pinkSwordSprite = sprite.apply(pinkDef.diffuse());
		}

		if (man.objects.containsKey("uryu_arrow") && man.objects.containsKey("uryu_bow_string") && man.objects.containsKey("uryu_bow")) {
			states.bow = buildBow(mb, sprite, cutout, translucent, glow, quadCount); // Uryu: the drawn bow with the pulled string and the nocked arrow
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

	/** Half width of the pulled string ribbon (model units = blocks). */
	private static final float STRING_HALF_WIDTH = 0.005f;

	/**
	 * Uryu's bow while it is drawn: the bow without the straight string, a string bent into a V (its middle vertices are at y = 0.5 and
	 * are pulled back along the arrow axis when drawn, see {@code ObjItemBakedModel#emitBowDraw}) and the arrow. All in model space.
	 */
	private BowMeshes buildBow(MeshBuilder mb, Function<String, Sprite> sprite, RenderMaterial cutout, RenderMaterial translucent, RenderMaterial glow, int[] count) {
		BowMeshes b = new BowMeshes();
		b.bow = buildBase(mb, List.of("uryu_bow"), sprite, cutout, translucent, NO_SHIFT, count);
		b.bowGlow = buildGlow(mb, List.of("uryu_bow"), sprite, glow, NO_SHIFT, count);
		b.arrow = buildBase(mb, List.of("uryu_arrow"), sprite, cutout, translucent, NO_SHIFT, count);
		b.arrowGlow = buildGlow(mb, List.of("uryu_arrow"), sprite, glow, NO_SHIFT, count);
		// the forming bow (URYU_VFX_STORYBOARD 2.1): the rest bow and the three flex bows (limbs bent 3, 6 and 9 degrees) as translucent meshes
		// that fade in while the limbs relax, and the straight string on its own (it appears at the string sweep); a missing flex bow falls back to the rest bow
		b.formBase = new Mesh[4];
		b.formGlow = new Mesh[4];
		for (int i = 0; i < 4; i++) {
			String obj = i == 0 ? "uryu_bow" : "uryu_bow_flex_" + i;
			if (!data.manifest().objects.containsKey(obj)) {
				obj = "uryu_bow";
			}
			b.formBase[i] = buildBase(mb, List.of(obj), sprite, translucent, translucent, NO_SHIFT, count);
			b.formGlow[i] = buildGlow(mb, List.of(obj), sprite, glow, NO_SHIFT, count);
		}
		b.straight = buildBase(mb, List.of("uryu_bow_string"), sprite, cutout, translucent, NO_SHIFT, count);
		b.straightGlow = buildGlow(mb, List.of("uryu_bow_string"), sprite, glow, NO_SHIFT, count);
		ItemManifest.ObjectDef sd = data.manifest().objects.get("uryu_bow_string");
		List<ObjGeometry.Quad> src = data.quads().get("uryu_bow_string");
		float[][] uv = src.get(0).uv();
		// string tips from the straight string mesh: the highest and lowest vertex (model space), the middle on the line between them
		float topY = -1e9f;
		float botY = 1e9f;
		float tipZ = 0.5f;
		for (ObjGeometry.Quad q : src) {
			for (int c = 0; c < 4; c++) {
				topY = Math.max(topY, q.pos()[c][1]);
				botY = Math.min(botY, q.pos()[c][1]);
				tipZ = q.pos()[c][2];
			}
		}
		float w = STRING_HALF_WIDTH;
		QuadEmitter em = mb.getEmitter();
		for (int pass = 0; pass < 2; pass++) { // 0 = lit string, 1 = emissive overlay
			Sprite sp = sprite.apply(pass == 0 ? sd.diffuse() : sd.emissive() != null ? sd.emissive() : sd.diffuse());
			RenderMaterial mat = pass == 0 ? cutout : glow;
			for (int plane = 0; plane < 2; plane++) {
				for (int half = 0; half < 2; half++) {
					float y0 = half == 0 ? topY : botY;
					float y1 = 0.5f;
					for (int side = 0; side < 2; side++) { // both windings: the string is seen from every side
						float[][] p = new float[4][];
						float dx = plane == 0 ? w : 0f;
						float dz = plane == 0 ? 0f : w;
						p[0] = new float[] {0.5f - dx, y0, tipZ - dz};
						p[1] = new float[] {0.5f + dx, y0, tipZ + dz};
						p[2] = new float[] {0.5f + dx, y1, tipZ + dz};
						p[3] = new float[] {0.5f - dx, y1, tipZ - dz};
						float[] n = plane == 0 ? new float[] {0f, 0f, 1f} : new float[] {1f, 0f, 0f};
						if (side == 1) {
							float[] t = p[0];
							p[0] = p[1];
							p[1] = t;
							t = p[2];
							p[2] = p[3];
							p[3] = t;
							n = new float[] {-n[0], -n[1], -n[2]};
						}
						float[][] nn = {n, n, n, n};
						ObjGeometry.Quad q = new ObjGeometry.Quad(p, uv, nn, false);
						emit(em, q, sp, mat, pass == 0 ? 0xFFFFFFFF : data.manifest().emissiveTint, pass == 0 ? 0f : GLOW_OFFSET, NO_SHIFT);
					}
				}
			}
			if (pass == 0) {
				b.string = mb.build();
			} else {
				b.stringGlow = mb.build();
			}
		}
		return b;
	}

	/** The meshes of the drawn bow, see {@link #buildBow}. */
	static final class BowMeshes {
		Mesh bow;
		Mesh bowGlow;
		Mesh arrow;
		Mesh arrowGlow;
		Mesh string;
		Mesh stringGlow;
		/** The forming bow (translucent): index 0 the rest bow, 1 to 3 the flex bows; with their emissive overlays (an entry may be null). */
		Mesh[] formBase;
		Mesh[] formGlow;
		/** The straight string alone, for the forming (cutout) and its emissive overlay. */
		Mesh straight;
		Mesh straightGlow;
	}

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

	/** Glow shell: the same quads pushed out by {@link #GLOW_OFFSET} and drawn with the emissive material (the colour is set when drawn). */
	private Mesh buildShell(MeshBuilder mb, List<String> objects, Function<String, Sprite> sprite, RenderMaterial glowMat, int[] count) {
		QuadEmitter em = mb.getEmitter();
		for (String obj : objects) {
			ItemManifest.ObjectDef def = data.manifest().objects.get(obj);
			Sprite sp = sprite.apply(def.diffuse());
			for (ObjGeometry.Quad q : data.quads().get(obj)) {
				emit(em, q, sp, glowMat, 0xFFFFFFFF, GLOW_OFFSET, NO_SHIFT);
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
		/** Pink glow shell of the drawn blade (SEALED only, when the manifest has a draw entry); null otherwise. */
		Mesh bladeShell;
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
		/** Blade axis in model space: base at the saya mouth, unit direction towards the tip, length; null when unknown. */
		float[] bladeBase;
		float[] bladeDir;
		float bladeLen;
		/** The drawn blade cut into small fragments for the shikai dissolve; null when the blade axis is unknown. */
		BladeShards shards;
		/** The drawn sword with the pink texture and its glow (R12: the sword that the petals build), the sprite of the pink texture for its fragments; null when the item has no such object. */
		Mesh pinkSword;
		Mesh pinkSwordGlow;
		/** The pink sword as a translucent mesh: it fades out over the steel when the pink of the shikai blade goes. */
		Mesh pinkSwordFade;
		Sprite pinkSwordSprite;
		/** Uryu's bow with the pulled string and the nocked arrow (items with those three objects), null otherwise. */
		BowMeshes bow;
	}

	/** Fragments of the drawn blade per source quad along / across the longer edge (the shikai dissolve breaks them off one by one). */
	private static final int SHARD_LONG = 3;
	private static final int SHARD_SHORT = 2;

	/**
	 * The blade quads (centre at or past the saya mouth, s of 0 to 1 on the blade axis) cut into a {@code SHARD_LONG x SHARD_SHORT} grid by
	 * bilinear interpolation of position, uv and normal; the hilt part is left out. All with the emissive translucent material.
	 */
	private BladeShards buildShards(List<String> objects, Function<String, Sprite> sprite, RenderMaterial glowMat, RenderMaterial cutoutMat, RenderMaterial translucentMat, float[] base, float[] dir, float len) {
		List<float[]> pos = new ArrayList<>();
		List<float[]> nrm = new ArrayList<>();
		List<float[]> uv = new ArrayList<>();
		List<Sprite> sprites = new ArrayList<>();
		List<RenderMaterial> solids = new ArrayList<>();
		for (String obj : objects) {
			ItemManifest.ObjectDef def = data.manifest().objects.get(obj);
			Sprite sp = sprite.apply(def.diffuse());
			for (ObjGeometry.Quad q : data.quads().get(obj)) {
				float cx = 0;
				float cy = 0;
				float cz = 0;
				for (int c = 0; c < 4; c++) {
					cx += q.pos()[c][0] * 0.25f;
					cy += q.pos()[c][1] * 0.25f;
					cz += q.pos()[c][2] * 0.25f;
				}
				float s = ((cx - base[0]) * dir[0] + (cy - base[1]) * dir[1] + (cz - base[2]) * dir[2]) / len;
				if (s < 0f) {
					continue; // the hilt stays
				}
				float[][] p = q.pos();
				float e01 = dist(p[0], p[1]);
				float e12 = dist(p[1], p[2]);
				// the grid runs (nu x nv) over the corner order 0-1-2-3: nu along edge 0-1, nv along edge 1-2
				int nu = e01 >= e12 ? SHARD_LONG : SHARD_SHORT;
				int nv = e01 >= e12 ? SHARD_SHORT : SHARD_LONG;
				for (int iv = 0; iv < nv; iv++) {
					for (int iu = 0; iu < nu; iu++) {
						float[] fp = new float[12];
						float[] fn = new float[12];
						float[] fu = new float[8];
						float[][] corner = {{iu, iv}, {iu + 1, iv}, {iu + 1, iv + 1}, {iu, iv + 1}};
						for (int c = 0; c < 4; c++) {
							float u = corner[c][0] / nu;
							float v = corner[c][1] / nv;
							float w0 = (1 - u) * (1 - v);
							float w1 = u * (1 - v);
							float w2 = u * v;
							float w3 = (1 - u) * v;
							for (int a = 0; a < 3; a++) {
								fp[c * 3 + a] = w0 * q.pos()[0][a] + w1 * q.pos()[1][a] + w2 * q.pos()[2][a] + w3 * q.pos()[3][a];
								fn[c * 3 + a] = w0 * q.nrm()[0][a] + w1 * q.nrm()[1][a] + w2 * q.nrm()[2][a] + w3 * q.nrm()[3][a];
							}
							for (int a = 0; a < 2; a++) {
								fu[c * 2 + a] = w0 * q.uv()[0][a] + w1 * q.uv()[1][a] + w2 * q.uv()[2][a] + w3 * q.uv()[3][a];
							}
							float nl = (float) Math.sqrt(fn[c * 3] * fn[c * 3] + fn[c * 3 + 1] * fn[c * 3 + 1] + fn[c * 3 + 2] * fn[c * 3 + 2]);
							if (nl > 1e-6f) {
								fn[c * 3] /= nl;
								fn[c * 3 + 1] /= nl;
								fn[c * 3 + 2] /= nl;
							}
						}
						pos.add(fp);
						nrm.add(fn);
						uv.add(fu);
						sprites.add(sp);
						solids.add(def.translucent() ? translucentMat : cutoutMat);
					}
				}
			}
		}
		if (pos.isEmpty()) {
			return null;
		}
		int n = pos.size();
		BladeShards b = new BladeShards(n);
		b.material = glowMat;
		for (int k = 0; k < n; k++) {
			System.arraycopy(pos.get(k), 0, b.pos, k * 12, 12);
			System.arraycopy(nrm.get(k), 0, b.nrm, k * 12, 12);
			System.arraycopy(uv.get(k), 0, b.uv, k * 8, 8);
			b.sprite[k] = sprites.get(k);
			b.solid[k] = solids.get(k);
			float cx = 0;
			float cy = 0;
			float cz = 0;
			for (int c = 0; c < 4; c++) {
				cx += b.pos[k * 12 + c * 3] * 0.25f;
				cy += b.pos[k * 12 + c * 3 + 1] * 0.25f;
				cz += b.pos[k * 12 + c * 3 + 2] * 0.25f;
			}
			b.ctr[k * 3] = cx;
			b.ctr[k * 3 + 1] = cy;
			b.ctr[k * 3 + 2] = cz;
			b.s[k] = ((cx - base[0]) * dir[0] + (cy - base[1]) * dir[1] + (cz - base[2]) * dir[2]) / len;
			// radial unit vectors of the corners (from the blade axis outwards), for the bloom shells
			for (int c = 0; c < 4; c++) {
				float px = b.pos[k * 12 + c * 3] - base[0];
				float py = b.pos[k * 12 + c * 3 + 1] - base[1];
				float pz = b.pos[k * 12 + c * 3 + 2] - base[2];
				float al = px * dir[0] + py * dir[1] + pz * dir[2];
				float rx = px - dir[0] * al;
				float ry = py - dir[1] * al;
				float rz = pz - dir[2] * al;
				float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
				if (rl > 1e-5f) {
					b.rad[k * 12 + c * 3] = rx / rl;
					b.rad[k * 12 + c * 3 + 1] = ry / rl;
					b.rad[k * 12 + c * 3 + 2] = rz / rl;
				}
			}
		}
		return b;
	}

	private static float dist(float[] a, float[] b) {
		float x = a[0] - b[0];
		float y = a[1] - b[1];
		float z = a[2] - b[2];
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	/** Fragments of the drawn blade in model space (corner order 0-1-2-3 as in the source quads), see {@link #buildShards}. */
	static final class BladeShards {
		final int n;
		/** 4 corners x 3 floats per fragment. */
		final float[] pos;
		final float[] nrm;
		/** Unit vectors from the blade axis to the corners (zero on the axis), for the bloom shells. */
		final float[] rad;
		/** 4 corners x 2 floats, normalised to the sprite. */
		final float[] uv;
		final float[] ctr;
		/** Position of the centre along the blade axis: 0 at the saya mouth, 1 at the tip. */
		final float[] s;
		final Sprite[] sprite;
		/** The lit material of the source object (cutout or translucent), for the part of the blade that is still there. */
		final RenderMaterial[] solid;
		RenderMaterial material;

		BladeShards(int n) {
			this.n = n;
			pos = new float[n * 12];
			nrm = new float[n * 12];
			rad = new float[n * 12];
			uv = new float[n * 8];
			ctr = new float[n * 3];
			s = new float[n];
			sprite = new Sprite[n];
			solid = new RenderMaterial[n];
		}
	}
}
