package dev.minebleach.reiatsutest.core.uryu;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The projectile list of the Quincy arrows (design/URYU.md section 7): no entity type, just (position, velocity, owner, damage, life)
 * stepped once per server tick. One instance per world. Each arrow moves along its velocity, the swept segment is tested against
 * blocks and entities through the {@link HitWorld} callback (so the logic runs against a fake world in tests), a hit entity is
 * reported once per arrow (the per arrow, per target hit limit), a pierce count lets an arrow go through that many targets and
 * a homing arrow steers towards the target position the world reports, at most {@code turnRadPerTick} per tick.
 *
 * <p>Pure: no Minecraft types. The server glue turns the {@link Event}s into damage and packets.
 */
public final class ArrowSim {
	/** Where an arrow comes from: tells the glue which damage type and invulnerability rule to use. */
	public enum Kind {
		BASIC, FOCUS, VOLLEY, RAIN
	}

	public enum EventType {
		/** The arrow flew from {@code from} to {@code to} this tick (the end is cut at the block or the target it stopped in). */
		MOVED,
		/** The arrow hit entity {@code targetId} at {@code to}. */
		HIT,
		/** The arrow stopped in a block at {@code to}. */
		BLOCKED,
		/** The life of the arrow ran out at {@code to}. */
		EXPIRED
	}

	/** Frozen facts of the arrow at the moment of the event. */
	public record Event(EventType type, int arrowId, UUID owner, Kind kind, float damage, int focus, V3 from, V3 to, int targetId) {
	}

	/** An entity the segment crosses, {@code t} = fraction 0..1 of the segment where it first touches. */
	public record EntityHit(int id, double t) {
	}

	/** What the arrows see of the world. */
	public interface HitWorld {
		/** Fraction 0..1 of the first solid block on {@code from -> to}, or a negative number when the way is free. */
		double raycastBlock(V3 from, V3 to);

		/**
		 * Entities that may be hit (never the owner, no dead, immune or spectating ones: the glue decides) whose hit shape, grown by
		 * {@code radius}, the segment crosses. Order is free.
		 */
		List<EntityHit> entitiesAlong(UUID owner, V3 from, V3 to, double radius);

		/** The point a homing arrow aims at for this entity, or null when it is gone (the arrow then flies straight on). */
		V3 targetPosition(int entityId);
	}

	/** Everything needed to launch an arrow. */
	public record Spec(UUID owner, Kind kind, V3 pos, V3 vel, double gravity, float damage, int life, int pierce, double radius,
			int homingTarget, double turnRadPerTick, int focus) {
	}

	public static final class Arrow {
		private final int id;
		private final UUID owner;
		private final Kind kind;
		private V3 pos;
		private V3 vel;
		private final double gravity;
		private final float damage;
		private int life;
		private int pierceLeft;
		private final double radius;
		private int homingTarget;
		private final double turn;
		private final int focus;
		private final Set<Integer> hit = new HashSet<>();
		private boolean dead;

		private Arrow(int id, Spec s) {
			this.id = id;
			this.owner = s.owner();
			this.kind = s.kind();
			this.pos = s.pos();
			this.vel = s.vel();
			this.gravity = s.gravity();
			this.damage = s.damage();
			this.life = s.life();
			this.pierceLeft = s.pierce();
			this.radius = s.radius();
			this.homingTarget = s.homingTarget();
			this.turn = s.turnRadPerTick();
			this.focus = s.focus();
		}

		public int id() {
			return id;
		}

		public UUID owner() {
			return owner;
		}

		public Kind kind() {
			return kind;
		}

		public V3 pos() {
			return pos;
		}

		public V3 vel() {
			return vel;
		}

		public float damage() {
			return damage;
		}

		public int life() {
			return life;
		}

		public int pierceLeft() {
			return pierceLeft;
		}

		public int homingTarget() {
			return homingTarget;
		}

		/** Stacks of Focus the arrow was shot with. */
		public int focus() {
			return focus;
		}

		public boolean hasHit(int entityId) {
			return hit.contains(entityId);
		}

		public int hits() {
			return hit.size();
		}
	}

	private final List<Arrow> arrows = new ArrayList<>();
	private int nextId = 1;
	private final int cap;

	public ArrowSim() {
		this(UryuBalance.MAX_ARROWS);
	}

	public ArrowSim(int cap) {
		this.cap = cap;
	}

	/** Launches an arrow; returns it, or null when the cap of arrows is reached. */
	public Arrow spawn(Spec s) {
		if (arrows.size() >= cap) {
			return null;
		}
		Arrow a = new Arrow(nextId++, s);
		arrows.add(a);
		return a;
	}

	public List<Arrow> arrows() {
		return List.copyOf(arrows);
	}

	public int size() {
		return arrows.size();
	}

	public boolean isEmpty() {
		return arrows.isEmpty();
	}

	/** Removes every arrow of an owner (death, logout, end of the bankai); returns how many. */
	public int removeOwned(UUID owner) {
		int before = arrows.size();
		arrows.removeIf(a -> a.owner.equals(owner));
		return before - arrows.size();
	}

	/** Removes the arrows of an owner of one kind. */
	public int removeOwned(UUID owner, Kind kind) {
		int before = arrows.size();
		arrows.removeIf(a -> a.owner.equals(owner) && a.kind == kind);
		return before - arrows.size();
	}

	public void clear() {
		arrows.clear();
	}

	// ---------------------------------------------------------------- stepping

	/** One tick for every arrow. Events are returned in the order they happened, per arrow: HIT..., BLOCKED / EXPIRED, MOVED. */
	public List<Event> step(HitWorld world) {
		List<Event> out = new ArrayList<>();
		for (Arrow a : arrows) {
			stepOne(a, world, out);
		}
		arrows.removeIf(a -> a.dead);
		return out;
	}

	private static void stepOne(Arrow a, HitWorld w, List<Event> out) {
		V3 from = a.pos;
		if (a.homingTarget >= 0) {
			V3 want = w.targetPosition(a.homingTarget);
			if (want == null) {
				a.homingTarget = -1;
			} else {
				double speed = a.vel.length();
				V3 dir = a.vel.normalizedOr(new V3(0, 0, 1));
				V3 aim = want.sub(a.pos).normalizedOr(dir);
				a.vel = turnToward(dir, aim, a.turn).scale(speed);
			}
		}
		if (a.gravity != 0.0) {
			a.vel = new V3(a.vel.x(), a.vel.y() - a.gravity, a.vel.z());
		}
		V3 to = from.add(a.vel);
		double endT = 1.0;
		boolean blocked = false;
		double tb = w.raycastBlock(from, to);
		if (tb >= 0.0) {
			endT = Math.min(1.0, tb);
			blocked = true;
		}
		List<EntityHit> hits = new ArrayList<>(w.entitiesAlong(a.owner, from, to, a.radius));
		hits.sort((x, y) -> Double.compare(x.t(), y.t()));
		boolean stoppedByEntity = false;
		for (EntityHit h : hits) {
			if (h.t() > endT) {
				break;
			}
			if (a.hit.contains(h.id())) {
				continue; // one hit per arrow and target
			}
			a.hit.add(h.id());
			out.add(new Event(EventType.HIT, a.id, a.owner, a.kind, a.damage, a.focus, from, from.lerp(to, h.t()), h.id()));
			if (a.pierceLeft > 0) {
				a.pierceLeft--;
			} else {
				endT = h.t();
				stoppedByEntity = true;
				blocked = false;
				break;
			}
		}
		V3 end = from.lerp(to, endT);
		a.pos = end;
		if (stoppedByEntity) {
			a.dead = true;
		} else if (blocked) {
			a.dead = true;
			out.add(new Event(EventType.BLOCKED, a.id, a.owner, a.kind, a.damage, a.focus, from, end, -1));
		} else {
			a.life--;
			if (a.life <= 0) {
				a.dead = true;
				out.add(new Event(EventType.EXPIRED, a.id, a.owner, a.kind, a.damage, a.focus, from, end, -1));
			}
		}
		out.add(new Event(EventType.MOVED, a.id, a.owner, a.kind, a.damage, a.focus, from, end, -1));
	}

	/**
	 * The unit vector {@code dir} turned towards the unit vector {@code want} by at most {@code maxRad} radians (a great circle
	 * step). Reaches {@code want} when it is within the limit.
	 */
	public static V3 turnToward(V3 dir, V3 want, double maxRad) {
		double d = Math.max(-1.0, Math.min(1.0, dir.dot(want)));
		double ang = Math.acos(d);
		if (ang <= maxRad || ang < 1.0E-9) {
			return want;
		}
		if (ang > Math.PI - 1.0E-6) {
			// straight behind: any perpendicular will do
			V3 axis = dir.cross(Math.abs(dir.y()) < 0.9 ? new V3(0, 1, 0) : new V3(1, 0, 0)).normalizedOr(new V3(1, 0, 0));
			return dir.scale(Math.cos(maxRad)).add(axis.scale(Math.sin(maxRad))).normalizedOr(dir);
		}
		double s = Math.sin(ang);
		return dir.scale(Math.sin(ang - maxRad) / s).add(want.scale(Math.sin(maxRad) / s)).normalizedOr(dir);
	}

	/** Angle in radians between two vectors. */
	public static double angleBetween(V3 a, V3 b) {
		V3 ua = a.normalizedOr(new V3(0, 0, 1));
		V3 ub = b.normalizedOr(new V3(0, 0, 1));
		return Math.acos(Math.max(-1.0, Math.min(1.0, ua.dot(ub))));
	}
}
