package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.client.model.DrawEvents;
import dev.minebleach.reiatsutest.core.fx.UryuBowForm;
import dev.minebleach.reiatsutest.core.fx.UryuBowGlow;
import dev.minebleach.reiatsutest.core.fx.UryuDrawLight;
import dev.minebleach.reiatsutest.core.fx.UryuEase;
import dev.minebleach.reiatsutest.core.fx.UryuFocusLook;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * The bow of Uryu (URYU_VFX_STORYBOARD 2.1 the forming from Reishi, 2.2 the light of the drawn bow). Called by {@link UryuFx}.
 *
 * <p>2.1 (key J, SEALED to drawn, {@link UryuBowForm}): every layer is a closed form function of the time since DRAW_START, evaluated each
 * frame against the CURRENT hand position, so the wheel, the limbs and the dashes stay on the hand while the player moves: 40 dashes, 12 specks
 * and 24 motes converge on the hand (t 0), the web wheel opens facing the aim (0.10), two neon limbs shoot out along the long axis with the
 * barbed notches popping as star glints (0.18), the solid bow fades in while the outlines go (0.30, the item model side:
 * {@code ObjItemBakedModel#emitBowForming}), a bright line sweeps the string (0.38), the wheel shrinks, six flakes fly off the tips and a ring
 * leaves the hand (0.42). The reverse (J again, 0.35 s) shows the wheel at 0.15, the outline for 0.1 s and 16 dashes collapsing into the hand.
 *
 * <p>2.2 (hold right click): the string brightens from Q2 to Q1, a pinpoint sits at the nock, Reishi dashes flow into it at 6 + 26 p per second,
 * the glow strip along the arrow lengthens, from p 0.5 the tips glint and a ghost wheel sits on the grip, at full draw a ring and a star ping and
 * the glow breathes (0.85 to 1.0 at 3 Hz, a constant 0.92 with Reduce Motion), the owner's FOV narrows by 1.5 degrees (not with Reduce Motion).
 * The emissive multiplier of the bow in the hand ({@link #glowMultiplier}) composes Focus, the draw and the muzzle flash ({@link UryuBowGlow}).
 *
 * <p>Public because {@code ObjItemBakedModel} (client.model) reads {@link #formAge} and {@link #glowMultiplier}. Everything is UNVERIFIED in game
 * (harness scenario {@code uryu_bow} / {@code uryu_draw}, shots p7_02_*).
 */
public final class UryuBowFx {
	private static final class Form {
		final double start;
		final boolean reverse;
		final boolean local;
		int cues;
		boolean launched;
		boolean settled;
		boolean sparked;
		final Random rng;

		Form(double start, boolean reverse, boolean local, int id) {
			this.start = start;
			this.reverse = reverse;
			this.local = local;
			this.rng = new Random(id * 31L + (long) (start * 1000));
		}
	}

	private static final class Draw {
		boolean half;
		boolean full;
		double dashCarry;
	}

	private static final Map<Integer, Form> FORMS = new HashMap<>();
	private static final Map<Integer, Draw> DRAWS = new HashMap<>();
	private static final Map<Integer, Double> FLASH = new HashMap<>();
	private static double fovBias;

	/** Statistics for the harness: sprites written in the last frame by the forming and the draw layers. */
	public static int lastFormSprites;
	public static int lastDrawSprites;
	/** Harness: forming and draws that were started on this client. */
	public static int formsStarted;
	public static int pingsFired;

	private UryuBowFx() {
	}

	static void init() {
		// sources are wired by UryuFx (frame and glow)
	}

	// ------------------------------------------------------------------ events and the item side

	/** The draw / sheathe events of Uryu's item. */
	static void onDraw(DrawEvents.Phase phase, LivingEntity entity, boolean local) {
		if (phase == DrawEvents.Phase.DRAW_START) {
			FORMS.put(entity.getId(), new Form(FxClock.now, false, local, entity.getId()));
			formsStarted++;
		} else if (phase == DrawEvents.Phase.SHEATHE_START) {
			FORMS.put(entity.getId(), new Form(FxClock.now, true, local, entity.getId()));
		}
	}

	/** The bow of this entity just shot (the muzzle): its emissive pass flashes x2 for 0.08 s (see {@link #glowMultiplier}). */
	static void flash(int entityId) {
		FLASH.put(entityId, FxClock.now);
	}

	/** Seconds since the bow of this entity started to form, or -1 when it is not forming (the item then draws the normal bow). */
	public static double formAge(int entityId) {
		Form f = FORMS.get(entityId);
		if (f == null || f.reverse) {
			return -1.0;
		}
		double t = FxClock.now - f.start;
		if (t >= UryuBowForm.DURATION) {
			return -1.0;
		}
		return t < 0 ? 0.0 : t;
	}

	/** The multiplier of the emissive pass of the bow in this entity's hand (see {@link UryuBowGlow}); 1 for the pendant. */
	public static double glowMultiplier(LivingEntity holder, boolean sealed) {
		if (sealed || holder == null) {
			return 1.0;
		}
		double p = UryuAnchors.power(holder, UryuAnchors.tickDelta());
		double age = FLASH.containsKey(holder.getId()) ? FxClock.now - FLASH.get(holder.getId()) : -1.0;
		double k = UryuBowGlow.bow(UryuFocusFx.stacksOf(holder.getId()), p, age);
		return k * UryuDrawLight.breath(FxClock.now, UryuAnchors.reduceMotion(), p);
	}

	// ------------------------------------------------------------------ per frame (world render START): clocks, sounds, spawns, FOV

	static void frame(double now) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null || mc.player == null) {
			FORMS.clear();
			DRAWS.clear();
			FLASH.clear();
			fovBias = 0;
			ScreenFx.setFovBias(0);
			return;
		}
		float td = UryuAnchors.tickDelta();
		UryuAnchors.Cam cam = UryuAnchors.cam();
		// the forming: sounds and the one shot spawns at the event times
		Iterator<Map.Entry<Integer, Form>> it = FORMS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Form> en = it.next();
			Form f = en.getValue();
			double t = now - f.start;
			double total = f.reverse ? UryuBowForm.REVERSE_DURATION : UryuBowForm.TOTAL;
			Entity ent = w.getEntityById(en.getKey());
			if (t > total + 0.05 || !(ent instanceof LivingEntity le) || !UryuAnchors.near(le, cam, UryuBowForm.RANGE)) {
				it.remove();
				continue;
			}
			UryuAnchors.Hand h = UryuAnchors.hand(le, 0.0, td, cam);
			double vol = f.local ? 1.0 : UryuBowForm.REMOTE_VOLUME;
			if (f.reverse) {
				if (f.cues == 0 && t >= 0) {
					f.cues = 1;
					FxSound.play(null, UryuBowForm.REVERSE_CUE.id(), h.grip()[0], h.grip()[1], h.grip()[2], UryuBowForm.REVERSE_CUE.pitch(), UryuBowForm.REVERSE_CUE.volume() * vol);
				}
				continue;
			}
			while (f.cues < UryuBowForm.CUES.size() && t >= UryuBowForm.CUES.get(f.cues).t()) {
				UryuBowForm.Cue c = UryuBowForm.CUES.get(f.cues++);
				FxSound.play(null, c.id(), h.grip()[0], h.grip()[1], h.grip()[2], c.pitch(), c.volume() * vol);
			}
			if (!f.launched && t >= 0) {
				f.launched = true;
				motes(f, h);
			}
			if (!f.settled && t >= UryuBowForm.T_SETTLE) {
				f.settled = true;
				settle(f, h);
			}
		}
		// the draw light of everybody who draws the bow
		boolean anyDraw = false;
		for (PlayerEntity pl : w.getPlayers()) {
			double p = UryuAnchors.power(pl, td);
			Draw d = DRAWS.get(pl.getId());
			if (p <= 0) {
				if (d != null) {
					DRAWS.remove(pl.getId());
				}
				continue;
			}
			if (!UryuAnchors.near(pl, cam, UryuBowForm.RANGE)) {
				continue;
			}
			anyDraw = true;
			if (d == null) {
				d = new Draw();
				DRAWS.put(pl.getId(), d);
			}
			drawFrame(pl, d, p, now, td, cam);
		}
		// the owner's field of view
		double target = 0.0;
		if (mc.player != null && UryuAnchors.first(mc.player)) {
			target = UryuDrawLight.fovTarget(UryuAnchors.power(mc.player, td), UryuAnchors.reduceMotion());
		}
		if (anyDraw || fovBias != 0.0) {
			fovBias = UryuDrawLight.fovStep(fovBias, target, FxClock.dt);
			ScreenFx.setFovBias(fovBias);
		}
		FLASH.values().removeIf(t0 -> now - t0 > 1.0);
	}

	/** The 24 motes (P:RM) of the gather: they start on a sphere of 3.0 blocks and fly to the hand in 0.30 s. */
	private static void motes(Form f, UryuAnchors.Hand h) {
		double rScale = h.first() ? 0.6 : 1.0;
		for (int i = 0; i < UryuBowForm.GATHER_MOTES; i++) {
			double[] d = UryuBowForm.gatherDirection(i, UryuBowForm.GATHER_MOTES);
			double r = UryuBowForm.GATHER_RADIUS * rScale * (0.75 + 0.25 * f.rng.nextDouble());
			double sx = h.grip()[0] + d[0] * r;
			double sy = h.grip()[1] + d[1] * r;
			double sz = h.grip()[2] + d[2] * r;
			double life = UryuBowForm.GATHER_LIFE;
			FxParticles.spec(FxParticles.Kind.REISHI_MOTE).at(sx, sy, sz).vel((h.grip()[0] - sx) / life, (h.grip()[1] - sy) / life, (h.grip()[2] - sz) / life)
					.life((int) Math.round(life * 20)).size(0.16, 0.08).color(UryuPalette.Q2).colorTo(UryuPalette.Q1).fade(0.3).seed(i).spawn();
		}
	}

	/** 0.42: six flakes fly off the tips (speed 2 blocks/s, 14 ticks) and a ring leaves the hand (0.8, 0.2 s). */
	private static void settle(Form f, UryuAnchors.Hand h) {
		double[] top = tipOf(h, 1);
		double[] bot = tipOf(h, -1);
		for (int i = 0; i < UryuBowForm.SETTLE_FLAKES; i++) {
			double[] tip = i % 2 == 0 ? top : bot;
			double a = f.rng.nextDouble() * Math.PI * 2;
			double s = UryuBowForm.SETTLE_FLAKE_SPEED;
			double vy = (i % 2 == 0 ? 1 : -1) * s * (0.6 + 0.6 * f.rng.nextDouble());
			FxParticles.spec(FxParticles.Kind.FLECHETTE).at(tip[0], tip[1], tip[2]).vel(Math.cos(a) * s * 0.7, vy, Math.sin(a) * s * 0.7)
					.life(UryuBowForm.SETTLE_FLAKE_LIFE_TICKS).size(0.30).color(UryuPalette.Q2).spin(0.25).seed(i + 7).spawn();
		}
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(h.grip()[0], h.grip()[1], h.grip()[2]).plane(h.aim()[0], h.aim()[1], h.aim()[2])
				.size(0.2, UryuBowForm.SETTLE_RING_SIZE * (h.first() ? 0.6 : 1.0)).sizeEase(FxMath.OC).life(UryuBowForm.SETTLE_RING_LIFE).curve(0.02, 0.7).color(UryuPalette.Q1)
				.peak(0.8).spawn();
	}

	private static double[] tipOf(UryuAnchors.Hand h, double sign) {
		return dev.minebleach.reiatsutest.core.fx.UryuBowAnchor.tip(h.grip(), h.up(), sign, h.scale());
	}

	/** Per frame state of one drawing player: the pings at p 0.5 and full draw, the Reishi dashes into the nock. */
	private static void drawFrame(PlayerEntity pl, Draw d, double p, double now, float td, UryuAnchors.Cam cam) {
		UryuAnchors.Hand h = UryuAnchors.hand(pl, p, td, cam);
		boolean local = pl == MinecraftClient.getInstance().player;
		double vol = local ? 1.0 : UryuBowForm.REMOTE_VOLUME;
		double[] n = h.nock();
		if (!d.half && UryuDrawLight.tipsLit(p)) {
			d.half = true;
			FxSound.play(null, UryuDrawLight.CHIME, n[0], n[1], n[2], UryuDrawLight.HALF_PITCH, UryuDrawLight.HALF_VOLUME * vol);
		}
		if (!d.full && UryuDrawLight.fullDraw(p)) {
			d.full = true;
			pingsFired++;
			FxSound.play(null, UryuDrawLight.CHIME, n[0], n[1], n[2], UryuDrawLight.FULL_PITCH, UryuDrawLight.FULL_VOLUME * vol);
			double k = h.first() ? 0.7 : 1.0;
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(n[0], n[1], n[2]).plane(h.aim()[0], h.aim()[1], h.aim()[2]).size(UryuDrawLight.PING_RING_FROM, UryuDrawLight.PING_RING_TO * k)
					.sizeEase(FxMath.OC).life(UryuDrawLight.PING_RING_TIME).curve(0.01, 0.8).color(UryuPalette.Q1).peak(0.9 * k).spawn();
			double[] head = UryuAnchors.add(n, h.aim(), UryuDrawLight.arrowStreakLength(p));
			FxGlowBatch.sprite(GlowSprite.STAR4).at(head[0], head[1], head[2]).size(UryuDrawLight.PING_STAR_SIZE * k).life(UryuDrawLight.PING_STAR_TIME).curve(0.01, 0.8)
					.color(UryuPalette.Q0).peak(1.0).rot(0.4, 0.0).spawn();
		}
		// Reishi flows into the nock: 6 + 26 p dashes per second from a sphere of 1.2 blocks, 0.18 s each
		d.dashCarry += UryuDrawLight.dashRate(p) * Math.min(0.1, FxClock.dt) * FxConfig.effectQuality;
		int count = (int) d.dashCarry;
		d.dashCarry -= count;
		count = Math.min(count, UryuDrawLight.DASH_MAX_PER_FRAME);
		Random r = new Random((long) (now * 1000) ^ pl.getId());
		double rad = UryuDrawLight.DASH_RADIUS * (h.first() ? 0.55 : 1.0);
		for (int i = 0; i < count; i++) {
			double[] dir = UryuBowForm.gatherDirection(r.nextInt(64), 64);
			double sx = n[0] + dir[0] * rad;
			double sy = n[1] + dir[1] * rad;
			double sz = n[2] + dir[2] * rad;
			double life = UryuDrawLight.DASH_LIFE;
			FxGlowBatch.sprite(GlowSprite.DASH).at(sx, sy, sz).vel((n[0] - sx) / life, (n[1] - sy) / life, (n[2] - sz) / life).streak().streakTime(0.05).size(0.3)
					.life(life).curve(0.02, 0.4).color(r.nextInt(4) == 0 ? UryuPalette.Q1 : UryuPalette.Q2).peak(h.first() ? 0.55 : 0.8).spawn();
		}
	}

	// ------------------------------------------------------------------ per frame glow (emitted into the batch of the frame)

	static void emitGlow(FxGlowBatch.Emitter e, double now) {
		lastFormSprites = 0;
		lastDrawSprites = 0;
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null) {
			return;
		}
		float td = UryuAnchors.tickDelta();
		UryuAnchors.Cam cam = UryuAnchors.cam();
		for (Map.Entry<Integer, Form> en : FORMS.entrySet()) {
			Entity ent = w.getEntityById(en.getKey());
			if (ent instanceof LivingEntity le) {
				forming(e, en.getValue(), le, now - en.getValue().start, td, cam);
			}
		}
		for (PlayerEntity pl : w.getPlayers()) {
			double p = UryuAnchors.power(pl, td);
			int stacks = UryuFocusFx.stacksOf(pl.getId());
			boolean tips3 = UryuFocusLook.layerOn(stacks, 3, FxConfig.effectQuality) && UryuAnchors.holdsBow(pl) && UryuAnchors.near(pl, cam, 32.0);
			if (p > 0 && DRAWS.containsKey(pl.getId())) {
				draw(e, pl, p, now, td, cam, stacks);
			} else if (tips3) {
				UryuAnchors.Hand h = UryuAnchors.hand(pl, 0.0, td, cam);
				tips(e, h, now, pl == mc.player ? 1.0 : UryuBowForm.REMOTE_GLOW, 0.8);
			}
		}
	}

	private static void spr(FxGlowBatch.Emitter e, GlowSprite s, double[] p, double size, double rot, float[] c, double inten) {
		if (inten > 0.004 && e.sprite(s, p[0], p[1], p[2], size, rot, c[0], c[1], c[2], inten)) {
			lastFormSprites++;
		}
	}

	private static void strk(FxGlowBatch.Emitter e, GlowSprite s, double[] head, double[] tail, double width, float[] c, double inten) {
		if (inten > 0.004 && e.streak(s, head[0], head[1], head[2], tail[0], tail[1], tail[2], width, c[0], c[1], c[2], inten)) {
			lastFormSprites++;
		}
	}

	private static void pln(FxGlowBatch.Emitter e, GlowSprite s, double[] p, double size, double[] n, double rot, float[] c, double inten) {
		if (inten > 0.004 && size > 0.01 && e.plane(s, p[0], p[1], p[2], size, n[0], n[1], n[2], rot, c[0], c[1], c[2], inten)) {
			lastFormSprites++;
		}
	}

	/** The forming (or its reverse) of one entity at {@code t} seconds. */
	private static void forming(FxGlowBatch.Emitter e, Form f, LivingEntity le, double t, float td, UryuAnchors.Cam cam) {
		if (t < 0 || !UryuAnchors.near(le, cam, UryuBowForm.RANGE)) {
			return;
		}
		UryuAnchors.Hand h = UryuAnchors.hand(le, 0.0, td, cam);
		double k = f.local ? 1.0 : UryuBowForm.REMOTE_GLOW;
		double s = h.scale();
		double[] g = h.grip();
		double[] u = h.up();
		double[] a = h.aim();
		boolean first = h.first();
		if (f.reverse) {
			reverse(e, h, t, k);
			return;
		}
		// the halo behind the wheel and the wheel itself (the aim is the plane normal: the viewer behind the owner sees it face on)
		double ha = UryuBowForm.haloAlpha(t) * k;
		spr(e, GlowSprite.GLOW_SOFT, g, UryuBowForm.HALO_SIZE * (first ? 0.55 : 1.0), 0, UryuPalette.FQ3, ha);
		double ws = UryuBowForm.wheelSize(t, first);
		pln(e, GlowSprite.WEB_WHEEL, g, ws, a, UryuBowForm.wheelRotation(t), UryuPalette.FQ1, UryuBowForm.wheelAlpha(t) * k);
		double star = t - UryuBowForm.T_WHEEL;
		if (star >= 0 && star <= UryuBowForm.STAR_FLASH_TIME) {
			spr(e, GlowSprite.STAR6, g, 0.7 * (first ? 0.6 : 1.0), t * 2.0, UryuPalette.FQ0, (1.0 - star / UryuBowForm.STAR_FLASH_TIME) * k);
		}
		// the limbs: a neon outline (white core, cyan glow) shoots out along the long axis, the notches pop as glints
		double len = UryuBowForm.limbLength(t) * s;
		double la = UryuBowForm.limbAlpha(t) * k;
		if (len > 0.01 && la > 0) {
			for (int side = -1; side <= 1; side += 2) {
				double[] end = UryuAnchors.add(g, u, side * len);
				strk(e, GlowSprite.LINE, end, g, UryuBowForm.LIMB_GLOW_WIDTH * s, UryuPalette.FQ3, la * 0.7);
				strk(e, GlowSprite.LINE, end, g, UryuBowForm.LIMB_CORE_WIDTH * s, UryuPalette.FQ1, la);
				for (int n = 0; n < UryuBowForm.NOTCHES; n++) {
					double na = UryuBowForm.notchAlpha(n, t);
					if (na > 0) {
						spr(e, GlowSprite.STAR4, UryuAnchors.add(g, u, side * UryuBowForm.notchFraction(n) * len), UryuBowForm.NOTCH_SIZE * s, n * 0.5, UryuPalette.FQ0, na * k);
					}
				}
			}
		}
		// the string sweep: a bright line runs tip to tip
		double sw = UryuBowForm.stringSweep(t);
		if (sw >= 0) {
			double[] top = UryuAnchors.add(UryuAnchors.add(g, u, UryuBowForm.LIMB_LENGTH * s), a, -dev.minebleach.reiatsutest.core.fx.UryuDrawLight.STRING_BEHIND_GRIP * s);
			double[] bot = UryuAnchors.add(UryuAnchors.add(g, u, -UryuBowForm.LIMB_LENGTH * s), a, -dev.minebleach.reiatsutest.core.fx.UryuDrawLight.STRING_BEHIND_GRIP * s);
			double[] pt = {bot[0] + (top[0] - bot[0]) * sw, bot[1] + (top[1] - bot[1]) * sw, bot[2] + (top[2] - bot[2]) * sw};
			double[] tail = UryuAnchors.add(pt, u, -0.35 * s);
			strk(e, GlowSprite.STREAK, pt, tail, 0.06, UryuPalette.FQ0, k);
			spr(e, GlowSprite.GLOW_CORE, pt, 0.18, 0, UryuPalette.FQ1, 0.8 * k);
		}
		// the gather: dashes (aligned radially, curved a little), specks
		gather(e, h, t, k, f);
	}

	private static void gather(FxGlowBatch.Emitter e, UryuAnchors.Hand h, double t, double k, Form f) {
		double rs = h.first() ? 0.6 : 1.0;
		double[] g = h.grip();
		int n = Math.max(8, (int) Math.round(UryuBowForm.GATHER_DASHES * UryuFocusLook.dashShare(FxConfig.effectQuality))); // tier Low: half the dashes
		for (int i = 0; i < n; i++) {
			double b = 0.10 * ((i * 0.6180339887) % 1.0);
			double u = (t - b) / UryuBowForm.GATHER_LIFE;
			if (u < 0 || u > 1) {
				continue;
			}
			double[] d = UryuBowForm.gatherDirection(i, n);
			double ang = 2 * Math.PI * 0.3 * (1.0 - u); // spiral: 0.3 turn about the vertical axis
			double c = Math.cos(ang);
			double sn = Math.sin(ang);
			double dx = d[0] * c - d[2] * sn;
			double dz = d[0] * sn + d[2] * c;
			double r = UryuBowForm.GATHER_RADIUS * rs * (1.0 - UryuEase.iq(u));
			double[] head = {g[0] + dx * r, g[1] + d[1] * r, g[2] + dz * r};
			double[] tail = {head[0] + dx * UryuBowForm.GATHER_DASH_SIZE, head[1] + d[1] * UryuBowForm.GATHER_DASH_SIZE, head[2] + dz * UryuBowForm.GATHER_DASH_SIZE};
			double in = UryuEase.smooth(0.0, 0.15, u);
			double out = 1.0 - UryuEase.smooth(0.8, 1.0, u);
			strk(e, GlowSprite.DASH, head, tail, 0.07, i % 3 == 0 ? UryuPalette.FQ1 : UryuPalette.FQ2, UryuBowForm.GATHER_DASH_INTENSITY * in * out * k);
		}
		for (int i = 0; i < UryuBowForm.GATHER_SPECKS; i++) {
			double b = 0.05 * i / UryuBowForm.GATHER_SPECKS;
			double u = (t - b) / (UryuBowForm.GATHER_LIFE * 1.2);
			if (u < 0 || u > 1) {
				continue;
			}
			double[] d = UryuBowForm.gatherDirection(i, UryuBowForm.GATHER_SPECKS);
			double r = UryuBowForm.GATHER_RADIUS * 0.8 * rs * (1.0 - UryuEase.oc(u));
			double[] p = UryuAnchors.add(g, d, r);
			spr(e, GlowSprite.SPECK, p, 0.07, 0, UryuPalette.FQ1, 0.9 * (1.0 - UryuEase.smooth(0.7, 1.0, u)) * k);
		}
	}

	/** The reverse: the wheel at 0.15 for 0.1 s, the outline for 0.1 s, then 16 dashes collapse from along the bow into the hand. */
	private static void reverse(FxGlowBatch.Emitter e, UryuAnchors.Hand h, double t, double k) {
		double[] g = h.grip();
		double s = h.scale();
		pln(e, GlowSprite.WEB_WHEEL, g, UryuBowForm.REVERSE_WHEEL_SIZE, h.aim(), t * 2.5, UryuPalette.FQ1, UryuBowForm.reverseWheelAlpha(t) * 0.8 * k);
		double oa = UryuBowForm.reverseOutlineAlpha(t) * k;
		if (oa > 0) {
			for (int side = -1; side <= 1; side += 2) {
				double[] end = UryuAnchors.add(g, h.up(), side * UryuBowForm.LIMB_LENGTH * s);
				strk(e, GlowSprite.LINE, end, g, UryuBowForm.LIMB_GLOW_WIDTH * s, UryuPalette.FQ3, oa * 0.6);
				strk(e, GlowSprite.LINE, end, g, UryuBowForm.LIMB_CORE_WIDTH * s, UryuPalette.FQ1, oa);
			}
		}
		for (int i = 0; i < UryuBowForm.REVERSE_DASHES; i++) {
			double u = (t - UryuBowForm.REVERSE_OUTLINE_TIME - 0.004 * i) / UryuBowForm.REVERSE_DASH_LIFE;
			if (u < 0 || u > 1) {
				continue;
			}
			double[] start = UryuAnchors.add(g, h.up(), UryuBowForm.reverseDashPosition(i) * UryuBowForm.LIMB_LENGTH * s);
			double e1 = UryuEase.ios(u);
			double[] head = {start[0] + (g[0] - start[0]) * e1, start[1] + (g[1] - start[1]) * e1, start[2] + (g[2] - start[2]) * e1};
			double[] tail = UryuAnchors.add(head, h.up(), UryuBowForm.reverseDashPosition(i) * 0.25);
			strk(e, GlowSprite.DASH, head, tail, 0.06, UryuPalette.FQ2, 0.8 * (1.0 - UryuEase.smooth(0.75, 1.0, u)) * k);
		}
	}

	/** The tips glint (twinkle 6 Hz): two star4 on the bow tips. */
	private static void tips(FxGlowBatch.Emitter e, UryuAnchors.Hand h, double now, double k, double inten) {
		for (int side = -1; side <= 1; side += 2) {
			double tw = UryuDrawLight.tipTwinkle(now + side * 0.04);
			spr(e, GlowSprite.STAR4, tipOf(h, side), UryuDrawLight.TIP_SIZE * 2.0, now * 0.6 * side, UryuPalette.FQ1, inten * tw * k);
		}
	}

	/** The light of a drawn bow (2.2): the string, the nock pinpoint, the arrow glow strip, the tip glints and the ghost wheel. */
	private static void draw(FxGlowBatch.Emitter e, PlayerEntity pl, double p, double now, float td, UryuAnchors.Cam cam, int stacks) {
		if (!UryuAnchors.near(pl, cam, UryuBowForm.RANGE)) {
			return;
		}
		UryuAnchors.Hand h = UryuAnchors.hand(pl, p, td, cam);
		boolean local = pl == MinecraftClient.getInstance().player;
		double k = local ? 1.0 : UryuBowForm.REMOTE_GLOW;
		double breath = UryuDrawLight.breath(now, UryuAnchors.reduceMotion(), p);
		double fk = k * breath;
		double[] n = h.nock();
		double[] a = h.aim();
		double s = h.scale();
		double mix = UryuDrawLight.stringMix(p);
		float[] stringCol = UryuAnchors.mix(UryuPalette.FQ2, UryuPalette.FQ1, mix);
		// the string: from tip to the nock to the other tip
		double[] topTip = UryuAnchors.add(UryuAnchors.add(h.grip(), h.up(), UryuBowForm.LIMB_LENGTH * s), a, -UryuDrawLight.STRING_BEHIND_GRIP * s);
		double[] botTip = UryuAnchors.add(UryuAnchors.add(h.grip(), h.up(), -UryuBowForm.LIMB_LENGTH * s), a, -UryuDrawLight.STRING_BEHIND_GRIP * s);
		double si = (0.30 + 0.35 * mix) * fk * (h.first() ? 0.7 : 1.0);
		drawStrk(e, GlowSprite.LINE, n, topTip, 0.035 * s, stringCol, si);
		drawStrk(e, GlowSprite.LINE, n, botTip, 0.035 * s, stringCol, si);
		// the pinpoint at the nock
		double ni = UryuDrawLight.nockIntensity(p) * fk;
		drawSpr(e, GlowSprite.GLOW_CORE, n, UryuDrawLight.NOCK_SIZE * 1.6, 0, UryuPalette.FQ1, ni);
		drawSpr(e, GlowSprite.GLOW_SOFT, n, 0.4, 0, UryuPalette.FQ3, ni * 0.4);
		// the glow strip of the arrow lengthens with the draw
		double len = UryuDrawLight.arrowStreakLength(p);
		double[] head = UryuAnchors.add(n, a, len);
		drawStrk(e, GlowSprite.STREAK, head, n, 0.10, UryuPalette.FQ3, 0.5 * fk);
		drawStrk(e, GlowSprite.STREAK, head, n, 0.035, UryuPalette.FQ0, 0.55 * fk);
		if (UryuDrawLight.fullDraw(p)) {
			drawSpr(e, GlowSprite.STAR4, head, 0.3, now * 0.4, UryuPalette.FQ0, 0.55 * fk);
		}
		// tips and ghost wheel from p 0.5 (the tips also glint with full Focus: see tips())
		if (UryuDrawLight.tipsLit(p)) {
			for (int side = -1; side <= 1; side += 2) {
				double tw = UryuDrawLight.tipTwinkle(now + side * 0.04);
				drawSpr(e, GlowSprite.STAR4, tipOf(h, side), UryuDrawLight.TIP_SIZE * 2.0, now * 0.6 * side, UryuPalette.FQ1, tw * k);
			}
			double[] wheelAt = UryuAnchors.add(h.grip(), a, -0.04);
			if (e.plane(GlowSprite.WEB_WHEEL, wheelAt[0], wheelAt[1], wheelAt[2], UryuDrawLight.GHOST_SIZE * (h.first() ? 0.7 : 1.0), a[0], a[1], a[2], now * 0.3,
					UryuPalette.FQ1[0], UryuPalette.FQ1[1], UryuPalette.FQ1[2], UryuDrawLight.ghostAlpha(p) * k)) {
				lastDrawSprites++;
			}
		} else if (UryuFocusLook.layerOn(stacks, 3, FxConfig.effectQuality)) {
			tips(e, h, now, k, 0.8);
		}
	}

	private static void drawSpr(FxGlowBatch.Emitter e, GlowSprite s, double[] p, double size, double rot, float[] c, double inten) {
		if (inten > 0.004 && e.sprite(s, p[0], p[1], p[2], size, rot, c[0], c[1], c[2], inten)) {
			lastDrawSprites++;
		}
	}

	private static void drawStrk(FxGlowBatch.Emitter e, GlowSprite s, double[] head, double[] tail, double width, float[] c, double inten) {
		if (inten > 0.004 && e.streak(s, head[0], head[1], head[2], tail[0], tail[1], tail[2], width, c[0], c[1], c[2], inten)) {
			lastDrawSprites++;
		}
	}
}
