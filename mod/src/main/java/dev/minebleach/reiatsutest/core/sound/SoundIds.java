package dev.minebleach.reiatsutest.core.sound;

import java.util.List;

/**
 * R11 T12: the sound events of the mod, one id per MOMENT of an ability or of the game (not per vanilla layer: a moment may play several vanilla
 * layers as its fallback, see {@link SoundMap}). The id is the registry path of the {@code SoundEvent} {@code reiatsu_test:<id>} and the key in
 * {@code config/reiatsu_sounds.json}; the clip of the event is {@code assets/reiatsu_test/sounds/anime/<group>/<rest>_N.ogg} where
 * {@code <group>} is the first segment of the id and {@code <rest>} the other segments joined by {@code _}. The table with the description of every
 * event is {@code design/results/r11_sound_sheet.md}; {@code SoundSheetTest} keeps the sheet, this list, {@code sounds.json} and the code in step.
 * Generated from the sheet once; edit by hand together with the sheet. Pure Java.
 */
public final class SoundIds {
	/** Namespace of the events (the mod id). */
	public static final String NAMESPACE = "reiatsu_test";

	// ---- general.draw
	public static final String GENERAL_DRAW_START = "general.draw.start";
	public static final String GENERAL_DRAW_RELEASE = "general.draw.release";
	// ---- general.sheathe
	public static final String GENERAL_SHEATHE_START = "general.sheathe.start";
	public static final String GENERAL_SHEATHE_END = "general.sheathe.end";
	// ---- general.sword
	public static final String GENERAL_SWORD_SWING = "general.sword.swing";
	public static final String GENERAL_SWORD_HIT_FLESH = "general.sword.hit_flesh";
	public static final String GENERAL_SWORD_HIT_BLOCK = "general.sword.hit_block";
	// ---- general.block
	public static final String GENERAL_BLOCK_HIT = "general.block.hit";
	public static final String GENERAL_BLOCK_ABILITY = "general.block.ability";
	// ---- general.parry
	public static final String GENERAL_PARRY = "general.parry";
	// ---- general.hurt
	public static final String GENERAL_HURT = "general.hurt";
	// ---- general.death
	public static final String GENERAL_DEATH = "general.death";
	// ---- general.seal
	public static final String GENERAL_SEAL_COLLAPSE = "general.seal.collapse";
	// ---- general.bankai
	public static final String GENERAL_BANKAI_END = "general.bankai.end";
	// ---- general.title_card
	public static final String GENERAL_TITLE_CARD = "general.title_card";
	// ---- general.shunpo
	public static final String GENERAL_SHUNPO_START = "general.shunpo.start";
	public static final String GENERAL_SHUNPO_ARRIVE = "general.shunpo.arrive";
	// ---- byakuya.shunpo
	public static final String BYAKUYA_SHUNPO_ACCENT = "byakuya.shunpo.accent";
	// ---- rukia.shunpo
	public static final String RUKIA_SHUNPO_ACCENT = "rukia.shunpo.accent";
	// ---- general.pressure
	public static final String GENERAL_PRESSURE_RELEASE = "general.pressure.release";
	// ---- general.bankai
	public static final String GENERAL_BANKAI_READY = "general.bankai.ready";
	public static final String GENERAL_BANKAI_TIMER_TICK = "general.bankai.timer_tick";
	// ---- general.ability
	public static final String GENERAL_ABILITY_READY = "general.ability.ready";
	// ---- general.reiatsu
	public static final String GENERAL_REIATSU_EMPTY = "general.reiatsu.empty";
	// ---- general.rage
	public static final String GENERAL_RAGE_EMPTY = "general.rage.empty";
	// ---- general.ability
	public static final String GENERAL_ABILITY_COOLDOWN = "general.ability.cooldown";
	// ---- general.rage
	public static final String GENERAL_RAGE_GAIN = "general.rage.gain";
	// ---- general.reiatsu
	public static final String GENERAL_REIATSU_RESTORED = "general.reiatsu.restored";
	// ---- general.terrain
	public static final String GENERAL_TERRAIN_BREAK = "general.terrain.break";
	// ---- ui.voice_toast
	public static final String UI_VOICE_TOAST = "ui.voice_toast";
	// ---- ui.deny
	public static final String UI_DENY_SHIKAI_TIME = "ui.deny.shikai_time";
	public static final String UI_DENY_BLOCKED = "ui.deny.blocked";
	public static final String UI_DENY_STATE = "ui.deny.state";
	public static final String UI_DENY_ITEM = "ui.deny.item";
	// ---- ui.action_denied
	public static final String UI_ACTION_DENIED = "ui.action_denied";
	// ---- ui.book_page
	public static final String UI_BOOK_PAGE = "ui.book_page";
	// ---- byakuya.shikai
	public static final String BYAKUYA_SHIKAI_PINK_HOLD = "byakuya.shikai.pink_hold";
	public static final String BYAKUYA_SHIKAI_RELEASE_FLASH = "byakuya.shikai.release_flash";
	public static final String BYAKUYA_SHIKAI_RELEASE_BLAST = "byakuya.shikai.release_blast";
	public static final String BYAKUYA_SHIKAI_SWARM_RELEASE = "byakuya.shikai.swarm_release";
	public static final String BYAKUYA_SHIKAI_SWARM_FLUTTER = "byakuya.shikai.swarm_flutter";
	public static final String BYAKUYA_SHIKAI_SWARM_HUM = "byakuya.shikai.swarm_hum";
	public static final String BYAKUYA_SHIKAI_ATTACK_START = "byakuya.shikai.attack_start";
	public static final String BYAKUYA_SHIKAI_ATTACK_HIT = "byakuya.shikai.attack_hit";
	public static final String BYAKUYA_SHIKAI_ATTACK_PETALS = "byakuya.shikai.attack_petals";
	public static final String BYAKUYA_SHIKAI_BARRIER_START = "byakuya.shikai.barrier_start";
	public static final String BYAKUYA_SHIKAI_BARRIER_END = "byakuya.shikai.barrier_end";
	public static final String BYAKUYA_SHIKAI_BARRIER_HIT = "byakuya.shikai.barrier_hit";
	public static final String BYAKUYA_SHIKAI_CAGE_CAST = "byakuya.shikai.cage_cast";
	public static final String BYAKUYA_SHIKAI_CAGE_TELEGRAPH = "byakuya.shikai.cage_telegraph";
	public static final String BYAKUYA_SHIKAI_CAGE_WARN = "byakuya.shikai.cage_warn";
	public static final String BYAKUYA_SHIKAI_CAGE_PULSE = "byakuya.shikai.cage_pulse";
	public static final String BYAKUYA_SHIKAI_CAGE_END = "byakuya.shikai.cage_end";
	// ---- byakuya.bankai
	public static final String BYAKUYA_BANKAI_AURA = "byakuya.bankai.aura";
	public static final String BYAKUYA_BANKAI_SWORD_DROP = "byakuya.bankai.sword_drop";
	public static final String BYAKUYA_BANKAI_SWORD_HANG = "byakuya.bankai.sword_hang";
	public static final String BYAKUYA_BANKAI_SWORD_ENTER = "byakuya.bankai.sword_enter";
	public static final String BYAKUYA_BANKAI_SWORD_RING = "byakuya.bankai.sword_ring";
	public static final String BYAKUYA_BANKAI_NIGHT = "byakuya.bankai.night";
	public static final String BYAKUYA_BANKAI_HEARTBEAT = "byakuya.bankai.heartbeat";
	public static final String BYAKUYA_BANKAI_FAULT = "byakuya.bankai.fault";
	public static final String BYAKUYA_BANKAI_ERUPTION = "byakuya.bankai.eruption";
	public static final String BYAKUYA_BANKAI_ROWS_DONE = "byakuya.bankai.rows_done";
	public static final String BYAKUYA_BANKAI_ROWS_HUM = "byakuya.bankai.rows_hum";
	public static final String BYAKUYA_BANKAI_ROWS_DUST = "byakuya.bankai.rows_dust";
	public static final String BYAKUYA_BANKAI_SCATTER_COMMAND = "byakuya.bankai.scatter_command";
	public static final String BYAKUYA_BANKAI_SCATTER_DISSOLVE = "byakuya.bankai.scatter_dissolve";
	public static final String BYAKUYA_BANKAI_SCATTER_PETALS = "byakuya.bankai.scatter_petals";
	public static final String BYAKUYA_BANKAI_SCATTER_STORM = "byakuya.bankai.scatter_storm";
	public static final String BYAKUYA_BANKAI_SCATTER_ATTACK = "byakuya.bankai.scatter_attack";
	public static final String BYAKUYA_BANKAI_SCATTER_ENVELOP = "byakuya.bankai.scatter_envelop";
	public static final String BYAKUYA_BANKAI_SCATTER_ENVELOP_PETALS = "byakuya.bankai.scatter_envelop_petals";
	public static final String BYAKUYA_BANKAI_SCATTER_RETURN = "byakuya.bankai.scatter_return";
	public static final String BYAKUYA_BANKAI_SCATTER_REFORM = "byakuya.bankai.scatter_reform";
	public static final String BYAKUYA_BANKAI_SCATTER_REFORM_END = "byakuya.bankai.scatter_reform_end";
	public static final String BYAKUYA_BANKAI_SCATTER_IMPACT = "byakuya.bankai.scatter_impact";
	public static final String BYAKUYA_BANKAI_SCATTER_IMPACT_PETALS = "byakuya.bankai.scatter_impact_petals";
	public static final String BYAKUYA_BANKAI_HAKUTEIKEN_CONDENSE = "byakuya.bankai.hakuteiken_condense";
	public static final String BYAKUYA_BANKAI_HAKUTEIKEN_WINGS = "byakuya.bankai.hakuteiken_wings";
	public static final String BYAKUYA_BANKAI_HAKUTEIKEN_BLADE = "byakuya.bankai.hakuteiken_blade";
	public static final String BYAKUYA_BANKAI_HAKUTEIKEN_DISSOLVE = "byakuya.bankai.hakuteiken_dissolve";
	public static final String BYAKUYA_BANKAI_HAKUTEIKEN_REFORM = "byakuya.bankai.hakuteiken_reform";
	public static final String BYAKUYA_BANKAI_HAKUTEIKEN_STRIKE = "byakuya.bankai.hakuteiken_strike";
	public static final String BYAKUYA_BANKAI_HAKUTEIKEN_BURST = "byakuya.bankai.hakuteiken_burst";
	public static final String BYAKUYA_BANKAI_SENKEI = "byakuya.bankai.senkei";
	// ---- rukia.shikai
	public static final String RUKIA_SHIKAI_RELEASE_FLASH = "rukia.shikai.release_flash";
	public static final String RUKIA_SHIKAI_RELEASE_BLAST = "rukia.shikai.release_blast";
	public static final String RUKIA_SHIKAI_RELEASE_RING = "rukia.shikai.release_ring";
	public static final String RUKIA_SHIKAI_AURA = "rukia.shikai.aura";
	public static final String RUKIA_SHIKAI_TSUKISHIRO_WINDUP = "rukia.shikai.tsukishiro_windup";
	public static final String RUKIA_SHIKAI_TSUKISHIRO_PILLAR = "rukia.shikai.tsukishiro_pillar";
	public static final String RUKIA_SHIKAI_TSUKISHIRO_CRUST = "rukia.shikai.tsukishiro_crust";
	public static final String RUKIA_SHIKAI_TSUKISHIRO_SHATTER = "rukia.shikai.tsukishiro_shatter";
	public static final String RUKIA_SHIKAI_HAKUREN_START = "rukia.shikai.hakuren_start";
	public static final String RUKIA_SHIKAI_HAKUREN_SPIKES = "rukia.shikai.hakuren_spikes";
	public static final String RUKIA_SHIKAI_HAKUREN_FLARE = "rukia.shikai.hakuren_flare";
	public static final String RUKIA_SHIKAI_HAKUREN_TRAIL = "rukia.shikai.hakuren_trail";
	public static final String RUKIA_SHIKAI_HAKUREN_WAVE = "rukia.shikai.hakuren_wave";
	public static final String RUKIA_SHIKAI_HAKUREN_FREEZE = "rukia.shikai.hakuren_freeze";
	public static final String RUKIA_SHIKAI_HAKUREN_SHATTER = "rukia.shikai.hakuren_shatter";
	public static final String RUKIA_SHIKAI_SHIRAFUNE_CAST = "rukia.shikai.shirafune_cast";
	public static final String RUKIA_SHIKAI_SHIRAFUNE_CHARGE = "rukia.shikai.shirafune_charge";
	public static final String RUKIA_SHIKAI_SHIRAFUNE_THROW = "rukia.shikai.shirafune_throw";
	public static final String RUKIA_SHIKAI_SHIRAFUNE_HIT = "rukia.shikai.shirafune_hit";
	public static final String RUKIA_SHIKAI_SHIRAFUNE_CHILL = "rukia.shikai.shirafune_chill";
	public static final String RUKIA_SHIKAI_SHIRAFUNE_RETRACT = "rukia.shikai.shirafune_retract";
	// ---- rukia.bankai
	public static final String RUKIA_BANKAI_RELEASE_FLASH = "rukia.bankai.release_flash";
	public static final String RUKIA_BANKAI_RELEASE_BLAST = "rukia.bankai.release_blast";
	public static final String RUKIA_BANKAI_RELEASE_GATHER = "rukia.bankai.release_gather";
	public static final String RUKIA_BANKAI_RELEASE_BURST = "rukia.bankai.release_burst";
	public static final String RUKIA_BANKAI_RELEASE_FLOWER = "rukia.bankai.release_flower";
	public static final String RUKIA_BANKAI_RELEASE_FREEZE = "rukia.bankai.release_freeze";
	public static final String RUKIA_BANKAI_RELEASE_WINGS = "rukia.bankai.release_wings";
	public static final String RUKIA_BANKAI_RELEASE_AMBIENT = "rukia.bankai.release_ambient";
	public static final String RUKIA_BANKAI_RELEASE_SETTLE = "rukia.bankai.release_settle";
	public static final String RUKIA_BANKAI_AURA = "rukia.bankai.aura";
	public static final String RUKIA_BANKAI_HUM = "rukia.bankai.hum";
	public static final String RUKIA_BANKAI_SNOW_STEP = "rukia.bankai.snow_step";
	public static final String RUKIA_BANKAI_GLINT = "rukia.bankai.glint";
	public static final String RUKIA_BANKAI_MOB_FROST = "rukia.bankai.mob_frost";
	public static final String RUKIA_BANKAI_FIELD_FRONT = "rukia.bankai.field_front";
	public static final String RUKIA_BANKAI_FIELD_CHIME = "rukia.bankai.field_chime";
	public static final String RUKIA_BANKAI_FIELD_CRACKLE = "rukia.bankai.field_crackle";
	public static final String RUKIA_BANKAI_FIELD_CRYSTAL = "rukia.bankai.field_crystal";
	public static final String RUKIA_BANKAI_FIELD_THAW = "rukia.bankai.field_thaw";
	public static final String RUKIA_BANKAI_FIELD_THAW_CRACKLE = "rukia.bankai.field_thaw_crackle";
	public static final String RUKIA_BANKAI_ENCASE = "rukia.bankai.encase";
	public static final String RUKIA_BANKAI_ENCASE_BREAK = "rukia.bankai.encase_break";
	public static final String RUKIA_BANKAI_ICE_WALL_GATHER = "rukia.bankai.ice_wall_gather";
	public static final String RUKIA_BANKAI_ICE_WALL_RELEASE = "rukia.bankai.ice_wall_release";
	public static final String RUKIA_BANKAI_ICE_WALL_RISE = "rukia.bankai.ice_wall_rise";
	public static final String RUKIA_BANKAI_ICE_WALL_START = "rukia.bankai.ice_wall_start";
	public static final String RUKIA_BANKAI_ICE_WALL_LAYER = "rukia.bankai.ice_wall_layer";
	public static final String RUKIA_BANKAI_ICE_WALL_HIT = "rukia.bankai.ice_wall_hit";
	public static final String RUKIA_BANKAI_ICE_WALL_BREAK = "rukia.bankai.ice_wall_break";
	// ---- gin.shikai
	public static final String GIN_SHIKAI_RELEASE = "gin.shikai.release";
	public static final String GIN_SHIKAI_IKOROSE_CAST = "gin.shikai.ikorose_cast";
	public static final String GIN_SHIKAI_HYAPPONZASHI_WINDUP = "gin.shikai.hyapponzashi_windup";
	public static final String GIN_SHIKAI_SNAKE_HOOK_CAST = "gin.shikai.snake_hook_cast";
	public static final String GIN_SHIKAI_SNAKE_HOOK_THROW = "gin.shikai.snake_hook_throw";
	public static final String GIN_SHIKAI_SNAKE_HOOK_HIT = "gin.shikai.snake_hook_hit";
	public static final String GIN_SHIKAI_STRIKE = "gin.shikai.strike";
	public static final String GIN_SHIKAI_STRIKE_HIT = "gin.shikai.strike_hit";
	// ---- gin.bankai
	public static final String GIN_BANKAI_RELEASE = "gin.bankai.release";
	public static final String GIN_BANKAI_BUTOU_RENJIN_CAST = "gin.bankai.butou_renjin_cast";
	public static final String GIN_BANKAI_BUTOU_RENJIN_SWEEP = "gin.bankai.butou_renjin_sweep";
	public static final String GIN_BANKAI_KOROSE_CAST = "gin.bankai.korose_cast";
	public static final String GIN_BANKAI_KOROSE_CHANNEL = "gin.bankai.korose_channel";
	public static final String GIN_BANKAI_KOROSE_DISSOLVE = "gin.bankai.korose_dissolve";
	public static final String GIN_BANKAI_KOROSE_INTERRUPT = "gin.bankai.korose_interrupt";
	public static final String GIN_BANKAI_HAKUFUKU = "gin.bankai.hakufuku";
	// ---- uryu.shikai
	public static final String URYU_SHIKAI_RELEASE = "uryu.shikai.release";
	public static final String URYU_SHIKAI_HEILIG_PFEIL = "uryu.shikai.heilig_pfeil";
	public static final String URYU_SHIKAI_SPRENGER = "uryu.shikai.sprenger";
	public static final String URYU_SHIKAI_HIRENKYAKU = "uryu.shikai.hirenkyaku";
	// ---- uryu.bankai
	public static final String URYU_BANKAI_RELEASE = "uryu.bankai.release";
	public static final String URYU_BANKAI_LICHT_REGEN = "uryu.bankai.licht_regen";
	public static final String URYU_BANKAI_HOMING_VOLLEY = "uryu.bankai.homing_volley";
	public static final String URYU_BANKAI_STERNENSTAUB = "uryu.bankai.sternenstaub";
	// ---- kido.byakurai
	public static final String KIDO_BYAKURAI_CAST = "kido.byakurai.cast";
	public static final String KIDO_BYAKURAI_HIT = "kido.byakurai.hit";
	// ---- kido.shakkaho
	public static final String KIDO_SHAKKAHO_CAST = "kido.shakkaho.cast";
	public static final String KIDO_SHAKKAHO_FLIGHT = "kido.shakkaho.flight";
	public static final String KIDO_SHAKKAHO_BURST = "kido.shakkaho.burst";
	// ---- kido.sai
	public static final String KIDO_SAI_CAST = "kido.sai.cast";
	public static final String KIDO_SAI_HIT = "kido.sai.hit";

	/** Every event id, in the order of the sheet. */
	public static final List<String> ALL = List.of(
			GENERAL_DRAW_START,
			GENERAL_DRAW_RELEASE,
			GENERAL_SHEATHE_START,
			GENERAL_SHEATHE_END,
			GENERAL_SWORD_SWING,
			GENERAL_SWORD_HIT_FLESH,
			GENERAL_SWORD_HIT_BLOCK,
			GENERAL_BLOCK_HIT,
			GENERAL_BLOCK_ABILITY,
			GENERAL_PARRY,
			GENERAL_HURT,
			GENERAL_DEATH,
			GENERAL_SEAL_COLLAPSE,
			GENERAL_BANKAI_END,
			GENERAL_TITLE_CARD,
			GENERAL_SHUNPO_START,
			GENERAL_SHUNPO_ARRIVE,
			BYAKUYA_SHUNPO_ACCENT,
			RUKIA_SHUNPO_ACCENT,
			GENERAL_PRESSURE_RELEASE,
			GENERAL_BANKAI_READY,
			GENERAL_BANKAI_TIMER_TICK,
			GENERAL_ABILITY_READY,
			GENERAL_REIATSU_EMPTY,
			GENERAL_RAGE_EMPTY,
			GENERAL_ABILITY_COOLDOWN,
			GENERAL_RAGE_GAIN,
			GENERAL_REIATSU_RESTORED,
			GENERAL_TERRAIN_BREAK,
			UI_VOICE_TOAST,
			UI_DENY_SHIKAI_TIME,
			UI_DENY_BLOCKED,
			UI_DENY_STATE,
			UI_DENY_ITEM,
			UI_ACTION_DENIED,
			UI_BOOK_PAGE,
			BYAKUYA_SHIKAI_PINK_HOLD,
			BYAKUYA_SHIKAI_RELEASE_FLASH,
			BYAKUYA_SHIKAI_RELEASE_BLAST,
			BYAKUYA_SHIKAI_SWARM_RELEASE,
			BYAKUYA_SHIKAI_SWARM_FLUTTER,
			BYAKUYA_SHIKAI_SWARM_HUM,
			BYAKUYA_SHIKAI_ATTACK_START,
			BYAKUYA_SHIKAI_ATTACK_HIT,
			BYAKUYA_SHIKAI_ATTACK_PETALS,
			BYAKUYA_SHIKAI_BARRIER_START,
			BYAKUYA_SHIKAI_BARRIER_END,
			BYAKUYA_SHIKAI_BARRIER_HIT,
			BYAKUYA_SHIKAI_CAGE_CAST,
			BYAKUYA_SHIKAI_CAGE_TELEGRAPH,
			BYAKUYA_SHIKAI_CAGE_WARN,
			BYAKUYA_SHIKAI_CAGE_PULSE,
			BYAKUYA_SHIKAI_CAGE_END,
			BYAKUYA_BANKAI_AURA,
			BYAKUYA_BANKAI_SWORD_DROP,
			BYAKUYA_BANKAI_SWORD_HANG,
			BYAKUYA_BANKAI_SWORD_ENTER,
			BYAKUYA_BANKAI_SWORD_RING,
			BYAKUYA_BANKAI_NIGHT,
			BYAKUYA_BANKAI_HEARTBEAT,
			BYAKUYA_BANKAI_FAULT,
			BYAKUYA_BANKAI_ERUPTION,
			BYAKUYA_BANKAI_ROWS_DONE,
			BYAKUYA_BANKAI_ROWS_HUM,
			BYAKUYA_BANKAI_ROWS_DUST,
			BYAKUYA_BANKAI_SCATTER_COMMAND,
			BYAKUYA_BANKAI_SCATTER_DISSOLVE,
			BYAKUYA_BANKAI_SCATTER_PETALS,
			BYAKUYA_BANKAI_SCATTER_STORM,
			BYAKUYA_BANKAI_SCATTER_ATTACK,
			BYAKUYA_BANKAI_SCATTER_ENVELOP,
			BYAKUYA_BANKAI_SCATTER_ENVELOP_PETALS,
			BYAKUYA_BANKAI_SCATTER_RETURN,
			BYAKUYA_BANKAI_SCATTER_REFORM,
			BYAKUYA_BANKAI_SCATTER_REFORM_END,
			BYAKUYA_BANKAI_SCATTER_IMPACT,
			BYAKUYA_BANKAI_SCATTER_IMPACT_PETALS,
			BYAKUYA_BANKAI_HAKUTEIKEN_CONDENSE,
			BYAKUYA_BANKAI_HAKUTEIKEN_WINGS,
			BYAKUYA_BANKAI_HAKUTEIKEN_BLADE,
			BYAKUYA_BANKAI_HAKUTEIKEN_DISSOLVE,
			BYAKUYA_BANKAI_HAKUTEIKEN_REFORM,
			BYAKUYA_BANKAI_HAKUTEIKEN_STRIKE,
			BYAKUYA_BANKAI_HAKUTEIKEN_BURST,
			BYAKUYA_BANKAI_SENKEI,
			RUKIA_SHIKAI_RELEASE_FLASH,
			RUKIA_SHIKAI_RELEASE_BLAST,
			RUKIA_SHIKAI_RELEASE_RING,
			RUKIA_SHIKAI_AURA,
			RUKIA_SHIKAI_TSUKISHIRO_WINDUP,
			RUKIA_SHIKAI_TSUKISHIRO_PILLAR,
			RUKIA_SHIKAI_TSUKISHIRO_CRUST,
			RUKIA_SHIKAI_TSUKISHIRO_SHATTER,
			RUKIA_SHIKAI_HAKUREN_START,
			RUKIA_SHIKAI_HAKUREN_SPIKES,
			RUKIA_SHIKAI_HAKUREN_FLARE,
			RUKIA_SHIKAI_HAKUREN_TRAIL,
			RUKIA_SHIKAI_HAKUREN_WAVE,
			RUKIA_SHIKAI_HAKUREN_FREEZE,
			RUKIA_SHIKAI_HAKUREN_SHATTER,
			RUKIA_SHIKAI_SHIRAFUNE_CAST,
			RUKIA_SHIKAI_SHIRAFUNE_CHARGE,
			RUKIA_SHIKAI_SHIRAFUNE_THROW,
			RUKIA_SHIKAI_SHIRAFUNE_HIT,
			RUKIA_SHIKAI_SHIRAFUNE_CHILL,
			RUKIA_SHIKAI_SHIRAFUNE_RETRACT,
			RUKIA_BANKAI_RELEASE_FLASH,
			RUKIA_BANKAI_RELEASE_BLAST,
			RUKIA_BANKAI_RELEASE_GATHER,
			RUKIA_BANKAI_RELEASE_BURST,
			RUKIA_BANKAI_RELEASE_FLOWER,
			RUKIA_BANKAI_RELEASE_FREEZE,
			RUKIA_BANKAI_RELEASE_WINGS,
			RUKIA_BANKAI_RELEASE_AMBIENT,
			RUKIA_BANKAI_RELEASE_SETTLE,
			RUKIA_BANKAI_AURA,
			RUKIA_BANKAI_HUM,
			RUKIA_BANKAI_SNOW_STEP,
			RUKIA_BANKAI_GLINT,
			RUKIA_BANKAI_MOB_FROST,
			RUKIA_BANKAI_FIELD_FRONT,
			RUKIA_BANKAI_FIELD_CHIME,
			RUKIA_BANKAI_FIELD_CRACKLE,
			RUKIA_BANKAI_FIELD_CRYSTAL,
			RUKIA_BANKAI_FIELD_THAW,
			RUKIA_BANKAI_FIELD_THAW_CRACKLE,
			RUKIA_BANKAI_ENCASE,
			RUKIA_BANKAI_ENCASE_BREAK,
			RUKIA_BANKAI_ICE_WALL_GATHER,
			RUKIA_BANKAI_ICE_WALL_RELEASE,
			RUKIA_BANKAI_ICE_WALL_RISE,
			RUKIA_BANKAI_ICE_WALL_START,
			RUKIA_BANKAI_ICE_WALL_LAYER,
			RUKIA_BANKAI_ICE_WALL_HIT,
			RUKIA_BANKAI_ICE_WALL_BREAK,
			GIN_SHIKAI_RELEASE,
			GIN_SHIKAI_IKOROSE_CAST,
			GIN_SHIKAI_HYAPPONZASHI_WINDUP,
			GIN_SHIKAI_SNAKE_HOOK_CAST,
			GIN_SHIKAI_SNAKE_HOOK_THROW,
			GIN_SHIKAI_SNAKE_HOOK_HIT,
			GIN_SHIKAI_STRIKE,
			GIN_SHIKAI_STRIKE_HIT,
			GIN_BANKAI_RELEASE,
			GIN_BANKAI_BUTOU_RENJIN_CAST,
			GIN_BANKAI_BUTOU_RENJIN_SWEEP,
			GIN_BANKAI_KOROSE_CAST,
			GIN_BANKAI_KOROSE_CHANNEL,
			GIN_BANKAI_KOROSE_DISSOLVE,
			GIN_BANKAI_KOROSE_INTERRUPT,
			GIN_BANKAI_HAKUFUKU,
			URYU_SHIKAI_RELEASE,
			URYU_SHIKAI_HEILIG_PFEIL,
			URYU_SHIKAI_SPRENGER,
			URYU_SHIKAI_HIRENKYAKU,
			URYU_BANKAI_RELEASE,
			URYU_BANKAI_LICHT_REGEN,
			URYU_BANKAI_HOMING_VOLLEY,
			URYU_BANKAI_STERNENSTAUB,
			KIDO_BYAKURAI_CAST,
			KIDO_BYAKURAI_HIT,
			KIDO_SHAKKAHO_CAST,
			KIDO_SHAKKAHO_FLIGHT,
			KIDO_SHAKKAHO_BURST,
			KIDO_SAI_CAST,
			KIDO_SAI_HIT);

	private SoundIds() {
	}

	/** The group (first segment): the folder under {@code sounds/anime/}. */
	public static String group(String id) {
		int dot = id.indexOf('.');
		return dot < 0 ? id : id.substring(0, dot);
	}

	/** Path of the clip {@code n} (1-based) of the event under {@code assets/reiatsu_test/sounds/}, without the extension. */
	public static String clipPath(String id, int n) {
		int dot = id.indexOf('.');
		String rest = dot < 0 ? id : id.substring(dot + 1);
		return "anime/" + group(id) + "/" + rest.replace('.', '_') + "_" + n;
	}
}
