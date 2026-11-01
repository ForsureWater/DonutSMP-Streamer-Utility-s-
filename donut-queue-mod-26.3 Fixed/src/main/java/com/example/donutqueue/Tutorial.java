package com.example.donutqueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Step-by-step interactive tutorials, one per system. A step either just explains something ("Next") or waits
 * for the player to really do it: the mod fires small events ("SKIP", "QUEUE_ADD", "BTN:Move Box", ...) and the
 * step completes when its trigger event shows up. Anything that needs real money/chat has a "simulate" button
 * (fake payments that only touch the queue / bidding, never earnings).
 */
final class Tutorial {
    private Tutorial() {}

    static final class Step {
        final String title;
        final String text;
        final String hud;       // short instruction shown on screen while the guide is closed
        final String trigger;   // event that completes the step (null = just press Next). "X*" = starts with X
        final int menuPage;     // >= 0: guide offers an OPEN MENU button on that page
        final String simId;     // non-null: guide offers a "simulate" button
        final String simLabel;

        Step(String title, String text, String hud, String trigger, int menuPage, String simId, String simLabel) {
            this.title = title;
            this.text = text;
            this.hud = hud;
            this.trigger = trigger;
            this.menuPage = menuPage;
            this.simId = simId;
            this.simLabel = simLabel;
        }

        Step sim(String id, String label) {
            return new Step(title, text, hud, trigger, menuPage, id, label);
        }
    }

    // --- step factories -----------------------------------------------------------------
    private static Step info(String title, String text) {
        return new Step(title, text, null, null, -1, null, null);
    }

    /** Do it in the game world / chat. */
    private static Step doIt(String title, String text, String hud, String trigger) {
        return new Step(title, text, hud, trigger, -1, null, null);
    }

    /** Do it in the neon menu (guide offers OPEN MENU on the given page). */
    private static Step menu(String title, String text, String hud, String trigger, int page) {
        return new Step(title, text, hud, trigger, page, null, null);
    }

    // --- state --------------------------------------------------------------------------
    private static boolean active = false;
    private static String name = "";
    private static List<Step> steps = new ArrayList<>();
    private static int index = 0;
    /** The guide is closed on purpose while the player tries something; don't pop it back up until they do it. */
    static boolean waitingWorld = false;
    /** Reopen the guide as soon as no other screen is open. */
    static boolean guidePending = false;
    static String lastResult = "";
    static boolean lastResultGood = true;

    private static boolean prevHasScreen = false;
    private static boolean snapInit = false;
    private static boolean pLever, pBid, pBidFinished, pTrack, pGoal, pSession, pMin, pBidMin;

    static boolean isActive() { return active; }
    static String name() { return name; }
    static int index() { return index; }
    static int count() { return steps.size(); }
    static Step current() { return active && index >= 0 && index < steps.size() ? steps.get(index) : null; }
    static boolean isLast() { return index >= steps.size() - 1; }

    static void start(String id) {
        steps = build(id);
        name = switch (id) {
            case "MAIN" -> "Main Menu";
            case "QUEUE" -> "Anti Gamble Skip";
            case "STARTBAL" -> "Starting Bal";
            case "BIDDING" -> "Bidding";
            case "AUCTION" -> "Auction Presets";
            case "INFO" -> "Info Display";
            case "EARNINGS" -> "Earnings";
            case "SESSIONS" -> "Sessions";
            default -> "Customisation";
        };
        index = 0;
        active = true;
        waitingWorld = false;
        guidePending = false;
        lastResult = "";
    }

    static void stop() {
        active = false;
        waitingWorld = false;
        guidePending = false;
        lastResult = "";
    }

    static void next() {
        if (!active) return;
        lastResult = "";
        if (index < steps.size() - 1) index++;
    }

    static void back() {
        if (!active) return;
        lastResult = "";
        if (index > 0) index--;
    }

    /** Something happened in the mod; completes the current step if it was waiting for exactly that. */
    static void event(String ev) {
        if (!active || ev == null) return;
        Step st = current();
        if (st == null || st.trigger == null) return;
        boolean hit = st.trigger.endsWith("*")
                ? ev.startsWith(st.trigger.substring(0, st.trigger.length() - 1))
                : ev.equals(st.trigger);
        if (!hit) return;
        next();
        waitingWorld = false;
        guidePending = true;
    }

    /** Called every client tick: spots state changes (so every way of doing something counts) and reopens the guide. */
    static void tick(Minecraft mc) {
        ModConfig c = DonutQueueClient.config;
        if (c == null) return;
        boolean lever = c.leverPaused;
        boolean bid = c.biddingActive;
        boolean bidFin = c.biddingFinished;
        boolean track = c.trackingEnabled || c.awaitingBalCapture;
        boolean goal = c.goalAmount > 0;
        boolean session = Sessions.isRunning();
        boolean min = c.minimumAmount > 0;
        boolean bidMin = c.biddingMinimum > 0;
        if (snapInit && active) {
            if (lever != pLever) event(lever ? "LEVER_PAUSED" : "LEVER_RESUMED");
            if (bid && !pBid) event("BID_STARTED");
            if (!bid && pBid) event(bidFin ? "BID_FINISHED" : "BID_STOPPED");
            if (track && !pTrack) event("TRACK_STARTED");
            if (!track && pTrack) event("TRACK_RESET");
            if (goal && !pGoal) event("GOAL_SET");
            if (!goal && pGoal) event("GOAL_CLEARED");
            if (session && !pSession) event("SESSION_STARTED");
            if (!session && pSession) event("SESSION_ENDED");
            if (min && !pMin) event("MIN_SET");
            if (bidMin && !pBidMin) event("BID_MIN_SET");
        }
        pLever = lever; pBid = bid; pBidFinished = bidFin; pTrack = track; pGoal = goal;
        pSession = session; pMin = min; pBidMin = bidMin;
        snapInit = true;

        if (!active) return;
        boolean has = mc.gui.screen() != null;
        if (mc.gui.screen() instanceof TutorialScreen) guidePending = false;
        if (prevHasScreen && !has && !waitingWorld) guidePending = true;
        prevHasScreen = has;
        if (guidePending && !has && mc.player != null) {
            guidePending = false;
            mc.gui.setScreen(new TutorialScreen());
        }
    }

    /** Small box at the top of the screen while the guide is closed. */
    static void renderOverlay(GuiGraphicsExtractor g, Minecraft mc) {
        Step st = current();
        if (st == null || mc == null || mc.gui.screen() instanceof TutorialScreen) return;
        int w = 280;
        int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
        int y = 3;
        String line = st.hud != null ? st.hud : "Close this screen to go on with the guide.";
        line = Tutorial.fill(line);
        List<String> lines = TutorialScreen.wrap(mc.font, line, w - 12);
        int h = 24 + Math.min(3, lines.size()) * 10;
        g.fill(x, y, x + w, y + h, 0xD0000000);
        Gfx.outline(g, x, y, w, h, Theme.neonHi);
        String head = "TUTORIAL: " + name + "  (" + (index + 1) + "/" + steps.size() + ")";
        g.text(mc.font, head, x + 6, y + 5, Theme.neonHi, true);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            g.text(mc.font, lines.get(i), x + 6, y + 17 + i * 10, 0xFFFFFFFF, true);
        }
        String foot = "Guide / exit: " + DonutQueueClient.keyLabel("MENU");
        g.text(mc.font, foot, x + w - 6 - mc.font.width(foot), y + h - 10, Theme.muted, false);
    }

    /** Replaces {SKIP} {LEVER} {MENU} {BID} {FLICKS} in a step text. */
    static String fill(String s) {
        if (s == null) return "";
        return s.replace("{SKIP}", DonutQueueClient.keyLabel("SKIP"))
                .replace("{LEVER}", DonutQueueClient.keyLabel("LEVER"))
                .replace("{MENU}", DonutQueueClient.keyLabel("MENU"))
                .replace("{BID}", DonutQueueClient.keyLabel("BID"))
                .replace("{FLICKS}", String.valueOf(Math.max(1, DonutQueueClient.config.flicksRequired)));
    }

    // =====================================================================================
    // The tutorials
    // =====================================================================================

    /** The first step of every tutorial: what to have ready before starting. */
    private static String requirements(String id) {
        return switch (id) {
            case "MAIN" -> "- Nothing special, just this menu.\n- Your menu key: {MENU}.";
            case "QUEUE" -> "- A LEVER or a NOTE BLOCK placed somewhere you can right-click (a lever needs {FLICKS} flicks, a note block 1 click).\n- Your skip key {SKIP} and lever pause key {LEVER} (change them in Controls).\n- Chat, to type /dq next and /dq clear.\n- No real money: payments are simulated with buttons.";
            case "STARTBAL" -> "- Chat, to type /bal once.\n- Nothing else. Sales and payments can't be faked, so the tutorial explains those instead.";
            case "BIDDING" -> "- Nothing special. Bids are simulated with buttons.\n- A few minutes of round time (you can stop the round whenever you like).";
            case "AUCTION" -> "- Your bidding key {BID} (change it in Controls).\n- Any item in mind for the auction (you pick it from a list, you do not need to own it).\n- Chat, to type /bidding <number>.";
            case "INFO" -> "- Nothing special, just this menu.";
            case "EARNINGS" -> "- Nothing special, just this menu.\n- Payments you made or got are listed in the log; with none yet the log and graphs are simply empty.";
            case "SESSIONS" -> "- Nothing special, just this menu.\n- Optional: Starting Bal tracking running, so the session can draw a balance graph (see the Starting Bal tutorial).";
            default -> "- Nothing special, just this menu.";
        };
    }

    private static List<Step> build(String id) {
        List<Step> l = new ArrayList<>();
        l.add(info("What you'll need", requirements(id)));
        switch (id) {
            case "MAIN" -> {
                l.add(info("Welcome", "This is the Main Menu. It shows your Starting Bal, today's earnings, a graph and four quick buttons. Everything else lives in the tabs on the left."));
                l.add(menu("Switch tabs", "Click the 'Anti Gamble Skip' tab on the left to switch pages. Every tab has its own Tutorial button at the bottom of the sidebar.",
                        "Open the menu and click the 'Anti Gamble Skip' tab.", "BTN:Anti Gamble Skip", 0));
                l.add(menu("Come back", "Now click 'Main Menu' to come back.", "Click the 'Main Menu' tab.", "BTN:Main Menu", 1));
                l.add(info("The four buttons", "The buttons at the bottom of the Main Menu start/end a session, open stats, start bidding and start tracking. You can change what each button does in Customisation > Menu customisation > Main menu items."));
                l.add(info("Hotkeys", "{MENU} opens this menu.\n{BID} opens the Bidding menu (presets).\n{SKIP} skips to the next player in the queue.\n{LEVER} pauses / resumes lever skipping.\nAll of them can be changed in Controls."));
            }
            case "QUEUE" -> {
                l.add(info("What this does", "When a player pays you, they go into a queue. You serve them one at a time and skip to the next with a lever, a note block, a key or a command. In this tutorial you try every part of it. Payments are SIMULATED, so nothing real is needed."));
                l.add(menu("The queue box", "The queue box on your screen shows who is first. Press the 'Queue HUD' button once to see it switch, then make sure it ends up ON.",
                        "Press the 'Queue HUD' button in the menu.", "BTN:Queue HUD*", 1));
                l.add(new Step("Payments join the queue", "Normally the mod reads your chat: when it sees 'Steve paid you $1m', Steve is added to the queue. Press the button to simulate a payment. Close the guide afterwards to see the queue box.",
                        "Press the simulate button in the guide.", "QUEUE_ADD", -1, "PAY", "Simulate: TutorialTom pays $1m"));
                l.add(new Step("A second payment", "Add another one so there is a line of two.", "Press the simulate button in the guide.",
                        "QUEUE_ADD", -1, "PAY", "Simulate another payment"));
                l.add(doIt("Skip with the key", "Skip to the next player with your skip key.", "Press {SKIP} to serve the first player.", "SKIP"));
                l.add(new Step("Skip with a lever or note block", "A lever needs {FLICKS} flicks (right-clicks) to serve a player, a note block needs just one click. Place a lever or note block (or use one you have) and use it. The flick count can be changed with /dq flicks <number>. The queue is empty? Use the simulate button first.",
                        "Right-click a lever {FLICKS} times, or a note block once.", "SKIP", -1, "PAY", "Simulate a payment (to fill the queue)"));
                l.add(new Step("Skip with a command", "The same thing works from chat. Type /dq next.", "Type /dq next in chat.", "SKIP", -1, "PAY", "Simulate a payment (to fill the queue)"));
                l.add(menu("Pause lever skipping", "This is made for when you play BLACKJACK (or anything else that uses levers or note blocks): while it is paused, they no longer skip payments, so nobody gets skipped by accident. Pause it with {LEVER} or the 'Lever Skip' button on the Anti Gamble page.",
                        "Press {LEVER} to PAUSE lever skipping (blackjack mode).", "LEVER_PAUSED", 1));
                l.add(menu("Resume it", "While it is paused, levers and note blocks skip nobody. Try one if you like, nothing happens. Remember to turn it back on after blackjack, or payments won't be skipped.",
                        "Press {LEVER} again to RESUME.", "LEVER_RESUMED", 1));
                l.add(menu("Minimum payment", "Payments below a minimum can be ignored. On the Anti Gamble page type 500k into MIN PAYMENT and press SET (also: /queue minimum).",
                        "Type 500k in MIN PAYMENT and press SET.", "MIN_SET", 1));
                l.add(new Step("A payment that is too small", "Now simulate a payment of $10k. It is below your minimum, so it is ignored and the player is NOT queued.",
                        "Press the simulate button in the guide.", "IGNORED_MIN", -1, "PAY_SMALL", "Simulate: TutorialTom pays $10k"));
                l.add(new Step("A big enough payment", "A payment of $1m is above your minimum, so it still joins the queue.",
                        "Press the simulate button in the guide.", "QUEUE_ADD", -1, "PAY", "Simulate: TutorialTom pays $1m"));
                l.add(new Step("Blacklist", "Players on the blacklist are ignored completely. Add names with /queue blacklist add <name> or in the Size & Blacklist screen. The button adds a pretend player to the blacklist, simulates a payment from them and removes them again.",
                        "Press the simulate button in the guide.", "IGNORED_BLACKLIST", -1, "PAY_BLACKLIST", "Simulate a blacklisted payer"));
                l.add(new Step("Whispers", "A payment that arrives as a whisper still counts for Earnings, Starting Bal and Bidding, but NEVER goes into the queue (people can fake whispers).",
                        "Press the simulate button in the guide.", "WHISPER", -1, "PAY_WHISPER", "Simulate a whispered payment"));
                l.add(menu("Clear the queue", "Wipe the whole queue with the 'Clear Queue' button (or /dq clear).",
                        "Press 'Clear Queue' (or type /dq clear).", "BTN:Clear Queue", 1));
                l.add(info("Tidy up", "If you set a test minimum, set MIN PAYMENT back to 0 (or empty) and press SET to accept every payment again. Other handy commands: /dq list, /dq remove <name>, /dq add <name> <amount>, /dq setlever (only one lever counts)."));
            }
            case "STARTBAL" -> {
                l.add(info("What this does", "Starting Bal tracks how much you earn from now on. It starts at 0 and goes up with payments and sales, down with purchases, and can show progress to a goal."));
                l.add(menu("The box", "Press 'Bal Box' to switch the box on your screen on or off. Leave it ON.", "Press the 'Bal Box' button.", "BTN:Bal Box*", 2));
                l.add(menu("Start tracking", "Press START TRACKING. The mod then waits for you to run /bal.", "Press START TRACKING.", "TRACK_STARTED", 2));
                l.add(doIt("Run /bal", "Type /bal in chat. The mod reads your balance from the answer as the starting point. This only happens once after starting.", "Type /bal in chat.", "BAL_CAPTURED"));
                l.add(info("Then it counts", "From now on sales, payments you get and orders you make move the number. A /bal after that is ignored on purpose, so it never counts as money."));
                l.add(menu("Set a goal", "Type 500m into GOAL AMOUNT and press SET. The ring in the box then shows how far you are (you can use k, m and b).", "Type 500m in GOAL AMOUNT and press SET.", "GOAL_SET", 2));
                l.add(menu("Move the box", "Press 'Move Box' to drag the box where you want it. Close the mover when you're done.", "Press 'Move Box'.", "BTN:Move Box", 2));
                l.add(menu("Clear the goal", "Press CLEAR GOAL to remove the goal again.", "Press CLEAR GOAL.", "GOAL_CLEARED", 2));
                l.add(menu("Reset tracking", "RESET TRACKING stops tracking and sets everything back to 0.", "Press RESET TRACKING.", "TRACK_RESET", 2));
                l.add(info("Commands", "/start_bal track - start tracking\n/start_bal goal set <amount> - set a goal\n/start_bal goal check - see progress\n/start_bal goal clear - stop"));
            }
            case "BIDDING" -> {
                l.add(info("What this does", "A bidding round has a timer. Whoever sends the biggest single payment before time runs out leads, and the box shows the leader. This tutorial uses simulated bids. For auctions of a specific item with presets, see the Auction tutorial in the Bidding menu ({BID})."));
                l.add(menu("The box", "Press 'Bid Box' to switch the bidding box on or off. Leave it ON.", "Press the 'Bid Box' button.", "BTN:Bid Box*", 3));
                l.add(menu("Minimum bid", "Payments below the minimum bid don't count. Type 50k into MINIMUM BID and press SET.", "Type 50k in MINIMUM BID and press SET.", "BID_MIN_SET", 3));
                l.add(menu("Start a round", "Type 5m into ROUND TIME (that's 5 minutes; 60 = 60 seconds, 1h = an hour) and press START.", "Type 5m in ROUND TIME and press START.", "BID_STARTED", 3));
                l.add(new Step("First bid", "Simulate a bid of $100k from TutorialTom. He takes the lead.", "Press the simulate button in the guide.", "BID_PAYMENT", -1, "BID1", "Simulate: TutorialTom bids $100k"));
                l.add(new Step("A higher bid", "A bigger bid takes the lead. A smaller one would not.", "Press the simulate button in the guide.", "BID_PAYMENT", -1, "BID2", "Simulate: TutorialAnn bids $250k"));
                l.add(info("The end", "When the timer hits zero the box shows the winner. Names on the bidding blacklist (Size, Color & Blacklist) can never lead."));
                l.add(menu("Stop the round", "Press STOP ROUND to cancel and reset the round.", "Press STOP ROUND.", "BID_STOPPED", 3));
                l.add(menu("Move the box", "Press 'Move Box' to place the bidding box.", "Press 'Move Box'.", "BTN:Move Box", 3));
            }
            case "AUCTION" -> {
                l.add(info("What this does", "You can start an auction two ways: with a PRESET (a saved item, amount and time, started with one click or /bidding <number>) or straight from the left side of the menu (pick an item, amount and time and press START). In this tutorial you make a preset and run it."));
                l.add(doIt("Open the menu", "Press {BID} to open the Bidding menu. If you are already in it, close it and press the key.", "Press {BID} to open the Bidding menu.", "BID_MENU_OPENED"));
                l.add(new Step("Pick an empty slot", "There are 8 preset slots. Click an EMPTY slot to start making a preset.", "Click an empty preset slot.", "PRESET_EDITOR_OPENED", -1, null, null));
                l.add(new Step("Make the preset", "Choose the item (search or click it), type the amount and the time (60, 5m, 1h), optionally a minimum bid for this preset, then press CONFIRM. Nothing is taken from what you are holding or have selected.",
                        "Pick an item, amount and time, then press CONFIRM.", "PRESET_SAVED", -1, null, null));
                l.add(new Step("Start it", "Now click the preset's button in the list. The auction starts and the Bidding box shows the item.", "Click your new preset in the Bidding menu.", "BID_STARTED", -1, null, null));
                l.add(new Step("Receive a bid", "Simulate a bid so you can see the leader in the Bidding box.", "Press the simulate button in the guide.", "BID_PAYMENT", -1, "BID1", "Simulate: TutorialTom bids $100k"));
                l.add(doIt("Stop it", "Open the Bidding menu again ({BID}) and press STOP (bottom left).", "Press {BID} and then STOP.", "BID_STOPPED"));
                l.add(doIt("Use the command", "Start the same preset from chat: type /bidding and its number, e.g. /bidding 1. This is the only bidding command.", "Type /bidding <preset number> in chat.", "BID_STARTED"));
                l.add(info("No preset needed", "Presets are optional. On the left of the Bidding menu pick an item, type the amount and the time and press START to run an auction right away, nothing is saved."));
                l.add(info("Settings (gear)", "The gear button next to Tutorial opens the other Bidding settings: show/hide the box, move the box, and size, colour and blacklist. Round time, minimum bid and STOP are right on the hotkey menu. The minimum bid there (and in a preset) only counts for that auction."));
                l.add(info("Edit and clear", "In the list, E changes a preset (the same editor, with everything filled in) and X empties it. To stop the running auction press STOP in the Bidding menu."));
            }
            case "INFO" -> {
                l.add(info("What this does", "The Info box is a small box on your screen with fields you choose (for example balance, time or other stats)."));
                l.add(menu("Switch it", "Press 'Info Box' to switch the box on or off. Leave it ON.", "Press the 'Info Box' button.", "BTN:Info Box*", 4));
                l.add(menu("Move it", "Press 'Move Box' and drag the box where you want it.", "Press 'Move Box'.", "BTN:Move Box", 4));
                l.add(menu("Edit fields", "'Edit Fields, Size & Color' lets you choose what the box shows, how big it is and its colour. Open it.", "Press 'Edit Fields, Size & Color'.", "BTN:Edit Fields*", 4));
            }
            case "EARNINGS" -> {
                l.add(info("What this does", "Earnings keeps a running total per player: what they paid you and what you paid them. It also draws graphs for every day."));
                l.add(menu("Tracking", "Press 'Earn Track' to switch tracking on or off. It must be ON to record.", "Press the 'Earn Track' button.", "BTN:Earn Track*", 5));
                l.add(menu("The box", "Press 'Earn Box' to show or hide the earnings box.", "Press the 'Earn Box' button.", "BTN:Earn Box*", 5));
                l.add(menu("Most lost / earned", "The list on the right shows who you earned the most from. Press the button under it to switch to who you lost the most to. Click a name in the list to mark them 'non gamble' (hidden). Press 'Removed' under the list to see everyone you hid and click a name to bring them back.", "Press 'Show Most Lost' / 'Show Most Earned'.", "BTN:Show Most*", 5));
                l.add(menu("Payments log", "Open the payments log. It lists every payment with time, who and how much. Use All / Got / Paid, the date box and the name search to filter.", "Press 'Payments log'.", "BTN:Payments log*", 5));
                l.add(menu("Go back", "Press 'Back' to leave the log.", "Press 'Back'.", "BTN:Back", 5));
                l.add(menu("Stats", "'Stats' shows the big graph for all days and each single day. Open it.", "Press 'Stats'.", "BTN:Stats*", 5));
                l.add(info("Commands", "/earnings track and /earnings stop - tracking on/off\n/earnings top - best players\n/earnings nongamble add <name> - hide a player\n/earnings reset - clear totals"));
            }
            case "SESSIONS" -> {
                l.add(info("What this does", "A session is a stretch of play you start and end yourself. It records the balance graph, every payment and who gained or lost you the most. Sessions are kept forever."));
                l.add(menu("Open the tab", "Click the 'Sessions' tab.", "Click the 'Sessions' tab.", "BTN:Sessions", 0));
                l.add(menu("Start a session", "Press 'start session'. For the balance graph, also use Starting Bal tracking (see that tutorial).", "Press 'start session'.", "SESSION_STARTED", 6));
                l.add(info("While it runs", "A small 'Live' pill shows on your screen. Click its position button in the Sessions page to cycle where it sits."));
                l.add(menu("End the session", "Press 'end session'. It is saved in the list below.", "Press 'end session'.", "SESSION_ENDED", 6));
                l.add(info("Look at old ones", "Click a session in the list to open its graph, its payments and its top players. The little x deletes one."));
            }
            default -> {
                l.add(info("What this does", "Customisation changes how things look: rank colours and the menu itself."));
                l.add(menu("Ranks", "Open 'Ranks'. It recolours player names by rank in the tab list and on name tags.", "Press 'Ranks'.", "BTN:Ranks", 7));
                l.add(menu("Menu customisation", "Open 'Menu customisation': colour, animations, which cards the Main Menu shows, which tabs are visible and what the four buttons do.", "Press 'Menu customisation'.", "BTN:Menu customisation", 7));
                l.add(info("Guards", "'Guards' asks 'are you sure?' before big spending. Pay Guard checks /pay <player> <amount>, Auction Guard checks auction house purchases. Each has an ON/OFF button and an amount (both OFF by default)."));
            }
        }
        l.add(info("All done!", "You finished the " + switch (id) {
            case "MAIN" -> "Main Menu";
            case "QUEUE" -> "Anti Gamble Skip";
            case "STARTBAL" -> "Starting Bal";
            case "BIDDING" -> "Bidding";
            case "AUCTION" -> "Auction Presets";
            case "INFO" -> "Info Display";
            case "EARNINGS" -> "Earnings";
            case "SESSIONS" -> "Sessions";
            default -> "Customisation";
        } + " tutorial. You can start it again any time with the Tutorial button."));
        return l;
    }
}
