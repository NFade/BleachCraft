package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ModSoundConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.sound.SoundEntry;
import dev.minebleach.reiatsutest.core.sound.SoundMap;
import dev.minebleach.reiatsutest.net.SoundCueS2C;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * R11 T12: the one place where the server plays a sound of the mod. Instead of {@code world.playSound(..., SoundEvents.X, ...)} it sends
 * {@link SoundCueS2C} to the players near the point; each client decides between its anime clip and the vanilla fallback the server names
 * (so the sound is the same as before when no clip is installed). The radius is the {@code range} of the event in {@code config/reiatsu_sounds.json}
 * (at least 16 times the volume, like a vanilla sound); {@code /reiatsu reload} re-reads the file.
 */
public final class ModSound {
	private static volatile SoundMap map = SoundMap.defaults();

	private ModSound() {
	}

	public static void init() {
		map = ModSoundConfig.load();
		ConfigReload.register(() -> map = ModSoundConfig.load());
	}

	/**
	 * @param event   an id of {@code core/sound/SoundIds}
	 * @param vanilla registry path of the vanilla sound that plays when the client has no clip for the event ("" = quiet without a clip)
	 * @param volume  volume of the vanilla fallback (a clip uses its own, see the config)
	 * @param pitch   pitch of the vanilla fallback
	 */
	public static void play(ServerWorld world, String event, double x, double y, double z, String vanilla, float volume, float pitch) {
		SoundEntry e = map.entry(event);
		if (e == null) {
			ReiatsuTest.LOGGER.warn("[sound] unknown event {}", event);
			return;
		}
		double radius = Math.max(e.range(), 16.0 * Math.max(1.0, volume));
		SoundCueS2C cue = new SoundCueS2C(event, x, y, z, vanilla == null ? "" : vanilla, volume, pitch);
		for (ServerPlayerEntity p : PlayerLookup.around(world, new Vec3d(x, y, z), radius)) {
			ServerPlayNetworking.send(p, cue);
		}
	}
}
