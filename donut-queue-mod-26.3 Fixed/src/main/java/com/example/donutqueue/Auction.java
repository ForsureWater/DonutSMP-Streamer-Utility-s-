package com.example.donutqueue;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Starting / stopping an auction round and small item helpers shared by the bidding menu, the command and the HUD box. */
final class Auction {
    private Auction() {}

    /** Starts a fresh round for the given item. Whoever pays the most before time runs out wins. */
    static void start(String itemId, int count, long seconds, double minimum) {
        ModConfig c = DonutQueueClient.config;
        c.biddingActive = true;
        c.biddingFinished = false;
        c.biddingTotalDurationMillis = seconds * 1000L;
        c.biddingEndTimeMillis = System.currentTimeMillis() + c.biddingTotalDurationMillis;
        c.biddingLeaderName = null;
        c.biddingLeaderAmount = 0;
        c.biddingItemId = itemId == null ? "" : itemId;
        c.biddingItemCount = count;
        c.biddingMinimum = Math.max(0, minimum);
        c.biddingBoxEnabled = true;
        c.save();
    }

    static void stop() {
        ModConfig c = DonutQueueClient.config;
        c.biddingActive = false;
        c.biddingFinished = false;
        c.biddingLeaderName = null;
        c.biddingLeaderAmount = 0;
        c.save();
    }

    /** Starts preset number n (1-based). Returns false if that slot is empty / out of range. */
    static boolean startPreset(int n) {
        ModConfig c = DonutQueueClient.config;
        c.ensureBidPresets();
        if (n < 1 || n > c.biddingPresets.size()) return false;
        ModConfig.BidPreset p = c.biddingPresets.get(n - 1);
        if (p.isEmpty()) return false;
        start(p.itemId, p.count, p.seconds, p.minimum);
        return true;
    }

    /** The item for an id like "minecraft:diamond", or ItemStack.EMPTY if the id is blank/unknown. */
    static ItemStack stackFor(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(ident);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    static String itemName(String id) {
        ItemStack s = stackFor(id);
        return s.isEmpty() ? id : s.getHoverName().getString();
    }

    /** 90 -> "1m30s", 3600 -> "1h", 45 -> "45s". */
    static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h");
        if (m > 0) sb.append(m).append("m");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString();
    }
}
