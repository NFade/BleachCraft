package dev.minebleach.reiatsutest.client.mixin;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BC5 decision 2: a Better Combat strike must not change the first person view of our sword. Better Combat gives its strike
 * animation the first person mode from {@code FirstPersonAnimationCompatibility.firstPersonMode()} (default AUTO =
 * THIRD_PERSON_MODEL): playerAnimator then cancels the whole {@code renderHandsWithItems} pass and draws the third person model
 * instead, which hides our arm, the scabbard in the left hand and the sword lift. For a zanpakuto in the main hand of the local
 * player the mode is NONE: the strike does not touch the first person view at all (our arm, scabbard and sword stay as they are;
 * the trail, sound and hit still happen). Other weapons keep the Better Combat setting. Called when a strike animation starts,
 * so the main hand item at that moment decides. Pseudo mixin into a Better Combat class: no BC code is copied, the config
 * option of the player is untouched.
 *
 * <p>BC5b: the strike gesture of the first person view is drawn by {@code FpMotion} (on the Better Combat attack start event).
 * Dev switch {@code "fp_bc_mode": 1} in pose_override.json returns the decision to Better Combat (its own third person model
 * animation in first person) for a comparison in game.
 */
@Pseudo
@Mixin(targets = "net.bettercombat.client.compat.FirstPersonAnimationCompatibility", remap = false)
public abstract class BcFirstPersonModeMixin {
	@Inject(method = "firstPersonMode", at = @At("HEAD"), cancellable = true, remap = false)
	private static void reiatsu$keepOurHand(CallbackInfoReturnable<FirstPersonMode> cir) {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		if (p != null && p.getMainHandStack().getItem() instanceof ZanpakutoItem
				&& !dev.minebleach.reiatsutest.client.model.FpMotion.useBcFirstPerson()) {
			cir.setReturnValue(FirstPersonMode.NONE);
		}
	}
}
