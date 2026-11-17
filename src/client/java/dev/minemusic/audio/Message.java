package dev.minemusic.audio;

/**
 * Text for the player's screens: a translation key (see assets/minemusic/lang) and its arguments,
 * which are Strings or other Messages. The screens turn it into Minecraft text, so the audio code
 * never touches Minecraft and every language gets its own wording.
 */
public record Message(String key, Object... args) {
    /** Text shown exactly as it is, for example an error printed by yt-dlp. */
    public static Message raw(String text) {
        return new Message(null, text);
    }
}
