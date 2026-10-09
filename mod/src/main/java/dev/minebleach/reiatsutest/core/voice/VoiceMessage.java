package dev.minebleach.reiatsutest.core.voice;

/**
 * One POST of the bridge page: recognised text, whether the recogniser says it is final, the recognition language, the
 * utterance index and the epoch milliseconds at which the browser received the result (latency logging).
 * {@code utt} is -1 when absent.
 */
public record VoiceMessage(String text, boolean isFinal, String lang, long utt, long clientTimeMs) {
}
