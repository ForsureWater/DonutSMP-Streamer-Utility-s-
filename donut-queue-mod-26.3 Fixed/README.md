# Forsurewater Streamer Utility (26.3 build)

Fabric client mod for Minecraft 26.3 (Java 25+).

- Watches DonutSMP chat for payments and keeps a queue / HUD of who is first in line.
- Neon menu (Right Shift by default) with Anti Gamble Skip, Starting Bal, Bidding, Info Display, Earnings, Sessions and Customisation pages (Customisation has Ranks and Menu customisation).
- Earnings > Stats: line graph of your gains (default) or balance, today by default, 10-minute steps.
- Earnings right rail: top 7 people who paid you the most that day. The button under it ("Show Most Lost" / "Show Most Earned") swaps the list to the 7 people you lost the most money to that day (you paid them more than they paid you). Your choice is remembered.

## Bidding menu & presets

Press **B** (rebindable in Controls > DonutSMP Streamer Utilitys > "Open Bidding Menu") to open the auction presets (8 slots):

- **Left side:** pick an item (searchable grid, or **Held** for the item in your hand), type the amount and the time (`60`, `5m`, `1h`) and press **START** for a one-off auction. **STOP** ends the running one.
- **Right side, presets (8 slots):** click an **empty** slot to make a preset: an editor opens where you choose the item, amount and time and press **CONFIRM** (presets are never made from what is selected on the left). Click a **filled** slot to start it. **E** edits a slot, **X** empties it.
- **MIN BID** (left side, and in every preset's editor) sets the minimum bid for that auction. A preset starts with its own minimum.
- The **gear** button next to Tutorial opens the remaining Bidding settings from the neon menu: show/hide box, move box, size/colour/blacklist (time, minimum bid and stop are on the menu itself).
- The Bidding box shows the item and amount while an auction runs.
- `/bidding <1-8>` starts the same preset from chat. It is the only bidding command; the old `/bidding start|stop|minimum|show|hide|blacklist` commands are gone (minimum bid, box, colour, size and blacklist are still in the neon menu's Bidding page).

## Guards (Customisation > Guards)

Two safety nets that ask "are you sure?" before money leaves your pocket. Both are OFF by default; each has an ON/OFF button and an amount (`k`, `m`, `b` work, e.g. `50k`).

- **Pay Guard:** typing `/pay <player> <amount>` with an amount at or above your limit opens a confirm menu ("Are you sure you want to pay <player> $<amount>?"). The command is only sent when you press Confirm.
- **Auction Guard:** clicking an auction house listing priced at or above your limit opens a confirm menu ("Are you sure you want to buy <n>x <item> for $<price>?"). The price is read from the listing's lore (a `Price:` / `Cost:` / `Buy Now:` line, small-caps text included; on an auction-looking screen any `$amount` line is used as a fallback). If it still doesn't trigger, run `/dq guarddebug`, click a listing, and read the `[Guard]` lines in chat. Like the Pay Guard, our popup is the only confirmation: after you press Confirm the mod also presses the server's own confirm page for you (so you are not asked twice). The popup text is grey with the item and the price in green.

## New additions

The **New additions** button in the neon menu's header lists what is new in this version. The Bidding tab was removed from the sidebar (it all lives in the Bidding menu now).

## Tutorials

Every page of the neon menu (Main Menu, Anti Gamble Skip, Starting Bal, Bidding, Info Display, Earnings, Sessions, Customisation) has a **Tutorial** button at the bottom of the right-hand rail, and the Bidding presets menu has one too.

Each tutorial starts with a "What you'll need" step (for example a lever or note block for Anti Gamble Skip). A tutorial is a step-by-step guide. Info steps just need **Next**. "Do it" steps wait until you really do the thing (press the skip key, flick a lever, set a minimum, start a session...) and move on by themselves. While you try something the guide is closed and a small box at the top of the screen tells you what to do; the menu key ([Right Shift] by default) brings the guide back, where you can also skip a step or exit.

The Anti Gamble Skip tutorial walks through everything: payments joining the queue, skipping with the key, a lever, a note block and `/dq next`, pausing and resuming lever skip, the minimum payment, a too-small payment, blacklisted players, whispered payments and clearing the queue. Payments in tutorials are **simulated** (buttons in the guide) and only touch the queue / bidding, never your earnings.

## Building

Needs JDK 25+ (run with `./gradlew`; Loom 1.19, Gradle 9.8, no mappings - 26.x is unobfuscated).

    gradle build

The jar ends up in `build/libs/`. Requires Fabric Loader 0.20.1+ and Fabric API 0.165.0+ for 26.3. Mod Menu is optional.

## Customisation > Ranks

Menu > Customisation > Ranks:

- **Enable Rank colours** (top button, off by default) recolours names by rank in the tab list, over heads and on DonutSMP's floating name tags, and shows your own tag in F5.
- **Change staff team colour**: hex colour for staff names and their star (default 5555FF). Reset puts it back.

Other rank rules (per-logo colours, `self_tag`, `self_scale`, `self_height`, `self_logo`, `stack_plus`) are still in `config/rankcolors.properties`; restart the game after editing it.

## Customisation > Menu customisation

Menu > Customisation > Menu customisation opens a small hub with five sections. Everything saves straight away and shows up live:

- **Menu colour**: 8 presets (Blue, Purple, Pink, Red, Orange, Gold, Green, Cyan) or any hex colour. The whole menu and every settings screen is re-shaded from it, including the graphs, the little balance chart and the donut logo (its blue frosting turns into the menu colour). Blue (2F6BFF) is the original look; Reset puts it back.
- **Main menu items**: opens the real menu in edit mode.
  - Click a card (Start Bal, Today, Stats row) to pick it up, then click another card to drop it into that spot (the held card follows your mouse; ESC puts it back).
  - **Click the Graph** to change what it shows: range (Today in 10-minute steps / All time per day) and value (Balance / Gains). The popup also has "Move this card".
  - **Click a bottom button** to change what it does: Session start/end, Stats page, Bidding start/stop, Tracking on/reset, Lever pause/resume, or jump to the Earnings / Sessions / Anti Gamble pages. Start Bidding reuses your last round time (60s the first time).
  - **Left side: 2 BUTTONS / 4 BUTTONS** switches the bottom row between one row of 2 and a 2 x 2 grid of 4 (each of the 4 can be set separately).
  - The right side has Show / Hide switches for each card, Reset layout and Done. Hidden cards show as ghosts in the editor and take no space in the real menu.
  - The goal bar has been removed from the main menu completely.
- **Sidebar tabs**: opens the real menu with everything faded except the sidebar. Click a tab to hide it (it fades and is gone from the real sidebar), click again to bring it back. Main Menu and Customisation always stay.
- **Animations**: on/off for switching between pages, style **Fade / Slide / Wipe**, speed **Fast / Normal / Slow**, with a looping live preview.
- **GUI style**: **Neon** (glow outline) / **Flat** (solid, no glow) / **Glass** (see-through), backdrop dim on/off and panel opacity 40-100%.

"Reset menu" on the hub puts all of the above back to the defaults. Settings live in `config/donutqueue.json` (`menuColor`, `menuStyle`, `menuAnimStyle`, `mainShow*`, `mainOrder`, `mainFourButtons`, `mainButtons`, `mainGraphRange`, `mainGraphGains`, `menuHiddenTabs`, ...).

## Payments log

Earnings > Payments (and a session's payments) now has a scrollbar you can click and drag (the mouse wheel still works), and a "search name" box. Type part of a name and the list shows every payment to and from anyone matching it, with the totals of what they paid you and what you paid them. All / Got / Paid still filter on top of the search.

## Addons (1.9.1+)

Other mods can add a page to the neon menu's sidebar. An addon implements `com.example.donutqueue.DonutQueueAddon` and registers it in its own `fabric.mod.json`:

    "entrypoints": { "donutqueue-addon": ["your.mod.YourAddon"] }

- `menuLabel()` is the text of the sidebar button, `createScreen(parent)` builds the screen it opens (go back to `parent` when it closes).
- `DonutQueueApi.palette()` returns the menu's current colours (it follows Menu customisation), so an addon can match the look.
- Addons are loaded at startup. A broken addon is skipped and never stops the menu. At most as many addon rows are shown as fit above the "ESC - close" hint (one, with the default tabs).
- Fabric only loads an addon's entrypoint class when this mod asks for it, so addons keep working (just without the menu entry) when this mod is not installed.

First addon: **Spotify HUD** (separate mod, see its own README).


## Screen features (Customisation > Screen features)

- **On Screen KBM:** shows your keys live. Pick how much shows: WASD, WASD + Mouse, WASD + Space + Mouse, or the full keyboard (Ctrl, Shift, Space) + Mouse.
- **CPS Counter:** left | right clicks per second (default spot is under the mouse buttons).
- **FPS Counter:** your current frames per second.
- Each one has its own **Move** button (drag it anywhere) and there is one **Size** button for all three. All are OFF until you turn them on.
