package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
	public static final Item SODE_NO_SHIRAYUKI = register("sode_no_shirayuki", CharacterId.RUKIA);
	public static final Item SENBONZAKURA = register("senbonzakura", CharacterId.BYAKUYA);

	/** Spike 2b test asset; kept so the spike harness (runSpike) stays usable. Not a zanpakuto for the state machine. */
	public static final Item SPIKE_ITEM = Registry.register(
			Registries.ITEM,
			ReiatsuTest.id("spike_item"),
			new Item(new Item.Settings().maxCount(1).component(ModComponents.RELEASE_STATE, ReleaseState.SEALED)));

	private ModItems() {
	}

	private static Item register(String name, CharacterId character) {
		return Registry.register(Registries.ITEM, ReiatsuTest.id(name),
				new ZanpakutoItem(new Item.Settings().maxCount(1).component(ModComponents.RELEASE_STATE, ReleaseState.SEALED),
						character));
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(new ItemStack(SODE_NO_SHIRAYUKI));
			entries.add(new ItemStack(SENBONZAKURA));
		});
	}

	/** Character of a stack's item, or NONE. */
	public static CharacterId characterOf(ItemStack stack) {
		return stack.getItem() instanceof ZanpakutoItem z ? z.character() : CharacterId.NONE;
	}
}
