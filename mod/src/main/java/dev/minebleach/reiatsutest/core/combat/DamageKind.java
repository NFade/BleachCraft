package dev.minebleach.reiatsutest.core.combat;

/** What kind of damage a hit is, as far as the block cares. The Minecraft side maps its damage types to this. */
public enum DamageKind {
	/** A direct melee hit of a living attacker (player attack, mob attack). The only kind a block reduces and a parry cancels. */
	MELEE,
	/** Everything else: abilities (freeze, indirect magic), projectiles, fall, fire, explosions. Never reduced, never parried. */
	OTHER
}
