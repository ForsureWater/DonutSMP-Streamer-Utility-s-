package com.example.donutqueue;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.ArrayList;
import java.util.List;

/** Finds the addons other mods registered under {@link DonutQueueAddon#ENTRYPOINT}. */
final class AddonRegistry {

    private static List<DonutQueueAddon> cache;

    private AddonRegistry() { }

    /** All installed addons, loaded once. A broken addon is skipped, never fatal. */
    static synchronized List<DonutQueueAddon> all() {
        if (cache != null) return cache;

        List<DonutQueueAddon> out = new ArrayList<>();
        try {
            for (EntrypointContainer<DonutQueueAddon> c
                    : FabricLoader.getInstance().getEntrypointContainers(DonutQueueAddon.ENTRYPOINT, DonutQueueAddon.class)) {
                try {
                    out.add(c.getEntrypoint());
                } catch (Throwable t) {
                    System.err.println("[DonutQueue] Addon from " + c.getProvider().getMetadata().getId()
                            + " could not be loaded: " + t);
                }
            }
        } catch (Throwable t) {
            System.err.println("[DonutQueue] Could not look for addons: " + t);
        }

        cache = out;
        return cache;
    }
}
