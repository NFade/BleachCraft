package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.client.fx.FxTune;
import dev.minebleach.reiatsutest.core.uryu.BowBodyAlign;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;

/**
 * R13 (user): third person, an archer shoots where he LOOKS, not where his legs point. While a player draws the bow (and a moment after the release) the body yaw is set
 * to the head yaw at the end of every client tick, for every player in sight (remote players too: the body yaw is computed on each client from the walk, so no packet
 * is needed). Key {@code uryu.tp.alignBody} 0 switches it off.
 */
public final class UryuBowBody {
	private static final Map<Integer, Long> LAST_DRAW = new HashMap<>();

	private UryuBowBody() {
	}

	public static void init() {
		ClientTickEvents.END_WORLD_TICK.register(UryuBowBody::tick);
	}

	private static void tick(ClientWorld world) {
		if (FxTune.d("uryu.tp.alignBody", 1.0) < 0.5) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		long now = world.getTime();
		for (PlayerEntity p : world.getPlayers()) {
			boolean drawing = UryuBowPose.drawing(p, p.getMainHandStack());
			if (drawing) {
				LAST_DRAW.put(p.getId(), now);
			}
			Long last = LAST_DRAW.get(p.getId());
			if (BowBodyAlign.active(drawing, now, last == null ? Long.MIN_VALUE : last)) {
				boolean self = p == mc.player;
				p.bodyYaw = self ? p.getYaw() : p.getHeadYaw();
				p.prevBodyYaw = self ? p.prevYaw : p.prevHeadYaw;
			}
		}
		if (LAST_DRAW.size() > 64) {
			LAST_DRAW.values().removeIf(t -> now - t > 200L || t > now);
		}
	}
}
