package com.example.donutqueue.rank;

import com.example.donutqueue.DonutQueueClient;
import com.example.donutqueue.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Recolors / rearranges the NAME LINE of anything that carries a rank logo.
 * Config: config/rankcolors.properties (version=2)
 */
public final class RankColors {
    /** color for the name; tintLogo = also recolor the logo glyph itself */
    private record Rule(int color, boolean tintLogo) {}

    private static final Map<Integer, Rule> MAP = new LinkedHashMap<>();
    private static boolean auto = true;
    private static boolean stackPlus = true;
    private static boolean selfTag = true;
    private static String selfLogo = "";
    private static float selfScale = 1.0f;
    private static double selfHeight = 2.0;

    /** every star-ish glyph a staff rank might use */
    private static final int[] STAFF_GLYPHS = {
        0x2605, 0x2606, 0x2726, 0x2727, 0x272A, 0x272B, 0x272C, 0x272D, 0x272E, 0x272F, 0x2730,
        0x2B50, 0x2B51, 0x2B52, 0x1F31F, 0x1F320
    };

    private static final String DEFAULT = """
        # Rank colors config
        version=2
        # copy the server's own logo color onto the name when no rule below matches
        auto=true
        # show + / ++ / +++ (Donut+) on its own line ABOVE the name
        stack_plus=true
        # show your own name tag (logo, name, money) in F5 / third person
        self_tag=true
        # force the logo on your own tag, e.g.  U+1F4F9@FF00A6   or   ++@00A6FF   (empty = detect automatically)
        self_logo=
        # size and height of your own tag (lower self_height = closer to your head, lower self_scale = smaller)
        self_scale=1.0
        self_height=2.0
        # (staff colour is now set in the menu: Customisation > Ranks > Change staff team colour)
        # U+<codepoint>=RRGGBB    name gets the color, the logo keeps its own color
        # U+<codepoint>=RRGGBB!   name AND logo get the color
        U+1F4F9=FF55FF
        """;

    static { load(); }

    /** Customisation > Ranks > Enable Rank colours. Everything in this class is a no-op while this is off. */
    public static boolean enabled() {
        ModConfig c = DonutQueueClient.config;
        return c != null && c.rankColorsEnabled;
    }

    /** Staff colour from Customisation > Ranks (replaces staff_color in rankcolors.properties). */
    private static int staffColor() {
        ModConfig c = DonutQueueClient.config;
        return c == null ? 0x5555FF : (c.staffColor & 0xFFFFFF);
    }

    private static boolean isStaffGlyph(int cp) {
        for (int g : STAFF_GLYPHS) if (g == cp) return true;
        return false;
    }

    public static boolean selfTag() { return selfTag; }
    public static String selfLogo() { return selfLogo; }
    public static float selfScale() { return selfScale; }
    public static double selfHeight() { return selfHeight; }

    public static void load() {
        MAP.clear();
        auto = true;
        stackPlus = true;
        selfTag = true;
        selfLogo = "";
        selfScale = 1.0f;
        selfHeight = 2.0;
        Path p = FabricLoader.getInstance().getConfigDir().resolve("rankcolors.properties");
        try {
            boolean write = true;
            if (Files.exists(p)) {
                Properties old = new Properties();
                try (var r = Files.newBufferedReader(p)) { old.load(r); }
                if ("2".equals(old.getProperty("version"))) write = false;
                else Files.move(p, p.resolveSibling("rankcolors.properties.bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            if (write) Files.writeString(p, DEFAULT);

            Properties props = new Properties();
            try (var r = Files.newBufferedReader(p)) { props.load(r); }

            for (String k : props.stringPropertyNames()) {
                String key = k.trim();
                String v = props.getProperty(k).trim();
                try {
                    switch (key.toLowerCase()) {
                        case "version", "staff_color" -> { continue; }
                        case "auto" -> { auto = Boolean.parseBoolean(v); continue; }
                        case "stack_plus" -> { stackPlus = Boolean.parseBoolean(v); continue; }
                        case "self_tag" -> { selfTag = Boolean.parseBoolean(v); continue; }
                        case "self_logo" -> { selfLogo = v; continue; }
                        case "self_scale" -> { selfScale = Float.parseFloat(v); continue; }
                        case "self_height" -> { selfHeight = Double.parseDouble(v); continue; }
                        default -> {}
                    }
                    boolean tint = v.endsWith("!");
                    if (tint) v = v.substring(0, v.length() - 1).trim();
                    int cp = Integer.parseInt(key.replaceFirst("(?i)^U\\+", ""), 16);
                    MAP.put(cp, new Rule(Integer.parseInt(v.replace("#", ""), 16), tint));
                } catch (Exception bad) {
                    System.err.println("[donutqueue-ranks] bad config line '" + k + "=" + v + "': " + bad);
                }
            }
        } catch (Exception e) {
            System.err.println("[donutqueue-ranks] could not read rankcolors.properties: " + e);
        }
    }

    /** A logo-ish glyph: non-ASCII, not a letter/digit/space (covers emoji, symbols, private-use font glyphs). */
    static boolean isSymbol(int cp) {
        return cp > 127 && cp != 0xA7 && !Character.isLetterOrDigit(cp) && !Character.isWhitespace(cp);
    }

    private record Seg(Style style, String text) {}

    public static Component apply(Component c) { return apply(c, null, false); }

    public static Component apply(Component c, String playerName) { return apply(c, playerName, false); }

    /**
     * @param playerName the owner of this name (null if unknown -> auto-detect and + stacking are skipped)
     * @param textDisplay true for floating text-display tags (multi-line capable -> + gets its own line)
     */
    public static Component apply(Component c, String playerName, boolean textDisplay) {
        if (c == null || !enabled()) return c;
        String all = c.getString();
        boolean hasSymbol = all.codePoints().anyMatch(RankColors::isSymbol);
        boolean hasPlus = textDisplay && stackPlus && playerName != null && all.indexOf('+') >= 0;
        if (!hasSymbol && !hasPlus) return c;

        List<Seg> segs = new ArrayList<>();
        c.visit((style, text) -> { segs.add(new Seg(style, text)); return Optional.empty(); }, Style.EMPTY);

        // split into lines (keeping the '\n' at the end of each line)
        List<List<Seg>> lines = new ArrayList<>();
        List<Seg> cur = new ArrayList<>();
        for (Seg s : segs) {
            String t = s.text();
            int start = 0;
            for (int i = 0; i < t.length(); i++) {
                if (t.charAt(i) == '\n') {
                    cur.add(new Seg(s.style(), t.substring(start, i + 1)));
                    lines.add(cur);
                    cur = new ArrayList<>();
                    start = i + 1;
                }
            }
            if (start < t.length()) cur.add(new Seg(s.style(), t.substring(start)));
        }
        lines.add(cur);

        boolean changed = false;
        MutableComponent out = Component.empty();
        for (List<Seg> line : lines) {
            Rule rule = lineRule(line, playerName);
            if (hasPlus) {
                List<Seg> stacked = stackPlus(line, playerName);
                if (stacked != line) { line = stacked; changed = true; }
            }
            for (Seg s : line) {
                if (rule == null) { out.append(Component.literal(s.text()).setStyle(s.style())); continue; }
                changed = true;
                StringBuilder run = new StringBuilder();
                boolean runIsLogo = false;
                for (int i = 0; i < s.text().length(); ) {
                    int cp = s.text().codePointAt(i);
                    i += Character.charCount(cp);
                    boolean logo = isSymbol(cp);
                    if (run.length() > 0 && logo != runIsLogo) { flush(out, run, s.style(), runIsLogo, rule); run.setLength(0); }
                    runIsLogo = logo;
                    run.appendCodePoint(cp);
                }
                if (run.length() > 0) flush(out, run, s.style(), runIsLogo, rule);
            }
        }
        return changed ? out : c;
    }

    private static void flush(MutableComponent out, StringBuilder run, Style st, boolean logo, Rule rule) {
        Style use = (logo && !rule.tintLogo()) ? st : st.withColor(TextColor.fromRgb(rule.color()));
        out.append(Component.literal(run.toString()).setStyle(use));
    }

    /** "+Name" -> "+" / "Name" (also ++ and +++), only on the line that holds the player's name. */
    private static List<Seg> stackPlus(List<Seg> line, String playerName) {
        StringBuilder plain = new StringBuilder();
        for (Seg s : line) plain.append(s.text());
        if (plain.indexOf(playerName) < 0) return line;
        int first = -1;
        for (int i = 0; i < line.size(); i++) if (!line.get(i).text().isBlank()) { first = i; break; }
        if (first < 0) return line;
        Seg s = line.get(first);
        String t = s.text();
        int n = 0;
        while (n < t.length() && t.charAt(n) == '+') n++;
        if (n == 0 || n > 4) return line;
        String rest = t.substring(n);
        boolean more = !rest.isBlank();
        for (int j = first + 1; j < line.size(); j++) if (!line.get(j).text().isBlank()) more = true;
        if (!more) return line;
        List<Seg> o = new ArrayList<>(line.subList(0, first));
        o.add(new Seg(s.style(), t.substring(0, n)));
        o.add(new Seg(Style.EMPTY, "\n"));
        if (!rest.isEmpty()) o.add(new Seg(s.style(), rest));
        o.addAll(line.subList(first + 1, line.size()));
        return o;
    }

    private static Rule lineRule(List<Seg> line, String playerName) {
        // 1) explicit config
        for (Seg s : line)
            for (int i = 0; i < s.text().length(); ) {
                int cp = s.text().codePointAt(i);
                i += Character.charCount(cp);
                Rule r = MAP.get(cp);
                if (r != null) return r;
                if (isStaffGlyph(cp)) return new Rule(staffColor(), true);
            }
        // 2) auto: copy the color the server gave the logo, only on the line that holds the player's name
        if (!auto || playerName == null || playerName.isEmpty()) return null;
        StringBuilder plain = new StringBuilder();
        for (Seg s : line) plain.append(s.text());
        if (plain.indexOf(playerName) < 0) return null;
        for (Seg s : line)
            for (int i = 0; i < s.text().length(); ) {
                int cp = s.text().codePointAt(i);
                i += Character.charCount(cp);
                if (!isSymbol(cp)) continue;
                TextColor tc = s.style().getColor();
                if (tc != null && tc.getValue() != 0xFFFFFF) return new Rule(tc.getValue(), false);
            }
        return null;
    }

    /** The player a text-display nametag belongs to (rides them, or floats right above them). */
    public static Player nearestPlayer(Entity e) {
        if (!(e.level() instanceof ClientLevel lvl)) return null;
        if (e.getVehicle() instanceof Player vp) return vp;
        Player best = null;
        double bd = 2.5 * 2.5;
        for (Player p : lvl.players()) {
            double dy = e.getY() - p.getY();
            if (dy < -0.5 || dy > 4.0) continue;
            double dx = p.getX() - e.getX(), dz = p.getZ() - e.getZ();
            double d = dx * dx + dz * dz;
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    public static String nearestPlayerName(Entity e) {
        Player p = nearestPlayer(e);
        return p == null ? null : p.getName().getString();
    }
}
