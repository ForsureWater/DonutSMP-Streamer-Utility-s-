package com.example.donutqueue;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.phys.BlockHitResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft 1.21.11 build (Mojang names, Java 21).
 * Uses GuiGraphicsExtractor (text / outline / blit with RenderPipelines), Screen#render,
 * ClientCommands and KeyMappingHelper from Fabric API 26.1.
 */
public class DonutQueueClient implements ClientModInitializer {

    public static ModConfig config;
    public static QueueManager queue;

    private final List<Pattern> compiledPatterns = new ArrayList<>();
    private final List<Pattern> compiledEarnPatterns = new ArrayList<>();
    private final List<Pattern> compiledSpendPatterns = new ArrayList<>();
    private final List<Pattern> compiledBalPatterns = new ArrayList<>();
    private final List<Pattern> compiledWhisperPatterns = new ArrayList<>();
    private final List<Pattern> compiledOrderPatterns = new ArrayList<>();
    private int flickCount = 0;
    private static boolean debugChat = false;
    private static boolean processingChatMessage = false;

    // 1.21.9+: keybind categories are KeyMapping.Category objects. The displayed name comes from the
    // translation key "key.category.donutqueue.main" (see assets/donutqueue/lang/en_us.json).
    private static final KeyMapping.Category DONUTQUEUE_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("donutqueue", "main"));

    // Unbound by default (InputConstants.UNKNOWN) so it can't collide with an
    // existing keybind - the player sets this themselves in Controls if
    // they want it.
    private static final KeyMapping TOGGLE_LEVER_SKIP_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.donutqueue.togglelever",
            InputConstants.UNKNOWN.getValue(),
            DONUTQUEUE_CATEGORY
    ));

    // Manually serves whoever's first and moves to the next person,
    // without needing to flick a lever or click a note block at all.
    // Ignores the lever-skip pause - it's a deliberate manual action.
    private static final KeyMapping SKIP_NEXT_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.donutqueue.skipnext",
            InputConstants.UNKNOWN.getValue(),
            DONUTQUEUE_CATEGORY
    ));

    // Opens the neon DonutSMP Streamer Utilitys menu. Right Shift by default.
    // Same category as the other two keybinds, so every key this mod adds
    // is listed together under "DonutSMP Streamer Utilitys" in Controls.
    private static final KeyMapping OPEN_MENU_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.donutqueue.openmenu",
            InputConstants.KEY_RSHIFT,
            DONUTQUEUE_CATEGORY
    ));

    // Opens the auction/bidding menu (pick an item, amount, time, and use the presets). B by default.
    private static final KeyMapping OPEN_BIDDING_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.donutqueue.openbidding",
            InputConstants.KEY_B,
            DONUTQUEUE_CATEGORY
    ));

    private static DonutQueueClient instance;

    /**
     * Puts a message in the player's own chat. Minecraft 26.1 renamed/moved the old
     * Player#displayClientMessage(Component, boolean), so look the method up by name at
     * runtime (sendSystemMessage first, the old name as a fallback) instead of hard-linking
     * to one that may not exist.
     */
    private static void say(net.minecraft.world.entity.player.Player player, Component message) {
        if (true) return; // chat messages are removed completely
        if (player == null) return;
        try {
            player.getClass().getMethod("sendSystemMessage", Component.class).invoke(player, message);
            return;
        } catch (ReflectiveOperationException ignored) {
            // fall through to the old name
        }
        try {
            player.getClass().getMethod("displayClientMessage", Component.class, boolean.class).invoke(player, message, false);
        } catch (ReflectiveOperationException e) {
            System.err.println("[DonutQueue] Could not send chat message: " + e);
        }
    }

    /** Draws a player's face (used by the neon menu for your own skin). Safe to call every frame. */
    static void drawHead(GuiGraphicsExtractor guiGraphics, String playerName, int x, int y, int size) {
        if (instance != null) instance.renderPlayerHead(guiGraphics, playerName, x, y, size);
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        config = ModConfig.load();
        queue = new QueueManager();
        compilePatterns();

        registerChatListener();
        registerHud();
        registerLever();
        registerCommands();
        registerKeybindTick();
        Guards.register();
        ClientTickEvents.END_CLIENT_TICK.register(com.example.donutqueue.rank.OwnTag::tick);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());

        // Chat saves: keep the chat when you leave a server and put it back when you join again
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ChatSave.save());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ChatSave.save());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ChatSave.replay(client));

        // Let addon mods (entrypoint "donutqueue-addon") set themselves up at startup, not only once the menu is opened.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> AddonRegistry.all());
    }

    private void compilePatterns() {
        compiledPatterns.clear();
        for (String raw : config.chatPatterns) {
            try {
                compiledPatterns.add(Pattern.compile(raw));
            } catch (Exception e) {
                System.err.println("[DonutQueue] Skipping invalid chatPatterns regex: " + raw);
            }
        }
        compiledEarnPatterns.clear();
        for (String raw : config.earnPatterns) {
            try {
                compiledEarnPatterns.add(Pattern.compile(raw));
            } catch (Exception e) {
                System.err.println("[DonutQueue] Skipping invalid earnPatterns regex: " + raw);
            }
        }
        // Built-in extra, always added (a saved config/donutqueue.json keeps its OLD pattern
        // list, so fixing the default alone would not reach existing installs): a chat line
        // that is ONLY an icon + amount, like what /sell prints ("$ 54.1K"). Case-insensitive
        // so a capital K/M/B counts, and tolerant of extra spacer/icon glyphs between the
        // icon and the number.
        compiledEarnPatterns.add(Pattern.compile(
                "(?i)^.{0,12}?(?:\\$|[^\\x00-\\x7F])(?:\\s|[^\\x00-\\x7F])*(?<amount>[0-9][0-9.,]*[kmb]?)\\s*$"));
        compiledSpendPatterns.clear();
        for (String raw : config.spendPatterns) {
            try {
                compiledSpendPatterns.add(Pattern.compile(raw));
            } catch (Exception e) {
                System.err.println("[DonutQueue] Skipping invalid spendPatterns regex: " + raw);
            }
        }
        compiledBalPatterns.clear();
        for (String raw : config.balPatterns) {
            try {
                compiledBalPatterns.add(Pattern.compile(raw));
            } catch (Exception e) {
                System.err.println("[DonutQueue] Skipping invalid balPatterns regex: " + raw);
            }
        }
        // Built-in extras so a /bal reply is ALWAYS recognised (and therefore never
        // counted as an earning) even if the saved config has an older pattern:
        // tolerates a custom-font $ icon in place of "$" and a K/M/B suffix.
        compiledBalPatterns.add(Pattern.compile(
                "(?i)you\\s+have\\s+\\S{0,3}\\s*(?<amount>[0-9][0-9,]*(?:\\.[0-9]+)?[kmb]?)\\s*$"));
        compiledWhisperPatterns.clear();
        for (String raw : config.whisperPatterns) {
            try {
                compiledWhisperPatterns.add(Pattern.compile(raw));
            } catch (Exception e) {
                System.err.println("[DonutQueue] Skipping invalid whisperPatterns regex: " + raw);
            }
        }
        compiledOrderPatterns.clear();
        for (String raw : config.orderPatterns) {
            try {
                compiledOrderPatterns.add(Pattern.compile(raw));
            } catch (Exception e) {
                System.err.println("[DonutQueue] Skipping invalid orderPatterns regex: " + raw);
            }
        }
    }

    // --- Chat detection (fully automatic, no player action needed) --------

    private void registerChatListener() {
        // ALLOW_GAME (not GAME) so detection always sees the ORIGINAL server line: the Chat customisation
        // (ChatStyle, MODIFY_GAME below) rewrites the line afterwards, and e.g. whisper detection relies on
        // the server's own "whispers to you:" wording.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true; // ignore action bar echoes, only look at chat
            if (processingChatMessage) return true; // safety: never process a message triggered by our own output
            processingChatMessage = true;
            try {
                handleChatMessage(message.getString());
            } finally {
                processingChatMessage = false;
            }
            return true; // never hide the message
        });
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
                overlay ? message : ChatSave.keep(ChatStyle.modify(message)));
        // player chat lines (the ones that are not "game" messages) are remembered too
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                ChatSave.keep(message));
    }

    private void handleChatMessage(String text) {
            if (debugChat) {
                StringBuilder codepoints = new StringBuilder();
                text.codePoints().forEach(cp -> codepoints.append(String.format("U+%04X ", cp)));
                System.out.println("[DonutQueue DEBUG] raw=\"" + text + "\"");
                System.out.println("[DonutQueue DEBUG] codepoints=" + codepoints);
                // NOTE: deliberately not echoing this into chat - doing so risked
                // feeding the echoed message back into this same listener and
                // recursing infinitely. Check logs/latest.log for these lines instead.
            }

            handleBalCapture(text);

            // A /bal reply ("You have $ 19,962,275") must never be treated as money
            // coming in - the lone-amount earn pattern would otherwise add it to the
            // Start Bal box. So once it has (optionally) been used to capture the
            // starting balance above, it is ignored completely.
            if (isBalLine(text)) return;

            // "/bal <someone else>" prints "<name> has $ 1.48". That is NOT money coming in, so it must
            // never reach the earn patterns (the lone-amount one would otherwise count it).
            if (isOtherBalLine(text)) return;

            boolean isWhisper = isWhisperLine(text);

            // "You ordered 1 Acacia Shelf" shows no price - ask for a /bal to work it out.
            if (!isWhisper) handleOrderPrompt(text);

            for (Pattern pattern : compiledPatterns) {
                Matcher m = pattern.matcher(text);
                if (!m.find()) continue;

                String player;
                String amount;
                try {
                    player = playerName(m, text);
                    amount = m.group("amount");
                } catch (IllegalArgumentException ex) {
                    continue; // pattern didn't have the expected named groups
                }
                if (player == null || player.isEmpty()) continue;

                handleBiddingPayment(player, amount);

                // A whisper (e.g. "X whispers to you: X paid you 1b") still
                // counts for Bidding above and Earnings/Starting Bal below,
                // but never goes into the actual serving queue - see
                // isWhisperLine()/whisperPatterns for why.
                if (isWhisper) {
                    Tutorial.event("WHISPER");
                    break;
                }

                admit(player, amount);
                break;
            }

            handleEarnSpend(text);
            handleEarningsTracking(text);
    }

    /** A payment line arrived (or is simulated): blacklist / minimum checks, then into the queue. Returns ADDED, BLACKLISTED or BELOW_MIN. */
    private String admit(String player, String amount) {
        Minecraft client = Minecraft.getInstance();

        if (isBlacklisted(player)) {
            if (client.player != null && config.queueChatMessagesEnabled) {
                say(client.player,
                        Component.literal("[DonutQueue] Ignored " + player + " - blacklisted"));
            }
            Tutorial.event("IGNORED_BLACKLIST");
            return "BLACKLISTED";
        }

        double parsedAmount = parseAmount(amount);
        if (config.minimumAmount > 0 && parsedAmount < config.minimumAmount) {
            if (client.player != null && config.queueChatMessagesEnabled) {
                say(client.player,
                        Component.literal("[DonutQueue] Ignored " + player + " ($" + (amount == null ? "?" : amount)
                                + ") - below minimum $" + formatAmount(config.minimumAmount)));
            }
            Tutorial.event("IGNORED_MIN");
            return "BELOW_MIN";
        }

        queue.add(new QueueEntry(player, amount == null ? "?" : amount, System.currentTimeMillis()));

        if (client.player != null && config.queueChatMessagesEnabled) {
            say(client.player,
                    Component.literal("[DonutQueue] Added " + player + " ($" + (amount == null ? "?" : amount)
                            + ") to the queue (position " + queue.size() + ")"));
        }
        Tutorial.event("QUEUE_ADD");
        return "ADDED";
    }

    /** Tutorial only: fake payments / bids. They only touch the queue and bidding, never earnings or Starting Bal. */
    static String simulate(String simId) {
        if (instance == null) return "Not ready yet.";
        return instance.simulateInternal(simId);
    }

    private String simulateInternal(String simId) {
        switch (simId) {
            case "PAY", "PAY_SMALL" -> {
                String amount = simId.equals("PAY") ? "1m" : "10k";
                String r = admit("TutorialTom", amount);
                return switch (r) {
                    case "ADDED" -> "TutorialTom ($" + amount + ") joined the queue.";
                    case "BELOW_MIN" -> "TutorialTom ($" + amount + ") was ignored - below your minimum.";
                    default -> "TutorialTom was ignored.";
                };
            }
            case "PAY_BLACKLIST" -> {
                String fake = "TutorialBlocked";
                boolean had = isBlacklisted(fake);
                if (!had) config.blacklist.add(fake);
                String r = admit(fake, "1m");
                if (!had) config.blacklist.removeIf(n -> n.equalsIgnoreCase(fake));
                return r.equals("BLACKLISTED") ? "TutorialBlocked is blacklisted - ignored." : "TutorialBlocked joined the queue.";
            }
            case "PAY_WHISPER" -> {
                Tutorial.event("WHISPER");
                return "A whispered payment: counted for earnings, but NOT queued.";
            }
            case "BID1", "BID2" -> {
                String who = simId.equals("BID1") ? "TutorialTom" : "TutorialAnn";
                String amt = simId.equals("BID1") ? "100k" : "250k";
                if (!config.biddingActive) return "No round is running - start one first.";
                String before = config.biddingLeaderName;
                double beforeAmt = config.biddingLeaderAmount;
                handleBiddingPayment(who, amt);
                boolean changed = who.equals(config.biddingLeaderName) && (!who.equals(before) || config.biddingLeaderAmount != beforeAmt);
                return changed ? who + " leads with $" + amt + "." : who + "'s bid did not take the lead (too low or below the minimum).";
            }
            default -> {
                return "";
            }
        }
    }

    /** Name of a key for tutorial texts. */
    static String keyLabel(String which) {
        KeyMapping k = switch (which) {
            case "SKIP" -> SKIP_NEXT_KEY;
            case "LEVER" -> TOGGLE_LEVER_SKIP_KEY;
            case "MENU" -> OPEN_MENU_KEY;
            default -> OPEN_BIDDING_KEY;
        };
        return "[" + k.getTranslatedKeyMessage().getString() + "]";
    }

    // "54nino has $ 1.48" - the reply to /bal <player>. Allows a "." / rank prefix and any icon or
    // spacer glyphs between "has" and the amount.
    private static final Pattern OTHER_BAL_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:\\[[^\\]]*\\]\\s*)?\\.?[a-zA-Z0-9_]{2,16}\\s+has[^0-9A-Za-z]{1,10}[0-9][0-9.,]*[kmb]?\\s*$");

    /** True if the line is someone else's balance (the reply to /bal <player>). */
    private static boolean isOtherBalLine(String text) {
        return OTHER_BAL_PATTERN.matcher(text).find();
    }

    /** True if the line looks like a /bal reply, per config.balPatterns (plus the built-in extra). */
    private boolean isBalLine(String text) {
        for (Pattern pattern : compiledBalPatterns) {
            Matcher m = pattern.matcher(text);
            if (m.find() && tryGroup(m, "amount") != null) return true;
        }
        return false;
    }

    /** True if the line is a whisper/DM (e.g. "X whispers to you: ..."), per config.whisperPatterns. */
    private boolean isWhisperLine(String text) {
        for (Pattern pattern : compiledWhisperPatterns) {
            if (pattern.matcher(text).find()) return true;
        }
        return false;
    }

    // --- Starting Bal: capture the baseline balance from a /bal response ---

    /**
     * Captures the starting balance from a /bal reply, but only once - right
     * after tracking has been started and we are waiting for it. A /bal at any
     * other time (including while already tracking) does nothing.
     */
    private void handleBalCapture(String text) {
        // /bal only does something ONCE: right after tracking is started and we are
        // waiting for it. Any /bal after that (while tracking, or any other time) does
        // nothing and is not added to the Start Bal box.
        // A /bal that follows an order settles what the order cost first.
        if (handleOrderBalReply(text)) return;

        if (!config.awaitingBalCapture) return;

        for (Pattern pattern : compiledBalPatterns) {
            Matcher m = pattern.matcher(text);
            if (!m.find()) continue;
            String amountStr = tryGroup(m, "amount");
            if (amountStr == null) continue;

            double amt = parseAmount(amountStr);
            Minecraft client = Minecraft.getInstance();

            // The real balance is only stored as the small "from $X" reference number.
            // The main Start Bal number (earnedTotal) starts at 0 and only moves with
            // payments from here on.
            config.startingBalance = amt;
            config.hasStartingBalance = true;
            Tutorial.event("BAL_CAPTURED");
            config.trackingEnabled = true;
            config.earnedTotal = 0;
            config.goalReachedNotified = false;
            config.awaitingBalCapture = false;
            config.save();
            DailyEarnings.markStart(config);

            if (client.player != null && config.startBalChatMessagesEnabled) {
                say(client.player, Component.literal(
                        "[DonutQueue] Starting balance captured: $" + formatAmount(amt)
                                + ". Start Bal begins at 0 and tracks earn/spend messages from here."));
            }
            return;
        }
    }

    /**
     * Some order-style purchases (e.g. "You ordered 1 Acacia Shelf") never show a
     * price in chat, so earnPatterns/spendPatterns have nothing to match. While
     * Starting Bal is tracking, an order line makes the mod ask for a /bal; the
     * next /bal reply (handleOrderBalReply) is compared with what Starting Bal
     * expected, and the difference is taken off (or added to) today's earnings.
     */
    private void handleOrderPrompt(String text) {
        if (!config.trackingEnabled || !config.hasStartingBalance || config.awaitingBalCapture) return;

        boolean isOrder = false;
        for (Pattern pattern : compiledOrderPatterns) {
            if (pattern.matcher(text).find()) {
                isOrder = true;
                break;
            }
        }
        if (!isOrder) return;

        config.awaitingOrderBalCapture = true;
        Minecraft client = Minecraft.getInstance();
        // Always shown (even with Chat Messages off) - without it you would not know a /bal is needed.
        if (client.player != null) {
            say(client.player, Component.literal(
                    "[DonutQueue] Order detected - run /bal so I can work out what it cost."));
        }
    }

    /** Handles the /bal reply that follows an order. Returns true if the line was used. */
    private boolean handleOrderBalReply(String text) {
        if (!config.awaitingOrderBalCapture) return false;
        if (!config.trackingEnabled || !config.hasStartingBalance) {
            config.awaitingOrderBalCapture = false; // tracking was reset/stopped meanwhile
            return false;
        }

        for (Pattern pattern : compiledBalPatterns) {
            Matcher m = pattern.matcher(text);
            if (!m.find()) continue;
            String amountStr = tryGroup(m, "amount");
            if (amountStr == null) continue;

            Minecraft client = Minecraft.getInstance();
            char last = amountStr.charAt(amountStr.length() - 1);
            if (Character.isLetter(last)) {
                // An abbreviated balance ("19.9M") is too rough to work out an exact cost from.
                if (client.player != null && config.startBalChatMessagesEnabled) {
                    say(client.player, Component.literal(
                            "[DonutQueue] That /bal is abbreviated, so I can't work out the order cost exactly."));
                }
                return true;
            }

            double actual = parseAmount(amountStr);
            // Payments tracked while waiting are already inside earnedTotal, so they are not double counted.
            double expected = config.startingBalance + config.earnedTotal;
            double diff = actual - expected;
            config.awaitingOrderBalCapture = false;

            if (Math.abs(diff) >= 0.005) {
                adjustEarned(diff); // takes it off (or adds it to) today's money and the Start Bal total
            }

            if (client.player != null && config.startBalChatMessagesEnabled) {
                String msg;
                if (diff < 0) {
                    msg = "[DonutQueue] Order cost $" + formatAmount(-diff) + " - taken off today's earnings.";
                } else if (diff > 0) {
                    msg = "[DonutQueue] Balance is $" + formatAmount(diff) + " higher than expected - added to today's earnings.";
                } else {
                    msg = "[DonutQueue] Balance matches - the order cost nothing extra.";
                }
                say(client.player, Component.literal(msg));
            }
            return true;
        }
        return false;
    }

    // --- Starting Bal earnings tracking: independent of the queue above ---

    private void handleEarnSpend(String text) {
        if (config.goalAmount <= 0 && !config.trackingEnabled) return; // neither goal nor plain tracking active

        for (Pattern pattern : compiledEarnPatterns) {
            Matcher m = pattern.matcher(text);
            if (!m.find()) continue;
            String amount = tryGroup(m, "amount");
            if (amount == null) continue;
            adjustEarned(parseAmount(amount));
            return; // only count a line once, even if it happens to match more than one list
        }

        for (Pattern pattern : compiledSpendPatterns) {
            Matcher m = pattern.matcher(text);
            if (!m.find()) continue;
            String amount = tryGroup(m, "amount");
            if (amount == null) continue;
            adjustEarned(-parseAmount(amount));
            return;
        }
    }

    /**
     * The (?<player>...) capture, keeping a leading "." (names like ".SpongySafe57623") even if
     * a custom pattern's name group doesn't include it. Throws IllegalArgumentException if the
     * pattern has no player group, like Matcher.group does.
     */
    private static String playerName(Matcher m, String text) {
        String name = m.group("player");
        if (name == null || name.isEmpty() || name.startsWith(".")) return name;
        int start = m.start("player");
        if (start >= 1 && text.charAt(start - 1) == '.'
                && (start == 1 || !Character.isLetterOrDigit(text.charAt(start - 2)))) {
            return "." + name;
        }
        return name;
    }

    private static String tryPlayer(Matcher m, String text) {
        try {
            return playerName(m, text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String tryGroup(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void adjustEarned(double delta) {
        config.earnedTotal += delta;
        config.save();
        DailyEarnings.record(delta, config);
    }

    private boolean isBlacklisted(String player) {
        for (String name : config.blacklist) {
            if (name.equalsIgnoreCase(player)) return true;
        }
        return false;
    }

    // --- Earnings leaderboard: net amount you've made from each player -----
    //
    // Independent of the Starting Bal goal/tracking above - this only turns
    // on/off via /earnings track and /earnings stop. Reuses earnPatterns AND
    // spendPatterns (the same detection Starting Bal uses) rather than a
    // third pair of pattern lists: an earn match (they paid you) adds to
    // that player's total, a spend match (you paid them) subtracts from it
    // - so paying someone back brings their number back down, same as a
    // real running balance with that person. Only counts a match that
    // actually captured a player name - the catch-all "line is just
    // $<amount>" earnPattern has no (?<player>...) group and is
    // deliberately skipped here.

    private void handleEarningsTracking(String text) {
        for (Pattern pattern : compiledEarnPatterns) {
            Matcher m = pattern.matcher(text);
            if (!m.find()) continue;
            String player = tryPlayer(m, text);
            String amount = tryGroup(m, "amount");
            if (player == null || player.isEmpty() || amount == null) continue;
            if (isBlacklisted(player)) return; // consistent with the main queue blacklist
            double value = parseAmount(amount);
            PayAlerts.onPaid(player, value);
            DailyEarnings.recordPlayer(player, value); // feeds "Top 7 today" (always on)
            if (config.earningsTrackingEnabled) addEarnings(player, value);
            return; // only count a line once, same as handleEarnSpend
        }

        for (Pattern pattern : compiledSpendPatterns) {
            Matcher m = pattern.matcher(text);
            if (!m.find()) continue;
            String player = tryPlayer(m, text);
            String amount = tryGroup(m, "amount");
            if (player == null || player.isEmpty() || amount == null) continue;
            if (isBlacklisted(player)) return;
            double value = -parseAmount(amount); // paying them back nets their total down
            DailyEarnings.recordPlayer(player, value);
            if (config.earningsTrackingEnabled) addEarnings(player, value);
            return;
        }
    }

    /** Adds delta (can be negative, e.g. paying someone back) to player's running total, matching an existing key case-insensitively. */
    private void addEarnings(String player, double delta) {
        if (delta == 0) return;
        for (String key : config.earningsTotals.keySet()) {
            if (key.equalsIgnoreCase(player)) {
                config.earningsTotals.put(key, config.earningsTotals.get(key) + delta);
                config.save();
                return;
            }
        }
        config.earningsTotals.put(player, delta);
        config.save();
    }

    /**
     * Highest-to-lowest, capped at limit. Only strictly-positive net totals -
     * someone you're at $0 or net negative with (you've paid them back at
     * least as much as they've paid you) drops off the board entirely rather
     * than showing a $0 or negative row. Their real running total (which can
     * be 0 or negative) is still kept in config.earningsTotals underneath,
     * so if they pay you again later it nets correctly from where it left off.
     */
    private static List<Map.Entry<String, Double>> topEarners(int limit) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>();
        for (Map.Entry<String, Double> e : config.earningsTotals.entrySet()) {
            if (e.getValue() > 0) entries.add(e);
        }
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        if (entries.size() > limit) {
            entries = entries.subList(0, limit);
        }
        return entries;
    }

    private static final int EARNINGS_BOX_WIDTH = 220;
    private static final int EARNINGS_BOX_HEIGHT = 92;

    /** Height of the Earnings box (it is a fixed-size graph now; the count is kept so callers don't change). */
    static int earningsBoxHeight(int count) {
        return EARNINGS_BOX_HEIGHT;
    }

    /** "$1.2M" / "-$1.2M" (compact=true) or "$500000" / "-$500000" (compact=false) - keeps the sign outside the $, avoiding "$-500000". */
    private static String formatMoneySigned(double value, boolean compact) {
        String magnitude = compact ? formatCompact(Math.abs(value)) : formatAmount(Math.abs(value));
        return (value < 0 ? "-$" : "$") + magnitude;
    }

    /**
     * Earnings box: a balance graph. The line starts in the MIDDLE of the box - that middle line is
     * your starting balance - and moves up when you earn and down when you spend. The scale is
     * symmetric around the starting balance, so the start always stays dead centre.
     */
    private void renderEarningsBox(GuiGraphicsExtractor guiGraphics) {
        if (!config.earningsBoxEnabled) return;
        drawBalanceGraph(guiGraphics, config.earningsBoxX, config.earningsBoxY, EARNINGS_BOX_WIDTH, EARNINGS_BOX_HEIGHT,
                "Balance", config.earningsBoxColor, config.earningsBoxColor, 0, false);
    }

    /**
     * Shared by the Earnings and Starting Bal boxes: a balance graph. The line starts in the MIDDLE
     * (that middle line is your starting balance) and moves up when you earn, down when you spend.
     * If goal > 0 a green line marks the goal and the scale stretches to fit it.
     */
    private void drawBalanceGraph(GuiGraphicsExtractor guiGraphics, int x, int y, int boxWidth, int boxHeight,
                                  String title, int titleColor, int lineColor, double goal, boolean done) {
        Minecraft client = Minecraft.getInstance();

        guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, 0xA0000000);
        Gfx.outline(guiGraphics, x, y, boxWidth, boxHeight, lineColor);

        guiGraphics.text(client.font, title, x + 6, y + 6, done ? 0xFF33FF33 : titleColor, true);

        // plot area (value labels live in the column on the right)
        int labelW = 52;
        int gx = x + 6;
        int gy = y + 20;
        int gw = boxWidth - 12 - labelW;
        int gh = boxHeight - 20 - 6;
        int midY = gy + gh / 2;

        guiGraphics.fill(gx, gy, gx + gw, gy + gh, 0x66000000);
        Gfx.outline(guiGraphics, gx, gy, gw, gh, 0x55FFFFFF);

        double[] run = DailyEarnings.runSeries();
        boolean ready = run.length > 0;
        // the middle line = starting balance
        guiGraphics.fill(gx + 1, midY, gx + gw - 1, midY + 1, 0xAA9A8B5F);

        if (!ready) {
            String msg = config.awaitingBalCapture ? "Run /bal in chat..." : "Start tracking to see the graph";
            guiGraphics.text(client.font, msg, gx + 4, gy + 4, 0xFFAAAAAA, true);
            return;
        }

        double base = config.hasStartingBalance ? config.startingBalance : 0;
        double maxDev = 1;
        for (double v : run) maxDev = Math.max(maxDev, Math.abs(v));
        if (goal > 0) maxDev = Math.max(maxDev, goal);
        maxDev *= 1.15; // a little headroom

        double current = run[run.length - 1];
        int curColor = current >= 0 ? 0xFF55FF55 : 0xFFFF5555;

        // current balance + change, top right of the title row
        String balText = "$" + formatCompact(base + current);
        String diffText = " (" + (current >= 0 ? "+" : "-") + "$" + formatCompact(Math.abs(current)) + ")";
        int diffW = client.font.width(diffText);
        int balW = client.font.width(balText);
        guiGraphics.text(client.font, diffText, x + boxWidth - 6 - diffW, y + 6, curColor, true);
        guiGraphics.text(client.font, balText, x + boxWidth - 6 - diffW - balW, y + 6, 0xFFFFFFFF, true);

        // axis labels: top = starting balance + range, middle = starting balance, bottom = start - range
        int lx = gx + gw + 4;
        guiGraphics.text(client.font, "$" + formatCompact(base + maxDev), lx, gy, 0xFF888888, true);
        guiGraphics.text(client.font, "$" + formatCompact(base), lx, midY - 4, 0xFFCCCCAA, true);
        guiGraphics.text(client.font, "$" + formatCompact(Math.max(0, base - maxDev)), lx, gy + gh - 8, 0xFF888888, true);

        int half0 = gh / 2 - 2;
        if (goal > 0) {
            int gyl = midY - (int) Math.round(goal / maxDev * half0);
            guiGraphics.fill(gx + 1, gyl, gx + gw - 1, gyl + 1, 0xAA33FF33);
        }

        int n = run.length;
        int half = gh / 2 - 2;
        int prevX = 0, prevY = 0;
        for (int i = 0; i < n; i++) {
            int px = gx + 1 + (n == 1 ? 0 : (int) Math.round(i * (gw - 3) / (double) (n - 1)));
            int py = midY - (int) Math.round(run[i] / maxDev * half);
            if (i > 0) {
                for (int sx = prevX; sx <= px; sx++) {
                    double t = px == prevX ? 1.0 : (sx - prevX) / (double) (px - prevX);
                    int ly = (int) Math.round(prevY + (py - prevY) * t);
                    guiGraphics.fill(sx, ly, sx + 1, ly + 2, lineColor);
                }
            }
            prevX = px;
            prevY = py;
        }
        // dot on the latest value
        guiGraphics.fill(prevX - 1, prevY - 1, prevX + 2, prevY + 3, 0xFFFFFFFF);
    }

    // --- Bidding: highest payment during a timed window wins ---------------

    private void handleBiddingPayment(String player, String amount) {
        if (!config.biddingActive) return;
        if (System.currentTimeMillis() >= config.biddingEndTimeMillis) return; // render loop will close it out
        for (String name : config.biddingBlacklist) {
            if (name.equalsIgnoreCase(player)) return;
        }
        double amt = parseAmount(amount);
        if (amt <= 0) return;
        if (config.biddingMinimum > 0 && amt < config.biddingMinimum) return; // below the minimum bid
        if (amt > config.biddingLeaderAmount) {
            config.biddingLeaderAmount = amt;
            config.biddingLeaderName = player;
            config.save();
            Tutorial.event("BID_PAYMENT");
        }
    }

    private void renderBiddingBox(GuiGraphicsExtractor guiGraphics) {
        if (!config.biddingBoxEnabled) return;
        Minecraft client = Minecraft.getInstance();

        // Close out a finished round the first frame we notice time is up.
        if (config.biddingActive && System.currentTimeMillis() >= config.biddingEndTimeMillis) {
            config.biddingActive = false;
            config.biddingFinished = true;
            config.save();
        }

        int x = config.biddingBoxX;
        int y = config.biddingBoxY;
        int boxWidth = 270;
        int boxHeight = 70;

        guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, 0xA0000000);
        Gfx.outline(guiGraphics, x, y, boxWidth, boxHeight, config.biddingBoxColor);

        // Avatar + countdown ring sit in the lower-left of the box; the
        // leader's name/amount sit to the right of that.
        int avatarSize = 24;
        int centerX = x + 18 + avatarSize / 2;
        int centerY = y + 46;
        int ringRadius = avatarSize / 2 + 6;
        int textX = x + 18 + avatarSize + 14;

        if (config.biddingActive) {
            long remainingMs = Math.max(0, config.biddingEndTimeMillis - System.currentTimeMillis());
            long totalSeconds = remainingMs / 1000;
            String time = (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
            guiGraphics.text(client.font, "Bidding - " + time + " left", x + 8, y + 6, config.biddingBoxColor, true);
            if (config.biddingMinimum > 0) {
                String minText = "Min $" + formatCompact(config.biddingMinimum);
                guiGraphics.text(client.font, minText, x + boxWidth - client.font.width(minText) - 8, y + 6, 0xFFCCCCCC, true);
            }

            double remainingFraction = config.biddingTotalDurationMillis > 0
                    ? remainingMs / (double) config.biddingTotalDurationMillis
                    : 0.0;
            renderCountdownRing(guiGraphics, centerX, centerY, ringRadius, remainingFraction, config.biddingBoxColor);
            renderPlayerHead(guiGraphics, config.biddingLeaderName, centerX - avatarSize / 2, centerY - avatarSize / 2, avatarSize);

            if (config.biddingLeaderName == null) {
                guiGraphics.text(client.font, "No bids yet", textX, centerY - 4, 0xFFAAAAAA, true);
            } else {
                guiGraphics.text(client.font, config.biddingLeaderName, textX, centerY - 10, 0xFF5588FF, true);
                guiGraphics.text(client.font, "$" + formatCompact(config.biddingLeaderAmount), textX, centerY + 1, 0xFF55FF55, true);
            }
        } else if (config.biddingFinished) {
            guiGraphics.text(client.font, "Bidding Ended!", x + 8, y + 6, config.biddingBoxColor, true);

            renderPlayerHead(guiGraphics, config.biddingLeaderName, centerX - avatarSize / 2, centerY - avatarSize / 2, avatarSize);
            Gfx.outline(guiGraphics, centerX - ringRadius, centerY - ringRadius, ringRadius * 2, ringRadius * 2, 0xFF444444);

            if (config.biddingLeaderName == null) {
                guiGraphics.text(client.font, "No bids received", textX, centerY - 4, 0xFFAAAAAA, true);
            } else {
                guiGraphics.text(client.font, "Winner:", textX, centerY - 10, 0xFFCCCCCC, true);
                guiGraphics.text(client.font, config.biddingLeaderName, textX, centerY + 1, 0xFF5588FF, true);
                guiGraphics.text(client.font, " ($" + formatCompact(config.biddingLeaderAmount) + ")",
                        textX + client.font.width(config.biddingLeaderName), centerY + 1, 0xFF55FF55, true);
            }
        } else {
            guiGraphics.text(client.font, "Bidding", x + 8, y + 6, config.biddingBoxColor, true);
            guiGraphics.text(client.font, "No bidding running", x + 8, y + 22, 0xFFAAAAAA, true);
            guiGraphics.text(client.font, "/bidding <preset number>", x + 8, y + 33, 0xFFAAAAAA, true);
        }

        if (config.biddingActive || config.biddingFinished) {
            renderAuctionItem(guiGraphics, client, x, y, boxWidth);
        }
    }

    /** Item being auctioned (icon + "64x Diamond") in the right-hand part of the Bidding box. */
    private void renderAuctionItem(GuiGraphicsExtractor guiGraphics, Minecraft client, int x, int y, int boxWidth) {
        if (config.biddingItemId == null || config.biddingItemId.isEmpty() || config.biddingItemCount <= 0) return;
        net.minecraft.world.item.ItemStack stack = Auction.stackFor(config.biddingItemId);
        if (stack.isEmpty()) return;
        String label = config.biddingItemCount + "x " + stack.getHoverName().getString();
        int maxW = 120;
        while (label.length() > 1 && client.font.width(label) > maxW) label = label.substring(0, label.length() - 1);
        int iconX = x + boxWidth - 8 - 16;
        int iconY = y + 22;
        guiGraphics.item(stack, iconX, iconY);
        guiGraphics.text(client.font, label, iconX - 4 - client.font.width(label), iconY + 4, 0xFFFFFFFF, true);
    }

    // --- Info Display: freeform IGN/min/max/game/custom text box -----------

    private void renderInfoBox(GuiGraphicsExtractor guiGraphics) {
        if (!config.infoBoxEnabled) return;
        Minecraft client = Minecraft.getInstance();

        // Fixed labels always show, in this order, whether or not a value
        // has been set - an unset value just shows "-" instead of the line
        // disappearing.
        String[][] rows = {
                {"IGN: ", config.infoIgn.isBlank() ? "-" : config.infoIgn},
                {"Min: ", config.infoMin.isBlank() ? "-" : config.infoMin},
                {"Max: ", config.infoMax.isBlank() ? "-" : config.infoMax},
                {"Game: ", config.infoGame.isBlank() ? "-" : config.infoGame}
        };

        int lineHeight = 11;
        int localWidth = 190;
        int localHeight = 10 + (rows.length + 1) * lineHeight; // +1 for the custom text line
        float scale = ModConfig.clampScale(config.infoBoxScale);

        // Scale the whole box (background, border, and every line of text)
        // together by wrapping all drawing in the same transform.
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(config.infoBoxX, config.infoBoxY);
        guiGraphics.pose().scale(scale, scale);

        guiGraphics.fill(0, 0, localWidth, localHeight, 0xA0000000);
        Gfx.outline(guiGraphics, 0, 0, localWidth, localHeight, config.infoBoxColor);

        int ly = 6;
        int labelColor = 0xFFAAAAAA;
        for (String[] row : rows) {
            String label = row[0];
            String value = row[1];
            guiGraphics.text(client.font, label, 6, ly, labelColor, true);
            int labelWidth = client.font.width(label);
            guiGraphics.text(client.font, value, 6 + labelWidth, ly, config.infoBoxColor, true);
            ly += lineHeight;
        }

        // Custom text line: shows the real text if set, otherwise a dim
        // placeholder hint (not real saved data, just a visual cue that
        // this line is available).
        if (!config.infoCustomText.isBlank()) {
            guiGraphics.text(client.font, config.infoCustomText, 6, ly, config.infoBoxColor, true);
        } else {
            guiGraphics.text(client.font, "Any additional info", 6, ly, 0xFF888888, true);
        }

        guiGraphics.pose().popMatrix();
    }

    /** Parses "30s", "5m", "1h", or a plain number (seconds) into a duration in seconds. Returns 0 if unparsable. */
    static long parseDurationSeconds(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        String cleaned = raw.trim().toLowerCase();
        char suffix = cleaned.charAt(cleaned.length() - 1);
        long multiplier = 1;
        String numPart = cleaned;
        if (suffix == 's' || suffix == 'm' || suffix == 'h') {
            numPart = cleaned.substring(0, cleaned.length() - 1);
            multiplier = suffix == 's' ? 1 : suffix == 'm' ? 60 : 3600;
        }
        try {
            return (long) (Double.parseDouble(numPart) * multiplier);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Parses "50", "50k", "1.5m", "2b", "1,000" etc. into a plain number. Returns 0 if unparsable. */
    static double parseAmount(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("?")) return 0;
        String cleaned = raw.replace(",", "").trim();
        if (cleaned.isEmpty()) return 0;
        char suffix = Character.toLowerCase(cleaned.charAt(cleaned.length() - 1));
        double multiplier = 1;
        if (suffix == 'k' || suffix == 'm' || suffix == 'b') {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
            multiplier = suffix == 'k' ? 1_000 : suffix == 'm' ? 1_000_000 : 1_000_000_000;
        }
        try {
            return Double.parseDouble(cleaned) * multiplier;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String formatAmount(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        // Rounded to 2 decimal places (String.format handles the sign
        // correctly on its own, so this works the same for negatives) -
        // Double's raw toString() could otherwise print a dozen+ digits.
        return String.format("%.2f", value);
    }

    // --- Player head icons (shared by the queue box and the bidding box) --
    //
    // Looking a player up in our own tab list only works if they're
    // currently connected on the exact same server/shard we are - on a big
    // network, someone whose payment we saw in chat might be on a totally
    // different sub-server and simply won't be in our tab list. So: try the
    // tab list first (instant, free, works offline), and if that comes up
    // empty, fetch the skin straight from Mojang in the background and
    // cache it. On 26.1 only the Mojang path is used, drawn via blitFace().

    private static final HttpClient SKIN_HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Map<String, Identifier> skinTextureCache = new ConcurrentHashMap<>();
    private static final Set<String> skinLookupInFlight = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> skinLookupFailedAt = new ConcurrentHashMap<>();
    private static final long SKIN_RETRY_COOLDOWN_MILLIS = 60_000L;

    /** Draws a player's face at (x, y), sized size x size pixels. */
    private void renderPlayerHead(GuiGraphicsExtractor guiGraphics, String playerName, int x, int y, int size) {
        if (playerName == null) {
            drawPlaceholderHead(guiGraphics, x, y, size);
            return;
        }

        // 26.1 build: the skin comes from Mojang and is cached (or the
        // placeholder shows while that's in flight / if it's already failed).
        // The tab-list shortcut used PlayerFaceRenderer, which was reworked in 26.1.
        String key = playerName.toLowerCase(Locale.ROOT);
        Identifier cached = skinTextureCache.get(key);
        if (cached != null) {
            blitFace(guiGraphics, cached, x, y, size);
            return;
        }

        Long failedAt = skinLookupFailedAt.get(key);
        boolean readyToRetry = failedAt == null || System.currentTimeMillis() - failedAt > SKIN_RETRY_COOLDOWN_MILLIS;
        if (readyToRetry) {
            requestSkinAsync(playerName, key);
        }
        drawPlaceholderHead(guiGraphics, x, y, size);
    }

    static void drawSpinningSkin(GuiGraphicsExtractor guiGraphics, String playerName, int centerX, int top, int height, float angle) {
        if (instance != null) instance.renderSpinningSkin(guiGraphics, playerName, centerX, top, height, angle);
    }

    // Skin boxes: texture origin u,v | size w,h,d | centre x, top y (model space, 1 unit = 1 skin texel, y down)
    // | second-layer origin u,v | second-layer inflation.  Model x grows toward the player's LEFT side.
    private static final double[][] SKIN_PARTS = {
            {0, 0, 8, 8, 8, 0, 0, 32, 0, 0.5},      // head + hat
            {16, 16, 8, 12, 4, 0, 8, 16, 32, 0.25}, // body + jacket
            {40, 16, 4, 12, 4, -6, 8, 40, 32, 0.25}, // right arm + sleeve
            {32, 48, 4, 12, 4, 6, 8, 48, 48, 0.25},  // left arm + sleeve
            {0, 16, 4, 12, 4, -2, 20, 0, 32, 0.25},  // right leg + pants
            {16, 48, 4, 12, 4, 2, 20, 0, 48, 0.25}   // left leg + pants
    };

    /**
     * A real 3D turntable of the skin: six boxes (head, body, arms, legs, plus the second skin layer) turned around
     * the vertical axis and seen from the side-on (orthographic), so every face is just a horizontally squashed copy of
     * its skin texture. Far parts are drawn first. Uses the same 64x64 skin texture as the head.
     */
    private void renderSpinningSkin(GuiGraphicsExtractor g, String playerName, int centerX, int top, int height, float angle) {
        Identifier tex = null;
        if (playerName != null) {
            String key = playerName.toLowerCase(Locale.ROOT);
            tex = skinTextureCache.get(key);
            if (tex == null) {
                Long failedAt = skinLookupFailedAt.get(key);
                if (failedAt == null || System.currentTimeMillis() - failedAt > SKIN_RETRY_COOLDOWN_MILLIS) {
                    requestSkinAsync(playerName, key);
                }
            }
        }
        if (tex == null) {
            int ph = Math.max(16, Math.min(48, height / 2));
            drawPlaceholderHead(g, centerX - ph / 2, top + Math.max(0, height / 2 - ph / 2), ph);
            return;
        }
        final double cosA = Math.cos(angle);
        final double sinA = Math.sin(angle);
        final float k = height / 33.0f;
        final float topY = top + 0.5f * k;

        // far parts first: depth of a part's centre is -x * sin(angle)
        Integer[] order = {0, 1, 2, 3, 4, 5};
        java.util.Arrays.sort(order, (i, j) -> Double.compare(-SKIN_PARTS[i][5] * sinA, -SKIN_PARTS[j][5] * sinA));
        for (int idx : order) {
            double[] p = SKIN_PARTS[idx];
            skinBox(g, tex, (int) p[0], (int) p[1], (int) p[2], (int) p[3], (int) p[4], p[5], p[6], 0.0, cosA, sinA, centerX, topY, k);
            skinBox(g, tex, (int) p[7], (int) p[8], (int) p[2], (int) p[3], (int) p[4], p[5], p[6], p[9], cosA, sinA, centerX, topY, k);
        }
    }

    private static void skinBox(GuiGraphicsExtractor g, Identifier tex, int u0, int v0, int w, int h, int d,
                                double cx, double topY, double infl, double cosA, double sinA,
                                float centerX, float screenTop, float k) {
        double hw = w / 2.0 + infl;
        double hd = d / 2.0 + infl;
        double y = topY - infl;
        double ht = h + 2 * infl;
        int vv = v0 + d;
        if (cosA > 0.001) { // front faces the viewer
            skinFace(g, tex, u0 + d, vv, w, h, cx * cosA + hd * sinA, hw * cosA, y, ht, centerX, screenTop, k);
        } else if (cosA < -0.001) { // back faces the viewer
            skinFace(g, tex, u0 + 2 * d + w, vv, w, h, cx * cosA - hd * sinA, hw * -cosA, y, ht, centerX, screenTop, k);
        }
        if (sinA < -0.001) { // the player's left side (model +x) faces the viewer
            skinFace(g, tex, u0 + d + w, vv, d, h, (cx + hw) * cosA, hd * -sinA, y, ht, centerX, screenTop, k);
        } else if (sinA > 0.001) { // the player's right side (model -x) faces the viewer
            skinFace(g, tex, u0, vv, d, h, (cx - hw) * cosA, hd * sinA, y, ht, centerX, screenTop, k);
        }
    }

    private static void skinFace(GuiGraphicsExtractor g, Identifier tex, int u, int v, int rw, int rh,
                                 double centerX, double halfW, double topY, double height,
                                 float screenCenterX, float screenTop, float k) {
        float left = screenCenterX + (float) ((centerX - halfW) * k);
        float sx = (float) (2.0 * halfW * k) / rw;
        float sy = (float) (height * k) / rh;
        if (sx <= 0.0001f) return;
        g.pose().pushMatrix();
        g.pose().translate(left, screenTop + (float) (topY * k));
        g.pose().scale(sx, sy);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, (float) u, (float) v, rw, rh, rw, rh, 64, 64);
        g.pose().popMatrix();
    }

    private void drawPlaceholderHead(GuiGraphicsExtractor guiGraphics, int x, int y, int size) {
        guiGraphics.fill(x, y, x + size, y + size, 0xFF2B2B2B);
        Gfx.outline(guiGraphics, x, y, size, size, 0xFF666666);
    }

    /** Draws the face + hat-overlay regions of a 64x64 skin texture, scaled to size x size. */
    private void blitFace(GuiGraphicsExtractor guiGraphics, Identifier texture, int x, int y, int size) {
        // base face: pixels (8,8)-(16,16) of the 64x64 skin
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64);
        // hat overlay: pixels (40,8)-(48,16)
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 40.0f, 8.0f, size, size, 8, 8, 64, 64);
    }

    /** Looks a username up via Mojang's public API, downloads their skin, and registers it as a texture. Runs off-thread. */
    private void requestSkinAsync(String playerName, String key) {
        if (!skinLookupInFlight.add(key)) return; // already fetching this one

        Thread thread = new Thread(() -> {
            try {
                // Bedrock players (Floodgate/Geyser) show up with a leading "." (".SpongySafe57623")
                // and have no Mojang account, so they go through the GeyserMC skin API instead.
                String skinUrl = isBedrockName(playerName)
                        ? lookupBedrockSkinUrl(playerName)
                        : lookupJavaSkinUrl(playerName);
                if (skinUrl == null) {
                    markFailed(key);
                    return;
                }

                HttpRequest imageReq = HttpRequest.newBuilder(URI.create(skinUrl))
                        .timeout(Duration.ofSeconds(5)).build();
                HttpResponse<byte[]> imageResp = SKIN_HTTP_CLIENT.send(imageReq, HttpResponse.BodyHandlers.ofByteArray());
                if (imageResp.statusCode() != 200) {
                    markFailed(key);
                    return;
                }

                NativeImage image = NativeImage.read(new ByteArrayInputStream(imageResp.body()));

                // Texture uploads have to happen on the render thread, not this background thread.
                Minecraft.getInstance().execute(() -> {
                    DynamicTexture texture = new DynamicTexture(() -> "donutqueue-skin-" + key, image);
                    Identifier id = Identifier.fromNamespaceAndPath("donutqueue",
                            "fetchedskin/" + key.replaceAll("[^a-z0-9_.-]", "_"));
                    Minecraft.getInstance().getTextureManager().register(id, texture);
                    skinTextureCache.put(key, id);
                    skinLookupInFlight.remove(key);
                });
            } catch (Exception e) {
                markFailed(key);
            }
        }, "DonutQueue-SkinFetch-" + key);
        thread.setDaemon(true);
        thread.start();
    }

    /** Bedrock (Floodgate) names start with a symbol, usually ".". */
    static boolean isBedrockName(String name) {
        if (name == null || name.isEmpty()) return false;
        char c = name.charAt(0);
        return !(Character.isLetterOrDigit(c) || c == '_');
    }

    /** Java players: username -> uuid -> session profile -> skin url. Null if not found. */
    private static String lookupJavaSkinUrl(String playerName) throws Exception {
        String encodedName = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
        HttpRequest uuidReq = HttpRequest.newBuilder(URI.create(
                        "https://api.mojang.com/users/profiles/minecraft/" + encodedName))
                .timeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> uuidResp = SKIN_HTTP_CLIENT.send(uuidReq, HttpResponse.BodyHandlers.ofString());
        if (uuidResp.statusCode() != 200) return null;
        String uuid = JsonParser.parseString(uuidResp.body()).getAsJsonObject().get("id").getAsString();

        HttpRequest profileReq = HttpRequest.newBuilder(URI.create(
                        "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid))
                .timeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> profileResp = SKIN_HTTP_CLIENT.send(profileReq, HttpResponse.BodyHandlers.ofString());
        if (profileResp.statusCode() != 200) return null;
        JsonObject profileJson = JsonParser.parseString(profileResp.body()).getAsJsonObject();
        String texturesB64 = profileJson.getAsJsonArray("properties").get(0)
                .getAsJsonObject().get("value").getAsString();
        String texturesJson = new String(Base64.getDecoder().decode(texturesB64), StandardCharsets.UTF_8);
        JsonObject skinObj = JsonParser.parseString(texturesJson).getAsJsonObject()
                .getAsJsonObject("textures").getAsJsonObject("SKIN");
        return skinObj == null ? null : skinObj.get("url").getAsString();
    }

    /**
     * Bedrock players: gamertag -> xuid -> skin, both from api.geysermc.org (the skin they
     * uploaded to the Geyser global API). Floodgate swaps spaces in a gamertag for "_", so
     * both spellings are tried. Null if not found.
     */
    private static String lookupBedrockSkinUrl(String playerName) throws Exception {
        String tag = playerName.replaceFirst("^[^A-Za-z0-9_]+", "");
        if (tag.isEmpty()) return null;
        String[] spellings = tag.indexOf('_') >= 0
                ? new String[]{tag, tag.replace('_', ' ')}
                : new String[]{tag};
        for (String spelling : spellings) {
            HttpRequest xuidReq = HttpRequest.newBuilder(URI.create(
                            "https://api.geysermc.org/v2/xbox/xuid/"
                                    + URLEncoder.encode(spelling, StandardCharsets.UTF_8).replace("+", "%20")))
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> xuidResp = SKIN_HTTP_CLIENT.send(xuidReq, HttpResponse.BodyHandlers.ofString());
            if (xuidResp.statusCode() != 200) continue;
            JsonObject xuidJson = JsonParser.parseString(xuidResp.body()).getAsJsonObject();
            if (!xuidJson.has("xuid")) continue;
            String xuid = xuidJson.get("xuid").getAsString();

            HttpRequest skinReq = HttpRequest.newBuilder(URI.create(
                            "https://api.geysermc.org/v2/skin/" + xuid))
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> skinResp = SKIN_HTTP_CLIENT.send(skinReq, HttpResponse.BodyHandlers.ofString());
            if (skinResp.statusCode() != 200) continue;
            JsonObject skinJson = JsonParser.parseString(skinResp.body()).getAsJsonObject();
            if (skinJson.has("texture_id") && !skinJson.get("texture_id").isJsonNull()) {
                return "https://textures.minecraft.net/texture/" + skinJson.get("texture_id").getAsString();
            }
        }
        return null;
    }

    private void markFailed(String key) {
        skinLookupFailedAt.put(key, System.currentTimeMillis());
        skinLookupInFlight.remove(key);
    }

    /**
     * Draws a ring of small tick marks around (centerX, centerY) that
     * empties clockwise from the top as remainingFraction drops from 1.0 to
     * 0.0 - ticks past that point in the sweep just aren't drawn, so the
     * ring visibly shrinks piece by piece instead of only changing color.
     */
    private void renderCountdownRing(GuiGraphicsExtractor guiGraphics, int centerX, int centerY, int radius,
                                      double remainingFraction, int color) {
        int tickCount = 24;
        double clamped = Math.max(0.0, Math.min(1.0, remainingFraction));
        int visibleTicks = (int) Math.ceil(tickCount * clamped);

        for (int i = 0; i < visibleTicks; i++) {
            double angle = (Math.PI * 2 * i / tickCount) - (Math.PI / 2); // 0 = 12 o'clock, sweeps clockwise
            int tx = centerX + (int) Math.round(Math.cos(angle) * radius);
            int ty = centerY + (int) Math.round(Math.sin(angle) * radius);
            guiGraphics.fill(tx - 1, ty - 1, tx + 2, ty + 2, color);
        }
    }

    // --- HUD: shows only the person at the front of the line --------------

    private void registerHud() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("donutqueue", "hud"),
                (guiGraphics, tickCounter) -> {
                    scaled(guiGraphics, config.hudX, config.hudY, config.hudScale, () -> renderHud(guiGraphics));
                    scaled(guiGraphics, config.startBalBoxX, config.startBalBoxY, config.startBalBoxScale,
                            () -> renderStartBalBox(guiGraphics));
                    scaled(guiGraphics, config.biddingBoxX, config.biddingBoxY, config.biddingBoxScale,
                            () -> renderBiddingBox(guiGraphics));
                    renderInfoBox(guiGraphics); // has its own scaling
                    scaled(guiGraphics, config.earningsBoxX, config.earningsBoxY, config.earningsBoxScale,
                            () -> renderEarningsBox(guiGraphics));
                    renderLiveSession(guiGraphics);
                    ScreenFeatures.render(guiGraphics);
                    PayAlerts.render(guiGraphics);
                    renderCelebration(guiGraphics);
                    Minecraft mcHud = Minecraft.getInstance();
                    if (mcHud.gui.screen() == null) Tutorial.renderOverlay(guiGraphics, mcHud);
                });
    }

    /** The Live pill while a session is running (position: config.liveBoxPos; default top-middle): "Live│+$1.2M" (how much you are up). */
    private void renderLiveSession(GuiGraphicsExtractor guiGraphics) {
        Sessions.Session live = Sessions.running();
        if (live == null) return;
        Minecraft client = Minecraft.getInstance();
        double net = Sessions.net(live);
        String amount = net > 0 ? "+$" + formatCompact(net) : (net < 0 ? "-$" + formatCompact(-net) : "$0");
        int amountColor = net > 0 ? 0xFF55FF55 : (net < 0 ? 0xFFFF5555 : 0xFFFFFFFF);
        String liveText = "Live";
        String bar = "\u2502";
        int total = client.font.width(liveText) + client.font.width(bar) + client.font.width(amount) + 18;
        int h = 17;
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        int x;
        int y = 6;
        switch (config.liveBoxPos) {
            case 1 -> x = 6;                       // top left
            case 2 -> x = sw - total - 6;          // top right
            case 3 -> { x = (sw - total) / 2; y = sh - 78; } // above the hotbar, middle (clear of hearts / armor)
            default -> x = (sw - total) / 2;       // top middle
        }
        // smooth pill: coloured edge (config.liveBoxColor), dark tint of that colour inside
        int base = 0xFF000000 | (config.liveBoxColor & 0xFFFFFF);
        int glow = 0x66000000 | (base & 0xFFFFFF);
        int dark = scaleRgb(base, 0.12f);
        int light = mixWhite(base, 0.35f);
        roundedFill(guiGraphics, x - 1, y - 1, total + 2, h + 2, (h + 2) / 2, glow); // soft glow
        roundedFill(guiGraphics, x, y, total, h, h / 2, base);
        roundedFill(guiGraphics, x + 1, y + 1, total - 2, h - 2, (h - 2) / 2, dark);
        int tx = x + 9;
        guiGraphics.text(client.font, liveText, tx, y + 5, light, true);
        tx += client.font.width(liveText);
        guiGraphics.text(client.font, bar, tx, y + 5, base, true);
        tx += client.font.width(bar);
        guiGraphics.text(client.font, amount, tx, y + 5, amountColor, true);
    }

    /** Multiplies the RGB channels of an ARGB colour by f (keeps it opaque). */
    private static int scaleRgb(int argb, float f) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * f));
        int gr = Math.min(255, Math.round(((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((argb & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    /** Blends an ARGB colour towards white by t (0 = unchanged, 1 = white). */
    private static int mixWhite(int argb, float t) {
        int r = Math.round(((argb >> 16) & 0xFF) + (255 - ((argb >> 16) & 0xFF)) * t);
        int gr = Math.round(((argb >> 8) & 0xFF) + (255 - ((argb >> 8) & 0xFF)) * t);
        int b = Math.round((argb & 0xFF) + (255 - (argb & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    /** Filled rectangle with smoothly rounded (circular) corners of radius r. */
    private static void roundedFill(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            int d = Math.min(row, h - 1 - row);
            int inset = 0;
            if (d < r) {
                double dy = r - d - 0.5;
                inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            }
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    /**
     * Runs a box's normal drawing code scaled around the box's top-left corner (x, y), so the
     * box grows/shrinks in place without its drawing code having to know about the size setting.
     */
    private static void scaled(GuiGraphicsExtractor g, int x, int y, float scale, Runnable draw) {
        float s = ModConfig.clampScale(scale);
        if (Math.abs(s - 1.0f) < 0.01f) {
            draw.run();
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(s, s);
        g.pose().translate((float) -x, (float) -y);
        try {
            draw.run();
        } finally {
            g.pose().popMatrix();
        }
    }

    /** A box dimension multiplied by its size setting (used by the box mover so its preview matches). */
    static int scaledSize(int size, float scale) {
        return Math.max(1, Math.round(size * ModConfig.clampScale(scale)));
    }

    /** Height of the Donut Queue box - also used by the box mover's preview so it matches. */
    static final int QUEUE_BOX_HEIGHT = 60;

    private void renderHud(GuiGraphicsExtractor guiGraphics) {
        if (!config.hudEnabled) return;

        Minecraft client = Minecraft.getInstance();
        QueueEntry first = queue.peekNext();
        int remaining = queue.size();

        int x = config.hudX;
        int y = config.hudY;
        int boxWidth = 190;
        int boxHeight = QUEUE_BOX_HEIGHT;

        guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, 0xA0000000);
        Gfx.outline(guiGraphics, x, y, boxWidth, boxHeight, config.queueBoxColor);

        // bottom-left: the last gamble that was skipped (the last person served), who and how much
        {
            List<QueueEntry> lastDone = queue.getHistory(1);
            String skipped = lastDone.isEmpty()
                    ? "Last bet: none yet"
                    : "Last bet: " + lastDone.get(0).playerName + " $" + lastDone.get(0).amount;
            int maxW = boxWidth - 12;
            while (skipped.length() > 4 && client.font.width(skipped) > maxW) {
                skipped = skipped.substring(0, skipped.length() - 1);
            }
            guiGraphics.fill(x + 4, y + boxHeight - 14, x + boxWidth - 4, y + boxHeight - 13, 0x33FFFFFF);
            guiGraphics.text(client.font, skipped, x + 6, y + boxHeight - 11, config.queueBoxColor, true);
        }

        String header = "Donut Queue" + (remaining > 1 ? " (+" + (remaining - 1) + " waiting)" : "");
        guiGraphics.text(client.font, header, x + 6, y + 6, config.queueBoxColor, true);

        // With Queue chat messages off, the "Lever skip PAUSED/resumed" line
        // that normally confirms a lever toggle in chat never shows - put
        // the same status here in the corner instead so it's not invisible.
        {
            String leverStatus = "Lever: " + (config.leverPaused ? "PAUSED" : "ON");
            int leverColor = config.leverPaused ? 0xFFFF5555 : 0xFF55FF55;
            int leverWidth = client.font.width(leverStatus);
            guiGraphics.text(client.font, leverStatus, x + boxWidth - leverWidth - 6, y + 6, leverColor, true);
        }

        if (first == null) {
            guiGraphics.text(client.font, "Empty - waiting for payments", x + 6, y + 22, 0xFFAAAAAA, true);
            return;
        }

        // Skin icon on the left, name + amount stacked to the right of it -
        // this is the "whose turn is it" row.
        int avatarSize = 20;
        int avatarX = x + 8;
        int avatarY = y + 22;
        renderPlayerHead(guiGraphics, first.playerName, avatarX, avatarY, avatarSize);

        int textX = avatarX + avatarSize + 8;
        String turnLabel = "Turn: ";
        guiGraphics.text(client.font, turnLabel, textX, avatarY + 2, 0xFFCCCCCC, true);
        guiGraphics.text(client.font, first.playerName, textX + client.font.width(turnLabel), avatarY + 2, 0xFF5588FF, true);
        String paidLabel = "Paid: ";
        guiGraphics.text(client.font, paidLabel, textX, avatarY + 12, 0xFFCCCCCC, true);
        guiGraphics.text(client.font, "$" + first.amount, textX + client.font.width(paidLabel), avatarY + 12, 0xFF55FF55, true);
    }

    // --- Starting Bal: tracks money earned toward a goal since a captured balance ---

    // Shared box dimensions - also used by StartBalConfigScreen's "Move box"
    // preview so the drag preview matches what actually renders. Sized for
    // the widest case (ring showing); the plain "no goal yet" state renders
    // narrower/shorter than this footprint.
    static final int STARTBAL_BOX_WIDTH = 250;
    static final int STARTBAL_BOX_HEIGHT = 54;
    private static final int STARTBAL_TEXT_WIDTH = 190;
    private static final int STARTBAL_RING_RADIUS = 16;

    private void renderStartBalBox(GuiGraphicsExtractor guiGraphics) {
        if (!config.startBalBoxEnabled) return;
        Minecraft client = Minecraft.getInstance();

        int x = config.startBalBoxX;
        int y = config.startBalBoxY;

        boolean hasGoal = config.goalAmount > 0;
        double earned = config.earnedTotal;
        boolean completed = hasGoal && earned >= config.goalAmount;

        // The ring only has something meaningful to show once there's a
        // reference point to measure against: a goal, a captured starting
        // balance, or we're mid-capture (shown as a static 0%/gray ring).
        boolean showRing = hasGoal || config.trackingEnabled || config.awaitingBalCapture;
        // Goal mode measures earned against the goal. Track mode (captured
        // via /bal) measures earned against the starting balance itself -
        // e.g. start with 300m, earn 150m -> 50%.
        double percentBase = hasGoal ? config.goalAmount
                : (config.hasStartingBalance ? config.startingBalance : 0);
        double fraction = (!config.awaitingBalCapture && percentBase > 0) ? earned / percentBase : 0.0;
        double clampedFraction = Math.max(0.0, Math.min(1.0, fraction));

        int boxWidth = showRing ? STARTBAL_BOX_WIDTH : STARTBAL_TEXT_WIDTH;
        int boxHeight = showRing ? STARTBAL_BOX_HEIGHT : 40;

        guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, 0xA0000000);
        Gfx.outline(guiGraphics, x, y, boxWidth, boxHeight, config.startBalBoxColor);

        String title = completed ? "Goal Complete!" : "Starting Bal";
        guiGraphics.text(client.font, title, x + 6, y + 6, completed ? 0xFF33FF33 : config.startBalBoxColor, true);

        // --- Left side: the original text table, unchanged ---
        if (config.awaitingBalCapture) {
            guiGraphics.text(client.font, "Run /bal now...", x + 6, y + 20, 0xFFFFCC66, true);
        } else if (hasGoal) {
            guiGraphics.text(client.font, "$" + formatCompact(earned) + " / $" + formatCompact(config.goalAmount),
                    x + 6, y + 20, 0xFFCCCCCC, true);
        } else if (config.trackingEnabled) {
            guiGraphics.text(client.font, "Tracking: $" + formatCompact(earned), x + 6, y + 20, 0xFFCCCCCC, true);
            if (config.hasStartingBalance) {
                guiGraphics.text(client.font, "from $" + formatCompact(config.startingBalance), x + 6, y + 31, 0xFF888888, true);
            }
        } else {
            guiGraphics.text(client.font, "No goal set - /start_bal goal set", x + 6, y + 20, 0xFFAAAAAA, true);
        }

        // --- Right side: circle, gray at 0% and filling green with progress ---
        if (showRing) {
            int ringCenterX = x + STARTBAL_TEXT_WIDTH + (boxWidth - STARTBAL_TEXT_WIDTH) / 2;
            int ringCenterY = y + boxHeight / 2;
            int ringColor = completed ? 0xFF33FF33 : config.startBalBoxColor;

            renderProgressRing(guiGraphics, ringCenterX, ringCenterY, STARTBAL_RING_RADIUS, fraction, ringColor, 0xFF555555);

            String pctText = config.awaitingBalCapture ? "0%" : ((int) Math.round(clampedFraction * 100) + "%");
            drawScaledCentered(guiGraphics, pctText, ringCenterX, ringCenterY - 4, 0.75f,
                    completed ? 0xFF33FF33 : 0xFFFFFFFF);
        }

        if (hasGoal) checkGoalReached(earned);
    }

    /**
     * Draws a donut-style progress ring: small filled squares around the
     * circle, fillColor for the portion completed (sweeping clockwise from
     * 12 o'clock) and emptyColor for the rest - same tick-ring technique as
     * renderCountdownRing below, just filling up instead of draining down.
     * At fraction 0.0 every tick is emptyColor, so the ring reads as plain
     * gray until progress starts coming in.
     */
    private void renderProgressRing(GuiGraphicsExtractor guiGraphics, int centerX, int centerY, int radius,
                                     double fraction, int fillColor, int emptyColor) {
        int tickCount = 28;
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        int filledTicks = (int) Math.round(tickCount * clamped);

        for (int i = 0; i < tickCount; i++) {
            double angle = (Math.PI * 2 * i / tickCount) - (Math.PI / 2); // 0 = 12 o'clock, sweeps clockwise
            int tx = centerX + (int) Math.round(Math.cos(angle) * radius);
            int ty = centerY + (int) Math.round(Math.sin(angle) * radius);
            boolean filled = i < filledTicks;
            int half = filled ? 2 : 1; // filled ticks drawn a bit thicker so the bar reads clearly
            guiGraphics.fill(tx - half, ty - half, tx + half + 1, ty + half + 1, filled ? fillColor : emptyColor);
        }
    }

    private void checkGoalReached(double earned) {
        if (config.goalReachedNotified) return;
        if (earned < config.goalAmount) return;

        config.goalReachedNotified = true;
        config.celebrationEndTimeMillis = System.currentTimeMillis() + 6000;
        config.save();
        SoundPlayer.playCelebrationSound();
    }

    private void renderCelebration(GuiGraphicsExtractor guiGraphics) {
        if (System.currentTimeMillis() > config.celebrationEndTimeMillis) return;

        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        String msg = "$$$ CONGRATS YOU REACHED YOUR GOAL!!! $$$";
        int centerX = screenWidth / 2;
        int y = screenHeight / 2 - 40;

        drawScaledCentered(guiGraphics, msg, centerX, y, 2.0f, 0xFF33FF33);
    }

    /**
     * Draws text scaled up (or down) around its own horizontal center,
     * using GuiGraphicsExtractor' transform stack. This is genuine scaling, not the
     * old repeated-offset fake-bold trick - noting this since pose-stack
     * based transforms are an area Minecraft has reworked in some recent
     * versions, so it's worth double-checking if this doesn't compile.
     */
    private void drawScaledCentered(GuiGraphicsExtractor guiGraphics, String text, int centerX, int y, float scale, int color) {
        Minecraft client = Minecraft.getInstance();
        int textWidth = client.font.width(text);
        float left = centerX - (textWidth * scale) / 2f;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(left, y);
        guiGraphics.pose().scale(scale, scale);
        guiGraphics.text(client.font, text, 0, 0, color, true);
        guiGraphics.pose().popMatrix();
    }

    /** Formats large numbers compactly for display, e.g. 364000000 -> "364.0M". */
    static String formatCompact(double value) {
        String sign = value < 0 ? "-" : "";
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000) return sign + String.format("%.1fB", abs / 1_000_000_000);
        if (abs >= 1_000_000) return sign + String.format("%.1fM", abs / 1_000_000);
        if (abs >= 1_000) return sign + String.format("%.1fK", abs / 1_000);
        return formatAmount(value); // already handles the sign and rounds to 2 decimals
    }

    // --- Lever (multi-flick) / note block (single click) trigger the advance --

    private void registerLever() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (config.leverPaused) return InteractionResult.PASS; // skip paused - do nothing

            BlockPos pos = hitResult.getBlockPos();
            boolean isLever = level.getBlockState(pos).getBlock() instanceof LeverBlock;
            boolean isNoteBlock = level.getBlockState(pos).getBlock() instanceof NoteBlock;
            if (!isLever && !isNoteBlock) {
                return InteractionResult.PASS;
            }

            if (config.restrictLever) {
                boolean matches = pos.getX() == config.leverX
                        && pos.getY() == config.leverY
                        && pos.getZ() == config.leverZ;
                if (!matches) {
                    return InteractionResult.PASS;
                }
            }

            if (isNoteBlock) {
                // Note blocks advance immediately on a single click - no flick counting.
                advanceQueue();
            } else {
                registerFlick();
            }
            return InteractionResult.PASS; // let the block still respond normally
        });
    }

    private void registerKeybindTick() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Tutorial.tick(client);
            while (TOGGLE_LEVER_SKIP_KEY.consumeClick()) {
                config.leverPaused = !config.leverPaused;
                config.save();
                if (client.player != null && config.queueChatMessagesEnabled) {
                    say(client.player, 
                            Component.literal("[DonutQueue] Lever skip " + (config.leverPaused ? "PAUSED" : "resumed")));
                }
            }

            while (OPEN_MENU_KEY.consumeClick()) {
                if (client.gui.screen() == null) {
                    if (Tutorial.isActive()) {
                        // during a tutorial the menu key brings back the guide (it has an OPEN MENU button)
                        Tutorial.waitingWorld = false;
                        client.gui.setScreen(new TutorialScreen());
                    } else {
                        client.gui.setScreen(new FuturisticMenuScreen(null));
                    }
                }
            }

            while (OPEN_BIDDING_KEY.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new BiddingMenuScreen());
                }
            }

            while (SKIP_NEXT_KEY.consumeClick()) {
                flickCount = 0; // don't let a half-finished lever flick carry over
                advanceQueue();
            }
        });
    }

    private void registerFlick() {
        Minecraft client = Minecraft.getInstance();
        flickCount++;

        int needed = Math.max(1, config.flicksRequired);
        if (flickCount < needed) {
            if (client.player != null && config.queueChatMessagesEnabled) {
                int left = needed - flickCount;
                say(client.player, 
                        Component.literal("[DonutQueue] " + left + " more flick" + (left == 1 ? "" : "s") + " to advance."));
            }
            return;
        }

        flickCount = 0;
        advanceQueue();
    }

    public static void advanceQueue() {
        Minecraft client = Minecraft.getInstance();
        QueueEntry served = queue.pollNext();
        QueueEntry next = queue.peekNext();

        if (served != null) Tutorial.event("SKIP");

        if (client.player == null) return;

        if (served == null) {
            if (config.queueChatMessagesEnabled) {
                say(client.player, Component.literal("[DonutQueue] The queue is already empty."));
            }
            return;
        }

        if (config.queueChatMessagesEnabled) {
            String msg = "[DonutQueue] Served " + served.playerName + " ($" + served.amount + "). "
                    + (next != null ? ("Now first: " + next.playerName) : "Queue is now empty.");
            say(client.player, Component.literal(msg));
        }
    }

    // --- Per-section chat feedback wrappers ---------------------------------
    //
    // Thin wrappers around feedback() below, each gated by that section's
    // own chat-messages toggle. Every /dq, /queue, /start_bal, /bidding and
    // /earnings command reply goes through the matching one of these,
    // rather than calling feedback() directly, so the "Chat Messages: ON/OFF"
    // toggle in each settings screen covers both a section's automatic
    // notifications (handled above) and its command replies in one switch.

    private static void queueFeedback(CommandContext<FabricClientCommandSource> ctx, String msg) {
        // chat messages removed
    }

    private static void startBalFeedback(CommandContext<FabricClientCommandSource> ctx, String msg) {
        // chat messages removed
    }

    private static void biddingFeedback(CommandContext<FabricClientCommandSource> ctx, String msg) {
        // chat messages removed
    }

    private static void earningsFeedback(CommandContext<FabricClientCommandSource> ctx, String msg) {
        // chat messages removed
    }

    // --- "Always show" replies for read-only query commands -----------------
    //
    // A command like /queue history, /dq list or /earnings list has no
    // point except to print something to chat - muting it along with
    // action confirmations would make the command useless. These bypass
    // the section's Chat Messages toggle entirely, on purpose.

    private static void queueInfo(CommandContext<FabricClientCommandSource> ctx, String msg) {
        feedback(ctx, msg);
    }

    private static void startBalInfo(CommandContext<FabricClientCommandSource> ctx, String msg) {
        feedback(ctx, msg);
    }

    private static void biddingInfo(CommandContext<FabricClientCommandSource> ctx, String msg) {
        feedback(ctx, msg);
    }

    private static void earningsInfo(CommandContext<FabricClientCommandSource> ctx, String msg) {
        feedback(ctx, msg);
    }

    // --- Commands -----------------------------------------------------------

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommands.literal("dq")
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("player", StringArgumentType.word())
                                        .then(ClientCommands.argument("amount", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String player = StringArgumentType.getString(ctx, "player");
                                                    String amount = StringArgumentType.getString(ctx, "amount");
                                                    queue.add(new QueueEntry(player, amount, System.currentTimeMillis()));
                                                    queueFeedback(ctx, "Added " + player + " to the queue.");
                                                    Tutorial.event("QUEUE_ADD");
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("guarddebug")
                                .executes(ctx -> {
                                    Guards.debug = !Guards.debug;
                                    ctx.getSource().sendFeedback(Component.literal("[Guard] debug " + (Guards.debug ? "ON - click an auction listing and read the [Guard] lines in chat." : "OFF.")));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("next")
                                .executes(ctx -> {
                                    flickCountResetHack();
                                    advanceQueue();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String player = StringArgumentType.getString(ctx, "player");
                                            boolean removed = queue.removeByName(player);
                                            queueFeedback(ctx, removed ? ("Removed " + player) : (player + " was not in the queue."));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("clear")
                                .executes(ctx -> {
                                    queue.clear();
                                    queueFeedback(ctx, "Queue cleared.");
                                    Tutorial.event("BTN:Clear Queue");
                                    return 1;
                                }))
                        .then(ClientCommands.literal("list")
                                .executes(ctx -> {
                                    List<QueueEntry> entries = queue.asList();
                                    if (entries.isEmpty()) {
                                        queueInfo(ctx, "Queue is empty.");
                                    } else {
                                        StringBuilder sb = new StringBuilder("Queue: ");
                                        for (int i = 0; i < entries.size(); i++) {
                                            sb.append(i + 1).append(". ").append(entries.get(i).playerName)
                                                    .append(" ($").append(entries.get(i).amount).append(")  ");
                                        }
                                        queueInfo(ctx, sb.toString());
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("hud")
                                .then(ClientCommands.literal("show").executes(ctx -> {
                                    config.hudEnabled = true;
                                    config.save();
                                    queueFeedback(ctx, "HUD shown.");
                                    return 1;
                                }))
                                .then(ClientCommands.literal("hide").executes(ctx -> {
                                    config.hudEnabled = false;
                                    config.save();
                                    queueFeedback(ctx, "HUD hidden.");
                                    return 1;
                                }))
                                .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                        .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    config.hudX = IntegerArgumentType.getInteger(ctx, "x");
                                                    config.hudY = IntegerArgumentType.getInteger(ctx, "y");
                                                    config.save();
                                                    queueFeedback(ctx, "HUD moved.");
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("flicks")
                                .then(ClientCommands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            config.flicksRequired = IntegerArgumentType.getInteger(ctx, "count");
                                            config.save();
                                            queueFeedback(ctx, "Now takes " + config.flicksRequired + " flick(s) to advance.");
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("setlever")
                                .executes(ctx -> {
                                    Minecraft client = Minecraft.getInstance();
                                    if (client.hitResult instanceof BlockHitResult blockHit) {
                                        BlockPos pos = blockHit.getBlockPos();
                                        config.restrictLever = true;
                                        config.leverX = pos.getX();
                                        config.leverY = pos.getY();
                                        config.leverZ = pos.getZ();
                                        config.save();
                                        queueFeedback(ctx, "Bound queue advance to the block at " + pos.toShortString());
                                    } else {
                                        queueFeedback(ctx, "Look directly at a lever or note block first.");
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("debugchat")
                                .executes(ctx -> {
                                    debugChat = !debugChat;
                                    queueFeedback(ctx, "Chat debug logging " + (debugChat ? "ON - every chat line will be echoed with its exact text." : "OFF."));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("unsetlever")
                                .executes(ctx -> {
                                    config.restrictLever = false;
                                    config.save();
                                    queueFeedback(ctx, "Any lever will now advance the queue.");
                                    return 1;
                                }))
            );

            dispatcher.register(
                ClientCommands.literal("queue")
                        .then(ClientCommands.literal("history")
                                .executes(ctx -> {
                                    showHistory(ctx, 10);
                                    return 1;
                                })
                                .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1, 25))
                                        .executes(ctx -> {
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            showHistory(ctx, amount);
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("blacklist")
                                .then(ClientCommands.literal("add")
                                        .then(ClientCommands.argument("player", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String player = StringArgumentType.getString(ctx, "player");
                                                    if (isBlacklistedStatic(player)) {
                                                        queueFeedback(ctx, player + " is already blacklisted.");
                                                    } else {
                                                        config.blacklist.add(player);
                                                        config.save();
                                                        int removed = queue.removeAllByName(player);
                                                        queueFeedback(ctx, "Blacklisted " + player
                                                                + (removed > 0 ? " and removed them from the queue." : "."));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("remove")
                                        .then(ClientCommands.argument("player", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String player = StringArgumentType.getString(ctx, "player");
                                                    boolean removed = config.blacklist.removeIf(n -> n.equalsIgnoreCase(player));
                                                    if (removed) config.save();
                                                    queueFeedback(ctx, removed ? ("Removed " + player + " from the blacklist.")
                                                            : (player + " wasn't blacklisted."));
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("list")
                                        .executes(ctx -> {
                                            if (config.blacklist.isEmpty()) {
                                                queueInfo(ctx, "Blacklist is empty.");
                                            } else {
                                                queueInfo(ctx, "Blacklisted: " + String.join(", ", config.blacklist));
                                            }
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("clear")
                                        .executes(ctx -> {
                                            config.blacklist.clear();
                                            config.save();
                                            queueFeedback(ctx, "Blacklist cleared.");
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("minimum")
                                .executes(ctx -> {
                                    queueInfo(ctx, config.minimumAmount > 0
                                            ? ("Current minimum: $" + formatAmount(config.minimumAmount))
                                            : "No minimum set - all payments are added.");
                                    return 1;
                                })
                                .then(ClientCommands.argument("amount", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String raw = StringArgumentType.getString(ctx, "amount");
                                            double parsed = parseAmount(raw);
                                            config.minimumAmount = parsed;
                                            config.save();
                                            queueFeedback(ctx, parsed > 0
                                                    ? ("Minimum payment set to $" + formatAmount(parsed))
                                                    : "Minimum cleared - all payments are added.");
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("disable")
                                .executes(ctx -> {
                                    config.leverPaused = !config.leverPaused;
                                    config.save();
                                    queueFeedback(ctx, "Lever/note block skip " + (config.leverPaused ? "PAUSED" : "resumed") + ".");
                                    return 1;
                                }))
            );

            dispatcher.register(
                ClientCommands.literal("start_bal")
                        .then(ClientCommands.literal("goal")
                                .then(ClientCommands.literal("set")
                                        .then(ClientCommands.argument("amount", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    double goal = parseAmount(StringArgumentType.getString(ctx, "amount"));
                                                    if (goal <= 0) {
                                                        startBalFeedback(ctx, "Give a valid goal amount, e.g. /start_bal goal set 500m");
                                                        return 1;
                                                    }
                                                    config.goalAmount = goal;
                                                    config.earnedTotal = 0;
                                                    config.goalReachedNotified = false;
                                                    config.trackingEnabled = false;
                                                    config.awaitingBalCapture = false;
                                                    config.save();
                                                    startBalFeedback(ctx, "Goal set to $" + formatAmount(goal)
                                                            + ". Tracking starts now - sell/earn messages count up, purchases count down. "
                                                            + "The ring will show % toward this goal.");
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("clear")
                                        .executes(ctx -> {
                                            config.goalAmount = 0;
                                            config.earnedTotal = 0;
                                            config.goalReachedNotified = false;
                                            config.trackingEnabled = false;
                                            config.awaitingBalCapture = false;
                                            config.hasStartingBalance = false;
                                            config.save();
                                            startBalFeedback(ctx, "Goal cleared.");
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("check")
                                        .executes(ctx -> {
                                            if (config.goalAmount > 0) {
                                                startBalInfo(ctx, "Earned so far: $" + formatCompact(config.earnedTotal)
                                                        + " / $" + formatCompact(config.goalAmount));
                                            } else if (config.awaitingBalCapture) {
                                                startBalInfo(ctx, "Still waiting on your /bal response to capture a starting balance.");
                                            } else if (config.trackingEnabled) {
                                                startBalInfo(ctx, "Tracking: $" + formatCompact(config.earnedTotal)
                                                        + (config.hasStartingBalance
                                                                ? " (started from $" + formatCompact(config.startingBalance) + ")"
                                                                : ""));
                                            } else {
                                                startBalInfo(ctx, "No goal set yet. Use /start_bal goal set <amount> or /start_bal track");
                                            }
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("debugearn")
                                        .executes(ctx -> {
                                            debugChat = !debugChat;
                                            startBalFeedback(ctx, "Earn/spend chat debug " + (debugChat ? "ON" : "OFF")
                                                    + " - reuses the same debug log as /dq debugchat, check logs/latest.log");
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("track")
                                .executes(ctx -> {
                                    config.goalAmount = 0;
                                    config.earnedTotal = 0;
                                    config.goalReachedNotified = false;
                                    config.trackingEnabled = false;
                                    config.hasStartingBalance = false;
                                    config.awaitingBalCapture = true;
                                    config.save();
                                    startBalFeedback(ctx, "Run /bal now - I'll grab the number from the response as your starting balance, "
                                            + "then track earn/spend messages from there (can go negative). Use /start_bal goal clear to stop.");
                                    return 1;
                                }))
            );

            // The only bidding command: /bidding <preset number> starts that saved auction.
            // Presets are made in the Bidding menu (hotkey, B by default).
            dispatcher.register(
                ClientCommands.literal("bidding")
                        .then(ClientCommands.argument("preset", IntegerArgumentType.integer(1, ModConfig.BID_PRESET_SLOTS))
                                .executes(ctx -> {
                                    int n = IntegerArgumentType.getInteger(ctx, "preset");
                                    if (!Auction.startPreset(n)) {
                                        biddingInfo(ctx, "Bidding preset " + n + " is empty - set it up in the Bidding menu first.");
                                    }
                                    return 1;
                                }))
            );

            dispatcher.register(
                ClientCommands.literal("earnings")
                        .then(ClientCommands.literal("track")
                                .executes(ctx -> {
                                    config.earningsTrackingEnabled = true;
                                    config.earningsTotals.clear();
                                    config.earningsBoxEnabled = true;
                                    config.save();
                                    earningsFeedback(ctx, "Now tracking earnings - top " + config.earningsShowCount
                                            + " payer(s) will show in the Earnings box. /earnings stop to pause, "
                                            + "/earnings reset to clear without stopping.");
                                    return 1;
                                }))
                        .then(ClientCommands.literal("stop")
                                .executes(ctx -> {
                                    config.earningsTrackingEnabled = false;
                                    config.save();
                                    earningsFeedback(ctx, "Earnings tracking stopped (totals kept). /earnings track to resume.");
                                    return 1;
                                }))
                        .then(ClientCommands.literal("nongamble")
                                .then(ClientCommands.literal("add")
                                        .then(ClientCommands.argument("player", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String player = StringArgumentType.getString(ctx, "player");
                                                    DailyEarnings.markNonGamble(player);
                                                    earningsFeedback(ctx, player + " marked non gamble - hidden from the lost-most list.");
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("remove")
                                        .then(ClientCommands.argument("player", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String player = StringArgumentType.getString(ctx, "player");
                                                    boolean removed = DailyEarnings.unmarkNonGamble(player);
                                                    earningsFeedback(ctx, removed ? (player + " is counted again.") : (player + " was not marked non gamble."));
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("list")
                                        .executes(ctx -> {
                                            earningsFeedback(ctx, config.nonGamblePlayers.isEmpty()
                                                    ? "Nobody is marked non gamble."
                                                    : "Non gamble: " + String.join(", ", config.nonGamblePlayers));
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("clear")
                                        .executes(ctx -> {
                                            DailyEarnings.clearNonGamble();
                                            earningsFeedback(ctx, "Non gamble list cleared.");
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("reset")
                                .executes(ctx -> {
                                    config.earningsTotals.clear();
                                    config.save();
                                    earningsFeedback(ctx, "Earnings totals cleared.");
                                    return 1;
                                }))
                        .then(ClientCommands.literal("show")
                                .executes(ctx -> {
                                    config.earningsBoxEnabled = true;
                                    config.save();
                                    earningsFeedback(ctx, "Earnings box shown.");
                                    return 1;
                                }))
                        .then(ClientCommands.literal("hide")
                                .executes(ctx -> {
                                    config.earningsBoxEnabled = false;
                                    config.save();
                                    earningsFeedback(ctx, "Earnings box hidden.");
                                    return 1;
                                }))
                        .then(ClientCommands.literal("top")
                                .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 25))
                                        .executes(ctx -> {
                                            config.earningsShowCount = IntegerArgumentType.getInteger(ctx, "count");
                                            config.save();
                                            earningsFeedback(ctx, "Now showing top " + config.earningsShowCount + " earner(s).");
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("list")
                                .executes(ctx -> {
                                    showEarningsList(ctx, config.earningsShowCount);
                                    return 1;
                                })
                                .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 25))
                                        .executes(ctx -> {
                                            showEarningsList(ctx, IntegerArgumentType.getInteger(ctx, "count"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("check")
                                .executes(ctx -> {
                                    earningsInfo(ctx, config.earningsTrackingEnabled
                                            ? ("Tracking earnings - " + config.earningsTotals.size() + " payer(s) recorded so far.")
                                            : "Not tracking earnings right now - /earnings track to start.");
                                    return 1;
                                }))
            );
        });
    }

    private static boolean isBlacklistedStatic(String player) {
        for (String name : config.blacklist) {
            if (name.equalsIgnoreCase(player)) return true;
        }
        return false;
    }

    private static void showHistory(CommandContext<FabricClientCommandSource> ctx, int amount) {
        List<QueueEntry> history = queue.getHistory(amount);
        if (history.isEmpty()) {
            queueInfo(ctx, "No one has been served yet.");
            return;
        }
        queueInfo(ctx, "Last " + history.size() + " served (newest first):");
        for (int i = 0; i < history.size(); i++) {
            QueueEntry e = history.get(i);
            ctx.getSource().sendFeedback(Component.literal((i + 1) + ". " + e.playerName + " - $" + e.amount));
        }
    }

    private static void showEarningsList(CommandContext<FabricClientCommandSource> ctx, int count) {
        List<Map.Entry<String, Double>> entries = topEarners(Math.max(1, Math.min(25, count)));
        if (entries.isEmpty()) {
            earningsInfo(ctx, "No earnings recorded yet.");
            return;
        }
        earningsInfo(ctx, "Top " + entries.size() + " earner(s) (highest first):");
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Double> e = entries.get(i);
            ctx.getSource().sendFeedback(Component.literal((i + 1) + ". " + e.getKey() + " - " + formatMoneySigned(e.getValue(), false)));
        }
    }

    // Manual /dq next should also reset the in-progress flick count so it
    // doesn't carry over oddly into the next person's flicks.
    private void flickCountResetHack() {
        flickCount = 0;
    }

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Component.literal("[DonutQueue] " + msg));
    }
}
