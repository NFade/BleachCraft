package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.registry.ModComponents;
import dev.minebleach.reiatsutest.registry.ReleaseState;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
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
	private final dev.minebleach.reiatsutest.core.obj.ItemManifest.ArmPose armPose;

	ObjItemBakedModel(ObjItemUnbakedModel.BakedStates states, ModelTransformation transformation, Sprite particle,
			dev.minebleach.reiatsutest.core.obj.ItemManifest.ArmPose armPose) {
		this.armPose = armPose;
		this.states = states;
		this.transformation = transformation;
		this.particle = particle;
	}

	/** First person arm pose from the manifest, or null. */
	public dev.minebleach.reiatsutest.core.obj.ItemManifest.ArmPose armPose() {
		return armPose;
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
		ObjItemUnbakedModel.StateMeshes sm = states.byState[state.ordinal()];
		ModelTransformationMode mode = context.itemTransformationMode();
		QuadEmitter em = context.getEmitter();
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
		return transformation;
	}

	@Override
	public ModelOverrideList getOverrides() {
		return ModelOverrideList.EMPTY;
	}
}
