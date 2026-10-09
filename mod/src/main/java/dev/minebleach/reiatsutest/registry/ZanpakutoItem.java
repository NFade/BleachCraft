package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.RequestSource;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.server.ZanpakutoManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** One item per character (ADR section 1). The visual state lives in the {@code release_state} component. */
public class ZanpakutoItem extends Item {
	private final CharacterId character;

	public ZanpakutoItem(Settings settings, CharacterId character) {
		super(settings);
		this.character = character;
	}

	public CharacterId character() {
		return character;
	}

	/**
	 * Right click draws the sword (SEALED to BASE, B4 step 3). Only the server acts (through the same path as the draw key,
	 * so every guard of the state machine applies); the result is PASS on both sides so no swing animation, no block
	 * placement and no item use animation interfere. Does nothing in any drawn state.
	 */
	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (!world.isClient && hand == Hand.MAIN_HAND && user instanceof ServerPlayerEntity sp) {
			StateMachine sm = ZanpakutoManager.machine(sp);
			if (sm != null && sm.state() == ZanpakutoState.SEALED) {
				ZanpakutoManager.performTransition(sp, ZanpakutoState.BASE, RequestSource.KEY, StateMachine.SERVER_SEQ);
			}
		}
		return TypedActionResult.pass(user.getStackInHand(hand));
	}
}
