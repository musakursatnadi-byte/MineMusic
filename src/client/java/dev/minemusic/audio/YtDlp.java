package dev.minemusic.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Runs yt-dlp: finds songs on YouTube and downloads the audio of one of them into the cache. */
final class YtDlp {
    private static final long CACHE_LIMIT_BYTES = 500L * 1024 * 1024;
    // Live streams (24/7 "radio" mixes) never end, so they can neither be downloaded nor played.
    private static final String NOT_LIVE = "!is_live & live_status!=is_live";
    // How many search results Play looks through for the first one that is not live.
    private static final int SEARCH_TRIES = 5;
    private static final int EXIT_MAX_DOWNLOADS = 101;
    private static final Pattern MIX_LINK = Pattern.compile("[?&]list=RD(?:AMVM)?([A-Za-z0-9_-]{11})(?:&|$)");

    record Track(String title, Path file, int durationSeconds) {
    }

    /** One search result: the YouTube video id, its title and its length (0 when unknown). */
    record Found(String id, String title, int durationSeconds) {
    }

    private YtDlp() {
    }

    static Track fetch(String input, Consumer<Message> status, BooleanSupplier cancelled, ProcessTracker tracker)
            throws IOException, InterruptedException {
        try {
            return fetchOnce(input, status, cancelled, tracker);
        } catch (MusicException e) {
            // YouTube now and then refuses a download with "HTTP Error 403"; asking again usually works.
            if (cancelled.getAsBoolean() || e.getMessage() == null || !e.getMessage().contains("HTTP Error 403")) {
                throw e;
            }
            return fetchOnce(input, status, cancelled, tracker);
        }
    }

    private static Track fetchOnce(String input, Consumer<Message> status, BooleanSupplier cancelled, ProcessTracker tracker)
            throws IOException, InterruptedException {
        Path cache = Tools.cacheDir();
        Files.createDirectories(cache);
        trimCache(cache);

        boolean byName = !isLink(input);
        List<String> cmd = byName ? searchCommand() : baseCommand();
        if (byName) {
            // The first result that is not a live stream (a never-ending live "mix" cannot be played).
            cmd.addAll(List.of("--max-downloads", "1"));
        }
        cmd.addAll(List.of(
                "-f", "bestaudio/best",
                "--no-simulate",
                "--print", "video:MMID %(id)s",
                "--print", "video:MMTITLE %(title)s",
                "--print", "video:MMDUR %(duration)s",
                "--progress",
                "--newline",
                "--progress-template", "download:MMPROG %(progress._percent_str)s",
                "-o", cache.resolve("%(id)s.%(ext)s").toString()));
        if (Tools.ffmpeg().endsWith(".exe")) {
            // Our own ffmpeg (Windows); elsewhere yt-dlp finds the one on PATH by itself.
            cmd.add("--ffmpeg-location");
            cmd.add(Path.of(Tools.ffmpeg()).toAbsolutePath().getParent().toString());
        }
        cmd.add(byName ? "ytsearch" + SEARCH_TRIES + ":" + input : link(input));

        String id = null;
        String title = null;
        int duration = 0;
        String error = null;

        Process process = tracker.start(cmd);
        try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = out.readLine()) != null) {
                if (cancelled.getAsBoolean()) {
                    throw new InterruptedException();
                }
                if (line.startsWith("MMID ")) {
                    id = line.substring(5).trim();
                } else if (line.startsWith("MMTITLE ")) {
                    title = line.substring(8).trim();
                    status.accept(new Message("minemusic.status.downloading", title));
                } else if (line.startsWith("MMDUR ")) {
                    duration = parseDuration(line.substring(6));
                } else if (line.startsWith("MMPROG ")) {
                    String percent = line.substring(7).trim().replace("%", "");
                    status.accept(new Message("minemusic.status.downloading_progress", percent, title != null ? title : input));
                } else if (line.startsWith("ERROR:")) {
                    error = line.substring(6).trim();
                }
            }
            int exit = process.waitFor();
            // 101: "--max-downloads" stopped the search after the first song, as asked.
            boolean finished = exit == 0 || exit == EXIT_MAX_DOWNLOADS;
            if (finished && id == null && error == null) {
                // Nothing passed the filter: the link (or every result) is a live stream.
                throw new MusicException(new Message("minemusic.error.live"));
            }
            if (!finished || id == null) {
                // yt-dlp's own error text is English and very specific, so it is shown as it is.
                throw new MusicException(error != null ? Message.raw(error) : new Message("minemusic.error.ytdlp_failed", Integer.toString(exit)));
            }
        } finally {
            tracker.finish(process);
        }

        Path file = findCached(cache, id);
        if (file == null) {
            throw new MusicException(new Message("minemusic.error.no_file"));
        }
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        return new Track(title != null ? title : id, file, duration);
    }

    /** Looks up a song's title without downloading it (for songs added to the playlist). */
    static String title(String input, ProcessTracker tracker) throws IOException, InterruptedException {
        if (!isLink(input)) {
            // The same song that Play will pick: the first result that is not a live stream.
            List<Found> found = search(input, SEARCH_TRIES, tracker);
            return found.isEmpty() ? null : found.get(0).title();
        }
        List<String> cmd = baseCommand();
        cmd.addAll(List.of("--skip-download", "--print", "MMTITLE %(title)s"));
        cmd.add(link(input));
        Process process = tracker.start(cmd);
        try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String title = null;
            String line;
            while ((line = out.readLine()) != null) {
                if (line.startsWith("MMTITLE ")) {
                    title = line.substring(8).trim();
                }
            }
            process.waitFor();
            return title;
        } finally {
            tracker.finish(process);
        }
    }

    /**
     * The first {@code count} YouTube results for a song name, without downloading anything.
     * "--flat-playlist" reads only the search page, so it takes about two seconds.
     */
    static List<Found> search(String query, int count, ProcessTracker tracker) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of(
                Tools.ytDlp(),
                "--ignore-config",
                "--flat-playlist",
                "--match-filter", NOT_LIVE,
                // The title goes last, so a "|" inside it does not matter.
                "--print", "MMRES %(id)s|%(duration|)s|%(title)s"));
        addJsRuntime(cmd);
        cmd.add("ytsearch" + count + ":" + query);
        List<Found> found = new ArrayList<>();
        String error = null;
        Process process = tracker.start(cmd);
        try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = out.readLine()) != null) {
                if (line.startsWith("MMRES ")) {
                    String[] parts = line.substring(6).split("\\|", 3);
                    if (parts.length == 3 && !parts[0].isBlank()) {
                        found.add(new Found(parts[0].trim(), parts[2].trim(), parseDuration(parts[1])));
                    }
                } else if (line.startsWith("ERROR:")) {
                    error = line.substring(6).trim();
                }
            }
            int exit = process.waitFor();
            if (exit != 0 && found.isEmpty()) {
                throw new MusicException(error != null ? Message.raw(error) : new Message("minemusic.error.ytdlp_failed", Integer.toString(exit)));
            }
        } finally {
            tracker.finish(process);
        }
        return found;
    }

    /** For a link: just that one video (a link from a playlist or a Mix plays the song it points at). */
    private static List<String> baseCommand() {
        List<String> cmd = new ArrayList<>(List.of(
                Tools.ytDlp(),
                "--ignore-config",
                "--no-playlist",
                "--playlist-items", "1",
                "--match-filter", NOT_LIVE));
        addJsRuntime(cmd);
        return cmd;
    }

    /** For a song name: the first few search results, live streams left out. */
    private static List<String> searchCommand() {
        List<String> cmd = new ArrayList<>(List.of(
                Tools.ytDlp(),
                "--ignore-config",
                "--match-filter", NOT_LIVE));
        addJsRuntime(cmd);
        return cmd;
    }

    private static boolean isLink(String input) {
        return input.startsWith("http://") || input.startsWith("https://");
    }

    /**
     * A YouTube Mix page (".../playlist?list=RD" + a video id) cannot be opened ("this playlist
     * type is unviewable"), but the id in it is the song the Mix starts from, so that one plays.
     */
    static String link(String input) {
        if (!input.contains("v=")) {
            Matcher mix = MIX_LINK.matcher(input);
            if (mix.find()) {
                return "https://www.youtube.com/watch?v=" + mix.group(1);
            }
        }
        return input;
    }

    /** yt-dlp needs a JavaScript program (deno) to read YouTube; on Windows it is our own copy. */
    private static void addJsRuntime(List<String> cmd) {
        if (Tools.ytDlp().endsWith(".exe")) {
            cmd.add("--js-runtimes");
            cmd.add("deno:" + Tools.deno());
        }
    }

    /** Asks yt-dlp to update itself; YouTube changes often and old versions stop working. */
    static void update(ProcessTracker tracker) throws IOException, InterruptedException {
        Process process = tracker.start(List.of(Tools.ytDlp(), "-U"));
        try {
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            process.waitFor();
        } finally {
            tracker.finish(process);
        }
    }

    private static int parseDuration(String text) {
        try {
            return (int) Math.round(Double.parseDouble(text.trim()));
        } catch (NumberFormatException e) {
            return 0; // live streams and some videos have no duration
        }
    }

    private static Path findCached(Path cache, String id) throws IOException {
        try (Stream<Path> files = Files.list(cache)) {
            return files.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(id + ".") && !name.endsWith(".part") && !name.endsWith(".ytdl") && !name.contains(".temp");
                    })
                    .max(Comparator.comparingLong(YtDlp::lastModified))
                    .orElse(null);
        }
    }

    /**
     * Deletes the least recently played songs once the cache grows past the limit. A file that
     * cannot be deleted right now (an antivirus may be reading it) is skipped; tidying up must
     * never stop the next song from playing.
     */
    private static void trimCache(Path cache) throws IOException {
        List<Path> files;
        try (Stream<Path> list = Files.list(cache)) {
            files = new ArrayList<>(list.filter(Files::isRegularFile).sorted(Comparator.comparingLong(YtDlp::lastModified)).toList());
        }
        long total = 0;
        for (Path p : files) {
            total += size(p);
        }
        for (Path p : files) {
            if (total <= CACHE_LIMIT_BYTES) {
                break;
            }
            long size = size(p);
            try {
                if (Files.deleteIfExists(p)) {
                    total -= size;
                }
            } catch (IOException e) {
                // Locked or already gone: try the next one.
            }
        }
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
