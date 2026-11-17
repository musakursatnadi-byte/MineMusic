package dev.minemusic.mixin;

import dev.minemusic.compat.Compat;
import dev.minemusic.screen.MineMusicScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @ModifyArg(
            method = "createPauseMenu()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;ILnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;", ordinal = 0),
            index = 0
    )
    private LayoutElement minemusic$addPauseButton(LayoutElement returnToGame) {
        // This full-width row is shared by singleplayer and multiplayer.
        // Preserve the original button; the grid accounts for the extra row,
        // server-specific buttons and window resizing.
        LinearLayout entry = LinearLayout.vertical().spacing(4);
        entry.addChild(returnToGame);
        entry.addChild(Button.builder(Component.translatable("minemusic.button.open"),
                button -> Compat.setScreen(minecraft, new MineMusicScreen(this)))
                .width(returnToGame.getWidth()).build());
        return entry;
    }
}
