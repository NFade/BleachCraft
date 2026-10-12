package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.state.BankaiFieldSpec;
import dev.minebleach.reiatsutest.core.world.FrostConfig;
import dev.minebleach.reiatsutest.net.EntityFxS2C;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The passive frost territory of the Rukia bankai (mech2 task 4; it replaces the old second ability Absolute Zero and the old 3 block slow
 * aura). For as long as the bankai lasts every valid enemy standing in the territory, the land the bankai froze (centre = her feet at the
 * release, radius = {@code frost.radius}, 30 by default, growing with the freeze wave), gets
 * <ul>
 * <li>Slowness {@code slowAmplifier + 1}, a short one renewed every {@code refreshTicks}, and</li>
 * <li>{@code tickDamage} HP (2.0 = one heart) of armor-ignoring cold damage every {@code periodTicks} (3 s).</li>
 * </ul>
 * Enemies: players (when {@code Tuning.affectPlayers} and the server pvp allow it, the same filter as every ability) and hostile mobs; never
 * the caster, allies of the same team, pets, Creative players or passive animals. A Byakuya barrier on the target keeps the Slowness off and cuts the
 * damage like it cuts any other. The territory ends with the bankai through the one bankai cleanup ({@link #end}); the tick also stops by itself
 * the moment the machine is no longer in the Rukia bankai.
 */
final class BankaiField {
	private record Field(RegistryKey<World> dim, double cx, double cy, double cz, int startTick) {
	}

	private static final Map<UUID, Field> FIELDS = new HashMap<>();
	/** Slowness applications and damage pulses since the server started (dev audit). */
	private static int slowPulses;
	private static int damagePulses;

	private BankaiField() {
	}

	/** Every server tick for a Rukia in BANKAI. The territory is created at the first tick it sees (the release point is her feet then). */
	static void tick(ServerPlayerEntity caster, BankaiFieldSpec spec, int now) {
		Field f = FIELDS.computeIfAbsent(caster.getUuid(),
				k -> new Field(caster.getServerWorld().getRegistryKey(), caster.getX(), caster.getY(), caster.getZ(), now));
		long age = now - f.startTick();
		boolean slow = spec.slowDue(age);
		boolean hurt = spec.damageDue(age);
		if (!slow && !hurt) {
			return;
		}
		if (caster.getServerWorld().getRegistryKey() != f.dim()) {
			return; // another dimension ends the bankai anyway; never hit across worlds
		}
		FrostConfig fc = FrostField.config();
		double radius = BankaiFieldSpec.radiusAt(age, fc.radius(), fc.waveSpeed(), fc.enabled());
		if (radius < 0.5) {
			return;
		}
		double height = Math.max(fc.up(), fc.down());
		List<LivingEntity> inside = new ArrayList<>();
		for (LivingEntity e : Targeting.cylinder(caster, new Vec3d(f.cx(), f.cy(), f.cz()), radius, height, spec.maxTargets())) {
			boolean player = e instanceof PlayerEntity;
			boolean invulnerable = player && ((PlayerEntity) e).getAbilities().invulnerable;
			if (BankaiFieldSpec.eligible(player, e instanceof Monster, e.isTeammate(caster), invulnerable)) {
				inside.add(e);
			}
		}
		if (inside.isEmpty()) {
			return;
		}
		DmgLog.begin("rukia.bankai.field");
		try {
			if (slow) {
				slowPulses++;
				for (LivingEntity e : inside) {
					Targeting.slow(caster, e, spec.slowAmplifier(), spec.slowTicks());
				}
				ServerFx.entities(caster, EntityFxS2C.SLOWED, now + spec.slowTicks(), inside);
			}
			if (hurt) {
				damagePulses++;
				// resetInvulnerability: the pulse must land as exactly tickDamage even right after another hit (no i-frame reduction)
				for (LivingEntity e : inside) {
					Targeting.hurt(caster, e, spec.tickDamage(), DamageTypes.INDIRECT_MAGIC, true);
				}
				ServerFx.entities(caster, EntityFxS2C.HIT, now, inside);
			}
		} finally {
			DmgLog.end();
		}
	}

	/** The one bankai cleanup ends the territory. Returns true when there was one. */
	static boolean end(UUID owner) {
		return FIELDS.remove(owner) != null;
	}

	/** 1 while a territory of this owner exists (dev audit; it must be 0 once the bankai is over). */
	static int live(UUID owner) {
		return FIELDS.containsKey(owner) ? 1 : 0;
	}

	static void clearAll() {
		FIELDS.clear();
	}

	static int slowPulses() {
		return slowPulses;
	}

	static int damagePulses() {
		return damagePulses;
	}
}
