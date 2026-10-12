package dev.minebleach.reiatsutest.core.book;

import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.util.List;
import java.util.Set;

/**
 * One page of a spellbook. {@code commandIds} are the voice commands (design/VOICE_PHRASES.md) the page describes (empty for
 * an introduction page), {@code states} the zanpakuto states in which they work (the page shown when the book is opened in
 * that state), {@code args} the values of the lang text in placeholder order.
 */
public record BookPage(List<String> commandIds, Set<ZanpakutoState> states, List<BookArg> args) {
	public BookPage {
		commandIds = List.copyOf(commandIds);
		states = Set.copyOf(states);
		args = List.copyOf(args);
	}

	/** False for an introduction page (nothing to cast), which is always readable and never dimmed. */
	public boolean hasCommands() {
		return !commandIds.isEmpty();
	}

	/** True when the commands work in every state (the kido pages). */
	public boolean anyState() {
		return states.size() == ZanpakutoState.values().length;
	}

	/** The states of this page in enum order (sealed, drawn, shikai, bankai), for display. */
	public List<ZanpakutoState> orderedStates() {
		List<ZanpakutoState> out = new java.util.ArrayList<>();
		for (ZanpakutoState s : ZanpakutoState.values()) {
			if (states.contains(s)) {
				out.add(s);
			}
		}
		return out;
	}
}
