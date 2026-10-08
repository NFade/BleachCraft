package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
	public static final Item SPIKE_ITEM = Registry.register(
			Registries.ITEM,
			ReiatsuTest.id("spike_item"),
			new Item(new Item.Settings().maxCount(1).component(ModComponents.RELEASE_STATE, ReleaseState.SEALED)));

	private ModItems() {
	}

	public static void init() {
		// class load registers the item (after the component)
	}
}
