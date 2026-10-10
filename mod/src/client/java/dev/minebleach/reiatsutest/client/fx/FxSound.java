package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Sound helper (1.5): vanilla events by registry path ({@code "block.beacon.activate"}), played at the effect position in
 * the PLAYERS category, volume x {@code fxSoundVolume}. At most 24 FX sounds live, the same id within 0.05 s is dropped,
 * {@code pointed_dripstone.land} at most 8 per second and {@code pink_petals.break} at most 10 per second, and a
 * DUCK window of another effect of the same caster halves (or silences) the sound.
 */
public final class FxSound {
	private static final Map<String, SoundEvent> CACHE = new HashMap<>();
	private static final Map<String, Double> LAST = new HashMap<>();
	private static final ArrayDeque<Double> LIVE = new ArrayDeque<>();
	private static final ArrayDeque<Double> DRIPSTONE = new ArrayDeque<>();
	private static final ArrayDeque<Double> PETALS = new ArrayDeque<>();
	/** Dev/harness: ids that were requested, in order (cleared by the harness). */
	public static final java.util.List<String> LOG = new java.util.concurrent.CopyOnWriteArrayList<>();
	public static int played;
	public static int dropped;

	private FxSound() {
	}

	private static SoundEvent resolve(String id) {
		return CACHE.computeIfAbsent(id, k -> {
			Identifier ident = k.contains(":") ? Identifier.of(k) : Identifier.ofVanilla(k);
			SoundEvent e = Registries.SOUND_EVENT.get(ident);
			if (e == null) {
				ReiatsuTest.LOGGER.warn("[fx] unknown sound {}", k);
			}
			return e;
		});
	}

	public static void play(EffectTimeline from, String id, double x, double y, double z, double pitch, double volume) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		double now = FxClock.now;
		Double last = LAST.get(id);
		if (last != null && now - last < 0.05 && now >= last) {
			dropped++;
			return;
		}
		if (id.endsWith("pointed_dripstone.land") && rateLimited(DRIPSTONE, now, 8)) {
			dropped++;
			return;
		}
		if (id.endsWith("pink_petals.break") && rateLimited(PETALS, now, 10)) {
			dropped++;
			return;
		}
		while (!LIVE.isEmpty() && now - LIVE.peekFirst() > 1.5) {
			LIVE.pollFirst();
		}
		if (LIVE.size() >= 24) {
			dropped++;
			return;
		}
		SoundEvent ev = resolve(id);
		if (ev == null) {
			return;
		}
		double vol = volume * FxConfig.fxSoundVolume;
		if (from != null) {
			vol *= FxTimelines.duckFactor(from);
			// remote casters are quieter with distance through the sound engine itself (positional); nothing more here
		}
		if (vol <= 0.001) {
			return;
		}
		LAST.put(id, now);
		LIVE.addLast(now);
		played++;
		LOG.add(id);
		mc.world.playSound(x, y, z, ev, SoundCategory.PLAYERS, (float) vol, (float) pitch, false);
	}

	/** Plays a non-positional UI sound (HUD): master volume only. */
	public static void ui(String id, double pitch, double volume) {
		MinecraftClient mc = MinecraftClient.getInstance();
		SoundEvent ev = resolve(id);
		if (ev == null || mc.getSoundManager() == null) {
			return;
		}
		LOG.add(id);
		mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(ev, (float) pitch, (float) volume));
	}

	private static boolean rateLimited(ArrayDeque<Double> q, double now, int perSecond) {
		while (!q.isEmpty() && now - q.peekFirst() > 1.0) {
			q.pollFirst();
		}
		if (q.size() >= perSecond) {
			return true;
		}
		q.addLast(now);
		return false;
	}
}
