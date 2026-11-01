package com.example.donutqueue.rank;

import com.example.donutqueue.mixin.DisplayInvoker;
import com.example.donutqueue.mixin.TextDisplayInvoker;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The server never shows you your own "[logo] Name / $ money" tag. In third person (F5) we spawn a
 * client-side-only Text Display that copies the look (scale, background, offset) of another player's tag
 * and fills in OUR logo, name and money.
 */
public final class OwnTag {
    private static final int FAKE_ID = 0x6FFF0001;
    private static final Pattern MONEY = Pattern.compile("\\$\\s*([0-9][0-9.,]*\\s?[KkMmBbTt]?)");

    private static Display.TextDisplay fake;
    private static List<SynchedEntityData.DataValue<?>> template;
    private static boolean templateRides = true;
    private static double templateDy = 2.3;
    private static Component pending = Component.empty();
    private static Component composed = Component.empty();
    private static String composedKey = "";
    private static int counter = 0;
    private static long retryAt = 0;          // after an error, wait a few seconds and try again (never stays off)
    private static boolean errorLogged = false;
    private static long lastAttempt = 0;
    private static boolean spawnedDefault = false;   // fake was built with built-in look (no template yet)
    private static String lastMoney = null;

    private record P(Style style, String text) {}

    public static void reset() { retryAt = 0; lastMoney = null; remove(); }

    public static boolean isFake(Entity e) { return e != null && e == fake; }

    public static Component text() { return composed; }

    public static String myName() {
        Player me = Minecraft.getInstance().player;
        return me == null ? null : me.getName().getString();
    }

    /** Called when we see another player's tag: remember how it is styled/positioned. */
    public static void captureTemplate(Entity e, Player owner) {
        if (owner == null || e == fake) return;
        Player me = Minecraft.getInstance().player;
        if (me != null && owner == me) return;
        List<SynchedEntityData.DataValue<?>> v = e.getEntityData().getNonDefaultValues();
        if (v == null || v.isEmpty()) return;
        template = v;
        templateRides = e.getVehicle() != null;
        templateDy = e.getY() - owner.getY();
    }

    /** Throttle for copying another player's tag (the mixin asks this before doing the work). */
    public static boolean wantsTemplate() {
        long now = System.currentTimeMillis();
        if (now - lastAttempt < (template == null ? 250 : 3000)) return false;
        lastAttempt = now;
        return true;
    }

    public static void tick(Minecraft mc) {
        if (System.currentTimeMillis() < retryAt) return;
        try {
            doTick(mc);
        } catch (Throwable t) {
            retryAt = System.currentTimeMillis() + 5000;   // try again in 5 s instead of staying off
            if (!errorLogged) {
                errorLogged = true;
                System.err.println("[donutqueue-ranks] own tag error (will retry): " + t);
                t.printStackTrace();
            }
            remove();
        }
    }

    private static void doTick(Minecraft mc) throws Exception {
        Player me = mc.player;
        ClientLevel lvl = mc.level;
        if (me == null || lvl == null) lastMoney = null;
        // always on: your own F5 name tag no longer depends on the Rank colours switch
        if (me == null || lvl == null || mc.options.getCameraType().isFirstPerson()) {
            remove();
            return;
        }
        for (Entity p : me.getPassengers()) {
            if (p instanceof Display.TextDisplay && p != fake) { remove(); return; }   // server already shows it
        }
        if (counter++ % 10 == 0) pending = compose(mc);
        String key = pending.toString();
        boolean useTemplate = template != null;
        boolean rides = false;   // we position it ourselves so self_height / self_scale always apply
        boolean stale = fake == null || fake.isRemoved() || fake.level() != lvl || !key.equals(composedKey)
            || (spawnedDefault && useTemplate);   // a real template showed up -> switch to the server's look
        if (stale) {
            remove();
            composed = pending;
            composedKey = key;
            spawn(me, lvl);
        } else {
            double dy = RankColors.selfHeight();
            fake.setPos(me.getX(), me.getY() + dy, me.getZ());
        }
    }

    private static void spawn(Player me, ClientLevel lvl) throws Exception {
        boolean useTemplate = template != null;
        boolean rides = false;
        double dy = RankColors.selfHeight();
        Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, lvl);
        d.setId(FAKE_ID);
        if (useTemplate) { d.getEntityData().assignValues(template); applyScale(d); }
        else applyDefaults(d);
        d.setPos(me.getX(), me.getY() + (rides ? 0 : dy), me.getZ());
        spawnedDefault = !useTemplate;
        fake = d;
        lvl.addEntity(d);
    }

    /**
     * Built-in look, used until we've seen another player's tag to copy: faces the camera, no dark box,
     * text shadow, scaled by self_scale. Every step is optional - if one fails the tag just looks plainer.
     */
    private static void applyDefaults(Display.TextDisplay d) {
        try { ((DisplayInvoker) (Object) d).donutqueue$setBillboardConstraints(Display.BillboardConstraints.CENTER); }
        catch (Throwable ignored) {}
        try { ((TextDisplayInvoker) (Object) d).donutqueue$setBackgroundColor(0); } catch (Throwable ignored) {}
        try { ((TextDisplayInvoker) (Object) d).donutqueue$setFlags((byte) 1); } catch (Throwable ignored) {}   // 1 = text shadow
        applyScale(d);
    }

    /** your own size (self_scale), overriding whatever scale/offset the copied tag had */
    private static void applyScale(Display.TextDisplay d) {
        try {
            float sc = RankColors.selfScale();
            Transformation t = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf());
            ((DisplayInvoker) (Object) d).donutqueue$setTransformation(t);
        } catch (Throwable ignored) {}
    }

    private static void remove() {
        if (fake == null) return;
        try { fake.stopRiding(); fake.discard(); } catch (Throwable ignored) {}
        fake = null;
    }

    // --- what to show ---

    private static Component compose(Minecraft mc) {
        String name = mc.player.getName().getString();
        MutableComponent out = Component.empty();

        List<P> logo = parseSelfLogo(RankColors.selfLogo());
        if (logo.isEmpty()) {
            PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null && info.getTabListDisplayName() != null) logo = logoOf(info.getTabListDisplayName(), name);
            if (logo.isEmpty()) logo = logoOf(mc.player.getDisplayName(), name);
        }
        for (P p : logo) out.append(Component.literal(p.text()).setStyle(p.style()));

        Style white = Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF));
        out.append(Component.literal(name).setStyle(white));

        String money = findMoney(sidebar(mc));
        if (money != null) lastMoney = money; else money = lastMoney;   // don't flicker when the sidebar redraws
        if (money != null) {
            out.append(Component.literal("\n"));
            out.append(Component.literal("$ ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x00FF00))));
            out.append(Component.literal(money).setStyle(white));
        }
        return out;
    }

    /** Symbols / '+' that sit in front of the player's name inside a tab-list or display-name component. */
    private static List<P> logoOf(Component c, String name) {
        List<P> segs = new ArrayList<>();
        c.visit((st, t) -> { segs.add(new P(st, t)); return Optional.empty(); }, Style.EMPTY);
        StringBuilder plain = new StringBuilder();
        for (P p : segs) plain.append(p.text());
        int idx = plain.indexOf(name);
        if (idx <= 0) return List.of();
        List<P> out = new ArrayList<>();
        int pos = 0;
        for (P p : segs) {
            StringBuilder keep = new StringBuilder();
            for (int i = 0; i < p.text().length(); ) {
                int cp = p.text().codePointAt(i);
                if (pos + i < idx && (cp == '+' || RankColors.isSymbol(cp))) keep.appendCodePoint(cp);
                i += Character.charCount(cp);
            }
            pos += p.text().length();
            if (keep.length() > 0) out.add(new P(p.style(), keep.toString()));
        }
        return out;
    }

    /** "U+1F4F9@FF00A6", "U+E000,U+E001@55FF55" or "++@00A6FF" */
    private static List<P> parseSelfLogo(String cfg) {
        if (cfg == null || cfg.isBlank()) return List.of();
        try {
            String text = cfg, color = null;
            int at = cfg.lastIndexOf('@');
            if (at >= 0) { text = cfg.substring(0, at); color = cfg.substring(at + 1).trim(); }
            StringBuilder sb = new StringBuilder();
            if (text.toUpperCase(Locale.ROOT).startsWith("U+")) {
                for (String part : text.split(","))
                    sb.appendCodePoint(Integer.parseInt(part.trim().replaceFirst("(?i)^U\\+", ""), 16));
            } else sb.append(text.trim());
            Style st = color == null ? Style.EMPTY : Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(color.replace("#", ""), 16)));
            return List.of(new P(st, sb.toString()));
        } catch (Exception e) {
            System.err.println("[donutqueue-ranks] bad self_logo '" + cfg + "': " + e);
            return List.of();
        }
    }

    static List<Component> sidebar(Minecraft mc) {
        List<Component> r = new ArrayList<>();
        if (mc.level == null) return r;
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return r;
        r.add(obj.getDisplayName());
        for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
            PlayerTeam t = sb.getPlayersTeam(e.owner());
            r.add(PlayerTeam.formatNameForTeam(t, e.ownerName()));
        }
        return r;
    }

    static String findMoney(List<Component> lines) {
        String fallback = null;
        for (Component c : lines) {
            String s = c.getString();
            Matcher m = MONEY.matcher(s);
            if (!m.find()) continue;
            String val = m.group(1).replace(" ", "").toUpperCase(Locale.ROOT);
            if (s.toLowerCase(Locale.ROOT).contains("money")) return val;
            if (fallback == null) fallback = val;
        }
        return fallback;
    }
}
