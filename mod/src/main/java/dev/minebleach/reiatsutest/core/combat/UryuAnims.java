package dev.minebleach.reiatsutest.core.combat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Uryu pose animations (design/URYU_VFX_STORYBOARD.md 3, authored by {@code tools/gen_uryu_poses.py}) and how the game plays them: which ability
 * gesture starts which animation, from which tick, and whether the arms follow the camera pitch and the bow follows the aim. Pure data.
 *
 * <p>Gesture ids are the {@code CastGestures} ids ({@code AbilityId.commandId}, the two release ids {@link #RELEASE_SHIKAI} / {@link #RELEASE_BANKAI}). The
 * first person rows of the same ids are in {@link UryuGestures}.
 */
public final class UryuAnims {
	public static final String IDLE = "idle";
	public static final String DRAW = "draw";
	public static final String RELEASE = "release";

	/** {@code CastGestures} ids of the two release events of Uryu (effect ids 70 and 71). */
	public static final String RELEASE_SHIKAI = "uryu.shikai.release";
	public static final String RELEASE_BANKAI = "uryu.bankai.release";

	/**
	 * One animation.
	 *
	 * @param anim name of the file {@code player_animations/uryu_<anim>.json}
	 * @param gesture the {@code CastGestures} id that starts it (null for the draw state machine: idle, draw, release)
	 * @param look 1: the arms follow the camera pitch (the shot goes where the camera looks), 0: the pose is fixed
	 * @param axis 1: the arrow axis of the bow follows the bow arm (the aimed poses), 0: the bow stays level and upright like at rest
	 * @param startTick animation tick at which a gesture starts (the effect event of Licht Regen and Sternenstaub comes at the RELEASE, a few ticks
	 *        into the animation: the raise before it is shortened to {@code eventTick - startTick} ticks; with a cast start event at the key press this is 0)
	 * @param eventTick tick of the animation at which the effect event of the ability happens (informational and for the tests)
	 */
	public record Meta(String anim, String gesture, double look, double axis, int startTick, int eventTick) {
	}

	private static final Map<String, Meta> BY_ANIM = new LinkedHashMap<>();
	private static final Map<String, Meta> BY_GESTURE = new LinkedHashMap<>();

	private static void add(Meta m) {
		BY_ANIM.put(m.anim, m);
		if (m.gesture != null) {
			BY_GESTURE.put(m.gesture, m);
		}
	}

	static {
		add(new Meta(IDLE, null, 0.0, 0.0, 0, 0));
		add(new Meta(DRAW, null, 1.0, 1.0, 0, 0));
		add(new Meta(RELEASE, null, 1.0, 1.0, 0, 0));
		add(new Meta("release_z", "uryu.shikai.heilig_pfeil", 1.0, 1.0, 0, 3));
		add(new Meta("sprenger", "uryu.shikai.sprenger", 0.0, 0.0, 0, 0));
		add(new Meta("seele", "uryu.shikai.seele_schneider", 0.0, 0.0, 0, 3));
		add(new Meta("blut", RELEASE_SHIKAI, 0.0, 0.0, 0, 12));
		add(new Meta("vollstandig", RELEASE_BANKAI, 0.0, 0.0, 0, 13));
		add(new Meta("wings", "uryu.bankai.wing_barrage", 0.0, 0.0, 0, 8));
		add(new Meta("licht_regen", "uryu.bankai.licht_regen", 0.0, 1.0, 8, 12));
		add(new Meta("sternenstaub", "uryu.bankai.sternenstaub", 1.0, 1.0, 10, 16));
	}

	private UryuAnims() {
	}

	/** All animations in file order. */
	public static List<String> names() {
		return List.copyOf(BY_ANIM.keySet());
	}

	public static Meta meta(String anim) {
		return BY_ANIM.get(anim);
	}

	/** The animation a gesture id plays, or null (the gesture is not an Uryu one). */
	public static Meta forGesture(String gestureId) {
		return gestureId == null ? null : BY_GESTURE.get(gestureId);
	}

	/** Resource path of an animation. */
	public static String resource(String anim) {
		return "/assets/reiatsu_test/player_animations/uryu_" + anim + ".json";
	}

	public static Map<String, Meta> all() {
		return Collections.unmodifiableMap(BY_ANIM);
	}
}
