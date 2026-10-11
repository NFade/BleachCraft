package dev.minebleach.reiatsutest.core.combat;

import java.util.UUID;

/**
 * One successful parry. Published on {@link ParryBus}; the future Rage (B4: rage grows from successful parries) subscribes to it.
 *
 * @param defender        the player who parried
 * @param attacker        the entity whose hit was parried (a player or a mob)
 * @param tick            server tick of the parry
 * @param preventedDamage damage the cancelled hit would have dealt (health points)
 */
public record ParryEvent(UUID defender, UUID attacker, int tick, float preventedDamage) {
}
