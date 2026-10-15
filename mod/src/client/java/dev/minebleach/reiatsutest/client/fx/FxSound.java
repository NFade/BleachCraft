package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ModSoundConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.sound.SoundMap;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Sound helper (1.5), since R11 T12 the one client entry for the sounds of the mod. A call names an EVENT ({@code core/sound/SoundIds}) and the vanilla sound
 * the code always played for that moment (the fallback). {@link SoundMap} (from {@code config/reiatsu_sounds.json}) decides: a clip of the event is loaded
 * (the player put the anime file in, {@code SoundManager} knows the event) -> the clip plays with the volume and pitch of the config; otherwise the vanilla
 * fallback plays at the effect position in the PLAYERS category with the pitch and volume the code asks for, exactly as before. Volume x
 * {@code fxSoundVolume}. At most 24 FX sounds live, the same sound id within 0.05 s is dropped, {@code pointed_dripstone.land} at most 8 per second and
 * {@code pink_petals.break} at most 10 per second (fallback only; a clip has the {@code minIntervalMs} of its event), and a DUCK window of another effect
 * of the same caster halves (or silences) the sound.
 */
public final class FxSound {
	private static final Map<String, SoundEvent> CACHE = new HashMap<>();
	private static final Map<String, Double> LAST = new HashMap<>();
	private static final ArrayDeque<Double> LIVE = new ArrayDeque<>();
	private static final ArrayDeque<Double> DRIPSTONE = new ArrayDeque<>();
	private static final ArrayDeque<Double> PETALS = new ArrayDeque<>();
	private static final Random RND = new Random();
	/** Dev/harness: sounds that were played, in order: the vanilla id of a fallback, {@code file:<id>} of a clip (cleared by the harness). */
	public static final java.util.List<String> LOG = new java.util.concurrent.CopyOnWriteArrayList<>();
	public static int played;
	public static int dropped;

	private static volatile SoundMap map = SoundMap.defaults();
	private static long lastCheckNanos;
	private static long lastStamp = Long.MIN_VALUE;
	private static boolean mapLoaded;

	private FxSound() {
	}

	// ------------------------------------------------------------------------------------------ config

	/** Loads {@code config/reiatsu_sounds.json} the first time and re-reads it when it changed (checked once a second). */
	private static void refresh() {
		long now = System.nanoTime();
		if (mapLoaded && now - lastCheckNanos < 1_000_000_000L) {
			return;
		}
		lastCheckNanos = now;
		long stamp = ModSoundConfig.stamp();
		if (mapLoaded && stamp == lastStamp) {
			return;
		}
		boolean first = !mapLoaded;
		mapLoaded = true;
		try {
			map = ModSoundConfig.load();
			lastStamp = ModSoundConfig.stamp();
			if (!first) {
				ReiatsuTest.LOGGER.info("[sound] client config reloaded");
			}
		} catch (RuntimeException e) {
			ReiatsuTest.LOGGER.warn("[sound] cannot read the sound config ({}), keeping the last values", e.toString());
		}
	}

	/** The current map (the dev tools look at it). */
	public static SoundMap map() {
		refresh();
		return map;
	}

	/** True when the client has at least one loaded clip for the sound event {@code id} (an event in sounds.json whose file exists). */
	static boolean isLoaded(String id) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Identifier ident = Identifier.tryParse(id);
		if (ident == null || mc.getSoundManager() == null) {
			return false;
		}
		WeightedSoundSet set = mc.getSoundManager().get(ident);
		return set != null && set.getWeight() > 0;
	}

	private static SoundEvent resolve(String id) {
		return CACHE.computeIfAbsent(id, k -> {
			Identifier ident = k.contains(":") ? Identifier.of(k) : Identifier.ofVanilla(k);
			SoundEvent e = Registries.SOUND_EVENT.get(ident);
			if (e == null && k.contains(":")) {
				e = SoundEvent.of(ident); // a clip set that only a sounds.json (a resource pack) knows
			}
			if (e == null) {
				ReiatsuTest.LOGGER.warn("[fx] unknown sound {}", k);
			}
			return e;
		});
	}

	private static SoundCategory category(String name) {
		try {
			return SoundCategory.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return SoundCategory.PLAYERS;
		}
	}

	private static long nowMs() {
		return System.nanoTime() / 1_000_000L;
	}

	// ------------------------------------------------------------------------------------------ play

	/**
	 * Plays the sound event {@code event} at a point. {@code vanilla}, {@code pitch} and {@code volume} are the old vanilla sound of this moment: they play
	 * when no clip is loaded (and a layered moment calls this once per layer; with a clip only the first call within {@code minIntervalMs} plays).
	 */
	public static void play(EffectTimeline from, String event, String vanilla, double x, double y, double z, double pitch, double volume) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		refresh();
		SoundMap.Decision d = map.decide(event, vanilla, FxSound::isLoaded, nowMs(), RND);
		if (!d.plays()) {
			if (d.kind() == SoundMap.Kind.LIMITED) {
				dropped++;
			}
			return;
		}
		boolean clip = d.kind() == SoundMap.Kind.FILE;
		if (clip && d.range() > 0 && mc.player != null && mc.player.squaredDistanceTo(x, y, z) > d.range() * d.range()) {
			return;
		}
		double outPitch = clip ? d.pitch() : pitch;
		double outVol = clip ? d.volume() : volume * d.volume();
		emit(from, d, clip, x, y, z, outPitch, outVol, mc);
	}

	/**
	 * Old-style call without an event id (code that appeared after the T12 sheet, e.g. the Uryu client effects): plays the vanilla sound as before,
	 * the event is not in the catalog so {@link SoundMap} falls back to {@code vanilla}.
	 */
	public static void play(EffectTimeline from, String vanilla, double x, double y, double z, double pitch, double volume) {
		play(from, "", vanilla, x, y, z, pitch, volume);
	}

	/** A moment with no vanilla sound of its own (new in T12): plays the clip of the event if there is one, otherwise nothing. {@code gain} scales the clip. */
	public static void playEvent(EffectTimeline from, String event, double x, double y, double z, double gain) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		refresh();
		SoundMap.Decision d = map.decide(event, null, FxSound::isLoaded, nowMs(), RND);
		if (d.kind() != SoundMap.Kind.FILE) {
			if (d.kind() == SoundMap.Kind.LIMITED) {
				dropped++;
			}
			return;
		}
		if (d.range() > 0 && mc.player != null && mc.player.squaredDistanceTo(x, y, z) > d.range() * d.range()) {
			return;
		}
		emit(from, d, true, x, y, z, d.pitch(), d.volume() * gain, mc);
	}

	private static void emit(EffectTimeline from, SoundMap.Decision d, boolean clip, double x, double y, double z, double pitch, double volume,
			MinecraftClient mc) {
		String id = d.soundId();
		double now = FxClock.now;
		Double last = LAST.get(id);
		if (last != null && now - last < 0.05 && now >= last) {
			dropped++;
			return;
		}
		if (!clip && id.endsWith("pointed_dripstone.land") && rateLimited(DRIPSTONE, now, 8)) {
			dropped++;
			return;
		}
		if (!clip && id.endsWith("pink_petals.break") && rateLimited(PETALS, now, 10)) {
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
		LOG.add(clip ? "file:" + id : id);
		mc.world.playSound(x, y, z, ev, clip ? category(d.category()) : SoundCategory.PLAYERS, (float) vol, (float) pitch, false);
	}

	/** Plays a non-positional UI sound (HUD): master volume only. {@code vanilla} and its pitch/volume are the fallback; a clip uses the config values. */
	public static void ui(String event, String vanilla, double pitch, double volume) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getSoundManager() == null) {
			return;
		}
		refresh();
		SoundMap.Decision d = map.decide(event, vanilla, FxSound::isLoaded, nowMs(), RND);
		if (!d.plays()) {
			return;
		}
		boolean clip = d.kind() == SoundMap.Kind.FILE;
		SoundEvent ev = resolve(d.soundId());
		if (ev == null) {
			return;
		}
		LOG.add(clip ? "file:" + d.soundId() : d.soundId());
		mc.getSoundManager().play(PositionedSoundInstance.master(ev, (float) (clip ? d.pitch() : pitch), (float) (clip ? d.volume() : volume * d.volume())));
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
