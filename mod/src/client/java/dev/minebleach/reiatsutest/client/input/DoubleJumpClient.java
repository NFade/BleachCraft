package dev.minebleach.reiatsutest.client.input;

import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.client.fx.FxParticles;
import dev.minebleach.reiatsutest.core.movement.DoubleJump;
import dev.minebleach.reiatsutest.core.movement.DoubleJumpConfig;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import dev.minebleach.reiatsutest.server.ConfigReload;
import java.util.Random;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Double jump in SHIKAI and BANKAI of every character: one extra jump in the air. Client glue around the pure {@link DoubleJump} state machine.
 *
 * <p>Vanilla movement is client-authoritative: the vertical velocity is set on the local player exactly like {@code LivingEntity.jump()}
 * does it (jump strength 0.42 plus the Jump Boost bonus, never lowering an upward speed that is already higher) and the server accepts the
 * resulting position packets. Fall damage is computed by the server from the position packets, so the air jump adds to the fall height
 * (nothing is reset here). The puff and the sound are client only: other players do not see or hear them (no client to server effect
 * packet exists, a new payload would be needed).
 */
public final class DoubleJumpClient {
	private static final Random RNG = new Random();
	private static final DoubleJump MACHINE = new DoubleJump();
	private static volatile DoubleJumpConfig config = DoubleJumpConfig.defaults();
	private static ClientPlayerEntity lastPlayer;

	private DoubleJumpClient() {
	}

	/** Loads the {@code doubleJump} config section and starts ticking. Reloaded with {@code /reiatsu reload} and on joining a world. */
	public static void init() {
		config = ModConfig.loadDoubleJump();
		ConfigReload.register(() -> config = ModConfig.loadDoubleJump());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> config = ModConfig.loadDoubleJump());
		ClientTickEvents.END_CLIENT_TICK.register(DoubleJumpClient::tick);
	}

	/** The numbers in force. */
	public static DoubleJumpConfig config() {
		return config;
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity p = client.player;
		if (p == null || client.world == null) {
			lastPlayer = null;
			MACHINE.resetAll();
			return;
		}
		if (p != lastPlayer) { // joined, respawned or changed dimension
			lastPlayer = p;
			MACHINE.resetAll();
		}
		DoubleJumpConfig cfg = config;
		if (!cfg.enabled()) {
			MACHINE.resetAll();
			return;
		}
		// the synced state of the LOCAL player; an attachment that cannot be read this frame means not allowed
		ZanpakutoData z = p.getAttached(ModAttachments.ZANPAKUTO);
		boolean allowed = z != null && DoubleJump.allowedState(z.zanpakutoState()) && client.currentScreen == null;
		boolean blocked = p.isTouchingWater() || p.isSubmergedInWater() || p.isInLava() || p.isClimbing() || p.isFallFlying()
				|| p.hasVehicle() || p.getAbilities().flying || p.isSpectator() || p.isSleeping() || !p.isAlive();
		boolean key = client.currentScreen == null && client.options.jumpKey.isPressed();
		if (MACHINE.tick(p.isOnGround(), key, blocked, allowed)) {
			fire(client, p, cfg);
		}
	}

	private static void fire(MinecraftClient client, ClientPlayerEntity p, DoubleJumpConfig cfg) {
		// LivingEntity.getJumpVelocity(): jump strength attribute (0.42) * block factor + Jump Boost; the block factor is left out (we are in the air)
		double base = p.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH) + p.getJumpBoostVelocityModifier();
		double up = base * cfg.strength();
		Vec3d v = p.getVelocity();
		p.setVelocity(v.x, Math.max(up, v.y), v.z);
		puff(client, p);
	}

	private static void puff(MinecraftClient client, ClientPlayerEntity p) {
		double x = p.getX();
		double y = p.getY() + 0.1;
		double z = p.getZ();
		for (int i = 0; i < 8; i++) {
			double a = (i + RNG.nextDouble() * 0.6) * (Math.PI * 2 / 8);
			double sp = 0.9 + RNG.nextDouble() * 0.7;
			FxParticles.spec(FxParticles.Kind.DUST_PUFF).at(x + Math.cos(a) * 0.25, y, z + Math.sin(a) * 0.25)
					.vel(Math.cos(a) * sp, 0.15 + RNG.nextDouble() * 0.25, Math.sin(a) * sp).drag(0.9).life(14)
					.size(0.3 + RNG.nextDouble() * 0.2, 0.8).color("#DCE4EC").alpha(0.4).seed(RNG.nextInt(4)).spawn();
		}
		client.world.playSound(x, y, z, SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.3f, 1.5f + RNG.nextFloat() * 0.2f, false);
	}
}
