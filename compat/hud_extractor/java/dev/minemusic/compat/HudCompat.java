package dev.minemusic.compat;

import dev.minemusic.hud.NowPlayingHud;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Minecraft 26.x: HUD elements fill a GuiGraphicsExtractor instead of drawing with GuiGraphics. */
public final class HudCompat {
    private HudCompat() {
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("minemusic", "now_playing"),
                (extractor, delta) -> NowPlayingHud.render(painter(extractor, Minecraft.getInstance())));
    }

    private static NowPlayingHud.Painter painter(GuiGraphicsExtractor extractor, Minecraft minecraft) {
        return new NowPlayingHud.Painter() {
            @Override
            public void fill(int x1, int y1, int x2, int y2, int argb) {
                extractor.fill(x1, y1, x2, y2, argb);
            }

            @Override
            public void text(String text, int x, int y, int argb) {
                extractor.text(minecraft.font, text, x, y, argb, true);
            }
        };
    }
}
