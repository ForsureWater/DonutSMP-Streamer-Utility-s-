package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Customisation > Chat. Turn the re-styled payment lines and whispers on (both OFF by default) and pick the
 * three colours: player names, the amount when someone paid you, the amount when you paid someone.
 * A live preview underneath shows exactly what chat will look like.
 */
public class ChatConfigScreen extends NeonScreen {

    private static final int DEF_NAME = 0xFF5599FF;
    private static final int DEF_RECEIVED = 0xFF55FF55;
    private static final int DEF_SENT = 0xFF55FF55;

    private final Screen parent;
    private String status = "";
    private int previewY = 0;

    private EditBox nameBox, receivedBox, sentBox;

    public ChatConfigScreen(Screen parent) {
        super(Component.literal("Chat"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig c = DonutQueueClient.config;
        int cxm = this.width / 2;
        int y = 46;

        // two toggles side by side
        this.addRenderableWidget(Button.builder(Component.literal(payLabel()), b -> {
            c.chatPaymentsEnabled = !c.chatPaymentsEnabled;
            c.save();
            b.setMessage(Component.literal(payLabel()));
        }).bounds(cxm - 100, y, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(whisperLabel()), b -> {
            c.chatWhispersEnabled = !c.chatWhispersEnabled;
            c.save();
            b.setMessage(Component.literal(whisperLabel()));
        }).bounds(cxm + 2, y, 98, 20).build());
        y += 20 + 20;

        nameBox = colourRow(cxm, y, c.chatNameColor, DEF_NAME, v -> c.chatNameColor = v, () -> c.chatNameColor, "Name");
        y += 20 + 16;
        receivedBox = colourRow(cxm, y, c.chatReceivedColor, DEF_RECEIVED, v -> c.chatReceivedColor = v, () -> c.chatReceivedColor, "Paid you");
        y += 20 + 16;
        sentBox = colourRow(cxm, y, c.chatSentColor, DEF_SENT, v -> c.chatSentColor = v, () -> c.chatSentColor, "You paid");
        y += 20 + 16;

        previewY = y + 10;
        y += 10 + 4 * 11 + 8;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cxm - 100, y, 200, 20).build());

        previewY += centerContent();
    }

    private static String payLabel() {
        return "Pay colours: " + (DonutQueueClient.config.chatPaymentsEnabled ? "ON" : "OFF");
    }

    private static String whisperLabel() {
        return "Whispers: " + (DonutQueueClient.config.chatWhispersEnabled ? "ON" : "OFF");
    }

    /** [hex box] [Set] [Reset] - the label and live colour swatch are drawn in render. */
    private EditBox colourRow(int cxm, int y, int current, int def, IntConsumer set, IntSupplier get, String what) {
        EditBox box = new EditBox(this.font, cxm - 100, y, 78, 20, Component.literal(what + " colour hex"));
        box.setMaxLength(8);
        box.setValue(String.format("%06X", current & 0xFFFFFF));
        this.addRenderableWidget(box);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> {
            Integer parsed = DonutQueueConfigScreen.parseHexColor(box.getValue());
            if (parsed == null) {
                status = "Enter a 6-digit hex colour, e.g. 5599FF.";
                return;
            }
            set.accept(parsed);
            DonutQueueClient.config.save();
            status = what + " colour set to #" + String.format("%06X", parsed & 0xFFFFFF);
        }).bounds(cxm + 5, y, 45, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            set.accept(def);
            DonutQueueClient.config.save();
            box.setValue(String.format("%06X", def & 0xFFFFFF));
            status = what + " colour reset to default.";
        }).bounds(cxm + 55, y, 45, 20).build());
        return box;
    }

    @Override
    protected String headerText() {
        return "CHAT";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);

        ModConfig c = DonutQueueClient.config;
        int cxm = this.width / 2;

        colourLabel(g, cxm, nameBox, "Player name colour", c.chatNameColor);
        colourLabel(g, cxm, receivedBox, "Amount colour - they paid you", c.chatReceivedColor);
        colourLabel(g, cxm, sentBox, "Amount colour - you paid them", c.chatSentColor);

        // live preview (always drawn, even with the toggles off, so you can see what turning them on does)
        int x = cxm - 100;
        g.text(this.font, "Preview", x, previewY - 11, C_NEON_HI, true);
        g.text(this.font, ChatStyle.recolourPayment(Component.literal("Forsurewaterr paid you $50M.")), x, previewY, 0xFFFFFFFF, true);
        g.text(this.font, ChatStyle.recolourPayment(Component.literal("You paid Forsurewaterr $100M.")), x, previewY + 11, 0xFFFFFFFF, true);
        g.text(this.font, ChatStyle.whisperOut("Forsurewaterr", "1"), x, previewY + 22, 0xFFFFFFFF, true);
        g.text(this.font, ChatStyle.whisperIn("Forsurewaterr", "hello"), x, previewY + 33, 0xFFFFFFFF, true);

        if (!status.isEmpty()) {
            g.text(this.font, status, cxm - this.font.width(status) / 2, this.height - 14, C_WARN, true);
        }
    }

    /** Label above a hex box + a small swatch of the colour being typed (or the saved one if it isn't valid yet). */
    private void colourLabel(GuiGraphicsExtractor g, int cxm, EditBox box, String text, int saved) {
        g.text(this.font, text, cxm - 100, box.getY() - 11, C_NEON_HI, true);
        Integer typed = DonutQueueConfigScreen.parseHexColor(box.getValue());
        int col = typed != null ? typed : (saved | 0xFF000000);
        g.fill(cxm - 18, box.getY(), cxm - 2, box.getY() + 20, col);
        Gfx.outline(g, cxm - 18, box.getY(), 16, 20, C_NEON);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
