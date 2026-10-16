package dev.minebleach.reiatsutest.client.model;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientOptions;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxTune;
import dev.minebleach.reiatsutest.core.combat.CastGesture;
import dev.minebleach.reiatsutest.core.combat.CastGestures;
import dev.minebleach.reiatsutest.core.combat.FpEase;
import dev.minebleach.reiatsutest.core.combat.TpMat4;
import dev.minebleach.reiatsutest.core.combat.UryuAnims;
import dev.minebleach.reiatsutest.core.combat.UryuBowFlow;
import dev.minebleach.reiatsutest.core.combat.UryuGestures;
import dev.minebleach.reiatsutest.core.combat.UryuPoseMath;
import dev.minebleach.reiatsutest.core.combat.UryuPoseTrack;
import dev.minebleach.reiatsutest.core.combat.UryuStringHand;
import dev.minebleach.reiatsutest.core.combat.UryuTpPose;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.registry.UryuCrossItem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Quaternionf;

/**
 * The poses of Uryu's bow (U-FX 11): the pull and release state machine ({@link UryuBowFlow}, pure) of every Uryu player in sight, driven by the draw
 * progress of the bow in the main hand, and what it drives:
 * <ul>
 * <li>third person: the animations of {@code player_animations/uryu_*.json} ({@link UryuPoseTrack}, authored by {@code tools/gen_uryu_poses.py}) as the values
 * {@link UryuTpLayer} hands to the model parts ({@link UryuTpPose}), and the rotation that keeps the bow upright in the moving hand ({@link UryuBowPose});</li>
 * <li>first person: the {@link CastGesture} rows {@link UryuGestures#DRAW} (sampled by the draw time) and {@link UryuGestures#RELEASE}, mixed into the sample
 * of {@link FpCast} ({@link #mixFirstPerson}), and the string hand ({@link #renderStringHand}), the left arm that the stance code of the swords does not draw for a bow.</li>
 * </ul>
 * The ability gestures start from the effect events ({@link #onEffectEvent}, called from {@link FpCast#onEffectEvent}): the animation of the third person arms, while the
 * first person rows of the same ability run in {@link FpCast} as for every other character. Licht Regen and Sternenstaub start at the cast start event (effect 78, the key
 * press) so the raise and the draw before their release event are seen; without that event (an old server) they start a few ticks before the release.
 *
 * <p>Dev switches in {@code pose_override.json}, section {@code uryu_pose}: {@code enabled} (0: all off, the vanilla bow pose and the old correction come back), {@code tp},
 * {@code fp} (0/1), {@code breath} (amplitude of the breathing, default 1). The bow hand is the main hand; the
 * config {@code uryu.bowHand} is read ({@link ClientOptions#uryuBowHand}): a left hander ({@code Options > Main Hand: Left}) gets the mirror image of every pose. UNVERIFIED in the game.
 */
public final class UryuBowMotion {
	private static final float RAD = (float) (Math.PI / 180.0);

	private static final class St {
		final UryuBowFlow flow = new UryuBowFlow(UryuBowMotion::endTick);
		long nanos;
		UryuTpPose.Frame frame;
		float lookPitchDeg;
		float presence;
		long presenceNanos;
		/** R14: right handers (bow in the LEFT hand, drawn by {@link TpBowLayer}): the mirrored pose that is used for the ability gestures only, and its fade 0..1. */
		UryuTpPose.Frame abilityFrame;
		double abilityEnv;
	}

	private static final Map<Integer, St> STATES = new HashMap<>();
	private static final Map<String, UryuPoseTrack> TRACKS = new HashMap<>();
	private static boolean warnedHand;
	/** Relaxed free hand at rest ({@code L} row layout px, py, pz, arx, ary, arz): low at the lower left, the forearm coming up from below the picture. */
	private static final float[] IDLE_L = {-0.78f, 0.07f, 0.05f, 5f, 0f, 0f};

	private UryuBowMotion() {
	}

	/** Registers the third person layer (called from {@link FpCast#init}). */
	public static void init() {
		UryuTpLayer.register();
		UryuCrossHold.register(); // R14: the arm of the sealed cross is lifted so the pendant can be seen
	}

	/** World change. */
	public static void clear() {
		STATES.clear();
		UryuCrossHold.clear();
	}

	// ------------------------------------------------------------------ config and data

	private static JsonObject cfg() {
		JsonObject o = HeldPose.overrideObject();
		if (o == null || !o.has("uryu_pose") || !o.get("uryu_pose").isJsonObject()) {
			return null;
		}
		return o.getAsJsonObject("uryu_pose");
	}

	private static float num(String key, float def) {
		JsonObject c = cfg();
		try {
			return c != null && c.has(key) && c.get(key).isJsonPrimitive() ? c.get(key).getAsFloat() : def;
		} catch (RuntimeException e) {
			return def;
		}
	}

	static boolean enabled() {
		return num("enabled", 1f) > 0.5f;
	}

	static boolean tpOn() {
		return enabled() && num("tp", 1f) > 0.5f;
	}

	static boolean fpOn() {
		return enabled() && num("fp", 1f) > 0.5f;
	}

	/** The track of an animation, loaded once from the resources (null when it is missing: that pose is off). */
	static UryuPoseTrack track(String anim) {
		if (TRACKS.containsKey(anim)) {
			return TRACKS.get(anim);
		}
		UryuPoseTrack t = null;
		String path = UryuAnims.resource(anim);
		try (InputStream in = UryuBowMotion.class.getResourceAsStream(path)) {
			if (in == null) {
				ReiatsuTest.LOGGER.warn("[uryupose] resource {} not found", path);
			} else {
				t = UryuPoseTrack.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
		} catch (IOException | RuntimeException e) {
			ReiatsuTest.LOGGER.warn("[uryupose] cannot read {}: {}", path, e.toString());
		}
		TRACKS.put(anim, t);
		return t;
	}

	private static int endTick(String anim) {
		UryuPoseTrack t = track(anim);
		return t == null ? 0 : t.endTick();
	}

	// ------------------------------------------------------------------ per entity state

	private static St state(int id) {
		if (STATES.size() > 64 && !STATES.containsKey(id)) {
			STATES.values().removeIf(s -> !s.flow.active());
		}
		return STATES.computeIfAbsent(id, k -> new St());
	}

	private static double nowSeconds() {
		return System.nanoTime() / 1.0e9;
	}

	/** True when the entity holds the drawn bow in the main hand in a body state the pose can handle. */
	private static boolean holdingBow(AbstractClientPlayerEntity p) {
		ItemStack main = p.getMainHandStack();
		return UryuCrossItem.isBow(main) && p.isAlive() && !p.isFallFlying() && !p.isSwimming() && !p.isSleeping() && !p.isClimbing();
	}

	/** Brings the state of the player up to date for this frame (render thread); cheap when called repeatedly within a frame. */
	private static St update(AbstractClientPlayerEntity p, float td) {
		St st = state(p.getId());
		long nanos = System.nanoTime();
		if (st.frame != null && nanos - st.nanos < 400_000L) {
			return st;
		}
		double dtSeconds = st.nanos == 0L ? 0.0 : Math.min(0.1, (nanos - st.nanos) / 1.0e9);
		st.nanos = nanos;
		double now = nanos / 1.0e9;
		boolean holding = enabled() && holdingBow(p);
		boolean using = holding && p.isUsingItem() && p.getActiveHand() == Hand.MAIN_HAND;
		st.flow.update(now, holding, using, using ? p.getItemUseTime() + td : 0.0);
		st.lookPitchDeg = p.getPitch(td);
		UryuBowFlow f = st.flow;
		if (!f.active()) {
			st.frame = null;
			st.abilityFrame = null;
			st.abilityEnv = 0.0;
			return st;
		}
		boolean mirror = p.getMainArm() == Arm.LEFT;
		if (!mirror && "left".equals(ClientOptions.uryuBowHand) && !warnedHand) {
			warnedHand = true;
			ReiatsuTest.LOGGER.info("[uryupose] uryu.bowHand is left: the bow is the MAIN hand item, set Options > Skin Customization > Main Hand: Left to hold it in the left hand (the poses mirror with it)");
		}
		UryuAnims.Meta cm = UryuAnims.meta(f.anim());
		UryuPoseTrack ct = track(f.anim());
		if (cm == null || ct == null) {
			st.frame = null;
			return st;
		}
		UryuTpPose.Layer cur = new UryuTpPose.Layer(ct, cm, f.tick(now));
		UryuTpPose.Layer prev = null;
		if (f.prevAnim() != null) {
			UryuAnims.Meta pm = UryuAnims.meta(f.prevAnim());
			UryuPoseTrack pt = track(f.prevAnim());
			if (pm != null && pt != null) {
				prev = new UryuTpPose.Layer(pt, pm, f.prevTick());
			}
		}
		boolean rm = FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
		st.frame = new UryuTpPose.Frame(cur, prev, f.xfade(now), f.weight(), mirror, Math.toRadians(st.lookPitchDeg), now,
				UryuPoseMath.breathScale(rm, num("breath", 1f)), !rm);
		// R14: a right hander holds the bow in the LEFT hand (the mirror image of the pose) and draws it with the clip of TpBowLayer; the authored ability
		// gestures were never used for him, the common cast layer moved the wrong arm instead. The mirrored pose is given to the layer for the length of an
		// ability gesture only (it fades in and out with the gesture), the idle hold and the draw stay with the clip
		if (UryuBowPose.bowInLeftHand(p) && num("abilityRight", 1f) > 0.5f) {
			double target = f.phase() == UryuBowFlow.Phase.ABILITY ? 1.0 : 0.0;
			double step = dtSeconds / 0.15;
			st.abilityEnv = st.abilityEnv < target ? Math.min(target, st.abilityEnv + step) : Math.max(target, st.abilityEnv - step);
			st.abilityFrame = st.abilityEnv <= 0.001 ? null : new UryuTpPose.Frame(cur, prev, f.xfade(now), f.weight() * st.abilityEnv, true,
					Math.toRadians(st.lookPitchDeg), now, UryuPoseMath.breathScale(rm, num("breath", 1f)), !rm);
		} else {
			st.abilityFrame = null;
			st.abilityEnv = 0.0;
		}
		return st;
	}

	// ------------------------------------------------------------------ third person

	/** The pose of this frame of a player, or null when it has none (not holding the drawn bow, switched off). */
	static UryuTpPose.Frame tpFrame(AbstractClientPlayerEntity p) {
		if (!tpOn()) {
			return null;
		}
		float td = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
		if (UryuBowPose.bowInLeftHand(p)) {
			// R12: right handed players hold the bow in the left hand and use the draw clip (TpBowLayer); R14: the ability gestures come from here, mirrored
			return update(p, td).abilityFrame;
		}
		return update(p, td).frame;
	}

	/**
	 * The rotation to put on the matrix stack before the bow is drawn in third person (right hander layout, mirrored for a left hander), or null when the arm is
	 * in the idle hold and the display transform of the item is right as it is.
	 */
	static Quaternionf bowCorrection(AbstractClientPlayerEntity p, boolean leftHanded) {
		UryuTpPose.Frame f = tpFrame(p);
		if (f == null) {
			return null;
		}
		TpMat4 c = UryuPoseMath.bowCorrection(UryuTpPose.bowArm(f), UryuTpPose.axis(f));
		if (leftHanded) {
			c = UryuPoseMath.mirror(c);
		}
		double[] q = c.quat();
		return new Quaternionf((float) q[0], (float) q[1], (float) q[2], (float) q[3]);
	}

	// ------------------------------------------------------------------ events

	/** An {@code effect_event} arrived (head of {@link FpCast#onEffectEvent}): starts the third person animation of an Uryu ability for its caster. */
	public static void onEffectEvent(EffectEventS2C e) {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.world == null || !tpOn()) {
				return;
			}
			int effect = e.effectId();
			boolean castStart = effect == EffectIds.URYU_CAST_START;
			if (castStart) {
				float[] p = e.params();
				if (p == null || p.length < 4) {
					return;
				}
				effect = Math.round(p[3]);
			}
			UryuAnims.Meta m = UryuAnims.forGesture(CastGestures.idForEffect(effect));
			if (m == null) {
				return;
			}
			Entity en = mc.world.getEntityById(e.casterId());
			if (!(en instanceof AbstractClientPlayerEntity player)) {
				return;
			}
			float td = mc.getRenderTickCounter().getTickDelta(false);
			update(player, td).flow.onAbility(m, castStart, nowSeconds());
		} catch (RuntimeException ex) {
			ReiatsuTest.LOGGER.warn("[uryupose] event failed: {}", ex.toString());
		}
	}

	// ------------------------------------------------------------------ first person

	/** The quiet breath of the idle hold ({@link UryuPoseMath#breathRow}), scaled by the breath factor of the frame. */
	private static CastGesture.Sample breath(St st, double now) {
		double b = st.frame == null ? 0.0 : st.frame.breath();
		if (b <= 0.0) {
			return null;
		}
		return new CastGesture.Sample(UryuPoseMath.breathRow(now, b), new float[CastGesture.L_DIM], new float[CastGesture.C_DIM],
				new float[CastGesture.T_DIM], new float[CastGesture.T_DIM], (float) st.flow.weight(), 0f);
	}

	private static CastGesture.Sample sampleGesture(St st, String anim, double tick, double now) {
		if (UryuAnims.IDLE.equals(anim)) {
			return breath(st, now);
		}
		UryuBowFlow f = st.flow;
		if (f.phase() == UryuBowFlow.Phase.ABILITY && anim.equals(f.anim())) {
			double k = f.preEvent(now);
			UryuAnims.Meta m = UryuAnims.meta(anim);
			CastGesture g = m == null || k < 0.0 ? null : FpCast.gesture(m.gesture());
			if (g != null) {
				// the pose the gesture starts from (its first rows: bow raised, string hand back) is reached by the event
				return CastGesture.Sample.fade(g.sample(0f, 0f), FpEase.smooth((float) k));
			}
		}
		String id = UryuGestures.rowId(anim);
		CastGesture g = id == null ? null : FpCast.gesture(id);
		if (g == null) {
			return null;
		}
		float t = UryuGestures.rowTime(anim, tick, g.dur());
		return UryuAnims.DRAW.equals(anim) ? g.sample(t, t) : g.sampleReal(t);
	}

	/** Ticks a pseudo draw lasts at least (the effect event of some abilities comes 0 to 3 ticks into the animation, too short to read as a draw). */
	private static final double AB_MIN_DRAW_TICKS = 5.0;

	/**
	 * R15: the first person archer view ({@link FpBow}) has no gesture of its own for the abilities. While an ability animation runs before its effect event
	 * (Heilig Pfeil, Sprenger, Seele Schneider, Wing Barrage, Licht Regen, Sternenstaub; not the releases of shikai and bankai) this gives the draw progress
	 * 0..1 of a pseudo draw: the bow is raised, the right hand goes to the string. At the event it returns -1, which is the release (the hand snaps forward).
	 * Returns -1 when no ability draws now.
	 */
	static double fpAbilityPull(ClientPlayerEntity p) {
		if (!fpOn() || num("abilityFp", 1f) < 0.5f) {
			return -1.0;
		}
		St st = update(p, MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false));
		UryuBowFlow f = st.flow;
		if (f.phase() != UryuBowFlow.Phase.ABILITY || st.frame == null) {
			return -1.0;
		}
		UryuAnims.Meta m = UryuAnims.meta(f.anim());
		if (m == null || m.gesture() == null || m.gesture().endsWith(".release")) {
			return -1.0;
		}
		double t = f.tick(st.nanos / 1.0e9);
		double draw = Math.max(AB_MIN_DRAW_TICKS, m.eventTick());
		return t >= draw ? -1.0 : Math.max(0.0, t / draw);
	}

	/**
	 * The sample of the local hands of this frame: the pull and the release of the bow of the local player, or {@code cast} (the gesture of an ability that runs
	 * in {@link FpCast}) when that is a gesture of its own. Called from {@link FpCast#update}.
	 */
	static CastGesture.Sample mixFirstPerson(ClientPlayerEntity p, CastGesture.Sample cast) {
		if (!fpOn()) {
			return cast;
		}
		St st = update(p, MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false));
		if (st.frame == null || cast.env() > 0.001f) {
			return cast;
		}
		UryuBowFlow f = st.flow;
		double now = st.nanos / 1.0e9;
		CastGesture.Sample cur = sampleGesture(st, f.anim(), f.tick(now), now);
		CastGesture.Sample prev = f.prevAnim() == null ? null : sampleGesture(st, f.prevAnim(), f.prevTick(), now);
		if (cur == null && prev == null) {
			return cast;
		}
		if (prev == null) {
			return cur;
		}
		return CastGesture.Sample.mix(prev == null ? CastGesture.Sample.NONE : prev, cur == null ? CastGesture.Sample.NONE : cur, (float) f.xfade(now));
	}

	/**
	 * The string hand: the vanilla left arm drawn in the picture at the place the {@code L} row of the gesture gives (a fist at the right cheek at full draw,
	 * leaving back and out on the release). Called at the head of {@link FpMotion#renderLeftGuard} (the sword code draws no left hand for a bow), with the
	 * matrices of the hand pass.
	 */
	static void renderStringHand(MatrixStack m, VertexConsumerProvider vcp, ClientPlayerEntity player, int light, ItemStack shown, float equip,
			CastGesture.Sample gs) {
		if (FpBow.wants(player, Hand.MAIN_HAND, shown)) {
			return; // R13: the archer's view (FpBow) draws both arms itself; a third arm appeared next to the bow hand during the abilities
		}
		boolean candidate = ClientOptions.showFirstPersonHand && fpOn() && !player.isInvisible() && !player.isUsingSpyglass()
				&& shown == player.getMainHandStack() && UryuCrossItem.isBow(shown);
		St st = STATES.get(player.getId());
		if (st == null) {
			return;
		}
		boolean idleHand = candidate && FxTune.d("uryu.fp.idleHand", 1.0) > 0.5;
		long nanos = System.nanoTime();
		double now = nanos / 1.0e9;
		String run = FpCast.runId(player.getId());
		boolean want = UryuStringHand.wanted(candidate, gs != null, st.flow.phase(), st.flow.tick(now), st.flow.preEvent(now),
				run != null && UryuGestures.hasStringHand(run));
		float dt = st.presenceNanos == 0L ? 0f : Math.min(0.1f, (nanos - st.presenceNanos) / 1.0e9f);
		st.presenceNanos = nanos;
		st.presence = UryuStringHand.presence(st.presence, want, dt);
		if (!idleHand && (st.presence <= 0.002f || gs == null)) {
			return;
		}
		int side = player.getMainArm() == Arm.LEFT ? -1 : 1;
		// the relaxed free hand at rest hangs at the lower left; the draw rows take over with the presence of the string hand (it keeps its way in while drawing)
		float w = gs == null ? 0f : st.presence;
		float[] row = new float[6];
		float[] gl = gs == null ? null : gs.l();
		for (int i = 0; i < 6; i++) {
			row[i] = IDLE_L[i] + ((gl == null ? IDLE_L[i] : gl[i]) - IDLE_L[i]) * w;
		}
		double[] hold = UryuFpHold.shift(side);
		float pres = idleHand ? 1f : st.presence;
		UryuStringHand.Placement pl0 = UryuStringHand.placement(side, row, equip, pres);
		// the free hand follows the shift of the bow hand (a bit more across, the nock is near the face)
		double follow = FxTune.d("uryu.fp.stringFollowX", 0.3);
		UryuStringHand.Placement pl = new UryuStringHand.Placement(pl0.baseX(), pl0.baseY(), pl0.baseZ(), pl0.dx() + (hold[0] * follow + side * FxTune.d("uryu.fp.stringDx", -0.7)) * w,
				pl0.dy() + (hold[1] + FxTune.d("uryu.fp.stringDy", -0.1)) * w, pl0.dz() + (hold[2] + FxTune.d("uryu.fp.stringDz", -0.35)) * w, pl0.rotDeg());
		double[] r = pl.rotDeg();
		m.push();
		m.translate(pl.baseX(), pl.baseY(), pl.baseZ());
		m.translate(pl.dx(), pl.dy(), pl.dz());
		m.translate(-side * UryuStringHand.FIST_X, UryuStringHand.FIST_Y, UryuStringHand.FIST_Z);
		m.multiply(new Quaternionf().rotationXYZ((float) r[0] * RAD, (float) r[1] * RAD, (float) r[2] * RAD));
		m.translate(side * UryuStringHand.FIST_X, -UryuStringHand.FIST_Y, -UryuStringHand.FIST_Z);
		FirstPersonHand.renderVanillaArmAt(MinecraftClient.getInstance(), player, side > 0, m, vcp, light);
		m.pop();
	}
}
