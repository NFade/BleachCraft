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
}
