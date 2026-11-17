package dev.minemusic.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 1.21.x – 26.1: screens are opened through Minecraft itself, the boss bars live on Gui. */
public final class Compat {
    private Compat() {
    }

    public static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.setScreen(screen);
    }

    public static Screen screen(Minecraft minecraft) {
        return minecraft.screen;
    }

    public static BossHealthOverlay bossOverlay(Minecraft minecraft) {
        return minecraft.gui.getBossOverlay();
    }
}
