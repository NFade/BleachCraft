package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.client.ClientOptions;
import dev.minebleach.reiatsutest.client.model.DrawEvents.Phase;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.ModItems;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/**
 * Client side timing of the draw and sheathe animation (step B3). Watches the synced {@code zanpakuto} attachment of every
 * player in view (not the item component, so swapping hotbar slots never looks like a draw): SEALED to released starts a
 * draw, released to SEALED a sheathe. The item model asks {@link #progress} while it renders (the renderer entity is set by
 * the {@code HeldItemRenderer#renderItem} mixin) and shows the sealed meshes at that progress (0 = sheathed, 1 = drawn)
 * instead of the released model for the duration; the swap to the released model happens in the tick that fires
 * {@link DrawEvents.Phase#DRAW_RELEASE}.
 */
public final class DrawTracker {
	/** Dev harness override: when not NaN, every main hand sealed sword (player, armor stand) is drawn at exactly this progress. */
	public static volatile float debugProgress = Float.NaN;

	private static final class Anim {
		final int direction; // +1 draw, -1 sheathe
		final float startP;
		final long startNanos;
		final CharacterId character;
		final ZanpakutoState target;

		Anim(int direction, float startP, long startNanos, CharacterId character, ZanpakutoState target) {
			this.direction = direction;
			this.startP = startP;
			this.startNanos = startNanos;
			this.character = character;
			this.target = target;
		}

		float progress(long now, float seconds) {
			// linear time t, eased progress p = 1 - (1 - t)^2 (ease-out: the blade leaves fast and settles slowly)
			float t0 = 1f - (float) Math.sqrt(Math.max(0f, 1f - startP));
			float t = t0 + direction * (now - startNanos) / 1.0e9f / seconds;
			t = Math.max(0f, Math.min(1f, t));
			return 1f - (1f - t) * (1f - t);
		}
	}

	private static final Map<Integer, ZanpakutoState> LAST = new HashMap<>();
	private static final Map<Integer, Anim> ACTIVE = new HashMap<>();
	private static ClientWorld lastWorld;
	/** Entity whose held item is being rendered right now (render thread, set by the mixin). */
	private static LivingEntity renderEntity;

	private DrawTracker() {
	}

	public static void beginRender(LivingEntity entity) {
		renderEntity = entity;
	}

	/** The entity whose held item is being rendered right now, or null. */
	public static LivingEntity renderEntity() {
		return renderEntity;
	}

	public static void endRender() {
		renderEntity = null;
	}

	/**
	 * Draw progress of the stack being rendered: 0 = sheathed .. 1 = drawn while an animation (or the debug override) runs
	 * for the main hand sword of the rendering entity, -1 = no animation, render the stack's own state.
	 */
	public static float progress(ItemStack stack) {
		LivingEntity e = renderEntity;
		if (e == null || e.getMainHandStack().getItem() != stack.getItem()) {
			return -1f;
		}
		if (!Float.isNaN(debugProgress)) {
			return debugProgress;
		}
		Anim a = ACTIVE.get(e.getId());
		return a == null ? -1f : a.progress(System.nanoTime(), seconds());
	}

	/** Animation progress of an entity's main hand zanpakuto (player, or any entity under the dev override), -1 = none running. */
	public static float animProgress(LivingEntity e) {
		if (!(e.getMainHandStack().getItem() instanceof dev.minebleach.reiatsutest.registry.ZanpakutoItem)) {
			return -1f;
		}
		if (!Float.isNaN(debugProgress)) {
			return debugProgress;
		}
		Anim a = ACTIVE.get(e.getId());
		return a == null ? -1f : a.progress(System.nanoTime(), seconds());
	}

	/**
	 * Where the sword of {@code stack} (the main hand stack of {@code e}) is on its way out of the scabbard: 0 sheathed, 1
	 * drawn. Follows the running animation, else the render state of the stack (SEALED = 0, everything else = 1).
	 */
	public static float effectiveProgress(LivingEntity e, ItemStack stack) {
		float a = animProgress(e);
		if (a >= 0f) {
			return a;
		}
		return stack.getOrDefault(dev.minebleach.reiatsutest.registry.ModComponents.RELEASE_STATE,
				dev.minebleach.reiatsutest.registry.ReleaseState.SEALED) == dev.minebleach.reiatsutest.registry.ReleaseState.SEALED ? 0f : 1f;
	}

	private static float seconds() {
		return Math.max(0.05f, ClientOptions.drawSeconds);
	}

	/** END_CLIENT_TICK. */
	public static void tick(MinecraftClient mc) {
		ClientWorld w = mc.world;
		if (w != lastWorld) {
			lastWorld = w;
			LAST.clear();
			ACTIVE.clear();
		}
		if (w == null) {
			return;
		}
		long now = System.nanoTime();
		boolean animate = ClientOptions.drawSeconds > 0f;
		Map<Integer, ZanpakutoState> seen = new HashMap<>();
		for (AbstractClientPlayerEntity p : w.getPlayers()) {
			ZanpakutoData z = p.getAttached(ModAttachments.ZANPAKUTO);
			ZanpakutoState st = z == null ? ZanpakutoState.SEALED : z.zanpakutoState();
			int id = p.getId();
			seen.put(id, st);
			ZanpakutoState last = LAST.get(id);
			boolean local = p == mc.player;
			CharacterId ch = z != null && z.characterId() != CharacterId.NONE ? z.characterId() : ModItems.characterOf(p.getMainHandStack());
			if (last != null && last != st && p.isAlive()) {
				Anim cur = ACTIVE.get(id);
				float curP = cur == null ? (last == ZanpakutoState.SEALED ? 0f : 1f) : cur.progress(now, seconds());
				if (last == ZanpakutoState.SEALED) {
					if (animate) {
						ACTIVE.put(id, new Anim(1, curP, now, ch, st));
						DrawEvents.EVENT.invoker().onDraw(Phase.DRAW_START, p, ch, st, local);
					} else {
						DrawEvents.EVENT.invoker().onDraw(Phase.DRAW_RELEASE, p, ch, st, local);
					}
				} else if (st == ZanpakutoState.SEALED) {
					if (animate) {
						ACTIVE.put(id, new Anim(-1, curP, now, ch, st));
						DrawEvents.EVENT.invoker().onDraw(Phase.SHEATHE_START, p, ch, st, local);
					} else {
						DrawEvents.EVENT.invoker().onDraw(Phase.SHEATHE_END, p, ch, st, local);
					}
				} else if (cur != null) {
					ACTIVE.put(id, new Anim(cur.direction, cur.startP, cur.startNanos, ch, st)); // shikai -> bankai while drawing
				}
			}
			Anim a = ACTIVE.get(id);
			if (a != null && (a.direction > 0 ? a.progress(now, seconds()) >= 1f : a.progress(now, seconds()) <= 0f)) {
				ACTIVE.remove(id);
				DrawEvents.EVENT.invoker().onDraw(a.direction > 0 ? Phase.DRAW_RELEASE : Phase.SHEATHE_END, p, a.character, a.target, local);
			}
		}
		LAST.clear();
		LAST.putAll(seen);
		for (Iterator<Integer> it = ACTIVE.keySet().iterator(); it.hasNext();) {
			if (!seen.containsKey(it.next())) {
				it.remove(); // the player left
			}
		}
	}
}
