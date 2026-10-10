package dev.minebleach.reiatsutest.client.mixin;

import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SHAKE (VFX_STORYBOARD 1.4): a decaying yaw and pitch rotation of the view. {@code tiltViewWhenHurt} (private, 1.21.1) is
 * called once for the world and once for the hand, so both shake. HEAD, not TAIL: the method returns early when the player
 * is not hurt.
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
}
