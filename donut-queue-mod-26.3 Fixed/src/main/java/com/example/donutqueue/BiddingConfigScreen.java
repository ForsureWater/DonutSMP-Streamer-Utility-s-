package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Side menu of the Bidding page in the neon menu. Only what the main page does NOT have:
 * the box colour and the bidding blacklist.
 */
public class BiddingConfigScreen extends NeonScreen {

    private static final int MAX_BLACKLIST_ROWS_SHOWN = 6;

    private final Screen parent;
    private EditBox blacklistBox;
    private EditBox colorBox;
    private Button sizeButton;
    private int blacklistListY;

    public BiddingConfigScreen(Screen parent) {
        super(Component.literal("Bidding"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = DonutQueueClient.config;
        int centerX = this.width / 2;
        int y = 46;
        int rowHeight = 24;

        // --- Box color (label drawn above in render) -------------------------
        colorBox = new EditBox(this.font, centerX - 100, y, 150, 20, Component.literal("Hex color"));
        colorBox.setMaxLength(8);
        colorBox.setValue(String.format("%06X", config.biddingBoxColor & 0xFFFFFF));
        this.addRenderableWidget(colorBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> {
            Integer parsed = DonutQueueConfigScreen.parseHexColor(colorBox.getValue());
            if (parsed != null) {
                config.biddingBoxColor = parsed;
                config.save();
            }
        }).bounds(centerX + 55, y, 45, 20).build());
        y += rowHeight + 14;

        // --- Box size (label drawn above in render) --------------------------
        sizeButton = addSizeRow(centerX, y, () -> config.biddingBoxScale, v -> config.biddingBoxScale = v);
        y += rowHeight + 14;

        // --- Blacklist (label drawn above in render) -------------------------
        blacklistBox = new EditBox(this.font, centerX - 100, y, 150, 20, Component.literal("Player name"));
        this.addRenderableWidget(blacklistBox);
        this.addRenderableWidget(Button.builder(Component.literal("Add"), b -> {
            String name = blacklistBox.getValue().trim();
            if (!name.isEmpty() && config.biddingBlacklist.stream().noneMatch(n -> n.equalsIgnoreCase(name))) {
                config.biddingBlacklist.add(name);
                config.save();
                blacklistBox.setValue("");
                this.clearWidgets();
                this.init();
            }
        }).bounds(centerX + 55, y, 45, 20).build());
        y += rowHeight + 4;

        // --- Clickable list of currently blacklisted players ------------------
        blacklistListY = y;
        int shown = Math.min(config.biddingBlacklist.size(), MAX_BLACKLIST_ROWS_SHOWN);
        for (int i = 0; i < shown; i++) {
            String name = config.biddingBlacklist.get(i);
            this.addRenderableWidget(Button.builder(Component.literal(name + "  (click to remove)"), b -> {
                config.biddingBlacklist.removeIf(n -> n.equalsIgnoreCase(name));
                config.save();
                this.clearWidgets();
                this.init();
            }).bounds(centerX - 100, y, 200, 16).build());
            y += 18;
        }
        if (config.biddingBlacklist.size() > shown) {
            y += 12;
        }
        if (config.biddingBlacklist.isEmpty()) {
            y += 12;
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

        guiGraphics.text(this.font, "Set Bidding box color (hex, e.g. FFAA33)",
                this.width / 2 - 100, colorBox.getY() - 12, C_NEON_HI, true);

        guiGraphics.text(this.font, "Bidding box size",
                this.width / 2 - 100, sizeButton.getY() - 12, C_NEON_HI, true);

        guiGraphics.text(this.font, "Who should be ignored for bidding?",
                this.width / 2 - 100, blacklistBox.getY() - 12, C_NEON_HI, true);

        ModConfig config = DonutQueueClient.config;
        if (config.biddingBlacklist.isEmpty()) {
            guiGraphics.text(this.font, "Nobody is blacklisted yet",
                    this.width / 2 - 100, blacklistListY, C_MUTED, true);
        } else if (config.biddingBlacklist.size() > MAX_BLACKLIST_ROWS_SHOWN) {
            int extra = config.biddingBlacklist.size() - MAX_BLACKLIST_ROWS_SHOWN;
            int noteY = blacklistListY + MAX_BLACKLIST_ROWS_SHOWN * 18;
            guiGraphics.text(this.font, "+" + extra + " more (remove some to see them)",
                    this.width / 2 - 100, noteY, C_MUTED, true);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
