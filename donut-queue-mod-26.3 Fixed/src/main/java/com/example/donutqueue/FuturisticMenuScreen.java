package com.example.donutqueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The dark-blue neon "DonutSMP Streamer Utilitys" menu.
 *
 * Left: sidebar with the main menu + one page per option (Anti Gamble Skip,
 * Starting Bal, Bidding, Info Display, Earnings).
 * Right rail: mini balance graph (amount on top, your skin + name at the bottom).
 * Main page: START BAL (starts at 0 - the real balance is only the small number
 * underneath) and the all-time balance graph, 0 at the bottom and 2x the starting
 * balance at the top.
 *
 * All the buttons are ordinary vanilla Buttons (so clicking is 100% reliable);
 * their look is painted on top in neon after the widgets render.
 */
public class FuturisticMenuScreen extends Screen {

    // --- Palette (dark blue neon) --------------------------------------
    private static final int C_GOOD       = 0xFF33E6A0;
    private static final int C_BAD        = 0xFFFF5577;
    private static final int C_WARN       = 0xFFFFC857;

    private static final String[] PAGES = {
            "Main Menu", "Anti Gamble Skip", "Starting Bal", "Bidding", "Info Display", "Earnings", "Sessions", "Customisation"
    };

    /** Remembers which page you were on when the screen is reopened. */
    private static int lastPage = 0;

    /** Lets the tutorial open the menu on a given page. */
    static void setLastPage(int p) {
        lastPage = Math.max(0, Math.min(p, PAGES.length - 1));
    }

    private static final String[] TUTORIAL_IDS = {"MAIN", "QUEUE", "STARTBAL", "BIDDING", "INFO", "EARNINGS", "SESSIONS", "CUSTOM"};

    /** Normal menu. */
    static final int EDIT_NONE = 0;
    /** Menu customisation > Main menu items: the real main page, cards can be grabbed, dropped and shown / hidden. */
    static final int EDIT_MAIN = 1;
    /** Menu customisation > Sidebar tabs: the real menu, dimmed, with the sidebar live - click a tab to hide / show it. */
    static final int EDIT_TABS = 2;

    static final String[] MAIN_IDS = {"START_BAL", "TODAY", "GRAPH", "STATS", "BUTTONS"};
    static final String[] MAIN_NAMES = {"Start Bal", "Today", "Graph", "Stats row", "Buttons"};

    /** What the 4 bottom button slots do out of the box. */
    static final String[] DEFAULT_BUTTONS = {"SESSION", "STATS", "BIDDING", "TRACKING"};
    /** Everything a bottom button can be set to (click a button in Menu customisation > Main menu items). */
    static final String[] ACT_IDS = {"SESSION", "STATS", "BIDDING", "TRACKING", "LEVER", "EARNINGS", "SESSIONS", "QUEUE"};
    static final String[] ACT_NAMES = {"Session start/end", "Stats page", "Bidding start/stop", "Tracking on/reset",
            "Lever pause/resume", "Earnings page", "Sessions page", "Anti Gamble page"};
    private static final int MAIN_GAP = 4;

    private final Screen parent;
    private final int editMode;
    /** Edit Main: the card that has been picked up (null = none). */
    private String carrying = null;
    private int page = lastPage;
    private boolean pendingRebuild = false;
    /** Edit Main: the popup that is open ("SLOT0".."SLOT3" = a bottom button, "GRAPH" = the graph), null = none. */
    private String pickerFor = null;

    // page-switch animation (Customisation > Menu customisation > Animations)
    private int shownPage = lastPage;
    private long transStart = 0;
    private int transDir = 1;
    /** The sidebar's own buttons - they never move during a slide. */
    private final List<AbstractWidget> sideWidgets = new ArrayList<>();
    private int sideCount = 0;

    /** Earnings page: false = normal settings, true = the Stats view (big graph + per-day graphs). */
    private boolean statsView = false;
    /** Stats view: -1 = the all-time overview, otherwise the index of one day in DailyEarnings.allDays() (last = today). */
    private static int statsDayIdx = Integer.MAX_VALUE;
    /** First day shown in the little day strip under the graph (it shows 30 days at a time). */
    private static int stripStart = Integer.MAX_VALUE;
    private static final int STRIP_DAYS = 30;
    private EditBox statsDateBox;
    private static String statsDateText = "";
    private EditBox sessDateBox;
    private static String sessFilter = "";
    private final List<Button> rowZones = new ArrayList<>();
    private final List<Button> delZones = new ArrayList<>();
    /** Sessions page: true = one session's graph is open, false = the list of sessions. */
    private boolean sessionView = false;
    private Sessions.Session openSession = null;
    /** Sessions list: which page of the past sessions is showing. */
    private static int sessPage = 0;
    private String statusLine = "";

    /** Payments log (Earnings page = all payments, Sessions page = the open session's payments). */
    private boolean logView = false;
    private int logScroll = 0;
    /** 0 = everything, 1 = only payments you got, 2 = only payments you made. */
    private int logFilter = 0;
    /** Payments log: name search (case-insensitive, matches part of a name). */
    private String logSearch = "";
    private EditBox logSearchBox;
    /** Payments log: day filter. Empty = all time, otherwise only payments from that day (see parseDate). */
    private String logDate = "";
    private EditBox logDateBox;
    /** Totals of everything that matches the name search (before the All / Got / Paid filter). */
    private double logGotSum = 0, logPaidSum = 0;
    private int logGotN = 0, logPaidN = 0;
    /** Scrollbar dragging (the mouse button is polled each frame). */
    private boolean logDrag = false, logWasDown = false;
    private List<PaymentLog.Payment> logCache = new ArrayList<>();
    private String logCacheKey = "";

    // layout (computed in init)
    private int px, py, pw, ph;
    private int sideW, railW, headerH;
    private int cx, cy, cw, ch;
    private int rx;

    // text inputs of the current page
    private EditBox minBox, goalBox, bidTimeBox, bidMinBox;

    private static final class NeonBtn {
        int x, y, w, h;
        Supplier<String> label;
        BooleanSupplier active;
    }

    private static final class Label {
        int x, y;
        String text;
    }

    private final List<NeonBtn> neon = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<EditBox> boxes = new ArrayList<>();

    public FuturisticMenuScreen(Screen parent) {
        this(parent, EDIT_NONE);
    }

    FuturisticMenuScreen(Screen parent, int editMode) {
        super(Component.literal("DonutSMP Streamer Utilitys"));
        this.parent = parent;
        this.editMode = editMode;
        Theme.apply(DonutQueueClient.config);
        if (editMode != EDIT_NONE || isTabHidden(page)) {
            page = 0;
            shownPage = 0;
        }
    }

    /** Pages 1-6 (Anti Gamble Skip .. Sessions) can be hidden from the sidebar; Main Menu and Customisation never are. */
    static boolean isTabHidden(int idx) {
        if (idx <= 0 || idx >= PAGES.length - 1) return false;
        if (idx == 3) return true; // Bidding lives in the hotkey menu now, its tab is gone from the sidebar
        List<String> hidden = DonutQueueClient.config.menuHiddenTabs;
        return hidden != null && hidden.contains(PAGES[idx]);
    }

    /** Names of the tabs that can be hidden, in sidebar order. */
    static String[] hideableTabs() {
        String[] out = new String[PAGES.length - 2];
        System.arraycopy(PAGES, 1, out, 0, out.length);
        return out;
    }

    // ------------------------------------------------------------------
    // Layout / widgets
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        pw = Math.min(this.width - 12, 470);
        ph = Math.min(this.height - 12, 252);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        sideW = 104;
        railW = 108;
        headerH = 34;

        rx = px + pw - railW - 6;
        cx = px + sideW + 8;
        cy = py + headerH + 8;
        cw = rx - 8 - cx;
        ch = py + ph - 8 - cy;

        buildWidgets();
    }

    private void buildWidgets() {
        neon.clear();
        labels.clear();
        boxes.clear();
        rowZones.clear();
        delZones.clear();
        minBox = null;
        goalBox = null;
        bidTimeBox = null;
        bidMinBox = null;
        sideWidgets.clear();
        sideCount = 0;

        if (editMode != EDIT_NONE) {
            buildEditWidgets();
            return;
        }

        // Sidebar navigation (tabs the player hid in Menu customisation are skipped)
        sideWidgets.clear();
        int row = 0;
        for (int i = 0; i < PAGES.length; i++) {
            if (isTabHidden(i)) continue;
            final int idx = i;
            sideWidgets.add(addNeon(px + 6, py + headerH + 6 + row * 23, sideW - 10, 20,
                    () -> PAGES[idx], () -> page == idx, () -> {
                        page = idx;
                        lastPage = idx;
                        statsView = false;
                        sessionView = false;
                        openSession = null;
                        logView = false;
                        remView = false;
                        logScroll = 0;
                        statusLine = "";
                        pendingRebuild = true;
                    }));
            row++;
        }

        // Addon tabs (separate mods registered under "donutqueue-addon", e.g. Spotify HUD):
        // one extra sidebar row each, as long as it fits above the "ESC - close" hint.
        for (DonutQueueAddon addon : AddonRegistry.all()) {
            if (py + headerH + 6 + row * 23 + 20 > py + ph - 16) break;
            final DonutQueueAddon a = addon;
            sideWidgets.add(addNeon(px + 6, py + headerH + 6 + row * 23, sideW - 10, 20,
                    () -> addonLabel(a), () -> false, () -> openAddon(a)));
            row++;
        }
        sideCount = row;

        // "New additions" next to the title in the header
        sideWidgets.add(addNeon(px + 38 + this.font.width("DonutSMP Streamer Utilitys") + 12, py + 8, 88, 18,
                () -> "New additions", () -> false,
                () -> this.minecraft.gui.setScreen(new NewAdditionsScreen(this))));

        // Discord button: top of the right rail, above the spinning skin
        sideWidgets.add(addNeon(rx, py + 6, railW, 16,
                () -> "Discord", () -> false,
                () -> com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create("https://discord.gg/tVEsazp3Cv"))));

        // Tutorial for the page that is open: bottom of the right rail (stays put while pages slide)
        final int tutPage = page;
        sideWidgets.add(addNeon(rx, py + ph - 26, railW, 18,
                () -> "Tutorial", () -> false, () -> {
                    Tutorial.start(TUTORIAL_IDS[Math.max(0, Math.min(tutPage, TUTORIAL_IDS.length - 1))]);
                    this.minecraft.gui.setScreen(new TutorialScreen());
                }));

        switch (page) {
            case 1 -> buildQueuePage();
            case 2 -> buildStartBalPage();
            case 3 -> buildBiddingPage();
            case 4 -> buildInfoPage();
            case 5 -> {
                if (remView) buildRemovedPage();
                else if (logView) buildPaymentLogPage();
                else if (statsView) buildEarningsStatsPage();
                else buildEarningsPage();
                buildTopSwapButton();
            }
            case 6 -> {
                if (sessionView) {
                    if (remView) buildRemovedPage();
                    else if (logView) buildPaymentLogPage();
                    else buildSessionDetailPage();
                    buildTopSwapButton();
                } else {
                    buildSessionsListPage();
                }
            }
            case 7 -> buildCustomisationPage();
            default -> buildMainPage();
        }
    }

    private static String addonLabel(DonutQueueAddon a) {
        try {
            String l = a.menuLabel();
            return l == null ? "Addon" : l;
        } catch (Throwable t) {
            return "Addon";
        }
    }

    /** Opens the screen of an addon; if the addon misbehaves the menu just stays open. */
    private void openAddon(DonutQueueAddon a) {
        try {
            Screen s = a.createScreen(this);
            if (s != null) this.minecraft.gui.setScreen(s);
        } catch (Throwable t) {
            System.err.println("[DonutQueue] Addon screen failed to open: " + t);
            statusLine = "That addon could not be opened.";
        }
    }

    /** Right rail on the Earnings page: swaps the list between "earned from most" and "lost most". */
    private void buildTopSwapButton() {
        ModConfig c = DonutQueueClient.config;
        // sits right under the 7-row list drawn by drawTopPlayers (card 70 + gap 8, title 16, 7 rows x 12)
        int by = Math.min(py + 6 + 70 + 8 + 16 + 7 * 12 + 6, py + ph - 26);
        addNeon(rx, by, railW, 18,
                () -> c.earningsTopShowLost ? "Show Most Earned" : "Show Most Lost",
                () -> c.earningsTopShowLost,
                () -> {
                    c.earningsTopShowLost = !c.earningsTopShowLost;
                    c.save();
                    pendingRebuild = true; // click zones differ between the two views
                });

        // Removed players: switch the list to the people you hid as "non gamble" (click one to bring them back)
        int by2 = Math.min(by + 20, py + ph - 26 - 15);
        addNeon(rx, by2, railW, 14,
                () -> "Removed",
                () -> false,
                () -> {
                    openRemoved();
                    pendingRebuild = true;
                });

        // Click a name in the list (earned or lost) to mark them non gamble (invisible click zones;
        // the rows themselves are painted in drawTopPlayers)
        {
            int sy = py + 6 + 70 + 8;
            for (int i = 0; i < 7; i++) {
                final int idx = i;
                Button zone = Button.builder(Component.literal(""), b -> {
                            List<Map.Entry<String, Double>> list = railTop(c.earningsTopShowLost);
                            if (idx < list.size()) markNonGambleName(list.get(idx).getKey());
                        })
                        .bounds(rx, sy + 15 + i * 12, railW, 12).build();
                this.addRenderableWidget(zone);
            }
        }
    }

    /** The day the right-rail list is about (today, or the day picked in Stats). */
    private String railDate() {
        String date = LocalDate.now().toString();
        DailyEarnings.Day[] days = DailyEarnings.allDays();
        if (statsView && statsDayIdx >= 0) date = days[Math.min(statsDayIdx, days.length - 1)].date;
        return date;
    }

    /** The rail list: the picked session's players in Sessions, otherwise the picked day's players. */
    private List<Map.Entry<String, Double>> railTop(boolean lost) {
        if (sessionView) {
            Sessions.Session s = selectedSession();
            if (s == null) return new ArrayList<>();
            return lost ? Sessions.topLosses(s, 7) : Sessions.topGains(s, 7);
        }
        return lost ? DailyEarnings.topLosses(railDate(), 7) : DailyEarnings.topPlayers(railDate(), 7);
    }

    private Sessions.Session selectedSession() {
        return sessionView ? openSession : null;
    }

    /** Dates the Stats graph covers: every day on the overview, otherwise the one picked day. */
    private List<String> statsDates() {
        DailyEarnings.Day[] days = DailyEarnings.allDays();
        List<String> dates = new ArrayList<>();
        int idx = Math.min(statsDayIdx, days.length - 1);
        if (idx < 0) {
            for (DailyEarnings.Day d : days) dates.add(d.date);
        } else {
            dates.add(days[idx].date);
        }
        return dates;
    }

    private void markNonGambleName(String name) {
        DailyEarnings.markNonGamble(name);
        statusLine = name + " marked non gamble - hidden from the lists.";
    }

    private int pillarSlotW() {
        return (cw - 4) / 7;
    }

    private int pillarStartX() {
        return cx + 2 + ((cw - 4) - pillarSlotW() * 7) / 2;
    }

    private Button addNeon(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier active, Runnable action) {
        Button b = Button.builder(Component.literal(""), btn -> {
                    String shown;
                    try {
                        shown = label.get();
                    } catch (RuntimeException ex) {
                        shown = "";
                    }
                    action.run();
                    Tutorial.event("BTN:" + shown);
                })
                .bounds(x, y, w, h).build();
        this.addRenderableWidget(b);

        NeonBtn n = new NeonBtn();
        n.x = x;
        n.y = y;
        n.w = w;
        n.h = h;
        n.label = label;
        n.active = active;
        neon.add(n);
        return b;
    }

    private EditBox addBox(int x, int y, int w, String name, int maxLen, String initial) {
        EditBox box = new EditBox(this.font, x, y, w, 20, Component.literal(name));
        box.setMaxLength(maxLen);
        if (initial != null && !initial.isEmpty()) box.setValue(initial);
        this.addRenderableWidget(box);
        boxes.add(box);
        return box;
    }

    private void addLabel(int x, int y, String text) {
        Label l = new Label();
        l.x = x;
        l.y = y;
        l.text = text;
        labels.add(l);
    }

    private int colW() {
        return (cw - 4) / 2;
    }

    private int colX(int c) {
        return cx + c * (colW() + 4);
    }

    /** Adds an ON/OFF toggle button in column c at height y. */
    private void toggle(int c, int y, String name, BooleanSupplier state, Runnable flip) {
        addNeon(c < 0 ? cx : colX(c), y, c < 0 ? cw : colW(), 20,
                () -> name + ": " + (state.getAsBoolean() ? "ON" : "OFF"),
                state,
                () -> {
                    flip.run();
                    DonutQueueClient.config.save();
                });
    }

    private void openMover(String title, int bw, int bh, String label, int color,
                           java.util.function.IntSupplier gx, java.util.function.IntSupplier gy,
                           java.util.function.IntConsumer sx, java.util.function.IntConsumer sy) {
        this.minecraft.gui.setScreen(new MovableBoxScreen(this, title, bw, bh, label, color, gx, gy, sx, sy));
    }

    // ---------------- Main page ----------------

    private void buildMainPage() {
        ModConfig c = DonutQueueClient.config;
        int[] br = mainLayout(c, false).get("BUTTONS");
        if (br == null) return;
        for (int i = 0; i < buttonCount(c); i++) {
            int[] f = buttonFace(br, i, c);
            final String act = buttonAction(c, i);
            addNeon(f[0], f[1], f[2], f[3], () -> actLabel(act), () -> actActive(act), () -> runAction(act));
        }
    }

    // ---------------- Bottom buttons (what each one does) ----------------

    static int buttonCount(ModConfig c) {
        return c.mainFourButtons ? 4 : 2;
    }

    /** {x, y, w, h} of bottom button number i inside the Buttons card r (1 row of 2, or 2 x 2 in 4-button mode). */
    static int[] buttonFace(int[] r, int i, ModConfig c) {
        int bw = (r[2] - 4) / 2;
        int col = i % 2;
        int x = r[0] + col * (bw + 4);
        int w = col == 0 ? bw : r[2] - bw - 4;
        if (!c.mainFourButtons) return new int[]{x, r[1], w, r[3]};
        int bh = (r[3] - 4) / 2;
        int row = i / 2;
        int y = r[1] + row * (bh + 4);
        int h = row == 0 ? bh : r[3] - bh - 4;
        return new int[]{x, y, w, h};
    }

    static int actIndex(String id) {
        for (int i = 0; i < ACT_IDS.length; i++) if (ACT_IDS[i].equals(id)) return i;
        return -1;
    }

    /** The action saved for slot i (falls back to the default if the saved value is missing or unknown). */
    static String buttonAction(ModConfig c, int i) {
        if (c.mainButtons != null && i < c.mainButtons.size() && actIndex(c.mainButtons.get(i)) >= 0) {
            return c.mainButtons.get(i);
        }
        return DEFAULT_BUTTONS[i];
    }

    static void setButtonAction(ModConfig c, int slot, String id) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < DEFAULT_BUTTONS.length; i++) l.add(buttonAction(c, i));
        l.set(slot, id);
        c.mainButtons = l;
    }

    static String actLabel(String id) {
        ModConfig c = DonutQueueClient.config;
        return switch (id) {
            case "SESSION" -> Sessions.isRunning() ? "END SESSION" : "START SESSION";
            case "STATS" -> "STATS";
            case "BIDDING" -> c.biddingActive ? "STOP BIDDING" : "START BIDDING";
            case "TRACKING" -> (c.trackingEnabled || c.awaitingBalCapture) ? "RESET TRACKING" : "START TRACKING";
            case "LEVER" -> c.leverPaused ? "RESUME LEVER" : "PAUSE LEVER";
            case "EARNINGS" -> "EARNINGS";
            case "SESSIONS" -> "SESSIONS";
            case "QUEUE" -> "ANTI GAMBLE";
            default -> id;
        };
    }

    static boolean actActive(String id) {
        ModConfig c = DonutQueueClient.config;
        return switch (id) {
            case "SESSION" -> Sessions.isRunning();
            case "BIDDING" -> c.biddingActive;
            case "TRACKING" -> c.trackingEnabled;
            case "LEVER" -> c.leverPaused;
            default -> false;
        };
    }

    private void runAction(String id) {
        ModConfig c = DonutQueueClient.config;
        switch (id) {
            case "SESSION" -> toggleSession();
            case "STATS" -> {
                page = 5;
                lastPage = 5;
                statsView = true;
                sessionView = false;
                openSession = null;
                logView = false;
                        remView = false;
                statsDayIdx = DailyEarnings.allDays().length - 1; // open on today
                stripStart = Integer.MAX_VALUE;
                statusLine = "";
                pendingRebuild = true;
            }
            case "BIDDING" -> toggleBidding();
            case "TRACKING" -> {
                if (c.trackingEnabled || c.awaitingBalCapture) resetTracking();
                else startTracking();
            }
            case "LEVER" -> {
                c.leverPaused = !c.leverPaused;
                c.save();
                statusLine = "Lever skip " + (c.leverPaused ? "PAUSED." : "resumed.");
            }
            case "EARNINGS" -> gotoPage(5);
            case "SESSIONS" -> gotoPage(6);
            case "QUEUE" -> gotoPage(1);
            default -> { }
        }
    }

    private void gotoPage(int idx) {
        page = idx;
        lastPage = idx;
        statsView = false;
        sessionView = false;
        openSession = null;
        logView = false;
                        remView = false;
        logScroll = 0;
        statusLine = "";
        pendingRebuild = true;
    }

    /** Start a bidding round (reusing the last round time, 60s the first time) or stop the one that is running. */
    private void toggleBidding() {
        ModConfig c = DonutQueueClient.config;
        if (c.biddingActive) {
            c.biddingActive = false;
            c.biddingFinished = false;
            c.biddingLeaderName = null;
            c.biddingLeaderAmount = 0;
            c.save();
            statusLine = "Bidding stopped and reset.";
            return;
        }
        long ms = c.biddingTotalDurationMillis > 0 ? c.biddingTotalDurationMillis : 60_000L;
        c.biddingActive = true;
        c.biddingFinished = false;
        c.biddingTotalDurationMillis = ms;
        c.biddingEndTimeMillis = System.currentTimeMillis() + ms;
        c.biddingLeaderName = null;
        c.biddingLeaderAmount = 0;
        c.biddingBoxEnabled = true;
            c.biddingItemId = "";
            c.biddingItemCount = 0;
        c.save();
        statusLine = "Bidding started for " + (ms / 1000L) + " seconds.";
    }

    private void startTracking() {
        ModConfig c = DonutQueueClient.config;
        c.earnedTotal = 0;
        c.goalReachedNotified = false;
        c.trackingEnabled = false;
        c.hasStartingBalance = false;
        c.awaitingBalCapture = true;
        c.save();
        DailyEarnings.clearRun();
        statusLine = "Now run /bal in chat - it will start from 0.";
    }

    private void resetTracking() {
        ModConfig c = DonutQueueClient.config;
        c.earnedTotal = 0;
        c.goalReachedNotified = false;
        c.trackingEnabled = false;
        c.hasStartingBalance = false;
        c.awaitingBalCapture = false;
        c.save();
        DailyEarnings.clearRun();
        statusLine = "Tracking reset.";
    }

    // ---------------- Anti Gamble Skip ----------------

    private void buildQueuePage() {
        ModConfig c = DonutQueueClient.config;
        int y = cy + 16;

        toggle(-1, y, "Queue HUD", () -> c.hudEnabled, () -> c.hudEnabled = !c.hudEnabled);
        y += 24;

        addNeon(cx, y, cw, 20,
                () -> "Lever Skip: " + (c.leverPaused ? "OFF" : "ON"),
                () -> !c.leverPaused,
                () -> {
                    c.leverPaused = !c.leverPaused;
                    c.save();
                });
        y += 24;

        addLabel(cx, y, "MIN PAYMENT (50k, 1.5m, 0 = none)");
        y += 10;
        minBox = addBox(cx, y, cw - 52, "Minimum payment", 20,
                c.minimumAmount > 0 ? DonutQueueClient.formatAmount(c.minimumAmount) : "");
        addNeon(cx + cw - 48, y, 48, 20, () -> "SET", () -> false, () -> {
            String raw = minBox.getValue().trim();
            double v = raw.isEmpty() ? 0 : DonutQueueClient.parseAmount(raw);
            if (v < 0) v = 0;
            c.minimumAmount = v;
            c.save();
            statusLine = v > 0 ? "Minimum payment set to $" + DonutQueueClient.formatAmount(v) : "Minimum payment cleared.";
        });
        y += 26;

        addNeon(colX(0), y, colW(), 20, () -> "Move HUD Box", () -> false, () ->
                openMover("Move HUD Box", DonutQueueClient.scaledSize(190, c.hudScale), DonutQueueClient.scaledSize(DonutQueueClient.QUEUE_BOX_HEIGHT, c.hudScale), "Donut Queue", c.queueBoxColor,
                        () -> c.hudX, () -> c.hudY, v -> c.hudX = v, v -> c.hudY = v));
        addNeon(colX(1), y, colW(), 20, () -> "Size & Blacklist", () -> false, () ->
                this.minecraft.gui.setScreen(new DonutQueueConfigScreen(this)));
        y += 24;

        addNeon(cx, y, cw, 20, () -> "Clear Queue", () -> false, () -> {
            DonutQueueClient.queue.clear();
            statusLine = "Queue cleared.";
        });
    }

    // ---------------- Starting Bal ----------------

    private void buildStartBalPage() {
        ModConfig c = DonutQueueClient.config;
        int y = cy + 16;

        toggle(-1, y, "Bal Box", () -> c.startBalBoxEnabled, () -> c.startBalBoxEnabled = !c.startBalBoxEnabled);
        y += 24;

        addNeon(colX(0), y, colW(), 20, () -> "START TRACKING", () -> c.awaitingBalCapture || c.trackingEnabled, this::startTracking);
        addNeon(colX(1), y, colW(), 20, () -> "RESET TRACKING", () -> false, this::resetTracking);
        y += 24;

        addLabel(cx, y, "GOAL AMOUNT (e.g. 500m, 2b)");
        y += 10;
        goalBox = addBox(cx, y, cw - 52, "Goal amount", 20,
                c.goalAmount > 0 ? DonutQueueClient.formatAmount(c.goalAmount) : "");
        addNeon(cx + cw - 48, y, 48, 20, () -> "SET", () -> false, () -> {
            double goal = DonutQueueClient.parseAmount(goalBox.getValue());
            if (goal <= 0) {
                statusLine = "Enter an amount like 500m or 2b.";
                return;
            }
            c.goalAmount = goal;
            c.earnedTotal = 0;
            c.goalReachedNotified = false;
            c.save();
            statusLine = "Goal set to $" + DonutQueueClient.formatAmount(goal);
        });
        y += 26;

        addNeon(colX(0), y, colW(), 20, () -> "CLEAR GOAL", () -> false, () -> {
            c.goalAmount = 0;
            c.goalReachedNotified = false;
            c.save();
            statusLine = "Goal cleared.";
        });
        addNeon(colX(1), y, colW(), 20, () -> "Move Box", () -> false, () ->
                openMover("Move Starting Bal Box", DonutQueueClient.scaledSize(DonutQueueClient.STARTBAL_BOX_WIDTH, c.startBalBoxScale),
                        DonutQueueClient.scaledSize(DonutQueueClient.STARTBAL_BOX_HEIGHT, c.startBalBoxScale),
                        "Starting Bal", c.startBalBoxColor,
                        () -> c.startBalBoxX, () -> c.startBalBoxY, v -> c.startBalBoxX = v, v -> c.startBalBoxY = v));
        y += 24;

        addNeon(cx, y, cw, 20, () -> "Size, Color & Check", () -> false, () ->
                this.minecraft.gui.setScreen(new StartBalConfigScreen(this)));
    }

    // ---------------- Bidding ----------------

    private void buildBiddingPage() {
        ModConfig c = DonutQueueClient.config;
        int y = cy + 16;

        toggle(-1, y, "Bid Box", () -> c.biddingBoxEnabled, () -> c.biddingBoxEnabled = !c.biddingBoxEnabled);
        y += 24;

        addLabel(cx, y, "ROUND TIME (60, 5m, 1h)");
        y += 10;
        bidTimeBox = addBox(cx, y, cw - 62, "Round time", 10, "");
        addNeon(cx + cw - 58, y, 58, 20, () -> "START", () -> c.biddingActive, () -> {
            long seconds = DonutQueueClient.parseDurationSeconds(bidTimeBox.getValue());
            if (seconds <= 0) {
                statusLine = "Give a valid time, e.g. 60, 5m or 1h.";
                return;
            }
            c.biddingActive = true;
            c.biddingFinished = false;
            c.biddingTotalDurationMillis = seconds * 1000L;
            c.biddingEndTimeMillis = System.currentTimeMillis() + c.biddingTotalDurationMillis;
            c.biddingLeaderName = null;
            c.biddingLeaderAmount = 0;
            c.biddingBoxEnabled = true;
            c.biddingItemId = "";
            c.biddingItemCount = 0;
            c.save();
            statusLine = "Bidding started for " + seconds + " seconds.";
        });
        y += 26;

        addLabel(cx, y, "MINIMUM BID (50k, 1.5m, 0 = none)");
        y += 10;
        bidMinBox = addBox(cx, y, cw - 52, "Minimum bid", 20,
                c.biddingMinimum > 0 ? DonutQueueClient.formatAmount(c.biddingMinimum) : "");
        addNeon(cx + cw - 48, y, 48, 20, () -> "SET", () -> false, () -> {
            String raw = bidMinBox.getValue().trim();
            double v = raw.isEmpty() ? 0 : DonutQueueClient.parseAmount(raw);
            if (v < 0) v = 0;
            c.biddingMinimum = v;
            c.save();
            statusLine = v > 0 ? "Minimum bid set to $" + DonutQueueClient.formatAmount(v) : "Minimum bid cleared.";
        });
        y += 26;

        addNeon(colX(0), y, colW(), 20, () -> "STOP ROUND", () -> false, () -> {
            c.biddingActive = false;
            c.biddingFinished = false;
            c.biddingLeaderName = null;
            c.biddingLeaderAmount = 0;
            c.save();
            statusLine = "Bidding stopped and reset.";
        });
        addNeon(colX(1), y, colW(), 20, () -> "Move Box", () -> false, () ->
                openMover("Move Bidding Box", DonutQueueClient.scaledSize(270, c.biddingBoxScale), DonutQueueClient.scaledSize(70, c.biddingBoxScale), "Bidding", c.biddingBoxColor,
                        () -> c.biddingBoxX, () -> c.biddingBoxY, v -> c.biddingBoxX = v, v -> c.biddingBoxY = v));
        y += 24;

        addNeon(cx, y, cw, 20, () -> "Size, Color & Blacklist", () -> false, () ->
                this.minecraft.gui.setScreen(new BiddingConfigScreen(this)));
    }

    // ---------------- Customisation ----------------

    private void buildCustomisationPage() {
        int y = cy + 16;
        addNeon(cx, y, cw, 20, () -> "Ranks", () -> false, () ->
                this.minecraft.gui.setScreen(new RankConfigScreen(this)));
        y += 24;
        addNeon(cx, y, cw, 20, () -> "Menu customisation", () -> false, () ->
                this.minecraft.gui.setScreen(new MenuCustomisationScreen(this)));
        y += 24;
        addNeon(cx, y, cw, 20, () -> "Chat", () -> false, () ->
                this.minecraft.gui.setScreen(new ChatHubScreen(this)));
        y += 24;
        addNeon(cx, y, cw, 20, () -> "Guards", () -> false, () ->
                this.minecraft.gui.setScreen(new GuardsScreen(this)));
        y += 24;
        addNeon(cx, y, cw, 20, () -> "Screen features", () -> false, () ->
                this.minecraft.gui.setScreen(new ScreenFeaturesScreen(this)));
        y += 24;
    }

    // ---------------- Info Display ----------------

    private void buildInfoPage() {
        ModConfig c = DonutQueueClient.config;
        int y = cy + 16;

        toggle(0, y, "Info Box", () -> c.infoBoxEnabled, () -> c.infoBoxEnabled = !c.infoBoxEnabled);
        addNeon(colX(1), y, colW(), 20, () -> "Move Box", () -> false, () ->
                openMover("Move Info Box", DonutQueueClient.scaledSize(190, c.infoBoxScale), DonutQueueClient.scaledSize(65, c.infoBoxScale), "Info Display", c.infoBoxColor,
                        () -> c.infoBoxX, () -> c.infoBoxY, v -> c.infoBoxX = v, v -> c.infoBoxY = v));
        y += 24;

        addNeon(cx, y, cw, 20, () -> "Edit Fields, Size & Color", () -> false, () ->
                this.minecraft.gui.setScreen(new InfoDisplayConfigScreen(this)));
    }

    // ---------------- Earnings ----------------

    private void buildEarningsPage() {
        ModConfig c = DonutQueueClient.config;
        int y = cy + 16;

        toggle(-1, y, "Earn Box", () -> c.earningsBoxEnabled, () -> c.earningsBoxEnabled = !c.earningsBoxEnabled);
        y += 24;

        toggle(0, y, "Earn Track", () -> c.earningsTrackingEnabled, () -> c.earningsTrackingEnabled = !c.earningsTrackingEnabled);
        addNeon(colX(1), y, colW(), 20, () -> "Reset Totals", () -> false, () -> {
            c.earningsTotals.clear();
            c.save();
            statusLine = "Earnings totals cleared.";
        });
        y += 24;

        addNeon(colX(0), y, colW(), 20, () -> "Move Box", () -> false, () -> {
            int previewHeight = DonutQueueClient.earningsBoxHeight(c.earningsShowCount);
            openMover("Move Earnings Box", DonutQueueClient.scaledSize(220, c.earningsBoxScale), DonutQueueClient.scaledSize(previewHeight, c.earningsBoxScale), "Earnings", c.earningsBoxColor,
                    () -> c.earningsBoxX, () -> c.earningsBoxY, v -> c.earningsBoxX = v, v -> c.earningsBoxY = v);
        });
        addNeon(colX(1), y, colW(), 20, () -> "Size & Color", () -> false, () ->
                this.minecraft.gui.setScreen(new EarningsConfigScreen(this)));
        y += 24;

        addNeon(cx, y, cw, 20, () -> "Stats  (big graph + each day)", () -> false, () -> {
            statsView = true;
            statsDayIdx = DailyEarnings.allDays().length - 1; // always open on today
            stripStart = Integer.MAX_VALUE;
            statusLine = "";
            pendingRebuild = true;
        });
        y += 24;

        addNeon(cx, y, cw, 20, () -> "Payments log  (who paid you / who you paid)", () -> false, () -> {
            openPaymentLog();
            pendingRebuild = true;
        });
        y += 24;

        addNeon(cx, y, cw, 20, () -> "Removed players  (non gamble)", () -> false, () -> {
            openRemoved();
            pendingRebuild = true;
        });
        y += 24;
    }

    // ---------------- Removed players ("non gamble") - same look as the Payments log ----------------

    private boolean remView = false;
    private int remScroll = 0;
    private String remSearch = "";
    private EditBox remSearchBox;
    /** Day filter (see parseDate). Empty = everyone ever removed, with their all-time total. Ignored in Sessions. */
    private String remDate = "";
    private EditBox remDateBox;
    private boolean remDrag = false, remWasDown = false;

    private void openRemoved() {
        remView = true;
        remScroll = 0;
        remSearch = "";
        remDate = sessionView ? "" : railDate();
        statusLine = "";
    }

    /** The removed people to list: name + their net for that day / session (all time when no date). Biggest amount first. */
    private List<Map.Entry<String, Double>> remEntries() {
        ModConfig c = DonutQueueClient.config;
        Map<String, Double> src;
        boolean needsActivity = true; // only people who were active in that day / session
        if (sessionView) {
            Sessions.Session s = selectedSession();
            src = s == null ? new java.util.LinkedHashMap<>() : Sessions.allPlayers(s);
        } else {
            LocalDate d = parseDate(remDate);
            if (d != null) {
                src = DailyEarnings.allPlayers(d.toString());
            } else {
                src = DailyEarnings.allPlayersAllTime();
                needsActivity = false;
            }
        }
        String q = remSearch.trim().toLowerCase(java.util.Locale.ROOT);
        List<Map.Entry<String, Double>> out = new ArrayList<>();
        for (String name : new ArrayList<>(c.nonGamblePlayers)) {
            Double net = null;
            for (Map.Entry<String, Double> e : src.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) { net = e.getValue(); break; }
            }
            if (net == null && needsActivity) continue;
            if (!q.isEmpty() && !name.toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            out.add(new java.util.AbstractMap.SimpleEntry<>(name, net == null ? 0.0 : net));
        }
        out.sort((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())));
        return out;
    }

    /** Rows that fit in the Removed list (it stops above the day strip when the strip is showing). */
    private int remRows() {
        if (statsView && !sessionView) {
            return Math.max(1, (statsLayout()[2] - 16 - logTop()) / LOG_ROW_H);
        }
        return logRows();
    }

    private void clampRemScroll() {
        int max = Math.max(0, remEntries().size() - remRows());
        remScroll = Math.max(0, Math.min(max, remScroll));
    }

    private void buildRemovedPage() {
        int y = cy + 16;
        addNeon(cx, y, 40, 20, () -> "Back", () -> false, () -> {
            remView = false;
            remScroll = 0;
            pendingRebuild = true;
        });
        int sx = cx + 46;
        if (!sessionView) {
            remDateBox = addBox(sx, y, 70, "Date", 12, remDate);
            remDateBox.setHint(Component.literal("all time"));
            remDateBox.setResponder(t -> {
                remDate = t;
                remScroll = 0;
            });
            sx += 76;
        }
        remSearchBox = addBox(sx, y, Math.max(40, cx + cw - sx), "Search name", 16, remSearch);
        remSearchBox.setHint(Component.literal("search name"));
        remSearchBox.setResponder(t -> {
            remSearch = t;
            remScroll = 0;
        });
        // one invisible click zone per visible row: click = bring that person back
        int rows = remRows();
        int top = logTop();
        for (int i = 0; i < rows; i++) {
            final int row = i;
            Button zone = Button.builder(Component.literal(""), b -> {
                        List<Map.Entry<String, Double>> list = remEntries();
                        int idx = remScroll + row;
                        if (idx < list.size()) {
                            String back = list.get(idx).getKey();
                            DailyEarnings.unmarkNonGamble(back);
                            statusLine = back + " is counted again.";
                            clampRemScroll();
                        }
                    })
                    .bounds(cx, top + i * LOG_ROW_H - 1, cw - 9, LOG_ROW_H).build();
            this.addRenderableWidget(zone);
        }

        // The green / red day strip (Stats): click a day to see the people removed on that day
        if (statsView && !sessionView) {
            int sy = statsLayout()[2];
            for (int i = 0; i < STRIP_DAYS; i++) {
                final int cell = i;
                Button zone = Button.builder(Component.literal(""), b -> {
                            DailyEarnings.Day[] all = DailyEarnings.allDays();
                            syncStrip(all.length);
                            int day = stripStart + cell;
                            if (day < all.length) {
                                statsDayIdx = day;
                                remDate = all[day].date;
                                if (remDateBox != null) remDateBox.setValue(remDate);
                                remScroll = 0;
                            }
                        })
                        .bounds(stripX() + i * stripCellW(), sy, stripCellW() - 1, 10).build();
                this.addRenderableWidget(zone);
            }
        }
    }

    private void drawRemoved(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<Map.Entry<String, Double>> list = remEntries();
        int rows = remRows();
        int top = logTop();
        // scrollbar drag (mouse button polled, like the log)
        boolean down = this.minecraft != null && this.minecraft.mouseHandler.isLeftPressed();
        int trackTop = top - 1;
        int trackH = rows * LOG_ROW_H;
        int trackX = cx + cw - 6;
        if (down && !remWasDown && list.size() > rows
                && mouseX >= trackX - 3 && mouseX < cx + cw + 2 && mouseY >= trackTop && mouseY < trackTop + trackH) {
            remDrag = true;
        }
        if (!down) remDrag = false;
        remWasDown = down;
        if (remDrag && list.size() > rows) {
            int thumbH = Math.max(10, trackH * rows / list.size());
            int span = trackH - thumbH;
            double f = span <= 0 ? 0 : (mouseY - trackTop - thumbH / 2.0) / span;
            remScroll = (int) Math.round(Math.max(0, Math.min(1, f)) * (list.size() - rows));
        }
        clampRemScroll();

        boolean searching = !remSearch.trim().isEmpty();
        LocalDate day = sessionView ? null : parseDate(remDate);
        String when = sessionView ? " - SESSION" : (day == null ? " - ALL TIME" : " - " + day);
        String title = (searching ? "MATCHES" : "REMOVED") + when;
        g.text(this.font, title, cx, cy + 42, C_WARN, false);
        g.fill(cx, top - 3, cx + cw, top - 2, Theme.edge);

        if (list.isEmpty()) {
            return;
        }

        int right = cx + cw - 9;
        for (int i = 0; i < rows; i++) {
            int idx = remScroll + i;
            if (idx >= list.size()) break;
            Map.Entry<String, Double> e = list.get(idx);
            int ry = top + i * LOG_ROW_H;
            boolean hover = mouseX >= cx && mouseX < right && mouseY >= ry - 1 && mouseY < ry + LOG_ROW_H - 1;
            if (hover) {
                g.fill(cx, ry - 1, right, ry + LOG_ROW_H - 1, Theme.btnHov);
            } else if (i % 2 == 1) {
                g.fill(cx, ry - 1, right, ry + LOG_ROW_H - 1, 0x40102060);
            }
            double v = e.getValue();
            boolean got = v > 0;
            boolean even = v == 0;
            String tag = hover ? "restore" : (even ? "even" : (got ? "got" : "paid"));
            g.text(this.font, tag, cx + 2, ry, hover ? C_GOOD : (even ? Theme.muted : (got ? C_GOOD : C_BAD)), false);

            String amt = even ? "$0" : (got ? "+$" : "-$") + DonutQueueClient.formatCompact(Math.abs(v));
            int amtW = this.font.width(amt);
            g.text(this.font, amt, right - 2 - amtW, ry, even ? Theme.muted : (got ? C_GOOD : C_BAD), false);

            String name = e.getKey();
            int maxName = right - 2 - amtW - 6 - (cx + 44);
            while (name.length() > 1 && this.font.width(name) > maxName) name = name.substring(0, name.length() - 1);
            g.text(this.font, name, cx + 44, ry, Theme.text, false);
        }

        g.fill(trackX, trackTop, trackX + 5, trackTop + trackH, Theme.track);
        if (list.size() > rows) {
            int thumbH = Math.max(10, trackH * rows / list.size());
            int maxScroll = list.size() - rows;
            int thumbY = trackTop + (trackH - thumbH) * remScroll / maxScroll;
            g.fill(trackX, thumbY, trackX + 5, thumbY + thumbH, remDrag ? 0xFFFFFFFF : Theme.neonHi);
        }
    }

    // ---------------- Payments log (Earnings + Sessions) ----------------

    private void openPaymentLog() {
        logView = true;
        logScroll = 0;
        logFilter = 0;
        logSearch = "";
        logDate = "";
        statusLine = "";
    }

    /** The day the payments log is limited to, or null for all time (empty / unreadable box). */
    private LocalDate logDay() {
        return parseDate(logDate);
    }

    private static final int LOG_ROW_H = 12;

    private int logTop() {
        return cy + 54;
    }

    private int logRows() {
        return Math.max(1, (cy + ch - 16 - logTop()) / LOG_ROW_H);
    }

    /** The payments being shown (newest first, filtered). Only rebuilt when something changed. */
    private List<PaymentLog.Payment> logEntries() {
        boolean session = page == 6;
        Sessions.Session s = session ? openSession : null;
        LocalDate day = logDay();
        String key = page + ":" + logFilter + ":" + logSearch + ":" + day + ":" + System.identityHashCode(s) + ":"
                + (session ? (s == null ? 0 : Sessions.paymentCount(s)) : PaymentLog.revision());
        if (key.equals(logCacheKey)) return logCache;
        List<PaymentLog.Payment> src = session
                ? (s == null ? new ArrayList<>() : Sessions.payments(s))
                : PaymentLog.newestFirst();
        if (day != null) {
            List<PaymentLog.Payment> out = new ArrayList<>();
            for (PaymentLog.Payment p : src) {
                LocalDate d = java.time.Instant.ofEpochMilli(p.t).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                if (d.equals(day)) out.add(p);
            }
            src = out;
        }
        String q = logSearch.trim().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) {
            List<PaymentLog.Payment> out = new ArrayList<>();
            for (PaymentLog.Payment p : src) {
                if (p.p.toLowerCase(java.util.Locale.ROOT).contains(q)) out.add(p);
            }
            src = out;
        }
        logGotSum = 0;
        logPaidSum = 0;
        logGotN = 0;
        logPaidN = 0;
        for (PaymentLog.Payment p : src) {
            if (p.a > 0) { logGotSum += p.a; logGotN++; }
            else { logPaidSum += -p.a; logPaidN++; }
        }
        if (logFilter != 0) {
            List<PaymentLog.Payment> out = new ArrayList<>();
            for (PaymentLog.Payment p : src) {
                if (logFilter == 1 ? p.a > 0 : p.a < 0) out.add(p);
            }
            src = out;
        }
        logCache = src;
        logCacheKey = key;
        return logCache;
    }

    private void clampLogScroll() {
        int max = Math.max(0, logEntries().size() - logRows());
        logScroll = Math.max(0, Math.min(max, logScroll));
    }

    private void buildPaymentLogPage() {
        int y = cy + 16;
        addNeon(cx, y, 40, 20, () -> "Back", () -> false, () -> {
            logView = false;
                        remView = false;
            logScroll = 0;
            pendingRebuild = true;
        });
        addNeon(cx + 46, y, 30, 20, () -> "All", () -> logFilter == 0, () -> { logFilter = 0; logScroll = 0; });
        addNeon(cx + 78, y, 34, 20, () -> "Got", () -> logFilter == 1, () -> { logFilter = 1; logScroll = 0; });
        addNeon(cx + 114, y, 34, 20, () -> "Paid", () -> logFilter == 2, () -> { logFilter = 2; logScroll = 0; });
        // name search: shows every payment to / from anyone whose name contains the text
        // day filter: empty = all time, or type a date (2026-10-16, 10-16, Oct 16)
        logDateBox = addBox(cx + 152, y, 70, "Date", 12, logDate);
        logDateBox.setHint(Component.literal("all time"));
        logDateBox.setResponder(t -> {
            logDate = t;
            logScroll = 0;
        });
        logSearchBox = addBox(cx + 226, y, Math.max(40, cw - 226), "Search name", 16, logSearch);
        logSearchBox.setHint(Component.literal("search name"));
        logSearchBox.setResponder(t -> {
            logSearch = t;
            logScroll = 0;
        });
    }

    /** Lets the scrollbar be clicked / dragged (the mouse button is polled, the wheel works too). */
    private void updateLogScrollbar(int n, int rows, int top, int mx, int my) {
        boolean down = this.minecraft != null && this.minecraft.mouseHandler.isLeftPressed();
        int trackTop = top - 1;
        int trackH = rows * LOG_ROW_H;
        int trackX = cx + cw - 6;
        if (down && !logWasDown && n > rows
                && mx >= trackX - 3 && mx < cx + cw + 2 && my >= trackTop && my < trackTop + trackH) {
            logDrag = true;
        }
        if (!down) logDrag = false;
        logWasDown = down;
        if (logDrag && n > rows) {
            int thumbH = Math.max(10, trackH * rows / n);
            int span = trackH - thumbH;
            double f = span <= 0 ? 0 : (my - trackTop - thumbH / 2.0) / span;
            logScroll = (int) Math.round(Math.max(0, Math.min(1, f)) * (n - rows));
        }
    }

    private void drawPaymentLog(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<PaymentLog.Payment> list = logEntries();
        int rows = logRows();
        int top = logTop();
        updateLogScrollbar(list.size(), rows, top, mouseX, mouseY);
        clampLogScroll();

        boolean searching = !logSearch.trim().isEmpty();
        LocalDate day = logDay();
        String when = day == null ? "" : " - " + day;
        String title = searching ? "MATCHES" + when : (page == 6 ? "SESSION PAYMENTS" : (day == null ? "ALL PAYMENTS" : "PAYMENTS")) + when;
        String count = list.size() + (logFilter == 1 ? " got" : logFilter == 2 ? " paid" : " total");
        if (searching) {
            count = "got +$" + DonutQueueClient.formatCompact(logGotSum) + " (" + logGotN + ")  paid -$"
                    + DonutQueueClient.formatCompact(logPaidSum) + " (" + logPaidN + ")";
        }
        g.text(this.font, title, cx, cy + 42, Theme.neonHi, false);
        g.text(this.font, count, cx + cw - 6 - this.font.width(count), cy + 42, Theme.muted, false);
        g.fill(cx, top - 3, cx + cw, top - 2, Theme.edge);

        if (list.isEmpty()) {
            String t = searching ? "No payments with that name"
                    : day != null ? "No payments on that day"
                    : (page == 6 ? "No payments in this session yet" : "No payments logged yet");
            g.text(this.font, t, cx + 4, top + 4, Theme.muted, false);
            if (!searching && day == null) g.text(this.font, "(only payments from now on are listed)", cx + 4, top + 16, Theme.muted, false);
            return;
        }

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm", java.util.Locale.ENGLISH);
        int right = cx + cw - 9; // room for the scrollbar
        for (int i = 0; i < rows; i++) {
            int idx = logScroll + i;
            if (idx >= list.size()) break;
            PaymentLog.Payment p = list.get(idx);
            int ry = top + i * LOG_ROW_H;
            boolean got = p.a > 0;
            if (mouseX >= cx && mouseX < right && mouseY >= ry - 1 && mouseY < ry + LOG_ROW_H - 1) {
                g.fill(cx, ry - 1, right, ry + LOG_ROW_H - 1, Theme.btnHov);
            } else if (i % 2 == 1) {
                g.fill(cx, ry - 1, right, ry + LOG_ROW_H - 1, 0x40102060);
            }
            String time = fmt.format(java.time.Instant.ofEpochMilli(p.t).atZone(java.time.ZoneId.systemDefault()));
            g.text(this.font, time, cx + 2, ry, Theme.muted, false);
            g.text(this.font, got ? "got" : "paid", cx + 64, ry, got ? C_GOOD : C_BAD, false);

            String amt = (got ? "+$" : "-$") + DonutQueueClient.formatCompact(Math.abs(p.a));
            int amtW = this.font.width(amt);
            g.text(this.font, amt, right - 2 - amtW, ry, got ? C_GOOD : C_BAD, false);

            String name = p.p;
            int maxName = right - 2 - amtW - 6 - (cx + 92);
            while (name.length() > 1 && this.font.width(name) > maxName) name = name.substring(0, name.length() - 1);
            g.text(this.font, name, cx + 92, ry, Theme.text, false);
        }

        // scrollbar
        int trackTop = top - 1;
        int trackH = rows * LOG_ROW_H;
        int trackX = cx + cw - 6;
        g.fill(trackX, trackTop, trackX + 5, trackTop + trackH, Theme.track);
        if (list.size() > rows) {
            int thumbH = Math.max(10, trackH * rows / list.size());
            int maxScroll = list.size() - rows;
            int thumbY = trackTop + (trackH - thumbH) * logScroll / maxScroll;
            g.fill(trackX, thumbY, trackX + 5, thumbY + thumbH, logDrag ? 0xFFFFFFFF : Theme.neonHi);
        }
    }

    /** Mouse wheel scrolls the payments log up / down. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (remView && (page == 5 || (page == 6 && sessionView))) {
            if (scrollY != 0) {
                remScroll -= (int) Math.signum(scrollY) * 3;
                clampRemScroll();
            }
            return true;
        }
        if (logView && (page == 5 || (page == 6 && sessionView))) {
            if (scrollY != 0) {
                logScroll -= (int) Math.signum(scrollY) * 3;
                clampLogScroll();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void toggleSession() {
        ModConfig c = DonutQueueClient.config;
        if (Sessions.isRunning()) {
            Sessions.Session done = Sessions.end();
            statusLine = "";
        } else {
            Sessions.start(c);
            statusLine = c.trackingEnabled && c.hasStartingBalance
                    ? "Session started."
                    : "Session started - start Starting Bal tracking to graph it.";
        }
    }

    private static String lengthText(long secs) {
        long h = secs / 3600, m = (secs % 3600) / 60, sec = secs % 60;
        if (h > 0) return h + "h" + String.format("%02d", m) + "m";
        if (m > 0) return m + "m" + String.format("%02d", sec) + "s";
        return sec + "s";
    }

    // ---------------- Earnings > Stats ----------------

    /** {graphY, graphH, stripY, statsRowY} for the Stats view. */
    private int[] statsLayout() {
        int graphY = cy + 16 + 24;
        int stripH = 10;
        int statsRowH = 22;
        int graphH = Math.max(40, cy + ch - statsRowH - stripH - 6 - graphY);
        int stripY = graphY + graphH + 3;
        int statsY = stripY + stripH + 3;
        return new int[]{graphY, graphH, stripY, statsY};
    }

    private int stripCellW() {
        return Math.max(2, cw / STRIP_DAYS);
    }

    private int stripX() {
        return cx + (cw - stripCellW() * STRIP_DAYS) / 2;
    }

    /** Keeps the 30-day strip scrolled so the picked day is always inside it. */
    private void syncStrip(int n) {
        int maxStart = Math.max(0, n - STRIP_DAYS);
        if (statsDayIdx >= 0) {
            int idx = Math.min(statsDayIdx, n - 1);
            if (stripStart > maxStart) stripStart = maxStart;
            if (idx < stripStart) stripStart = idx;
            else if (idx >= stripStart + STRIP_DAYS) stripStart = idx - STRIP_DAYS + 1;
        }
        stripStart = Math.max(0, Math.min(stripStart, maxStart));
    }

    /**
     * Reads a date typed by the user: 2026-07-26, 2026/7/26, 26.7.2026, "Jul 26 2026", or without a
     * year ("07-26", "Jul 26" = this year). Returns null if it is not a date.
     */
    static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        int year = LocalDate.now().getYear();
        String[] patterns = {"yyyy-M-d", "yyyy/M/d", "d.M.yyyy", "MMM d yyyy", "MMM d, yyyy", "M-d", "MMM d", "d.M."};
        for (String pat : patterns) {
            try {
                java.time.format.DateTimeFormatter f = new java.time.format.DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern(pat)
                        .parseDefaulting(java.time.temporal.ChronoField.YEAR, year)
                        .toFormatter(java.util.Locale.ENGLISH);
                return LocalDate.parse(t, f);
            } catch (Exception ignored) {
                // try the next format
            }
        }
        return null;
    }

    /** Jumps the Stats page to the date typed in the box. */
    private void goToStatsDate() {
        LocalDate d = parseDate(statsDateBox == null ? statsDateText : statsDateBox.getValue());
        DailyEarnings.Day[] days = DailyEarnings.allDays();
        if (d == null) {
            statusLine = "Type a date like 2026-07-26 or Jul 26.";
            return;
        }
        LocalDate first = LocalDate.parse(days[0].date);
        if (d.isBefore(first)) {
            statusLine = "No data before " + days[0].date + ".";
            statsDayIdx = 0;
            return;
        }
        long idx = java.time.temporal.ChronoUnit.DAYS.between(first, d);
        statsDayIdx = (int) Math.min(idx, days.length - 1);
        statusLine = "";
    }

    private void buildEarningsStatsPage() {
        int y = cy + 16;
        addNeon(cx, y, 36, 20, () -> "Back", () -> false, () -> {
            statsView = false;
            pendingRebuild = true;
        });
        addNeon(cx + 40, y, 32, 20, () -> "ALL", () -> statsDayIdx < 0, () -> statsDayIdx = -1);
        addNeon(cx + 76, y, 20, 20, () -> "<", () -> false, () -> {
            int n = DailyEarnings.allDays().length;
            statsDayIdx = statsDayIdx < 0 ? n - 1 : Math.max(0, Math.min(statsDayIdx, n - 1) - 1);
        });
        addNeon(cx + 100, y, 20, 20, () -> ">", () -> false, () -> {
            int n = DailyEarnings.allDays().length;
            statsDayIdx = statsDayIdx < 0 ? n - 1 : Math.min(n - 1, Math.min(statsDayIdx, n - 1) + 1);
        });
        // search any day since you started using the mod
        statsDateBox = addBox(cx + 124, y, 78, "Date", 12, statsDateText);
        statsDateBox.setResponder(t -> statsDateText = t);
        addNeon(cx + 206, y, 26, 20, () -> "Go", () -> false, this::goToStatsDate);

        int[] L = statsLayout();

        // "Show Most Lost": clicking a pillar marks that person non gamble (invisible click zones;
        // the pillars are painted in drawLossPillars)
        if (DonutQueueClient.config.earningsTopShowLost) {
            for (int i = 0; i < 7; i++) {
                final int idx = i;
                Button zone = Button.builder(Component.literal(""), b -> {
                            List<Map.Entry<String, Double>> list = DailyEarnings.topLossesOver(statsDates(), 7);
                            if (idx < list.size()) markNonGambleName(list.get(idx).getKey());
                        })
                        .bounds(pillarStartX() + i * pillarSlotW(), L[0], pillarSlotW(), L[1]).build();
                this.addRenderableWidget(zone);
            }
        }

        // one tiny clickable cell per day under the graph (painted in paintDayStrip); the strip shows 30 days
        // at a time and scrolls along with the picked day
        for (int i = 0; i < STRIP_DAYS; i++) {
            final int cell = i;
            Button zone = Button.builder(Component.literal(""), b -> {
                        int n = DailyEarnings.allDays().length;
                        syncStrip(n);
                        int day = stripStart + cell;
                        if (day < n) statsDayIdx = day;
                    })
                    .bounds(stripX() + i * stripCellW(), L[2], stripCellW() - 1, 10).build();
            this.addRenderableWidget(zone);
        }
    }

    // ---------------- Sessions page (side menu) ----------------

    private static final int SESS_ROW_H = 24;
    private static final int SESS_LIST_TOP = 103;

    private int sessRows() {
        return Math.max(1, (ch - SESS_LIST_TOP - 16) / SESS_ROW_H);
    }

    private static String sessionStamp(Sessions.Session s) {
        return java.time.format.DateTimeFormatter.ofPattern("MMM dd yyyy HH:mm", java.util.Locale.ENGLISH)
                .format(java.time.Instant.ofEpochMilli(Sessions.startMillis(s)).atZone(java.time.ZoneId.systemDefault()));
    }

    /** Every session ever (newest first), or - if a date is typed in the search box - only that day's sessions. */
    private List<Sessions.Session> sessionList() {
        List<Sessions.Session> all = Sessions.allNewestFirst();
        if (sessFilter.trim().isEmpty()) return all;
        LocalDate d = parseDate(sessFilter);
        if (d == null) return new ArrayList<>();
        String key = d.toString();
        all.removeIf(x -> !Sessions.date(x).equals(key));
        return all;
    }

    private int sessPages() {
        int rows = sessRows();
        return Math.max(1, (sessionList().size() + rows - 1) / rows);
    }

    private void buildSessionsListPage() {
        rowZones.clear();
        delZones.clear();
        int top = cy + 16;
        // start / end button inside the "active session" box
        int hexW = 64;
        int posW = 64;
        addNeon(cx + 6, top + 24, cw - 12 - hexW - 4 - posW - 4, 18, () -> Sessions.isRunning() ? "end session" : "start session",
                Sessions::isRunning, this::toggleSession);

        // where the in-game "Live" pill sits: click to cycle top middle > top left > top right > above hotbar
        addNeon(cx + cw - 6 - hexW - 4 - posW, top + 24, posW, 18, () -> {
            return switch (DonutQueueClient.config.liveBoxPos) {
                case 1 -> "Top left";
                case 2 -> "Top right";
                case 3 -> "Hotbar";
                default -> "Top mid";
            };
        }, () -> false, () -> {
            DonutQueueClient.config.liveBoxPos = (DonutQueueClient.config.liveBoxPos + 1) % 4;
            DonutQueueClient.config.save();
        });

        // hex colour of the in-game "Live" pill (applies as soon as 6 valid hex digits are typed)
        EditBox liveColorBox = addBox(cx + cw - 6 - hexW, top + 23, hexW, "Live color", 7,
                String.format("%06X", DonutQueueClient.config.liveBoxColor & 0xFFFFFF));
        liveColorBox.setHint(Component.literal("Live hex"));
        liveColorBox.setResponder(t -> {
            Integer parsed = DonutQueueConfigScreen.parseHexColor(t);
            if (parsed != null) {
                DonutQueueClient.config.liveBoxColor = parsed;
                DonutQueueClient.config.save();
            }
        });

        // search any date (works for every day since you started using the mod); filters as you type
        sessDateBox = addBox(cx, cy + 66, cw - 54, "Search date", 14, sessFilter);
        sessDateBox.setHint(Component.literal("search date  (2026-07-26 or Jul 26)"));
        sessDateBox.setResponder(t -> {
            sessFilter = t;
            sessPage = 0;
        });
        addNeon(cx + cw - 50, cy + 66, 24, 20, () -> "<", () -> false, () -> sessPage = Math.max(0, sessPage - 1));
        addNeon(cx + cw - 24, cy + 66, 24, 20, () -> ">", () -> false,
                () -> sessPage = Math.min(sessPages() - 1, sessPage + 1));

        // one click zone per row (opens the graph) + one per delete "x"; painted in paintSessionRows,
        // shown/hidden every frame depending on how many sessions the list has
        int rows = sessRows();
        for (int i = 0; i < rows; i++) {
            final int slot = i;
            int ry = cy + SESS_LIST_TOP + i * SESS_ROW_H;
            Button open = Button.builder(Component.literal(""), b -> {
                        List<Sessions.Session> all = sessionList();
                        int idx = sessPage * rows + slot;
                        if (idx < all.size()) {
                            openSession = all.get(idx);
                            sessionView = true;
                            logView = false;
                        remView = false;
                            pendingRebuild = true;
                        }
                    }).bounds(cx, ry, cw - 20, SESS_ROW_H - 3).build();
            Button del = Button.builder(Component.literal(""), b -> {
                        List<Sessions.Session> all = sessionList();
                        int idx = sessPage * rows + slot;
                        if (idx < all.size() && Sessions.delete(all.get(idx))) {
                            statusLine = "Session deleted.";
                        }
                    }).bounds(cx + cw - 19, ry, 19, SESS_ROW_H - 3).build();
            this.addRenderableWidget(open);
            this.addRenderableWidget(del);
            rowZones.add(open);
            delZones.add(del);
        }
    }

    /** Hides the click zones of rows that have no session in them (so no empty boxes show). */
    private void updateRowZones() {
        List<Sessions.Session> all = sessionList();
        int rows = rowZones.size();
        sessPage = Math.max(0, Math.min(sessPages() - 1, sessPage));
        for (int i = 0; i < rows; i++) {
            int idx = sessPage * rows + i;
            boolean exists = idx < all.size();
            rowZones.get(i).visible = exists;
            delZones.get(i).visible = exists && Sessions.endMillis(all.get(idx)) != 0;
        }
    }

    private void drawSessionsList(GuiGraphicsExtractor g, ModConfig c) {
        // active session box
        int top = cy + 16;
        tile(g, cx, top, cw, 46);
        Sessions.Session r = Sessions.running();
        if (r == null) {
            String t = "no active session";
            g.text(this.font, t, cx + (cw - this.font.width(t)) / 2, top + 7, Theme.muted, false);
        } else {
            String t = "LIVE  " + lengthText(Sessions.durationSeconds(r)) + "   " + signedMoney(Sessions.net(r));
            g.text(this.font, t, cx + (cw - this.font.width(t)) / 2, top + 7, Sessions.net(r) >= 0 ? C_GOOD : C_BAD, false);
        }

        // PAST header + page number
        List<Sessions.Session> all = sessionList();
        int pages = sessPages();
        sessPage = Math.max(0, Math.min(pages - 1, sessPage));
        boolean filtered = !sessFilter.trim().isEmpty();
        LocalDate d = filtered ? parseDate(sessFilter) : null;
        String head = !filtered ? "ALL SESSIONS" : (d == null ? "NOT A DATE" : "SESSIONS ON " + d);
        g.text(this.font, head, cx, cy + 90, d == null && filtered ? C_WARN : Theme.neonHi, false);
        String pg = all.size() + " found   " + (sessPage + 1) + "/" + pages;
        g.text(this.font, pg, cx + cw - this.font.width(pg), cy + 90, Theme.muted, false);
        g.fill(cx, cy + 100, cx + cw, cy + 101, Theme.edge);
    }

    /** Painted after the widgets so the invisible click zones underneath don't show through. */
    private void paintSessionRows(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<Sessions.Session> all = sessionList();
        int rows = sessRows();
        for (int i = 0; i < rows; i++) {
            int idx = sessPage * rows + i;
            if (idx >= all.size()) break;
            Sessions.Session s = all.get(idx);
            int ry = cy + SESS_LIST_TOP + i * SESS_ROW_H;
            int rw = cw - 20;
            int rh = SESS_ROW_H - 3;
            boolean live = Sessions.endMillis(s) == 0;
            boolean hov = mouseX >= cx && mouseX < cx + rw && mouseY >= ry && mouseY < ry + rh;

            g.fill(cx, ry, cx + rw, ry + rh, hov ? Theme.btnHov : Theme.tile);
            Gfx.outline(g, cx, ry, rw, rh, hov ? Theme.neonHi : Theme.edge);
            g.text(this.font, sessionStamp(s), cx + 5, ry + 3, Theme.text, false);

            double net = Sessions.net(s);
            String amt = signedMoney(net);
            g.text(this.font, amt, cx + rw - 5 - this.font.width(amt), ry + 3, net >= 0 ? C_GOOD : C_BAD, false);
            String sub = (live ? "LIVE  " : "") + lengthText(Sessions.durationSeconds(s)) + "  -  " + Sessions.events(s) + " events";
            g.text(this.font, sub, cx + 5, ry + 12, live ? C_GOOD : Theme.muted, false);

            // delete "x" (finished sessions only)
            int dx = cx + cw - 19;
            boolean dhov = !live && mouseX >= dx && mouseX < dx + 19 && mouseY >= ry && mouseY < ry + rh;
            g.fill(dx, ry, dx + 19, ry + rh, dhov ? 0xFF7A1F33 : Theme.tile);
            Gfx.outline(g, dx, ry, 19, rh, dhov ? C_BAD : Theme.edge);
            if (!live) g.text(this.font, "x", dx + 7, ry + (rh - 8) / 2, dhov ? 0xFFFFFFFF : Theme.muted, false);
        }
    }

    // ---- one session's graph ----

    /** {graphY, graphH, (unused), statsRowY} for the open session. */
    private int[] detailLayout() {
        int graphY = cy + 16 + 24;
        int statsRowH = 22;
        int graphH = Math.max(40, cy + ch - statsRowH - 6 - graphY);
        return new int[]{graphY, graphH, graphY + graphH, graphY + graphH + 6};
    }

    private void buildSessionDetailPage() {
        int y = cy + 16;
        addNeon(cx, y, 40, 20, () -> "Back", () -> false, () -> {
            sessionView = false;
            openSession = null;
            pendingRebuild = true;
        });
        addNeon(cx + 44, y, 22, 20, () -> "<", () -> false, () -> stepSession(-1));
        addNeon(cx + 70, y, 22, 20, () -> ">", () -> false, () -> stepSession(1));
        addNeon(cx + cw - 64, y, 64, 20, () -> "Payments", () -> false, () -> {
            openPaymentLog();
            pendingRebuild = true;
        });

        int[] L = detailLayout();
        if (DonutQueueClient.config.earningsTopShowLost) {
            for (int i = 0; i < 7; i++) {
                final int idx = i;
                Button zone = Button.builder(Component.literal(""), b -> {
                            Sessions.Session s = selectedSession();
                            if (s == null) return;
                            List<Map.Entry<String, Double>> list = Sessions.topLosses(s, 7);
                            if (idx < list.size()) markNonGambleName(list.get(idx).getKey());
                        })
                        .bounds(pillarStartX() + i * pillarSlotW(), L[0], pillarSlotW(), L[1]).build();
                this.addRenderableWidget(zone);
            }
        }
    }

    /** < goes to the older session, > to the newer one. */
    private void stepSession(int dir) {
        List<Sessions.Session> all = Sessions.allNewestFirst(); // newest first
        int i = all.indexOf(openSession);
        if (i < 0) return;
        int j = i - dir; // newer = lower index
        if (j >= 0 && j < all.size()) {
            openSession = all.get(j);
            logScroll = 0;
            pendingRebuild = true;
        }
    }

    private static String clock(long millis) {
        java.time.LocalTime t = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalTime();
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private void drawSessionsView(GuiGraphicsExtractor g, int mouseX, int mouseY, ModConfig c) {
        int[] L = detailLayout();
        Sessions.Session s = openSession;
        if (s == null) return;

        String head = Sessions.date(s).substring(5) + "  (" + (c.earningsTopShowLost ? "lost to" : "balance") + ")";
        g.text(this.font, head, cx + cw - this.font.width(head), cy, Theme.text, false);

        boolean live = Sessions.endMillis(s) == 0;
        String info = clock(Sessions.startMillis(s)) + "-" + (live ? "LIVE" : clock(Sessions.endMillis(s)));
        g.text(this.font, info, cx + 98, cy + 16 + 6, live ? C_GOOD : Theme.text, false);

        int q = cw / 4;
        if (c.earningsTopShowLost) {
            // pillars are painted last (see render)
            double total = Sessions.totalLoss(s);
            statCell(g, cx, L[3], "TOTAL LOST", total > 0 ? "-$" + DonutQueueClient.formatCompact(total) : "$0", total > 0 ? C_BAD : Theme.muted);
            statCell(g, cx + q, L[3], "PEOPLE", String.valueOf(Sessions.topLosses(s, 1000).size()), Theme.text);
            statCell(g, cx + q * 2, L[3], "LENGTH", lengthText(Sessions.durationSeconds(s)), Theme.muted);
            int hidden = c.nonGamblePlayers.size();
            statCell(g, cx + q * 3, L[3], "NON GAMBLE", hidden + " hidden", hidden > 0 ? C_WARN : Theme.muted);
        } else {
            drawSessionGraph(g, cx, L[0], cw, L[1], s, mouseX, mouseY);
            double net = Sessions.net(s);
            statCell(g, cx, L[3], "SESSION NET", signedMoney(net), net >= 0 ? C_GOOD : C_BAD);
            statCell(g, cx + q, L[3], "BALANCE", Sessions.hasBalance(s) ? "$" + DonutQueueClient.formatCompact(Sessions.endBalance(s)) : "no start bal", Theme.text);
            statCell(g, cx + q * 2, L[3], "LENGTH", lengthText(Sessions.durationSeconds(s)), Theme.text);
            statCell(g, cx + q * 3, L[3], "EVENTS", String.valueOf(Sessions.events(s)), Theme.muted);
        }
    }

    /** One session as the same kind of line graph the Stats page uses: your balance from start to end. */
    private void drawSessionGraph(GuiGraphicsExtractor g, int gx, int gy, int gw, int gh,
                                  Sessions.Session s, int mouseX, int mouseY) {
        final int n = 96;
        double[] v = new double[n];
        boolean[] has = new boolean[n];
        String[] titles = new String[n];
        if (s == null) {
            drawLinePlot(g, gx, gy, gw, gh, v, has, Double.NaN, false, new int[]{0}, new String[]{""},
                    titles, mouseX, mouseY, "No session on this day");
            return;
        }
        double[][] pts = Sessions.points(s);
        double real = Sessions.durationSeconds(s);
        double dur = Math.max(60, real);
        double base = Sessions.startBalance(s);
        long startMs = Sessions.startMillis(s);
        int pi = 0;
        double running = 0;
        for (int b = 0; b < n; b++) {
            double tEnd = dur * (b + 1) / n;
            while (pi < pts.length && pts[pi][0] <= tEnd) {
                running = pts[pi][1];
                pi++;
            }
            v[b] = base + running;
            has[b] = dur * b / n <= real;
            titles[b] = clock(startMs + (long) (tEnd * 1000));
        }
        int[] ti = {0, 24, 48, 72, n - 1};
        String[] tl = new String[ti.length];
        for (int i = 0; i < ti.length; i++) {
            double f = ti[i] == n - 1 ? 1.0 : ti[i] / (double) n;
            tl[i] = clock(startMs + (long) (dur * f * 1000));
        }
        drawLinePlot(g, gx, gy, gw, gh, v, has, base, false, ti, tl, titles, mouseX, mouseY,
                "No payments in this session");
    }

    private void paintDayStrip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        DailyEarnings.Day[] days = DailyEarnings.allDays();
        syncStrip(days.length);
        int[] L = statsLayout();
        int cwid = stripCellW();
        int sx = stripX();
        for (int i = 0; i < STRIP_DAYS; i++) {
            int di = stripStart + i;
            if (di >= days.length) break;
            int x = sx + i * cwid;
            int y = L[2];
            double net = days[di].net;
            int col = net > 0 ? 0xFF1E9E6E : (net < 0 ? 0xFFB03A56 : Theme.flat);
            boolean sel = di == Math.min(statsDayIdx, days.length - 1) && statsDayIdx >= 0;
            boolean hov = mouseX >= x && mouseX < x + cwid && mouseY >= y && mouseY < y + 10;
            g.fill(x, y, x + cwid - 1, y + 10, col);
            if (sel) {
                g.fill(x, y, x + cwid - 1, y + 10, Theme.neonHi);
                g.fill(x + 1, y + 1, x + cwid - 2, y + 9, col);
                Gfx.outline(g, x, y, cwid - 1, 10, 0xFFFFFFFF);
            } else if (hov) {
                Gfx.outline(g, x, y, cwid - 1, 10, Theme.neonHi);
            }
            if (hov) {
                String tip = days[di].date;
                int tw = this.font.width(tip) + 6;
                int tx = Math.max(cx, Math.min(cx + cw - tw, mouseX - tw / 2));
                g.fill(tx, y - 13, tx + tw, y - 2, 0xF0071138);
                Gfx.outline(g, tx, y - 13, tw, 11, Theme.neon);
                g.text(this.font, tip, tx + 3, y - 11, 0xFFFFFFFF, false);
            }
        }
    }

    private void drawStatsView(GuiGraphicsExtractor g, int mouseX, int mouseY, ModConfig c) {
        DailyEarnings.Day[] days = DailyEarnings.allDays();
        int[] L = statsLayout();
        int idx = Math.min(statsDayIdx, days.length - 1);

        // date shown between the < and > buttons
        String lbl = idx < 0 ? "ALL TIME" : days[idx].date;
        String mode = c.earningsTopShowLost ? "lost to" : "balance";
        String head = lbl + "  (" + mode + ")";
        g.text(this.font, head, cx + cw - this.font.width(head), cy, Theme.text, false);

        if (c.earningsTopShowLost) {
            // "Show Most Lost" is on: the graph becomes a pillar chart of the 7 people you lost most to
            List<String> dates = statsDates(); // pillars themselves are painted last, over the click zones
            double lossTotal = DailyEarnings.totalLossOver(dates);
            int q = cw / 4;
            statCell(g, cx, L[3], "TOTAL LOST", lossTotal > 0 ? "-$" + DonutQueueClient.formatCompact(lossTotal) : "$0", lossTotal > 0 ? C_BAD : Theme.muted);
            statCell(g, cx + q, L[3], "PEOPLE", String.valueOf(DailyEarnings.topLossesOver(dates, 1000).size()), Theme.text);
            statCell(g, cx + q * 2, L[3], idx < 0 ? "RANGE" : "DAY", idx < 0 ? "all time" : days[idx].date.substring(2), Theme.muted);
            int hidden = c.nonGamblePlayers.size();
            statCell(g, cx + q * 3, L[3], "NON GAMBLE", hidden + " hidden", hidden > 0 ? C_WARN : Theme.muted);
        } else if (idx < 0) {
            drawOverviewLine(g, cx, L[0], cw, L[1], days, false, mouseX, mouseY);
            drawOverviewStats(g, cx, L[3], days);
        } else {
            drawDayGraph(g, cx, L[0], cw, L[1], days[idx].date,
                    Double.isNaN(days[idx].balance) ? Double.NaN : days[idx].balance - days[idx].net, false, mouseX, mouseY);
            double[][] pts = DailyEarnings.dayPoints(days[idx].date);
            double net = days[idx].net;
            int q = cw / 4;
            statCell(g, cx, L[3], "DAY NET", pts.length == 0 && net == 0 ? "no data" : signedMoney(net), net >= 0 ? C_GOOD : C_BAD);
            statCell(g, cx + q, L[3], "EVENTS", String.valueOf(pts.length), Theme.text);
            statCell(g, cx + q * 2, L[3], "BALANCE", Double.isNaN(days[idx].balance) ? "no data" : "$" + DonutQueueClient.formatCompact(days[idx].balance), Theme.text);
            statCell(g, cx + q * 3, L[3], "DATE", days[idx].date.substring(5), Theme.muted);
        }
    }

    /**
     * Pillar chart for "Show Most Lost": the 7 people you lost the most to, one pillar each.
     * Above a pillar: a ring with their share of your total loss (like the Starting Bal ring),
     * then the amount they won from you. Under it: their skin head, then their name.
     */
    private void drawLossPillars(GuiGraphicsExtractor g, int gx, int gy, int gw, int gh,
                                 List<Map.Entry<String, Double>> top, double total, String msg,
                                 int mouseX, int mouseY) {
        g.fill(gx, gy, gx + gw, gy + gh, Theme.plot);
        Gfx.outline(g, gx, gy, gw, gh, Theme.edge);

        if (top.isEmpty() || total <= 0) {
            g.text(this.font, msg, gx + (gw - this.font.width(msg)) / 2, gy + gh / 2 - 4, Theme.muted, false);
            return;
        }

        final int slots = 7;
        int slotW = (gw - 4) / slots;
        int startX = gx + 2 + ((gw - 4) - slotW * slots) / 2;

        int headSize = 12;
        int bottomH = headSize + 2 + 9 + 3;        // head + gap + name + padding
        int ringR = gh >= 125 ? 12 : (gh >= 100 ? 10 : 8);
        int topH = 3 + ringR * 2 + 2 + 9 + 2;      // ring + gap + amount line
        int minPillar = 12;
        // if the card is too short for the ring, drop it rather than squash the pillars
        boolean showRing = gh - bottomH - topH >= minPillar + 8;
        if (!showRing) topH = 3 + 9 + 2;

        int baseY = gy + gh - bottomH;               // bottom edge of every pillar
        int maxPillarH = Math.max(minPillar, baseY - (gy + topH));
        double biggest = -top.get(0).getValue();

        g.fill(gx + 2, baseY, gx + gw - 2, baseY + 1, Theme.edge);

        for (int i = 0; i < top.size(); i++) {
            String name = top.get(i).getKey();
            double lost = -top.get(i).getValue();
            double share = lost / total;

            int sx = startX + i * slotW;
            int mid = sx + slotW / 2;
            int pw2 = Math.max(8, slotW - 10);
            int ph2 = Math.max(3, (int) Math.round(lost / biggest * maxPillarH));
            int top2 = baseY - ph2;

            // pillar: red, brighter edge on top, faint glow
            g.fill(mid - pw2 / 2 - 1, top2 - 1, mid + pw2 / 2 + 1, baseY, 0x33FF5577);
            g.fill(mid - pw2 / 2, top2, mid + pw2 / 2, baseY, 0xFFB03A56);
            g.fill(mid - pw2 / 2, top2, mid + pw2 / 2, top2 + 2, C_BAD);

            // amount they won from you, right above the pillar
            String amt = "-$" + DonutQueueClient.formatCompact(lost);
            int amtW = this.font.width(amt);
            float amtScale = amtW > slotW - 1 ? (slotW - 1) / (float) amtW : 1.0f;
            int amtY = top2 - 10;
            if (amtScale < 1.0f) {
                drawScaled(g, amt, mid - (int) (amtW * amtScale / 2), amtY + 1, amtScale, C_BAD);
            } else {
                g.text(this.font, amt, mid - amtW / 2, amtY, C_BAD, false);
            }

            // % ring above the amount
            if (showRing) {
                int ringCy = Math.max(gy + 3 + ringR, amtY - 3 - ringR);
                drawPercentRing(g, mid, ringCy, ringR, share, C_BAD);
            }

            // skin head + name under the pillar
            DonutQueueClient.drawHead(g, name, mid - headSize / 2, baseY + 2, headSize);
            String shown = name.startsWith(".") && this.font.width(name) > slotW ? name.substring(1) : name;
            while (shown.length() > 1 && this.font.width(shown) > slotW - 1) shown = shown.substring(0, shown.length() - 1);
            g.text(this.font, shown, mid - this.font.width(shown) / 2, baseY + 2 + headSize + 2, Theme.text, false);

            // hover read-out
            if (mouseX >= sx && mouseX < sx + slotW && mouseY >= gy && mouseY < gy + gh) {
                String tip = name + "  -$" + DonutQueueClient.formatCompact(lost) + "  (" + percentText(share) + " of your loss)  - click = non gamble";
                int tw = this.font.width(tip) + 6;
                int tx = Math.max(gx + 2, Math.min(gx + gw - tw - 2, mouseX - tw / 2));
                int ty = gy + 2;
                g.fill(tx, ty, tx + tw, ty + 11, 0xF0071138);
                Gfx.outline(g, tx, ty, tw, 11, Theme.neon);
                g.text(this.font, tip, tx + 3, ty + 2, 0xFFFFFFFF, false);
            }
        }
    }

    private static String percentText(double share) {
        double pct = share * 100.0;
        if (pct > 0 && pct < 1) return "<1%";
        return Math.round(pct) + "%";
    }

    /**
     * Ring of little squares (same look as the Starting Bal ring) that fills clockwise from the top
     * with `fraction`, the unfilled squares dim, and the percent written in the middle.
     */
    private void drawPercentRing(GuiGraphicsExtractor g, int centerX, int centerY, int r, double fraction, int color) {
        double f = Math.max(0, Math.min(1, fraction));
        int ticks = 24;
        int lit = (int) Math.round(ticks * f);
        if (f > 0 && lit == 0) lit = 1;
        int half = r >= 10 ? 1 : 0;                  // square is (2*half+1) px wide
        for (int i = 0; i < ticks; i++) {
            double a = (Math.PI * 2 * i / ticks) - Math.PI / 2;
            int tx = centerX + (int) Math.round(Math.cos(a) * r);
            int ty = centerY + (int) Math.round(Math.sin(a) * r);
            g.fill(tx - half, ty - half, tx + half + 1, ty + half + 1, i < lit ? color : Theme.ringOff);
        }
        String t = percentText(f);
        float sc = t.length() >= 4 ? 0.6f : (t.length() == 3 ? 0.75f : 0.9f);
        int tw = (int) Math.round(this.font.width(t) * sc);
        drawScaled(g, t, centerX - tw / 2, centerY - (int) Math.round(4 * sc), sc, 0xFFFFFFFF);
    }

    private static String shortMoney(double v) {
        return (v < 0 ? "-" : "") + DonutQueueClient.formatCompact(Math.abs(v));
    }

    /** One day's graph: a line of your net for that day in 10-minute steps (0 = start of the day). */
    private void drawDayGraph(GuiGraphicsExtractor g, int gx, int gy, int gw, int gh, String date, double openBal, boolean gains, int mouseX, int mouseY) {
        double[][] pts = DailyEarnings.dayPoints(date);
        final int n = 144; // 24h * 6 ten-minute steps
        double[] v = new double[n];
        boolean[] has = new boolean[n];
        String[] titles = new String[n];

        boolean isToday = date.equals(LocalDate.now().toString());
        int lastB = isToday ? Math.min(n - 1, java.time.LocalTime.now().toSecondOfDay() / 600) : n - 1;

        double running = 0;
        int pi = 0;
        for (int b = 0; b < n; b++) {
            while (pi < pts.length && pts[pi][0] < (b + 1) * 600.0) {
                running = pts[pi][1];
                pi++;
            }
            v[b] = gains ? running : openBal + running;
            has[b] = pts.length > 0 && b <= lastB && (gains || !Double.isNaN(openBal));
            titles[b] = String.format("%02d:%02d", b / 6, (b % 6) * 10);
        }
        drawLinePlot(g, gx, gy, gw, gh, v, has, gains ? 0 : openBal, gains,
                new int[]{0, 36, 72, 108, 143}, new String[]{"00:00", "06:00", "12:00", "18:00", "24:00"},
                titles, mouseX, mouseY,
                !gains && Double.isNaN(openBal) && pts.length > 0 ? "No balance known for this day" : "No payments recorded this day");
    }

    /** All-time overview as a line: your balance at the end of each day. */
    private void drawOverviewLine(GuiGraphicsExtractor g, int gx, int gy, int gw, int gh,
                                  DailyEarnings.Day[] days, boolean gains, int mouseX, int mouseY) {
        int n = days.length;
        double[] v = new double[n];
        boolean[] has = new boolean[n];
        String[] titles = new String[n];
        for (int i = 0; i < n; i++) {
            titles[i] = days[i].date;
            has[i] = gains || !Double.isNaN(days[i].balance);
            v[i] = gains ? days[i].net : (has[i] ? days[i].balance : 0);
        }
        int[] ti = {0, n / 4, n / 2, (n * 3) / 4, n - 1};
        String[] tl = new String[ti.length];
        for (int i = 0; i < ti.length; i++) tl[i] = i == ti.length - 1 ? "today" : titles[ti[i]].substring(2);
        drawLinePlot(g, gx, gy, gw, gh, v, has, gains ? 0 : Double.NaN, gains, ti, tl, titles, mouseX, mouseY,
                "Start tracking to see the graph");
    }

    /**
     * Clean line chart (like a price chart): blue line, faint grid, value axis on the right,
     * a tag on the latest value, and a crosshair + dot + read-out on hover.
     */
    private void drawLinePlot(GuiGraphicsExtractor g, int gx, int gy, int gw, int gh,
                              double[] v, boolean[] has, double baseline, boolean signed,
                              int[] tickIdx, String[] tickLabels, String[] titles,
                              int mouseX, int mouseY, String emptyText) {
        final int labelW = 46;
        final int plotX = gx + 2;
        final int plotW = gw - labelW - 4;
        final int plotY = gy + 3;
        final int plotH = gh - 13;
        if (plotW < 30 || plotH < 14) return;
        final int n = v.length;
        final int LINE = Theme.neonHi;

        g.fill(plotX, plotY, plotX + plotW, plotY + plotH, Theme.plot);
        Gfx.outline(g, plotX, plotY, plotW, plotH, Theme.edge);

        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        int last = -1;
        for (int i = 0; i < n; i++) {
            if (!has[i]) continue;
            last = i;
            lo = Math.min(lo, v[i]);
            hi = Math.max(hi, v[i]);
        }
        if (last < 0) {
            g.text(this.font, emptyText, plotX + 6, plotY + plotH / 2 - 4, Theme.muted, false);
            return;
        }
        if (!Double.isNaN(baseline)) {
            lo = Math.min(lo, baseline);
            hi = Math.max(hi, baseline);
        }
        double span = hi - lo;
        if (span < 1) span = Math.max(1, Math.abs(hi) * 0.02);
        final double fLo = lo - span * 0.12;
        final double fHi = hi + span * 0.12;
        java.util.function.DoubleToIntFunction yOf = val ->
                plotY + plotH - 2 - (int) Math.round((val - fLo) / (fHi - fLo) * (plotH - 4));
        java.util.function.IntUnaryOperator xOf = i ->
                plotX + 1 + (int) Math.round(i * (plotW - 3) / (double) Math.max(1, n - 1));

        // vertical grid + x labels
        for (int t = 0; t < tickIdx.length; t++) {
            int tx = xOf.applyAsInt(tickIdx[t]);
            g.fill(tx, plotY + 1, tx + 1, plotY + plotH - 1, Theme.neonA(0x14));
            int lw = this.font.width(tickLabels[t]);
            int lx = Math.max(plotX, Math.min(plotX + plotW - lw, tx - lw / 2));
            g.text(this.font, tickLabels[t], lx, plotY + plotH + 3, Theme.muted, false);
        }

        // horizontal grid + value axis on the right
        for (int i = 0; i <= 4; i++) {
            double val = fLo + (fHi - fLo) * i / 4.0;
            int yy = yOf.applyAsInt(val);
            g.fill(plotX + 1, yy, plotX + plotW - 1, yy + 1, Theme.neonA(0x18));
            g.text(this.font, axisMoney(val, signed), plotX + plotW + 4, yy - 4, Theme.muted, false);
        }

        // zero / start line
        if (!Double.isNaN(baseline)) {
            int by = yOf.applyAsInt(baseline);
            g.fill(plotX + 1, by, plotX + plotW - 1, by + 1, 0x889A8B5F);
        }

        // the line
        for (int i = 1; i < n; i++) {
            if (!has[i] || !has[i - 1]) continue;
            int x0 = xOf.applyAsInt(i - 1), x1 = xOf.applyAsInt(i);
            int y0 = yOf.applyAsInt(v[i - 1]), y1 = yOf.applyAsInt(v[i]);
            for (int x = x0; x <= x1; x++) {
                double t0 = x1 == x0 ? 1.0 : (x - x0) / (double) (x1 - x0);
                double t1 = x1 == x0 ? 1.0 : (x + 1 - x0) / (double) (x1 - x0);
                int ya = (int) Math.round(y0 + (y1 - y0) * t0);
                int yb = x < x1 ? (int) Math.round(y0 + (y1 - y0) * t1) : ya;
                g.fill(x, Math.min(ya, yb), x + 1, Math.max(ya, yb) + 2, LINE);
            }
        }

        // latest value: dot + tag on the axis
        int lx = xOf.applyAsInt(last);
        int ly = yOf.applyAsInt(v[last]);
        g.fill(plotX + 1, ly, plotX + plotW - 1, ly + 1, Theme.neonA(0x55));
        g.fill(lx - 1, ly - 1, lx + 2, ly + 3, 0xFFFFFFFF);
        tag(g, plotX + plotW, labelW, ly, LINE, axisMoney(v[last], signed));

        // crosshair + read-out
        if (mouseX >= plotX && mouseX < plotX + plotW && mouseY >= plotY && mouseY < plotY + plotH) {
            int idx = (int) Math.round((mouseX - plotX - 1) / (double) (plotW - 3) * (n - 1));
            idx = Math.max(0, Math.min(n - 1, idx));
            int hx = xOf.applyAsInt(idx);
            g.fill(hx, plotY + 1, hx + 1, plotY + plotH - 1, Theme.neonHiA(0x55));
            g.fill(plotX + 1, mouseY, plotX + plotW - 1, mouseY + 1, Theme.neonHiA(0x33));
            if (has[idx]) {
                int hy = yOf.applyAsInt(v[idx]);
                g.fill(hx - 2, hy - 1, hx + 3, hy + 3, 0xFFFFFFFF);
                g.fill(hx - 1, hy, hx + 2, hy + 2, LINE);
                g.text(this.font, titles[idx] + "   " + tipMoney(v[idx], signed), plotX + 4, plotY + 3, Theme.text, true);
            } else {
                g.text(this.font, titles[idx] + "   no data", plotX + 4, plotY + 3, Theme.muted, true);
            }
        }
    }

    private void drawOverviewStats(GuiGraphicsExtractor g, int x0, int y, DailyEarnings.Day[] days) {
        double net = 0;
        double best = Double.NEGATIVE_INFINITY;
        double worst = Double.POSITIVE_INFINITY;
        String bestD = "";
        String worstD = "";
        int active = 0;
        for (DailyEarnings.Day d : days) {
            net += d.net;
            if (d.net != 0) {
                active++;
                if (d.net > best) { best = d.net; bestD = d.date; }
                if (d.net < worst) { worst = d.net; worstD = d.date; }
            }
        }
        String todayKey = LocalDate.now().toString();
        int q = cw / 4;
        statCell(g, x0, y, "TOTAL NET", active == 0 ? "no data" : signedMoney(net), net >= 0 ? C_GOOD : C_BAD);
        statCell(g, x0 + q, y, "BEST DAY", active == 0 ? "no data" : signedMoney(best) + (bestD.equals(todayKey) ? " (today)" : ""), C_GOOD);
        statCell(g, x0 + q * 2, y, "WORST DAY", active == 0 ? "no data" : signedMoney(worst) + (worstD.equals(todayKey) ? " (today)" : ""), C_BAD);
        statCell(g, x0 + q * 3, y, "AVG / DAY", active == 0 ? "no data" : signedMoney(net / active), Theme.text);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** This screen paints its own backdrop; Screen#render would otherwise draw the vanilla one over it. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        renderMenu(g, mouseX, mouseY, partialTick);
        Tutorial.renderOverlay(g, this.minecraft);
    }

    private void renderMenu(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (pendingRebuild) {
            pendingRebuild = false;
            this.clearWidgets();
            buildWidgets();
        }

        ModConfig c = DonutQueueClient.config;

        if (editMode != EDIT_NONE) {
            renderEdit(g, mouseX, mouseY, partialTick, c);
            return;
        }

        // page-switch transition: starts when the page changed since the last frame
        if (page != shownPage) {
            transDir = page > shownPage ? 1 : -1;
            shownPage = page;
            transStart = System.currentTimeMillis();
        }
        float anim = MenuAnim.progress(transStart, c);
        int slideDy = MenuAnim.slideOffset(anim, transDir, c);

        // backdrop + glowing frame
        g.fill(0, 0, this.width, this.height, Theme.dim);
        if (Theme.glow) {
            Gfx.outline(g, px - 2, py - 2, pw + 4, ph + 4, Theme.neonA(0x22));
            Gfx.outline(g, px - 1, py - 1, pw + 2, ph + 2, Theme.neonA(0x55));
        }
        g.fill(px, py, px + pw, py + ph, Theme.panel);

        // header (top-left corner): donut logo + name
        g.fill(px + 1, py + 1, rx - 4, py + headerH, Theme.header);
        g.fill(px + 1, py + headerH, rx - 4, py + headerH + 1, Theme.neon);
        DonutLogo.draw(g, px + 6, py + 4, 1);
        g.text(this.font, "DonutSMP Streamer Utilitys", px + 38, py + 13, Theme.neonHi, true);

        // sidebar
        g.fill(px + 1, py + headerH + 1, px + sideW, py + ph - 1, Theme.sidebar);
        g.fill(px + sideW, py + headerH + 1, px + sideW + 1, py + ph - 1, Theme.edge);
        if (page == 5) {
            // bottom-left corner of the Earnings menu
            g.text(this.font, "Inspired By Gambler Plus", cx, py + ph - 14, 0xFF8A8A8A, false);
        }

        // page content (slid a few pixels while the Slide animation plays)
        g.pose().pushMatrix();
        g.pose().translate(0f, (float) slideDy);
        g.text(this.font, PAGES[page].toUpperCase() + (page == 5 && remView ? " - REMOVED" : page == 5 && logView ? " - PAYMENTS" : page == 5 && statsView ? " - STATS" : ""), cx, cy, Theme.neonHi, true);
        g.fill(cx, cy + 10, cx + cw, cy + 11, Theme.edge);
        switch (page) {
            case 0 -> drawMainPage(g, mouseX, mouseY, c);
            case 2 -> drawStartBalInfo(g, c);
            case 3 -> drawBiddingInfo(g, c);
            case 4 -> drawInfoInfo(g, c);
            case 5 -> {
                if (remView) drawRemoved(g, mouseX, mouseY);
                else if (logView) drawPaymentLog(g, mouseX, mouseY);
                else if (statsView) drawStatsView(g, mouseX, mouseY, c);
            }
            case 6 -> {
                if (sessionView) {
                    if (remView) drawRemoved(g, mouseX, mouseY);
                    else if (logView) drawPaymentLog(g, mouseX, mouseY);
                    else drawSessionsView(g, mouseX, mouseY, c);
                } else drawSessionsList(g, c);
            }
            default -> { }
        }

        for (Label l : labels) {
            g.text(this.font, l.text, l.x, l.y, Theme.muted, false);
        }

        if (!statusLine.isEmpty()) {
            g.text(this.font, statusLine, cx, py + ph - 14, C_WARN, true);
        }
        g.pose().popMatrix();

        // right rail
        drawRail(g, mouseX, mouseY, c);

        if (page == 6 && !sessionView && !rowZones.isEmpty()) updateRowZones();

        // vanilla widgets (invisible buttons + text fields); page widgets ride along with the slide
        List<AbstractWidget> slid = new ArrayList<>();
        if (slideDy != 0) {
            for (Object o : this.children()) {
                if (o instanceof AbstractWidget w && !sideWidgets.contains(w)) {
                    w.setY(w.getY() + slideDy);
                    slid.add(w);
                }
            }
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // neon faces painted over the invisible vanilla buttons
        paintNeonFaces(g, mouseX, mouseY, slideDy);
        if (page == 5 && statsView) paintDayStrip(g, mouseX, mouseY);
        if (page == 6 && !sessionView) paintSessionRows(g, mouseX, mouseY);
        if (page == 5 || (page == 6 && sessionView)) {
            // painted after the widgets so the invisible click zones underneath don't show through
            if (page == 6 && c.earningsTopShowLost && !logView && !remView) {
                int[] L = detailLayout();
                Sessions.Session s = selectedSession();
                drawLossPillars(g, cx, L[0], cw, L[1],
                        s == null ? new ArrayList<>() : Sessions.topLosses(s, 7),
                        s == null ? 0 : Sessions.totalLoss(s),
                        s == null ? "No session on this day" : "No losses in this session", mouseX, mouseY);
            } else if (page == 5 && statsView && !logView && !remView && c.earningsTopShowLost) {
                int[] L = statsLayout();
                List<String> dates = statsDates();
                drawLossPillars(g, cx, L[0], cw, L[1],
                        DailyEarnings.topLossesOver(dates, 7), DailyEarnings.totalLossOver(dates),
                        "No losses " + (dates.size() > 1 ? "in all time" : "on this day"), mouseX, mouseY);
            }
            drawTopPlayers(g, rx, py + 6 + 70 + 8, railW, mouseX, mouseY);
        }
        for (EditBox box : boxes) {
            Gfx.outline(g, box.getX() - 1, box.getY() - 1, box.getWidth() + 2, box.getHeight() + 2, Theme.neon);
        }
        for (AbstractWidget w : slid) w.setY(w.getY() - slideDy);

        // fade / wipe cover over the page area (the sidebar and right rail are left alone)
        MenuAnim.drawCover(g, px + sideW + 2, py + headerH + 2, rx - 3, py + ph - 1, anim, c);
    }

    // ---- main page -----------------------------------------------------

    // ---------------- Main page layout (order + show/hide) ----------------

    static int mainIndex(String id) {
        for (int i = 0; i < MAIN_IDS.length; i++) if (MAIN_IDS[i].equals(id)) return i;
        return -1;
    }

    /** The saved order, tidied: every item exactly once, unknown names dropped. */
    static List<String> mainOrder(ModConfig c) {
        List<String> o = new ArrayList<>();
        if (c.mainOrder != null) {
            for (String id : c.mainOrder) if (mainIndex(id) >= 0 && !o.contains(id)) o.add(id);
        }
        for (String id : MAIN_IDS) if (!o.contains(id)) o.add(id);
        c.mainOrder = o;
        return o;
    }

    static boolean mainShown(ModConfig c, String id) {
        return switch (id) {
            case "START_BAL" -> c.mainShowStartBal;
            case "TODAY" -> c.mainShowToday;
            case "GRAPH" -> c.mainShowGraph;
            case "STATS" -> c.mainShowStats;
            case "BUTTONS" -> c.mainShowButtons;
            default -> false;
        };
    }

    static void setMainShown(ModConfig c, String id, boolean v) {
        switch (id) {
            case "START_BAL" -> c.mainShowStartBal = v;
            case "TODAY" -> c.mainShowToday = v;
            case "GRAPH" -> c.mainShowGraph = v;
            case "STATS" -> c.mainShowStats = v;
            case "BUTTONS" -> c.mainShowButtons = v;
            default -> { }
        }
    }

    private static boolean isHalf(String id) {
        return id.equals("START_BAL") || id.equals("TODAY");
    }

    private static int rowHeight(String id) {
        return switch (id) {
            case "START_BAL", "TODAY" -> 38;
            case "STATS" -> 22;
            case "BUTTONS" -> DonutQueueClient.config.mainFourButtons ? 44 : 20;
            default -> 0; // GRAPH is the flexible one
        };
    }

    /**
     * Works out where each Main Menu item goes: {x, y, w, h}. Items stack top to bottom in the saved order
     * (Start Bal and Today sit side by side when they are next to each other), the Graph takes whatever
     * height is left. all = true includes hidden items too (the editor shows them as ghosts).
     */
    private Map<String, int[]> mainLayout(ModConfig c, boolean all) {
        List<List<String>> rows = new ArrayList<>();
        for (String id : mainOrder(c)) {
            if (!all && !mainShown(c, id)) continue;
            List<String> last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (isHalf(id) && last != null && last.size() == 1 && isHalf(last.get(0))) {
                last.add(id);
            } else {
                List<String> r = new ArrayList<>();
                r.add(id);
                rows.add(r);
            }
        }
        int fixed = 0;
        boolean hasGraph = false;
        for (List<String> r : rows) {
            if (r.get(0).equals("GRAPH")) hasGraph = true;
            else fixed += rowHeight(r.get(0));
        }
        int gaps = Math.max(0, rows.size() - 1) * MAIN_GAP;
        int graphRow = hasGraph ? Math.max(11 + 34, (ch - 16) - fixed - gaps) : 0;

        Map<String, int[]> out = new LinkedHashMap<>();
        int y = cy + 16;
        int startW = (cw * 11) / 20;
        for (List<String> r : rows) {
            int h = r.get(0).equals("GRAPH") ? graphRow : rowHeight(r.get(0));
            if (r.size() == 2) {
                int x = cx;
                for (String id : r) {
                    int w = id.equals("START_BAL") ? startW : cw - startW - 4;
                    out.put(id, new int[]{x, y, w, h});
                    x += w + 4;
                }
            } else {
                out.put(r.get(0), new int[]{cx, y, cw, h});
            }
            y += h + MAIN_GAP;
        }
        return out;
    }

    private void drawMainPage(GuiGraphicsExtractor g, int mouseX, int mouseY, ModConfig c) {
        Map<String, int[]> lay = mainLayout(c, false);
        if (lay.isEmpty()) {
            int y = cy + 16;
            g.text(this.font, "Nothing is shown on this page.", cx, y, Theme.muted, false);
            g.text(this.font, "Customisation > Menu customisation >", cx, y + 12, Theme.muted, false);
            g.text(this.font, "Main menu items", cx, y + 23, Theme.muted, false);
            return;
        }
        for (Map.Entry<String, int[]> e : lay.entrySet()) {
            drawBlock(g, e.getKey(), e.getValue(), mouseX, mouseY, c, false);
        }
    }

    /** Draws one Main Menu item inside its rectangle r = {x, y, w, h}. */
    private void drawBlock(GuiGraphicsExtractor g, String id, int[] r, int mouseX, int mouseY, ModConfig c, boolean editing) {
        int x = r[0];
        int y = r[1];
        int w = r[2];
        int h = r[3];
        switch (id) {
            case "START_BAL" -> {
                // starts at 0 - the real balance is only the small number underneath
                tile(g, x, y, w, h);
                g.text(this.font, "START BAL", x + 5, y + 4, Theme.muted, false);
                if (c.awaitingBalCapture) {
                    g.text(this.font, "$0", x + 5, y + 15, C_WARN, true);
                    g.text(this.font, "run /bal in chat now", x + 5, y + 27, C_WARN, false);
                } else {
                    int col = c.earnedTotal >= 0 ? C_GOOD : C_BAD;
                    drawScaled(g, money(c.earnedTotal), x + 5, y + 14, 1.5f, col);
                    String under = c.hasStartingBalance ? "from $" + DonutQueueClient.formatCompact(c.startingBalance) : "--";
                    g.text(this.font, under, x + 5, y + 28, Theme.muted, false);
                }
            }
            case "TODAY" -> {
                tile(g, x, y, w, h);
                g.text(this.font, "TODAY", x + 5, y + 4, Theme.muted, false);
                double today = DailyEarnings.todayNet();
                g.text(this.font, signedMoney(today), x + 5, y + 15, today >= 0 ? C_GOOD : C_BAD, true);
                String state = c.awaitingBalCapture ? "WAITING" : (c.awaitingOrderBalCapture ? "RUN /bal" : (c.trackingEnabled ? "TRACKING" : "IDLE"));
                g.text(this.font, state, x + 5, y + 27, c.awaitingOrderBalCapture || c.awaitingBalCapture ? C_WARN : (c.trackingEnabled ? C_GOOD : Theme.muted), false);
            }
            case "GRAPH" -> {
                String gTitle = (c.mainGraphRange == 1 ? "ALL TIME" : "TODAY") + (c.mainGraphGains ? " GAINS" : " BALANCE");
                g.text(this.font, gTitle, x, y, Theme.neonHi, false);
                String sw = editing ? "click to change" : (c.mainGraphRange == 1 ? "per day" : "10 min graph");
                g.text(this.font, sw, x + w - this.font.width(sw), y, Theme.muted, false);
                int mx = editing ? -9999 : mouseX; // no hover read-out while arranging
                int my = editing ? -9999 : mouseY;
                drawGraph(g, x, y + 11, w, Math.max(20, h - 11), DailyEarnings.allDays(), c, mx, my);
            }
            case "STATS" -> drawOverviewStats(g, x, y, DailyEarnings.allDays());
            case "BUTTONS" -> {
                // the real buttons are widgets; the editor just shows what they look like
                if (editing) {
                    for (int i = 0; i < buttonCount(c); i++) {
                        int[] f = buttonFace(r, i, c);
                        editFace(g, f[0], f[1], f[2], f[3], actLabel(buttonAction(c, i)));
                    }
                }
            }
            default -> { }
        }
    }

    private void editFace(GuiGraphicsExtractor g, int x, int y, int w, int h, String label) {
        g.fill(x, y, x + w, y + h, Theme.btn);
        Gfx.outline(g, x, y, w, h, Theme.btnEdge);
        g.text(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, Theme.text, false);
    }

    /** Paints the neon look over the invisible vanilla buttons. The sidebar's buttons (the first sideCount) never slide. */
    private void paintNeonFaces(GuiGraphicsExtractor g, int mouseX, int mouseY, int slideDy) {
        for (int ni = 0; ni < neon.size(); ni++) {
            NeonBtn b = neon.get(ni);
            int dyb = ni < sideCount ? 0 : slideDy;
            boolean hov = mouseX >= b.x && mouseX < b.x + b.w && mouseY >= b.y + dyb && mouseY < b.y + dyb + b.h;
            boolean act = b.active.getAsBoolean();
            int by0 = b.y + dyb;
            int bg = act ? Theme.btnAct : Theme.btn;
            if (hov) bg = Theme.btnHov;
            g.fill(b.x, by0, b.x + b.w, by0 + b.h, bg);
            Gfx.outline(g, b.x, by0, b.w, b.h, hov ? Theme.neonHi : (act ? Theme.neon : Theme.btnEdge));
            if (act) g.fill(b.x, by0, b.x + 2, by0 + b.h, Theme.neonHi);
            String s = b.label.get();
            int tw = this.font.width(s);
            g.text(this.font, s, b.x + (b.w - tw) / 2, by0 + (b.h - 8) / 2, hov || act ? 0xFFFFFFFF : Theme.text, false);
        }
    }

    // ---------------- Menu customisation editors (Main menu items / Sidebar tabs) ----------------

    private static int withAlpha(int argb, int a) {
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static boolean inside(int[] r, int mx, int my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private void buildEditWidgets() {
        ModConfig c = DonutQueueClient.config;
        if (editMode == EDIT_TABS) {
            // one invisible click zone per sidebar row; the look is painted in drawEditSidebar
            for (int i = 0; i < PAGES.length; i++) {
                final int idx = i;
                Button z = Button.builder(Component.literal(""), b -> toggleTab(idx))
                        .bounds(px + 6, py + headerH + 6 + i * 23, sideW - 10, 20).build();
                z.active = i > 0 && i < PAGES.length - 1; // Main Menu + Customisation always stay
                this.addRenderableWidget(z);
            }
            addNeon(cx + cw - 70, py + ph - 28, 70, 20, () -> "DONE", () -> false, this::onClose);
            return;
        }

        // EDIT_MAIN
        if (pickerFor != null) {
            buildPicker(c);
            return;
        }

        // left side: how many bottom buttons the main menu has
        int ly = py + headerH + 22;
        addNeon(px + 6, ly, sideW - 10, 20, () -> "2 BUTTONS", () -> !c.mainFourButtons, () -> {
            c.mainFourButtons = false;
            c.save();
            pendingRebuild = true; // the Buttons card changes height
        });
        addNeon(px + 6, ly + 24, sideW - 10, 20, () -> "4 BUTTONS", () -> c.mainFourButtons, () -> {
            c.mainFourButtons = true;
            c.save();
            pendingRebuild = true;
        });

        // one click zone per card (hidden ones too, shown as ghosts). The Graph opens its popup, each bottom
        // button opens its own popup; every other card is grabbed / dropped. While a card is held, any click drops it.
        for (Map.Entry<String, int[]> e : mainLayout(c, true).entrySet()) {
            final String id = e.getKey();
            int[] r = e.getValue();
            boolean shown = mainShown(c, id);
            if (id.equals("BUTTONS") && shown) {
                for (int i = 0; i < buttonCount(c); i++) {
                    final int slot = i;
                    int[] f = buttonFace(r, i, c);
                    this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
                        if (carrying != null) onCardClick(id);
                        else openPicker("SLOT" + slot);
                    }).bounds(f[0], f[1], f[2], f[3]).build());
                }
            } else if (id.equals("GRAPH") && shown) {
                this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
                    if (carrying != null) onCardClick(id);
                    else openPicker("GRAPH");
                }).bounds(r[0], r[1], r[2], r[3]).build());
            } else {
                this.addRenderableWidget(Button.builder(Component.literal(""), b -> onCardClick(id))
                        .bounds(r[0], r[1], r[2], r[3]).build());
            }
        }
        // right rail: show / hide switches + Reset + Done
        int y = py + 72;
        for (int i = 0; i < MAIN_IDS.length; i++) {
            final String id = MAIN_IDS[i];
            final String name = MAIN_NAMES[i];
            addNeon(rx, y, railW, 16,
                    () -> name + ": " + (mainShown(c, id) ? "ON" : "OFF"),
                    () -> mainShown(c, id),
                    () -> {
                        setMainShown(c, id, !mainShown(c, id));
                        c.save();
                        pendingRebuild = true;
                    });
            y += 18;
        }
        y += 2;
        addNeon(rx, y, railW, 18, () -> "Reset layout", () -> false, () -> {
            c.mainOrder = new ArrayList<>(List.of(MAIN_IDS));
            for (String id : MAIN_IDS) setMainShown(c, id, true);
            c.mainFourButtons = false;
            c.mainButtons = new ArrayList<>(List.of(DEFAULT_BUTTONS));
            c.mainGraphRange = 0;
            c.mainGraphGains = false;
            c.save();
            carrying = null;
            pendingRebuild = true;
        });
        y += 20;
        addNeon(rx, y, railW, 18, () -> "DONE", () -> false, this::onClose);
    }

    private void openPicker(String what) {
        pickerFor = what;
        carrying = null;
        pendingRebuild = true;
    }

    /** The popup over the content area: pick what a bottom button does, or what the graph shows. */
    private void buildPicker(ModConfig c) {
        int x = cx;
        int y = cy + 14;
        int bw = (cw - 4) / 2;
        int bw2 = cw - bw - 4;
        int bottom;
        if (pickerFor.equals("GRAPH")) {
            int y0 = y + 12;
            addNeon(x, y0, bw, 20, () -> "Today (10 min)", () -> c.mainGraphRange == 0, () -> { c.mainGraphRange = 0; c.save(); });
            addNeon(x + bw + 4, y0, bw2, 20, () -> "All time (days)", () -> c.mainGraphRange == 1, () -> { c.mainGraphRange = 1; c.save(); });
            int y1 = y + 46;
            addNeon(x, y1, bw, 20, () -> "Balance", () -> !c.mainGraphGains, () -> { c.mainGraphGains = false; c.save(); });
            addNeon(x + bw + 4, y1, bw2, 20, () -> "Gains (profit)", () -> c.mainGraphGains, () -> { c.mainGraphGains = true; c.save(); });
            bottom = y + 74;
        } else {
            final int slot = pickerFor.charAt(4) - '0';
            for (int i = 0; i < ACT_IDS.length; i++) {
                final String id = ACT_IDS[i];
                final String name = ACT_NAMES[i];
                int bx = x + (i % 2) * (bw + 4);
                int by = y + 12 + (i / 2) * 24;
                addNeon(bx, by, i % 2 == 0 ? bw : bw2, 20, () -> name, () -> buttonAction(c, slot).equals(id), () -> {
                    setButtonAction(c, slot, id);
                    c.save();
                    pickerFor = null;
                    pendingRebuild = true;
                });
            }
            bottom = y + 12 + 4 * 24 + 4;
        }
        final String card = pickerFor.equals("GRAPH") ? "GRAPH" : "BUTTONS";
        addNeon(x, bottom, bw, 20, () -> "Move this card", () -> false, () -> {
            pickerFor = null;
            carrying = card;
            pendingRebuild = true;
        });
        addNeon(x + bw + 4, bottom, bw2, 20, () -> "Done", () -> false, () -> {
            pickerFor = null;
            pendingRebuild = true;
        });
    }

    private void toggleTab(int idx) {
        if (idx <= 0 || idx >= PAGES.length - 1) return;
        ModConfig c = DonutQueueClient.config;
        if (c.menuHiddenTabs == null) c.menuHiddenTabs = new ArrayList<>();
        String name = PAGES[idx];
        if (!c.menuHiddenTabs.removeIf(t -> t.equals(name))) c.menuHiddenTabs.add(name);
        c.save();
    }

    /** Click a card to pick it up, click another card to drop it into that spot (clicking the same card puts it back). */
    private void onCardClick(String id) {
        if (carrying == null) {
            carrying = id;
            return;
        }
        if (!carrying.equals(id)) {
            ModConfig c = DonutQueueClient.config;
            List<String> o = mainOrder(c);
            int to = o.indexOf(id);
            o.remove(carrying);
            o.add(Math.max(0, Math.min(o.size(), to)), carrying);
            c.mainOrder = o;
            c.save();
            pendingRebuild = true; // the click zones follow the new layout
        }
        carrying = null;
    }

    /** The sidebar as the editor shows it: hidden tabs stay visible but faded (and are gone from the real menu). */
    private void drawEditSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean interactive) {
        for (int i = 0; i < PAGES.length; i++) {
            int x = px + 6;
            int y = py + headerH + 6 + i * 23;
            int w = sideW - 10;
            int h = 20;
            boolean hideable = i > 0 && i < PAGES.length - 1;
            boolean hidden = isTabHidden(i);
            boolean hov = interactive && hideable && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            int a = hidden ? 0x66 : 0xFF;
            // solid base first so the invisible button underneath doesn't show through the faded face
            g.fill(x, y, x + w, y + h, 0xFF000000 | (Theme.sidebar & 0xFFFFFF));
            g.fill(x, y, x + w, y + h, withAlpha(hov ? Theme.btnHov : Theme.btn, a));
            Gfx.outline(g, x, y, w, h, withAlpha(hov ? Theme.neonHi : Theme.btnEdge, a));
            String s = PAGES[i];
            g.text(this.font, s, x + (w - this.font.width(s)) / 2, y + (h - 8) / 2, withAlpha(Theme.text, hidden ? 0x77 : 0xFF), false);
            if (hidden) g.fill(x + 4, y + h / 2, x + w - 4, y + h / 2 + 1, withAlpha(Theme.neonHi, 0x88));
        }
    }

    private void renderEdit(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, ModConfig c) {
        boolean main = editMode == EDIT_MAIN;

        // frame, header, sidebar background
        g.fill(0, 0, this.width, this.height, Theme.dim);
        if (Theme.glow) {
            Gfx.outline(g, px - 2, py - 2, pw + 4, ph + 4, Theme.neonA(0x22));
            Gfx.outline(g, px - 1, py - 1, pw + 2, ph + 2, Theme.neonA(0x55));
        }
        g.fill(px, py, px + pw, py + ph, Theme.panel);
        g.fill(px + 1, py + 1, rx - 4, py + headerH, Theme.header);
        g.fill(px + 1, py + headerH, rx - 4, py + headerH + 1, Theme.neon);
        DonutLogo.draw(g, px + 6, py + 4, 1);
        g.text(this.font, "DonutSMP Streamer Utilitys", px + 38, py + 13, Theme.neonHi, true);
        g.fill(px + 1, py + headerH + 1, px + sideW, py + ph - 1, Theme.sidebar);
        g.fill(px + sideW, py + headerH + 1, px + sideW + 1, py + ph - 1, Theme.edge);

        // widgets (click zones + rail buttons). Their default look is painted over below.
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        int faded = withAlpha(Theme.panel, 0xB8);

        if (!main) {
            // Sidebar tabs: the whole menu is shown very faded; only the sidebar is live
            g.fill(cx, cy + 10, cx + cw, cy + 11, Theme.edge);
            drawMainPage(g, -9999, -9999, c);
            drawRail(g, -9999, -9999, c);
            g.fill(px + 1, py + 1, px + pw - 1, py + headerH + 1, faded);
            g.fill(px + sideW + 1, py + headerH + 1, px + pw - 1, py + ph - 1, faded);
            drawEditSidebar(g, mouseX, mouseY, true);
            g.text(this.font, "EDIT SIDEBAR TABS", cx, cy, Theme.neonHi, true);
            g.text(this.font, "Click a tab on the left to hide it.", cx, cy + 18, Theme.text, true);
            g.text(this.font, "Click it again to bring it back.", cx, cy + 30, Theme.text, true);
            g.text(this.font, "Faded tabs are gone from the real menu.", cx, cy + 46, Theme.muted, false);
            g.text(this.font, "Main Menu and Customisation always stay.", cx, cy + 58, Theme.muted, false);
            paintNeonFaces(g, mouseX, mouseY, 0);
            return;
        }

        // Main menu items: header + sidebar faded, the page's cards are live
        g.fill(px + 1, py + 1, rx - 4, py + headerH + 1, faded);
        if (pickerFor == null) {
            int ty = py + headerH + 8;
            g.text(this.font, "BOTTOM BUTTONS", px + 8, ty, Theme.neonHi, false);
            int hy = py + headerH + 74;
            String[] tips = {"Choose how many", "buttons the main", "menu has. Click", "a button or the", "graph to change", "what it does.", "", "ESC = put back"};
            for (int i = 0; i < tips.length; i++) {
                g.text(this.font, tips[i], px + 8, hy + i * 10, Theme.muted, false);
            }
        }

        g.text(this.font, "EDIT MAIN MENU", cx, cy, Theme.neonHi, true);
        g.fill(cx, cy + 10, cx + cw, cy + 11, Theme.edge);

        Map<String, int[]> lay = mainLayout(c, true);
        String hover = null;
        for (Map.Entry<String, int[]> e : lay.entrySet()) {
            if (inside(e.getValue(), mouseX, mouseY)) hover = e.getKey();
        }
        for (Map.Entry<String, int[]> e : lay.entrySet()) {
            String id = e.getKey();
            int[] r = e.getValue();
            boolean shown = mainShown(c, id);
            // solid base: hides the invisible button underneath
            g.fill(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1] + r[3] + 1, 0xFF000000 | (Theme.panel & 0xFFFFFF));
            if (shown) {
                drawBlock(g, id, r, mouseX, mouseY, c, true);
            } else {
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], Theme.neonA(0x14));
                String t = MAIN_NAMES[mainIndex(id)] + " (hidden)";
                g.text(this.font, t, r[0] + (r[2] - this.font.width(t)) / 2, r[1] + (r[3] - 8) / 2, withAlpha(Theme.muted, 0x99), false);
            }
            int edge = Theme.btnEdge;
            if (id.equals(carrying)) {
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], withAlpha(Theme.panel, 0xA0)); // the spot it was lifted from
                edge = C_WARN;
            } else if (id.equals(hover)) {
                edge = carrying != null ? C_GOOD : Theme.neonHi;
            }
            Gfx.outline(g, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, edge);

            // where it will land: a bar on the edge it will end up on
            if (carrying != null && id.equals(hover) && !id.equals(carrying)) {
                List<String> o = mainOrder(c);
                boolean below = o.indexOf(carrying) < o.indexOf(id);
                int ly = below ? r[1] + r[3] + 1 : r[1] - 3;
                g.fill(r[0], ly, r[0] + r[2], ly + 2, C_GOOD);
            }
        }

        // hovering the graph / a bottom button: it can be clicked to change what it does
        if (carrying == null && pickerFor == null && hover != null) {
            int[] hr = lay.get(hover);
            if (hover.equals("BUTTONS") && mainShown(c, "BUTTONS")) {
                for (int i = 0; i < buttonCount(c); i++) {
                    int[] f = buttonFace(hr, i, c);
                    if (mouseX >= f[0] && mouseX < f[0] + f[2] && mouseY >= f[1] && mouseY < f[1] + f[3]) {
                        Gfx.outline(g, f[0], f[1], f[2], f[3], Theme.neonHi);
                        g.fill(f[0], f[1], f[0] + f[2], f[1] + f[3], Theme.neonA(0x22));
                    }
                }
                g.text(this.font, "Click a button to change what it does", cx, py + ph - 14, Theme.muted, false);
            } else if (hover.equals("GRAPH") && mainShown(c, "GRAPH")) {
                g.text(this.font, "Click the graph to change what it shows", cx, py + ph - 14, Theme.muted, false);
            }
        }

        // right rail
        g.text(this.font, "EDIT MAIN MENU", rx, py + 6, Theme.neonHi, true);
        if (pickerFor == null) {
            g.text(this.font, "Click card = grab", rx, py + 17, Theme.muted, false);
            g.text(this.font, "Click spot = drop", rx, py + 27, Theme.muted, false);
            g.text(this.font, "Graph & buttons:", rx, py + 37, Theme.muted, false);
            g.text(this.font, "click to change", rx, py + 47, Theme.muted, false);
            g.text(this.font, "SHOW / HIDE", rx, py + 60, Theme.neonHi, false);
        }

        // popup: what does this button / the graph do
        if (pickerFor != null) {
            int x0 = cx - 2;
            int y0 = cy + 12;
            g.fill(x0, y0, cx + cw + 2, py + ph - 2, 0xFF000000 | (Theme.panel & 0xFFFFFF));
            Gfx.outline(g, x0, y0, cw + 4, py + ph - 2 - y0, Theme.neon);
            int ty = cy + 14;
            if (pickerFor.equals("GRAPH")) {
                g.text(this.font, "GRAPH - RANGE", cx + 2, ty + 2, Theme.neonHi, false);
                g.text(this.font, "GRAPH - SHOWS", cx + 2, ty + 36, Theme.neonHi, false);
            } else {
                int slot = pickerFor.charAt(4) - '0' + 1;
                g.text(this.font, "BUTTON " + slot + " - WHAT SHOULD IT DO?", cx + 2, ty + 2, Theme.neonHi, false);
            }
        }
        paintNeonFaces(g, mouseX, mouseY, 0);

        if (carrying != null) {
            g.text(this.font, "Click where " + MAIN_NAMES[mainIndex(carrying)] + " should go.", cx, py + ph - 14, C_WARN, true);
            int[] r = lay.get(carrying);
            if (r != null) {
                int gw = Math.min(r[2], 140);
                int gh = Math.min(r[3], 36);
                int gx = mouseX - gw / 2;
                int gy = mouseY - gh / 2;
                g.fill(gx, gy, gx + gw, gy + gh, withAlpha(Theme.neon, 0x70));
                Gfx.outline(g, gx, gy, gw, gh, Theme.neonHi);
                String t = MAIN_NAMES[mainIndex(carrying)];
                g.text(this.font, t, gx + (gw - this.font.width(t)) / 2, gy + (gh - 8) / 2, 0xFFFFFFFF, true);
            }
        }
    }

    private void statCell(GuiGraphicsExtractor g, int x, int y, String label, String value, int color) {
        g.text(this.font, label, x, y, Theme.muted, false);
        g.text(this.font, value, x, y + 10, color, false);
    }

    private void tile(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, Theme.tile);
        Gfx.outline(g, x, y, w, h, Theme.edge);
    }

    // candle data is worked out once per change of history (not every frame - it can be years of days)
    private static int candleRev = -1;
    private static int candleMax = -1;
    private static double[] cO, cH, cL, cC;
    private static boolean[] cHas, cFlat;
    private static String[] cTitles;
    private static double cFirstOpen = Double.NaN;
    private static String cFirstDate = "", cMidDate = "";

    /** The all-time graph: candles of your balance (open -> close, wicks = intraday high/low). Many days share one candle once there are too many to fit. */
    private void drawGraph(GuiGraphicsExtractor g, int gx, int gy, int gw, int gh,
                           DailyEarnings.Day[] days, ModConfig c, int mouseX, int mouseY) {
        // today's graph (10-minute steps) replaces the old all-time candles
        String today = LocalDate.now().toString();
        double openBal = Double.NaN;
        for (DailyEarnings.Day d : days) {
            if (d.date.equals(today)) {
                openBal = Double.isNaN(d.balance) ? Double.NaN : d.balance - d.net;
                break;
            }
        }
        if (c.mainGraphRange == 1) {
            drawOverviewLine(g, gx, gy, gw, gh, days, c.mainGraphGains, mouseX, mouseY);
            return;
        }
        drawDayGraph(g, gx, gy, gw, gh, today, openBal, c.mainGraphGains, mouseX, mouseY);
    }

    private static void buildCandles(DailyEarnings.Day[] days, int maxC) {
        int nd = days.length;
        double[] o = new double[nd], h = new double[nd], l = new double[nd], cl = new double[nd];
        boolean[] has = new boolean[nd], flat = new boolean[nd];
        double firstOpen = Double.NaN;
        for (int i = 0; i < nd; i++) {
            double close = days[i].balance;
            if (Double.isNaN(close)) continue;
            double[][] pts = DailyEarnings.dayPoints(days[i].date);
            double open = close - days[i].net;
            double hi = Math.max(open, close);
            double lo = Math.min(open, close);
            for (double[] p : pts) {
                double b = open + p[1];
                hi = Math.max(hi, b);
                lo = Math.min(lo, b);
            }
            has[i] = true;
            flat[i] = days[i].net == 0 && pts.length == 0;
            o[i] = open;
            cl[i] = close;
            h[i] = hi;
            l[i] = lo;
            if (Double.isNaN(firstOpen)) firstOpen = open;
        }
        cFirstOpen = firstOpen;

        // too many days for one candle each: group neighbouring days into one candle
        int per = Math.max(1, (nd + maxC - 1) / maxC);
        int n = (nd + per - 1) / per;
        cO = new double[n]; cH = new double[n]; cL = new double[n]; cC = new double[n];
        cHas = new boolean[n]; cFlat = new boolean[n];
        cTitles = new String[n];
        for (int b = 0; b < n; b++) {
            int from = b * per, to = Math.min(nd, from + per);
            boolean any = false, allFlat = true;
            for (int i = from; i < to; i++) {
                if (!has[i]) continue;
                if (!any) { cO[b] = o[i]; cH[b] = h[i]; cL[b] = l[i]; any = true; }
                cH[b] = Math.max(cH[b], h[i]);
                cL[b] = Math.min(cL[b], l[i]);
                cC[b] = cl[i];
                if (!flat[i]) allFlat = false;
            }
            cHas[b] = any;
            cFlat[b] = any && allFlat;
            cTitles[b] = per == 1 ? days[from].date : days[from].date + " > " + days[to - 1].date;
        }
        cFirstDate = nd > 0 ? days[0].date.substring(2) : "";
        cMidDate = nd > 0 ? days[nd / 2].date.substring(2) : "";
    }

    private void drawCandlePlot(GuiGraphicsExtractor g, int gx, int gy, int gw, int gh,
                                double[] o, double[] h, double[] l, double[] cl, boolean[] has, boolean[] flat,
                                double baseline, boolean signed,
                                String leftLabel, String midLabel, String rightLabel,
                                String[] titles, int mouseX, int mouseY, String emptyText) {
        final int labelW = 46;
        final int plotX = gx + 2;
        final int plotW = gw - labelW - 4;
        final int plotY = gy + 3;
        final int plotH = gh - 13;
        if (plotW < 30 || plotH < 14) return;
        final int n = o.length;
        final int UP = 0xFF26A69A;
        final int DOWN = 0xFFEF5350;

        g.fill(plotX, plotY, plotX + plotW, plotY + plotH, Theme.plot);
        Gfx.outline(g, plotX, plotY, plotW, plotH, Theme.edge);

        double lo = baseline;
        double hi = baseline;
        int last = -1;
        for (int i = 0; i < n; i++) {
            if (!has[i]) continue;
            last = i;
            lo = Math.min(lo, l[i]);
            hi = Math.max(hi, h[i]);
        }
        if (last < 0) {
            g.text(this.font, emptyText, plotX + 6, plotY + plotH / 2 - 4, Theme.muted, false);
            return;
        }
        double span = hi - lo;
        if (span < 1) span = Math.max(1, Math.abs(hi) * 0.02);
        final double fLo = lo - span * 0.10;
        final double fHi = lo + span * 1.10;
        java.util.function.DoubleToIntFunction yOf = v ->
                plotY + plotH - 2 - (int) Math.round((v - fLo) / (fHi - fLo) * (plotH - 4));

        // faint grid + price axis on the right
        for (int i = 0; i <= 4; i++) {
            double v = fLo + (fHi - fLo) * i / 4.0;
            int yy = yOf.applyAsInt(v);
            g.fill(plotX + 1, yy, plotX + plotW - 1, yy + 1, Theme.neonA(0x12));
            g.text(this.font, axisMoney(v, signed), plotX + plotW + 4, yy - 4, Theme.muted, false);
        }

        // baseline (like the horizontal line in the reference chart)
        int by = yOf.applyAsInt(baseline);
        g.fill(plotX + 1, by, plotX + plotW - 1, by + 1, 0xAA9A8B5F);

        // candles
        double cell = plotW / (double) n;
        int bodyW = Math.max(1, (int) cell - 2);
        if (bodyW % 2 == 0) bodyW--;
        bodyW = Math.max(1, bodyW);
        int half = bodyW / 2;
        for (int i = 0; i < n; i++) {
            if (!has[i]) continue;
            int cxp = plotX + (int) Math.round((i + 0.5) * cell);
            int yC = yOf.applyAsInt(cl[i]);
            if (flat[i]) {
                g.fill(cxp - half, yC, cxp + half + 1, yC + 1, Theme.grid);
                continue;
            }
            int col = cl[i] >= o[i] ? UP : DOWN;
            int yO = yOf.applyAsInt(o[i]);
            int yH = yOf.applyAsInt(h[i]);
            int yL = yOf.applyAsInt(l[i]);
            g.fill(cxp, yH, cxp + 1, yL + 1, col);
            int top = Math.min(yO, yC);
            int bot = Math.max(yO, yC);
            g.fill(cxp - half, top, cxp + half + 1, bot + 1, col);
        }

        // last-price line + tags on the axis
        int lastCol = cl[last] >= o[last] ? UP : DOWN;
        int ly = yOf.applyAsInt(cl[last]);
        g.fill(plotX + 1, ly, plotX + plotW - 1, ly + 1, (0x66 << 24) | (lastCol & 0xFFFFFF));
        if (Math.abs(by - ly) >= 11) {
            tag(g, plotX + plotW, labelW, by, 0xFF5A5240, axisMoney(baseline, signed));
        }
        tag(g, plotX + plotW, labelW, ly, lastCol, axisMoney(cl[last], signed));

        // x labels
        g.text(this.font, leftLabel, plotX, plotY + plotH + 3, Theme.muted, false);
        g.text(this.font, midLabel, plotX + (plotW - this.font.width(midLabel)) / 2, plotY + plotH + 3, Theme.muted, false);
        g.text(this.font, rightLabel, plotX + plotW - this.font.width(rightLabel), plotY + plotH + 3, Theme.muted, false);

        // crosshair + O/H/L/C read-out
        if (mouseX >= plotX && mouseX < plotX + plotW && mouseY >= plotY && mouseY < plotY + plotH) {
            int idx = Math.max(0, Math.min(n - 1, (int) ((mouseX - plotX) / cell)));
            int cxp = plotX + (int) Math.round((idx + 0.5) * cell);
            g.fill(cxp, plotY + 1, cxp + 1, plotY + plotH - 1, Theme.neonHiA(0x55));
            g.fill(plotX + 1, mouseY, plotX + plotW - 1, mouseY + 1, Theme.neonHiA(0x55));
            double mv = fLo + (plotY + plotH - 2 - mouseY) / (double) (plotH - 4) * (fHi - fLo);
            tag(g, plotX + plotW, labelW, mouseY, Theme.neon, axisMoney(mv, signed));

            if (has[idx]) {
                int col = cl[idx] >= o[idx] ? UP : DOWN;
                String ohlc = "O" + axisMoney(o[idx], signed) + " H" + axisMoney(h[idx], signed)
                        + " L" + axisMoney(l[idx], signed) + " C" + axisMoney(cl[idx], signed);
                if (plotH >= 30) {
                    g.text(this.font, titles[idx], plotX + 4, plotY + 3, Theme.text, true);
                    g.text(this.font, ohlc, plotX + 4, plotY + 13, col, true);
                } else {
                    g.text(this.font, titles[idx] + " C" + axisMoney(cl[idx], signed), plotX + 4, plotY + 3, col, true);
                }
            } else {
                g.text(this.font, titles[idx] + "  no data", plotX + 4, plotY + 3, Theme.muted, true);
            }
        }
    }

    // ---- other pages' info text --------------------------------------

    private void drawStartBalInfo(GuiGraphicsExtractor g, ModConfig c) {
        int y = cy + 16 + 24 * 2 + 10 + 26 + 24 * 2 + 4;
        String st = c.awaitingBalCapture ? "WAITING - run /bal in chat now"
                : (c.trackingEnabled ? "TRACKING ACTIVE" : "IDLE - press START TRACKING");
        g.text(this.font, st, cx, y, c.trackingEnabled ? C_GOOD : (c.awaitingBalCapture ? C_WARN : Theme.muted), false);
        if (c.hasStartingBalance) {
            g.text(this.font, money(c.earnedTotal) + "   (from $" + DonutQueueClient.formatCompact(c.startingBalance) + ")",
                    cx, y + 11, Theme.text, false);
        }
        if (c.awaitingOrderBalCapture) {
            g.text(this.font, "Order detected - run /bal to take its cost off", cx, y + 22, C_WARN, false);
        }
    }

    private void drawBiddingInfo(GuiGraphicsExtractor g, ModConfig c) {
        int y = cy + 16 + 24 + 10 + 26 + 10 + 26 + 24 * 2 + 4;
        String st;
        int col;
        if (c.biddingActive) {
            long left = Math.max(0, c.biddingEndTimeMillis - System.currentTimeMillis()) / 1000L;
            st = "ACTIVE - " + left + "s left";
            col = C_GOOD;
        } else if (c.biddingFinished) {
            st = "FINISHED";
            col = C_WARN;
        } else {
            st = "IDLE";
            col = Theme.muted;
        }
        g.text(this.font, st, cx, y, col, false);
        if (c.biddingLeaderName != null) {
            g.text(this.font, "Leader: " + c.biddingLeaderName + " ($" + DonutQueueClient.formatCompact(c.biddingLeaderAmount) + ")",
                    cx, y + 11, Theme.text, false);
        }
    }

    private void drawInfoInfo(GuiGraphicsExtractor g, ModConfig c) {
        int y = cy + 16 + 24 * 2 + 6;
        g.text(this.font, "IGN:  " + dash(c.infoIgn), cx, y, Theme.text, false);
        g.text(this.font, "Min:  " + dash(c.infoMin), cx, y + 11, Theme.text, false);
        g.text(this.font, "Max:  " + dash(c.infoMax), cx, y + 22, Theme.text, false);
        g.text(this.font, "Game: " + dash(c.infoGame), cx, y + 33, Theme.text, false);
        g.text(this.font, c.infoCustomText == null || c.infoCustomText.isEmpty() ? "Any additional info" : c.infoCustomText,
                cx, y + 44, Theme.muted, false);
    }

    private static String dash(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }

    // ---- right rail: Discord button on top, then the spinning skin (Main Menu) or your balance (other pages) ----

    private void drawRail(GuiGraphicsExtractor g, int mouseX, int mouseY, ModConfig c) {
        int x = rx;
        int w = railW;
        int y = py + 6 + 20;   // the Discord button sits above, at py + 6
        boolean mainMenu = page == 0;
        // Other pages keep the old 70px rail height so the lists under it stay put; the Main Menu has the room to go big.
        int h = mainMenu ? Math.max(50, ph - 58) : 50;

        g.fill(x, y, x + w, y + h, Theme.tile);
        Gfx.outline(g, x, y, w, h, Theme.neon);
        Gfx.outline(g, x - 1, y - 1, w + 2, h + 2, Theme.neonA(0x44));

        Minecraft mc = Minecraft.getInstance();
        String name = mc.player != null ? mc.player.getName().getString() : null;
        if (mainMenu) {
            drawSpinningSkin(g, name, x + 1, y + 1, x + w - 1, y + h - 1);
            return;
        }
        // skin head in the middle at the top, the EXACT balance under it
        int head = 20;
        DonutQueueClient.drawHead(g, name, x + (w - head) / 2, y + 5, head);
        double bal = c.hasStartingBalance ? c.startingBalance + c.earnedTotal : Double.NaN;
        boolean negative = !Double.isNaN(bal) && bal < 0;
        String amount = Double.isNaN(bal) ? "--" : negative ? "Please do /bal" : "$" + Guards.money(bal);
        int tw = this.font.width(amount);
        float sc = tw > w - 6 ? (float) (w - 6) / tw : 1.0f;
        g.pose().pushMatrix();
        g.pose().translate(x + w / 2.0f, (float) (y + 31));
        g.pose().scale(sc, sc);
        g.text(this.font, amount, -tw / 2, 0, negative ? C_WARN : Theme.text, true);
        g.pose().popMatrix();
    }

    /** Your skin as a smoothly turning 3D figure (one full turn every 18 seconds), as big as the card allows. */
    private void drawSpinningSkin(GuiGraphicsExtractor g, String name, int x0, int y0, int x1, int y1) {
        double turn = (System.nanoTime() % 18_000_000_000L) / 18_000_000_000.0;
        float angle = (float) (turn * Math.PI * 2.0);
        int availH = (y1 - y0) - 8;
        int availW = (x1 - x0) - 6;
        int height = Math.max(20, Math.min(availH, (int) (33.0 * availW / 17.0)));
        int top = y0 + 4 + (availH - height) / 2;
        DonutQueueClient.drawSpinningSkin(g, name, (x0 + x1) / 2, top, height, angle);
    }

    /**
     * Right rail on the Earnings page. Default: the 7 people you made the most money off on one day.
     * With the "Showing" button flipped: the 7 people you lost the most money to that day.
     */
    private void drawTopPlayers(GuiGraphicsExtractor g, int x, int sy, int w, int mouseX, int mouseY) {
        boolean lost = DonutQueueClient.config.earningsTopShowLost;
        String today = LocalDate.now().toString();
        String date = railDate();

        g.fill(x, sy - 1, x + w, sy + 16 + 7 * 12, Theme.panel); // clear whatever sits under the list
        String title;
        if (sessionView) title = lost ? "SESSION LOST" : "SESSION TOP 7";
        else if (lost) title = date.equals(today) ? "LOST MOST TODAY" : "LOST  " + date.substring(5);
        else title = date.equals(today) ? "TOP 7 TODAY" : "TOP 7  " + date.substring(5);
        g.text(this.font, title, x + 2, sy, lost ? C_BAD : Theme.neonHi, false);
        g.fill(x, sy + 10, x + w, sy + 11, Theme.edge);

        List<Map.Entry<String, Double>> top = railTop(lost);
        if (top.isEmpty()) {
            g.text(this.font, lost ? "No losses" : "No payments", x + 2, sy + 16, Theme.muted, false);
        }
        int[] rankColors = {0xFFFFD700, 0xFFC8D2E6, 0xFFCD8B4E};
        for (int i = 0; i < top.size(); i++) {
            Map.Entry<String, Double> e = top.get(i);
            int ry = sy + 16 + i * 12;
            if (mouseX >= x && mouseX < x + w && mouseY >= ry - 1 && mouseY < ry + 11) {
                g.fill(x, ry - 1, x + w, ry + 11, Theme.btnHov);
            }
            String amt = lost ? "-$" + DonutQueueClient.formatCompact(-e.getValue()) : "$" + DonutQueueClient.formatCompact(e.getValue());
            int amtW = this.font.width(amt);
            String rank = (i + 1) + ". ";
            String name = e.getKey();
            int maxName = w - 4 - amtW - 6 - this.font.width(rank);
            while (name.length() > 1 && this.font.width(name) > maxName) name = name.substring(0, name.length() - 1);
            int nameColor = lost ? Theme.text : (i < 3 ? rankColors[i] : Theme.text);
            g.text(this.font, rank + name, x + 2, ry, nameColor, false);
            g.text(this.font, amt, x + w - 2 - amtW, ry, lost ? C_BAD : C_GOOD, false);
        }
    }

    private void drawSpark(GuiGraphicsExtractor g, int x, int y, int w, int h, double[] pts) {
        g.fill(x, y, x + w, y + h, Theme.spark);
        if (pts.length == 0) {
            g.fill(x, y + h / 2, x + w, y + h / 2 + 1, Theme.neonA(0x44));
            return;
        }
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double v : pts) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        if (hi - lo < 1e-9) {
            // flat: centre the line
            lo -= 1;
            hi += 1;
        }
        int n = Math.max(2, pts.length);
        int prevX = x, prevY = y + h / 2;
        for (int i = 0; i < n; i++) {
            double v = pts.length == 1 ? pts[0] : pts[Math.min(i, pts.length - 1)];
            int xx = x + (int) Math.round(i * (w - 1) / (double) (n - 1));
            int yy = y + h - 2 - (int) Math.round((v - lo) / (hi - lo) * (h - 4));
            if (i > 0) {
                for (int sx = prevX; sx <= xx; sx++) {
                    double t = xx == prevX ? 1.0 : (sx - prevX) / (double) (xx - prevX);
                    int ly = (int) Math.round(prevY + (yy - prevY) * t);
                    g.fill(sx, ly, sx + 1, y + h - 1, Theme.neonA(0x22));
                    g.fill(sx, ly, sx + 1, ly + 1, Theme.neonHi);
                }
            }
            prevX = xx;
            prevY = yy;
        }
        g.fill(prevX - 1, prevY - 1, prevX + 2, prevY + 2, 0xFFFFFFFF);
        Gfx.outline(g, x, y, w, h, Theme.edge);
    }

    // ---- small helpers ---------------------------------------------------

    private static String money(double v) {
        return (v < 0 ? "-$" : "$") + DonutQueueClient.formatCompact(Math.abs(v));
    }

    /** Value-axis / price-tag label: signed (+$1.2M / -$300K) for net graphs, plain ($1.2M) for balance graphs. */
    private static String axisMoney(double v, boolean signed) {
        return signed ? signedMoney(v) : money(v);
    }

    /** Hover-tooltip label; same format as the axis. */
    private static String tipMoney(double v, boolean signed) {
        return axisMoney(v, signed);
    }

    private static String signedMoney(double v) {
        if (v > 0) return "+$" + DonutQueueClient.formatCompact(v);
        if (v < 0) return "-$" + DonutQueueClient.formatCompact(-v);
        return "$0";
    }

    /** Coloured price tag on the right axis: a box of width w starting at x, vertically centred on cy. */
    private void tag(GuiGraphicsExtractor g, int x, int w, int cy, int color, String text) {
        int y0 = cy - 5;
        g.fill(x, y0, x + w, y0 + 11, color);
        int tw = this.font.width(text);
        g.text(this.font, text, x + Math.max(2, (w - tw) / 2), y0 + 2, 0xFFFFFFFF, true);
    }

    private void drawScaled(GuiGraphicsExtractor g, String text, int x, int y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);
        g.text(this.font, text, 0, 0, color, true);
        g.pose().popMatrix();
    }

    @Override
    public void onClose() {
        if (editMode == EDIT_MAIN && pickerFor != null) {
            pickerFor = null; // ESC closes the popup first
            pendingRebuild = true;
            return;
        }
        if (editMode == EDIT_MAIN && carrying != null) {
            carrying = null; // ESC while holding a card just puts it back
            return;
        }
        DonutQueueClient.config.save();
        this.minecraft.gui.setScreen(parent);
    }
}
