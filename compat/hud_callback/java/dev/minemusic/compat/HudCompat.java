package dev.minemusic.compat;

import dev.minemusic.hud.NowPlayingHud;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Minecraft 1.21.x: the HUD is drawn with GuiGraphics through Fabric's HudRenderCallback. */
public final class HudCompat {
    private HudCompat() {
    }

    @SuppressWarnings("deprecation") // still works on 1.21.6+, and is the one API all 1.21 versions share
    public static void register() {
        HudRenderCallback.EVENT.register((graphics, delta) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (!minecraft.options.hideGui) {
                NowPlayingHud.render(painter(graphics, minecraft));
            }
        });
    }

    private static NowPlayingHud.Painter painter(GuiGraphics graphics, Minecraft minecraft) {
        return new NowPlayingHud.Painter() {
            @Override
            public void fill(int x1, int y1, int x2, int y2, int argb) {
                graphics.fill(x1, y1, x2, y2, argb);
            }

            @Override
            public void text(String text, int x, int y, int argb) {
                graphics.drawString(minecraft.font, text, x, y, argb, true);
            }
        };
    }
}
