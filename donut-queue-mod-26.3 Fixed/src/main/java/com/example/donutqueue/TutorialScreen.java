package com.example.donutqueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The guide window of a tutorial: explains the current step and has the buttons to try / simulate / go on. */
public class TutorialScreen extends NeonScreen {

    private static final int W = 360;
    private static final int H = 214;

    private int left;
    private int top;
    private int builtIndex = -1;

    public TutorialScreen() {
        super(Component.literal("Tutorial"));
    }

    /** Splits text into lines no wider than maxW (keeps \n as line breaks). */
    static List<String> wrap(Font font, String text, int maxW) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (font.width(test) > maxW && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    @Override
    protected boolean showPanel() {
        return false;
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = Math.max(2, (this.height - H) / 2);
        builtIndex = Tutorial.index();
        Tutorial.Step st = Tutorial.current();
        if (st == null) return;

        // row A: what to do
        List<Object[]> acts = new ArrayList<>(); // {label, Runnable}
        if (st.menuPage >= 0) {
            acts.add(new Object[]{"OPEN MENU", (Runnable) () -> {
                FuturisticMenuScreen.setLastPage(st.menuPage);
                Tutorial.waitingWorld = false;
                Tutorial.guidePending = false;
                this.minecraft.gui.setScreen(new FuturisticMenuScreen(null));
            }});
        }
        if (st.simId != null) {
            acts.add(new Object[]{st.simLabel, (Runnable) () -> {
                String r = DonutQueueClient.simulate(st.simId);
                Tutorial.lastResult = r;
            }});
        }
        // "do it in the game" steps (no menu involved) get a TRY IN GAME button, also the ones that have a simulate button
        // (lever / note block / command skipping)
        if (st.trigger != null && st.menuPage < 0 && (st.simId == null || st.trigger.equals("SKIP"))) {
            acts.add(new Object[]{"TRY IN GAME", (Runnable) this::tryIt});
        } else if (st.trigger != null && st.menuPage >= 0 && st.trigger.startsWith("LEVER_")) {
            acts.add(new Object[]{"TRY IN GAME", (Runnable) this::tryIt});
        }
        if (!acts.isEmpty()) {
            int gap = 6;
            int bw = (W - 28 - gap * (acts.size() - 1)) / acts.size();
            for (int i = 0; i < acts.size(); i++) {
                final Runnable r = (Runnable) acts.get(i)[1];
                this.addRenderableWidget(Button.builder(Component.literal((String) acts.get(i)[0]), b -> r.run())
                        .bounds(left + 14 + i * (bw + gap), top + H - 56, bw, 20).build());
            }
        }

        // row B: navigation
        Button back = this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            Tutorial.back();
        }).bounds(left + 14, top + H - 30, 80, 20).build());
        back.active = Tutorial.index() > 0;
        this.addRenderableWidget(Button.builder(Component.literal("Exit tutorial"), b -> {
            Tutorial.stop();
            this.minecraft.gui.setScreen(null);
        }).bounds(left + (W - 100) / 2, top + H - 30, 100, 20).build());
        String nextLabel = Tutorial.isLast() ? "Finish" : "Next";
        this.addRenderableWidget(Button.builder(Component.literal(nextLabel), b -> {
            if (Tutorial.isLast()) {
                Tutorial.stop();
                this.minecraft.gui.setScreen(null);
            } else {
                Tutorial.next();
            }
        }).bounds(left + W - 14 - 80, top + H - 30, 80, 20).build());
    }

    private void tryIt() {
        Tutorial.waitingWorld = true;
        Tutorial.guidePending = false;
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public void onClose() {
        // Esc on a "do it" step = go and do it; on a plain step = leave the tutorial
        Tutorial.Step st = Tutorial.current();
        if (st != null && st.trigger != null) {
            Tutorial.waitingWorld = true;
            Tutorial.guidePending = false;
        } else {
            Tutorial.stop();
        }
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (builtIndex != Tutorial.index()) {
            this.clearWidgets();
            this.init();
        }
        Tutorial.Step st = Tutorial.current();
        neonBackdrop(g);

        if (Theme.glow) {
            Gfx.outline(g, left - 2, top - 2, W + 4, H + 4, Theme.neonA(0x22));
            Gfx.outline(g, left - 1, top - 1, W + 2, H + 2, Theme.neonA(0x55));
        }
        g.fill(left, top, left + W, top + H, C_PANEL);
        g.fill(left + 1, top + 1, left + W - 1, top + 23, C_HEADER);
        g.fill(left + 1, top + 23, left + W - 1, top + 24, C_NEON);
        g.text(this.font, "TUTORIAL - " + Tutorial.name(), left + 10, top + 8, C_NEON_HI, true);
        String prog = "Step " + (Tutorial.index() + 1) + " / " + Tutorial.count();
        g.text(this.font, prog, left + W - 10 - this.font.width(prog), top + 8, C_MUTED, true);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);

        if (st != null) {
            g.text(this.font, st.title, left + 14, top + 34, C_NEON_HI, true);
            List<String> lines = wrap(this.font, Tutorial.fill(st.text), W - 28);
            int y = top + 50;
            for (String ln : lines) {
                if (y > top + H - 88) break;
                g.text(this.font, ln, left + 14, y, C_TEXT, true);
                y += 10;
            }
            if (st.trigger != null) {
                g.text(this.font, "> Waiting for you to do it...", left + 14, top + H - 84, C_WARN, true);
            }
            if (!Tutorial.lastResult.isEmpty()) {
                g.text(this.font, fitText(Tutorial.lastResult, W - 28), left + 14, top + H - 72, C_GOOD, true);
            }
        }
    }
}
