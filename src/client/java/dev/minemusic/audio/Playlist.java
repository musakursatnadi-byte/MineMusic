package dev.minemusic.audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One song list, saved as a text file in %APPDATA%/MineMusic/lists (shared by all profiles).
 * Each line is "link or search text" TAB "title".
 */
public final class Playlist {
    public static final int MAX_SIZE = 200;

    public record Entry(String input, String title) {
    }

    private final Path file;
    private final List<Entry> entries = new ArrayList<>();
    // A deleted list is never written again (a late title lookup must not bring its file back).
    private boolean deleted;

    private Playlist(Path file) {
        this.file = file;
    }

    static Playlist load(Path file) {
        Playlist playlist = new Playlist(file);
        try {
            if (Files.isRegularFile(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int tab = line.indexOf('\t');
                    String input = (tab >= 0 ? line.substring(0, tab) : line).trim();
                    String title = tab >= 0 ? line.substring(tab + 1).trim() : "";
                    if (!input.isEmpty() && playlist.entries.size() < MAX_SIZE) {
                        playlist.entries.add(new Entry(input, title.isEmpty() ? input : title));
                    }
                }
            }
        } catch (IOException ignored) {
            // A broken list file just starts empty.
        }
        return playlist;
    }

    Path file() {
        return file;
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public synchronized int size() {
        return entries.size();
    }

    synchronized Entry get(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    synchronized boolean add(String input, String title) {
        if (entries.size() >= MAX_SIZE) {
            return false;
        }
        entries.add(new Entry(input, title));
        save();
        return true;
    }

    synchronized void remove(int index) {
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            save();
        }
    }

    /** Moves an entry up (delta -1) or down (delta +1). */
    synchronized void move(int index, int delta) {
        int target = index + delta;
        if (index >= 0 && index < entries.size() && target >= 0 && target < entries.size()) {
            entries.add(target, entries.remove(index));
            save();
        }
    }

    synchronized void clear() {
        entries.clear();
        save();
    }

    /** Fills in the real title once yt-dlp has found it. */
    synchronized void setTitle(String input, String title) {
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.input().equals(input) && !e.title().equals(title)) {
                entries.set(i, new Entry(input, title));
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    synchronized void markDeleted() {
        deleted = true;
    }

    synchronized void save() {
        if (deleted) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (Entry e : entries) {
            text.append(e.input().replace('\t', ' ')).append('\t').append(e.title().replace('\t', ' ').replace('\n', ' ')).append('\n');
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Not being able to save the list should never break the music.
        }
    }
}
