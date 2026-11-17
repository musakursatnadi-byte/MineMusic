package dev.minemusic.screen;

import dev.minemusic.audio.MusicPlayer;
import dev.minemusic.audio.Playlist;
import dev.minemusic.compat.Compat;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shows the playlist a page at a time: click a song to play it, move it up/down or remove it. */
public final class PlaylistScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int TITLE_WIDTH = 240;
    // The list picker row is as wide as a song row: 240 + 3 × (2 + 20) = 306 = 20 + 2 + 262 + 2 + 20.
    private static final int PICKER_WIDTH = 262;

    private final Screen parent;
    private final MusicPlayer player = MusicPlayer.get();
    private HeaderAndFooterLayout layout;
    private int page;
    // What the page was built from; when the player changes it (a title arrives, the next song starts) the page is rebuilt.
    private List<Playlist.Entry> shownEntries;
    private int shownIndex;
    private boolean shownShuffle;
    private int shownList;
    private List<Integer> shownLists;

    public PlaylistScreen(Screen parent) {
        super(Component.translatable("minemusic.playlist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int previousList = shownList;
        shownEntries = player.getPlaylist();
        shownIndex = player.getPlaylistIndex();
        shownShuffle = player.isShuffle();
        shownList = player.getSelectedList();
        shownLists = player.getListIds();
        if (shownList != previousList) {
            page = 0;
        }

        layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);
        LinearLayout body = layout.addToContents(LinearLayout.vertical().spacing(4));
        body.defaultCellSetting().alignHorizontallyCenter();

        // ◀ List name (2/3) ▶ — the arrows (or a click on the name) switch between the lists.
        int position = shownLists.indexOf(shownList);
        boolean several = shownLists.size() > 1;
        LinearLayout picker = body.addChild(LinearLayout.horizontal().spacing(2));
        Button left = picker.addChild(Button.builder(Component.literal("◀"), b -> switchList(-1)).width(20).build());
        left.active = several;
        String listLabel = listName(shownList) + (several ? " (" + (position + 1) + "/" + shownLists.size() + ")" : "");
        Button name = picker.addChild(Button.builder(Component.literal(Texts.fit(font, listLabel, PICKER_WIDTH - 12)), b -> switchList(1))
                .width(PICKER_WIDTH).build());
        name.active = several;
        Button right = picker.addChild(Button.builder(Component.literal("▶"), b -> switchList(1)).width(20).build());
        right.active = several;

        int perPage = rowsPerPage();
        int pages = Math.max(1, (shownEntries.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));

        if (shownEntries.isEmpty()) {
            body.addChild(new StringWidget(Component.translatable("minemusic.playlist.empty"), font));
        }
        int first = page * perPage;
        for (int i = first; i < Math.min(first + perPage, shownEntries.size()); i++) {
            int index = i;
            Playlist.Entry entry = shownEntries.get(i);
            String label = (i == shownIndex ? "♪ " : (i + 1) + ". ") + entry.title();
            LinearLayout row = body.addChild(LinearLayout.horizontal().spacing(2));
            row.addChild(Button.builder(Component.literal(Texts.fit(font, label, TITLE_WIDTH - 12)), b -> player.playEntry(index))
                    .width(TITLE_WIDTH).build());
            Button up = row.addChild(Button.builder(Component.literal("▲"), b -> player.moveInPlaylist(index, -1)).width(20).build());
            up.active = i > 0;
            Button down = row.addChild(Button.builder(Component.literal("▼"), b -> player.moveInPlaylist(index, 1)).width(20).build());
            down.active = i < shownEntries.size() - 1;
            row.addChild(Button.builder(Component.literal("X"), b -> player.removeFromPlaylist(index)).width(20).build());
        }

        if (pages > 1) {
            LinearLayout nav = body.addChild(LinearLayout.horizontal().spacing(4));
            Button back = nav.addChild(Button.builder(Component.translatable("minemusic.playlist.previous_page"), b -> turnPage(-1)).width(100).build());
            back.active = page > 0;
            Button pageLabel = nav.addChild(Button.builder(
                    Component.translatable("minemusic.playlist.page", Integer.toString(page + 1), Integer.toString(pages)), b -> {
                    }).width(100).build());
            pageLabel.active = false;
            Button forward = nav.addChild(Button.builder(Component.translatable("minemusic.playlist.next_page"), b -> turnPage(1)).width(100).build());
            forward.active = page < pages - 1;
        }

        LinearLayout actions = body.addChild(LinearLayout.horizontal().spacing(4));
        Button playAll = actions.addChild(Button.builder(Component.translatable("minemusic.playlist.play_all"), b -> player.playAll()).width(100).build());
        playAll.active = !shownEntries.isEmpty();
        actions.addChild(Button.builder(Texts.onOff("minemusic.playlist.shuffle", shownShuffle), b -> {
            player.toggleShuffle();
            rebuildWidgets();
        }).width(100).build());
        Button clear = actions.addChild(Button.builder(Component.translatable("minemusic.playlist.clear"), b -> confirmClear()).width(100).build());
        clear.active = !shownEntries.isEmpty();

        LinearLayout listActions = body.addChild(LinearLayout.horizontal().spacing(4));
        listActions.addChild(Button.builder(Component.translatable("minemusic.lists.new"), b -> newList()).width(100).build());
        listActions.addChild(Button.builder(Component.translatable("minemusic.lists.rename"), b -> renameList()).width(100).build());
        Button delete = listActions.addChild(Button.builder(Component.translatable("minemusic.lists.delete"), b -> confirmDelete()).width(100).build());
        delete.active = several;

        layout.addToFooter(Button.builder(Component.translatable("gui.back"), b -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void tick() {
        super.tick();
        if (!player.getPlaylist().equals(shownEntries) || player.getPlaylistIndex() != shownIndex
                || player.getSelectedList() != shownList || !player.getListIds().equals(shownLists)) {
            rebuildWidgets();
        }
    }

    @Override
    public void onClose() {
        Compat.setScreen(minecraft, parent);
    }

    private int rowsPerPage() {
        // Header and footer take 33 pixels each; the list picker, the page buttons and the two
        // action rows need room too.
        int room = height - 66 - 4 * ROW_HEIGHT - 8;
        return Math.max(1, room / ROW_HEIGHT);
    }

    /** "List 3" for a list without a name. */
    private String listName(int id) {
        String name = player.getListName(id);
        return name.isEmpty() ? Component.translatable("minemusic.lists.default", Integer.toString(id)).getString() : name;
    }

    private void switchList(int delta) {
        int position = shownLists.indexOf(shownList);
        int next = Math.floorMod(position + delta, shownLists.size());
        player.selectList(shownLists.get(next));
        rebuildWidgets();
    }

    private void newList() {
        Compat.setScreen(minecraft, new ListNameScreen(this, Component.translatable("minemusic.lists.new_title"), "",
                name -> player.createList(name)));
    }

    private void renameList() {
        int id = shownList;
        Compat.setScreen(minecraft, new ListNameScreen(this, Component.translatable("minemusic.lists.rename_title"),
                player.getListName(id), name -> player.renameList(id, name)));
    }

    private void confirmDelete() {
        int id = shownList;
        Compat.setScreen(minecraft, new ConfirmScreen(yes -> {
            if (yes) {
                player.deleteList(id);
            }
            Compat.setScreen(minecraft, this);
        }, Component.translatable("minemusic.lists.delete_title"), Component.translatable("minemusic.lists.delete_message", listName(id))));
    }

    private void turnPage(int delta) {
        page += delta;
        rebuildWidgets();
    }

    private void confirmClear() {
        Compat.setScreen(minecraft, new ConfirmScreen(yes -> {
            if (yes) {
                player.clearPlaylist();
            }
            Compat.setScreen(minecraft, this);
        }, Component.translatable("minemusic.playlist.clear_title"), Component.translatable("minemusic.playlist.clear_message")));
    }
}
