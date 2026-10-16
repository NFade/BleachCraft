package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.combat.ParryBus;
import dev.minebleach.reiatsutest.core.rage.RageConfig;
import dev.minebleach.reiatsutest.core.rage.RageMeter;
import dev.minebleach.reiatsutest.core.rage.RageReward;
import dev.minebleach.reiatsutest.core.rage.RageTuning;
import dev.minebleach.reiatsutest.core.uryu.BowDraw;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.data.RageData;
import dev.minebleach.reiatsutest.registry.data.RageTuningData;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Minecraft glue of Rage (PLAN_2026-10-11 B4): the third resource next to reiatsu. The rules live in {@link RageMeter} (pure,
 * tested). Gain comes from
 * <ul>
 * <li>damage TAKEN by the player and damage DEALT by the player, both from {@code AFTER_DAMAGE} (the amount that really landed:
 * a parried hit gives nothing, a blocked hit counts for the reduced amount, a hit eaten by the Byakuya barrier counts nothing);
 * the attacker is {@code source.getAttacker()}, so sword hits (Better Combat calls the vanilla attack), abilities and
 * projectiles of a player all count, against players and mobs;</li>
 * <li>the killing blow: Fabric does not fire AFTER_DAMAGE for a hit that kills, which is why small mobs gave "almost nothing";
 * the hit is remembered in {@code ALLOW_DAMAGE} (asked amount, victim health) and settled in {@code AFTER_DEATH};</li>
 * <li>successful parries ({@link ParryBus}).</li>
 * </ul>
 * R11 T3: damage taken gives nothing (default), sword/projectile hits give 0.3 per hp (mobs 25 percent, capped per target per 10 s), a parry
 * 4.0 at most once a second, an ability or kido cast one fixed 2.0 for its first landed hit ({@link #onCast}); the numbers are the
 * {@code rage} section of the config ({@link #config()}).
 * Every gain is an INFO line {@code [rage] +X from <reason> ...} in the server log. The bar drains out of combat, resets on death
 * and logout, and is synced to its owner as the {@link ModAttachments#RAGE} attachment (only when the value changed).
 *
 * <p>Round 5: shikai and bankai entry need Rage ({@link RageConfig#shikaiCostTenths()}, {@link RageConfig#bankaiCostTenths()}); R15: only bankai pays it, shikai just needs the bar to hold the 40.
 * The state machine reads {@link #valueOf} at the request and emits a RageSpent event that {@link ZanpakutoManager} turns into
 * {@link #spend}; keys and voice share that one path ({@code ZanpakutoManager.performTransition}).
 */
public final class RageManager {
	/** The numbers in use (R11 T3: the {@code rage} section of config/reiatsu_test.json, re-read by {@code /reiatsu reload}). */
	private static volatile RageConfig config = RageConfig.defaults();

	private static final Map<UUID, RageMeter> METERS = new HashMap<>();
	/** Last player-caused hit seen by ALLOW_DAMAGE per victim entity id, for the killing blow. Cleared every tick. */
	private static final Map<Integer, Pending> PENDING = new HashMap<>();
	/** Players in dev god mode ({@code /reiatsu dev god on}): their Rage bar is held at the maximum every tick. */
	private static final java.util.Set<UUID> INFINITE = new java.util.HashSet<>();
	private static MinecraftServer server;
	/** R11 review fix 2: greater than 0 while {@code Targeting.hurtUnrewarded} runs its damage call (single server thread); hits inside pay no Rage. */
	private static int noRewardDepth;
	/** A player-caused hit that may turn out lethal: who, how much was asked, how much health the victim had. */
	private record Pending(UUID attacker, float amount, float healthBefore) {
	}

	private RageManager() {
	}

	/** Marks the damage call that follows as unrewarded (the frost field pulses): see {@link RageReward}. Pair with {@link #endUnrewarded}. */
	static void beginUnrewarded() {
		noRewardDepth++;
	}

	static void endUnrewarded() {
		noRewardDepth = Math.max(0, noRewardDepth - 1);
	}

	/** R13: the Uryu mark of the damage call in progress (single server thread); NONE outside {@link #markUryu}. */
	private static RageReward.UryuMark uryuMark = RageReward.UryuMark.NONE;
	/** R13: damage the marked basic arrow was shot with (before armor), the basis of its proportional Rage. */
	private static float uryuRawHp;

	/**
	 * R13: runs {@code body} (an {@code UryuTargets.hurt} call) with the Rage mark of an Uryu arrow or blast: {@code BOW} pays Rage proportional to
	 * {@code rawHp}, {@code CAST} pays the fixed reward of the cast (once). Everything else keeps the normal rules.
	 */
	public static void markUryu(RageReward.UryuMark mark, float rawHp, Runnable body) {
		RageReward.UryuMark prevMark = uryuMark;
		float prevHp = uryuRawHp;
		uryuMark = mark;
		uryuRawHp = rawHp;
		try {
			body.run();
		} finally {
			uryuMark = prevMark;
			uryuRawHp = prevHp;
		}
	}

	/** The rage numbers in force. */
	public static RageConfig config() {
		return config;
	}

	/** Loads the {@code rage} section at start-up and registers the reload; call before the first session is created. */
	public static void initConfig() {
		config = ModConfig.loadRage();
		ConfigReload.register(() -> {
			RageConfig c = ModConfig.loadRage();
			config = c;
			for (RageMeter m : METERS.values()) {
				m.setConfig(c);
			}
			ZanpakutoManager.refreshRage(c);
			ReiatsuTest.LOGGER.info("[rage] config reloaded: dealt {}/hp (cap {}), mob {}%, target cap {}/10s, parry {} (gap {} ticks), ability {}, bankai min shikai {} ticks",
					c.dealtTenthsPerHp(), c.maxDealtGainPerHitTenths(), c.mobMultiplierPercent(), c.targetCapPer10sTenths(), c.parryGainTenths(),
					c.parryGapTicks(), c.abilityHitTenths(), c.bankaiMinShikaiTicks());
		});
	}

	public static void init() {
		initConfig();
		ParryBus.register(e -> {
			MinecraftServer srv = server;
			ServerPlayerEntity defender = srv == null ? null : srv.getPlayerManager().getPlayer(e.defender());
			if (defender == null) {
				return; // defender went offline in the same tick
			}
			int gained = meterOf(e.defender()).onParry(e.tick());
			logGain(defender, gained, "parry", "");
		});
		// registered after ZanpakutoManager's listener: a hit it cancels (parry, barrier) never reaches this one
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity a && a != entity && amount > 0.0F) {
				PENDING.put(entity.getId(), new Pending(a.getUuid(), amount, entity.getHealth() + entity.getAbsorptionAmount()));
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
				onDamaged(entity, source, damageTaken, blocked));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			onKilled(entity, source);
			if (entity instanceof ServerPlayerEntity p) {
				meterOf(p.getUuid()).reset();
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
			meterOf(handler.player.getUuid()).reset();
			sync(handler.player, meterOf(handler.player.getUuid()));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
			METERS.remove(handler.player.getUuid());
			INFINITE.remove(handler.player.getUuid());
		});
		ServerTickEvents.END_SERVER_TICK.register(RageManager::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> server = srv);
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			METERS.clear();
			INFINITE.clear();
			PENDING.clear();
			server = null;
		});
	}

	private static RageMeter meterOf(UUID id) {
		return METERS.computeIfAbsent(id, k -> new RageMeter(config));
	}

	/** The meter of an online player (created on first use). Read-only use for HUD/commands; spend through {@link #spend}. */
	public static RageMeter meter(ServerPlayerEntity p) {
		return meterOf(p.getUuid());
	}

	/** Live Rage of a player in tenths (0 for a player without a meter): the state machine's read-only view. */
	public static int valueOf(UUID id) {
		RageMeter m = METERS.get(id);
		return m == null ? 0 : m.value();
	}

	/** True if the player has at least {@code costTenths} of Rage. */
	public static boolean canSpend(ServerPlayerEntity p, int costTenths) {
		return meterOf(p.getUuid()).canSpend(costTenths);
	}

	/** Pays {@code costTenths} if possible (syncs right away); false changes nothing. */
	public static boolean spend(ServerPlayerEntity p, int costTenths) {
		RageMeter m = meterOf(p.getUuid());
		boolean ok = m.spend(costTenths);
		if (ok) {
			if (costTenths > 0) {
				ReiatsuTest.LOGGER.info("[rage] -{} spent ({}: {}/{})", fmt(costTenths), p.getName().getString(), fmt(m.value()),
						fmt(m.state().max()));
			}
			sync(p, m);
		}
		return ok;
	}

	/** Dev command {@code /reiatsu dev rage <points>}: sets the bar directly and syncs at once. */
	public static void devSet(ServerPlayerEntity p, int tenths) {
		RageMeter m = meterOf(p.getUuid());
		m.devSet(tenths, p.getServer().getTicks());
		ReiatsuTest.LOGGER.info("[rage] {} set to {} by command", p.getName().getString(), fmt(m.value()));
		sync(p, m);
	}

	/** Dev god mode: keeps the Rage bar of {@code p} full every tick (or lets it behave normally again). */
	public static void setInfinite(ServerPlayerEntity p, boolean on) {
		if (on) {
			INFINITE.add(p.getUuid());
			devSet(p, meterOf(p.getUuid()).state().max());
		} else {
			INFINITE.remove(p.getUuid());
		}
	}

	private static void onDamaged(LivingEntity victim, DamageSource source, float damageTaken, boolean blocked) {
		if (!(damageTaken > 0.0F) || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return; // nothing landed, or /kill and the void
		}
		int now = victim.getServer() == null ? 0 : victim.getServer().getTicks();
		if (victim instanceof ServerPlayerEntity p && !blocked) {
			int gained = meterOf(p.getUuid()).onDamageTaken(damageTaken, now);
			logGain(p, gained, "taken", " " + fmt1(damageTaken) + " hp from " + sourceName(source));
		}
		Entity attacker = source.getAttacker();
		if (attacker instanceof ServerPlayerEntity a && a != victim && !(victim instanceof ArmorStandEntity)) {
			RageMeter m = meterOf(a.getUuid());
			boolean mob = !(victim instanceof PlayerEntity);
			RageReward reward = RageReward.classify(noRewardDepth > 0, isAbilityDamage(source), uryuMark, BowDraw.tuning().castRewardForAbilityArrows());
			if (reward == RageReward.NONE) {
				return; // R11 review fix 2: a frost field pulse pays nothing and leaves the armed cast reward for a real ability hit
			}
			if (reward == RageReward.ARROW) {
				int gained = m.onFixedDealt(BowDraw.tuning().arrowRageTenths(uryuRawHp), victim.getId(), mob, now);
				logGain(a, gained, "arrow", " " + fmt1(uryuRawHp) + " hp shot at " + victim.getName().getString() + (mob ? " (mob)" : ""));
			} else if (reward == RageReward.ABILITY) {
				int gained = m.onAbilityHit(mob, now); // R11 T3: one fixed reward per cast, however many targets it hits
				if (gained > 0) {
					logGain(a, gained, "ability", " " + fmt1(damageTaken) + " hp to " + victim.getName().getString());
				}
			} else {
				int gained = m.onDamageDealt(RukiaMomentum.unboost(victim, now, damageTaken), victim.getId(), mob, now); // R11 T6: not multiplied by the stacks
				logGain(a, gained, "dealt", " " + fmt1(damageTaken) + " hp to " + victim.getName().getString() + (mob ? " (mob)" : ""));
			}
		}
	}

	/** Damage of the mod's abilities and kido: {@code Targeting.hurt} uses FREEZE and INDIRECT_MAGIC with the caster as attacker. */
	static boolean isAbilityDamage(DamageSource source) {
		return source.isOf(DamageTypes.FREEZE) || source.isOf(DamageTypes.INDIRECT_MAGIC);
	}

	/** An accepted ability or kido cast arms the one reward of its first landed hit (R11 T3). */
	public static void onCast(ServerPlayerEntity p) {
		meterOf(p.getUuid()).armAbility(p.getServer().getTicks());
	}

	/** The killing blow of a player: AFTER_DAMAGE did not fire for it, so settle the hit remembered by ALLOW_DAMAGE. */
	private static void onKilled(LivingEntity victim, DamageSource source) {
		Pending hit = PENDING.remove(victim.getId());
		if (hit == null || !(source.getAttacker() instanceof ServerPlayerEntity a) || !a.getUuid().equals(hit.attacker())
				|| source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}
		float landed = Math.min(hit.amount(), hit.healthBefore());
		if (victim instanceof ArmorStandEntity || noRewardDepth > 0) {
			return; // an armor stand, or an unrewarded hit (frost field pulse, R11 review fix 2)
		}
		RageMeter m = meterOf(a.getUuid());
		boolean mob = !(victim instanceof PlayerEntity);
		int now = a.getServer().getTicks();
		RageReward kill = RageReward.classify(false, isAbilityDamage(source), uryuMark, BowDraw.tuning().castRewardForAbilityArrows());
		if (kill == RageReward.ARROW) {
			int gained = m.onFixedDealt(BowDraw.tuning().arrowRageTenths(uryuRawHp), victim.getId(), mob, now);
			logGain(a, gained, "arrow-kill", " " + fmt1(uryuRawHp) + " hp shot at " + victim.getName().getString() + (mob ? " (mob)" : ""));
			return;
		}
		if (kill == RageReward.ABILITY) {
			int gained = m.onAbilityHit(mob, now);
			if (gained > 0) {
				logGain(a, gained, "ability-kill", " " + fmt1(landed) + " hp to " + victim.getName().getString());
			}
			return;
		}
		int gained = m.onDamageDealt(landed, victim.getId(), mob, now);
		logGain(a, gained, "dealt-kill", " " + fmt1(landed) + " hp to " + victim.getName().getString() + (mob ? " (mob)" : ""));
	}

	private static void logGain(ServerPlayerEntity p, int tenths, String reason, String detail) {
		RageMeter m = meterOf(p.getUuid());
		ReiatsuTest.LOGGER.info("[rage] +{} from {}{} ({}: {}/{})", fmt(tenths), reason, detail, p.getName().getString(),
				fmt(m.value()), fmt(m.state().max()));
	}

	private static String sourceName(DamageSource source) {
		Entity a = source.getAttacker();
		String type = source.getType().msgId();
		return a == null ? type : type + "/" + a.getName().getString();
	}

	private static String fmt(int tenths) {
		return String.format(Locale.ROOT, "%.1f", tenths / 10.0);
	}

	private static String fmt1(float v) {
		return String.format(Locale.ROOT, "%.1f", v);
	}

	private static void tick(MinecraftServer srv) {
		PENDING.clear();
		int now = srv.getTicks();
		for (ServerPlayerEntity p : srv.getPlayerManager().getPlayerList()) {
			RageMeter m = meterOf(p.getUuid());
			m.tick(now);
			if (INFINITE.contains(p.getUuid())) {
				m.devSet(m.state().max(), now);
			}
			sync(p, m);
		}
	}

	/** Writes the attachment only when it differs, so a quiet bar costs no packets. */
	private static void sync(ServerPlayerEntity p, RageMeter m) {
		RageData d = RageData.of(m.state());
		if (!d.equals(p.getAttached(ModAttachments.RAGE))) {
			p.setAttached(ModAttachments.RAGE, d);
		}
		// R11 review fix 4: the HUD shows the costs and the minimum shikai time of THIS config (a reload reaches the owners within a tick)
		RageTuningData t = RageTuningData.of(RageTuning.of(config));
		if (!t.equals(p.getAttached(ModAttachments.RAGE_TUNING))) {
			p.setAttached(ModAttachments.RAGE_TUNING, t);
		}
	}
}
