package dev.minebleach.reiatsutest.mixin;

import dev.minebleach.reiatsutest.core.combat.BarrierRules;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import dev.minebleach.reiatsutest.server.RukiaMomentum;
import dev.minebleach.reiatsutest.server.ZanpakutoManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The zanpakuto block uses {@code UseAction.BLOCK} (third person arm pose, vanilla use hold), and the vanilla shield rules read
 * that action: {@code blockedByShield} would zero every hit from the front after 5 ticks, damage the item and play shield sounds.
 * Our block has its own rules (parry window, minus 50 percent; {@code server.BlockParry}), so the vanilla shield check is
 * switched off while a zanpakuto is the item in use.
 *
 * <p>Mech2 task 1: while a Byakuya barrier stands on a player, control effects do not land on him
 * ({@link BarrierRules}): status effects of the control set (one gate, {@code canHaveStatusEffect}, which every add path goes
 * through: our abilities, mobs, potions, {@code /effect}) and knockback ({@code takeKnockback}, our pushes and vanilla hits).
 * Server only: the client world is never asked, so a packet can never be refused there.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "blockedByShield", at = @At("HEAD"), cancellable = true)
	private void reiatsu$noVanillaShieldForZanpakuto(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (ZanpakutoItem.isBlocking((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * R11 T6: the Rukia passive multiplies her sword damage before anything else reads the amount (block, barrier, armor, Rage all see the
	 * boosted hit, except Rage which divides the bonus out again). The multiplier is 1.0 for everybody who is not a released Rukia with
	 * stacks; the whole decision lives in {@link RukiaMomentum#meleeMultiplier}. Server only.
	 */
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float reiatsu$rukiaMomentum(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient) {
			return amount;
		}
		float mult = RukiaMomentum.meleeMultiplier(self, source);
		return mult == 1.0F ? amount : amount * mult;
	}

	@Inject(method = "canHaveStatusEffect", at = @At("HEAD"), cancellable = true)
	private void reiatsu$barrierRefusesControlEffects(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient || !ZanpakutoManager.barrierControlImmune(self)) {
			return;
		}
		String path = effect.getEffectType().getKey().map(k -> k.getValue().getPath()).orElse(null);
		if (BarrierRules.blocksEffect(true, path)) {
			ZanpakutoManager.noteBarrierRefused(self, "effect " + path);
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
	private void reiatsu$barrierRefusesKnockback(double strength, double x, double z, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.getWorld().isClient && BarrierRules.blocksKnockback(ZanpakutoManager.barrierControlImmune(self))) {
			ZanpakutoManager.noteBarrierRefused(self, "knockback");
			ci.cancel();
		} else if (!self.getWorld().isClient) {
			dev.minebleach.reiatsutest.server.uryu.UryuManager.onPushed(self); // a push takes the Focus of an Uryu (no-op for everybody else)
		}
	}
}
