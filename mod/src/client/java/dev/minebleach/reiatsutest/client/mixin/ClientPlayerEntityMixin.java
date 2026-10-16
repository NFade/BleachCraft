package dev.minebleach.reiatsutest.client.mixin;

import dev.minebleach.reiatsutest.registry.UryuCrossItem;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uryu draws his Heilig Bogen without being slowed (design/URYU_PLAN.md revision 2). Vanilla {@code ClientPlayerEntity#tickMovement}
 * multiplies {@code input.movementSideways} and {@code input.movementForward} by 0.2 (and clears {@code ticksLeftToDoubleTapSprint})
 * inside {@code if (this.isUsingItem() && !this.hasVehicle())}. That is the ONLY call of {@code isUsingItem()} in {@code tickMovement}
 * (checked in the mapped 1.21.1 bytecode: the other callers are {@code canStartSprinting} and {@code isUsingItem} itself), so redirecting
 * that one call to "using an item that is not the Uryu bow" removes the factor for the bow only; every other item, and every sword
 * block, is slowed exactly as before. The server has no matching slowdown (the server never reads the use state for movement speed,
 * and the mod adds no attribute modifier for Uryu), so the server accepts the full speed.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
	/**
	 * R14: no running while the string is drawn. The slowdown above is removed for the bow, so a sprint that began before the draw would go on at full
	 * speed; vanilla only forbids STARTING a sprint while an item is used. The sprint ends at the head of every movement tick while the Uryu bow is drawn,
	 * the client then tells the server (the stop-sprint command), and {@code UryuManager} cancels it on the server as well.
	 */
	@Inject(method = "tickMovement()V", at = @At("HEAD"))
	private void reiatsu$noSprintWhileDrawingTheBow(CallbackInfo ci) {
		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		if (self.isSprinting() && self.isUsingItem() && UryuCrossItem.isBow(self.getActiveItem())) {
			self.setSprinting(false);
		}
	}

	@Redirect(method = "tickMovement()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
	private boolean reiatsu$noSlowWhileDrawingTheBow(ClientPlayerEntity self) {
		return self.isUsingItem() && !(self.getActiveItem().getItem() instanceof UryuCrossItem);
	}
}
