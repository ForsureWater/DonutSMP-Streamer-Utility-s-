package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Short list of everything that is new in this version. Scroll with the wheel or the Up / Down buttons. */
public class NewAdditionsScreen extends NeonScreen {

    private static final int W = 400;
    private static final int H = 252;

    private static final int C_REMOVED = 0xFFFF6B6B;

    private static final String[] TAB_NAMES = {"Additions", "Changes", "Removed"};

    /** A big header with {title, text} items under it. A paragraph starting with "!" is drawn highlighted. Paragraphs are separated by \n. */
    private record Section(String header, String[][] items) {}

    /** One list of sections per tab: 0 = Additions, 1 = Changes, 2 = Removed. */
    private static final Section[][] TABS = {
            // ---- Additions ----
            {
                    new Section("SCREEN FEATURES", new String[][]{
                            {"Screen features button",
                                    "New button in Customisation with three things you can put on your screen. All of them are OFF until you turn them on."},
                            {"On Screen KBM",
                                    "Shows your keys live. Pick how much shows: WASD, WASD + Mouse, WASD + Space + Mouse or the full keyboard (Ctrl, Shift, Space) + Mouse. LMB and RMB light up when you click."},
                            {"CPS counter",
                                    "Shows your left and right clicks per second like 7 | 3 CPS. Its default spot is just under the mouse buttons."},
                            {"FPS counter",
                                    "Shows your current frames per second."},
                            {"Move, colour and size",
                                    "One Move button opens a screen with all three boxes on it, and you drag each one on its own. One colour box (type a hex colour and press Set, or Reset) colours them all. One Size button changes all three.\n!Everything here is OFF by default."},
                    }),
                    new Section("MAIN MENU", new String[][]{
                            {"Discord button",
                                    "A Discord button at the top right of the menu opens the Discord invite in your browser."},
                            {"Spinning skin",
                                    "On the Main Menu your own skin turns slowly in 3D, as big as the card allows. On every other page the card shows your skin head with your exact balance under it."},
                    }),
                    new Section("EARNINGS", new String[][]{
                            {"Removed players list",
                                    "New Removed players button on the Earnings page (and Removed under the right-hand list). It looks like the Payments log: who, how much you got from or paid them, a name search, a date box and a scrollbar. Click a name to bring them back."},
                    }),
                    new Section("GUARDS", new String[][]{
                            {"Quick Buy",
                                    "The Auction Guard now also works in the Quick Buy menu, and it asks you to confirm on every click, not just the first one."},
                            {"Short or full amounts",
                                    "Warnings now say $175M instead of $175,000,000. Each guard has a button to switch to the whole number.\n!Short is the default."},
                            {"Coloured pay warning",
                                    "In the pay confirm menu the player name is blue and the amount is green."},
                            {"Debug command",
                                    "/dq guarddebug shows the title and lore of any item you click, so a listing that is not detected can be fixed quickly."},
                    }),
                    new Section("CHAT", new String[][]{
                            {"Chat Colours and Chat Settings",
                                    "Customisation > Chat now has two buttons. Chat Colours has all the colour options from before. Chat Settings has the new options below."},
                            {"Chat saves",
                                    "When you leave a server and join back, your last 100 chat lines are put back instead of being deleted. You can turn it ON or OFF in Chat Settings.\n!ON by default."},
                            {"Payment sounds",
                                    "Plays a sound when someone pays you. Turn it ON or OFF in Chat Settings.\n!OFF by default."},
                            {"You bought",
                                    "In \"You bought 64 Netherrack for $ 19.9K\" the item amount, the item name and the price are green."},
                    }),
            },
            // ---- Changes ----
            {
                    new Section("BALANCE", new String[][]{
                            {"Negative balance",
                                    "If your balance goes into minus, the balance card says Please do /bal instead of showing the number."},
                    }),
                    new Section("GUARDS", new String[][]{
                            {"Better price reading",
                                    "The Auction Guard now reads Price, Cost or Buy Now lines, understands small caps text and falls back to any $ amount on a shop style screen."},
                    }),
                    new Section("CHAT", new String[][]{
                            {"Pay colours on by default",
                                    "Pay colours are now ON with the player name in blue. The amount is green for payments you got and for payments you made (it used to be red)."},
                    }),
            },
            // ---- Removed ----
            {
                    new Section("MENU", new String[][]{
                            {"Extra texts",
                                    "The info texts under the Customisation buttons, the Queue info lines and the SYSTEM box are gone, and so is the ESC - close hint and the Click a name = non gamble hint."},
                            {"Balance graph card",
                                    "The old balance graph card at the top right was replaced by the Discord button and the skin."},
                    }),
            },
    };

    private final Screen parent;
    private int left;
    private int top;
    private int scroll = 0;
    private int tab = 0;

    private static final int K_HEADER = 0, K_TITLE = 1, K_TEXT = 2, K_GAP = 3, K_DIVIDER = 4;

    /** One drawn row of the list; h is its height in pixels. */
    private record Row(int kind, String text, int color, int h) {}

    private final List<Row> rows = new ArrayList<>();

    public NewAdditionsScreen(Screen parent) {
        super(Component.literal("New additions"));
        this.parent = parent;
    }

    @Override
    protected boolean showPanel() {
        return false;
    }

    /** Pixel area the rows are drawn in. */
    private int viewTop() {
        return top + 56;
    }

    private int viewBottom() {
        return top + H - 36;
    }

    /** How many rows fit when the list starts at row `from`. */
    private int rowsFitting(int from) {
        int used = 0;
        int n = 0;
        for (int i = from; i < rows.size(); i++) {
            if (used + rows.get(i).h() > viewBottom() - viewTop()) break;
            used += rows.get(i).h();
            n++;
        }
        return n;
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = Math.max(2, (this.height - H) / 2);

        buildRows();
        clampScroll();

        int tw = (W - 28 - 8) / 3;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            this.addRenderableWidget(Button.builder(Component.literal(TAB_NAMES[i]), b -> {
                tab = idx;
                scroll = 0;
                buildRows();
                clampScroll();
            }).bounds(left + 14 + i * (tw + 4), top + 30, tw, 18).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(left + 14, top + H - 30, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Up"), b -> {
            scroll -= 5;
            clampScroll();
        }).bounds(left + W - 14 - 100 - 4 - 100, top + H - 30, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Down"), b -> {
            scroll += 5;
            clampScroll();
        }).bounds(left + W - 14 - 100, top + H - 30, 100, 20).build());
    }

    /** Builds the rows of the open tab (colours are read here so they follow the menu theme). */
    private void buildRows() {
        rows.clear();
        int headColor = tab == 2 ? C_REMOVED : (tab == 1 ? C_WARN : C_NEON_HI);
        Section[] sections = TABS[tab];
        for (int si = 0; si < sections.length; si++) {
            Section sec = sections[si];
            if (si > 0) {
                rows.add(new Row(K_GAP, "", 0, 4));
                rows.add(new Row(K_DIVIDER, "", 0, 7));
                rows.add(new Row(K_GAP, "", 0, 4));
            }
            rows.add(new Row(K_HEADER, sec.header(), headColor, 24));
            for (String[] item : sec.items()) {
                rows.add(new Row(K_TITLE, item[0], tab == 2 ? C_REMOVED : C_TEXT, 15));
                for (String para : item[1].split("\n")) {
                    boolean hl = para.startsWith("!");
                    String text = hl ? para.substring(1) : para;
                    for (String ln : TutorialScreen.wrap(this.font, text, W - 36)) {
                        rows.add(new Row(K_TEXT, ln, hl ? C_WARN : C_TEXT, 10));
                    }
                }
                rows.add(new Row(K_GAP, "", 0, 6));
            }
        }
    }

    private void clampScroll() {
        int max = 0;
        while (max < rows.size() && max + rowsFitting(max) < rows.size()) max++;
        // `max` is the first start row from which the end of the list is fully visible
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scroll -= (int) Math.signum(scrollY) * 3;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(g);
        if (Theme.glow) {
            Gfx.outline(g, left - 2, top - 2, W + 4, H + 4, Theme.neonA(0x22));
            Gfx.outline(g, left - 1, top - 1, W + 2, H + 2, Theme.neonA(0x55));
        }
        g.fill(left, top, left + W, top + H, C_PANEL);
        g.fill(left + 1, top + 1, left + W - 1, top + 23, C_HEADER);
        g.fill(left + 1, top + 23, left + W - 1, top + 24, C_NEON);
        String head = "NEW ADDITIONS - v1.9.21";
        g.text(this.font, head, left + (W - this.font.width(head)) / 2, top + 8, C_NEON_HI, true);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);

        // the open tab stays lit
        {
            int tw = (W - 28 - 8) / 3;
            int bx = left + 14 + tab * (tw + 4);
            g.fill(bx, top + 30, bx + tw, top + 48, Theme.btnAct);
            Gfx.outline(g, bx, top + 30, tw, 18, C_NEON_HI);
            g.fill(bx, top + 30, bx + 2, top + 48, C_NEON_HI);
            String t = TAB_NAMES[tab];
            g.text(this.font, t, bx + (tw - this.font.width(t)) / 2, top + 35, 0xFFFFFFFF, false);
        }

        int y = viewTop();
        int x = left + 18;
        int n = rowsFitting(scroll);
        for (int i = 0; i < n; i++) {
            Row r = rows.get(scroll + i);
            switch (r.kind()) {
                case K_HEADER -> {
                    drawScaled(g, r.text(), x, y + 1, 1.8f, r.color());
                    g.fill(x, y + r.h() - 4, left + W - 18, y + r.h() - 3, r.color());
                }
                case K_TITLE -> drawScaled(g, r.text(), x, y + 1, 1.25f, r.color());
                case K_TEXT -> g.text(this.font, r.text(), x, y, r.color(), true);
                case K_DIVIDER -> g.fill(left + 10, y + 2, left + W - 10, y + 4, C_NEON);
                default -> { }
            }
            y += r.h();
        }
        if (rows.size() > 0 && (scroll > 0 || scroll + n < rows.size())) {
            String more = (scroll + n < rows.size()) ? "scroll for more" : "end";
            g.text(this.font, more, left + W / 2 - this.font.width(more) / 2, top + H - 24, C_MUTED, false);
        }
    }

    private void drawScaled(GuiGraphicsExtractor g, String text, int x, int y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);
        g.text(this.font, text, 0, 0, color, true);
        g.pose().popMatrix();
    }
}
