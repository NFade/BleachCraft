package dev.minebleach.reiatsutest.client.mixin;

import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SHAKE (VFX_STORYBOARD 1.4): a decaying yaw and pitch rotation of the view. {@code tiltViewWhenHurt} (private, 1.21.1) is
 * called once for the world and once for the hand, so both shake. HEAD, not TAIL: the method returns early when the player
 * is not hurt.
 *
 * <p>SHUNPO RUSH: {@code getFov} (Yarn, private, returns the vertical FOV in degrees) gets {@link ScreenFx#fovKickDeg()} added
 * for the world camera only ({@code changingFov} is true there). The hand keeps its FOV, so it does not swim with the kick.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"))
	private void reiatsu$shake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		if (!ScreenFx.shaking()) {
			return;
		}
		double[] o = new double[2];
		ScreenFx.shakeOffsets(o);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) o[0]));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) o[1]));
	}

	/**
	 * Ability gesture camera nudge ({@code FpCast}, pitch above 0 looks up): a small pitch, yaw and roll on the same two calls as the shake,
	 * so the world and the hands move together. Zero under reduce motion and photosensitive safe.
	 */
	@Inject(method = "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"))
	private void reiatsu$castCamera(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		float[] c = reiatsu$cam;
		dev.minebleach.reiatsutest.client.model.FpCast.camera(c);
		if (c[0] == 0f && c[1] == 0f && c[2] == 0f) {
			return;
		}
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(c[1]));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-c[0]));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(c[2]));
	}

	@org.spongepowered.asm.mixin.Unique
	private final float[] reiatsu$cam = new float[4];

	@Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
	private void reiatsu$shunpoFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		if (!changingFov) {
			return;
		}
		double kick = ScreenFx.fovKickDeg();
		dev.minebleach.reiatsutest.client.model.FpCast.camera(reiatsu$cam);
		kick += reiatsu$cam[3];
		if (Math.abs(kick) > 0.0001) {
			cir.setReturnValue(cir.getReturnValue() + kick);
		}
	}
}
