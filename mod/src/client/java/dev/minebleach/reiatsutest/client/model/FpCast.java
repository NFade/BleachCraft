package dev.minebleach.reiatsutest.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxTune;
import dev.minebleach.reiatsutest.core.combat.CastGesture;
import dev.minebleach.reiatsutest.core.combat.CastGestures;
import dev.minebleach.reiatsutest.core.combat.FpEase;
import dev.minebleach.reiatsutest.core.combat.FreeHands;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Dispatcher of the ability gestures (FP_ABILITY_ANIMS stage 1). One {@code effect_event} (hooked from {@code ClientNet}, nothing else of
 * the effect code is touched) starts the {@link CastGesture} of its ability for the caster: the local player gets the first person
 * hands ({@link FpMotion} adds the sampled pose to the held stance, {@link #renderFreeHands} draws the empty hands of the Byakuya bankai) and
 * the camera nudge ({@code GameRendererMixin}); every caster gets the third person arm deltas ({@link TpCastLayer}).
 *
 * <p>STEERING: while {@link ClientState#steering()} is true (set by the swarm steering key code) the local hands hold the sustained
 * conducting gesture {@link CastGestures#STEER}; releasing it plays the gesture out.
 *
 * <p>Switches and numbers, dev only ({@code pose_override.json}, section {@code fp_cast}): {@code enabled} (0: everything off),
 * {@code fp}, {@code tp}, {@code cam} (0/1), {@code scale} (amplitude of hands), {@code cam_scale}, {@code speed} (time scale),
 * {@code free.anchor_r}, {@code free.anchor_l}, {@code free.arm_rot} (the empty hands), and one object per ability id
 * ({@code "rukia.shikai.release": {"enabled": 1, "dur": 1.3, "in": .1, "out": .35, "R": [[t, px, py, pz, rx, ry, rz, ease], ...], "L":
 * [[t, px, py, pz, arx, ary, arz, free, ease], ...], "C": [[t, pitch, yaw, roll, fov, ease], ...], "TR"/"TL": [[t, pitch, yaw, roll,
 * ease], ...], "orbit": [["P"|"T"|"L", t0, t1, radius, hz, phase]], "vib": [["R"|"L", t0, t1, amp, hz]], "holds": [[at, len]]}}).
 * A channel that is present replaces the shipped one. Ease: 0 smooth, 1 out, 2 in, 3 linear, 4 spring. See design/results/fp_abilities_s1.md.
 */
public final class FpCast {
	private static final float RAD = (float) (Math.PI / 180.0);
	private static final float FIST_X = 0.134f;
	private static final float FIST_Y = -0.018f;
	private static final float FIST_Z = -0.316f;
	/** Seconds a running gesture takes to hand over to the next one. */
	private static final float CROSSFADE = 0.15f;

	private static final class Run {
		CastGesture g;
		long start;
		Run prev;
		long fadeStart;
	}

	private static final Map<Integer, Run> RUNS = new HashMap<>();
	private static final Map<Integer, CastGesture.Sample> TP_CACHE = new HashMap<>();
	private static final Map<Integer, Long> TP_CACHE_AT = new HashMap<>();

	// local frame cache
	private static long cacheNanos;
	private static boolean cacheValid;
	private static CastGesture.Sample localSample;
	private static CastGesture.Sample localCast = CastGesture.Sample.NONE;
	private static Object lastWorld;
	// steering
	private static boolean steerRunning;
	private static float steerG;
	private static float steerClock;
	private static long steerLast;
	private static CastGesture.Sample steerSample = CastGesture.Sample.NONE;

	// config cache
	private static JsonObject cfgSource;
	private static final Map<String, CastGesture> CFG = new HashMap<>();

	/** Statistics for the harness / log: gestures started. */
	public static int started;
	public static String lastId = "";

	private FpCast() {
	}

	public static void init() {
		TpCastLayer.register();
	}

	// ------------------------------------------------------------------ config

	private static JsonObject cfgRoot() {
		JsonObject o = HeldPose.overrideObject();
		if (o == null || !o.has("fp_cast") || !o.get("fp_cast").isJsonObject()) {
			return null;
		}
		return o.getAsJsonObject("fp_cast");
	}

	private static float num(String key, float def) {
		JsonObject c = cfgRoot();
		if (c == null) {
			return def;
		}
		JsonElement e = c.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsFloat() : def;
		} catch (RuntimeException ex) {
			return def;
		}
	}

	private static float[] vec(String key, float[] def) {
		JsonObject c = cfgRoot();
		JsonElement e = c == null ? null : c.get(key);
		try {
			if (e != null && e.isJsonArray() && e.getAsJsonArray().size() >= 3) {
				JsonArray a = e.getAsJsonArray();
				return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
			}
		} catch (RuntimeException ex) {
			// fall through
		}
		return def;
	}

	/**
	 * Number of the reach limit section {@code fp_cast.clamp} (round 5): {@code enabled} (0 off), {@code near} (blocks the hands and the blade must stay in
	 * front of the camera, default 0.12; the near plane is at 0.05), {@code fwd} (the softest limit of the forward reach of a hand, blocks, 0.30),
	 * {@code release} (seconds a limited gesture takes to come back to full size, 0.15), {@code screen_x} / {@code screen_y_min} / {@code screen_y_max}
	 * (the centre of a fist stays inside the picture: 1.0 of the half width, between -1.05 and 0.95 of the half height).
	 */
	static float clampNum(String key, float def) {
		JsonObject c = cfgRoot();
		if (c == null || !c.has("clamp") || !c.get("clamp").isJsonObject()) {
			return def;
		}
		JsonElement e = c.getAsJsonObject("clamp").get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsFloat() : def;
		} catch (RuntimeException ex) {
			return def;
		}
	}

	private static boolean on(String key) {
		return num(key, 1f) > 0.5f;
	}

	public static boolean enabled() {
		return on("enabled");
	}

	/** The gesture of an ability with the override file applied (null: unknown, or switched off with {@code "<id>": {"enabled": 0}}). */
	static CastGesture gesture(String id) {
		JsonObject c = cfgRoot();
		if (c != cfgSource) {
			cfgSource = c;
			CFG.clear();
		}
		if (CFG.containsKey(id)) {
			return CFG.get(id);
		}
		CastGesture base = CastGestures.defaults().get(id);
		CastGesture res = base;
		if (base != null && c != null && c.has(id) && c.get(id).isJsonObject()) {
			try {
				res = parse(base, c.getAsJsonObject(id));
			} catch (RuntimeException e) {
				ReiatsuTest.LOGGER.warn("[fpcast] bad override of {}: {}", id, e.toString());
			}
		}
		CFG.put(id, res);
		return res;
	}

	private static CastGesture parse(CastGesture base, JsonObject o) {
		if (o.has("enabled") && o.get("enabled").getAsFloat() < 0.5f) {
			return null;
		}
		CastGesture.Builder b = base.toBuilder();
		if (o.has("dur")) {
			b.dur(o.get("dur").getAsDouble());
		}
		if (o.has("in")) {
			b.in(o.get("in").getAsDouble());
		}
		if (o.has("out")) {
			b.out(o.get("out").getAsDouble());
		}
		if (o.has("sustain")) {
			b.sustain(o.get("sustain").getAsDouble());
		}
		String[][] ch = {{"R", "R", "6"}, {"L", "L", "7"}, {"C", "C", "4"}, {"TR", "r", "3"}, {"TL", "l", "3"}};
		for (String[] c : ch) {
			if (!o.has(c[0])) {
				continue;
			}
			char code = c[1].charAt(0);
			int dim = Integer.parseInt(c[2]);
			b.clear(code);
			for (JsonElement row : o.getAsJsonArray(c[0])) {
				JsonArray a = row.getAsJsonArray();
				if (a.size() < dim + 1) {
					throw new IllegalArgumentException(c[0] + " row needs " + (dim + 1) + " numbers");
				}
				float[] v = new float[dim];
				for (int i = 0; i < dim; i++) {
					v[i] = a.get(1 + i).getAsFloat();
				}
				int ease = a.size() > dim + 1 ? a.get(dim + 1).getAsInt() : CastGesture.E_SMOOTH;
				b.add(code, new CastGesture.Key(a.get(0).getAsFloat(), v, ease));
			}
		}
		if (o.has("orbit")) {
			b.clear('O');
			for (JsonElement row : o.getAsJsonArray("orbit")) {
				JsonArray a = row.getAsJsonArray();
				b.orbit(a.get(0).getAsString().charAt(0), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble(),
						a.get(4).getAsDouble(), a.size() > 5 ? a.get(5).getAsDouble() : 0.0);
			}
		}
		if (o.has("vib")) {
			b.clear('V');
			for (JsonElement row : o.getAsJsonArray("vib")) {
				JsonArray a = row.getAsJsonArray();
				b.vib(a.get(0).getAsString().charAt(0), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble(), a.get(4).getAsDouble());
			}
		}
		if (o.has("holds")) {
			b.clear('H');
			for (JsonElement row : o.getAsJsonArray("holds")) {
				JsonArray a = row.getAsJsonArray();
				b.hold(a.get(0).getAsDouble(), a.get(1).getAsDouble());
			}
		}
		return b.build();
	}

	// ------------------------------------------------------------------ events

	/** An {@code effect_event} arrived (called from {@code ClientNet}, main thread): starts the gesture of its ability for the caster. */
	public static void onEffectEvent(MinecraftClient mc, EffectEventS2C e) {
		try {
			if (mc.world == null || !enabled()) {
				return;
			}
			String id = CastGestures.idForEffect(e.effectId());
			if (id == null) {
				return;
			}
			CastGesture g = gesture(id);
			if (g == null) {
				return;
			}
			if (CastGestures.BYAKUYA_SHIKAI.equals(id)) {
				g = CastGestures.withPinkHold(g, FxTune.d("release.pinkTime", CastGestures.PINK_HOLD));
			}
			start(e.casterId(), g, System.nanoTime());
		} catch (RuntimeException ex) {
			ReiatsuTest.LOGGER.warn("[fpcast] start failed: {}", ex.toString());
		}
	}

	/** Starts a gesture for an entity (a running one is handed over by a short crossfade). */
	static void start(int entityId, CastGesture g, long now) {
		Run old = RUNS.get(entityId);
		Run r = new Run();
		r.g = g;
		r.start = now;
		if (old != null && now - old.start < (long) (old.g.totalSeconds() * 1.0e9f)) {
			old.prev = null;
			r.prev = old;
			r.fadeStart = now;
		}
		RUNS.put(entityId, r);
		if (RUNS.size() > 64) {
			RUNS.values().removeIf(x -> now - x.start > 15_000_000_000L);
		}
		started++;
		lastId = g.id();
		cacheValid = false;
		TP_CACHE.remove(entityId);
	}

	/** Clears everything (world change, tests). */
	public static void clear() {
		RUNS.clear();
		TP_CACHE.clear();
		TP_CACHE_AT.clear();
		localSample = null;
		localCast = CastGesture.Sample.NONE;
		steerSample = CastGesture.Sample.NONE;
		steerRunning = false;
		cacheValid = false;
	}

	// ------------------------------------------------------------------ sampling

	private static CastGesture.Sample sampleRun(Run r, long now, float speed) {
		float t = (now - r.start) / 1.0e9f * speed;
		CastGesture.Sample s = t > r.g.totalSeconds() ? null : r.g.sampleReal(t);
		if (r.prev != null) {
			float k = FpEase.smooth((now - r.fadeStart) / 1.0e9f / CROSSFADE);
			if (k >= 1f) {
				r.prev = null;
			} else {
				CastGesture.Sample p = sampleRun(r.prev, now, speed);
				if (p == null) {
					p = CastGesture.Sample.NONE;
				}
				s = CastGesture.Sample.mix(p, s == null ? CastGesture.Sample.NONE : s, k);
			}
		}
		return s;
	}

	private static CastGesture.Sample amplified(CastGesture.Sample s, float k) {
		return k == 1f ? s : s.scaled(k);
	}

	/** Recomputes the sample of the local player when the frame is new (the world pass and the hand pass share it). */
	private static void update() {
		long now = System.nanoTime();
		if (cacheValid && now - cacheNanos < 1_000_000L) {
			return;
		}
		cacheNanos = now;
		cacheValid = true;
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity p = mc.player;
		if (p == null || mc.world == null) {
			if (lastWorld != null || !RUNS.isEmpty()) {
				clear();
			}
			lastWorld = null;
			localSample = null;
			return;
		}
		if (mc.world != lastWorld) {
			lastWorld = mc.world;
			clear();
			cacheNanos = now;
			cacheValid = true;
		}
		if (!enabled()) {
			localSample = null;
			return;
		}
		float speed = Math.max(0.05f, num("speed", 1f));
		float amp = num("scale", 1f);
		CastGesture.Sample cast = CastGesture.Sample.NONE;
		Run r = RUNS.get(p.getId());
		if (r != null) {
			CastGesture.Sample s = sampleRun(r, now, speed);
			if (s == null) {
				RUNS.remove(p.getId());
			} else {
				cast = s;
			}
		}
		// the steering: sustained while the key is held, then plays out
		CastGesture sg = gesture(CastGestures.STEER);
		boolean steering = sg != null && ClientState.steering() && p.getMainHandStack().getItem() instanceof ZanpakutoItem;
		float dt = steerLast == 0L ? 0f : Math.min(0.1f, (now - steerLast) / 1.0e9f) * speed;
		steerLast = now;
		if (steering) {
			if (!steerRunning) {
				steerRunning = true;
				steerG = 0f;
				steerClock = 0f;
			}
			steerG = Math.min(steerG + dt, Math.max(0f, sg.sustainAt()));
			steerClock += dt;
		} else if (steerRunning) {
			steerG += dt;
			steerClock += dt;
			if (sg == null || steerG > sg.dur() + sg.out()) {
				steerRunning = false;
			}
		}
		steerSample = steerRunning && sg != null ? sg.sample(steerG, steerClock) : CastGesture.Sample.NONE;
		localCast = cast;
		CastGesture.Sample total = steerRunning ? CastGesture.Sample.mix(cast, steerSample, steerSample.env()) : cast;
		boolean any = total.env() > 0.001f || steerRunning;
		localSample = any ? amplified(total, amp) : null;
	}

	/** Hands of the local player this frame (rigid pose of the main hand and the sword, the free hand), or null when no gesture runs. */
	static CastGesture.Sample fpSample() {
		if (!on("fp")) {
			return null;
		}
		update();
		return localSample;
	}

	/** Third person arm deltas of a caster this frame, or null. */
	static CastGesture.Sample tpSample(int entityId) {
		if (!on("tp") || !enabled()) {
			return null;
		}
		long now = System.nanoTime();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null && mc.player.getId() == entityId) {
			update();
			return localSample;
		}
		Run r = RUNS.get(entityId);
		if (r == null) {
			return null;
		}
		Long at = TP_CACHE_AT.get(entityId);
		if (at != null && now - at < 1_000_000L) {
			return TP_CACHE.get(entityId);
		}
		CastGesture.Sample s = sampleRun(r, now, Math.max(0.05f, num("speed", 1f)));
		if (s == null) {
			RUNS.remove(entityId);
			TP_CACHE.remove(entityId);
			TP_CACHE_AT.remove(entityId);
			return null;
		}
		s = amplified(s, num("scale", 1f));
		TP_CACHE.put(entityId, s);
		TP_CACHE_AT.put(entityId, now);
		return s;
	}

	/** True while a gesture of this entity can change its third person arms. */
	static boolean tpActive(int entityId) {
		if (RUNS.isEmpty() && !steerRunning) {
			return false;
		}
		return tpSample(entityId) != null;
	}

	// ------------------------------------------------------------------ camera

	/**
	 * Camera nudge of this frame in degrees: out[0] pitch (above 0 = up), out[1] yaw (above 0 = right), out[2] roll, out[3] fov. All zero
	 * under reduce motion and photosensitive safe (the FP_ABILITY_ANIMS RM column), scaled by {@code screenFxScale} and {@code cam_scale}.
	 */
	public static void camera(float[] out) {
		out[0] = 0f;
		out[1] = 0f;
		out[2] = 0f;
		out[3] = 0f;
		if (!on("cam") || FxConfig.reduceMotion || FxConfig.photosensitiveSafe) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || !mc.options.getPerspective().isFirstPerson()) {
			return;
		}
		update();
		CastGesture.Sample s = localSample;
		if (s == null) {
			return;
		}
		float k = (float) FxConfig.screenFxScale * num("cam_scale", 1f);
		float[] c = s.c();
		for (int i = 0; i < 4; i++) {
			out[i] = c[i] * k;
		}
	}

	// ------------------------------------------------------------------ empty hands (Byakuya bankai)

	/** FpParams of {@link FreeHands}: the numbers of {@link FreeHands} come from the flat keys {@code free.<key>} of the {@code fp_cast} section. */
	private static final dev.minebleach.reiatsutest.core.combat.FpParams FREE = new dev.minebleach.reiatsutest.core.combat.FpParams() {
		@Override
		public float num(String key, float def) {
			return FpCast.num("free." + key, def);
		}

		@Override
		public float[] vec(String key, float[] def) {
			return FpCast.vec("free." + key, def);
		}
	};

	/** Smoothed presence of the empty hands (0 hidden below the screen edge, 1 in the idle pose) and the clock that steps it. */
	private static float freeWeight;
	private static long freeLast;

	/**
	 * Draws the empty hands of the local player while the held zanpakuto shows no sword in the hand (the Byakuya bankai: the sword is scattered,
	 * the stack in the main hand draws nothing, and vanilla draws no arm for a held item, so before round 6 the screen was empty outside a
	 * gesture). BOTH vanilla arms are drawn all the time ({@code free.idle} 0 brings the old behaviour back: only while a gesture runs) in the
	 * idle pose of {@link FreeHands}: the right forearm raised across the body, palm down, the left hand low at the hip, a slow breath. They rise from
	 * below the screen edge when the bankai starts ({@code free.rise} seconds) and with the equip animation. A gesture of the ability
	 * ({@code R} row: right hand, {@code L} row: left hand; deltas on the idle) moves them away from the idle and back; the breath is silent while
	 * one runs. Called at the head of {@code FpMotion.renderLeftGuard}, with the matrices of the hand pass, before the buffer is flushed (the vanilla
	 * arm model is drawn like {@link FirstPersonHand#renderVanillaArmAt}: that code draws the arm of an empty hand without any item, so the check
	 * {@code item instanceof ZanpakutoItem} of the held arm code does not stand in its way).
	 */
	static void renderFreeHands(MatrixStack m, VertexConsumerProvider vcp, ClientPlayerEntity player, int light, ItemStack shown, float equip) {
		long now = System.nanoTime();
		float dt = freeLast == 0L ? 0f : Math.min(0.1f, (now - freeLast) / 1.0e9f);
		freeLast = now;
		boolean candidate = dev.minebleach.reiatsutest.client.ClientOptions.showFirstPersonHand && !player.isInvisible() && !player.isUsingSpyglass()
				&& shown == player.getMainHandStack() && shown.getItem() instanceof ZanpakutoItem;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (candidate) {
			BakedModel model = mc.getItemRenderer().getModel(shown, player.getWorld(), player, 0);
			if (model instanceof ObjItemBakedModel obj && obj.drawsInHand(shown)) {
				candidate = false;
			}
		}
		CastGesture.Sample s = candidate ? fpSample() : null;
		boolean gesture = s != null && s.hand() > 0.01f;
		boolean want = candidate && (on("free.idle") || gesture);
		freeWeight = FreeHands.stepPresence(freeWeight, want, dt, Math.max(0.05f, num("free.rise", 0.45f)));
		if (!want || freeWeight <= 0.001f) {
			return;
		}
		boolean left = player.getMainArm() == Arm.LEFT;
		int i = left ? -1 : 1;
		float eqY = -0.6f * (1f - Math.max(0f, Math.min(1f, equip)));
		float time = (float) ((now % 100_000_000_000L) / 1.0e9);
		float breath = FxConfig.reduceMotion || FxConfig.photosensitiveSafe ? 0f : (s == null ? 1f : 1f - Math.max(0f, Math.min(1f, s.env())));
		// the forward reach of the empty hands is limited softly (free.fwd, blocks) like the hands with the sword
		float fwd = num("free.fwd", 0.50f);
		float[] rr = s == null ? null : s.r().clone();
		float[] ll = s == null ? null : s.l().clone();
		if (rr != null) {
			rr[2] = dev.minebleach.reiatsutest.core.combat.FpClamp.softForward(rr[2], fwd);
			ll[2] = dev.minebleach.reiatsutest.core.combat.FpClamp.softForward(ll[2], fwd);
		}
		// main hand: the right hand layout (mirrored for a left hander)
		drawFreeArm(mc, player, m, vcp, light, left, i * 0.56f, -0.52f + eqY, left, FreeHands.frame(FREE, false, rr, time, breath, freeWeight));
		// the other hand: the left hand layout
		drawFreeArm(mc, player, m, vcp, light, !left, -i * 0.56f, -0.52f + eqY, left, FreeHands.frame(FREE, true, ll, time, breath, freeWeight));
	}

	/**
	 * One empty arm: at the hand frame of its side ({@code ox}, {@code oy}, -0.72), moved by the frame and turned about its fist. The frame is in the
	 * right hander layout; {@code mirror} (a left hander) mirrors it (x of the shift, y and z of the turn). {@code armLeft}: the arm that is drawn is the left one.
	 */
	private static void drawFreeArm(MinecraftClient mc, ClientPlayerEntity player, MatrixStack m, VertexConsumerProvider vcp, int light, boolean armLeft,
			float ox, float oy, boolean mirror, FreeHands.Frame f) {
		float mx = mirror ? -1f : 1f;
		float[] q = mirror ? FreeHands.mirrorQuat(f.q()) : f.q();
		float fx = (armLeft ? -1f : 1f) * FIST_X;
		m.push();
		m.translate(ox, oy, -0.72f);
		m.translate(mx * f.move()[0], f.move()[1], f.move()[2]);
		m.translate(fx, FIST_Y, FIST_Z);
		m.multiply(new Quaternionf(q[0], q[1], q[2], q[3]));
		m.translate(-fx, -FIST_Y, -FIST_Z);
		FirstPersonHand.renderVanillaArmAt(mc, player, armLeft, m, vcp, light);
		m.pop();
	}

	/** Turns the matrix about the point (x, y, z) by Euler angles in degrees (camera frame of the hand code). */
	static void turnAbout(MatrixStack m, float x, float y, float z, float rx, float ry, float rz) {
		if (rx == 0f && ry == 0f && rz == 0f) {
			return;
		}
		m.translate(x, y, z);
		m.multiply(new Quaternionf().rotationXYZ(rx * RAD, ry * RAD, rz * RAD));
		m.translate(-x, -y, -z);
	}

	/** Statistics for logs: the set of running gestures. */
	public static String describe() {
		StringBuilder sb = new StringBuilder();
		for (Iterator<Map.Entry<Integer, Run>> it = RUNS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Run> en = it.next();
			sb.append(en.getKey()).append('=').append(en.getValue().g.id()).append(' ');
		}
		return sb.toString().trim();
	}
}
