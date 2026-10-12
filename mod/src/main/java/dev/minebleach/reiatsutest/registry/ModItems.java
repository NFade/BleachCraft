package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
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

	/**
	 * Melee attribute modifiers of a zanpakuto, same numbers as a vanilla diamond sword: +6 attack damage (player base 1, so 7
	 * per full-charge hit) and -2.4 attack speed (4 - 2.4 = 1.6). Better Combat takes the swing damage from these modifiers and
	 * multiplies it by its per-attack {@code damage_multiplier} (katana preset 0.8 / 1.2), and the swing cooldown from the speed.
	 * Katana speed is a separate later step, so the speed stays the vanilla sword one here.
	 */
	public static final double ATTACK_DAMAGE_BONUS = 3.0;
	public static final double ATTACK_SPEED_BONUS = -2.4;

	private static AttributeModifiersComponent meleeAttributes() {
		return AttributeModifiersComponent.builder()
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE,
						new EntityAttributeModifier(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID, ATTACK_DAMAGE_BONUS,
								EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.add(EntityAttributes.GENERIC_ATTACK_SPEED,
						new EntityAttributeModifier(Item.BASE_ATTACK_SPEED_MODIFIER_ID, ATTACK_SPEED_BONUS,
								EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.build();
	}

	private static Item register(String name, CharacterId character) {
		return Registry.register(Registries.ITEM, ReiatsuTest.id(name),
				new ZanpakutoItem(new Item.Settings().maxCount(1).component(ModComponents.RELEASE_STATE, ReleaseState.SEALED)
						.attributeModifiers(meleeAttributes()), character));
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(new ItemStack(SODE_NO_SHIRAYUKI));
			entries.add(new ItemStack(SENBONZAKURA));
		});
		ModBooks.init();
	}

	/** Character of a stack's item, or NONE. */
	public static CharacterId characterOf(ItemStack stack) {
		return stack.getItem() instanceof ZanpakutoItem z ? z.character() : CharacterId.NONE;
	}
}
