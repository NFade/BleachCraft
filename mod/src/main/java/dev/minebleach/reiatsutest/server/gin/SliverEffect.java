package dev.minebleach.reiatsutest.server.gin;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * The marker of a sliver on a living entity: a status effect with no icon, no particles and no effect of its own. It exists so that every
 * way of clearing effects in the game cleanses the sliver for free and without a mixin: a bucket of milk, a totem pop, /effect clear, and
 * the death of the carrier. The authoritative record is still {@code SliverBook}; the glue compares the two every tick and treats a
 * marker that went missing before the timer as "milk" (the cleanse reason of the design).
 */
public final class SliverEffect extends StatusEffect {
	private static RegistryEntry<StatusEffect> entry;

	private SliverEffect() {
		super(StatusEffectCategory.NEUTRAL, 0xE8F4FF);
	}

	/** Registers the effect; called once from the mod initialiser (before the first player joins). */
	public static void register() {
		if (entry == null) {
			entry = Registry.registerReference(Registries.STATUS_EFFECT, ReiatsuTest.id("gin_sliver"), new SliverEffect());
		}
	}

	public static RegistryEntry<StatusEffect> entry() {
		return entry;
	}

	/** Puts the marker on {@code target} for {@code ticks} (replaces an older one: this is also the Renjin timer reset). */
	public static void apply(LivingEntity target, int ticks) {
		target.removeStatusEffect(entry); // a shorter new duration would not replace a longer old one
		target.addStatusEffect(new StatusEffectInstance(entry, Math.max(1, ticks), 0, false, false, false));
	}

	public static boolean present(LivingEntity target) {
		return target.hasStatusEffect(entry);
	}

	public static void remove(LivingEntity target) {
		target.removeStatusEffect(entry);
	}
}
