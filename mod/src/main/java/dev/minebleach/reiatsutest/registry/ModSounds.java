package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.sound.SoundIds;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * R11 T12: one {@link SoundEvent} {@code reiatsu_test:<id>} per entry of {@link SoundIds#ALL}. The clips of the events are NOT in the repository
 * (anime audio is copyrighted): {@code assets/reiatsu_test/sounds.json} points at {@code sounds/anime/...} files that the player puts in locally; an event
 * without a clip is quiet and the game plays its vanilla fallback instead (see {@code core/sound/SoundMap}, {@code client/fx/FxSound}).
 */
public final class ModSounds {
	private static final Map<String, SoundEvent> EVENTS = new LinkedHashMap<>();

	private ModSounds() {
	}

	public static void init() {
		for (String id : SoundIds.ALL) {
			Identifier ident = ReiatsuTest.id(id);
			EVENTS.put(id, Registry.register(Registries.SOUND_EVENT, ident, SoundEvent.of(ident)));
		}
	}

	/** The registered event of an id of {@link SoundIds}, or null. */
	public static SoundEvent get(String id) {
		return EVENTS.get(id);
	}
}
