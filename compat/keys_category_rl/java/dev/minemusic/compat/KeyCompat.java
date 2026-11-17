package dev.minemusic.compat;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;

/** Minecraft 1.21.9 – 1.21.10: key categories are registered objects named by a ResourceLocation. */
public final class KeyCompat {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("minemusic", "main"));

    private KeyCompat() {
    }

    public static KeyMapping register(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, key, CATEGORY));
    }
}
