package com.example.donutqueue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Earnings "sessions": one button starts a session, the same button ends it.
 *
 * While a session is running, every payment/purchase that Starting Bal tracking sees is logged on
 * the session (for its balance graph), and every payment to/from a player is logged per player
 * (for the "gained most" / "lost most" views). Sessions are kept forever (old ones are thinned to keep the file small).
 * Stored in config/donutqueue_sessions.json.
 */
final class Sessions {
    private Sessions() {}

    private static final int MAX_POINTS = 1000;
    private static final long SAVE_EVERY_MS = 5000;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** One session. Fields are plain data so Gson can save them. */
    static final class Session {
        long startMillis;
        long endMillis;                 // 0 while the session is still running
        double startBalance;            // balance when it started (0 if no Starting Bal was running)
        boolean hasBalance;             // false = startBalance is unknown, the graph shows net from $0
        double net;                     // earned (+) / spent (-) during the session
        List<double[]> pts = new ArrayList<>();                      // {secondsSinceStart, netSoFar}
        LinkedHashMap<String, Double> players = new LinkedHashMap<>(); // player -> net they paid you
        List<PaymentLog.Payment> log = new ArrayList<>();            // every payment, oldest first
    }

    private static final List<Session> sessions = new ArrayList<>(); // oldest first
    private static boolean loaded = false;
    private static boolean dirty = false;
    private static long lastSave = 0;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutqueue_sessions.json");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path p = file();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                Type t = new TypeToken<ArrayList<Session>>() {}.getType();
                List<Session> read = GSON.fromJson(r, t);
                if (read != null) {
                    for (Session s : read) {
                        if (s == null) continue;
                        if (s.pts == null) s.pts = new ArrayList<>();
                        if (s.players == null) s.players = new LinkedHashMap<>();
                        if (s.log == null) s.log = new ArrayList<>();
                        sessions.add(s);
                    }
                }
            } catch (Exception e) {
                System.err.println("[DonutQueue] Could not read sessions: " + e);
            }
        }
        compactAll();
        // don't lose the last few seconds of a running session when the game closes
        Runtime.getRuntime().addShutdownHook(new Thread(Sessions::saveNow));
    }

    /** Keeps only about `max` points of a finished session's graph (spread evenly, first and last kept). */
    private static void thin(Session s, int max) {
        if (s.pts.size() <= max) return;
        List<double[]> out = new ArrayList<>();
        double step = (s.pts.size() - 1) / (double) (max - 1);
        for (int i = 0; i < max; i++) out.add(s.pts.get((int) Math.round(i * step)));
        s.pts = out;
    }

    /** Finished sessions keep at most 300 graph points, and ones older than 30 days only 100. */
    private static void compactAll() {
        String cutoff = LocalDate.now().minusDays(29).toString();
        for (Session s : sessions) {
            if (s.endMillis == 0) continue;
            thin(s, dateOf(s).compareTo(cutoff) < 0 ? 100 : 300);
        }
    }

    static synchronized void saveNow() {
        if (!loaded) return;
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(sessions), w);
            }
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (IOException e) {
            System.err.println("[DonutQueue] Could not save sessions: " + e);
        }
    }

    private static void saveSoon() {
        dirty = true;
        if (System.currentTimeMillis() - lastSave >= SAVE_EVERY_MS) saveNow();
    }

    // ---- start / end ----------------------------------------------------------

    static synchronized boolean isRunning() {
        return running() != null;
    }

    static synchronized Session running() {
        ensureLoaded();
        for (int i = sessions.size() - 1; i >= 0; i--) {
            if (sessions.get(i).endMillis == 0) return sessions.get(i);
        }
        return null;
    }

    /** Starts a session (does nothing if one is already running). */
    static synchronized Session start(ModConfig c) {
        Session r = running();
        if (r != null) return r;
        Session s = new Session();
        s.startMillis = System.currentTimeMillis();
        s.hasBalance = c.hasStartingBalance && c.trackingEnabled;
        s.startBalance = s.hasBalance ? c.startingBalance + c.earnedTotal : 0;
        s.pts.add(new double[]{0, 0});
        sessions.add(s);
        saveNow();
        return s;
    }

    /** Ends the running session. Returns it, or null if none was running. */
    static synchronized Session end() {
        Session r = running();
        if (r == null) return null;
        r.endMillis = System.currentTimeMillis();
        thin(r, 300);
        saveNow();
        return r;
    }

    // ---- recording (called from DailyEarnings) --------------------------------

    /** A balance change of `delta` just happened. */
    static synchronized void onMoney(double delta) {
        Session s = running();
        if (s == null) return;
        s.net += delta;
        double sec = (System.currentTimeMillis() - s.startMillis) / 1000.0;
        s.pts.add(new double[]{sec, s.net});
        if (s.pts.size() > MAX_POINTS) {
            List<double[]> thinned = new ArrayList<>();
            for (int i = 0; i < s.pts.size(); i += 2) thinned.add(s.pts.get(i));
            double[] last = s.pts.get(s.pts.size() - 1);
            if (thinned.get(thinned.size() - 1) != last) thinned.add(last);
            s.pts = thinned;
        }
        saveSoon();
    }

    /** `player` just netted you `delta` (negative if you paid them). */
    static synchronized void onPlayer(String player, double delta) {
        Session s = running();
        if (s == null || player == null || player.isEmpty() || delta == 0) return;
        String key = player;
        for (String k : s.players.keySet()) {
            if (k.equalsIgnoreCase(player)) { key = k; break; }
        }
        s.players.merge(key, delta, Double::sum);
        s.log.add(new PaymentLog.Payment(System.currentTimeMillis(), key, delta));
        if (s.log.size() > 5000) s.log.subList(0, 500).clear();
        saveSoon();
    }

    // ---- reading (used by the menu) -------------------------------------------

    private static String dateOf(Session s) {
        return Instant.ofEpochMilli(s.startMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    /** yyyy-MM-dd the session started on. */
    static synchronized String date(Session s) {
        return dateOf(s);
    }

    /** Every session ever recorded, newest first. */
    static synchronized List<Session> allNewestFirst() {
        ensureLoaded();
        List<Session> out = new ArrayList<>(sessions);
        java.util.Collections.reverse(out);
        return out;
    }

    /** Deletes a finished session (a running one has to be ended first). */
    static synchronized boolean delete(Session s) {
        ensureLoaded();
        if (s == null || s.endMillis == 0) return false;
        boolean removed = sessions.remove(s);
        if (removed) saveNow();
        return removed;
    }

    /** Sessions that started on this day (yyyy-MM-dd), oldest first. */
    static synchronized List<Session> forDate(String date) {
        ensureLoaded();
        List<Session> out = new ArrayList<>();
        for (Session s : sessions) if (dateOf(s).equals(date)) out.add(s);
        return out;
    }

    /** Sum of the net of every session that started on this day. */
    static synchronized double netForDate(String date) {
        double sum = 0;
        for (Session s : forDate(date)) sum += s.net;
        return sum;
    }

    static synchronized double net(Session s) {
        return s.net;
    }

    static synchronized boolean hasBalance(Session s) {
        return s.hasBalance;
    }

    static synchronized double startBalance(Session s) {
        return s.startBalance;
    }

    static synchronized double endBalance(Session s) {
        return s.startBalance + s.net;
    }

    static synchronized long startMillis(Session s) {
        return s.startMillis;
    }

    static synchronized long endMillis(Session s) {
        return s.endMillis;
    }

    static synchronized long durationSeconds(Session s) {
        long end = s.endMillis == 0 ? System.currentTimeMillis() : s.endMillis;
        return Math.max(0, (end - s.startMillis) / 1000);
    }

    /** {secondsSinceStart, netSoFar}, oldest first. */
    static synchronized double[][] points(Session s) {
        return s.pts.toArray(new double[0][]);
    }

    /** Every payment of the session (who paid you / who you paid), newest first. */
    static synchronized List<PaymentLog.Payment> payments(Session s) {
        List<PaymentLog.Payment> out = new ArrayList<>(s.log);
        java.util.Collections.reverse(out);
        return out;
    }

    /** Number of payments logged on the session (changes whenever a new one comes in). */
    static synchronized int paymentCount(Session s) {
        return s.log.size();
    }

    /** Number of payments/purchases logged on the session. */
    static synchronized int events(Session s) {
        return Math.max(0, s.pts.size() - 1);
    }

    /** Everyone's net during the session, unfiltered. */
    static synchronized Map<String, Double> allPlayers(Session s) {
        return new java.util.LinkedHashMap<>(s.players);
    }

    /** People who netted you the most during the session, highest first (positive totals only). */
    static synchronized List<Map.Entry<String, Double>> topGains(Session s, int limit) {
        List<Map.Entry<String, Double>> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : s.players.entrySet()) {
            if (e.getValue() > 0 && !DailyEarnings.isNonGamble(e.getKey()))
                out.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
        }
        out.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** People you lost the most to during the session, biggest loss first (values stay negative). */
    static synchronized List<Map.Entry<String, Double>> topLosses(Session s, int limit) {
        List<Map.Entry<String, Double>> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : s.players.entrySet()) {
            if (e.getValue() < 0 && !DailyEarnings.isNonGamble(e.getKey()))
                out.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
        }
        out.sort((a, b) -> Double.compare(a.getValue(), b.getValue()));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** Everything lost to people in this session, as a positive number (the 100% of the % rings). */
    static synchronized double totalLoss(Session s) {
        double total = 0;
        for (Map.Entry<String, Double> e : topLosses(s, Integer.MAX_VALUE)) total += -e.getValue();
        return total;
    }

    /** Wipes every session (used if the daily history is cleared). */
    static synchronized void clear() {
        ensureLoaded();
        sessions.clear();
        saveNow();
    }
}
