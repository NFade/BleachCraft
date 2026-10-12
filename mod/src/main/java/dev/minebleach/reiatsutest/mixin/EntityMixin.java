package dev.minebleach.reiatsutest.mixin;

import dev.minebleach.reiatsutest.core.combat.BarrierRules;
import dev.minebleach.reiatsutest.server.ZanpakutoManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mech2 task 1: the freezing ticks (Rukia ice through {@code Targeting.chill}, powder snow) cannot be raised on a player under a
 * Byakuya barrier. Only an increase is refused, so the counter still drains. Server only.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "setFrozenTicks", at = @At("HEAD"), cancellable = true)
	private void reiatsu$barrierRefusesFreezing(int frozenTicks, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (self.getWorld().isClient) {
			return;
		}
		if (BarrierRules.blocksFrozenTicks(ZanpakutoManager.barrierControlImmune(self), self.getFrozenTicks(), frozenTicks)) {
			ci.cancel();
		}
	}
}
