package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.server.uryu.UryuManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * Uryu Ishida's Quincy cross and, once drawn, his Heilig Bogen (design/URYU_PLAN.md revision 2). A {@link ZanpakutoItem} for the state
 * machine and the client sites, but its use action is the vanilla BOW one, not the sword block: hold right click to draw, release to
 * fire. {@link ZanpakutoItem#isBlocking} is false for it, so the parry window, the ability lock of the block and the block pose never
 * apply while the bow is drawn.
 *
 * <p>Wiring (all vanilla paths of a bow): {@link #use} starts the use hold on both sides (client and server call it), the client
 * HeldItemRenderer then draws the bow pull transform ({@link #getUseAction}) and the player model takes the BOW_AND_ARROW arm pose;
 * releasing the key sends the stop-use packet and the SERVER calls {@link #onStoppedUsing} with the remaining ticks, which fires the
 * Heilig Pfeil in {@code UryuManager.releaseBow}. SEALED: the pendant does nothing (use passes).
 */
public class UryuCrossItem extends ZanpakutoItem {
	public UryuCrossItem(Settings settings, CharacterId character) {
		super(settings, character);
	}

	/** True when the stack is drawn (BASE, SHIKAI or BANKAI): only then it is a bow. */
	public static boolean isBow(ItemStack stack) {
		return stack.getItem() instanceof UryuCrossItem
				&& stack.getOrDefault(ModComponents.RELEASE_STATE, ReleaseState.SEALED) != ReleaseState.SEALED;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (hand != Hand.MAIN_HAND || !isBow(stack)) {
			return TypedActionResult.pass(stack);
		}
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return isBow(stack) ? UseAction.BOW : UseAction.NONE;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	/** Server side (the stop-use packet): the release of the draw. {@code remainingUseTicks} counts down from {@link #getMaxUseTime}. */
	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (world.isClient || !(user instanceof ServerPlayerEntity player) || !isBow(stack)) {
			return;
		}
		UryuManager.releaseBow(player, getMaxUseTime(stack, user) - remainingUseTicks);
	}
}
