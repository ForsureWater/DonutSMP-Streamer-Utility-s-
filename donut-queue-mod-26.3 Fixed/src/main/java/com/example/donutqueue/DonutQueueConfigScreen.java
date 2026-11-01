package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

/**
 * Side menu of the Anti Gamble Skip page in the neon menu. It only holds what the main page does
 * NOT already have (HUD box colour + the blacklist) - move box, show/hide, lever skip, chat
 * messages, minimum payment and serve/clear all live on the main page.
 */
public class DonutQueueConfigScreen extends NeonScreen {

    private static final int MAX_BLACKLIST_ROWS_SHOWN = 6;

    private final Screen parent;

    private EditBox blacklistBox;
    private EditBox colorBox;
    private Button sizeButton;
    private int blacklistListY;

    public DonutQueueConfigScreen(Screen parent) {
        super(Component.literal("Anti Gamble Skip"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = DonutQueueClient.config;
        int centerX = this.width / 2;
        int y = 46;
        int rowHeight = 24;

        // --- HUD box color (label drawn above in render) ---------------------
        colorBox = new EditBox(this.font, centerX - 100, y, 150, 20, Component.literal("Hex color"));
        colorBox.setMaxLength(8);
        colorBox.setValue(String.format("%06X", config.queueBoxColor & 0xFFFFFF));
        this.addRenderableWidget(colorBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> {
            Integer parsed = parseHexColor(colorBox.getValue());
            if (parsed != null) {
                config.queueBoxColor = parsed;
                config.save();
            }
        }).bounds(centerX + 55, y, 45, 20).build());
        y += rowHeight + 14;

        // --- Box size (label drawn above in render) --------------------------
        sizeButton = addSizeRow(centerX, y, () -> config.hudScale, v -> config.hudScale = v);
        y += rowHeight + 14;

        // --- Blacklist (label drawn above in render) -------------------------
        blacklistBox = new EditBox(this.font, centerX - 100, y, 150, 20, Component.literal("Player name"));
        this.addRenderableWidget(blacklistBox);
        this.addRenderableWidget(Button.builder(Component.literal("Add"), b -> {
            String name = blacklistBox.getValue().trim();
            if (!name.isEmpty() && config.blacklist.stream().noneMatch(n -> n.equalsIgnoreCase(name))) {
                config.blacklist.add(name);
                config.save();
                DonutQueueClient.queue.removeAllByName(name);
                blacklistBox.setValue("");
                this.clearWidgets();
                this.init();
            }
        }).bounds(centerX + 55, y, 45, 20).build());
        y += rowHeight + 4;

        // --- Clickable list of currently blacklisted players ------------------
        blacklistListY = y;
        int shown = Math.min(config.blacklist.size(), MAX_BLACKLIST_ROWS_SHOWN);
        for (int i = 0; i < shown; i++) {
            String name = config.blacklist.get(i);
            this.addRenderableWidget(Button.builder(Component.literal(name + "  (click to remove)"), b -> {
                config.blacklist.removeIf(n -> n.equalsIgnoreCase(name));
                config.save();
                this.clearWidgets();
                this.init();
            }).bounds(centerX - 100, y, 200, 16).build());
            y += 18;
        }
        if (config.blacklist.size() > shown) {
            y += 12; // room for the "+N more" note
        }
        if (config.blacklist.isEmpty()) {
            y += 12; // room for "Nobody is blacklisted yet"
        }
        y += 6;

        // --- Done ---------------------------------------------------------
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(centerX - 100, y, 200, 20).build());

        blacklistListY += centerContent();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(guiGraphics);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        neonFaces(guiGraphics, mouseX, mouseY);

        guiGraphics.text(this.font, "Set HUD box color (hex, e.g. 3399FF)",
                this.width / 2 - 100, colorBox.getY() - 12, C_NEON_HI, true);

        guiGraphics.text(this.font, "HUD box size",
                this.width / 2 - 100, sizeButton.getY() - 12, C_NEON_HI, true);

        guiGraphics.text(this.font, "Who would you like to blacklist?",
                this.width / 2 - 100, blacklistBox.getY() - 12, C_NEON_HI, true);

        ModConfig config = DonutQueueClient.config;
        if (config.blacklist.isEmpty()) {
            guiGraphics.text(this.font, "Nobody is blacklisted yet",
                    this.width / 2 - 100, blacklistListY, C_MUTED, true);
        } else if (config.blacklist.size() > MAX_BLACKLIST_ROWS_SHOWN) {
            int extra = config.blacklist.size() - MAX_BLACKLIST_ROWS_SHOWN;
            int noteY = blacklistListY + MAX_BLACKLIST_ROWS_SHOWN * 18;
            guiGraphics.text(this.font, "+" + extra + " more (remove some to see them)",
                    this.width / 2 - 100, noteY, C_MUTED, true);
        }
    }

    /** Parses a 6-digit hex color like "3399FF" (with or without a leading #) into an opaque ARGB int, or null if invalid. */
    static Integer parseHexColor(String raw) {
        if (raw == null) return null;
        String hex = raw.trim().replace("#", "");
        if (hex.length() != 6) return null;
        try {
            return 0xFF000000 | (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
