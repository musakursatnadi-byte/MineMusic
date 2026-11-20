package dev.minemusic.compat;

import dev.minemusic.hud.NowPlayingHud;
import dev.minemusic.loader.NeoForgeEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** NeoForge (Minecraft 26.x): the song-name line is one more HUD layer, drawn above all the others. */
public final class HudCompat {
    private HudCompat() {
    }

    public static void register() {
        NeoForgeEntry.modBus().addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath("minemusic", "now_playing"),
                (extractor, delta) -> NowPlayingHud.render(painter(extractor, Minecraft.getInstance()))));
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
