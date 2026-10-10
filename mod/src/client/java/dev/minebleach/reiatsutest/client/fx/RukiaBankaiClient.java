package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.FieldAnchorPolicy;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.entity.FxAnchorEntity;
import dev.minebleach.reiatsutest.entity.FxAnchorKind;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The passive of the Rukia bankai (VFX_STORYBOARD 5.2) for every player in view: the frost ground under her feet (a decal that
 * follows, the previous one fades over 1.5 s), the standing crystals (6 at the release point, 8 on the rim, same seed on every
 * client, they stay where the bankai began), the owner's frost edge, frost under hostile mobs inside the aura, and the numbers the
 * feature renderer needs (ice sheen level, ribbon segments). Everything follows the synced attachment, so late joiners and other
 * clients see it too; the release timeline only adds the one shot parts. The server anchor of kind FIELD / p0 = 2 gives the
 * release point to a client that joined late.
 */
public final class RukiaBankaiClient {
	private static final class P {
		boolean bankai;
		double since;
		double endedAt = -1;
		double flareAt = -10;
		FxDecals.Decal decal;
		double decalX;
		double decalZ;
		boolean fieldMade;
		Vec3d fieldPos;
		ScreenFx.Layer vignette;
		double nextBreath;
		boolean seenBefore;
		int lastSegments;
	}

	private static final class MobFrost {
		FxDecals.Decal decal;
		double lastSeen;
	}

	private static final Map<Integer, P> PLAYERS = new HashMap<>();
	private static final Map<Integer, MobFrost> MOBS = new HashMap<>();
	private static final Random RNG = new Random(0xB4A1);
	private static int tickCounter;
	/** Statistics for the harness. */
	public static int fieldsMade;

	private RukiaBankaiClient() {
	}

	public static void clear() {
		PLAYERS.clear();
		MOBS.clear();
	}

	/** Seconds since the bankai of this player started (late joiners count as fully settled). */
	public static double bankaiAge(int entityId) {
		P p = PLAYERS.get(entityId);
		return p == null || !p.bankai ? -1 : FxClock.now - p.since;
	}

	/** True while the ice sheen and the ribbons of this player are drawn (bankai, plus 0.4 s after it ended). */
	public static boolean visible(int entityId) {
		P p = PLAYERS.get(entityId);
		return p != null && (p.bankai || (p.endedAt >= 0 && FxClock.now - p.endedAt < 0.45));
	}

	/** Ice sheen intensity 0..1 (0 to 0.5 over 0.6 to 1.2 s after the release, settling to 0.22 by 2.0, flaring on a cast). */
	public static double sheen(int entityId) {
		P p = PLAYERS.get(entityId);
		if (p == null) {
			return 0;
		}
		double now = FxClock.now;
		double level;
		if (!p.bankai) {
			double e = p.endedAt < 0 ? 1 : (now - p.endedAt) / 0.4;
			return Math.max(0, 0.22 * (1 - FxMath.clamp(e)));
		}
		double age = now - p.since;
		if (age < 0.6) {
			level = 0;
		} else if (age < 1.2) {
			level = 0.5 * FxMath.smoothstep(0.6, 1.2, age);
		} else if (age < 2.0) {
			level = FxMath.lerp(0.5, 0.22, FxMath.smoothstep(1.2, 2.0, age));
		} else {
			level = 0.22;
		}
		double fl = 1 - FxMath.clamp((now - p.flareAt) / 0.3);
		return Math.max(level, 0.5 * fl);
	}

	/**
	 * Number of ribbon segments out per chain (fractional, 9 segments and the tip): none before 0.9 s, one per 0.05 s, all out after 0.5 s; when the bankai ends they
	 * retract one segment per 0.03 s.
	 */
	public static double ribbonSegments(int entityId) {
		P p = PLAYERS.get(entityId);
		if (p == null) {
			return 0;
		}
		double now = FxClock.now;
		if (!p.bankai) {
			return p.endedAt < 0 ? 0 : Math.max(0, 10 - (now - p.endedAt) / 0.03);
		}
		return FxMath.clamp((now - p.since - 0.9) / 0.05, 0, 10);
	}

	/** Seconds since the segment with this index appeared (the spring flick). */
	public static double segmentAge(int entityId, int index) {
		P p = PLAYERS.get(entityId);
		return p == null ? 10 : FxClock.now - p.since - 0.9 - 0.05 * index;
	}

	/** Any cast of her: the sheen flares to 0.5 for 0.3 s. */
	public static void flare(int entityId) {
		P p = PLAYERS.get(entityId);
		if (p != null) {
			p.flareAt = FxClock.now;
		}
	}

	/** The release point of a field: the anchor of this owner if there is one, else null. */
	private static Vec3d anchorOf(MinecraftClient mc, int ownerId) {
		for (Entity e : mc.world.getEntities()) {
			if (e instanceof FxAnchorEntity a && a.kind() == FxAnchorKind.FIELD && a.p0() == FieldAnchorPolicy.FIELD_RUKIA_BANKAI && a.ownerId() == ownerId) {
				return a.getPos();
			}
		}
		return null;
	}

	/** END_CLIENT_TICK. */
	public static void tick(MinecraftClient mc) {
		tickCounter++;
		if (mc.world == null || mc.player == null || mc.isPaused() || FxClock.frozen) {
			return;
		}
		double now = FxClock.now;
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		Iterator<Map.Entry<Integer, P>> it = PLAYERS.entrySet().iterator();
		while (it.hasNext()) {
			var en = it.next();
			if (mc.world.getEntityById(en.getKey()) == null) {
				endField(en.getKey(), en.getValue(), false);
				it.remove();
			}
		}
		for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
			ZanpakutoData z = pl.getAttached(ModAttachments.ZANPAKUTO);
			boolean bankai = z != null && z.zanpakutoState() == ZanpakutoState.BANKAI && z.characterId() == CharacterId.RUKIA;
			P p = PLAYERS.get(pl.getId());
			if (p == null) {
				p = new P();
				PLAYERS.put(pl.getId(), p);
				p.seenBefore = false;
			}
			if (bankai && !p.bankai) {
				p.bankai = true;
				p.endedAt = -1;
				p.since = p.seenBefore ? now : now - 5.0; // an observed transition starts the release clock, a late join is already settled
				p.fieldMade = false;
			} else if (!bankai && p.bankai) {
				p.bankai = false;
				p.endedAt = now;
				endField(pl.getId(), p, true);
			}
			p.seenBefore = true;
			if (!p.bankai) {
				continue;
			}
			double dist = cam.distanceTo(pl.getPos());
			if (dist > 96 || !pl.isAlive()) {
				continue;
			}
			double age = now - p.since;
			boolean local = pl == mc.player && !FxClient.forceRemote();
			// ---- the frost ground that follows her
			Vec3d feet = pl.getPos();
			if (p.decal == null || Math.hypot(feet.x - p.decalX, feet.z - p.decalZ) > 0.6) {
				if (p.decal != null) {
					p.decal.fadeOutNow(1.5);
				}
				double delay = p.decal == null && age < 0.6 ? 0.6 - age : 0.0;
				p.decal = FxDecals.frost(feet.x, feet.z, feet.y, FxTune.d("rbankai.decalRadius", 3.4), delay).reveal(p.decal == null ? 0.5 : 0.4).alpha(FxTune.d("rbankai.decal", 0.82));
				p.decalX = feet.x;
				p.decalZ = feet.z;
			}
			// ---- the standing field: 6 crystals at the release point, 8 on the rim
			if (!p.fieldMade) {
				p.fieldMade = true;
				Vec3d at = age >= 4.0 ? anchorOf(mc, pl.getId()) : null;
				p.fieldPos = at != null ? at : feet;
				makeField(pl.getId(), p.fieldPos, age);
			}
			// ---- owner screen: the release frost edge settles to 0.06
			if (local && age >= 2.0 && p.vignette == null) {
				p.vignette = ScreenFx.vignette(ScreenFx.Kind.FROST, 0.06, 1.0, 1.0);
			}
			// ---- glints on the crystals
			if (tickCounter % 2 == 0) {
				glints(pl.getId(), cam);
			}
			if (tickCounter % 5 == 0) {
				mobs(mc, pl, p, dist);
			}
		}
		// frost under mobs that left the aura fades
		MOBS.entrySet().removeIf(e -> {
			if (now - e.getValue().lastSeen > 0.4) {
				if (e.getValue().decal != null) {
					e.getValue().decal.fadeOutNow(0.5);
				}
				return true;
			}
			return false;
		});
	}

	private static void endField(int ownerId, P p, boolean shatter) {
		if (shatter) {
			FxMeshPass.shatterField(ownerId, 4, 4.5);
		} else {
			FxMeshPass.clearField(ownerId);
		}
		if (p.decal != null) {
			p.decal.fadeOutNow(1.0);
			p.decal = null;
		}
		if (p.vignette != null) {
			p.vignette.release(1.0);
			p.vignette = null;
		}
		p.fieldMade = false;
	}

	/** 6 release crystals (a x3, b x3) at radius 1.2 to 1.6 and 8 rim crystals (a, b) at 2.6 to 3.0, seeded by the owner; staged by the release clock. */
	private static void makeField(int ownerId, Vec3d at, double age) {
		Random r = new Random(ownerId * 7919L + 13);
		double sc = FxTune.d("rbankai.crystalScale", 1.0);
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * 2 * (i + r.nextDouble() * 0.6) / 6;
			double rad = 1.2 + r.nextDouble() * 0.4;
			double px = at.x + Math.cos(a) * rad;
			double pz = at.z + Math.sin(a) * rad;
			double py = FxGround.sample(px, pz, at.y);
			double lean = 0.1 + r.nextDouble() * 0.18;
			boolean small = i % 2 == 0;
			double delay = Math.max(0.0, 0.6 + 0.06 * i - age);
			FxMeshPass.Crystal c = FxMeshPass.crystal(small ? FxMeshes.CRYSTAL_A : FxMeshes.CRYSTAL_B, px, py - 0.03, pz, Math.cos(a) * lean, 1, Math.sin(a) * lean, r.nextDouble() * 6.28,
					(small ? 4.6 : 2.5) * sc * (0.85 + 0.3 * r.nextDouble()), delay);
			c.field = ownerId;
			c.pulsePhase = i * 0.17;
		}
		for (int j = 0; j < 8; j++) {
			double a = Math.PI * 2 * (j + r.nextDouble() * 0.7) / 8;
			double rad = 2.6 + r.nextDouble() * 0.4;
			double px = at.x + Math.cos(a) * rad;
			double pz = at.z + Math.sin(a) * rad;
			double py = FxGround.sample(px, pz, at.y);
			double lean = 0.1 + r.nextDouble() * 0.2;
			boolean small = j % 2 == 1;
			double delay = Math.max(0.0, 1.4 + 0.22 * j - age);
			FxMeshPass.Crystal c = FxMeshPass.crystal(small ? FxMeshes.CRYSTAL_A : FxMeshes.CRYSTAL_B, px, py - 0.03, pz, Math.cos(a) * lean, 1, Math.sin(a) * lean, r.nextDouble() * 6.28,
					(small ? 5.2 : 3.0) * sc * (0.85 + 0.3 * r.nextDouble()), delay);
			c.field = ownerId;
			c.pulsePhase = j * 0.31 + 0.5;
		}
		fieldsMade++;
	}

	private static void glints(int ownerId, Vec3d cam) {
		double now = FxClock.now;
		for (FxMeshPass.Crystal c : FxMeshPass.crystals()) {
			if (c.field != ownerId || now < c.birth || cam.squaredDistanceTo(c.x, c.y, c.z) > 48 * 48) {
				continue;
			}
			boolean first = !c.glinted && c.grown(now);
			if (first || RNG.nextInt(60) == 0) {
				c.glinted = true;
				Vec3d tip = c.tip();
				FxGlowBatch.sprite(GlowSprite.STAR4).at(tip.x, tip.y, tip.z).lifeTicks(first ? 10 : 8).size(first ? 1.0 : 0.8).color("#FFFFFF").peak(0.95).twinkle()
						.rot(RNG.nextDouble() * 6, 1).spawn();
			}
		}
	}

	/** Frost under hostile mobs inside the aura (every 5 ticks, at most 24 mobs): a small decal, motes at the feet, glints on the body. */
	private static void mobs(MinecraftClient mc, AbstractClientPlayerEntity pl, P p, double dist) {
		if (dist > 24) {
			return;
		}
		double r = FxTune.d("rbankai.mobRadius", 3.2);
		Box box = pl.getBoundingBox().expand(r);
		int n = 0;
		for (HostileEntity m : mc.world.getEntitiesByClass(HostileEntity.class, box, h -> h.isAlive() && h.squaredDistanceTo(pl) <= r * r)) {
			if (++n > 24) {
				break;
			}
			MobFrost mf = MOBS.get(m.getId());
			if (mf == null) {
				mf = new MobFrost();
				MOBS.put(m.getId(), mf);
				mf.decal = FxDecals.create(FxDecals.FROST, m.getX(), m.getZ(), m.getY(), Math.max(0.6, m.getWidth() * 0.9), 0).reveal(0.4).alpha(0.8);
				FxSound.play(null, "entity.player.hurt_freeze", m.getX(), m.getY() + 1, m.getZ(), 1.2, 0.4);
			}
			mf.lastSeen = FxClock.now;
			if (RNG.nextDouble() < 0.7) {
				FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(m.getX() + (RNG.nextDouble() - 0.5) * m.getWidth(), m.getY() + 0.1, m.getZ() + (RNG.nextDouble() - 0.5) * m.getWidth())
						.vel(0, 0.7, 0).drag(0.96).life(16).size(0.3).color("#EAF8FF").colorTo("#BFE4FF").seed(n).fade(0.5).spawn();
			}
			if (RNG.nextDouble() < 0.4) {
				FxGlowBatch.sprite(GlowSprite.STAR6).at(m.getX() + (RNG.nextDouble() - 0.5) * m.getWidth(), m.getY() + RNG.nextDouble() * m.getHeight(),
						m.getZ() + (RNG.nextDouble() - 0.5) * m.getWidth()).lifeTicks(9).size(0.4).color("#FFFFFF").peak(0.8).twinkle().rot(RNG.nextDouble() * 6, 1).spawn();
			}
		}
	}
}
