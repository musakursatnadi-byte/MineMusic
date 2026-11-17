package dev.minemusic.audio;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** The player's choices, saved in %APPDATA%/MineMusic/ayarlar.properties (shared by all profiles). */
final class Settings {
    int volume = 50;
    MusicPlayer.Repeat repeat = MusicPlayer.Repeat.OFF;
    boolean shuffle = false;
    boolean muteGameMusic = true;
    boolean showHud = true;
    String lastUrl = "";
    // What the player typed for the last song (a song name stays a name even when a picked
    // search result, a link, is what lastUrl plays again); shown in the MineMusic screen's box.
    String lastText = "";
    // The playlist shown in the Playlist screen (its number in the lists folder).
    int list = 1;

    private static Path file() {
        return Tools.home().resolve("ayarlar.properties");
    }

    static Settings load() {
        Settings s = new Settings();
        Path file = file();
        if (Files.isRegularFile(file)) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
                s.volume = Math.max(0, Math.min(100, Integer.parseInt(p.getProperty("volume", "50").trim())));
                // Version 0.2 only had "loop" (repeat one song).
                String repeat = p.getProperty("repeat", Boolean.parseBoolean(p.getProperty("loop", "false").trim()) ? "ONE" : "OFF");
                s.repeat = MusicPlayer.Repeat.valueOf(repeat.trim());
                s.shuffle = Boolean.parseBoolean(p.getProperty("shuffle", "false").trim());
                s.muteGameMusic = Boolean.parseBoolean(p.getProperty("muteGameMusic", "true").trim());
                s.showHud = Boolean.parseBoolean(p.getProperty("showHud", "true").trim());
                s.lastUrl = p.getProperty("lastUrl", "");
                s.lastText = p.getProperty("lastText", s.lastUrl);
                s.list = Integer.parseInt(p.getProperty("list", "1").trim());
            } catch (IOException | IllegalArgumentException ignored) {
                // A broken settings file just falls back to the defaults.
            }
        }
        return s;
    }

    void save() {
        Properties p = new Properties();
        p.setProperty("volume", Integer.toString(volume));
        p.setProperty("repeat", repeat.name());
        p.setProperty("shuffle", Boolean.toString(shuffle));
        p.setProperty("muteGameMusic", Boolean.toString(muteGameMusic));
        p.setProperty("showHud", Boolean.toString(showHud));
        p.setProperty("lastUrl", lastUrl);
        p.setProperty("lastText", lastText);
        p.setProperty("list", Integer.toString(list));
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                p.store(w, "MineMusic");
            }
        } catch (IOException ignored) {
            // Not being able to save settings should never break the music.
        }
    }
}
