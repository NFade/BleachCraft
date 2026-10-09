package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.LivingEntity;

/**
 * Hooks of the draw / sheathe animation (step B3) for the effect code of phase 6. Fired on the client thread from the
 * client tick, for every player (the local one and the others in view).
 *
 * <ul>
 * <li>{@link Phase#DRAW_START}: the state left SEALED, the blade starts to slide out of the saya.</li>
 * <li>{@link Phase#DRAW_RELEASE}: the blade is clear of the saya; in the SAME tick the held model switches from the sealed
 * sword to the released model of the new state. THIS is the moment of the release flash: draw the flash here and the
 * model swap stays hidden. {@code target} is the new state.</li>
 * <li>{@link Phase#SHEATHE_START}: the state returned to SEALED, the blade starts to slide back.</li>
 * <li>{@link Phase#SHEATHE_END}: the sword is sheathed again (a seal effect may end here).</li>
 * </ul>
 * With {@code draw_animation_seconds} = 0 only DRAW_RELEASE and SHEATHE_END are fired (immediately).
 */
public final class DrawEvents {
	public enum Phase {
		DRAW_START, DRAW_RELEASE, SHEATHE_START, SHEATHE_END
	}

	/**
	 * @param entity the player holding the sword
	 * @param character which zanpakuto (from the synced attachment while released, NONE otherwise)
	 * @param target the state the draw leads to (DRAW_*), or SEALED (SHEATHE_*)
	 * @param local true for the local player (first person view of the effect)
	 */
	@FunctionalInterface
	public interface Listener {
		void onDraw(Phase phase, LivingEntity entity, CharacterId character, ZanpakutoState target, boolean local);
	}

	public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
			listeners -> (phase, entity, character, target, local) -> {
				for (Listener l : listeners) {
					l.onDraw(phase, entity, character, target, local);
				}
			});

	private DrawEvents() {
	}
}
