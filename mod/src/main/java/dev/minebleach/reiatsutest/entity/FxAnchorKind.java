package dev.minebleach.reiatsutest.entity;

/** {@code kind} of an {@link FxAnchorEntity} (VFX_STORYBOARD 1.2 "Anchor rules"). */
public final class FxAnchorKind {
	/** One per cast, static, lives for the anchor life of its table. */
	public static final byte FIELD = 0;
	/** Byakuya petal swarm, follows the owner from effect id 3 until 10/11/death/logout. */
	public static final byte SWARM = 1;
	/** Byakuya bankai blade rows, static at the release point (S2): p0 = flat look yaw (deg), p1 = feet y. */
	public static final byte ROWS = 2;
	/** Hakuteiken wings and halo, follows the owner, effect id 33 only. */
	public static final byte WINGS = 3;

	/** {@code phaseKind} values (S3). */
	public static final byte PHASE_STANDING = 0;
	public static final byte PHASE_SCATTERED = 32;
	public static final byte PHASE_CONSUMED = 33;

	private FxAnchorKind() {
	}

	public static boolean followsOwner(byte kind) {
		return kind == SWARM || kind == WINGS;
	}
}
