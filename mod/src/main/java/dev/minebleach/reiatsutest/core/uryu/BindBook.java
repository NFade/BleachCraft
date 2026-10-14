package dev.minebleach.reiatsutest.core.uryu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The pure rules of the Sprenger bind (round 2): an entity that is inside the pentagon at the bind moment is ROOTED (no walking, no jump)
 * and, if it is a player, may not use the shunpo, for {@link UryuBalance#SPRENGER_BIND_TICKS} ticks (from the bind moment to the
 * explosion). The bind ends early as soon as the entity takes damage ({@link #onDamage}: its own explosion does not count, the glue
 * {@link #release}s the entity first), and at once on death, logout, a state change of the caster, a change of dimension and the
 * server stopping (the glue calls {@link #release} / {@link #clear}).
 *
 * <p>One book for the whole server: entity uuid to the first tick at which the bind is over. The glue applies the attribute modifiers
 * and the shunpo ban from {@link #active}.
 */
public final class BindBook {
	private final Map<UUID, Integer> until = new HashMap<>();

	/** Binds {@code id} from tick {@code now} for {@code ticks}; a longer bind already running is kept. */
	public void bind(UUID id, int now, int ticks) {
		if (ticks <= 0) {
			return;
		}
		until.merge(id, now + ticks, Math::max);
	}

	/** True while the entity is bound at tick {@code now}: from the bind tick up to, not including, the end tick. */
	public boolean active(UUID id, int now) {
		Integer end = until.get(id);
		return end != null && now < end;
	}

	/** True when the book has the entity (also after its time ran out, until the glue takes it off). */
	public boolean has(UUID id) {
		return until.containsKey(id);
	}

	/** First tick at which the bind is over, or 0. */
	public int endOf(UUID id) {
		Integer end = until.get(id);
		return end == null ? 0 : end;
	}

	/**
	 * The entity took damage: a running bind ends now. True when there was a bind to break (the glue then takes the modifiers off).
	 * An entity that is not bound, or whose bind the glue already released (the own explosion), returns false.
	 */
	public boolean onDamage(UUID id) {
		return until.remove(id) != null;
	}

	/** Ends the bind for any other reason (explosion, death, logout, state or dimension change, stop). True when there was one. */
	public boolean release(UUID id) {
		return until.remove(id) != null;
	}

	/** Entities whose time is up at tick {@code now} (still in the book; the glue takes them off and {@link #release}s them). */
	public List<UUID> expired(int now) {
		List<UUID> out = new ArrayList<>();
		for (Map.Entry<UUID, Integer> e : until.entrySet()) {
			if (now >= e.getValue()) {
				out.add(e.getKey());
			}
		}
		return out;
	}

	public List<UUID> all() {
		return new ArrayList<>(until.keySet());
	}

	public int size() {
		return until.size();
	}

	public void clear() {
		until.clear();
	}
}
