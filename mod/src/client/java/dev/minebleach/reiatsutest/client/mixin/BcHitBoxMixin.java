package dev.minebleach.reiatsutest.client.mixin;

import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * R15 (user: "the slash must hit wherever the sword flies, above, beside, even when the aim is not exactly on the enemy"). Better Combat finds the targets of a
 * swing client side in an oriented box in front of the camera ({@code WeaponHitBoxes.createHitbox}): its HORIZONTAL_PLANE is 2r wide but only r/3 (about one block)
 * tall and turns with the pitch of the view, its VERTICAL_PLANE only r/3 wide, so an enemy a little above, below or beside the crosshair was missed. For a zanpakuto in
 * the main hand of the local player the boxes are made roomy: at least 2.4r wide and 1.6r tall for the horizontal swings, 1.6r wide and 2r tall for the vertical ones,
 * 1.4r each way for the forward box (r = the attack range, so nothing reaches farther than before; depth is unchanged). The server only checks the distance, so the
 * client box decides. Pseudo mixin into a Better Combat class, other weapons keep their boxes; switch off with {@code bc.hitbox = 0} in fx_override.json.
 */
@Pseudo
@Mixin(targets = "net.bettercombat.client.collision.WeaponHitBoxes", remap = false)
public abstract class BcHitBoxMixin {
	@Inject(method = "createHitbox", at = @At("RETURN"), cancellable = true, remap = false)
	private static void reiatsu$roomy(@org.spongepowered.asm.mixin.injection.Coerce Object shape, double range, boolean spin, CallbackInfoReturnable<Vec3d> cir) {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		Vec3d v = cir.getReturnValue();
		if (p == null || v == null || !(p.getMainHandStack().getItem() instanceof ZanpakutoItem) || dev.minebleach.reiatsutest.client.fx.FxTune.d("bc.hitbox", 1.0) < 0.5) {
			return;
		}
		String name = String.valueOf(shape);
		double w = Math.abs(v.x);
		double h = Math.abs(v.y);
		double d = Math.abs(v.z);
		switch (name) {
			case "HORIZONTAL_PLANE" -> cir.setReturnValue(new Vec3d(Math.max(w, range * 2.4), Math.max(h, range * 1.6), d));
			case "VERTICAL_PLANE" -> cir.setReturnValue(new Vec3d(Math.max(w, range * 1.6), Math.max(h, range * 2.0), d));
			case "FORWARD_BOX" -> cir.setReturnValue(new Vec3d(Math.max(w, range * 1.4), Math.max(h, range * 1.4), d));
			default -> { }
		}
	}
}
