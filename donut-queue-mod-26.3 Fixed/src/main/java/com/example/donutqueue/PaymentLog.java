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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Line-by-line history of every payment: who paid you (+) and who you paid (-), with the time.
 * Shown on the Earnings page ("Payments log") and, per session, on the Sessions page.
 * Stored in config/donutqueue_payments.json. Only payments made after this log was added are in it.
 */
final class PaymentLog {
    private PaymentLog() {}

    /** One payment. amount > 0 = they paid you, amount < 0 = you paid them. */
    static final class Payment {
        long t;        // System.currentTimeMillis()
        String p;      // player name
        double a;      // signed amount

        Payment() {}

        Payment(long t, String p, double a) {
            this.t = t;
            this.p = p;
            this.a = a;
        }
    }

    private static final int MAX = 20000;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final List<Payment> list = new ArrayList<>(); // oldest first
    private static boolean loaded = false;
    private static boolean dirty = false;
    private static long lastSave = 0;
    private static int rev = 0;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutqueue_payments.json");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path p = file();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                Type t = new TypeToken<ArrayList<Payment>>() {}.getType();
                List<Payment> read = GSON.fromJson(r, t);
                if (read != null) {
                    for (Payment x : read) if (x != null && x.p != null) list.add(x);
                }
            } catch (Exception e) {
                System.err.println("[DonutQueue] Could not read payment log: " + e);
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread(PaymentLog::saveNow));
    }

    static synchronized void saveNow() {
        if (!loaded) return;
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(list), w);
            }
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (IOException e) {
            System.err.println("[DonutQueue] Could not save payment log: " + e);
        }
    }

    /** Logs one payment (negative amount = you paid them). */
    static synchronized void add(String player, double amount) {
        if (player == null || player.isEmpty() || amount == 0) return;
        ensureLoaded();
        list.add(new Payment(System.currentTimeMillis(), player, amount));
        if (list.size() > MAX) list.subList(0, 1000).clear();
        rev++;
        dirty = true;
        if (System.currentTimeMillis() - lastSave >= 5000) saveNow();
    }

    /** Every payment, newest first. */
    static synchronized List<Payment> newestFirst() {
        ensureLoaded();
        List<Payment> out = new ArrayList<>(list);
        Collections.reverse(out);
        return out;
    }

    /** Goes up whenever a payment is added (lets the menu cache what it has already worked out). */
    static synchronized int revision() {
        return rev;
    }

    static synchronized void clear() {
        ensureLoaded();
        list.clear();
        rev++;
        saveNow();
    }
}
