package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.client.fx.ShikaiDissolve;
import dev.minebleach.reiatsutest.client.fx.FxTune;
import dev.minebleach.reiatsutest.core.fx.DissolveMath;
import dev.minebleach.reiatsutest.core.fx.DissolveSwirl;
import dev.minebleach.reiatsutest.core.obj.DrawRig;
import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import net.minecraft.entity.LivingEntity;
import dev.minebleach.reiatsutest.registry.ModComponents;
import dev.minebleach.reiatsutest.registry.ReleaseState;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * Baked OBJ item model: picks the mesh by release state (item component) and display context in
 * {@link #emitItemQuads}, then emits it (plus per-frame animated segments) to the Fabric renderer.
 */
public final class ObjItemBakedModel implements BakedModel {
	/** Dev stats for spike step 11: total nanoseconds and number of emitItemQuads calls. */
	public static final AtomicLong EMIT_NANOS = new AtomicLong();
	public static final AtomicLong EMIT_CALLS = new AtomicLong();
	public static final boolean STATS = Boolean.getBoolean("reiatsu.spike");

	private final ObjItemUnbakedModel.BakedStates states;
	private final ModelTransformation transformation;
	private final Sprite particle;
	private final dev.minebleach.reiatsutest.core.obj.ItemManifest manifest;

	ObjItemBakedModel(ObjItemUnbakedModel.BakedStates states, ModelTransformation transformation, Sprite particle,
			dev.minebleach.reiatsutest.core.obj.ItemManifest manifest) {
		this.manifest = manifest;
		this.states = states;
		this.transformation = transformation;
		this.particle = particle;
	}

	/** First person arm pose from the manifest, or null. */
	public dev.minebleach.reiatsutest.core.obj.ItemManifest.ArmPose armPose(ItemStack stack) {
		return manifest.armPose(stack.getOrDefault(ModComponents.RELEASE_STATE, ReleaseState.SEALED).asString());
	}

	/** True if the stack's state draws something in hand (bankai Byakuya draws nothing, so no arm either). */
	public boolean drawsInHand(ItemStack stack) {
		ReleaseState state = stack.getOrDefault(ModComponents.RELEASE_STATE, ReleaseState.SEALED);
		return !states.byState[state.ordinal()].handEmpty;
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
		long t0 = STATS ? System.nanoTime() : 0L;
		ReleaseState state = stack.getOrDefault(ModComponents.RELEASE_STATE, ReleaseState.SEALED);
		ModelTransformationMode mode = context.itemTransformationMode();
		QuadEmitter em = context.getEmitter();
		ObjItemUnbakedModel.StateMeshes sm = states.byState[state.ordinal()];
		if (state == ReleaseState.SHIKAI && states.saya != null && isHandMode(mode)) {
			// plan A6: the Byakuya release holds the drawn blade for 0.4 s and turns it pink before it bursts into petals
			LivingEntity ph = DrawTracker.renderEntity();
			if (ph != null && ph.getMainHandStack() == stack) {
				int tint = dev.minebleach.reiatsutest.client.fx.ReleaseHold.tint(ph.getId());
				if (tint != 0) {
					ObjItemUnbakedModel.StateMeshes bare = states.byState[ReleaseState.SEALED.ordinal()];
					context.pushTransform(q -> {
						for (int c = 0; c < 4; c++) {
							q.color(c, mulColor(q.color(c), tint));
						}
						return true;
					});
					bare.handBase.outputTo(em);
					if (bare.handGlow != null) {
						bare.handGlow.outputTo(em);
					}
					context.popTransform();
					// plan G-glow round 2: the glow builds up around the blade during the hold (stacked translucent shells)
					ShikaiDissolve.Look lk = states.shards != null ? ShikaiDissolve.look(ph.getId()) : null;
					if (lk != null) {
						emitBloom(states, lk, context);
					}
					stats(t0);
					return;
				}
			}
		}
		if (state == ReleaseState.SHIKAI && states.bladeBase != null && isHandMode(mode)) {
			// plan G-glow: after the pink hold the drawn blade glows and breaks into petals from the tip towards the hilt
			LivingEntity dp = DrawTracker.renderEntity();
			if (dp != null && dp.getMainHandStack() == stack) {
				ShikaiDissolve.Look lk = states.shards != null && ShikaiDissolve.look(dp.getId()) != null ? ShikaiDissolve.look(dp.getId()) : null;
				if (lk != null && lk.shards) {
					emitShardDissolve(states, lk, context);
					stats(t0);
					return;
				}
				if (ShikaiDissolve.active(dp.getId())) {
					emitDissolve(states, ShikaiDissolve.progress(dp.getId()), context);
					stats(t0);
					return;
				}
			}
		}
		if (states.saya != null && isHandMode(mode)) {
			// B4 step 2: the scabbard is drawn separately; the sword of the main hand follows the draw rig
			LivingEntity holder = DrawTracker.renderEntity();
			if (holder != null && holder.getMainHandStack() == stack) {
				float p = DrawTracker.effectiveProgress(holder, stack);
				if (state == ReleaseState.SEALED || state == ReleaseState.BASE || p < 1f) {
					sm = states.byState[ReleaseState.SEALED.ordinal()]; // the bare drawn sword (BASE uses the same mesh)
					if (p < 1f && mode.isFirstPerson()) {
						emitRigged(sm, rig(mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND).at(p), context);
						stats(t0);
						return;
					}
					if (p < 1f) {
						stats(t0); // third person: the sword is in the hip scabbard or travelling to the hand, ScabbardRenderer draws it (T3)
						return;
					}
				}
			} else if (state == ReleaseState.SEALED) {
				sm = states.byState[ReleaseState.SEALED.ordinal()]; // an off hand or loose stack: the whole sheathed sword
				sm.otherBase.outputTo(em);
				stats(t0);
				return;
			}
		}
		switch (mode) {
			case GUI -> {
				if (sm.icon != null) {
					sm.icon.outputTo(em);
				}
			}
			case GROUND, FIXED, HEAD -> {
				sm.otherBase.outputTo(em);
				if (sm.otherGlow != null) {
					sm.otherGlow.outputTo(em);
				}
				emitDynamic(sm, context);
			}
			default -> { // hand modes, and NONE (R1.3: fall back to the hand mesh)
				sm.handBase.outputTo(em);
				if (sm.handGlow != null) {
					sm.handGlow.outputTo(em);
				}
				emitDynamic(sm, context);
			}
		}
		if (STATS) {
			EMIT_NANOS.addAndGet(System.nanoTime() - t0);
			EMIT_CALLS.incrementAndGet();
		}
	}

	/** Component wise product of two ARGB colours (alpha of the first kept). */
	private static int mulColor(int a, int b) {
		int r = ((a >> 16) & 255) * ((b >> 16) & 255) / 255;
		int g = ((a >> 8) & 255) * ((b >> 8) & 255) / 255;
		int bl = (a & 255) * (b & 255) / 255;
		return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
	}

	private static boolean isHandMode(ModelTransformationMode mode) {
		return switch (mode) {
			case GUI, GROUND, FIXED, HEAD -> false;
			default -> true;
		};
	}

	private void stats(long t0) {
		if (STATS) {
			EMIT_NANOS.addAndGet(System.nanoTime() - t0);
			EMIT_CALLS.incrementAndGet();
		}
	}

	/** The bare sword (hilt + blade) transformed by a rigid transform of the model space, normals rotated with it. */
	private static void emitRigged(ObjItemUnbakedModel.StateMeshes sm, DrawRig.Rigid rg, RenderContext context) {
		Vector3f vv = new Vector3f();
		context.pushTransform(q -> {
			for (int c = 0; c < 4; c++) {
				q.copyPos(c, vv);
				float[] v = rg.apply(new float[] {vv.x, vv.y, vv.z});
				q.pos(c, v[0], v[1], v[2]);
				if (q.hasNormal(c)) {
					q.copyNormal(c, vv);
					float[] n = rg.rotateNormal(new float[] {vv.x, vv.y, vv.z});
					q.normal(c, n[0], n[1], n[2]);
				}
			}
			return true;
		});
		sm.handBase.outputTo(context.getEmitter());
		if (sm.handGlow != null) {
			sm.handGlow.outputTo(context.getEmitter());
		}
		context.popTransform();
	}

	/**
	 * Plan G-glow: the drawn blade breaks into petals from the hilt towards the tip ({@link DissolveMath}). Three parts, all in model space:
	 * the shikai hilt (tinted pink, back to its own colour as the dissolve ends), the remaining drawn blade (quads behind the front
	 * removed by the transform, pink), and its glow shell (the same quads pushed out, emissive, fading with the dissolve).
	 */
	private static void emitDissolve(ObjItemUnbakedModel.BakedStates st, double p, RenderContext context) {
		QuadEmitter em = context.getEmitter();
		boolean tipFirst = ShikaiDissolve.tipFirst();
		double boundary = DissolveMath.boundary(p, tipFirst);
		float[] base = st.bladeBase;
		float[] dir = st.bladeDir;
		float len = st.bladeLen;
		int hilt = ShikaiDissolve.hiltTint(p);
		context.pushTransform(q -> {
			for (int c = 0; c < 4; c++) {
				q.color(c, mulColor(q.color(c), hilt));
			}
			return true;
		});
		ObjItemUnbakedModel.StateMeshes shikai = st.byState[ReleaseState.SHIKAI.ordinal()];
		shikai.handBase.outputTo(em);
		if (shikai.handGlow != null) {
			shikai.handGlow.outputTo(em);
		}
		context.popTransform();
		ObjItemUnbakedModel.StateMeshes bare = st.byState[ReleaseState.SEALED.ordinal()];
		int blade = ShikaiDissolve.bladeTint();
		context.pushTransform(q -> {
			if (!bladeQuad(q, base, dir, len, boundary, tipFirst)) {
				return false;
			}
			for (int c = 0; c < 4; c++) {
				q.color(c, mulColor(q.color(c), blade));
			}
			return true;
		});
		bare.handBase.outputTo(em);
		context.popTransform();
		int shell = ShikaiDissolve.shellArgb(p);
		if (bare.bladeShell != null && (shell >>> 24) != 0) {
			context.pushTransform(q -> {
				if (!bladeQuad(q, base, dir, len, boundary, tipFirst)) {
					return false;
				}
				for (int c = 0; c < 4; c++) {
					q.color(c, shell);
				}
				return true;
			});
			bare.bladeShell.outputTo(em);
			context.popTransform();
		}
	}

	// ------------------------------------------------------------------------------------------ plan G-glow round 2: fragments and bloom

	/** Model-space offsets (units) of the three bloom shells, inner to outer, their alpha weights and white mix. */
	private static final float[] BLOOM_OFF = {0.006f, 0.015f, 0.030f};
	private static final double[] BLOOM_ALPHA = {1.0, 0.55, 0.28};
	private static final double[] BLOOM_WHITE = {0.55, 0.30, 0.10};
	private static float[] detScratch = new float[0];
	private static final float[] FRAG = new float[12];
	private static final double[] PRM = new double[DissolveSwirl.COUNT];
	private static final double[] OFF = new double[3];

	private static double smooth(double e0, double e1, double x) {
		double t = x <= e0 ? 0 : x >= e1 ? 1 : (x - e0) / (e1 - e0);
		return t * t * (3 - 2 * t);
	}

	/** One fragment of the blade as a quad: corner positions in {@code p12}, the rest from the baked arrays. */
	private static void emitFragment(QuadEmitter em, ObjItemUnbakedModel.BladeShards sh, int k, float[] p12, int argb, net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial mat) {
		for (int c = 0; c < 4; c++) {
			em.pos(c, p12[c * 3], p12[c * 3 + 1], p12[c * 3 + 2]);
			em.uv(c, sh.uv[k * 8 + c * 2], sh.uv[k * 8 + c * 2 + 1]);
			em.normal(c, sh.nrm[k * 12 + c * 3], sh.nrm[k * 12 + c * 3 + 1], sh.nrm[k * 12 + c * 3 + 2]);
			em.color(c, argb);
		}
		em.material(mat);
		em.cullFace(null);
		em.nominalFace(null);
		em.spriteBake(sh.sprite[k], MutableQuadView.BAKE_NORMALIZED);
		em.emit();
	}

	/** The stack of translucent shells around the fragments that are still on the blade ({@code det} null: all of them), emissive, pink to white. */
	private static void emitBloomLayers(ObjItemUnbakedModel.BakedStates st, ShikaiDissolve.Look lk, float[] det, RenderContext context) {
		ObjItemUnbakedModel.BladeShards sh = st.shards;
		QuadEmitter em = context.getEmitter();
		double width = FxTune.d("release.glowBloomWidth", 1.0) * (0.8 + 0.4 * Math.min(1.3, lk.level));
		double el = Math.max(0, lk.el);
		for (int layer = 0; layer < 3; layer++) {
			double alpha = Math.min(0.85, lk.bloom * BLOOM_ALPHA[layer] * lk.level);
			if (alpha < 0.01) {
				continue;
			}
			int argb = ShikaiDissolve.glowArgb(BLOOM_WHITE[layer], alpha);
			float off = (float) (BLOOM_OFF[layer] * width);
			for (int k = 0; k < sh.n; k++) {
				if (det != null && el >= det[k]) {
					continue;
				}
				for (int c = 0; c < 12; c++) {
					FRAG[c] = sh.pos[k * 12 + c] + sh.rad[k * 12 + c] * off;
				}
				emitFragment(em, sh, k, FRAG, argb, sh.material);
			}
		}
	}

	/** The glow around the whole blade during the pink hold. */
	private static void emitBloom(ObjItemUnbakedModel.BakedStates st, ShikaiDissolve.Look lk, RenderContext context) {
		emitBloomLayers(st, lk, null, context);
	}

	/**
	 * Plan G-glow round 2: the dissolve with fragments. The hilt (tinted back to its own colour), the part of the blade that is still
	 * there (small fragments, pink to white-pink, with the bloom shells around), and the fragments behind the front: each flies off in
	 * the spiral of {@link DissolveSwirl} for {@code release.shardLife} seconds, turning, shrinking and fading while the petal of the
	 * world part ({@code ShikaiDissolve}) takes over from the same place.
	 */
	private static void emitShardDissolve(ObjItemUnbakedModel.BakedStates st, ShikaiDissolve.Look lk, RenderContext context) {
		QuadEmitter em = context.getEmitter();
		ObjItemUnbakedModel.BladeShards sh = st.shards;
		double p = Math.max(0, lk.p);
		double el = Math.max(0, lk.el);
		// the hilt: the shikai hilt, pink at the start and back to its own colour at the end
		int hilt = ShikaiDissolve.hiltTint(p);
		context.pushTransform(q -> {
			for (int c = 0; c < 4; c++) {
				q.color(c, mulColor(q.color(c), hilt));
			}
			return true;
		});
		ObjItemUnbakedModel.StateMeshes shikai = st.byState[ReleaseState.SHIKAI.ordinal()];
		shikai.handBase.outputTo(em);
		if (shikai.handGlow != null) {
			shikai.handGlow.outputTo(em);
		}
		context.popTransform();
		if (detScratch.length < sh.n) {
			detScratch = new float[sh.n];
		}
		float[] det = detScratch;
		for (int k = 0; k < sh.n; k++) {
			det[k] = (float) (lk.dur * DissolveMath.detachProgress(sh.s[k], lk.tipFirst));
		}
		// the blade that is left: pink going to white-pink around the break
		double white = 0.5 * Math.sin(Math.PI * Math.min(1.0, Math.max(0.0, p / 0.55)));
		int solid = ShikaiDissolve.bladeGlowTint(white);
		for (int k = 0; k < sh.n; k++) {
			if (el >= det[k]) {
				continue;
			}
			System.arraycopy(sh.pos, k * 12, FRAG, 0, 12);
			emitFragment(em, sh, k, FRAG, solid, sh.solid[k]);
		}
		emitBloomLayers(st, lk, det, context);
		// the fragments in flight
		float[] dir = st.bladeDir;
		double dx = dir[0];
		double dy = dir[1];
		double dz = dir[2];
		double rx = Math.abs(dy) > 0.95 ? 1 : 0;
		double ry = Math.abs(dy) > 0.95 ? 0 : 1;
		double e1x = -dz * ry;
		double e1y = dz * rx;
		double e1z = dx * ry - dy * rx;
		double e1l = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
		e1x /= e1l;
		e1y /= e1l;
		e1z /= e1l;
		double e2x = dy * e1z - dz * e1y;
		double e2y = dz * e1x - dx * e1z;
		double e2z = dx * e1y - dy * e1x;
		double reach = st.bladeLen * FxTune.d("release.shardReach", 1.0);
		double life = Math.max(0.05, lk.shardLife);
		for (int k = 0; k < sh.n; k++) {
			double tau = el - det[k];
			if (tau < 0 || tau >= life) {
				continue;
			}
			DissolveSwirl.params(lk.seed, k, lk.spiral, lk.radius, lk.lift, lk.life, PRM);
			DissolveSwirl.offset(PRM, tau, OFF);
			double ox = (e1x * OFF[0] + e2x * OFF[2] + dx * OFF[1]) * reach;
			double oy = (e1y * OFF[0] + e2y * OFF[2] + dy * OFF[1]) * reach;
			double oz = (e1z * OFF[0] + e2z * OFF[2] + dz * OFF[1]) * reach;
			double u = tau / life;
			double scale = 1.0 - 0.85 * smooth(0.2, 1.0, u);
			double ang = PRM[DissolveSwirl.SPIN] * tau * 0.7;
			double ca = Math.cos(ang);
			double sa = Math.sin(ang);
			double kx = PRM[DissolveSwirl.AX];
			double ky = PRM[DissolveSwirl.AY];
			double kz = PRM[DissolveSwirl.AZ];
			float cx = sh.ctr[k * 3];
			float cy = sh.ctr[k * 3 + 1];
			float cz = sh.ctr[k * 3 + 2];
			for (int c = 0; c < 4; c++) {
				double vx = sh.pos[k * 12 + c * 3] - cx;
				double vy = sh.pos[k * 12 + c * 3 + 1] - cy;
				double vz = sh.pos[k * 12 + c * 3 + 2] - cz;
				double dot = kx * vx + ky * vy + kz * vz;
				double rxx = vx * ca + (ky * vz - kz * vy) * sa + kx * dot * (1 - ca);
				double ryy = vy * ca + (kz * vx - kx * vz) * sa + ky * dot * (1 - ca);
				double rzz = vz * ca + (kx * vy - ky * vx) * sa + kz * dot * (1 - ca);
				FRAG[c * 3] = (float) (cx + rxx * scale + ox);
				FRAG[c * 3 + 1] = (float) (cy + ryy * scale + oy);
				FRAG[c * 3 + 2] = (float) (cz + rzz * scale + oz);
			}
			double heat = DissolveSwirl.heat(tau, 0.15);
			double alpha = 1.0 - smooth(0.35, 1.0, u);
			emitFragment(em, sh, k, FRAG, ShikaiDissolve.glowArgb(0.25 + 0.6 * heat, alpha), sh.material);
		}
	}

	/**
	 * Hands the model to view matrix of the item (display transform and centre shift as {@code ItemRenderer} applies them) and the blade data
	 * to {@link ShikaiDissolve}, which places the world part of the effect (halo, petals, sparks) with it. Called by the hand item mixin
	 * once the matrix stack holds every pose change; leaves {@code m} unchanged.
	 */
	public void captureDissolvePose(LivingEntity e, ModelTransformationMode mode, boolean leftHanded, net.minecraft.client.util.math.MatrixStack m) {
		if (states.bladeBase == null) {
			return;
		}
		m.push();
		getTransformation().getTransformation(mode).apply(leftHanded, m);
		m.translate(-0.5f, -0.5f, -0.5f);
		ObjItemUnbakedModel.BladeShards sh = states.shards;
		ShikaiDissolve.capturePose(e.getId(), mode.isFirstPerson(), m.peek().getPositionMatrix(), states.bladeBase, states.bladeDir, states.bladeLen,
				sh != null ? sh.ctr : null, sh != null ? sh.s : null);
		m.pop();
	}

	/** Keeps a quad of the drawn blade while the dissolve front has not reached its centre; the hilt part (s below 0) is never kept. */
	private static boolean bladeQuad(MutableQuadView q, float[] base, float[] dir, float len, double boundary, boolean tipFirst) {
		Vector3f v = new Vector3f();
		double sum = 0;
		for (int c = 0; c < 4; c++) {
			q.copyPos(c, v);
			sum += DissolveMath.along(v.x, v.y, v.z, base[0], base[1], base[2], dir[0], dir[1], dir[2], len);
		}
		double s = sum / 4;
		return s >= 0 && DissolveMath.keep(s, boundary, tipFirst);
	}

	// ------------------------------------------------------------------ scabbard access (ScabbardRenderer, FirstPersonHand)

	/** The scabbard mesh (model space, grip at 0.5), or null when the item has none. */
	public Mesh sayaMesh() {
		return states.saya;
	}

	/** The bare sword of the sealed state (hilt + blade), model space. */
	public Mesh swordMesh() {
		return states.byState[ReleaseState.SEALED.ordinal()].handBase;
	}

	public ItemManifest manifest() {
		return manifest;
	}

	private DrawRig rigRight;
	private DrawRig rigLeft;
	private float rigMult = -1f;
	private String keyRight;
	private String keyLeft;

	/**
	 * The draw rig for the right (main arm right) or left hand pose; rebuilt when the first person scale option or the held lift
	 * (manifest or dev override) changes. The stow move is compensated by the lift: the lift raises the sword and the scabbard
	 * together everywhere in the hand, the sheathed sword stays where it was.
	 */
	public DrawRig rig(boolean left) {
		float m = dev.minebleach.reiatsutest.client.ClientOptions.firstPersonScaleMultiplier;
		float[] lift = HeldPose.of(manifest).lift();
		String key = m + ":" + java.util.Arrays.toString(lift);
		if (m != rigMult) {
			rigMult = m;
			rigRight = null;
			rigLeft = null;
		}
		DrawRig r = left ? rigLeft : rigRight;
		if (r == null || !key.equals(left ? keyLeft : keyRight)) {
			ItemManifest.Stow st = manifest.stow != null ? manifest.stow
					: new ItemManifest.Stow(new float[3], new float[3], 0.55f, 0.6f, 0.3f);
			float[] mv = st.move();
			st = new ItemManifest.Stow(st.rot(), new float[] {mv[0] - lift[0], mv[1] - lift[1], mv[2] - lift[2]}, st.slideEnd(), st.pull(),
					st.retract(), st.armRollStow(), st.armRollHeld());
			ModelTransformationMode mode = left ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
			r = new DrawRig(states.sayaMeta, HandMath.stowToModel(getTransformation().getTransformation(mode), left, st), st.slideEnd(), st.pull(), st.retract());
			if (left) {
				rigLeft = r;
				keyLeft = key;
			} else {
				rigRight = r;
				keyRight = key;
			}
		}
		return r;
	}

	/** First person, ground and head draw only this many chain segments (ADR section 1: the full ribbon would fill the screen); fixed draws none, third person all. */
	private static final int SHORT_CHAIN_SEGMENTS = 4;

	/**
	 * Chain of hinged segments waving every frame (spike step 9: proves per-frame quads in emitItemQuads). Each hinge
	 * swings a small angle about the model X axis with a travelling phase lag, so a 10 segment ribbon undulates like
	 * cloth instead of whipping (angles accumulate up the chain).
	 */
	private static void emitDynamic(ObjItemUnbakedModel.StateMeshes sm, RenderContext context) {
		if (sm.dynamic.isEmpty()) {
			return;
		}
		double t = System.nanoTime() / 1.0e9;
		int n = sm.dynamic.size();
		ModelTransformationMode mode = context.itemTransformationMode();
		if (mode == ModelTransformationMode.FIXED) {
			n = 0; // item frame: the 2.5 m ribbon would leave the frame
		} else if (mode.isFirstPerson() || mode == ModelTransformationMode.GROUND || mode == ModelTransformationMode.HEAD) {
			n = Math.min(n, SHORT_CHAIN_SEGMENTS);
		}
		float[] angles = new float[n];
		for (int i = 0; i < n; i++) {
			angles[i] = (float) ((0.10 + 0.012 * i) * Math.sin(2 * Math.PI * 0.8 * t - i * 0.75));
		}
		for (int i = 0; i < n; i++) {
			final int seg = i;
			context.pushTransform(q -> {
				Vector3f v = new Vector3f();
				for (int c = 0; c < 4; c++) {
					q.copyPos(c, v);
					// apply segment seg first, then its parents up the chain (each rotates about its own hinge)
					for (int k = seg; k >= 0; k--) {
						rotateX(v, sm.dynamicHinges.get(k), angles[k]);
					}
					q.pos(c, v);
				}
				return true;
			});
			sm.dynamic.get(i).outputTo(context.getEmitter());
			Mesh glow = sm.dynamicGlow.get(i);
			if (glow != null) {
				glow.outputTo(context.getEmitter());
			}
			context.popTransform();
		}
	}

	private static void rotateX(Vector3f v, float[] hinge, float angle) {
		float y = v.y - hinge[1];
		float z = v.z - hinge[2];
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		v.y = hinge[1] + y * cos - z * sin;
		v.z = hinge[2] + y * sin + z * cos;
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
		return false;
	}

	@Override
	public boolean isBuiltin() {
		return false;
	}

	@Override
	public Sprite getParticleSprite() {
		return particle;
	}

	@Override
	public ModelTransformation getTransformation() {
		float m = dev.minebleach.reiatsutest.client.ClientOptions.firstPersonScaleMultiplier;
		if (m == 1f) {
			return transformation;
		}
		if (m != scaledMult || scaled == null) { // first person scale multiplier of the client options, about the grip
			scaled = new ModelTransformation(transformation.thirdPersonLeftHand, transformation.thirdPersonRightHand,
					scaleBy(transformation.firstPersonLeftHand, m), scaleBy(transformation.firstPersonRightHand, m),
					transformation.head, transformation.gui, transformation.ground, transformation.fixed);
			scaledMult = m;
		}
		return scaled;
	}

	private float scaledMult = 1f;
	private ModelTransformation scaled;

	private static net.minecraft.client.render.model.json.Transformation scaleBy(
			net.minecraft.client.render.model.json.Transformation t, float m) {
		return new net.minecraft.client.render.model.json.Transformation(t.rotation, t.translation,
				new org.joml.Vector3f(t.scale).mul(m));
	}

	@Override
	public ModelOverrideList getOverrides() {
		return ModelOverrideList.EMPTY;
	}
}
