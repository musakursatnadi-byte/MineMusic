package dev.minemusic.keys;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minemusic.audio.MusicPlayer;
import dev.minemusic.compat.Compat;
import dev.minemusic.compat.KeyCompat;
import dev.minemusic.screen.MineMusicScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Shortcut keys; players can change them in Options > Controls > Key Binds > MineMusic. */
public final class MineMusicKeys {
    // Minecraft's own key numbers: they changed in 26.3 (F10 was 299, now 67) and "no key" is not
    // always -1, so the numbers must come from the Minecraft version the jar is built for.
    private static final int KEY_F8 = InputConstants.KEY_F8;
    private static final int KEY_F9 = InputConstants.KEY_F9;
    private static final int KEY_F10 = InputConstants.KEY_F10;
    private static final int UNBOUND = InputConstants.UNKNOWN.getValue();

    private static KeyMapping open;
    private static KeyMapping pause;
    private static KeyMapping onOff;
    private static KeyMapping next;
    private static KeyMapping previous;

    private MineMusicKeys() {
    }

    public static void register() {
        open = KeyCompat.register("key.minemusic.open", KEY_F10);
        pause = KeyCompat.register("key.minemusic.pause", KEY_F9);
        onOff = KeyCompat.register("key.minemusic.toggle", KEY_F8);
        next = KeyCompat.register("key.minemusic.next", UNBOUND);
        previous = KeyCompat.register("key.minemusic.previous", UNBOUND);
        ClientTickEvents.END_CLIENT_TICK.register(MineMusicKeys::tick);
    }

    private static boolean checkedSavedKeys;

    /**
     * MineMusic up to 1.1.0 saved key numbers that do not exist on Minecraft 26.3 (written as
     * "key.keyboard.299" or "key.keyboard.-1" in options.txt); "-1" even crashed the game when a
     * world was joined. Such a saved key is put back to its default once, on the title screen,
     * before any world is joined.
     */
    private static void repairSavedKeys(Minecraft minecraft) {
        boolean changed = false;
        for (KeyMapping mapping : new KeyMapping[]{open, pause, onOff, next, previous}) {
            if (mapping.saveString().matches("key\\.keyboard\\.-?\\d+")) {
                mapping.setKey(mapping.getDefaultKey());
                changed = true;
            }
        }
        if (changed) {
            KeyMapping.resetMapping();
            minecraft.options.save();
        }
    }

    private static void tick(Minecraft minecraft) {
        if (!checkedSavedKeys) {
            checkedSavedKeys = true;
            repairSavedKeys(minecraft);
        }
        MusicPlayer player = MusicPlayer.get();
        while (open.consumeClick()) {
            Compat.setScreen(minecraft, new MineMusicScreen(null));
        }
        while (pause.consumeClick()) {
            MusicPlayer.State state = player.getState();
            if (state == MusicPlayer.State.PLAYING || state == MusicPlayer.State.PAUSED) {
                player.togglePause();
            } else if (!player.getLastUrl().isEmpty()) {
                // Nothing is playing: the same key starts the last song again.
                player.play(player.getLastUrl());
            }
        }
        while (onOff.consumeClick()) {
            player.toggleOnOff();
        }
        while (next.consumeClick()) {
            player.next();
        }
        while (previous.consumeClick()) {
            player.previous();
        }
    }
}
