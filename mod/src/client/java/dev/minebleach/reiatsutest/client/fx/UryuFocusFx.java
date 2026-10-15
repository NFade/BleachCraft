package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.UryuBowForm;
import dev.minebleach.reiatsutest.core.fx.UryuFocusLook;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * The Focus stacks of the Uryu players on this client and the Focus feedback on their bodies (URYU_VFX_STORYBOARD 2.4, the body of Uryu), called
 * through {@link UryuAuraFx}. The numbers are in {@link UryuFocusLook} (unit tested). The server sends the FOCUS packet to the owner and to the
 * players who track him (S-U2), so everybody in view sees the layers; a player who starts tracking later sees nothing until the next change.
 *
 * <pre>
 *   gain   pop: star4 at the hand (0.15 s), 8 dashes converge (0.12 s), chime 1.2 / 1.5 / 1.9; the new layer fades in over 0.2 s
 *   1      dashes converge on the bow hand (6/s from 1.6 blocks); the bow glows +30 percent (UryuBowFx.glowMultiplier)
 *   2      + ring on the ground at the feet (radius 0.9, breathing 0.8..1.0 at 1 Hz), 10 dashes/s, three flakes orbit the body, hum every 2 s
 *   3      + the Zeichen cross on a plane behind the bow, a pentagon outline turning on the ground (r 1.4), a rim glow round the body, tip glints on
 *          the bow (UryuBowFx), 14 dashes/s, hum every 1.4 s
 *   loss   12 dashes fly outward from the body (radius 0 to 1.6, 0.15 s), glass tick
 * </pre>
 * Reduce Motion: no orbiting flakes, the glyphs do not turn, the ring does not breathe. Tier Low: layers 1 and 2 only, dashes halved
 * ({@link UryuFocusLook#maxLayer}, {@link UryuFocusLook#dashShare}). UNVERIFIED in game (harness {@code uryu_focus}, shots p7_02_focus_*).
 */
final class UryuFocusFx {
	/** Stacks of OTHER Uryu players (entity id to 1..3), from the FOCUS packets that carry a caster id (S-U2). */
	private static final Map<Integer, Integer> OTHERS = new HashMap<>();
	/** FX clock second of the last gain of an entity (the newest layer fades in from it). */
	private static final Map<Integer, Double> GAIN = new HashMap<>();
	private static final Map<Integer, Double> HUM = new HashMap<>();
	private static final Map<Integer, Double> DASH_CARRY = new HashMap<>();
	/** The ring radius in blocks: RING_THIN draws its ring at 0.4375 of the sprite width. */
	private static final double RING_FRACTION = 0.4375;

	/** Harness: pops, losses and the glow sprites written in the last frame. */
	static int pops;
	static int losses;
	static int lastSprites;

	private UryuFocusFx() {
	}

	static void init() {
		// frame and glow are called by UryuAuraFx from UryuFx
	}

	// ------------------------------------------------------------------ the packet

	/** The FOCUS packet: {@code ids = {stacks, casterId}}; an older packet without caster id is the owner's own count. */
	static void onFocus(int[] ids) {
		int stacks = ids.length > 0 ? Math.max(0, Math.min(UryuFocusLook.MAX_STACKS, ids[0])) : 0;
		ClientPlayerEntity me = MinecraftClient.getInstance().player;
		int id = ids.length < 2 && me != null ? me.getId() : ids.length >= 2 ? ids[1] : -1;
		int before = id < 0 ? 0 : stacksOf(id);
		if (ids.length < 2 || me != null && ids[1] == me.getId()) {
			UryuFx.focus = stacks;
		} else if (stacks <= 0) {
			OTHERS.remove(ids[1]);
		} else {
			OTHERS.put(ids[1], stacks);
		}
		if (id >= 0 && before != stacks) {
			changed(id, before, stacks);
		}
	}

	/** Stacks of Focus of a player (the local one too; 0 when none was sent). */
	static int stacksOf(int entityId) {
		ClientPlayerEntity me = MinecraftClient.getInstance().player;
		if (me != null && me.getId() == entityId) {
			return UryuFx.focus;
		}
		return OTHERS.getOrDefault(entityId, 0);
	}

	// ------------------------------------------------------------------ gain and loss

	private static void changed(int id, int from, int to) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null) {
			return;
		}
		Entity ent = w.getEntityById(id);
		if (!(ent instanceof LivingEntity le)) {
			return;
		}
		float td = UryuAnchors.tickDelta();
		UryuAnchors.Cam cam = UryuAnchors.cam();
		if (!UryuAnchors.near(le, cam, UryuBowForm.RANGE)) {
			return;
		}
		UryuAnchors.Hand h = UryuAnchors.hand(le, UryuAnchors.power(le, td), td, cam);
		double[] g = h.grip();
		Random r = new Random(id * 7919L + to * 31L + (long) (FxClock.now * 100));
		if (UryuFocusLook.isGain(from, to)) {
			pops++;
			GAIN.put(id, FxClock.now);
			double k = h.first() ? 0.6 : 1.0;
			FxGlowBatch.sprite(GlowSprite.STAR4).at(g[0], g[1], g[2]).size(UryuFocusLook.POP_STAR_SIZE * k).life(UryuFocusLook.POP_STAR_LIFE).curve(0.01, 0.7)
					.color(UryuPalette.Q0).peak(0.95).rot(r.nextDouble() * 3, 1.0).spawn();
			for (int i = 0; i < UryuFocusLook.POP_DASHES; i++) {
				double[] d = UryuBowForm.gatherDirection(r.nextInt(64), 64);
				double rad = UryuFocusLook.POP_DASH_RADIUS * k;
				double life = UryuFocusLook.POP_DASH_LIFE;
				double sx = g[0] + d[0] * rad;
				double sy = g[1] + d[1] * rad;
				double sz = g[2] + d[2] * rad;
				FxGlowBatch.sprite(GlowSprite.DASH).at(sx, sy, sz).vel((g[0] - sx) / life, (g[1] - sy) / life, (g[2] - sz) / life).streak().streakTime(0.05).size(0.3)
						.life(life).curve(0.01, 0.4).color(UryuPalette.Q2).peak(0.8).spawn();
			}
			double vol = le == mc.player ? 1.0 : UryuBowForm.REMOTE_VOLUME;
			FxSound.play(null, "block.amethyst_block.chime", g[0], g[1], g[2], UryuFocusLook.gainPitch(to), UryuFocusLook.GAIN_VOLUME * vol);
		} else if (UryuFocusLook.isLoss(from, to)) {
			losses++;
			double[] c = {h.feet()[0], h.feet()[1] + 1.0, h.feet()[2]};
			for (int i = 0; i < UryuFocusLook.RESET_DASHES; i++) {
				double[] d = UryuBowForm.gatherDirection(i, UryuFocusLook.RESET_DASHES);
				double life = UryuFocusLook.RESET_LIFE;
				double sp = UryuFocusLook.RESET_RADIUS / life;
				FxGlowBatch.sprite(GlowSprite.DASH).at(c[0], c[1], c[2]).vel(d[0] * sp, d[1] * sp, d[2] * sp).streak().streakTime(0.05).size(0.3).life(life).curve(0.01, 0.5)
						.color(UryuPalette.Q2).peak(0.7).spawn();
			}
			double vol = le == mc.player ? 1.0 : UryuBowForm.REMOTE_VOLUME;
			FxSound.play(null, "block.amethyst_block.break", c[0], c[1], c[2], UryuFocusLook.RESET_PITCH, UryuFocusLook.RESET_VOLUME * vol);
		}
	}

	// ------------------------------------------------------------------ per frame spawns (layer 1 flow, hum) and cleanup

	/** Per frame: dashes converge on the bow hand, the hum, and the state of players that are gone is dropped. */
	static void frame(double now) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null) {
			return;
		}
		if (OTHERS.isEmpty() && UryuFx.focus <= 0) {
			return;
		}
		float td = UryuAnchors.tickDelta();
		UryuAnchors.Cam cam = UryuAnchors.cam();
		double q = FxConfig.effectQuality;
		for (PlayerEntity pl : w.getPlayers()) {
			int stacks = stacksOf(pl.getId());
			if (stacks <= 0 || !UryuAnchors.near(pl, cam, UryuBowForm.RANGE)) {
				continue;
			}
			UryuAnchors.Hand h = UryuAnchors.hand(pl, UryuAnchors.power(pl, td), td, cam);
			double[] g = h.grip();
			double rate = UryuFocusLook.dashRate(stacks, q);
			double carry = DASH_CARRY.getOrDefault(pl.getId(), 0.0) + rate * Math.min(0.1, FxClock.dt);
			int n = Math.min(3, (int) carry);
			carry -= (int) carry;
			DASH_CARRY.put(pl.getId(), carry);
			Random r = new Random((long) (now * 1000) * 31L + pl.getId());
			double k = h.first() ? 0.55 : 1.0;
			for (int i = 0; i < n; i++) {
				double[] d = UryuBowForm.gatherDirection(r.nextInt(64), 64);
				double rad = UryuFocusLook.DASH_RADIUS * k;
				double life = UryuFocusLook.DASH_LIFE;
				double sx = g[0] + d[0] * rad;
				double sy = g[1] + d[1] * rad;
				double sz = g[2] + d[2] * rad;
				FxGlowBatch.sprite(GlowSprite.DASH).at(sx, sy, sz).vel((g[0] - sx) / life, (g[1] - sy) / life, (g[2] - sz) / life).streak().streakTime(0.05)
						.size(UryuFocusLook.DASH_SIZE).life(life).curve(0.03, 0.4).color(r.nextInt(5) == 0 ? UryuPalette.Q1 : UryuPalette.Q2)
						.peak(UryuFocusLook.DASH_ALPHA * (h.first() ? 0.7 : 1.0)).spawn();
			}
			double iv = UryuFocusLook.humInterval(stacks);
			if (iv > 0 && UryuFocusLook.layerOn(stacks, 2, q)) {
				double last = HUM.getOrDefault(pl.getId(), -10.0);
				if (now - last >= iv) {
					HUM.put(pl.getId(), now);
					double dx = pl.getX() - cam.pos()[0];
					double dy = pl.getY() - cam.pos()[1];
					double dz = pl.getZ() - cam.pos()[2];
					if (dx * dx + dy * dy + dz * dz <= UryuFocusLook.HUM_RANGE * UryuFocusLook.HUM_RANGE) {
						FxSound.play(null, "block.beacon.ambient", g[0], g[1], g[2], UryuFocusLook.humPitch(stacks), UryuFocusLook.humVolume(stacks));
					}
				}
			}
		}
	}

	/** Per frame: nothing of another player's Focus stays when there is no world (disconnect) or when the player left the view. */
	static void cleanup() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null) {
			OTHERS.clear();
			GAIN.clear();
			HUM.clear();
			DASH_CARRY.clear();
			return;
		}
		if (!OTHERS.isEmpty() && FxClock.frame % 40 == 0) {
			Iterator<Integer> it = OTHERS.keySet().iterator();
			while (it.hasNext()) {
				if (w.getEntityById(it.next()) == null) {
					it.remove();
				}
			}
		}
	}

	// ------------------------------------------------------------------ per frame glow (the layers 2 and 3)

	static void emitGlow(FxGlowBatch.Emitter e, double now) {
		lastSprites = 0;
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null || OTHERS.isEmpty() && UryuFx.focus <= 0) {
			return;
		}
		float td = UryuAnchors.tickDelta();
		UryuAnchors.Cam cam = UryuAnchors.cam();
		double q = FxConfig.effectQuality;
		boolean rm = UryuAnchors.reduceMotion();
		for (PlayerEntity pl : w.getPlayers()) {
			int stacks = stacksOf(pl.getId());
			if (stacks < 2 || !UryuAnchors.near(pl, cam, 40.0)) {
				continue;
			}
			boolean local = pl == mc.player;
			double k = local ? 1.0 : UryuBowForm.REMOTE_GLOW;
			double gain = now - GAIN.getOrDefault(pl.getId(), -10.0);
			UryuAnchors.Hand h = UryuAnchors.hand(pl, UryuAnchors.power(pl, td), td, cam);
			double fx = h.feet()[0];
			double fy = h.feet()[1];
			double fz = h.feet()[2];
			// layer 2: the thin ring on the ground, the orbiting flakes
			if (UryuFocusLook.layerOn(stacks, 2, q)) {
				double f2 = stacks == 2 ? UryuFocusLook.layerFade(gain) : 1.0;
				double breath = UryuFocusLook.ringBreath(now, rm);
				if (e.ground(GlowSprite.RING_THIN, fx, fy + 0.06, fz, UryuFocusLook.RING_SIZE * 0.5 / RING_FRACTION, UryuFocusLook.RING_SIZE * 0.5 / RING_FRACTION, 0.0,
						UryuPalette.FQ2[0], UryuPalette.FQ2[1], UryuPalette.FQ2[2], UryuFocusLook.RING_ALPHA * breath * f2 * k)) {
					lastSprites++;
				}
				if (!rm) {
					for (int i = 0; i < UryuFocusLook.ORBIT_FLAKES; i++) {
						double[] o = UryuFocusLook.orbitOffset(i, now);
						if (e.sprite(GlowSprite.FLAKE, fx + o[0], fy + o[1], fz + o[2], UryuFocusLook.ORBIT_SIZE, now * 1.3 + i * 2.1, UryuPalette.FQ2[0], UryuPalette.FQ2[1],
								UryuPalette.FQ2[2], UryuFocusLook.ORBIT_ALPHA * f2 * k)) {
							lastSprites++;
						}
					}
				}
			}
			// layer 3: the Zeichen cross behind the bow, the pentagon outline on the ground, the rim glow round the body
			if (UryuFocusLook.layerOn(stacks, 3, q)) {
				double f3 = UryuFocusLook.layerFade(gain);
				boolean first = h.first();
				double[] a = h.aim();
				double[] behind = UryuAnchors.add(h.grip(), a, -0.08);
				double size = first ? UryuFocusLook.CROSS_SIZE_FIRST : UryuFocusLook.CROSS_SIZE;
				double alpha = first ? UryuFocusLook.CROSS_ALPHA_FIRST : UryuFocusLook.CROSS_ALPHA;
				if (e.plane(GlowSprite.ZEICHEN_CROSS, behind[0], behind[1], behind[2], size, a[0], a[1], a[2], UryuFocusLook.rotation(UryuFocusLook.CROSS_REV_PER_S, now, rm),
						UryuPalette.FQ1[0], UryuPalette.FQ1[1], UryuPalette.FQ1[2], alpha * f3 * k)) {
					lastSprites++;
				}
				double ps = UryuFocusLook.PENTAGON_SIZE;
				if (e.ground(GlowSprite.PENT_LINE, fx, fy + 0.07, fz, ps, ps, UryuFocusLook.rotation(UryuFocusLook.PENTAGON_REV_PER_S, now, rm), UryuPalette.FQ7[0],
						UryuPalette.FQ7[1], UryuPalette.FQ7[2], UryuFocusLook.PENTAGON_ALPHA * f3 * k)) {
					lastSprites++;
				}
				if (!first && e.sprite(GlowSprite.GLOW_SOFT, fx, fy + UryuFocusLook.BODY_GLOW_HEIGHT, fz, UryuFocusLook.BODY_GLOW_SIZE, 0.0, UryuPalette.FQ3[0], UryuPalette.FQ3[1],
						UryuPalette.FQ3[2], UryuFocusLook.BODY_GLOW_ALPHA * f3 * k)) {
					lastSprites++;
				}
			}
		}
	}
}
