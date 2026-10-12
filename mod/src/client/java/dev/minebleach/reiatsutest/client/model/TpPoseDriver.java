package dev.minebleach.reiatsutest.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.combat.TpPose;
import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

/**
 * Client glue of the third person pose ({@link TpPose}, tp_pose 2026-10-11). Once per frame and player it reads the facts
 * (draw progress of {@link DrawTracker}, block held, handedness, arm model), smooths the block weight and the hold weight in real
 * time, runs the pure solver and keeps the result. Three consumers read it in the same frame: {@link TpPoseLayer} (the arms, a
 * playerAnimator layer), {@link BcItemPose} (the held sword transform) and {@link ScabbardRenderer} (scabbard and the sword
 * while it is not in the hand). Armor stands and everything that is not a player keep the old hip scabbard without arms.
 *
 * <p>Hot tuning (dev only, {@link HeldPose}): {@code pose_override.json} section {@code "tp"} with dotted keys, any subset, for
 * example {@code {"tp": {"hold.pos": [0, 6.5, -5.4], "hold.tilt": 12, "grip.c_r": -0.8}}}; see {@link TpPose} for the key names
 * (right handed layout, px and degrees; a left hander is mirrored by the solver).
 */
public final class TpPoseDriver {
	private static final class St {
		double b;
		double hold;
		long nanos;
		TpPose.Result res;
		long resNanos;
		boolean holding;
	}

	private static final Map<Integer, St> STATES = new HashMap<>();

	/** {@link TpPose.Params} backed by the dev override file; defaults when there is none (production). */
	private static final TpPose.Params PARAMS = new TpPose.Params() {
		private JsonElement raw(String key) {
			JsonObject o = HeldPose.overrideObject();
			if (o != null && o.has("tp") && o.get("tp").isJsonObject()) {
				JsonObject t = o.getAsJsonObject("tp");
				if (t.has(key)) {
					return t.get(key);
				}
			}
			return null;
		}

		@Override
		public double num(String key, double def) {
			JsonElement e = raw(key);
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
		}

		@Override
		public double[] vec(String key, double[] def) {
			JsonElement e = raw(key);
			if (e != null && e.isJsonArray() && e.getAsJsonArray().size() >= 3) {
				JsonArray a = e.getAsJsonArray();
				return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
			}
			return def;
		}
	};

	private TpPoseDriver() {
	}

	public static TpPose.Params params() {
		return PARAMS;
	}

	/** True when the pose of this entity can be solved: a player with a zanpakuto (with a scabbard model) in the main hand. */
	private static boolean eligible(LivingEntity e) {
		if (!(e instanceof AbstractClientPlayerEntity p) || !(p.getMainHandStack().getItem() instanceof ZanpakutoItem)) {
			return false;
		}
		return !p.isFallFlying() && !p.isSwimming() && !p.isSleeping() && !p.isClimbing() && p.isAlive();
	}

	/**
	 * True while the entity holds a zanpakuto block (round6 tp_block). {@link ZanpakutoItem#isBlocking} first; two more ways for the
	 * moments in which it says no although the block is on: (1) the local player: the client puts the active item at once on the click
	 * ({@code setCurrentHand} does not set the using flag on the client, the flag comes back from the server one ping later), so the
	 * third person pose follows the click without that delay; (2) the flag is there (tracked data {@code LIVING_FLAGS}, main hand) but
	 * the client could not resolve the active stack ({@code onTrackedDataSet} copies it from the hand at the moment the flag arrives; with
	 * the equipment packet behind the flag it stays empty, and the next tick clears it, so {@code getActiveItem} is empty for the whole
	 * block). UNVERIFIED in game.
	 */
	static boolean blockingOf(LivingEntity e) {
		if (ZanpakutoItem.isBlocking(e)) {
			return true;
		}
		if (e == MinecraftClient.getInstance().player) {
			ItemStack a = e.getActiveItem();
			if (!a.isEmpty() && a.getItem() instanceof ZanpakutoItem && e.getItemUseTimeLeft() > 0) {
				return true;
			}
		}
		return e.isUsingItem() && e.getActiveHand() == Hand.MAIN_HAND && e.getMainHandStack().getItem() instanceof ZanpakutoItem;
	}

	/**
	 * Advances the smoothing of this entity and re-solves the pose when it is stale (more than 2 ms since the last solve, i.e. once
	 * per frame). Returns the state or null for an entity without a pose. Render thread.
	 */
	private static St update(LivingEntity e) {
		if (!(e instanceof AbstractClientPlayerEntity player)) {
			return null;
		}
		St st = STATES.get(e.getId());
		long now = System.nanoTime();
		boolean elig = eligible(e);
		boolean holding = elig;
		ItemStack main = e.getMainHandStack();
		ObjItemBakedModel om = holding ? ScabbardRenderer.modelOf(e, main) : null;
		holding = om != null;
		if (st == null) {
			if (!holding) {
				debugLog(e, null, elig, holding, now);
				return null;
			}
			st = new St();
			STATES.put(e.getId(), st);
			if (STATES.size() > 64) {
				STATES.values().removeIf(x -> x != null && now - x.nanos > 5_000_000_000L && !x.holding);
			}
		}
		double dt = st.nanos == 0L ? 0.0 : (now - st.nanos) / 1.0e9;
		if (st.nanos == 0L || dt >= 0.001) {
			dt = Math.min(0.25, dt);
			boolean blocking = holding && blockingOf(e);
			double up = Math.max(0.01, PARAMS.num("guard.in_s", 0.10));
			double down = Math.max(0.01, PARAMS.num("guard.out_s", 0.15));
			double fade = Math.max(0.01, PARAMS.num("hold.fade_s", 0.15));
			st.b = blocking ? Math.min(1.0, st.b + dt / up) : Math.max(0.0, st.b - dt / down);
			st.hold = holding ? Math.min(1.0, st.hold + dt / fade) : Math.max(0.0, st.hold - dt / fade);
			st.nanos = now;
		}
		st.holding = holding;
		if (holding && (st.res == null || now - st.resNanos > 2_000_000L)) {
			float p = DrawTracker.effectiveProgress(e, main);
			ItemManifest man = om.manifest();
			ItemManifest.Hip h = ScabbardRenderer.hipOf(man);
			Transformation tr = om.getTransformation().getTransformation(ModelTransformationMode.THIRD_PERSON_RIGHT_HAND);
			TpPose.Hip hip = new TpPose.Hip(d3(h.posPx()), d3(h.dir()), h.scale());
			TpPose.Display disp = new TpPose.Display(new double[] {tr.rotation.x(), tr.rotation.y(), tr.rotation.z()},
					new double[] {tr.translation.x(), tr.translation.y(), tr.translation.z()}, tr.scale.x());
			boolean thin = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
			boolean left = e.getMainArm() == Arm.LEFT;
			st.res = TpPose.solve(new TpPose.Input(p, st.b, left, thin, hip, disp, om.rig(false)), PARAMS);
			st.resNanos = now;
		}
		debugLog(e, st, elig, holding, now);
		return st;
	}

	// ------------------------------------------------------------------ round6 tp_block: debug log and the last word on the arms

	/** Dev switch {@code "tp": {"debug": 1}} in pose_override.json (the file is only read in a development environment). */
	private static boolean debugOn() {
		HeldPose.poll();
		return PARAMS.num("debug", 0) > 0.5;
	}

	private static final Map<Integer, Long> LOG_AT = new HashMap<>();

	/**
	 * {@code tp.debug=1}: at most once a second per player that holds a zanpakuto in the main hand and uses an item or has a block weight,
	 * one INFO line {@code [tp] block for <name> blocking=.. eligible=.. layerWeight=..} (then the numbers that tell where it breaks:
	 * vanilla using flag, active item and hand, the block weight {@code b}, how many frames the layer wrote the arms and what the
	 * final model pass did, see {@link #finishPass}).
	 */
	private static void debugLog(LivingEntity e, St st, boolean eligible, boolean holding, long now) {
		if (!(e instanceof AbstractClientPlayerEntity p) || !(p.getMainHandStack().getItem() instanceof ZanpakutoItem) || !debugOn()) {
			return;
		}
		boolean using = e.isUsingItem();
		if (!using && (st == null || st.b <= 0.001)) {
			return;
		}
		Long last = LOG_AT.get(e.getId());
		if (last != null && now - last < 1_000_000_000L) {
			return;
		}
		LOG_AT.put(e.getId(), now);
		if (LOG_AT.size() > 64) {
			LOG_AT.values().removeIf(x -> now - x > 10_000_000_000L);
		}
		TpPoseLayer.Trace tr = TpPoseLayer.trace(e.getId());
		MinecraftClient mc = MinecraftClient.getInstance();
		ReiatsuTest.LOGGER.info("[tp] block for {} blocking={} eligible={} layerWeight={} | holding={} b={} vanillaBlocking={} using={} active={} hand={} "
				+ "useLeft={} local={} firstPerson={} | layerFrames={} modelPasses={} lastPass={} restored={} applied={}",
				p.getName().getString(), holding && blockingOf(e), eligible, st == null ? "none" : String.format("%.2f", st.hold), holding,
				st == null ? "none" : String.format("%.2f", st.b), ZanpakutoItem.isBlocking(e), using,
				e.getActiveItem().isEmpty() ? "empty" : e.getActiveItem().getItem().toString(), e.getActiveHand(), e.getItemUseTimeLeft(),
				e == mc.player, e == mc.player && mc.options.getPerspective().isFirstPerson(), tr.layerFrames, tr.passes, tr.lastPass,
				tr.restored, tr.applied);
	}

	/**
	 * Last word on the arms of a blocking player (round6 tp_block), called at the RETURN of {@code PlayerEntityModel#setAngles}
	 * ({@code PlayerEntityModelMixin}), i.e. after vanilla ({@code BipedEntityModel}, {@code ArmPose.BLOCK}) and after playerAnimator
	 * ({@link TpPoseLayer} runs inside the same method, before the sleeves are copied). While the block weight is up it makes sure
	 * the arms hold what the solver wants: if the layer did not write an arm in this pass (its stack was inactive or never asked) the
	 * pose is blended in here with the same weight and numbers; if something changed the arm after the layer, the layer's values are put
	 * back. Idle when the layer worked and nothing came after it (the normal case). Better Combat strikes are not blocked while the
	 * block is on ({@code BcCombatGate}), so there is no strike to override here.
	 */
	public static void finishPass(PlayerEntityModel<?> m, AbstractClientPlayerEntity p) {
		TpPoseLayer.Trace tr = TpPoseLayer.trace(p.getId());
		tr.passes++;
		tr.lastPass = "idle";
		St st = STATES.get(p.getId());
		if (st == null || !st.holding || st.res == null || st.b <= 0.02) {
			return;
		}
		boolean changed = false;
		for (int i = 0; i < 2; i++) {
			net.minecraft.client.model.ModelPart part = i == 0 ? m.rightArm : m.leftArm;
			float[] t = armTarget(p, i == 0);
			if (t == null) {
				continue;
			}
			if (tr.hasRot[i] && tr.hasPos[i]) {
				float[] r = tr.rot[i];
				float[] o = tr.pos[i];
				boolean differs = Math.abs(part.pitch - r[0]) > 1e-4f || Math.abs(part.yaw - r[1]) > 1e-4f || Math.abs(part.roll - r[2]) > 1e-4f
						|| Math.abs(part.pivotX - o[0]) > 1e-4f || Math.abs(part.pivotY - o[1]) > 1e-4f || Math.abs(part.pivotZ - o[2]) > 1e-4f;
				if (differs) {
					part.setAngles(r[0], r[1], r[2]);
					part.pivotX = o[0];
					part.pivotY = o[1];
					part.pivotZ = o[2];
					tr.restored++;
					tr.lastPass = "restored";
					changed = true;
				}
			} else {
				float w = t[0];
				part.setAngles(part.pitch + (t[1] - part.pitch) * w, part.yaw + (t[2] - part.yaw) * w, part.roll + (t[3] - part.roll) * w);
				part.pivotX += t[4] * w;
				part.pivotY += t[5] * w;
				part.pivotZ += t[6] * w;
				tr.applied++;
				tr.lastPass = "applied";
				changed = true;
			}
		}
		if (changed) {
			m.rightSleeve.copyTransform(m.rightArm);
			m.leftSleeve.copyTransform(m.leftArm);
		}
	}

	/**
	 * What the pose wants of one arm in this frame: {@code {w, pitch, yaw, roll, dx, dy, dz}} (radians and px, {@code w} the weight of
	 * the override against the arm as the layers below produced it) or null when the arm is not posed.
	 */
	static float[] armTarget(AbstractClientPlayerEntity p, boolean right) {
		TpPose.Result r = lastResult(p);
		if (r == null) {
			return null;
		}
		float hold = layerWeight(p);
		if (hold <= 0f) {
			return null;
		}
		boolean isMain = right == (p.getMainArm() == Arm.RIGHT);
		TpPose.Arm a = isMain ? r.main() : r.off();
		float w = (float) (isMain ? r.wMain() : r.wOff()) * hold;
		if (w <= 0f) {
			return null;
		}
		return new float[] {w, (float) a.pitch(), (float) a.yaw(), (float) a.roll(), (float) a.dx(), (float) a.dy(), (float) a.dz()};
	}

	private static double[] d3(float[] v) {
		return new double[] {v[0], v[1], v[2]};
	}

	/** The pose of a player for the scabbard renderer (null: no pose, use the plain hip scabbard). */
	static TpPose.Result resultFor(LivingEntity e) {
		St st = update(e);
		return st == null || !st.holding ? null : st.res;
	}

	/** Overall weight (0..1, eased) of the arm override for the playerAnimator layer; also keeps the pose up to date. */
	static float layerWeight(AbstractClientPlayerEntity p) {
		St st = update(p);
		return st == null || st.res == null ? 0f : (float) st.hold;
	}

	/** The last solved pose (no update), for the layer's transform queries within the same frame. */
	static TpPose.Result lastResult(LivingEntity e) {
		St st = STATES.get(e.getId());
		return st == null ? null : st.res;
	}

	/**
	 * Held item correction of the sword hand (translation in blocks, quaternion x y z w, weight 0..1) or null when the entity has
	 * no pose or the item is not on the pose hand. The weight eases it in and out with the hold weight.
	 */
	static Grip grip(LivingEntity e, boolean leftHanded) {
		St st = update(e);
		if (st == null || !st.holding || st.res == null || leftHanded != (e.getMainArm() == Arm.LEFT)) {
			return null;
		}
		return new Grip(st.res.gT(), st.res.gQ(), (float) st.hold);
	}

	record Grip(double[] t, double[] q, float weight) {
	}
}
