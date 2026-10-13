package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * PLACEHOLDER visuals of Uryu Ishida (design/URYU.md step 2, no art yet). The server simulates everything; this class only draws:
 * the Heilig Pfeil as short glowing line segments from the arrow list packet ({@link UryuArrowsS2C#ARROWS}, one per server tick, so a
 * segment lives about one tick), the Sprenger pentagon, its explosion, the Sternenstaub line and a few particles for the dash and the
 * Licht Regen circle. Everything goes through {@link FxShapes} and vanilla particles, nothing is cached between packets.
 */
public final class UryuFx {
	/** Effect ids 72 to 77 are Uryu's ability events (the server also sends the data packet for the ones that need exact positions). */
	public static boolean handles(int effectId) {
		return effectId >= 72 && effectId <= 77;
	}

	private UryuFx() {
	}

	/** Effect events: the dash trail (74) and the Licht Regen circle (75); the rest is drawn from the data packets. */
	public static void play(MinecraftClient client, EffectEventS2C e) {
		ClientWorld w = client.world;
		if (w == null) {
			return;
		}
		float[] pr = e.params();
		if (e.effectId() == 74) {
			double len = pr.length > 0 ? pr[0] : 8.0;
			Vec3d d = new Vec3d(e.dx(), e.dy(), e.dz());
			d = d.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : d.normalize();
			for (double t = 0.0; t <= len; t += 0.5) {
				w.addParticle(ParticleTypes.END_ROD, e.x() + d.x * t, e.y() + 1.0 + d.y * t, e.z() + d.z * t, 0, 0.01, 0);
			}
		} else if (e.effectId() == 75 && pr.length >= 4) {
			ring(w, pr[0], pr[1] + 0.1, pr[2], pr[3]);
		}
	}

	private static void ring(ClientWorld w, double x, double y, double z, double r) {
		int n = 28;
		for (int i = 0; i < n; i++) {
			double a = 2.0 * Math.PI * i / n;
			w.addParticle(ParticleTypes.END_ROD, x + Math.cos(a) * r, y, z + Math.sin(a) * r, 0, 0.02, 0);
		}
	}

	/** The data packets: arrows, explosion, line, pentagon. */
	public static void onArrows(UryuArrowsS2C p) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null) {
			return;
		}
		float[] d = p.data();
		switch (p.kind()) {
			case UryuArrowsS2C.ARROWS -> arrows(p.ids().length, d);
			case UryuArrowsS2C.BURST -> {
				if (d.length >= 5) {
					ring(w, d[0], d[1] + 0.2, d[2], d[3]);
					w.addParticle(ParticleTypes.EXPLOSION_EMITTER, d[0], d[1] + 0.5, d[2], 0, 0, 0);
				}
			}
			case UryuArrowsS2C.LINE -> {
				if (d.length >= 7) {
					line(new Vec3d(d[0], d[1], d[2]), new Vec3d(d[3], d[4], d[5]), d[6]);
				}
			}
			case UryuArrowsS2C.TRAP -> {
				if (d.length >= 5) {
					pentagon(d[0], d[1], d[2], d[3], d[4] / 20.0);
				}
			}
			default -> { }
		}
	}

	/** One bright segment per arrow, 1.4 blocks long behind the head. */
	private static void arrows(int n, float[] d) {
		int drawn = Math.min(n, 24);
		for (int i = 0; i < drawn; i++) {
			int o = i * UryuArrowsS2C.PER_ARROW;
			Vec3d v = new Vec3d(d[o + 3], d[o + 4], d[o + 5]);
			if (v.lengthSquared() < 1.0E-6) {
				continue;
			}
			Vec3d u = v.normalize();
			double len = 1.4;
			boolean full = d[o + 6] >= 3f;
			FxShapes.Strip s = FxShapes.strip(d[o] - u.x * len, d[o + 1] - u.y * len, d[o + 2] - u.z * len, u.x, u.y, u.z, 0).length(a -> len).width(a -> full ? 0.16 : 0.1);
			s.color(full ? "#FFFFFF" : "#A9D8F5").life(0.06);
		}
	}

	private static void line(Vec3d a, Vec3d b, double halfWidth) {
		Vec3d d = b.subtract(a);
		double len = d.length();
		if (len < 0.5) {
			return;
		}
		Vec3d u = d.multiply(1.0 / len);
		double life = 0.45;
		FxShapes.Strip glow = FxShapes.strip(a.x, a.y, a.z, u.x, u.y, u.z, 0).length(x -> len).width(x -> halfWidth * 2.0 * Math.max(0.0, 1.0 - x / life));
		glow.color("#7FD0FF").life(life).intensity(x -> 0.7 * Math.max(0.0, 1.0 - x / life));
		FxShapes.Strip core = FxShapes.strip(a.x, a.y, a.z, u.x, u.y, u.z, 0).length(x -> len).width(x -> 0.25);
		core.color("#FFFFFF").life(life).intensity(x -> Math.max(0.0, 1.0 - x / life));
	}

	/** Five Seele Schneider as upright strips and the five edges of the pentagon, until the explosion. */
	private static void pentagon(double cx, double cy, double cz, double r, double seconds) {
		double[] px = new double[5];
		double[] pz = new double[5];
		for (int i = 0; i < 5; i++) {
			double ang = -Math.PI / 2.0 + i * 2.0 * Math.PI / 5.0;
			px[i] = cx + Math.cos(ang) * r;
			pz[i] = cz + Math.sin(ang) * r;
			FxShapes.Strip blade = FxShapes.strip(px[i], cy, pz[i], 0, 1, 0, 0).length(x -> 1.2).width(x -> 0.12);
			blade.color("#A9D8F5").life(seconds);
		}
		for (int i = 0; i < 5; i++) {
			int j = (i + 2) % 5; // the star of the pentagram, which is what the five blades span
			double dx = px[j] - px[i];
			double dz = pz[j] - pz[i];
			double len = Math.hypot(dx, dz);
			FxShapes.Strip edge = FxShapes.strip(px[i], cy + 0.1, pz[i], dx / len, 0, dz / len, 0).length(x -> len).width(x -> 0.07);
			edge.color("#7FD0FF").life(seconds).intensity(x -> 0.35 + 0.65 * Math.min(1.0, x / seconds));
		}
	}
}
