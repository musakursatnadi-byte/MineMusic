package dev.minemusic.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 26.2+: the open screen moved from Minecraft to Gui, and the boss bars to Gui's Hud. */
public final class Compat {
    private Compat() {
    }

    public static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.gui.setScreen(screen);
    }

    public static Screen screen(Minecraft minecraft) {
        return minecraft.gui.screen();
    }

    public static BossHealthOverlay bossOverlay(Minecraft minecraft) {
        return minecraft.gui.hud.getBossOverlay();
    }
}
