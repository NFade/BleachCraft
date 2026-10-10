package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Continuous reiatsu aura of every released player in view (VFX_STORYBOARD 2.2): a per-player spawner on a 5 tick cadence,
 * rate x effect quality x distance LOD, at most 120 particles per player and 400 per client. Rukia shikai: pale wisps
 * rising off the body plus star glints along the blade; Byakuya shikai: lilac wisps; Byakuya bankai: slower, longer lived
 * wisps and a heartbeat; Rukia bankai: a placeholder (frost wisps and drifting snowflakes) until the full design of 5.2.
 * The orbiting petals of the Byakuya bankai belong to the swarm and come with it.
 */
public final class AuraFx {
	private static final class PlayerAura {
		ZanpakutoState state = ZanpakutoState.SEALED;
		CharacterId character = CharacterId.NONE;
		CharacterId lastReleased = CharacterId.NONE;
		int nextSpawnTick;
		int nextSoundTick;
		int nextRingTick;
		final ArrayDeque<Integer> live = new ArrayDeque<>();
		final ArrayDeque<FxParticles.FxParticle> wisps = new ArrayDeque<>();
	}

	private static final Map<Integer, PlayerAura> PLAYERS = new HashMap<>();
	private static int tickCounter;
	private static final Random RNG = new Random(0xA17A);
	/** Statistics for the harness. */
	public static int spawned;
	public static int peakLive;

	private AuraFx() {
	}

	/** The character a player last had released (the seal event arrives after the state is already SEALED). */
	public static CharacterId lastCharacter(int entityId) {
		PlayerAura p = PLAYERS.get(entityId);
		return p == null ? CharacterId.NONE : p.lastReleased;
	}

	public static int liveFor(int entityId) {
		PlayerAura p = PLAYERS.get(entityId);
		return p == null ? 0 : p.live.size();
	}

	/** The seal: the wisps of that player fade out within 8 ticks instead of drifting on for 2 s. */
	public static void dissolve(int entityId) {
		PlayerAura a = PLAYERS.get(entityId);
		if (a == null) {
			return;
		}
		for (FxParticles.FxParticle p : a.wisps) {
			p.fadeOutWithin(8);
		}
		a.wisps.clear();
	}

	public static void clear() {
		PLAYERS.clear();
	}

	public static void tick(MinecraftClient mc) {
		tickCounter++;
		if (mc.world == null || mc.player == null || mc.isPaused() || FxClock.frozen) {
			return;
		}
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		int total = 0;
		for (PlayerAura a : PLAYERS.values()) {
			total += a.live.size();
		}
		Iterator<Map.Entry<Integer, PlayerAura>> it = PLAYERS.entrySet().iterator();
		while (it.hasNext()) {
			var en = it.next();
			if (mc.world.getEntityById(en.getKey()) == null) {
				it.remove();
			}
		}
		for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
			ZanpakutoData z = pl.getAttached(ModAttachments.ZANPAKUTO);
			if (z == null) {
				continue;
			}
			PlayerAura a = PLAYERS.computeIfAbsent(pl.getId(), k -> new PlayerAura());
			a.state = z.zanpakutoState();
			a.character = z.characterId();
			boolean released = a.state == ZanpakutoState.SHIKAI || a.state == ZanpakutoState.BANKAI;
			if (released) {
				a.lastReleased = a.character;
			}
			while (!a.live.isEmpty() && a.live.peekFirst() <= tickCounter) {
				total--;
				a.live.pollFirst();
			}
			if (!released || !pl.isAlive() || pl.isSpectator()) {
				continue;
			}
			double d = cam.distanceTo(pl.getPos());
			double lod = FxMath.lod(d);
			if (lod <= 0) {
				continue;
			}
			if (tickCounter >= a.nextSoundTick) {
				sound(a, pl.getPos());
			}
			if (tickCounter < a.nextSpawnTick) {
				continue;
			}
			a.nextSpawnTick = tickCounter + 5;
			total += spawn(a, pl, lod, total);
			peakLive = Math.max(peakLive, a.live.size());
		}
	}

	private static void sound(PlayerAura a, Vec3d at) {
		boolean rukiaShikai = a.character == CharacterId.RUKIA && a.state == ZanpakutoState.SHIKAI;
		boolean byakBankai = a.character == CharacterId.BYAKUYA && a.state == ZanpakutoState.BANKAI;
		if (rukiaShikai) {
			a.nextSoundTick = tickCounter + 40;
			FxSound.play(null, "block.powder_snow.step", at.x, at.y + 1.0, at.z, 1.2, 0.25);
		} else if (byakBankai) {
			a.nextSoundTick = tickCounter + 30;
			FxSound.play(null, "entity.warden.heartbeat", at.x, at.y + 1.0, at.z, 0.7, 0.15);
		} else {
			a.nextSoundTick = tickCounter + 40;
		}
	}

	/** Spawns one cadence worth of particles; returns how many wisps. */
	private static int spawn(PlayerAura a, AbstractClientPlayerEntity pl, double lod, int clientTotal) {
		Vec3d feet = pl.getPos();
		boolean rukia = a.character == CharacterId.RUKIA;
		boolean bankai = a.state == ZanpakutoState.BANKAI;
		double perSecond = rukia ? (bankai ? 20 : 16) : (bankai ? 20 : 14);
		double expect = perSecond * 0.25;
		int n = (int) expect + (RNG.nextDouble() < expect - (int) expect ? 1 : 0);
		int made = 0;
		for (int i = 0; i < n; i++) {
			if (a.live.size() >= 120 || clientTotal + made >= 400) {
				break;
			}
			double ang = RNG.nextDouble() * Math.PI * 2;
			double rad = (bankai ? 0.9 : 0.6) * Math.sqrt(RNG.nextDouble());
			double h = RNG.nextDouble() * (bankai && rukia ? 1.8 : 1.0);
			int lifeTicks = rukia ? 36 : bankai ? 50 : 36;
			FxParticles.Spec p = FxParticles.spec(FxParticles.Kind.WISP).at(feet.x + Math.cos(ang) * rad, feet.y + h, feet.z + Math.sin(ang) * rad)
					.seed(RNG.nextInt(8)).size(0.95).alpha(1.0).lod(lod).fade(0.4);
			if (rukia) {
				p.color(bankai ? "#FFFFFF" : "#DDF3FF").colorTo("#9ED3F0").vel((RNG.nextDouble() - 0.5) * 0.2, bankai ? 0.9 : 1.2, (RNG.nextDouble() - 0.5) * 0.2)
						.life(36);
			} else if (bankai) {
				p.color("#CFC3F0").colorTo("#8F7FD0").vel((RNG.nextDouble() - 0.5) * 0.15, 0.6, (RNG.nextDouble() - 0.5) * 0.15).life(50);
			} else {
				p.color("#D9C8F0").colorTo("#B7A2E0").vel((RNG.nextDouble() - 0.5) * 0.2, 1.2, (RNG.nextDouble() - 0.5) * 0.2).life(36);
			}
			if (p.spawn()) {
				a.live.addLast(tickCounter + lifeTicks);
				a.wisps.addLast(FxParticles.lastSpawned);
				while (a.wisps.size() > 140) {
					a.wisps.pollFirst();
				}
				made++;
				spawned++;
			}
		}
		// body halo (cylindrical billboard, additive) and, on a slow beat, a ring on the ground: they carry the aura on a bright day
		String halo = rukia ? (bankai ? "#EAF8FF" : "#BFE4FF") : (bankai ? "#B9A8F0" : "#D9C8F0");
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(feet.x, feet.y + (bankai && rukia ? 1.0 : 0.95), feet.z).axisY().size(bankai ? 2.4 : 1.7).lifeTicks(11)
				.curve(0.15, 0.6).color(halo).peak(bankai ? 0.38 : 0.30).lod(lod).spawn();
		if (tickCounter >= a.nextRingTick) {
			a.nextRingTick = tickCounter + (bankai ? 28 : 24);
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(feet.x, feet.y + 0.05, feet.z).ground().size(1.6, bankai ? 5.4 : 4.2).sizeEase(FxMath.OC).life(0.9)
					.curve(0.1, 0.75).color(halo).peak(0.32).lod(lod).spawn();
		}
		// star glints along the blade line of a Rukia shikai (about 3 per second)
		if (rukia && !bankai && RNG.nextDouble() < 0.75) {
			double yaw = Math.toRadians(pl.getYaw());
			double fx = -Math.sin(yaw);
			double fz = Math.cos(yaw);
			double hx = feet.x - fz * 0.35 + fx * 0.45;
			double hz = feet.z + fx * 0.35 + fz * 0.45;
			double hy = feet.y + 1.1 + RNG.nextDouble() * 0.9;
			FxGlowBatch.sprite(GlowSprite.STAR6).at(hx, hy, hz).lifeTicks(8).size(0.25 + RNG.nextDouble() * 0.2).color("#FFFFFF").peak(0.8).twinkle()
					.rot(RNG.nextDouble() * 6.28, 1.5).lod(lod).spawn();
		}
		// Rukia bankai placeholder: a few snowflakes drifting down around the body
		if (rukia && bankai && RNG.nextDouble() < 0.7) {
			double ang = RNG.nextDouble() * Math.PI * 2;
			double rad = 0.8 + RNG.nextDouble() * 0.7;
			FxParticles.spec(FxParticles.Kind.SNOWFLAKE).at(feet.x + Math.cos(ang) * rad, feet.y + 2.0 + RNG.nextDouble() * 0.5, feet.z + Math.sin(ang) * rad)
					.vel(0, -0.5, 0).life(40).size(0.16).seed(RNG.nextInt(3)).spin(0.05).alpha(0.9).lod(lod).spawn();
		}
		return made;
	}
}
