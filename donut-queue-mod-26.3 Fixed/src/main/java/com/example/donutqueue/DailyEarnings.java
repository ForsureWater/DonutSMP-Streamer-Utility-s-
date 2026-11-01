package com.example.donutqueue;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Keeps ALL-TIME daily history (every day since the mod was first used) for the neon menu's graphs.
 *
 * For every calendar day we remember two things:
 *   net     - how much was earned (+) / spent (-) that day while Starting Bal was running
 *   balance - what your balance was at the END of that day (starting balance + earned so far),
 *             only known on days where a starting balance had been captured
 *
 * The graph plots balance, with 0 at the bottom and (2 x starting balance) at the top.
 * Stored in config/donutqueue_daily.properties.
 */
final class DailyEarnings {
    private DailyEarnings() {}


    /** One row of history. balance is NaN on days that never had a known balance. */
    static final class Day {
        final String date;
        final double net;
        final double balance;

        Day(String date, double net, double balance) {
            this.date = date;
            this.net = net;
            this.balance = balance;
        }
    }

    private static final TreeMap<String, double[]> days = new TreeMap<>(); // date -> {net, balance}

    // Per-day detail for the Stats graphs: date -> list of {secondOfDay, netSoFarThatDay}, one point
    // per payment/purchase while Starting Bal was running. Older history recorded before this
    // existed simply has no points (the Stats page says so).
    private static final TreeMap<String, List<double[]>> intraday = new TreeMap<>();
    private static final int MAX_POINTS_PER_DAY = 800;

    // Per-day, per-player net: date -> (player -> net they paid you that day). Drives the
    // "Top 7 today" / "Lost most" list on the Earnings page.
    private static final TreeMap<String, java.util.LinkedHashMap<String, Double>> byPlayer = new TreeMap<>();
    private static boolean loaded = false;

    // Recent live balance samples (one per payment) - drives the little top-right
    // graph so it moves right away instead of waiting for tomorrow. Not saved.
    private static final java.util.ArrayDeque<Double> recent = new java.util.ArrayDeque<>();
    private static final int RECENT_MAX = 40;

    private static void pushRecent(double balance) {
        recent.addLast(balance);
        while (recent.size() > RECENT_MAX) recent.pollFirst();
    }

    // Balance graph in the in-game Earnings box: how much you have earned/spent (earnedTotal) after
    // every payment since Starting Bal tracking last began. Saved, so it survives a restart.
    private static final List<Double> run = new ArrayList<>();
    private static final int RUN_MAX = 300;

    private static void pushRun(double earned) {
        run.add(earned);
        if (run.size() > RUN_MAX) {
            List<Double> thinned = new ArrayList<>();
            for (int i = 0; i < run.size(); i += 2) thinned.add(run.get(i));
            double last = run.get(run.size() - 1);
            if (thinned.get(thinned.size() - 1) != last) thinned.add(last);
            run.clear();
            run.addAll(thinned);
        }
    }

    /** earnedTotal after each payment since tracking began, oldest first. First value is 0 (the starting balance). */
    static synchronized double[] runSeries() {
        ensureLoaded();
        double[] out = new double[run.size()];
        for (int i = 0; i < out.length; i++) out[i] = run.get(i);
        return out;
    }

    /** Forgets the graph (tracking was reset). */
    static synchronized void clearRun() {
        ensureLoaded();
        run.clear();
        save();
    }

    /** Recent balance samples, oldest first (may be empty). */
    static synchronized double[] recentBalances() {
        double[] out = new double[recent.size()];
        int i = 0;
        for (double d : recent) out[i++] = d;
        return out;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutqueue_daily.properties");
    }

    private static String todayKey() {
        return LocalDate.now().toString(); // yyyy-MM-dd
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        // writes are throttled while you play; make sure the last ones reach the disk on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (DailyEarnings.class) {
                if (dirty) save();
            }
        }));
        Path p = file();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            Properties props = new Properties();
            props.load(r);
            for (String key : props.stringPropertyNames()) {
                if (!key.startsWith("net.")) continue;
                String date = key.substring(4);
                double net = Double.parseDouble(props.getProperty(key, "0"));
                double bal = Double.NaN;
                String balStr = props.getProperty("bal." + date);
                if (balStr != null) bal = Double.parseDouble(balStr);
                days.put(date, new double[]{net, bal});
            }
            for (String key : props.stringPropertyNames()) {
                if (!key.startsWith("ply.")) continue;
                String date = key.substring(4);
                java.util.LinkedHashMap<String, Double> map = new java.util.LinkedHashMap<>();
                for (String part : props.getProperty(key, "").split(",")) {
                    int cut = part.lastIndexOf(':');
                    if (cut <= 0) continue;
                    try {
                        map.put(part.substring(0, cut), Double.parseDouble(part.substring(cut + 1)));
                    } catch (NumberFormatException ignored) {
                        // skip a damaged entry
                    }
                }
                if (!map.isEmpty()) byPlayer.put(date, map);
            }
            String runStr = props.getProperty("run");
            if (runStr != null && !runStr.isEmpty()) {
                for (String part : runStr.split(",")) {
                    try {
                        run.add(Double.parseDouble(part));
                    } catch (NumberFormatException ignored) {
                        // skip a damaged point
                    }
                }
            }
            for (String key : props.stringPropertyNames()) {
                if (!key.startsWith("pts.")) continue;
                String date = key.substring(4);
                List<double[]> list = new ArrayList<>();
                for (String part : props.getProperty(key, "").split(",")) {
                    String[] kv = part.split(":");
                    if (kv.length != 2) continue;
                    try {
                        list.add(new double[]{Double.parseDouble(kv[0]), Double.parseDouble(kv[1])});
                    } catch (NumberFormatException ignored) {
                        // skip a damaged point
                    }
                }
                if (!list.isEmpty()) intraday.put(date, list);
            }
        } catch (Exception e) {
            System.err.println("[DonutQueue] Could not read daily history: " + e);
        }
        trim();
    }

    /**
     * Nothing is ever deleted. To keep the file small over years, the per-payment detail of days older
     * than 30 days is thinned to one point per 10 minutes (the day totals and player totals stay exact).
     */
    private static void trim() {
        String cutoff = LocalDate.now().minusDays(29).toString();
        List<String> keys = new ArrayList<>(intraday.headMap(cutoff).keySet());
        for (String key : keys) {
            List<double[]> l = intraday.get(key);
            if (l == null || l.size() <= 144) continue;
            List<double[]> out = new ArrayList<>();
            int lastB = -1;
            for (double[] pt : l) {
                int bucket = (int) (pt[0] / 600);
                if (bucket == lastB) out.set(out.size() - 1, pt);
                else { out.add(pt); lastB = bucket; }
            }
            intraday.put(key, out);
        }
    }

    // ---- throttled saving + change counter ----------------------------------

    private static boolean dirty = false;
    private static long lastSave = 0;
    private static int rev = 0;

    private static void saveSoon() {
        dirty = true;
        if (System.currentTimeMillis() - lastSave >= 5000) save();
    }

    /** Goes up whenever the history changes (lets the menu cache what it has already worked out). */
    static synchronized int revision() {
        return rev;
    }

    static synchronized void save() {
        ensureLoaded();
        Properties props = new Properties();
        for (Map.Entry<String, double[]> e : days.entrySet()) {
            props.setProperty("net." + e.getKey(), Double.toString(e.getValue()[0]));
            if (!Double.isNaN(e.getValue()[1])) {
                props.setProperty("bal." + e.getKey(), Double.toString(e.getValue()[1]));
            }
        }
        for (Map.Entry<String, List<double[]>> e : intraday.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (double[] p : e.getValue()) {
                if (sb.length() > 0) sb.append(',');
                sb.append((int) p[0]).append(':').append(p[1]);
            }
            props.setProperty("pts." + e.getKey(), sb.toString());
        }
        for (Map.Entry<String, java.util.LinkedHashMap<String, Double>> e : byPlayer.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Double> pe : e.getValue().entrySet()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(pe.getKey()).append(':').append(pe.getValue());
            }
            props.setProperty("ply." + e.getKey(), sb.toString());
        }
        if (!run.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (double d : run) {
                if (sb.length() > 0) sb.append(',');
                sb.append(d);
            }
            props.setProperty("run", sb.toString());
        }
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                props.store(w, "DonutQueue daily earnings");
            }
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (IOException e) {
            System.err.println("[DonutQueue] Could not save daily history: " + e);
        }
    }

    private static double[] today() {
        String key = todayKey();
        double[] row = days.get(key);
        if (row == null) {
            row = new double[]{0, Double.NaN};
            days.put(key, row);
            trim();
        }
        return row;
    }

    /** Call AFTER config.earnedTotal has been changed by delta. */
    static synchronized void record(double delta, ModConfig config) {
        ensureLoaded();
        double[] row = today();
        row[0] += delta;
        addPoint(todayKey(), LocalTime.now().toSecondOfDay(), row[0]);
        if (config.hasStartingBalance) {
            row[1] = config.startingBalance + config.earnedTotal;
            pushRecent(row[1]);
        }
        // graph series (also works in goal mode without a captured balance); restarts from where earnedTotal was
        double prevEarned = config.earnedTotal - delta;
        if (run.isEmpty() || Math.abs(run.get(run.size() - 1) - prevEarned) > 0.5) {
            run.clear();
            run.add(prevEarned);
        }
        pushRun(config.earnedTotal);
        Sessions.onMoney(delta);
        rev++;
        saveSoon();
    }

    /** Adds delta (negative if you paid them back) to what `player` has netted you today. */
    static synchronized void recordPlayer(String player, double delta) {
        if (player == null || player.isEmpty() || delta == 0) return;
        ensureLoaded();
        today(); // makes sure today exists so old days (and their player lists) get trimmed together
        java.util.LinkedHashMap<String, Double> map = byPlayer.computeIfAbsent(todayKey(), k -> new java.util.LinkedHashMap<>());
        String key = player;
        for (String k : map.keySet()) {
            if (k.equalsIgnoreCase(player)) { key = k; break; }
        }
        map.merge(key, delta, Double::sum);
        PaymentLog.add(player, delta);
        Sessions.onPlayer(player, delta);
        rev++;
        saveSoon();
    }

    /** The people who netted you the most on one day (yyyy-MM-dd), highest first, positive totals only. */
    static synchronized List<Map.Entry<String, Double>> topPlayers(String date, int limit) {
        ensureLoaded();
        List<Map.Entry<String, Double>> out = new ArrayList<>();
        java.util.LinkedHashMap<String, Double> map = byPlayer.get(date);
        if (map != null) {
            for (Map.Entry<String, Double> e : map.entrySet()) {
                if (e.getValue() > 0 && !isNonGamble(e.getKey())) out.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
            }
        }
        out.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** Everyone's net for one day (yyyy-MM-dd), unfiltered. */
    static synchronized java.util.LinkedHashMap<String, Double> allPlayers(String date) {
        ensureLoaded();
        java.util.LinkedHashMap<String, Double> map = byPlayer.get(date);
        return map == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(map);
    }

    /** Everyone's net across every saved day, unfiltered. */
    static synchronized java.util.LinkedHashMap<String, Double> allPlayersAllTime() {
        ensureLoaded();
        java.util.LinkedHashMap<String, Double> sum = new java.util.LinkedHashMap<>();
        for (java.util.LinkedHashMap<String, Double> map : byPlayer.values()) {
            for (Map.Entry<String, Double> e : map.entrySet()) {
                String key = e.getKey();
                for (String k : sum.keySet()) {
                    if (k.equalsIgnoreCase(key)) { key = k; break; }
                }
                sum.merge(key, e.getValue(), Double::sum);
            }
        }
        return sum;
    }

    // ---- "non gamble": people left out of the lost-most views ----------------

    static boolean isNonGamble(String player) {
        ModConfig c = DonutQueueClient.config;
        if (c == null || c.nonGamblePlayers == null) return false;
        for (String n : c.nonGamblePlayers) if (n.equalsIgnoreCase(player)) return true;
        return false;
    }

    /** Hides this person from the top-earners list, the lost-most list and the pillars. */
    static synchronized void markNonGamble(String player) {
        ModConfig c = DonutQueueClient.config;
        if (c == null || player == null || player.isEmpty() || isNonGamble(player)) return;
        c.nonGamblePlayers.add(player);
        c.save();
    }

    /** Counts this person again. Returns false if they weren't marked. */
    static synchronized boolean unmarkNonGamble(String player) {
        ModConfig c = DonutQueueClient.config;
        if (c == null || c.nonGamblePlayers == null) return false;
        boolean removed = c.nonGamblePlayers.removeIf(n -> n.equalsIgnoreCase(player));
        if (removed) c.save();
        return removed;
    }

    static synchronized void clearNonGamble() {
        ModConfig c = DonutQueueClient.config;
        if (c == null) return;
        c.nonGamblePlayers.clear();
        c.save();
    }

    /**
     * The people you lost the most money to on one day (yyyy-MM-dd): the ones you paid more than they
     * paid you. Biggest loss first, negative totals only (values stay negative).
     */
    static synchronized List<Map.Entry<String, Double>> topLosses(String date, int limit) {
        ensureLoaded();
        List<Map.Entry<String, Double>> out = new ArrayList<>();
        java.util.LinkedHashMap<String, Double> map = byPlayer.get(date);
        if (map != null) {
            for (Map.Entry<String, Double> e : map.entrySet()) {
                if (e.getValue() < 0 && !isNonGamble(e.getKey())) out.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
            }
        }
        out.sort((a, b) -> Double.compare(a.getValue(), b.getValue()));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /**
     * The people you lost the most to across several days (yyyy-MM-dd), biggest total loss first.
     * Values stay negative. A person's total is their net over those days, so winning some back
     * from them counts against it; only people still net-negative are listed.
     */
    static synchronized List<Map.Entry<String, Double>> topLossesOver(List<String> dates, int limit) {
        ensureLoaded();
        java.util.LinkedHashMap<String, Double> sum = new java.util.LinkedHashMap<>();
        for (String date : dates) {
            java.util.LinkedHashMap<String, Double> map = byPlayer.get(date);
            if (map == null) continue;
            for (Map.Entry<String, Double> e : map.entrySet()) {
                String key = e.getKey();
                for (String k : sum.keySet()) {
                    if (k.equalsIgnoreCase(key)) { key = k; break; }
                }
                sum.merge(key, e.getValue(), Double::sum);
            }
        }
        List<Map.Entry<String, Double>> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : sum.entrySet()) {
            if (e.getValue() < 0 && !isNonGamble(e.getKey())) out.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
        }
        out.sort((a, b) -> Double.compare(a.getValue(), b.getValue()));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /**
     * Everything you lost across those days, as a positive number: the sum of every
     * person's negative total (the 100% that each pillar's circle is a share of).
     */
    static synchronized double totalLossOver(List<String> dates) {
        List<Map.Entry<String, Double>> all = topLossesOver(dates, Integer.MAX_VALUE);
        double total = 0;
        for (Map.Entry<String, Double> e : all) total += -e.getValue();
        return total;
    }

    private static void addPoint(String date, int secondOfDay, double netSoFar) {
        List<double[]> list = intraday.computeIfAbsent(date, k -> new ArrayList<>());
        list.add(new double[]{secondOfDay, netSoFar});
        if (list.size() > MAX_POINTS_PER_DAY) {
            // thin out: keep every other point (and always the newest one)
            List<double[]> thinned = new ArrayList<>();
            for (int i = 0; i < list.size(); i += 2) thinned.add(list.get(i));
            double[] last = list.get(list.size() - 1);
            if (thinned.get(thinned.size() - 1) != last) thinned.add(last);
            intraday.put(date, thinned);
        }
    }

    /**
     * The payments recorded on one day (yyyy-MM-dd), oldest first, as {secondOfDay, netSoFarThatDay}.
     * Empty if nothing was recorded that day (or it predates the per-day graphs).
     */
    static synchronized double[][] dayPoints(String date) {
        ensureLoaded();
        List<double[]> list = intraday.get(date);
        return list == null ? new double[0][] : list.toArray(new double[0][]);
    }

    /** Call when a new starting balance has just been captured: stamps today's balance. */
    static synchronized void markStart(ModConfig config) {
        ensureLoaded();
        double[] row = today();
        row[1] = config.startingBalance + config.earnedTotal;
        recent.clear();
        pushRecent(row[1]);
        run.clear();
        run.add(0.0); // the graph starts at your starting balance
        rev++;
        save();
    }

    /** Net earned today (can be negative). */
    static synchronized double todayNet() {
        ensureLoaded();
        double[] row = days.get(todayKey());
        return row == null ? 0 : row[0];
    }

    private static Day[] allCache = null;
    private static int allCacheRev = -1;
    private static String allCacheDay = "";

    /**
     * Every calendar day from the first day with data until today, oldest first (days with no entry
     * are included with net 0). If there is no data yet this is just today.
     */
    static synchronized Day[] allDays() {
        ensureLoaded();
        String todayK = todayKey();
        if (allCache != null && allCacheRev == rev && allCacheDay.equals(todayK)) return allCache;

        List<Day> out = new ArrayList<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end;
        try {
            if (!days.isEmpty()) start = LocalDate.parse(days.firstKey());
        } catch (Exception ignored) {
            start = end;
        }
        if (start.isAfter(end)) start = end;

        double carry = Double.NaN;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.toString();
            double[] row = days.get(key);
            double net = 0;
            if (row != null) {
                net = row[0];
                if (!Double.isNaN(row[1])) carry = row[1];
            }
            out.add(new Day(key, net, carry));
        }
        allCache = out.toArray(new Day[0]);
        allCacheRev = rev;
        allCacheDay = todayK;
        return allCache;
    }

    /** True if there is at least one day with real data. */
    static synchronized boolean hasData() {
        ensureLoaded();
        return !days.isEmpty();
    }

    static synchronized void clear() {
        ensureLoaded();
        days.clear();
        intraday.clear();
        byPlayer.clear();
        recent.clear();
        run.clear();
        PaymentLog.clear();
        rev++;
        save();
    }
}
