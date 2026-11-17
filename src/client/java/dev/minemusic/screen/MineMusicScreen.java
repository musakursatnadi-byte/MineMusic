package dev.minemusic.screen;

import dev.minemusic.audio.Message;
import dev.minemusic.audio.MusicPlayer;
import dev.minemusic.compat.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MineMusicScreen extends Screen {
    private static final int ROW_WIDTH = 308;
    private static final int FLASH_TICKS = 60;

    private final Screen parent;
    private final MusicPlayer player = MusicPlayer.get();
    private HeaderAndFooterLayout layout;
    private EditBox linkBox;
    private Button pauseButton;
    private Button previousButton;
    private Button nextButton;
    private Button repeatButton;
    private Button playlistButton;
    private StringWidget statusText;
    // Keeps what was typed when the window is resized (init() runs again then).
    private String typedLink;
    // A short note (like "added to the playlist") that replaces the status line for a moment.
    private Component flash;
    private int flashTicks;

    public MineMusicScreen(Screen parent) {
        super(Component.translatable("minemusic.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (linkBox != null) {
            typedLink = linkBox.getValue();
        }
        layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);

        LinearLayout body = layout.addToContents(LinearLayout.vertical().spacing(4));
        body.defaultCellSetting().alignHorizontallyCenter();

        // Minecraft hides an EditBox hint while the box is focused, so the label says what goes in it.
        body.addChild(new StringWidget(Component.translatable("minemusic.screen.link_label"), font));
        linkBox = new EditBox(font, ROW_WIDTH, 20, Component.translatable("minemusic.screen.link_label"));
        linkBox.setMaxLength(1000);
        linkBox.setHint(Component.translatable("minemusic.screen.link_hint").withStyle(ChatFormatting.GRAY));
        linkBox.setValue(typedLink != null ? typedLink : player.getLastUrl());
        body.addChild(linkBox);

        LinearLayout controls = body.addChild(LinearLayout.horizontal().spacing(4));
        controls.addChild(button("minemusic.screen.play", 100, b -> player.play(linkBox.getValue())));
        pauseButton = controls.addChild(Button.builder(pauseLabel(), b -> player.togglePause()).width(100).build());
        controls.addChild(button("minemusic.screen.stop", 100, b -> player.stop()));

        LinearLayout list = body.addChild(LinearLayout.horizontal().spacing(4));
        previousButton = list.addChild(button("minemusic.screen.previous", 100, b -> player.previous()));
        list.addChild(button("minemusic.screen.add", 100, b -> addToPlaylist()));
        nextButton = list.addChild(button("minemusic.screen.next", 100, b -> player.next()));

        LinearLayout options = body.addChild(LinearLayout.horizontal().spacing(4));
        options.addChild(new VolumeSlider(204));
        repeatButton = options.addChild(Button.builder(repeatLabel(), b -> player.cycleRepeat()).width(100).build());

        LinearLayout more = body.addChild(LinearLayout.horizontal().spacing(4));
        playlistButton = more.addChild(Button.builder(playlistLabel(), b -> Compat.setScreen(minecraft, new PlaylistScreen(this))).width(152).build());
        more.addChild(button("minemusic.screen.settings", 152, b -> Compat.setScreen(minecraft, new SettingsScreen(this))));

        statusText = body.addChild(new StringWidget(ROW_WIDTH, 9, Component.empty(), font));

        layout.addToFooter(Button.builder(Component.translatable("gui.back"), b -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        refresh();
        setInitialFocus(linkBox);
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void tick() {
        super.tick();
        if (flashTicks > 0) {
            flashTicks--;
        }
        refresh();
    }

    @Override
    public void onClose() {
        Compat.setScreen(minecraft, parent);
    }

    private void addToPlaylist() {
        String input = linkBox.getValue().trim();
        if (player.addToPlaylist(input)) {
            flash = Component.translatable("minemusic.screen.added", input).withStyle(ChatFormatting.AQUA);
        } else {
            flash = Component.translatable("minemusic.screen.add_failed").withStyle(ChatFormatting.RED);
        }
        flashTicks = FLASH_TICKS;
        refresh();
    }

    /** The music runs on its own thread, so the labels are refreshed every tick. */
    private void refresh() {
        MusicPlayer.State state = player.getState();
        pauseButton.setMessage(pauseLabel());
        pauseButton.active = state == MusicPlayer.State.PLAYING || state == MusicPlayer.State.PAUSED;
        boolean hasPlaylist = !player.getPlaylist().isEmpty();
        previousButton.active = hasPlaylist;
        nextButton.active = hasPlaylist;
        repeatButton.setMessage(repeatLabel());
        playlistButton.setMessage(playlistLabel());

        Component line;
        if (flashTicks > 0 && flash != null) {
            line = flash;
        } else {
            Message status = player.getStatus();
            ChatFormatting color = switch (state) {
                case ERROR -> ChatFormatting.RED;
                case PLAYING -> ChatFormatting.GREEN;
                case LOADING, PAUSED -> ChatFormatting.YELLOW;
                case IDLE -> ChatFormatting.GRAY;
            };
            line = (status != null ? Texts.of(status) : Component.translatable("minemusic.status.idle_hint")).withStyle(color);
        }
        String text = Texts.fit(font, line.getString(), ROW_WIDTH);
        Component message = Component.literal(text).withStyle(line.getStyle());
        if (!message.equals(statusText.getMessage())) {
            // Sizing the widget to the text lets the layout center it the same way on every version.
            statusText.setMessage(message);
            statusText.setWidth(font.width(text));
            layout.arrangeElements();
        }
    }

    private Button button(String key, int width, Button.OnPress onPress) {
        return Button.builder(Component.translatable(key), onPress).width(width).build();
    }

    private Component pauseLabel() {
        return Component.translatable(player.getState() == MusicPlayer.State.PAUSED ? "minemusic.screen.resume" : "minemusic.screen.pause");
    }

    private Component repeatLabel() {
        String mode = switch (player.getRepeat()) {
            case OFF -> "minemusic.repeat.off";
            case ONE -> "minemusic.repeat.one";
            case ALL -> "minemusic.repeat.all";
        };
        return Component.translatable("minemusic.screen.repeat", Component.translatable(mode));
    }

    private Component playlistLabel() {
        return Component.translatable("minemusic.screen.playlist", Integer.toString(player.getPlaylist().size()));
    }

    private final class VolumeSlider extends AbstractSliderButton {
        VolumeSlider(int width) {
            super(0, 0, width, 20, Component.empty(), player.getVolume() / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("minemusic.screen.volume", Long.toString(Math.round(value * 100))));
        }

        @Override
        protected void applyValue() {
            player.changeVolume((int) Math.round(value * 100) - player.getVolume());
        }
    }
}
