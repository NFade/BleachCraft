package dev.minebleach.reiatsutest.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientOptions;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.core.combat.FpClamp;
import dev.minebleach.reiatsutest.core.combat.FpGeometry;
import dev.minebleach.reiatsutest.core.combat.FpParams;
import dev.minebleach.reiatsutest.core.combat.FpRig;
import dev.minebleach.reiatsutest.core.combat.FpPose;
import dev.minebleach.reiatsutest.core.combat.GuardMotion;
import dev.minebleach.reiatsutest.core.combat.LiveSwing;
import dev.minebleach.reiatsutest.core.combat.SwingMotion;
import dev.minebleach.reiatsutest.core.combat.TwoHandMotion;
import dev.minebleach.reiatsutest.core.obj.DrawRig;
import dev.minebleach.reiatsutest.registry.ModComponents;
import dev.minebleach.reiatsutest.registry.ReleaseState;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First person motion of the main hand with a zanpakuto (BC5b): our own strike, the block stance, the parry and blocked hit recoil.
 * The whole hand (arm and sword) is moved as one rigid body: a transform about the grip is pushed onto the matrix stack before
 * {@link FirstPersonHand} draws the arm and kept until the sword is drawn. The free left hand supports the blade in the stance.
 * All maths is in {@code core/combat} ({@link SwingMotion}, {@link GuardMotion}, {@link TwoHandMotion}); this class only keeps the clocks and
 * draws. BC5c: a drawn sword is held with both hands ({@link TwoHandMotion}): after the draw the stance blends in, the left hand comes up on
 * the hilt, strikes and the guard are two handed poses about a pivot between the fists.
 *
 * <p>Why our own strike instead of the Better Combat first person animation: Better Combat (playerAnimator) shows its animation in
 * first person by cancelling the whole {@code renderHandsWithItems} pass and drawing the third person body model with the
 * camera detached ({@code FirstPersonMode.THIRD_PERSON_MODEL}), so our arm, scabbard hand and sword lift cannot be shown at the same
 * time; the other mode that keeps the hand pass ({@code VANILLA}) is not one Better Combat ever picks. So {@code BcFirstPersonModeMixin}
 * keeps the hand pass ({@code NONE}), and this class plays a short gesture on the Better Combat start event
 * ({@code BetterCombatClientEvents.ATTACK_START}, public API): the hit key is reached exactly when Better Combat's upswing ends (the
 * moment of the hit), the profile follows the animation name of the attack (combo order of the katana). Switch for a comparison in
 * game: since round 7 Better Combat draws its own strike animation by default, {@code pose_override.json} {@code "fp_bc_mode": 0} brings back this gesture (dev only).
 *
 * <p>Hot reload (dev only, {@link HeldPose}): all numbers of {@code core/combat} come from {@code pose_override.json}, sections
 * {@code fp_swing}, {@code fp_guard}, {@code fp_pivot}, {@code fp_bc_mode}, {@code fp_enabled}; see design/results/fp_combat.md. The
 * numbers are right-hand numbers; the left hand mirrors x of shifts and y, z of rotations.
 */
public final class FpMotion {
	/** Where the vanilla fist of an empty hand is in the hand frame of a held item (see {@link FirstPersonHand}), blocks. */
	private static final float FIST_X = 0.134f;
	private static final float FIST_Y = -0.018f;
	private static final float FIST_Z = -0.316f;
	private static final float RAD = (float) (Math.PI / 180.0);

	/** {@link FpParams} backed by the dev override file; defaults when there is no file (production). */
	private static final FpParams PARAMS = new FpParams() {
		@Override
		public float num(String key, float def) {
			JsonElement e = find(key);
			try {
				return e != null && e.isJsonPrimitive() ? e.getAsFloat() : def;
			} catch (RuntimeException ex) {
				return def;
			}
		}

		@Override
		public float[] vec(String key, float[] def) {
			JsonElement e = find(key);
			try {
				if (e != null && e.isJsonArray()) {
					JsonArray a = e.getAsJsonArray();
					if (a.size() >= 3) {
						return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
					}
				}
			} catch (RuntimeException ex) {
				// fall through to the default
			}
			return def;
		}
	};

	// ---- clocks and state (render thread; the BC event and the effect event also run on the main thread)
	private static long lastNanos;
	private static float weight;
	private static boolean swinging;
	private static long swingStart;
	private static SwingMotion.Profile swingProfile = SwingMotion.Profile.H_R;
	private static float swingUp;
	private static SwingMotion.Timing swingTiming = SwingMotion.Timing.DEFAULT;
	private static FpPose swingOrigin = FpPose.ZERO;
	private static long parryAt;
	private static float parrySide;
	private static long hitAt;
	private static float hitSide;
	private static FpPose current = FpPose.ZERO;
	private static boolean frameValid;
	private static float frameLeftWeight;
	private static boolean sealedFrame;
	/** Two handed hold (BC5c): weight of the stance, whether this frame uses the two handed keys, pivot shift, left hand on the hilt. */
	private static float twoWeight;
	private static boolean twoKeys;
	private static float twoPivotS;
	private static boolean frameLeftTwo;
	/** Ability gesture of this frame (FpCast, FP_ABILITY_ANIMS stage 1): its sample, and the pose without it (the free left hand keeps to it). */
	private static dev.minebleach.reiatsutest.core.combat.CastGesture.Sample frameGest;
	private static FpPose frameBase = FpPose.ZERO;
	/**
	 * Round 6, the strike from the live bones of the third person animation ({@link LiveSwing}): the rest sample of the stance of the third person pose
	 * (kept fresh while no strike runs), the life of the current strike, the fallback to the profile, the fade back to the stance.
	 */
	private static LiveSwing.Frame liveRest;
	private static LiveSwing.Run liveRun = new LiveSwing.Run();
	private static boolean liveProfile;
	private static boolean liveFading;
	private static long liveFadeStart;
	private static FpPose liveFadeFrom = FpPose.ZERO;
	private static float liveMax;
	private static int liveLogged;
	/** Block with the sheathed sword (SEALED): smoothed weight (the scabbard hand and the scabbard follow the pose), this frame. */
	private static float sealedBlock;
	/** Reach limit of the ability gestures (round 5): smoothed factors of the main hand row and of the free hand row. */
	private static float kRight = 1f;
	private static float kLeft = 1f;

	private FpMotion() {
	}

	/** The tunable numbers (dev override file), for the other first person poses (FpBow). */
	public static FpParams params() {
		return PARAMS;
	}

	/** Registers the Better Combat strike start event (client init). */
	public static void init() {
		BetterCombatClientEvents.ATTACK_START.register(FpMotion::onAttackStart);
		FpCast.init();
	}

	// ------------------------------------------------------------------ params

	private static JsonElement find(String key) {
		JsonObject o = HeldPose.overrideObject();
		if (o == null) {
			return null;
		}
		String[] parts = key.split("\\.");
		JsonElement cur = o.get("fp_" + parts[0]);
		for (int i = 1; i < parts.length && cur != null; i++) {
			cur = cur.isJsonObject() ? cur.getAsJsonObject().get(parts[i]) : null;
		}
		return cur;
	}

	/**
	 * Round 7 (user decision): the first person strike of a zanpakuto is Better Combat's own animation (playerAnimator
	 * {@code THIRD_PERSON_MODEL}, public API only, nothing copied). Default on; {@code "fp_bc_mode": 0} in pose_override.json brings our own
	 * {@code FpMotion} gesture back. Read by {@link dev.minebleach.reiatsutest.client.mixin.BcFirstPersonModeMixin}.
	 */
	public static boolean useBcFirstPerson() {
		return PARAMS.num("bc_mode", 1f) > 0.5f;
	}

	private static boolean enabled(String section) {
		return PARAMS.num(section + ".enabled", 1f) > 0.5f && PARAMS.num("enabled", 1f) > 0.5f;
	}

	// ------------------------------------------------------------------ events

	/** Better Combat started an attack of the local player: play our strike (called on the main thread from {@code doAttack}). */
	private static void onAttackStart(ClientPlayerEntity player, AttackHand hand) {
		try {
			if (!(player.getMainHandStack().getItem() instanceof ZanpakutoItem) || hand.isOffHand() || !enabled("swing")
					|| useBcFirstPerson()) {
				return;
			}
			MinecraftClient mc = MinecraftClient.getInstance();
			float cooldown = cooldownTicks(player);
			int upTicks;
			try {
				upTicks = ((MinecraftClient_BetterCombat) (Object) mc).getUpswingTicks();
			} catch (RuntimeException | LinkageError e) {
				upTicks = Math.round((float) hand.attack().upswing() * cooldown);
			}
			String anim = hand.attack() == null ? null : hand.attack().animation();
			int combo = hand.combo() == null ? 0 : hand.combo().current();
			float up = Math.max(1, upTicks) * 0.05f;
			SwingMotion.Timing base = SwingMotion.timing(PARAMS);
			// the recovery shrinks with fast weapons: the whole strike should fit the cooldown of Better Combat
			float room = cooldown * 0.05f - up - base.follow();
			SwingMotion.Timing tm = new SwingMotion.Timing(base.windFrac(), base.follow(),
					Math.max(0.10f, Math.min(base.recover(), room > 0f ? room : base.recover())));
			swingOrigin = current;
			swingProfile = SwingMotion.profileFor(anim, combo);
			swingUp = up;
			swingTiming = tm;
			swingStart = System.nanoTime();
			swinging = true;
		} catch (RuntimeException e) {
			ReiatsuTest.LOGGER.warn("[fpmotion] strike start failed: {}", e.toString());
		}
	}

	private static float cooldownTicks(ClientPlayerEntity p) {
		try {
			float c = net.bettercombat.logic.PlayerAttackHelper.getAttackCooldownTicksCapped(p);
			return c > 0f ? c : 12f;
		} catch (RuntimeException | LinkageError e) {
			return 12f;
		}
	}

	/** The local player parried a hit (effect event 41): the sword jerks forward. {@code side} > 0: the attacker stands on the right. */
	public static void onParry(float side) {
		parryAt = System.nanoTime();
		parrySide = side;
	}

	/** The local player blocked a hit with reduced damage: the sword is pushed back. */
	public static void onBlockedHit(float side) {
		hitAt = System.nanoTime();
		hitSide = side;
	}

	// ------------------------------------------------------------------ per frame

	private static void reset() {
		weight = 0f;
		swinging = false;
		parryAt = 0L;
		hitAt = 0L;
		current = FpPose.ZERO;
		frameValid = false;
		frameLeftWeight = 0f;
		twoWeight = 0f;
		twoKeys = false;
		twoPivotS = 0f;
		frameLeftTwo = false;
		frameGest = null;
		frameBase = FpPose.ZERO;
		sealedBlock = 0f;
		kRight = 1f;
		kLeft = 1f;
		liveRest = null;
		liveFading = false;
		liveProfile = false;
	}

	/** True when the stack in the main hand draws a sword in the hand (not the empty hand of the Byakuya bankai). */
	private static ObjItemBakedModel handModel(ClientPlayerEntity player, ItemStack main) {
		BakedModel model = MinecraftClient.getInstance().getItemRenderer().getModel(main, player.getWorld(), player, 0);
		// Uryu's bow is no sword: no two handed sword stance for it
		return model instanceof ObjItemBakedModel obj && obj.drawsInHand(main) && !(main.getItem() instanceof dev.minebleach.reiatsutest.registry.UryuCrossItem) ? obj : null;
	}

	/** Once per frame at the head of the first person hand pass: advances the clocks and computes the pose of this frame. */
	public static void frame(ClientPlayerEntity player, float tickDelta) {
		long now = System.nanoTime();
		float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		ItemStack main = player.getMainHandStack();
		if (!(main.getItem() instanceof ZanpakutoItem) || !enabled("guard") && !enabled("swing") && !enabled("two")) {
			reset();
			return;
		}
		boolean blocking = enabled("guard") && ZanpakutoItem.isBlocking(player) && player.getActiveHand() == Hand.MAIN_HAND;
		weight = GuardMotion.stepWeight(weight, blocking, dt, Math.max(0.01f, PARAMS.num("guard.in", 0.10f)),
				Math.max(0.01f, PARAMS.num("guard.out", 0.25f)));
		// BC5c: a drawn sword (not SEALED, a sword in the hand) is held with both hands; the stance comes in after the draw has finished
		boolean sealed = main.getOrDefault(ModComponents.RELEASE_STATE, ReleaseState.SEALED) == ReleaseState.SEALED;
		twoKeys = false;
		boolean twoOn = false;
		if (enabled("two") && !sealed && handModel(player, main) != null) {
			twoKeys = true;
			twoOn = DrawRig.sayaOut(DrawTracker.effectiveProgress(player, main)) >= 1f;
		}
		twoWeight = GuardMotion.stepWeight(twoWeight, twoOn, dt, Math.max(0.01f, PARAMS.num("two.in", 0.30f)),
				Math.max(0.01f, PARAMS.num("two.out", 0.12f)));
		twoPivotS = twoKeys || twoWeight > 0f ? TwoHandMotion.pivotS(PARAMS, twoWeight) : 0f;
		FpPose rest = twoKeys ? TwoHandMotion.stance(PARAMS, twoWeight) : FpPose.ZERO;
		FpPose base = rest;
		// round 6: the strike of the two handed hold follows the live third person animation (fp_swing.live.enabled 0: the profiles of round 5 only)
		boolean leftHander = player.getMainArm() == Arm.LEFT;
		boolean liveOn = twoKeys && enabled("swing") && PARAMS.num("swing.live.enabled", 1f) > 0.5f && !useBcFirstPerson();
		LiveSwing.Gains gains = liveOn ? LiveSwing.Gains.of(PARAMS) : LiveSwing.Gains.DEFAULT;
		if (!liveOn) {
			liveRest = null;
			liveFading = false;
		} else if (!swinging && !liveFading) {
			// no strike: the third person stance is the rest the strike is measured against
			LiveSwing.Bones rb = LiveSwingSampler.sample(player, leftHander, tickDelta);
			liveRest = rb == null ? null : LiveSwing.frame(rb, gains.bend(), gains.torso());
		}
		if (swinging) {
			float t = (now - swingStart) / 1.0e9f;
			boolean useProfile = !liveOn || liveRest == null || liveProfile;
			if (!useProfile) {
				LiveSwing.Bones cb = LiveSwingSampler.sample(player, leftHander, tickDelta);
				boolean driven = LiveSwingSampler.itemDriven(player, leftHander, tickDelta);
				if (cb == null) {
					liveProfile = true;
					useProfile = true;
				} else {
					// the look pitch adjustment of the Better Combat layer is taken out (the hands follow the camera already), only while the layer drives the sword
					if (driven) {
						cb = LiveSwing.compensate(cb, (float) Math.toRadians(player.getPitch()), gains.pitchComp());
					}
					LiveSwing.Delta d = LiveSwing.delta(liveRest, LiveSwing.frame(cb, gains.bend(), gains.torso()), gains);
					float mag = d.magnitude();
					liveMax = Math.max(liveMax, mag);
					boolean active = driven || mag > PARAMS.num("swing.live.eps", 1.5f);
					int st = liveRun.step(active, t, dt, PARAMS.num("swing.live.gate", 0.12f), PARAMS.num("swing.live.tail", 0.10f),
							PARAMS.num("swing.live.max_sec", 2.0f));
					if (st == 2) {
						liveProfile = true;
						useProfile = true;
						ReiatsuTest.LOGGER.info("[fplive] the strike animation was not seen (maxMag {}), the profile plays", liveMax);
					} else {
						FpPose abs = LiveSwing.pose(rest, d, leftHander);
						float in = Math.max(0.005f, PARAMS.num("swing.live.in", 0.04f));
						base = LiveSwing.blend(rest, abs, dev.minebleach.reiatsutest.core.combat.FpEase.smooth(t / in));
						if (PARAMS.num("swing.live.debug", 0f) > 0.5f && liveLogged < 14 && t >= liveLogged * 0.04f) {
							liveLogged++;
							float[] e = abs.rot();
							ReiatsuTest.LOGGER.info("[fplive] t={} mag={} move=({}, {}, {}) euler=({}, {}, {}) driven={}", String.format("%.2f", t),
									String.format("%.1f", mag), String.format("%.2f", d.move()[0]), String.format("%.2f", d.move()[1]),
									String.format("%.2f", d.move()[2]), String.format("%.0f", e[0]), String.format("%.0f", e[1]), String.format("%.0f", e[2]), active);
						}
						if (st == 1) {
							swinging = false;
							liveFading = true;
							liveFadeStart = now;
							liveFadeFrom = base;
						}
					}
				}
			}
			if (useProfile) {
				if (t >= SwingMotion.duration(swingUp, swingTiming)) {
					swinging = false;
				} else if (twoKeys) {
					base = SwingMotion.sample(TwoHandMotion.keys(PARAMS, swingProfile), rest, swingOrigin, t, swingUp, swingTiming);
				} else {
					base = SwingMotion.sample(PARAMS, swingProfile, swingOrigin, t, swingUp, swingTiming);
				}
			}
		} else if (liveFading) {
			float u = (now - liveFadeStart) / 1.0e9f / Math.max(0.02f, PARAMS.num("swing.live.out", 0.10f));
			if (u >= 1f) {
				liveFading = false;
			} else {
				base = LiveSwing.blend(liveFadeFrom, rest, dev.minebleach.reiatsutest.core.combat.FpEase.smooth(u));
			}
		}
		float amp = FxConfig.reduceMotion || FxConfig.photosensitiveSafe ? 0.5f : 1f;
		FpPose parry = FpPose.ZERO;
		if (parryAt != 0L) {
			float s = (now - parryAt) / 1.0e9f;
			if (s > GuardMotion.kickLength(PARAMS, GuardMotion.Kick.PARRY)) {
				parryAt = 0L;
			} else {
				parry = GuardMotion.kick(PARAMS, GuardMotion.Kick.PARRY, s, parrySide, amp);
			}
		}
		FpPose hit = FpPose.ZERO;
		if (hitAt != 0L) {
			float s = (now - hitAt) / 1.0e9f;
			if (s > GuardMotion.kickLength(PARAMS, GuardMotion.Kick.HIT)) {
				hitAt = 0L;
			} else {
				hit = GuardMotion.kick(PARAMS, GuardMotion.Kick.HIT, s, hitSide, amp);
			}
		}
		float time = (float) ((now % 100_000_000_000L) / 1.0e9);
		// round 5: with the sheathed sword the block carries the scabbard and the sword in it as one body (the scabbard and the hand that holds
		// it follow the pose, see pushSaya); strikes, recoils and gestures stay a hint (guard.sealed_scale), they must not pull the sword out of the scabbard
		sealedFrame = sealed;
		float ss = PARAMS.num("guard.sealed_scale", 0.4f);
		FpPose guardPose = twoKeys ? TwoHandMotion.guard(PARAMS) : GuardMotion.stance(PARAMS, 1f);
		FpPose baseUse = base;
		if (sealed) {
			guardPose = GuardMotion.sealedGuard(PARAMS);
			baseUse = base.scaled(ss);
			parry = parry.scaled(ss);
			hit = hit.scaled(ss);
		}
		FpPose pose = GuardMotion.pose(PARAMS, baseUse, guardPose, weight, time, parry, hit);
		sealedBlock = sealed ? dev.minebleach.reiatsutest.core.combat.FpEase.smooth(weight) : 0f;
		if (sealed) {
			// r8: the sheathed hold sits lower (the scabbard, the sword in it and both hands follow the pose); it gives way to the block pose
			float[] low = GuardMotion.sealedRest(PARAMS);
			float k = 1f - sealedBlock;
			pose = pose.plus(FpPose.of(low[0] * k, low[1] * k, low[2] * k, 0f, 0f, 0f));
		}
		// ability gesture (FpCast): a delta on the stance, rigid with the sword about the pivot between the fists; the breath gives way to it
		dev.minebleach.reiatsutest.core.combat.CastGesture.Sample gs = FpCast.fpSample();
		if (twoKeys) {
			pose = pose.plus(TwoHandMotion.idle(PARAMS, time, twoWeight * (1f - weight) * (gs == null ? 1f : 1f - gs.env())));
		}
		frameBase = pose;
		gs = limitGesture(gs, pose, dt, twoKeys ? handModel(player, main) : null);
		frameGest = gs;
		if (gs != null) {
			float[] gr = gs.r();
			FpPose gp = FpPose.of(gr[0], gr[1], gr[2], gr[3], gr[4], gr[5]);
			pose = pose.plus(sealed ? gp.scaled(ss) : gp);
		}
		current = pose;
		frameValid = true;
		frameLeftTwo = twoKeys && PARAMS.num("two.left.enabled", 1f) > 0.5f;
		if (frameLeftTwo) {
			// the free hand rides on the hilt: it comes up after the draw together with the stance
			frameLeftWeight = TwoHandMotion.leftWeight(twoWeight);
		} else {
			frameLeftWeight = enabled("guard") && PARAMS.num("guard.left.enabled", 1f) > 0.5f ? GuardMotion.leftWeight(weight) : 0f;
		}
	}

	/**
	 * Reach limits of the ability gesture of this frame ({@code fp_cast.clamp.*}, round 5; right hander layout, the same numbers as the poses):
	 * the forward reach of the hands is limited softly ({@code fwd}, blocks), and the hands and the blade are kept {@code near} blocks in front of
	 * the camera and the fists inside the picture by one factor of the main hand row and one of the free hand row ({@link FpClamp}, the sword is
	 * only limited where it would be cut visibly). The factors fall at once and come back over {@code release} seconds, so a limited gesture never jumps. Only for the two handed
	 * hold; the sheathed sword and the empty hands only get the soft forward limit.
	 */
	private static dev.minebleach.reiatsutest.core.combat.CastGesture.Sample limitGesture(
			dev.minebleach.reiatsutest.core.combat.CastGesture.Sample gs, FpPose base, float dt, ObjItemBakedModel obj) {
		if (gs == null) {
			kRight = 1f;
			kLeft = 1f;
			return null;
		}
		if (FpCast.clampNum("enabled", 1f) < 0.5f) {
			return gs;
		}
		float fwd = FpCast.clampNum("fwd", 0.30f);
		// r8: both hand rows are made smaller (hands.scale, hands.drop) and softly limited forward, then the reach factors below keep every arm out of the
		// central rectangle of the picture (FpCentre) as well as in front of the camera
		dev.minebleach.reiatsutest.core.combat.FpCentre.Shaped shaped = dev.minebleach.reiatsutest.core.combat.FpCentre.shape(gs.r(), gs.l(), gs.env(),
				FpCast.handsNum("scale", FpCast.HANDS_SCALE), FpCast.handsNum("drop", FpCast.HANDS_DROP), fwd);
		float[] r = shaped.r();
		float[] l = shaped.l();
		float tr = 1f;
		float tl = 1f;
		if (obj != null) {
			try {
				Transformation t = obj.getTransformation().getTransformation(ModelTransformationMode.FIRST_PERSON_RIGHT_HAND);
				FpRig rig = new FpRig(new float[] {t.rotation.x(), t.rotation.y(), t.rotation.z()},
						new float[] {t.translation.x(), t.translation.y(), t.translation.z()}, t.scale.x(), HeldPose.of(obj.manifest()).lift());
				FpGeometry.Hold hold = FpGeometry.Hold.twoHanded(PARAMS, rig);
				MinecraftClient mc = MinecraftClient.getInstance();
				float aspect = mc.getWindow().getFramebufferHeight() > 0
						? (float) mc.getWindow().getFramebufferWidth() / mc.getWindow().getFramebufferHeight() : 16f / 9f;
				FpClamp.Limits lim = new FpClamp.Limits(FpCast.clampNum("near", FpClamp.NEAR), aspect, FpCast.clampNum("screen_x", 1.0f),
						FpCast.clampNum("screen_y_min", -1.2f), FpCast.clampNum("screen_y_max", 0.95f),
						FpCast.clampNum("centre_x", dev.minebleach.reiatsutest.core.combat.FpCentre.HALF_X),
						FpCast.clampNum("centre_y", dev.minebleach.reiatsutest.core.combat.FpCentre.HALF_Y));
				FpClamp.Factors f = FpClamp.factors(hold, rig, base, r, l, lim);
				tr = f.right();
				tl = f.left();
			} catch (RuntimeException e) {
				ReiatsuTest.LOGGER.warn("[fpmotion] reach limit failed: {}", e.toString());
			}
		}
		float rel = FpCast.clampNum("release", 0.15f);
		kRight = FpClamp.release(kRight, tr, dt, rel);
		kLeft = FpClamp.release(kLeft, tl, dt, rel);
		for (int i = 0; i < 6; i++) {
			r[i] *= kRight;
			l[i] *= kLeft;
		}
		return new dev.minebleach.reiatsutest.core.combat.CastGesture.Sample(r, l, gs.c(), gs.tr(), gs.tl(), gs.env(), gs.hand());
	}

	/**
	 * Shift of the right arm against the sword for this frame (hand frame blocks, a left hander mirrored; weight of the two handed hold applied),
	 * or null: the fist sits flush under the tsuba in the two handed hold. Called by {@link FirstPersonHand} inside the pose push.
	 */
	public static float[] armShift(boolean left) {
		if (!frameValid || !twoKeys || twoWeight <= 0.001f) {
			return null;
		}
		float w = dev.minebleach.reiatsutest.core.combat.FpEase.smooth(twoWeight);
		float[] a = TwoHandMotion.armShift(PARAMS, left);
		return new float[] {a[0] * w, a[1] * w, a[2] * w};
	}

	/** Turn of the right arm about its fist for this frame (degrees x y z, mirrored for a left hander, weight applied), or null. */
	public static float[] armRot(boolean left) {
		if (!frameValid || !twoKeys || twoWeight <= 0.001f) {
			return null;
		}
		float w = dev.minebleach.reiatsutest.core.combat.FpEase.smooth(twoWeight);
		float[] a = TwoHandMotion.armRot(PARAMS, left);
		return new float[] {a[0] * w, a[1] * w, a[2] * w};
	}

	/**
	 * The scabbard of the sheathed sword follows the same rigid pose as the sword in it (round 5: the block with the sheathed sword; before, the
	 * sword moved alone and left the scabbard). Pushes the pose onto the stack in the resting hand frame, before the lift and the display
	 * transform; returns true when a matrix was pushed (the caller pops it).
	 */
	public static boolean pushSaya(ClientPlayerEntity player, ItemStack stack, ObjItemBakedModel obj, Transformation tr, boolean leftHanded,
			MatrixStack m) {
		if (!frameValid || current.isNearZero() || stack != player.getMainHandStack()) {
			return false;
		}
		Matrix4f toHand = HandMath.modelToHand(tr, leftHanded);
		float[] pv = pivot(obj, stack, player, tr, leftHanded, toHand);
		FpPose q = leftHanded ? current.mirrored() : current;
		float[] qq = q.quaternion();
		m.push();
		m.translate(q.pos()[0], q.pos()[1], q.pos()[2]);
		m.translate(pv[0], pv[1], pv[2]);
		m.multiply(new Quaternionf(qq[0], qq[1], qq[2], qq[3]));
		m.translate(-pv[0], -pv[1], -pv[2]);
		return true;
	}

	/** Weight (0..1) of the block with the sheathed sword: the left hand slides to its place on the scabbard, the forearm turns. */
	public static float sealedBlockWeight() {
		return frameValid && sealedFrame ? sealedBlock : 0f;
	}

	/** Place of the left hand on the scabbard (metres from the grip): {@code nearest} (the hand's own place) blended into the block place. */
	public static float sealedLeftS(float nearest) {
		float w = sealedBlockWeight();
		if (w <= 0f) {
			return nearest;
		}
		float s = GuardMotion.sealedLeftS(PARAMS);
		return nearest + (s - nearest) * w;
	}

	/**
	 * Turns the left forearm about its fist in the camera frame of the left hand (the matrix is at the left hand frame, already moved to the
	 * fist) while the sealed block is up: it comes in from the lower left. Weighted by {@link #sealedBlockWeight}.
	 */
	public static void turnSealedLeftArm(MatrixStack m, boolean mainLeft) {
		float w = sealedBlockWeight();
		if (w <= 0.001f) {
			return;
		}
		float[] a = GuardMotion.sealedLeftArmRot(PARAMS, mainLeft);
		int i = mainLeft ? -1 : 1;
		m.translate(-i * FIST_X, FIST_Y, FIST_Z);
		m.multiply(new Quaternionf().rotationXYZ(a[0] * w * RAD, a[1] * w * RAD, a[2] * w * RAD));
		m.translate(i * FIST_X, -FIST_Y, -FIST_Z);
	}

	/** Grip of the held sword in the hand frame (display transform plus the held lift): the pivot of the motion. */
	private static float[] pivot(ObjItemBakedModel obj, ItemStack stack, AbstractClientPlayerEntity player, Transformation tr,
			boolean left, Matrix4f toHand) {
		float[] ov = PARAMS.vec("pivot", null);
		Vector3f g;
		if (ov != null) {
			g = new Vector3f((left ? -1f : 1f) * ov[0], ov[1], ov[2]);
		} else {
			g = toHand.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
			Vector3f lift = FirstPersonHand.lift(obj, stack, player, left);
			if (lift != null) {
				g.add(lift);
			}
		}
		if (twoPivotS != 0f) {
			// two handed hold: the sword turns about a point of the hilt between the fists, not about the grip of the right hand
			Vector3f along = toHand.transformPosition(new Vector3f(0.5f, 0.5f + twoPivotS, 0.5f))
					.sub(toHand.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f)));
			g.add(along);
		}
		return new float[] {g.x, g.y, g.z};
	}

	/**
	 * Pushes the hand motion onto the matrix stack (head of the held item render, before the arm is drawn). Returns true when a matrix
	 * was pushed; the caller pops it after the sword is drawn.
	 */
	public static boolean push(ClientPlayerEntity player, ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack m) {
		if (!frameValid || current.isNearZero() || FpBow.active()) {
			return false;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		BakedModel model = mc.getItemRenderer().getModel(stack, player.getWorld(), player, 0);
		if (!(model instanceof ObjItemBakedModel obj) || !obj.drawsInHand(stack)) {
			return false;
		}
		Transformation tr = model.getTransformation().getTransformation(mode);
		Matrix4f toHand = HandMath.modelToHand(tr, leftHanded);
		float[] pv = pivot(obj, stack, player, tr, leftHanded, toHand);
		FpPose q = leftHanded ? current.mirrored() : current;
		float[] qq = q.quaternion();
		m.push();
		m.translate(q.pos()[0], q.pos()[1], q.pos()[2]);
		m.translate(pv[0], pv[1], pv[2]);
		m.multiply(new Quaternionf(qq[0], qq[1], qq[2], qq[3]));
		m.translate(-pv[0], -pv[1], -pv[2]);
		return true;
	}

	/**
	 * The free hand (drawn with the hands, before the buffer is flushed, same frame as the scabbard hand of {@link ScabbardRenderer}).
	 * Two handed hold (BC5c, default): it rides on the hilt below the right hand (rigid with the sword through strikes and guard) and comes
	 * up from below the screen after the draw. One handed fallback ({@code fp_two.enabled} 0): it supports the blade while the guard is up.
	 * Only when the scabbard is gone (after the draw): before that the other hand holds it.
	 */
	public static void renderLeftGuard(MatrixStack m, VertexConsumerProvider vcp, ClientPlayerEntity player, int light, ItemStack shown,
			float equip) {
		FpCast.renderFreeHands(m, vcp, player, light, shown, equip); // the empty hands of the Byakuya bankai (nothing for a sword in the hand)
		if (!frameValid || frameLeftWeight <= 0.01f || !ClientOptions.showFirstPersonHand || player.isInvisible()
				|| shown != player.getMainHandStack() || !(shown.getItem() instanceof ZanpakutoItem)) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		BakedModel model = mc.getItemRenderer().getModel(shown, player.getWorld(), player, 0);
		if (!(model instanceof ObjItemBakedModel obj) || !obj.drawsInHand(shown)) {
			return;
		}
		if (DrawRig.sayaOut(DrawTracker.effectiveProgress(player, shown)) < 1f) {
			return;
		}
		boolean left = player.getMainArm() == Arm.LEFT;
		ModelTransformationMode mode = left ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
		Transformation tr = model.getTransformation().getTransformation(mode);
		Matrix4f toHand = HandMath.modelToHand(tr, left);
		float[] pv = pivot(obj, shown, player, tr, left, toHand);
		float s = frameLeftTwo ? PARAMS.num("two.left.s", TwoHandMotion.LEFT_S) : PARAMS.num("guard.left.s", 0.30f);
		Vector3f bp = toHand.transformPosition(new Vector3f(0.5f, 0.5f + s, 0.5f));
		Vector3f lift = FirstPersonHand.lift(obj, shown, player, left);
		if (lift != null) {
			bp.add(lift);
		}
		FpPose q = left ? current.mirrored() : current;
		float[] bladePoint = new float[] {bp.x, bp.y, bp.z};
		float[] t = frameLeftTwo ? TwoHandMotion.leftFist(PARAMS, q, pv, bladePoint, left) : GuardMotion.leftFist(PARAMS, q, pv, bladePoint);
		float eqY = -0.6f * (1f - Math.max(0f, Math.min(1f, equip)));
		int i = left ? -1 : 1;
		// ability gesture: the free hand leaves the hilt (free = 1: it keeps the stance place instead of riding with the sword) and moves by its row
		dev.minebleach.reiatsutest.core.combat.CastGesture.Sample gs = frameLeftTwo ? frameGest : null;
		float[] gl = gs == null ? null : gs.l();
		if (gl != null) {
			float free = Math.max(0f, Math.min(1f, gl[6]));
			if (free > 0.001f) {
				float[] tb = TwoHandMotion.leftFist(PARAMS, left ? frameBase.mirrored() : frameBase, pv, bladePoint, left);
				for (int k = 0; k < 3; k++) {
					t[k] += (tb[k] - t[k]) * free;
				}
			}
			t[0] += i * gl[0];
			t[1] += gl[1];
			t[2] += gl[2];
		}
		// absolute (camera frame of the hand code) positions: the main hand frame, the empty fist of the other hand, the target
		Vector3f target = new Vector3f(i * 0.56f + t[0], -0.52f + eqY + t[1], -0.72f + t[2]);
		Vector3f rest = new Vector3f(-i * (0.56f + FIST_X), -0.52f + eqY + FIST_Y, -0.72f + FIST_Z);
		float e = dev.minebleach.reiatsutest.core.combat.FpEase.smooth(frameLeftWeight);
		// it comes up from below the screen edge, outside of the sword (two handed: from below the hilt, where the scabbard hand left)
		Vector3f from = frameLeftTwo
				? new Vector3f(target).add(-i * PARAMS.num("two.left.in_dx", 0.30f), PARAMS.num("two.left.in_dy", -0.90f), PARAMS.num("two.left.in_dz", 0.10f))
				: new Vector3f(rest).add(-i * PARAMS.num("guard.left.in_dx", 0.25f), PARAMS.num("guard.left.in_dy", -0.8f), 0f);
		Vector3f pos = new Vector3f(from).lerp(target, e);
		Vector3f delta = pos.sub(rest);
		m.push();
		m.translate(-i * 0.56f, -0.52f + eqY, -0.72f);
		m.translate(delta.x, delta.y, delta.z);
		if (frameLeftTwo) {
			// round3: the vanilla off hand arm points from the fist to the lower right of the screen; turned about the fist it comes in from the
			// lower left, under the right arm, like the left hand of the third person pose. Camera frame, a left hander mirrors y and z.
			float[] ar = TwoHandMotion.leftArmRot(PARAMS, left);
			if (gl != null) {
				ar = new float[] {ar[0] + gl[3], ar[1] + i * gl[4], ar[2] + i * gl[5]};
			}
			// round 5: the arm is rigid with the sword like the right one: it turns with the pose (about the fist), the standing turn is given in the sword frame
			float[] pq = q.quaternion();
			if (gl != null && gl[6] > 0.001f) {
				// a hand that stays at its stance place (free) does not follow the sword's turn either
				pq = FpGeometry.slerp(pq, (left ? frameBase.mirrored() : frameBase).quaternion(), Math.max(0f, Math.min(1f, gl[6])));
			}
			Quaternionf turn = new Quaternionf(pq[0], pq[1], pq[2], pq[3]).mul(new Quaternionf().rotationXYZ(ar[0] * RAD, ar[1] * RAD, ar[2] * RAD));
			m.translate(-i * FIST_X, FIST_Y, FIST_Z);
			m.multiply(turn);
			m.translate(i * FIST_X, -FIST_Y, -FIST_Z);
		}
		FirstPersonHand.renderVanillaArmAt(mc, player, !left, m, vcp, light);
		m.pop();
	}
}
