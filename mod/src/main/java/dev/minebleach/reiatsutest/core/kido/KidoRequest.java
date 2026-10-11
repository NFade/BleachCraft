package dev.minebleach.reiatsutest.core.kido;

import dev.minebleach.reiatsutest.core.state.RequestSource;

/** A request to cast a spell, from the key or from the voice bridge ({@code StateMachine.SERVER_SEQ} for voice). */
public record KidoRequest(KidoId kido, RequestSource source, int clientSeq) {
}
