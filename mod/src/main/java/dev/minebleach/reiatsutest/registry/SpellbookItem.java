package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.core.book.SpellbookId;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A spellbook (B3). Right click opens a read-only client screen; the item itself does nothing on the server. The screen
 * lives in the client source set, so the client registers an opener here at start-up ({@link #clientOpener}).
 */
public class SpellbookItem extends Item {
	/** Set by the client initializer; null on a dedicated server (the item is then inert). */
	public static volatile Consumer<SpellbookId> clientOpener;

	private final SpellbookId book;

	public SpellbookItem(Settings settings, SpellbookId book) {
		super(settings);
		this.book = book;
	}

	public SpellbookId book() {
		return book;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			Consumer<SpellbookId> opener = clientOpener;
			if (opener != null) {
				opener.accept(book);
			}
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.reiatsu_test.book.open").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.reiatsu_test.book.numbers").formatted(Formatting.DARK_GRAY));
	}
}
