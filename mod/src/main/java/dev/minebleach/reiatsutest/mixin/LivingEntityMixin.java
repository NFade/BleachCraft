package dev.minebleach.reiatsutest.mixin;

import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The zanpakuto block uses {@code UseAction.BLOCK} (third person arm pose, vanilla use hold), and the vanilla shield rules read
 * that action: {@code blockedByShield} would zero every hit from the front after 5 ticks, damage the item and play shield sounds.
 * Our block has its own rules (parry window, minus 50 percent; {@code server.BlockParry}), so the vanilla shield check is
 * switched off while a zanpakuto is the item in use.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "blockedByShield", at = @At("HEAD"), cancellable = true)
	private void reiatsu$noVanillaShieldForZanpakuto(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (ZanpakutoItem.isBlocking((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
