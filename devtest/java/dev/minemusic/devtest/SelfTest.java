package dev.minemusic.devtest;

import dev.minemusic.audio.MusicPlayer;
import dev.minemusic.audio.Tools;
import dev.minemusic.compat.Compat;
import dev.minemusic.screen.ListNameScreen;
import dev.minemusic.screen.MineMusicScreen;
import dev.minemusic.screen.PlaylistScreen;
import dev.minemusic.screen.SettingsScreen;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev-only automatic test for Minecraft 1.21.9+. It is compiled in only with -Pselftest=<project> and
 * walks through what a player does: Esc, MineMusic, paste a link, Play, the playlist, settings,
 * shortcut keys and the song name on screen. Every step is logged with "[SELFTEST]" and
 * screenshots go to run/screenshots. The player's real playlist and settings are put back at the end.
 */
public final class SelfTest {
    private static final Logger LOG = LoggerFactory.getLogger("minemusic-selftest");
    private static final String LINK = "https://www.youtube.com/watch?v=jNQXAC9IVRw";
    private static final String SONG_1 = "C418 Sweden";
    private static final String SONG_2 = "C418 Wet Hands";
    static final KeyEvent ENTER = new KeyEvent(InputConstants.KEY_RETURN, 0, 0);
    private static final int KEY_F8 = InputConstants.KEY_F8;
    private static final int KEY_F9 = InputConstants.KEY_F9;
    private static final int KEY_F10 = InputConstants.KEY_F10;

    static int failures;

    private SelfTest() {
    }

    public static void start() {
        // -Pselftestmode=galeri takes the Modrinth gallery screenshots instead of testing.
        String mode = System.getProperty("minemusic.selftest", "");
        Runnable test = switch (mode) {
            case "galeri" -> GalleryTest::run;
            case "galeri-listeler" -> GalleryTest::runLists;
            default -> SelfTest::run;
        };
        Thread thread = new Thread(test, "MineMusic-selftest");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        Minecraft mc = Minecraft.getInstance();
        MusicPlayer player = MusicPlayer.get();
        Path[] userFiles = {Tools.home().resolve("ayarlar.properties")};
        backup(userFiles);
        // The test works in a list of its own, so the player's playlists are never touched.
        player.createList("SelfTest");
        int testList = player.getSelectedList();
        try {
            log("dünyanın yüklenmesi bekleniyor");
            waitFor("dünya yüklendi", () -> mc.player != null && mc.level != null, 300);
            sleep(8);

            // --- Translation ---
            String play = Language.getInstance().getOrDefault("minemusic.screen.play");
            check(mc.options.languageCode.equals("tr_tr") && play.equals("Çal"),
                    "Türkçe dil dosyası yüklendi (dil: " + mc.options.languageCode + ", 'Çal' butonu: '" + play + "')");
            check(Language.getInstance().has("key.minemusic.open"), "Kısayol tuşu adları dil dosyasında var");

            // --- Pause menu button and main screen ---
            onMain(() -> Compat.setScreen(mc, new PauseScreen(true)));
            sleep(2);
            Button mineMusic = onMainGet(() -> findButton(TestCompat.screen(mc), "minemusic.button.open"));
            check(mineMusic != null, "ESC menüsünde MineMusic butonu var");
            if (mineMusic == null) {
                return;
            }
            onMain(() -> mineMusic.onPress(ENTER));
            sleep(1);
            check(onMainGet(() -> TestCompat.screen(mc) instanceof MineMusicScreen), "MineMusic butonu MineMusic ekranını açtı");
            onMain(() -> player.clearPlaylist());
            player.changeVolume(20 - player.getVolume());
            shot(mc, "1-minemusic-ekrani");

            // --- Minecraft's own music goes quiet ---
            onMain(() -> mc.getMusicManager().startPlaying(mc.getSituationalMusic()));
            sleep(2);
            check(vanillaMusicPlaying(mc), "Test için Minecraft müziği başlatıldı");
            playAndWait(mc, LINK, "link");
            sleep(1);
            check(!vanillaMusicPlaying(mc), "MineMusic çalarken Minecraft müziği sustu");

            // --- Playlist ---
            typeAndPress(mc, SONG_1, "minemusic.screen.add");
            typeAndPress(mc, SONG_2, "minemusic.screen.add");
            check(player.getPlaylist().size() == 2, "İki şarkı listeye eklendi (liste: " + player.getPlaylist().size() + ")");
            waitFor("şarkı adları bulundu", () -> player.getPlaylist().stream().noneMatch(e -> e.title().equals(e.input())), 60);
            log("  liste: " + player.getPlaylist().stream().map(e -> e.title()).toList());
            onMain(() -> Compat.setScreen(mc, new PlaylistScreen(TestCompat.screen(mc))));
            sleep(1);
            shot(mc, "2-calma-listesi");
            press(mc, "minemusic.playlist.play_all");
            waitPlaying(mc, "Hepsini çal ilk şarkıyı başlattı");
            check(player.getPlaylistIndex() == 0, "Listede 1. şarkı çalıyor: " + player.getTitle());
            sleep(2);
            shot(mc, "3-liste-caliyor");
            onMain(player::next);
            waitPlaying(mc, "Sonraki şarkıya geçildi");
            check(player.getPlaylistIndex() == 1 && player.getTitle().contains("Wet Hands"), "Listede 2. şarkı çalıyor: " + player.getTitle());
            onMain(player::previous);
            waitPlaying(mc, "Önceki şarkıya dönüldü");
            check(player.getPlaylistIndex() == 0 && player.getTitle().contains("Sweden"), "Yine 1. şarkı çalıyor: " + player.getTitle());

            // --- Settings screen ---
            onMain(() -> Compat.setScreen(mc, new SettingsScreen(null)));
            sleep(1);
            shot(mc, "4-ayarlar");
            check(onMainGet(() -> findButtonStartingWith(TestCompat.screen(mc), "minemusic.settings.mute_music") != null), "Ayarlar ekranında 'Minecraft müziğini sustur' var");

            // --- Several playlists: new, switch, rename, delete (the song keeps playing) ---
            Path lists = Tools.home().resolve("lists");
            check(Files.isRegularFile(lists.resolve("1.txt")) && !Files.exists(Tools.home().resolve("playlist.txt")),
                    "Eski çalma listesi lists/1.txt oldu (" + (Files.isRegularFile(lists.resolve("1.txt")) ? Files.readAllLines(lists.resolve("1.txt")).size() + " şarkı" : "yok") + ")");
            int listsBefore = player.getListIds().size();
            String playingSong = player.getTitle();
            onMain(() -> Compat.setScreen(mc, new PlaylistScreen(null)));
            sleep(1);
            press(mc, "minemusic.lists.new");
            sleep(1);
            check(onMainGet(() -> TestCompat.screen(mc) instanceof ListNameScreen), "'+ Yeni liste' isim ekranını açtı");
            onMain(() -> find(TestCompat.screen(mc), EditBox.class).setValue("Maden müziği"));
            press(mc, "gui.done");
            sleep(1);
            int newList = player.getSelectedList();
            check(player.getListIds().size() == listsBefore + 1 && player.getListName(newList).equals("Maden müziği") && player.getPlaylist().isEmpty(),
                    "Yeni boş liste açıldı ve seçildi: '" + player.getListName(newList) + "' (" + player.getListIds().size() + " liste)");
            onMain(() -> player.addToPlaylist(SONG_2));
            sleep(1);
            check(player.getPlaylist().size() == 1, "Yeni listeye şarkı eklendi");
            check(player.getTitle().equals(playingSong) && player.getState() == MusicPlayer.State.PLAYING, "Liste değişince çalan şarkı kesilmedi: " + player.getTitle());
            shot(mc, "4b-yeni-liste");
            Button left = onMainGet(() -> findLiteral(TestCompat.screen(mc), "◀"));
            onMain(() -> left.onPress(ENTER));
            sleep(1);
            check(player.getSelectedList() != newList, "◀ ile öbür listeye geçildi (liste " + player.getSelectedList() + ")");
            onMain(() -> player.selectList(newList));
            sleep(1);
            press(mc, "minemusic.lists.rename");
            sleep(1);
            String shownName = onMainGet(() -> find(TestCompat.screen(mc), EditBox.class).getValue());
            onMain(() -> find(TestCompat.screen(mc), EditBox.class).setValue("Savaş müziği"));
            press(mc, "gui.done");
            sleep(1);
            check(shownName.equals("Maden müziği") && player.getListName(newList).equals("Savaş müziği"), "Liste adı değişti: " + player.getListName(newList));
            press(mc, "minemusic.lists.delete");
            sleep(1);
            press(mc, "gui.yes");
            sleep(1);
            check(player.getListIds().size() == listsBefore && !player.getListIds().contains(newList) && !Files.exists(lists.resolve(newList + ".txt")),
                    "Liste silindi (" + player.getListIds().size() + " liste kaldı)");
            onMain(() -> player.selectList(testList));

            // --- Song name on screen and shortcut keys (in the game, no screen open) ---
            onMain(() -> Compat.setScreen(mc, null));
            sleep(3);
            shot(mc, "5-oyunda-sarki-adi");
            onMain(() -> KeyMapping.click(key(KEY_F9)));
            sleep(1);
            check(player.getState() == MusicPlayer.State.PAUSED, "F9 tuşu müziği duraklattı (durum: " + player.getState() + ")");
            shot(mc, "6-f9-duraklatildi");
            onMain(() -> KeyMapping.click(key(KEY_F9)));
            sleep(1);
            check(player.getState() == MusicPlayer.State.PLAYING, "F9 tuşu müziği sürdürdü (durum: " + player.getState() + ")");
            sleep(3);
            String before = player.getPosition();
            onMain(() -> KeyMapping.click(key(KEY_F8)));
            sleep(1);
            String offText = onMainGet(() -> player.getStatus() == null ? "" : dev.minemusic.screen.Texts.of(player.getStatus()).getString());
            check(player.getState() == MusicPlayer.State.IDLE && player.getTitle().isEmpty(),
                    "F8 tuşu müziği kapattı (" + before + " noktasında, durum yazısı: '" + offText + "')");
            shot(mc, "6b-f8-kapali");
            sleep(2);
            onMain(() -> KeyMapping.click(key(KEY_F8)));
            waitPlaying(mc, "F8 tuşu müziği tekrar açtı");
            int beforeSeconds = seconds(before);
            int afterSeconds = seconds(player.getPosition());
            check(player.getTitle().contains("Sweden") && afterSeconds >= beforeSeconds - 1 && afterSeconds <= beforeSeconds + 3,
                    "Aynı şarkı kaldığı yerden devam etti (" + before + " → " + player.getPosition() + ")");
            shot(mc, "6c-f8-acildi");
            onMain(() -> KeyMapping.click(key(KEY_F10)));
            sleep(1);
            check(onMainGet(() -> TestCompat.screen(mc) instanceof MineMusicScreen), "F10 tuşu MineMusic ekranını açtı");
            shot(mc, "7-f10-ekran");

            // --- Leaving the world turns the music off (it kept playing on the title screen); F8 goes on ---
            onMain(() -> Compat.setScreen(mc, new PauseScreen(true)));
            sleep(1);
            check(player.getState() == MusicPlayer.State.PLAYING, "Dünyadan çıkmadan önce müzik çalıyor");
            String beforeLeaving = player.getPosition();
            press(mc, "menu.returnToMenu");
            waitFor("ana menüye dönüldü", () -> mc.level == null && TestCompat.screen(mc) instanceof TitleScreen, 120);
            sleep(2);
            check(player.getState() == MusicPlayer.State.IDLE && player.getTitle().isEmpty(),
                    "Dünyadan çıkınca müzik kapandı (" + beforeLeaving + " noktasında, durum: " + player.getState() + ")");
            shot(mc, "8-ana-menu-sessiz");
            onMain(player::toggleOnOff);
            waitPlaying(mc, "F8 ana menüden sonra müziği tekrar açtı");
            int leftAt = seconds(beforeLeaving);
            int goesOnAt = seconds(player.getPosition());
            check(player.getTitle().contains("Sweden") && goesOnAt >= leftAt - 1 && goesOnAt <= leftAt + 10,
                    "Şarkı dünyadan çıkılan yerden devam etti (" + beforeLeaving + " → " + player.getPosition() + ")");

            player.stop();
            sleep(1);
            check(player.getState() == MusicPlayer.State.IDLE, "Durdur sonrası müzik kapandı");
        } catch (Throwable e) {
            failures++;
            LOG.error("[SELFTEST] HATA", e);
        } finally {
            player.stop();
            player.deleteList(testList);
            restore(userFiles);
            log(failures == 0 ? "SONUÇ: BAŞARILI" : "SONUÇ: " + failures + " HATA");
            sleep(2);
            mc.execute(mc::stop);
        }
    }

    private static boolean vanillaMusicPlaying(Minecraft mc) throws Exception {
        Field current = MusicManager.class.getDeclaredField("currentMusic");
        current.setAccessible(true);
        return onMainGet(() -> {
            try {
                return current.get(mc.getMusicManager()) != null;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** A keyboard key; 26.3 renamed InputConstants.Type.KEYSYM to KEYBOARD. */
    static InputConstants.Key key(int code) {
        InputConstants.Type type;
        try {
            type = InputConstants.Type.valueOf("KEYSYM");
        } catch (IllegalArgumentException e) {
            type = InputConstants.Type.valueOf("KEYBOARD");
        }
        return type.getOrCreate(code);
    }

    /** "1:23 / 3:36" → 83 */
    private static int seconds(String position) {
        String played = position.split(" / ")[0].trim();
        String[] parts = played.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private static void playAndWait(Minecraft mc, String input, String label) throws Exception {
        typeAndPress(mc, input, "minemusic.screen.play");
        waitPlaying(mc, label + " ile müzik başladı");
    }

    private static void typeAndPress(Minecraft mc, String input, String buttonKey) throws Exception {
        onMain(() -> find(TestCompat.screen(mc), EditBox.class).setValue(input));
        press(mc, buttonKey);
        sleep(1);
    }

    static void waitPlaying(Minecraft mc, String what) throws Exception {
        MusicPlayer player = MusicPlayer.get();
        long start = System.currentTimeMillis();
        String last = "";
        sleep(1);
        while (System.currentTimeMillis() - start < 180_000) {
            String now = player.getState() + " | " + onMainGet(() -> player.getStatus() == null ? "" : dev.minemusic.screen.Texts.of(player.getStatus()).getString());
            if (!now.equals(last)) {
                log("  " + now);
                last = now;
            }
            if (player.getState() == MusicPlayer.State.PLAYING || player.getState() == MusicPlayer.State.ERROR) {
                break;
            }
            Thread.sleep(250);
        }
        check(player.getState() == MusicPlayer.State.PLAYING, what + " (" + (System.currentTimeMillis() - start) / 1000 + " sn)");
    }

    static void press(Minecraft mc, String key) throws Exception {
        Button button = onMainGet(() -> findButton(TestCompat.screen(mc), key));
        if (button == null) {
            throw new IllegalStateException("'" + key + "' butonu bulunamadı");
        }
        onMain(() -> button.onPress(ENTER));
    }

    static Button findLiteral(Screen screen, String text) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getString().equals(text)) {
                return button;
            }
        }
        throw new IllegalStateException("'" + text + "' butonu bulunamadı");
    }

    static Button findButton(Screen screen, String key) {
        String label = Component.translatable(key).getString();
        if (screen != null) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof Button button && button.getMessage().getString().equals(label)) {
                    return button;
                }
            }
        }
        return null;
    }

    static Button findButtonStartingWith(Screen screen, String key) {
        String start = Component.translatable(key, "").getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getString().startsWith(start)) {
                return button;
            }
        }
        return null;
    }

    static <T> T find(Screen screen, Class<T> type) {
        for (GuiEventListener child : screen.children()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
        }
        throw new IllegalStateException(type.getSimpleName() + " bulunamadı");
    }

    static void shot(Minecraft mc, String name) throws Exception {
        onMain(() -> Screenshot.grab(mc.gameDirectory, "selftest-" + name + ".png", TestCompat.renderTarget(mc), 1, msg -> {
        }));
        log("ekran görüntüsü: selftest-" + name + ".png");
        sleep(1);
    }

    static void backup(Path[] files) {
        for (Path file : files) {
            try {
                Path copy = file.resolveSibling(file.getFileName() + ".selftest-yedek");
                Files.deleteIfExists(copy);
                if (Files.exists(file)) {
                    Files.copy(file, copy);
                }
            } catch (Exception e) {
                LOG.warn("[SELFTEST] yedeklenemedi: {}", file, e);
            }
        }
    }

    static void restore(Path[] files) {
        for (Path file : files) {
            try {
                Path copy = file.resolveSibling(file.getFileName() + ".selftest-yedek");
                if (Files.exists(copy)) {
                    Files.move(copy, file, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.deleteIfExists(file);
                }
                log("geri konuldu: " + file.getFileName());
            } catch (Exception e) {
                LOG.warn("[SELFTEST] geri konulamadı: {}", file, e);
            }
        }
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
        }
        log((ok ? "OK    " : "HATA  ") + what);
    }

    static void waitFor(String what, BooleanSupplier condition, int seconds) throws InterruptedException {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new IllegalStateException("zaman aşımı: " + what);
            }
            Thread.sleep(250);
        }
        log(what);
    }

    static void onMain(Runnable task) throws Exception {
        Minecraft.getInstance().submit(task).get(30, TimeUnit.SECONDS);
    }

    static <T> T onMainGet(Supplier<T> task) throws Exception {
        return Minecraft.getInstance().submit(task).get(30, TimeUnit.SECONDS);
    }

    static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    static void log(String message) {
        LOG.info("[SELFTEST] {}", message);
    }
}
