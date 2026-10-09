package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import net.minecraft.item.Item;

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
}
