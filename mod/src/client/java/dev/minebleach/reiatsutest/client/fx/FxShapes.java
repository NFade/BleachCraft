package dev.minebleach.reiatsutest.client.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;

/**
 * Additive oriented geometry Q:x (1.2): light pillars, frost walls and crossed-quad strips with the soft cells of the glow
 * atlas, drawn once per frame at {@code WorldRenderEvents.LAST} through the same linear filtered additive layer as the glow
 * sprites (so depth, grade and Fabulous handling are shared). Shapes read their size and brightness from functions of the age
 * so every client draws the same thing; HITSTOP of the owning timeline freezes them like the sprites.
 */
public final class FxShapes {
	private static final DoubleUnaryOperator ONE = a -> 1.0;

	public abstract static class Shape {
		double birth;
		double life = 3.0;
		int owner = -1;
		float r = 1;
		float g = 1;
		float b = 1;
		GlowSprite cell = GlowSprite.PILLAR;
		DoubleUnaryOperator intensity = ONE;
		boolean dead;

		public Shape color(String hex) {
			float[] c = FxMath.hex(hex);
			r = c[0];
			g = c[1];
			b = c[2];
			return this;
		}

		public Shape life(double seconds) {
			life = seconds;
			return this;
		}

		public Shape intensity(DoubleUnaryOperator f) {
			intensity = f;
			return this;
		}

		public Shape cell(GlowSprite c) {
			cell = c;
			return this;
		}

		public Shape owner(EffectTimeline t) {
			if (t != null) {
				owner = t.slot;
			}
			return this;
		}

		public Shape delay(double seconds) {
			birth = FxClock.now + seconds;
			return this;
		}

		double age(double now) {
			double held = owner >= 0 ? FxTimelines.held(owner) : 0;
			return now - held - birth;
		}

		abstract int emit(BufferBuilder buf, Vec3d cam, double age, double boost);
	}

	/** A vertical light pillar facing the camera (cylindrical billboard), the base at {@code y0(age)}. */
	public static final class Beam extends Shape {
		final double x;
		final double z;
		public DoubleUnaryOperator y0 = a -> 0;
		public DoubleUnaryOperator height = a -> 10;
		public DoubleUnaryOperator width = a -> 2;
		/** Brightness of the top relative to the base (1 = even, 0 = fades out upward). */
		public double topFade = 1.0;
		public double scrollSpeed = 2.0;
		public double ripple = 0.18;
		final int segments;

		Beam(double x, double z, double y0, int segments) {
			this.x = x;
			this.z = z;
			this.segments = segments;
			this.y0 = a -> y0;
		}

		public Beam y0(DoubleUnaryOperator f) {
			y0 = f;
			return this;
		}

		public Beam height(DoubleUnaryOperator f) {
			height = f;
			return this;
		}

		public Beam width(DoubleUnaryOperator f) {
			width = f;
			return this;
		}

		public Beam topFade(double f) {
			topFade = f;
			return this;
		}

		public Beam scroll(double speed, double rip) {
			scrollSpeed = speed;
			ripple = rip;
			return this;
		}

		@Override
		int emit(BufferBuilder buf, Vec3d cam, double age, double boost) {
			double inten = intensity.applyAsDouble(age) * boost;
			double h = height.applyAsDouble(age);
			double w = width.applyAsDouble(age);
			if (inten < 0.004 || h <= 0.02 || w <= 0.01) {
				return 0;
			}
			double base = y0.applyAsDouble(age);
			double rx = x - cam.x;
			double rz = z - cam.z;
			double hl = Math.hypot(rx, rz);
			double ux = hl < 1e-3 ? 1 : -rz / hl * w * 0.5;
			double uz = hl < 1e-3 ? 0 : rx / hl * w * 0.5;
			int n = 0;
			for (int k = 0; k < segments; k++) {
				double f0 = (double) k / segments;
				double f1 = (double) (k + 1) / segments;
				double yy0 = base + h * f0;
				double yy1 = base + h * f1;
				double c0 = shade(f0, yy0, age) * inten;
				double c1 = shade(f1, yy1, age) * inten;
				float v0 = cell.v1 - (cell.v1 - cell.v0) * (float) f0;
				float v1 = cell.v1 - (cell.v1 - cell.v0) * (float) f1;
				n += quad(buf, cam, x - ux, yy0, z - uz, x + ux, yy0, z + uz, x + ux, yy1, z + uz, x - ux, yy1, z - uz, cell.u0, v0, cell.u1, v1, c0, c1, r, g, b);
			}
			return n;
		}

		private double shade(double f, double y, double age) {
			double fall = 1.0 + (topFade - 1.0) * f;
			double rip = 1.0 - ripple + ripple * Math.sin(2 * Math.PI * (y * 0.22 - scrollSpeed * age * 0.5));
			return fall * rip;
		}
	}

	/** A band standing on a circle (frost wall, pillar of facets): {@code sides} cell quads centred on the circle. */
	public static final class Wall extends Shape {
		final double x;
		final double z;
		public DoubleUnaryOperator y0;
		public DoubleUnaryOperator radius = a -> 1;
		public DoubleUnaryOperator height = a -> 3;
		public double topFade = 1.0;
		public double scrollSpeed = 2.0;
		public double ripple = 0.2;
		public double overlap = 1.7;
		final int sides;
		final int segments;
		double spin;

		Wall(double x, double z, double y0, int sides, int segments) {
			this.x = x;
			this.z = z;
			this.sides = sides;
			this.segments = segments;
			this.y0 = a -> y0;
		}

		public Wall radius(DoubleUnaryOperator f) {
			radius = f;
			return this;
		}

		public Wall height(DoubleUnaryOperator f) {
			height = f;
			return this;
		}

		public Wall y0(DoubleUnaryOperator f) {
			y0 = f;
			return this;
		}

		public Wall topFade(double f) {
			topFade = f;
			return this;
		}

		public Wall scroll(double speed, double rip) {
			scrollSpeed = speed;
			ripple = rip;
			return this;
		}

		public Wall spin(double rad) {
			spin = rad;
			return this;
		}

		@Override
		int emit(BufferBuilder buf, Vec3d cam, double age, double boost) {
			double inten = intensity.applyAsDouble(age) * boost;
			double h = height.applyAsDouble(age);
			double rad = radius.applyAsDouble(age);
			if (inten < 0.004 || h <= 0.02 || rad <= 0.02) {
				return 0;
			}
			double base = y0.applyAsDouble(age);
			double half = Math.PI * rad / sides * overlap;
			int n = 0;
			for (int i = 0; i < sides; i++) {
				double a = spin * age + Math.PI * 2 * i / sides;
				double cx = x + Math.cos(a) * rad;
				double cz = z + Math.sin(a) * rad;
				double tx = -Math.sin(a) * half;
				double tz = Math.cos(a) * half;
				// facets facing the camera brighten a little (the front of a cylinder is what the eye sees)
				double face = 0.75 + 0.25 * Math.abs(Math.cos(a - Math.atan2(cam.z - z, cam.x - x)));
				for (int k = 0; k < segments; k++) {
					double f0 = (double) k / segments;
					double f1 = (double) (k + 1) / segments;
					double y0a = base + h * f0;
					double y1a = base + h * f1;
					double c0 = shade(f0, y0a, age, i) * inten * face;
					double c1 = shade(f1, y1a, age, i) * inten * face;
					float v0 = cell.v1 - (cell.v1 - cell.v0) * (float) f0;
					float v1 = cell.v1 - (cell.v1 - cell.v0) * (float) f1;
					n += quad(buf, cam, cx - tx, y0a, cz - tz, cx + tx, y0a, cz + tz, cx + tx, y1a, cz + tz, cx - tx, y1a, cz - tz, cell.u0, v0, cell.u1, v1, c0, c1, r, g, b);
				}
			}
			return n;
		}

		private double shade(double f, double y, double age, int side) {
			double fall = 1.0 + (topFade - 1.0) * f;
			double rip = 1.0 - ripple + ripple * Math.sin(2 * Math.PI * (y * 0.3 - scrollSpeed * age * 0.5) + side * 1.3);
			return fall * rip;
		}
	}

	/** Two crossed quads along an axis from a point (a blade of light): {@code LINE} cell, length and width of the age. */
	public static final class Strip extends Shape {
		final double x;
		final double y;
		final double z;
		final double dx;
		final double dy;
		final double dz;
		public DoubleUnaryOperator length = a -> 1;
		public DoubleUnaryOperator width = a -> 0.3;

		Strip(double x, double y, double z, double dx, double dy, double dz) {
			double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
			this.x = x;
			this.y = y;
			this.z = z;
			this.dx = dx / l;
			this.dy = dy / l;
			this.dz = dz / l;
			this.cell = GlowSprite.LINE;
		}

		public Strip length(DoubleUnaryOperator f) {
			length = f;
			return this;
		}

		public Strip width(DoubleUnaryOperator f) {
			width = f;
			return this;
		}

		@Override
		int emit(BufferBuilder buf, Vec3d cam, double age, double boost) {
			double inten = intensity.applyAsDouble(age) * boost;
			double len = length.applyAsDouble(age);
			double w = width.applyAsDouble(age) * 0.5;
			if (inten < 0.004 || len <= 0.01 || w <= 0.001) {
				return 0;
			}
			// two perpendicular axes to the strip direction
			double ax = dy * 0 - dz * 1;
			double ay = dz * 0 - dx * 0;
			double az = dx * 1 - dy * 0;
			// axis a = d x up (falls back to x when d is vertical)
			double al = Math.sqrt(ax * ax + ay * ay + az * az);
			if (al < 1e-4) {
				ax = 1;
				ay = 0;
				az = 0;
				al = 1;
			}
			ax /= al;
			ay /= al;
			az /= al;
			// axis b = d x a
			double bx = dy * az - dz * ay;
			double by = dz * ax - dx * az;
			double bz = dx * ay - dy * ax;
			double ex = x + dx * len;
			double ey = y + dy * len;
			double ez = z + dz * len;
			int n = 0;
			n += quad(buf, cam, x - ax * w, y - ay * w, z - az * w, ex - ax * w, ey - ay * w, ez - az * w, ex + ax * w, ey + ay * w, ez + az * w, x + ax * w, y + ay * w, z + az * w,
					cell.u0, cell.v1, cell.u1, cell.v0, inten, inten, r, g, b, true);
			n += quad(buf, cam, x - bx * w, y - by * w, z - bz * w, ex - bx * w, ey - by * w, ez - bz * w, ex + bx * w, ey + by * w, ez + bz * w, x + bx * w, y + by * w, z + bz * w,
					cell.u0, cell.v1, cell.u1, cell.v0, inten, inten, r, g, b, true);
			return n;
		}
	}

	private static final List<Shape> SHAPES = new ArrayList<>();
	public static int lastDrawn;

	private FxShapes() {
	}

	private static <T extends Shape> T add(T s, double delay) {
		s.birth = FxClock.now + delay;
		while (SHAPES.size() >= 64) {
			SHAPES.remove(0);
		}
		SHAPES.add(s);
		return s;
	}

	public static Beam beam(double x, double z, double y0, double delay) {
		return add(new Beam(x, z, y0, 8), delay);
	}

	public static Wall wall(double x, double z, double y0, int sides, double delay) {
		return add(new Wall(x, z, y0, sides, 6), delay);
	}

	public static Strip strip(double x, double y, double z, double dx, double dy, double dz, double delay) {
		return add(new Strip(x, y, z, dx, dy, dz), delay);
	}

	public static boolean active() {
		return !SHAPES.isEmpty();
	}

	public static void clear() {
		SHAPES.clear();
	}

	/** WorldRenderEvents.LAST, after the glow sprites. */
	public static void draw(WorldRenderContext ctx) {
		lastDrawn = 0;
		if (SHAPES.isEmpty()) {
			return;
		}
		double now = FxClock.now;
		SHAPES.removeIf(s -> s.age(now) > s.life);
		if (SHAPES.isEmpty()) {
			return;
		}
		Vec3d cam = ctx.camera().getPos();
		double boost = FxConfig.glowIntensity * (ScreenFx.gradeStrong() ? 1.3 : 1.0);
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		int n = 0;
		for (Shape s : SHAPES) {
			double age = s.age(now);
			if (age < 0) {
				continue;
			}
			n += s.emit(buf, cam, age, boost);
		}
		lastDrawn = n / 4;
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			FxGlowBatch.layer().draw(built);
		}
	}

	// ------------------------------------------------------------------------------------------------ quad writer

	private static int quad(BufferBuilder buf, Vec3d cam, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double x3,
			double y3, double z3, float u0, float v0, float u1, float v1, double cBottom, double cTop, float r, float g, float b) {
		return quad(buf, cam, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, v0, u1, v1, cBottom, cTop, r, g, b, false);
	}

	/**
	 * One quad, corners 0 and 1 at the bottom (or the start), 2 and 3 at the top (or the end). Vertical form: u0..u1 across, v0 at the
	 * bottom edge, v1 at the top. Axis form ({@code along}): corners 0 -> 1 run along the axis, u runs along it, v across.
	 */
	private static int quad(BufferBuilder buf, Vec3d cam, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double x3,
			double y3, double z3, float u0, float v0, float u1, float v1, double cBottom, double cTop, float r, float g, float b, boolean along) {
		float cb0 = (float) Math.min(1.0, cBottom);
		float ct0 = (float) Math.min(1.0, cTop);
		if (along) {
			// (x0,x3) start edge, (x1,x2) end edge: u from u0 (start) to u1 (end), v from v1 (side 0/1... ) to v0
			buf.vertex((float) (x0 - cam.x), (float) (y0 - cam.y), (float) (z0 - cam.z)).texture(u0, v0).color(r * cb0, g * cb0, b * cb0, 1f);
			buf.vertex((float) (x1 - cam.x), (float) (y1 - cam.y), (float) (z1 - cam.z)).texture(u1, v0).color(r * ct0, g * ct0, b * ct0, 1f);
			buf.vertex((float) (x2 - cam.x), (float) (y2 - cam.y), (float) (z2 - cam.z)).texture(u1, v1).color(r * ct0, g * ct0, b * ct0, 1f);
			buf.vertex((float) (x3 - cam.x), (float) (y3 - cam.y), (float) (z3 - cam.z)).texture(u0, v1).color(r * cb0, g * cb0, b * cb0, 1f);
			return 4;
		}
		buf.vertex((float) (x0 - cam.x), (float) (y0 - cam.y), (float) (z0 - cam.z)).texture(u0, v0).color(r * cb0, g * cb0, b * cb0, 1f);
		buf.vertex((float) (x1 - cam.x), (float) (y1 - cam.y), (float) (z1 - cam.z)).texture(u1, v0).color(r * cb0, g * cb0, b * cb0, 1f);
		buf.vertex((float) (x2 - cam.x), (float) (y2 - cam.y), (float) (z2 - cam.z)).texture(u1, v1).color(r * ct0, g * ct0, b * ct0, 1f);
		buf.vertex((float) (x3 - cam.x), (float) (y3 - cam.y), (float) (z3 - cam.z)).texture(u0, v1).color(r * ct0, g * ct0, b * ct0, 1f);
		return 4;
	}
}
