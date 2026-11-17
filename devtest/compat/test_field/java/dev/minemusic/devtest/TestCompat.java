package dev.minemusic.devtest;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Self-test helpers for Minecraft 1.21.9 – 26.1.x: the open screen is a field on Minecraft. */
final class TestCompat {
    private TestCompat() {
    }

    static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    static RenderTarget renderTarget(Minecraft mc) {
        return mc.getMainRenderTarget();
    }

    /** Removes chat lines and toasts (advancements and so on) so screenshots stay clean. */
    static void clearOverlays(Minecraft mc) {
        mc.gui.getChat().clearMessages(false);
        mc.getToastManager().clear();
    }
}
