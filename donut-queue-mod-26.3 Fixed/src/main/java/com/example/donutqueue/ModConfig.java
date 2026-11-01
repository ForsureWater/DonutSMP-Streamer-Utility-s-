package com.example.donutqueue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Persisted settings. Edit config/donutqueue.json directly, or use the
 * /dq commands in-game — both read/write the same file.
 *
 * chatPatterns: each entry must be a Java regex with two named groups,
 * (?<player>...) and (?<amount>...). The mod tries each pattern in order
 * against every chat line and stops at the first match. Add your server's
 * exact wording here if these defaults don't match.
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("donutqueue.json");

    // (?<player>\.?[a-zA-Z0-9_]{2,16}) - the leading \.? keeps a "." if the
    // server prefixes names with one (e.g. ".SpongySafe57623 paid you $500K")
    // so it shows up as part of the name everywhere (queue box, Earnings
    // box, chat messages) instead of being silently stripped off.
    public List<String> chatPatterns = new ArrayList<>(List.of(
            "(?i)you\\s+have\\s+received\\s+\\S*?\\s*(?<amount>[0-9][0-9.,]*[kmb]?)\\s+from\\s+(?<player>\\.?[a-zA-Z0-9_]{2,16})",
            "(?i)you\\s+received\\s+\\S*?\\s*(?<amount>[0-9][0-9.,]*[kmb]?)\\s+from\\s+(?<player>\\.?[a-zA-Z0-9_]{2,16})",
            "(?i)(?<player>\\.?[a-zA-Z0-9_]{2,16})\\s+has\\s+paid\\s+you\\s+\\S*?\\s*(?<amount>[0-9][0-9.,]*[kmb]?)",
            "(?i)(?<player>\\.?[a-zA-Z0-9_]{2,16})\\s+paid\\s+you\\s+\\S*?\\s*(?<amount>[0-9][0-9.,]*[kmb]?)"
    ));

    // Lines matching any of these are whispers/DMs, e.g. "Highfive2k22
    // whispers to you: Highfive2k22 paid you 1b" - a private echo of a
    // payment that (on some servers) shows up alongside the normal public
    // chat line for the same payment. A matching line still counts for
    // Bidding, Earnings and Starting Bal (chatPatterns/earnPatterns above
    // still run against it), but is never added to the actual serving
    // queue, avoiding a duplicate queue entry for the same payment.
    public List<String> whisperPatterns = new ArrayList<>(List.of(
            "(?i)whispers\\s+to\\s+you\\s*:",
            "(?i)^\\s*\\[?\\S{2,16}\\s*(?:->|→)\\s*(?:me|you)\\]?\\s*:"
    ));

    // Lines matching any of these are an order-style purchase (e.g. "You
    // ordered 1 Andesite Slab") that doesn't show a price in chat at all -
    // see handleOrderPrompt()/awaitingOrderBalCapture in DonutQueueClient
    // for how that missing price gets reconciled via a /bal check instead.
    public List<String> orderPatterns = new ArrayList<>(List.of(
            "(?i)^you\\s+ordered\\s+.+"
    ));

    public int hudX = 8;
    public int hudY = 8;
    public boolean hudEnabled = false;

    // How many lever flicks it takes to serve the person at the front and
    // move to the next one.
    public int flicksRequired = 2;

    // If restrictLever is true, only the lever at (leverX, leverY, leverZ)
    // counts toward advancing the queue. Otherwise any lever does. Set this
    // with /dq setlever while looking directly at the lever you want to use.
    public boolean restrictLever = false;
    public int leverX = 0;
    public int leverY = 0;
    public int leverZ = 0;

    // When true, flicking a lever does nothing - queue advancement is
    // paused. Toggle with the keybinding (set it in Controls) or the
    // button in the Anti Gamble Skip settings screen.
    public boolean leverPaused = false;

    // Names (case-insensitive) that should never be auto-added to the queue.
    // Manage with /queue blacklist add|remove|list|clear.
    public List<String> blacklist = new ArrayList<>();

    // Minimum payment amount required for an automatic chat-triggered add.
    // 0 means no minimum. Set with /queue minimum <amount>. Supports the
    // same k/m/b suffixes as chat amounts (e.g. "50k").
    public double minimumAmount = 0;

    // --- Starting Bal goal tracking -------------------------------------

    // 0 = no goal currently set.
    public double goalAmount = 0;
    // Running total: increases on earn messages (sales, "paid you", etc.),
    // decreases on spend messages ("you bought...", "you paid..."). Resets
    // to 0 whenever a new goal is set, or when /start_bal track is started.
    public double earnedTotal = 0;
    // True when running in plain tracking mode (/start_bal track) rather
    // than goal mode - shows just the running number, no target, no
    // celebration.
    public boolean trackingEnabled = false;
    // Prevents the celebration sound/overlay from repeating every frame
    // once the goal has been reached.
    public boolean goalReachedNotified = false;
    // Timestamp (System.currentTimeMillis) until which the big green
    // congratulations overlay should keep showing. 0 = not showing.
    public long celebrationEndTimeMillis = 0;

    public int startBalBoxX = 8;
    public int startBalBoxY = 60;
    public boolean startBalBoxEnabled = false;

    // --- Starting Bal: capture a baseline from a /bal response -----------

    // True right after /start_bal track, until the next chat line that
    // matches balPatterns below comes in. Deliberately NOT saved across a
    // restart (transient) - if you relaunch mid-wait, you'd have to run
    // /start_bal track again anyway since the prompt is gone.
    public transient boolean awaitingBalCapture = false;
    // The balance captured from /bal when tracking last began. Purely
    // informational (shown under the ring) - earnedTotal above is still
    // what actually drives progress, via earnPatterns/spendPatterns.
    public double startingBalance = 0;
    public boolean hasStartingBalance = false;

    // True right after an order-style message (see orderPatterns below) is
    // seen with Starting Bal actively tracking, until the next /bal response
    // comes in - same transient reasoning as awaitingBalCapture above.
    public transient boolean awaitingOrderBalCapture = false;

    // Chat patterns matched against a /bal response while awaitingBalCapture
    // is true, e.g. "You have $ 19,962,275". Needs a named (?<amount>...)
    // group. Tried in order, first match wins.
    public List<String> balPatterns = new ArrayList<>(List.of(
            "(?i)you\\s+have\\s+\\$?\\s*(?<amount>[0-9][0-9,]*(?:\\.[0-9]+)?)\\s*$"
    ));

    // Chat patterns that ADD to earnedTotal (sales, being paid, etc). Each
    // needs a named (?<amount>...) group. Tried against every chat line
    // alongside spendPatterns below. Uses \S*?\s* (any short icon/symbol,
    // then optional space) rather than a literal \$ before the amount -
    // some servers render the payment icon as a custom-font glyph that
    // isn't the literal "$" character, so requiring "$" here could silently
    // fail to count a payment even though it looks like a $ on screen.
    public List<String> earnPatterns = new ArrayList<>(List.of(
            "(?i)(?<player>\\.?[a-zA-Z0-9_]{2,16})\\s+bought\\s+your\\s+.+?\\s+for\\s+\\S*?\\s*(?<amount>[0-9][0-9.,]*[kmb]?)",
            "(?i)(?<player>\\.?[a-zA-Z0-9_]{2,16})\\s+paid\\s+you\\s+\\S*?\\s*(?<amount>[0-9][0-9.,]*[kmb]?)",
            "(?i)(?<player>\\.?[a-zA-Z0-9_]{2,16})\\s+has\\s+paid\\s+you\\s+\\S*?\\s*(?<amount>[0-9][0-9.,]*[kmb]?)",
            "(?i)you\\s+have\\s+received\\s+\\S*?\\s*(?<amount>[0-9][0-9.,]*[kmb]?)\\s+from\\s+(?<player>\\.?[a-zA-Z0-9_]{2,16})",
            // A chat line that's ENTIRELY just a dollar amount, like what /sell
            // prints. Requires a "$" or an actual icon glyph (any non-ASCII
            // codepoint - what a custom-font payment icon renders as) right
            // before the amount, NOT just any punctuation, so this can't
            // accidentally match an unrelated line that happens to have a
            // number after a comma or colon ("Kills: 5", "123, 45", etc).
            "(?i)^.{0,12}?(?:\\$|[^\\x00-\\x7F])(?:\\s|[^\\x00-\\x7F])*(?<amount>[0-9][0-9.,]*[kmb]?)\\s*$"
    ));

    // Chat patterns that SUBTRACT from earnedTotal (buying items, paying
    // someone else, etc). Same (?<amount>...) group requirement, same
    // icon-tolerant \S*?\s* reasoning as earnPatterns above.
    public List<String> spendPatterns = new ArrayList<>(List.of(
            "(?i)you\\s+bought\\s+.+?\\s+for\\s+\\S*?\\s*(?<amount>[0-9][0-9.,]*[kmb]?)",
            "(?i)you\\s+paid\\s+(?<player>\\.?[a-zA-Z0-9_]{2,16})\\s+\\S*?\\s*(?<amount>[0-9][0-9.,]*[kmb]?)"
    ));

    // --- Box colors (ARGB hex, e.g. FF3399FF) ----------------------------
    public int queueBoxColor = 0xFF3399FF;
    public int startBalBoxColor = 0xFF33CC66;

    // --- Bidding -----------------------------------------------------------

    // All HUD boxes (queue, Starting Bal, Bidding, Info, Earnings) are OFF on a fresh install; turn them on in the menu.
    public boolean biddingBoxEnabled = false;
    public int biddingBoxX = 8;
    public int biddingBoxY = 112;
    public int biddingBoxColor = 0xFFFFAA33;

    public boolean biddingActive = false;
    public boolean biddingFinished = false;
    public long biddingEndTimeMillis = 0;
    // How long the current/last round was set for, in milliseconds - needed
    // alongside biddingEndTimeMillis to work out what fraction of time is
    // left (for the countdown ring), since only knowing the end time isn't
    // enough to know the starting length.
    public long biddingTotalDurationMillis = 0;
    public String biddingLeaderName = null;
    public double biddingLeaderAmount = 0;

    // Minimum amount a payment must be to count as a bid (0 = no minimum). Supports k/m/b like every
    // other amount. Set on the Bidding page or with /bidding minimum <amount>.
    public double biddingMinimum = 0;

    // --- Auction presets (Bidding menu, hotkey) ------------------------------
    /** How many preset slots the bidding menu / "/bidding <number>" has. */
    public static final int BID_PRESET_SLOTS = 8;

    /** One saved auction: what is being auctioned, how many of it and how long the round lasts. */
    public static class BidPreset {
        public String itemId = "";
        public int count = 1;
        public long seconds = 60;
        /** Minimum bid for this preset (0 = none). Applied when the preset is started. */
        public double minimum = 0;

        public boolean isEmpty() {
            return itemId == null || itemId.isEmpty() || count <= 0 || seconds <= 0;
        }
    }

    /** Always BID_PRESET_SLOTS long; an empty slot has an empty itemId. */
    public List<BidPreset> biddingPresets = new ArrayList<>();

    /** The item/amount of the auction that is running (or just finished); shown in the Bidding box. Empty = none. */
    public String biddingItemId = "";
    public int biddingItemCount = 0;

    /** Pads/trims the preset list to BID_PRESET_SLOTS and replaces nulls (old or hand-edited configs). */
    public void ensureBidPresets() {
        if (biddingPresets == null) biddingPresets = new ArrayList<>();
        while (biddingPresets.size() < BID_PRESET_SLOTS) biddingPresets.add(new BidPreset());
        while (biddingPresets.size() > BID_PRESET_SLOTS) biddingPresets.remove(biddingPresets.size() - 1);
        for (int i = 0; i < biddingPresets.size(); i++) {
            if (biddingPresets.get(i) == null) biddingPresets.set(i, new BidPreset());
        }
        if (biddingItemId == null) biddingItemId = "";
    }

    // Names (case-insensitive) whose payments are ignored for bidding
    // purposes, independent of the main queue blacklist.
    public List<String> biddingBlacklist = new ArrayList<>();

    // --- Info Display -------------------------------------------------------

    public boolean infoBoxEnabled = false;
    public int infoBoxX = 8;
    public int infoBoxY = 160;
    public int infoBoxColor = 0xFFFF66FF;
    // Scales the whole box (background, border, and text) up or down.
    public float infoBoxScale = 1.0f;

    // Size of the other HUD boxes (0.5x - 3.0x). The whole box - background, border and text - is scaled
    // around its top-left corner, so it stays where you put it. Set in each page's side menu.
    public float hudScale = 1.0f;
    public float startBalBoxScale = 1.0f;
    public float biddingBoxScale = 1.0f;
    public float earningsBoxScale = 1.0f;

    public static float clampScale(float s) {
        if (Float.isNaN(s) || s <= 0) return 1.0f;
        return Math.max(0.5f, Math.min(3.0f, s));
    }

    public String infoIgn = "";
    public String infoMin = "";
    public String infoMax = "";
    public String infoGame = "";
    // Max 25 characters, enforced by the EditBox in the settings screen.
    public String infoCustomText = "";

    // --- Earnings leaderboard ------------------------------------------------

    // True while /earnings track is active. Only while true do incoming
    // payments matched via earnPatterns (above) that captured a player
    // name get added to earningsTotals below. Independent of the Starting
    // Bal goal/tracking flags - this is its own on/off switch.
    public boolean earningsTrackingEnabled = false;

    // Running total per player since tracking last started (or was last
    // reset), keyed case-insensitively (the key stored is whatever case
    // the name first appeared in). NET total: earnPatterns matches (they
    // paid you) add, spendPatterns matches (you paid them) subtract - so
    // if you pay someone back, their total goes back down. Can go negative
    // if you've paid a given player more than they've paid you.
    public LinkedHashMap<String, Double> earningsTotals = new LinkedHashMap<>();

    // How many rows of the leaderboard to display, 1-25. Set with
    // /earnings top <n> or the +/- stepper in the Earnings settings screen.
    public int earningsShowCount = 5;

    // Earnings page right rail: false = "TOP 7" (who paid you the most that day),
    // true = "LOST MOST" (who you paid more than they paid you that day).
    public boolean earningsTopShowLost = false;

    // People marked "non gamble" on the Earnings page: they are left out of the lost-most list and
    // the pillar graph (and out of the loss total the % rings are a share of).
    public List<String> nonGamblePlayers = new ArrayList<>();

    /** Customisation > Ranks: recolour names (tab list, nametags, text-display tags) by rank. */
    public boolean rankColorsEnabled = false;
    /** Customisation > Ranks > Change staff team colour (name AND star turn this colour). */
    public int staffColor = 0xFF5555FF;

    // --- Customisation > Menu customisation ----------------------------------
    /** Menu colour (the neon accent everything else is shaded from). Default blue = the original look. */
    public int menuColor = 0xFF2F6BFF;
    /** GUI style: 0 = Neon (glow outline), 1 = Flat (solid, no glow), 2 = Glass (see-through). */
    public int menuStyle = 0;
    /** Dim the game behind the menu. */
    public boolean menuBackdropDim = true;
    /** How solid the menu panel is, 40-100 (%). */
    public int menuPanelOpacity = 100;

    /** Animation when switching between menu pages. */
    public boolean menuAnimEnabled = true;
    /** 0 = Fade, 1 = Slide, 2 = Wipe. */
    public int menuAnimStyle = 0;
    /** 0 = Fast, 1 = Normal, 2 = Slow. */
    public int menuAnimSpeed = 1;

    /** What is shown on the Main Menu page. */
    public boolean mainShowStartBal = true;
    public boolean mainShowToday = true;
    public boolean mainShowGraph = true;
    public boolean mainShowStats = true;
    public boolean mainShowButtons = true;
    /** Order of the Main Menu page's items, top to bottom (drag & drop in Menu customisation > Main menu items). */
    public List<String> mainOrder = new ArrayList<>(List.of("START_BAL", "TODAY", "GRAPH", "STATS", "BUTTONS"));

    /** Main Menu bottom buttons: false = 2 buttons (one row), true = 4 buttons (2 x 2). */
    public boolean mainFourButtons = false;
    /** What each of the 4 bottom button slots does (action ids, see FuturisticMenuScreen.ACT_IDS). Only the first 2 are used in 2-button mode. */
    public List<String> mainButtons = new ArrayList<>(List.of("SESSION", "STATS", "BIDDING", "TRACKING"));
    /** Main Menu graph: 0 = today (10 minute steps), 1 = all time (one point per day). */
    public int mainGraphRange = 0;
    /** Main Menu graph: false = your balance, true = your gains (profit / loss). */
    public boolean mainGraphGains = false;

    /** Sidebar tabs the player has hidden (names from the menu's page list). Main Menu and Customisation can't be hidden. */
    public List<String> menuHiddenTabs = new ArrayList<>();

    // --- Customisation > Chat ------------------------------------------------
    /** Re-style "X paid you $50M." / "You paid X $50M." lines (names, amounts coloured). Off until turned on in the menu. */
    public boolean chatPaymentsEnabled = false;
    /** Re-style whispers as "You -> Name: msg" / "Name -> You: msg" (names coloured, message white + bold). Off until turned on. */
    public boolean chatWhispersEnabled = false;
    /** Colour of player names in the re-styled lines (default light blue). */
    public int chatNameColor = 0xFF5599FF;
    /** Colour of the amount when someone paid YOU (default green). */
    public int chatReceivedColor = 0xFF55FF55;
    /** Colour of the amount when YOU paid someone (default red). */
    public int chatSentColor = 0xFF55FF55;
    /** One-time upgrade marker: pay colours (name blue, amount green) switched on for existing configs. */
    public boolean chatColoursV2 = false;

    // --- Customisation > Guards ----------------------------------------------
    /** Pay Guard: ask "are you sure?" before a /pay of at least guardPayAmount. Off until turned on. */
    public boolean guardPayEnabled = false;
    public double guardPayAmount = 1_000_000;
    /** false: the warning shows "175M"; true: it shows the whole number "175,000,000". */
    public boolean guardPayFullNumber = false;
    public boolean guardAuctionFullNumber = false;
    /** Auction Guard: ask "are you sure?" before buying an auction listing priced at least guardAuctionAmount. Off until turned on. */
    public boolean guardAuctionEnabled = false;
    public double guardAuctionAmount = 1;

    public boolean earningsBoxEnabled = false;
    public int earningsBoxX = 8;
    public int earningsBoxY = 220;
    public int earningsBoxColor = 0xFFFFD700;

    /** Colour of the "Live | +$..." pill shown at the top of the screen while a session is running. */
    public int liveBoxColor = 0xFF3399FF;

    /** Where the Live pill sits: 0 = top middle (default), 1 = top left, 2 = top right, 3 = above the hotbar (middle). */
    public int liveBoxPos = 0;

    // --- Per-section chat message toggles -------------------------------
    //
    // Each of these mutes ALL chat output belonging to that section -
    // both automatic notifications (e.g. "Served X ($Y). Queue is now
    // empty.", "Starting balance captured: $X") and that section's own
    // command replies (e.g. "/dq add" confirming it added someone). The
    // action still happens either way - this only silences the chat text.
    // All default true (current behavior, unchanged unless you turn one off).
    public boolean queueChatMessagesEnabled = true;
    public boolean startBalChatMessagesEnabled = true;
    public boolean biddingChatMessagesEnabled = true;
    public boolean earningsChatMessagesEnabled = true;

    // --- Customisation > Screen features (all OFF until turned on) -------------
    public boolean kbmEnabled = false;
    /** 0 = WASD, 1 = WASD + mouse, 2 = WASD + Space + mouse, 3 = full (WASD, Ctrl, Shift, Space, mouse). */
    public int kbmMode = 1;
    public int kbmX = 8;
    public int kbmY = 120;
    public boolean cpsEnabled = false;
    public int cpsX = 8;
    public int cpsY = 214;
    public boolean fpsEnabled = false;
    public int fpsX = 8;
    public int fpsY = 100;
    public float screenFeaturesScale = 1.0f;
    /** Colour of the Screen features boxes (RGB). 0 = follow the menu theme. */
    public int screenFeaturesColor = 0;

    // --- Customisation > Chat > Chat Settings ----------------------------------
    /** Keep your chat when you leave a server and join back (the last 100 lines are restored). */
    public boolean chatSaveEnabled = true;
    /** Play a sound whenever someone pays you. */
    public boolean paySoundsEnabled = false;

    public static ModConfig load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                if (config != null) {
                    config.ensureBidPresets();
                    // A saved config keeps its OLD pattern lists, so upgrade them here: names
                    // with a leading "." (e.g. ".SpongySafe57623") must be captured with the dot.
                    boolean changed = fixNamePatterns(config.chatPatterns);
                    changed |= fixNamePatterns(config.earnPatterns);
                    changed |= fixNamePatterns(config.spendPatterns);
                    if (!config.chatColoursV2) {
                        config.chatColoursV2 = true;
                        config.chatPaymentsEnabled = true;
                        config.chatNameColor = 0xFF5599FF;
                        config.chatReceivedColor = 0xFF55FF55;
                        config.chatSentColor = 0xFF55FF55;
                        changed = true;
                    }
                    if (changed) config.save();
                    return config;
                }
            } catch (Exception e) {
                System.err.println("[DonutQueue] Couldn't read config/donutqueue.json (check it's valid JSON - "
                        + "a missing comma or bracket from hand-editing is the usual cause). Using defaults instead. Error: " + e);
            }
        }
        ModConfig fresh = new ModConfig();
        fresh.chatColoursV2 = true;
        fresh.chatPaymentsEnabled = true;
        fresh.ensureBidPresets();
        fresh.save();
        return fresh;
    }

    /** Makes every (?<player>...) group in the list accept an optional leading "." - returns true if anything changed. */
    private static boolean fixNamePatterns(List<String> list) {
        if (list == null) return false;
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            String p = list.get(i);
            if (p == null) continue;
            String f = p.replace("(?<player>[a-zA-Z0-9_]", "(?<player>\\.?[a-zA-Z0-9_]")
                    .replace("(?<player>\\w", "(?<player>\\.?\\w");
            if (!f.equals(p)) {
                list.set(i, f);
                changed = true;
            }
        }
        return changed;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
