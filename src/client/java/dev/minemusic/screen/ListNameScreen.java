package dev.minemusic.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minemusic.compat.Compat;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Asks for a playlist's name (for a new list or a rename). Enter or Done saves, Cancel goes back. */
public final class ListNameScreen extends Screen {
    // From Minecraft itself: the numbers changed in 26.3.
    private static final int KEY_ENTER = InputConstants.KEY_RETURN;
    private static final int KEY_KEYPAD_ENTER = InputConstants.KEY_NUMPADENTER;
    private static final int MAX_LENGTH = 32;

    private final Screen parent;
    private final String initial;
    private final Consumer<String> onDone;
    private HeaderAndFooterLayout layout;
    private EditBox nameBox;

    public ListNameScreen(Screen parent, Component title, String initial, Consumer<String> onDone) {
        super(title);
        this.parent = parent;
        this.initial = initial;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        String typed = nameBox != null ? nameBox.getValue() : initial;
        layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);
        LinearLayout body = layout.addToContents(LinearLayout.vertical().spacing(4));
        body.defaultCellSetting().alignHorizontallyCenter();
        body.addChild(new StringWidget(Component.translatable("minemusic.lists.name_label"), font));
        nameBox = new EditBox(font, 200, 20, Component.translatable("minemusic.lists.name_label"));
        nameBox.setMaxLength(MAX_LENGTH);
        nameBox.setValue(typed);
        body.addChild(nameBox);

        LinearLayout buttons = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        buttons.addChild(Button.builder(Component.translatable("gui.done"), b -> done()).width(150).build());
        buttons.addChild(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).width(150).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        setInitialFocus(nameBox);
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == KEY_ENTER || event.key() == KEY_KEYPAD_ENTER) {
            done();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Compat.setScreen(minecraft, parent);
    }

    private void done() {
        onDone.accept(nameBox.getValue());
        Compat.setScreen(minecraft, parent);
    }
}
