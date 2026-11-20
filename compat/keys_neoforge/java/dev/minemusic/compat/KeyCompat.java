package dev.minemusic.compat;

import dev.minemusic.loader.NeoForgeEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** NeoForge (Minecraft 26.x): key mappings are handed to NeoForge when it asks for them. */
public final class KeyCompat {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("minemusic", "main"));
    private static final List<KeyMapping> MAPPINGS = new ArrayList<>();

    static {
        NeoForgeEntry.modBus().addListener((RegisterKeyMappingsEvent event) -> {
            event.registerCategory(CATEGORY);
            MAPPINGS.forEach(event::register);
        });
    }

    private KeyCompat() {
    }

    public static KeyMapping register(String name, int key) {
        KeyMapping mapping = new KeyMapping(name, key, CATEGORY);
        MAPPINGS.add(mapping);
        return mapping;
    }
}
