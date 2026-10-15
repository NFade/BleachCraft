package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.client.model.DrawEvents;
import dev.minebleach.reiatsutest.core.fx.UryuBowForm;
import dev.minebleach.reiatsutest.core.fx.UryuBowGlow;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.LivingEntity;

/**
 * The bow of Uryu (URYU_VFX_STORYBOARD 2.1 forming from Reishi, 2.2 the light of the drawn bow). First step: the clock of the forming that
 * the item model reads ({@link #formAge}) and the emissive multiplier ({@link #glowMultiplier}); the world side is added in the next step.
 * Public because {@code ObjItemBakedModel} (client.model) reads it. Called by {@link UryuFx}.
 */
public final class UryuBowFx {
	/** Entity id to the FX clock second at which its forming started. */
	private static final Map<Integer, Double> FORMING = new HashMap<>();

	private UryuBowFx() {
	}

	static void init() {
		// per frame sources are added by the world side
	}

	/** The draw / sheathe events of Uryu's item. */
	static void onDraw(DrawEvents.Phase phase, LivingEntity entity, boolean local) {
		if (phase == DrawEvents.Phase.DRAW_START) {
			FORMING.put(entity.getId(), FxClock.now);
		}
	}

	/** Seconds since the bow of this entity started to form, or -1 when it is not forming (the item then draws the normal bow). */
	public static double formAge(int entityId) {
		Double s = FORMING.get(entityId);
		if (s == null) {
			return -1.0;
		}
		double t = FxClock.now - s;
		if (t >= UryuBowForm.DURATION) {
			FORMING.remove(entityId);
			return -1.0;
		}
		return t < 0 ? 0.0 : t;
	}

	/** The multiplier of the emissive pass of the bow in this entity's hand (see {@link UryuBowGlow}); 1 for the pendant. */
	public static double glowMultiplier(LivingEntity holder, boolean sealed) {
		return 1.0;
	}
}
