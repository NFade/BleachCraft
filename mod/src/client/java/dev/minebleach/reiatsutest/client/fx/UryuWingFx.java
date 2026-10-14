package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import dev.minebleach.reiatsutest.core.uryu.WingBarrage;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * The wings (URYU_VFX_STORYBOARD 2.10 to 2.12; U-FX 7 and 8 fill it with the Vollstaendig release, the permanent bankai wings and the real
 * barrage look). Today: the glow-streak wings that stand behind the caster for the 6 s of the wing barrage (effect 75) and flare with every
 * volley. Called by {@link UryuFx}.
 */
final class UryuWingFx {
	private static final class Wing {
		int caster;
		double born;
		double life;
	}

	private static final List<Wing> WINGS = new ArrayList<>();

	private UryuWingFx() {
	}

	static void init() {
		// nothing yet
	}

	/** Effect 75: the barrage starts, the wings open for {@code params[4]} seconds. */
	static void barrage(EffectEventS2C e) {
		float[] pr = e.params();
		double seconds = pr.length > 4 ? pr[4] : UryuBalance.WING_TICKS / 20.0;
		WINGS.removeIf(x -> x.caster == e.casterId());
		Wing wing = new Wing();
		wing.caster = e.casterId();
		wing.born = FxClock.now;
		wing.life = seconds;
		WINGS.add(wing);
		FxSound.play(null, "entity.phantom.flap", e.x(), e.y() + 1.5, e.z(), 1.7, 0.9);
		FxSound.play(null, "block.amethyst_block.resonate", e.x(), e.y() + 1.5, e.z(), 1.8, 0.8);
	}

	/** The bankai of a player ended (any reason): his wings go with it. */
	static void clearWings(int casterId) {
		WINGS.removeIf(w -> w.caster == casterId);
	}

	static void emitGlow(FxGlowBatch.Emitter e, double now) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		WINGS.removeIf(w -> now - w.born > w.life + 0.4);
		float td = mc.getRenderTickCounter().getTickDelta(false);
		for (Wing w : WINGS) {
			Entity en = mc.world.getEntityById(w.caster);
			if (!(en instanceof LivingEntity le) || !le.isAlive()) {
				continue;
			}
			wing(e, w, le, td, now);
		}
	}

	/** One pair of wings: two sharp swept spars per side, a fan of feathers between them and the trailing edge. */
	private static void wing(FxGlowBatch.Emitter e, Wing w, LivingEntity le, float td, double now) {
		float[] glow = UryuFx.GLOW;
		float[] pale = UryuFx.PALE;
		float[] white = UryuFx.WHITE;
		double age = now - w.born;
		double open = Math.min(1.0, age / 0.35);
		double flare = FxMath.ob(open); // easeOutBack: the wings snap open
		double endFade = age > w.life ? Math.max(0.0, 1.0 - (age - w.life) / 0.4) : 1.0;
		double cyc = (age % (UryuBalance.WING_CYCLE / 20.0)) / (UryuBalance.WING_CYCLE / 20.0);
		double volley = Math.exp(-cyc * 7.0); // a pulse with every volley
		double inten = endFade * (0.65 + 0.5 * volley);
		double yaw = le.prevBodyYaw + (le.bodyYaw - le.prevBodyYaw) * td;
		double fx = le.prevX + (le.getX() - le.prevX) * td;
		double fy = le.prevY + (le.getY() - le.prevY) * td;
		double fz = le.prevZ + (le.getZ() - le.prevZ) * td;
		V3 feet = new V3(fx, fy, fz);
		double grow = (0.35 + 0.65 * flare) * (1.0 + 0.07 * volley);
		for (int side = 0; side < 2; side++) {
			V3 root = shoulder(side, yaw, feet);
			V3 up = scaledTip(WingBarrage.tip(side, yaw, feet), root, grow);
			V3 low = scaledTip(WingBarrage.tip(2 + side, yaw, feet), root, grow);
			// the spars
			e.streak(GlowSprite.STREAK, up.x(), up.y(), up.z(), root.x(), root.y(), root.z(), 0.2, glow[0], glow[1], glow[2], inten);
			e.streak(GlowSprite.STREAK, low.x(), low.y(), low.z(), root.x(), root.y(), root.z(), 0.2, glow[0], glow[1], glow[2], inten);
			e.streak(GlowSprite.STREAK, up.x(), up.y(), up.z(), root.x(), root.y(), root.z(), 0.07, 1f, 1f, 1f, 0.8 * inten);
			// the feathers: a fan from the root between the two tips, longer in the middle, angular trailing edge
			V3 prev = up;
			int n = 5;
			for (int i = 1; i <= n; i++) {
				double t = (double) i / n;
				V3 mid = up.lerp(low, t);
				double extend = 1.0 + 0.18 * Math.sin(Math.PI * t); // the middle feathers are the longest: a pointed outline
				V3 tip = new V3(root.x() + (mid.x() - root.x()) * extend, root.y() + (mid.y() - root.y()) * extend, root.z() + (mid.z() - root.z()) * extend);
				e.streak(GlowSprite.STREAK, tip.x(), tip.y(), tip.z(), root.x(), root.y(), root.z(), 0.13, pale[0], pale[1], pale[2], 0.7 * inten);
				e.streak(GlowSprite.STREAK, tip.x(), tip.y(), tip.z(), prev.x(), prev.y(), prev.z(), 0.09, glow[0], glow[1], glow[2], 0.8 * inten);
				e.sprite(GlowSprite.GLOW_SOFT, tip.x(), tip.y(), tip.z(), 0.35 + 0.35 * volley, 0, white[0], white[1], white[2], 0.5 * inten);
				prev = tip;
			}
			e.sprite(GlowSprite.GLOW_SOFT, up.x(), up.y(), up.z(), 0.9 + 1.0 * volley, 0, glow[0], glow[1], glow[2], 0.8 * inten);
			e.sprite(GlowSprite.STAR4, up.x(), up.y(), up.z(), 0.5 + 0.9 * volley, age * 1.5, 1f, 1f, 1f, 0.9 * inten * (0.4 + 0.6 * volley));
		}
	}

	/** Where a wing grows from, on the back at shoulder height, one per side (0 right, 1 left). */
	private static V3 shoulder(int side, double bodyYawDeg, V3 feet) {
		double yaw = Math.toRadians(bodyYawDeg);
		double rx = -Math.cos(yaw) * 0.18 * (side == 0 ? 1 : -1);
		double rz = -Math.sin(yaw) * 0.18 * (side == 0 ? 1 : -1);
		double bx = Math.sin(yaw) * 0.22; // a little behind the spine
		double bz = -Math.cos(yaw) * 0.22;
		return new V3(feet.x() + rx + bx, feet.y() + 1.35, feet.z() + rz + bz);
	}

	private static V3 scaledTip(V3 tip, V3 root, double k) {
		return new V3(root.x() + (tip.x() - root.x()) * k, root.y() + (tip.y() - root.y()) * k, root.z() + (tip.z() - root.z()) * k);
	}
}
