package dev.minebleach.reiatsutest.core.combat;

/**
 * Result of {@link BlockRules#resolve}: what a block does to one incoming hit.
 *
 * @param kind   NONE (not affected), BLOCKED (reduced) or PARRIED (cancelled)
 * @param damage the damage that should actually be dealt (equals the input for NONE, 0 for PARRIED)
 */
public record BlockOutcome(Kind kind, float damage) {
	public enum Kind { NONE, BLOCKED, PARRIED }

	public boolean parried() {
		return kind == Kind.PARRIED;
	}

	public boolean blocked() {
		return kind == Kind.BLOCKED;
	}
}
