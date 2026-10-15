package dev.minebleach.reiatsutest.core.sound;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * R11 T12: the shipped defaults of every sound event (interval, range, category); the rest ({@code files} = the event itself, no fallback override,
 * volume 1, pitch 1) is the same for all. {@link SoundMap} overlays {@code config/reiatsu_sounds.json} on these. Pure Java.
 */
public final class SoundCatalog {
	/** Default minimum interval between two plays of the same clip (ms): the vanilla layers of one moment collapse into one clip. */
	public static final int DEFAULT_INTERVAL_MS = 80;
	/** Default audible range of a clip (blocks); also the radius in which the server sends the event. */
	public static final double DEFAULT_RANGE = 32.0;
	public static final String DEFAULT_CATEGORY = "players";

	private static final Map<String, SoundEntry> DEFAULTS = new LinkedHashMap<>();

	static {
		add(SoundIds.GENERAL_DRAW_START, 150, 32.0, "players");
		add(SoundIds.GENERAL_DRAW_RELEASE, 150, 32.0, "players");
		add(SoundIds.GENERAL_SHEATHE_START, 150, 32.0, "players");
		add(SoundIds.GENERAL_SHEATHE_END, 150, 32.0, "players");
		add(SoundIds.GENERAL_SWORD_SWING, 80, 32.0, "players");
		add(SoundIds.GENERAL_SWORD_HIT_FLESH, 80, 32.0, "players");
		add(SoundIds.GENERAL_SWORD_HIT_BLOCK, 80, 32.0, "players");
		add(SoundIds.GENERAL_BLOCK_HIT, 120, 32.0, "players");
		add(SoundIds.GENERAL_BLOCK_ABILITY, 120, 32.0, "players");
		add(SoundIds.GENERAL_PARRY, 150, 32.0, "players");
		add(SoundIds.GENERAL_HURT, 80, 32.0, "players");
		add(SoundIds.GENERAL_DEATH, 80, 32.0, "players");
		add(SoundIds.GENERAL_SEAL_COLLAPSE, 80, 32.0, "players");
		add(SoundIds.GENERAL_BANKAI_END, 80, 32.0, "players");
		add(SoundIds.GENERAL_TITLE_CARD, 300, 32.0, "players");
		add(SoundIds.GENERAL_SHUNPO_START, 100, 32.0, "players");
		add(SoundIds.GENERAL_SHUNPO_ARRIVE, 100, 32.0, "players");
		add(SoundIds.BYAKUYA_SHUNPO_ACCENT, 100, 32.0, "players");
		add(SoundIds.RUKIA_SHUNPO_ACCENT, 100, 32.0, "players");
		add(SoundIds.GENERAL_PRESSURE_RELEASE, 1500, 48.0, "players");
		add(SoundIds.GENERAL_BANKAI_READY, 300, 16.0, "master");
		add(SoundIds.GENERAL_BANKAI_TIMER_TICK, 300, 16.0, "master");
		add(SoundIds.GENERAL_ABILITY_READY, 100, 16.0, "master");
		add(SoundIds.GENERAL_REIATSU_EMPTY, 150, 16.0, "master");
		add(SoundIds.GENERAL_RAGE_EMPTY, 150, 16.0, "master");
		add(SoundIds.GENERAL_ABILITY_COOLDOWN, 100, 16.0, "master");
		add(SoundIds.GENERAL_RAGE_GAIN, 80, 32.0, "players");
		add(SoundIds.GENERAL_REIATSU_RESTORED, 80, 32.0, "players");
		add(SoundIds.GENERAL_TERRAIN_BREAK, 80, 32.0, "players");
		add(SoundIds.UI_VOICE_TOAST, 100, 16.0, "master");
		add(SoundIds.UI_DENY_SHIKAI_TIME, 150, 16.0, "master");
		add(SoundIds.UI_DENY_BLOCKED, 150, 16.0, "master");
		add(SoundIds.UI_DENY_STATE, 150, 16.0, "master");
		add(SoundIds.UI_DENY_ITEM, 150, 16.0, "master");
		add(SoundIds.UI_ACTION_DENIED, 150, 16.0, "master");
		add(SoundIds.UI_BOOK_PAGE, 50, 16.0, "master");
		add(SoundIds.BYAKUYA_SHIKAI_PINK_HOLD, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_RELEASE_FLASH, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_RELEASE_BLAST, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_SWARM_RELEASE, 250, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_SWARM_FLUTTER, 250, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_SWARM_HUM, 400, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_ATTACK_START, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_ATTACK_HIT, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_ATTACK_PETALS, 150, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_BARRIER_START, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_BARRIER_END, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_BARRIER_HIT, 150, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_CAGE_CAST, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_CAGE_TELEGRAPH, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_CAGE_WARN, 100, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_CAGE_PULSE, 100, 32.0, "players");
		add(SoundIds.BYAKUYA_SHIKAI_CAGE_END, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_AURA, 500, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SWORD_DROP, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SWORD_HANG, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SWORD_ENTER, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SWORD_RING, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_NIGHT, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_HEARTBEAT, 700, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_FAULT, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_ERUPTION, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_ROWS_DONE, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_ROWS_HUM, 2500, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_ROWS_DUST, 120, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_COMMAND, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_DISSOLVE, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_PETALS, 100, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_STORM, 350, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_ATTACK, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_ENVELOP, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_ENVELOP_PETALS, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_RETURN, 150, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_REFORM, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_REFORM_END, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_IMPACT, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SCATTER_IMPACT_PETALS, 100, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_HAKUTEIKEN_CONDENSE, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_HAKUTEIKEN_WINGS, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_HAKUTEIKEN_BLADE, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_HAKUTEIKEN_DISSOLVE, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_HAKUTEIKEN_REFORM, 80, 32.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_HAKUTEIKEN_STRIKE, 80, 64.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_HAKUTEIKEN_BURST, 80, 64.0, "players");
		add(SoundIds.BYAKUYA_BANKAI_SENKEI, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_RELEASE_FLASH, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_RELEASE_BLAST, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_RELEASE_RING, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_AURA, 500, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_WINDUP, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_PILLAR, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_CRUST, 100, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_SHATTER, 60, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_HAKUREN_START, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_HAKUREN_SPIKES, 70, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_HAKUREN_FLARE, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_HAKUREN_TRAIL, 120, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_HAKUREN_WAVE, 300, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_HAKUREN_FREEZE, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_HAKUREN_SHATTER, 100, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_CAST, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_CHARGE, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_THROW, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_HIT, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_CHILL, 80, 32.0, "players");
		add(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_RETRACT, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_RELEASE_FLASH, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_RELEASE_BLAST, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_RELEASE_GATHER, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_RELEASE_BURST, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_RELEASE_FLOWER, 100, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_RELEASE_FREEZE, 60, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_RELEASE_WINGS, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_RELEASE_AMBIENT, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_RELEASE_SETTLE, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_AURA, 500, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_HUM, 1500, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_SNOW_STEP, 500, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_GLINT, 200, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_MOB_FROST, 300, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_FIELD_FRONT, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_FIELD_CHIME, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_FIELD_CRACKLE, 120, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_FIELD_CRYSTAL, 150, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_FIELD_THAW, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_FIELD_THAW_CRACKLE, 120, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_ENCASE, 100, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_ENCASE_BREAK, 100, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_ICE_WALL_GATHER, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_ICE_WALL_RELEASE, 80, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_ICE_WALL_RISE, 100, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_ICE_WALL_START, 200, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_ICE_WALL_LAYER, 100, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_ICE_WALL_HIT, 120, 32.0, "players");
		add(SoundIds.RUKIA_BANKAI_ICE_WALL_BREAK, 200, 32.0, "players");
		add(SoundIds.GIN_SHIKAI_RELEASE, 80, 48.0, "players");
		add(SoundIds.GIN_SHIKAI_IKOROSE_CAST, 80, 32.0, "players");
		add(SoundIds.GIN_SHIKAI_HYAPPONZASHI_WINDUP, 80, 32.0, "players");
		add(SoundIds.GIN_SHIKAI_SNAKE_HOOK_CAST, 80, 32.0, "players");
		add(SoundIds.GIN_SHIKAI_SNAKE_HOOK_THROW, 80, 32.0, "players");
		add(SoundIds.GIN_SHIKAI_SNAKE_HOOK_HIT, 80, 32.0, "players");
		add(SoundIds.GIN_SHIKAI_STRIKE, 60, 32.0, "players");
		add(SoundIds.GIN_SHIKAI_STRIKE_HIT, 60, 32.0, "players");
		add(SoundIds.GIN_BANKAI_RELEASE, 80, 64.0, "players");
		add(SoundIds.GIN_BANKAI_BUTOU_RENJIN_CAST, 80, 32.0, "players");
		add(SoundIds.GIN_BANKAI_BUTOU_RENJIN_SWEEP, 100, 32.0, "players");
		add(SoundIds.GIN_BANKAI_KOROSE_CAST, 80, 32.0, "players");
		add(SoundIds.GIN_BANKAI_KOROSE_CHANNEL, 80, 32.0, "players");
		add(SoundIds.GIN_BANKAI_KOROSE_DISSOLVE, 80, 32.0, "players");
		add(SoundIds.GIN_BANKAI_KOROSE_INTERRUPT, 80, 32.0, "players");
		add(SoundIds.GIN_BANKAI_HAKUFUKU, 80, 32.0, "players");
		add(SoundIds.URYU_SHIKAI_RELEASE, 80, 32.0, "players");
		add(SoundIds.URYU_SHIKAI_HEILIG_PFEIL, 80, 32.0, "players");
		add(SoundIds.URYU_SHIKAI_SPRENGER, 80, 32.0, "players");
		add(SoundIds.URYU_SHIKAI_HIRENKYAKU, 80, 32.0, "players");
		add(SoundIds.URYU_BANKAI_RELEASE, 80, 32.0, "players");
		add(SoundIds.URYU_BANKAI_LICHT_REGEN, 80, 32.0, "players");
		add(SoundIds.URYU_BANKAI_HOMING_VOLLEY, 80, 32.0, "players");
		add(SoundIds.URYU_BANKAI_STERNENSTAUB, 80, 32.0, "players");
		add(SoundIds.KIDO_BYAKURAI_CAST, 80, 32.0, "players");
		add(SoundIds.KIDO_BYAKURAI_HIT, 80, 32.0, "players");
		add(SoundIds.KIDO_SHAKKAHO_CAST, 80, 32.0, "players");
		add(SoundIds.KIDO_SHAKKAHO_FLIGHT, 300, 32.0, "players");
		add(SoundIds.KIDO_SHAKKAHO_BURST, 80, 48.0, "players");
		add(SoundIds.KIDO_SAI_CAST, 80, 32.0, "players");
		add(SoundIds.KIDO_SAI_HIT, 80, 32.0, "players");
	}

	private SoundCatalog() {
	}

	private static void add(String id, int intervalMs, double range, String category) {
		DEFAULTS.put(id, new SoundEntry(id, java.util.List.of(SoundIds.NAMESPACE + ":" + id), "", 1.0, 1.0, 1.0, intervalMs, range, category));
	}

	/** The default entry of every event, in the order of {@link SoundIds#ALL}. */
	public static Map<String, SoundEntry> defaults() {
		return java.util.Collections.unmodifiableMap(DEFAULTS);
	}

	/** The default entry of one event, or null for an unknown id. */
	public static SoundEntry of(String id) {
		return DEFAULTS.get(id);
	}
}
