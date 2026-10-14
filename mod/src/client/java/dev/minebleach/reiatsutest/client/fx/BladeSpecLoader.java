package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.BladeSpec;
import dev.minebleach.reiatsutest.core.obj.ObjMeta;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

/**
 * Reads the size of the Byakuya bankai blade from {@code byakuya_bankai_meta.json} (block {@code "blade"}: length, width, thickness, apex) into the
 * {@link BladeSpec} holder, once (R11 T13). A missing file or block keeps the defaults of the shipped model. The size is NOT tuned by constants any more:
 * {@code bankai.bladeLength} (fx_override.json) resizes the whole blade in one number (see {@link RowsFx#bladeScaleSetting}).
 */
final class BladeSpecLoader {
	private static boolean done;

	private BladeSpecLoader() {
	}

	static synchronized void ensure() {
		if (done) {
			return;
		}
		done = true;
		try {
			Identifier id = ReiatsuTest.id("models/obj/byakuya_bankai/byakuya_bankai_meta.json");
			Resource r = MinecraftClient.getInstance().getResourceManager().getResource(id).orElse(null);
			if (r == null) {
				ReiatsuTest.LOGGER.warn("[fx] blade meta not found: {} (default blade size {} m)", id, BladeSpec.DEFAULT_LENGTH);
				return;
			}
			try (InputStream in = r.getInputStream()) {
				ObjMeta meta = ObjMeta.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
				BladeSpec.apply(meta.blade);
			}
			ReiatsuTest.LOGGER.info("[fx] bankai blade from meta: length {} width {} thickness {} tipZ {}", BladeSpec.length(), BladeSpec.width(), BladeSpec.thickness(), BladeSpec.tipZ());
		} catch (Exception e) {
			ReiatsuTest.LOGGER.error("[fx] blade meta failed to load, default blade size", e);
		}
	}
}
