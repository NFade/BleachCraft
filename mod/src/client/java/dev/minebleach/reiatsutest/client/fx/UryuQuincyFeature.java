package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.UryuBlutTiming;
import dev.minebleach.reiatsutest.core.fx.UryuVollstTiming;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.UryuCrossItem;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Everything of Uryu that is drawn ON the player model, for every Uryu player in view (URYU_VFX_STORYBOARD 2.5, 2.11, 4.4; U-FX 6 made this
 * class, U-FX 7 adds the wings and bands of the bankai state through {@link #addBankaiPass}).
 *
 * <p><b>The vein overlay (Blut Arterie, SHIKAI).</b> {@code textures/fx/uryu_vein_skin.png} is a 64 x 64 image in the PLAYER SKIN UV layout (the
 * columns that exist on classic and slim arms, chest and neck): the posed player model is drawn again, part by part (chest, right arm, left
 * arm; the head, legs and the second skin layer are not drawn), with that texture, slightly inflated so that the sleeves and the jacket do not
 * hide the lines ({@link UryuBlutTiming#armScaleX} and friends; the part scale and pivot are restored right after). Two passes: a solid
 * translucent emissive pass (reads on a bright sky, rule 5 of the storyboard) and an additive pass on top (the glow at night). The draw-on from
 * the fingertips to the neck cannot be a shader mask, so the texture exists in {@link UryuBlutTiming#STAGES} versions
 * ({@code uryu_vein/stage_NN.png}, written by tools/gen_uryu_textures.py) and the stage follows the sweep of the release. The brightness
 * ({@link UryuBlutTiming#releaseLevel}: the 3 beats of the release, the state level 0.5, 0.8 at Focus 3, the shot flare, Reduce Motion constant
 * 0.7) goes into the vertex colour.
 *
 * <p><b>State.</b> {@link #tick} (END_CLIENT_TICK, registered in {@link #register}, so it also runs for a player that was never seen casting)
 * reads the synced attachment of every player in the world (SHIKAI or BANKAI of Uryu, within 48 blocks), watches the bow (a shot is the falling
 * edge of the draw) and spawns the forearm motes of the state aura. The release event {@code 70} calls {@link #onRelease} so that the sweep
 * starts 0.2 s after the event; an attachment that arrives without an event (a player who came into view later) is shown settled; one that
 * arrives a little before the event waits {@value #PENDING_WAIT} s for it.
 *
 * <p><b>First person.</b> {@link #firstPersonArm} draws the overlay on the vanilla arm that {@code FirstPersonHand} just drew (same matrix stack).
 *
 * <p><b>For U-FX 7 (wings, bands, emblem, flakes):</b> {@link #state} gives the snapshot of a player ({@link State#bankai()}, {@link State#bankaiAge()}),
 * {@link #addBankaiPass} registers a {@link Pass} that is called from {@link #render} for every visible player in the BANKAI state with a
 * {@link Ctx} (matrix stack in the model frame, vertex consumers, the posed model, the slim flag). The vein overlay fades out when SHIKAI ends
 * (0.4 s), so a bankai release does not need to touch this class again.
 *
 * <p>Everything that cannot be seen without the game is listed as UNVERIFIED in design/results/uryu_ufx6.md.
 */
public final class UryuQuincyFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	private static final int LIGHT = 0xF000F0;
	/** Seconds a SHIKAI attachment waits for its release event before the veins start by themselves. */
	static final double PENDING_WAIT = 0.15;
	private static final Identifier[] STAGE = new Identifier[UryuBlutTiming.STAGES];

	static {
		for (int k = 0; k < STAGE.length; k++) {
			STAGE[k] = ReiatsuTest.id(String.format("textures/fx/uryu_vein/stage_%02d.png", k));
		}
	}

	/** Statistics for the harness. */
	public static int drawn;
	public static int veinDraws;
	public static int firstPersonDraws;
	public static double lastLevel;
	public static int lastStage = -1;
	private static boolean warned;

	// ------------------------------------------------------------------------------------------ the state of the players

	private static final class Q {
		boolean shikai;
		boolean bankai;
		boolean seen;
		/** FX time of the release event (t = 0 of the table), NaN when none came. */
		double t0 = Double.NaN;
		double eventAt = -1000;
		/** FX time the SHIKAI attachment was first seen without an event (NaN = none pending). */
		double pendingAt = Double.NaN;
		double endedAt = -1000;
		double bankaiSince = -1000;
		/** FX time the BANKAI state ended (the bands fade out for {@link UryuVollstTiming#BANDS_FADE_OUT} s, so the passes are still called). */
		double bankaiEndedAt = -1000;
		double fireAt = -1000;
		boolean wasDrawing;
		int lastUse;
		double lastLevel;
		int focus;
		/** The state level (0.5, 0.8 at Focus 3), smoothed so that a change of Focus does not make the overlay jump. NaN = not set yet. */
		double state = Double.NaN;
		double nextMote;
	}

	/** What another step may read of a player: the state of the Uryu attachment and the vein level. */
	public record State(boolean shikai, boolean bankai, double bankaiAge, double veinLevel, int veinStage, double endedAge) {
		public static final State NONE = new State(false, false, 0.0, 0.0, -1, -1.0);

		public State(boolean shikai, boolean bankai, double bankaiAge, double veinLevel, int veinStage) {
			this(shikai, bankai, bankaiAge, veinLevel, veinStage, -1.0);
		}
	}

	/** The frame a {@link Pass} draws in. */
	public record Ctx(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity player, PlayerEntityModel<AbstractClientPlayerEntity> model,
			float tickDelta, double now, boolean slim, State state) {
	}

	/** A draw pass of the BANKAI state (U-FX 7: wings, bands, emblem). */
	public interface Pass {
		void draw(Ctx ctx);
	}

	private static final Map<Integer, Q> PLAYERS = new HashMap<>();
	private static final Set<Integer> FORCED = new HashSet<>();
	private static final Set<Integer> FORCED_BANKAI = new HashSet<>();
	/** Players that were in the world on the previous tick (a player that appears already in SHIKAI is shown settled, one that changes is a release). */
	private static final Set<Integer> KNOWN = new HashSet<>();
	private static final List<Pass> BANKAI_PASSES = new ArrayList<>();
	private static final Random RNG = new Random(0x51AC);
	private static boolean registered;

	public UryuQuincyFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	/** Once from {@link FxClient#init()}: the feature renderer on every player renderer, and the per tick state. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			if (renderer instanceof PlayerEntityRenderer pr) {
				helper.register(new UryuQuincyFeature(pr));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(UryuQuincyFeature::tick);
	}

	/** U-FX 7: adds a pass that is drawn for every visible Uryu player in the BANKAI state (after the veins). */
	public static void addBankaiPass(Pass pass) {
		if (!BANKAI_PASSES.contains(pass)) {
			BANKAI_PASSES.add(pass);
		}
	}

	/** Harness: treats the player as Uryu in SHIKAI whatever its attachment says (the second player of the slim / classic shots). */
	public static void forceShikai(int entityId, boolean on) {
		if (on) {
			FORCED.add(entityId);
		} else {
			FORCED.remove(entityId);
		}
	}

	/** Harness: treats the player as Uryu in BANKAI whatever its attachment says (the stand-ins of the three player frame test). */
	public static void forceBankai(int entityId, boolean on) {
		if (on) {
			FORCED_BANKAI.add(entityId);
		} else {
			FORCED_BANKAI.remove(entityId);
		}
	}

	public static void clear() {
		PLAYERS.clear();
		FORCED.clear();
		FORCED_BANKAI.clear();
		KNOWN.clear();
	}

	/** The release event (id 70) of a caster: t = 0 of the table is now; the veins start at 0.2 s. */
	public static void onRelease(int casterId) {
		Q q = PLAYERS.computeIfAbsent(casterId, k -> new Q());
		q.t0 = FxClock.now;
		q.eventAt = q.t0;
		q.pendingAt = Double.NaN;
		q.endedAt = -1000;
	}

	/** A shot of this player (the draw ended with a release): brightens the veins for 0.3 s. Also called by {@link #tick}. */
	public static void onFire(int casterId) {
		Q q = PLAYERS.get(casterId);
		if (q != null) {
			q.fireAt = FxClock.now;
		}
	}

	/** The snapshot of a player for the other steps and the harness. */
	public static State state(int entityId) {
		Q q = PLAYERS.get(entityId);
		if (q == null) {
			return State.NONE;
		}
		double[] v = vein(q, FxClock.now);
		return new State(q.shikai, q.bankai, q.bankai ? FxClock.now - q.bankaiSince : 0.0, v[0], (int) v[1], endedAge(q, FxClock.now));
	}

	/** Seconds since the BANKAI state ended while the passes still fade out (see {@link UryuVollstTiming#BANDS_FADE_OUT}), else -1. */
	private static double endedAge(Q q, double now) {
		double age = now - q.bankaiEndedAt;
		return !q.bankai && age >= 0 && age < UryuVollstTiming.BANDS_FADE_OUT ? age : -1.0;
	}

	private static boolean isUryu(AbstractClientPlayerEntity pl, ZanpakutoState want) {
		ZanpakutoData z = pl.getAttached(ModAttachments.ZANPAKUTO);
		return z != null && z.characterId() == CharacterId.URYU && z.zanpakutoState() == want;
	}

	// ------------------------------------------------------------------------------------------ per tick

	private static void tick(MinecraftClient mc) {
		if (mc.world == null || mc.player == null) {
			if (!PLAYERS.isEmpty()) {
				PLAYERS.clear();
			}
			return;
		}
		double now = FxClock.now;
		Vec3d cam = mc.gameRenderer.getCamera() != null ? mc.gameRenderer.getCamera().getPos() : mc.player.getPos();
		double max2 = UryuBlutTiming.VIEW_DISTANCE * UryuBlutTiming.VIEW_DISTANCE;
		Set<Integer> present = new HashSet<>();
		for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
			present.add(pl.getId());
		}
		KNOWN.retainAll(present);
		Iterator<Map.Entry<Integer, Q>> it = PLAYERS.entrySet().iterator();
		while (it.hasNext()) {
			if (!present.contains(it.next().getKey())) {
				it.remove();
			}
		}
		for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
			int id = pl.getId();
			boolean known = !KNOWN.add(id);
			boolean forced = FORCED.contains(id);
			boolean forcedBankai = FORCED_BANKAI.contains(id);
			boolean shikai = !forcedBankai && (forced || isUryu(pl, ZanpakutoState.SHIKAI));
			boolean bankai = forcedBankai || !forced && isUryu(pl, ZanpakutoState.BANKAI);
			Q q = PLAYERS.get(id);
			if (q == null) {
				if (!shikai && !bankai) {
					continue;
				}
				q = new Q();
				q.seen = known;
				PLAYERS.put(id, q);
			}
			if (shikai && !q.shikai) {
				q.shikai = true;
				q.endedAt = -1000;
				if (Double.isNaN(q.t0) || now - q.eventAt > 2.0) {
					// an observed change waits a moment for the release event; a player that was already in SHIKAI when he came into view is settled
					if (q.seen) {
						q.pendingAt = now;
						q.t0 = Double.NaN;
					} else {
						q.t0 = now - 100.0;
					}
				}
			} else if (!shikai && q.shikai) {
				q.shikai = false;
				q.endedAt = now;
				q.t0 = Double.NaN;
				q.pendingAt = Double.NaN;
			}
			if (bankai && !q.bankai) {
				q.bankai = true;
				q.bankaiSince = q.seen ? now : now - 100.0;
			} else if (!bankai && q.bankai) {
				q.bankai = false;
				q.bankaiEndedAt = now;
				UryuWingFx.bankaiEnded(id); // the wings break (idempotent: the end event 44 and the seal do the same)
			}
			q.seen = true;
			// a record made by the release event whose attachment has not arrived yet is kept for 2 s (the packets of one tick come in any order)
			if (!q.shikai && !q.bankai && now - q.endedAt > UryuBlutTiming.FADE_OUT + 0.1 && now - q.eventAt > 2.0
					&& now - q.bankaiEndedAt > UryuVollstTiming.BANDS_FADE_OUT + 0.1) {
				PLAYERS.remove(id);
				continue;
			}
			q.focus = pl == mc.player ? UryuFx.focus : UryuFx.focusOf(id);
			double target = UryuBlutTiming.stateLevel(q.focus);
			q.state = Double.isNaN(q.state) ? target : UryuBlutTiming.approach(q.state, target, 0.3);
			// a shot: the falling edge of the bow draw
			boolean drawing = q.shikai && pl.isUsingItem() && pl.getActiveHand() == Hand.MAIN_HAND && UryuCrossItem.isBow(pl.getMainHandStack());
			if (q.wasDrawing && !drawing && q.lastUse >= 4) {
				q.fireAt = now;
			}
			q.wasDrawing = drawing;
			q.lastUse = drawing ? pl.getItemUseTime() : 0;
			if (q.shikai && cam.squaredDistanceTo(pl.getPos()) <= max2 && !pl.isInvisible()) {
				motes(pl, q, now);
			}
		}
	}

	/** The state aura: 4 reishi motes per second drift up from the forearms (rise 0.6 b/s, life 16 t), only while the veins are lit. */
	private static void motes(AbstractClientPlayerEntity pl, Q q, double now) {
		double[] v = vein(q, now);
		if (v[0] < 0.2 || FxConfig.reduceMotion && v[0] < 0.4) {
			return;
		}
		// 4 per second at 20 ticks: probability 0.2 per tick, from a random forearm
		if (RNG.nextDouble() > UryuBlutTiming.MOTES_PER_SECOND / 20.0) {
			return;
		}
		boolean left = RNG.nextBoolean();
		double yaw = Math.toRadians(pl.bodyYaw);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double side = (left ? -1 : 1) * 0.36;
		double h = pl.isSneaking() ? 0.65 : 0.85;
		Vec3d p = pl.getPos();
		double along = (RNG.nextDouble() - 0.5) * 0.3;
		FxParticles.spec(FxParticles.Kind.REISHI_MOTE).at(p.x + rx * side + fx * along, p.y + h + (RNG.nextDouble() - 0.5) * 0.3, p.z + rz * side + fz * along)
				.vel(RNG.nextGaussian() * 0.05, UryuBlutTiming.MOTE_RISE, RNG.nextGaussian() * 0.05).color(UryuPalette.Q2).size(0.16, 0.05)
				.life(UryuBlutTiming.MOTE_LIFE_TICKS).seed(RNG.nextInt(1 << 16)).drag(0.97).alpha(0.9).spawn();
	}

	// ------------------------------------------------------------------------------------------ the vein level of a player

	/** {level, stage} at {@code now}: stage -1 or level 0 draws nothing. */
	private static double[] vein(Q q, double now) {
		boolean rm = FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
		if (q.shikai) {
			double t0 = q.t0;
			if (Double.isNaN(t0)) {
				if (!Double.isNaN(q.pendingAt) && now - q.pendingAt >= PENDING_WAIT) {
					q.t0 = t0 = q.pendingAt; // no event came: the veins start by themselves
				} else {
					return new double[] {0.0, -1};
				}
			}
			double t = now - t0;
			double level = UryuBlutTiming.releaseLevel(t, rm, Double.isNaN(q.state) ? UryuBlutTiming.stateLevel(q.focus) : q.state);
			if (!rm) {
				level = UryuBlutTiming.withFire(level, UryuBlutTiming.fireEnvelope(now - q.fireAt));
			}
			int stage = t >= UryuBlutTiming.T_BURST ? UryuBlutTiming.STAGES - 1 : UryuBlutTiming.stage(UryuBlutTiming.sweep(t));
			q.lastLevel = level;
			return new double[] {stage < 0 ? 0.0 : level, stage};
		}
		double since = now - q.endedAt;
		if (since >= UryuBlutTiming.FADE_OUT || q.lastLevel <= 0) {
			return new double[] {0.0, -1};
		}
		return new double[] {q.lastLevel * UryuBlutTiming.fadeOut(since), UryuBlutTiming.STAGES - 1};
	}

	// ------------------------------------------------------------------------------------------ the feature renderer

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity entity, float limbAngle, float limbDistance, float tickDelta,
			float animationProgress, float headYaw, float headPitch) {
		Q q = PLAYERS.get(entity.getId());
		if (q == null || entity.isInvisible() || entity.isSpectator()) {
			return;
		}
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			Vec3d cam = mc.gameRenderer.getCamera().getPos();
			if (cam.squaredDistanceTo(entity.getPos()) > UryuBlutTiming.VIEW_DISTANCE * UryuBlutTiming.VIEW_DISTANCE) {
				return;
			}
			double now = FxClock.now;
			PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
			boolean slim = entity.getSkinTextures().model() == SkinTextures.Model.SLIM;
			double[] v = vein(q, now);
			if (v[1] >= 0 && !UryuBlutTiming.invisible(v[0])) {
				drawn++;
				lastLevel = v[0];
				lastStage = (int) v[1];
				drawParts(matrices, vcp, model, slim, armored(entity), v[0], (int) v[1], UryuBlutTiming.parts(FxConfig.uryuVeinOverlay));
			}
			double ended = endedAge(q, now);
			if ((q.bankai || ended >= 0) && !BANKAI_PASSES.isEmpty()) {
				Ctx c = new Ctx(matrices, vcp, light, entity, model, tickDelta, now, slim,
						new State(q.shikai, q.bankai, q.bankai ? now - q.bankaiSince : 0.0, v[0], (int) v[1], ended));
				for (Pass p : BANKAI_PASSES) {
					p.draw(c);
				}
			}
		} catch (RuntimeException ex) {
			if (!warned) {
				warned = true;
				ReiatsuTest.LOGGER.error("[fx] uryu quincy feature renderer failed (further errors are not logged)", ex);
			}
		}
	}

	/**
	 * Draws the veins on the given parts of the posed model: the solid translucent pass, then the additive pass. The parts are inflated (scale about
	 * the pivot, pivot shifted so that an arm keeps its place) for the two passes and restored right after.
	 */
	private static void drawParts(MatrixStack matrices, VertexConsumerProvider vcp, PlayerEntityModel<AbstractClientPlayerEntity> model, boolean slim, boolean armored, double level, int stage,
			List<UryuBlutTiming.Part> parts) {
		Identifier tex = STAGE[Math.max(0, Math.min(STAGE.length - 1, stage))];
		double glow = Math.min(1.0, UryuBlutTiming.glowGain(level) * FxConfig.uryuGlow * Math.max(0.0, FxConfig.emissiveMultiplier));
		int solid = FxMeshes.argb(1.0, 1.0, 1.0, UryuBlutTiming.solidAlpha(level));
		int add = FxMeshes.argb(glow, glow, glow, 1.0);
		// inflate the parts (scale about the pivot, the pivot shifted so that the arm keeps its place), draw both passes, restore
		int n = parts.size();
		ModelPart[] mp = new ModelPart[n];
		float[] saved = new float[n * 3];
		for (int i = 0; i < n; i++) {
			UryuBlutTiming.Part p = parts.get(i);
			ModelPart m = part(model, p);
			mp[i] = m;
			saved[i * 3] = m.xScale;
			saved[i * 3 + 1] = m.zScale;
			saved[i * 3 + 2] = m.pivotX;
			if (p == UryuBlutTiming.Part.BODY) {
				m.xScale = (float) UryuBlutTiming.bodyScaleX(armored);
				m.zScale = (float) UryuBlutTiming.bodyScaleZ(armored);
			} else {
				boolean left = p == UryuBlutTiming.Part.LEFT_ARM;
				double sx = UryuBlutTiming.armScaleX(slim, armored);
				m.xScale = (float) sx;
				m.zScale = (float) UryuBlutTiming.armScaleZ(armored);
				m.pivotX += (float) UryuBlutTiming.armPivotShiftX(slim, left, sx);
			}
		}
		try {
			VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(tex));
			for (ModelPart m : mp) {
				m.render(matrices, vc, LIGHT, OverlayTexture.DEFAULT_UV, solid);
			}
			vc = vcp.getBuffer(RenderLayer.getEyes(tex));
			for (ModelPart m : mp) {
				m.render(matrices, vc, LIGHT, OverlayTexture.DEFAULT_UV, add);
			}
			veinDraws++;
		} finally {
			for (int i = 0; i < n; i++) {
				mp[i].xScale = saved[i * 3];
				mp[i].zScale = saved[i * 3 + 1];
				mp[i].pivotX = saved[i * 3 + 2];
			}
		}
	}

	/** A chest piece of armor is drawn with a 1 px dilation over the torso and the arms: the overlay has to reach beyond it. */
	private static boolean armored(AbstractClientPlayerEntity pl) {
		ItemStack chest = pl.getEquippedStack(EquipmentSlot.CHEST);
		return !chest.isEmpty() && chest.getItem() instanceof ArmorItem a && a.getSlotType() == EquipmentSlot.CHEST;
	}

	private static ModelPart part(PlayerEntityModel<AbstractClientPlayerEntity> model, UryuBlutTiming.Part p) {
		return switch (p) {
			case BODY -> model.body;
			case RIGHT_ARM -> model.rightArm;
			case LEFT_ARM -> model.leftArm;
		};
	}

	/**
	 * First person: the overlay on the vanilla arm that was JUST drawn into {@code m} by {@code PlayerEntityRenderer.renderRightArm / renderLeftArm}
	 * (the model still holds that arm's pose). Called by {@code FirstPersonHand} after each of those calls; does nothing unless the local player
	 * (or the player the arm belongs to) has lit veins.
	 */
	public static void firstPersonArm(AbstractClientPlayerEntity player, PlayerEntityRenderer renderer, boolean left, MatrixStack m, VertexConsumerProvider vcp) {
		Q q = PLAYERS.get(player.getId());
		if (q == null) {
			return;
		}
		try {
			boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
			if (q.bankai) {
				UryuWingRenderer.firstPersonBracer(player, renderer.getModel(), slim, left, m, vcp); // the bracer of the bankai state on this arm
			}
			double[] v = vein(q, FxClock.now);
			if (v[1] < 0 || UryuBlutTiming.invisible(v[0])) {
				return;
			}
			firstPersonDraws++;
			drawParts(m, vcp, renderer.getModel(), slim, false, v[0], (int) v[1], List.of(left ? UryuBlutTiming.Part.LEFT_ARM : UryuBlutTiming.Part.RIGHT_ARM));
		} catch (RuntimeException ex) {
			if (!warned) {
				warned = true;
				ReiatsuTest.LOGGER.error("[fx] uryu first person veins failed (further errors are not logged)", ex);
			}
		}
	}
}
