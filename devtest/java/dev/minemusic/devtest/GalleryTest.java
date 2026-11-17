package dev.minemusic.devtest;

import static dev.minemusic.devtest.SelfTest.backup;
import static dev.minemusic.devtest.SelfTest.check;
import static dev.minemusic.devtest.SelfTest.find;
import static dev.minemusic.devtest.SelfTest.log;
import static dev.minemusic.devtest.SelfTest.onMain;
import static dev.minemusic.devtest.SelfTest.onMainGet;
import static dev.minemusic.devtest.SelfTest.restore;
import static dev.minemusic.devtest.SelfTest.sleep;
import static dev.minemusic.devtest.SelfTest.waitFor;
import static dev.minemusic.devtest.SelfTest.waitPlaying;

import dev.minemusic.audio.Message;
import dev.minemusic.audio.MusicPlayer;
import dev.minemusic.audio.Tools;
import dev.minemusic.compat.Compat;
import dev.minemusic.mixin.BossHealthOverlayAccess;
import dev.minemusic.screen.MineMusicScreen;
import dev.minemusic.screen.PlaylistScreen;
import dev.minemusic.screen.SettingsScreen;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Dev-only run (-Pselftestmode=galeri) that takes the Modrinth gallery screenshots
 * ("gallery-NN-*.png" in run/screenshots): every screen, the shortcut keys, the song name on screen
 * in a few places, and the Turkish interface. It runs in a copy of a world
 * (run/saves/SelfTestWorld); the player's own MineMusic files are put back.
 */
final class GalleryTest {
    private static final String[] PLAYLIST = {"C418 Wet Hands", "C418 Sweden", "C418 Haggstrom", "C418 Aria Math", "C418 Mice on Venus"};
    // A song that is not in the cache yet, so its download can be photographed.
    private static final String NEW_SONG = "C418 Dry Hands";

    private GalleryTest() {
    }

    static void run() {
        run("");
    }

    /** Only the pictures of the playlists (-Pselftestmode=galeri-listeler). */
    static void runLists() {
        run("lists");
    }

    /** Only the pictures of the search results (-Pselftestmode=galeri-arama). */
    static void runSearch() {
        run("search");
    }

    private static void run(String only) {
        Minecraft mc = Minecraft.getInstance();
        MusicPlayer player = MusicPlayer.get();
        Path[] userFiles = {Tools.home().resolve("ayarlar.properties")};
        backup(userFiles);
        // The test works in a list of its own, so the player's playlists are never touched.
        player.createList("SelfTest");
        int testList = player.getSelectedList();
        try {
            log("GALERİ: dünyanın yüklenmesi bekleniyor");
            waitFor("dünya yüklendi", () -> mc.player != null && mc.level != null && mc.getSingleplayerServer() != null, 300);
            MinecraftServer server = mc.getSingleplayerServer();
            sleep(5);
            ensureAlive(mc);

            // A calm, sunny world at the spawn point: time and weather stand still, no monsters.
            for (String rule : new String[]{"log_admin_commands false", "advance_time false", "advance_weather false",
                    "spawn_monsters false", "show_advancement_messages false"}) {
                cmd(server, "gamerule " + rule);
            }
            cmd(server, "execute as @a run gamemode creative");
            cmd(server, "effect clear @a");
            cmd(server, "difficulty easy");
            cmd(server, "kill @e[type=minecraft:zombie]");
            cmd(server, "kill @e[type=minecraft:wither]");
            cmd(server, "weather clear");
            cmd(server, "time set 2000");
            BlockPos home = onServer(server, () -> server.overworld().getLevelData().getRespawnData().pos());
            int surface = surface(server, home);
            String goHome = "execute in minecraft:overworld run tp @a " + home.getX() + ".5 " + surface + " " + home.getZ() + ".5";
            cmd(server, goHome);
            sleep(3);
            lookOpen(server);
            sleep(6);

            // --- The screens ---
            player.changeVolume(30 - player.getVolume());
            onMain(player::clearPlaylist);
            for (String song : PLAYLIST) {
                onMain(() -> player.addToPlaylist(song));
            }
            waitFor("çalma listesindeki şarkı adları bulundu",
                    () -> player.getPlaylist().stream().noneMatch(e -> e.title().equals(e.input())), 180);
            openAndPress(mc, () -> new PlaylistScreen(new MineMusicScreen(null)), "minemusic.playlist.play_all");
            waitPlaying(mc, "Çalma listesi çalıyor");
            onMain(() -> Compat.setScreen(mc, new PlaylistScreen(new MineMusicScreen(null))));
            sleep(3);
            gallery(mc, "03-playlist");
            if (only.equals("lists")) {
                shootLists(mc, player);
                return;
            }
            if (only.equals("search")) {
                shootSearch(mc);
                return;
            }

            onMain(() -> Compat.setScreen(mc, new MineMusicScreen(null)));
            sleep(1);
            onMain(() -> find(TestCompat.screen(mc), EditBox.class).setValue("C418 Wet Hands"));
            sleep(3);
            gallery(mc, "02-minemusic-screen");

            onMain(() -> Compat.setScreen(mc, new SettingsScreen(new MineMusicScreen(null))));
            sleep(2);
            gallery(mc, "04-settings");

            // Options > Controls > Key Binds, scrolled down to the MineMusic keys.
            onMain(() -> Compat.setScreen(mc, new KeyBindsScreen(null, mc.options)));
            sleep(1);
            onMain(() -> scrollToBottom(TestCompat.screen(mc)));
            sleep(2);
            gallery(mc, "05-key-binds");

            onMain(() -> Compat.setScreen(mc, new PauseScreen(true)));
            sleep(2);
            gallery(mc, "01-pause-menu");

            // --- In the game: the song name, pause (F9), off (F8), a download ---
            onMain(() -> Compat.setScreen(mc, null));
            playSong(mc, "C418 Sweden");
            sleep(4);
            gallery(mc, "06-playing");

            onMain(player::togglePause);
            sleep(1);
            check(player.getState() == MusicPlayer.State.PAUSED, "Duraklatıldı");
            gallery(mc, "07-paused");
            onMain(player::togglePause);
            sleep(2);

            onMain(player::toggleOnOff);
            gallery(mc, "08-turned-off");
            sleep(3);
            onMain(player::toggleOnOff);
            waitPlaying(mc, "F8 ile yine açıldı");

            onMain(() -> player.play(NEW_SONG));
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 60_000) {
                Message status = player.getStatus();
                if (player.getState() != MusicPlayer.State.LOADING
                        || status != null && status.key().equals("minemusic.status.downloading_progress")) {
                    break;
                }
                Thread.sleep(50);
            }
            log("  indirme anı: " + player.getState() + " " + (player.getStatus() == null ? "-" : player.getStatus().key()));
            onMain(() -> TestCompat.clearOverlays(mc));
            onMain(() -> Screenshot.grab(mc.gameDirectory, "gallery-09-downloading.png", TestCompat.renderTarget(mc), 1, msg -> {
            }));
            log("galeri resmi: gallery-09-downloading.png");
            waitPlaying(mc, "Yeni şarkı indi ve çalıyor");

            // --- Around the world ---
            cmd(server, "execute in minecraft:the_nether run spreadplayers 0 0 0 40 under 90 false @a");
            cmd(server, "effect give @a minecraft:night_vision infinite 0 true");
            sleep(3);
            lookOpen(server);
            playSong(mc, "Lena Raine Pigstep");
            sleep(6);
            gallery(mc, "10-nether");

            // A Wither that does not move (no AI): the song name moves below its health bar.
            cmd(server, "effect clear @a");
            cmd(server, goHome);
            sleep(3);
            lookOpen(server);
            sleep(3);
            cmd(server, "execute as @a at @s run summon minecraft:wither ^ ^2 ^7 {NoAI:1b,PersistenceRequired:1b}");
            cmd(server, "execute as @a at @s run tp @s ~ ~ ~ facing entity @e[type=minecraft:wither,limit=1,sort=nearest] eyes");
            playSong(mc, "C418 Boss");
            sleep(3);
            check(onMainGet(() -> ((BossHealthOverlayAccess) Compat.bossOverlay(mc)).minemusic$events().size()) == 1,
                    "Wither çubuğu görünüyor, şarkı adı onun altına iniyor");
            gallery(mc, "11-boss");
            cmd(server, "kill @e[type=minecraft:wither]");

            // On the main island, 28 blocks south of the exit portal and looking at it
            // (spreadplayers can pick the top of an obsidian pillar, which only shows the sky).
            ServerLevel end = server.getLevel(net.minecraft.world.level.Level.END);
            int endY = onServer(server, () -> {
                end.getChunk(0, 1);
                return end.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 28);
            });
            cmd(server, "execute in minecraft:the_end run tp @a 0.5 " + endY + " 28.5 facing 0 62 0");
            cmd(server, "effect give @a minecraft:night_vision infinite 0 true");
            sleep(5);
            cmd(server, "kill @e[type=minecraft:ender_dragon]");
            cmd(server, "execute in minecraft:the_end run tp @a 0.5 " + endY + " 28.5 facing 0 62 0");
            playSong(mc, "C418 The End");
            sleep(12);
            gallery(mc, "12-end");

            // --- The same screen in Turkish (one of the 36 languages) ---
            cmd(server, "effect clear @a");
            cmd(server, goHome);
            look(server);
            playSong(mc, "C418 Sweden");
            onMain(() -> {
                mc.options.languageCode = "tr_tr";
                mc.getLanguageManager().setSelected("tr_tr");
            });
            CompletableFuture<Void> reload = onMainGet(mc::reloadResourcePacks);
            reload.get(180, TimeUnit.SECONDS);
            sleep(3);
            onMain(() -> Compat.setScreen(mc, new MineMusicScreen(null)));
            sleep(1);
            onMain(() -> find(TestCompat.screen(mc), EditBox.class).setValue("C418 Sweden"));
            sleep(3);
            gallery(mc, "13-turkish");
        } catch (Throwable e) {
            SelfTest.failures++;
            org.slf4j.LoggerFactory.getLogger("minemusic-selftest").error("[SELFTEST] HATA", e);
        } finally {
            player.stop();
            player.deleteList(testList);
            restore(userFiles);
            log(SelfTest.failures == 0 ? "SONUÇ: BAŞARILI" : "SONUÇ: " + SelfTest.failures + " HATA");
            sleep(2);
            mc.execute(mc::stop);
        }
    }

    /**
     * Opens a screen and presses one of its buttons. A click on the game window can close the
     * screen in between, so it is opened again a few times before giving up.
     */
    private static void openAndPress(Minecraft mc, Supplier<Screen> screen, String key) throws Exception {
        for (int i = 0; i < 5; i++) {
            onMain(() -> Compat.setScreen(mc, screen.get()));
            sleep(1);
            Button button = onMainGet(() -> SelfTest.findButton(TestCompat.screen(mc), key));
            if (button != null) {
                onMain(() -> button.onPress(SelfTest.ENTER));
                return;
            }
            log("  '" + key + "' bulunamadı, açık ekran: " + onMainGet(() -> String.valueOf(TestCompat.screen(mc))));
        }
        throw new IllegalStateException("'" + key + "' butonu bulunamadı");
    }

    /** Several named playlists in the picker, and the screen where a new list gets its name. */
    private static void shootLists(Minecraft mc, MusicPlayer player) throws Exception {
        int songs = player.getSelectedList();
        onMain(() -> player.renameList(songs, "My favourites"));
        onMain(() -> player.createList("Fight music"));
        int fight = player.getSelectedList();
        onMain(() -> player.addToPlaylist("C418 Boss"));
        onMain(() -> player.createList("Mining music"));
        int mining = player.getSelectedList();
        for (String song : new String[]{"Lena Raine Ancestry", "C418 Haggstrom", "C418 Aria Math"}) {
            onMain(() -> player.addToPlaylist(song));
        }
        waitFor("liste şarkılarının adları bulundu",
                () -> player.getPlaylist().stream().noneMatch(e -> e.title().equals(e.input())), 120);
        onMain(() -> Compat.setScreen(mc, new PlaylistScreen(new MineMusicScreen(null))));
        sleep(2);
        gallery(mc, "14-playlists");

        openAndPress(mc, () -> new PlaylistScreen(new MineMusicScreen(null)), "minemusic.lists.new");
        sleep(1);
        onMain(() -> find(TestCompat.screen(mc), EditBox.class).setValue("Boss battle"));
        sleep(1);
        gallery(mc, "15-new-list");
        onMain(() -> Compat.setScreen(mc, null));
        onMain(() -> player.deleteList(mining));
        onMain(() -> player.deleteList(fight));
    }

    /** The YouTube results for a song name, then one of them added with "+". */
    private static void shootSearch(Minecraft mc) throws Exception {
        onMain(() -> Compat.setScreen(mc, new MineMusicScreen(null)));
        sleep(1);
        onMain(() -> find(TestCompat.screen(mc), EditBox.class).setValue("C418 Sweden"));
        SelfTest.press(mc, "minemusic.screen.play");
        SelfTest.waitForResults(mc);
        sleep(2);
        parkMouse(mc);
        gallery(mc, "16-search-results");
        Button plus = onMainGet(() -> SelfTest.findLiteral(TestCompat.screen(mc), "+"));
        onMain(() -> plus.onPress(SelfTest.ENTER));
        parkMouse(mc);
        gallery(mc, "17-search-added");
        onMain(() -> Compat.setScreen(mc, null));
    }

    /**
     * Moves the game's idea of the mouse to the top-left corner, so no button is hovered and no
     * tooltip covers the picture (the real cursor stays where it is until someone moves it).
     */
    private static void parkMouse(Minecraft mc) throws Exception {
        onMain(() -> {
            for (String name : new String[]{"xpos", "ypos"}) {
                try {
                    Field field = mc.mouseHandler.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    field.setDouble(mc.mouseHandler, 0);
                } catch (ReflectiveOperationException e) {
                    log("  (fare köşeye alınamadı: " + name + ")");
                }
            }
        });
        sleep(1);
    }

    private static void playSong(Minecraft mc, String song) throws Exception {
        onMain(() -> MusicPlayer.get().play(song));
        waitPlaying(mc, "Çalıyor: " + song);
    }

    /** Scrolls the key list of the Key Binds screen to the end, where the mods' keys are. */
    private static void scrollToBottom(Screen screen) {
        for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (AbstractScrollArea.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        AbstractScrollArea list = (AbstractScrollArea) field.get(screen);
                        list.setScrollAmount(list.maxScrollAmount());
                        return;
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        log("  (tuş listesi bulunamadı)");
    }

    /** A clean screenshot: a stray game menu is closed (except for the pause-menu picture), chat and toasts removed. */
    private static void gallery(Minecraft mc, String name) throws Exception {
        if (!name.equals("01-pause-menu")) {
            onMain(() -> {
                if (TestCompat.screen(mc) instanceof PauseScreen) {
                    Compat.setScreen(mc, null);
                    log("  (açılan oyun menüsü kapatıldı)");
                }
            });
        }
        ensureAlive(mc);
        onMain(() -> TestCompat.clearOverlays(mc));
        sleep(1);
        onMain(() -> Screenshot.grab(mc.gameDirectory, "gallery-" + name + ".png", TestCompat.renderTarget(mc), 1, msg -> {
        }));
        log("galeri resmi: gallery-" + name + ".png");
        sleep(1);
    }

    /** The copy world can be saved with a dead player (a stopped run); the player is respawned then. */
    private static void ensureAlive(Minecraft mc) throws Exception {
        if (onMainGet(() -> mc.player != null && mc.player.isDeadOrDying())) {
            log("  (oyuncu ölüydü, yeniden doğuyor)");
            onMain(() -> {
                mc.player.respawn();
                Compat.setScreen(mc, null);
            });
            waitFor("oyuncu yeniden doğdu", () -> mc.player != null && !mc.player.isDeadOrDying(), 30);
            sleep(2);
            onMain(() -> Compat.setScreen(mc, null));
        }
    }

    /** Runs a command as the server; its answer (or error) goes to the game log, not the chat. */
    private static void cmd(MinecraftServer server, String command) throws Exception {
        server.submit(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command))
                .get(60, TimeUnit.SECONDS);
        log("  /" + command);
    }

    /**
     * Turns to the most open direction (where a solid block is hit last at eye height), so the
     * picture shows the view and not a wall, and looks a little down.
     */
    private static void lookOpen(MinecraftServer server) throws Exception {
        float yaw = onServer(server, () -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            Vec3 eye = p.getEyePosition();
            float best = p.getYRot();
            int bestDistance = -1;
            for (int i = 0; i < 16; i++) {
                float angle = i * 22.5f;
                double dx = -Math.sin(Math.toRadians(angle));
                double dz = Math.cos(Math.toRadians(angle));
                int d = 1;
                while (d < 64 && !level.getBlockState(BlockPos.containing(eye.x + dx * d, eye.y, eye.z + dz * d)).isSolid()) {
                    d++;
                }
                if (d > bestDistance) {
                    bestDistance = d;
                    best = angle;
                }
            }
            return best;
        });
        cmd(server, "execute as @a at @s run tp @s ~ ~ ~ " + yaw + " 8");
    }

    /** Looks straight ahead, a little down. */
    private static void look(MinecraftServer server) throws Exception {
        cmd(server, "execute as @a at @s run tp @s ~ ~ ~ ~ 8");
    }

    /** The top of the overworld at a spot; the chunk is loaded first (unloaded chunks report the bottom of the world). */
    private static int surface(MinecraftServer server, BlockPos pos) throws Exception {
        return onServer(server, () -> {
            ServerLevel level = server.overworld();
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        });
    }

    private static <T> T onServer(MinecraftServer server, Supplier<T> task) throws Exception {
        return server.submit(task).get(180, TimeUnit.SECONDS);
    }
}
