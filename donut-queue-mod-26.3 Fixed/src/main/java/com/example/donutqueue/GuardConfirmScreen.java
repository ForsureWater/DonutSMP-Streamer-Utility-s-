package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** The "Are you sure?" popup used by the Pay Guard and the Auction Guard. Esc = cancel. */
final class GuardConfirmScreen extends NeonScreen {

    private final String header;
    private final Component message;
    private final Screen back;
    private final Runnable onConfirm;

    private List<FormattedCharSequence> lines = List.of();
    private int px, py, pw, ph;

    GuardConfirmScreen(String header, String message, Screen back, Runnable onConfirm) {
        this(header, Component.literal(message), back, onConfirm);
    }

    GuardConfirmScreen(String header, Component message, Screen back, Runnable onConfirm) {
        super(Component.literal(header));
        this.header = header;
        this.message = message;
        this.back = back;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        pw = Math.min(300, this.width - 20);
        lines = this.font.split(message, pw - 24);
        int textH = lines.size() * 11;
        ph = 24 + 12 + textH + 14 + 20 + 14;
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        int by = py + 24 + 12 + textH + 14;
        int bw = (pw - 24 - 6) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Confirm"), b -> {
            this.minecraft.gui.setScreen(back);
            onConfirm.run();
        }).bounds(px + 12, by, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(px + 12 + bw + 6, by, bw, 20).build());
    }

    @Override
    protected boolean showPanel() {
        return false; // we draw our own, taller panel
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(g); // just the dim, showPanel() is false
        if (Theme.glow) {
            Gfx.outline(g, px - 2, py - 2, pw + 4, ph + 4, Theme.neonA(0x22));
            Gfx.outline(g, px - 1, py - 1, pw + 2, ph + 2, Theme.neonA(0x55));
        }
        g.fill(px, py, px + pw, py + ph, C_PANEL);
        g.fill(px + 1, py + 1, px + pw - 1, py + 23, C_HEADER);
        g.fill(px + 1, py + 23, px + pw - 1, py + 24, C_NEON);
        g.text(this.font, header, px + (pw - this.font.width(header)) / 2, py + 8, C_NEON_HI, true);

        int ty = py + 24 + 12;
        for (FormattedCharSequence l : lines) {
            g.text(this.font, l, px + (pw - this.font.width(l)) / 2, ty, C_TEXT, false);
            ty += 11;
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(back);
    }
}
