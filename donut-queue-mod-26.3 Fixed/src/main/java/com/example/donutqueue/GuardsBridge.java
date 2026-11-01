package com.example.donutqueue;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/** Public doorway for the mixin (Guards itself is package-private like the rest of the mod's helpers). */
public final class GuardsBridge {
    private GuardsBridge() {}

    public static boolean auctionClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button, ContainerInput type) {
        try {
            return Guards.guardAuctionClick(screen, slot, slotId, button, type);
        } catch (Throwable t) {
            System.err.println("[DonutQueue] Auction Guard error (click let through): " + t);
            return false;
        }
    }
}
