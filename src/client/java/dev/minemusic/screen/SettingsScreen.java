package dev.minemusic.screen;

import dev.minemusic.audio.MusicPlayer;
import dev.minemusic.compat.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final MusicPlayer player = MusicPlayer.get();
    private HeaderAndFooterLayout layout;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("minemusic.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);
        LinearLayout body = layout.addToContents(LinearLayout.vertical().spacing(4));
        body.defaultCellSetting().alignHorizontallyCenter();

        body.addChild(Button.builder(Texts.onOff("minemusic.settings.mute_music", player.isMuteGameMusic()), b -> {
            player.toggleMuteGameMusic();
            b.setMessage(Texts.onOff("minemusic.settings.mute_music", player.isMuteGameMusic()));
        }).width(260).build());
        body.addChild(Button.builder(Texts.onOff("minemusic.settings.show_hud", player.isShowHud()), b -> {
            player.toggleShowHud();
            b.setMessage(Texts.onOff("minemusic.settings.show_hud", player.isShowHud()));
        }).width(260).build());
        body.addChild(new StringWidget(Component.translatable("minemusic.settings.keys_hint").withStyle(ChatFormatting.GRAY), font));

        layout.addToFooter(Button.builder(Component.translatable("gui.back"), b -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        Compat.setScreen(minecraft, parent);
    }
}
