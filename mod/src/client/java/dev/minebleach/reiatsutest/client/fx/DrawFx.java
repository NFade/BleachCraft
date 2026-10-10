package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.client.model.DrawEvents;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Listens to {@link DrawEvents#EVENT}. Mapping of the state machine (B4: SEALED -> BASE -> SHIKAI -> BANKAI) to effects:
 * <ul>
 * <li>SEALED -> BASE is the plain draw: {@code DRAW_START} a short scabbard rattle, {@code DRAW_RELEASE} (blade clear of the saya)
 * a steel glint and the "shing". No flash, no particles of the release: nothing is released yet.</li>
 * <li>BASE -> SHIKAI is the release: it has no draw animation, the server sends {@code effect_event} 1 / 3 and the release flash
 * of {@link ReleaseFx} hides the model swap (the stack component changes in the same moment).</li>
 * <li>SHIKAI / BANKAI -> SEALED: {@code effect_event} 10 / 11 ({@link SealFx}); {@code SHEATHE_START} / {@code SHEATHE_END} add the
 * scabbard sounds and a glint when the blade is home.</li>
 * <li>A forced state (dev command or a client that joins late) from SEALED straight to SHIKAI / BANKAI plays the draw, but not the
 * release flash (there is no event).</li>
 * </ul>
 */
public final class DrawFx {
	private static final Random RNG = new Random(0xD2A3);

	private DrawFx() {
	}

	public static void init() {
		DrawEvents.EVENT.register(DrawFx::onDraw);
	}

	static void onDraw(DrawEvents.Phase phase, LivingEntity entity, CharacterId character, ZanpakutoState target, boolean local) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Vec3d p = entity.getPos();
		double lod = FxMath.lod(mc.gameRenderer.getCamera().getPos().distanceTo(p));
		if (lod <= 0) {
			return;
		}
		switch (phase) {
			case DRAW_START -> FxSound.play(null, "item.armor.equip_iron", p.x, p.y + 1.0, p.z, 1.7, 0.30 * lod);
			case DRAW_RELEASE -> {
				FxSound.play(null, "item.trident.return", p.x, p.y + 1.0, p.z, 1.9, 0.30 * lod);
				glint(entity, local, character, lod, 1.0f);
			}
			case SHEATHE_START -> FxSound.play(null, "item.armor.equip_chain", p.x, p.y + 1.0, p.z, 1.3, 0.28 * lod);
			case SHEATHE_END -> {
				FxSound.play(null, "item.armor.equip_iron", p.x, p.y + 1.0, p.z, 1.15, 0.34 * lod);
				glint(entity, local, character, lod, 0.6f);
			}
		}
	}

	/** Where the steel catches the light: in first person near the guard (right, low, in front of the camera), otherwise at the hand. */
	private static void glint(LivingEntity entity, boolean local, CharacterId character, double lod, float strength) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Vec3d at;
		if (local && mc.options.getPerspective().isFirstPerson()) {
			var cam = mc.gameRenderer.getCamera();
			Vec3d look = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
			Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
			Vec3d up = right.crossProduct(look).normalize();
			at = cam.getPos().add(look.multiply(0.9)).add(right.multiply(0.42)).add(up.multiply(-0.28));
		} else {
			double yaw = Math.toRadians(entity.getYaw());
			double fx = -Math.sin(yaw);
			double fz = Math.cos(yaw);
			Vec3d f = entity.getPos();
			at = new Vec3d(f.x - fz * 0.35 + fx * 0.45, f.y + 1.15, f.z + fx * 0.35 + fz * 0.45);
		}
		String tint = character == CharacterId.BYAKUYA ? "#F3E4FF" : "#EAF8FF";
		FxGlowBatch.sprite(GlowSprite.STAR4).at(at.x, at.y, at.z).lifeTicks(8).size(0.7 * strength, 0.1).sizeEase(FxMath.IQ).color(tint).peak(0.95)
				.rot(0.4, 2.0).lod(lod).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(at.x, at.y, at.z).lifeTicks(6).size(0.45 * strength, 0.2).color("#FFFFFF").peak(0.7).lod(lod).spawn();
		for (int i = 0; i < 5; i++) {
			double a = RNG.nextDouble() * Math.PI * 2;
			FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(at.x, at.y, at.z).vel(Math.cos(a) * 1.4, 0.6 + RNG.nextDouble(), Math.sin(a) * 1.4).drag(0.88)
					.life(10).size(0.1).color(tint).seed(RNG.nextInt(4)).lod(lod).spawn();
		}
	}
}
