package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.SeeleFxMath;
import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Seele Schneider combo (B, SHIKAI, effect 74 and the SLASH / IMPACT packets, URYU_VFX_STORYBOARD 2.9, step U-FX 5). The server runs the lunge
 * (3 ticks) and the three slashes (ticks 3, 8, 13); this class draws them:
 * <ul>
 * <li><b>the Reishi blade</b>: the long {@code uryu_seele_long} mesh (0.9 m, scaled {@link SeeleFxMath#BLADE_SCALE}) in the free hand, drawn full bright
 * by its own pass; the hilt slides out of the hip in 0.1 s, the blade grows in 0.08 s, sweeps the 100 degree cone of every slash (right to left, back,
 * then the overhead chop) and breaks into flakes and dashes 0.2 s after the last stroke. A glow strip and a tip glint go with it;</li>
 * <li><b>the strokes</b>: a ribbon of 12 segments behind the blade tip (Q2 body, white core), a crescent wave that leaves the blade and runs out to
 * the reach, and the crescent sprite of the whole stroke as an echo; tip glints and flakes along the arc; the third stroke is wider and cracks the
 * ground (decal, glow crack, ring);</li>
 * <li><b>the lunge</b>: three strips with a white core along the run, a dash stream, the Reishi platform disc under the start point, three cyan
 * afterimages of the player model (tier dependent), the throw sound and a 3 degree FOV kick for the caster;</li>
 * <li><b>the hits</b> ({@code IMPACT} with kind 99, routed here by {@link UryuFx}): rays, star, glow and flakes at the target when the blade crosses it,
 * the knockback burst of the third stroke (radial dashes and a ring), the crit sound.</li>
 * </ul>
 * First person: the pivot is the free hand in camera space, the ribbon width is clipped near the camera ({@link SeeleFxMath#nearClip}); the strokes
 * of the local player follow his own view, those of the others the server packet. Everything follows the caster entity per frame. Timings and
 * shapes are in the pure {@link SeeleFxMath} (tested); Reduce Motion, the screen effect scale and the quality tiers are handled by
 * {@link ScreenFx}, {@link FxConfig} and the particle caps; segments follow the tier. Tuning keys (fx_override.json): {@code uryu.seeleBlade}
 * (blade scale), {@code uryu.seeleGlow} (glow multiplier of this effect).
 */
public final class UryuSeeleFx {
	private static final Identifier TEXTURE = ReiatsuTest.id("textures/item/uryu_fx_diffuse.png");
	private static final int FULL_LIGHT = 0xF000F0;
	private static final int MAX_COMBOS = 8;

	/** One stroke. */
	private static final class Slash {
		final Combo combo;
		final int index;
		final SeeleFxMath.Profile profile;
		final SeeleFxMath.Frame frame;
		final double born;
		final double reach;
		final int hits;
		/** The direction the blade had when the stroke began (the blend of {@link SeeleFxMath#blendDir}). */
		final V3 prevDir;
		int nextGlint;
		boolean impact;

		Slash(Combo combo, int index, SeeleFxMath.Frame frame, double born, double reach, int hits, V3 prevDir) {
			this.combo = combo;
			this.index = index;
			this.profile = SeeleFxMath.profile(index);
			this.frame = frame;
			this.born = born;
			this.reach = reach * profile.reachScale();
			this.hits = hits;
			this.prevDir = prevDir;
		}
	}

	/** One combo of one caster: the blade and its strokes. */
	private static final class Combo {
		int casterId = -1;
		boolean local;
		boolean rightHanded = true;
		double strength = 1.0;
		double lod = 1.0;
		long seed;
		double born;
		EffectTimeline timeline;
		SeeleFxMath.Frame pre;
		double dissolveAt;
		boolean dissolved;
		double deadAt = Double.MAX_VALUE;
		final List<Slash> slashes = new ArrayList<>();
		/** Last known eye position of the caster (used when the entity is not loaded). */
		Vec3d eye;
		// resolved per frame
		boolean fp;
		boolean resolved;
		Vec3d pivot;
		Vec3d hilt;
		Vec3d hip;
		Vec3d tip;
		Vec3d dir;
		Vec3d normal;
		double len;
		double age;
		V3 lastDir;

		Slash last() {
			return slashes.isEmpty() ? null : slashes.get(slashes.size() - 1);
		}
	}

	/** A cyan copy of the player model standing in the run. */
	private static final class Image {
		double x;
		double y;
		double z;
		float yaw;
		double born;
		Identifier texture;
		boolean slim;
		boolean local;
	}

	private static final List<Combo> COMBOS = new ArrayList<>();
	private static final List<Image> IMAGES = new ArrayList<>();
	private static boolean inited;
	private static FxMesh mesh;
	private static boolean meshTried;
	private static RenderLayer layer;
	private static PlayerEntityModel<AbstractClientPlayerEntity> wide;
	private static PlayerEntityModel<AbstractClientPlayerEntity> slim;

	/** Harness statistics (reset by {@link #resetStats()}). */
	public static int bladesDrawn;
	public static int combosStarted;
	public static int slashesSeen;
	public static int hitsSeen;
	public static int lastSegments;

	private UryuSeeleFx() {
	}

	static void init() {
		if (inited) {
			return;
		}
		inited = true;
		WorldRenderEvents.AFTER_ENTITIES.register(UryuSeeleFx::draw);
	}

	/** Dev/harness: forgets every combo and copy. */
	public static void clear() {
		COMBOS.clear();
		IMAGES.clear();
	}

	public static void resetStats() {
		combosStarted = 0;
		slashesSeen = 0;
		hitsSeen = 0;
	}

	public static int liveCombos() {
		return COMBOS.size();
	}

	/** Harness: the number of strokes of the newest combo that are still drawing and its blade length (0..1), or -1 without a combo. */
	public static double bladeLength() {
		return COMBOS.isEmpty() ? -1.0 : COMBOS.get(COMBOS.size() - 1).len;
	}

	// ============================================================================================================ helpers

	private static V3 v3(Vec3d v) {
		return new V3(v.x, v.y, v.z);
	}

	private static Vec3d vec(V3 v) {
		return new Vec3d(v.x(), v.y(), v.z());
	}

	private static double glow() {
		return FxTune.d("uryu.seeleGlow", 1.0);
	}

	private static double bladeScale() {
		return FxTune.d("uryu.seeleBlade", SeeleFxMath.BLADE_SCALE);
	}

	private static int tier() {
		return FxConfig.tier().ordinal();
	}

	private static float tickDelta() {
		return MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
	}

	private static EffectTimeline timelineOf(int casterId) {
		for (EffectTimeline t : FxTimelines.active()) {
			if (t.effectId == 74 && t.casterId == casterId && !t.isEnded()) {
				return t;
			}
		}
		return null;
	}

	private static void cues(EffectTimeline from, List<SeeleFxMath.Cue> cues, Vec3d at) {
		for (SeeleFxMath.Cue c : cues) {
			FxSound.play(from, c.id(), at.x, at.y, at.z, c.pitch(), c.volume());
		}
	}

	private static void flake(Combo c, Vec3d p, Vec3d v, int seed, double size, int life, double gravity) {
		FxParticles.spec(FxParticles.Kind.FLECHETTE).at(p.x, p.y, p.z).vel(v.x, v.y, v.z).drag(0.9).gravity(gravity).life(life).size(size)
				.color(UryuPalette.Q2).colorTo(UryuPalette.Q1).fade(0.4).spin(0.35).seed(seed).lod(c == null ? 1.0 : c.lod).owner(c == null ? null : c.timeline).spawn();
	}

	private static double strengthFor(Vec3d at) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) {
			return 1.0;
		}
		return FxMath.screenStrength(mc.gameRenderer.getCamera().getPos().distanceTo(at));
	}

	// ============================================================================================================ lunge

	/** The lunge event (effect 74, t = 0): feet and look of the caster. Creates the combo and plays the run. */
	static void lunge(ClientWorld w, EffectEventS2C e) {
		init();
		Vec3d feet = new Vec3d(e.x(), e.y(), e.z());
		Vec3d look = new Vec3d(e.dx(), e.dy(), e.dz());
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		flat = flat.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : flat.normalize();
		Vec3d look0 = look.lengthSquared() < 1.0E-6 ? flat : look.normalize();
		EffectTimeline t = FxTimelines.create(74, e.seed(), feet, look0, e.params(), e.casterId(), FxClient.forceRemote());
		Combo c = new Combo();
		c.casterId = e.casterId();
		c.local = t.localCaster;
		c.strength = t.strength;
		c.lod = t.lod;
		c.seed = e.seed();
		c.born = FxClock.now;
		c.timeline = t;
		c.dissolveAt = c.born + SeeleFxMath.DISSOLVE_AT;
		c.pre = SeeleFxMath.serverFrame(v3(look0), 0);
		Entity ent = w.getEntityById(e.casterId());
		if (ent instanceof PlayerEntity p) {
			c.rightHanded = p.getMainArm() == Arm.RIGHT;
		}
		c.eye = feet.add(0, 1.62, 0);
		add(c);
		combosStarted++;
		final Vec3d fFlat = flat;
		t.lifetime(SeeleFxMath.COMBO_LIFETIME);
		t.at(0.0, x -> runVisuals(w, c, t, feet, fFlat));
		FxTimelines.start(t);
	}

	private static void add(Combo c) {
		while (COMBOS.size() >= MAX_COMBOS) {
			COMBOS.remove(0);
		}
		COMBOS.add(c);
	}

	/** t = 0: strips, dashes, the platform disc, the afterimages, the sounds and the FOV kick. */
	private static void runVisuals(ClientWorld w, Combo c, EffectTimeline t, Vec3d feet, Vec3d flat) {
		double len = SeeleFxMath.lungeLength();
		// first person: the head strip would run through the middle of the screen and the dashes fly in the face: only the low ones, to the sides
		boolean fp = c.local && !FxClient.forceRemote() && MinecraftClient.getInstance().options.getPerspective().isFirstPerson();
		int tier = tier();
		for (int i = 0; i < SeeleFxMath.LUNGE_HEIGHTS.length; i++) {
			if (fp && i == SeeleFxMath.LUNGE_HEIGHTS.length - 1) {
				continue;
			}
			double y = feet.y + SeeleFxMath.LUNGE_HEIGHTS[i];
			final double fl = len;
			FxShapes.Strip body = FxShapes.strip(feet.x, y, feet.z, flat.x, 0, flat.z, 0).length(a -> fl * FxMath.oc(a / 0.12)).width(a -> 0.24 * (1.0 - 0.5 * FxMath.clamp(a / 0.25)));
			body.cell(GlowSprite.STREAK).color(UryuPalette.Q3).life(0.25).owner(t).intensity(a -> 0.8 * glow() * Math.max(0.0, 1.0 - a / 0.25));
			FxShapes.Strip core = FxShapes.strip(feet.x, y, feet.z, flat.x, 0, flat.z, 0).length(a -> fl * FxMath.oc(a / 0.12)).width(a -> 0.06);
			core.cell(GlowSprite.LINE).color(UryuPalette.Q0).life(0.22).owner(t).intensity(a -> 0.9 * glow() * Math.max(0.0, 1.0 - a / 0.22));
		}
		Random r = t.rng(740);
		// the dash stream behind the runner
		int dashes = SeeleFxMath.tierCount(SeeleFxMath.LUNGE_DASHES, tier);
		for (int i = 0; i < dashes; i++) {
			double f = (double) i / dashes * len;
			double side = (r.nextDouble() - 0.5) * 0.5;
			double h = 0.3 + r.nextDouble() * 1.3;
			if (fp) {
				side = (i % 2 == 0 ? 1 : -1) * (0.35 + r.nextDouble() * 0.4);
				h = 0.2 + r.nextDouble() * 0.8;
			}
			Vec3d p = feet.add(flat.x * f - flat.z * side, h, flat.z * f + flat.x * side);
			FxGlowBatch.sprite(GlowSprite.DASH).at(p.x, p.y, p.z).vel(-flat.x * 7.0, 0.2, -flat.z * 7.0).drag(0.9).streak().streakTime(0.04).size(0.55).delay(f / len * 0.1)
					.life(0.2).curve(0.01, 0.6).color(i % 3 == 0 ? UryuPalette.Q1 : UryuPalette.Q2).peak(0.75 * glow()).owner(t).lod(c.lod).spawn();
		}
		// the Reishi platform at the start point (Hirenkyaku) and the Reishi that gathers at the hip
		FxGlowBatch.sprite(GlowSprite.PLATFORM_DISC).at(feet.x, feet.y + 0.04, feet.z).ground().rot(Math.atan2(flat.z, flat.x), 0).size(SeeleFxMath.DISC_SIZE)
				.life(SeeleFxMath.DISC_LIFE).curve(0.03, 0.6).color(UryuPalette.Q2).peak(0.8 * glow()).owner(t).lod(c.lod).spawn();
		Vec3d hip = feet.add(0, 0.9, 0);
		for (int i = 0; i < SeeleFxMath.tierCount(SeeleFxMath.START_DASHES, tier); i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double up = (r.nextDouble() - 0.5) * 1.2;
			Vec3d from = hip.add(Math.cos(a) * 1.1, up, Math.sin(a) * 1.1);
			Vec3d v = hip.subtract(from).multiply(1.0 / 0.12);
			FxGlowBatch.sprite(GlowSprite.DASH).at(from.x, from.y, from.z).vel(v.x, v.y, v.z).streak().streakTime(0.02).size(0.4).life(0.12).curve(0.01, 0.5)
					.color(UryuPalette.Q2).peak(0.7 * glow()).owner(t).lod(c.lod).spawn();
		}
		// afterimages of the player model: three copies, 0.04 s apart, standing in the run
		spawnImages(w, c, feet, flat);
		cues(t, SeeleFxMath.lungeCues(), feet.add(0, 1.0, 0));
		if (c.local) {
			ScreenFx.shunpoRush(0.0, SeeleFxMath.LUNGE_FOV_DEG, SeeleFxMath.LUNGE_FOV_SECS);
		}
	}

	private static void spawnImages(ClientWorld w, Combo c, Vec3d feet, Vec3d flat) {
		int n = Math.min(3, FxConfig.uryuAfterimages);
		Entity ent = w.getEntityById(c.casterId);
		if (n <= 0 || !(ent instanceof AbstractClientPlayerEntity pl)) {
			return;
		}
		SkinTextures skin = pl.getSkinTextures();
		float yaw = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));
		for (int i = 0; i < n; i++) {
			Image im = new Image();
			double f = i * SeeleFxMath.AFTERIMAGE_STEP;
			im.x = feet.x + flat.x * f;
			im.y = feet.y;
			im.z = feet.z + flat.z * f;
			im.yaw = yaw;
			im.born = FxClock.now + i * SeeleFxMath.AFTERIMAGE_GAP;
			im.texture = skin.texture();
			im.slim = skin.model() == SkinTextures.Model.SLIM;
			im.local = c.local;
			IMAGES.add(im);
		}
		while (IMAGES.size() > 12) {
			IMAGES.remove(0);
		}
	}

	// ============================================================================================================ strokes

	/** One slash of the combo (SLASH packet): the blade sweeps the cone, a crescent stays behind. Data = {eye xyz, look xyz, index, reach, cone degrees, hits}. */
	static void slash(float[] d) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null) {
			return;
		}
		init();
		double now = FxClock.now;
		Vec3d origin = new Vec3d(d[0], d[1], d[2]);
		int index = Math.max(0, Math.min(2, Math.round(d[6])));
		double reach = d[7];
		double cone = d[8];
		int hits = d.length > 9 ? Math.round(d[9]) : 0;
		Combo c = comboFor(w, origin, index, now);
		Vec3d look = new Vec3d(d[3], d[4], d[5]);
		if (c.local && mc.player != null) {
			look = mc.player.getRotationVec(tickDelta()); // the owner sees his own view
		}
		SeeleFxMath.Frame fr = SeeleFxMath.frame(v3(look), SeeleFxMath.profile(index), cone);
		resolve(c, w, now, fr);
		Slash s = new Slash(c, index, fr, now, reach, hits, c.lastDir);
		c.slashes.add(s);
		while (c.slashes.size() > 3) {
			c.slashes.remove(0);
		}
		if (index == 2) {
			c.dissolveAt = Math.max(now + 0.05, SeeleFxMath.dissolveStart(now));
		}
		slashesSeen++;
		// the blade is where the stroke starts: refresh the pose at once so the first frame already shows the stroke
		resolve(c, w, now, fr);

		EffectTimeline t = c.timeline != null && !c.timeline.isEnded() ? c.timeline : timelineOf(c.casterId);
		Vec3d at = c.hilt != null ? c.hilt : origin;
		cues(t, SeeleFxMath.slashCues(index), at);
		SeeleFxMath.Screen sc = SeeleFxMath.screen(index);
		if (c.local || c.strength > 0) {
			ScreenFx.shake(sc.shakeDeg(), sc.shakeSecs(), c.strength);
		}
		// the flash at the hilt when the stroke starts and flakes shed along the arc
		FxGlowBatch.sprite(GlowSprite.STAR4).at(at.x, at.y, at.z).size(0.9).life(0.14).curve(0.01, 0.6).rot(Math.random() * 3, 3.0).color(UryuPalette.Q0).peak(0.9 * glow()).owner(t).lod(c.lod).spawn();
		double rOut = SeeleFxMath.outerRadiusMax(s.reach);
		for (int i = 0; i < SeeleFxMath.ARC_FLAKES; i++) {
			double u = 0.2 + 0.3 * i;
			V3 dr = fr.dir(u);
			Vec3d p = c.pivot.add(vec(dr).multiply(SeeleFxMath.innerRadius(bladeScale())));
			Vec3d tangent = vec(fr.dir(Math.min(1.0, u + 0.1))).subtract(vec(dr)).normalize();
			flake(c, p, vec(dr).multiply(2.0).add(tangent.multiply(3.0)), index * 7 + i, 0.32, 9, 0.0);
		}
		ReiatsuTest.LOGGER.info("[fx] seele slash {} reach {} cone {} hits {} outer {}", index + 1, fmt(s.reach), fmt(cone), hits, fmt(rOut));
	}

	private static String fmt(double v) {
		return String.format(java.util.Locale.ROOT, "%.2f", v);
	}

	/** The combo a slash packet belongs to: the one whose caster stands at the origin; a slash without a lunge event (lost, or a later join) starts its own. */
	private static Combo comboFor(ClientWorld w, Vec3d origin, int index, double now) {
		for (int i = COMBOS.size() - 1; i >= 0; i--) {
			Combo c = COMBOS.get(i);
			if (c.dissolved && now > c.deadAt) {
				continue;
			}
			Vec3d p = casterEye(w, c, 0f);
			if (p != null && p.squaredDistanceTo(origin) < 9.0 && c.slashes.size() <= index) {
				return c;
			}
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		Combo c = new Combo();
		c.born = now - SeeleFxMath.slashTime(index);
		c.dissolveAt = c.born + SeeleFxMath.DISSOLVE_AT;
		c.eye = origin;
		PlayerEntity near = null;
		double best = 6.25;
		for (PlayerEntity p : w.getPlayers()) {
			double dd = p.getPos().add(0, 1.6, 0).squaredDistanceTo(origin);
			if (dd < best) {
				best = dd;
				near = p;
			}
		}
		if (near != null) {
			c.casterId = near.getId();
			c.local = near == mc.player && !FxClient.forceRemote();
			c.rightHanded = near.getMainArm() == Arm.RIGHT;
		}
		c.strength = c.local ? 1.0 : strengthFor(origin);
		c.lod = FxMath.lod(mc.gameRenderer != null && mc.gameRenderer.getCamera() != null ? mc.gameRenderer.getCamera().getPos().distanceTo(origin) : 0);
		c.pre = SeeleFxMath.serverFrame(new V3(0, 0, 1), 0);
		add(c);
		combosStarted++;
		return c;
	}

	private static Vec3d casterEye(ClientWorld w, Combo c, float td) {
		Entity ent = c.casterId >= 0 ? w.getEntityById(c.casterId) : null;
		if (ent != null) {
			return ent.getLerpedPos(td).add(0, ent.getEyeHeight(ent.getPose()), 0);
		}
		return c.eye;
	}

	// ============================================================================================================ hits

	/** An IMPACT packet of kind 99 (a target hit by a stroke), at the centre of the target: the sparks come when the blade crosses it. */
	static void hit(ClientWorld w, double x, double y, double z) {
		init();
		hitsSeen++;
		double now = FxClock.now;
		Vec3d at = new Vec3d(x, y, z);
		Slash best = null;
		double bestAge = 1.0e9;
		for (Combo c : COMBOS) {
			Slash s = c.last();
			if (s == null || c.pivot == null) {
				continue;
			}
			double age = now - s.born;
			if (age < -0.05 || age > 0.3 || c.pivot.distanceTo(at) > s.reach + 2.5) {
				continue;
			}
			if (age < bestAge) {
				best = s;
				bestAge = age;
			}
		}
		if (best == null) {
			spawnHit(w, null, 0, at, null);
			return;
		}
		final Slash s = best;
		Vec3d to = at.subtract(s.combo.pivot);
		double u = SeeleFxMath.crossProgress(s.frame, v3(to));
		double delay = Math.max(0.0, SeeleFxMath.passTime(u) - Math.max(0.0, now - s.born));
		Vec3d from = s.combo.pivot;
		if (delay < 0.004) {
			spawnHit(w, s.combo, s.index, at, from);
		} else {
			FxDelayed.after(delay, () -> spawnHit(MinecraftClient.getInstance().world, s.combo, s.index, at, from));
		}
	}

	private static void spawnHit(ClientWorld w, Combo c, int index, Vec3d p, Vec3d from) {
		if (w == null) {
			return;
		}
		SeeleFxMath.Hit h = SeeleFxMath.hit(index);
		EffectTimeline t = c == null ? null : c.timeline;
		double lod = c == null ? 1.0 : c.lod;
		Random r = new Random(Double.doubleToLongBits(p.x * 31 + p.z) ^ (long) (FxClock.now * 1000));
		double g = glow();
		FxGlowBatch.sprite(GlowSprite.BURST_RAYS).at(p.x, p.y, p.z).size(h.burst()).life(0.14).curve(0.01, 0.7).rot(r.nextDouble() * Math.PI * 2, 0).color(UryuPalette.Q1)
				.peak(0.9 * g).owner(t).lod(lod).spawn();
		FxGlowBatch.sprite(GlowSprite.STAR4).at(p.x, p.y, p.z).size(h.star() * 0.5, h.star()).sizeEase(FxMath.OE).life(0.18).curve(0.01, 0.6).rot(r.nextDouble() * 3, 2.0)
				.color(UryuPalette.Q0).peak(1.0 * g).owner(t).lod(lod).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(p.x, p.y, p.z).size(h.glow() * 0.5, h.glow()).life(0.25).curve(0.02, 0.7).color(UryuPalette.Q3).peak(0.55 * g).owner(t).lod(lod).spawn();
		Vec3d out = from == null ? new Vec3d(0, 0, 1) : new Vec3d(p.x - from.x, 0, p.z - from.z);
		out = out.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : out.normalize();
		for (int i = 0; i < h.flakes(); i++) {
			double a = Math.atan2(out.z, out.x) + (r.nextDouble() - 0.5) * 2.4;
			double sp = 3.0 + r.nextDouble() * 3.0;
			flake(c, p, new Vec3d(Math.cos(a) * sp, 1.0 + r.nextDouble() * 2.0, Math.sin(a) * sp), i, 0.3 + r.nextDouble() * 0.15, 10 + r.nextInt(5), 0.5);
		}
		if (h.dashes() > 0) {
			// the knockback burst of the third stroke: radial dashes, the ground ring under the target
			for (int i = 0; i < h.dashes(); i++) {
				double a = Math.PI * 2 * i / h.dashes() + (r.nextDouble() - 0.5) * 0.3;
				FxGlowBatch.sprite(GlowSprite.DASH).at(p.x, p.y - 0.3, p.z).vel(Math.cos(a) * 9.0, 0.4, Math.sin(a) * 9.0).drag(0.88).streak().streakTime(0.04).size(0.7).life(0.2)
						.curve(0.01, 0.6).color(UryuPalette.Q1).peak(0.9 * g).owner(t).lod(lod).spawn();
			}
		}
		if (h.ring()) {
			double gy = FxGround.sample(w, p.x, p.z, p.y - 0.9);
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(p.x, gy + 0.05, p.z).ground().size(SeeleFxMath.RING_FROM * 2, SeeleFxMath.RING_TO * 1.2).sizeEase(FxMath.OC).life(SeeleFxMath.RING_LIFE)
					.curve(0.02, 0.6).color(UryuPalette.Q2).peak(0.7 * g).owner(t).lod(lod).spawn();
		}
		cues(t, SeeleFxMath.hitCues(index), p);
		SeeleFxMath.Screen sc = SeeleFxMath.screen(index);
		if (sc.hitstopMs() > 0 && t != null && !t.isEnded() && !FxConfig.reduceMotion && !FxConfig.photosensitiveSafe) {
			t.hitstop(sc.hitstopMs());
		}
	}

	// ============================================================================================================ per frame

	/** Per frame (render START): the pose of every blade, tip glints, the ground impact of the chop, the dissolve, and the end of old combos. */
	static void frame(double now) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null) {
			COMBOS.clear();
			IMAGES.clear();
			return;
		}
		for (Iterator<Combo> it = COMBOS.iterator(); it.hasNext();) {
			Combo c = it.next();
			if (now > c.deadAt || now - c.born > 3.0) {
				it.remove();
				continue;
			}
			resolve(c, w, now, null);
			if (!c.resolved) {
				continue;
			}
			Slash s = c.last();
			if (s != null) {
				glints(c, s, now);
				if (s.profile.chop() && !s.impact && SeeleFxMath.progress(now - s.born) >= 0.85) {
					s.impact = true;
					groundImpact(w, c, s);
				}
			}
			if (!c.dissolved && now >= c.dissolveAt) {
				c.dissolved = true;
				c.deadAt = now + SeeleFxMath.DISSOLVE + 0.15;
				dissolve(c);
			}
		}
		for (Iterator<Image> it = IMAGES.iterator(); it.hasNext();) {
			if (now - it.next().born > SeeleFxMath.AFTERIMAGE_LIFE) {
				it.remove();
			}
		}
	}

	/** Sets pivot, hilt, tip, direction and visible length of the blade of a combo for this frame. {@code forced} is the frame of a stroke that is just being created. */
	private static void resolve(Combo c, ClientWorld w, double now, SeeleFxMath.Frame forced) {
		MinecraftClient mc = MinecraftClient.getInstance();
		float td = tickDelta();
		Slash cur = c.last();
		SeeleFxMath.Frame fr = forced != null ? forced : cur != null ? cur.frame : c.pre;
		Vec3d eye = casterEye(w, c, td);
		if (eye == null) {
			return;
		}
		c.eye = eye;
		c.fp = c.local && !FxClient.forceRemote() && mc.options.getPerspective().isFirstPerson();
		double side = SeeleFxMath.offhandSide(c.rightHanded);
		Vec3d look = vec(fr.look());
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		flat = flat.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : flat.normalize();
		Vec3d right = flat.crossProduct(new Vec3d(0, 1, 0)).normalize(); // look x up: the right hand side of the view
		if (c.fp && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null && mc.player != null) {
			Vec3d cam = mc.gameRenderer.getCamera().getPos();
			Vec3d pl = mc.player.getRotationVec(td);
			c.pivot = cam.add(right.multiply(side * SeeleFxMath.FP_SIDE)).add(0, -SeeleFxMath.FP_DOWN, 0).add(pl.multiply(SeeleFxMath.FP_FORWARD));
		} else {
			c.pivot = eye.add(right.multiply(side * SeeleFxMath.SHOULDER_SIDE)).add(0, -SeeleFxMath.SHOULDER_DOWN, 0);
		}
		double t = now - c.born;
		V3 d;
		V3 n;
		if (cur == null) {
			// before the first stroke: the blade waits where the first sweep starts
			d = fr.dir(0);
			n = fr.normal();
		} else {
			double age = now - cur.born;
			d = SeeleFxMath.blendDir(cur.prevDir, cur.frame.dir(SeeleFxMath.progress(age)), age);
			n = cur.frame.normal();
		}
		c.lastDir = d;
		c.dir = vec(d);
		c.normal = vec(n);
		double scale = bladeScale();
		c.len = SeeleFxMath.bladeLength(t, c.dissolveAt - c.born);
		c.hip = c.pivot.add(0, -SeeleFxMath.HIP_DOWN, 0);
		Vec3d hand = c.pivot.add(c.dir.multiply(SeeleFxMath.HILT_DIST));
		double rise = SeeleFxMath.hiltRise(t);
		c.hilt = rise >= 1.0 ? hand : c.hip.add(hand.subtract(c.hip).multiply(rise));
		c.tip = c.hilt.add(c.dir.multiply(c.len * SeeleFxMath.BLADE_MESH_LEN * scale));
		c.age = t;
		c.resolved = true;
	}

	/** A star4 at the blade tip every segment of the sweep. */
	private static void glints(Combo c, Slash s, double now) {
		int n = SeeleFxMath.segments(tier());
		double u = SeeleFxMath.progress(now - s.born);
		while (s.nextGlint <= n && (double) s.nextGlint / n <= u + 1.0E-9) {
			double uk = (double) s.nextGlint / n;
			Vec3d p = c.pivot.add(vec(s.frame.dir(uk)).multiply(SeeleFxMath.innerRadius(bladeScale())));
			FxGlowBatch.sprite(GlowSprite.STAR4).at(p.x, p.y, p.z).size(0.34).life(0.12).curve(0.01, 0.6).rot(s.nextGlint * 0.7, 2.0).color(UryuPalette.Q1).peak(0.85 * glow())
					.owner(c.timeline).lod(c.lod).spawn();
			s.nextGlint++;
		}
	}

	/** The chop reaches the floor: a crack, its glow, a ring on the ground and (when nothing was hit) the shock lines. */
	private static void groundImpact(ClientWorld w, Combo c, Slash s) {
		Vec3d look = vec(s.frame.look());
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		flat = flat.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : flat.normalize();
		double dist = SeeleFxMath.crackDistance(s.reach);
		double x = c.pivot.x + flat.x * dist;
		double z = c.pivot.z + flat.z * dist;
		double feetY = c.eye.y - 1.62;
		double gy = FxGround.sample(w, x, z, feetY);
		Random r = new Random(c.seed * 31 + 7);
		double g = glow();
		FxDecals.create(FxDecals.CRACK, x, z, gy, SeeleFxMath.CRACK_RADIUS, 0.0).reveal(0.12).fade(1.6, 0.8).alpha(0.8);
		FxGlowBatch.sprite(GlowSprite.CRACK).at(x, gy + 0.05, z).ground().rot(r.nextDouble() * Math.PI * 2, 0).size(2.0).life(0.55).curve(0.03, 0.6).color(UryuPalette.Q2)
				.peak(0.9 * g).owner(c.timeline).lod(c.lod).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(x, gy + 0.06, z).ground().size(SeeleFxMath.RING_FROM * 2, SeeleFxMath.RING_TO * 2).sizeEase(FxMath.OC).life(SeeleFxMath.RING_LIFE)
				.curve(0.02, 0.6).color(UryuPalette.Q2).peak(0.8 * g).owner(c.timeline).lod(c.lod).spawn();
		if (s.hits <= 0) {
			SeeleFxMath.Hit h = SeeleFxMath.hit(s.index);
			for (int i = 0; i < Math.max(4, h.dashes()); i++) {
				double a = Math.PI * 2 * i / Math.max(4, h.dashes()) + (r.nextDouble() - 0.5) * 0.3;
				FxGlowBatch.sprite(GlowSprite.DASH).at(x, gy + 0.2, z).vel(Math.cos(a) * 9.0, 0.5, Math.sin(a) * 9.0).drag(0.88).streak().streakTime(0.04).size(0.7).life(0.2)
						.curve(0.01, 0.6).color(UryuPalette.Q1).peak(0.9 * g).owner(c.timeline).lod(c.lod).spawn();
			}
		}
		for (int i = 0; i < 4; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			flake(c, new Vec3d(x, gy + 0.1, z), new Vec3d(Math.cos(a) * 2.5, 2.5 + r.nextDouble() * 2.0, Math.sin(a) * 2.5), 40 + i, 0.3, 12, 0.5);
		}
	}

	/** The blade breaks up: flechette flakes and dashes fly back to the hand. */
	private static void dissolve(Combo c) {
		if (c.hilt == null || c.tip == null) {
			return;
		}
		Random r = new Random(c.seed * 17 + 3);
		double g = glow();
		Vec3d hilt = c.hilt;
		Vec3d span = c.tip.subtract(c.hilt);
		for (int i = 0; i < SeeleFxMath.DISSOLVE_FLAKES; i++) {
			Vec3d p = hilt.add(span.multiply((i + r.nextDouble()) / SeeleFxMath.DISSOLVE_FLAKES));
			Vec3d v = hilt.subtract(p).multiply(1.0 / SeeleFxMath.DISSOLVE).add((r.nextDouble() - 0.5) * 1.5, r.nextDouble() * 1.0, (r.nextDouble() - 0.5) * 1.5);
			flake(c, p, v, i, 0.3 + r.nextDouble() * 0.12, 6, 0.0);
		}
		for (int i = 0; i < SeeleFxMath.DISSOLVE_DASHES; i++) {
			Vec3d p = hilt.add(span.multiply(r.nextDouble()));
			Vec3d v = hilt.subtract(p).multiply(1.0 / SeeleFxMath.DISSOLVE);
			FxGlowBatch.sprite(GlowSprite.DASH).at(p.x, p.y, p.z).vel(v.x, v.y, v.z).streak().streakTime(0.03).size(0.45).life(SeeleFxMath.DISSOLVE).curve(0.01, 0.5)
					.color(i % 3 == 0 ? UryuPalette.Q1 : UryuPalette.Q2).peak(0.8 * g).owner(c.timeline).lod(c.lod).spawn();
		}
		FxGlowBatch.sprite(GlowSprite.STAR4).at(hilt.x, hilt.y, hilt.z).size(0.45).life(0.15).curve(0.01, 0.6).color(UryuPalette.Q0).peak(0.8 * g).owner(c.timeline).lod(c.lod).delay(SeeleFxMath.DISSOLVE * 0.6).spawn();
		SeeleFxMath.Cue cue = SeeleFxMath.dissolveCue();
		FxSound.play(c.timeline, cue.id(), hilt.x, hilt.y, hilt.z, cue.pitch(), cue.volume());
	}

	// ============================================================================================================ glow

	/** The blade glow and the strokes: ribbon behind the tip, the outer crescent wave, the crescent echo. */
	static void emitGlow(FxGlowBatch.Emitter e, double now) {
		if (COMBOS.isEmpty()) {
			return;
		}
		double g = glow();
		for (Combo c : COMBOS) {
			if (!c.resolved || c.pivot == null) {
				continue;
			}
			emitBlade(e, c, now, g);
			for (Slash s : c.slashes) {
				emitSlash(e, c, s, now, g);
			}
		}
	}

	private static void emitBlade(FxGlowBatch.Emitter e, Combo c, double now, double g) {
		float[] q0 = UryuPalette.FQ0;
		float[] q2 = UryuPalette.FQ2;
		float[] q3 = UryuPalette.FQ3;
		double t = c.age;
		// the dagger slides out of the hip: a thin white trail behind the rising hilt
		if (t < 0.14 && c.hip != null) {
			double k = 1.0 - t / 0.14;
			e.streak(GlowSprite.STREAK, c.hilt.x, c.hilt.y, c.hilt.z, c.hip.x, c.hip.y, c.hip.z, 0.10, q0[0], q0[1], q0[2], 0.8 * k * g);
		}
		if (c.len < 0.03) {
			return;
		}
		double keep = Math.min(1.0, c.len * 2.0);
		// first person: the blade passes close to the camera, its glow strip is clipped like the ribbons
		Vec3d mid = c.hilt.add(c.tip).multiply(0.5);
		double wb = clip(e, mid, 0.30, c.fp);
		e.streak(GlowSprite.STREAK, c.tip.x, c.tip.y, c.tip.z, c.hilt.x, c.hilt.y, c.hilt.z, wb, q3[0], q3[1], q3[2], 0.7 * keep * g);
		e.streak(GlowSprite.STREAK, c.tip.x, c.tip.y, c.tip.z, c.hilt.x, c.hilt.y, c.hilt.z, Math.min(0.07, wb), q0[0], q0[1], q0[2], 0.95 * keep * g);
		e.sprite(GlowSprite.GLOW_SOFT, c.tip.x, c.tip.y, c.tip.z, 0.9 * Math.min(1.0, c.len * 1.5), 0, q2[0], q2[1], q2[2], 0.6 * keep * g);
		double tw = FxConfig.reduceMotion || FxConfig.photosensitiveSafe ? 0.85 : 0.75 + 0.25 * Math.sin(now * 40.0); // the glint flickers (Reduce Motion: steady)
		e.sprite(GlowSprite.STAR4, c.tip.x, c.tip.y, c.tip.z, 0.5 * Math.min(1.0, c.len * 1.5), now * 3.0, q0[0], q0[1], q0[2], 0.8 * tw * keep * g);
		e.sprite(GlowSprite.GLOW_SOFT, c.hilt.x, c.hilt.y, c.hilt.z, 0.32, 0, q2[0], q2[1], q2[2], 0.45 * keep * g);
	}

	private static void emitSlash(FxGlowBatch.Emitter e, Combo c, Slash s, double now, double g) {
		double age = now - s.born;
		if (age < 0 || age > SeeleFxMath.SWEEP + SeeleFxMath.OUTER_LIFE + 0.05) {
			return;
		}
		float[] q0 = UryuPalette.FQ0;
		float[] q1 = UryuPalette.FQ1;
		float[] q2 = UryuPalette.FQ2;
		float[] q3 = UryuPalette.FQ3;
		int tier = tier();
		int n = SeeleFxMath.segments(tier) * SeeleFxMath.subdivision(tier);
		int ov = SeeleFxMath.ribbonOverlap(tier);
		double gain = SeeleFxMath.ribbonGain(ov);
		lastSegments = n;
		double p = SeeleFxMath.progress(age);
		double rIn = SeeleFxMath.innerRadius(bladeScale());
		double rOut = SeeleFxMath.outerRadiusMax(s.reach);
		double wMax = s.profile.widthMax();
		Vec3d pivot = c.pivot;
		boolean fp = c.fp;
		// the points of the blade tip ribbon (inner) and of the crescent wave (outer), along the swept part of the arc
		Vec3d[] in = new Vec3d[n + 1];
		Vec3d[] out = new Vec3d[n + 1];
		double[] aIn = new double[n + 1];
		double[] aOut = new double[n + 1];
		for (int k = 0; k <= n; k++) {
			double u = p * k / n;
			double since = age - SeeleFxMath.passTime(u);
			Vec3d dirK = vec(s.frame.dir(u));
			in[k] = pivot.add(dirK.multiply(rIn));
			out[k] = pivot.add(dirK.multiply(SeeleFxMath.outerRadius(since, rIn, rOut)));
			aIn[k] = SeeleFxMath.innerAlpha(since);
			aOut[k] = SeeleFxMath.outerAlpha(since);
		}
		for (int k = 1; k <= n; k++) {
			double f = (double) k / n;
			double a1 = 0.5 * (aIn[k] + aIn[k - 1]);
			if (a1 > 0.004) {
				double w1 = clip(e, in[k], SeeleFxMath.trailWidth(f, 0.30), fp);
				e.streak(GlowSprite.LINE, in[k].x, in[k].y, in[k].z, in[k - 1].x, in[k - 1].y, in[k - 1].z, w1, q2[0], q2[1], q2[2], 0.75 * a1 * g);
				e.streak(GlowSprite.LINE, in[k].x, in[k].y, in[k].z, in[k - 1].x, in[k - 1].y, in[k - 1].z, w1 * 0.3, q0[0], q0[1], q0[2], 0.9 * a1 * g);
			}
			double a2 = 0.5 * (aOut[k] + aOut[k - 1]);
			if (a2 > 0.004) {
				// the body of the crescent: streaks that reach back over several points, so their ramps add up to an even band
				int j = SeeleFxMath.windowStart(k, ov);
				double ab = 0.7 * aOut[k] + 0.3 * aOut[j];
				double w2 = clip(e, out[k], SeeleFxMath.trailWidth(f, wMax), fp);
				e.streak(GlowSprite.STREAK, out[k].x, out[k].y, out[k].z, out[j].x, out[j].y, out[j].z, w2, q3[0], q3[1], q3[2], 0.8 * ab * gain * g);
				e.streak(GlowSprite.LINE, out[k].x, out[k].y, out[k].z, out[k - 1].x, out[k - 1].y, out[k - 1].z,
						clip(e, out[k], SeeleFxMath.coreWidth(f) * s.profile.crescentScale(), fp), q1[0], q1[1], q1[2], 0.85 * a2 * g);
			}
		}
		// the head of the stroke: a bright glow where the blade is
		if (age < SeeleFxMath.SWEEP + 0.05) {
			double fade = 1.0 - SeeleFxMath.clamp01((age - SeeleFxMath.SWEEP) / 0.05);
			Vec3d tip = pivot.add(vec(s.frame.dir(p)).multiply(rIn));
			e.sprite(GlowSprite.GLOW_SOFT, tip.x, tip.y, tip.z, 1.1, 0, q2[0], q2[1], q2[2], 0.7 * fade * g);
		}
		// the crescent sprite of the whole stroke as it ends
		double echo = SeeleFxMath.echoAlpha(age);
		if (echo > 0.004) {
			double size = SeeleFxMath.crescentSize(rOut, s.frame.halfCone(), s.profile.crescentScale());
			V3 mid = s.frame.look();
			V3 nrm = s.frame.normal();
			V3 centre = SeeleFxMath.crescentCentre(v3(pivot), mid, rOut, size);
			double rot = SeeleFxMath.planeRot(nrm, mid);
			e.plane(GlowSprite.CRESCENT, centre.x(), centre.y(), centre.z(), size, nrm.x(), nrm.y(), nrm.z(), rot, q3[0], q3[1], q3[2], 0.85 * echo * g);
			if (s.profile.wide()) {
				double size2 = size * 1.35;
				double r2 = rOut * 0.9;
				V3 c2 = SeeleFxMath.crescentCentre(v3(pivot), mid, r2, size2);
				e.plane(GlowSprite.CRESCENT, c2.x(), c2.y(), c2.z(), size2, nrm.x(), nrm.y(), nrm.z(), rot, q2[0], q2[1], q2[2], 0.55 * echo * g);
			}
		}
	}

	/** First person: the ribbon is narrower within 1.5 blocks of the camera. */
	private static double clip(FxGlowBatch.Emitter e, Vec3d p, double width, boolean fp) {
		if (!fp) {
			return width;
		}
		double dist = Math.sqrt((p.x - e.cx) * (p.x - e.cx) + (p.y - e.cy) * (p.y - e.cy) + (p.z - e.cz) * (p.z - e.cz));
		return SeeleFxMath.nearClip(width, dist);
	}

	// ============================================================================================================ rendering (mesh and afterimages)

	private static PlayerEntityModel<AbstractClientPlayerEntity> model(boolean thin) {
		if (thin) {
			if (slim == null) {
				slim = new PlayerEntityModel<>(MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER_SLIM), true);
			}
			return slim;
		}
		if (wide == null) {
			wide = new PlayerEntityModel<>(MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER), false);
		}
		return wide;
	}

	/** The run pose of a copy: the free (left) arm forward with the blade, the other back, the legs in a stride. */
	private static void pose(PlayerEntityModel<AbstractClientPlayerEntity> m) {
		for (ModelPart p : new ModelPart[] {m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg, m.leftSleeve, m.rightSleeve, m.leftPants, m.rightPants, m.jacket}) {
			p.resetTransform();
		}
		m.setVisible(true);
		m.child = false;
		m.riding = false;
		m.head.pitch = -0.2f;
		m.hat.copyTransform(m.head);
		m.rightLeg.pitch = 0.8f;
		m.leftLeg.pitch = -0.8f;
		m.rightArm.pitch = 0.5f;
		m.leftArm.pitch = -1.25f;
		m.rightArm.roll = 0.08f;
		m.leftArm.roll = -0.08f;
		m.rightPants.copyTransform(m.rightLeg);
		m.leftPants.copyTransform(m.leftLeg);
		m.rightSleeve.copyTransform(m.rightArm);
		m.leftSleeve.copyTransform(m.leftArm);
		m.jacket.copyTransform(m.body);
	}

	private static void draw(WorldRenderContext ctx) {
		bladesDrawn = 0;
		drawImages(ctx);
		drawBlades(ctx);
	}

	private static void drawImages(WorldRenderContext ctx) {
		if (IMAGES.isEmpty()) {
			return;
		}
		VertexConsumerProvider vcp = ctx.consumers();
		if (vcp == null) {
			return;
		}
		double now = FxClock.now;
		Vec3d cam = ctx.camera().getPos();
		MatrixStack ms = ctx.matrixStack();
		float ascale = (float) FxMath.clamp(FxTune.d("shunpo.afterimageScale", 0.9375), 0.25, 2.0);
		int rgb = FxMath.packRgb(UryuPalette.FQ3);
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean fp = mc.options.getPerspective().isFirstPerson() && !FxClient.forceRemote();
		double minSq = SeeleFxMath.AFTERIMAGE_FP_MIN_DIST * SeeleFxMath.AFTERIMAGE_FP_MIN_DIST;
		for (Image im : IMAGES) {
			if (fp && im.local && (im.x - cam.x) * (im.x - cam.x) + (im.z - cam.z) * (im.z - cam.z) < minSq) {
				continue; // the owner does not look at a ghost of himself standing in his face
			}
			double t = (now - im.born) / SeeleFxMath.AFTERIMAGE_LIFE;
			if (t < 0 || t >= 1.0) {
				continue;
			}
			float alpha = (float) (SeeleFxMath.AFTERIMAGE_ALPHA * Math.pow(1.0 - t, 1.4));
			if (alpha < 0.01f) {
				continue;
			}
			PlayerEntityModel<AbstractClientPlayerEntity> m = model(im.slim);
			pose(m);
			ms.push();
			ms.translate(im.x - cam.x, im.y - cam.y, im.z - cam.z);
			ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - im.yaw));
			ms.translate(0.0F, 0.9F, 0.0F);
			ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-22.0F));
			ms.translate(0.0F, -0.9F, 0.0F);
			ms.scale(-1.0F, -1.0F, 1.0F);
			ms.scale(ascale, ascale, ascale);
			ms.translate(0.0F, -1.501F, 0.0F);
			VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(im.texture));
			int argb = (Math.round(alpha * 255f) << 24) | rgb;
			m.render(ms, vc, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, argb);
			ms.pop();
		}
	}

	private static void drawBlades(WorldRenderContext ctx) {
		if (COMBOS.isEmpty()) {
			return;
		}
		if (!meshTried) {
			meshTried = true;
			mesh = FxMesh.load("uryu_fx", "uryu_seele_long");
			layer = RenderLayer.getEntityCutoutNoCull(TEXTURE);
		}
		if (mesh == null) {
			return;
		}
		Vec3d cam = ctx.camera().getPos();
		BufferBuilder buf = null;
		float[] m = new float[9];
		double scale = bladeScale();
		int n = 0;
		for (Combo c : COMBOS) {
			if (!c.resolved || c.hilt == null || c.len < 0.03) {
				continue;
			}
			if (buf == null) {
				buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
			}
			V3[] b = SeeleFxMath.bladeBasis(v3(c.dir), v3(c.normal));
			double sl = scale * c.len;
			double sw = scale * (0.35 + 0.65 * c.len);
			m[0] = (float) (b[0].x() * sw);
			m[1] = (float) (b[1].x() * sl);
			m[2] = (float) (b[2].x() * sw);
			m[3] = (float) (b[0].y() * sw);
			m[4] = (float) (b[1].y() * sl);
			m[5] = (float) (b[2].y() * sw);
			m[6] = (float) (b[0].z() * sw);
			m[7] = (float) (b[1].z() * sl);
			m[8] = (float) (b[2].z() * sw);
			mesh.write(buf, m, c.hilt.x - cam.x, c.hilt.y - cam.y, c.hilt.z - cam.z, 0xFFFFFFFF, FULL_LIGHT);
			n++;
		}
		if (buf != null) {
			BuiltBuffer built = buf.endNullable();
			if (built != null) {
				layer.draw(built);
			}
		}
		bladesDrawn = n;
	}
}
