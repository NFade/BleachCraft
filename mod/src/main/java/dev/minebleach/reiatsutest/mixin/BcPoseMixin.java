package dev.minebleach.reiatsutest.mixin;

import dev.minebleach.reiatsutest.core.combat.BcAttackGate;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.ModComponents;
import dev.minebleach.reiatsutest.registry.ReleaseState;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import net.bettercombat.logic.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * tp_pose: the Better Combat katana stance (pose {@code pose_two_handed_katana}) is never shown for our swords. Our weapon presets
 * inherit the pose of {@code bettercombat:katana}; this mixin returns an empty pose from {@code PlayerAttackHelper.poseForPlayer}
 * whenever a zanpakuto is in the main hand ({@link BcAttackGate#poseSuppressed}). The stance (hand on the hilt while sheathed, the
 * draw, the upright two handed hold, the block) is drawn by the third person pose layer ({@code client/model/TpPoseLayer}), whose
 * numbers can be tuned and which follows the same draw progress as the first person view; the strike animations of Better Combat
 * still play over it.
 *
 * <p>{@code poseForPlayer} is the single source of the stance: the client of the local player reads it every tick
 * ({@code AbstractClientPlayerEntityMixin.updateAnimationsOnTick}, an empty base pose clears the layers), the server writes it into
 * the data tracker that other clients play ({@code PlayerEntityMixin.post_Tick}), so both sides stay in step. Pseudo mixin into a
 * Better Combat class: no BC code is copied, the preset files are untouched.
 */
@Pseudo
@Mixin(targets = "net.bettercombat.logic.PlayerAttackHelper", remap = false)
public abstract class BcPoseMixin {
	@Inject(method = "poseForPlayer", at = @At("RETURN"), cancellable = true, remap = false)
	private static void reiatsu$poseOnlyWhenDrawn(PlayerEntity player, CallbackInfoReturnable<Pose> cir) {
		ItemStack main = player.getMainHandStack();
		boolean holds = main.getItem() instanceof ZanpakutoItem;
		if (!holds) {
			return;
		}
		ReleaseState rs = main.getOrDefault(ModComponents.RELEASE_STATE, ReleaseState.SEALED);
		ZanpakutoState st = ZanpakutoState.values()[rs.ordinal()]; // same order as ReleaseState
		if (BcAttackGate.poseSuppressed(true, st, ZanpakutoItem.isBlocking(player))) {
			Pose p = cir.getReturnValue();
			cir.setReturnValue(new Pose("", p == null ? "" : p.offHand()));
		}
	}
}
