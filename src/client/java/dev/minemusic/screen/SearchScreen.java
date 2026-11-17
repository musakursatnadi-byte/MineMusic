package dev.minemusic.screen;

import dev.minemusic.audio.Message;
import dev.minemusic.audio.MusicPlayer;
import dev.minemusic.compat.Compat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The first YouTube results for a song name, so the right song can be picked instead of just the
 * first one: "▶ title" plays it, "+" adds it to the playlist.
 */
public final class SearchScreen extends Screen {
    private static final int ROW_WIDTH = 308;
    // A result row is as wide as the rows of the MineMusic screen: 284 + 4 + 20 = 308.
    private static final int TITLE_WIDTH = 284;
    private static final int FLASH_TICKS = 60;

    private final Screen parent;
    private final MusicPlayer player = MusicPlayer.get();
    private final MusicPlayer.Search search;
    // Links added to the playlist from this screen; their "+" turns into a tick.
    private final Set<String> added = new HashSet<>();
    private HeaderAndFooterLayout layout;
    private StringWidget statusText;
    private boolean showingResults;
    private Component flash;
    private int flashTicks;

    public SearchScreen(Screen parent, String query) {
        super(Component.translatable("minemusic.search.title"));
        this.parent = parent;
        this.search = player.search(query);
    }

    @Override
    protected void init() {
        layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);
        LinearLayout body = layout.addToContents(LinearLayout.vertical().spacing(4));
        body.defaultCellSetting().alignHorizontallyCenter();

        String query = Component.translatable("minemusic.search.query", search.query()).getString();
        body.addChild(new StringWidget(Component.literal(Texts.fit(font, query, ROW_WIDTH)).withStyle(ChatFormatting.GRAY), font));

        List<MusicPlayer.SearchResult> results = search.results();
        showingResults = results != null;
        Button first = null;
        if (results != null) {
            for (MusicPlayer.SearchResult result : results) {
                LinearLayout row = body.addChild(LinearLayout.horizontal().spacing(4));
                Button play = row.addChild(Button.builder(resultLabel(result), b -> play(result))
                        .width(TITLE_WIDTH)
                        .tooltip(Tooltip.create(Component.literal(result.title())))
                        .build());
                boolean done = added.contains(result.link());
                Button add = row.addChild(Button.builder(Component.literal(done ? "✔" : "+"), b -> add(result))
                        .width(20)
                        .tooltip(Tooltip.create(Component.translatable("minemusic.search.add_tooltip")))
                        .build());
                add.active = !done;
                if (first == null) {
                    first = play;
                }
            }
        }

        statusText = body.addChild(new StringWidget(ROW_WIDTH, 9, Component.empty(), font));

        layout.addToFooter(Button.builder(Component.translatable("gui.back"), b -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        refresh();
        if (first != null) {
            // Enter plays the first result, like the Play button did before there were results to pick from.
            setInitialFocus(first);
        }
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
        if (!showingResults && search.results() != null) {
            rebuildWidgets();
        }
        refresh();
    }

    @Override
    public void onClose() {
        search.cancel();
        Compat.setScreen(minecraft, parent);
    }

    private void play(MusicPlayer.SearchResult result) {
        player.play(result.link());
        // Back to the MineMusic screen, which shows the song loading.
        onClose();
    }

    private void add(MusicPlayer.SearchResult result) {
        if (player.addToPlaylist(result.link(), result.title())) {
            added.add(result.link());
            flash = Component.translatable("minemusic.screen.added", result.title()).withStyle(ChatFormatting.AQUA);
        } else {
            flash = Component.translatable("minemusic.screen.add_failed").withStyle(ChatFormatting.RED);
        }
        flashTicks = FLASH_TICKS;
        rebuildWidgets();
    }

    /** "▶ Title  3:36", the title cut so the length always fits. */
    private Component resultLabel(MusicPlayer.SearchResult result) {
        String time = result.durationSeconds() > 0 ? "  " + formatTime(result.durationSeconds()) : "";
        String name = Texts.fit(font, "▶ " + result.title(), TITLE_WIDTH - 12 - font.width(time));
        return Component.literal(name).append(Component.literal(time).withStyle(ChatFormatting.GRAY));
    }

    private void refresh() {
        Component line;
        List<MusicPlayer.SearchResult> results = search.results();
        Message error = search.error();
        if (flashTicks > 0 && flash != null) {
            line = flash;
        } else if (error != null) {
            line = Texts.of(error).withStyle(ChatFormatting.RED);
        } else if (results == null) {
            line = Texts.of(search.status()).withStyle(ChatFormatting.YELLOW);
        } else if (results.isEmpty()) {
            line = Component.translatable("minemusic.search.none").withStyle(ChatFormatting.GRAY);
        } else {
            line = Component.translatable("minemusic.search.hint").withStyle(ChatFormatting.GRAY);
        }
        String text = Texts.fit(font, line.getString(), ROW_WIDTH);
        Component message = Component.literal(text).withStyle(line.getStyle());
        if (!message.equals(statusText.getMessage())) {
            statusText.setMessage(message);
            statusText.setWidth(font.width(text));
            layout.arrangeElements();
        }
    }

    /** 216 → "3:36", 3665 → "1:01:05". */
    static String formatTime(int seconds) {
        int h = seconds / 3600;
        int m = seconds / 60 % 60;
        int s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
