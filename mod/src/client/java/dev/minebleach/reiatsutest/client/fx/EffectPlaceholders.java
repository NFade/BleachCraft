package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.net.EntityFxS2C;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * Phase 4 placeholder for effect events: logs them and spawns a few vanilla particles so the server events are visible.
 * Real visuals (EffectTimeline, anchor entities, shaders) are phase 6.
 */
public final class EffectPlaceholders {
	private EffectPlaceholders() {
	}

	public static void play(MinecraftClient client, EffectEventS2C e) {
		StringBuilder params = new StringBuilder();
		for (float f : e.params()) {
			params.append(String.format(java.util.Locale.ROOT, " %.1f", f));
		}
		ReiatsuTest.LOGGER.info("[fx] effect_event id={} caster={} seed={} pos=({}, {}, {}) dir=({}, {}, {}) startTick={} params[{}]{}",
				e.effectId(), e.casterId(), e.seed(), fmt(e.x()), fmt(e.y()), fmt(e.z()), fmt(e.dx()), fmt(e.dy()), fmt(e.dz()),
				e.startTick(), e.params().length, params);
		ClientWorld world = client.world;
		if (world == null) {
			return;
		}
		Random rnd = new Random(e.seed());
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		Vec3d aim = e.params().length >= 3 ? new Vec3d(e.params()[0], e.params()[1], e.params()[2]) : pos;
		float size = e.params().length >= 4 ? e.params()[3] : 0f;
		switch (e.effectId()) {
			case 1 -> ring(world, ParticleTypes.SNOWFLAKE, pos, 1.2, 24, 0.12, rnd);
			case 2 -> {
				ring(world, ParticleTypes.SNOWFLAKE, pos, 2.0, 40, 0.2, rnd);
				ring(world, ParticleTypes.END_ROD, pos, 1.0, 16, 0.1, rnd);
			}
			case 3 -> ring(world, ParticleTypes.CHERRY_LEAVES, pos.add(0, 1, 0), 1.2, 24, 0.1, rnd);
			case 4 -> {
				ring(world, ParticleTypes.CHERRY_LEAVES, pos.add(0, 1, 0), 2.5, 48, 0.2, rnd);
				ring(world, ParticleTypes.END_ROD, pos, 1.0, 16, 0.1, rnd);
			}
			case 10, 11 -> ring(world, ParticleTypes.CLOUD, pos.add(0, 1, 0), 0.8, 14, 0.05, rnd);
			case 20 -> ring(world, ParticleTypes.SNOWFLAKE, pos.add(0, 0.2, 0), Math.max(size, 4f), 48, 0.02, rnd);
			case 21 -> line(world, ParticleTypes.SNOWFLAKE, pos.add(0, 0.3, 0), new Vec3d(dir.x, 0, dir.z).normalize(), Math.max(size, 12f), 36, rnd);
			case 22 -> line(world, ParticleTypes.END_ROD, pos.add(0, 1.4, 0), aim.subtract(pos.add(0, 1.4, 0)).normalize(), 8, 20, rnd);
			case 23 -> ring(world, ParticleTypes.SNOWFLAKE, pos.add(0, 0.5, 0), Math.max(size, 10f), 90, 0.02, rnd);
			case 30 -> ring(world, ParticleTypes.CHERRY_LEAVES, aim, 1.5, 16, 0.05, rnd);
			case 31 -> ring(world, ParticleTypes.CHERRY_LEAVES, pos.add(0, 1, 0), 2.5, 40, 0.02, rnd);
			case 32 -> {
				ring(world, ParticleTypes.CHERRY_LEAVES, pos.add(0, 1, 0), 5, 40, 0.05, rnd);
				ring(world, ParticleTypes.CHERRY_LEAVES, aim, 5, 60, 0.15, rnd);
			}
			case 33 -> {
				line(world, ParticleTypes.END_ROD, e.casterId() >= 0 ? pos.add(0, 1.6, 0) : pos, aim.subtract(pos.add(0, 1.6, 0)).normalize(), Math.max(size, 10f), 40, rnd);
				ring(world, ParticleTypes.END_ROD, aim, 5, 60, 0.2, rnd);
			}
			default -> ring(world, ParticleTypes.CLOUD, pos.add(0, 1, 0), 1, 10, 0.05, rnd);
		}
	}

	public static void entities(MinecraftClient client, EntityFxS2C fx) {
		ClientWorld world = client.world;
		ReiatsuTest.LOGGER.info("[fx] entity_fx kind={} until={} entities={}", fx.kind(), fx.untilTick(), fx.entityIds().length);
		if (world == null) {
			return;
		}
		Random rnd = new Random(fx.untilTick());
		for (int id : fx.entityIds()) {
			Entity en = world.getEntityById(id);
			if (en != null) {
				ring(world, ParticleTypes.SNOWFLAKE, en.getPos().add(0, en.getHeight() * 0.5, 0), en.getWidth(), 12, 0.02, rnd);
			}
		}
	}

	private static void ring(ClientWorld w, ParticleEffect type, Vec3d c, double radius, int n, double up, Random rnd) {
		for (int i = 0; i < n; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double r = radius * (0.6 + 0.4 * rnd.nextDouble());
			w.addParticle(type, c.x + Math.cos(a) * r, c.y + rnd.nextDouble() * 0.4, c.z + Math.sin(a) * r, 0, up, 0);
		}
	}

	private static void line(ClientWorld w, ParticleEffect type, Vec3d from, Vec3d dir, double length, int n, Random rnd) {
		if (Double.isNaN(dir.x)) {
			return;
		}
		for (int i = 0; i < n; i++) {
			double d = length * i / Math.max(1, n - 1);
			w.addParticle(type, from.x + dir.x * d + (rnd.nextDouble() - 0.5) * 0.3, from.y + dir.y * d + (rnd.nextDouble() - 0.5) * 0.3,
					from.z + dir.z * d + (rnd.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
		}
	}

	private static String fmt(double v) {
		return String.format(java.util.Locale.ROOT, "%.2f", v);
	}
}
