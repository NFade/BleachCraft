package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * Uryu Ishida's Quincy cross (design/URYU.md). A {@link ZanpakutoItem} for the state machine and the client sites, but it does NOT
 * block on right click: that button is reserved for drawing the Heilig Bogen. Without a use hold {@code ZanpakutoItem.isBlocking}
 * is never true for him, so the parry and the ability lock of the block never apply.
 */
public class UryuCrossItem extends ZanpakutoItem {
	public UryuCrossItem(Settings settings, CharacterId character) {
		super(settings, character);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return TypedActionResult.pass(user.getStackInHand(hand));
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.NONE;
	}
}
