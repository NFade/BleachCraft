package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import dev.minebleach.reiatsutest.core.state.Trigger;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.entity.FxAnchorEntity;
import dev.minebleach.reiatsutest.entity.FxAnchorKind;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;

/**
 * THE client side end of a bankai (mech2 task 2). Whatever ended it on the server (45 s timer, seal, death, logout, dimension change,
 * dev reset) the client gets here the same way, from two sources that cannot both be missed:
 * <ul>
 * <li>the server message {@link EffectIds#BANKAI_CLEANUP} ({@link #onEvent}), sent by the one server cleanup;</li>
 * <li>the {@link #watch watcher}: an owner seen in BANKAI whose synced state is no longer BANKAI, or whose entity left the world, is ended
 * here too (a lost packet, a dimension change, an unloaded chunk, a respawn).</li>
 * </ul>
 * {@link #end} is idempotent and drops every bankai effect of that owner: the rows with their hilt, the storm, the Hakuteiken, the Rukia
 * passive (frost ground, crystals, frost edge, ribbons), the shikai swarm, every held vignette and grade of the owner, the running
 * bankai timelines, the freeze_desat pass. Four seconds later the {@link #audit} lists what is still alive (it must be nothing) and logs it
 * as {@code [bankai-audit]}.
 */
public final class BankaiEnd {
	/** Bankai ends handled since the start (statistics for the harness). */
	public static int ends;
	/** Audits that found something alive (the harness expects 0). */
	public static int dirtyAudits;
	/** The last audit line. */
	public static volatile String lastAudit = "";

	/** Owners seen in BANKAI by the watcher or by a bankai release event. */
	private static final Set<Integer> LIVE = new HashSet<>();
	/** FX clock of the last end per owner (the server message and the watcher both arrive for one end: the second one is a no-op). */
	private static final Map<Integer, Double> LAST_END = new HashMap<>();
	/** Audit rounds waiting (owner -> due FX time); the harness reads them with {@link #pendingAudits()}. */
	private static int pending;
	/** FX seconds in which a second end of the same owner is the same end (the server message and the watcher both fire; a new bankai needs the 60 s lock). */
	private static final double DEDUPE = 3.0;

	private BankaiEnd() {
	}

	/** Effect ids of the running timelines that die with the bankai (release 2 and 4, ice wall 24, scatter 32, hakuteiken 33, frost field 42). */
	static boolean bankaiEffect(int id) {
		return id == 2 || id == 4 || id == 24 || id == 32 || id == 33 || id == EffectIds.FROST_FIELD;
	}

	public static void reset() {
		LIVE.clear();
		LAST_END.clear();
	}

	/** The server message (id 44): params = {trigger ordinal, character code}. */
	public static void onEvent(EffectEventS2C e) {
		float[] p = e.params();
		Trigger t = p.length > 0 && (int) p[0] >= 0 && (int) p[0] < Trigger.values().length ? Trigger.values()[(int) p[0]] : Trigger.REQUEST;
		end(e.casterId(), instantFor(t), "server " + t, t == Trigger.BANKAI_CAP);
	}

	/** Death, logout and a dimension change drop everything at once; the timer, the seal and the dev reset let the rows sink as designed. */
	static boolean instantFor(Trigger t) {
		return t == Trigger.DEATH || t == Trigger.LOGOUT || t == Trigger.DIMENSION_CHANGE;
	}

	/** Ends every bankai effect of an owner. Idempotent. */
	public static void end(int ownerId, boolean instant, String why) {
		end(ownerId, instant, why, false);
	}

	/**
	 * As above; {@code keepSwarm}: the bankai only falls back to SHIKAI (the timer), so the petal swarm around the owner goes on (r7: it used to be
	 * dropped with the big blades). The swarm is a different effect from the rows.
	 */
	public static void end(int ownerId, boolean instant, String why, boolean keepSwarm) {
		Double last = LAST_END.get(ownerId);
		if (last != null && FxClock.now - last < DEDUPE) {
			return;
		}
		LAST_END.put(ownerId, FxClock.now);
		LIVE.remove(ownerId);
		ends++;
		boolean rows = RowsFx.endOwner(ownerId, instant);
		boolean storm = StormFx.endOwner(ownerId);
		boolean hak = HakuteikenFx.endOwner(ownerId);
		boolean rukia = RukiaBankaiClient.endOwner(ownerId, instant);
		if (!keepSwarm) {
			SwarmFx.end(ownerId);
		}
		int timelines = FxTimelines.cancelCaster(ownerId, BankaiEnd::bankaiEffect);
		int layers = ScreenFx.releaseOwner(ownerId, instant ? 0.4 : 1.0);
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean local = mc.player != null && mc.player.getId() == ownerId;
		if (local) {
			FreezeDesat.set(0.0, 0.3);
		}
		ReiatsuTest.LOGGER.info("[bankai-end] owner {} ({}{}): rows={} storm={} hakuteiken={} rukia={} timelines={} layers={}", ownerId, why, instant ? ", instant" : "",
				rows, storm, hak, rukia, timelines, layers);
		pending++;
		FxDelayed.after(instant ? 2.5 : 5.0, () -> {
			pending--;
			audit(ownerId, "after " + why);
		});
	}

	/** END_CLIENT_TICK: ends the owners whose state left BANKAI or whose entity is gone. */
	public static void watch(MinecraftClient mc) {
		if (mc.world == null) {
			if (!LIVE.isEmpty()) {
				LIVE.clear();
			}
			return;
		}
		Set<Integer> seen = new HashSet<>();
		for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
			ZanpakutoData z = pl.getAttached(ModAttachments.ZANPAKUTO);
			boolean bankai = z != null && z.zanpakutoState() == ZanpakutoState.BANKAI;
			int id = pl.getId();
			seen.add(id);
			if (bankai) {
				// (the end message arrives a moment before the attachment turns: do not take that last BANKAI sync for a new bankai)
				Double last = LAST_END.get(id);
				if (!LIVE.contains(id) && (last == null || FxClock.now - last >= DEDUPE)) {
					LIVE.add(id);
				}
			} else if (LIVE.contains(id)) {
				// (back in SHIKAI: the petal swarm around the owner goes on)
				end(id, false, "state left BANKAI", z != null && z.zanpakutoState() == ZanpakutoState.SHIKAI);
			}
		}
		if (!LIVE.isEmpty()) {
			for (Integer id : new HashSet<>(LIVE)) {
				if (!seen.contains(id)) {
					end(id, true, "entity gone");
				}
			}
		}
	}

	/** True for a few seconds after an end: the anchor entity may still be in the world for a tick or two and must not bring the rows back. */
	public static boolean recentlyEnded(int ownerId) {
		Double last = LAST_END.get(ownerId);
		return last != null && FxClock.now - last < DEDUPE;
	}

	public static int pendingAudits() {
		return pending;
	}

	// ------------------------------------------------------------------------------------------ audit

	/** What is still alive of the bankai of an owner on this client. All zero after an end. */
	public record Leftovers(int rows, int storm, int hakuteiken, int rukiaPassive, int crystals, int layers, int timelines, int anchors, boolean desat) {
		public boolean clean() {
			return rows == 0 && storm == 0 && hakuteiken == 0 && !rukiaPassiveAlive() && crystals == 0 && layers == 0 && timelines == 0 && anchors == 0 && !desat;
		}

		private boolean rukiaPassiveAlive() {
			return rukiaPassive != 0;
		}

		@Override
		public String toString() {
			return "rows=" + rows + " storm=" + storm + " hakuteiken=" + hakuteiken + " rukiaPassive=" + rukiaPassive + " crystals=" + crystals + " layers=" + layers
					+ " timelines=" + timelines + " anchors=" + anchors + " desat=" + desat;
		}
	}

	public static Leftovers leftovers(int ownerId) {
		MinecraftClient mc = MinecraftClient.getInstance();
		int crystals = 0;
		for (FxMeshPass.Crystal c : FxMeshPass.crystals()) {
			crystals += c.field == ownerId ? 1 : 0;
		}
		int anchors = 0;
		if (mc.world != null) {
			for (Entity en : mc.world.getEntities()) {
				if (en instanceof FxAnchorEntity a && a.ownerId() == ownerId && (a.kind() == FxAnchorKind.ROWS || a.kind() == FxAnchorKind.FIELD)) {
					anchors++;
				}
			}
		}
		boolean local = mc.player != null && mc.player.getId() == ownerId;
		return new Leftovers(RowsFx.of(ownerId) != null ? 1 : 0, StormFx.of(ownerId) != null ? 1 : 0, HakuteikenFx.of(ownerId) != null ? 1 : 0,
				RukiaBankaiClient.active(ownerId) ? 1 : 0, crystals, ScreenFx.heldOwner(ownerId), FxTimelines.countCaster(ownerId, BankaiEnd::bankaiEffect), anchors,
				local && FreezeDesat.active());
	}

	/** Logs the leftovers of an owner. The owner is expected NOT to be in a bankai at this point; if it is again (a new one began) nothing is reported. */
	static Leftovers audit(int ownerId, String when) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Entity en = mc.world == null ? null : mc.world.getEntityById(ownerId);
		if (en instanceof AbstractClientPlayerEntity pl) {
			ZanpakutoData z = pl.getAttached(ModAttachments.ZANPAKUTO);
			if (z != null && z.zanpakutoState() == ZanpakutoState.BANKAI && z.characterId() != CharacterId.NONE) {
				return leftovers(ownerId); // a new bankai started meanwhile: its effects are expected
			}
		}
		Leftovers l = leftovers(ownerId);
		lastAudit = "owner " + ownerId + " " + when + ": " + l;
		if (l.clean()) {
			ReiatsuTest.LOGGER.info("[bankai-audit] owner {} {}: clean ({})", ownerId, when, l);
		} else {
			dirtyAudits++;
			ReiatsuTest.LOGGER.warn("[bankai-audit] owner {} {}: LEFTOVERS {}", ownerId, when, l);
		}
		return l;
	}
}
