package com.example.donutqueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Customisation > Chat > Chat Settings > Payment sounds. A sound plays whenever someone pays you, and when
 * Forsurewaterr is the one paying, "the Goat is here" shows on screen for 3 seconds and then fades out.
 */
final class PayAlerts {
    private PayAlerts() {}

    private static final String GOAT = "Forsurewaterr";
    private static final long HOLD_MS = 3000L;
    private static final long FADE_MS = 700L;
    private static volatile long goatAt = 0L;

    /** Called when "X paid you $..." was read from chat. */
    static void onPaid(String player, double amount) {
        ModConfig c = DonutQueueClient.config;
        if (c == null || !c.paySoundsEnabled || amount <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        String name = player == null ? "" : player.startsWith(".") ? player.substring(1) : player;
        if (name.equalsIgnoreCase(GOAT)) goatAt = System.currentTimeMillis();
        mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f)));
    }

    static void render(GuiGraphicsExtractor g) {
        long start = goatAt;
        if (start == 0L) return;
        long age = System.currentTimeMillis() - start;
        if (age > HOLD_MS + FADE_MS) {
            goatAt = 0L;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud.isHidden()) return;
        int alpha = age <= HOLD_MS ? 255 : (int) (255 * (1.0 - (age - HOLD_MS) / (double) FADE_MS));
        if (alpha < 8) return;
        String text = "the Goat is here";
        float scale = 3.0f;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int tw = mc.font.width(text);
        g.pose().pushMatrix();
        g.pose().translate(sw / 2.0f, sh * 0.25f);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, -tw / 2, 0, (alpha << 24) | 0xFFD700, true);
        g.pose().popMatrix();
    }
}
