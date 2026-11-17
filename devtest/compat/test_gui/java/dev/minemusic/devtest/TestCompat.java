package dev.minemusic.devtest;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Self-test helpers for Minecraft 26.2+: the open screen lives on Gui, the render target on GameRenderer. */
final class TestCompat {
    private TestCompat() {
    }

    static Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    static RenderTarget renderTarget(Minecraft mc) {
        return mc.gameRenderer.mainRenderTarget();
    }

    /** Removes chat lines and toasts (advancements and so on) so screenshots stay clean. */
    static void clearOverlays(Minecraft mc) {
        mc.gui.hud.getChat().clearMessages(false);
        mc.gui.toastManager().clear();
    }
}
