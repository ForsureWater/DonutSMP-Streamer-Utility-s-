package com.example.donutqueue;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Customisation > Chat. Two separate things (the mod only changes how a line is DRAWN - payment / earnings
 * detection still reads the server's original text):
 *
 *   PAYMENTS - text is NOT changed, only colours:
 *     "X paid you $50M."   X in the name colour, "$50M" in the received colour
 *     "You paid X $50M."   X in the name colour, "$50M" in the sent colour
 *   WHISPERS - the line IS rewritten:
 *     "You whisper to X: hi"   ->  You -> X: hi   (names in the name colour, message white + bold)
 *     "X whispers to you: hi"  ->  X -> You: hi
 *
 * Everything is OFF until switched on in the menu. Only lines that match the WHOLE line are touched.
 */
final class ChatStyle {
    private ChatStyle() {}

    private static final String NAME = "\\.?[A-Za-z0-9_]{2,16}";
    // amount: optional icon/$ glyph, then number with optional k/m/b suffix, optional trailing "."
    private static final String AMT = "(?<money>[^0-9A-Za-z\\s]{0,3}\\s*[0-9][0-9.,]*[kKmMbB]?)";

    private static final Pattern PAID_YOU = Pattern.compile(
            "^\\s*(?<name>" + NAME + ")\\s+(?:has\\s+)?paid\\s+you\\s+" + AMT + "\\s*\\.?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOU_PAID = Pattern.compile(
            "^\\s*you\\s+paid\\s+(?<name>" + NAME + ")\\s+" + AMT + "\\s*\\.?\\s*$", Pattern.CASE_INSENSITIVE);
    /** "You bought 64 Netherrack for $ 19.9K" - the item (count + name) and the price turn green. */
    private static final Pattern BOUGHT = Pattern.compile(
            "^\\s*you\\s+bought\\s+(?<item>.+?)\\s+for\\s+" + AMT + "\\s*\\.?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final int BUY_GREEN = 0x55FF55;

    private static final Pattern WHISPER_OUT = Pattern.compile(
            "^\\s*you\\s+whisper(?:ed)?\\s+to\\s+(?<name>" + NAME + ")\\s*:\\s?(?<msg>.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern WHISPER_IN = Pattern.compile(
            "^\\s*(?<name>" + NAME + ")\\s+whispers?\\s+to\\s+you\\s*:\\s?(?<msg>.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final int WHITE = 0xFFFFFF;
    private static final int GRAY = 0xAAAAAA;

    /** Returns the re-styled line, or the original message if the feature is off / the line isn't one of ours. */
    static Component modify(Component message) {
        try {
            ModConfig c = DonutQueueClient.config;
            if (c == null || message == null) return message;
            if (!c.chatPaymentsEnabled && !c.chatWhispersEnabled) return message;
            String text = message.getString();
            if (text.isEmpty() || text.length() > 400) return message;

            Matcher m;
            if (c.chatWhispersEnabled) {
                m = WHISPER_OUT.matcher(text);
                if (m.matches()) return whisperOut(m.group("name"), m.group("msg"));
                m = WHISPER_IN.matcher(text);
                if (m.matches()) return whisperIn(m.group("name"), m.group("msg"));
            }
            if (c.chatPaymentsEnabled) return recolourPayment(message);
        } catch (RuntimeException e) {
            // never let a styling problem break chat
        }
        return message;
    }

    /**
     * Payment lines keep their exact text (and font/icon); only the player name and the amount change colour.
     * Returns the message untouched if it isn't a payment line. Ignores the on/off toggle (the preview uses it).
     */
    static Component recolourPayment(Component message) {
        ModConfig c = DonutQueueClient.config;
        String text = message.getString();
        Matcher m = PAID_YOU.matcher(text);
        int moneyColor = c.chatReceivedColor;
        if (!m.matches()) {
            m = YOU_PAID.matcher(text);
            moneyColor = c.chatSentColor;
            if (!m.matches()) return recolourBought(message, text);
        }
        int moneyEnd = m.end("money");
        while (moneyEnd > m.start("money") && (text.charAt(moneyEnd - 1) == '.' || text.charAt(moneyEnd - 1) == ',')) moneyEnd--;
        int[][] ranges = {
                {m.start("name"), m.end("name"), c.chatNameColor & 0xFFFFFF},
                {m.start("money"), moneyEnd, moneyColor & 0xFFFFFF}
        };
        return recolour(message, ranges);
    }

    /** "You bought 64 Netherrack for $ 19.9K": the item count + name and the price in green. */
    private static Component recolourBought(Component message, String text) {
        Matcher m = BOUGHT.matcher(text);
        if (!m.matches()) return message;
        int moneyEnd = m.end("money");
        while (moneyEnd > m.start("money") && (text.charAt(moneyEnd - 1) == '.' || text.charAt(moneyEnd - 1) == ',')) moneyEnd--;
        int[][] ranges = {
                {m.start("item"), m.end("item"), BUY_GREEN},
                {m.start("money"), moneyEnd, BUY_GREEN}
        };
        return recolour(message, ranges);
    }

    /** Rebuilds the message with the given [start, end) character ranges coloured, keeping every other style. */
    private static Component recolour(Component message, int[][] ranges) {
        MutableComponent out = Component.empty();
        int[] pos = {0};
        message.visit((style, t) -> {
            int base = pos[0];
            int i = 0;
            while (i < t.length()) {
                int rgb = colorAt(ranges, base + i);
                int j = i + 1;
                while (j < t.length() && colorAt(ranges, base + j) == rgb) j++;
                Style st = rgb < 0 ? style : style.withColor(TextColor.fromRgb(rgb));
                out.append(Component.literal(t.substring(i, j)).setStyle(st));
                i = j;
            }
            pos[0] = base + t.length();
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static int colorAt(int[][] ranges, int index) {
        for (int[] r : ranges) if (index >= r[0] && index < r[1]) return r[2];
        return -1;
    }

    // --- whisper builders (also used by the settings screen's preview) -----------------------------

    static Component whisperOut(String name, String msg) {
        ModConfig c = DonutQueueClient.config;
        return Component.empty()
                .append(part("You", c.chatNameColor, false))
                .append(part(" -> ", GRAY, false))
                .append(part(name, c.chatNameColor, false))
                .append(part(": ", GRAY, false))
                .append(part(msg, WHITE, true));
    }

    static Component whisperIn(String name, String msg) {
        ModConfig c = DonutQueueClient.config;
        return Component.empty()
                .append(part(name, c.chatNameColor, false))
                .append(part(" -> ", GRAY, false))
                .append(part("You", c.chatNameColor, false))
                .append(part(": ", GRAY, false))
                .append(part(msg, WHITE, true));
    }

    private static MutableComponent part(String text, int rgb, boolean bold) {
        Style st = Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF));
        if (bold) st = st.withBold(true);
        return Component.literal(text).setStyle(st);
    }
}
