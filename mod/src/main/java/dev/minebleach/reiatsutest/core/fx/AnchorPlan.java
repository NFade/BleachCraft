package dev.minebleach.reiatsutest.core.fx;

import java.util.List;

/**
 * Decides which effect anchors the server spawns, ends or re-phases for an {@code effect_event} id (VFX_STORYBOARD 1.2
 * "Anchor rules", S1 to S3). Pure data: {@code ServerFx} glue executes the commands with real entities.
 * <ul>
 * <li>SWARM (Byakuya shikai petals): spawned by id 3, follows the owner, ended by 4 (the sword is dropped), 10, 11 and every reset.</li>
 * <li>ROWS (Byakuya bankai blades): spawned by id 4, STATIC at the release point (S2), ended by 10, 11 and every reset.</li>
 * <li>ROWS phase (S3): id 32 sets {@link #PHASE_SCATTERED}, id 33 {@link #PHASE_CONSUMED} with the current game time.</li>
 * </ul>
 */
public final class AnchorPlan {
	public static final byte KIND_FIELD = 0;
	public static final byte KIND_SWARM = 1;
	public static final byte KIND_ROWS = 2;
	public static final byte KIND_WINGS = 3;
	public static final byte PHASE_STANDING = 0;
	public static final byte PHASE_SCATTERED = 32;
	public static final byte PHASE_CONSUMED = 33;

	public enum Op { SPAWN_SWARM, END_SWARM, SPAWN_ROWS, END_ROWS, ROWS_PHASE }

	public record Command(Op op, byte phaseKind) {
		static Command of(Op op) {
			return new Command(op, PHASE_STANDING);
		}
	}

	private AnchorPlan() {
	}

	/** Commands for one effect event, in execution order (an old anchor of the same kind is always ended before a new one). */
	public static List<Command> forEffect(int effectId) {
		return switch (effectId) {
			case 3 -> List.of(Command.of(Op.END_SWARM), Command.of(Op.END_ROWS), Command.of(Op.SPAWN_SWARM));
			case 4 -> List.of(Command.of(Op.END_SWARM), Command.of(Op.END_ROWS), Command.of(Op.SPAWN_ROWS));
			case 10, 11 -> List.of(Command.of(Op.END_SWARM), Command.of(Op.END_ROWS));
			case 32 -> List.of(new Command(Op.ROWS_PHASE, PHASE_SCATTERED));
			case 33 -> List.of(new Command(Op.ROWS_PHASE, PHASE_CONSUMED));
			default -> List.of();
		};
	}

	/** Death, logout, dimension change, hand lost, seal without an event: everything of that owner ends. */
	public static List<Command> forReset() {
		return List.of(Command.of(Op.END_SWARM), Command.of(Op.END_ROWS));
	}

	/** Flat look yaw in degrees (Minecraft convention: 0 = +Z, 90 = -X) of a look vector, 0 for a vertical look. */
	public static float flatYawDeg(double dx, double dz) {
		if (dx * dx + dz * dz < 1.0E-8) {
			return 0f;
		}
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}
}
