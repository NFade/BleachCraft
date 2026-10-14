package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** One item per character (ADR section 1). The visual state lives in the {@code release_state} component. */
public class ZanpakutoItem extends Item {
	/** Block hold time: effectively unlimited (1 hour), the block ends when the player releases the right mouse button. */
	public static final int BLOCK_MAX_USE_TICKS = 72000;

	private final CharacterId character;

	public ZanpakutoItem(Settings settings, CharacterId character) {
		super(settings);
		this.character = character;
	}

	public CharacterId character() {
		return character;
	}

	/**
	 * Right click holds a block (design/PARRY_PLAN.md section 2): the sword is NOT drawn or sheathed by the mouse any more, that
	 * is the draw key (J). Same pattern as a shield: {@code setCurrentHand} on both sides and CONSUME, so the vanilla use hold,
	 * the block arm pose and the stop-use packet on release do the tracking. Only the main hand blocks: the whole state model
	 * (draw, release, mirror) is main-hand based.
	 */
	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (hand != Hand.MAIN_HAND) {
			return TypedActionResult.pass(stack);
		}
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BLOCK;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return BLOCK_MAX_USE_TICKS;
	}

	/**
	 * True while the entity holds a block with a zanpakuto. Own definition on purpose: the vanilla {@code isBlocking()} only
	 * becomes true after 5 ticks (shield delay), which would eat the 4 tick parry window.
	 */
	public static boolean isBlocking(LivingEntity entity) {
		// Uryu's use hold is the bow draw (UseAction.BOW), never a block: no parry window, no ability lock, no block pose
		return entity.isUsingItem() && entity.getActiveItem().getItem() instanceof ZanpakutoItem && !(entity.getActiveItem().getItem() instanceof UryuCrossItem);
	}

	/** Ticks since the block began (0 on the tick the right button went down), or -1 when not blocking. */
	public static int blockTicks(LivingEntity entity) {
		return isBlocking(entity) ? entity.getItemUseTime() : -1;
	}
}
