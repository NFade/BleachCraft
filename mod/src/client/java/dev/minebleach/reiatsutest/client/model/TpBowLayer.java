package dev.minebleach.reiatsutest.client.model;

import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxTune;
import dev.minebleach.reiatsutest.core.uryu.BowClipState;
import dev.minebleach.reiatsutest.core.uryu.BowStance;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Third person draw of Uryu's bow for right handed players (the bow is in the LEFT hand, the right hand draws the string, see
 * {@link UryuBowPose#bowInLeftHand}): a playerAnimator layer above the cast layer (1600) and below the Better Combat strike layer (2000) that plays the
 * clip {@code uryu_bow_draw} (assets/reiatsu_test/player_animations, made in Blockbench: design/anim/player_game_rig_bow_v2.bbmodel, straight arms, the string
 * arm only slides back). The playback is not the library's own timer: {@link BowClipState} follows the real draw (frozen at full draw, released fast
 * forward, see there) and this layer moves the player by the ticks it asks for; the first 3 ticks fade in from the vanilla bow pose.
 */
public final class TpBowLayer implements IAnimation {
	private static final int PRIORITY = 1700;
	private static final Identifier CLIP = Identifier.of("reiatsu_test", "uryu_bow_draw");
	private static final int FADE_IN_TICKS = 3;
	/** R13: degrees the string arm is turned towards the body centre while the bow is drawn (key {@code uryu.tp.drawArmYaw}). */
	private static final double DRAW_ARM_YAW_DEG = -30.0;
	/** Default sign of the chest turn of the clip (the first in-game test: the turn went the wrong way, so -1). */
	private static final double TORSO_YAW_SCALE = -1.0;
	private static final Vec3f ZERO = new Vec3f(0f, 0f, 0f);

	private final AbstractClientPlayerEntity player;
	private final BowClipState state = new BowClipState();
	private final ModifierLayer<Clip> layer = new ModifierLayer<>();

	private TpBowLayer(AbstractClientPlayerEntity player) {
		this.player = player;
	}

	public static void register() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((p, stack) -> stack.addAnimLayer(PRIORITY, new TpBowLayer(p)));
	}

	/** The registered clip, or null while the resource has not loaded (the layer then stays silent and the vanilla pose shows). */
	private static KeyframeAnimation clip() {
		IPlayable p = PlayerAnimationRegistry.getAnimation(CLIP);
		return p instanceof KeyframeAnimation ka ? ka : null;
	}

	private boolean wantsDraw() {
		return UryuBowPose.bowInLeftHand(player) && UryuBowPose.drawing(player, player.getMainHandStack());
	}

	@Override
	public void tick() {
		BowClipState.Step step = state.step(wantsDraw());
		if (step.restart()) {
			KeyframeAnimation anim = clip();
			ReiatsuTest.LOGGER.info("[uryu-bow] draw clip start (player {}, clip loaded: {})", player.getGameProfile().getName(), anim != null);
			if (anim != null) {
				layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_IN_TICKS, Ease.LINEAR), new Clip(anim));
			} else {
				layer.setAnimation(null);
			}
		} else if (!step.active()) {
			layer.setAnimation(null);
		}
		Clip c = layer.getAnimation();
		if (c != null) {
			c.frozen = state.holding();
			for (int i = 0; i < step.advance(); i++) {
				c.advance();
			}
		}
		layer.tick();
	}

	/**
	 * The library ticks ONLY the layers that report active (AnimationStack.tick), so an idle layer would never start: it is active while a clip runs
	 * AND while the player is drawing the bow (that is what lets the first tick() start the clip).
	 */
	@Override
	public boolean isActive() {
		return state.phase() != BowClipState.Phase.IDLE || wantsDraw();
	}

	@Override
	public void setupAnim(float tickDelta) {
		if (layer.getAnimation() != null) {
			layer.setupAnim(tickDelta);
		}
	}

	@Override
	public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
		if (layer.getAnimation() == null) {
			return value0;
		}
		if ("leftLeg".equals(modelName) || "rightLeg".equals(modelName)) {
			return value0; // the legs are always vanilla (they turn with the body in the stance)
		}
		Vec3f v = layer.get3DTransform(modelName, type, tickDelta, value0);
		if (v == null) {
			return value0;
		}
		float k = (float) FxTune.d("uryu.torsoYawScale", TORSO_YAW_SCALE);
		// the stance (turned chest, arm slide) follows the vanilla walk while the player moves (shooting on the run)
		float w = (float) BowClipState.stanceWeight(player.limbAnimator.getSpeed(tickDelta));
		if ("body".equals(modelName) || "torso".equals(modelName)) {
			if (type != TransformType.ROTATION) {
				return v;
			}
			return new Vec3f(v.getX(), value0.getY() + k * w * (v.getY() - value0.getY()), v.getZ());
		}
		boolean arm = "leftArm".equals(modelName) || "rightArm".equals(modelName);
		if (!arm && !"head".equals(modelName)) {
			return v;
		}
		if (FxTune.d("uryu.bodyCompensate", 1.0) < 0.5) {
			return v;
		}
		// playerAnimator turns the WHOLE model with the body yaw: give the head and arms the turn back so they look and aim forward (see BowStance)
		// R13: the turn to take back is the turn the model REALLY has: the torso yaw of the whole stack (the clip plus whatever the layers below add to the body). The
		// old formula used the clip's own torso yaw only, so the arms came out sideways whenever a lower layer had added a yaw to the body (after the first person pose, for one)
		Vec3f body = layer.get3DTransform("body", TransformType.ROTATION, tickDelta, ZERO);
		float full = body == null ? 0f : k * body.getY();
		float turn = full * w;
		try {
			Vec3f total = PlayerAnimationAccess.getPlayerAnimLayer(player).get3DTransform("body", TransformType.ROTATION, tickDelta, ZERO);
			if (total != null && FxTune.d("uryu.compFromStack", 1.0) > 0.5) {
				turn = total.getY();
				full = w > 0.001f ? turn / w : 0f;
			}
		} catch (RuntimeException ex) {
			// the clip based value stays
		}
		if (type == TransformType.ROTATION) {
			// R13 (user): the string hand points a little towards the middle of the body, so it looks as if it pulls the string and does not just pass beside it
			float in = "rightArm".equals(modelName) ? (float) Math.toRadians(FxTune.d("uryu.tp.drawArmYaw", DRAW_ARM_YAW_DEG)) * (float) state.drawWeight() : 0f;
			// R13: both arms follow the look pitch like the vanilla bow pose (arm pitch -90 degrees plus the head pitch), so the bow points where the archer looks up or down
			float pitch = arm ? (float) Math.toRadians(player.getPitch(tickDelta)) * (float) FxTune.d("uryu.tp.armPitch", 1.0) * (float) state.drawWeight() : 0f;
			return new Vec3f(v.getX() + pitch, v.getY() + turn + in, v.getZ());
		}
		if (arm && type == TransformType.POSITION) {
			double[] r = BowStance.rotateXZ(v.getX(), v.getZ(), turn);
			return new Vec3f((float) r[0], v.getY(), (float) r[1]);
		}
		return v;
	}

	/** The library player with its own timer cut off: only {@link #advance} moves it; at the hold pose the sub tick interpolation is pinned to 0 so it stands still. */
	private static final class Clip extends KeyframeAnimationPlayer {
		boolean frozen;

		Clip(KeyframeAnimation anim) {
			super(anim);
		}

		@Override
		public void tick() {
			// driven by BowClipState through advance()
		}

		void advance() {
			super.tick();
		}

		@Override
		public void setupAnim(float tickDelta) {
			super.setupAnim(frozen ? 0f : tickDelta);
		}

		@Override
		public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
			return super.get3DTransform(modelName, type, frozen ? 0f : tickDelta, value0);
		}
	}
}
