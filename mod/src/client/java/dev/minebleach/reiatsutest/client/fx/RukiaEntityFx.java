package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.net.EntityFxS2C;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Entity bound visuals of the Rukia abilities (5.3, 9): {@code entity_fx} ENCASED and FROZEN put an ice shell on the entity
 * (ends at the server tick the packet names, mapped through {@link FxServerClock}), SLOWED puffs frost around it, and HIT (the
 * server's real damage, S5) shatters an ENCASED shell into shard meshes and throws sparks. Shells emit glints (and frost for
 * FROZEN) from the client tick.
 */
public final class RukiaEntityFx {
	/** Statistics for the harness. */
	public static int shellsMade;
	public static int hitsSeen;
	public static int shellsBroken;
	private static final Map<Integer, Double> LAST_SLOW = new HashMap<>();
	private static final Random RNG = new Random(0x51CE);
	private static int tickCounter;

	private RukiaEntityFx() {
	}

	public static void onEntityFx(MinecraftClient client, EntityFxS2C fx) {
		ReiatsuTest.LOGGER.info("[fx] entity_fx kind={} until={} entities={}", fx.kind(), fx.untilTick(), fx.entityIds().length);
		ClientWorld w = client.world;
		if (w == null) {
			return;
		}
		Vec3d cam = client.gameRenderer.getCamera().getPos();
		for (int id : fx.entityIds()) {
			Entity en = w.getEntityById(id);
			if (en == null) {
				continue;
			}
			double dist = cam.distanceTo(en.getPos());
			double lod = FxMath.lod(dist);
			Vec3d c = en.getPos().add(0, en.getHeight() * 0.5, 0);
			switch (fx.kind()) {
				case EntityFxS2C.ENCASED, EntityFxS2C.FROZEN -> {
					if (dist > 64) {
						continue;
					}
					boolean enc = fx.kind() == EntityFxS2C.ENCASED;
					FxMeshPass.shell(id, enc, FxServerClock.fxTimeOf(fx.untilTick(), enc ? 1.5 : 4.0));
					shellsMade++;
					FxSound.play(null, "block.amethyst_cluster.place", c.x, c.y, c.z, enc ? 1.4 : 1.2, 0.5);
					puff(en, lod, enc ? 10 : 8, "#EAF8FF");
				}
				case EntityFxS2C.SLOWED -> {
					Double last = LAST_SLOW.get(id);
					if (last == null || FxClock.now - last > 0.5) {
						LAST_SLOW.put(id, FxClock.now);
						puff(en, lod, 6, "#DFF3FF");
					}
				}
				case EntityFxS2C.HIT -> hit(en, lod, c);
				default -> { }
			}
		}
	}

	/** Frost puffing off the base of the entity. */
	private static void puff(Entity en, double lod, int n, String color) {
		Vec3d p = en.getPos();
		double w = Math.max(0.3, en.getWidth());
		for (int i = 0; i < n; i++) {
			double a = RNG.nextDouble() * Math.PI * 2;
			double r = w * (0.4 + 0.6 * RNG.nextDouble());
			FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(p.x + Math.cos(a) * r, p.y + 0.1 + RNG.nextDouble() * 0.4, p.z + Math.sin(a) * r)
					.vel(Math.cos(a) * 0.6, 0.8 + RNG.nextDouble(), Math.sin(a) * 0.6).drag(0.95).life(16 + RNG.nextInt(6)).size(0.36).color(color).colorTo("#BFE4FF").seed(i).fade(0.4).lod(lod)
					.spawn();
		}
		for (int i = 0; i < 3; i++) {
			FxGlowBatch.sprite(GlowSprite.STAR4).at(p.x + (RNG.nextDouble() - 0.5) * w, p.y + RNG.nextDouble() * en.getHeight(), p.z + (RNG.nextDouble() - 0.5) * w).lifeTicks(9)
					.size(0.4 + RNG.nextDouble() * 0.3).color("#FFFFFF").peak(0.9).twinkle().rot(RNG.nextDouble() * 6, 1).lod(lod).spawn();
		}
	}

	/** A real hit (S5): the shell of an ENCASED entity bursts into shards, sparks fly. */
	private static void hit(Entity en, double lod, Vec3d c) {
		hitsSeen++;
		for (FxMeshPass.Shell s : FxMeshPass.shells()) {
			if (s.entityId == en.getId() && s.encased) {
				if (FxMeshPass.breakShell(en.getId(), 10, 5.0) != null) {
					shellsBroken++;
					FxSound.play(null, "block.glass.break", c.x, c.y, c.z, 1.2, 0.6);
				}
				break;
			}
		}
		for (int i = 0; i < 6; i++) {
			double a = RNG.nextDouble() * Math.PI * 2;
			double s = 1.5 + RNG.nextDouble() * 3.0;
			FxGlowBatch.sprite(GlowSprite.STAR4).at(c.x, c.y, c.z).vel(Math.cos(a) * s, (RNG.nextDouble() - 0.2) * 2.5, Math.sin(a) * s).drag(0.9).lifeTicks(10)
					.size(0.4 + RNG.nextDouble() * 0.4).color("#EAF8FF").peak(0.95).twinkle().rot(RNG.nextDouble() * 6, 2).lod(lod).spawn();
		}
		for (int i = 0; i < 6; i++) {
			double a = RNG.nextDouble() * Math.PI * 2;
			double s = 2.0 + RNG.nextDouble() * 3.0;
			FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(c.x, c.y, c.z).vel(Math.cos(a) * s, (RNG.nextDouble() - 0.3) * 3, Math.sin(a) * s).drag(0.92).life(14).size(0.4)
					.color("#FFFFFF").colorTo("#9ED3F0").seed(i).fade(0.4).lod(lod).spawn();
		}
	}

	/** END_CLIENT_TICK: glints on every shell (2 per second), frost off FROZEN shells (20 per second). */
	public static void tick(MinecraftClient mc) {
		tickCounter++;
		if (mc.world == null || FxClock.frozen || mc.isPaused()) {
			return;
		}
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		for (FxMeshPass.Shell s : FxMeshPass.shells()) {
			Vec3d c = s.center();
			if (cam.squaredDistanceTo(c) > 48 * 48) {
				continue;
			}
			double h = s.height();
			if (tickCounter % 10 == 0) {
				FxGlowBatch.sprite(GlowSprite.STAR6).at(c.x + (RNG.nextDouble() - 0.5) * 0.7, c.y + (RNG.nextDouble() - 0.5) * h * 0.9, c.z + (RNG.nextDouble() - 0.5) * 0.7)
						.lifeTicks(10).size(0.45 + RNG.nextDouble() * 0.3).color("#FFFFFF").peak(0.85).twinkle().rot(RNG.nextDouble() * 6, 1).spawn();
			}
			if (!s.encased && tickCounter % 2 == 0) {
				FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(c.x + (RNG.nextDouble() - 0.5) * 0.8, c.y + (RNG.nextDouble() - 0.5) * h, c.z + (RNG.nextDouble() - 0.5) * 0.8)
						.vel(0, 0.4, 0).drag(0.96).life(14).size(0.32).color("#EAF8FF").colorTo("#BFE4FF").seed(tickCounter).fade(0.5).spawn();
			}
		}
	}
}
