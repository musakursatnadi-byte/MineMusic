package dev.minemusic.audio;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * All of the player's song lists. Each one is a file in %APPDATA%/MineMusic/lists named after its
 * number (1.txt, 2.txt ...); the names the player gave them are in lists/names.properties. A list
 * without a name is shown as "List 1", "List 2" ... in the player's language.
 */
final class Playlists {
    static final int MAX_LISTS = 50;
    static final int MAX_NAME_LENGTH = 32;

    private final Path dir;
    private final Properties names = new Properties();
    private final TreeMap<Integer, Playlist> lists = new TreeMap<>();

    private Playlists(Path dir) {
        this.dir = dir;
    }

    static Playlists load() {
        Playlists playlists = new Playlists(Tools.home().resolve("lists"));
        playlists.read();
        return playlists;
    }

    private void read() {
        try {
            Files.createDirectories(dir);
            // Up to 1.0.3 there was a single list in playlist.txt; it becomes list 1.
            Path old = Tools.home().resolve("playlist.txt");
            Path first = dir.resolve("1.txt");
            if (Files.isRegularFile(old) && !Files.exists(first)) {
                Files.move(old, first);
            }
            Path namesFile = dir.resolve("names.properties");
            if (Files.isRegularFile(namesFile)) {
                try (Reader r = Files.newBufferedReader(namesFile, StandardCharsets.UTF_8)) {
                    names.load(r);
                }
            }
            try (Stream<Path> files = Files.list(dir)) {
                files.forEach(file -> {
                    String name = file.getFileName().toString();
                    if (name.matches("\\d{1,4}\\.txt")) {
                        int id = Integer.parseInt(name.substring(0, name.length() - 4));
                        if (id > 0) {
                            lists.put(id, Playlist.load(file));
                        }
                    }
                });
            }
        } catch (IOException ignored) {
            // Lists that cannot be read are left out; there is always at least one.
        }
        if (lists.isEmpty()) {
            lists.put(1, Playlist.load(dir.resolve("1.txt")));
        }
    }

    synchronized List<Integer> ids() {
        return List.copyOf(lists.keySet());
    }

    synchronized boolean has(int id) {
        return lists.containsKey(id);
    }

    synchronized int firstId() {
        return lists.firstKey();
    }

    synchronized Playlist get(int id) {
        return lists.get(id);
    }

    /** The name the player gave the list, or "" for the default "List N". */
    synchronized String name(int id) {
        return names.getProperty(Integer.toString(id), "");
    }

    /** Makes a new empty list and returns its number, or -1 when there are too many already. */
    synchronized int create(String name) {
        if (lists.size() >= MAX_LISTS) {
            return -1;
        }
        int id = lists.lastKey() + 1;
        Playlist list = Playlist.load(dir.resolve(id + ".txt"));
        list.save(); // the empty file keeps the list after a restart
        lists.put(id, list);
        setName(id, name);
        return id;
    }

    synchronized void rename(int id, String name) {
        if (lists.containsKey(id)) {
            setName(id, name);
        }
    }

    /** Deletes a list and its file. The last list cannot be deleted (it can be cleared instead). */
    synchronized Playlist delete(int id) {
        if (lists.size() <= 1 || !lists.containsKey(id)) {
            return null;
        }
        Playlist list = lists.remove(id);
        list.markDeleted();
        try {
            Files.deleteIfExists(list.file());
        } catch (IOException ignored) {
            // A file that cannot be deleted just stays; the list is gone from the game anyway.
        }
        names.remove(Integer.toString(id));
        saveNames();
        return list;
    }

    private void setName(int id, String name) {
        String clean = name == null ? "" : name.strip().replace('\n', ' ').replace('\t', ' ');
        if (clean.length() > MAX_NAME_LENGTH) {
            clean = clean.substring(0, MAX_NAME_LENGTH);
        }
        if (clean.isEmpty()) {
            names.remove(Integer.toString(id));
        } else {
            names.setProperty(Integer.toString(id), clean);
        }
        saveNames();
    }

    private void saveNames() {
        try {
            Files.createDirectories(dir);
            try (Writer w = Files.newBufferedWriter(dir.resolve("names.properties"), StandardCharsets.UTF_8)) {
                names.store(w, "MineMusic playlist names");
            }
        } catch (IOException ignored) {
            // Not being able to save the names should never break the music.
        }
    }
}
