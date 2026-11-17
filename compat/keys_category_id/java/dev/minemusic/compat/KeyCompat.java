package dev.minemusic.compat;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Minecraft 1.21.11: ResourceLocation was renamed to Identifier. */
public final class KeyCompat {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("minemusic", "main"));

    private KeyCompat() {
    }

    public static KeyMapping register(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, key, CATEGORY));
    }
}
