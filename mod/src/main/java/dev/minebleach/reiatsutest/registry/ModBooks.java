package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.book.SpellbookId;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

/** The spellbook items (B3): book_sode_no_shirayuki, book_senbonzakura, book_kido. Called from {@link ModItems#init()}. Also registers the creative tab of the mod. */
public final class ModBooks {
	private static final Map<SpellbookId, Item> ITEMS = new EnumMap<>(SpellbookId.class);

	public static final Item SODE_NO_SHIRAYUKI = register(SpellbookId.SODE_NO_SHIRAYUKI);
	public static final Item SENBONZAKURA = register(SpellbookId.SENBONZAKURA);
	public static final Item KIDO = register(SpellbookId.KIDO);

	private ModBooks() {
	}

	private static Item register(SpellbookId book) {
		Item item = Registry.register(Registries.ITEM, ReiatsuTest.id(book.id),
				new SpellbookItem(new Item.Settings().maxCount(1), book));
		ITEMS.put(book, item);
		return item;
	}

	public static Item item(SpellbookId book) {
		return ITEMS.get(book);
	}

	public static void init() {
		// Next to the zanpakuto in the combat tab ...
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			for (SpellbookId b : SpellbookId.values()) {
				entries.add(new ItemStack(ITEMS.get(b)));
			}
		});
		// ... and in the creative tab of the mod: both zanpakuto and the three books.
		Registry.register(Registries.ITEM_GROUP, ReiatsuTest.id("main"), FabricItemGroup.builder()
				.displayName(Text.translatable("itemGroup.reiatsu_test"))
				.icon(() -> new ItemStack(KIDO))
				.entries((context, entries) -> {
					entries.add(new ItemStack(ModItems.SODE_NO_SHIRAYUKI));
					entries.add(new ItemStack(ModItems.SENBONZAKURA));
					for (SpellbookId b : SpellbookId.values()) {
						entries.add(new ItemStack(ITEMS.get(b)));
					}
				})
				.build());
	}
}
