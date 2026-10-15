package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.SprengerFxPlan;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Sprenger (H, SHIKAI, effect 73, URYU_VFX_STORYBOARD 2.7 and 2.8; U-FX 4). Canon: the figure on the ground is a pentagon OUTLINE with
 * five spokes and a hub (five Seele Schneider fall at the corners, the neon edges join them one after the other), NOT a star; the Quincy
 * Zeichen flares only for a moment at the bind and then at the explosion. At 0.8 s the legs are bound (a fence of light rises, a column
 * charges in the middle, {@link UryuBindFx} draws the roots on the bound entities), at 1.75 s everything holds its breath, and at the
 * server's explosion (the BURST packet, or sooner for an early detonation) a column of light fills the pentagon, a Zeichen flares, a ring of
 * dust rolls out and a scar stays.
 *
 * <p>Structure: the TRAP packet starts an {@link EffectTimeline} (one-shots: landings, sounds, dust, screen) and a {@link Pentagon} whose
 * continuous parts (daggers' glow cards, spokes, edges, floor, fence, bind flash) are closed form functions of the FX clock drawn by the glow
 * source {@link #emitGlow} and the fence pass {@link #drawCurtains}, so the harness freeze and the hit stop hold them. The BURST packet (not a
 * clock) starts the explosion timeline; a cancelled pentagon (END with reason CANCEL, or no BURST by {@link SprengerFxPlan#cancelTimeout})
 * sinks its daggers and fades without a fence or an explosion. All numbers are {@link SprengerFxPlan} and {@link UryuPalette}; a few visual
 * gains are {@code FxTune} keys {@code spr.*}. Called by {@link UryuFx}.
 */
final class UryuSprengerFx {
	static final class Pentagon {
		int group;
		double cx;
		double cy;
		double cz;
		double r;
		double born;
		double life;
		final double[] px = new double[5];
		final double[] pz = new double[5];
		final UryuSeeleRenderer.Seele[] seeles = new UryuSeeleRenderer.Seele[5];
		final double[] baseY = new double[5];
		/** Entity id of the caster (effect 73), -1 until known. */
		int owner = -1;
		int slot = -1;
		int exSlot = -1;
		long seed;
		/** FX clock of the BURST packet, or -1. */
		double exploded = -1;
		boolean early;
		/** FX clock of the cancel, or -1. */
		double cancelled = -1;
		/** FX clock after which the charge shapes of this pentagon stop drawing (burst or cancel). */
		double cut = Double.MAX_VALUE;
		ScreenFx.Layer grade;
		boolean shattered;
	}

	private static final List<Pentagon> PENTAGONS = new ArrayList<>();
	private static int nextGroup = 1;
	private static boolean inited;
	/** The caster of the last effect 73 and when it came (the TRAP packet and the effect event arrive in either order within a tick). */
	private static int lastCastOwner = -1;
	private static double lastCastClock = -10;
	/** Dev/harness statistics. */
	static int trapsSeen;
	static int burstsSeen;
	static int cancels;
	static int lastCurtains;

	private static final float[] Q0 = {1f, 1f, 1f};
	private static final float[] Q1 = FxMath.hex(UryuPalette.Q1);
	private static final float[] Q2 = FxMath.hex(UryuPalette.Q2);
	private static final float[] Q3 = FxMath.hex(UryuPalette.Q3);
	private static final float[] Q7 = FxMath.hex(UryuPalette.Q7);
	private static final float[] Q8 = FxMath.hex(UryuPalette.Q8);

	private UryuSprengerFx() {
	}

	static void init() {
		if (inited) {
			return;
		}
		inited = true;
		WorldRenderEvents.LAST.register(UryuSprengerFx::drawCurtains);
	}

	// ------------------------------------------------------------------ bind packets (S-U3, the roots live in UryuBindFx)

	/** S-U3 BIND: {@code ids} = the bound entities, {@code d = {cx, cy, cz, ticks until the root ends (counted from the packet)}}. */
	static void onBind(int[] ids, float[] d) {
		UryuBindFx.onBind(ids, d);
	}

	/** S-U3 END: {@code ids} = the freed entities, {@code d = {cx, cy, cz, reason}} with the codes of {@code BindEnd}. */
	static void onEnd(int[] ids, float[] d) {
		UryuBindFx.onEnd(ids, d);
	}

	/** True while the server says this entity is rooted by a pentagon. */
	static boolean isBound(int entityId) {
		return UryuBindFx.isBound(entityId);
	}

	/** Effect event 73 (Sprenger cast): says whose pentagon the TRAP packet of the same tick is. */
	static void onCast(EffectEventS2C e) {
		lastCastOwner = e.casterId();
		lastCastClock = FxClock.now;
		for (Pentagon p : PENTAGONS) {
			if (p.owner < 0 && FxClock.now - p.born < 0.5) {
				p.owner = e.casterId();
			}
		}
	}

	/** END with reason CANCEL for the pentagon at this centre: it fizzled (the caster died, left, changed state or replaced it). */
	static void onBindCancelled(double x, double y, double z) {
		for (Pentagon p : PENTAGONS) {
			if (p.exploded < 0 && p.cancelled < 0 && Math.abs(p.cx - x) < 1.0 && Math.abs(p.cz - z) < 1.0) {
				cancel(p);
			}
		}
	}

	/** The five dagger hilt points (x, y, z) of the pentagon at this centre, or the reference pentagon when none is known (the chains of a bound entity start there). */
	static double[][] hilts(double cx, double cy, double cz) {
		double[][] out = new double[5][3];
		Pentagon found = null;
		for (Pentagon p : PENTAGONS) {
			if (Math.abs(p.cx - cx) < 1.0 && Math.abs(p.cz - cz) < 1.0) {
				found = p;
			}
		}
		for (int i = 0; i < 5; i++) {
			if (found != null) {
				out[i][0] = found.px[i];
				out[i][1] = found.seeles[i] != null ? found.seeles[i].y : found.cy + SprengerFxPlan.HILT_HEIGHT;
				out[i][2] = found.pz[i];
			} else {
				out[i][0] = SprengerFxPlan.vertexX(cx, SprengerFxPlan.REFERENCE_RADIUS, i);
				out[i][1] = cy + SprengerFxPlan.HILT_HEIGHT;
				out[i][2] = SprengerFxPlan.vertexZ(cz, SprengerFxPlan.REFERENCE_RADIUS, i);
			}
		}
		return out;
	}

	/** Harness: forgets every pentagon, root and dagger (the next scenario starts clean). */
	static void reset() {
		PENTAGONS.clear();
		UryuBindFx.clear();
		UryuSeeleRenderer.clear();
		trapsSeen = 0;
		burstsSeen = 0;
		cancels = 0;
		lastCurtains = 0;
		UryuBindFx.bindsSeen = 0;
	}

	/** Pentagons alive (harness). */
	static int count() {
		return PENTAGONS.size();
	}

	// ------------------------------------------------------------------ helpers

	private static boolean reduced() {
		return FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
	}

	private static double tune(String key, double def) {
		return FxTune.d(key, def);
	}

	private static SprengerFxPlan.Detail detail() {
		return SprengerFxPlan.detail(FxConfig.fxTier.name());
	}

	private static ClientPlayerEntity me() {
		return MinecraftClient.getInstance().player;
	}

	private static boolean mine(Pentagon p) {
		ClientPlayerEntity me = me();
		return me != null && p.owner == me.getId();
	}

	/** Distance of the camera to the pentagon edge (the 1.4 distance rule for the screen effects of a remote pentagon). */
	private static double edgeDistance(Pentagon p) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) {
			return 0.0;
		}
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		return Math.max(0.0, Math.hypot(cam.x - p.cx, cam.z - p.cz) - p.r);
	}

	/** True for the owner and for a player rooted by a pentagon. */
	private static boolean involved(Pentagon p) {
		return mine(p) || UryuBindFx.localBound();
	}

	private static Pentagon nearest(double x, double z, double within) {
		Pentagon hit = null;
		double best = within;
		for (Pentagon p : PENTAGONS) {
			if (p.exploded >= 0 || p.cancelled >= 0) {
				continue;
			}
			double dd = Math.hypot(p.cx - x, p.cz - z);
			if (dd < best) {
				best = dd;
				hit = p;
			}
		}
		return hit;
	}

	private static void sound(EffectTimeline t, String id, Pentagon p, double pitch, double volume) {
		FxSound.play(t, id, p.cx, p.cy + 1.0, p.cz, pitch, volume);
	}

	// ------------------------------------------------------------------ the TRAP packet: the figure

	/** TRAP: five Seele Schneider fall into the vertices of the pentagon and the figure draws itself; the explosion comes from the BURST packet. */
	static void trap(double cx, double cy, double cz, double r, double seconds) {
		init();
		trapsSeen++;
		double now = FxClock.now;
		Pentagon pg = new Pentagon();
		pg.group = nextGroup++;
		pg.cx = cx;
		pg.cy = cy;
		pg.cz = cz;
		pg.r = r;
		pg.born = now;
		pg.life = seconds;
		pg.seed = Double.doubleToLongBits(cx) * 31L + Double.doubleToLongBits(cz) * 17L + pg.group;
		if (now - lastCastClock < 0.5) {
			pg.owner = lastCastOwner;
		}
		for (int i = 0; i < 5; i++) {
			pg.px[i] = SprengerFxPlan.vertexX(cx, r, i);
			pg.pz[i] = SprengerFxPlan.vertexZ(cz, r, i);
			UryuSeeleRenderer.Seele s = new UryuSeeleRenderer.Seele();
			// tip down, leaning a little towards the centre; the tip stands in the ground, the hilt about a metre above it
			double lean = 0.18;
			Vec3d toCentre = new Vec3d(cx - pg.px[i], 0, cz - pg.pz[i]).normalize();
			s.dx = toCentre.x * lean;
			s.dy = -1;
			s.dz = toCentre.z * lean;
			Vec3d dd = new Vec3d(s.dx, s.dy, s.dz).normalize();
			s.scale = (float) SprengerFxPlan.DAGGER_SCALE;
			s.x = pg.px[i] - dd.x * 0.3 * s.scale;
			s.y = cy + 0.05 - dd.y * 0.3 * s.scale;
			s.z = pg.pz[i] - dd.z * 0.3 * s.scale;
			s.born = now + SprengerFxPlan.dropAt(i);
			s.fall = SprengerFxPlan.DAGGER_DROP;
			s.dropHeight = SprengerFxPlan.DAGGER_HEIGHT;
			s.life = seconds + 1.5; // the burst or the cancel removes them; this is the safety net
			s.group = pg.group;
			s.spinDegPerSec = 0;
			s.color = UryuSeeleRenderer.TONE_QUINCY;
			pg.seeles[i] = s;
			pg.baseY[i] = s.y;
			UryuSeeleRenderer.add(s);
		}
		PENTAGONS.removeIf(x -> x.exploded < 0 && x.cancelled < 0 ? now - x.born > x.life + 2.0 : false);
		PENTAGONS.add(pg);
		// a pooled sprite that stays alive (and invisible) for the whole life: the Fabulous depth copy is only taken while the batch has live sprites,
		// and the figure itself is drawn by the glow source, which does not count
		FxGlowBatch.sprite(GlowSprite.SPECK).at(cx, cy, cz).size(0.001).life(seconds + 4.0).peak(0.0).spawn();
		EffectTimeline t = FxTimelines.create(73, pg.seed, new Vec3d(cx, cy, cz), new Vec3d(0, 0, 1), new float[] {(float) r, (float) seconds}, pg.owner, false);
		pg.slot = t.slot;
		schedule(t, pg);
		FxTimelines.start(t);
	}

	private static void schedule(EffectTimeline t, Pentagon pg) {
		SprengerFxPlan.Detail detail = detail();
		t.lifetime(SprengerFxPlan.EXPLODE_AT + 1.4);
		// 0.00 the daggers fall (the meshes are UryuSeeleRenderer's): every landing is a spark, a few ricochet flakes and a thud
		for (int i = 0; i < 5; i++) {
			final int k = i;
			t.at(SprengerFxPlan.landAt(i), x -> landing(x, pg, k));
		}
		// 0.30 hub and spokes (drawn by emitGlow), a rising chime
		t.at(SprengerFxPlan.SPOKE_AT, x -> sound(x, SprengerFxPlan.SPOKE_SOUND.id(), pg, SprengerFxPlan.SPOKE_SOUND.pitch(), SprengerFxPlan.SPOKE_SOUND.volume()));
		// 0.55 the five edges draw in order, a crystal place for each
		for (int i = 0; i < 5; i++) {
			final int k = i;
			t.at(SprengerFxPlan.EDGE_AT + SprengerFxPlan.EDGE_SOUND_STEP * i,
					x -> sound(x, SprengerFxPlan.EDGE_SOUND, pg, SprengerFxPlan.EDGE_SOUND_PITCH + 0.08 * k, SprengerFxPlan.EDGE_SOUND_VOLUME));
		}
		// 0.80 bind: fence (drawCurtains), column, flash; 0.80 to 1.75 charge: dust ring every 0.4 s, glitter, rising hum, the owner's tremor
		t.at(SprengerFxPlan.BIND_AT, x -> bind(x, pg));
		for (int k = 0; k < 3; k++) {
			final int kk = k;
			t.at(SprengerFxPlan.BIND_AT + SprengerFxPlan.CHARGE_DUST_EVERY * k, x -> dustRing(x, pg, kk));
		}
		for (int k = 0; k < SprengerFxPlan.HUM_STEPS; k++) {
			final int kk = k;
			t.at(SprengerFxPlan.BIND_AT + SprengerFxPlan.HUM_EVERY * k, x -> hum(x, pg, kk));
		}
		if (detail.glitter()) {
			int n = (int) Math.floor((SprengerFxPlan.ANTICIPATION_AT - SprengerFxPlan.BIND_AT) * SprengerFxPlan.GLITTER_PER_SECOND);
			for (int k = 0; k < n; k++) {
				final int kk = k;
				t.at(SprengerFxPlan.BIND_AT + k / SprengerFxPlan.GLITTER_PER_SECOND, x -> glitter(x, pg, kk));
			}
		}
		// 1.75 anticipation: everything dims, the column becomes a needle, grade, the other Uryu sounds of the caster are ducked
		t.at(SprengerFxPlan.ANTICIPATION_AT, x -> anticipation(x, pg));
		t.duck(SprengerFxPlan.ANTICIPATION_AT, SprengerFxPlan.EXPLODE_AT, 0.0);
	}

	private static void landing(EffectTimeline x, Pentagon pg, int i) {
		double px = pg.px[i];
		double pz = pg.pz[i];
		FxGlowBatch.sprite(GlowSprite.STAR4).at(px, pg.cy + 0.15, pz).size(0.7).life(0.12).curve(0.01, 0.7).color(UryuPalette.Q1).peak(1.0).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(px, pg.cy + 0.1, pz).size(1.2).life(0.2).curve(0.01, 0.7).color(UryuPalette.Q2).peak(0.6).owner(x).spawn();
		Random r = x.rng(10 + i);
		for (int k = 0; k < 6; k++) {
			double a = r.nextDouble() * Math.PI * 2;
			double s = 2.0 + r.nextDouble() * 2.5;
			FxParticles.spec(FxParticles.Kind.FLECHETTE).at(px, pg.cy + 0.2, pz).vel(Math.cos(a) * s, 2.0 + r.nextDouble() * 2.0, Math.sin(a) * s).drag(0.92).gravity(0.9).life(14)
					.size(0.3 + r.nextDouble() * 0.1).color(UryuPalette.Q3).seed(r.nextInt(3)).spin((r.nextDouble() - 0.5) * 0.7).owner(x).spawn();
		}
		FxSound.play(x, SprengerFxPlan.LAND_SOUND, px, pg.cy, pz, SprengerFxPlan.LAND_PITCH + SprengerFxPlan.LAND_PITCH_STEP * i, SprengerFxPlan.LAND_VOLUME);
	}

	/** 0.80: the legs are bound. */
	private static void bind(EffectTimeline x, Pentagon pg) {
		sound(x, SprengerFxPlan.BIND_SOUND_A.id(), pg, SprengerFxPlan.BIND_SOUND_A.pitch(), SprengerFxPlan.BIND_SOUND_A.volume());
		sound(x, SprengerFxPlan.BIND_SOUND_B.id(), pg, SprengerFxPlan.BIND_SOUND_B.pitch(), SprengerFxPlan.BIND_SOUND_B.volume());
		if (mine(pg) && x.screen) {
			ScreenFx.flash(reduced() ? 0.15 : 0.20, Q2, true, 1.0);
		}
		// the column that charges in the middle: a Q3 body and a thin white heart, both growing for 0.95 s, gone with the burst or the cancel
		FxShapes.Beam body = FxShapes.beam(pg.cx, pg.cz, pg.cy, 0).height(SprengerFxPlan::chargeHeight).width(SprengerFxPlan::chargeWidth).topFade(0.45).scroll(2.5, 0.2);
		body.color(UryuPalette.Q3).life(SprengerFxPlan.CHARGE_TIME).intensity(a -> SprengerFxPlan.chargeIntensity(a) * FxMath.clamp(a / 0.05) * alive(pg)).owner(x);
		FxShapes.Beam heart = FxShapes.beam(pg.cx, pg.cz, pg.cy, 0).height(SprengerFxPlan::chargeHeight).width(a -> SprengerFxPlan.chargeWidth(a) * 0.3).topFade(0.6).scroll(3.5, 0.1);
		heart.color(UryuPalette.Q0).life(SprengerFxPlan.CHARGE_TIME).intensity(a -> 0.8 * SprengerFxPlan.chargeIntensity(a) * FxMath.clamp(a / 0.05) * alive(pg)).owner(x);
	}

	/** 1 while the pentagon is neither exploded nor cancelled. */
	private static double alive(Pentagon pg) {
		return FxClock.now < pg.cut ? 1.0 : 0.0;
	}

	/** The white dust that rolls round the pentagon while it charges (every 0.4 s a ring of 28 puffs, a slow swirl outward). */
	private static void dustRing(EffectTimeline x, Pentagon pg, int ring) {
		int n = Math.max(4, (int) Math.round(SprengerFxPlan.CHARGE_DUST * detail().dust() / (double) SprengerFxPlan.HIGH.dust()));
		Random r = x.rng(100 + ring);
		double rad = pg.r * SprengerFxPlan.CHARGE_DUST_RADIUS;
		for (int j = 0; j < n; j++) {
			double a = Math.PI * 2 * j / n + r.nextDouble() * 0.2 + ring * 0.4;
			double out = 0.8;
			double swirl = reduced() ? 0.0 : 1.2;
			FxParticles.spec(FxParticles.Kind.DUST_PUFF).at(pg.cx + Math.cos(a) * rad, pg.cy + 0.2, pg.cz + Math.sin(a) * rad)
					.vel(Math.cos(a) * out - Math.sin(a) * swirl, 0.4 + r.nextDouble() * 0.2, Math.sin(a) * out + Math.cos(a) * swirl).drag(0.97).life(30).size(0.9 + r.nextDouble() * 0.3, 2.0)
					.color(UryuPalette.Q9).alpha(0.45).seed(r.nextInt(4)).fade(0.5).owner(x).spawn();
		}
	}

	private static void hum(EffectTimeline x, Pentagon pg, int k) {
		sound(x, SprengerFxPlan.HUM_SOUND, pg, SprengerFxPlan.humPitch(k), SprengerFxPlan.HUM_VOLUME);
		if (mine(pg) && x.screen) {
			ScreenFx.shake(SprengerFxPlan.tremor(k), 0.3, 1.0);
		}
	}

	private static void glitter(EffectTimeline x, Pentagon pg, int k) {
		Random r = x.rng(200 + k);
		double a = r.nextDouble() * Math.PI * 2;
		double rad = Math.sqrt(r.nextDouble()) * pg.r * 0.9;
		FxGlowBatch.sprite(GlowSprite.GLITTER).at(pg.cx + Math.cos(a) * rad, pg.cy + 0.1, pg.cz + Math.sin(a) * rad).vel(0, 1.4 + r.nextDouble() * 0.6, 0).size(0.3).life(1.0).curve(0.1, 0.5)
				.color(UryuPalette.Q2).peak(0.8).twinkle().rot(r.nextDouble() * 6.28, 0).owner(x).spawn();
	}

	/** 1.75: the figure dims, the column becomes a needle, the grade darkens the world for everybody close. */
	private static void anticipation(EffectTimeline x, Pentagon pg) {
		FxShapes.Beam needle = FxShapes.beam(pg.cx, pg.cz, pg.cy, 0).height(a -> SprengerFxPlan.CHARGE_HEIGHT).width(a -> 2.0 * SprengerFxPlan.NEEDLE_RADIUS).topFade(0.4).scroll(1.0, 0.05);
		needle.color(UryuPalette.Q0).life(SprengerFxPlan.EXPLODE_AT - SprengerFxPlan.ANTICIPATION_AT + 0.4).intensity(a -> 0.9 * FxMath.clamp(a / 0.03) * alive(pg)).owner(x);
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null && x.screen) {
			double dist = mc.gameRenderer.getCamera().getPos().distanceTo(new Vec3d(pg.cx, pg.cy, pg.cz));
			boolean inv = involved(pg);
			if (SprengerFxPlan.gradeApplies(inv, dist)) {
				double f = SprengerFxPlan.screenFactor(inv, edgeDistance(pg));
				pg.grade = ScreenFx.grade(FxMath.hex(UryuPalette.GRADE_BANKAI), SprengerFxPlan.GRADE_S, SprengerFxPlan.GRADE_RAMP, Math.max(f, inv ? 1.0 : 0.25)).owner(pg.owner);
			}
		}
	}

	// ------------------------------------------------------------------ cancel

	/** The pentagon fizzles: daggers sink in 0.2 s, edges and fence fade in 0.3 s, no explosion. */
	private static void cancel(Pentagon p) {
		if (p.cancelled >= 0 || p.exploded >= 0) {
			return;
		}
		cancels++;
		p.cancelled = FxClock.now;
		p.cut = FxClock.now;
		if (p.grade != null) {
			p.grade.release(SprengerFxPlan.CANCEL_FADE);
			p.grade = null;
		}
		FxSound.play(null, "block.amethyst_block.break", p.cx, p.cy + 0.5, p.cz, 1.9, 0.25);
		ReiatsuTest.LOGGER.info("[fx] sprenger cancelled at ({}, {}, {})", p.cx, p.cy, p.cz);
	}

	// ------------------------------------------------------------------ the BURST packet: the explosion

	/** The explosion: BURST from the server (at 2.0 s, or at once for a Focus 3 arrow: {@code early}). */
	static void burst(ClientWorld w, double x, double y, double z, double r, boolean early) {
		init();
		burstsSeen++;
		double now = FxClock.now;
		Pentagon pg = nearest(x, z, 4.0);
		if (pg == null) {
			// the TRAP packet was never seen (the pentagon started out of range or before the client joined): explode from the packet alone
			pg = new Pentagon();
			pg.group = nextGroup++;
			pg.cx = x;
			pg.cy = y;
			pg.cz = z;
			pg.r = r;
			pg.born = now - SprengerFxPlan.EXPLODE_AT;
			pg.life = SprengerFxPlan.EXPLODE_AT;
			pg.seed = Double.doubleToLongBits(x) * 31L + Double.doubleToLongBits(z) * 17L + pg.group;
			for (int i = 0; i < 5; i++) {
				pg.px[i] = SprengerFxPlan.vertexX(x, r, i);
				pg.pz[i] = SprengerFxPlan.vertexZ(z, r, i);
			}
			PENTAGONS.add(pg);
		}
		pg.exploded = now;
		pg.early = early;
		pg.cut = now;
		// the daggers shatter into flakes and go
		Random rd = new Random(pg.seed + 5);
		for (int i = 0; i < 5; i++) {
			UryuSeeleRenderer.Seele s = pg.seeles[i];
			double hy = s != null ? s.y + UryuSeeleRenderer.lift(s, now) : pg.cy + SprengerFxPlan.HILT_HEIGHT;
			for (int k = 0; k < 4; k++) {
				double a = rd.nextDouble() * Math.PI * 2;
				double sp = 2.0 + rd.nextDouble() * 3.0;
				FxParticles.spec(FxParticles.Kind.FLECHETTE).at(pg.px[i], hy - 0.4, pg.pz[i]).vel(Math.cos(a) * sp, 1.5 + rd.nextDouble() * 2.5, Math.sin(a) * sp).drag(0.93).gravity(0.6)
						.life(22 + rd.nextInt(8)).size(0.32 + rd.nextDouble() * 0.12).color(UryuPalette.Q3).seed(rd.nextInt(3)).spin((rd.nextDouble() - 0.5) * 0.6).spawn();
			}
		}
		UryuSeeleRenderer.removeGroup(pg.group);
		ClientPlayerEntity me = me();
		EffectTimeline ex = FxTimelines.create(73, pg.seed ^ 0x5DEECE66DL, new Vec3d(pg.cx, pg.cy, pg.cz), new Vec3d(0, 0, 1), new float[] {(float) pg.r, early ? 1f : 0f}, pg.owner, false);
		pg.exSlot = ex.slot;
		scheduleExplosion(ex, pg);
		FxTimelines.start(ex);
		ReiatsuTest.LOGGER.info("[fx] sprenger explodes{} at ({}, {}, {}) r {} owner {} me {}", early ? " early" : "", pg.cx, pg.cy, pg.cz, pg.r, pg.owner, me != null ? me.getId() : -1);
	}

	private static void scheduleExplosion(EffectTimeline ex, Pentagon pg) {
		SprengerFxPlan.Detail detail = detail();
		ex.lifetime(SprengerFxPlan.AFTERMATH_END);
		ex.at(0.0, x -> explosion(x, pg));
		for (SprengerFxPlan.Sfx s : SprengerFxPlan.EXPLOSION_SOUNDS) {
			if (s.at() > 0.0) {
				ex.at(s.at(), x -> FxSound.play(x, s.id(), pg.cx, pg.cy + 0.5, pg.cz, s.pitch(), s.volume()));
			}
		}
		ex.at(SprengerFxPlan.SHATTER_AT, x -> shatter(x, pg));
		// 0.30 to 1.0 the aftermath: glitter falling, the dust settling
		if (detail.glitter()) {
			int n = (int) Math.floor((SprengerFxPlan.AFTERMATH_TO - SprengerFxPlan.AFTERMATH_FROM) * SprengerFxPlan.AFTERMATH_GLITTER_PER_SECOND);
			for (int k = 0; k < n; k++) {
				final int kk = k;
				ex.at(SprengerFxPlan.AFTERMATH_FROM + k / SprengerFxPlan.AFTERMATH_GLITTER_PER_SECOND, x -> falling(x, pg, kk));
			}
		}
		int d = (int) Math.floor((SprengerFxPlan.AFTERMATH_TO - SprengerFxPlan.AFTERMATH_FROM) * SprengerFxPlan.AFTERMATH_DUST_PER_SECOND);
		for (int k = 0; k < d; k++) {
			final int kk = k;
			ex.at(SprengerFxPlan.AFTERMATH_FROM + k / SprengerFxPlan.AFTERMATH_DUST_PER_SECOND, x -> settle(x, pg, kk));
		}
	}

	/** t 0 of the explosion: hit stop, the column, the ragged edge, the rays, the Zeichen flare, the shock ring, dust, motes, sparks, sounds, the screen. */
	private static void explosion(EffectTimeline x, Pentagon pg) {
		SprengerFxPlan.Detail detail = detail();
		double r = pg.r;
		double k = r / SprengerFxPlan.REFERENCE_RADIUS;
		boolean early = pg.early;
		double cx = pg.cx;
		double cz = pg.cz;
		double cy = pg.cy;
		Random rnd = x.rng(1);
		x.hitstop(SprengerFxPlan.HITSTOP_MS);
		double coreK = early ? SprengerFxPlan.EARLY_CORE_INTENSITY : SprengerFxPlan.NORMAL_CORE_INTENSITY;
		String coreCol = early ? UryuPalette.Q0 : UryuPalette.Q1;
		// the column filling the pentagon: a core wall, an outer wall and a bright axis, 0 to 40 high in 0.12 s, then narrowing to 0.3
		FxShapes.Wall core = FxShapes.wall(cx, cz, cy, 16, 0).radius(a -> SprengerFxPlan.coreRadius(r) * SprengerFxPlan.columnWidthScale(a)).height(SprengerFxPlan::columnHeight)
				.topFade(0.7).scroll(3.0, 0.15);
		core.color(coreCol).life(SprengerFxPlan.COLUMN_LIFE).intensity(a -> tune("spr.colCore", 0.55) * coreK * SprengerFxPlan.columnIntensity(a)).owner(x).cell(GlowSprite.PILLAR);
		FxShapes.Wall outer = FxShapes.wall(cx, cz, cy, 16, 0).radius(a -> r * SprengerFxPlan.columnWidthScale(a)).height(SprengerFxPlan::columnHeight).topFade(0.5).scroll(2.0, 0.2);
		outer.color(early ? UryuPalette.Q2 : UryuPalette.Q3).life(SprengerFxPlan.COLUMN_LIFE).intensity(a -> tune("spr.colOuter", 0.35) * SprengerFxPlan.columnIntensity(a)).owner(x)
				.cell(GlowSprite.PILLAR);
		FxShapes.Beam axis = FxShapes.beam(cx, cz, cy, 0).height(SprengerFxPlan::columnHeight).width(a -> 2.6 * k * SprengerFxPlan.columnWidthScale(a)).topFade(0.6).scroll(4.0, 0.1);
		axis.color(UryuPalette.Q0).life(SprengerFxPlan.COLUMN_LIFE).intensity(a -> tune("spr.colAxis", 0.8) * coreK * SprengerFxPlan.columnIntensity(a)).owner(x);
		// the ragged edge: narrower beams of different heights at seeded points inside the pentagon, staggered by 0.02 s
		for (SprengerFxPlan.Ragged g : SprengerFxPlan.ragged(pg.seed, r, detail.ragged())) {
			FxShapes.Beam rag = FxShapes.beam(cx + g.dx(), cz + g.dz(), cy, g.delay()).height(a -> g.height() * FxMath.oe(a / 0.1)).width(a -> 2.0 * g.radius() * (1.0 - 0.5 * FxMath.clamp(a / 0.5)))
					.topFade(0.4).scroll(3.0, 0.2);
			rag.color(early ? UryuPalette.Q0 : UryuPalette.Q2).life(0.6).intensity(a -> 0.6 * SprengerFxPlan.columnIntensity(a * 1.25)).owner(x);
		}
		// ground: rays filling the pentagon, the floor flash, the body flash
		FxGlowBatch.sprite(GlowSprite.BURST_RAYS).at(cx, cy + 0.12, cz).ground().size(SprengerFxPlan.RAYS_SIZE * k).life(SprengerFxPlan.RAYS_LIFE).curve(0.01, 0.7).rot(rnd.nextDouble() * 6.28, 0.4)
				.color(UryuPalette.Q1).peak(0.9).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(cx, cy + 0.1, cz).ground().size(SprengerFxPlan.FLOOR_SIZE * k * 1.2).life(0.5).curve(0.01, 0.7).color(coreCol).peak(0.9).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(cx, cy + 1.2, cz).size(r * 3.0 * 0.8, r * 3.0).life(0.4).curve(0.01, 0.7).color(UryuPalette.Q0).peak(1.0).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.STAR6).at(cx, cy + 1.0, cz).size(r * 2.0, r * 0.4).life(0.25).curve(0.01, 0.7).color(UryuPalette.Q0).peak(1.0).rot(0.3, 1.5).owner(x).spawn();
		if (early) {
			// early detonation (a fully focused arrow touched the figure): the core burns white, a diamond star sits where the arrow went in, a second thin shock ring runs ahead
			FxGlowBatch.sprite(GlowSprite.DIAMOND_STAR).at(cx, cy + 1.4, cz).size(r * 1.8, r * 0.5).life(0.3).curve(0.01, 0.7).color(UryuPalette.Q0).peak(1.0).rot(0.0, 0.6).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(cx, cy + 0.14, cz).ground().size(0.2, SprengerFxPlan.shockRingSize(r) * 1.15 / 0.875).sizeEase(FxMath.OC).life(0.3)
					.curve(0.01, 0.7).color(UryuPalette.Q0).peak(0.9).owner(x).spawn();
		}
		// the Zeichen flare (the pentagram with a ring at each point, apex towards -z like the figure) and its five ray streaks
		FxGlowBatch.sprite(GlowSprite.ZEICHEN_STAR).at(cx, cy + 0.14, cz).ground().size(SprengerFxPlan.flareSize(r, early)).life(SprengerFxPlan.FLARE_LIFE).curve(0.02, 0.9)
				.rot(Math.PI, reduced() ? 0.0 : SprengerFxPlan.FLARE_TURNS * Math.PI * 2).color(UryuPalette.Q1).peak(SprengerFxPlan.FLARE_PEAK).owner(x).spawn();
		for (int i = 0; i < 5; i++) {
			double ang = SprengerFxPlan.vertexAngle(i);
			FxShapes.Strip ray = FxShapes.strip(pg.px[i], cy + 0.16, pg.pz[i], Math.cos(ang), 0, Math.sin(ang), 0).length(a -> SprengerFxPlan.rayLength(a, r))
					.width(a -> SprengerFxPlan.RAY_WIDTH * 2.4 * (1.0 - 0.6 * FxMath.clamp(a / SprengerFxPlan.RAY_LIFE)));
			ray.color(UryuPalette.Q3).life(SprengerFxPlan.RAY_LIFE).intensity(a -> 0.9 * (1.0 - FxMath.clamp(a / SprengerFxPlan.RAY_LIFE))).owner(x).cell(GlowSprite.STREAK);
		}
		// the ground shock ring (radius 0 to 9, 0.4 s, easeOutCubic) and a thin white one behind it
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(cx, cy + 0.1, cz).ground().size(0.2, SprengerFxPlan.shockRingSize(r) / 0.75).sizeEase(FxMath.OC).life(SprengerFxPlan.SHOCK_RING_LIFE)
				.curve(0.01, 0.7).color(UryuPalette.Q2).peak(0.9).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(cx, cy + 0.12, cz).ground().size(0.2, SprengerFxPlan.shockRingSize(r) / 0.875 * 0.85).sizeEase(FxMath.OC).life(SprengerFxPlan.SHOCK_RING_LIFE + 0.1)
				.curve(0.01, 0.7).color(UryuPalette.Q0).peak(0.7).owner(x).spawn();
		// dust ring: puffs from r 3.2 outward at 7 b/s curling up; motes and sparks radial and up
		for (int i = 0; i < detail.dust(); i++) {
			double a = Math.PI * 2 * i / detail.dust() + rnd.nextDouble() * 0.15;
			double sp = 7.0 * (0.8 + 0.4 * rnd.nextDouble());
			FxParticles.spec(FxParticles.Kind.DUST_PUFF).at(cx + Math.cos(a) * r * 1.07, cy + 0.2, cz + Math.sin(a) * r * 1.07)
					.vel(Math.cos(a) * sp, 1.2 * (0.7 + 0.6 * rnd.nextDouble()), Math.sin(a) * sp).drag(0.9).life(24).size(1.2 + rnd.nextDouble() * 0.5, 2.8).color(UryuPalette.Q9).alpha(0.6)
					.seed(rnd.nextInt(4)).fade(0.45).owner(x).spawn();
		}
		for (int i = 0; i < detail.motes(); i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double rad = Math.sqrt(rnd.nextDouble()) * r * 0.9;
			double sp = 8.0 * (0.4 + 0.6 * rnd.nextDouble());
			double up = 0.3 + rnd.nextDouble() * 0.7;
			FxParticles.spec(FxParticles.Kind.REISHI_MOTE).at(cx + Math.cos(a) * rad, cy + 0.3, cz + Math.sin(a) * rad).vel(Math.cos(a) * sp * (1.0 - up * 0.5), sp * up, Math.sin(a) * sp * (1.0 - up * 0.5))
					.drag(0.92).life(20).size(0.35 + rnd.nextDouble() * 0.15).color(UryuPalette.Q2).colorTo(UryuPalette.Q0).seed(rnd.nextInt(4)).fade(0.4).owner(x).spawn();
		}
		for (int i = 0; i < detail.stars(); i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double rad = Math.sqrt(rnd.nextDouble()) * r * 0.95;
			FxGlowBatch.sprite(GlowSprite.STAR4).at(cx + Math.cos(a) * rad, cy + 0.4 + rnd.nextDouble() * 1.0, cz + Math.sin(a) * rad)
					.vel(Math.cos(a) * 2.0, 3.0 + rnd.nextDouble() * 6.0, Math.sin(a) * 2.0).drag(0.94).size(0.3 + rnd.nextDouble() * 0.4).life(0.4 + rnd.nextDouble() * 0.3).curve(0.02, 0.6)
					.color(UryuPalette.Q0).peak(0.9).twinkle().rot(rnd.nextDouble() * 6.28, (rnd.nextDouble() - 0.5) * 3).owner(x).spawn();
		}
		// the sounds that start now (the crystal breaks at 0.07 and 0.14 s are scheduled)
		for (SprengerFxPlan.Sfx s : SprengerFxPlan.EXPLOSION_SOUNDS) {
			if (s.at() <= 0.0) {
				FxSound.play(x, s.id(), cx, cy + 0.5, cz, s.pitch(), s.volume());
			}
		}
		// the screen: flash by distance (the owner and the bound feel it fully), shake, the owner's FOV kick, an impact frame for the bound
		if (x.screen) {
			boolean inv = involved(pg) || UryuBindFx.localJustExploded(FxClock.now);
			double f = SprengerFxPlan.screenFactor(inv, edgeDistance(pg));
			ScreenFx.flash(early ? SprengerFxPlan.FLASH_EARLY : SprengerFxPlan.FLASH, Q1, false, f);
			ScreenFx.shake(SprengerFxPlan.SHAKE_AMPLITUDE, SprengerFxPlan.SHAKE_TIME, f);
			if (mine(pg)) {
				ScreenFx.shunpoRush(0.0, SprengerFxPlan.FOV_KICK, SprengerFxPlan.FOV_KICK_TIME);
			}
			if (UryuBindFx.localJustExploded(FxClock.now)) {
				ScreenFx.impactFrame(1.0);
			}
		}
	}

	/** 0.15 after the burst: the fence shatters into falling flakes, the grade lets go, a scar stays on the ground. */
	private static void shatter(EffectTimeline x, Pentagon pg) {
		pg.shattered = true;
		Random r = x.rng(3);
		for (int i = 0; i < SprengerFxPlan.SHATTER_FLAKES; i++) {
			int e = r.nextInt(5);
			int e2 = (e + 1) % 5;
			double u = r.nextDouble();
			double px = pg.px[e] + (pg.px[e2] - pg.px[e]) * u;
			double pz = pg.pz[e] + (pg.pz[e2] - pg.pz[e]) * u;
			FxParticles.spec(FxParticles.Kind.FLECHETTE).at(px, pg.cy + 0.3 + r.nextDouble() * 2.6, pz).vel((r.nextDouble() - 0.5) * 1.5, 0.3 + r.nextDouble(), (r.nextDouble() - 0.5) * 1.5)
					.drag(0.95).gravity(1.0).life(24 + r.nextInt(10)).size(0.34 + r.nextDouble() * 0.1).color(UryuPalette.Q3).seed(r.nextInt(3)).spin((r.nextDouble() - 0.5) * 0.6).owner(x).spawn();
		}
		FxSound.play(x, SprengerFxPlan.SHATTER_SOUND.id(), pg.cx, pg.cy + 1.0, pg.cz, SprengerFxPlan.SHATTER_SOUND.pitch(), SprengerFxPlan.SHATTER_SOUND.volume());
		if (pg.grade != null) {
			pg.grade.release(SprengerFxPlan.GRADE_FADE);
			pg.grade = null;
		}
		FxDecals.create(FxDecals.PENT_SCAR, pg.cx, pg.cz, pg.cy, SprengerFxPlan.scarRadius(pg.r), 0.0).reveal(0.2).alpha(SprengerFxPlan.SCAR_ALPHA).tint("#FFFFFF")
				.fade(0.5, SprengerFxPlan.SCAR_LIFE - 0.5);
	}

	private static void falling(EffectTimeline x, Pentagon pg, int k) {
		Random r = x.rng(300 + k);
		double a = r.nextDouble() * Math.PI * 2;
		double rad = Math.sqrt(r.nextDouble()) * pg.r * 0.9;
		FxGlowBatch.sprite(GlowSprite.GLITTER).at(pg.cx + Math.cos(a) * rad, pg.cy + 2.0 + r.nextDouble() * 2.5, pg.cz + Math.sin(a) * rad).vel(0, -0.5, 0).gravity(1.0).size(0.3).life(0.9)
				.curve(0.05, 0.5).color(UryuPalette.Q2).peak(0.7 * (1.0 - 0.5 * k / 14.0)).twinkle().rot(r.nextDouble() * 6.28, 0).owner(x).spawn();
	}

	private static void settle(EffectTimeline x, Pentagon pg, int k) {
		Random r = x.rng(400 + k);
		double a = r.nextDouble() * Math.PI * 2;
		double rad = pg.r * (0.9 + r.nextDouble() * 0.6);
		FxParticles.spec(FxParticles.Kind.DUST_PUFF).at(pg.cx + Math.cos(a) * rad, pg.cy + 0.2, pg.cz + Math.sin(a) * rad).vel(Math.cos(a) * 0.8, 0.5, Math.sin(a) * 0.8).drag(0.95).life(30)
				.size(1.0, 1.8).color(UryuPalette.Q9).alpha(0.35).seed(r.nextInt(4)).fade(0.5).owner(x).spawn();
	}

	// ------------------------------------------------------------------ per frame

	/** Per frame (render START): the daggers' tone follows the pulse, cancelled daggers sink, pentagons that were not told to explode are let go, old ones go. */
	static void frame(double now) {
		if (MinecraftClient.getInstance().world == null) {
			if (!PENTAGONS.isEmpty()) {
				PENTAGONS.clear();
			}
			UryuBindFx.clear();
			return;
		}
		boolean rm = reduced();
		for (Pentagon p : PENTAGONS) {
			if (p.exploded < 0 && p.cancelled < 0) {
				double age = now - p.born;
				if (age > SprengerFxPlan.cancelTimeout(p.life)) {
					cancel(p); // the BURST never came
					continue;
				}
				double pulse01 = 0.5 + 0.5 * (SprengerFxPlan.pulse(age, p.life, rm) - 0.65) / 0.35;
				double dim = SprengerFxPlan.dim(age);
				int tone = UryuSeeleRenderer.tone((0.55 + 0.45 * FxMath.clamp(pulse01)) * (0.35 + 0.65 * FxMath.clamp((dim - 0.2) / 0.8)));
				for (UryuSeeleRenderer.Seele s : p.seeles) {
					if (s != null) {
						s.color = tone;
					}
				}
			} else if (p.cancelled >= 0) {
				double c = now - p.cancelled;
				for (int i = 0; i < 5; i++) {
					UryuSeeleRenderer.Seele s = p.seeles[i];
					if (s != null) {
						s.y = p.baseY[i] - SprengerFxPlan.sinkDepth(c);
						s.life = Math.min(s.life, p.cancelled - s.born + SprengerFxPlan.CANCEL_SINK + 0.02); // shrinks away in its last 0.15 s
					}
				}
			}
		}
		PENTAGONS.removeIf(p -> p.exploded >= 0 ? now - p.exploded > SprengerFxPlan.AFTERMATH_END + 0.4
				: p.cancelled >= 0 ? now - p.cancelled > SprengerFxPlan.CANCEL_SINK + SprengerFxPlan.CANCEL_FADE + 0.3 : now - p.born > p.life + 3.0);
		UryuBindFx.frame(now);
	}

	// ------------------------------------------------------------------ the glow of the figure

	/** The glow of the pentagons (daggers' cards, hub, spokes, edges, floor, bind flash) and of the roots on the bound entities. */
	static void emitGlow(FxGlowBatch.Emitter e, double now) {
		boolean rm = reduced();
		for (Pentagon p : PENTAGONS) {
			double dist = Math.hypot(p.cx - e.cx, p.cz - e.cz);
			if (dist > 110.0) {
				continue;
			}
			if (p.exploded >= 0) {
				double since = now - p.exploded - FxTimelines.held(p.exSlot);
				double fade = SprengerFxPlan.fadeOut(since - SprengerFxPlan.SHATTER_AT, SprengerFxPlan.EDGE_FADE);
				double blast = SprengerFxPlan.edgeBlast(since) * (p.early ? 1.3 : 1.0);
				if (fade > 0.004 || blast > 0.004) {
					double a = Math.max(0.0, p.exploded - p.born);
					figure(e, p, now, a, SprengerFxPlan.edgeIntensity(a, p.life, rm) * fade, fade, false, 0.0, blast, rm);
				}
			} else if (p.cancelled >= 0) {
				double c = now - p.cancelled;
				double a = Math.max(0.0, p.cancelled - p.born);
				double fade = SprengerFxPlan.fadeOut(c, SprengerFxPlan.CANCEL_FADE);
				if (fade > 0.004) {
					figure(e, p, now, a, SprengerFxPlan.edgeIntensity(a, p.life, rm) * fade, fade, true, SprengerFxPlan.sinkDepth(c), 0.0, rm);
				}
			} else {
				double a = now - p.born;
				double dim = SprengerFxPlan.dim(a);
				figure(e, p, now, a, SprengerFxPlan.edgeIntensity(a, p.life, rm) * dim, dim, true, 0.0, 0.0, rm);
			}
		}
		UryuBindFx.emit(e, now);
	}

	/**
	 * One pentagon at figure time {@code a}: {@code inten} is the brightness of the lines, {@code fade} the brightness of the rest, {@code daggers}
	 * says whether the daggers still stand (their glow cards and fall streaks), {@code sink} how far they have sunk, {@code blast} the white
	 * flash of the edges, spokes and corners at the explosion ({@link SprengerFxPlan#edgeBlast}).
	 */
	private static void figure(FxGlowBatch.Emitter e, Pentagon p, double now, double a, double inten, double fade, boolean daggers, double sink, double blast, boolean rm) {
		double k = p.r / SprengerFxPlan.REFERENCE_RADIUS;
		double y = p.cy + 0.09;
		double lineW = tune("spr.edgeWidth", 0.5);
		float[] c7 = Q7;
		// the floor inside turns saturated blue
		double fill = SprengerFxPlan.floorFill(a) * tune("spr.floorGain", 1.8);
		if (fill > 0.004) {
			double pulse01 = 0.8 + 0.2 * (SprengerFxPlan.pulse(a, p.life, rm) - 0.65) / 0.35;
			e.ground(GlowSprite.GLOW_SOFT, p.cx, p.cy + 0.05, p.cz, SprengerFxPlan.FLOOR_SIZE * k, SprengerFxPlan.FLOOR_SIZE * k, 0.0, Q8[0], Q8[1], Q8[2], fill * pulse01 * fade);
		}
		// the bind: a six-point flare at the hub and a ring wave that runs out to the daggers (NEVER the Zeichen: the star belongs to the explosion)
		double hub = daggers ? SprengerFxPlan.hubFlare(a) : 0.0; // an exploded figure holds its last pose: no frozen flash
		if (hub > 0.004) {
			e.sprite(GlowSprite.GLOW_CORE, p.cx, y + 0.3, p.cz, 2.2 * k, 0.0, Q0[0], Q0[1], Q0[2], 0.8 * hub * fade);
			e.sprite(GlowSprite.STAR6, p.cx, y + 0.3, p.cz, 2.6 * k, rm ? 0.0 : a * 3.0, Q1[0], Q1[1], Q1[2], 1.3 * hub * fade);
		}
		double wave = daggers ? SprengerFxPlan.bindWave(a) : 0.0;
		if (wave > 0.004) {
			double wr = SprengerFxPlan.bindWaveRadius(a, p.r);
			e.ground(GlowSprite.RING_THIN, p.cx, y + 0.03, p.cz, wr / 0.4375, wr / 0.4375, 0.0, Q1[0], Q1[1], Q1[2], wave * fade);
			e.ground(GlowSprite.RING_SOFT, p.cx, y + 0.02, p.cz, wr / 0.375, wr / 0.375, 0.0, Q2[0], Q2[1], Q2[2], 0.45 * wave * fade);
		}
		// daggers: the falling streak and the glow card at the hilt
		if (daggers) {
			for (int i = 0; i < 5; i++) {
				UryuSeeleRenderer.Seele s = p.seeles[i];
				if (s == null) {
					continue;
				}
				double li = a - SprengerFxPlan.dropAt(i);
				if (li < 0.0) {
					continue;
				}
				double hx = s.x;
				double hy = s.y + UryuSeeleRenderer.lift(s, now);
				double hz = s.z;
				if (li < SprengerFxPlan.DAGGER_DROP + 0.1) {
					double sf = li < SprengerFxPlan.DAGGER_DROP ? 1.0 : 1.0 - (li - SprengerFxPlan.DAGGER_DROP) / 0.1;
					e.streak(GlowSprite.STREAK, hx, hy + 0.3, hz, hx, hy + 0.3 + SprengerFxPlan.FALL_STREAK, hz, 0.14 * 3.0, Q1[0], Q1[1], Q1[2], 0.8 * sf * fade);
				}
				double hold = FxMath.clamp(li / 0.1);
				double pulse01 = 0.5 + 0.5 * (SprengerFxPlan.pulse(a, p.life, rm) - 0.65) / 0.35;
				double card = hold * fade * (0.7 + 0.3 * pulse01);
				e.sprite(GlowSprite.GLOW_SOFT, hx, hy + 0.1, hz, 0.9, 0.0, Q2[0], Q2[1], Q2[2], 0.5 * card);
				double tw = rm ? 1.0 : 0.6 + 0.4 * Math.sin(2.0 * Math.PI * 6.0 * a + i * 1.3);
				e.sprite(GlowSprite.STAR4, hx, hy + 0.1, hz, 0.5, a * 0.5, Q0[0], Q0[1], Q0[2], 0.7 * card * tw);
			}
		}
		// the hub (a rotating six-point star) and the spokes that run to the daggers
		double hubK = FxMath.clamp((a - SprengerFxPlan.SPOKE_AT) / 0.1);
		if (hubK > 0.0) {
			e.sprite(GlowSprite.GLOW_CORE, p.cx, y + 0.1, p.cz, 0.8 * k, 0.0, Q2[0], Q2[1], Q2[2], 0.6 * hubK * inten);
			e.sprite(GlowSprite.STAR6, p.cx, y + 0.12, p.cz, 0.9 * k, rm ? 0.0 : a * 1.6, Q1[0], Q1[1], Q1[2], 0.95 * hubK * Math.min(1.0, inten + 0.3 * fade));
		}
		double len = SprengerFxPlan.spokeLength(a, p.r);
		if (len > 0.02) {
			boolean arrived = len >= p.r - 1e-6;
			double beadK = arrived ? FxMath.clamp((a - SprengerFxPlan.spokesDoneAt(p.r)) / 0.08) : 0.0;
			for (int i = 0; i < 5; i++) {
				double ux = (p.px[i] - p.cx) / p.r;
				double uz = (p.pz[i] - p.cz) / p.r;
				double hx = p.cx + ux * len;
				double hz = p.cz + uz * len;
				e.streak(GlowSprite.FLECHETTE_TRAIL, hx, y, hz, p.cx, y, p.cz, lineW * 1.1, c7[0], c7[1], c7[2], 0.85 * inten);
				e.streak(GlowSprite.LINE, hx, y + 0.01, hz, p.cx, y + 0.01, p.cz, lineW * 0.4, Q0[0], Q0[1], Q0[2], 0.8 * inten);
				if (blast > 0.004) {
					e.streak(GlowSprite.LINE, hx, y + 0.02, hz, p.cx, y + 0.02, p.cz, lineW * 2.6, Q0[0], Q0[1], Q0[2], 0.9 * blast);
				}
				if (!arrived) {
					e.sprite(GlowSprite.GLOW_CORE, hx, y + 0.08, hz, 0.4, 0.0, Q1[0], Q1[1], Q1[2], 0.9 * fade);
				} else {
					// the bead at the dagger, and the star of the bind flash
					e.sprite(GlowSprite.GLOW_CORE, p.px[i], y + 0.1, p.pz[i], 0.35, 0.0, Q1[0], Q1[1], Q1[2], 0.9 * beadK * Math.min(1.0, inten + 0.2));
					double bf = SprengerFxPlan.bindFlash(a);
					if (bf > 0.004) {
						e.sprite(GlowSprite.STAR4, p.px[i], y + 0.15, p.pz[i], 0.6, a, Q1[0], Q1[1], Q1[2], 0.9 * bf * fade);
					}
				}
			}
		}
		// the five edges draw in order, a bright head runs along each
		for (int i = 0; i < 5; i++) {
			double prog = SprengerFxPlan.edgeProgress(i, a);
			if (prog <= 0.0) {
				continue;
			}
			int j = (i + 1) % 5;
			double hx = p.px[i] + (p.px[j] - p.px[i]) * prog;
			double hz = p.pz[i] + (p.pz[j] - p.pz[i]) * prog;
			e.streak(GlowSprite.LINE, hx, y, hz, p.px[i], y, p.pz[i], lineW * 2.2, Q3[0], Q3[1], Q3[2], 0.28 * inten);
			e.streak(GlowSprite.LINE, hx, y, hz, p.px[i], y, p.pz[i], lineW, c7[0], c7[1], c7[2], 0.95 * inten);
			e.streak(GlowSprite.LINE, hx, y + 0.01, hz, p.px[i], y + 0.01, p.pz[i], lineW * 0.4, Q0[0], Q0[1], Q0[2], 0.9 * inten);
			// the same edge as a flat band on the floor: it reads from above, where the camera facing lines are only a hairline
			double ex = hx - p.px[i];
			double ez = hz - p.pz[i];
			e.ground(GlowSprite.LINE, (hx + p.px[i]) * 0.5, y - 0.01, (hz + p.pz[i]) * 0.5, Math.hypot(ex, ez), lineW * 2.0, Math.atan2(ez, ex), Q3[0], Q3[1], Q3[2], 0.3 * inten);
			if (blast > 0.004) {
				e.streak(GlowSprite.LINE, hx, y + 0.02, hz, p.px[i], y + 0.02, p.pz[i], lineW * 2.6, Q0[0], Q0[1], Q0[2], 0.9 * blast);
			}
			if (prog < 1.0) {
				double dx = (p.px[j] - p.px[i]) / SprengerFxPlan.edgeLength(p.r);
				double dz = (p.pz[j] - p.pz[i]) / SprengerFxPlan.edgeLength(p.r);
				e.streak(GlowSprite.STREAK, hx, y + 0.02, hz, hx - dx * 0.9, y + 0.02, hz - dz * 0.9, 0.5, Q0[0], Q0[1], Q0[2], fade);
				e.sprite(GlowSprite.GLOW_CORE, hx, y + 0.1, hz, 0.5, 0.0, Q0[0], Q0[1], Q0[2], fade);
			}
		}
		// a bead on each corner once its edge is drawn (the corner is where two edges meet)
		for (int i = 0; i < 5; i++) {
			if (SprengerFxPlan.edgeProgress(i, a) >= 1.0 || SprengerFxPlan.edgeProgress((i + 4) % 5, a) >= 1.0) {
				e.sprite(GlowSprite.GLOW_CORE, p.px[i], y + 0.1, p.pz[i], 0.3, 0.0, Q1[0], Q1[1], Q1[2], 0.8 * inten);
				if (blast > 0.004) {
					// the dagger of this corner bursts
					e.sprite(GlowSprite.STAR4, p.px[i], y + 0.9, p.pz[i], 1.6 * k, a * 0.5, Q0[0], Q0[1], Q0[2], 0.95 * blast);
					e.sprite(GlowSprite.GLOW_SOFT, p.px[i], y + 0.9, p.pz[i], 2.2 * k, 0.0, Q2[0], Q2[1], Q2[2], 0.6 * blast);
				}
			}
		}
	}

	// ------------------------------------------------------------------ the fence (own pass: curtains along the edges)

	/** One curtain to draw: the edge and its height, brightness and rune bands. */
	private record Curtain(Pentagon p, double height, double alpha, double age) {
	}

	/** WorldRenderEvents.LAST (registered after the glow batch, so it draws over it): the five fence curtains of every pentagon that has bound its legs. */
	private static void drawCurtains(WorldRenderContext ctx) {
		lastCurtains = 0;
		if (PENTAGONS.isEmpty()) {
			return;
		}
		double now = FxClock.now;
		boolean rm = reduced();
		double gain = tune("spr.fenceGain", 2.2);
		List<Curtain> list = new ArrayList<>();
		for (Pentagon p : PENTAGONS) {
			double age;
			double fade = 1.0;
			if (p.cancelled >= 0) {
				age = p.cancelled - p.born;
				fade = SprengerFxPlan.fadeOut(now - p.cancelled, SprengerFxPlan.CANCEL_FADE);
			} else if (p.exploded >= 0) {
				age = p.exploded - p.born;
				double since = now - p.exploded - FxTimelines.held(p.exSlot);
				fade = since >= SprengerFxPlan.SHATTER_AT ? 0.0 : 1.0; // the curtains shatter into flakes (the particles are the shatter)
			} else {
				age = now - p.born;
				fade = SprengerFxPlan.dim(age);
			}
			double h = SprengerFxPlan.fenceHeight(age);
			if (h <= 0.02 || fade <= 0.004) {
				continue;
			}
			double breath = 0.85 + 0.15 * SprengerFxPlan.pulse(now - p.born, p.life, rm);
			list.add(new Curtain(p, h, SprengerFxPlan.FENCE_ALPHA * gain * fade * breath * FxMath.clamp((age - SprengerFxPlan.BIND_AT) / 0.08), age));
		}
		if (list.isEmpty()) {
			return;
		}
		Vec3d cam = ctx.camera().getPos();
		double boost = FxConfig.glowIntensity * FxConfig.uryuGlow * (ScreenFx.gradeStrong() ? 1.3 : 1.0);
		boolean runes = detail().runes();
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		int n = 0;
		for (Curtain c : list) {
			Pentagon p = c.p();
			double y0 = p.cy + 0.09;
			for (int i = 0; i < 5; i++) {
				int j = (i + 1) % 5;
				n += quad(buf, cam, GlowSprite.FENCE, p.px[i], y0, p.pz[i], p.px[j], p.pz[j], 0.0, c.height(), Q7, c.alpha() * boost, c.alpha() * boost * 0.7);
				if (runes && c.height() > SprengerFxPlan.FENCE_HEIGHT * 0.5) {
					for (int b = 0; b < SprengerFxPlan.RUNE_BANDS; b++) {
						double phase = SprengerFxPlan.runePhase(c.age() - SprengerFxPlan.BIND_AT, b);
						double yb = phase * (c.height() - SprengerFxPlan.RUNE_HEIGHT);
						double k = c.alpha() * boost * 1.8 * SprengerFxPlan.runeAlpha(phase);
						n += quad(buf, cam, GlowSprite.RUNE_BAND, p.px[i], y0, p.pz[i], p.px[j], p.pz[j], yb, yb + SprengerFxPlan.RUNE_HEIGHT, Q1, k, k);
					}
				}
			}
		}
		lastCurtains = n / 4;
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			FxGlowBatch.layerU().draw(built);
		}
	}

	/** A vertical quad above the ground segment (x0, z0) to (x1, z1) from height {@code h0} to {@code h1} above {@code y}, premultiplied additive. */
	private static int quad(BufferBuilder buf, Vec3d cam, GlowSprite s, double x0, double y, double z0, double x1, double z1, double h0, double h1, float[] c, double kBottom, double kTop) {
		float b0 = (float) Math.min(1.0, kBottom);
		float t0 = (float) Math.min(1.0, kTop);
		buf.vertex((float) (x0 - cam.x), (float) (y + h0 - cam.y), (float) (z0 - cam.z)).texture(s.u0, s.v1).color(c[0] * b0, c[1] * b0, c[2] * b0, 1f);
		buf.vertex((float) (x1 - cam.x), (float) (y + h0 - cam.y), (float) (z1 - cam.z)).texture(s.u1, s.v1).color(c[0] * b0, c[1] * b0, c[2] * b0, 1f);
		buf.vertex((float) (x1 - cam.x), (float) (y + h1 - cam.y), (float) (z1 - cam.z)).texture(s.u1, s.v0).color(c[0] * t0, c[1] * t0, c[2] * t0, 1f);
		buf.vertex((float) (x0 - cam.x), (float) (y + h1 - cam.y), (float) (z0 - cam.z)).texture(s.u0, s.v0).color(c[0] * t0, c[1] * t0, c[2] * t0, 1f);
		return 4;
	}
}
