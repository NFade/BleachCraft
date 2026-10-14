package dev.minebleach.reiatsutest.client.model;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientOptions;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.core.combat.CastGesture;
import dev.minebleach.reiatsutest.core.combat.CastGestures;
import dev.minebleach.reiatsutest.core.combat.TpMat4;
import dev.minebleach.reiatsutest.core.combat.UryuAnims;
import dev.minebleach.reiatsutest.core.combat.UryuBowFlow;
import dev.minebleach.reiatsutest.core.combat.UryuGestures;
import dev.minebleach.reiatsutest.core.combat.UryuPoseMath;
import dev.minebleach.reiatsutest.core.combat.UryuPoseTrack;
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
	private static final float FIST_X = 0.134f;
	private static final float FIST_Y = -0.018f;
	private static final float FIST_Z = -0.316f;
	/** Turn of the string arm about its fist (degrees, right hander layout): it comes in from the lower left like the free hand of the two handed hold. */
	private static final float[] STRING_ARM_TURN = {-15f, -30f, -35f};
	/** A cast start (effect 78) this many seconds old makes the ability event of the same ability not restart its animation. */
	private static final double CAST_START_WINDOW = 3.0;

	private static final class St {
		final UryuBowFlow flow = new UryuBowFlow(UryuBowMotion::endTick);
		long nanos;
		UryuTpPose.Frame frame;
		float lookPitchDeg;
		String castAnim;
		double castAt;
		float presence;
		long presenceNanos;
	}

	private static final Map<Integer, St> STATES = new HashMap<>();
	private static final Map<String, UryuPoseTrack> TRACKS = new HashMap<>();
	private static boolean warnedHand;

	private UryuBowMotion() {
	}

	/** Registers the third person layer (called from {@link FpCast#init}). */
	public static void init() {
		UryuTpLayer.register();
	}

	/** World change. */
	public static void clear() {
		STATES.clear();
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
		st.nanos = nanos;
		double now = nanos / 1.0e9;
		boolean holding = enabled() && holdingBow(p);
		boolean using = holding && p.isUsingItem() && p.getActiveHand() == Hand.MAIN_HAND;
		st.flow.update(now, holding, using, using ? p.getItemUseTime() + td : 0.0);
		st.lookPitchDeg = p.getPitch(td);
		UryuBowFlow f = st.flow;
		if (!f.active()) {
			st.frame = null;
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
				rm ? 0.5 * num("breath", 1f) : num("breath", 1f), !rm);
		return st;
	}

	// ------------------------------------------------------------------ third person

	/** The pose of this frame of a player, or null when it has none (not holding the drawn bow, switched off). */
	static UryuTpPose.Frame tpFrame(AbstractClientPlayerEntity p) {
		if (!tpOn()) {
			return null;
		}
		float td = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
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
			St st = update(player, td);
			double now = nowSeconds();
			if (castStart) {
				st.castAnim = m.anim();
				st.castAt = now;
				st.flow.trigger(m.anim(), 0, now);
			} else if (m.anim().equals(st.castAnim) && now - st.castAt < CAST_START_WINDOW) {
				// the animation runs since the key press: the event of the release changes nothing
				st.castAnim = null;
			} else {
				st.flow.trigger(m.anim(), m.startTick(), now);
			}
		} catch (RuntimeException ex) {
			ReiatsuTest.LOGGER.warn("[uryupose] event failed: {}", ex.toString());
		}
	}

	// ------------------------------------------------------------------ first person

	private static CastGesture.Sample sampleGesture(String anim, double tick) {
		String id = UryuAnims.DRAW.equals(anim) ? UryuGestures.DRAW : UryuAnims.RELEASE.equals(anim) ? UryuGestures.RELEASE : null;
		if (id == null) {
			return null;
		}
		CastGesture g = FpCast.gesture(id);
		if (g == null) {
			return null;
		}
		float t = (float) (tick / UryuBowFlow.TPS);
		if (UryuAnims.DRAW.equals(anim)) {
			t = Math.min(t, g.dur());
			return g.sample(t, t);
		}
		return g.sampleReal(t);
	}

	/**
	 * Progress 0..1 of the raise or the draw before the effect event of Licht Regen and Sternenstaub (their animation runs since the cast start event, the key press,
	 * and their first person gesture only starts at the release event), or -1 when the flow is not in that phase.
	 */
	private static double preEvent(UryuBowFlow f, double now) {
		if (f.phase() != UryuBowFlow.Phase.ABILITY) {
			return -1.0;
		}
		UryuAnims.Meta m = UryuAnims.meta(f.anim());
		if (m == null || m.startTick() <= 0 || m.eventTick() <= 0) {
			return -1.0;
		}
		double t = f.tick(now);
		return t >= m.eventTick() ? -1.0 : Math.max(0.0, t / m.eventTick());
	}

	/** The quiet breath of the idle hold: the hand rises and falls 0.004 blocks and rolls 0.8 degrees at 0.22 Hz (storyboard 3.2), scaled by the breath factor of the frame. */
	private static CastGesture.Sample breath(St st, double now) {
		double b = st.frame == null ? 0.0 : st.frame.breath();
		if (b <= 0.0) {
			return null;
		}
		float s = (float) (Math.sin(2.0 * Math.PI * 0.22 * now) * b);
		return new CastGesture.Sample(new float[] {0f, 0.004f * s, 0f, 0f, 0f, 0.8f * s}, new float[CastGesture.L_DIM], new float[CastGesture.C_DIM],
				new float[CastGesture.T_DIM], new float[CastGesture.T_DIM], (float) st.flow.weight(), 0f);
	}

	private static CastGesture.Sample sampleGesture(St st, String anim, double tick, double now) {
		if (UryuAnims.IDLE.equals(anim)) {
			return breath(st, now);
		}
		UryuBowFlow f = st.flow;
		if (f.phase() == UryuBowFlow.Phase.ABILITY && anim.equals(f.anim())) {
			double k = preEvent(f, now);
			UryuAnims.Meta m = UryuAnims.meta(anim);
			CastGesture g = m == null || k < 0.0 ? null : FpCast.gesture(m.gesture());
			if (g != null) {
				// the pose the gesture starts from (its first rows: bow raised, string hand back) is reached by the event
				return CastGesture.Sample.fade(g.sample(0f, 0f), dev.minebleach.reiatsutest.core.combat.FpEase.smooth((float) k));
			}
		}
		String id = UryuAnims.DRAW.equals(anim) ? UryuGestures.DRAW : UryuAnims.RELEASE.equals(anim) ? UryuGestures.RELEASE : null;
		if (id == null) {
			return null;
		}
		CastGesture g = FpCast.gesture(id);
		if (g == null) {
			return null;
		}
		float t = (float) (tick / UryuBowFlow.TPS);
		if (UryuAnims.DRAW.equals(anim)) {
			t = Math.min(t, g.dur());
			return g.sample(t, t);
		}
		return g.sampleReal(t);
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
		boolean candidate = ClientOptions.showFirstPersonHand && fpOn() && !player.isInvisible() && !player.isUsingSpyglass()
				&& shown == player.getMainHandStack() && UryuCrossItem.isBow(shown);
		St st = STATES.get(player.getId());
		long now = System.nanoTime();
		boolean want = false;
		if (candidate && gs != null && st != null) {
			UryuBowFlow.Phase ph = st.flow.phase();
			String run = FpCast.runId(player.getId());
			want = ph == UryuBowFlow.Phase.DRAW || ph == UryuBowFlow.Phase.RELEASE && st.flow.tick(now / 1.0e9) < 14.0
					|| preEvent(st.flow, now / 1.0e9) >= 0.0 || run != null && UryuGestures.hasStringHand(run);
		}
		if (st == null) {
			return;
		}
		float dt = st.presenceNanos == 0L ? 0f : Math.min(0.1f, (now - st.presenceNanos) / 1.0e9f);
		st.presenceNanos = now;
		st.presence = want ? Math.min(1f, st.presence + dt / 0.18f) : Math.max(0f, st.presence - dt / 0.22f);
		if (st.presence <= 0.002f || gs == null) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean left = player.getMainArm() == Arm.LEFT;
		int i = left ? -1 : 1;
		float[] gl = gs.l();
		float eqY = -0.6f * (1f - Math.max(0f, Math.min(1f, equip)));
		float e = dev.minebleach.reiatsutest.core.combat.FpEase.smooth(st.presence);
		// absolute (camera frame of the hand code): the main hand frame plus the row, the place of the empty fist of the other hand, the way in from below
		org.joml.Vector3f target = new org.joml.Vector3f(i * 0.56f + i * gl[0], -0.52f + eqY + gl[1], -0.72f + gl[2]);
		org.joml.Vector3f rest = new org.joml.Vector3f(-i * (0.56f + FIST_X), -0.52f + eqY + FIST_Y, -0.72f + FIST_Z);
		org.joml.Vector3f from = new org.joml.Vector3f(target).add(0f, -0.9f, 0.1f);
		org.joml.Vector3f pos = new org.joml.Vector3f(from).lerp(target, e);
		org.joml.Vector3f delta = pos.sub(rest);
		float[] ar = {STRING_ARM_TURN[0] + gl[3], i * STRING_ARM_TURN[1] + i * gl[4], i * STRING_ARM_TURN[2] + i * gl[5]};
		m.push();
		m.translate(-i * 0.56f, -0.52f + eqY, -0.72f);
		m.translate(delta.x, delta.y, delta.z);
		m.translate(-i * FIST_X, FIST_Y, FIST_Z);
		m.multiply(new Quaternionf().rotationXYZ(ar[0] * RAD, ar[1] * RAD, ar[2] * RAD));
		m.translate(i * FIST_X, -FIST_Y, -FIST_Z);
		FirstPersonHand.renderVanillaArmAt(mc, player, !left, m, vcp, light);
		m.pop();
	}
}
