package dev.minemusic.audio;

import java.io.IOException;

/** A failure with a translated message for the player. */
final class MusicException extends IOException {
    final Message message;

    MusicException(Message message) {
        super(message.key() != null ? message.key() : String.valueOf(message.args()[0]));
        this.message = message;
    }
}
