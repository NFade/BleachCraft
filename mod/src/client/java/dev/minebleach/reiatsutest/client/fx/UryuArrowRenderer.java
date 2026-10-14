package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.uryu.ArrowSim;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * The flying Heilig Pfeil of every Uryu on this client (design/URYU_PLAN.md U4): the server simulates, {@link UryuArrowsS2C#ARROWS}
 * lists the live arrows near the receiver once per server tick (position and velocity per tick, flags = focus + 4 * kind + 32 * steering).
 * Each arrow is drawn with the exported {@code uryu_arrow} mesh, oriented along its velocity, extrapolated by the time since its packet
 * (so it moves smoothly between the 20 Hz packets), plus a glowing head, a trail along the last positions (the path bends with the
 * homing), a launch flash when an id appears and END_ROD sparks. The model is drawn at full brightness (the emissive atlas of the
 * model is mostly white-blue reishi).
 */
public final class UryuArrowRenderer {
	private static final Identifier TEXTURE = ReiatsuTest.id("textures/item/uryu_diffuse.png");
	private static final int FULL_LIGHT = 0xF000F0;
	private static final int TRAIL_POINTS = 8;
	private static final float[] PALE = {0.66f, 0.85f, 0.96f};
	private static final float[] GLOW = {0.50f, 0.82f, 1.0f};

	private static final class Arrow {
		double x;
		double y;
		double z;
		double vx;
		double vy;
		double vz;
		long arrivedNs;
		int focus;
		int kind;
		boolean steering;
		final ArrayDeque<double[]> trail = new ArrayDeque<>();
	}

	private static final Map<Integer, Arrow> ARROWS = new HashMap<>();
	private static FxMesh mesh;
	private static boolean meshTried;
	private static RenderLayer layer;
	private static boolean inited;
	/** Harness / debug: arrows drawn in the last frame. */
	public static int lastDrawn;

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
	}

	/** The arrow list packet: add and update the arrows in it, drop the ones that are gone. */
	public static void onList(int[] ids, float[] d) {
		init();
		long now = System.nanoTime();
		Iterator<Integer> it = ARROWS.keySet().iterator();
		java.util.Set<Integer> present = new java.util.HashSet<>();
		for (int id : ids) {
			present.add(id);
		}
		while (it.hasNext()) {
			if (!present.contains(it.next())) {
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
			a.arrivedNs = now;
			a.trail.addFirst(new double[] {a.x, a.y, a.z});
			while (a.trail.size() > TRAIL_POINTS) {
				a.trail.removeLast();
			}
			if (fresh) {
				launch(a);
			}
		}
	}

	private static boolean strong(Arrow a) {
		return a.kind == ArrowSim.Kind.FOCUS.ordinal();
	}

	/** The muzzle flash: a bright core, a ring of sparks along the flight direction, a soft sound. */
	private static void launch(Arrow a) {
		Vec3d v = new Vec3d(a.vx, a.vy, a.vz);
		if (v.lengthSquared() < 1.0E-6) {
			return;
		}
		Vec3d u = v.normalize();
		boolean z = strong(a);
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(a.x - u.x * 0.3, a.y - u.y * 0.3, a.z - u.z * 0.3).size(z ? 1.6 : 1.0, z ? 0.4 : 0.25).life(0.18).color(PALE)
				.peak(z ? 1.0 : 0.8).spawn();
		FxGlowBatch.sprite(GlowSprite.STAR4).at(a.x, a.y, a.z).size(z ? 1.4 : 0.9, 0.1).life(0.14).color("#FFFFFF").peak(0.9).rot(Math.random() * 3, 0).spawn();
		ClientWorld w = MinecraftClient.getInstance().world;
		if (w != null) {
			int n = z ? 8 : 4;
			for (int i = 0; i < n; i++) {
				double sx = (Math.random() - 0.5) * 0.25;
				double sy = (Math.random() - 0.5) * 0.25;
				double sz = (Math.random() - 0.5) * 0.25;
				w.addParticle(ParticleTypes.END_ROD, a.x, a.y, a.z, u.x * 0.25 + sx, u.y * 0.25 + sy, u.z * 0.25 + sz);
			}
		}
	}

	private static void tick(MinecraftClient mc) {
		ClientWorld w = mc.world;
		if (w == null) {
			ARROWS.clear();
			return;
		}
		if (ARROWS.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		// stale arrows (no packet for a second: the sender left range or the connection stalled) are dropped
		ARROWS.values().removeIf(a -> now - a.arrivedNs > 1_000_000_000L);
		for (Arrow a : ARROWS.values()) {
			double age = Math.min(1.5, (now - a.arrivedNs) / 50.0e6);
			double px = a.x + a.vx * age;
			double py = a.y + a.vy * age;
			double pz = a.z + a.vz * age;
			if (strong(a) || Math.random() < 0.5) {
				w.addParticle(ParticleTypes.END_ROD, px, py, pz, -a.vx * 0.03 + (Math.random() - 0.5) * 0.02, -a.vy * 0.03 + (Math.random() - 0.5) * 0.02,
						-a.vz * 0.03 + (Math.random() - 0.5) * 0.02);
			}
		}
	}

	/** Hit spark: a flash, a few sparks and a chime; {@code type} {@link UryuArrowsS2C#IMPACT_ENTITY} / {@link UryuArrowsS2C#IMPACT_BLOCK}. */
	public static void impact(double x, double y, double z, int type, int kind) {
		boolean z2 = kind == ArrowSim.Kind.FOCUS.ordinal();
		boolean entity = type == UryuArrowsS2C.IMPACT_ENTITY;
		FxGlowBatch.sprite(GlowSprite.STAR4).at(x, y, z).size(entity ? (z2 ? 2.0 : 1.4) : 0.8, 0.2).life(0.22).color("#FFFFFF").peak(1.0).rot(Math.random() * 3, 2.0).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(x, y, z).size(entity ? 2.2 : 1.1, 0.5).life(0.3).color(GLOW).peak(0.8).spawn();
		ClientWorld w = MinecraftClient.getInstance().world;
		if (w != null) {
			int n = entity ? 10 : 5;
			for (int i = 0; i < n; i++) {
				w.addParticle(ParticleTypes.END_ROD, x, y, z, (Math.random() - 0.5) * 0.35, (Math.random() - 0.3) * 0.3, (Math.random() - 0.5) * 0.35);
			}
		}
		FxSound.play(null, entity ? "block.amethyst_block.chime" : "block.amethyst_block.hit", x, y, z, entity ? 1.7 : 1.4, 0.7);
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
			mesh = FxMesh.load("uryu", "uryu_arrow");
			layer = RenderLayer.getEntityCutoutNoCull(TEXTURE);
		}
		if (mesh == null) {
			return;
		}
		Vec3d cam = ctx.camera().getPos();
		long now = System.nanoTime();
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
		float[] m = new float[9];
		int n = 0;
		for (Arrow a : ARROWS.values()) {
			double age = Math.min(1.5, (now - a.arrivedNs) / 50.0e6);
			Vec3d v = new Vec3d(a.vx, a.vy, a.vz);
			if (v.lengthSquared() < 1.0E-6) {
				continue;
			}
			Vec3d d = v.normalize();
			// two axes perpendicular to the flight direction
			Vec3d ref = Math.abs(d.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
			Vec3d r = ref.crossProduct(d).normalize();
			Vec3d u = r.crossProduct(d); // (r, d, u) right handed: a proper rotation, so the winding and the normals stay right
			float s = strong(a) ? 2.6f : 1.6f;
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
			double back = 0.35 * s; // the arrow's point ends a little ahead of the simulated position
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
		long now = System.nanoTime();
		for (Arrow a : ARROWS.values()) {
			double age = Math.min(1.5, (now - a.arrivedNs) / 50.0e6);
			double[] h = headOf(a, age);
			boolean z = strong(a);
			double speed = Math.sqrt(a.vx * a.vx + a.vy * a.vy + a.vz * a.vz);
			if (speed < 1.0E-6) {
				continue;
			}
			// head glow, pulsing a little; bigger for the Z arrow; white core for a fully focused one
			double pulse = 0.85 + 0.15 * Math.sin(now / 6.0e7 + a.x);
			e.sprite(GlowSprite.GLOW_SOFT, h[0], h[1], h[2], (z ? 1.7 : 0.95) * pulse, 0, GLOW[0], GLOW[1], GLOW[2], z ? 1.0 : 0.75);
			e.sprite(GlowSprite.STAR4, h[0], h[1], h[2], (z ? 0.9 : 0.5) * pulse, now / 2.0e8, 1f, 1f, 1f, z || a.focus >= 3 ? 1.0 : 0.7);
			// the trail: the head, then the last positions; each piece dimmer and thinner
			double[] prev = h;
			int k = 0;
			double width0 = z ? 0.55 : 0.32;
			for (double[] p : a.trail) {
				double f = 1.0 - (double) k / (TRAIL_POINTS + 1);
				if (Math.abs(p[0] - prev[0]) + Math.abs(p[1] - prev[1]) + Math.abs(p[2] - prev[2]) > 1.0E-4) {
					e.streak(GlowSprite.STREAK, prev[0], prev[1], prev[2], p[0], p[1], p[2], width0 * f, GLOW[0], GLOW[1], GLOW[2], (z ? 1.0 : 0.75) * f);
					e.streak(GlowSprite.STREAK, prev[0], prev[1], prev[2], p[0], p[1], p[2], width0 * 0.4 * f, 1f, 1f, 1f, 0.8 * f);
				}
				prev = p;
				k++;
			}
			if (a.steering) {
				e.sprite(GlowSprite.RING_THIN, h[0], h[1], h[2], 0.7 * pulse, 0, PALE[0], PALE[1], PALE[2], 0.35);
			}
		}
	}
}
