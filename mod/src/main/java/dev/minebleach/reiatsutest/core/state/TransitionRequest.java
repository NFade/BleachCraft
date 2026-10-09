package dev.minebleach.reiatsutest.core.state;

/** A request to change state. {@code held} is the character of the zanpakuto in the main hand (NONE = none). */
public record TransitionRequest(ZanpakutoState target, RequestSource source, int clientSeq, CharacterId held) {
}
