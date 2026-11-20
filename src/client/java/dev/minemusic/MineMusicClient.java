package dev.minemusic;

import dev.minemusic.audio.MusicPlayer;
import dev.minemusic.compat.Compat;
import dev.minemusic.compat.HudCompat;
import dev.minemusic.keys.MineMusicKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The start of the mod, the same for every mod loader. The loader's own entry point
 * (loader/fabric or loader/forge) calls {@link #init()} once and {@link #endTick} after every
 * client tick.
 */
public final class MineMusicClient {
    public static final String MOD_ID = "minemusic";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean inWorld;

    private MineMusicClient() {
    }

    public static void init() {
        // The game's master volume slider turns MineMusic down too.
        MusicPlayer.get().setGameVolume(() -> Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER));
        MineMusicKeys.register();
        HudCompat.register();
        LOGGER.info("MineMusic client initialized.");
        startSelfTestIfPresent();
    }

    public static void endTick(Minecraft minecraft) {
        MineMusicKeys.tick(minecraft);
        turnOffAfterLeaving(minecraft);
    }

    /**
     * Leaving a world or a server turns the music off like F8 (the title screen has no MineMusic
     * button, so it could not be stopped there). F8 in the next world goes on where it stopped.
     * Only the screens a player lands on after leaving count, so switching servers inside a
     * network (which shows loading screens in between) keeps the music playing.
     */
    private static void turnOffAfterLeaving(Minecraft minecraft) {
        if (minecraft.level != null) {
            inWorld = true;
            return;
        }
        Screen screen = Compat.screen(minecraft);
        boolean left = screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen || screen instanceof DisconnectedScreen;
        if (!inWorld || !left) {
            return;
        }
        inWorld = false;
        MusicPlayer player = MusicPlayer.get();
        MusicPlayer.State state = player.getState();
        if (state == MusicPlayer.State.PLAYING || state == MusicPlayer.State.PAUSED || state == MusicPlayer.State.LOADING) {
            player.toggleOnOff();
            LOGGER.info("Left the world: MineMusic turned off (F8 goes on where it stopped).");
        }
    }

    /** The automatic test in devtest/ is only compiled in for -Pselftest dev runs; release jars skip this. */
    private static void startSelfTestIfPresent() {
        try {
            Class.forName("dev.minemusic.devtest.SelfTest").getMethod("start").invoke(null);
        } catch (ClassNotFoundException e) {
            // Normal game: no self-test.
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("MineMusic self-test could not start", e);
        }
    }
}
