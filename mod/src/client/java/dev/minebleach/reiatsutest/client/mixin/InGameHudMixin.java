package dev.minebleach.reiatsutest.client.mixin;

import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flash, vignette, impact frame and speed lines are drawn before the HUD layers so every HUD element stays on top. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"))
	private void reiatsu$screenFx(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		ScreenFx.renderUnderHud(context);
	}
}
