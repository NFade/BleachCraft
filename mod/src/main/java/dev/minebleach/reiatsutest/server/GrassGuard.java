package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.combat.SwingThruRules;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;

/**
 * R11 T11, server half: a player with a zanpakuto in the main hand does not break soft vegetation ({@link SoftPlants}: grass, ferns, flowers, dead bushes, sprigs) with it. The client half
 * ({@code client/BcGrassSwing}) makes the click swing the sword; this guard covers a client that still sends a dig (other versions, Better Combat settings).
 */
public final class GrassGuard {
	private GrassGuard() {
	}

	public static void init() {
		PlayerBlockBreakEvents.BEFORE.register(GrassGuard::allow);
	}

	private static boolean allow(World world, PlayerEntity player, BlockPos pos, BlockState state, net.minecraft.block.entity.BlockEntity be) {
		if (player.isCreative()) {
			return true;
		}
		boolean noCollision = state.getCollisionShape(world, pos).isEmpty();
		// R11 review fix 7: only soft vegetation is protected; torches, crops, sugar cane, saplings, redstone dust ... break as usual
		return !SwingThruRules.shouldSwing(player.getMainHandStack().getItem() instanceof ZanpakutoItem, SoftPlants.isSoftPlant(state), noCollision,
				state.getHardness(world, pos));
	}
}
