package dev.minebleach.reiatsutest.client.mixin;

import java.util.List;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The passes of a post effect processor, so the freeze_desat amount can be set every frame (a post JSON holds static uniforms only). */
@Mixin(PostEffectProcessor.class)
public interface PostEffectProcessorAccessor {
	@Accessor("passes")
	List<PostEffectPass> reiatsu$passes();
}
