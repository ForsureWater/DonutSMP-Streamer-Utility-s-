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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Holds the actual line of people waiting. Saved to
 * config/donutqueue_state.json after every change so the queue survives
 * a game restart.
 */
public class QueueManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("donutqueue_state.json");
    private static final Path HISTORY_PATH = FabricLoader.getInstance().getConfigDir().resolve("donutqueue_history.json");
    private static final int HISTORY_MAX = 25;

    private final Deque<QueueEntry> queue = new ArrayDeque<>();

    // Newest served person at the front (index 0), oldest at the back.
    private final Deque<QueueEntry> history = new ArrayDeque<>();

    public QueueManager() {
        load();
        loadHistory();
    }

    public synchronized void add(QueueEntry entry) {
        queue.addLast(entry);
        save();
    }

    /** Removes and returns whoever is first in line, or null if empty. */
    public synchronized QueueEntry pollNext() {
        QueueEntry entry = queue.pollFirst();
        save();
        if (entry != null) {
            recordServed(entry);
        }
        return entry;
    }

    /** Adds someone to the served-history log (newest at the front), capped at 25. */
    public synchronized void recordServed(QueueEntry entry) {
        history.addFirst(new QueueEntry(entry.playerName, entry.amount, System.currentTimeMillis()));
        while (history.size() > HISTORY_MAX) {
            history.removeLast();
        }
        saveHistory();
    }

    /** Newest-first list of up to `amount` (max 25) most recently served people. */
    public synchronized List<QueueEntry> getHistory(int amount) {
        int capped = Math.max(1, Math.min(amount, HISTORY_MAX));
        List<QueueEntry> all = new ArrayList<>(history);
        return all.subList(0, Math.min(capped, all.size()));
    }

    public synchronized QueueEntry peekNext() {
        return queue.peekFirst();
    }

    public synchronized List<QueueEntry> asList() {
        return new ArrayList<>(queue);
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized void clear() {
        queue.clear();
        save();
    }

    public synchronized boolean removeByName(String name) {
        Iterator<QueueEntry> it = queue.iterator();
        while (it.hasNext()) {
            QueueEntry e = it.next();
            if (e.playerName.equalsIgnoreCase(name)) {
                it.remove();
                save();
                return true;
            }
        }
        return false;
    }

    /** Removes every queued entry matching this name (case-insensitive). Returns how many were removed. */
    public synchronized int removeAllByName(String name) {
        int removed = 0;
        Iterator<QueueEntry> it = queue.iterator();
        while (it.hasNext()) {
            QueueEntry e = it.next();
            if (e.playerName.equalsIgnoreCase(name)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) save();
        return removed;
    }

    private void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(queue), writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void load() {
        if (!Files.exists(PATH)) return;
        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<ArrayList<QueueEntry>>() {}.getType();
            List<QueueEntry> loaded = GSON.fromJson(reader, listType);
            if (loaded != null) {
                queue.addAll(loaded);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveHistory() {
        try {
            Files.createDirectories(HISTORY_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(HISTORY_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(history), writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadHistory() {
        if (!Files.exists(HISTORY_PATH)) return;
        try (Reader reader = Files.newBufferedReader(HISTORY_PATH, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<ArrayList<QueueEntry>>() {}.getType();
            List<QueueEntry> loaded = GSON.fromJson(reader, listType);
            if (loaded != null) {
                history.addAll(loaded);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
