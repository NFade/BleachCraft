package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Seele Schneider daggers in the world (the exported {@code uryu_seele} mesh, 0.3 m, hilt at its origin, +Y along the blade in OBJ axes):
 * the five blades of the Sprenger pentagon and the Reishi blade of the Seele Schneider combo. Each instance has a position of its
 * HILT, a blade direction, a scale, a drop in animation (it falls from {@code dropHeight} above to its place over {@code fall} seconds, easing
 * in) and a life; fades out by shrinking in the last 0.15 s. Drawn at full brightness. FX clock time ({@link FxClock#now}).
 */
public final class UryuSeeleRenderer {
	private static final Identifier TEXTURE = ReiatsuTest.id("textures/item/uryu_diffuse.png");
	private static final int FULL_LIGHT = 0xF000F0;

	/** One dagger. Fields are plain on purpose: the owners (Sprenger, the combo) update them. */
	public static final class Seele {
		public double x;
		public double y;
		public double z;
		public double dx;
		public double dy = -1;
		public double dz;
		public float scale = 3.0f;
		/** Seconds of the FX clock at which it appears, how long it falls, from how high, and how long it lives in total. */
		public double born;
		public double fall = 0.0;
		public double dropHeight = 0.0;
		public double life = 2.0;
		public double spinDegPerSec;
		/** Tint, ARGB. */
		public int color = 0xFFFFFFFF;
		/** Owner tag (a Sprenger id or a combo id) so a whole group can be removed at once. */
		public int group;
		boolean dead;
	}

	private static final List<Seele> LIVE = new ArrayList<>();
	private static FxMesh mesh;
	private static boolean meshTried;
	private static RenderLayer layer;
	private static boolean inited;
	public static int lastDrawn;

	private UryuSeeleRenderer() {
	}

	public static void init() {
		if (inited) {
			return;
		}
		inited = true;
		WorldRenderEvents.AFTER_ENTITIES.register(UryuSeeleRenderer::draw);
	}

	public static Seele add(Seele s) {
		init();
		while (LIVE.size() >= 64) {
			LIVE.remove(0);
		}
		LIVE.add(s);
		return s;
	}

	/** Removes every dagger of a group (the pentagon exploded). */
	public static void removeGroup(int group) {
		LIVE.removeIf(s -> s.group == group);
	}

	public static void clear() {
		LIVE.clear();
	}

	public static int live() {
		return LIVE.size();
	}

	private static void draw(WorldRenderContext ctx) {
		lastDrawn = 0;
		if (LIVE.isEmpty()) {
			return;
		}
		if (!meshTried) {
			meshTried = true;
			mesh = FxMesh.load("uryu", "uryu_seele");
			layer = RenderLayer.getEntityCutoutNoCull(TEXTURE);
		}
		if (mesh == null) {
			return;
		}
		double now = FxClock.now;
		LIVE.removeIf(s -> now - s.born > s.life);
		if (LIVE.isEmpty()) {
			return;
		}
		Vec3d cam = ctx.camera().getPos();
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
		float[] m = new float[9];
		int n = 0;
		for (Seele s : LIVE) {
			double age = now - s.born;
			if (age < 0) {
				continue;
			}
			Vec3d d = new Vec3d(s.dx, s.dy, s.dz);
			if (d.lengthSquared() < 1.0E-9) {
				continue;
			}
			d = d.normalize();
			Vec3d ref = Math.abs(d.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
			Vec3d r = ref.crossProduct(d).normalize();
			Vec3d u = r.crossProduct(d);
			double roll = Math.toRadians(s.spinDegPerSec * age);
			if (roll != 0.0) {
				Vec3d r2 = r.multiply(Math.cos(roll)).add(u.multiply(Math.sin(roll)));
				u = u.multiply(Math.cos(roll)).subtract(r.multiply(Math.sin(roll)));
				r = r2;
			}
			double k = 1.0;
			double fadeStart = s.life - 0.15;
			if (age > fadeStart) {
				k = Math.max(0.0, 1.0 - (age - fadeStart) / 0.15);
			}
			float sc = (float) (s.scale * k);
			if (sc < 0.01f) {
				continue;
			}
			m[0] = (float) (r.x * sc);
			m[1] = (float) (d.x * sc);
			m[2] = (float) (u.x * sc);
			m[3] = (float) (r.y * sc);
			m[4] = (float) (d.y * sc);
			m[5] = (float) (u.y * sc);
			m[6] = (float) (r.z * sc);
			m[7] = (float) (d.z * sc);
			m[8] = (float) (u.z * sc);
			double lift = 0.0;
			if (s.fall > 0 && age < s.fall) {
				double f = age / s.fall;
				lift = s.dropHeight * (1.0 - f * f); // falls with an easing in
			}
			mesh.write(buf, m, s.x - cam.x, s.y + lift - cam.y, s.z - cam.z, s.color, FULL_LIGHT);
			n++;
		}
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			layer.draw(built);
		}
		lastDrawn = n;
	}
}
