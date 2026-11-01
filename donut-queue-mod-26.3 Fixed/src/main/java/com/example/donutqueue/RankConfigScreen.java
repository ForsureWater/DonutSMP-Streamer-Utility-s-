package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Customisation > Ranks. Top button turns rank colours on/off (names in the tab list, over heads and on
 * DonutSMP's floating tags get recoloured by rank); below it you can change the staff team colour.
 */
public class RankConfigScreen extends NeonScreen {

    private static final int DEFAULT_STAFF = 0xFF5555FF;

    private final Screen parent;
    private Button enableButton;
    private EditBox colorBox;
    private String status = "";

    public RankConfigScreen(Screen parent) {
        super(Component.literal("Ranks"));
        this.parent = parent;
    }

    private static String enableLabel() {
        return "Enable Rank colours: " + (DonutQueueClient.config.rankColorsEnabled ? "ON" : "OFF");
    }

    @Override
    protected void init() {
        ModConfig config = DonutQueueClient.config;
        int centerX = this.width / 2;
        int y = 46;

        // --- Enable Rank colours (top) ---------------------------------------
        enableButton = this.addRenderableWidget(Button.builder(Component.literal(enableLabel()), b -> {
            config.rankColorsEnabled = !config.rankColorsEnabled;
            config.save();
            b.setMessage(Component.literal(enableLabel()));
            status = config.rankColorsEnabled ? "Rank colours enabled." : "Rank colours disabled.";
        }).bounds(centerX - 100, y, 200, 20).build());
        y += 20 + 8 + 12 + 12;

        // --- Change staff team colour (label drawn above in render) ----------
        colorBox = new EditBox(this.font, centerX - 100, y, 78, 20, Component.literal("Staff colour hex"));
        colorBox.setMaxLength(8);
        colorBox.setValue(String.format("%06X", config.staffColor & 0xFFFFFF));
        this.addRenderableWidget(colorBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> {
            Integer parsed = DonutQueueConfigScreen.parseHexColor(colorBox.getValue());
            if (parsed == null) {
                status = "Enter a 6-digit hex colour, e.g. 5555FF.";
                return;
            }
            config.staffColor = parsed;
            config.save();
            status = "Staff team colour set to #" + String.format("%06X", parsed & 0xFFFFFF);
        }).bounds(centerX + 5, y, 45, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            config.staffColor = DEFAULT_STAFF;
            config.save();
            colorBox.setValue(String.format("%06X", DEFAULT_STAFF & 0xFFFFFF));
            status = "Staff team colour reset to default.";
        }).bounds(centerX + 55, y, 45, 20).build());
        y += 20 + 14;

        // --- Done ------------------------------------------------------------
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(centerX - 100, y, 200, 20).build());

        centerContent();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);

        int cx = this.width / 2;
        g.text(this.font, "Change staff team colour (hex, e.g. 5555FF)", cx - 100, colorBox.getY() - 12, C_NEON_HI, true);

        // live preview of the typed colour, between the box and the Set button
        Integer preview = DonutQueueConfigScreen.parseHexColor(colorBox.getValue());
        int col = preview != null ? preview : (DonutQueueClient.config.staffColor | 0xFF000000);
        g.fill(cx - 18, colorBox.getY(), cx - 2, colorBox.getY() + 20, col);
        Gfx.outline(g, cx - 18, colorBox.getY(), 16, 20, C_NEON);

        if (!status.isEmpty()) {
            g.text(this.font, status, cx - this.font.width(status) / 2, this.height - 14, C_WARN, true);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
