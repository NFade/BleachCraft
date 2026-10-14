package dev.minebleach.reiatsutest.client.mixin;

import dev.minebleach.reiatsutest.registry.UryuCrossItem;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
	@Redirect(method = "tickMovement()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
	private boolean reiatsu$noSlowWhileDrawingTheBow(ClientPlayerEntity self) {
		return self.isUsingItem() && !(self.getActiveItem().getItem() instanceof UryuCrossItem);
	}
}
