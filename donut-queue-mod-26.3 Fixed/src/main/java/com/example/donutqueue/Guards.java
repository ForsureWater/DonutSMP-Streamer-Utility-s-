package com.example.donutqueue;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Customisation > Guards. Two safety nets that ask "are you sure?" before money leaves your pocket:
 *   Pay Guard     - /pay <player> <amount> with amount at or above your limit opens a confirm menu first.
 *   Auction Guard - clicking an auction house listing priced at or above your limit opens a confirm menu first.
 * Both are OFF by default and each has its own limit.
 */
final class Guards {
    private Guards() {}

    /** Screen to open on the next client tick (opening one straight from a chat command would be closed again by the chat screen). */
    private static Screen pending;
    /** True only while WE re-send a /pay the player just confirmed. */
    private static boolean sendingConfirmedPay;
    /** After a confirmed auction click, further clicks are let through for a moment (the server often adds its own confirm page). */

    /** After our own confirm, we click the server's follow-up "confirm" page for the player (until this time). */
    private static long autoConfirmUntil;
    /** The auction menu the confirmed click came from; the server's confirm page is a different menu. */
    private static AbstractContainerMenu autoConfirmFrom;

    private static final Pattern AUCTION_TITLE = Pattern.compile("(?i)auction|\\bah\\b|market|bazaar|listing|quick\\s*buy|shop|store");
    /** "Price: $1.5K", "Cost - 1,500", "Buy Now: $2m" ... (the keyword and the number may be separated by symbols/spaces). */
    private static final Pattern PRICE = Pattern.compile("(?i)(?:price|cost|buy\\s*now|buyout)[^0-9\\n]{0,20}?([0-9][0-9.,]*\\s?[kmbt]?)(?![a-z])");
    /** Fallback: any "$1.5K" style amount in the lore. */
    private static final Pattern DOLLAR = Pattern.compile("\\$\\s?([0-9][0-9.,]*\\s?[kmbt]?)(?![a-z])", Pattern.CASE_INSENSITIVE);

    /** Chat lines for the debug command; when true every container click reports what the guard saw. */
    static boolean debug;

    static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(Guards::allowCommand);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (pending != null) {
                Screen s = pending;
                pending = null;
                mc.gui.setScreen(s);
            }
            autoConfirmTick(mc);
        });
    }

    // ------------------------------------------------------------------
    // Pay Guard
    // ------------------------------------------------------------------

    private static boolean allowCommand(String command) {
        if (sendingConfirmedPay) return true;
        ModConfig c = DonutQueueClient.config;
        if (c == null || !c.guardPayEnabled) return true;

        String raw = command.trim();
        if (raw.startsWith("/")) raw = raw.substring(1);
        String[] t = raw.split("\\s+");
        if (t.length < 3 || !t[0].equalsIgnoreCase("pay")) return true;

        double amount = DonutQueueClient.parseAmount(t[2]);
        if (amount <= 0 || amount < c.guardPayAmount) return true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return true;
        final String cmd = raw;
        MutableComponent payMsg = Component.literal("Are you sure you want to pay ").withStyle(st -> st.withColor(0xAAAAAA))
                .append(Component.literal(t[1]).withStyle(st -> st.withColor(0x55AAFF)))
                .append(Component.literal(" ").withStyle(st -> st.withColor(0xAAAAAA)))
                .append(Component.literal("$" + show(amount, c.guardPayFullNumber)).withStyle(st -> st.withColor(0x55FF55)))
                .append(Component.literal("?").withStyle(st -> st.withColor(0xAAAAAA)));
        pending = new GuardConfirmScreen("CONFIRM PAYMENT", payMsg,
                null,
                () -> {
                    sendingConfirmedPay = true;
                    try {
                        if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
                    } finally {
                        sendingConfirmedPay = false;
                    }
                });
        return false; // hold the command until the player confirms
    }

    // ------------------------------------------------------------------
    // Auction Guard (called from ContainerScreenMixin)
    // ------------------------------------------------------------------

    /** Returns true if the click was held back and a confirm menu will open instead. */
    static boolean guardAuctionClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button, ContainerInput type) {
        ModConfig c = DonutQueueClient.config;
        if (debug && slot != null && !(slot.container instanceof Inventory)) debugDump(screen, slot, type);
        if (c == null || !c.guardAuctionEnabled) return false;
        if (slot == null || (type != ContainerInput.PICKUP && type != ContainerInput.QUICK_MOVE)) return false;
        if (slot.container instanceof Inventory) return false;          // your own inventory is never a purchase
        // Only the server's OWN confirm page (opened by a click we already confirmed) may be clicked through.
        // Everything else - including the next click inside the same Quick Buy / auction screen - asks again.
        if (autoConfirmUntil != 0 && System.currentTimeMillis() < autoConfirmUntil && screen.getMenu() != autoConfirmFrom) return false;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return false;
        // A real "Price:" line on the item is enough proof of a listing; only the loose "$" fallback needs an auction-looking title.
        boolean titleOk = AUCTION_TITLE.matcher(normalize(screen.getTitle().getString())).find();
        double price = findPrice(stack, titleOk);
        if (price <= 0 || price < c.guardAuctionAmount) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return false;
        final AbstractContainerMenu menu = screen.getMenu();
        String what = stack.getCount() + "x " + stack.getHoverName().getString();
        MutableComponent msg = Component.literal("Are you sure you want to buy ").withStyle(s -> s.withColor(0xAAAAAA))
                .append(Component.literal(what).withStyle(s -> s.withColor(0x55FF55)))
                .append(Component.literal(" for ").withStyle(s -> s.withColor(0xAAAAAA)))
                .append(Component.literal("$" + show(price, c.guardAuctionFullNumber)).withStyle(s -> s.withColor(0x55FF55)))
                .append(Component.literal("?").withStyle(s -> s.withColor(0xAAAAAA)));
        pending = new GuardConfirmScreen("CONFIRM PURCHASE", msg,
                screen,
                () -> {
                    if (mc.player == null || mc.gameMode == null || mc.player.containerMenu != menu) return;
                    autoConfirmFrom = menu;
                    autoConfirmUntil = System.currentTimeMillis() + 5000L;
                    mc.gameMode.handleContainerInput(menu.containerId, slotId, button, type, mc.player);
                });
        return true;
    }

    private static final Pattern CONFIRM_BTN = Pattern.compile("(?i)confirm|accept|\\bbuy\\b|purchase");
    private static final Pattern CANCEL_BTN = Pattern.compile("(?i)cancel|deny|decline|\\bno\\b|back");

    /**
     * Same as the Pay Guard: our popup is the only "are you sure?". When the server answers the confirmed click
     * with its own confirm page, we press its confirm button for the player (once, within 5 seconds).
     */
    private static void autoConfirmTick(Minecraft mc) {
        if (autoConfirmUntil == 0) return;
        if (System.currentTimeMillis() > autoConfirmUntil) {
            autoConfirmUntil = 0;
            autoConfirmFrom = null;
            return;
        }
        if (mc.player == null || mc.gameMode == null) return;
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        AbstractContainerMenu menu = cs.getMenu();
        if (menu == autoConfirmFrom || menu == mc.player.inventoryMenu) return;
        for (Slot sl : menu.slots) {
            if (sl.container instanceof Inventory) continue;
            ItemStack it = sl.getItem();
            if (it.isEmpty()) continue;
            String name = it.getHoverName().getString();
            if (CONFIRM_BTN.matcher(name).find() && !CANCEL_BTN.matcher(name).find()) {
                autoConfirmUntil = 0;
                autoConfirmFrom = null;
                mc.gameMode.handleContainerInput(menu.containerId, sl.index, 0, ContainerInput.PICKUP, mc.player);
                return;
            }
        }
    }

    /** Turns small caps / fancy unicode letters into plain ascii so "ᴘʀɪᴄᴇ" matches "price", and strips colour codes. */
    static String normalize(String in) {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            if (ch == '\u00a7') { i++; continue; } // section-sign colour code + its letter
            int idx = SMALL_CAPS.indexOf(ch);
            if (idx >= 0) { sb.append((char) ('a' + idx)); continue; }
            if (ch >= '\uFF21' && ch <= '\uFF3A') { sb.append((char) ('a' + ch - '\uFF21')); continue; } // fullwidth A-Z
            if (ch >= '\uFF41' && ch <= '\uFF5A') { sb.append((char) ('a' + ch - '\uFF41')); continue; } // fullwidth a-z
            if (ch >= '\uFF10' && ch <= '\uFF19') { sb.append((char) ('0' + ch - '\uFF10')); continue; } // fullwidth digits
            if (ch == '\uFF04' || ch == '\uFE69') { sb.append('$'); continue; }
            sb.append(ch);
        }
        return sb.toString();
    }

    // a b c d e f g h i j k l m n o p q r s t u v w x y z  (small-cap look-alikes)
    private static final String SMALL_CAPS = "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D\u0274\u1D0F\u1D18\uA7AF\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22";

    /** The loose-price reader used by the guard. */
    static double findPrice(ItemStack stack) {
        return findPrice(stack, true);
    }

    /** Reads the price off an auction listing's lore (a line like "Price: $1.5K"). 0 if there isn't one. */
    static double findPrice(ItemStack stack, boolean allowDollarFallback) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return 0;
        // 1) a line that says price / cost / buy now
        for (Component line : lore.lines()) {
            Matcher m = PRICE.matcher(normalize(line.getString()));
            if (m.find()) {
                double v = DonutQueueClient.parseAmount(m.group(1).replace(" ", "").replaceAll("[.,]+$", ""));
                if (v > 0) return v;
            }
        }
        // 2) otherwise any "$123" amount, but only on a screen that looks like an auction house
        if (allowDollarFallback) {
            for (Component line : lore.lines()) {
                Matcher m = DOLLAR.matcher(normalize(line.getString()));
                if (m.find()) {
                    double v = DonutQueueClient.parseAmount(m.group(1).replace(" ", "").replaceAll("[.,]+$", ""));
                    if (v > 0) return v;
                }
            }
        }
        return 0;
    }

    /** /dq guarddebug: shows the menu title and the lore lines of whatever container slot you click. */
    private static void debugDump(AbstractContainerScreen<?> screen, Slot slot, ContainerInput type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return;
        String title = screen.getTitle().getString();
        double p = findPrice(st, true);
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[Guard] title=\"" + title + "\" (normalized \"" + normalize(title)
                + "\") click=" + type + " item=" + st.getHoverName().getString() + " price=" + (p > 0 ? "$" + money(p) : "NOT FOUND")));
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[Guard]   lore: " + line.getString()));
            }
        } else {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[Guard]   (no lore on this item)"));
        }
    }

    /** 1500000 -> "1,500,000", 1.5 -> "1.5", 2 -> "2". */
    /** 175000000 -> "175M" (short) or "175,000,000" (full). */
    static String show(double v, boolean full) {
        return full ? money(v) : shortMoney(v);
    }

    /** 175000000 -> "175M", 1250000 -> "1.25M", 50000 -> "50K". Under 1000 it is just the number. */
    static String shortMoney(double v) {
        double a = Math.abs(v);
        double d;
        String suf;
        if (a >= 1e12) { d = v / 1e12; suf = "T"; }
        else if (a >= 1e9) { d = v / 1e9; suf = "B"; }
        else if (a >= 1e6) { d = v / 1e6; suf = "M"; }
        else if (a >= 1e3) { d = v / 1e3; suf = "K"; }
        else return money(v);
        String t = String.format(Locale.ROOT, "%.2f", d);
        if (t.contains(".")) {
            t = t.replaceAll("0+$", "");
            if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        }
        return t + suf;
    }

    static String money(double v) {
        String s = String.format(Locale.ROOT, "%,.2f", v);
        if (s.endsWith(".00")) return s.substring(0, s.length() - 3);
        if (s.endsWith("0")) return s.substring(0, s.length() - 1);
        return s;
    }
}
