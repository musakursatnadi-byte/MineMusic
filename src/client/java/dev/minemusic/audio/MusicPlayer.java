package dev.minemusic.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays songs in the background: yt-dlp downloads one into the cache, ffmpeg turns it into
 * raw sound, and Java's sound system plays that. Everything runs on its own thread, so the game
 * never waits for it. This class does not touch Minecraft at all, so it is the same for every version.
 */
public final class MusicPlayer {
    public enum State { IDLE, LOADING, PLAYING, PAUSED, ERROR }

    /** What happens when a song ends: stop (or go on in the playlist), play it again, or start the playlist over. */
    public enum Repeat { OFF, ONE, ALL }

    private static final Logger LOGGER = LoggerFactory.getLogger("minemusic");
    private static final MusicPlayer INSTANCE = new MusicPlayer();

    private static final int SAMPLE_RATE = 48000;
    private static final int BYTES_PER_FRAME = 4; // 16-bit stereo
    private static final Duration YTDLP_UPDATE_INTERVAL = Duration.ofDays(7);
    private static final int SEARCH_RESULTS = 5;

    /** One YouTube search result: the link to play and what the results screen shows. */
    public record SearchResult(String link, String title, int durationSeconds) {
    }

    /** A search running on its own thread; the results screen looks at it every tick. */
    public static final class Search {
        private final String query;
        private volatile Message status = new Message("minemusic.search.searching");
        private volatile List<SearchResult> results;
        private volatile Message error;
        private volatile boolean cancelled;

        private Search(String query) {
            this.query = query;
        }

        public String query() {
            return query;
        }

        /** What the search is doing now (downloading the helper programs, searching...). */
        public Message status() {
            return status;
        }

        /** The results, or null while the search is still running. */
        public List<SearchResult> results() {
            return results;
        }

        /** Why the search failed, or null. */
        public Message error() {
            return error;
        }

        /** The screen was closed: whatever comes back is ignored. */
        public void cancel() {
            cancelled = true;
        }
    }

    private final Settings settings = Settings.load();
    private final Playlists lists = Playlists.load();
    // The list the playing song came from (the Playlist screen may show another one), or null.
    private Playlist playingList;
    private final ProcessTracker processes = new ProcessTracker();
    // Every play/stop starts a new session; threads of an old session notice and quit.
    private final AtomicInteger session = new AtomicInteger();

    private volatile State state = State.IDLE;
    private volatile Message status;
    private volatile String title = "";
    private volatile String currentInput = "";
    // Which playlist entry is playing, or -1 for a song started from the link box.
    private volatile int playlistIndex = -1;
    private volatile int durationSeconds;
    private volatile long playedFrames;
    private volatile boolean paused;
    private volatile boolean triedUpdate;
    private volatile DoubleSupplier gameVolume = () -> 1.0;
    // The downloaded song that is playing, or null.
    private volatile YtDlp.Track currentTrack;
    // What was playing when the music was turned off, so turning it on continues there.
    private String resumeInput;
    private int resumeIndex = -1;
    private int resumeSeconds;
    private YtDlp.Track resumeTrack;
    private Playlist resumeList;
    // A short note for the corner of the screen (like "music turned off"), shown for a few seconds.
    private volatile Message hudNote;
    private volatile long hudNoteAt;
    // Where the playing song sat in the playlist before it was removed from it, or -1.
    private int removedPlayingAt = -1;

    private MusicPlayer() {
        Runtime.getRuntime().addShutdownHook(new Thread(processes::killAll, "MineMusic-cleanup"));
    }

    public static MusicPlayer get() {
        return INSTANCE;
    }

    /** A link is played as it is; anything else is a song name to search for. */
    public static boolean isLink(String text) {
        String t = text.trim();
        return t.startsWith("http://") || t.startsWith("https://");
    }

    /** Looks up the first few YouTube results for a song name, on its own thread. */
    public Search search(String query) {
        Search search = new Search(query.trim());
        Thread thread = new Thread(() -> runSearch(search), "MineMusic-search");
        thread.setDaemon(true);
        thread.start();
        return search;
    }

    private void runSearch(Search search) {
        Consumer<Message> progress = msg -> search.status = msg;
        try {
            Tools.ensure(progress, () -> search.cancelled);
            progress.accept(new Message("minemusic.search.searching"));
            updateYtDlpIfOld(progress);
            List<YtDlp.Found> found;
            try {
                found = YtDlp.search(search.query, SEARCH_RESULTS, processes);
            } catch (IOException first) {
                // As with playing: a fresh yt-dlp usually fixes it. Try that once per game start.
                if (triedUpdate || search.cancelled) {
                    throw first;
                }
                triedUpdate = true;
                progress.accept(new Message("minemusic.status.updating"));
                YtDlp.update(processes);
                found = YtDlp.search(search.query, SEARCH_RESULTS, processes);
            }
            search.results = found.stream()
                    .map(f -> new SearchResult("https://www.youtube.com/watch?v=" + f.id(), f.title(), f.durationSeconds()))
                    .toList();
        } catch (InterruptedException e) {
            search.error = new Message("minemusic.status.stopped");
        } catch (Exception e) {
            if (!search.cancelled) {
                LOGGER.warn("MineMusic could not search for {}", search.query, e);
            }
            search.error = new Message("minemusic.status.error", describe(e));
        }
    }

    /** Plays a link or song name typed in the MineMusic screen. */
    public synchronized void play(String input) {
        String text = input.trim();
        if (text.isEmpty()) {
            state = State.ERROR;
            status = new Message("minemusic.error.empty");
            return;
        }
        settings.lastUrl = text;
        settings.lastText = text;
        settings.save();
        start(text, -1, 0, null, null);
    }

    /**
     * Plays a song picked from the search results. The MineMusic screen keeps showing the song
     * name that was searched for, while the picked link is what F9 plays again.
     */
    public synchronized void playResult(String link, String query) {
        settings.lastUrl = link;
        settings.lastText = query.trim();
        settings.save();
        start(link, -1, 0, null, null);
    }

    /** What was typed for the last song, for the MineMusic screen's box. */
    public String getLastText() {
        return settings.lastText;
    }

    /** Plays one song of the list on screen; when it ends the next one of that list follows. */
    public synchronized void playEntry(int index) {
        playFrom(selected(), index);
    }

    private void playFrom(Playlist list, int index) {
        Playlist.Entry entry = list.get(index);
        if (entry != null) {
            start(entry.input(), index, 0, null, list);
        }
    }

    /** "Play all": the first song of the list on screen, or a random one when shuffle is on. */
    public synchronized void playAll() {
        int size = selected().size();
        if (size > 0) {
            playEntry(settings.shuffle ? ThreadLocalRandom.current().nextInt(size) : 0);
        }
    }

    /** Next song: in the list that is playing, or else the list on screen. */
    public synchronized void next() {
        Playlist list = listForSkipping();
        int size = list.size();
        if (size == 0) {
            return;
        }
        int from = list == playingList ? playlistIndex : -1;
        playFrom(list, from < 0 ? 0 : nextIndex(list, from, true));
    }

    public synchronized void previous() {
        Playlist list = listForSkipping();
        int size = list.size();
        if (size == 0) {
            return;
        }
        int from = list == playingList ? playlistIndex : -1;
        playFrom(list, from <= 0 ? size - 1 : Math.min(from, size) - 1);
    }

    private Playlist listForSkipping() {
        return playingList != null && playlistIndex >= 0 ? playingList : selected();
    }

    public synchronized void stop() {
        session.incrementAndGet();
        processes.killAll();
        paused = false;
        if (state != State.IDLE) {
            state = State.IDLE;
            status = new Message("minemusic.status.stopped");
        }
        title = "";
        currentInput = "";
        currentTrack = null;
        playlistIndex = -1;
        playingList = null;
        removedPlayingAt = -1;
    }

    public void togglePause() {
        if (state == State.PLAYING) {
            paused = !paused;
        }
    }

    /**
     * Turns the music off, or on again at the second it was turned off. With nothing to go back
     * to, it plays the last link.
     */
    public synchronized void toggleOnOff() {
        State s = getState();
        if (s == State.PLAYING || s == State.PAUSED || s == State.LOADING) {
            String input = currentInput;
            int index = playlistIndex;
            Playlist list = playingList;
            boolean loading = s == State.LOADING;
            int seconds = loading ? 0 : (int) (playedFrames / SAMPLE_RATE);
            YtDlp.Track track = loading ? null : currentTrack;
            stop();
            resumeInput = input;
            resumeIndex = index;
            resumeList = list;
            resumeSeconds = seconds;
            resumeTrack = track;
            status = new Message("minemusic.status.turned_off");
            note(status);
        } else if (resumeInput != null) {
            String input = resumeInput;
            resumeInput = null;
            // The song is already downloaded, so it can start at once without asking YouTube again.
            start(input, resumeList != null ? resumeIndex : -1, resumeSeconds, resumeTrack, resumeList);
        } else if (!settings.lastUrl.isEmpty()) {
            start(settings.lastUrl, -1, 0, null, null);
        }
    }

    private void note(Message message) {
        hudNote = message;
        hudNoteAt = System.currentTimeMillis();
    }

    public State getState() {
        return state == State.PLAYING && paused ? State.PAUSED : state;
    }

    /** One line describing what is going on, or null when there is nothing to say. */
    public Message getStatus() {
        return switch (getState()) {
            case PLAYING -> new Message("minemusic.status.playing", title, position());
            case PAUSED -> new Message("minemusic.status.paused", title, position());
            default -> status;
        };
    }

    /**
     * The short line for the corner of the game screen: the song while it plays, what is
     * happening while it loads, and a short note (like "music turned off") after a key. Null hides it.
     */
    public Message getHudMessage() {
        return switch (getState()) {
            case PLAYING -> new Message("minemusic.hud.playing", title, position());
            case PAUSED -> new Message("minemusic.hud.paused", title, position());
            case LOADING -> new Message("minemusic.hud.note", status != null ? status : new Message("minemusic.status.preparing"));
            default -> hudNote != null && System.currentTimeMillis() - hudNoteAt < 3000
                    ? new Message("minemusic.hud.note", hudNote)
                    : null;
        };
    }

    /** The song that is playing (or paused), or "" when nothing is. */
    public String getTitle() {
        State s = getState();
        return s == State.PLAYING || s == State.PAUSED ? title : "";
    }

    public String getPosition() {
        return position();
    }

    /** True while MineMusic wants the game's own background music to stay quiet. */
    public boolean shouldMuteGameMusic() {
        State s = state;
        return settings.muteGameMusic && (s == State.LOADING || s == State.PLAYING);
    }

    public int getVolume() {
        return settings.volume;
    }

    public void changeVolume(int delta) {
        settings.volume = Math.max(0, Math.min(100, settings.volume + delta));
        settings.save();
    }

    public Repeat getRepeat() {
        return settings.repeat;
    }

    public void cycleRepeat() {
        settings.repeat = Repeat.values()[(settings.repeat.ordinal() + 1) % Repeat.values().length];
        settings.save();
    }

    public boolean isShuffle() {
        return settings.shuffle;
    }

    public void toggleShuffle() {
        settings.shuffle = !settings.shuffle;
        settings.save();
    }

    public boolean isMuteGameMusic() {
        return settings.muteGameMusic;
    }

    public void toggleMuteGameMusic() {
        settings.muteGameMusic = !settings.muteGameMusic;
        settings.save();
    }

    public boolean isShowHud() {
        return settings.showHud;
    }

    public void toggleShowHud() {
        settings.showHud = !settings.showHud;
        settings.save();
    }

    public String getLastUrl() {
        return settings.lastUrl;
    }

    /** Lets the game's master volume slider also turn the music down. */
    public void setGameVolume(DoubleSupplier gameVolume) {
        this.gameVolume = gameVolume;
    }

    // --- Playlists --------------------------------------------------------------------------

    /** The list shown in the Playlist screen (the songs added with "+ Add to list" go there). */
    private Playlist selected() {
        if (!lists.has(settings.list)) {
            settings.list = lists.firstId();
        }
        return lists.get(settings.list);
    }

    /** The songs of the list on screen. */
    public synchronized List<Playlist.Entry> getPlaylist() {
        return selected().entries();
    }

    /** Which entry of the list on screen is playing, or -1 (also when another list is playing). */
    public synchronized int getPlaylistIndex() {
        return playingList == selected() ? playlistIndex : -1;
    }

    /** The numbers of all lists, in order. */
    public synchronized List<Integer> getListIds() {
        return lists.ids();
    }

    /** The number of the list on screen. */
    public synchronized int getSelectedList() {
        selected();
        return settings.list;
    }

    /** The name the player gave a list, or "" when it has none (it is then shown as "List N"). */
    public synchronized String getListName(int id) {
        return lists.name(id);
    }

    public synchronized void selectList(int id) {
        if (lists.has(id) && id != settings.list) {
            settings.list = id;
            settings.save();
        }
    }

    /** Makes a new empty list and shows it. Returns false when there are already too many. */
    public synchronized boolean createList(String name) {
        int id = lists.create(name);
        if (id < 0) {
            return false;
        }
        selectList(id);
        return true;
    }

    public synchronized void renameList(int id, String name) {
        lists.rename(id, name);
    }

    /** Deletes a list. A song from it keeps playing, but nothing of that list follows. */
    public synchronized boolean deleteList(int id) {
        Playlist removed = lists.delete(id);
        if (removed == null) {
            return false;
        }
        if (removed == playingList) {
            playingList = null;
            playlistIndex = -1;
            removedPlayingAt = -1;
        }
        if (removed == resumeList) {
            resumeList = null;
        }
        selected();
        settings.save();
        return true;
    }

    /**
     * Adds a link or song name to the playlist. If it is the song that is playing, its title is
     * known already; otherwise yt-dlp looks it up in the background.
     */
    public synchronized boolean addToPlaylist(String input) {
        String text = input.trim();
        if (text.isEmpty()) {
            return false;
        }
        boolean playingIt = text.equals(currentInput) && !title.isEmpty();
        Playlist list = selected();
        if (!list.add(text, playingIt ? title : text)) {
            return false;
        }
        if (!playingIt && Tools.installed()) {
            Thread lookup = new Thread(() -> lookUpTitle(list, text), "MineMusic-title");
            lookup.setDaemon(true);
            lookup.start();
        }
        return true;
    }

    /** Adds a song picked from the search results; its title is known already. */
    public synchronized boolean addToPlaylist(String link, String title) {
        return selected().add(link, title);
    }

    public synchronized void removeFromPlaylist(int index) {
        Playlist list = selected();
        list.remove(index);
        if (list != playingList) {
            return;
        }
        if (index == playlistIndex) {
            // The song keeps playing; when it ends, the one that moved into its place comes next.
            playlistIndex = -1;
            removedPlayingAt = index;
        } else if (index < playlistIndex) {
            playlistIndex--;
        } else if (index < removedPlayingAt) {
            removedPlayingAt--;
        }
    }

    public synchronized void moveInPlaylist(int index, int delta) {
        Playlist list = selected();
        list.move(index, delta);
        if (list != playingList) {
            return;
        }
        if (index == playlistIndex) {
            playlistIndex = Math.max(0, Math.min(list.size() - 1, index + delta));
        } else if (index + delta == playlistIndex) {
            playlistIndex = index;
        }
    }

    public synchronized void clearPlaylist() {
        Playlist list = selected();
        list.clear();
        if (list == playingList) {
            playlistIndex = -1;
            removedPlayingAt = -1;
        }
    }

    private void lookUpTitle(Playlist list, String input) {
        try {
            String found = YtDlp.title(input, processes);
            if (found != null && !found.isEmpty()) {
                list.setTitle(input, found);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.debug("MineMusic could not look up the title of {}", input, e);
        }
    }

    // --- Playback ---------------------------------------------------------------------------

    /**
     * {@code known} is a song that is already downloaded; then yt-dlp is skipped. {@code list} is
     * the playlist the song comes from ({@code index} is its place there), or null with index -1.
     */
    private synchronized void start(String input, int index, int startSeconds, YtDlp.Track known, Playlist list) {
        stop();
        resumeInput = null;
        resumeTrack = null;
        resumeList = null;
        int id = session.get();
        currentInput = input;
        playlistIndex = list != null ? index : -1;
        playingList = list != null && index >= 0 ? list : null;
        removedPlayingAt = -1;
        title = "";
        durationSeconds = 0;
        playedFrames = 0;
        state = State.LOADING;
        status = new Message("minemusic.status.preparing");

        Playlist from = playingList;
        Thread thread = new Thread(() -> run(id, input, from, startSeconds, known), "MineMusic-player");
        thread.setDaemon(true);
        thread.start();
    }

    private void run(int id, String input, Playlist list, int startSeconds, YtDlp.Track known) {
        try {
            YtDlp.Track track = known;
            if (track == null || !Files.isRegularFile(track.file())) {
                Tools.ensure(msg -> update(id, State.LOADING, msg), () -> !isCurrent(id));
                update(id, State.LOADING, new Message("minemusic.status.searching"));
                track = fetch(id, input);
            }
            if (!isCurrent(id)) {
                return;
            }
            currentTrack = track;
            title = track.title();
            durationSeconds = track.durationSeconds();
            if (list != null) {
                list.setTitle(input, track.title());
            }
            // Turned back on near the very end: just start the song over.
            int from = durationSeconds > 0 && startSeconds >= durationSeconds - 1 ? 0 : startSeconds;
            do {
                playedFrames = (long) from * SAMPLE_RATE;
                update(id, State.PLAYING, null);
                if (playFile(id, track.file(), from) == 0) {
                    throw new MusicException(new Message("minemusic.error.cannot_play"));
                }
                from = 0;
            } while (isCurrent(id) && settings.repeat == Repeat.ONE);
            songEnded(id, track.title());
        } catch (InterruptedException e) {
            // Stopped on purpose.
        } catch (Exception e) {
            if (isCurrent(id)) {
                LOGGER.warn("MineMusic could not play {}", input, e);
                update(id, State.ERROR, new Message("minemusic.status.error", describe(e)));
            }
        }
    }

    /**
     * Goes on with the playlist, or stops when there is nothing left to play. The list may have
     * been reordered or shortened while the song played, so the current position is used, not the
     * one the song started at.
     */
    private synchronized void songEnded(int id, String finishedTitle) {
        if (!isCurrent(id)) {
            return;
        }
        Playlist list = playingList;
        int next = -1;
        if (list != null && playlistIndex >= 0) {
            next = nextIndex(list, playlistIndex, false);
        } else if (list != null && removedPlayingAt >= 0) {
            next = nextAfterRemoved(list);
        }
        if (next >= 0) {
            playFrom(list, next);
        } else {
            state = State.IDLE;
            status = new Message("minemusic.status.finished", finishedTitle);
            title = "";
            playlistIndex = -1;
            playingList = null;
        }
    }

    /** The song that follows one that was removed from the list while it played. */
    private int nextAfterRemoved(Playlist list) {
        int size = list.size();
        if (size == 0) {
            return -1;
        }
        if (settings.shuffle) {
            return ThreadLocalRandom.current().nextInt(size);
        }
        if (removedPlayingAt < size) {
            return removedPlayingAt;
        }
        return settings.repeat == Repeat.ALL ? 0 : -1;
    }

    /** The entry after {@code index}; at the end of the list it wraps only when asked or when repeating all. */
    private int nextIndex(Playlist list, int index, boolean wrap) {
        int size = list.size();
        if (size == 0) {
            return -1;
        }
        if (settings.shuffle && size > 1) {
            int pick = ThreadLocalRandom.current().nextInt(size - 1);
            return pick >= index ? pick + 1 : pick;
        }
        if (index + 1 < size) {
            return index + 1;
        }
        return wrap || settings.repeat == Repeat.ALL ? 0 : -1;
    }

    private YtDlp.Track fetch(int id, String input) throws IOException, InterruptedException {
        Consumer<Message> progress = msg -> update(id, State.LOADING, msg);
        updateYtDlpIfOld(progress);
        try {
            return YtDlp.fetch(input, progress, () -> !isCurrent(id), processes);
        } catch (IOException first) {
            // YouTube changes often; a fresh yt-dlp usually fixes it. Try that once per game start.
            if (triedUpdate || !isCurrent(id)) {
                throw first;
            }
            triedUpdate = true;
            update(id, State.LOADING, new Message("minemusic.status.updating"));
            YtDlp.update(processes);
            return YtDlp.fetch(input, progress, () -> !isCurrent(id), processes);
        }
    }

    /** yt-dlp is updated once a week; YouTube changes often and old versions stop working. */
    private void updateYtDlpIfOld(Consumer<Message> progress) throws IOException, InterruptedException {
        Path exe = Path.of(Tools.ytDlp());
        if (Files.isRegularFile(exe) && olderThan(exe, YTDLP_UPDATE_INTERVAL)) {
            progress.accept(new Message("minemusic.status.updating"));
            YtDlp.update(processes);
            Files.setLastModifiedTime(exe, FileTime.fromMillis(System.currentTimeMillis()));
        }
    }

    /**
     * Plays the file from {@code startSeconds} until it ends or the session changes.
     * Returns how many frames were played.
     */
    private long playFile(int id, Path file, int startSeconds) throws IOException, InterruptedException, LineUnavailableException {
        // -ss before -i makes ffmpeg jump straight to that second.
        Process ffmpeg = processes.startData(List.of(Tools.ffmpeg(),
                "-nostdin", "-hide_banner", "-loglevel", "error",
                "-ss", Integer.toString(startSeconds),
                "-i", file.toString(),
                "-vn", "-f", "s16le", "-acodec", "pcm_s16le",
                "-ar", Integer.toString(SAMPLE_RATE), "-ac", "2", "-"));
        long startFrames = (long) startSeconds * SAMPLE_RATE;
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        long frames = 0;
        try (InputStream in = ffmpeg.getInputStream()) {
            // A small buffer (0.2 s) keeps pause and volume changes snappy.
            line.open(format, SAMPLE_RATE * BYTES_PER_FRAME / 5);
            line.start();
            byte[] buf = new byte[8192];
            int carry = 0;
            while (isCurrent(id)) {
                if (paused) {
                    if (line.isRunning()) {
                        line.stop();
                    }
                    Thread.sleep(50);
                    continue;
                }
                if (!line.isRunning()) {
                    line.start();
                }
                int n = in.read(buf, carry, buf.length - carry);
                if (n < 0) {
                    break;
                }
                int total = carry + n;
                int whole = total - total % BYTES_PER_FRAME;
                applyVolume(buf, whole);
                line.write(buf, 0, whole);
                frames += whole / BYTES_PER_FRAME;
                playedFrames = startFrames + frames;
                carry = total - whole;
                System.arraycopy(buf, whole, buf, 0, carry);
            }
            if (isCurrent(id)) {
                line.drain();
            }
        } finally {
            line.stop();
            line.flush();
            line.close();
            processes.finish(ffmpeg);
        }
        return frames;
    }

    private void applyVolume(byte[] buf, int length) {
        double v = settings.volume / 100.0;
        // Squared so the low end of the scale feels gradual, like Minecraft's own sliders.
        double gain = v * v * Math.max(0, Math.min(1, gameVolume.getAsDouble()));
        if (gain >= 0.999) {
            return;
        }
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
            sample = (int) (sample * gain);
            buf[i] = (byte) sample;
            buf[i + 1] = (byte) (sample >> 8);
        }
    }

    private void update(int id, State newState, Message newStatus) {
        if (isCurrent(id)) {
            state = newState;
            status = newStatus;
        }
    }

    private boolean isCurrent(int id) {
        return session.get() == id;
    }

    private String position() {
        String played = time(playedFrames / SAMPLE_RATE);
        return durationSeconds > 0 ? played + " / " + time(durationSeconds) : played;
    }

    private static String time(long seconds) {
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private static boolean olderThan(Path file, Duration age) {
        try {
            return Files.getLastModifiedTime(file).toInstant().plus(age).isBefore(java.time.Instant.now());
        } catch (IOException e) {
            return false;
        }
    }

    private static Message describe(Exception e) {
        if (e instanceof MusicException music) {
            return music.message;
        }
        if (e instanceof LineUnavailableException) {
            return new Message("minemusic.error.audio_device");
        }
        if (e.getMessage() != null && e.getMessage().startsWith("Cannot run program")) {
            return new Message("minemusic.error.program");
        }
        if (e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException
                || e instanceof java.net.http.HttpConnectTimeoutException) {
            return new Message("minemusic.error.no_internet");
        }
        String text = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return Message.raw(text.length() > 100 ? text.substring(0, 97) + "..." : text);
    }
}
