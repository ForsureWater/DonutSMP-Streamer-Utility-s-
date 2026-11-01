package com.example.donutqueue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Customisation > Chat > Chat Settings > Chat saves. Minecraft wipes the chat when you leave a server; this keeps
 * the last 100 lines (colours included) and puts them back when you join again - also after restarting the game.
 */
final class ChatSave {
    private ChatSave() {}

    private static final int MAX_LINES = 100;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("donutqueue-chat.json");
    private static final Gson GSON = new Gson();
    private static final List<Component> lines = new ArrayList<>();
    private static boolean loaded = false;

    private static boolean on() {
        ModConfig c = DonutQueueClient.config;
        return c != null && c.chatSaveEnabled;
    }

    /** Remembers a line that was just shown in chat and returns it unchanged. */
    static synchronized Component keep(Component message) {
        if (message == null || !on()) return message;
        ensureLoaded();
        lines.add(message);
        while (lines.size() > MAX_LINES) lines.remove(0);
        return message;
    }

    /** Puts the remembered lines back into the (now empty) chat after joining. */
    static synchronized void replay(Minecraft mc) {
        if (mc == null || mc.gui == null || !on()) return;
        ensureLoaded();
        for (Component line : new ArrayList<>(lines)) {
            mc.gui.hud.getChat().addClientSystemMessage(line);
        }
    }

    static synchronized void save() {
        if (!on() || !loaded) return;
        try {
            JsonArray arr = new JsonArray();
            for (Component line : lines) {
                JsonElement je = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, line).result().orElse(null);
                if (je != null) arr.add(je);
            }
            Files.writeString(FILE, GSON.toJson(arr), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[DonutQueue] Couldn't save chat: " + e);
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;
        try {
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement je : arr) {
                try {
                    ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, je).result().ifPresent(lines::add);
                } catch (Exception ignored) {
                    // skip one bad line
                }
            }
            while (lines.size() > MAX_LINES) lines.remove(0);
        } catch (Exception e) {
            System.err.println("[DonutQueue] Couldn't read saved chat: " + e);
        }
    }
}
