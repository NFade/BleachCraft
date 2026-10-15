package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.UryuArrowLook;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * The flying Heilig Pfeil of every Uryu on this client (URYU_VFX_STORYBOARD 2.3; design/URYU_PLAN.md U4): the server simulates,
 * {@link UryuArrowsS2C#ARROWS} lists the live arrows near the receiver once per server tick (position and velocity per tick, flags = focus +
 * 4 * kind + 32 * steering). Each arrow is drawn in three layers, extrapolated by the time since its packet (so it moves smoothly between the
 * 20 Hz packets):
 * <ol>
 * <li>the Quincy arrow mesh {@code uryu_fx/uryu_arrow} (thin shaft, chevron fletching, barbed head) at full brightness, scale 1.4 + 0.8 p
 * (+0.4 per Focus stack, at most 3.0), p read back from the speed ({@link UryuArrowLook#drawPower});</li>
 * <li>the additive glow: a strip along the shaft (0.9 + 1.2 p, Q3 with a white core), a pinpoint and a star at the head, the steering ring of a
 * homing arrow;</li>
 * <li>the trail: the last 8 packet positions as {@code flechette_trail} strips (head 0.7 to tail 0, Q2 with a white core), and for Focus 2 and 3
 * (and the Z arrow) a flake spark every 2.0 blocks.</li>
 * </ol>
 * The one shot effects (muzzle, hit) are {@link UryuArrowFx}. The kinds RAIN and WING get the lean look (short trail, no muzzle); their own
 * looks come with U-FX 8 and 9. The old placeholder colour constants are gone; the palette is {@link UryuPalette}. UNVERIFIED in game.
 */
public final class UryuArrowRenderer {
	private static final Identifier TEXTURE = ReiatsuTest.id("textures/item/uryu_fx_diffuse.png");
	private static final int FULL_LIGHT = 0xF000F0;

	private static final class Arrow {
		double x;
		double y;
		double z;
		double vx;
		double vy;
		double vz;
		double arrivedS;
		int focus;
		int kind;
		boolean steering;
		double p = 1.0;
		boolean own;
		int ticks;
		double carry;
		final ArrayDeque<double[]> trail = new ArrayDeque<>();
	}

	/** An arrow that just left the list (it hit something): the IMPACT packet is matched to it to know the Focus level and the direction. */
	private record Ended(double x, double y, double z, double vx, double vy, double vz, int focus, int kind, boolean own, double ns) {
	}

	private static final Map<Integer, Arrow> ARROWS = new HashMap<>();
	private static final ArrayDeque<Ended> ENDED = new ArrayDeque<>();
	private static FxMesh mesh;
	private static boolean meshTried;
	private static RenderLayer layer;
	private static boolean inited;
	/** Harness / debug: arrows drawn in the last frame. */
	public static int lastDrawn;
	public static int lastGlow;

	private UryuArrowRenderer() {
	}

	public static void init() {
		if (inited) {
			return;
		}
		inited = true;
		WorldRenderEvents.AFTER_ENTITIES.register(UryuArrowRenderer::draw);
		FxGlowBatch.addSource(UryuArrowRenderer::emitGlow);
		ClientTickEvents.END_CLIENT_TICK.register(UryuArrowRenderer::tick);
	}

	public static int live() {
		return ARROWS.size();
	}

	public static void clear() {
		ARROWS.clear();
		ENDED.clear();
	}

	/** The Focus level and kind of the live (or just ended) arrows, for the harness: kind in the high bits. */
	public static int[] debugKinds() {
		int[] out = new int[ARROWS.size()];
		int i = 0;
		for (Arrow a : ARROWS.values()) {
			out[i++] = a.focus | (a.kind << 2);
		}
		return out;
	}

	/** The arrow list packet: add and update the arrows in it, drop the ones that are gone. */
	public static void onList(int[] ids, float[] d) {
		init();
		double now = FxClock.now;
		Set<Integer> present = new HashSet<>();
		for (int id : ids) {
			present.add(id);
		}
		Iterator<Map.Entry<Integer, Arrow>> it = ARROWS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Arrow> en = it.next();
			if (!present.contains(en.getKey())) {
				Arrow g = en.getValue();
				ENDED.addLast(new Ended(g.x, g.y, g.z, g.vx, g.vy, g.vz, g.focus, g.kind, g.own, now));
				while (ENDED.size() > 24) {
					ENDED.pollFirst();
				}
				it.remove();
			}
		}
		for (int i = 0; i < ids.length; i++) {
			int o = i * UryuArrowsS2C.PER_ARROW;
			if (o + 6 >= d.length) {
				break;
			}
			Arrow a = ARROWS.get(ids[i]);
			boolean fresh = a == null;
			if (fresh) {
				a = new Arrow();
				ARROWS.put(ids[i], a);
			}
			double ox = a.x;
			double oy = a.y;
			double oz = a.z;
			int flags = Math.round(d[o + 6]);
			a.x = d[o];
			a.y = d[o + 1];
			a.z = d[o + 2];
			a.vx = d[o + 3];
			a.vy = d[o + 4];
			a.vz = d[o + 5];
			a.focus = flags & 3;
			a.kind = (flags >> 2) & 7;
			a.steering = (flags & 32) != 0;
			a.arrivedS = now;
			double speed = Math.sqrt(a.vx * a.vx + a.vy * a.vy + a.vz * a.vz);
			a.p = UryuArrowLook.drawPower(speed, a.focus, a.kind);
			a.trail.addFirst(new double[] {a.x, a.y, a.z});
			int keep = UryuArrowLook.trailPoints(a.kind);
			while (a.trail.size() > keep) {
				a.trail.removeLast();
			}
			if (fresh) {
				launch(a);
			} else {
				flakeSparks(a, ox, oy, oz);
			}
		}
	}

	/** One flake spark every 2.0 blocks of the path (1.2 for the Z arrow), only for Focus 2 and 3 and the Z arrow. */
	private static void flakeSparks(Arrow a, double ox, double oy, double oz) {
		double spacing = UryuArrowLook.flakeSpacing(a.kind, a.focus);
		if (spacing <= 0) {
			return;
		}
		double step = Math.sqrt((a.x - ox) * (a.x - ox) + (a.y - oy) * (a.y - oy) + (a.z - oz) * (a.z - oz));
		double[] f = UryuArrowLook.sparkFractions(a.carry, step, spacing);
		Random r = new Random(Double.doubleToLongBits(a.x * 3 + a.z));
		for (int i = 0; i < f.length - 1; i++) {
			UryuArrowFx.spark(ox + (a.x - ox) * f[i], oy + (a.y - oy) * f[i], oz + (a.z - oz) * f[i], r);
		}
		a.carry = f[f.length - 1];
	}

	/** The first sighting of an arrow: who shot it and the muzzle. */
	private static void launch(Arrow a) {
		Vec3d v = new Vec3d(a.vx, a.vy, a.vz);
		if (v.lengthSquared() < 1.0E-6 || !UryuArrowLook.hasMuzzle(a.kind)) {
			return;
		}
		PlayerEntity shooter = UryuArrowFx.shooterNear(a.x, a.y, a.z);
		a.own = shooter != null && shooter == MinecraftClient.getInstance().player;
		UryuArrowFx.muzzle(a.x, a.y, a.z, v.normalize(), a.kind, a.p, shooter, a.own);
	}

	private static void tick(MinecraftClient mc) {
		ClientWorld w = mc.world;
		if (w == null) {
			ARROWS.clear();
			ENDED.clear();
			return;
		}
		double now = FxClock.now;
		ENDED.removeIf(e -> now - e.ns() > 0.8);
		if (ARROWS.isEmpty()) {
			return;
		}
		// stale arrows (no packet for a second: the sender left range or the connection stalled) are dropped
		ARROWS.values().removeIf(a -> now - a.arrivedS > 1.0);
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		for (Arrow a : ARROWS.values()) {
			a.ticks++;
			if (a.kind == UryuArrowLook.RAIN || a.ticks % UryuArrowLook.WHOOSH_TICKS != 0) {
				continue;
			}
			double dx = a.x - cam.x;
			double dy = a.y - cam.y;
			double dz = a.z - cam.z;
			if (dx * dx + dy * dy + dz * dz <= UryuArrowLook.WHOOSH_RANGE * UryuArrowLook.WHOOSH_RANGE) {
				FxSound.play(null, UryuArrowLook.WHOOSH_ID, a.x, a.y, a.z, UryuArrowLook.WHOOSH_PITCH, UryuArrowLook.WHOOSH_VOLUME);
			}
		}
	}

	/**
	 * Hit spark ({@code type} {@link UryuArrowsS2C#IMPACT_ENTITY} / {@link UryuArrowsS2C#IMPACT_BLOCK}, {@code kind} the arrow kind ordinal): the
	 * packet carries no Focus, so the arrow that just ended (or is still listed) within 4.5 blocks of the hit tells the level and the direction.
	 */
	public static void impact(double x, double y, double z, int type, int kind) {
		double bestD = UryuArrowLook.MATCH_DISTANCE * UryuArrowLook.MATCH_DISTANCE;
		int focus = 0;
		double vy = 0;
		boolean own = false;
		Ended hitEnded = null;
		for (Ended e : ENDED) {
			if (e.kind() != kind) {
				continue;
			}
			double d = sq(e.x() - x, e.y() - y, e.z() - z);
			if (d < bestD) {
				bestD = d;
				hitEnded = e;
			}
		}
		Integer liveId = null;
		for (Map.Entry<Integer, Arrow> en : ARROWS.entrySet()) {
			Arrow a = en.getValue();
			if (a.kind != kind) {
				continue;
			}
			double d = sq(a.x - x, a.y - y, a.z - z);
			if (d < bestD) {
				bestD = d;
				liveId = en.getKey();
				hitEnded = null;
			}
		}
		if (liveId != null) {
			Arrow a = ARROWS.get(liveId);
			focus = a.focus;
			vy = a.vy;
			own = a.own;
		} else if (hitEnded != null) {
			focus = hitEnded.focus();
			vy = hitEnded.vy();
			own = hitEnded.own();
			ENDED.remove(hitEnded);
		}
		int level = UryuArrowLook.hitLevel(kind, focus);
		boolean floor = type == UryuArrowsS2C.IMPACT_BLOCK && vy < -0.05 && UryuArrowLook.floorHit(y);
		UryuArrowFx.hit(x, y, z, type, level, floor, own);
	}

	private static double sq(double dx, double dy, double dz) {
		return dx * dx + dy * dy + dz * dz;
	}

	private static double[] headOf(Arrow a, double age) {
		return new double[] {a.x + a.vx * age, a.y + a.vy * age, a.z + a.vz * age};
	}

	private static void draw(WorldRenderContext ctx) {
		lastDrawn = 0;
		if (ARROWS.isEmpty()) {
			return;
		}
		if (!meshTried) {
			meshTried = true;
			mesh = FxMesh.load("uryu_fx", "uryu_arrow");
			layer = RenderLayer.getEntityCutoutNoCull(TEXTURE);
		}
		if (mesh == null) {
			return;
		}
		Vec3d cam = ctx.camera().getPos();
		double now = FxClock.now;
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
		float[] m = new float[9];
		int n = 0;
		for (Arrow a : ARROWS.values()) {
			double age = Math.min(1.5, (now - a.arrivedS) * 20.0);
			Vec3d v = new Vec3d(a.vx, a.vy, a.vz);
			if (v.lengthSquared() < 1.0E-6) {
				continue;
			}
			Vec3d d = v.normalize();
			// two axes perpendicular to the flight direction
			Vec3d ref = Math.abs(d.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
			Vec3d r = ref.crossProduct(d).normalize();
			Vec3d u = r.crossProduct(d); // (r, d, u) right handed: a proper rotation, so the winding and the normals stay right
			float s = (float) UryuArrowLook.meshScale(a.kind, a.p, a.focus);
			// local x -> r, local y (the shaft, tail at 0) -> d, local z -> u
			m[0] = (float) (r.x * s);
			m[1] = (float) (d.x * s);
			m[2] = (float) (u.x * s);
			m[3] = (float) (r.y * s);
			m[4] = (float) (d.y * s);
			m[5] = (float) (u.y * s);
			m[6] = (float) (r.z * s);
			m[7] = (float) (d.z * s);
			m[8] = (float) (u.z * s);
			double[] h = headOf(a, age);
			double back = 0.5 * s; // the 0.5 m mesh ends with its point on the simulated position
			mesh.write(buf, m, h[0] - d.x * back - cam.x, h[1] - d.y * back - cam.y, h[2] - d.z * back - cam.z, a.focus >= 3 ? 0xFFFFFFFF : 0xFFE8F4FF, FULL_LIGHT);
			n++;
		}
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			layer.draw(built);
		}
		lastDrawn = n;
	}

	private static void emitGlow(FxGlowBatch.Emitter e) {
		lastGlow = 0;
		if (ARROWS.isEmpty()) {
			return;
		}
		double now = FxClock.now;
		double sec = now;
		boolean rm = UryuAnchors.reduceMotion();
		for (Arrow a : ARROWS.values()) {
			double age = Math.min(1.5, (now - a.arrivedS) * 20.0);
			double speed = Math.sqrt(a.vx * a.vx + a.vy * a.vy + a.vz * a.vz);
			if (speed < 1.0E-6) {
				continue;
			}
			double[] h = headOf(a, age);
			double dx = a.vx / speed;
			double dy = a.vy / speed;
			double dz = a.vz / speed;
			boolean z = a.kind == UryuArrowLook.Z;
			boolean rain = a.kind == UryuArrowLook.RAIN;
			double pulse = rm ? 0.92 : 0.85 + 0.15 * Math.sin(sec * 17.0 + a.x);
			// (2) the glow strip along the shaft: Q3 with a white core, a pinpoint and a star at the head
			double len = UryuArrowLook.streakLength(a.p) * (rain ? 0.6 : 1.0);
			double[] tail = {h[0] - dx * len, h[1] - dy * len, h[2] - dz * len};
			double sc = (z ? 1.5 : 1.0) * (rain ? 0.7 : 1.0);
			glow(e, e.streak(GlowSprite.STREAK, h[0], h[1], h[2], tail[0], tail[1], tail[2], UryuArrowLook.STREAK_WIDTH * sc, UryuPalette.FQ3[0], UryuPalette.FQ3[1],
					UryuPalette.FQ3[2], UryuArrowLook.STREAK_ALPHA * pulse));
			glow(e, e.streak(GlowSprite.STREAK, h[0], h[1], h[2], tail[0] * 0.5 + h[0] * 0.5, tail[1] * 0.5 + h[1] * 0.5, tail[2] * 0.5 + h[2] * 0.5, UryuArrowLook.STREAK_WIDTH * 0.35 * sc,
					1f, 1f, 1f, 0.6 * pulse));
			glow(e, e.sprite(GlowSprite.GLOW_CORE, h[0], h[1], h[2], UryuArrowLook.HEAD_SIZE * (z ? 2.0 : 1.0), 0, UryuPalette.FQ1[0], UryuPalette.FQ1[1], UryuPalette.FQ1[2],
					UryuArrowLook.HEAD_ALPHA * pulse));
			if (!rain && (z || a.focus >= 2)) {
				glow(e, e.sprite(GlowSprite.STAR4, h[0], h[1], h[2], (z ? 0.8 : 0.45) * pulse, rm ? 0.0 : sec * 0.8, 1f, 1f, 1f, z || a.focus >= 3 ? 0.95 : 0.7));
			}
			// (3) the trail: the head, then the last positions; each piece dimmer and thinner
			double[] prev = h;
			int n = a.trail.size();
			int k = 0;
			for (double[] p : a.trail) {
				if (Math.abs(p[0] - prev[0]) + Math.abs(p[1] - prev[1]) + Math.abs(p[2] - prev[2]) > 1.0E-4) {
					double w = UryuArrowLook.trailPieceWidth(a.kind, k, n);
					double al = UryuArrowLook.trailAlpha(k, n);
					glow(e, e.streak(GlowSprite.FLECHETTE_TRAIL, prev[0], prev[1], prev[2], p[0], p[1], p[2], w, UryuPalette.FQ2[0], UryuPalette.FQ2[1], UryuPalette.FQ2[2], al));
					glow(e, e.streak(GlowSprite.STREAK, prev[0], prev[1], prev[2], p[0], p[1], p[2], w * 0.3, 1f, 1f, 1f, al * 0.8));
				}
				prev = p;
				k++;
			}
			// the ring of a homing arrow rides behind the head, one turn per second
			if (a.steering && !rain) {
				double b = UryuArrowLook.STEER_RING_BEHIND;
				if (e.plane(GlowSprite.RING_THIN, h[0] - dx * b, h[1] - dy * b, h[2] - dz * b, UryuArrowLook.steerRingSize(a.kind), dx, dy, dz, rm ? 0.0 : sec * Math.PI * 2.0,
						UryuPalette.FQ2[0], UryuPalette.FQ2[1], UryuPalette.FQ2[2], UryuArrowLook.STEER_RING_ALPHA * pulse)) {
					lastGlow++;
				}
			}
		}
	}

	private static void glow(FxGlowBatch.Emitter e, boolean drawn) {
		if (drawn) {
			lastGlow++;
		}
	}
}
