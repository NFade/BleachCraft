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
 * rate x effect quality x distance LOD, at most 120 particles per player and 400 per client. Layers (design rule 1): pale
 * wisps rising off the body (the silhouette), a glow layer (body pillar, halo, ground ring) and sparkle accents; the
 * glittering motes are alpha sprites so the aura still reads on a bright sky (rule 5). Rukia shikai adds star glints on the
 * blade line and shed snowflakes; Byakuya shikai lilac glints and rising glow petals; Byakuya bankai a heartbeat (lub-dub
 * ring and body pulse in step with the sound); Rukia bankai the interim of 5.2 (snowfall disc, breathing frost sigil, ground
 * mist, glints, cold breath) until the frost decal, crystals and ribbons come with the bankai step. The orbiting petals of
 * the Byakuya bankai belong to the swarm. Numbers can be tuned live with {@code aura.*} keys of {@link FxTune}.
 */
public final class AuraFx {
	private static final class PlayerAura {
		ZanpakutoState state = ZanpakutoState.SEALED;
		CharacterId character = CharacterId.NONE;
		CharacterId lastReleased = CharacterId.NONE;
		int nextSpawnTick;
		int nextSoundTick;
		int nextRingTick;
		int nextBreathTick;
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

	/** The seal: every particle of that player fades out within 8 ticks instead of drifting on for seconds. */
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
		boolean rukia = a.character == CharacterId.RUKIA;
		boolean bankai = a.state == ZanpakutoState.BANKAI;
		if (rukia && !bankai) {
			a.nextSoundTick = tickCounter + 40;
			FxSound.play(null, "block.powder_snow.step", at.x, at.y + 1.0, at.z, 1.2, 0.25);
		} else if (!rukia && bankai) {
			a.nextSoundTick = tickCounter + 30;
			FxSound.play(null, "entity.warden.heartbeat", at.x, at.y + 1.0, at.z, 0.7, 0.15);
		} else if (rukia) {
			// the cold hum (every 4 s) and a snow step at random 2 to 4 s
			a.nextSoundTick = tickCounter + 40 + RNG.nextInt(40);
			FxSound.play(null, RNG.nextBoolean() ? "block.amethyst_block.resonate" : "block.powder_snow.step", at.x, at.y + 1.0, at.z, 0.6 + RNG.nextDouble() * 0.7,
					0.15);
		} else {
			a.nextSoundTick = tickCounter + 40;
		}
	}

	private static double tune(String key, double def) {
		return FxTune.d("aura." + key, def);
	}

	/** Spawns the particle and registers it with the player's live list (cap) and the dissolve list (seal). */
	private static boolean track(PlayerAura a, FxParticles.Spec p, int lifeTicks) {
		if (!p.spawn()) {
			return false;
		}
		a.live.addLast(tickCounter + lifeTicks);
		a.wisps.addLast(FxParticles.lastSpawned);
		while (a.wisps.size() > 220) {
			a.wisps.pollFirst();
		}
		spawned++;
		return true;
	}

	private static int count(double perSecond) {
		double expect = perSecond * 0.25 * tune("rate", 1.0);
		return (int) expect + (RNG.nextDouble() < expect - (int) expect ? 1 : 0);
	}

	/** Spawns one cadence worth of particles; returns how many particles were made (for the client cap). */
	private static int spawn(PlayerAura a, AbstractClientPlayerEntity pl, double lod, int clientTotal) {
		Vec3d feet = pl.getPos();
		boolean rukia = a.character == CharacterId.RUKIA;
		boolean bankai = a.state == ZanpakutoState.BANKAI;
		double now = FxClock.now;
		int made = 0;
		int cap = 120;
		// the wisps are alpha sprites: at night they would bury the body, in daylight they are what carries the aura, so they follow the sky light
		double day = FxMath.clamp((MinecraftClient.getInstance().world.getSkyBrightness(1.0f) - 0.2) / 0.8);
		double wispMul = FxMath.lerp(tune("nightWisp", 0.55), 1.0, day);
		// ------------------------------------------------------------------ wisps (the body of the aura)
		double wispRate = rukia ? (bankai ? 16 : 22) : (bankai ? 22 : 18);
		double wispSize = tune("wispSize", rukia ? (bankai ? 1.45 : 1.2) : (bankai ? 1.4 : 1.15));
		int lifeW = rukia ? (bankai ? 44 : 36) : (bankai ? 50 : 36);
		double rise = rukia ? (bankai ? 0.9 : 1.3) : (bankai ? 0.6 : 1.2);
		double radMax = bankai ? 0.95 : 0.65;
		double hMax = rukia && bankai ? 1.9 : 1.1;
		int n = count(wispRate * FxMath.lerp(tune("nightRate", 0.8), 1.0, day));
		for (int i = 0; i < n && a.live.size() < cap && clientTotal + made < 400; i++) {
			double ang = RNG.nextDouble() * Math.PI * 2;
			double rad = radMax * Math.sqrt(RNG.nextDouble());
			double h = RNG.nextDouble() * hMax;
			FxParticles.Spec p = FxParticles.spec(FxParticles.Kind.WISP).at(feet.x + Math.cos(ang) * rad, feet.y + h, feet.z + Math.sin(ang) * rad)
					.seed(RNG.nextInt(8)).size(wispSize * (0.8 + 0.4 * RNG.nextDouble())).alpha(tune("wispAlpha", bankai && rukia ? 0.85 : 1.0) * wispMul).lod(lod).fade(0.4).life(lifeW)
					.vel((RNG.nextDouble() - 0.5) * 0.2, rise * (0.85 + 0.3 * RNG.nextDouble()), (RNG.nextDouble() - 0.5) * 0.2);
			if (rukia) {
				p.color(bankai ? "#FFFFFF" : "#EAF8FF").colorTo(bankai ? "#BFE4FF" : "#9ED3F0");
			} else if (bankai) {
				p.color(FxTune.s("aura.byakBankaiColor", "#D2BEF7")).colorTo("#8A74D8");
			} else {
				p.color(FxTune.s("aura.byakShikaiColor", "#EBC8F8")).colorTo("#BE93E6");
			}
			if (track(a, p, lifeW)) {
				made++;
			}
		}
		// ------------------------------------------------------------------ glittering motes (alpha sprites: they read in daylight)
		double moteRate = rukia ? (bankai ? 8 : 12) : (bankai ? 10 : 8);
		n = count(moteRate);
		for (int i = 0; i < n && a.live.size() < cap && clientTotal + made < 400; i++) {
			double ang = RNG.nextDouble() * Math.PI * 2;
			double rad = 0.5 + RNG.nextDouble() * (bankai ? 0.9 : 0.6);
			double tang = (RNG.nextDouble() - 0.3) * 0.9; // swirl: a tangential start velocity
			FxParticles.Spec p = FxParticles.spec(FxParticles.Kind.FROST_MOTE)
					.at(feet.x + Math.cos(ang) * rad, feet.y + RNG.nextDouble() * 1.6, feet.z + Math.sin(ang) * rad)
					.vel(-Math.sin(ang) * tang, 0.6 + RNG.nextDouble() * 0.8, Math.cos(ang) * tang).drag(0.97)
					.size(tune("moteSize", 0.40) * (0.8 + 0.5 * RNG.nextDouble())).seed(RNG.nextInt(4)).life(26 + RNG.nextInt(10)).lod(lod).fade(0.45);
			if (rukia) {
				p.color("#FFFFFF").colorTo(FxTune.s("aura.moteEnd", "#BFE4FF"));
			} else {
				p.color("#F9E6FF").colorTo(bankai ? "#9F8FE0" : "#C9A8EE");
			}
			if (track(a, p, 36)) {
				made++;
			}
		}
		// ------------------------------------------------------------------ light layer: body pillar glow, halo, ground ring
		String halo = rukia ? (bankai ? "#EAF8FF" : "#BFE4FF") : (bankai ? "#B9A8F0" : "#D9C8F0");
		double glow = tune("glow", 1.0);
		// body glow: two soft discs one above the other (a tall column glow without hard edges, kept above the ground)
		double bodyS = bankai ? 1.7 : 1.35;
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(feet.x, feet.y + 0.75, feet.z).axisY().size(bodyS).lifeTicks(12).curve(0.12, 0.6).color(halo)
				.peak((bankai ? 0.30 : 0.26) * glow * (1.0 + 0.3 * day)).lod(lod).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(feet.x, feet.y + 1.55, feet.z).axisY().size(bodyS * 0.9).lifeTicks(12).curve(0.12, 0.6).color(halo)
				.peak((bankai ? 0.26 : 0.22) * glow * (1.0 + 0.3 * day)).lod(lod).spawn();
		if (!(!rukia && bankai) && tickCounter >= a.nextRingTick) {
			a.nextRingTick = tickCounter + (bankai ? 28 : 24);
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(feet.x, feet.y + 0.05, feet.z).ground().size(1.6, bankai ? 5.4 : 4.2).sizeEase(FxMath.OC).life(0.9)
					.curve(0.1, 0.75).color(halo).peak(0.32 * glow).lod(lod).spawn();
		}
		double yaw = Math.toRadians(pl.getYaw());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		// ------------------------------------------------------------------ per state extras
		if (rukia && !bankai) {
			// star glints along the blade line (about 4 per second) and a few snowflakes shed from the shoulders
			if (RNG.nextDouble() < 0.9) {
				double hx = feet.x - fz * 0.35 + fx * 0.45;
				double hz = feet.z + fx * 0.35 + fz * 0.45;
				double hy = feet.y + 1.1 + RNG.nextDouble() * 0.95;
				FxGlowBatch.sprite(GlowSprite.STAR6).at(hx, hy, hz).lifeTicks(9).size(0.35 + RNG.nextDouble() * 0.3).color("#FFFFFF").peak(0.9).twinkle()
						.rot(RNG.nextDouble() * 6.28, 1.5).lod(lod).spawn();
			}
			n = count(3);
			for (int i = 0; i < n && a.live.size() < cap; i++) {
				double ang = RNG.nextDouble() * Math.PI * 2;
				FxParticles.Spec p = FxParticles.spec(FxParticles.Kind.SNOWFLAKE)
						.at(feet.x + Math.cos(ang) * 0.7, feet.y + 1.5 + RNG.nextDouble() * 0.4, feet.z + Math.sin(ang) * 0.7)
						.vel(Math.cos(ang) * 0.25, -0.35, Math.sin(ang) * 0.25).life(40).size(0.17).seed(RNG.nextInt(3)).spin((RNG.nextDouble() - 0.5) * 0.12).alpha(0.95)
						.lod(lod);
				if (track(a, p, 40)) {
					made++;
				}
			}
		} else if (rukia) {
			made += rukiaBankaiExtras(a, feet, lod, now, cap, clientTotal + made, fx, fz, glow);
		} else if (!bankai) {
			// Byakuya shikai: lilac star glints and rising glow petals
			if (RNG.nextDouble() < 0.6) {
				FxGlowBatch.sprite(GlowSprite.STAR4).at(feet.x + (RNG.nextDouble() - 0.5) * 1.2, feet.y + 0.4 + RNG.nextDouble() * 1.6, feet.z + (RNG.nextDouble() - 0.5) * 1.2)
						.lifeTicks(9).size(0.3 + RNG.nextDouble() * 0.3).color("#F3E4FF").peak(0.85).twinkle().rot(RNG.nextDouble() * 6.28, 1.0).lod(lod).spawn();
			}
			n = count(3);
			for (int i = 0; i < n; i++) {
				double ang = RNG.nextDouble() * Math.PI * 2;
				double rad = 0.5 + RNG.nextDouble() * 0.5;
				FxGlowBatch.sprite(GlowSprite.PETAL_GLOW).at(feet.x + Math.cos(ang) * rad, feet.y + 0.2 + RNG.nextDouble() * 0.8, feet.z + Math.sin(ang) * rad)
						.vel(-Math.sin(ang) * 0.5, 0.9, Math.cos(ang) * 0.5).lifeTicks(22).size(0.32 + RNG.nextDouble() * 0.2).color("#F9C8F6").peak(0.8)
						.rot(RNG.nextDouble() * 6.28, (RNG.nextDouble() - 0.5) * 4).lod(lod).spawn();
			}
		} else {
			byakuyaBankaiExtras(a, feet, lod, glow);
		}
		return made;
	}

	/** Rukia bankai, the interim of 5.2 until the decal, crystals and ribbons exist: snowfall disc, breathing frost sigil, ground mist, glints, cold breath. */
	private static int rukiaBankaiExtras(PlayerAura a, Vec3d feet, double lod, double now, int cap, int clientTotal, double fx, double fz, double glow) {
		int made = 0;
		// snowfall: a disc of radius 3.5 at 3.5 above the feet, flakes fall 0.55 b/s with a sideways drift, spin
		int n = count(tune("snowRate", 9));
		for (int i = 0; i < n && a.live.size() < cap && clientTotal + made < 400; i++) {
			double ang = RNG.nextDouble() * Math.PI * 2;
			double rad = 3.5 * Math.sqrt(RNG.nextDouble());
			// no flake right in front of the lens (a 0.2 block sprite one block from the camera fills a tenth of the screen)
			Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
			if (Math.hypot(feet.x + Math.cos(ang) * rad - cam.x, feet.z + Math.sin(ang) * rad - cam.z) < 2.5) {
				continue;
			}
			FxParticles.Spec p = FxParticles.spec(FxParticles.Kind.SNOWFLAKE).at(feet.x + Math.cos(ang) * rad, feet.y + 3.5, feet.z + Math.sin(ang) * rad)
					.vel((RNG.nextDouble() - 0.5) * 0.4, -0.55, (RNG.nextDouble() - 0.5) * 0.4).life(120).size(tune("snowSize", 0.22)).seed(RNG.nextInt(3))
					.spin((RNG.nextDouble() - 0.5) * 0.10).alpha(0.95).lod(lod).fade(0.15);
			if (track(a, p, 120)) {
				made++;
			}
		}
		// the frost sigil on the ground, radius 3, 0.02 rev/s, breathing +-10 percent at 0.25 Hz
		double breath = 1.0 + 0.10 * Math.sin(2 * Math.PI * 0.25 * now);
		FxGlowBatch.sprite(GlowSprite.FROST_SIGIL).at(feet.x, feet.y + 0.04, feet.z).ground().size(6.0 * breath).lifeTicks(12).curve(0.12, 0.6)
				.rot(2 * Math.PI * 0.02 * now, 0).color("#CFEFFF").peak(tune("sigil", 0.34) * glow).lod(lod).spawn();
		// ground mist hugging the frost: one soft sprite per cadence drifting around her
		double ang = RNG.nextDouble() * Math.PI * 2;
		double rad = 0.8 + RNG.nextDouble() * 1.8;
		FxGlowBatch.sprite(GlowSprite.MIST).at(feet.x + Math.cos(ang) * rad, feet.y + 0.12, feet.z + Math.sin(ang) * rad).ground().rot(RNG.nextDouble() * 6.28, 0.1)
				.size(3.4 + RNG.nextDouble())
				.vel(-Math.sin(ang) * 0.35, 0.05, Math.cos(ang) * 0.35).lifeTicks(30).curve(0.3, 0.6).color("#EAF8FF").peak(tune("mist", 0.22) * glow).lod(lod).spawn();
		// glints on the ice air (6 per second)
		n = count(6);
		for (int i = 0; i < n; i++) {
			double ga = RNG.nextDouble() * Math.PI * 2;
			double gr = 3.0 * Math.sqrt(RNG.nextDouble());
			FxGlowBatch.sprite(GlowSprite.STAR6).at(feet.x + Math.cos(ga) * gr, feet.y + 0.2 + RNG.nextDouble() * 2.3, feet.z + Math.sin(ga) * gr).lifeTicks(10)
					.size(0.35 + RNG.nextDouble() * 0.3).color("#FFFFFF").peak(0.85).twinkle().rot(RNG.nextDouble() * 6.28, 1.0).lod(lod).spawn();
		}
		// cold breath: every 3 s six motes puff forward from the head
		if (tickCounter >= a.nextBreathTick) {
			a.nextBreathTick = tickCounter + 60;
			for (int i = 0; i < 6; i++) {
				FxParticles.Spec p = FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(feet.x + fx * 0.3, feet.y + 1.55, feet.z + fz * 0.3)
						.vel(fx * (0.6 + RNG.nextDouble() * 0.6) + (RNG.nextDouble() - 0.5) * 0.3, 0.1 + RNG.nextDouble() * 0.25,
								fz * (0.6 + RNG.nextDouble() * 0.6) + (RNG.nextDouble() - 0.5) * 0.3)
						.drag(0.94).life(20).size(0.2).color("#EAF8FF").alpha(0.45).seed(RNG.nextInt(4)).lod(lod);
				if (track(a, p, 20)) {
					made++;
				}
			}
		}
		return made;
	}

	/** Byakuya bankai: the heartbeat of the aura, a lub-dub pair of lilac pulses (ground ring and body flash) in step with the sound. */
	private static void byakuyaBankaiExtras(PlayerAura a, Vec3d feet, double lod, double glow) {
		if (tickCounter < a.nextRingTick) {
			return;
		}
		a.nextRingTick = tickCounter + 30;
		for (int k = 0; k < 2; k++) {
			double delay = k * 0.2;
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(feet.x, feet.y + 0.05, feet.z).ground().size(1.4, k == 0 ? 7.0 : 5.0).sizeEase(FxMath.OC).life(0.8).delay(delay)
					.curve(0.08, 0.7).color("#B9A8F0").peak((k == 0 ? 0.40 : 0.28) * glow).lod(lod).spawn();
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(feet.x, feet.y + 1.0, feet.z).axisY().size(2.0, 3.2).sizeEase(FxMath.OC).life(0.5).delay(delay)
					.curve(0.06, 0.7).color("#CFC3F0").peak((k == 0 ? 0.35 : 0.22) * glow).lod(lod).spawn();
		}
	}
}
