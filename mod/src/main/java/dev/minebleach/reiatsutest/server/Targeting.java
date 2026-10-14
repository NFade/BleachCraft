package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.combat.BlockRules;
import dev.minebleach.reiatsutest.core.combat.HitRules;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Area queries, hit tests and effect helpers for abilities. Everything is server-side and loaded-chunks only. */
final class Targeting {
	private Targeting() {
	}

	/**
	 * Never the caster, a spectator, a dead entity, a pet of the caster, an immune type, or a player while player hits are off
	 * ({@link Tuning#affectPlayers}, default on) or the server has {@code pvp=false}. The verdict is {@link #refusal}.
	 */
	static boolean valid(ServerPlayerEntity caster, LivingEntity e) {
		return refusal(caster, e) == HitRules.Refusal.OK;
	}

	static HitRules.Refusal refusal(ServerPlayerEntity caster, LivingEntity e) {
		boolean pet = e instanceof Tameable t && caster.getUuid().equals(t.getOwnerUuid());
		boolean player = e instanceof PlayerEntity;
		return HitRules.refusal(e == caster, e.isAlive(), e.isSpectator(), e.getType().isIn(Tuning.IMMUNE), pet, player,
				Tuning.affectPlayers, caster.getServer() != null && caster.getServer().isPvpEnabled());
	}

	/** Predicate for entity queries; players that are left out for a config reason are named in the {@code [dmg]} log. */
	private static boolean validLogged(ServerPlayerEntity caster, LivingEntity e) {
		HitRules.Refusal r = refusal(caster, e);
		if (r == HitRules.Refusal.PLAYERS_OFF || r == HitRules.Refusal.SERVER_PVP_OFF || r == HitRules.Refusal.IMMUNE_TYPE
				|| r == HitRules.Refusal.SPECTATOR) {
			if (e instanceof PlayerEntity) {
				DmgLog.refused(caster, e, r);
			}
		}
		return r == HitRules.Refusal.OK;
	}

	/** Valid living entities whose bounding box touches {@code box}, nearest to {@code near} first, at most {@code max}. */
	static List<LivingEntity> inBox(ServerPlayerEntity caster, Box box, Vec3d near, int max) {
		ServerWorld world = caster.getServerWorld();
		List<LivingEntity> found = world.getEntitiesByClass(LivingEntity.class, box, e -> validLogged(caster, e));
		found.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(near)));
		return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
	}

	static List<LivingEntity> sphere(ServerPlayerEntity caster, Vec3d center, double radius, int max) {
		Box box = Box.of(center, radius * 2, radius * 2, radius * 2);
		List<LivingEntity> in = new ArrayList<>();
		for (LivingEntity e : inBox(caster, box.expand(1.0), center, Integer.MAX_VALUE)) {
			// distance to the closest point of the bounding box, so big mobs on the edge count
			Box b = e.getBoundingBox();
			double dx = Math.max(Math.max(b.minX - center.x, 0), center.x - b.maxX);
			double dy = Math.max(Math.max(b.minY - center.y, 0), center.y - b.maxY);
			double dz = Math.max(Math.max(b.minZ - center.z, 0), center.z - b.maxZ);
			if (dx * dx + dy * dy + dz * dz <= radius * radius && !IceWall.blocked(caster.getServerWorld(), center, b.getCenter())) { // R11 T5: not through the ice wall
				in.add(e);
				if (in.size() >= max) {
					break;
				}
			}
		}
		return in;
	}

	/** Horizontal circle of {@code radius} around {@code center}, within {@code height} blocks above and below. */
	static List<LivingEntity> cylinder(ServerPlayerEntity caster, Vec3d center, double radius, double height, int max) {
		Box box = new Box(center.x - radius, center.y - height, center.z - radius,
				center.x + radius, center.y + height, center.z + radius);
		List<LivingEntity> in = new ArrayList<>();
		for (LivingEntity e : inBox(caster, box, center, Integer.MAX_VALUE)) {
			double dx = e.getX() - center.x;
			double dz = e.getZ() - center.z;
			double r = radius + e.getWidth() * 0.5;
			if (dx * dx + dz * dz <= r * r && !IceWall.blocked(caster.getServerWorld(), center.add(0, 1.0, 0), e.getBoundingBox().getCenter())) { // R11 T5
				in.add(e);
				if (in.size() >= max) {
					break;
				}
			}
		}
		return in;
	}

	/**
	 * Entities within {@code halfWidth} of the segment origin + dir * [from, to] (dir normalised). Entities in {@code skip} are left out.
	 */
	static List<LivingEntity> segment(ServerPlayerEntity caster, Vec3d origin, Vec3d dir, double from, double to,
			double halfWidth, int max, Set<Integer> skip) {
		Vec3d a = origin.add(dir.multiply(from));
		Vec3d b = origin.add(dir.multiply(to));
		Box box = new Box(a, b).expand(halfWidth + 1.0);
		List<LivingEntity> in = new ArrayList<>();
		for (LivingEntity e : inBox(caster, box, origin, Integer.MAX_VALUE)) {
			if (skip != null && skip.contains(e.getId())) {
				continue;
			}
			Vec3d c = e.getBoundingBox().getCenter().subtract(origin);
			double along = c.dotProduct(dir);
			double lateral = c.subtract(dir.multiply(along)).length();
			double slack = e.getWidth() * 0.5;
			if (along >= from - slack && along <= to + slack && lateral <= halfWidth + slack
					&& !IceWall.blocked(caster.getServerWorld(), origin, e.getBoundingBox().getCenter())) { // R11 T5: a wall between the origin and the target
				in.add(e);
				if (in.size() >= max) {
					break;
				}
			}
		}
		return in;
	}

	/** Block-only ray from {@code start} to {@code start + dir * range}: the hit point, or the far end on a miss. */
	static Vec3d rayPoint(ServerPlayerEntity caster, Vec3d start, Vec3d dir, double range) {
		Vec3d end = start.add(dir.multiply(range));
		BlockHitResult hit = caster.getServerWorld()
				.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
	}

	/** First valid living entity along the ray before any block, or null. */
	static LivingEntity firstOnRay(ServerPlayerEntity caster, Vec3d start, Vec3d dir, double range) {
		Vec3d end = rayPoint(caster, start, dir, range);
		Box box = new Box(start, end).expand(1.5);
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : inBox(caster, box, start, Integer.MAX_VALUE)) {
			Optional<Vec3d> hit = e.getBoundingBox().expand(0.3).raycast(start, end);
			if (hit.isPresent()) {
				double d = start.squaredDistanceTo(hit.get());
				if (d < bestDist) {
					bestDist = d;
					best = e;
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ effects

	/**
	 * Damages {@code target} on behalf of {@code caster} and writes the outcome to the {@code [dmg]} log (who, how much, and the
	 * reason when nothing was lost: Creative, parry, barrier, i-frames, team ...).
	 *
	 * <p>THE one damage function of every ability and kido of the mod (area hits, beams, damage over time ticks of the fields, the
	 * flight of the swarm), so the r8 block rule lives here: a target that holds the zanpakuto block (right mouse) takes
	 * {@code abilityBlockReduction} (30 percent) less. The reduced amount then goes through {@code LivingEntity.damage}, where the
	 * Byakuya barrier cut (80 percent), armor and resistance apply to it as usual, so the cuts multiply (30 + 80 percent leave 14
	 * percent of the hit) and the total can never pass 100 percent.
	 */
	static HitRules.Outcome hurt(ServerPlayerEntity caster, LivingEntity target, float hp, RegistryKey<DamageType> type, boolean resetInvulnerability) {
		return hurt(caster, target, hp, type, resetInvulnerability, true, false);
	}

	/**
	 * Same as {@link #hurt} without the Rukia passive (R11 T6): kido and the damage-over-time pulses of the frost field neither get the
	 * bonus of the stacks nor add a stack, so a field with an enemy inside cannot keep the passive full on its own.
	 */
	static HitRules.Outcome hurtPlain(ServerPlayerEntity caster, LivingEntity target, float hp, RegistryKey<DamageType> type, boolean resetInvulnerability) {
		return hurt(caster, target, hp, type, resetInvulnerability, false, false);
	}

	/**
	 * {@link #hurtPlain} that also pays no Rage to the caster (R11 review fix 2): the pulses of the frost field are damage over time that exists only
	 * while the field stands; they must not consume the armed cast reward ({@code abilityHitTenths}) nor pay the "dealt" share. Kido keeps using
	 * {@link #hurtPlain} and pays its cast reward as intended.
	 */
	static HitRules.Outcome hurtUnrewarded(ServerPlayerEntity caster, LivingEntity target, float hp, RegistryKey<DamageType> type, boolean resetInvulnerability) {
		return hurt(caster, target, hp, type, resetInvulnerability, false, true);
	}

	private static HitRules.Outcome hurt(ServerPlayerEntity caster, LivingEntity target, float hp, RegistryKey<DamageType> type, boolean resetInvulnerability,
			boolean momentum, boolean unrewarded) {
		if (momentum) {
			hp *= RukiaMomentum.abilityMultiplier(caster); // R11 T6: Rukia's stacks raise the ability before the block and barrier cuts
		}
		float asked = hp;
		if (ZanpakutoItem.isBlocking(target)) {
			hp = BlockRules.abilityDamage(true, hp, ZanpakutoManager.configOf(caster).abilityBlockReduction());
			if (hp != asked) {
				DmgLog.blockCut(caster, target, asked, hp);
				if (target instanceof ServerPlayerEntity defender) {
					// r8 fx2: the lighter block picture and sound; a blade scrape only when the hit came from a sword, never changes the numbers
					ServerFx.blockFeedbackAbility(defender, caster, asked - hp, type.equals(DamageTypes.PLAYER_ATTACK) || type.equals(DamageTypes.MOB_ATTACK));
				}
			}
		}
		DamageSource src = caster.getServerWorld().getDamageSources().create(type, caster);
		if (resetInvulnerability) {
			target.timeUntilRegen = 0; // multi-hit abilities are meant to land every hit
		}
		boolean creative = target instanceof PlayerEntity pl && (pl.isCreative() || pl.getAbilities().invulnerable);
		boolean invulnerable = target.isInvulnerableTo(src);
		boolean team = target instanceof PlayerEntity pl && !caster.shouldDamagePlayer(pl);
		boolean iframes = target.timeUntilRegen > 10;
		float before = target.getHealth() + target.getAbsorptionAmount();
		DmgLog.clearVeto();
		boolean returned;
		if (unrewarded) {
			RageManager.beginUnrewarded(); // AFTER_DAMAGE / AFTER_DEATH run inside damage(): the mark covers exactly this call
			try {
				returned = target.damage(src, hp);
			} finally {
				RageManager.endUnrewarded();
			}
		} else {
			returned = target.damage(src, hp);
		}
		float lost = before - (target.getHealth() + target.getAbsorptionAmount());
		HitRules.Outcome out = HitRules.outcome(hp, returned, lost, DmgLog.vetoFor(target), creative, invulnerable, team, iframes);
		DmgLog.clearVeto();
		DmgLog.hit(caster, target, asked, Math.max(0.0F, lost), out);
		if (momentum && out == HitRules.Outcome.DAMAGED) {
			RukiaMomentum.onAbilityHit(caster, target);
		}
		return out;
	}

	/** Slowness plus vanilla freezing ticks (frozen ticks decay by 2 per tick, so 2 * duration keeps them above the visual threshold). */
	static void chill(ServerPlayerEntity caster, LivingEntity target, int slownessAmplifier, int durationTicks) {
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, durationTicks, slownessAmplifier, false, true, true), caster);
		int want = target.getMinFreezeDamageTicks() + 2 * durationTicks;
		if (target.getFrozenTicks() < want) {
			target.setFrozenTicks(want);
		}
	}

	/** Slowness only (no frostbite): the petal cage and other non-ice controls. */
	static void slow(ServerPlayerEntity caster, LivingEntity target, int amplifier, int durationTicks) {
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, durationTicks, amplifier, false, true, true), caster);
	}

	static void knockback(LivingEntity target, Vec3d from, double strength) {
		target.takeKnockback(strength, from.x - target.getX(), from.z - target.getZ());
		target.velocityModified = true;
	}
}
