package dev.minebleach.reiatsutest.core.state;

public record AbilityRequest(AbilityId ability, RequestSource source, int clientSeq, CharacterId held) {
}
